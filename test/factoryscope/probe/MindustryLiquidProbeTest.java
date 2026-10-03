package factoryscope.probe;

import factoryscope.area.*;
import factoryscope.liquid.*;
import factoryscope.model.*;
import factoryscope.network.*;
import mindustry.content.*;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.environment.Floor;
import mindustry.world.blocks.distribution.DirectionLiquidBridge;
import mindustry.world.blocks.liquid.LiquidBridge;
import mindustry.world.blocks.liquid.LiquidJunction;
import mindustry.world.blocks.production.GenericCrafter;
import mindustry.world.blocks.production.Pump;
import org.junit.jupiter.api.*;

import java.util.*;

import static mindustry.Vars.*;
import static org.junit.jupiter.api.Assertions.*;

/** Topology adapter checks use real v160.5 buildings and the engine's public transfer decisions as oracles. */
class MindustryLiquidProbeTest{
    private static final ResourceRef water = new ResourceRef(ResourceKind.liquid, "water", "Water");
    private static final ResourceRef oil = new ResourceRef(ResourceKind.liquid, "oil", "Oil");

    @BeforeAll static void boot(){ HeadlessGame.start(); }
    @BeforeEach void freshWorld(){ HeadlessGame.newWorld(36); }

    @Test
    void conduitAcceptsBackAndSideInsertionButNeverItsOutputSide(){
        Building conduit = place(Blocks.conduit, 10, 10, 0);
        BuildingRef ref = AreaProbe.refOf(conduit);
        LiquidNetwork graph = scan(AreaSelection.of(8, 8, 12, 12));

        assertTrue(graph.graph.isReachable(input(ref, NetworkSide.west), output(ref, NetworkSide.east), water));
        assertTrue(graph.graph.isReachable(input(ref, NetworkSide.north), output(ref, NetworkSide.east), water));
        assertTrue(graph.graph.isReachable(input(ref, NetworkSide.south), output(ref, NetworkSide.east), water));
        assertFalse(graph.graph.isReachable(input(ref, NetworkSide.east), output(ref, NetworkSide.west), water));

        Building source = place(ModdedBlocks.liquidSource, 11, 10, 0);
        assertFalse(conduit.acceptLiquid(source, Liquids.water), "engine oracle rejects insertion at conduit output");
        assertTrue(conduit.acceptLiquid(place(ModdedBlocks.liquidSource, 9, 10, 0), Liquids.water),
            "engine oracle accepts back-side insertion when the buffer is compatible");
        assertTrue(conduit.acceptLiquid(place(ModdedBlocks.liquidSource, 10, 11, 0), Liquids.water),
            "engine oracle accepts side insertion when the buffer is compatible");
    }

    @Test
    void conduitOutputAndRejectedFrontRotateConsistentlyInAllFourDirections(){
        Map<Integer, Building> conduits = new HashMap<>();
        Map<Integer, Map<NetworkSide, Building>> sources = new HashMap<>();
        for(int rotation = 0; rotation < 4; rotation++){
            int x = 6 + rotation * 7, y = 14;
            Building conduit = place(Blocks.conduit, x, y, rotation);
            conduits.put(rotation, conduit);
            Map<NetworkSide, Building> adjacent = new EnumMap<>(NetworkSide.class);
            for(NetworkSide side : NetworkSide.values()){
                adjacent.put(side, place(ModdedBlocks.liquidSource, x + side.dx, y + side.dy, 0));
            }
            sources.put(rotation, adjacent);
        }
        LiquidNetwork graph = scan(AreaSelection.of(4, 11, 29, 17));

        for(int rotation = 0; rotation < 4; rotation++){
            Building conduit = conduits.get(rotation);
            BuildingRef ref = AreaProbe.refOf(conduit);
            NetworkSide front = NetworkSide.rotation(rotation);
            for(NetworkSide side : NetworkSide.values()){
                boolean input = side != front;
                assertEquals(input, conduit.acceptLiquid(sources.get(rotation).get(side), Liquids.water),
                    "engine acceptance for rotation " + rotation + " side " + side);
                assertEquals(input, graph.graph.isReachable(input(ref, side), output(ref, front), water),
                    "structural direction for rotation " + rotation + " side " + side);
            }
        }
    }

    @Test
    void conduitFrontTransfersToItsEstablishedLiquidDestination(){
        Building conduit = place(Blocks.conduit, 10, 10, 0);
        Building destination = place(ModdedBlocks.liquidConsumer, 11, 10, 0);
        conduit.liquids.add(Liquids.water, 5f);

        float transferred = conduit.moveLiquidForward(false, Liquids.water);

        assertTrue(transferred > 0f, "the real Conduit movement method transfers along its front output");
        assertTrue(destination.liquids.get(Liquids.water) > 0f);
    }

    @Test
    void currentOilInConduitDoesNotEraseWaterTopology(){
        Building producer = place(ModdedBlocks.liquidSource, 9, 10, 0);
        Building conduit = place(Blocks.conduit, 10, 10, 0);
        Building consumer = place(ModdedBlocks.liquidConsumer, 11, 10, 0);
        AreaSelection selection = AreaSelection.of(8, 8, 13, 12);
        LiquidNetwork empty = MindustryLiquidProbe.scan(selection, Team.sharded, Map.of());
        conduit.liquids.add(Liquids.oil, 4f);
        LiquidNetwork occupied = MindustryLiquidProbe.scan(selection, Team.sharded, Map.of());

        assertEquals(empty.graph.edges.size(), occupied.graph.edges.size());
        assertEquals(empty.graph.edges.stream().map(Object::toString).toList(), occupied.graph.edges.stream().map(Object::toString).toList());
        assertTrue(occupied.graph.isReachable(output(AreaProbe.refOf(producer), NetworkSide.east),
            input(AreaProbe.refOf(consumer), NetworkSide.west), water));
        assertEquals(List.of(oil), MindustryFactoryProbe.probe(conduit).storedLiquids.stream().map(state -> state.liquid).toList());
    }

    @Test
    void conduitOnlyCreatesExternalOutputAtItsFacingSide(){
        Building producer = place(ModdedBlocks.liquidSource, 9, 10, 0);
        Building conduit = place(Blocks.conduit, 10, 10, 0);
        Building front = place(ModdedBlocks.liquidConsumer, 11, 10, 0);
        Building side = place(ModdedBlocks.liquidConsumer, 10, 11, 0);
        LiquidNetwork graph = scan(AreaSelection.of(8, 8, 13, 13));
        BuildingRef producerRef = AreaProbe.refOf(producer), conduitRef = AreaProbe.refOf(conduit);

        assertTrue(graph.graph.isReachable(output(producerRef, NetworkSide.east),
            input(AreaProbe.refOf(front), NetworkSide.west), water));
        assertFalse(graph.graph.isReachable(output(producerRef, NetworkSide.east),
            input(AreaProbe.refOf(side), NetworkSide.south), water),
            "a neighboring consumer cannot create a route through a non-output conduit side");
        assertFalse(graph.graph.ports.contains(output(conduitRef, NetworkSide.north)));
    }

    @Test
    void liquidJunctionPreservesBothStraightChannelsEvenWhenDisabled(){
        Building west = place(ModdedBlocks.liquidSource, 9, 10, 0);
        Building junction = place(Blocks.liquidJunction, 10, 10, 0);
        Building east = place(ModdedBlocks.liquidConsumer, 11, 10, 0);
        Building northSink = place(ModdedBlocks.liquidConsumer, 10, 11, 0);
        BuildingRef junctionRef = AreaProbe.refOf(junction);
        AreaSelection selection = AreaSelection.of(8, 8, 12, 12);

        LiquidNetwork enabled = MindustryLiquidProbe.scan(selection, Team.sharded, Map.of());
        junction.enabled = false;
        LiquidNetwork disabled = MindustryLiquidProbe.scan(selection, Team.sharded, Map.of());

        NetworkPort westIn = input(junctionRef, NetworkSide.west);
        NetworkPort eastOut = output(junctionRef, NetworkSide.east);
        NetworkPort northOut = output(junctionRef, NetworkSide.north);
        assertTrue(enabled.graph.isReachable(output(AreaProbe.refOf(west), NetworkSide.east),
            input(AreaProbe.refOf(east), NetworkSide.west), water));
        assertFalse(enabled.graph.isReachable(westIn, northOut, water));
        assertEquals(enabled.graph.edges.size(), disabled.graph.edges.size(), "enablement is runtime state, not topology");

        // Independent engine oracle: with the Junction enabled, its destination keeps the same axis.
        junction.enabled = true;
        assertSame(east, ((LiquidJunction.LiquidJunctionBuild)junction).getLiquidDestination(west, Liquids.water));
        assertNotSame(northSink, ((LiquidJunction.LiquidJunctionBuild)junction).getLiquidDestination(west, Liquids.water));
    }

    @Test
    void liquidRouterExposesMultipleStructuralBranchesWithoutAFlowClaim(){
        Building router = place(Blocks.liquidRouter, 10, 10, 0);
        Building west = place(ModdedBlocks.liquidSource, 9, 10, 0);
        Building east = place(ModdedBlocks.liquidConsumer, 11, 10, 0);
        Building north = place(ModdedBlocks.liquidConsumer, 10, 11, 0);
        LiquidNetwork graph = scan(AreaSelection.of(8, 8, 12, 12));
        BuildingRef routerRef = AreaProbe.refOf(router);
        NetworkPort westIn = input(routerRef, NetworkSide.west);

        assertTrue(graph.graph.isReachable(westIn, output(routerRef, NetworkSide.east), water));
        assertTrue(graph.graph.isReachable(westIn, output(routerRef, NetworkSide.north), water));
        assertTrue(graph.storageEndpoints.contains(routerRef), "a router/tank can be transport and storage, never a producer");
        assertTrue(graph.graph.isReachable(westIn, input(AreaProbe.refOf(east), NetworkSide.west), water));
        assertTrue(graph.graph.isReachable(westIn, input(AreaProbe.refOf(north), NetworkSide.south), water));
        assertFalse(graph.resources.isEmpty(), "the graph captures resource identity, not current transfer activity");
    }

    @Test
    void liquidRouterCanDumpToEachStructurallyModeledBranch(){
        Building router = place(Blocks.liquidRouter, 10, 10, 0);
        Building east = place(ModdedBlocks.liquidConsumer, 11, 10, 0);
        Building north = place(ModdedBlocks.liquidConsumer, 10, 11, 0);
        router.liquids.add(Liquids.water, 10f);

        ((mindustry.world.blocks.liquid.LiquidRouter.LiquidRouterBuild)router).updateTile();

        assertTrue(east.liquids.get(Liquids.water) > 0f, "engine dump reaches the east branch");
        assertTrue(north.liquids.get(Liquids.water) > 0f, "engine dump reaches the north branch");
    }

    @Test
    void currentLiquidInRouterDoesNotChangeItsStructuralResourceBranches(){
        Building router = place(Blocks.liquidRouter, 10, 10, 0);
        Building source = place(ModdedBlocks.liquidSource, 9, 10, 0);
        Building consumer = place(ModdedBlocks.liquidConsumer, 11, 10, 0);
        AreaSelection selection = AreaSelection.of(8, 8, 12, 12);
        LiquidNetwork empty = scan(selection);
        router.liquids.add(Liquids.oil, 3f);
        LiquidNetwork occupied = scan(selection);

        assertEquals(empty.graph.edges.toString(), occupied.graph.edges.toString());
        assertTrue(occupied.graph.isReachable(output(AreaProbe.refOf(source), NetworkSide.east),
            input(AreaProbe.refOf(consumer), NetworkSide.west), water));
    }

    @Test
    void crafterProductsRemainLiquidSpecificAndRespectRotatedOutputDirections(){
        Building source = place(ModdedBlocks.dualLiquidSource, 10, 10, 1);
        Building northWater = place(ModdedBlocks.liquidConsumer, 10, 11, 0);
        Building westOil = place(ModdedBlocks.oilConsumer, 9, 10, 0);
        LiquidNetwork graph = scan(AreaSelection.of(8, 8, 13, 13));
        BuildingRef sourceRef = AreaProbe.refOf(source);
        NetworkPort north = output(sourceRef, NetworkSide.north), west = output(sourceRef, NetworkSide.west);

        assertTrue(graph.graph.isReachable(north, input(AreaProbe.refOf(northWater), NetworkSide.south), water),
            () -> graph.graph.edges.toString());
        assertFalse(graph.graph.isReachable(north, input(AreaProbe.refOf(northWater), NetworkSide.south), oil));
        assertFalse(graph.graph.isReachable(west, input(AreaProbe.refOf(westOil), NetworkSide.east), water));
        assertTrue(graph.graph.isReachable(west, input(AreaProbe.refOf(westOil), NetworkSide.east), oil));

        source.liquids.add(Liquids.water, 1f);
        source.liquids.add(Liquids.oil, 1f);
        ((GenericCrafter.GenericCrafterBuild)source).dumpOutputs();
        assertTrue(northWater.liquids.get(Liquids.water) > 0f, "the engine dumps Water to its rotated declared side");
        assertTrue(westOil.liquids.get(Liquids.oil) > 0f, "the engine dumps Oil to its own rotated declared side");
        assertEquals(0f, northWater.liquids.get(Liquids.oil), "one output cannot borrow the other liquid's direction");
        assertEquals(0f, westOil.liquids.get(Liquids.water));
    }

    @Test
    void invalidModdedOutputDirectionIsIncompleteRatherThanUnrestricted(){
        Building source = place(ModdedBlocks.invalidDirectionLiquidSource, 10, 10, 0);
        Building consumer = place(ModdedBlocks.liquidConsumer, 11, 10, 0);
        AreaDiagnosticResult area = AreaProbe.scan(AreaSelection.of(8, 8, 13, 12), Team.sharded);

        LiquidTrace trace = LiquidTraceAnalyzer.input(area, AreaProbe.refOf(consumer), water);

        assertFalse(trace.complete);
        assertFalse(trace.noRouteProven);
        assertTrue(trace.incompleteConnections.stream().anyMatch(value -> value.building.equals(AreaProbe.refOf(source))));
        assertFalse(area.liquids.graph.isReachable(output(AreaProbe.refOf(source), NetworkSide.east),
            input(AreaProbe.refOf(consumer), NetworkSide.west), water));
    }

    @Test
    void filterConsumerKeepsAllAcceptedLiquidIdentities(){
        Building consumer = place(ModdedBlocks.filteredLiquidConsumer, 10, 10, 0);
        FactorySnapshot snapshot = MindustryFactoryProbe.probe(consumer);
        ResourceState requirement = snapshot.inputs.stream().filter(input -> input.kind == ResourceKind.liquid).findFirst().orElseThrow();

        assertTrue(requirement.acceptedResources.contains(liquid(Liquids.water)));
        assertTrue(requirement.acceptedResources.contains(liquid(Liquids.cryofluid)));
        assertFalse(requirement.accepts(liquid(Liquids.oil)));
    }

    @Test
    void unsupportedTransportBesideConsumerPreventsFalseNoRouteProof(){
        Building armored = place(Blocks.platedConduit, 10, 10, 0);
        Building consumer = place(ModdedBlocks.liquidConsumer, 11, 10, 0);
        AreaSelection selection = AreaSelection.of(8, 8, 13, 12);
        AreaDiagnosticResult area = AreaProbe.scan(selection, Team.sharded);
        LiquidTrace trace = LiquidTraceAnalyzer.input(area, AreaProbe.refOf(consumer), water);

        assertFalse(trace.noRouteProven, "an unmodeled adjacent transport is not a proven dead end");
        assertFalse(trace.complete);
        assertTrue(trace.unsupportedInterruptions.stream().anyMatch(value -> value.transport.equals(AreaProbe.refOf(armored))));
    }

    @Test
    void unsupportedTransportJustOutsideSelectionIsNotMisreportedAsKnownBoundary(){
        Building armored = place(Blocks.platedConduit, 10, 10, 0);
        Building consumer = place(ModdedBlocks.liquidConsumer, 11, 10, 0);
        AreaDiagnosticResult area = AreaProbe.scan(AreaSelection.of(11, 9, 13, 12), Team.sharded);

        LiquidTrace trace = LiquidTraceAnalyzer.input(area, AreaProbe.refOf(consumer), water);

        assertTrue(trace.boundaryContinuations.isEmpty(), "unsupported routing outside scope is not a proven boundary route");
        assertFalse(trace.noRouteProven);
        assertTrue(trace.unsupportedTransports.contains(AreaProbe.refOf(armored)),
            "the immediate visible interruption remains locatable although it lies outside the selected rectangle");
    }

    @Test
    void unsupportedTransportOutsideOutputTraceIsNotMisreportedAsBoundary(){
        Building source = place(ModdedBlocks.liquidSource, 10, 10, 0);
        Building armored = place(Blocks.platedConduit, 11, 10, 0);
        AreaDiagnosticResult area = AreaProbe.scan(AreaSelection.of(8, 8, 10, 12), Team.sharded);

        LiquidTrace trace = LiquidTraceAnalyzer.output(area, AreaProbe.refOf(source), water);

        assertTrue(trace.boundaryContinuations.isEmpty(), "unsupported transport is not a known structural continuation");
        assertFalse(trace.complete);
        assertFalse(trace.noRouteProven);
        assertTrue(trace.unsupportedTransports.contains(AreaProbe.refOf(armored)));
        assertTrue(trace.unsupportedInterruptions.stream().anyMatch(value -> value.transport.equals(AreaProbe.refOf(armored))));
    }

    @Test
    void declaredModdedConsumerWithoutLiquidOutputIsAModeledTerminal(){
        Building source = place(ModdedBlocks.liquidSource, 9, 10, 0);
        Building consumer = place(ModdedBlocks.unknownLiquidConsumer, 10, 10, 0);
        AreaDiagnosticResult area = AreaProbe.scan(AreaSelection.of(8, 8, 12, 12), Team.sharded);
        LiquidTrace trace = LiquidTraceAnalyzer.input(area, AreaProbe.refOf(consumer), water);

        assertTrue(trace.targetUsesLiquid);
        assertTrue(trace.complete);
        assertEquals(List.of(AreaProbe.refOf(source)), trace.producers().stream().map(endpoint -> endpoint.building).toList());
        assertFalse(area.liquids.unsupportedTransport.contains(AreaProbe.refOf(consumer)));
    }

    @Test
    void unknownLiquidOutputTransportRemainsAnUncertaintyBoundary(){
        Building unknown = place(ModdedBlocks.unknownLiquidTransport, 10, 10, 0);
        Building target = place(ModdedBlocks.liquidConsumer, 11, 10, 0);
        AreaDiagnosticResult area = AreaProbe.scan(AreaSelection.of(8, 8, 13, 12), Team.sharded);
        LiquidTrace trace = LiquidTraceAnalyzer.input(area, AreaProbe.refOf(target), water);

        assertTrue(area.liquids.unsupportedTransport.contains(AreaProbe.refOf(unknown)));
        assertFalse(trace.noRouteProven);
        assertFalse(trace.complete);
        assertTrue(trace.unsupportedInterruptions.stream().anyMatch(value -> value.transport.equals(AreaProbe.refOf(unknown))));
    }

    @Test
    void storedLiquidSnapshotEnumeratesEveryPositiveModuleEntry(){
        Building tank = place(Blocks.liquidTank, 10, 10, 0);
        tank.liquids.add(Liquids.water, 4f);
        tank.liquids.add(Liquids.oil, 3f);

        FactorySnapshot snapshot = MindustryFactoryProbe.probe(tank);

        assertEquals(Set.of(water, oil), new HashSet<>(snapshot.storedLiquids.stream().map(state -> state.liquid).toList()));
        assertEquals(2, snapshot.storedLiquids.size(), "LiquidModule.current() is not a complete inventory");
    }

    @Test
    void validLiquidBridgeLinkIsStructuralAndInvalidProximityIsNot(){
        Building source = place(Blocks.bridgeConduit, 8, 10, 0);
        Building destination = place(Blocks.bridgeConduit, 12, 10, 0);
        source.configure(world.tile(12, 10).pos());
        BuildingRef sourceRef = AreaProbe.refOf(source), destinationRef = AreaProbe.refOf(destination);
        LiquidNetwork linked = scan(AreaSelection.of(6, 8, 20, 12));
        NetworkPort remoteOut = output(sourceRef, NetworkSide.east), remoteIn = input(destinationRef, NetworkSide.west);
        assertTrue(linked.graph.isReachable(remoteOut, remoteIn, water));

        source.liquids.add(Liquids.water, 5f);
        ((LiquidBridge.LiquidBridgeBuild)source).warmup = 1f;
        ((LiquidBridge.LiquidBridgeBuild)source).updateTransport(destination);
        assertTrue(destination.liquids.get(Liquids.water) > 0f,
            "the configured remote route is confirmed by the actual v160.5 bridge transfer implementation");

        Building near = place(Blocks.bridgeConduit, 10, 20, 0);
        Building nearTarget = place(Blocks.bridgeConduit, 12, 20, 0);
        LiquidNetwork unlinked = scan(AreaSelection.of(8, 18, 14, 22));
        assertFalse(unlinked.graph.edges.stream().anyMatch(edge -> edge.from.building.equals(AreaProbe.refOf(near))
            && edge.to.building.equals(AreaProbe.refOf(nearTarget))),
            "proximity alone never creates a remote bridge edge");
    }

    @Test
    void enemyBridgeAndStoredLiquidNeverLeakIntoTheSelectedTeamGraph(){
        Building source = place(Blocks.bridgeConduit, 8, 10, 0);
        Building enemy = place(Blocks.bridgeConduit, 14, 10, 0, Team.crux);
        enemy.liquids.add(Liquids.oil, 5f);
        source.configure(enemy.tile.pos());

        LiquidNetwork graph = scan(AreaSelection.of(6, 8, 18, 12));

        assertFalse(graph.graph.ports.stream().anyMatch(port -> port.building.equals(AreaProbe.refOf(enemy))));
        assertFalse(graph.graph.edges.stream().anyMatch(edge -> edge.from.building.equals(AreaProbe.refOf(enemy))
            || edge.to.building.equals(AreaProbe.refOf(enemy))));
        assertFalse(graph.resources.contains(oil), "hidden enemy buffer content is not exposed as a resource");
        assertFalse(graph.storageEndpoints.contains(AreaProbe.refOf(enemy)));
    }

    @Test
    void armoredAndDirectionLiquidBridgesRemainExplicitlyPartial(){
        Building armored = place(Blocks.platedConduit, 10, 10, 0);
        Building directional = place(Blocks.reinforcedBridgeConduit, 14, 10, 0);
        LiquidNetwork graph = scan(AreaSelection.of(8, 8, 16, 12));

        assertTrue(graph.unsupportedTransport.contains(AreaProbe.refOf(armored)));
        assertTrue(graph.unsupportedTransport.contains(AreaProbe.refOf(directional)));
        assertTrue(graph.partial());
    }

    @Test
    void placedPumpProductComesFromTheActualLiquidDrop(){
        world.tile(9, 9).setFloor((Floor)Blocks.water);
        world.tile(10, 9).setFloor((Floor)Blocks.water);
        world.tile(9, 10).setFloor((Floor)Blocks.water);
        world.tile(10, 10).setFloor((Floor)Blocks.water);
        Building pump = place(Blocks.mechanicalPump, 10, 10, 0);
        assertInstanceOf(Pump.PumpBuild.class, pump);
        ResourceRef actual = liquid(((Pump.PumpBuild)pump).liquidDrop);
        FactorySnapshot snapshot = MindustryFactoryProbe.probe(pump);
        assertTrue(snapshot.producedLiquids.contains(actual));
    }

    @Test
    void disabledPlacedPumpStillTracesThroughAConduitToWaterConsumer(){
        world.tile(10, 10).setFloor((Floor)Blocks.water);
        Building pump = place(Blocks.mechanicalPump, 10, 10, 0);
        assertEquals(Liquids.water, ((Pump.PumpBuild)pump).liquidDrop);
        pump.enabled = false;
        place(Blocks.conduit, 11, 10, 0);
        Building conduit = place(Blocks.conduit, 12, 10, 0);
        Building consumer = place(Blocks.cryofluidMixer, 13, 10, 0);
        assertEquals(2, consumer.block.size);
        assertSame(consumer, world.tile(13, 10).build, "the multiblock west edge touches the conduit");
        AreaSelection selection = AreaSelection.of(8, 8, 16, 13);
        AreaDiagnosticResult area = AreaProbe.scan(selection, Team.sharded);

        LiquidTrace trace = LiquidTraceAnalyzer.input(area, AreaProbe.refOf(consumer), water);

        assertTrue(trace.targetUsesLiquid);
        assertTrue(area.liquids.graph.edges.stream().anyMatch(edge -> edge.from.building.equals(AreaProbe.refOf(conduit))
            && edge.to.building.equals(AreaProbe.refOf(consumer))), "the conduit output must connect to the mixer input");
        assertTrue(trace.producers().stream().anyMatch(endpoint -> endpoint.building.equals(AreaProbe.refOf(pump))),
            () -> "products=" + MindustryFactoryProbe.probe(pump).producedLiquids
                + ", entries=" + area.entries.stream().map(entry -> entry.ref + ":" + entry.snapshot.producedLiquids).toList()
                + ", ports=" + area.liquids.graph.ports.stream().map(MindustryLiquidProbeTest::portText).toList()
                + ", traversed=" + trace.traversedEdges.stream().map(MindustryLiquidProbeTest::edgeText).toList()
                + ", edges=" + area.liquids.graph.edges.stream().map(MindustryLiquidProbeTest::edgeText).toList());
    }

    @Test
    void solidPumpProductIsKnownBeforeItsFirstUpdate(){
        Building pump = place(ModdedBlocks.solidLiquidPump, 10, 10, 0);
        assertNull(((Pump.PumpBuild)pump).liquidDrop, "the engine assigns this during updateTile");

        FactorySnapshot snapshot = MindustryFactoryProbe.probe(pump);

        assertTrue(snapshot.producedLiquids.contains(oil), "SolidPump.result is structural product metadata");
    }

    @Test
    void solidPumpOutputCanReachItsDeclaredLiquidConsumer(){
        Building pump = place(ModdedBlocks.solidLiquidPump, 10, 10, 0);
        Building consumer = place(ModdedBlocks.oilConsumer, 11, 10, 0);
        AreaDiagnosticResult area = AreaProbe.scan(AreaSelection.of(8, 8, 13, 12), Team.sharded);

        LiquidTrace trace = LiquidTraceAnalyzer.input(area, AreaProbe.refOf(consumer), oil);

        assertTrue(trace.complete);
        assertEquals(List.of(AreaProbe.refOf(pump)), trace.producers().stream().map(endpoint -> endpoint.building).toList());
    }

    @Test
    void erekirHydrogenGasKeepsItsResourceIdentityThroughLiquidTopology(){
        Building source = place(Blocks.electrolyzer, 10, 10, 0);
        Building consumer = place(ModdedBlocks.hydrogenConsumer, 10, 8, 0);
        AreaSelection selection = AreaSelection.of(8, 6, 12, 13);
        LiquidNetwork graph = scan(selection);
        ResourceRef hydrogen = liquid(Liquids.hydrogen);

        assertTrue(MindustryFactoryProbe.probe(source).producedLiquids.contains(hydrogen));
        assertTrue(graph.graph.isReachable(output(AreaProbe.refOf(source), NetworkSide.south),
            input(AreaProbe.refOf(consumer), NetworkSide.north), hydrogen));
        assertFalse(graph.graph.isReachable(output(AreaProbe.refOf(source), NetworkSide.south),
            input(AreaProbe.refOf(consumer), NetworkSide.north), water));
    }

    private static LiquidNetwork scan(AreaSelection selection){
        return MindustryLiquidProbe.scan(selection, Team.sharded, Map.of());
    }

    private static Building place(Block block, int x, int y, int rotation){
        return place(block, x, y, rotation, Team.sharded);
    }

    private static Building place(Block block, int x, int y, int rotation, Team team){
        Tile tile = world.tile(x, y);
        tile.setBlock(block, team, rotation);
        assertNotNull(tile.build, "failed to place " + block.name);
        return tile.build;
    }

    private static NetworkPort input(BuildingRef ref, NetworkSide side){ return new NetworkPort(ref, side, "in"); }
    private static NetworkPort output(BuildingRef ref, NetworkSide side){ return new NetworkPort(ref, side, "out"); }
    private static String portText(NetworkPort port){ return port.building + "/" + port.channel + "/" + port.side; }
    private static String edgeText(LiquidNetworkEdge edge){ return portText(edge.from) + " -> " + portText(edge.to) + " " + edge.liquids; }
    private static ResourceRef liquid(mindustry.type.Liquid value){ return new ResourceRef(ResourceKind.liquid, value.name, value.localizedName); }
}
