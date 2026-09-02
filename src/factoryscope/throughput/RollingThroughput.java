package factoryscope.throughput;

import java.util.*;

/** Fixed-memory simulation-time buckets for one monitored relationship. */
public final class RollingThroughput{
    private final long windowMillis, bucketMillis, minimumMillis;
    private final NavigableMap<Long, Integer> buckets = new TreeMap<>();
    private long started = -1, latest = -1;

    public RollingThroughput(long windowMillis, long bucketMillis, long minimumMillis){
        if(windowMillis <= 0 || bucketMillis <= 0 || minimumMillis <= 0 || minimumMillis > windowMillis) throw new IllegalArgumentException();
        this.windowMillis = windowMillis; this.bucketMillis = bucketMillis; this.minimumMillis = minimumMillis;
    }
    public void observe(long simulationMillis, int items){
        if(items < 0) throw new IllegalArgumentException("items");
        if(started < 0) started = simulationMillis;
        latest = Math.max(latest, simulationMillis);
        long bucket = simulationMillis / bucketMillis;
        buckets.merge(bucket, items, Integer::sum);
        trim();
    }
    public void advance(long simulationMillis){
        if(started < 0) started = simulationMillis;
        latest = Math.max(latest, simulationMillis);
        trim();
    }
    public ThroughputSample sample(){
        if(started < 0 || latest < started) return new ThroughputSample(0, 0, false);
        long elapsed = Math.min(windowMillis, latest - Math.max(started, latest - windowMillis));
        int total = buckets.values().stream().mapToInt(Integer::intValue).sum();
        return new ThroughputSample(total, elapsed, elapsed >= minimumMillis);
    }
    public void reset(){ buckets.clear(); started = latest = -1; }
    private void trim(){
        long minimumBucket = Math.floorDiv(Math.max(0, latest - windowMillis), bucketMillis);
        buckets.headMap(minimumBucket, false).clear();
    }
}
