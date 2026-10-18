package factoryscope.probe;

import arc.*;
import arc.backend.headless.*;
import arc.files.*;
import arc.util.*;
import mindustry.*;
import mindustry.core.*;
import mindustry.game.*;
import mindustry.net.*;
import mindustry.world.*;

import static mindustry.Vars.*;

/**
 * Boots a headless Mindustry with real content so the probe can be tested against the actual game.
 *
 * <p>The sequence mirrors the one Mindustry uses in its own test module. No renderer, no window and no
 * map assets are involved: the world is a small block of tiles created in memory.
 */
final class HeadlessGame{
    private static final Fi DATA_FOLDER = new Fi("build/test_data");
    private static boolean started;

    private HeadlessGame(){
    }

    static synchronized void start(){
        if(started) return;
        started = true;

        boolean[] ready = {false};
        Throwable[] failure = {null};
        Log.useColors = false;

        ApplicationCore core = new ApplicationCore(){
            @Override
            public void setup(){
                DATA_FOLDER.deleteDirectory();
                Core.settings.setDataDirectory(DATA_FOLDER);
                headless = true;
                net = new Net(null);
                tree = new FileTree();
                Vars.init();
                world = new World();
                content.createBaseContent();
                //stand-ins for common mod shapes, registered exactly where a real mod would register them
                ModdedBlocks.create();
                content.init();

                add(logic = new Logic());
                add(netServer = new NetServer());
            }

            @Override
            public void init(){
                super.init();
                ready[0] = true;
                Thread.currentThread().interrupt();
            }
        };

        new HeadlessApplication(core, throwable -> failure[0] = throwable);

        long deadline = System.currentTimeMillis() + 60_000L;
        while(!ready[0]){
            if(failure[0] != null) throw new IllegalStateException("headless Mindustry failed to start", failure[0]);
            if(System.currentTimeMillis() > deadline) throw new IllegalStateException("headless Mindustry did not start in time");
            try{
                Thread.sleep(10L);
            }catch(InterruptedException e){
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted while starting headless Mindustry", e);
            }
        }
    }

    /** Replaces the world with an empty square of tiles and resets the rules to a plain survival game. */
    static void newWorld(int size){
        //Logic.reset() starts every map with fresh team data. The indexer creates each team's building
        //quadtree once, with the bounds of the world it first sees, and only clears it on later loads, so
        //team data carried over from a smaller test world would silently drop buildings outside it.
        state.teams = new Teams();
        Tiles tiles = world.resize(size, size);
        world.beginMapLoad();
        tiles.fill();
        world.endMapLoad();

        state.rules = new Rules();
        state.set(GameState.State.playing);
        //the probe converts per-frame figures into per-second ones, so a defined frame length is required
        Time.setDeltaProvider(() -> 1f);
        Time.update();
    }

    /** Advances the world by whole frames, updating what Logic.update does for transport: graphs, buildings, bullets. */
    static void run(int frames){
        for(int i = 0; i < frames; i++){
            Time.update();
            mindustry.gen.Groups.powerGraph.update();
            mindustry.gen.Groups.build.update();
            mindustry.gen.Groups.bullet.update();
        }
    }
}
