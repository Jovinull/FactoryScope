package factoryscope.probe;

import factoryscope.analysis.*;
import factoryscope.area.*;
import factoryscope.model.*;
import factoryscope.network.*;
import factoryscope.liquid.*;
import factoryscope.trace.TraceDirection;
import mindustry.content.Blocks;
import mindustry.game.Team;
import mindustry.gen.Building;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.*;

import static mindustry.Vars.world;
import static org.junit.jupiter.api.Assertions.*;

/** Reports on-demand LiquidScope costs without making machine-dependent timing a test gate. */
@Tag("liquid-benchmark")
class LiquidBenchmark{
    private static final int WORLD = 320, WARMUP = 2, RUNS = 5;
    private static final ResourceRef water = new ResourceRef(ResourceKind.liquid, "water", "Water");

    @BeforeAll static void boot(){ HeadlessGame.start(); }

    @Test
    void measureLiquidScopeStages(){
        System.out.printf("%n%-10s %10s %10s %10s %10s %10s%n", "buildings", "collect", "probe+buffer", "graph", "trace", "ui-model");
        for(int count : new int[]{50, 250, 1000, 4000}) measure(count);
        System.out.printf("milliseconds, median of %d runs after %d warm-up runs; trace fixture is an independent %d-node chain%n",
            RUNS, WARMUP, 4000);
    }

    private void measure(int count){
        HeadlessGame.newWorld(WORLD);
        int side = (int)Math.ceil(Math.sqrt(count)), placed = 0;
        for(int y = 2; y < side * 3 + 2 && placed < count; y += 3){
            for(int x = 2; x < side * 3 + 2 && placed < count; x += 3){
                world.tile(x, y).setBlock(Blocks.liquidRouter, Team.sharded, 0);
                placed++;
            }
        }
        assertEquals(count, placed);
        AreaSelection selection = AreaSelection.of(0, 0, WORLD - 1, WORLD - 1);
        Fixture traceFixture = traceFixture(4000);

        double[] collect = new double[RUNS], probe = new double[RUNS], graph = new double[RUNS];
        double[] trace = new double[RUNS], ui = new double[RUNS];
        for(int run = -WARMUP; run < RUNS; run++){
            long t0 = System.nanoTime();
            var buildings = AreaProbe.collect(selection, Team.sharded);
            long t1 = System.nanoTime();

            Map<BuildingRef, FactorySnapshot> snapshots = new HashMap<>();
            int storedEntries = 0;
            for(Building build : buildings){
                BuildingRef ref = AreaProbe.refOf(build);
                FactorySnapshot snapshot = MindustryFactoryProbe.probe(build);
                snapshots.put(ref, snapshot);
                storedEntries += snapshot.storedLiquids.size();
            }
            long t2 = System.nanoTime();

            LiquidNetwork network = MindustryLiquidProbe.scan(selection, Team.sharded, snapshots);
            long t3 = System.nanoTime();

            LiquidTrace result = LiquidTraceAnalyzer.input(traceFixture.area, traceFixture.target, water);
            long t4 = System.nanoTime();
            StringBuilder model = new StringBuilder();
            model.append(result.target).append(result.liquid.name).append(result.complete);
            for(LiquidTraceEndpoint endpoint : result.endpoints){
                model.append(endpoint.building.blockName).append(endpoint.kind).append(endpoint.path.ports().size());
            }
            for(LiquidNetworkEdge edge : network.graph.edges) model.append(edge.from).append(edge.to);
            long t5 = System.nanoTime();

            assertEquals(count, buildings.size);
            assertEquals(0, storedEntries, "the empty fixture should capture no positive liquid buffers");
            assertNotNull(network.graph);
            assertEquals(1, result.producers().size());
            assertTrue(model.length() > 0);
            if(run >= 0){
                collect[run] = ms(t0, t1);
                probe[run] = ms(t1, t2);
                graph[run] = ms(t2, t3);
                trace[run] = ms(t3, t4);
                ui[run] = ms(t4, t5);
            }
        }
        System.out.printf("%-10d %10.2f %10.2f %10.2f %10.2f %10.2f%n", count,
            median(collect), median(probe), median(graph), median(trace), median(ui));
    }

    private static Fixture traceFixture(int conduits){
        List<NetworkPort> ports = new ArrayList<>(conduits * 2 + 2);
        List<LiquidNetworkEdge> edges = new ArrayList<>(conduits * 2 + 1);
        BuildingRef source = ref("liquid-source", 0);
        NetworkPort previous = port(source, NetworkSide.east, "out");
        ports.add(previous);

        for(int i = 1; i <= conduits; i++){
            BuildingRef conduit = ref("conduit", i);
            NetworkPort input = port(conduit, NetworkSide.west, "in");
            NetworkPort output = port(conduit, NetworkSide.east, "out");
            ports.add(input);
            ports.add(output);
            edges.add(new LiquidNetworkEdge(previous, input, LiquidConstraint.any()));
            edges.add(new LiquidNetworkEdge(input, output, LiquidConstraint.any()));
            previous = output;
        }

        BuildingRef target = ref("liquid-consumer", conduits + 1);
        NetworkPort targetInput = port(target, NetworkSide.west, "in");
        ports.add(targetInput);
        edges.add(new LiquidNetworkEdge(previous, targetInput, LiquidConstraint.any()));
        LiquidNetwork network = new LiquidNetwork(new LiquidNetworkGraph(ports, edges), List.of(), List.of(),
            Map.of(), Map.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(water));
        AreaEntry sourceEntry = entry(source, FactorySnapshot.builder("Liquid Source").producedLiquid(water).build());
        AreaEntry targetEntry = entry(target, FactorySnapshot.builder("Liquid Consumer")
            .input(ResourceState.of(ResourceKind.liquid, "Water").contentId("water").build()).build());
        AreaDiagnosticResult area = AreaAnalyzer.analyze(AreaSelection.of(0, 0, conduits + 1, 2), 2,
            List.of(sourceEntry, targetEntry)).withLiquids(network);
        return new Fixture(area, target);
    }

    private static AreaEntry entry(BuildingRef ref, FactorySnapshot snapshot){
        return new AreaEntry(ref, snapshot, new DiagnosticResult(List.of(Finding.of(DiagnosticReason.active, Severity.normal))));
    }

    private static BuildingRef ref(String block, int x){ return new BuildingRef(x, 1, block, block, 1, 1); }
    private static NetworkPort port(BuildingRef ref, NetworkSide side, String channel){ return new NetworkPort(ref, side, channel); }
    private static double ms(long start, long end){ return (end - start) / 1_000_000d; }
    private static double median(double[] values){ Arrays.sort(values); return values[values.length / 2]; }

    private static final class Fixture{
        final AreaDiagnosticResult area;
        final BuildingRef target;
        Fixture(AreaDiagnosticResult area, BuildingRef target){ this.area = area; this.target = target; }
    }
}
