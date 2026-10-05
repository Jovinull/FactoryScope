package factoryscope.trace;

import factoryscope.analysis.*;
import factoryscope.area.*;
import factoryscope.model.*;
import factoryscope.network.*;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class TraceAnalyzerTest{
    private static final AreaSelection AREA = AreaSelection.of(0, 0, 40, 40);
    private static final ResourceRef sand = item("sand", "Sand");
    private static final ResourceRef copper = item("copper", "Copper");
    private static final ResourceRef lead = item("lead", "Lead");

    @Test
    void inputTraceFindsProducerAndOneShortestStructuralPath(){
        BuildingRef producer = ref("drill", 1);
        BuildingRef belt = ref("conveyor", 2);
        BuildingRef target = ref("silicon-smelter", 3);
        NetworkPort producerOut = port(producer, NetworkSide.east, "out");
        NetworkPort beltIn = port(belt, NetworkSide.west, "in");
        NetworkPort beltOut = port(belt, NetworkSide.east, "out");
        NetworkPort targetIn = port(target, NetworkSide.west, "in");
        NetworkGraph graph = graph(List.of(producerOut, beltIn, beltOut, targetIn), List.of(
            edge(producerOut, beltIn, ItemConstraint.only(sand)),
            edge(beltIn, beltOut, ItemConstraint.any()),
            edge(beltOut, targetIn, ItemConstraint.any())));

        SupplyTrace trace = input(network(graph), target, sand, List.of(
            producer(producer, sand, DiagnosticReason.active), consumer(target, sand)));

        assertTrue(trace.complete);
        assertFalse(trace.noRouteProven);
        assertEquals(List.of(producer), trace.producers().stream().map(endpoint -> endpoint.building).toList());
        TracePath path = trace.producers().get(0).path;
        assertEquals(List.of(producerOut, beltIn, beltOut, targetIn), path.ports());
        assertEquals(3, path.edges().size());
        assertFalse(path.conditional());
    }

    @Test
    void inputTraceReturnsSeveralUniqueProducerPaths(){
        BuildingRef first = ref("drill", 1), second = ref("drill", 2), router = ref("router", 3), target = ref("smelter", 4);
        NetworkPort firstOut = port(first, NetworkSide.east, "out");
        NetworkPort secondOut = port(second, NetworkSide.east, "out");
        NetworkPort routerIn = port(router, NetworkSide.west, "in");
        NetworkPort routerOut = port(router, NetworkSide.east, "out");
        NetworkPort targetIn = port(target, NetworkSide.west, "in");
        NetworkGraph graph = graph(List.of(firstOut, secondOut, routerIn, routerOut, targetIn), List.of(
            edge(firstOut, routerIn, ItemConstraint.any()), edge(secondOut, routerIn, ItemConstraint.any()),
            edge(routerIn, routerOut, ItemConstraint.any()), edge(routerOut, targetIn, ItemConstraint.any())));

        SupplyTrace trace = input(network(graph), target, sand, List.of(
            producer(first, sand, DiagnosticReason.active), producer(second, sand, DiagnosticReason.disabled),
            consumer(target, sand)));

        assertEquals(List.of(first, second), trace.producers().stream().map(endpoint -> endpoint.building).toList());
        assertEquals(List.of(firstOut, routerIn, routerOut, targetIn), trace.producers().get(0).path.ports());
        assertEquals(List.of(secondOut, routerIn, routerOut, targetIn), trace.producers().get(1).path.ports());
        assertEquals(1, trace.findings.size());
        assertEquals(NetworkFinding.Kind.reachableProducerDisabled, trace.findings.get(0).kind);
        assertEquals(NetworkFinding.Certainty.informational, trace.findings.get(0).certainty);
        assertEquals(second, trace.findings.get(0).building);
    }

    @Test
    void severalRoutesToOneProducerCountTheBuildingOnceAndChooseDeterministically(){
        BuildingRef producer = ref("drill", 1), left = ref("conveyor", 2), right = ref("conveyor", 3), target = ref("smelter", 4);
        NetworkPort p = port(producer, NetworkSide.east, "out"), pNorth = port(producer, NetworkSide.north, "out"),
            li = port(left, NetworkSide.west, "in"),
            lo = port(left, NetworkSide.east, "out"), ri = port(right, NetworkSide.west, "in"),
            ro = port(right, NetworkSide.east, "out"), ti = port(target, NetworkSide.west, "in");
        List<NetworkEdge> edges = List.of(
            edge(p, li, ItemConstraint.any()), edge(li, lo, ItemConstraint.any()), edge(lo, ti, ItemConstraint.any()),
            edge(pNorth, ri, ItemConstraint.any()), edge(ri, ro, ItemConstraint.any()), edge(ro, ti, ItemConstraint.any()));
        SupplyTrace trace = input(network(graph(List.of(p, pNorth, li, lo, ri, ro, ti), edges)), target, sand,
            List.of(producer(producer, sand, DiagnosticReason.active), consumer(target, sand)));

        assertEquals(1, trace.producers().size());
        assertEquals(List.of(p, li, lo, ti), trace.producers().get(0).path.ports(),
            "stable graph ordering chooses the same shortest path on every run");
    }

    @Test
    void representativePathUsesTheShortestReachableProducerPort(){
        BuildingRef producer = ref("drill", 1), longA = ref("conveyor", 2), longB = ref("conveyor", 3),
            shortBelt = ref("conveyor", 4), target = ref("smelter", 5);
        NetworkPort producerEast = port(producer, NetworkSide.east, "out");
        NetworkPort producerNorth = port(producer, NetworkSide.north, "out");
        NetworkPort longAIn = port(longA, NetworkSide.west, "in"), longAOut = port(longA, NetworkSide.east, "out");
        NetworkPort longBIn = port(longB, NetworkSide.west, "in"), longBOut = port(longB, NetworkSide.east, "out");
        NetworkPort shortIn = port(shortBelt, NetworkSide.west, "in"), shortOut = port(shortBelt, NetworkSide.east, "out");
        NetworkPort targetIn = port(target, NetworkSide.west, "in");
        NetworkGraph graph = graph(List.of(producerEast, producerNorth, longAIn, longAOut, longBIn, longBOut,
            shortIn, shortOut, targetIn), List.of(
            edge(producerEast, longAIn, ItemConstraint.any()), edge(longAIn, longAOut, ItemConstraint.any()),
            edge(longAOut, longBIn, ItemConstraint.any()), edge(longBIn, longBOut, ItemConstraint.any()),
            edge(longBOut, targetIn, ItemConstraint.any()), edge(producerNorth, shortIn, ItemConstraint.any()),
            edge(shortIn, shortOut, ItemConstraint.any()), edge(shortOut, targetIn, ItemConstraint.any())));

        SupplyTrace trace = input(network(graph), target, sand, List.of(
            producer(producer, sand, DiagnosticReason.active), consumer(target, sand)));

        assertEquals(List.of(producerNorth, shortIn, shortOut, targetIn), trace.producers().get(0).path.ports());
    }

    @Test
    void completeDisconnectedInputCanProveNoStructuralRoute(){
        BuildingRef target = ref("smelter", 1);
        SupplyTrace trace = input(network(graph(List.of(port(target, NetworkSide.north, "in")), List.of())),
            target, sand, List.of(consumer(target, sand)));

        assertTrue(trace.complete);
        assertTrue(trace.noRouteProven);
        assertTrue(trace.findings.stream().anyMatch(f -> f.kind == NetworkFinding.Kind.noStructuralInputRoute
            && f.certainty == NetworkFinding.Certainty.proven));
    }

    @Test
    void aCompleteRouteCanIdentifyItsTerminalTransportAsADeadEnd(){
        BuildingRef belt = ref("conveyor", 2), target = ref("smelter", 3);
        NetworkPort beltIn = port(belt, NetworkSide.west, "in"), beltOut = port(belt, NetworkSide.east, "out");
        NetworkPort targetIn = port(target, NetworkSide.west, "in");
        NetworkGraph graph = graph(List.of(beltIn, beltOut, targetIn), List.of(
            edge(beltIn, beltOut, ItemConstraint.any()), edge(beltOut, targetIn, ItemConstraint.any())));

        SupplyTrace trace = input(network(graph), target, sand, List.of(consumer(target, sand)));

        assertFalse(trace.noRouteProven, "the target reaches a known transport branch even though no source endpoint is reachable");
        assertEquals(List.of(belt), trace.structuralDeadEnds);
        assertTrue(trace.findings.stream().anyMatch(f -> f.kind == NetworkFinding.Kind.noReachableInAreaProducer
            && f.certainty == NetworkFinding.Certainty.proven));
        assertFalse(trace.findings.stream().anyMatch(f -> f.kind == NetworkFinding.Kind.noStructuralInputRoute));
        assertTrue(trace.findings.stream().anyMatch(f -> f.kind == NetworkFinding.Kind.structuralDeadEnd
            && f.certainty == NetworkFinding.Certainty.proven && belt.equals(f.building)));
    }

    @Test
    void aReachableProducerDoesNotHideASeparateDeadEndBranch(){
        BuildingRef producer = ref("drill", 1), producerBelt = ref("conveyor", 2),
            deadBelt = ref("conveyor", 3), merge = ref("router", 4), target = ref("smelter", 5);
        NetworkPort producerOut = port(producer, NetworkSide.east, "out");
        NetworkPort producerIn = port(producerBelt, NetworkSide.west, "in");
        NetworkPort producerOutPort = port(producerBelt, NetworkSide.east, "out");
        NetworkPort deadIn = port(deadBelt, NetworkSide.west, "in");
        NetworkPort deadOut = port(deadBelt, NetworkSide.east, "out");
        NetworkPort mergeWest = port(merge, NetworkSide.west, "in");
        NetworkPort mergeSouth = port(merge, NetworkSide.south, "in");
        NetworkPort mergeEast = port(merge, NetworkSide.east, "out");
        NetworkPort targetIn = port(target, NetworkSide.west, "in");
        NetworkGraph graph = graph(List.of(producerOut, producerIn, producerOutPort, deadIn, deadOut,
            mergeWest, mergeSouth, mergeEast, targetIn), List.of(
            edge(producerOut, producerIn, ItemConstraint.any()), edge(producerIn, producerOutPort, ItemConstraint.any()),
            edge(producerOutPort, mergeWest, ItemConstraint.any()), edge(deadIn, deadOut, ItemConstraint.any()),
            edge(deadOut, mergeSouth, ItemConstraint.any()), edge(mergeWest, mergeEast, ItemConstraint.any()),
            edge(mergeSouth, mergeEast, ItemConstraint.any()), edge(mergeEast, targetIn, ItemConstraint.any())));

        SupplyTrace trace = input(network(graph), target, sand, List.of(
            producer(producer, sand, DiagnosticReason.active), consumer(target, sand)));

        assertEquals(List.of(producer), trace.producers().stream().map(endpoint -> endpoint.building).toList());
        assertEquals(List.of(deadBelt), trace.structuralDeadEnds);
        assertEquals(new TreeSet<>(graph.edges), new TreeSet<>(trace.traversedEdges),
            "the explored trace region includes the known dead-end branch as well as the producer path");
    }

    @Test
    void producersAndMultipleDeadEndsSurviveASharedBranchPoint(){
        BuildingRef first = ref("drill", 1), second = ref("drill", 2), producerBeltA = ref("conveyor", 3),
            producerBeltB = ref("conveyor", 4), deadBeltA = ref("conveyor", 5), deadBeltB = ref("conveyor", 6),
            router = ref("router", 7), target = ref("smelter", 8);
        NetworkPort firstOut = port(first, NetworkSide.east, "out"), secondOut = port(second, NetworkSide.east, "out");
        NetworkPort paIn = port(producerBeltA, NetworkSide.west, "in"), paOut = port(producerBeltA, NetworkSide.east, "out");
        NetworkPort pbIn = port(producerBeltB, NetworkSide.west, "in"), pbOut = port(producerBeltB, NetworkSide.east, "out");
        NetworkPort daIn = port(deadBeltA, NetworkSide.west, "in"), daOut = port(deadBeltA, NetworkSide.east, "out");
        NetworkPort dbIn = port(deadBeltB, NetworkSide.west, "in"), dbOut = port(deadBeltB, NetworkSide.east, "out");
        NetworkPort rw = port(router, NetworkSide.west, "in"), rs = port(router, NetworkSide.south, "in"),
            rn = port(router, NetworkSide.north, "in"), re = port(router, NetworkSide.east, "in"),
            routerOut = port(router, NetworkSide.east, "out"), targetIn = port(target, NetworkSide.west, "in");
        NetworkGraph graph = graph(List.of(firstOut, secondOut, paIn, paOut, pbIn, pbOut, daIn, daOut,
            dbIn, dbOut, rw, rs, rn, re, routerOut, targetIn), List.of(
            edge(firstOut, paIn, ItemConstraint.any()), edge(paIn, paOut, ItemConstraint.any()), edge(paOut, rw, ItemConstraint.any()),
            edge(secondOut, pbIn, ItemConstraint.any()), edge(pbIn, pbOut, ItemConstraint.any()), edge(pbOut, rs, ItemConstraint.any()),
            edge(daIn, daOut, ItemConstraint.any()), edge(daOut, rn, ItemConstraint.any()),
            edge(dbIn, dbOut, ItemConstraint.any()), edge(dbOut, re, ItemConstraint.any()),
            edge(rw, routerOut, ItemConstraint.any()), edge(rs, routerOut, ItemConstraint.any()),
            edge(rn, routerOut, ItemConstraint.any()), edge(re, routerOut, ItemConstraint.any()),
            edge(routerOut, targetIn, ItemConstraint.any())));

        SupplyTrace trace = input(network(graph), target, sand, List.of(
            producer(first, sand, DiagnosticReason.active), producer(second, sand, DiagnosticReason.active),
            consumer(target, sand), limited(producerBeltA), limited(producerBeltB), limited(deadBeltA),
            limited(deadBeltB), limited(router)));

        assertEquals(List.of(first, second), trace.producers().stream().map(endpoint -> endpoint.building).toList());
        assertEquals(List.of(deadBeltA, deadBeltB), trace.structuralDeadEnds);
    }

    @Test
    void boundaryContinuationPreventsNoRouteConclusion(){
        BuildingRef target = ref("smelter", 1);
        NetworkPort input = port(target, NetworkSide.west, "in");
        ItemNetwork network = new ItemNetwork(graph(List.of(input), List.of()), List.of(), List.of(input),
            List.of(), List.of(), List.of(), List.of(sand));

        SupplyTrace trace = input(network, target, sand, List.of(consumer(target, sand)));

        assertFalse(trace.complete);
        assertFalse(trace.noRouteProven);
        assertEquals(List.of(input), trace.boundaryContinuations);
        assertTrue(trace.findings.stream().anyMatch(f -> f.kind == NetworkFinding.Kind.routeContinuesOutsideArea));
    }

    @Test
    void anIncomingBoundaryIsNotMisreportedAsAStructuralDeadEnd(){
        BuildingRef belt = ref("conveyor", 2), target = ref("smelter", 3);
        NetworkPort beltIn = port(belt, NetworkSide.west, "in");
        NetworkPort beltOut = port(belt, NetworkSide.east, "out");
        NetworkPort targetIn = port(target, NetworkSide.west, "in");
        ItemNetwork network = new ItemNetwork(graph(List.of(beltIn, beltOut, targetIn),
            List.of(edge(beltIn, beltOut, ItemConstraint.any()), edge(beltOut, targetIn, ItemConstraint.any()))),
            List.of(), List.of(beltIn),
            List.of(), List.of(), List.of(), List.of(sand));

        SupplyTrace trace = input(network, target, sand, List.of(consumer(target, sand)));

        assertEquals(List.of(beltIn), trace.boundaryContinuations);
        assertTrue(trace.structuralDeadEnds.isEmpty(), "a known outside continuation is not an in-area dead end");
        assertFalse(trace.noRouteProven);
    }

    @Test
    void unsupportedTransportPreventsNoRouteConclusion(){
        BuildingRef belt = ref("conveyor", 2), target = ref("smelter", 1), unknown = ref("armored-conveyor", 3);
        NetworkPort beltIn = port(belt, NetworkSide.west, "in"), beltOut = port(belt, NetworkSide.east, "out");
        NetworkPort targetIn = port(target, NetworkSide.west, "in");
        NetworkInterruption interruption = new NetworkInterruption(beltIn, unknown, NetworkInterruption.Direction.incoming);
        ItemNetwork network = new ItemNetwork(graph(List.of(beltIn, beltOut, targetIn), List.of(
                edge(beltIn, beltOut, ItemConstraint.any()), edge(beltOut, targetIn, ItemConstraint.any()))), List.of(), List.of(),
            List.of(unknown), List.of(interruption), List.of(), List.of(sand));

        SupplyTrace trace = input(network, target, sand, List.of(consumer(target, sand)));

        assertFalse(trace.complete);
        assertFalse(trace.noRouteProven);
        assertEquals(List.of(interruption), trace.unsupportedInterruptions);
        assertTrue(trace.structuralDeadEnds.isEmpty());
        assertTrue(trace.findings.stream().anyMatch(f -> f.kind == NetworkFinding.Kind.unsupportedTransport));
    }

    @Test
    void oneUnsupportedBuildingCreatesOneFindingEvenWhenSeveralPortsAreInterrupted(){
        BuildingRef target = ref("smelter", 1), unknown = ref("armored-conveyor", 2);
        NetworkPort west = port(target, NetworkSide.west, "in"), east = port(target, NetworkSide.east, "in");
        NetworkInterruption fromWest = new NetworkInterruption(west, unknown, NetworkInterruption.Direction.incoming);
        NetworkInterruption fromEast = new NetworkInterruption(east, unknown, NetworkInterruption.Direction.incoming);
        ItemNetwork network = new ItemNetwork(graph(List.of(west, east), List.of()), List.of(), List.of(), List.of(unknown),
            List.of(fromWest, fromEast), List.of(), List.of(sand));

        SupplyTrace trace = input(network, target, sand, List.of(consumer(target, sand)));

        assertEquals(2, trace.unsupportedInterruptions.size(), "both affected input ports remain visible");
        assertEquals(1, trace.findings.stream().filter(f -> f.kind == NetworkFinding.Kind.unsupportedTransport
            && unknown.equals(f.building)).count(), "the same unsupported building is one finding");
    }

    @Test
    void unrelatedUnsupportedTransportDoesNotInvalidateALocallyCompleteTrace(){
        BuildingRef belt = ref("conveyor", 2), target = ref("smelter", 3);
        BuildingRef unrelated = ref("armored-conveyor", 30);
        NetworkPort beltIn = port(belt, NetworkSide.west, "in"), beltOut = port(belt, NetworkSide.east, "out");
        NetworkPort targetIn = port(target, NetworkSide.west, "in");
        ItemNetwork network = new ItemNetwork(graph(List.of(beltIn, beltOut, targetIn), List.of(
                edge(beltIn, beltOut, ItemConstraint.any()), edge(beltOut, targetIn, ItemConstraint.any()))),
            List.of(), List.of(), List.of(unrelated), List.of(), List.of(), List.of(sand));

        SupplyTrace trace = input(network, target, sand, List.of(consumer(target, sand)));

        assertTrue(trace.complete, "an isolated, local-only unsupported block cannot affect this reachable subgraph");
        assertFalse(trace.noRouteProven);
        assertTrue(trace.unsupportedInArea.isEmpty());
        assertTrue(trace.structuralDeadEnds.contains(belt));
        assertTrue(trace.findings.stream().anyMatch(f -> f.kind == NetworkFinding.Kind.structuralDeadEnd
            && f.certainty == NetworkFinding.Certainty.proven && belt.equals(f.building)));
        assertFalse(trace.findings.stream().anyMatch(f -> f.kind == NetworkFinding.Kind.unsupportedTransport));
    }

    @Test
    void unrelatedSkippedDiagnosticsDoNotInvalidateACompleteSubgraph(){
        BuildingRef target = ref("smelter", 3), skipped = ref("modded-crafter", 30);
        NetworkPort targetIn = port(target, NetworkSide.west, "in");
        ItemNetwork network = network(graph(List.of(targetIn), List.of()));
        AreaDiagnosticResult area = AreaAnalyzer.analyze(AREA, 2, List.of(consumer(target, sand)))
            .withNetwork(network).withSkippedBuildings(List.of(skipped));

        SupplyTrace trace = TraceAnalyzer.input(area, target, sand);

        assertFalse(trace.diagnosticsIncomplete);
        assertTrue(trace.complete);
        assertTrue(trace.noRouteProven);
    }

    @Test
    void unlocatedSkippedDiagnosticsRemainConservative(){
        BuildingRef target = ref("smelter", 3);
        NetworkPort targetIn = port(target, NetworkSide.west, "in");
        AreaDiagnosticResult area = AreaAnalyzer.analyze(AREA, 2, List.of(consumer(target, sand)))
            .withNetwork(network(graph(List.of(targetIn), List.of())));

        SupplyTrace trace = TraceAnalyzer.input(area, target, sand);

        assertTrue(trace.diagnosticsIncomplete);
        assertFalse(trace.complete);
        assertFalse(trace.noRouteProven);
    }

    @Test
    void aSkippedBuildingAtATerminalPortPreventsADeadEndConclusion(){
        BuildingRef belt = ref("conveyor", 2), target = ref("smelter", 3), skipped = ref("modded-crafter", 1);
        NetworkPort beltIn = port(belt, NetworkSide.west, "in"), beltOut = port(belt, NetworkSide.east, "out");
        NetworkPort targetIn = port(target, NetworkSide.west, "in");
        NetworkGraph graph = graph(List.of(beltIn, beltOut, targetIn), List.of(
            edge(beltIn, beltOut, ItemConstraint.any()), edge(beltOut, targetIn, ItemConstraint.any())));
        AreaDiagnosticResult area = AreaAnalyzer.analyze(AREA, 3, List.of(consumer(target, sand), limited(belt)))
            .withNetwork(network(graph)).withSkippedBuildings(List.of(skipped));

        SupplyTrace trace = TraceAnalyzer.input(area, target, sand);

        assertTrue(trace.diagnosticsIncomplete);
        assertFalse(trace.complete);
        assertFalse(trace.noRouteProven);
        assertTrue(trace.structuralDeadEnds.isEmpty());
    }

    @Test
    void aSkippedEvenSizedBuildingUsesMindustrysAsymmetricFootprint(){
        BuildingRef target = new BuildingRef(10, 10, "smelter", "Smelter", 3, 1);
        BuildingRef skippedProducer = new BuildingRef(6, 10, "large-mod-crafter", "Large Mod Crafter", 4, 1);
        NetworkPort targetIn = port(target, NetworkSide.west, "in");
        AreaDiagnosticResult area = AreaAnalyzer.analyze(AREA, 2, List.of(consumer(target, sand)))
            .withNetwork(network(graph(List.of(targetIn), List.of())))
            .withSkippedBuildings(List.of(skippedProducer));

        SupplyTrace trace = TraceAnalyzer.input(area, target, sand);

        assertTrue(trace.diagnosticsIncomplete, "the skipped 4x4 building touches the target's western input");
        assertFalse(trace.complete);
        assertFalse(trace.noRouteProven);
    }

    @Test
    void skippedBuildingIndexChecksEveryTerminalSideAndPreservesTeamIsolation(){
        BuildingRef target = new BuildingRef(20, 20, "smelter", "Smelter", 3, 1);
        List<NetworkPort> inputs = List.of(
            port(target, NetworkSide.east, "in"), port(target, NetworkSide.north, "in"),
            port(target, NetworkSide.west, "in"), port(target, NetworkSide.south, "in"));
        List<BuildingRef> adjacent = List.of(ref("mod-east", 22, 20, 1), ref("mod-north", 20, 22, 1),
            ref("mod-west", 18, 20, 1), ref("mod-south", 20, 18, 1));
        AreaDiagnosticResult touchingArea = AreaAnalyzer.analyze(AREA, 5, List.of(consumer(target, sand)))
            .withNetwork(network(graph(inputs, List.of()))).withSkippedBuildings(adjacent);

        SupplyTrace touching = TraceAnalyzer.input(touchingArea, target, sand);

        assertTrue(touching.diagnosticsIncomplete, "same-team skipped buildings on each terminal side remain relevant");
        assertFalse(touching.complete);
        assertFalse(touching.noRouteProven);

        BuildingRef otherTeam = ref("enemy-mod", 18, 20, 2);
        AreaDiagnosticResult enemyArea = AreaAnalyzer.analyze(AREA, 2, List.of(consumer(target, sand)))
            .withNetwork(network(graph(inputs, List.of()))).withSkippedBuildings(List.of(otherTeam));
        SupplyTrace enemyOnly = TraceAnalyzer.input(enemyArea, target, sand);

        assertTrue(enemyOnly.complete, "a different team's adjacent skipped building is not evidence about this route");
        assertTrue(enemyOnly.noRouteProven);
    }

    @Test
    void deadEndClassificationUsesOnlySkippedBuildingsRelevantToTheTrace(){
        BuildingRef sourcePortBuilding = ref("conveyor", 1), target = ref("smelter", 10);
        BuildingRef skippedBesideSourceOutput = ref("modded-building", 2);
        NetworkPort sourceOut = port(sourcePortBuilding, NetworkSide.east, "out");
        NetworkPort targetIn = port(target, NetworkSide.west, "in");
        NetworkGraph graph = graph(List.of(sourceOut, targetIn), List.of(edge(sourceOut, targetIn, ItemConstraint.any())));
        AreaDiagnosticResult area = AreaAnalyzer.analyze(AREA, 2, List.of(consumer(target, sand)))
            .withNetwork(network(graph)).withSkippedBuildings(List.of(skippedBesideSourceOutput));

        SupplyTrace trace = TraceAnalyzer.input(area, target, sand);

        assertFalse(trace.diagnosticsIncomplete, "existing relevance policy only treats terminal input-side skips as trace uncertainty");
        assertEquals(List.of(sourcePortBuilding), trace.structuralDeadEnds,
            "dead-end classification must preserve the prior relevant-skipped subset semantics");
    }

    @Test
    void itemRequestsForResourcesUnusedByTheTargetProduceNoTraceEndpoints(){
        BuildingRef source = ref("drill", 1), target = ref("factory", 2), downstream = ref("factory", 3);
        NetworkPort sourceOut = port(source, NetworkSide.east, "out"), targetIn = port(target, NetworkSide.west, "in"),
            targetOut = port(target, NetworkSide.east, "out"), downstreamIn = port(downstream, NetworkSide.west, "in");
        NetworkGraph graph = graph(List.of(sourceOut, targetIn, targetOut, downstreamIn), List.of(
            edge(sourceOut, targetIn, ItemConstraint.any()), edge(targetOut, downstreamIn, ItemConstraint.any())));
        AreaDiagnosticResult area = area(network(graph), List.of(
            producer(source, copper, DiagnosticReason.active), consumer(target, sand), consumer(downstream, lead)));

        SupplyTrace input = TraceAnalyzer.input(area, target, copper);
        SupplyTrace output = TraceAnalyzer.output(area, target, lead);

        assertFalse(input.targetUsesItem);
        assertTrue(input.endpoints.isEmpty());
        assertTrue(input.boundaryContinuations.isEmpty());
        assertFalse(input.noRouteProven);
        assertFalse(output.targetUsesItem);
        assertTrue(output.endpoints.isEmpty());
        assertTrue(output.boundaryContinuations.isEmpty());
        assertFalse(output.noRouteProven);
    }

    @Test
    void absentTargetSnapshotDiffersFromMissingModeledPorts(){
        BuildingRef target = ref("factory", 2);
        NetworkPort targetIn = port(target, NetworkSide.west, "in");
        ItemNetwork withPort = network(graph(List.of(targetIn), List.of()));
        AreaDiagnosticResult missingSnapshot = area(withPort, List.of(new AreaEntry(target, SupportLevel.minimal,
            new DiagnosticResult(List.of(Finding.of(DiagnosticReason.active, Severity.normal))))));
        FactorySnapshot snapshot = FactorySnapshot.builder(target.blockName).support(SupportLevel.full)
            .input(ResourceState.of(ResourceKind.item, sand.name).contentId(sand.id).build()).build();
        AreaDiagnosticResult missingPorts = area(network(graph(List.of(), List.of())), List.of(new AreaEntry(target,
            snapshot, new DiagnosticResult(List.of(Finding.of(DiagnosticReason.active, Severity.normal))))));

        SupplyTrace absentSnapshot = TraceAnalyzer.input(missingSnapshot, target, sand);
        SupplyTrace absentPorts = TraceAnalyzer.input(missingPorts, target, sand);

        assertTrue(absentSnapshot.diagnosticsIncomplete);
        assertFalse(absentSnapshot.topologyIncomplete);
        assertFalse(absentSnapshot.targetUsesItem);
        assertFalse(absentPorts.diagnosticsIncomplete);
        assertTrue(absentPorts.topologyIncomplete);
        assertTrue(absentPorts.targetUsesItem);
        assertFalse(absentSnapshot.noRouteProven);
        assertFalse(absentPorts.noRouteProven);
    }

    @Test
    void targetIsNotAnEndpointOrProofOfNoRouteWhenItsOutputLoopsBackToItsInput(){
        BuildingRef target = ref("modded-processor", 1), belt = ref("conveyor", 2);
        NetworkPort targetIn = port(target, NetworkSide.west, "in"), targetOut = port(target, NetworkSide.east, "out"),
            beltIn = port(belt, NetworkSide.west, "in"), beltOut = port(belt, NetworkSide.east, "out");
        NetworkGraph graph = graph(List.of(targetIn, targetOut, beltIn, beltOut), List.of(
            edge(targetOut, beltIn, ItemConstraint.any()), edge(beltIn, beltOut, ItemConstraint.any()),
            edge(beltOut, targetIn, ItemConstraint.any())));
        FactorySnapshot snapshot = FactorySnapshot.builder(target.blockName).support(SupportLevel.full)
            .enabled(true).shouldConsume(true).producedItem(sand)
            .input(ResourceState.of(ResourceKind.item, sand.name).contentId(sand.id).build()).build();
        AreaDiagnosticResult area = area(network(graph), List.of(new AreaEntry(target, snapshot,
            new DiagnosticResult(List.of(Finding.of(DiagnosticReason.active, Severity.normal)))), limited(belt)));

        SupplyTrace input = TraceAnalyzer.input(area, target, sand);
        SupplyTrace output = TraceAnalyzer.output(area, target, sand);

        assertTrue(input.endpoints.isEmpty());
        assertFalse(input.noRouteProven, "a self-returning cycle is not an external producer route or proof of disconnection");
        assertTrue(output.endpoints.isEmpty());
        assertFalse(output.noRouteProven, "the target's input is not a distinct downstream consumer");
    }

    @Test
    void storageIsASeparateEndpointAndNeverCountedAsAProducer(){
        BuildingRef storage = ref("vault", 1), target = ref("smelter", 2);
        NetworkPort storeOut = port(storage, NetworkSide.east, "out"), targetIn = port(target, NetworkSide.west, "in");
        ItemNetwork network = new ItemNetwork(graph(List.of(storeOut, targetIn),
            List.of(edge(storeOut, targetIn, ItemConstraint.any()))), List.of(), List.of(), List.of(), List.of(),
            List.of(storage), List.of(sand));
        AreaDiagnosticResult area = area(network, List.of(consumer(target, sand)));

        SupplyTrace trace = TraceAnalyzer.input(area, target, sand);

        assertEquals(0, trace.producers().size());
        assertEquals(1, trace.endpoints.size());
        assertEquals(TraceEndpointKind.storage, trace.endpoints.get(0).kind);
        assertFalse(trace.noRouteProven, "a known storage endpoint is not a production source");
    }

    @Test
    void sorterConstraintsAreAppliedToInputAndOutputTraces(){
        BuildingRef producer = ref("crafter", 1), leadProducer = ref("crafter", 0), sorter = ref("sorter", 2), target = ref("factory", 3);
        NetworkPort p = port(producer, NetworkSide.east, "out"), si = port(sorter, NetworkSide.west, "in"),
            leadOut = port(leadProducer, NetworkSide.east, "out"), so = port(sorter, NetworkSide.east, "out"),
            ti = port(target, NetworkSide.west, "in");
        NetworkGraph graph = graph(List.of(p, leadOut, si, so, ti), List.of(
            edge(p, si, ItemConstraint.only(copper)), edge(leadOut, si, ItemConstraint.only(copper)),
            edge(si, so, ItemConstraint.only(copper)),
            edge(so, ti, ItemConstraint.any())));
        List<AreaEntry> entries = List.of(producer(producer, copper, DiagnosticReason.active),
            producer(leadProducer, lead, DiagnosticReason.active), consumerWithItems(target, copper, lead));

        assertEquals(1, TraceAnalyzer.input(area(network(graph), entries), target, copper).producers().size());
        assertEquals(List.of(target), TraceAnalyzer.output(area(network(graph), entries), producer, copper).endpoints.stream()
            .map(endpoint -> endpoint.building).toList());
        assertTrue(TraceAnalyzer.output(area(network(graph), entries), producer, lead).endpoints.isEmpty());
        SupplyTrace rejected = TraceAnalyzer.input(area(network(graph), List.of(
            producer(leadProducer, lead, DiagnosticReason.active), consumer(target, lead))), target, lead);
        assertTrue(rejected.producers().isEmpty());
        assertFalse(rejected.noRouteProven, "a route reaches the Sorter but no in-area source can use the configured item path");
        assertTrue(rejected.structuralDeadEnds.contains(sorter));
        assertTrue(rejected.findings.stream().anyMatch(f -> f.kind == NetworkFinding.Kind.noReachableInAreaProducer));
        assertFalse(rejected.findings.stream().anyMatch(f -> f.kind == NetworkFinding.Kind.noStructuralInputRoute));
    }

    @Test
    void representativePathRetainsConditionalGateMetadata(){
        BuildingRef producer = ref("drill", 1), gate = ref("overflow-gate", 2), target = ref("factory", 3);
        NetworkPort source = port(producer, NetworkSide.east, "out");
        NetworkPort gateIn = port(gate, NetworkSide.west, "in");
        NetworkPort gateOut = port(gate, NetworkSide.east, "out");
        NetworkPort targetIn = port(target, NetworkSide.west, "in");
        NetworkGraph graph = graph(List.of(source, gateIn, gateOut, targetIn), List.of(
            edge(source, gateIn, ItemConstraint.any()), edge(gateIn, gateOut, ItemConstraint.any()),
            new NetworkEdge(gateOut, targetIn, ItemConstraint.any(), true)));

        SupplyTrace trace = input(network(graph), target, sand, List.of(
            producer(producer, sand, DiagnosticReason.active), consumer(target, sand)));

        assertEquals(List.of(source, gateIn, gateOut, targetIn), trace.producers().get(0).path.ports());
        assertTrue(trace.producers().get(0).path.conditional());
    }

    @Test
    void junctionChannelsDoNotCrossDuringTraceTraversal(){
        BuildingRef west = ref("drill", 1), junction = ref("junction", 2), northTarget = ref("factory", 3), south = ref("drill", 4);
        NetworkPort westOut = port(west, NetworkSide.east, "out"), jWestIn = port(junction, NetworkSide.west, "in"),
            jEastOut = port(junction, NetworkSide.east, "out"), jNorthOut = port(junction, NetworkSide.north, "out"),
            jSouthIn = port(junction, NetworkSide.south, "in"), targetIn = port(northTarget, NetworkSide.south, "in"),
            southOut = port(south, NetworkSide.north, "out");
        NetworkGraph graph = graph(List.of(westOut, jWestIn, jEastOut, jNorthOut, jSouthIn, targetIn, southOut), List.of(
            edge(westOut, jWestIn, ItemConstraint.any()), edge(jWestIn, jEastOut, ItemConstraint.any()),
            edge(jSouthIn, jNorthOut, ItemConstraint.any()), edge(jNorthOut, targetIn, ItemConstraint.any()),
            edge(southOut, jSouthIn, ItemConstraint.any())));
        SupplyTrace trace = input(network(graph), northTarget, sand, List.of(
            producer(west, sand, DiagnosticReason.active), producer(south, sand, DiagnosticReason.active), consumer(northTarget, sand)));

        assertEquals(List.of(south), trace.producers().stream().map(endpoint -> endpoint.building).toList());
    }

    @Test
    void cyclesTerminateAndKeepOneEndpoint(){
        BuildingRef producer = ref("drill", 1), a = ref("router", 2), b = ref("router", 3), target = ref("factory", 4);
        NetworkPort p = port(producer, NetworkSide.east, "out"), ai = port(a, NetworkSide.west, "in"),
            ao = port(a, NetworkSide.east, "out"), bi = port(b, NetworkSide.west, "in"),
            bo = port(b, NetworkSide.east, "out"), ti = port(target, NetworkSide.west, "in");
        NetworkGraph graph = graph(List.of(p, ai, ao, bi, bo, ti), List.of(
            edge(p, ai, ItemConstraint.any()), edge(ai, ao, ItemConstraint.any()),
            edge(ao, bi, ItemConstraint.any()), edge(bi, bo, ItemConstraint.any()),
            edge(bo, ai, ItemConstraint.any()), edge(bo, ti, ItemConstraint.any())));

        SupplyTrace trace = assertTimeoutPreemptively(Duration.ofSeconds(2), () -> input(network(graph), target, sand, List.of(
            producer(producer, sand, DiagnosticReason.active), consumer(target, sand))));

        assertEquals(1, trace.producers().size());
        assertEquals(5, trace.producers().get(0).path.edges().size());
    }

    @Test
    void outputTraceFindsConsumersAndUsesItemIdentityNotDisplayName(){
        BuildingRef producer = ref("crafter", 1), target = ref("factory", 2);
        ResourceRef modA = item("mod-a-alloy", "Alloy"), modB = item("mod-b-alloy", "Alloy");
        NetworkPort source = port(producer, NetworkSide.east, "out"), input = port(target, NetworkSide.west, "in");
        NetworkGraph graph = graph(List.of(source, input), List.of(edge(source, input, ItemConstraint.only(modA))));
        AreaDiagnosticResult area = area(network(graph), List.of(producer(producer, modA, DiagnosticReason.active),
            consumer(target, modA)));

        TraceEndpoint endpoint = TraceAnalyzer.output(area, producer, modA).endpoints.get(0);
        assertEquals(List.of(source, input), endpoint.path.ports());
        assertEquals(1, endpoint.path.edges().size());
        assertTrue(TraceAnalyzer.output(area, producer, modB).endpoints.isEmpty());
    }

    @Test
    void outputTraceKeepsConsumerAndDeadEndBranchesAndExcludesItsTarget(){
        BuildingRef target = ref("crafter", 1), router = ref("router", 2), consumer = ref("factory", 3),
            deadBelt = ref("conveyor", 4);
        NetworkPort targetOut = port(target, NetworkSide.east, "out"), routerWest = port(router, NetworkSide.west, "in"),
            routerEast = port(router, NetworkSide.east, "out"), routerNorth = port(router, NetworkSide.north, "out"),
            consumerIn = port(consumer, NetworkSide.west, "in"), deadIn = port(deadBelt, NetworkSide.west, "in"),
            deadOut = port(deadBelt, NetworkSide.east, "out");
        NetworkGraph graph = graph(List.of(targetOut, routerWest, routerEast, routerNorth, consumerIn, deadIn, deadOut), List.of(
            edge(targetOut, routerWest, ItemConstraint.any()), edge(routerWest, routerEast, ItemConstraint.any()),
            edge(routerWest, routerNorth, ItemConstraint.any()), edge(routerEast, consumerIn, ItemConstraint.any()),
            edge(routerNorth, deadIn, ItemConstraint.any()), edge(deadIn, deadOut, ItemConstraint.any())));
        AreaDiagnosticResult area = area(network(graph), List.of(
            producer(target, sand, DiagnosticReason.active), consumer(consumer, sand), limited(router), limited(deadBelt)));

        SupplyTrace trace = TraceAnalyzer.output(area, target, sand);

        assertEquals(List.of(consumer), trace.endpoints.stream().map(endpoint -> endpoint.building).toList());
        assertEquals(List.of(deadBelt), trace.structuralDeadEnds);
        assertFalse(trace.endpoints.stream().anyMatch(endpoint -> endpoint.building.equals(target)));
    }

    @Test
    void outputTraceDistinguishesNoConsumerFromNoStructuralRoute(){
        BuildingRef target = ref("crafter", 1), belt = ref("conveyor", 2);
        NetworkPort targetOut = port(target, NetworkSide.east, "out"), beltIn = port(belt, NetworkSide.west, "in"),
            beltOut = port(belt, NetworkSide.east, "out");
        NetworkGraph graph = graph(List.of(targetOut, beltIn, beltOut), List.of(
            edge(targetOut, beltIn, ItemConstraint.any()), edge(beltIn, beltOut, ItemConstraint.any())));
        SupplyTrace trace = TraceAnalyzer.output(area(network(graph), List.of(
            producer(target, sand, DiagnosticReason.active), limited(belt))), target, sand);

        assertTrue(trace.complete);
        assertFalse(trace.noRouteProven);
        assertTrue(trace.endpoints.isEmpty());
        assertEquals(List.of(belt), trace.structuralDeadEnds);
        assertEquals(new TreeSet<>(graph.edges), new TreeSet<>(trace.traversedEdges));
        assertTrue(trace.findings.stream().anyMatch(f -> f.kind == NetworkFinding.Kind.noReachableInAreaConsumer
            && f.certainty == NetworkFinding.Certainty.proven));
        assertFalse(trace.findings.stream().anyMatch(f -> f.kind == NetworkFinding.Kind.noStructuralOutputRoute));
    }

    @Test
    void outputOverlayCollectsEveryBranchAfterASharedPath(){
        BuildingRef producer = ref("crafter", 1), belt = ref("conveyor", 2), first = ref("factory", 3), second = ref("factory", 4);
        NetworkPort producerOut = port(producer, NetworkSide.east, "out");
        NetworkPort beltIn = port(belt, NetworkSide.west, "in"), beltOut = port(belt, NetworkSide.east, "out");
        NetworkPort firstIn = port(first, NetworkSide.west, "in"), secondIn = port(second, NetworkSide.west, "in");
        NetworkGraph graph = graph(List.of(producerOut, beltIn, beltOut, firstIn, secondIn), List.of(
            edge(producerOut, beltIn, ItemConstraint.any()), edge(beltIn, beltOut, ItemConstraint.any()),
            edge(beltOut, firstIn, ItemConstraint.any()), edge(beltOut, secondIn, ItemConstraint.any())));
        AreaDiagnosticResult area = area(network(graph), List.of(producer(producer, sand, DiagnosticReason.active),
            consumer(first, sand), consumer(second, sand)));

        SupplyTrace trace = TraceAnalyzer.output(area, producer, sand);
        Set<NetworkEdge> highlighted = new HashSet<>();
        trace.endpoints.forEach(endpoint -> endpoint.path.addEdgesTo(highlighted));

        assertEquals(graph.edges.size(), highlighted.size());
    }

    private static SupplyTrace input(ItemNetwork network, BuildingRef target, ResourceRef item, List<AreaEntry> entries){
        return TraceAnalyzer.input(area(network, entries), target, item);
    }

    private static AreaDiagnosticResult area(ItemNetwork network, List<AreaEntry> entries){
        return AreaAnalyzer.analyze(AREA, entries.size(), entries).withNetwork(network);
    }

    private static ItemNetwork network(NetworkGraph graph){
        return new ItemNetwork(graph, List.of(), List.of(), List.of());
    }

    private static AreaEntry producer(BuildingRef ref, ResourceRef item, DiagnosticReason reason){
        FactorySnapshot snapshot = FactorySnapshot.builder(ref.blockName).support(SupportLevel.full)
            .enabled(true).shouldConsume(true).productionValid(true).efficiency(1f, 1f).blockEfficiencyScale(1f)
            .producedItem(item).build();
        return new AreaEntry(ref, snapshot, new DiagnosticResult(List.of(Finding.of(reason, Severity.normal))));
    }

    private static AreaEntry limited(BuildingRef ref){
        FactorySnapshot snapshot = FactorySnapshot.builder(ref.blockName).support(SupportLevel.minimal).build();
        return new AreaEntry(ref, snapshot, FactoryAnalyzer.analyze(snapshot));
    }

    private static AreaEntry consumer(BuildingRef ref, ResourceRef item){
        FactorySnapshot snapshot = FactorySnapshot.builder(ref.blockName).support(SupportLevel.full)
            .enabled(true).shouldConsume(true).productionValid(true).efficiency(1f, 1f).blockEfficiencyScale(1f)
            .input(ResourceState.of(ResourceKind.item, item.name).contentId(item.id).build()).build();
        return new AreaEntry(ref, snapshot, new DiagnosticResult(List.of(Finding.of(DiagnosticReason.active, Severity.normal))));
    }

    private static AreaEntry consumerWithItems(BuildingRef ref, ResourceRef... items){
        FactorySnapshot.Builder snapshot = FactorySnapshot.builder(ref.blockName).support(SupportLevel.full)
            .enabled(true).shouldConsume(true).productionValid(true).efficiency(1f, 1f).blockEfficiencyScale(1f);
        for(ResourceRef item : items){
            snapshot.input(ResourceState.of(ResourceKind.item, item.name).contentId(item.id).build());
        }
        return new AreaEntry(ref, snapshot.build(), new DiagnosticResult(List.of(Finding.of(DiagnosticReason.active, Severity.normal))));
    }

    private static BuildingRef ref(String block, int x){ return ref(block, x, 10, 1); }
    private static BuildingRef ref(String block, int x, int y, int team){ return new BuildingRef(x, y, block, block, 1, team); }
    private static ResourceRef item(String id, String name){ return new ResourceRef(ResourceKind.item, id, name); }
    private static NetworkPort port(BuildingRef ref, NetworkSide side, String channel){ return new NetworkPort(ref, side, channel); }
    private static NetworkEdge edge(NetworkPort from, NetworkPort to, ItemConstraint items){ return new NetworkEdge(from, to, items, false); }
    private static NetworkGraph graph(List<NetworkPort> ports, List<NetworkEdge> edges){ return new NetworkGraph(ports, edges); }
}
