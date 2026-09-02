package factoryscope.throughput;

import java.util.*;

/** Immutable view of a monitoring session for one graph snapshot. */
public final class ThroughputSnapshot{
    public final Map<ThroughputKey, ThroughputSample> exact;
    public final long simulationMillis;

    public ThroughputSnapshot(Map<ThroughputKey, ThroughputSample> exact, long simulationMillis){
        TreeMap<ThroughputKey, ThroughputSample> ordered = new TreeMap<>(exact);
        this.exact = Collections.unmodifiableMap(ordered);
        this.simulationMillis = simulationMillis;
    }
}
