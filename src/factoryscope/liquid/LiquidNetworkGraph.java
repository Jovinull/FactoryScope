package factoryscope.liquid;

import factoryscope.model.ResourceRef;
import factoryscope.network.NetworkPort;

import java.util.*;

/** Immutable, deterministic, resource-aware directed liquid topology. */
public final class LiquidNetworkGraph{
    public final List<NetworkPort> ports;
    public final List<LiquidNetworkEdge> edges;
    private final Map<NetworkPort, List<LiquidNetworkEdge>> outgoing;
    private final Map<NetworkPort, List<LiquidNetworkEdge>> incoming;

    public LiquidNetworkGraph(Collection<NetworkPort> ports, Collection<LiquidNetworkEdge> edges){
        this.ports = List.copyOf(new TreeSet<>(ports));
        this.edges = List.copyOf(new TreeSet<>(edges));
        Map<NetworkPort, List<LiquidNetworkEdge>> from = new TreeMap<>(), to = new TreeMap<>();
        for(LiquidNetworkEdge edge : this.edges){
            from.computeIfAbsent(edge.from, ignored -> new ArrayList<>()).add(edge);
            to.computeIfAbsent(edge.to, ignored -> new ArrayList<>()).add(edge);
        }
        from.replaceAll((port, list) -> List.copyOf(list));
        to.replaceAll((port, list) -> List.copyOf(list));
        outgoing = Map.copyOf(from);
        incoming = Map.copyOf(to);
    }

    public List<LiquidNetworkEdge> outgoing(NetworkPort port){ return outgoing.getOrDefault(port, List.of()); }
    public List<LiquidNetworkEdge> incoming(NetworkPort port){ return incoming.getOrDefault(port, List.of()); }

    public Set<NetworkPort> reachableFrom(NetworkPort source, ResourceRef liquid){
        return traverse(source, liquid, true);
    }

    public Set<NetworkPort> reaching(NetworkPort target, ResourceRef liquid){
        return traverse(target, liquid, false);
    }

    public boolean isReachable(NetworkPort source, NetworkPort target, ResourceRef liquid){
        return reachableFrom(source, liquid).contains(target);
    }

    private Set<NetworkPort> traverse(NetworkPort start, ResourceRef liquid, boolean forward){
        if(start == null || liquid == null || liquid.kind != factoryscope.model.ResourceKind.liquid || liquid.id == null){
            return Set.of();
        }
        Set<NetworkPort> visited = new TreeSet<>();
        ArrayDeque<NetworkPort> pending = new ArrayDeque<>();
        visited.add(start);
        pending.add(start);
        while(!pending.isEmpty()){
            NetworkPort port = pending.removeFirst();
            List<LiquidNetworkEdge> adjacent = forward ? outgoing(port) : incoming(port);
            for(LiquidNetworkEdge edge : adjacent){
                if(!edge.liquids.allows(liquid)) continue;
                NetworkPort next = forward ? edge.to : edge.from;
                if(visited.add(next)) pending.addLast(next);
            }
        }
        return Collections.unmodifiableSet(visited);
    }
}
