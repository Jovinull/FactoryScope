package factoryscope.trace;

import factoryscope.analysis.*;
import factoryscope.area.*;
import factoryscope.model.*;
import factoryscope.network.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Informational stress measurements for the skipped-building checks used by Supply Trace. */
class SkippedTraceBenchmark{
    private static final ResourceRef sand = new ResourceRef(ResourceKind.item, "sand", "Sand");

    @Test
    @Tag("trace-skipped-benchmark")
    void measuresIrrelevantRelevantAndMixedSkippedBuildings(){
        for(int size : new int[]{100, 500, 1000, 2000, 4000}){
            for(SkippedLayout layout : SkippedLayout.values()) measure(size, layout);
        }
    }

    private static void measure(int size, SkippedLayout layout){
        Fixture fixture = fixture(size, size, layout);
        for(int i = 0; i < 4; i++) assertExpected(fixture, layout);

        long[] samples = new long[ nineSamples() ];
        for(int i = 0; i < samples.length; i++){
            long start = System.nanoTime();
            SupplyTrace trace = TraceAnalyzer.input(fixture.area, fixture.target, sand);
            samples[i] = System.nanoTime() - start;
            assertEquals(1, trace.producers().size());
            assertEquals(fixture.edgeCount, trace.traversedEdges.size());
            assertEquals(layout != SkippedLayout.irrelevant, trace.diagnosticsIncomplete);
        }

        Arrays.sort(samples);
        System.out.printf(Locale.ROOT, "trace skipped layout=%s route=%d visitedPorts=%d skipped=%d median=%.3f ms p90=%.3f ms%n",
            layout, size, fixture.visitedPorts, size,
            samples[samples.length / 2] / 1_000_000d,
            samples[(int)(samples.length * 0.9)] / 1_000_000d);
    }

    private static int nineSamples(){
        return 9;
    }

    private static void assertExpected(Fixture fixture, SkippedLayout layout){
        SupplyTrace trace = TraceAnalyzer.input(fixture.area, fixture.target, sand);
        assertEquals(1, trace.producers().size());
        assertEquals(fixture.edgeCount, trace.traversedEdges.size());
        assertEquals(layout != SkippedLayout.irrelevant, trace.diagnosticsIncomplete);
    }

    private static Fixture fixture(int routeSize, int skippedCount, SkippedLayout layout){
        BuildingRef producer = ref("drill", 0, 10);
        BuildingRef target = ref("silicon-smelter", 3 * (routeSize + 1), 10);
        List<NetworkPort> ports = new ArrayList<>(routeSize * 2 + 2);
        List<NetworkEdge> edges = new ArrayList<>(routeSize * 2 + 1);
        List<BuildingRef> skipped = new ArrayList<>(skippedCount);
        List<AreaEntry> entries = new ArrayList<>(2);

        NetworkPort previous = port(producer, NetworkSide.east, "out");
        ports.add(previous);
        for(int i = 1; i <= routeSize; i++){
            BuildingRef belt = ref("conveyor", 3 * i, 10);
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
        entries.add(entry(producer, FactorySnapshot.builder("Drill").producedItem(sand).build()));
        entries.add(entry(target, FactorySnapshot.builder("Silicon Smelter")
            .input(ResourceState.of(ResourceKind.item, sand.name).contentId(sand.id).build()).build()));

        int relevantCount = switch(layout){
            case irrelevant -> 0;
            case relevant -> skippedCount;
            case mixed -> skippedCount / 2;
        };
        for(int i = 0; i < skippedCount; i++){
            if(i < relevantCount){
                // One skipped building touches each distinct, visited conveyor input port.
                skipped.add(ref("skipped-mod-building", 3 * (i + 1) - 1, 10));
            }else{
                // Still inside the selected area, but not beside any visited route port.
                skipped.add(ref("skipped-mod-building", 3 * (i + 1), 20));
            }
        }

        NetworkGraph graph = new NetworkGraph(ports, edges);
        int selectionMaxX = target.tileX + 1;
        AreaDiagnosticResult area = AreaAnalyzer.analyze(AreaSelection.of(0, 0, selectionMaxX, 22),
                entries.size() + skippedCount, entries)
            .withNetwork(new ItemNetwork(graph, List.of(), List.of(), List.of()))
            .withSkippedBuildings(skipped);
        return new Fixture(area, target, graph.ports.size(), graph.edges.size());
    }

    private static AreaEntry entry(BuildingRef ref, FactorySnapshot snapshot){
        return new AreaEntry(ref, snapshot, new DiagnosticResult(List.of(Finding.of(DiagnosticReason.active, Severity.normal))));
    }

    private static BuildingRef ref(String block, int x, int y){ return new BuildingRef(x, y, block, block, 1, 1); }
    private static NetworkPort port(BuildingRef ref, NetworkSide side, String channel){ return new NetworkPort(ref, side, channel); }
    private static NetworkEdge edge(NetworkPort from, NetworkPort to){ return new NetworkEdge(from, to, ItemConstraint.any(), false); }

    private enum SkippedLayout{ irrelevant, relevant, mixed }
    private record Fixture(AreaDiagnosticResult area, BuildingRef target, int visitedPorts, int edgeCount){ }
}
