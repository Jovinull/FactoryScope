package factoryscope.probe;

import factoryscope.area.*;
import factoryscope.analysis.DiagnosticReason;
import factoryscope.power.*;
import mindustry.content.Blocks;
import mindustry.content.Items;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.world.Block;
import mindustry.world.blocks.power.BeamNode;
import mindustry.world.blocks.power.PowerDiode;
import org.junit.jupiter.api.*;

import java.util.*;

import static mindustry.Vars.*;
import static org.junit.jupiter.api.Assertions.*;

/** PowerScope adapter tests against Mindustry's real v160.5 PowerGraph implementation. */
class MindustryPowerProbeTest{
    @BeforeAll
    static void boot(){
        HeadlessGame.start();
    }

    @BeforeEach
    void freshWorld(){
        HeadlessGame.newWorld(48);
    }

    @Test
    void selectedBuildingsUseTheirWholeEngineMaintainedGrid(){
        Building solar = place(Blocks.solarPanel, 8, 8);
        Building battery = place(Blocks.battery, 9, 8);
        Building smelter = place(Blocks.siliconSmelter, 10, 8);
        battery.power.status = 0.5f;

        PowerGridReport report = MindustryPowerProbe.scan(List.of(solar), Team.sharded);

        assertEquals(1, report.grids.size());
        PowerGridSnapshot grid = report.grids.get(0).snapshot;
        assertEquals(1, grid.selectedMemberCount);
        assertTrue(grid.extendsOutsideSelection);
        assertTrue(grid.members.stream().anyMatch(member -> member.ref.equals(AreaProbe.refOf(smelter))));
        assertEquals(1, grid.producers.size(), "generator membership comes from PowerGraph.producers");
        assertTrue(grid.batteries.stream().anyMatch(member -> member.ref.equals(AreaProbe.refOf(battery))));
        assertTrue(grid.connections.stream().anyMatch(edge -> edge.first.equals(AreaProbe.refOf(solar))
            && edge.second.equals(AreaProbe.refOf(battery))));
    }

    @Test
    void probingDoesNotMutateGraphMembershipBatteryOrConsumerStatus(){
        Building solar = place(Blocks.solarPanel, 8, 8);
        Building battery = place(Blocks.battery, 9, 8);
        Building smelter = place(Blocks.siliconSmelter, 10, 8);
        battery.power.status = 0.37f;
        float status = smelter.power.status;
        int members = solar.power.graph.all.size;
        int producers = solar.power.graph.producers.size;
        int consumers = solar.power.graph.consumers.size;
        int batteries = solar.power.graph.batteries.size;

        MindustryPowerProbe.scan(List.of(solar, battery, smelter), Team.sharded);

        assertEquals(members, solar.power.graph.all.size);
        assertEquals(producers, solar.power.graph.producers.size);
        assertEquals(consumers, solar.power.graph.consumers.size);
        assertEquals(batteries, solar.power.graph.batteries.size);
        assertEquals(0.37f, battery.power.status, 0.0001f);
        assertEquals(status, smelter.power.status, 0.0001f);
    }

    @Test
    void distinctEngineGraphsStayDistinctInOneAreaReport(){
        Building first = place(Blocks.solarPanel, 5, 5);
        Building second = place(Blocks.solarPanel, 35, 35);

        PowerGridReport report = MindustryPowerProbe.scan(List.of(first, second), Team.sharded, Map.of());

        assertNotSame(first.power.graph, second.power.graph);
        assertEquals(2, report.grids.size());
        assertNotEquals(report.grids.get(0).snapshot.anchor, report.grids.get(1).snapshot.anchor);
    }

    @Test
    void areaScanIncludesOnePowerSnapshotForTheIntersectedGrid(){
        Building solar = place(Blocks.solarPanel, 8, 8);
        place(Blocks.battery, 9, 8);
        place(Blocks.siliconSmelter, 10, 8);

        AreaDiagnosticResult result = AreaProbe.scan(AreaSelection.of(8, 8, 8, 8), Team.sharded);

        assertEquals(1, result.power.grids.size());
        assertEquals(1, result.power.grids.get(0).snapshot.selectedMemberCount);
        assertTrue(result.power.grids.get(0).snapshot.extendsOutsideSelection);
        assertEquals(1, solar.power.graph.producers.size);
    }

    @Test
    void establishedPowerNodeLinksComeFromTheEngineConnections(){
        Building solar = place(Blocks.solarPanel, 5, 5);
        Building node = place(Blocks.powerNode, 11, 5);
        node.configureAny(solar.pos());

        PowerGridReport report = MindustryPowerProbe.scan(List.of(solar, node), Team.sharded);

        assertSame(solar.power.graph, node.power.graph);
        assertEquals(1, report.grids.size());
        assertTrue(report.grids.get(0).snapshot.connections.contains(
            new PowerConnection(AreaProbe.refOf(solar), AreaProbe.refOf(node))));
    }

    @Test
    void aRefreshProbeObservesEnginePowerGraphsMergingAfterANodeLink(){
        Building solar = place(Blocks.solarPanel, 5, 5);
        Building node = place(Blocks.powerNode, 11, 5);

        PowerGridReport before = MindustryPowerProbe.scan(List.of(solar, node), Team.sharded);
        assertEquals(2, before.grids.size());

        node.configureAny(solar.pos());

        PowerGridReport after = MindustryPowerProbe.scan(List.of(solar, node), Team.sharded);
        assertSame(solar.power.graph, node.power.graph);
        assertEquals(1, after.grids.size());
        assertEquals(2, after.grids.get(0).snapshot.selectedMemberCount);
    }

    @Test
    void beamNodeUsesItsEstablishedEngineGraphLinkRatherThanACopiedPowerNodeRule(){
        Building solar = place(Blocks.solarPanel, 5, 5);
        Building beam = place(Blocks.beamNode, 6, 5);
        assertInstanceOf(BeamNode.BeamNodeBuild.class, beam);
        ((BeamNode.BeamNodeBuild)beam).updateDirections(); //the engine does this when tileChanges advances

        PowerGridReport report = MindustryPowerProbe.scan(List.of(solar, beam), Team.sharded);

        assertSame(solar.power.graph, beam.power.graph);
        assertEquals(1, report.grids.size());
        assertTrue(report.grids.get(0).snapshot.connections.contains(
            new PowerConnection(AreaProbe.refOf(solar), AreaProbe.refOf(beam))));
    }

    @Test
    void powerDiodeKeepsTwoGraphsSeparateAndCapturesItsConditionalDirection(){
        Building leftSolar = place(Blocks.solarPanel, 5, 10);
        Building leftBattery = place(Blocks.battery, 6, 10);
        Building diode = place(Blocks.diode, 7, 10);
        Building rightBattery = place(Blocks.battery, 8, 10);
        Building rightSolar = place(Blocks.solarPanel, 9, 10);
        leftBattery.power.status = 0.8f;
        rightBattery.power.status = 0.2f;

        PowerGridReport report = MindustryPowerProbe.scan(List.of(diode), Team.sharded);

        assertInstanceOf(PowerDiode.PowerDiodeBuild.class, diode);
        assertNotSame(leftSolar.power.graph, rightSolar.power.graph);
        assertEquals(2, report.grids.size(), "a diode is a cross-grid relation, not a graph merge");
        assertEquals(1, report.diodeLinks.size());
        PowerDiodeLink link = report.diodeLinks.get(0);
        assertEquals(AreaProbe.refOf(diode), link.diode);
        assertTrue(link.transferPossible, "both endpoint graphs have enabled battery capacity");
        assertEquals(report.grids.get(0).snapshot.id, link.fromGrid);
        assertEquals(report.grids.get(1).snapshot.id, link.toGrid);
        assertTrue(report.grids.stream().allMatch(grid -> grid.snapshot.selectedMemberCount == 0));
        assertTrue(report.grids.stream().allMatch(grid -> grid.snapshot.extendsOutsideSelection));
    }

    @Test
    void diodeWithoutBatteryCapacityIsNotDescribedAsTransferCapable(){
        Building left = place(Blocks.solarPanel, 6, 10);
        Building diode = place(Blocks.diode, 7, 10);
        Building right = place(Blocks.solarPanel, 8, 10);

        PowerGridReport report = MindustryPowerProbe.scan(List.of(diode), Team.sharded);

        assertNotSame(left.power.graph, right.power.graph);
        assertEquals(1, report.diodeLinks.size());
        assertFalse(report.diodeLinks.get(0).transferPossible);
    }

    @Test
    void moddedPowerGeneratorIsDiscoveredByPowerGraphRole(){
        Building generator = place(ModdedBlocks.moddedGenerator, 8, 8);

        PowerGridSnapshot grid = MindustryPowerProbe.scan(generator, Team.sharded).grids.get(0).snapshot;

        assertEquals(1, grid.producers.size());
        assertTrue(grid.producers.get(0).ref.equals(AreaProbe.refOf(generator)));
        assertEquals(generator.getPowerProduction() * generator.timeScale() * 60f,
            grid.generationPerSecond, 0.001f);
    }

    @Test
    void nonFiniteSandboxDemandIsUnavailableRatherThanMisreportedAsNoDemand(){
        Building source = place(Blocks.solarPanel, 8, 8);
        Building sink = place(Blocks.powerVoid, 9, 8);
        assertTrue(source.power.graph.consumers.contains(sink));

        PowerGridSnapshot grid = MindustryPowerProbe.scan(source, Team.sharded).grids.get(0).snapshot;

        assertFalse(grid.hasMetrics);
        assertEquals(PowerGridState.unavailable, PowerGridAnalyzer.analyze(grid).state);
        assertFalse(PowerGridAnalyzer.analyze(grid).has(PowerFinding.NO_CURRENT_DEMAND));
        var single = MindustryFactoryProbe.probe(sink).power;
        assertFalse(single.hasGridMetrics);
        assertEquals(0f, single.gridDemandPerSecond);
    }

    @Test
    void generatorAnnotationsReuseFactoryAnalyzerForDisabledAndFuelStarvedStates(){
        Building disabledSolar = place(Blocks.solarPanel, 8, 8);
        disabledSolar.enabled = false;
        Building fuelStarved = place(Blocks.combustionGenerator, 24, 24);

        PowerGridReport report = MindustryPowerProbe.scan(List.of(disabledSolar, fuelStarved), Team.sharded);

        PowerMemberSnapshot disabled = report.grids.stream().flatMap(grid -> grid.snapshot.producers.stream())
            .filter(member -> member.ref.equals(AreaProbe.refOf(disabledSolar))).findFirst().orElseThrow();
        PowerMemberSnapshot starved = report.grids.stream().flatMap(grid -> grid.snapshot.producers.stream())
            .filter(member -> member.ref.equals(AreaProbe.refOf(fuelStarved))).findFirst().orElseThrow();
        assertEquals(DiagnosticReason.disabled, disabled.diagnostic.reason());
        assertEquals(DiagnosticReason.missingItemInput, starved.diagnostic.reason());
    }

    @Test
    void graphSnapshotUsesEngineRateAndBatteryValuesAndAccountsForOverdriveOnce(){
        Building solar = place(Blocks.solarPanel, 8, 8);
        Building battery = place(Blocks.battery, 9, 8);
        solar.applyBoost(1.75f, 60f);
        battery.power.status = 0.25f;
        float frame = arc.util.Time.delta;

        PowerGridSnapshot grid = MindustryPowerProbe.scan(List.of(solar, battery), Team.sharded)
            .grids.get(0).snapshot;

        assertEquals(solar.power.graph.getPowerProduced() / frame * 60f, grid.generationPerSecond, 0.001f);
        assertEquals(solar.power.graph.getPowerNeeded() / frame * 60f, grid.demandPerSecond, 0.001f);
        assertEquals(solar.power.graph.getBatteryStored(), grid.batteryStored, 0.001f);
        assertEquals(solar.power.graph.getTotalBatteryCapacity(), grid.batteryCapacity, 0.001f);
    }

    private static Building place(Block block, int x, int y){
        world.tile(x, y).setBlock(block, Team.sharded, 0);
        Building build = world.tile(x, y).build;
        assertNotNull(build, "failed to place " + block.name);
        if(build.block.update) build.updateConsumption();
        return build;
    }
}
