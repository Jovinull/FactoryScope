package factoryscope.liquid;

import factoryscope.analysis.FactoryAnalyzer;
import factoryscope.area.*;
import factoryscope.model.*;
import factoryscope.network.*;
import factoryscope.trace.TraceDirection;
import factoryscope.trace.TraceEndpointKind;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class LiquidTraceAnalyzerTest{
    private static final AreaSelection selection = AreaSelection.of(0, 0, 30, 30);
    private static final ResourceRef water = liquid("water", "Water");
    private static final ResourceRef oil = liquid("oil", "Oil");

    @Test
    void inputTraceFindsOneProducerAndDeterministicStructuralPath(){
        BuildingRef pump = ref("pump", 1), conduit = ref("conduit", 2), target = ref("crafter", 3);
        NetworkPort source = port(pump, NetworkSide.east, "out");
        NetworkPort conduitIn = port(conduit, NetworkSide.west, "in"), conduitOut = port(conduit, NetworkSide.east, "out");
        NetworkPort sink = port(target, NetworkSide.west, "in");
        LiquidNetwork network = network(List.of(source, conduitIn, conduitOut, sink), List.of(
            edge(source, conduitIn, LiquidConstraint.only(water)), edge(conduitIn, conduitOut, LiquidConstraint.any()),
            edge(conduitOut, sink, LiquidConstraint.only(water))));

        LiquidTrace trace = LiquidTraceAnalyzer.input(area(network,
            entry(pump, producer(water)), entry(conduit, FactorySnapshot.builder("conduit").build()),
            entry(target, consumer(water))), target, water);

        assertTrue(trace.complete);
        assertFalse(trace.noRouteProven);
        assertEquals(List.of(pump), trace.producers().stream().map(endpoint -> endpoint.building).toList());
        assertEquals(List.of(source, conduitIn, conduitOut, sink), trace.producers().get(0).path.ports());
        assertEquals(TraceEndpointKind.producer, trace.endpoints.get(0).kind);
    }

    @Test
    void currentContentsDoNotParticipateInStructuralResourceRouting(){
        BuildingRef pump = ref("pump", 1), conduit = ref("conduit", 2), target = ref("crafter", 3);
        NetworkPort source = port(pump, NetworkSide.east, "out"), pipeIn = port(conduit, NetworkSide.west, "in");
        NetworkPort pipeOut = port(conduit, NetworkSide.east, "out"), sink = port(target, NetworkSide.west, "in");
        LiquidNetwork network = network(List.of(source, pipeIn, pipeOut, sink), List.of(
            edge(source, pipeIn, LiquidConstraint.only(water)), edge(pipeIn, pipeOut, LiquidConstraint.any()),
            edge(pipeOut, sink, LiquidConstraint.only(water))));
        FactorySnapshot occupiedWithOil = FactorySnapshot.builder("conduit")
            .storedLiquid(new StoredLiquidState(oil, 6f, 10f)).build();

        LiquidTrace trace = LiquidTraceAnalyzer.input(area(network, entry(pump, producer(water)),
            entry(conduit, occupiedWithOil), entry(target, consumer(water))), target, water);

        assertEquals(List.of(pump), trace.producers().stream().map(endpoint -> endpoint.building).toList());
        assertEquals(List.of(oil), occupiedWithOil.storedLiquids.stream().map(state -> state.liquid).toList());
    }

    @Test
    void liquidIdentityFiltersEachEdgeAndDoesNotUseDisplayName(){
        ResourceRef otherWater = liquid("othermod-water", "Water");
        BuildingRef sourceBuilding = ref("pump", 1), target = ref("crafter", 2);
        NetworkPort source = port(sourceBuilding, NetworkSide.east, "out"), sink = port(target, NetworkSide.west, "in");
        LiquidNetwork graph = network(List.of(source, sink), List.of(edge(source, sink, LiquidConstraint.only(otherWater))));

        LiquidTrace trace = LiquidTraceAnalyzer.input(area(graph, entry(sourceBuilding, producer(water)),
            entry(target, consumer(water))), target, water);

        assertTrue(trace.producers().isEmpty());
        assertTrue(trace.noRouteProven, "a complete topology with only a different content identity proves this liquid has no route");
    }

    @Test
    void skippedBuildingDiagnosticsPreventStrongNoRouteConclusion(){
        BuildingRef target = ref("crafter", 1), skipped = ref("unknown-liquid-building", 2);
        NetworkPort sink = port(target, NetworkSide.west, "in");
        LiquidNetwork graph = network(List.of(sink), List.of());
        AreaDiagnosticResult area = area(graph, entry(target, consumer(water))).withSkippedBuildings(List.of(skipped));

        LiquidTrace trace = LiquidTraceAnalyzer.input(area, target, water);

        assertFalse(trace.complete);
        assertFalse(trace.noRouteProven, "a missing building snapshot cannot be described as a complete no-route proof");
    }

    @Test
    void unsupportedAndBoundaryEvidencePreventStrongNoRoute(){
        BuildingRef target = ref("crafter", 1), unknown = ref("armored-conduit", 2);
        NetworkPort sink = port(target, NetworkSide.west, "in");
        LiquidNetwork boundary = new LiquidNetwork(graph(List.of(sink), List.of()), List.of(), List.of(sink),
            Map.of(), Map.of(sink, LiquidConstraint.only(water)), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(water));
        LiquidTrace outside = LiquidTraceAnalyzer.input(area(boundary, entry(target, consumer(water))), target, water);
        assertFalse(outside.complete);
        assertFalse(outside.noRouteProven);
        assertEquals(List.of(sink), outside.boundaryContinuations);

        LiquidInterruption interruption = new LiquidInterruption(sink, unknown, LiquidInterruption.Direction.incoming,
            LiquidConstraint.only(water));
        LiquidNetwork partial = new LiquidNetwork(graph(List.of(sink), List.of()), List.of(), List.of(),
            Map.of(), Map.of(), List.of(unknown), List.of(interruption), List.of(), List.of(), List.of(), List.of(water));
        LiquidTrace interrupted = LiquidTraceAnalyzer.input(area(partial, entry(target, consumer(water))), target, water);
        assertFalse(interrupted.complete);
        assertFalse(interrupted.noRouteProven);
        assertEquals(List.of(interruption), interrupted.unsupportedInterruptions);
    }

    @Test
    void reachableProducerCoexistsWithAnUnsupportedBranch(){
        BuildingRef pump = ref("pump", 1), target = ref("crafter", 2), unknown = ref("armored-conduit", 3);
        NetworkPort source = port(pump, NetworkSide.east, "out"), sink = port(target, NetworkSide.west, "in");
        LiquidInterruption interruption = new LiquidInterruption(sink, unknown, LiquidInterruption.Direction.incoming,
            LiquidConstraint.only(water));
        LiquidNetwork graph = new LiquidNetwork(graph(List.of(source, sink),
            List.of(edge(source, sink, LiquidConstraint.only(water)))), List.of(), List.of(), Map.of(), Map.of(),
            List.of(unknown), List.of(interruption), List.of(), List.of(), List.of(), List.of(water));

        LiquidTrace trace = LiquidTraceAnalyzer.input(area(graph, entry(pump, producer(water)),
            entry(target, consumer(water))), target, water);

        assertEquals(List.of(pump), trace.producers().stream().map(endpoint -> endpoint.building).toList());
        assertEquals(List.of(interruption), trace.unsupportedInterruptions);
        assertFalse(trace.complete);
        assertFalse(trace.noRouteProven);
    }

    @Test
    void twoDeadEndsAfterASharedPrefixAreBothPreserved(){
        BuildingRef target = ref("crafter", 1), router = ref("liquid-router", 2);
        BuildingRef deadA = ref("conduit-a", 3), deadB = ref("conduit-b", 4);
        NetworkPort targetIn = port(target, NetworkSide.west, "in");
        NetworkPort routerOut = port(router, NetworkSide.east, "out");
        NetworkPort routerInA = port(router, NetworkSide.west, "in"), routerInB = port(router, NetworkSide.north, "in");
        NetworkPort deadAIn = port(deadA, NetworkSide.west, "in"), deadAOut = port(deadA, NetworkSide.east, "out");
        NetworkPort deadBIn = port(deadB, NetworkSide.south, "in"), deadBOut = port(deadB, NetworkSide.north, "out");
        List<NetworkPort> ports = List.of(targetIn, routerOut, routerInA, routerInB, deadAIn, deadAOut, deadBIn, deadBOut);
        List<LiquidNetworkEdge> edges = List.of(edge(routerOut, targetIn, LiquidConstraint.only(water)),
            edge(routerInA, routerOut, LiquidConstraint.any()), edge(routerInB, routerOut, LiquidConstraint.any()),
            edge(deadAOut, routerInA, LiquidConstraint.only(water)), edge(deadAIn, deadAOut, LiquidConstraint.any()),
            edge(deadBOut, routerInB, LiquidConstraint.only(water)), edge(deadBIn, deadBOut, LiquidConstraint.any()));

        LiquidTrace trace = LiquidTraceAnalyzer.input(area(network(ports, edges), entry(target, consumer(water))), target, water);

        assertEquals(List.of(deadA, deadB), trace.structuralDeadEnds);
        assertFalse(trace.noRouteProven, "the graph contains known structural branches, not an empty topology");
    }

    @Test
    void producerDeadEndBoundaryAndUnsupportedBranchRemainDistinctEvidence(){
        BuildingRef target = ref("consumer", 1), producer = ref("pump", 2), dead = ref("conduit-dead", 3);
        BuildingRef unsupported = ref("armored-conduit", 4);
        NetworkPort producerOut = port(producer, NetworkSide.east, "out");
        NetworkPort deadOut = port(dead, NetworkSide.east, "out");
        NetworkPort targetA = port(target, NetworkSide.west, "in");
        NetworkPort targetB = port(target, NetworkSide.east, "in");
        NetworkPort targetC = port(target, NetworkSide.north, "in");
        NetworkPort targetD = port(target, NetworkSide.south, "in");
        LiquidInterruption interruption = new LiquidInterruption(targetD, unsupported,
            LiquidInterruption.Direction.incoming, LiquidConstraint.only(water));
        LiquidNetwork topology = new LiquidNetwork(graph(List.of(producerOut, deadOut, targetA, targetB, targetC, targetD),
            List.of(edge(producerOut, targetA, LiquidConstraint.only(water)),
                edge(deadOut, targetB, LiquidConstraint.only(water)))), List.of(), List.of(targetC),
            Map.of(), Map.of(targetC, LiquidConstraint.only(water)), List.of(unsupported), List.of(interruption),
            List.of(), List.of(), List.of(), List.of(water));

        LiquidTrace trace = LiquidTraceAnalyzer.input(area(topology, entry(producer, producer(water)),
            entry(target, consumer(water))), target, water);

        assertEquals(List.of(producer), trace.producers().stream().map(endpoint -> endpoint.building).toList());
        assertEquals(List.of(dead), trace.structuralDeadEnds);
        assertEquals(List.of(targetC), trace.boundaryContinuations);
        assertEquals(List.of(interruption), trace.unsupportedInterruptions);
        assertFalse(trace.complete);
        assertFalse(trace.noRouteProven);
    }

    @Test
    void boundaryAndUnsupportedTerminationsAreNotDuplicatedAsDeadEnds(){
        BuildingRef target = ref("consumer", 1), pipe = ref("conduit", 2), unknown = ref("armored-conduit", 3);
        NetworkPort targetIn = port(target, NetworkSide.west, "in");
        NetworkPort targetOtherIn = port(target, NetworkSide.north, "in");
        NetworkPort pipeOut = port(pipe, NetworkSide.east, "out");
        NetworkPort pipeIn = port(pipe, NetworkSide.west, "in");
        NetworkPort interruptedPipeOut = port(ref("conduit-interrupted", 4), NetworkSide.east, "out");
        LiquidInterruption interruption = new LiquidInterruption(interruptedPipeOut, unknown,
            LiquidInterruption.Direction.incoming, LiquidConstraint.only(water));
        LiquidNetwork topology = new LiquidNetwork(graph(List.of(targetIn, targetOtherIn, pipeOut, pipeIn, interruptedPipeOut),
            List.of(edge(pipeOut, targetIn, LiquidConstraint.only(water)),
                edge(pipeIn, pipeOut, LiquidConstraint.any()),
                edge(interruptedPipeOut, targetOtherIn, LiquidConstraint.only(water)))), List.of(), List.of(pipeIn),
            Map.of(), Map.of(pipeIn, LiquidConstraint.only(water)), List.of(unknown), List.of(interruption),
            List.of(), List.of(), List.of(), List.of(water));

        LiquidTrace trace = LiquidTraceAnalyzer.input(area(topology, entry(target, consumer(water))), target, water);

        assertEquals(List.of(pipeIn), trace.boundaryContinuations);
        assertEquals(List.of(interruption), trace.unsupportedInterruptions);
        assertTrue(trace.structuralDeadEnds.isEmpty());
    }

    @Test
    void outputBoundaryIsAnOutsideContinuationNotAnOutputFailure(){
        BuildingRef target = ref("pump", 1);
        NetworkPort source = port(target, NetworkSide.east, "out");
        LiquidNetwork network = new LiquidNetwork(graph(List.of(source), List.of()), List.of(source), List.of(),
            Map.of(source, LiquidConstraint.only(water)), Map.of(), List.of(), List.of(), List.of(), List.of(),
            List.of(), List.of(water));

        LiquidTrace trace = LiquidTraceAnalyzer.output(area(network, entry(target, producer(water))), target, water);

        assertFalse(trace.complete, "the selected-area trace cannot conclude what lies beyond its boundary");
        assertFalse(trace.noRouteProven);
        assertEquals(List.of(source), trace.boundaryContinuations);
        assertTrue(trace.endpoints.isEmpty(), "outside consumers are not recursively inspected");
    }

    @Test
    void storageIsNotPromotedToProducerAndTargetIsExcludedFromItsOwnCycle(){
        BuildingRef tank = ref("liquid-tank", 1), target = ref("crafter", 2);
        NetworkPort tankOut = port(tank, NetworkSide.east, "out"), sink = port(target, NetworkSide.west, "in");
        LiquidNetwork tankNetwork = new LiquidNetwork(graph(List.of(tankOut, sink),
            List.of(edge(tankOut, sink, LiquidConstraint.only(water)))), List.of(), List.of(), Map.of(), Map.of(),
            List.of(), List.of(), List.of(tank), List.of(), List.of(), List.of(water));
        FactorySnapshot storedTank = FactorySnapshot.builder("Liquid Tank")
            .storedLiquid(new StoredLiquidState(water, 50f, 100f)).build();
        LiquidTrace fromStorage = LiquidTraceAnalyzer.input(area(tankNetwork, entry(tank, storedTank),
            entry(target, consumer(water))), target, water);

        assertTrue(fromStorage.producers().isEmpty());
        assertEquals(TraceEndpointKind.storage, fromStorage.endpoints.get(0).kind);

        BuildingRef self = ref("modded-processor", 3);
        NetworkPort selfOut = port(self, NetworkSide.east, "out"), selfIn = port(self, NetworkSide.west, "in");
        LiquidNetwork cycle = network(List.of(selfOut, selfIn), List.of(edge(selfOut, selfIn, LiquidConstraint.only(water))));
        FactorySnapshot both = FactorySnapshot.builder("Modded Processor").producedLiquid(water)
            .input(consumer(water).inputs.get(0)).build();
        LiquidTrace selfTrace = LiquidTraceAnalyzer.input(area(cycle, entry(self, both)), self, water);

        assertTrue(selfTrace.producers().isEmpty(), "the target cannot become its own upstream endpoint");
        assertFalse(selfTrace.noRouteProven);
    }

    @Test
    void producerAndDeadEndBranchCoexistAndCyclesTerminate(){
        BuildingRef pump = ref("pump", 1), router = ref("liquid-router", 2), deadConduit = ref("conduit", 3), target = ref("crafter", 4);
        NetworkPort source = port(pump, NetworkSide.west, "out");
        NetworkPort routerProducerIn = port(router, NetworkSide.west, "in"), routerDeadIn = port(router, NetworkSide.south, "in");
        NetworkPort routerOut = port(router, NetworkSide.east, "out"), targetIn = port(target, NetworkSide.west, "in");
        NetworkPort deadIn = port(deadConduit, NetworkSide.south, "in"), deadOut = port(deadConduit, NetworkSide.north, "out");
        List<NetworkPort> ports = List.of(source, routerProducerIn, routerDeadIn, routerOut, targetIn, deadIn, deadOut);
        List<LiquidNetworkEdge> edges = List.of(edge(source, routerProducerIn, LiquidConstraint.only(water)),
            edge(routerProducerIn, routerOut, LiquidConstraint.any()), edge(routerDeadIn, routerOut, LiquidConstraint.any()),
            edge(routerOut, targetIn, LiquidConstraint.only(water)), edge(deadIn, deadOut, LiquidConstraint.any()),
            edge(deadOut, routerDeadIn, LiquidConstraint.only(water)), edge(routerOut, routerProducerIn, LiquidConstraint.only(water)));
        LiquidTrace trace = LiquidTraceAnalyzer.input(area(network(ports, edges),
            entry(pump, producer(water)), entry(target, consumer(water))), target, water);

        assertEquals(List.of(pump), trace.producers().stream().map(endpoint -> endpoint.building).toList());
        assertEquals(List.of(deadConduit), trace.structuralDeadEnds);
        assertTrue(trace.traversedEdges.size() >= 6);
    }

    @Test
    void manyProducersOnASharedTrunkKeepOneLazyRepresentativePathEach(){
        BuildingRef router = ref("liquid-router", 1), target = ref("consumer", 2);
        NetworkPort routerIn = port(router, NetworkSide.west, "in"), routerOut = port(router, NetworkSide.east, "out");
        NetworkPort targetIn = port(target, NetworkSide.west, "in");
        List<NetworkPort> ports = new ArrayList<>(List.of(routerIn, routerOut, targetIn));
        List<LiquidNetworkEdge> edges = new ArrayList<>();
        edges.add(edge(routerIn, routerOut, LiquidConstraint.any()));
        edges.add(edge(routerOut, targetIn, LiquidConstraint.only(water)));
        List<AreaEntry> entries = new ArrayList<>();
        entries.add(entry(target, consumer(water)));
        for(int i = 0; i < 200; i++){
            BuildingRef producer = ref("producer-" + i, 10 + i);
            NetworkPort output = port(producer, NetworkSide.east, "out");
            ports.add(output);
            edges.add(edge(output, routerIn, LiquidConstraint.only(water)));
            entries.add(entry(producer, producer(water)));
        }

        LiquidTrace trace = LiquidTraceAnalyzer.input(area(network(ports, edges), entries.toArray(AreaEntry[]::new)), target, water);

        assertEquals(200, trace.producers().size());
        assertTrue(trace.producers().stream().allMatch(endpoint -> endpoint.path.edges().size() == 3));
    }

    @Test
    void multipleRoutesToOneProducerYieldOneStableRepresentativePath(){
        BuildingRef producer = ref("pump", 1), target = ref("consumer", 2);
        NetworkPort sourceWest = port(producer, NetworkSide.west, "out");
        NetworkPort sourceNorth = port(producer, NetworkSide.north, "out");
        NetworkPort routeAWest = port(ref("conduit-a", 3), NetworkSide.west, "in");
        NetworkPort routeAEast = port(routeAWest.building, NetworkSide.east, "out");
        NetworkPort routeBWest = port(ref("conduit-b", 4), NetworkSide.west, "in");
        NetworkPort routeBEast = port(routeBWest.building, NetworkSide.east, "out");
        NetworkPort targetWest = port(target, NetworkSide.west, "in");
        List<NetworkPort> ports = List.of(sourceWest, sourceNorth, routeAWest, routeAEast,
            routeBWest, routeBEast, targetWest);
        List<LiquidNetworkEdge> routeA = List.of(
            edge(sourceWest, routeAWest, LiquidConstraint.only(water)),
            edge(routeAWest, routeAEast, LiquidConstraint.any()),
            edge(routeAEast, targetWest, LiquidConstraint.only(water)));
        List<LiquidNetworkEdge> routeB = List.of(
            edge(sourceNorth, routeBWest, LiquidConstraint.only(water)),
            edge(routeBWest, routeBEast, LiquidConstraint.any()),
            edge(routeBEast, targetWest, LiquidConstraint.only(water)));

        List<LiquidNetworkEdge> forwardInsertion = new ArrayList<>(routeA);
        forwardInsertion.addAll(routeB);
        LiquidTrace first = LiquidTraceAnalyzer.input(area(network(ports, forwardInsertion),
            entry(producer, producer(water)), entry(target, consumer(water))), target, water);
        List<LiquidNetworkEdge> reversedInsertion = new ArrayList<>(routeB);
        reversedInsertion.addAll(routeA);
        LiquidTrace second = LiquidTraceAnalyzer.input(area(network(ports, reversedInsertion),
            entry(producer, producer(water)), entry(target, consumer(water))), target, water);

        assertEquals(1, first.producers().size(), "the same producer is one endpoint despite multiple routes");
        assertEquals(first.producers().get(0).path.edges(), second.producers().get(0).path.edges(),
            "the representative path is deterministic and independent of insertion order");
        assertEquals(3, first.producers().get(0).path.edges().size());
    }

    @Test
    void filterConsumerCanTraceAnyExplicitlyAcceptedLiquid(){
        BuildingRef pump = ref("pump", 1), target = ref("coolant-consumer", 2);
        NetworkPort source = port(pump, NetworkSide.east, "out"), sink = port(target, NetworkSide.west, "in");
        LiquidNetwork graph = network(List.of(source, sink), List.of(edge(source, sink, LiquidConstraint.only(water))));
        ResourceState filter = ResourceState.of(ResourceKind.liquid, "Coolant")
            .accepts(List.of(water, oil)).satisfaction(0f).build();
        FactorySnapshot targetSnapshot = FactorySnapshot.builder("coolant-consumer").input(filter).build();

        LiquidTrace trace = LiquidTraceAnalyzer.input(area(graph, entry(pump, producer(water)), entry(target, targetSnapshot)), target, water);

        assertTrue(trace.targetUsesLiquid);
        assertEquals(List.of(pump), trace.producers().stream().map(endpoint -> endpoint.building).toList());
        assertFalse(LiquidTraceAnalyzer.input(area(graph, entry(pump, producer(water)), entry(target, targetSnapshot)),
            target, liquid("cryofluid", "Cryofluid")).targetUsesLiquid);
    }

    @Test
    void incompleteConsumerMetadataPreventsFalseOutputDeadEnd(){
        BuildingRef source = ref("crafter", 1), unknownConsumer = ref("dynamic-consumer", 2);
        NetworkPort sourceOut = port(source, NetworkSide.east, "out");
        LiquidUncertainty uncertainty = new LiquidUncertainty(sourceOut, unknownConsumer,
            LiquidUncertainty.Direction.outgoing, LiquidUncertainty.Kind.incompleteRequirement,
            LiquidConstraint.only(water));
        LiquidNetwork graph = new LiquidNetwork(graph(List.of(sourceOut), List.of()), List.of(), List.of(),
            Map.of(), Map.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(uncertainty),
            List.of(water));

        LiquidTrace trace = LiquidTraceAnalyzer.output(area(graph, entry(source, producer(water))), source, water);

        assertFalse(trace.complete);
        assertFalse(trace.noRouteProven);
        assertTrue(trace.structuralDeadEnds.isEmpty());
        assertEquals(List.of(uncertainty), trace.incompleteConnections);
    }

    @Test
    void incompleteProducerMetadataPreventsFalseInputDeadEnd(){
        BuildingRef target = ref("crafter", 1), unknownProducer = ref("dynamic-producer", 2);
        NetworkPort targetIn = port(target, NetworkSide.west, "in");
        LiquidUncertainty uncertainty = new LiquidUncertainty(targetIn, unknownProducer,
            LiquidUncertainty.Direction.incoming, LiquidUncertainty.Kind.incompleteProduct,
            LiquidConstraint.only(water));
        LiquidNetwork graph = new LiquidNetwork(graph(List.of(targetIn), List.of()), List.of(), List.of(),
            Map.of(), Map.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(uncertainty),
            List.of(water));

        LiquidTrace trace = LiquidTraceAnalyzer.input(area(graph, entry(target, consumer(water))), target, water);

        assertFalse(trace.complete);
        assertFalse(trace.noRouteProven);
        assertTrue(trace.structuralDeadEnds.isEmpty());
        assertEquals(List.of(uncertainty), trace.incompleteConnections);
    }

    @Test
    void unidentifiedTargetRequirementIsNotReportedAsNotAConsumer(){
        BuildingRef target = ref("dynamic-consumer", 1);
        FactorySnapshot snapshot = FactorySnapshot.builder("Dynamic Consumer").liquidInputsComplete(false).build();

        LiquidTrace trace = LiquidTraceAnalyzer.input(area(network(List.of(), List.of()), entry(target, snapshot)), target, water);

        assertFalse(trace.targetUsesLiquid);
        assertTrue(trace.requirementsIncomplete);
        assertFalse(trace.complete);
        assertFalse(trace.noRouteProven);
        assertTrue(trace.structuralDeadEnds.isEmpty());
    }

    @Test
    void targetOutsideSelectedSnapshotIsDistinctFromIncompleteRequirement(){
        BuildingRef target = ref("crafter", 50);
        LiquidTrace trace = LiquidTraceAnalyzer.input(area(network(List.of(), List.of())), target, water);

        assertFalse(trace.targetIncluded);
        assertFalse(trace.targetUsesLiquid);
        assertFalse(trace.requirementsIncomplete);
        assertFalse(trace.noRouteProven);
    }

    @Test
    void unsupportedTargetIsNamedInsteadOfPresentedAsADeadEnd(){
        BuildingRef target = ref("unknown-liquid-transport", 1);
        LiquidNetwork network = new LiquidNetwork(graph(List.of(), List.of()), List.of(), List.of(),
            Map.of(), Map.of(), List.of(target), List.of(), List.of(), List.of(), List.of(), List.of(water));
        FactorySnapshot consumer = consumer(water);

        LiquidTrace trace = LiquidTraceAnalyzer.input(area(network, entry(target, consumer)), target, water);

        assertTrue(trace.targetUsesLiquid);
        assertFalse(trace.complete);
        assertFalse(trace.noRouteProven);
        assertEquals(List.of(target), trace.unsupportedTransports);
        assertTrue(trace.structuralDeadEnds.isEmpty());
    }

    @Test
    void outputTraceFindsConsumerWithoutCallingItCurrentSupply(){
        BuildingRef pump = ref("pump", 1), target = ref("crafter", 2);
        NetworkPort source = port(target, NetworkSide.east, "out"), sink = port(pump, NetworkSide.west, "in");
        LiquidNetwork graph = network(List.of(source, sink), List.of(edge(source, sink, LiquidConstraint.only(water))));

        LiquidTrace trace = LiquidTraceAnalyzer.output(area(graph, entry(target, producer(water)), entry(pump, consumer(water))), target, water);

        assertEquals(TraceDirection.output, trace.direction);
        assertEquals(List.of(pump), trace.endpoints.stream().map(endpoint -> endpoint.building).toList());
        assertEquals(TraceEndpointKind.consumer, trace.endpoints.get(0).kind);
    }

    @Test
    void zeroRateLiquidDeclarationIsNotAProducer(){
        BuildingRef target = ref("zero-output-crafter", 1);
        NetworkPort output = port(target, NetworkSide.east, "out");
        FactorySnapshot snapshot = FactorySnapshot.builder("Zero Output Crafter")
            .output(new OutputState(ResourceKind.liquid, water.name, water.id, 0f, 0f, 0f, 10f, false))
            .build();
        LiquidTrace trace = LiquidTraceAnalyzer.output(area(network(List.of(output), List.of()), entry(target, snapshot)),
            target, water);

        assertTrue(snapshot.producedLiquids.isEmpty());
        assertTrue(trace.targetIncluded);
        assertFalse(trace.targetUsesLiquid);
        assertFalse(trace.noRouteProven);
    }

    @Test
    void unsupportedBranchForAnotherLiquidDoesNotPoisonThisTrace(){
        BuildingRef target = ref("dual-output-crafter", 1), unknown = ref("unknown-liquid-transport", 2);
        NetworkPort waterOut = port(target, NetworkSide.east, "out");
        LiquidInterruption oilInterruption = new LiquidInterruption(waterOut, unknown,
            LiquidInterruption.Direction.outgoing, LiquidConstraint.only(oil));
        LiquidNetwork graph = new LiquidNetwork(graph(List.of(waterOut), List.of()), List.of(), List.of(),
            Map.of(), Map.of(), List.of(unknown), List.of(oilInterruption), List.of(), List.of(), List.of(),
            List.of(water, oil));
        FactorySnapshot snapshot = FactorySnapshot.builder("Dual Output")
            .producedLiquid(water).producedLiquid(oil).build();

        LiquidTrace waterTrace = LiquidTraceAnalyzer.output(area(graph, entry(target, snapshot)), target, water);
        LiquidTrace oilTrace = LiquidTraceAnalyzer.output(area(graph, entry(target, snapshot)), target, oil);

        assertTrue(waterTrace.complete, "the unsupported branch is constrained to Oil");
        assertTrue(waterTrace.unsupportedInterruptions.isEmpty());
        assertTrue(oilTrace.topologyIncomplete);
        assertEquals(List.of(oilInterruption), oilTrace.unsupportedInterruptions);
        assertFalse(oilTrace.noRouteProven);
    }

    @Test
    void disconnectedUnsupportedTransportForSameLiquidDoesNotPoisonThisTrace(){
        BuildingRef pump = ref("pump", 1), target = ref("crafter", 2), unknown = ref("armored-conduit", 20);
        NetworkPort source = port(pump, NetworkSide.east, "out");
        NetworkPort sink = port(target, NetworkSide.west, "in");
        NetworkPort unrelated = port(unknown, NetworkSide.west, "in");
        LiquidInterruption interruption = new LiquidInterruption(unrelated, unknown,
            LiquidInterruption.Direction.incoming, LiquidConstraint.only(water));
        LiquidNetwork graph = new LiquidNetwork(graph(List.of(source, sink, unrelated),
            List.of(edge(source, sink, LiquidConstraint.only(water)))), List.of(), List.of(), Map.of(), Map.of(),
            List.of(unknown), List.of(interruption), List.of(), List.of(), List.of(), List.of(water));

        LiquidTrace trace = LiquidTraceAnalyzer.input(area(graph, entry(pump, producer(water)),
            entry(target, consumer(water))), target, water);

        assertTrue(trace.complete, "an unsupported branch that is disconnected from the traced subgraph is irrelevant");
        assertTrue(trace.unsupportedInterruptions.isEmpty());
        assertEquals(List.of(pump), trace.producers().stream().map(endpoint -> endpoint.building).toList());
    }

    private static AreaDiagnosticResult area(LiquidNetwork network, AreaEntry... entries){
        AreaDiagnosticResult area = AreaAnalyzer.analyze(selection, entries.length, List.of(entries));
        return area.withLiquids(network);
    }

    private static AreaEntry entry(BuildingRef ref, FactorySnapshot snapshot){
        return new AreaEntry(ref, snapshot, FactoryAnalyzer.analyze(snapshot));
    }

    private static FactorySnapshot producer(ResourceRef liquid){
        return FactorySnapshot.builder("producer").producedLiquid(liquid).build();
    }

    private static FactorySnapshot consumer(ResourceRef liquid){
        return FactorySnapshot.builder("consumer").input(ResourceState.of(ResourceKind.liquid, liquid.name)
            .contentId(liquid.id).satisfaction(0f).build()).build();
    }

    private static LiquidNetwork network(Collection<NetworkPort> ports, Collection<LiquidNetworkEdge> edges){
        return new LiquidNetwork(graph(ports, edges), List.of(), List.of(), Map.of(), Map.of(), List.of(), List.of(),
            List.of(), List.of(), List.of(), List.of(water, oil));
    }

    private static LiquidNetworkGraph graph(Collection<NetworkPort> ports, Collection<LiquidNetworkEdge> edges){
        return new LiquidNetworkGraph(ports, edges);
    }

    private static LiquidNetworkEdge edge(NetworkPort from, NetworkPort to, LiquidConstraint liquid){
        return new LiquidNetworkEdge(from, to, liquid);
    }

    private static NetworkPort port(BuildingRef ref, NetworkSide side, String channel){
        return new NetworkPort(ref, side, channel);
    }

    private static BuildingRef ref(String name, int x){
        return new BuildingRef(x, 3, name, name, 1, 1);
    }

    private static ResourceRef liquid(String id, String name){
        return new ResourceRef(ResourceKind.liquid, id, name);
    }
}
