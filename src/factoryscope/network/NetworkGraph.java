package factoryscope.network;

import factoryscope.model.*;

import java.util.*;

/** Immutable, deterministic directed item topology. */
public final class NetworkGraph{
    public final List<NetworkPort> ports;
    public final List<NetworkEdge> edges;
    private final Map<NetworkPort, List<NetworkEdge>> outgoing;
    private final Map<NetworkPort, List<NetworkEdge>> incoming;

    public NetworkGraph(Collection<NetworkPort> ports, Collection<NetworkEdge> edges){
        TreeSet<NetworkPort> orderedPorts = new TreeSet<>(ports);
        TreeSet<NetworkEdge> orderedEdges = new TreeSet<>(edges);
        this.ports = List.copyOf(orderedPorts);
        this.edges = List.copyOf(orderedEdges);
        Map<NetworkPort, List<NetworkEdge>> bySource = new TreeMap<>();
        Map<NetworkPort, List<NetworkEdge>> byTarget = new TreeMap<>();
        for(NetworkEdge edge : this.edges){
            bySource.computeIfAbsent(edge.from, ignored -> new ArrayList<>()).add(edge);
            byTarget.computeIfAbsent(edge.to, ignored -> new ArrayList<>()).add(edge);
        }
        bySource.replaceAll((port, list) -> List.copyOf(list));
        byTarget.replaceAll((port, list) -> List.copyOf(list));
        this.outgoing = Map.copyOf(bySource);
        this.incoming = Map.copyOf(byTarget);
    }

    public boolean isReachable(NetworkPort source, NetworkPort target, ResourceRef item){
        return reachableFrom(source, item).contains(target);
    }

    public Set<NetworkPort> reachableFrom(NetworkPort source, ResourceRef item){
        if(source == null || item == null) return Set.of();
        Set<NetworkPort> visited = new TreeSet<>();
        ArrayDeque<NetworkPort> pending = new ArrayDeque<>();
        visited.add(source);
        pending.add(source);
        while(!pending.isEmpty()){
            NetworkPort current = pending.removeFirst();
            for(NetworkEdge edge : outgoing.getOrDefault(current, List.of())){
                if(edge.items.allows(item) && visited.add(edge.to)) pending.addLast(edge.to);
            }
        }
        return Collections.unmodifiableSet(visited);
    }

    /** Ports that can structurally reach {@code target} for one item. */
    public Set<NetworkPort> reaching(NetworkPort target, ResourceRef item){
        if(target == null || item == null) return Set.of();
        Set<NetworkPort> visited = new TreeSet<>();
        ArrayDeque<NetworkPort> pending = new ArrayDeque<>();
        visited.add(target);
        pending.add(target);
        while(!pending.isEmpty()){
            NetworkPort current = pending.removeFirst();
            for(NetworkEdge edge : incoming.getOrDefault(current, List.of())){
                if(edge.items.allows(item) && visited.add(edge.from)) pending.addLast(edge.from);
            }
        }
        return Collections.unmodifiableSet(visited);
    }

    /** Weak components are for summaries only; item travel always uses directed reachability. */
    public List<Set<NetworkPort>> weakComponents(){
        Map<NetworkPort, Set<NetworkPort>> adjacent = new TreeMap<>();
        for(NetworkEdge edge : edges){
            adjacent.computeIfAbsent(edge.from, ignored -> new TreeSet<>()).add(edge.to);
            adjacent.computeIfAbsent(edge.to, ignored -> new TreeSet<>()).add(edge.from);
        }
        List<Set<NetworkPort>> result = new ArrayList<>();
        Set<NetworkPort> visited = new TreeSet<>();
        for(NetworkPort first : adjacent.keySet()){
            if(!visited.add(first)) continue;
            Set<NetworkPort> component = new TreeSet<>();
            ArrayDeque<NetworkPort> pending = new ArrayDeque<>();
            pending.add(first);
            while(!pending.isEmpty()){
                NetworkPort current = pending.removeFirst();
                component.add(current);
                for(NetworkPort next : adjacent.getOrDefault(current, Set.of())) if(visited.add(next)) pending.addLast(next);
            }
            result.add(Collections.unmodifiableSet(component));
        }
        return List.copyOf(result);
    }
}
