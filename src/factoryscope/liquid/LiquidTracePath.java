package factoryscope.liquid;

import factoryscope.network.NetworkPort;

import java.util.*;

/** One deterministic shortest structural route, represented by a shared predecessor map. */
public final class LiquidTracePath{
    private final NetworkPort start, end;
    private final Map<NetworkPort, LiquidNetworkEdge> predecessor;
    private final boolean towardEnd;

    private LiquidTracePath(NetworkPort start, NetworkPort end,
                            Map<NetworkPort, LiquidNetworkEdge> predecessor, boolean towardEnd){
        this.start = start;
        this.end = end;
        this.predecessor = predecessor;
        this.towardEnd = towardEnd;
    }

    static LiquidTracePath toTarget(NetworkPort source, NetworkPort target, Map<NetworkPort, LiquidNetworkEdge> next){
        return new LiquidTracePath(source, target, next, true);
    }

    static LiquidTracePath fromSource(NetworkPort source, NetworkPort target, Map<NetworkPort, LiquidNetworkEdge> previous){
        return new LiquidTracePath(source, target, previous, false);
    }

    public List<NetworkPort> ports(){
        List<NetworkPort> result = new ArrayList<>();
        Set<NetworkPort> seen = new HashSet<>();
        if(towardEnd){
            NetworkPort current = start;
            result.add(current);
            while(!current.equals(end) && seen.add(current)){
                LiquidNetworkEdge edge = predecessor.get(current);
                if(edge == null) break;
                current = edge.to;
                result.add(current);
            }
        }else{
            NetworkPort current = end;
            result.add(current);
            while(!current.equals(start) && seen.add(current)){
                LiquidNetworkEdge edge = predecessor.get(current);
                if(edge == null) break;
                current = edge.from;
                result.add(current);
            }
            Collections.reverse(result);
        }
        return List.copyOf(result);
    }

    public List<LiquidNetworkEdge> edges(){
        List<LiquidNetworkEdge> result = new ArrayList<>();
        Set<NetworkPort> seen = new HashSet<>();
        if(towardEnd){
            NetworkPort current = start;
            while(!current.equals(end) && seen.add(current)){
                LiquidNetworkEdge edge = predecessor.get(current);
                if(edge == null) break;
                result.add(edge);
                current = edge.to;
            }
        }else{
            NetworkPort current = end;
            while(!current.equals(start) && seen.add(current)){
                LiquidNetworkEdge edge = predecessor.get(current);
                if(edge == null) break;
                result.add(edge);
                current = edge.from;
            }
            Collections.reverse(result);
        }
        return List.copyOf(result);
    }
}
