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
import mindustry.world.blocks.liquid.LiquidRouter;
import mindustry.world.blocks.production.GenericCrafter;
import mindustry.world.blocks.production.Pump;
import mindustry.type.Liquid;
import org.junit.jupiter.api.*;

import java.util.*;

import static mindustry.Vars.*;
import static org.junit.jupiter.api.Assertions.*;

/** Topology adapter checks use real v160.5 buildings and the engine's public transfer decisions as oracles. */
class MindustryLiquidProbeTest{
    private static final ResourceRef water = new ResourceRef(ResourceKind.liquid, "water", "Water");
    private static final ResourceRef oil = new ResourceRef(ResourceKind.liquid, "oil", "Oil");

    @BeforeAll static void boot(){ HeadlessGame.start(); }
    @BeforeEach void freshWorld(){
        ModdedBlocks.dynamicLiquidRequirements = new mindustry.type.LiquidStack[]{new mindustry.type.LiquidStack(Liquids.water, 0.1f)};
        HeadlessGame.newWorld(36);
    }

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
                Building source = sources.get(rotation).get(side);
                assertEquals(input, conduit.acceptLiquid(source, Liquids.water),
                    "engine acceptance for rotation " + rotation + " side " + side);
                assertEquals(input, graph.graph.isReachable(input(ref, side), output(ref, front), water),
                    "structural direction for rotation " + rotation + " side " + side);
                assertEquals(input, graph.graph.ports.contains(input(ref, side)),
                    "only the three engine-accepted sides become Conduit input ports");
                if(input){
                    assertFalse(graph.graph.isReachable(output(ref, front), input(ref, side), water),
                        "a Conduit does not structurally reverse its forward transport direction");
                }

                source.liquids.add(Liquids.water, 1f);
                source.dumpLiquid(Liquids.water);
                assertEquals(input, conduit.liquids.get(Liquids.water) > 0f,
                    "real engine dump transfer for rotation " + rotation + " side " + side);
                conduit.liquids.clear();
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
        List<String> expectedEdges = empty.graph.edges.stream().map(Object::toString).toList();
        for(Liquid liquid : List.of(Liquids.water, Liquids.oil)){
            for(float amount : new float[]{1f, conduit.block.liquidCapacity}){
                conduit.liquids.clear();
                conduit.liquids.add(liquid, amount);
                LiquidNetwork occupied = MindustryLiquidProbe.scan(selection, Team.sharded, Map.of());
                assertEquals(expectedEdges, occupied.graph.edges.stream().map(Object::toString).toList(),
                    "contents and fullness do not change static routes: " + liquid.name + " " + amount);
                assertTrue(occupied.graph.isReachable(output(AreaProbe.refOf(producer), NetworkSide.east),
                    input(AreaProbe.refOf(consumer), NetworkSide.west), water));
                assertFalse(occupied.graph.ports.contains(input(AreaProbe.refOf(conduit), NetworkSide.east)),
                    "the current Water buffer cannot create a front/input port");
                assertFalse(occupied.graph.isReachable(input(AreaProbe.refOf(conduit), NetworkSide.east),
                    output(AreaProbe.refOf(conduit), NetworkSide.west), water),
                    "the current Water buffer cannot create reverse/front input topology");
            }
        }
        conduit.liquids.clear();
        conduit.liquids.add(Liquids.oil, 4f);
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
        junction.liquids.add(Liquids.water, 5f);
        junction.moveLiquid(((LiquidJunction.LiquidJunctionBuild)junction).getLiquidDestination(west, Liquids.water), Liquids.water);
        assertTrue(east.liquids.get(Liquids.water) > 0f, "engine movement continues west-to-east");
        assertEquals(0f, northSink.liquids.get(Liquids.water), "the crossing channel receives no Water");
    }

    @Test
    void reinforcedLiquidVariantsKeepTheirEngineBaseRoutingAndStorageRoles(){
        Building west = place(ModdedBlocks.liquidSource, 5, 10, 0);
        Building junction = place(Blocks.reinforcedLiquidJunction, 6, 10, 0);
        Building east = place(ModdedBlocks.liquidConsumer, 7, 10, 0);
        Building northSink = place(ModdedBlocks.liquidConsumer, 6, 11, 0);

        Building router = place(Blocks.reinforcedLiquidRouter, 12, 10, 0);
        Building routerEast = place(ModdedBlocks.liquidConsumer, 13, 10, 0);
        Building routerNorth = place(ModdedBlocks.liquidConsumer, 12, 11, 0);
        Building container = place(Blocks.reinforcedLiquidContainer, 19, 10, 0);
        Building tank = place(Blocks.reinforcedLiquidTank, 25, 10, 0);

        AreaSelection selection = AreaSelection.of(3, 7, 31, 14);
        LiquidNetwork graph = scan(selection);
        BuildingRef junctionRef = AreaProbe.refOf(junction);
        BuildingRef routerRef = AreaProbe.refOf(router);

        assertTrue(graph.graph.isReachable(output(AreaProbe.refOf(west), NetworkSide.east),
            input(AreaProbe.refOf(east), NetworkSide.west), water));
        assertFalse(graph.graph.isReachable(input(junctionRef, NetworkSide.west),
            output(junctionRef, NetworkSide.north), water));
        assertSame(east, ((LiquidJunction.LiquidJunctionBuild)junction).getLiquidDestination(west, Liquids.water),
            "the reinforced block is an engine LiquidJunction with straight-axis destinations");

        assertTrue(graph.graph.isReachable(input(routerRef, NetworkSide.west),
            output(routerRef, NetworkSide.east), water));
        assertTrue(graph.graph.isReachable(input(routerRef, NetworkSide.west),
            input(AreaProbe.refOf(routerNorth), NetworkSide.south), water));
        router.liquids.add(Liquids.water, 10f);
        ((LiquidRouter.LiquidRouterBuild)router).updateTile();
        assertTrue(routerEast.liquids.get(Liquids.water) > 0f,
            "reinforced router uses the engine LiquidRouter dump behavior");
        assertTrue(routerNorth.liquids.get(Liquids.water) > 0f,
            "reinforced router exposes another structurally possible side without claiming a split rate");

        assertTrue(graph.storageEndpoints.contains(AreaProbe.refOf(router)));
        assertTrue(graph.storageEndpoints.contains(AreaProbe.refOf(container)));
        assertTrue(graph.storageEndpoints.contains(AreaProbe.refOf(tank)));
        assertTrue(MindustryFactoryProbe.probe(router).producedLiquids.isEmpty());
        assertTrue(MindustryFactoryProbe.probe(container).producedLiquids.isEmpty());
        assertTrue(MindustryFactoryProbe.probe(tank).producedLiquids.isEmpty(),
            "storage variants remain storage and never become producers");
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
        Building west = place(ModdedBlocks.liquidConsumer, 9, 10, 0);
        Building south = place(ModdedBlocks.liquidConsumer, 10, 9, 0);
        router.liquids.add(Liquids.water, 10f);

        ((mindustry.world.blocks.liquid.LiquidRouter.LiquidRouterBuild)router).updateTile();

        assertTrue(east.liquids.get(Liquids.water) > 0f, "engine dump reaches the east branch");
        assertTrue(north.liquids.get(Liquids.water) > 0f, "engine dump reaches the north branch");
        assertTrue(west.liquids.get(Liquids.water) > 0f, "engine dump reaches the west branch");
        assertTrue(south.liquids.get(Liquids.water) > 0f, "engine dump reaches the south branch");
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
        for(int rotation = 0; rotation < 4; rotation++){
            int x = 6 + rotation * 7, y = 10;
            Building source = place(ModdedBlocks.dualLiquidSource, x, y, rotation);
            NetworkSide waterSide = NetworkSide.rotation(rotation);
            NetworkSide oilSide = NetworkSide.rotation(rotation + 1);
            Building waterSink = place(ModdedBlocks.anyLiquidConsumer, x + waterSide.dx, y + waterSide.dy, 0);
            Building oilSink = place(ModdedBlocks.anyLiquidConsumer, x + oilSide.dx, y + oilSide.dy, 0);
            LiquidNetwork graph = scan(AreaSelection.of(4, 7, 32, 13));
            BuildingRef sourceRef = AreaProbe.refOf(source);

            assertTrue(graph.graph.isReachable(output(sourceRef, waterSide),
                input(AreaProbe.refOf(waterSink), waterSide.opposite()), water), "Water output rotation " + rotation);
            assertFalse(graph.graph.isReachable(output(sourceRef, waterSide),
                input(AreaProbe.refOf(waterSink), waterSide.opposite()), oil),
                "the Water side cannot carry the sibling Oil product into a multi-liquid tank");
            assertFalse(graph.graph.isReachable(output(sourceRef, oilSide),
                input(AreaProbe.refOf(oilSink), oilSide.opposite()), water),
                "the Oil side cannot carry the sibling Water product into a multi-liquid tank");
            assertTrue(graph.graph.isReachable(output(sourceRef, oilSide),
                input(AreaProbe.refOf(oilSink), oilSide.opposite()), oil), "Oil output rotation " + rotation);

            source.liquids.add(Liquids.water, 1f);
            source.liquids.add(Liquids.oil, 1f);
            ((GenericCrafter.GenericCrafterBuild)source).dumpOutputs();
            assertTrue(waterSink.liquids.get(Liquids.water) > 0f, "engine dumps Water on its rotated side");
            assertTrue(oilSink.liquids.get(Liquids.oil) > 0f, "engine dumps Oil on its rotated side");
            assertEquals(0f, waterSink.liquids.get(Liquids.oil), "Water cannot borrow the Oil direction");
            assertEquals(0f, oilSink.liquids.get(Liquids.water), "Oil cannot borrow the Water direction");
        }
    }

    @Test
    void unrestrictedLiquidProductDoesNotMakeItsDirectedSiblingUnrestricted(){
        Building source = place(ModdedBlocks.mixedDirectionLiquidSource, 10, 10, 0);
        Building northTank = place(ModdedBlocks.anyLiquidConsumer, 10, 11, 0);
        Building eastOil = place(ModdedBlocks.oilConsumer, 11, 10, 0);
        LiquidNetwork graph = scan(AreaSelection.of(8, 8, 13, 13));
        BuildingRef sourceRef = AreaProbe.refOf(source);

        assertTrue(graph.graph.isReachable(output(sourceRef, NetworkSide.north),
            input(AreaProbe.refOf(northTank), NetworkSide.south), water));
        assertFalse(graph.graph.isReachable(output(sourceRef, NetworkSide.north),
            input(AreaProbe.refOf(northTank), NetworkSide.south), oil),
            "Oil's east-only declaration is not widened by Water's unrestricted output");
        assertTrue(graph.graph.isReachable(output(sourceRef, NetworkSide.east),
            input(AreaProbe.refOf(eastOil), NetworkSide.west), oil));
    }

    @Test
    void dynamicLiquidConsumerRequirementsAreReevaluatedOnRefresh(){
        Building consumer = place(ModdedBlocks.dynamicLiquidConsumer, 10, 10, 0);
        AreaSelection selection = AreaSelection.of(8, 8, 12, 12);

        AreaDiagnosticResult waterReport = AreaProbe.scan(selection, Team.sharded);
        LiquidTrace waterTrace = LiquidTraceAnalyzer.input(waterReport, AreaProbe.refOf(consumer), water);
        assertTrue(waterTrace.targetUsesLiquid);

        ModdedBlocks.dynamicLiquidRequirements = new mindustry.type.LiquidStack[]{
            new mindustry.type.LiquidStack(Liquids.oil, 0.1f)
        };
        AreaDiagnosticResult oilReport = AreaProbe.scan(selection, Team.sharded);
        LiquidTrace staleWater = LiquidTraceAnalyzer.input(oilReport, AreaProbe.refOf(consumer), water);
        LiquidTrace refreshedOil = LiquidTraceAnalyzer.input(oilReport, AreaProbe.refOf(consumer), oil);
        assertFalse(staleWater.targetUsesLiquid, "Refresh must remove the prior dynamic Water requirement");
        assertTrue(refreshedOil.targetUsesLiquid, "Refresh must expose the newly configured Oil requirement");

        ModdedBlocks.dynamicLiquidRequirements = null;
        AreaDiagnosticResult malformed = AreaProbe.scan(selection, Team.sharded);
        LiquidTrace incomplete = LiquidTraceAnalyzer.input(malformed, AreaProbe.refOf(consumer), water);
        assertFalse(malformed.entries.stream().filter(entry -> entry.ref.equals(AreaProbe.refOf(consumer)))
            .findFirst().orElseThrow().snapshot.liquidInputsComplete);
        assertTrue(incomplete.requirementsIncomplete, "a null dynamic result is incomplete, not an empty requirement set");
        assertFalse(incomplete.noRouteProven);

        for(mindustry.type.LiquidStack malformedStack : new mindustry.type.LiquidStack[]{null,
            new mindustry.type.LiquidStack(null, 0.1f)}){
            ModdedBlocks.dynamicLiquidRequirements = new mindustry.type.LiquidStack[]{malformedStack};
            AreaDiagnosticResult malformedEntry = AreaProbe.scan(selection, Team.sharded);
            FactorySnapshot malformedSnapshot = malformedEntry.entries.stream()
                .filter(entry -> entry.ref.equals(AreaProbe.refOf(consumer))).findFirst().orElseThrow().snapshot;
            assertFalse(malformedSnapshot.liquidInputsComplete,
                "null dynamic stacks/liquid identities remain explicit uncertainty");
        }
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

        Building positiveInvalid = place(ModdedBlocks.invalidPositiveDirectionLiquidSource, 10, 20, 0);
        Building secondConsumer = place(ModdedBlocks.liquidConsumer, 11, 20, 0);
        AreaDiagnosticResult positiveArea = AreaProbe.scan(AreaSelection.of(8, 18, 13, 22), Team.sharded);
        LiquidTrace positiveTrace = LiquidTraceAnalyzer.input(positiveArea, AreaProbe.refOf(secondConsumer), water);
        assertFalse(positiveTrace.complete, "direction 4 is not normalized to a supported side");
        assertFalse(positiveTrace.noRouteProven);
        assertTrue(positiveTrace.incompleteConnections.stream()
            .anyMatch(value -> value.building.equals(AreaProbe.refOf(positiveInvalid))));
    }

    @Test
    void declaredCrafterLiquidProductRemainsStructuralAtZeroTimeScale(){
        Building source = place(ModdedBlocks.liquidSource, 10, 10, 0);
        Building consumer = place(ModdedBlocks.liquidConsumer, 11, 10, 0);
        source.applySlowdown(0f, 60f);
        assertEquals(0f, source.timeScale(), "fixture has zero current production speed");

        AreaDiagnosticResult area = AreaProbe.scan(AreaSelection.of(8, 8, 13, 12), Team.sharded);
        LiquidTrace trace = LiquidTraceAnalyzer.input(area, AreaProbe.refOf(consumer), water);

        assertTrue(area.entries.stream().filter(entry -> entry.ref.equals(AreaProbe.refOf(source)))
            .findFirst().orElseThrow().snapshot.producedLiquids.contains(water),
            "declared output identity is structural and independent of current rate");
        assertEquals(List.of(AreaProbe.refOf(source)), trace.producers().stream().map(endpoint -> endpoint.building).toList());
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
    void filterEnumerationFailureDiscardsPartialSetAndMarksRequirementsIncomplete(){
        Building consumer = place(ModdedBlocks.throwingFilterLiquidConsumer, 10, 10, 0);

        ModdedBlocks.filterSawWater = false;
        ModdedBlocks.failFilterEnumeration = true;
        FactorySnapshot snapshot;
        try{
            snapshot = MindustryFactoryProbe.probe(consumer);
        }finally{
            ModdedBlocks.failFilterEnumeration = false;
        }

        assertTrue(ModdedBlocks.filterSawWater, "the filter fixture fails after a partial accepted set exists");
        assertFalse(snapshot.liquidInputsComplete,
            "one filter predicate failure cannot turn a partially enumerated set into a complete set");
        assertTrue(snapshot.inputs.stream().noneMatch(input -> input.kind == ResourceKind.liquid
                && input.acceptedResources.contains(water)),
            "the partially accumulated Water identity is not exposed as the whole accepted set");
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
        assertTrue(linked.unsupportedTransport.contains(sourceRef));
        assertTrue(linked.unsupportedTransport.contains(destinationRef));
        assertFalse(linked.graph.isReachable(remoteOut, remoteIn, water),
            "configured remote transfer is real, but its local/remote port rules are intentionally partial");

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
    void linkedLiquidBridgeDoesNotStructurallyDumpToItsLocalNeighbors(){
        Building producer = place(ModdedBlocks.liquidSource, 9, 10, 0);
        Building bridge = place(Blocks.bridgeConduit, 10, 10, 0);
        Building remote = place(Blocks.bridgeConduit, 14, 10, 0);
        Building localConsumer = place(ModdedBlocks.liquidConsumer, 10, 11, 0);
        bridge.configure(remote.tile.pos());

        // The engine accepts a local input away from the configured link direction, then the
        // linked bridge update sends it remotely. A valid-link LiquidBridge does not run doDump().
        producer.liquids.add(Liquids.water, 5f);
        producer.dumpLiquid(Liquids.water);
        assertTrue(bridge.liquids.get(Liquids.water) > 0f, "the local input reaches the bridge module");
        LiquidBridge.LiquidBridgeBuild bridgeBuild = (LiquidBridge.LiquidBridgeBuild)bridge;
        bridgeBuild.warmup = 1f;
        bridgeBuild.updateTile();
        assertTrue(remote.liquids.get(Liquids.water) > 0f, "the configured remote link receives the liquid");
        assertEquals(0f, localConsumer.liquids.get(Liquids.water),
            "with a valid remote link, the bridge does not locally dump to this neighbor");

        LiquidNetwork graph = scan(AreaSelection.of(7, 8, 17, 13));
        assertFalse(graph.graph.isReachable(output(AreaProbe.refOf(producer), NetworkSide.east),
            input(AreaProbe.refOf(localConsumer), NetworkSide.south), water),
            "a static route must not use LiquidBridge as an all-side local router while its configured link is valid");

        AreaDiagnosticResult area = AreaProbe.scan(AreaSelection.of(7, 8, 17, 13), Team.sharded);
        LiquidTrace trace = LiquidTraceAnalyzer.input(area, AreaProbe.refOf(localConsumer), water);
        assertFalse(trace.complete);
        assertFalse(trace.noRouteProven, "the partial bridge prevents a complete no-route conclusion");
        assertTrue(trace.unsupportedInterruptions.stream().anyMatch(value -> value.transport.equals(AreaProbe.refOf(bridge))),
            "the relevant bridge is surfaced as an interruption, not silently omitted");
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
