package factoryscope.throughput;

/** A bounded-window result based on discrete observed items and simulation time. */
public final class ThroughputSample{
    public final int items;
    public final long elapsedMillis;
    public final boolean ready;
    public ThroughputSample(int items, long elapsedMillis, boolean ready){
        this.items = items; this.elapsedMillis = elapsedMillis; this.ready = ready;
    }
    public double perSecond(){ return elapsedMillis == 0 ? 0d : items * 1000d / elapsedMillis; }
}
