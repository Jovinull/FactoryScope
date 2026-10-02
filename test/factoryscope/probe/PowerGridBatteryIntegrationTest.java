package factoryscope.probe;

import factoryscope.power.*;
import arc.util.Time;
import mindustry.content.Blocks;
import mindustry.world.blocks.power.PowerGenerator;
import mindustry.game.Team;
import mindustry.gen.Building;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static mindustry.Vars.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real-engine checks that battery support and generation remain separate PowerScope facts. */
class PowerGridBatteryIntegrationTest{
    private float oldDelta;
    @BeforeAll static void boot(){ HeadlessGame.start(); }
    @BeforeEach void freshWorld(){
        oldDelta = Time.delta;
        Time.delta = 1f;
        HeadlessGame.newWorld(48);
    }
    @org.junit.jupiter.api.AfterEach void restoreDelta(){ Time.delta = oldDelta; }

    @Test
    void aChargedBatteryCanKeepTheGridSatisfiedWhileGenerationRemainsBelowDemand(){
        Building solar = place(ModdedBlocks.moddedGenerator, 8, 8);
        ((PowerGenerator.GeneratorBuild)solar).productionEfficiency = 1f;
        Building battery = place(Blocks.battery, 9, 8);
        Building smelter = place(Blocks.siliconSmelter, 10, 8);
        smelter.shouldConsumePower = true;
        var graph = solar.power.graph;
        assertSame(graph, battery.power.graph);
        assertSame(graph, smelter.power.graph);
        assertTrue(graph.getPowerProduced() < graph.getPowerNeeded());
        battery.power.status = 1f;

        graph.update();

        PowerGridSnapshot snapshot = MindustryPowerProbe.scan(List.of(solar), Team.sharded)
            .grids.get(0).snapshot;
        PowerGridResult result = PowerGridAnalyzer.analyze(snapshot);
        assertEquals(PowerGridState.generationBelowDemand, result.state);
        assertTrue(result.has(PowerFinding.GENERATION_BELOW_DEMAND));
        assertTrue(result.has(PowerFinding.BATTERY_RESERVES_PRESENT));
        assertFalse(result.has(PowerFinding.GRID_UNDERPOWERED));
        assertTrue(snapshot.generationPerSecond < snapshot.demandPerSecond);
        assertTrue(snapshot.satisfaction >= 0.999f,
            "PowerGraph satisfaction includes this update's battery discharge");
        assertEquals(snapshot.satisfaction, MindustryFactoryProbe.probe(smelter).power.satisfaction, 0.0001f,
            "the existing per-building diagnostic and grid view read the same unbuffered satisfaction");
        assertTrue(snapshot.batteryStored < snapshot.batteryCapacity,
            "the engine's battery use changed stored energy, which the probe reads without changing it");
    }

    @Test
    void anEmptyBatteryDoesNotTurnInsufficientGenerationIntoAHealthyGrid(){
        Building solar = place(ModdedBlocks.moddedGenerator, 8, 8);
        ((PowerGenerator.GeneratorBuild)solar).productionEfficiency = 1f;
        Building battery = place(Blocks.battery, 9, 8);
        Building smelter = place(Blocks.siliconSmelter, 10, 8);
        smelter.shouldConsumePower = true;
        battery.power.status = 0f;
        solar.power.graph.update();

        PowerGridSnapshot snapshot = MindustryPowerProbe.scan(List.of(solar), Team.sharded)
            .grids.get(0).snapshot;
        PowerGridResult result = PowerGridAnalyzer.analyze(snapshot);
        assertTrue(snapshot.generationPerSecond < snapshot.demandPerSecond);
        assertTrue(snapshot.satisfaction < 0.999f);
        assertEquals(snapshot.satisfaction, MindustryFactoryProbe.probe(smelter).power.satisfaction, 0.0001f);
        assertEquals(PowerGridState.underpowered, result.state);
        assertTrue(result.has(PowerFinding.GRID_UNDERPOWERED));
        assertFalse(result.has(PowerFinding.BATTERY_RESERVES_PRESENT));
    }

    private static Building place(mindustry.world.Block block, int x, int y){
        world.tile(x, y).setBlock(block, Team.sharded, 0);
        Building build = world.tile(x, y).build;
        assertNotNull(build);
        if(build.block.update) build.updateConsumption();
        return build;
    }
}
