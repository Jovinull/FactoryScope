package factoryscope.probe;

import factoryscope.area.*;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.world.*;
import org.junit.jupiter.api.*;

import java.util.*;

import static mindustry.Vars.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("network-benchmark")
class NetworkBenchmark{
    private static final int WARMUP = 3, RUNS = 7, WORLD = 320;

    @BeforeAll static void boot(){ HeadlessGame.start(); }

    @Test
    void measureNetworkConstruction(){
        System.out.printf("%-10s %12s%n", "buildings", "network scan");
        for(int count : new int[]{50, 250, 1000, 4000}) measure(count);
        System.out.println("milliseconds, median of " + RUNS + " runs after " + WARMUP + " warm-up runs");
    }

    private void measure(int count){
        HeadlessGame.newWorld(WORLD);
        int side = (int)Math.ceil(Math.sqrt(count));
        int placed = 0;
        for(int y = 2; y < side * 3 + 2 && placed < count; y += 3) for(int x = 2; x < side * 3 + 2 && placed < count; x += 3){
            world.tile(x, y).setBlock(Blocks.conveyor, Team.sharded, 0);
            placed++;
        }
        assertEquals(count, placed);
        AreaSelection area = AreaSelection.of(0, 0, WORLD - 1, WORLD - 1);
        double[] times = new double[RUNS];
        for(int run = -WARMUP; run < RUNS; run++){
            long start = System.nanoTime();
            var network = MindustryNetworkProbe.scan(area, Team.sharded);
            long end = System.nanoTime();
            assertFalse(network.graph.ports.isEmpty());
            if(run >= 0) times[run] = (end - start) / 1_000_000d;
        }
        Arrays.sort(times);
        System.out.printf("%-10d %12.2f%n", count, times[times.length / 2]);
    }
}
