package factoryscope.throughput;

import factoryscope.area.*;
import factoryscope.model.*;
import factoryscope.network.*;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

class ThroughputSessionTest{
    @Test void ignoresObservationsUntilStartedAndClearsOnReset(){
        Object graph = new Object();
        ThroughputSession session = new ThroughputSession(graph, 10_000, 500, 500);
        ThroughputKey key = new ThroughputKey(port(1), port(2), new ResourceRef(ResourceKind.item, "copper", "Copper"));
        session.observe(key, 0, 8);
        assertTrue(session.snapshot(0).exact.isEmpty());
        session.start();
        session.observe(key, 0, 8);
        session.advance(500);
        assertEquals(8, session.snapshot(500).exact.get(key).items);
        session.reset();
        assertTrue(session.snapshot(500).exact.isEmpty());
        assertTrue(session.belongsTo(graph));
        assertFalse(session.belongsTo(new Object()));
    }

    private static NetworkPort port(int x){
        return new NetworkPort(new BuildingRef(x, 1, "conveyor", "Conveyor", 1, 1), NetworkSide.east, "");
    }
}
