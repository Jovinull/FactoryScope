package factoryscope.mpfixture;

import arc.*;
import arc.util.*;
import factoryscope.mpshared.*;
import factoryscope.probe.*;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.mod.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.environment.*;
import mindustry.world.blocks.sandbox.*;
import mindustry.world.blocks.units.*;

import static mindustry.Vars.*;

/**
 * Development-only dedicated-server fixture for the multiplayer acceptance run.
 *
 * <p>When the server has loaded it builds a small generated world with fog of war: the player team's factory
 * fixtures around its core, one enemy building inside that team's vision and one far outside it. It then opens
 * the server on the port the launcher chose and answers the client harness's truth requests with digests of
 * what FactoryScope concludes from the server's own, authoritative state.
 */
public class MultiplayerFixture extends Mod{
    static final String TAG = "[MP-FIXTURE]";

    public MultiplayerFixture(){
        Events.on(ServerLoadEvent.class, event -> Core.app.post(this::host));
    }

    void host(){
        try{
            int port = Integer.getInteger("factoryscope.mp.port", 6567);
            Core.settings.put("port", port);

            logic.reset();
            Rules rules = new Rules();
            rules.fog = true;
            rules.staticFog = true;
            rules.waves = false;
            rules.waveTimer = false;
            rules.canGameOver = false;
            rules.defaultTeam = Team.sharded;
            rules.waveTeam = Team.crux;
            state.rules = rules;

            world.loadGenerator(MultiplayerDigest.WORLD_SIZE, MultiplayerDigest.WORLD_SIZE, tiles -> {
                for(int x = 0; x < tiles.width; x++){
                    for(int y = 0; y < tiles.height; y++){
                        tiles.set(x, y, new Tile(x, y, Blocks.stone, Blocks.air, Blocks.air));
                    }
                }
            });
            state.rules = rules;
            //NetworkIO.writeWorld sends the map tags to every joining client
            state.map = new mindustry.maps.Map(arc.struct.StringMap.of("name", "FactoryScope multiplayer fixture"));
            Events.fire(new RulesLoadEvent(rules));
            logic.play();
            build();
            netServer.openServer();
            netServer.addPacketHandler(MultiplayerDigest.TRUTH, (player, content) ->
                Call.clientPacketReliable(player.con, MultiplayerDigest.TRUTH, truth()));
            Log.info(TAG + " hosting on port @", port);
        }catch(Throwable t){
            Log.err(TAG + " could not host the fixture world", t);
        }
    }

    void build(){
        place(Blocks.coreShard, MultiplayerDigest.CORE_X, MultiplayerDigest.CORE_Y, Team.sharded, 0);
        place(Blocks.coreShard, MultiplayerDigest.ENEMY_CORE_X, MultiplayerDigest.ENEMY_CORE_Y, Team.crux, 0);
        Building core = world.build(MultiplayerDigest.CORE_X, MultiplayerDigest.CORE_Y);
        if(core != null) core.items.add(Items.copper, 500);

        //a running smelter: both inputs from item sources, power from an adjacent source, output into a void
        int[] ok = position(MultiplayerDigest.SMELTER_OK);
        place(Blocks.siliconSmelter, ok[0], ok[1], Team.sharded, 0);
        source(Items.sand, ok[0] - 1, ok[1], Team.sharded);
        source(Items.coal, ok[0] - 1, ok[1] + 1, Team.sharded);
        place(Blocks.powerSource, ok[0], ok[1] + 2, Team.sharded, 0);
        place(Blocks.itemVoid, ok[0] + 2, ok[1], Team.sharded, 0);

        //a smelter whose only sand route comes from an unpowered drill: a stable, traceable shortage
        int[] starved = position(MultiplayerDigest.SMELTER_STARVED);
        int[] drill = position(MultiplayerDigest.LASER_DRILL);
        for(int x = drill[0] - 1; x <= drill[0] + 1; x++){
            for(int y = drill[1] - 1; y <= drill[1] + 1; y++) world.tile(x, y).setFloor((Floor)Blocks.sand);
        }
        place(Blocks.laserDrill, drill[0], drill[1], Team.sharded, 0);
        for(int x = drill[0] + 2; x < starved[0]; x++) place(Blocks.conveyor, x, drill[1], Team.sharded, 0);
        place(Blocks.siliconSmelter, starved[0], starved[1], Team.sharded, 0);
        source(Items.coal, starved[0], starved[1] + 2, Team.sharded);
        place(Blocks.powerSource, starved[0] + 2, starved[1], Team.sharded, 0);

        //a kiln with its items but only an unfuelled generator on its grid
        int[] kiln = position(MultiplayerDigest.KILN);
        place(Blocks.kiln, kiln[0], kiln[1], Team.sharded, 0);
        int[] generator = position(MultiplayerDigest.GENERATOR);
        place(Blocks.combustionGenerator, generator[0], generator[1], Team.sharded, 0);
        source(Items.sand, kiln[0] + 2, kiln[1], Team.sharded);
        source(Items.lead, kiln[0] + 2, kiln[1] + 1, Team.sharded);

        //a mixer whose water can only come from an unpowered pump: a stable, traceable liquid shortage
        int[] mixer = position(MultiplayerDigest.MIXER);
        int[] pump = position(MultiplayerDigest.PUMP);
        for(int x = pump[0]; x <= pump[0] + 1; x++){
            for(int y = pump[1]; y <= pump[1] + 1; y++) world.tile(x, y).setFloor((Floor)Blocks.water);
        }
        place(Blocks.rotaryPump, pump[0], pump[1], Team.sharded, 0);
        for(int x = pump[0] + 2; x < mixer[0]; x++) place(Blocks.conduit, x, pump[1], Team.sharded, 0);
        place(Blocks.cryofluidMixer, mixer[0], mixer[1], Team.sharded, 0);
        place(Blocks.powerSource, mixer[0] + 2, mixer[1], Team.sharded, 0);
        source(Items.titanium, mixer[0], mixer[1] + 2, Team.sharded);

        //a loaded turret with nothing to shoot at, and a unit factory with nothing selected
        int[] duo = position(MultiplayerDigest.DUO);
        Building turret = place(Blocks.duo, duo[0], duo[1], Team.sharded, 0);
        for(int i = 0; i < 6; i++) turret.handleItem(null, Items.copper);
        int[] factory = position(MultiplayerDigest.FACTORY);
        Building unitFactory = place(Blocks.groundFactory, factory[0], factory[1], Team.sharded, 0);
        ((UnitFactory.UnitFactoryBuild)unitFactory).currentPlan = -1;

        //an enemy building inside the player team's vision, and one far outside it holding synced items
        int[] visible = position(MultiplayerDigest.VISIBLE_ENEMY);
        place(Blocks.kiln, visible[0], visible[1], Team.crux, 0);
        int[] hidden = position(MultiplayerDigest.HIDDEN_ENEMY);
        place(Blocks.siliconSmelter, hidden[0], hidden[1], Team.crux, 0);
        source(Items.sand, hidden[0] - 1, hidden[1], Team.crux);
        source(Items.coal, hidden[0] - 1, hidden[1] + 1, Team.crux);
        place(Blocks.powerSource, hidden[0], hidden[1] + 2, Team.crux, 0);
    }

    /** One line per fixture plus the area report, each a digest of the server's own FactoryScope result. */
    String truth(){
        StringBuilder text = new StringBuilder();
        for(String name : MultiplayerDigest.FIXTURES.keySet()){
            Building build = fixture(name);
            text.append(name).append('=').append(build == null ? "missing" : MultiplayerDigest.building(build)).append('\n');
        }
        text.append(MultiplayerDigest.AREA_KEY).append('=')
            .append(MultiplayerDigest.area(AreaProbe.scan(MultiplayerDigest.AREA, Team.sharded))).append('\n');
        return text.toString();
    }

    Building fixture(String name){
        int[] position = position(name);
        return world.build(position[0], position[1]);
    }

    static int[] position(String name){
        return MultiplayerDigest.FIXTURES.get(name);
    }

    Building place(Block block, int x, int y, Team team, int rotation){
        world.tile(x, y).setBlock(block, team, rotation);
        return world.build(x, y);
    }

    void source(Item item, int x, int y, Team team){
        Building source = place(Blocks.itemSource, x, y, team, 0);
        if(source instanceof ItemSource.ItemSourceBuild build) build.outputItem = item;
    }
}
