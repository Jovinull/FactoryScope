package factoryscope.probe;

import factoryscope.area.*;
import factoryscope.model.*;
import factoryscope.network.*;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.world.*;
import org.junit.jupiter.api.*;

import java.util.*;

import static mindustry.Vars.*;
import static org.junit.jupiter.api.Assertions.*;

/** Transport semantics checked against real v159.7 block instances. */
class MindustryNetworkProbeTest{
    private static final ResourceRef copper = new ResourceRef(ResourceKind.item, "copper", "Copper");
    private static final ResourceRef lead = new ResourceRef(ResourceKind.item, "lead", "Lead");

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
            conveyor.tile.remove();
        }
    }

    @Test
    void armoredConveyorsKeepTheSameForwardOnlyTopology(){
        for(int rotation = 0; rotation < 4; rotation++){
            Building conveyor = place(Blocks.armoredConveyor, 10, 10, rotation);
            ItemNetwork network = MindustryNetworkProbe.scan(AreaSelection.of(8, 8, 12, 12), Team.sharded);
            BuildingRef ref = AreaProbe.refOf(conveyor);
            NetworkSide forward = NetworkSide.rotation(rotation);

            assertTrue(network.graph.isReachable(input(ref, forward.opposite()), output(ref, forward), copper));
            assertFalse(network.graph.isReachable(input(ref, forward), output(ref, forward.opposite()), copper));
            conveyor.tile.remove();
        }
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

    private static NetworkPort input(BuildingRef ref, NetworkSide side){ return new NetworkPort(ref, side, "in"); }
    private static NetworkPort output(BuildingRef ref, NetworkSide side){ return new NetworkPort(ref, side, "out"); }

    private static Building place(Block block, int x, int y, int rotation){ return place(block, x, y, Team.sharded, rotation); }

    private static Building place(Block block, int x, int y, Team team, int rotation){
        Tile tile = world.tile(x, y);
        tile.setBlock(block, team, rotation);
        assertNotNull(tile.build, "failed to place " + block.name);
        return tile.build;
    }
}
