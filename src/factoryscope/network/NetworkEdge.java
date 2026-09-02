package factoryscope.network;

import java.util.*;

/** A directed structural possibility, never an observation of current item flow. */
public final class NetworkEdge implements Comparable<NetworkEdge>{
    public final NetworkPort from;
    public final NetworkPort to;
    public final ItemConstraint items;
    public final boolean conditional;

    public NetworkEdge(NetworkPort from, NetworkPort to, ItemConstraint items, boolean conditional){
        this.from = Objects.requireNonNull(from, "from");
        this.to = Objects.requireNonNull(to, "to");
        this.items = Objects.requireNonNull(items, "items");
        this.conditional = conditional;
    }

    @Override
    public int compareTo(NetworkEdge other){
        int fromOrder = from.compareTo(other.from);
        if(fromOrder != 0) return fromOrder;
        int toOrder = to.compareTo(other.to);
        if(toOrder != 0) return toOrder;
        int itemOrder = items.toString().compareTo(other.items.toString());
        return itemOrder != 0 ? itemOrder : Boolean.compare(conditional, other.conditional);
    }
}
