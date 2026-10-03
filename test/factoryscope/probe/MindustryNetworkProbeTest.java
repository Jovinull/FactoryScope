package factoryscope.probe;

import factoryscope.area.*;
import factoryscope.analysis.DiagnosticReason;
import factoryscope.model.*;
import factoryscope.network.*;
import factoryscope.trace.*;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.world.*;
import mindustry.world.blocks.environment.Floor;
import mindustry.world.blocks.distribution.ItemBridge;
import org.junit.jupiter.api.*;

import java.util.*;

import static mindustry.Vars.*;
import static org.junit.jupiter.api.Assertions.*;

/** Transport semantics checked against real v160.5 block instances. */
class MindustryNetworkProbeTest{
    private static final ResourceRef copper = new ResourceRef(ResourceKind.item, "copper", "Copper");
    private static final ResourceRef lead = new ResourceRef(ResourceKind.item, "lead", "Lead");
    private static final ResourceRef sand = new ResourceRef(ResourceKind.item, "sand", "Sand");

    @BeforeAll
    static void boot(){ HeadlessGame.start(); }

    @BeforeEach
    void freshWorld(){ HeadlessGame.newWorld(32); }

    @Test
    void conveyorsHaveOnlyTheirForwardRouteInEveryRotation(){
        for(int rotation = 0; rotation < 4; rotation++){
            Building conveyor = place(Blocks.conveyor, 10, 10, rotation);
            ItemNetwork network = MindustryNetworkProbe.scan(AreaSelection.of(8, 8, 12, 12), Team.sharded);
            BuildingRef ref = AreaProbe.refOf(conveyor);
            NetworkSide forward = NetworkSide.rotation(rotation);

            assertTrue(network.graph.isReachable(input(ref, forward.opposite()), output(ref, forward), copper));
            assertFalse(network.graph.isReachable(input(ref, forward), output(ref, forward.opposite()), copper));
            assertFalse(network.graph.isReachable(input(ref, forward), output(ref, forward), copper),
                "the engine rejects structural input from the conveyor's output side");
            conveyor.tile.remove();
        }
    }

    @Test
    void armoredConveyorsAndDuctsArePartialUntilTheirInputRulesAreModeled(){
        Building conveyor = place(Blocks.armoredConveyor, 10, 10, 0);
        Building duct = place(Blocks.armoredDuct, 14, 10, 0);

        ItemNetwork network = MindustryNetworkProbe.scan(AreaSelection.of(8, 8, 16, 12), Team.sharded);

        assertEquals(NetworkCompleteness.partialUnsupportedTransport, network.completeness);
        assertEquals(Set.of(AreaProbe.refOf(conveyor), AreaProbe.refOf(duct)), new HashSet<>(network.unsupportedTransport));
        assertTrue(network.graph.edges.isEmpty());
    }

    @Test
    void distributorUsesItsWholeFootprintForExternalRoutes(){
        Building source = place(Blocks.conveyor, 9, 10, 0);
        Building distributor = place(Blocks.distributor, 10, 10, 0);
        Building target = place(Blocks.conveyor, 12, 10, 0);

        ItemNetwork network = MindustryNetworkProbe.scan(AreaSelection.of(8, 8, 14, 13), Team.sharded);

        assertTrue(network.graph.isReachable(output(AreaProbe.refOf(source), NetworkSide.east),
            input(AreaProbe.refOf(target), NetworkSide.west), copper));
        assertFalse(network.graph.edges.stream().anyMatch(edge -> edge.from.building.equals(AreaProbe.refOf(distributor))
            && edge.to.building.equals(AreaProbe.refOf(distributor)) && edge.from.channel.equals("out")
            && edge.to.channel.equals("in")));
    }

    @Test
    void junctionChannelsCrossWithoutConnectingToEachOther(){
        Building junction = place(Blocks.junction, 10, 10, 0);
        ItemNetwork network = MindustryNetworkProbe.scan(AreaSelection.of(8, 8, 12, 12), Team.sharded);
        BuildingRef ref = AreaProbe.refOf(junction);

        assertTrue(network.graph.isReachable(input(ref, NetworkSide.west), output(ref, NetworkSide.east), copper));
        assertTrue(network.graph.isReachable(input(ref, NetworkSide.north), output(ref, NetworkSide.south), copper));
        assertFalse(network.graph.isReachable(input(ref, NetworkSide.west), output(ref, NetworkSide.north), copper));
        assertFalse(network.graph.isReachable(input(ref, NetworkSide.south), output(ref, NetworkSide.east), copper));
    }

    @Test
    void sorterRoutesConfiguredAndOtherItemsToDifferentPorts(){
        Building sorter = place(Blocks.sorter, 10, 10, 0);
        sorter.configure(Items.copper);
        ItemNetwork network = MindustryNetworkProbe.scan(AreaSelection.of(8, 8, 12, 12), Team.sharded);
        BuildingRef ref = AreaProbe.refOf(sorter);
        NetworkPort west = input(ref, NetworkSide.west);

        assertTrue(network.graph.isReachable(west, output(ref, NetworkSide.east), copper));
        assertFalse(network.graph.isReachable(west, output(ref, NetworkSide.north), copper));
        assertFalse(network.graph.isReachable(west, output(ref, NetworkSide.east), lead));
        assertTrue(network.graph.isReachable(west, output(ref, NetworkSide.north), lead));
    }

    @Test
    void invertedSorterReversesTheConfiguredItemRoute(){
        Building sorter = place(Blocks.invertedSorter, 10, 10, 0);
        sorter.configure(Items.copper);
        ItemNetwork network = MindustryNetworkProbe.scan(AreaSelection.of(8, 8, 12, 12), Team.sharded);
        BuildingRef ref = AreaProbe.refOf(sorter);
        NetworkPort west = input(ref, NetworkSide.west);

        assertFalse(network.graph.isReachable(west, output(ref, NetworkSide.east), copper));
        assertTrue(network.graph.isReachable(west, output(ref, NetworkSide.north), copper));
        assertTrue(network.graph.isReachable(west, output(ref, NetworkSide.east), lead));
        assertFalse(network.graph.isReachable(west, output(ref, NetworkSide.north), lead));
    }

    @Test
    void unconfiguredSortersKeepTheirEngineDefaultRoutes(){
        Building normal = place(Blocks.sorter, 10, 10, 0);
        Building inverted = place(Blocks.invertedSorter, 14, 10, 0);
        ItemNetwork network = MindustryNetworkProbe.scan(AreaSelection.of(8, 8, 16, 12), Team.sharded);

        BuildingRef normalRef = AreaProbe.refOf(normal);
        BuildingRef invertedRef = AreaProbe.refOf(inverted);
        NetworkPort normalWest = input(normalRef, NetworkSide.west);
        NetworkPort invertedWest = input(invertedRef, NetworkSide.west);

        assertFalse(network.graph.isReachable(normalWest, output(normalRef, NetworkSide.east), copper));
        assertTrue(network.graph.isReachable(normalWest, output(normalRef, NetworkSide.north), copper));
        assertTrue(network.graph.isReachable(invertedWest, output(invertedRef, NetworkSide.east), copper));
        assertFalse(network.graph.isReachable(invertedWest, output(invertedRef, NetworkSide.north), copper));
    }

    @Test
    void ductsHaveOnlyTheirForwardRoute(){
        Building duct = place(Blocks.duct, 10, 10, 0);
        ItemNetwork network = MindustryNetworkProbe.scan(AreaSelection.of(8, 8, 12, 12), Team.sharded);
        BuildingRef ref = AreaProbe.refOf(duct);

        assertTrue(network.graph.isReachable(input(ref, NetworkSide.west), output(ref, NetworkSide.east), copper));
        assertFalse(network.graph.isReachable(input(ref, NetworkSide.east), output(ref, NetworkSide.west), copper));
    }

    @Test
    void overflowGateKeepsForwardAndSideRoutesAsConditionalPossibilities(){
        Building gate = place(Blocks.overflowGate, 10, 10, 0);
        ItemNetwork network = MindustryNetworkProbe.scan(AreaSelection.of(8, 8, 12, 12), Team.sharded);
        BuildingRef ref = AreaProbe.refOf(gate);
        NetworkPort west = input(ref, NetworkSide.west);

        assertTrue(network.graph.isReachable(west, output(ref, NetworkSide.east), copper));
        assertTrue(network.graph.isReachable(west, output(ref, NetworkSide.north), copper));
        assertTrue(network.graph.isReachable(west, output(ref, NetworkSide.south), copper));
    }

    @Test
    void underflowGateKeepsItsAlternativeRoutesAsConditionalPossibilities(){
        Building gate = place(Blocks.underflowGate, 10, 10, 0);
        ItemNetwork network = MindustryNetworkProbe.scan(AreaSelection.of(8, 8, 12, 12), Team.sharded);
        BuildingRef ref = AreaProbe.refOf(gate);
        NetworkPort west = input(ref, NetworkSide.west);

        assertTrue(network.graph.isReachable(west, output(ref, NetworkSide.east), copper));
        assertTrue(network.graph.isReachable(west, output(ref, NetworkSide.north), copper));
        assertTrue(network.graph.isReachable(west, output(ref, NetworkSide.south), copper));
    }

    @Test
    void gatesKeepPreferredAndFallbackRoutesDistinct(){
        Building overflow = place(Blocks.overflowGate, 10, 10, 0);
        Building underflow = place(Blocks.underflowGate, 14, 10, 0);
        ItemNetwork network = MindustryNetworkProbe.scan(AreaSelection.of(8, 8, 16, 12), Team.sharded);

        BuildingRef overflowRef = AreaProbe.refOf(overflow);
        BuildingRef underflowRef = AreaProbe.refOf(underflow);
        assertFalse(edge(network, overflowRef, NetworkSide.west, NetworkSide.east).conditional);
        assertTrue(edge(network, overflowRef, NetworkSide.west, NetworkSide.north).conditional);
        assertTrue(edge(network, underflowRef, NetworkSide.west, NetworkSide.east).conditional);
        assertFalse(edge(network, underflowRef, NetworkSide.west, NetworkSide.north).conditional);
    }

    @Test
    void overflowDuctKeepsForwardAndSideRoutesAsConditionalPossibilities(){
        Building duct = place(Blocks.overflowDuct, 10, 10, 0);
        ItemNetwork network = MindustryNetworkProbe.scan(AreaSelection.of(8, 8, 12, 12), Team.sharded);
        BuildingRef ref = AreaProbe.refOf(duct);
        NetworkPort west = input(ref, NetworkSide.west);

        assertTrue(network.graph.isReachable(west, output(ref, NetworkSide.east), copper));
        assertTrue(network.graph.isReachable(west, output(ref, NetworkSide.north), copper));
        assertTrue(network.graph.isReachable(west, output(ref, NetworkSide.south), copper));
    }

    @Test
    void anEnemyTransportDoesNotAppearInThePlayersTopology(){
        place(Blocks.conveyor, 10, 10, 0);
        place(Blocks.conveyor, 11, 10, Team.crux, 0);

        ItemNetwork network = MindustryNetworkProbe.scan(AreaSelection.of(8, 8, 14, 12), Team.sharded);

        assertEquals(8, network.graph.ports.size(), "the enemy conveyor must not be collected as a neighbor");
        assertTrue(network.boundaryPorts.isEmpty(), "an enemy neighbor is not a boundary continuation");
    }

    @Test
    void storageIsAnItemEndpointNotAnAutomaticConveyorSource(){
        Building storage = place(Blocks.container, 10, 10, 0);
        Building conveyor = place(Blocks.conveyor, 12, 10, 0);

        ItemNetwork network = MindustryNetworkProbe.scan(AreaSelection.of(8, 8, 14, 13), Team.sharded);

        assertFalse(network.graph.isReachable(output(AreaProbe.refOf(storage), NetworkSide.east),
            input(AreaProbe.refOf(conveyor), NetworkSide.west), copper));
    }

    @Test
    void unloadersArePartialUntilTheirNeighborTransferRulesAreModeled(){
        Building unloader = place(Blocks.unloader, 10, 10, 0);

        ItemNetwork network = MindustryNetworkProbe.scan(AreaSelection.of(8, 8, 12, 12), Team.sharded);

        assertEquals(NetworkCompleteness.partialUnsupportedTransport, network.completeness);
        assertEquals(List.of(AreaProbe.refOf(unloader)), network.unsupportedTransport);
        assertTrue(network.graph.ports.isEmpty());
        assertTrue(network.graph.edges.isEmpty());
    }

    @Test
    void itemBridgesUseTheirConfiguredRemoteLinkRatherThanProximity(){
        Building source = place(Blocks.itemBridge, 8, 10, 0);
        Building linked = place(Blocks.itemBridge, 12, 10, 0);
        Building nearby = place(Blocks.itemBridge, 9, 10, 0);
        source.configure(linked.tile.pos());

        ItemNetwork network = MindustryNetworkProbe.scan(AreaSelection.of(6, 8, 16, 12), Team.sharded);
        BuildingRef sourceRef = AreaProbe.refOf(source);
        BuildingRef linkedRef = AreaProbe.refOf(linked);
        BuildingRef nearbyRef = AreaProbe.refOf(nearby);

        assertTrue(network.graph.isReachable(output(sourceRef, NetworkSide.east), input(linkedRef, NetworkSide.west), copper));
        assertFalse(network.graph.isReachable(output(sourceRef, NetworkSide.east), input(nearbyRef, NetworkSide.west), copper));
    }

    @Test
    void linkedItemBridgesDoNotAlsoDumpIntoTheirLocalNeighbors(){
        Building feeder = place(Blocks.conveyor, 7, 10, 0);
        Building source = place(Blocks.itemBridge, 8, 10, 0);
        Building local = place(Blocks.conveyor, 9, 10, 0);
        Building target = place(Blocks.itemBridge, 12, 10, 0);
        source.configure(target.tile.pos());

        ItemNetwork network = MindustryNetworkProbe.scan(AreaSelection.of(6, 8, 16, 12), Team.sharded);

        assertTrue(network.graph.isReachable(output(AreaProbe.refOf(feeder), NetworkSide.east),
            input(AreaProbe.refOf(source), NetworkSide.west), copper));
        assertFalse(network.graph.isReachable(output(AreaProbe.refOf(feeder), NetworkSide.east),
            input(AreaProbe.refOf(local), NetworkSide.west), copper));
        assertTrue(network.graph.isReachable(output(AreaProbe.refOf(feeder), NetworkSide.east),
            input(AreaProbe.refOf(target), NetworkSide.west), copper));
    }

    @Test
    void itemBridgesDoNotCreateRemoteRoutesWithoutAValidConfiguredTarget(){
        Building source = place(Blocks.itemBridge, 8, 10, 0);
        Building candidate = place(Blocks.itemBridge, 14, 10, 0);
        Building conveyor = place(Blocks.conveyor, 11, 10, 0);

        ItemNetwork unconfigured = MindustryNetworkProbe.scan(AreaSelection.of(6, 8, 16, 12), Team.sharded);
        assertFalse(unconfigured.graph.isReachable(output(AreaProbe.refOf(source), NetworkSide.east),
            input(AreaProbe.refOf(candidate), NetworkSide.east), copper));

        source.configure(conveyor.tile.pos());
        ItemNetwork broken = MindustryNetworkProbe.scan(AreaSelection.of(6, 8, 16, 12), Team.sharded);
        assertFalse(broken.graph.isReachable(output(AreaProbe.refOf(source), NetworkSide.east),
            input(AreaProbe.refOf(candidate), NetworkSide.east), copper));
    }

    @Test
    void crossTypeBridgeNeedsBothBlocksToAllowThatLink(){
        Building source = place(ModdedBlocks.crossTypeBridge, 8, 10, 0);
        Building target = place(ModdedBlocks.sameTypeBridge, 12, 10, 0);
        source.configure(target.tile.pos());

        ItemNetwork network = MindustryNetworkProbe.scan(AreaSelection.of(6, 8, 14, 12), Team.sharded);

        assertTrue(((ItemBridge)source.block).linkValid(source.tile, target.tile));
        assertFalse(((ItemBridge)target.block).linkValid(source.tile, target.tile));
        assertFalse(target.acceptItem(source, Items.copper), "the receiving bridge rejects this cross-type link");
        assertFalse(network.graph.isReachable(output(AreaProbe.refOf(source), NetworkSide.east),
            input(AreaProbe.refOf(target), NetworkSide.west), copper));
    }

    @Test
    void crossTypeBridgeIsModeledWhenBothBlocksAllowIt(){
        Building source = place(ModdedBlocks.crossTypeBridge, 8, 10, 0);
        Building target = place(ModdedBlocks.otherCrossTypeBridge, 12, 10, 0);
        source.configure(target.tile.pos());

        ItemNetwork network = MindustryNetworkProbe.scan(AreaSelection.of(6, 8, 14, 12), Team.sharded);

        assertTrue(target.acceptItem(source, Items.copper));
        assertTrue(network.graph.isReachable(output(AreaProbe.refOf(source), NetworkSide.east),
            input(AreaProbe.refOf(target), NetworkSide.west), copper));
    }

    @Test
    void itemBridgesRejectConfiguredTargetsOutsideTheirEngineRange(){
        Building source = place(Blocks.itemBridge, 8, 10, 0);
        Building distant = place(Blocks.itemBridge, 14, 10, 0);
        source.configure(distant.tile.pos());

        ItemNetwork network = MindustryNetworkProbe.scan(AreaSelection.of(6, 8, 16, 12), Team.sharded);

        assertFalse(network.graph.isReachable(output(AreaProbe.refOf(source), NetworkSide.east),
            input(AreaProbe.refOf(distant), NetworkSide.west), copper));
    }

    @Test
    void anExitIntoTheNextTileOutsideTheSelectionIsAContinuationNotADisconnection(){
        Building inside = place(Blocks.conveyor, 10, 10, 0);
        place(Blocks.conveyor, 11, 10, 0);

        ItemNetwork network = MindustryNetworkProbe.scan(AreaSelection.of(10, 10, 10, 10), Team.sharded);

        assertTrue(network.boundaryPorts.contains(output(AreaProbe.refOf(inside), NetworkSide.east)));
    }

    @Test
    void anOutsideSourceWithACompatibleOutputIsRecordedAsAnInputContinuation(){
        place(Blocks.conveyor, 10, 10, 0);
        Building inside = place(Blocks.conveyor, 11, 10, 0);

        ItemNetwork network = MindustryNetworkProbe.scan(AreaSelection.of(11, 10, 11, 10), Team.sharded);

        assertTrue(network.boundaryInputs.contains(input(AreaProbe.refOf(inside), NetworkSide.west)));
    }

    @Test
    void anOutsideProducerCanContinueIntoASelectedFactory(){
        for(int x = 9; x <= 10; x++) for(int y = 9; y <= 10; y++) world.tile(x, y).setFloor((Floor)Blocks.sand);
        place(Blocks.mechanicalDrill, 9, 9, 0);
        Building target = place(Blocks.siliconSmelter, 11, 9, 0);

        AreaDiagnosticResult area = AreaProbe.scan(AreaSelection.of(11, 9, 12, 10), Team.sharded);
        SupplyTrace trace = TraceAnalyzer.input(area, AreaProbe.refOf(target), sand);

        assertFalse(trace.complete);
        assertFalse(trace.noRouteProven);
        assertEquals(List.of(input(AreaProbe.refOf(target), NetworkSide.west)), trace.boundaryContinuations);
        assertTrue(trace.producers().isEmpty(), "the outside drill is a continuation, not an in-area endpoint");
    }

    @Test
    void aMultiOutputProducerOutsideTheAreaOnlyContinuesItsDeclaredItems(){
        Building source = place(ModdedBlocks.yieldScaled, 10, 9, 0);
        Building target = place(Blocks.siliconSmelter, 11, 9, 0);

        AreaDiagnosticResult area = AreaProbe.scan(AreaSelection.of(11, 9, 11, 9), Team.sharded);
        SupplyTrace sandTrace = TraceAnalyzer.input(area, AreaProbe.refOf(target), sand);

        assertTrue(source.isValid());
        assertTrue(area.network.boundaryInputs.contains(input(AreaProbe.refOf(target), NetworkSide.west)),
            "source=" + source.tile.x + "," + source.tile.y + " size=" + source.block.size
                + " target=" + target.tile.x + "," + target.tile.y + " size=" + target.block.size
                + " boundary=" + area.network.boundaryInputs);
        ItemConstraint boundaryItems = area.network.boundaryInputConstraints.get(input(AreaProbe.refOf(target), NetworkSide.west));
        assertNotNull(boundaryItems);
        assertTrue(boundaryItems.allows(new ResourceRef(ResourceKind.item, "graphite", "Graphite")));
        assertTrue(boundaryItems.allows(new ResourceRef(ResourceKind.item, "silicon", "Silicon")));
        assertFalse(boundaryItems.allows(sand));
        assertTrue(sandTrace.boundaryContinuations.isEmpty(), "Graphite and Silicon do not continue Sand");
        assertTrue(sandTrace.complete);
        assertTrue(sandTrace.noRouteProven);
    }

    @Test
    void anUnsupportedNeighborIsAnInterruptionRatherThanADeadEndOrGuessedEdge(){
        Building unknown = place(Blocks.armoredConveyor, 10, 10, 0);
        Building inside = place(Blocks.conveyor, 11, 10, 0);

        ItemNetwork network = MindustryNetworkProbe.scan(AreaSelection.of(11, 10, 11, 10), Team.sharded);

        assertTrue(network.graph.edges.stream().noneMatch(edge -> edge.from.building.equals(AreaProbe.refOf(unknown))
            || edge.to.building.equals(AreaProbe.refOf(unknown))));
        assertTrue(network.unsupportedConnections.stream().anyMatch(connection ->
            connection.port.equals(input(AreaProbe.refOf(inside), NetworkSide.west))
                && connection.transport.equals(AreaProbe.refOf(unknown))
                && connection.direction == NetworkInterruption.Direction.incoming));
        assertEquals(NetworkCompleteness.partialUnsupportedTransport, network.completeness);
    }

    @Test
    void aConfiguredIncomingBridgeOutsideTheSelectionIsAnInputContinuation(){
        Building source = place(Blocks.itemBridge, 8, 10, 0);
        Building target = place(Blocks.itemBridge, 12, 10, 0);
        source.configure(target.tile.pos());
        ((ItemBridge.ItemBridgeBuild)target).incoming.add(source.tile.pos());

        ItemNetwork network = MindustryNetworkProbe.scan(AreaSelection.of(12, 10, 12, 10), Team.sharded);

        assertTrue(network.boundaryInputs.contains(input(AreaProbe.refOf(target), NetworkSide.west)));
        assertTrue(network.graph.ports.stream().noneMatch(port -> port.building.equals(AreaProbe.refOf(source))),
            "the probe must not pull the remote source into the selected area");
    }

    @Test
    void drillsExposeTheirDominantItemAsAProductWithoutAddingAProductionRate(){
        world.tile(10, 10).setFloor((Floor)Blocks.sand);
        Building drill = place(Blocks.mechanicalDrill, 10, 10, 0);

        FactorySnapshot snapshot = MindustryFactoryProbe.probe(drill);
        ItemNetwork network = MindustryNetworkProbe.scan(AreaSelection.of(8, 8, 12, 12), Team.sharded);

        assertEquals(List.of("sand"), snapshot.producedItems.stream().map(item -> item.id).toList());
        assertTrue(snapshot.outputs.isEmpty(), "drill capability is not relabeled as a rate measurement");
        assertTrue(network.resources.stream().anyMatch(item -> item.id.equals("sand")));
    }

    @Test
    void aRealDrillCanBeTracedToAMissingCrafterInput(){
        world.tile(9, 9).setFloor((Floor)Blocks.sand);
        world.tile(10, 9).setFloor((Floor)Blocks.sand);
        world.tile(9, 10).setFloor((Floor)Blocks.sand);
        world.tile(10, 10).setFloor((Floor)Blocks.sand);
        Building drill = place(Blocks.mechanicalDrill, 9, 9, 0);
        Building firstBelt = place(Blocks.conveyor, 11, 9, 0);
        Building secondBelt = place(Blocks.conveyor, 12, 9, 0);
        Building thirdBelt = place(Blocks.conveyor, 13, 9, 0);
        Building smelter = place(Blocks.siliconSmelter, 14, 9, 0);
        AreaSelection selection = AreaSelection.of(8, 8, 16, 13);

        AreaDiagnosticResult area = AreaProbe.scan(selection, Team.sharded);
        SupplyTrace trace = TraceAnalyzer.input(area, AreaProbe.refOf(smelter), sand);

        assertEquals(1, trace.producers().size(), "products="
            + area.entries.stream().map(entry -> entry.ref + "=" + (entry.snapshot == null ? "null" : entry.snapshot.producedItems)).toList()
            + " edges=" + area.network.graph.edges.stream().map(edge -> edge.from.building + ":" + edge.from.side + ">"
                + edge.to.building + ":" + edge.to.side).toList());
        assertEquals(AreaProbe.refOf(drill), trace.producers().get(0).building);
        assertTrue(trace.producers().get(0).path.ports().stream().anyMatch(port -> port.building.equals(AreaProbe.refOf(firstBelt))));
        assertTrue(trace.producers().get(0).path.ports().stream().anyMatch(port -> port.building.equals(AreaProbe.refOf(secondBelt))));
        assertTrue(trace.producers().get(0).path.ports().stream().anyMatch(port -> port.building.equals(AreaProbe.refOf(thirdBelt))));
        assertTrue(trace.complete);
        assertFalse(trace.noRouteProven);
    }

    @Test
    void aDisabledReachableDrillRemainsAProducerWithItsDiagnostic(){
        for(int x = 9; x <= 10; x++){
            for(int y = 10; y <= 11; y++) world.tile(x, y).setFloor((Floor)Blocks.sand);
            for(int y = 12; y <= 13; y++) world.tile(x, y).setFloor((Floor)Blocks.sand);
        }
        Building operating = place(Blocks.mechanicalDrill, 9, 10, 0);
        Building disabled = place(Blocks.mechanicalDrill, 9, 12, 0);
        disabled.enabled = false;
        place(Blocks.conveyor, 11, 10, 0);
        place(Blocks.conveyor, 12, 10, 0);
        place(Blocks.conveyor, 13, 10, 0);
        place(Blocks.conveyor, 11, 12, 0);
        place(Blocks.conveyor, 12, 12, 0);
        place(Blocks.conveyor, 13, 12, 0);
        place(Blocks.conveyor, 14, 12, 3);
        Building smelter = place(Blocks.siliconSmelter, 14, 10, 0);

        AreaDiagnosticResult area = AreaProbe.scan(AreaSelection.of(8, 8, 17, 15), Team.sharded);
        SupplyTrace trace = TraceAnalyzer.input(area, AreaProbe.refOf(smelter), sand);

        assertEquals(Set.of(AreaProbe.refOf(operating), AreaProbe.refOf(disabled)),
            new HashSet<>(trace.producers().stream().map(endpoint -> endpoint.building).toList()));
        assertEquals(2, trace.producers().size());
        assertEquals(DiagnosticReason.disabled, trace.producers().stream()
            .filter(endpoint -> endpoint.building.equals(AreaProbe.refOf(disabled)))
            .findFirst().orElseThrow().diagnostic.reason());
    }

    @Test
    void ductBridgesArePartialUntilTheirRemoteIngressIsModeled(){
        Building source = place(Blocks.ductBridge, 8, 10, 0);
        Building target = place(Blocks.ductBridge, 12, 10, 0);

        ItemNetwork network = MindustryNetworkProbe.scan(AreaSelection.of(6, 8, 14, 12), Team.sharded);

        assertEquals(NetworkCompleteness.partialUnsupportedTransport, network.completeness);
        assertEquals(Set.of(AreaProbe.refOf(source), AreaProbe.refOf(target)), new HashSet<>(network.unsupportedTransport));
        assertTrue(network.graph.edges.isEmpty());
    }

    @Test
    void ductRouterUsesItsConfiguredItemForTheForwardExit(){
        Building router = place(Blocks.ductRouter, 10, 10, 0);
        router.configure(Items.copper);
        ItemNetwork network = MindustryNetworkProbe.scan(AreaSelection.of(8, 8, 12, 12), Team.sharded);
        BuildingRef ref = AreaProbe.refOf(router);
        NetworkPort input = input(ref, NetworkSide.west);

        assertTrue(network.graph.isReachable(input, output(ref, NetworkSide.east), copper));
        assertFalse(network.graph.isReachable(input, output(ref, NetworkSide.north), copper));
        assertFalse(network.graph.isReachable(input, output(ref, NetworkSide.east), lead));
        assertTrue(network.graph.isReachable(input, output(ref, NetworkSide.north), lead));
    }

    @Test
    void unknownModdedTransportIsReportedWithoutGuessedEdges(){
        Building unknown = place(ModdedBlocks.unknownTransport, 10, 10, 0);
        ItemNetwork network = MindustryNetworkProbe.scan(AreaSelection.of(8, 8, 12, 12), Team.sharded);

        assertEquals(NetworkCompleteness.partialUnsupportedTransport, network.completeness);
        assertEquals(List.of(AreaProbe.refOf(unknown)), network.unsupportedTransport);
        assertTrue(network.graph.ports.isEmpty());
        assertTrue(network.graph.edges.isEmpty());
    }

    @Test
    void unknownItemConsumingTransportIsTopologyIncompleteNotDiagnosisIncomplete(){
        Building unknown = place(ModdedBlocks.unknownTransport, 10, 10, 0);
        AreaDiagnosticResult area = AreaProbe.scan(AreaSelection.of(8, 8, 12, 12), Team.sharded);
        SupplyTrace trace = TraceAnalyzer.input(area, AreaProbe.refOf(unknown), sand);

        assertTrue(trace.targetUsesItem);
        assertFalse(trace.diagnosticsIncomplete);
        assertTrue(trace.topologyIncomplete);
        assertFalse(trace.noRouteProven);
    }

    @Test
    void plastaniumConveyorsAreExplicitlyPartialUntilTheirBatchModesAreModeled(){
        Building conveyor = place(Blocks.plastaniumConveyor, 10, 10, 0);
        ItemNetwork network = MindustryNetworkProbe.scan(AreaSelection.of(8, 8, 12, 12), Team.sharded);

        assertEquals(NetworkCompleteness.partialUnsupportedTransport, network.completeness);
        assertEquals(List.of(AreaProbe.refOf(conveyor)), network.unsupportedTransport);
    }

    @Test
    void conventionalModdedCraftersContributeTheirInputsAndOutputToTheFilter(){
        place(ModdedBlocks.conventional, 10, 10, 0);

        ItemNetwork network = MindustryNetworkProbe.scan(AreaSelection.of(8, 8, 14, 14), Team.sharded);
        Set<String> resources = new HashSet<>();
        for(ResourceRef resource : network.resources) resources.add(resource.id);

        assertEquals(Set.of("copper", "lead", "graphite"), resources);
    }

    @Test
    void conventionalModdedCrafterCanBeTracedAsAProducer(){
        Building source = place(ModdedBlocks.conventional, 10, 10, 0);
        place(Blocks.conveyor, 11, 10, 0);
        Building target = place(ModdedBlocks.traceConsumer, 12, 10, 0);
        AreaSelection selection = AreaSelection.of(8, 8, 14, 12);

        AreaDiagnosticResult area = AreaProbe.scan(selection, Team.sharded);
        SupplyTrace trace = TraceAnalyzer.input(area, AreaProbe.refOf(target),
            new ResourceRef(ResourceKind.item, "graphite", "Graphite"));

        assertEquals(List.of(AreaProbe.refOf(source)), trace.producers().stream().map(endpoint -> endpoint.building).toList());
    }

    @Test
    void multipleOutputCrafterRoutesOnlyItsDeclaredProducts(){
        Building source = place(ModdedBlocks.yieldScaled, 10, 10, 0);
        Building belt = place(Blocks.conveyor, 11, 10, 0);
        Building target = place(ModdedBlocks.traceConsumer, 12, 10, 0);

        AreaDiagnosticResult area = AreaProbe.scan(AreaSelection.of(8, 8, 14, 12), Team.sharded);
        SupplyTrace copperTrace = TraceAnalyzer.output(area, AreaProbe.refOf(source), copper);
        SupplyTrace graphiteTrace = TraceAnalyzer.output(area, AreaProbe.refOf(source),
            new ResourceRef(ResourceKind.item, "graphite", "Graphite"));
        NetworkEdge sourceEdge = area.network.graph.edges.stream()
            .filter(edge -> edge.from.building.equals(AreaProbe.refOf(source))
                && edge.to.building.equals(AreaProbe.refOf(belt)))
            .findFirst().orElseThrow();

        assertTrue(sourceEdge.items.allows(new ResourceRef(ResourceKind.item, "graphite", "Graphite")));
        assertTrue(sourceEdge.items.allows(new ResourceRef(ResourceKind.item, "silicon", "Silicon")));
        assertFalse(sourceEdge.items.allows(copper));
        assertTrue(copperTrace.endpoints.isEmpty());
        assertEquals(List.of(AreaProbe.refOf(target)), graphiteTrace.endpoints.stream()
            .map(endpoint -> endpoint.building).toList());
    }

    @Test
    void nonCrafterItemConsumerCanBeTracedFromItsMissingInput(){
        for(int x = 9; x <= 10; x++) for(int y = 9; y <= 10; y++) world.tile(x, y).setFloor((Floor)Blocks.oreCoal);
        Building source = place(Blocks.mechanicalDrill, 9, 9, 0);
        place(Blocks.conveyor, 11, 9, 0);
        Building target = place(ModdedBlocks.coalConsumer, 12, 9, 0);

        AreaDiagnosticResult area = AreaProbe.scan(AreaSelection.of(8, 8, 14, 12), Team.sharded);
        SupplyTrace trace = TraceAnalyzer.input(area, AreaProbe.refOf(target),
            new ResourceRef(ResourceKind.item, "coal", "Coal"));

        assertTrue(trace.targetUsesItem);
        assertFalse(trace.diagnosticsIncomplete);
        assertEquals(List.of(AreaProbe.refOf(source)), trace.producers().stream().map(endpoint -> endpoint.building).toList());
    }

    @Test
    void extractionDoesNotChangeWorldState(){
        Building bridge = place(Blocks.itemBridge, 10, 10, 1);
        bridge.configure(world.tile(14, 10).pos());
        bridge.enabled = false;
        bridge.items.add(Items.copper, 3);
        int rotation = bridge.rotation;
        int link = ((mindustry.world.blocks.distribution.ItemBridge.ItemBridgeBuild)bridge).link;
        int items = bridge.items.get(Items.copper);

        MindustryNetworkProbe.scan(AreaSelection.of(8, 8, 16, 12), Team.sharded);

        assertEquals(rotation, bridge.rotation);
        assertEquals(link, ((mindustry.world.blocks.distribution.ItemBridge.ItemBridgeBuild)bridge).link);
        assertEquals(items, bridge.items.get(Items.copper));
        assertFalse(bridge.enabled);
    }

    @Test
    void runtimeFullnessAndEnablementDoNotChangeStaticRoutes(){
        Building first = place(Blocks.conveyor, 10, 10, 0);
        Building second = place(Blocks.conveyor, 11, 10, 0);
        AreaSelection area = AreaSelection.of(8, 8, 13, 12);
        ItemNetwork before = MindustryNetworkProbe.scan(area, Team.sharded);

        first.items.add(Items.copper, first.block.itemCapacity);
        second.items.add(Items.copper, second.block.itemCapacity);
        first.enabled = false;
        ItemNetwork after = MindustryNetworkProbe.scan(area, Team.sharded);

        assertEquals(before.graph.ports, after.graph.ports);
        assertEquals(before.graph.edges.size(), after.graph.edges.size());
        for(int i = 0; i < before.graph.edges.size(); i++){
            assertEquals(0, before.graph.edges.get(i).compareTo(after.graph.edges.get(i)));
        }
    }

    private static NetworkPort input(BuildingRef ref, NetworkSide side){ return new NetworkPort(ref, side, "in"); }
    private static NetworkPort output(BuildingRef ref, NetworkSide side){ return new NetworkPort(ref, side, "out"); }

    private static NetworkEdge edge(ItemNetwork network, BuildingRef ref, NetworkSide from, NetworkSide to){
        return network.graph.edges.stream().filter(edge -> edge.from.equals(input(ref, from)) && edge.to.equals(output(ref, to)))
            .findFirst().orElseThrow();
    }

    private static Building place(Block block, int x, int y, int rotation){ return place(block, x, y, Team.sharded, rotation); }

    private static Building place(Block block, int x, int y, Team team, int rotation){
        Tile tile = world.tile(x, y);
        tile.setBlock(block, team, rotation);
        assertNotNull(tile.build, "failed to place " + block.name);
        return tile.build;
    }
}
