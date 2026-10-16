package factoryscope.probe;

import factoryscope.area.*;
import factoryscope.liquid.*;
import factoryscope.model.*;
import factoryscope.network.*;
import factoryscope.trace.*;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.distribution.StackConveyor;
import org.junit.jupiter.api.*;

import java.util.*;

import static mindustry.Vars.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Duct bridges, unloaders, stack conveyors, mass drivers, liquid bridges and armored conduits, each checked
 * twice against v160.7: the modeled topology says where a resource can go, then the running engine moves it
 * and must deliver exactly there.
 */
class TransportFamiliesTest{
    private static final ResourceRef copper = item("copper", "Copper"), lead = item("lead", "Lead"),
        sand = item("sand", "Sand"), coal = item("coal", "Coal"), silicon = item("silicon", "Silicon");
    private static final ResourceRef water = new ResourceRef(ResourceKind.liquid, "water", "Water");
    //StackConveyor's protected state constants
    private static final int stackMove = 0, stackLoad = 1, stackUnload = 2;

    @BeforeAll
    static void boot(){ HeadlessGame.start(); }

    @BeforeEach
    void freshWorld(){ HeadlessGame.newWorld(48); }

    // ------------------------------------------------------------------ duct bridges

    @Test
    void aLinkedDuctBridgeSendsEverythingToItsLinkAndTheUnlinkedEndMovesItForward(){
        Building source = source(Items.copper, 6, 10);
        Building feed = place(Blocks.duct, 7, 10, 0);
        Building first = place(Blocks.ductBridge, 8, 10, 0);
        Building last = place(Blocks.ductBridge, 11, 10, 0);
        place(Blocks.duct, 12, 10, 0);
        Building end = place(Blocks.container, 13, 10, 0);
        place(Blocks.duct, 8, 11, 1);
        Building sideEnd = place(Blocks.container, 8, 12, 0);

        ItemNetwork network = MindustryNetworkProbe.scan(AreaSelection.of(5, 8, 16, 15), Team.sharded);
        assertOnlySandboxSourceUnsupported(network, source);
        assertTrue(reachable(network, out(feed, NetworkSide.east), in(end, NetworkSide.west), copper));
        assertFalse(reachable(network, out(feed, NetworkSide.east), in(sideEnd, NetworkSide.south), copper),
            "a linked duct bridge never puts items out beside itself");
        assertTrue(network.graph.edges.stream().anyMatch(edge -> edge.from.equals(out(first, NetworkSide.east))
            && edge.to.equals(in(last, NetworkSide.west))), "the remote link is one edge");

        HeadlessGame.run(600);
        assertTrue(end.items.get(Items.copper) > 0, "the engine delivers through the link and out of the unlinked end");
        assertEquals(0, sideEnd.items.get(Items.copper));
    }

    @Test
    void ductBridgeAcceptanceMatchesTheEngineOnEverySide(){
        Building first = place(Blocks.ductBridge, 8, 10, 0);
        Building last = place(Blocks.ductBridge, 11, 10, 0);
        place(Blocks.ductBridge, 8, 13, 3);
        Building west = place(Blocks.duct, 7, 10, 0);
        Building south = place(Blocks.duct, 8, 9, 1);
        Building north = place(Blocks.duct, 8, 11, 3);
        Building front = place(Blocks.duct, 9, 10, 2);
        Building intoUnlinked = place(Blocks.duct, 11, 9, 1);
        //the bridge arriving from the north registers itself on its target's next update
        HeadlessGame.run(2);

        ItemNetwork network = MindustryNetworkProbe.scan(AreaSelection.of(5, 7, 14, 15), Team.sharded);
        Map<Building, Boolean> expected = new LinkedHashMap<>();
        expected.put(west, true);
        expected.put(south, true);
        expected.put(north, false);
        expected.put(front, false);
        expected.forEach((neighbor, accepted) -> {
            assertEquals(accepted, first.acceptItem(neighbor, Items.copper), "engine, from " + neighbor.tileX() + "," + neighbor.tileY());
            assertEquals(accepted, edgeBetween(network, neighbor, first), "model, from " + neighbor.tileX() + "," + neighbor.tileY());
        });
        assertFalse(last.acceptItem(intoUnlinked, Items.copper), "an unlinked duct bridge accepts nothing from neighbors");
        assertFalse(edgeBetween(network, intoUnlinked, last));
    }

    @Test
    void ductBridgeLinksAcrossTheSelectionEdgeAreBoundaryContinuations(){
        Building first = place(Blocks.ductBridge, 8, 10, 0);
        Building last = place(Blocks.ductBridge, 11, 10, 0);

        ItemNetwork sender = MindustryNetworkProbe.scan(AreaSelection.of(6, 8, 9, 12), Team.sharded);
        ItemNetwork receiver = MindustryNetworkProbe.scan(AreaSelection.of(10, 8, 13, 12), Team.sharded);

        assertTrue(sender.boundaryPorts.contains(out(first, NetworkSide.east)));
        assertTrue(receiver.boundaryInputs.contains(in(last, NetworkSide.west)));
    }

    // ------------------------------------------------------------------ unloaders

    @Test
    void anUnloaderEmptiesStorageIntoItsOtherNeighborsButNeverIntoStorage(){
        Building store = place(Blocks.container, 10, 10, 0);
        store.items.add(Items.copper, 100);
        place(Blocks.unloader, 12, 10, 0);
        place(Blocks.conveyor, 13, 10, 0);
        Building end = place(Blocks.container, 14, 10, 0);
        Building otherStore = place(Blocks.container, 12, 11, 0);

        ItemNetwork network = MindustryNetworkProbe.scan(AreaSelection.of(8, 8, 17, 14), Team.sharded);
        assertEquals(NetworkCompleteness.complete, network.completeness);
        assertTrue(reachable(network, out(store, NetworkSide.east), in(end, NetworkSide.west), copper));
        assertFalse(reachable(network, out(store, NetworkSide.east), in(otherStore, NetworkSide.south), copper),
            "an unloader does not load storage");

        HeadlessGame.run(300);
        assertTrue(end.items.get(Items.copper) > 0);
        assertEquals(0, otherStore.items.get(Items.copper));
    }

    @Test
    void supplyTraceFollowsASortingUnloaderBackToTheStorageItTakesFrom(){
        Building store = place(Blocks.container, 10, 10, 0);
        store.items.add(Items.sand, 50);
        store.items.add(Items.coal, 50);
        Building unloader = place(Blocks.unloader, 12, 10, 0);
        unloader.configure(Items.sand);
        Building smelter = place(Blocks.siliconSmelter, 13, 10, 0);

        AreaDiagnosticResult area = AreaProbe.scan(AreaSelection.of(8, 8, 16, 13), Team.sharded);
        SupplyTrace sandTrace = TraceAnalyzer.input(area, AreaProbe.refOf(smelter), sand);
        SupplyTrace coalTrace = TraceAnalyzer.input(area, AreaProbe.refOf(smelter), coal);

        assertTrue(sandTrace.endpoints.stream().anyMatch(endpoint -> endpoint.kind == TraceEndpointKind.storage
            && endpoint.building.equals(AreaProbe.refOf(store))), "sand traces back through the unloader to the container");
        assertTrue(coalTrace.endpoints.stream().noneMatch(endpoint -> endpoint.building.equals(AreaProbe.refOf(store))),
            "the unloader is set to sand, so coal has no route from the container");

        HeadlessGame.run(120);
        assertTrue(smelter.items.get(Items.sand) > 0);
        assertEquals(0, smelter.items.get(Items.coal));
    }

    @Test
    void anUnsetUnloaderEmptiesACrafterOfItsProductsAndOfTheInputsItHolds(){
        Building feed = place(Blocks.conveyor, 9, 10, 0);
        Building smelter = place(Blocks.siliconSmelter, 10, 10, 0);
        smelter.items.add(Items.sand, 5);
        smelter.items.add(Items.silicon, 5);
        place(Blocks.unloader, 12, 10, 0);
        place(Blocks.conveyor, 13, 10, 0);
        Building end = place(Blocks.container, 14, 10, 0);

        ItemNetwork network = MindustryNetworkProbe.scan(AreaSelection.of(8, 8, 17, 13), Team.sharded);
        assertTrue(reachable(network, out(smelter, NetworkSide.east), in(end, NetworkSide.west), silicon), "the product");
        assertTrue(reachable(network, out(feed, NetworkSide.east), in(end, NetworkSide.west), sand),
            "sand fed to the smelter can be taken out again");
        assertFalse(reachable(network, out(feed, NetworkSide.east), in(end, NetworkSide.west), lead),
            "the smelter never holds lead");

        HeadlessGame.run(300);
        assertTrue(end.items.get(Items.silicon) > 0);
        assertTrue(end.items.get(Items.sand) > 0);
    }

    @Test
    void anUnloaderCannotTakeTurretAmmunition(){
        Building duo = place(Blocks.duo, 10, 10, 0);
        for(int i = 0; i < 6; i++) duo.handleItem(null, Items.copper);
        Building unloader = place(Blocks.unloader, 11, 10, 0);
        place(Blocks.conveyor, 12, 10, 0);
        Building end = place(Blocks.container, 13, 10, 0);

        ItemNetwork network = MindustryNetworkProbe.scan(AreaSelection.of(8, 8, 16, 13), Team.sharded);
        assertFalse(edgeBetween(network, duo, unloader), "ammunition is not kept in the turret's item module");

        HeadlessGame.run(300);
        assertEquals(0, end.items.get(Items.copper));
    }

    @Test
    void aDuctUnloaderTakesFromBehindAndGivesOnlyToItsFrontEvenIfThatIsStorage(){
        Building store = place(Blocks.container, 10, 10, 0);
        store.items.add(Items.copper, 30);
        store.items.add(Items.lead, 30);
        Building unloader = place(Blocks.ductUnloader, 12, 10, 0);
        unloader.configure(Items.lead);
        Building end = place(Blocks.container, 13, 10, 0);
        place(Blocks.duct, 12, 11, 1);
        Building sideEnd = place(Blocks.container, 12, 12, 0);

        ItemNetwork network = MindustryNetworkProbe.scan(AreaSelection.of(8, 8, 16, 15), Team.sharded);
        assertEquals(NetworkCompleteness.complete, network.completeness);
        assertTrue(reachable(network, out(store, NetworkSide.east), in(end, NetworkSide.west), lead));
        assertFalse(reachable(network, out(store, NetworkSide.east), in(end, NetworkSide.west), copper), "set to lead");
        assertFalse(reachable(network, out(store, NetworkSide.east), in(sideEnd, NetworkSide.south), lead), "front only");

        HeadlessGame.run(300);
        assertTrue(end.items.get(Items.lead) > 0);
        assertEquals(0, end.items.get(Items.copper));
        assertEquals(0, sideEnd.items.total());
    }

    // ------------------------------------------------------------------ stack conveyors

    @Test
    void aPlastaniumLineLoadsAtItsDockMovesStacksAndDumpsAtItsEnd(){
        Building source = source(Items.copper, 7, 10);
        Building feed = place(Blocks.conveyor, 8, 10, 0);
        Building dock = place(Blocks.plastaniumConveyor, 9, 10, 0);
        Building middle = place(Blocks.plastaniumConveyor, 10, 10, 0);
        Building tail = place(Blocks.plastaniumConveyor, 11, 10, 0);
        place(Blocks.conveyor, 12, 10, 0);
        Building end = place(Blocks.container, 13, 10, 0);
        place(Blocks.conveyor, 11, 11, 1);
        Building tailSide = place(Blocks.container, 11, 12, 0);
        place(Blocks.conveyor, 10, 9, 3);
        Building middleSide = place(Blocks.container, 10, 7, 0);

        assertEquals(stackLoad, ((StackConveyor.StackConveyorBuild)dock).state);
        assertEquals(stackMove, ((StackConveyor.StackConveyorBuild)middle).state);
        assertEquals(stackUnload, ((StackConveyor.StackConveyorBuild)tail).state);

        ItemNetwork network = MindustryNetworkProbe.scan(AreaSelection.of(6, 6, 16, 15), Team.sharded);
        assertOnlySandboxSourceUnsupported(network, source);
        assertTrue(reachable(network, out(feed, NetworkSide.east), in(end, NetworkSide.west), copper));
        assertTrue(reachable(network, out(feed, NetworkSide.east), in(tailSide, NetworkSide.south), copper),
            "an unloading plastanium conveyor dumps to every side");
        assertFalse(reachable(network, out(feed, NetworkSide.east), in(middleSide, NetworkSide.north), copper),
            "a moving stack never leaves sideways");

        HeadlessGame.run(900);
        assertTrue(end.items.get(Items.copper) > 0);
        assertTrue(tailSide.items.get(Items.copper) > 0);
        assertEquals(0, middleSide.items.get(Items.copper));
    }

    @Test
    void onlyALoadingDockAcceptsItems(){
        Building dock = place(Blocks.plastaniumConveyor, 9, 10, 0);
        Building middle = place(Blocks.plastaniumConveyor, 10, 10, 0);
        place(Blocks.plastaniumConveyor, 11, 10, 0);
        Building intoDock = place(Blocks.conveyor, 9, 11, 3);
        Building intoMiddle = place(Blocks.conveyor, 10, 11, 3);

        ItemNetwork network = MindustryNetworkProbe.scan(AreaSelection.of(7, 8, 13, 13), Team.sharded);

        assertTrue(dock.acceptItem(intoDock, Items.copper));
        assertTrue(edgeBetween(network, intoDock, dock));
        assertFalse(middle.acceptItem(intoMiddle, Items.copper));
        assertFalse(edgeBetween(network, intoMiddle, middle));
    }

    @Test
    void anUnloadingSurgeConveyorOnlyMovesForward(){
        source(Items.copper, 7, 10);
        Building feed = place(Blocks.conveyor, 8, 10, 0);
        place(Blocks.surgeConveyor, 9, 10, 0);
        place(Blocks.surgeConveyor, 10, 10, 0);
        Building tail = place(Blocks.surgeConveyor, 11, 10, 0);
        place(Blocks.conveyor, 12, 10, 0);
        Building end = place(Blocks.container, 13, 10, 0);
        place(Blocks.conveyor, 11, 11, 1);
        Building tailSide = place(Blocks.container, 11, 12, 0);

        assertEquals(stackUnload, ((StackConveyor.StackConveyorBuild)tail).state);
        ItemNetwork network = MindustryNetworkProbe.scan(AreaSelection.of(6, 8, 16, 15), Team.sharded);
        assertTrue(reachable(network, out(feed, NetworkSide.east), in(end, NetworkSide.west), copper));
        assertFalse(reachable(network, out(feed, NetworkSide.east), in(tailSide, NetworkSide.south), copper));

        HeadlessGame.run(900);
        assertTrue(end.items.get(Items.copper) > 0);
        assertEquals(0, tailSide.items.get(Items.copper));
    }

    // ------------------------------------------------------------------ mass drivers

    @Test
    void massDriversFireAtTheirLinkAndARelayAlsoDumpsWhatItReceives(){
        Building source = source(Items.copper, 6, 10);
        Building feed = place(Blocks.conveyor, 7, 10, 0);
        Building sender = place(Blocks.massDriver, 9, 10, 0);
        place(Blocks.powerSource, 9, 12, 0);
        place(Blocks.conveyor, 9, 8, 3);
        Building senderSide = place(Blocks.container, 9, 6, 0);

        Building relay = place(Blocks.massDriver, 24, 10, 0);
        place(Blocks.powerSource, 24, 12, 0);
        place(Blocks.conveyor, 24, 8, 3);
        Building relaySide = place(Blocks.container, 24, 6, 0);

        Building receiver = place(Blocks.massDriver, 39, 10, 0);
        place(Blocks.powerSource, 39, 12, 0);
        place(Blocks.conveyor, 41, 10, 0);
        Building end = place(Blocks.container, 42, 10, 0);

        sender.configure(relay.pos());
        relay.configure(receiver.pos());

        ItemNetwork network = MindustryNetworkProbe.scan(AreaSelection.of(4, 4, 45, 14), Team.sharded);
        assertOnlySandboxSourceUnsupported(network, source);
        assertTrue(reachable(network, out(feed, NetworkSide.east), in(end, NetworkSide.west), copper));
        assertTrue(reachable(network, out(feed, NetworkSide.east), in(relaySide, NetworkSide.north), copper),
            "a driver that is fired at dumps, even when it fires on");
        assertFalse(reachable(network, out(feed, NetworkSide.east), in(senderSide, NetworkSide.north), copper),
            "a linked driver nobody fires at never dumps");

        //while the sender keeps firing, the relay turns to receive every shot before its own reload ends, so
        //it only dumps; once the feed stops, it fires on to the receiver
        HeadlessGame.run(2400);
        assertTrue(relaySide.items.get(Items.copper) > 0, "the relay dumps part of what it receives");
        assertEquals(0, senderSide.items.get(Items.copper));
        source.tile.setAir();
        HeadlessGame.run(1200);
        assertTrue(end.items.get(Items.copper) > 0, "the relay fires on and the receiver dumps what reached it");
        assertEquals(0, senderSide.items.get(Items.copper));
    }

    @Test
    void massDriverLinksAcrossTheSelectionEdgeAreBoundaryContinuations(){
        Building sender = place(Blocks.massDriver, 9, 10, 0);
        Building receiver = place(Blocks.massDriver, 24, 10, 0);
        sender.configure(receiver.pos());

        ItemNetwork senderSide = MindustryNetworkProbe.scan(AreaSelection.of(6, 6, 12, 14), Team.sharded);
        ItemNetwork receiverSide = MindustryNetworkProbe.scan(AreaSelection.of(21, 6, 27, 14), Team.sharded);

        assertTrue(senderSide.boundaryPorts.contains(out(sender, NetworkSide.east)));
        assertTrue(receiverSide.boundaryInputs.contains(in(receiver, NetworkSide.west)));
    }

    // ------------------------------------------------------------------ liquid bridges and armored conduits

    @Test
    void aLinkedLiquidBridgeSendsRemotelyAndOnlyItsUnlinkedEndDumps(){
        for(Block bridgeBlock : new Block[]{Blocks.bridgeConduit, Blocks.phaseConduit}){
            HeadlessGame.newWorld(48);
            place(ModdedBlocks.liquidSource, 7, 10, 0);
            Building first = place(bridgeBlock, 8, 10, 0);
            place(Blocks.powerSource, 8, 9, 0);
            Building last = place(bridgeBlock, 11, 10, 0);
            first.configure(last.tile.pos());
            Building far = place(ModdedBlocks.liquidConsumer, 12, 10, 0);
            Building beside = place(ModdedBlocks.liquidConsumer, 11, 11, 0);
            Building near = place(ModdedBlocks.liquidConsumer, 8, 11, 0);

            AreaDiagnosticResult area = AreaProbe.scan(AreaSelection.of(5, 7, 15, 14), Team.sharded);
            assertFalse(area.liquids.partial(), bridgeBlock.name + " is modeled transport");
            LiquidTrace farTrace = LiquidTraceAnalyzer.input(area, AreaProbe.refOf(far), water);
            LiquidTrace besideTrace = LiquidTraceAnalyzer.input(area, AreaProbe.refOf(beside), water);
            LiquidTrace nearTrace = LiquidTraceAnalyzer.input(area, AreaProbe.refOf(near), water);
            assertFalse(farTrace.producers().isEmpty(), bridgeBlock.name + ": through the link");
            assertFalse(besideTrace.producers().isEmpty(), bridgeBlock.name + ": the unlinked end dumps to every free side");
            assertTrue(nearTrace.complete && nearTrace.noRouteProven, bridgeBlock.name + ": the linked end never dumps");

            HeadlessGame.run(300);
            assertTrue(far.liquids.get(Liquids.water) > 0f, bridgeBlock.name);
            assertTrue(beside.liquids.get(Liquids.water) > 0f, bridgeBlock.name);
            assertEquals(0f, near.liquids.get(Liquids.water), bridgeBlock.name);
        }
    }

    @Test
    void aReinforcedBridgeConduitSendsForwardAndItsUnlinkedEndOnlyFeedsItsFront(){
        place(ModdedBlocks.liquidSource, 7, 10, 0);
        Building first = place(Blocks.reinforcedBridgeConduit, 8, 10, 0);
        Building last = place(Blocks.reinforcedBridgeConduit, 11, 10, 0);
        Building far = place(ModdedBlocks.liquidConsumer, 12, 10, 0);
        Building beside = place(ModdedBlocks.liquidConsumer, 11, 11, 0);
        Building near = place(ModdedBlocks.liquidConsumer, 8, 11, 0);
        Building intoUnlinked = place(ModdedBlocks.liquidSource, 11, 8, 0);
        Building intoUnlinkedConduit = place(Blocks.conduit, 11, 9, 1);

        AreaDiagnosticResult area = AreaProbe.scan(AreaSelection.of(5, 7, 15, 14), Team.sharded);
        assertFalse(area.liquids.partial());
        assertFalse(LiquidTraceAnalyzer.input(area, AreaProbe.refOf(far), water).producers().isEmpty());
        assertTrue(LiquidTraceAnalyzer.input(area, AreaProbe.refOf(beside), water).noRouteProven);
        assertTrue(LiquidTraceAnalyzer.input(area, AreaProbe.refOf(near), water).noRouteProven);
        assertFalse(last.acceptLiquid(intoUnlinkedConduit, Liquids.water), "an unlinked bridge accepts only its incoming bridge");
        assertTrue(LiquidTraceAnalyzer.output(area, AreaProbe.refOf(intoUnlinked), water).endpoints.stream()
            .noneMatch(endpoint -> endpoint.kind == TraceEndpointKind.consumer), "so nothing reaches a consumer from its side");

        HeadlessGame.run(300);
        assertTrue(far.liquids.get(Liquids.water) > 0f);
        assertEquals(0f, beside.liquids.get(Liquids.water));
        assertEquals(0f, near.liquids.get(Liquids.water));
    }

    @Test
    void armoredConduitAcceptanceMatchesTheEngineForEverySourceSideAndRotation(){
        Block[] sources = {Blocks.conduit, Blocks.liquidRouter, Blocks.liquidJunction, ModdedBlocks.liquidSource};
        for(Block armored : new Block[]{Blocks.platedConduit, Blocks.reinforcedConduit}){
            for(int rotation = 0; rotation < 4; rotation++){
                for(NetworkSide side : NetworkSide.values()){
                    for(Block sourceBlock : sources){
                        HeadlessGame.newWorld(32);
                        Building target = place(armored, 16, 16, rotation);
                        //a conduit source points into the armored conduit; the other sources are not rotated
                        Building source = place(sourceBlock, 16 + side.dx, 16 + side.dy, side.opposite().ordinal());
                        boolean engine = target.acceptLiquid(source, Liquids.water);
                        LiquidNetwork network = MindustryLiquidProbe.scan(AreaSelection.of(12, 12, 20, 20), Team.sharded, Map.of());
                        boolean modeled = network.graph.edges.stream().anyMatch(edge -> edge.from.building.equals(AreaProbe.refOf(source))
                            && edge.to.building.equals(AreaProbe.refOf(target)));
                        assertEquals(engine, modeled, armored.name + " rotation=" + rotation + " source=" + sourceBlock.name + " side=" + side);
                    }
                }
            }
        }
    }

    @Test
    void anArmoredConduitCarriesLiquidFromBehindToItsFront(){
        Building producer = place(ModdedBlocks.liquidSource, 9, 10, 0);
        place(Blocks.platedConduit, 10, 10, 0);
        Building consumer = place(ModdedBlocks.liquidConsumer, 11, 10, 0);

        AreaDiagnosticResult area = AreaProbe.scan(AreaSelection.of(7, 8, 13, 12), Team.sharded);
        LiquidTrace trace = LiquidTraceAnalyzer.input(area, AreaProbe.refOf(consumer), water);
        assertTrue(trace.complete);
        assertEquals(List.of(AreaProbe.refOf(producer)), trace.producers().stream().map(endpoint -> endpoint.building).toList());

        HeadlessGame.run(120);
        assertTrue(consumer.liquids.get(Liquids.water) > 0f);
    }

    // ------------------------------------------------------------------ helpers

    /** The sandbox Item Source is in the transportation group, so it is the one interruption these fixtures have. */
    private static void assertOnlySandboxSourceUnsupported(ItemNetwork network, Building source){
        assertEquals(List.of(AreaProbe.refOf(source)), network.unsupportedTransport);
        assertTrue(network.unsupportedConnections.stream().allMatch(connection -> connection.transport.equals(AreaProbe.refOf(source))));
    }

    private static boolean reachable(ItemNetwork network, NetworkPort from, NetworkPort to, ResourceRef item){
        return network.graph.isReachable(from, to, item);
    }

    private static boolean edgeBetween(ItemNetwork network, Building from, Building to){
        BuildingRef source = AreaProbe.refOf(from), target = AreaProbe.refOf(to);
        return network.graph.edges.stream().anyMatch(edge -> edge.from.building.equals(source) && edge.to.building.equals(target));
    }

    private static NetworkPort in(Building build, NetworkSide side){ return new NetworkPort(AreaProbe.refOf(build), side, "in"); }
    private static NetworkPort out(Building build, NetworkSide side){ return new NetworkPort(AreaProbe.refOf(build), side, "out"); }

    private static ResourceRef item(String id, String name){ return new ResourceRef(ResourceKind.item, id, name); }

    private static Building source(Item item, int x, int y){
        Building source = place(Blocks.itemSource, x, y, 0);
        source.configure(item);
        return source;
    }

    private static Building place(Block block, int x, int y, int rotation){
        Tile tile = world.tile(x, y);
        tile.setBlock(block, Team.sharded, rotation);
        assertNotNull(tile.build, "failed to place " + block.name);
        return tile.build;
    }
}
