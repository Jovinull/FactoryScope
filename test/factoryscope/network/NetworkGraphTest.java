package factoryscope.network;

import factoryscope.area.*;
import factoryscope.model.*;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class NetworkGraphTest{
    private static final ResourceRef copper = new ResourceRef(ResourceKind.item, "copper", "Copper");
    private static final ResourceRef lead = new ResourceRef(ResourceKind.item, "lead", "Lead");

    @Test
    void directedReachabilityDoesNotInventAReverseConveyor(){
        NetworkPort a = port(1), b = port(2), c = port(3);
        NetworkGraph graph = graph(a, b, c, edge(a, b), edge(b, c));

        assertTrue(graph.isReachable(a, c, copper));
        assertFalse(graph.isReachable(c, a, copper));
    }

    @Test
    void resourceConstraintsChangeReachability(){
        NetworkPort in = port(1), selected = port(2), other = port(3);
        NetworkGraph graph = graph(in, selected, other,
            new NetworkEdge(in, selected, ItemConstraint.only(copper), false),
            new NetworkEdge(in, other, ItemConstraint.except(copper), false));

        assertTrue(graph.isReachable(in, selected, copper));
        assertFalse(graph.isReachable(in, other, copper));
        assertFalse(graph.isReachable(in, selected, lead));
        assertTrue(graph.isReachable(in, other, lead));
    }

    @Test
    void cyclesTerminateAndKeepTheirReachablePorts(){
        NetworkPort a = port(1), b = port(2), c = port(3);
        NetworkGraph graph = graph(a, b, c, edge(a, b), edge(b, c), edge(c, a));

        assertEquals(Set.of(a, b, c), graph.reachableFrom(a, copper));
    }

    @Test
    void weakComponentsDoNotConfuseDisconnectedNetworks(){
        NetworkPort a = port(1), b = port(2), c = port(3), d = port(4);
        NetworkGraph graph = graph(a, b, c, d, edge(a, b), edge(c, d));

        assertEquals(2, graph.weakComponents().size());
        assertEquals(Set.of(a, b), graph.weakComponents().get(0));
        assertEquals(Set.of(c, d), graph.weakComponents().get(1));
    }

    @Test
    void insertionOrderCannotChangeTheGraphOrder(){
        NetworkPort a = port(1), b = port(2), c = port(3);
        NetworkGraph first = graph(c, a, b, edge(b, c), edge(a, b));
        NetworkGraph second = graph(a, b, c, edge(a, b), edge(b, c));

        assertEquals(second.ports, first.ports);
        assertEquals(second.edges.size(), first.edges.size());
        for(int i = 0; i < first.edges.size(); i++) assertEquals(0, first.edges.get(i).compareTo(second.edges.get(i)));
    }

    private static NetworkGraph graph(NetworkPort a, NetworkPort b, NetworkPort c, NetworkEdge... edges){
        return new NetworkGraph(List.of(a, b, c), List.of(edges));
    }

    private static NetworkGraph graph(NetworkPort a, NetworkPort b, NetworkPort c, NetworkPort d, NetworkEdge... edges){
        return new NetworkGraph(List.of(a, b, c, d), List.of(edges));
    }

    private static NetworkEdge edge(NetworkPort from, NetworkPort to){
        return new NetworkEdge(from, to, ItemConstraint.any(), false);
    }

    private static NetworkPort port(int x){
        return new NetworkPort(new BuildingRef(x, 1, "test", "Test", 1, 1), NetworkSide.east, "");
    }
}
