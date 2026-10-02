package factoryscope.probe;

import factoryscope.area.*;
import factoryscope.analysis.DiagnosticReason;
import factoryscope.power.*;
import arc.util.Time;
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
        smelter.shouldConsumePower = true;
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
        assertEquals(solar.power.graph.getPowerNeeded() / arc.util.Time.delta * 60f,
            grid.demandPerSecond, 0.001f,
            "a one-building selection still reports the whole engine graph's external consumer demand");
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
    void anEnemyPowerGraphNeverEntersTheVisibleReport(){
        Building friendly = place(Blocks.solarPanel, 5, 5, Team.sharded);
        Building enemy = place(Blocks.solarPanel, 35, 35, Team.crux);

        PowerGridReport report = MindustryPowerProbe.scan(List.of(friendly, enemy), Team.sharded);

        assertEquals(1, report.grids.size());
        PowerGridSnapshot grid = report.grids.get(0).snapshot;
        assertEquals(1, grid.selectedMemberCount);
        assertTrue(grid.members.stream().allMatch(member -> member.ref.teamId == Team.sharded.id));
        assertFalse(grid.members.stream().anyMatch(member -> member.ref.equals(AreaProbe.refOf(enemy))));
    }

    @Test
    void foreignMemberInsideAnEngineGraphWithholdsAggregatesAndDoesNotLeakItsIdentity(){
        state.rules.fog = true;
        Building friendly = place(Blocks.solarPanel, 5, 5, Team.sharded);
        Building foreign = place(Blocks.solarPanel, 35, 35, Team.crux);
        //Simulate an unsupported/stale engine graph containing a foreign member. This is deliberately
        //not normal Mindustry topology; it proves the adapter fails closed if a mod corrupts membership.
        friendly.power.graph.add(foreign);

        PowerGridSnapshot grid = MindustryPowerProbe.scan(friendly, Team.sharded).grids.get(0).snapshot;

        assertFalse(grid.visibilityComplete);
        assertFalse(grid.hasMetrics, "partial graph membership must not expose aggregate values");
        assertTrue(grid.members.stream().allMatch(member -> member.ref.teamId == Team.sharded.id));
        assertTrue(grid.producers.stream().allMatch(member -> member.ref.teamId == Team.sharded.id));
        assertFalse(grid.members.stream().anyMatch(member -> member.ref.equals(AreaProbe.refOf(foreign))));
        assertFalse(grid.producers.stream().anyMatch(member -> member.ref.equals(AreaProbe.refOf(foreign))));
        assertEquals(0f, grid.generationPerSecond);
        assertEquals(0f, grid.demandPerSecond);
    }

    @Test
    void perBuildingPowerSnapshotAlsoWithholdsMetricsForAnIncompleteEngineGraph(){
        state.rules.fog = true;
        Building friendlyConsumer = place(Blocks.siliconSmelter, 5, 5, Team.sharded);
        friendlyConsumer.shouldConsumePower = true;
        Building hiddenEnemyGenerator = place(Blocks.solarPanel, 35, 35, Team.crux);
        friendlyConsumer.power.graph.add(hiddenEnemyGenerator);

        var power = MindustryFactoryProbe.probe(friendlyConsumer).power;

        assertNotNull(power);
        assertFalse(power.hasGridMetrics,
            "single-building diagnostics must not bypass PowerScope's incomplete-graph visibility guard");
        assertEquals(0f, power.gridGenerationPerSecond);
        assertEquals(0f, power.gridDemandPerSecond);
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
        assertEquals(1, report.grids.get(0).snapshot.connections.size(),
            "reciprocal engine connections represent one physical electrical link");
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
    void aRefreshProbeObservesEnginePowerGraphsSplittingAfterANodeUnlink(){
        Building solar = place(Blocks.solarPanel, 5, 5);
        Building node = place(Blocks.powerNode, 11, 5);
        node.configureAny(solar.pos());
        assertSame(solar.power.graph, node.power.graph);

        node.configureAny(solar.pos());
        PowerGridReport after = MindustryPowerProbe.scan(List.of(solar, node), Team.sharded);

        assertNotSame(solar.power.graph, node.power.graph);
        assertEquals(2, after.grids.size());
        assertTrue(after.grids.stream().allMatch(grid -> grid.snapshot.selectedMemberCount == 1));
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
        assertEquals(PowerDiodeBatteryState.bothEndpointsHaveCapacity, link.batteryState,
            "both endpoint graphs have enabled battery capacity");
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
        assertEquals(PowerDiodeBatteryState.atLeastOneEndpointLacksCapacity,
            report.diodeLinks.get(0).batteryState);
    }

    @Test
    void diodeDirectionUsesTheEngineBackAndFrontForAllRotations(){
        int[][] directions = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};
        for(int rotation = 0; rotation < directions.length; rotation++){
            int centerX = 7 + rotation * 10, centerY = 12;
            int dx = directions[rotation][0], dy = directions[rotation][1];
            Building diode = place(Blocks.diode, centerX, centerY, Team.sharded, rotation);
            Building expectedFront = place(Blocks.battery, centerX + dx, centerY + dy);
            Building expectedBack = place(Blocks.battery, centerX - dx, centerY - dy);

            PowerGridReport report = MindustryPowerProbe.scan(List.of(diode), Team.sharded);

            assertEquals(2, report.grids.size(), "rotation " + rotation + " keeps endpoint graphs distinct");
            assertEquals(1, report.diodeLinks.size());
            PowerDiodeLink link = report.diodeLinks.get(0);
            assertTrue(report.grids.get(link.fromGrid).snapshot.members.stream()
                .anyMatch(member -> member.ref.equals(AreaProbe.refOf(expectedBack))),
                "rotation " + rotation + " sends from the diode back");
            assertTrue(report.grids.get(link.toGrid).snapshot.members.stream()
                .anyMatch(member -> member.ref.equals(AreaProbe.refOf(expectedFront))),
                "rotation " + rotation + " sends to the diode front");
        }
    }

    @Test
    void onlySelectedDiodesAreDiscoveredAndOutsideScopeIsExplicitlyLimited(){
        Building back = place(Blocks.battery, 7, 12);
        Building diode = place(Blocks.diode, 8, 12);
        Building front = place(Blocks.battery, 9, 12);

        PowerGridReport report = MindustryPowerProbe.scan(List.of(back), Team.sharded);

        assertEquals(1, report.grids.size());
        assertEquals(1, report.grids.get(0).snapshot.selectedMemberCount);
        assertTrue(report.diodeLinks.isEmpty(),
            "the adapter does not world-scan for a diode outside the selected buildings");
        assertTrue(diode.isValid() && front.isValid());
    }

    @Test
    void asymmetricDiodeBatteryCapacityIsNotReportedAsPresentOnBothSides(){
        Building backBattery = place(Blocks.battery, 7, 12);
        Building diode = place(Blocks.diode, 8, 12);
        Building frontSolar = place(Blocks.solarPanel, 9, 12);

        PowerDiodeLink link = MindustryPowerProbe.scan(List.of(diode), Team.sharded).diodeLinks.get(0);

        assertEquals(PowerDiodeBatteryState.atLeastOneEndpointLacksCapacity, link.batteryState,
            "one battery-bearing side does not establish battery capacity on both endpoints");
        assertNotNull(backBattery.power.graph);
        assertNotNull(frontSolar.power.graph);
    }

    @Test
    void diodeCapacityIsUnavailableWhenAnEndpointGridHasHiddenMembers(){
        state.rules.fog = true;
        Building backSolar = place(Blocks.solarPanel, 5, 12);
        Building diode = place(Blocks.diode, 6, 12);
        Building frontBattery = place(Blocks.battery, 7, 12);
        Building hiddenBattery = place(Blocks.battery, 30, 30, Team.crux);
        backSolar.power.graph.add(hiddenBattery);

        PowerGridReport report = MindustryPowerProbe.scan(List.of(diode), Team.sharded);

        assertEquals(2, report.grids.size());
        assertEquals(1, report.diodeLinks.size());
        assertTrue(report.grids.stream().anyMatch(grid -> !grid.snapshot.visibilityComplete));
        assertEquals(PowerDiodeBatteryState.unavailable, report.diodeLinks.get(0).batteryState,
            "partial graph visibility must not leak whether the hidden endpoint has battery capacity");
        assertNotNull(frontBattery);
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
    void moddedPowerConsumerIsDiscoveredAndMeasuredByPowerGraphRole(){
        Building consumer = place(ModdedBlocks.moddedPowerConsumer, 8, 8);
        consumer.shouldConsumePower = true;

        PowerGridSnapshot grid = MindustryPowerProbe.scan(consumer, Team.sharded).grids.get(0).snapshot;

        assertEquals(1, grid.consumers.size());
        assertTrue(grid.consumers.get(0).ref.equals(AreaProbe.refOf(consumer)));
        assertEquals(consumer.block.consPower.requestedPower(consumer) * consumer.timeScale() * 60f,
            grid.demandPerSecond, 0.001f);
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
        Building generator = place(ModdedBlocks.moddedGenerator, 8, 8);
        ((mindustry.world.blocks.power.PowerGenerator.GeneratorBuild)generator).productionEfficiency = 1f;
        Building battery = place(Blocks.battery, 9, 8);
        Building sink = place(Blocks.siliconSmelter, 10, 8);
        sink.shouldConsumePower = true;
        generator.applyBoost(1.75f, 60f);
        assertEquals(1.75f, generator.timeScale(), 0.001f, "the fixture must actually be overdriven");
        battery.power.status = 0.25f;
        float frame = arc.util.Time.delta;
        float expectedBalance = (generator.power.graph.getPowerProduced()
            - generator.power.graph.getPowerNeeded()) / frame * 60f;
        generator.power.graph.update();

        PowerGridSnapshot grid = MindustryPowerProbe.scan(List.of(generator, battery), Team.sharded)
            .grids.get(0).snapshot;

        assertEquals(generator.power.graph.getPowerProduced() / frame * 60f, grid.generationPerSecond, 0.001f);
        assertNotEquals(generator.getPowerProduction() * 60f, grid.generationPerSecond, 0.001f,
            "the engine graph rate must include the generator's overdrive exactly once");
        assertEquals(generator.power.graph.getPowerNeeded() / frame * 60f, grid.demandPerSecond, 0.001f);
        assertEquals(generator.power.graph.getBatteryStored(), grid.batteryStored, 0.001f);
        assertEquals(generator.power.graph.getTotalBatteryCapacity(), grid.batteryCapacity, 0.001f);
        assertEquals(expectedBalance, grid.balancePerSecond, 0.001f,
            "the first raw mean sample is (producer output - requested demand) / delta, then displayed per second");
        assertEquals(generator.power.graph.hasPowerBalanceSamples(), grid.balanceReliable);
        assertFalse(grid.balanceReliable, "one update is not the engine's mature 60-sample balance window");
        assertEquals(grid.generationPerSecond,
            grid.producers.stream().mapToDouble(member -> member.generationPerSecond).sum(), 0.001);
        assertEquals(grid.demandPerSecond,
            grid.consumers.stream().mapToDouble(member -> member.demandPerSecond).sum(), 0.001);
        assertEquals(grid.batteryStored,
            grid.batteries.stream().mapToDouble(member -> member.batteryStored).sum(), 0.001);
        assertEquals(grid.batteryCapacity,
            grid.batteries.stream().mapToDouble(member -> member.batteryCapacity).sum(), 0.001);
    }

    @Test
    void pausedPowerRatesApplyTimeScaleOnceForProducerAndCurrentConsumerRequest(){
        Building generator = place(ModdedBlocks.moddedGenerator, 8, 8);
        ((mindustry.world.blocks.power.PowerGenerator.GeneratorBuild)generator).productionEfficiency = 1f;
        Building sink = place(Blocks.siliconSmelter, 9, 8);
        sink.shouldConsumePower = true;
        generator.applyBoost(1.75f, 60f);
        sink.applyBoost(1.5f, 60f);
        float oldDelta = Time.delta;
        try{
            float expectedGeneration = generator.getPowerProduction() * generator.timeScale() * 60f;
            float requested = sink.block.consPower.requestedPower(sink);
            float expectedDemand = requested * sink.timeScale() * 60f;
            for(float delta : new float[]{0.25f, 1f, 2f, 0f}){
                Time.delta = delta;
                PowerGridSnapshot grid = MindustryPowerProbe.scan(List.of(generator, sink), Team.sharded)
                    .grids.get(0).snapshot;

                assertTrue(grid.hasMetrics, "valid simulation delta " + delta);
                assertEquals(expectedGeneration, grid.generationPerSecond, 0.001f,
                    "generation remains frame-rate independent and scales once at delta " + delta);
                assertEquals(expectedDemand, grid.demandPerSecond, 0.001f,
                    "current requested demand remains frame-rate independent at delta " + delta);
            }
        }finally{
            Time.delta = oldDelta;
        }
    }

    @Test
    void nonFiniteSimulationDeltaMakesAggregatePowerMetricsUnavailable(){
        Building generator = place(ModdedBlocks.moddedGenerator, 8, 8);
        Building sink = place(Blocks.siliconSmelter, 9, 8);
        sink.shouldConsumePower = true;
        float oldDelta = Time.delta;
        try{
            for(float invalidDelta : new float[]{Float.NaN, Float.POSITIVE_INFINITY, -1f}){
                Time.delta = invalidDelta;
                PowerGridSnapshot grid = MindustryPowerProbe.scan(List.of(generator, sink), Team.sharded)
                    .grids.get(0).snapshot;
                assertFalse(grid.hasMetrics, "invalid simulation delta " + invalidDelta + " is not a pause");
                assertEquals(PowerGridState.unavailable, PowerGridAnalyzer.analyze(grid).state);
                assertEquals(0f, grid.generationPerSecond, "unavailable metrics use a non-display placeholder");
            }
        }finally{
            Time.delta = oldDelta;
        }
    }

    private static Building place(Block block, int x, int y){
        return place(block, x, y, Team.sharded);
    }

    private static Building place(Block block, int x, int y, Team team){
        return place(block, x, y, team, 0);
    }

    private static Building place(Block block, int x, int y, Team team, int rotation){
        world.tile(x, y).setBlock(block, team, rotation);
        Building build = world.tile(x, y).build;
        assertNotNull(build, "failed to place " + block.name);
        if(build.block.update) build.updateConsumption();
        return build;
    }
}
