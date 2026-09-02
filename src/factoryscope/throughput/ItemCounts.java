package factoryscope.throughput;

import factoryscope.model.*;

import java.util.*;

/** Immutable item counts captured at an observation boundary. */
public final class ItemCounts{
    private final Map<ResourceRef, Integer> values;
    public ItemCounts(Map<ResourceRef, Integer> values){
        TreeMap<ResourceRef, Integer> ordered = new TreeMap<>(Comparator.comparing(ResourceRef::key));
        values.forEach((item, count) -> { if(count > 0) ordered.put(item, count); });
        this.values = Collections.unmodifiableMap(ordered);
    }
    public int count(ResourceRef item){ return values.getOrDefault(item, 0); }
    public Set<ResourceRef> items(){ return values.keySet(); }
    /** Items removed between captures; additions are deliberately not treated as transport. */
    public Map<ResourceRef, Integer> removedSince(ItemCounts before){
        TreeMap<ResourceRef, Integer> removed = new TreeMap<>(Comparator.comparing(ResourceRef::key));
        before.items().forEach(item -> {
            int count = before.count(item) - count(item);
            if(count > 0) removed.put(item, count);
        });
        return removed;
    }
}
