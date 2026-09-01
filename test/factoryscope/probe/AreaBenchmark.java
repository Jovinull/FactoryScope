package factoryscope.probe;

import arc.struct.*;
import factoryscope.analysis.*;
import factoryscope.area.*;
import factoryscope.model.*;
import factoryscope.ui.*;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.world.*;
import org.junit.jupiter.api.*;

import java.util.*;

import static mindustry.Vars.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * A repeatable measurement of what an area analysis costs, stage by stage.
 *
 * <p>Not part of {@code gradlew test}: run it with {@code gradlew areaBenchmark}. It deliberately
 * asserts nothing about time. A wall-clock threshold in a test suite fails on a loaded machine and
 * passes on a fast one, which teaches a maintainer to ignore it; the numbers are printed instead, so
 * a regression shows up as a number that moved rather than as a flaky red build.
 *
 * <p>The only assertion is that the work actually happened - a benchmark that quietly measured an
 * empty area would be worse than none.
 */
@Tag("area-benchmark")
class AreaBenchmark{
    /** Enough repetitions that the JIT has warmed up and one slow GC pause does not dominate. */
    private static final int WARMUP = 3;
    private static final int RUNS = 7;
    private static final int SPACING = 4;
    private static final int WORLD = 320;

    @BeforeAll
    static void boot(){
        HeadlessGame.start();
    }

    @Test
    void measureAreaAnalysis(){
        System.out.println();
        System.out.printf("%-10s %10s %10s %10s %10s %10s %10s%n",
            "buildings", "collect", "probe", "diagnose", "aggregate", "present", "scan total");

        for(int count : new int[]{50, 250, 1000, 4000}){
            measure(count);
        }
        System.out.println();
        System.out.println("milliseconds, median of " + RUNS + " runs after " + WARMUP + " warm-up runs");
    }

    private void measure(int count){
        HeadlessGame.newWorld(WORLD);
        AreaSelection area = fill(count);

        assertEquals(count, AreaProbe.collect(area, Team.sharded).size,
            "the benchmark must actually be measuring " + count + " buildings");

        double[] collect = new double[RUNS];
        double[] probe = new double[RUNS];
        double[] diagnose = new double[RUNS];
        double[] aggregate = new double[RUNS];
        double[] present = new double[RUNS];
        double[] scan = new double[RUNS];

        for(int run = -WARMUP; run < RUNS; run++){
            long t0 = System.nanoTime();
            Seq<Building> buildings = AreaProbe.collect(area, Team.sharded);
            long t1 = System.nanoTime();

            List<FactorySnapshot> snapshots = new ArrayList<>(buildings.size);
            for(Building build : buildings) snapshots.add(MindustryFactoryProbe.probe(build));
            long t2 = System.nanoTime();

            List<AreaEntry> entries = new ArrayList<>(buildings.size);
            for(int i = 0; i < buildings.size; i++){
                FactorySnapshot snapshot = snapshots.get(i);
                entries.add(new AreaEntry(AreaProbe.refOf(buildings.get(i)), snapshot.support,
                    FactoryAnalyzer.analyze(snapshot)));
            }
            long t3 = System.nanoTime();

            AreaDiagnosticResult result = AreaAnalyzer.analyze(area, buildings.size, entries);
            long t4 = System.nanoTime();

            buildReportModel(result);
            long t5 = System.nanoTime();

            AreaProbe.scan(area, Team.sharded);
            long t6 = System.nanoTime();

            if(run < 0) continue;
            collect[run] = ms(t0, t1);
            probe[run] = ms(t1, t2);
            diagnose[run] = ms(t2, t3);
            aggregate[run] = ms(t3, t4);
            present[run] = ms(t4, t5);
            scan[run] = ms(t5, t6);
        }

        System.out.printf("%-10d %10.2f %10.2f %10.2f %10.2f %10.2f %10.2f%n", count,
            median(collect), median(probe), median(diagnose), median(aggregate), median(present), median(scan));
    }

    /**
     * Everything the dialog does to a result before any widget exists: the status lines, the issue
     * headlines, and the affected-building rows it would show, each resolved back to a live building
     * the way a drill-down does.
     */
    private void buildReportModel(AreaDiagnosticResult result){
        StringBuilder sink = new StringBuilder();
        result.summary.byStatus.forEach((status, n) -> sink.append(n).append(AreaText.status(status)));

        for(AreaIssueGroup group : result.issues){
            sink.append(AreaText.issueTitle(group)).append(group.buildingCount());
            int shown = Math.min(group.buildingCount(), 40);
            for(int i = 0; i < shown; i++){
                BuildingRef ref = group.buildings.get(i);
                sink.append(ref.blockName).append(AreaProbe.resolve(ref) == null ? '-' : '+');
            }
        }
        assertFalse(sink.isEmpty());
    }

    /** Lays out {@code count} starved silicon smelters on a grid and returns the area that covers them. */
    private AreaSelection fill(int count){
        int side = (int)Math.ceil(Math.sqrt(count));
        int placed = 0;

        for(int row = 0; row < side && placed < count; row++){
            for(int col = 0; col < side && placed < count; col++){
                int x = 2 + col * SPACING, y = 2 + row * SPACING;
                if(x >= WORLD - 2 || y >= WORLD - 2) continue;
                Tile tile = world.tile(x, y);
                tile.setBlock(Blocks.siliconSmelter, Team.sharded, 0);
                tile.build.updateConsumption();
                placed++;
            }
        }

        assertEquals(count, placed, "could not place the whole grid; the world is too small");
        return AreaSelection.of(0, 0, WORLD - 1, WORLD - 1);
    }

    private static double ms(long from, long to){
        return (to - from) / 1_000_000d;
    }

    private static double median(double[] values){
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        return sorted[sorted.length / 2];
    }
}
