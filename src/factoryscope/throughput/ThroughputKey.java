package factoryscope.throughput;

import factoryscope.model.*;
import factoryscope.network.*;

import java.util.*;

/** Stable identity for an observed item movement on one directed network edge. */
public final class ThroughputKey implements Comparable<ThroughputKey>{
    public final NetworkPort from;
    public final NetworkPort to;
    public final ResourceRef item;

    public ThroughputKey(NetworkPort from, NetworkPort to, ResourceRef item){
        this.from = Objects.requireNonNull(from, "from");
        this.to = Objects.requireNonNull(to, "to");
        this.item = Objects.requireNonNull(item, "item");
    }

    @Override public int compareTo(ThroughputKey other){
        int result = from.compareTo(other.from);
        if(result != 0) return result;
        result = to.compareTo(other.to);
        return result != 0 ? result : item.key().compareTo(other.item.key());
    }
    @Override public boolean equals(Object other){
        return other instanceof ThroughputKey key && from.equals(key.from) && to.equals(key.to) && item.equals(key.item);
    }
    @Override public int hashCode(){ return Objects.hash(from, to, item); }
}
