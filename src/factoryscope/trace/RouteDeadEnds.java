package factoryscope.trace;

import factoryscope.area.BuildingRef;
import factoryscope.network.NetworkPort;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Where a traced route stops inside the area, shared by item and liquid traces.
 *
 * <p>A route enters a building through the ports a search reaches from a port of another building. The
 * building is a dead end when, for one such entry, nothing the search reaches from it inside the building
 * leads on to another building or past the area's known edge. A side that no route uses, such as the free side
 * of a conveyor whose back is fed, belongs to an entry that does lead on and is not reported by itself.
 */
public final class RouteDeadEnds{
    private RouteDeadEnds(){
    }

    /**
     * @param visited every port the trace search reached
     * @param next the ports one search step away from a port, already limited to the traced resource
     * @param continuesElsewhere ports whose route goes on somewhere the graph does not show, such as an area boundary
     * @param excluded buildings that are never dead ends: the target, endpoints, and buildings without full evidence
     */
    public static Set<BuildingRef> find(Collection<NetworkPort> visited, Function<NetworkPort, List<NetworkPort>> next,
                                        Predicate<NetworkPort> continuesElsewhere, Predicate<BuildingRef> excluded){
        Walk walk = new Walk(next, continuesElsewhere);
        Set<BuildingRef> result = new HashSet<>();
        List<BuildingRef> fed = new ArrayList<>(), unfed = new ArrayList<>();
        for(NetworkPort from : visited){
            fed.clear();
            unfed.clear();
            for(NetworkPort entry : walk.step(from)){
                if(entry.building.equals(from.building) || excluded.test(entry.building)) continue;
                (walk.leadsOn(entry) ? fed : unfed).add(entry.building);
            }
            //several ports entered from the same place are one route: it ends only if none of them leads on
            for(BuildingRef building : unfed) if(!fed.contains(building)) result.add(building);
        }
        return result;
    }

    private static final class Walk{
        private final Function<NetworkPort, List<NetworkPort>> next;
        private final Predicate<NetworkPort> continuesElsewhere;
        //a route stays inside one building for a step or two, so a list is cheaper than hashing ports
        private final List<NetworkPort> inside = new ArrayList<>();

        Walk(Function<NetworkPort, List<NetworkPort>> next, Predicate<NetworkPort> continuesElsewhere){
            this.next = next;
            this.continuesElsewhere = continuesElsewhere;
        }

        List<NetworkPort> step(NetworkPort port){
            return next.apply(port);
        }

        /** Whether the route entering a building at {@code entry} gets out of it again. */
        boolean leadsOn(NetworkPort entry){
            inside.clear();
            inside.add(entry);
            for(int i = 0; i < inside.size(); i++){
                NetworkPort port = inside.get(i);
                for(NetworkPort following : step(port)){
                    if(!following.building.equals(entry.building)) return true;
                    if(!inside.contains(following)) inside.add(following);
                }
                if(continuesElsewhere.test(port)) return true;
            }
            return false;
        }
    }
}
