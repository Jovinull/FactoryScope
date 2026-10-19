package factoryscope.network;

import java.util.*;

/** A directed structural possibility, never an observation of current item flow. */
public final class NetworkEdge implements Comparable<NetworkEdge>{
    public final NetworkPort from;
    public final NetworkPort to;
    public final ResourceConstraint resources;
    public final boolean conditional;

    public NetworkEdge(NetworkPort from, NetworkPort to, ResourceConstraint items, boolean conditional){
        this.from = Objects.requireNonNull(from, "from");
        this.to = Objects.requireNonNull(to, "to");
        this.resources = Objects.requireNonNull(items, "resources");
        this.conditional = conditional;
    }

    @Override
    public int compareTo(NetworkEdge other){
        int fromOrder = from.compareTo(other.from);
        if(fromOrder != 0) return fromOrder;
        int toOrder = to.compareTo(other.to);
        if(toOrder != 0) return toOrder;
        int resourceOrder = resources.toString().compareTo(other.resources.toString());
        return resourceOrder != 0 ? resourceOrder : Boolean.compare(conditional, other.conditional);
    }

    @Override
    public String toString(){
        return from + " -> " + to + " [" + resources + (conditional ? ", conditional" : "") + "]";
    }
}
