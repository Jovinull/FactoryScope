package factoryscope.throughput;

import java.util.*;

/** Bounded observation state attached to one immutable topology snapshot. */
public final class ThroughputSession{
    private final Object graphIdentity;
    private final long windowMillis;
    private final long bucketMillis;
    private final long minimumMillis;
    private final Map<ThroughputKey, RollingThroughput> flows = new HashMap<>();
    private boolean active;

    public ThroughputSession(Object graphIdentity, long windowMillis, long bucketMillis, long minimumMillis){
        this.graphIdentity = Objects.requireNonNull(graphIdentity, "graphIdentity");
        this.windowMillis = windowMillis;
        this.bucketMillis = bucketMillis;
        this.minimumMillis = minimumMillis;
    }

    public void start(){ active = true; }
    public void stop(){ active = false; }
    public boolean active(){ return active; }
    public boolean belongsTo(Object graph){ return graphIdentity == graph; }

    public void observe(ThroughputKey key, long simulationMillis, int items){
        if(!active) return;
        flows.computeIfAbsent(key, ignored -> new RollingThroughput(windowMillis, bucketMillis, minimumMillis))
            .observe(simulationMillis, items);
    }

    public void advance(long simulationMillis){
        if(active) flows.values().forEach(flow -> flow.advance(simulationMillis));
    }

    public ThroughputSnapshot snapshot(long simulationMillis){
        Map<ThroughputKey, ThroughputSample> values = new HashMap<>();
        flows.forEach((key, flow) -> values.put(key, flow.sample()));
        return new ThroughputSnapshot(values, simulationMillis);
    }

    public void reset(){ flows.clear(); }
}
