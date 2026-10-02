package factoryscope.probe;

import factoryscope.power.*;
import mindustry.content.Blocks;
import mindustry.game.Team;
import mindustry.gen.Building;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.*;

import static mindustry.Vars.*;
import static org.junit.jupiter.api.Assertions.*;

/** Repeatable PowerGraph grouping, immutable snapshot, connection and presentation-model measurement. */
@Tag("power-benchmark")
class PowerBenchmark{
    private static final int WARMUP = 2, RUNS = 5, WORLD = 320;

    @BeforeAll static void boot(){ HeadlessGame.start(); }

    @Test
    void measuresPowerScopeSnapshotAndModel(){
        System.out.printf("%-10s %12s %12s %12s%n", "nodes", "probe", "model", "connections");
        for(int count : new int[]{50, 250, 1000, 4000}) measure(count);
        measureManyGraphs(4000);
        System.out.println("milliseconds, median of " + RUNS + " runs after " + WARMUP + " warm-up runs");
    }

    private void measure(int count){
        HeadlessGame.newWorld(WORLD);
        int side = (int)Math.ceil(Math.sqrt(count));
        List<Building> selected = new ArrayList<>(count);
        for(int i = 0; i < count; i++){
            int x = 2 + i % side;
            int y = 2 + i / side;
            world.tile(x, y).setBlock(Blocks.powerNode, Team.sharded, 0);
            selected.add(world.tile(x, y).build);
        }
        assertEquals(count, selected.stream().filter(Objects::nonNull).count());
        assertTrue(selected.stream().allMatch(build -> build.power != null));
        List<Double> probe = new ArrayList<>(RUNS), model = new ArrayList<>(RUNS);
        PowerGridReport report = null;
        for(int run = -WARMUP; run < RUNS; run++){
            long start = System.nanoTime();
            report = MindustryPowerProbe.scan(selected, Team.sharded, Map.of());
            long captured = System.nanoTime();
            String modelText = model(report);
            long modeled = System.nanoTime();
            assertNotNull(modelText);
            assertEquals(1, report.grids.size(), "the engine should retain this adjacent node network as one graph");
            assertEquals(count, report.grids.get(0).snapshot.members.size());
            if(run >= 0){
                probe.add((captured - start) / 1_000_000d);
                model.add((modeled - captured) / 1_000_000d);
            }
        }
        long connections = report.grids.get(0).snapshot.connections.size();
        System.out.printf("%-10d %12.2f %12.2f %12d%n", count, median(probe), median(model), connections);
    }

    private void measureManyGraphs(int count){
        HeadlessGame.newWorld(WORLD);
        int side = (int)Math.ceil(Math.sqrt(count));
        List<Building> selected = new ArrayList<>(count);
        Set<mindustry.world.blocks.power.PowerGraph> graphs =
            Collections.newSetFromMap(new IdentityHashMap<>());
        for(int i = 0; i < count; i++){
            //Solar panels do not conduct to one another. Spacing also prevents accidental footprint overlap.
            int x = 2 + i % side * 3;
            int y = 2 + i / side * 3;
            world.tile(x, y).setBlock(Blocks.solarPanel, Team.sharded, 0);
            Building build = world.tile(x, y).build;
            selected.add(build);
            graphs.add(build.power.graph);
        }
        assertEquals(count, graphs.size(), "fixture must exercise independent engine graphs");
        List<Double> probe = new ArrayList<>(RUNS), model = new ArrayList<>(RUNS);
        PowerGridReport report = null;
        for(int run = -WARMUP; run < RUNS; run++){
            long start = System.nanoTime();
            report = MindustryPowerProbe.scan(selected, Team.sharded, Map.of());
            long captured = System.nanoTime();
            String modelText = model(report);
            long modeled = System.nanoTime();
            assertNotNull(modelText);
            assertEquals(count, report.grids.size());
            if(run >= 0){
                probe.add((captured - start) / 1_000_000d);
                model.add((modeled - captured) / 1_000_000d);
            }
        }
        System.out.printf("%-10s %12.2f %12.2f %12d%n", count + " grids",
            median(probe), median(model), 0);
    }

    private static String model(PowerGridReport report){
        StringBuilder rows = new StringBuilder(report.grids.size() * 32);
        for(PowerGridResult grid : report.grids){
            PowerGridSnapshot snapshot = grid.snapshot;
            rows.append(snapshot.id).append(':').append(grid.state).append(':')
                .append(snapshot.selectedMemberCount).append('/').append(snapshot.members.size());
            for(PowerMemberSnapshot member : snapshot.members){
                rows.append('|').append(member.ref.blockId).append('@')
                    .append(member.ref.tileX).append(',').append(member.ref.tileY);
            }
            for(PowerConnection edge : snapshot.connections){
                rows.append('>').append(edge.first.tileX).append(',').append(edge.first.tileY)
                    .append('-').append(edge.second.tileX).append(',').append(edge.second.tileY);
            }
        }
        return rows.toString();
    }

    private static double median(List<Double> values){
        values.sort(Double::compare);
        return values.get(values.size() / 2);
    }
}
