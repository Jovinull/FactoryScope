package factoryscope.trace;

import factoryscope.analysis.*;
import factoryscope.area.*;
import factoryscope.model.*;
import factoryscope.network.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class TraceBenchmark{
    private static final ResourceRef sand = new ResourceRef(ResourceKind.item, "sand", "Sand");

    @Test
    @Tag("trace-benchmark")
    void measuresLongLinearSupplyTraces(){
        for(int conveyors : new int[]{50, 250, 1000, 4000}){
            Fixture fixture = fixture(conveyors);
            for(int i = 0; i < 8; i++) assertTrue(TraceAnalyzer.input(fixture.area, fixture.target, sand).complete);

            long[] timings = new long[41];
            for(int i = 0; i < timings.length; i++){
                long start = System.nanoTime();
                SupplyTrace trace = TraceAnalyzer.input(fixture.area, fixture.target, sand);
                timings[i] = System.nanoTime() - start;
                assertEquals(1, trace.producers().size());
            }
            Arrays.sort(timings);
            System.out.printf("trace %d conveyors median=%.3f ms p90=%.3f ms path=%d edges%n",
                conveyors, timings[timings.length / 2] / 1_000_000d,
                timings[(int)(timings.length * 0.9)] / 1_000_000d,
                TraceAnalyzer.input(fixture.area, fixture.target, sand).producers().get(0).path.edges().size());
        }
    }

    @Test
    @Tag("trace-benchmark")
    void measuresManyProducerPathsSharingOneLongRoute(){
        for(int producers : new int[]{50, 250, 1000, 4000}){
            Fixture fixture = fanInFixture(producers);
            for(int i = 0; i < 5; i++) assertEquals(producers, TraceAnalyzer.input(fixture.area, fixture.target, sand).producers().size());

            long[] timings = new long[21];
            for(int i = 0; i < timings.length; i++){
                long start = System.nanoTime();
                SupplyTrace trace = TraceAnalyzer.input(fixture.area, fixture.target, sand);
                timings[i] = System.nanoTime() - start;
                assertEquals(producers, trace.producers().size());
                Set<NetworkEdge> highlighted = new HashSet<>();
                trace.producers().forEach(endpoint -> endpoint.path.addEdgesTo(highlighted));
                assertEquals(fixture.area.network.graph.edges.size(), highlighted.size());
            }
            Arrays.sort(timings);
            System.out.printf("trace %d producers over a shared %d-conveyor route median=%.3f ms p90=%.3f ms%n",
                producers, producers, timings[timings.length / 2] / 1_000_000d,
                timings[(int)(timings.length * 0.9)] / 1_000_000d);
        }
    }

    private static Fixture fixture(int conveyors){
        BuildingRef producer = ref("drill", 0);
        BuildingRef target = ref("silicon-smelter", conveyors + 1);
        List<NetworkPort> ports = new ArrayList<>(conveyors * 2 + 2);
        List<NetworkEdge> edges = new ArrayList<>(conveyors * 2 + 1);
        NetworkPort previous = port(producer, NetworkSide.east, "out");
        ports.add(previous);

        for(int i = 1; i <= conveyors; i++){
            BuildingRef belt = ref("conveyor", i);
            NetworkPort input = port(belt, NetworkSide.west, "in");
            NetworkPort output = port(belt, NetworkSide.east, "out");
            ports.add(input);
            ports.add(output);
            edges.add(edge(previous, input));
            edges.add(edge(input, output));
            previous = output;
        }

        NetworkPort targetInput = port(target, NetworkSide.west, "in");
        ports.add(targetInput);
        edges.add(edge(previous, targetInput));
        NetworkGraph graph = new NetworkGraph(ports, edges);
        FactorySnapshot source = FactorySnapshot.builder("Drill").producedItem(sand).build();
        FactorySnapshot consumer = FactorySnapshot.builder("Silicon Smelter")
            .input(ResourceState.of(ResourceKind.item, sand.name).contentId(sand.id).build()).build();
        AreaEntry producerEntry = entry(producer, source);
        AreaEntry targetEntry = entry(target, consumer);
        AreaDiagnosticResult area = AreaAnalyzer.analyze(AreaSelection.of(0, 0, conveyors + 1, 2), 2,
            List.of(producerEntry, targetEntry)).withNetwork(new ItemNetwork(graph, List.of(), List.of(), List.of()));
        return new Fixture(area, target);
    }

    private static Fixture fanInFixture(int count){
        BuildingRef router = ref("router", count + 1);
        NetworkPort routerIn = port(router, NetworkSide.west, "in");
        NetworkPort routerOut = port(router, NetworkSide.east, "out");
        List<NetworkPort> ports = new ArrayList<>(count * 3 + 2);
        List<NetworkEdge> edges = new ArrayList<>(count * 3 + 2);
        ports.add(routerIn);
        ports.add(routerOut);
        edges.add(edge(routerIn, routerOut));
        List<AreaEntry> entries = new ArrayList<>(count + 1);

        for(int i = 0; i < count; i++){
            BuildingRef producer = ref("drill", i);
            NetworkPort output = port(producer, NetworkSide.east, "out");
            ports.add(output);
            edges.add(edge(output, routerIn));
            entries.add(entry(producer, FactorySnapshot.builder("Drill").producedItem(sand).build()));
        }

        NetworkPort previous = routerOut;
        for(int i = 0; i < count; i++){
            BuildingRef belt = ref("conveyor", count + 2 + i);
            NetworkPort input = port(belt, NetworkSide.west, "in");
            NetworkPort output = port(belt, NetworkSide.east, "out");
            ports.add(input);
            ports.add(output);
            edges.add(edge(previous, input));
            edges.add(edge(input, output));
            previous = output;
        }

        BuildingRef target = ref("silicon-smelter", count * 2 + 3);
        NetworkPort targetInput = port(target, NetworkSide.west, "in");
        ports.add(targetInput);
        edges.add(edge(previous, targetInput));
        entries.add(entry(target, FactorySnapshot.builder("Silicon Smelter")
            .input(ResourceState.of(ResourceKind.item, sand.name).contentId(sand.id).build()).build()));
        NetworkGraph graph = new NetworkGraph(ports, edges);
        AreaDiagnosticResult area = AreaAnalyzer.analyze(AreaSelection.of(0, 0, count * 2 + 4, 2), entries.size(), entries)
            .withNetwork(new ItemNetwork(graph, List.of(), List.of(), List.of()));
        return new Fixture(area, target);
    }

    private static AreaEntry entry(BuildingRef ref, FactorySnapshot snapshot){
        return new AreaEntry(ref, snapshot, new DiagnosticResult(List.of(Finding.of(DiagnosticReason.active, Severity.normal))));
    }

    private static BuildingRef ref(String block, int x){ return new BuildingRef(x, 1, block, block, 1, 1); }
    private static NetworkPort port(BuildingRef ref, NetworkSide side, String channel){ return new NetworkPort(ref, side, channel); }
    private static NetworkEdge edge(NetworkPort from, NetworkPort to){ return new NetworkEdge(from, to, ItemConstraint.any(), false); }

    private record Fixture(AreaDiagnosticResult area, BuildingRef target){
    }
}
