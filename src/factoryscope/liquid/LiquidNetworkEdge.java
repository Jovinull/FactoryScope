package factoryscope.liquid;

import factoryscope.network.NetworkPort;

import java.util.Objects;

/** Directed structural possibility; it does not represent liquid currently moving. */
public final class LiquidNetworkEdge implements Comparable<LiquidNetworkEdge>{
    public final NetworkPort from;
    public final NetworkPort to;
    public final LiquidConstraint liquids;

    public LiquidNetworkEdge(NetworkPort from, NetworkPort to, LiquidConstraint liquids){
        this.from = Objects.requireNonNull(from, "from");
        this.to = Objects.requireNonNull(to, "to");
        this.liquids = Objects.requireNonNull(liquids, "liquids");
    }

    @Override
    public int compareTo(LiquidNetworkEdge other){
        int order = from.compareTo(other.from);
        if(order != 0) return order;
        order = to.compareTo(other.to);
        return order != 0 ? order : liquids.toString().compareTo(other.liquids.toString());
    }

    @Override
    public boolean equals(Object other){
        return other instanceof LiquidNetworkEdge edge && from.equals(edge.from) && to.equals(edge.to)
            && liquids.toString().equals(edge.liquids.toString());
    }

    @Override
    public int hashCode(){
        return Objects.hash(from, to, liquids.toString());
    }

    @Override
    public String toString(){
        return from + " -> " + to + " [" + liquids + "]";
    }
}
