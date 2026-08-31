package factoryscope.probe;

import factoryscope.area.*;
import factoryscope.model.*;
import factoryscope.network.*;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.world.*;
import org.junit.jupiter.api.*;

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
    void anEnemyTransportDoesNotAppearInThePlayersTopology(){
        place(Blocks.conveyor, 10, 10, 0);
        place(Blocks.conveyor, 11, 10, Team.crux, 0);

        ItemNetwork network = MindustryNetworkProbe.scan(AreaSelection.of(8, 8, 14, 12), Team.sharded);

        assertEquals(8, network.graph.ports.size(), "the enemy conveyor must not be collected as a neighbor");
    }

    @Test
    void itemBridgesUseTheirConfiguredRemoteLinkRatherThanProximity(){
        Building source = place(Blocks.itemBridge, 8, 10, 0);
        Building linked = place(Blocks.itemBridge, 14, 10, 0);
        Building nearby = place(Blocks.itemBridge, 9, 10, 0);
        source.configure(linked.tile.pos());

        ItemNetwork network = MindustryNetworkProbe.scan(AreaSelection.of(6, 8, 16, 12), Team.sharded);
        BuildingRef sourceRef = AreaProbe.refOf(source);
        BuildingRef linkedRef = AreaProbe.refOf(linked);
        BuildingRef nearbyRef = AreaProbe.refOf(nearby);

        assertTrue(network.graph.isReachable(output(sourceRef, NetworkSide.east), input(linkedRef, NetworkSide.east), copper));
        assertFalse(network.graph.isReachable(output(sourceRef, NetworkSide.east), input(nearbyRef, NetworkSide.east), copper));
    }

    @Test
    void anExitIntoTheNextTileOutsideTheSelectionIsAContinuationNotADisconnection(){
        Building inside = place(Blocks.conveyor, 10, 10, 0);
        place(Blocks.conveyor, 11, 10, 0);

        ItemNetwork network = MindustryNetworkProbe.scan(AreaSelection.of(10, 10, 10, 10), Team.sharded);

        assertTrue(network.boundaryPorts.contains(output(AreaProbe.refOf(inside), NetworkSide.east)));
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
