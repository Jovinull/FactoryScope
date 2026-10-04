package factoryscope.ui;

import arc.math.geom.Vec2;
import factoryscope.area.BuildingRef;
import factoryscope.probe.AreaProbe;
import factoryscope.probe.MindustryPowerProbe;
import factoryscope.power.PowerGridReport;
import mindustry.content.Blocks;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.Player;
import mindustry.world.Block;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static mindustry.Vars.*;
import static org.junit.jupiter.api.Assertions.*;

class PowerOverlayFogTest{
    @BeforeAll
    static void boot() throws Exception{
        invokeHeadless("start");
    }

    @BeforeEach
    void freshWorld() throws Exception{
        invokeHeadless("newWorld", new Class<?>[]{int.class}, new Object[]{48});
    }

    @Test
    void heldPowerOverlayUsesCapturedPositionsAfterAMemberIsDestroyedUnderFog() throws Exception{
        state.rules.fog = true;
        state.rules.pvp = true;
        Building solar = place(Blocks.solarPanel, 8, 8, Team.sharded);
        Building battery = place(Blocks.battery, 9, 8, Team.sharded);
        PowerGridReport report = MindustryPowerProbe.scan(List.of(solar, battery), Team.sharded);
        BuildingRef batteryRef = AreaProbe.refOf(battery);

        assertTrue(report.grids.stream().flatMap(grid -> grid.snapshot.connections.stream())
            .anyMatch(edge -> edge.first.equals(batteryRef) || edge.second.equals(batteryRef)),
            "the snapshot contains the battery connection before visibility is lost");

        fogControl.resetFog();
        assertTrue(battery.inFogTo(Team.crux), "Mindustry must independently report the member hidden");
        battery.tile.remove();
        assertNull(AreaProbe.resolve(batteryRef), "the current world no longer resolves the hidden member");

        Map<?, ?> positions = capturedPositions(new PowerOverlay(report));
        assertTrue(positions.containsKey(batteryRef),
            "the PowerScope world overlay must use the held snapshot, not re-read hidden current existence");
        Vec2 point = (Vec2)positions.get(batteryRef);
        assertNotNull(point);
        assertEquals(batteryRef.tileX * tilesize + ((batteryRef.size + 1) % 2) * tilesize / 2f,
            point.x, 0.001f, "snapshot position follows Mindustry's Building.init/Tile.drawx rule");
        assertEquals(batteryRef.tileY * tilesize + ((batteryRef.size + 1) % 2) * tilesize / 2f,
            point.y, 0.001f, "snapshot position follows Mindustry's Building.init/Tile.drawy rule");
    }

    @Test
    void reportNavigationResolverRejectsBuildingsMindustryCurrentlyHides(){
        state.rules.fog = true;
        state.rules.pvp = true;
        Building target = place(Blocks.solarPanel, 12, 12, Team.sharded);
        Player previousPlayer = player;
        try{
            player = Player.create();
            player.team(Team.crux);
            fogControl.resetFog();

            assertTrue(target.inFogTo(player.team()), "Mindustry must independently report this target hidden");
            assertNull(FactoryScopeUI.visibleTarget(AreaProbe.refOf(target)),
                "a navigation action must not resolve a current Building from a stale reference through fog");
        }finally{
            player = previousPlayer;
        }
    }

    private static Map<?, ?> capturedPositions(PowerOverlay overlay) throws Exception{
        Field field = PowerOverlay.class.getDeclaredField("positions");
        field.setAccessible(true);
        return (Map<?, ?>)field.get(overlay);
    }

    private static void invokeHeadless(String name) throws Exception{
        invokeHeadless(name, new Class<?>[0], new Object[0]);
    }

    private static void invokeHeadless(String name, Class<?>[] parameterTypes, Object[] args) throws Exception{
        Class<?> type = Class.forName("factoryscope.probe.HeadlessGame");
        Method method = type.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        method.invoke(null, args);
    }

    private static Building place(Block block, int x, int y, Team team){
        world.tile(x, y).setBlock(block, team, 0);
        Building build = world.tile(x, y).build;
        assertNotNull(build, "failed to place " + block.name);
        if(build.block.update) build.updateConsumption();
        return build;
    }
}
