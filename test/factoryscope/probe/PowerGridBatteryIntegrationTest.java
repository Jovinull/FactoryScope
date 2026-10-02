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

    @Test
    void engineBalanceIsRecentRatherThanAnInstantaneousGenerationMinusDemandValue(){
        Building generator = place(ModdedBlocks.moddedGenerator, 8, 8);
        var generatorBuild = (PowerGenerator.GeneratorBuild)generator;
        generatorBuild.productionEfficiency = 1f;
        Building smelter = place(Blocks.siliconSmelter, 9, 8);
        smelter.shouldConsumePower = false;
        var graph = generator.power.graph;

        for(int i = 0; i < 60; i++) graph.update();
        assertTrue(graph.hasPowerBalanceSamples(), "the engine's 60 samples make the mean mature");
        assertTrue(graph.getPowerBalance() > 0f);

        generatorBuild.productionEfficiency = 0f;
        smelter.shouldConsumePower = true;
        graph.update();
        PowerGridSnapshot snapshot = MindustryPowerProbe.scan(generator, Team.sharded).grids.get(0).snapshot;

        assertEquals(0f, snapshot.generationPerSecond, 0.001f);
        assertTrue(snapshot.demandPerSecond > 0f);
        assertTrue(snapshot.balanceReliable);
        assertTrue(snapshot.balancePerSecond > 0f,
            "the displayed window retains most previous surplus after a single-update transition to deficit");
        assertEquals(graph.getPowerBalance() * 60f, snapshot.balancePerSecond, 0.001f);
    }

    @Test
    void consumerOnlyGraphUsesEngineSatisfactionAndReportsNoGeneration(){
        Building consumer = place(ModdedBlocks.moddedPowerConsumer, 8, 8);
        consumer.shouldConsumePower = true;
        consumer.power.graph.update();

        PowerGridResult result = PowerGridAnalyzer.analyze(
            MindustryPowerProbe.scan(consumer, Team.sharded).grids.get(0).snapshot);

        assertEquals(PowerGridState.noGeneration, result.state);
        assertTrue(result.has(PowerFinding.NO_GENERATION));
        assertTrue(result.has(PowerFinding.GRID_UNDERPOWERED));
        assertEquals(0f, result.snapshot.generationPerSecond);
        assertTrue(result.snapshot.demandPerSecond > 0f);
        assertEquals(0f, result.snapshot.satisfaction);
    }

    @Test
    void generatorOnlyGraphIsNoDemandRatherThanInfiniteSurplus(){
        Building generator = place(ModdedBlocks.moddedGenerator, 8, 8);
        ((PowerGenerator.GeneratorBuild)generator).productionEfficiency = 1f;
        generator.power.graph.update();

        PowerGridResult result = PowerGridAnalyzer.analyze(
            MindustryPowerProbe.scan(generator, Team.sharded).grids.get(0).snapshot);

        assertEquals(PowerGridState.noCurrentDemand, result.state);
        assertTrue(result.has(PowerFinding.NO_CURRENT_DEMAND));
        assertFalse(result.has(PowerFinding.GENERATION_SURPLUS));
        assertTrue(result.snapshot.generationPerSecond > 0f);
        assertEquals(0f, result.snapshot.demandPerSecond);
    }

    @Test
    void batteryOnlyGraphIsNotClassifiedAsAProducerOrSurplus(){
        Building battery = place(Blocks.battery, 8, 8);
        battery.power.status = 0.4f;
        battery.power.graph.update();

        PowerGridResult result = PowerGridAnalyzer.analyze(
            MindustryPowerProbe.scan(battery, Team.sharded).grids.get(0).snapshot);

        assertEquals(PowerGridState.noCurrentDemand, result.state);
        assertTrue(result.snapshot.producers.isEmpty());
        assertTrue(result.snapshot.consumers.isEmpty());
        assertEquals(1, result.snapshot.batteries.size());
        assertEquals(0f, result.snapshot.generationPerSecond);
        assertEquals(0f, result.snapshot.demandPerSecond);
        assertTrue(result.snapshot.batteryStored > 0f);
        assertFalse(result.has(PowerFinding.GENERATION_SURPLUS));
    }

    @Test
    void disabledBatteryDoesNotContributeStoredEnergyOrCapacity(){
        Building solar = place(Blocks.solarPanel, 8, 8);
        Building battery = place(Blocks.battery, 9, 8);
        battery.power.status = 0.75f;
        battery.enabled = false;

        PowerGridSnapshot snapshot = MindustryPowerProbe.scan(solar, Team.sharded).grids.get(0).snapshot;

        assertTrue(snapshot.hasMetrics);
        assertEquals(1, snapshot.batteries.size(), "the disabled battery remains an engine grid member");
        assertEquals(0f, snapshot.batteryStored);
        assertEquals(0f, snapshot.batteryCapacity);
        assertEquals(0f, snapshot.batteries.get(0).batteryStored);
        assertEquals(0f, snapshot.batteries.get(0).batteryCapacity);
    }

    @Test
    void teamCheatPowerRuleHidesSyntheticEngineMetrics(){
        state.rules.teams.get(Team.sharded).cheat = true;
        Building solar = place(Blocks.solarPanel, 8, 8);
        Building smelter = place(Blocks.siliconSmelter, 9, 8);
        smelter.updateConsumption();
        solar.power.graph.update();

        PowerGridSnapshot snapshot = MindustryPowerProbe.scan(solar, Team.sharded).grids.get(0).snapshot;
        PowerGridResult result = PowerGridAnalyzer.analyze(snapshot);

        assertTrue(snapshot.cheatPowered);
        assertFalse(snapshot.hasMetrics);
        assertEquals(PowerGridState.cheatPowered, result.state);
        assertTrue(result.has(PowerFinding.CHEAT_POWER_RULE));
        assertEquals(0f, snapshot.generationPerSecond);
        assertEquals(0f, snapshot.demandPerSecond);
    }

    @Test
    void inactiveConsumerDoesNotContributeCurrentRequestedDemand(){
        Building consumer = place(ModdedBlocks.moddedPowerConsumer, 8, 8);
        consumer.shouldConsumePower = true;
        PowerGridSnapshot active = MindustryPowerProbe.scan(consumer, Team.sharded).grids.get(0).snapshot;
        assertTrue(active.demandPerSecond > 0f);

        consumer.shouldConsumePower = false;
        PowerGridSnapshot inactive = MindustryPowerProbe.scan(consumer, Team.sharded).grids.get(0).snapshot;

        assertEquals(0f, inactive.demandPerSecond,
            "grid demand is current shouldConsumePower request, not nominal block usage");
        assertEquals(0f, inactive.consumers.get(0).demandPerSecond);
    }

    private static Building place(mindustry.world.Block block, int x, int y){
        world.tile(x, y).setBlock(block, Team.sharded, 0);
        Building build = world.tile(x, y).build;
        assertNotNull(build);
        if(build.block.update) build.updateConsumption();
        return build;
    }
}
