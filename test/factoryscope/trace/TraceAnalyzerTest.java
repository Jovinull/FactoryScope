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

        assertTrue(trace.noRouteProven);
        assertEquals(List.of(belt), trace.structuralDeadEnds);
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
        assertTrue(rejected.noRouteProven);
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

    private static BuildingRef ref(String block, int x){ return new BuildingRef(x, 10, block, block, 1, 1); }
    private static ResourceRef item(String id, String name){ return new ResourceRef(ResourceKind.item, id, name); }
    private static NetworkPort port(BuildingRef ref, NetworkSide side, String channel){ return new NetworkPort(ref, side, channel); }
    private static NetworkEdge edge(NetworkPort from, NetworkPort to, ItemConstraint items){ return new NetworkEdge(from, to, items, false); }
    private static NetworkGraph graph(List<NetworkPort> ports, List<NetworkEdge> edges){ return new NetworkGraph(ports, edges); }
}
