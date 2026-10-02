package factoryscope.trace;

import factoryscope.network.*;

import java.util.*;

/** One deterministic structural route, represented by a shared BFS predecessor map. */
public final class TracePath{
    private final NetworkPort start;
    private final NetworkPort end;
    private final Map<NetworkPort, NetworkEdge> predecessor;
    private final boolean towardEnd;

    private TracePath(NetworkPort start, NetworkPort end, Map<NetworkPort, NetworkEdge> predecessor, boolean towardEnd){
        this.start = Objects.requireNonNull(start, "start");
        this.end = Objects.requireNonNull(end, "end");
        this.predecessor = Objects.requireNonNull(predecessor, "predecessor");
        this.towardEnd = towardEnd;
    }

    static TracePath toTarget(NetworkPort source, NetworkPort target, Map<NetworkPort, NetworkEdge> nextToTarget){
        return new TracePath(source, target, nextToTarget, true);
    }

    static TracePath fromSource(NetworkPort source, NetworkPort target, Map<NetworkPort, NetworkEdge> fromSource){
        return new TracePath(source, target, fromSource, false);
    }

    public List<NetworkPort> ports(){
        List<NetworkPort> result = new ArrayList<>();
        Set<NetworkPort> seen = new HashSet<>();
        if(towardEnd){
            NetworkPort current = start;
            result.add(current);
            while(!current.equals(end) && seen.add(current)){
                NetworkEdge edge = predecessor.get(current);
                if(edge == null) break;
                current = edge.to;
                result.add(current);
            }
        }else{
            NetworkPort current = end;
            result.add(current);
            while(!current.equals(start) && seen.add(current)){
                NetworkEdge edge = predecessor.get(current);
                if(edge == null) break;
                current = edge.from;
                result.add(current);
            }
            Collections.reverse(result);
        }
        return List.copyOf(result);
    }

    public List<NetworkEdge> edges(){
        List<NetworkEdge> result = new ArrayList<>();
        Set<NetworkPort> seen = new HashSet<>();
        if(towardEnd){
            NetworkPort current = start;
            while(!current.equals(end) && seen.add(current)){
                NetworkEdge edge = predecessor.get(current);
                if(edge == null) break;
                result.add(edge);
                current = edge.to;
            }
        }else{
            NetworkPort current = end;
            while(!current.equals(start) && seen.add(current)){
                NetworkEdge edge = predecessor.get(current);
                if(edge == null) break;
                result.add(edge);
                current = edge.from;
            }
            Collections.reverse(result);
        }
        return List.copyOf(result);
    }

    public boolean addEdgesTo(Set<NetworkEdge> edges){
        boolean added = false;
        Set<NetworkPort> seen = new HashSet<>();
        if(towardEnd){
            NetworkPort current = start;
            while(!current.equals(end) && seen.add(current)){
                NetworkEdge edge = predecessor.get(current);
                if(edge == null || !edges.add(edge)) break;
                added = true;
                current = edge.to;
            }
        }else{
            NetworkPort current = end;
            while(!current.equals(start) && seen.add(current)){
                NetworkEdge edge = predecessor.get(current);
                if(edge == null || !edges.add(edge)) break;
                added = true;
                current = edge.from;
            }
        }
        return added;
    }

    public boolean conditional(){
        Set<NetworkPort> seen = new HashSet<>();
        if(towardEnd){
            NetworkPort current = start;
            while(!current.equals(end) && seen.add(current)){
                NetworkEdge edge = predecessor.get(current);
                if(edge == null) break;
                if(edge.conditional) return true;
                current = edge.to;
            }
        }else{
            NetworkPort current = end;
            while(!current.equals(start) && seen.add(current)){
                NetworkEdge edge = predecessor.get(current);
                if(edge == null) break;
                if(edge.conditional) return true;
                current = edge.from;
            }
        }
        return false;
    }
}
