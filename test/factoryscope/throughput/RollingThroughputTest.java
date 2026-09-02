package factoryscope.throughput;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

class RollingThroughputTest{
    @Test void collectsOnlySimulationTime(){
        RollingThroughput flow = new RollingThroughput(10_000, 500, 1_000);
        flow.advance(0);
        flow.observe(500, 5);
        flow.advance(1_000);
        assertTrue(flow.sample().ready);
        assertEquals(5, flow.sample().items);
        assertEquals(5d, flow.sample().perSecond());
        flow.advance(1_000);
        assertEquals(5d, flow.sample().perSecond(), 0.0001);
    }

    @Test void boundsOldHistory(){
        RollingThroughput flow = new RollingThroughput(1_000, 100, 100);
        flow.observe(0, 10);
        flow.advance(2_000);
        assertEquals(0, flow.sample().items);
    }
}
