package factoryscope.ui;

import arc.graphics.g2d.*;
import factoryscope.area.BuildingRef;
import factoryscope.liquid.*;
import factoryscope.model.ResourceRef;
import factoryscope.network.*;
import factoryscope.trace.TraceEndpointKind;
import factoryscope.trace.TraceDirection;
import mindustry.Vars;
import mindustry.graphics.*;

import java.util.*;

/** Static structural liquid connectivity; it deliberately has no animated or quantitative flow encoding. */
final class LiquidOverlay{
    private static final int MAX_DRAWN_EDGES = 900;
    private final LiquidNetwork network;
    private final ResourceRef liquid;
    private final LiquidTrace trace;
    private final List<LiquidNetworkEdge> drawEdges;
    private final Set<LiquidNetworkEdge> traversed = new HashSet<>(), representative = new HashSet<>();

    LiquidOverlay(LiquidNetwork network, ResourceRef liquid, LiquidTrace trace){
        this.network = network;
        this.liquid = liquid;
        this.trace = trace;
        LinkedHashSet<LiquidNetworkEdge> selected = new LinkedHashSet<>();
        if(trace != null){
            traversed.addAll(trace.traversedEdges);
            // The full explored region is already represented by traversedEdges. Emphasize one stable
            // representative endpoint path; expanding every producer path here can be quadratic on a
            // long shared trunk, while the list still marks every endpoint individually below.
            if(!trace.endpoints.isEmpty()){
                TraceEndpointKind wanted = trace.direction == TraceDirection.input
                    ? TraceEndpointKind.producer : TraceEndpointKind.consumer;
                LiquidTraceEndpoint representativeEndpoint = trace.endpoints.stream()
                    .filter(endpoint -> endpoint.kind == wanted).findFirst().orElse(trace.endpoints.get(0));
                for(LiquidNetworkEdge edge : representativeEndpoint.path.edges()){
                    representative.add(edge);
                    addIfVisible(selected, edge);
                }
            }
            for(LiquidNetworkEdge edge : trace.traversedEdges) addIfVisible(selected, edge);
        }
        for(LiquidNetworkEdge edge : network.graph.edges) addIfVisible(selected, edge);
        this.drawEdges = List.copyOf(selected);
    }

    void draw(){
        Draw.z(Layer.overlayUI - 0.01f);
        int drawn = 0;
        for(LiquidNetworkEdge edge : drawEdges){
            boolean active = trace == null || traversed.contains(edge);
            boolean path = representative.contains(edge);
            Draw.color(path || trace == null ? Pal.accent : active ? Pal.accent : Pal.gray);
            Draw.alpha(path || trace == null ? 0.9f : active ? 0.55f : 0.22f);
            Lines.stroke(path ? 2.4f : trace == null ? 1.7f : 1f);
            Lines.line(x(edge.from), y(edge.from), x(edge.to), y(edge.to));
            drawn++;
        }
        Draw.color(Pal.accent);
        if(trace != null){
            for(LiquidTraceEndpoint endpoint : trace.endpoints) marker(endpoint.building, false);
            for(BuildingRef deadEnd : trace.structuralDeadEnds) marker(deadEnd, true);
            for(LiquidInterruption interruption : trace.unsupportedInterruptions) marker(interruption.transport, true);
            for(LiquidUncertainty uncertainty : trace.incompleteConnections) marker(uncertainty.building, true);
        }
        Collection<NetworkPort> boundaries;
        if(trace == null){
            TreeSet<NetworkPort> all = new TreeSet<>(network.boundaryInputs);
            all.addAll(network.boundaryPorts);
            boundaries = all;
        }else{
            boundaries = trace.boundaryContinuations;
        }
        for(NetworkPort port : boundaries){
            float px = centerX(port.building), py = centerY(port.building);
            Lines.stroke(2f);
            Lines.line(px, py, px + port.side.dx * Vars.tilesize * 0.35f, py + port.side.dy * Vars.tilesize * 0.35f);
        }
        Draw.reset();
    }

    private void addIfVisible(Set<LiquidNetworkEdge> selected, LiquidNetworkEdge edge){
        if(selected.size() < MAX_DRAWN_EDGES && (liquid == null || edge.liquids.allows(liquid))) selected.add(edge);
    }

    private static void marker(BuildingRef ref, boolean cross){
        float x = centerX(ref), y = centerY(ref), radius = Vars.tilesize * 0.34f;
        Lines.stroke(2f);
        if(cross){
            Lines.line(x - radius, y - radius, x + radius, y + radius);
            Lines.line(x - radius, y + radius, x + radius, y - radius);
        }else Lines.circle(x, y, radius);
    }

    private static float x(NetworkPort port){ return centerX(port.building) + port.side.dx * Vars.tilesize * 0.25f; }
    private static float y(NetworkPort port){ return centerY(port.building) + port.side.dy * Vars.tilesize * 0.25f; }
    private static float centerX(BuildingRef ref){ return (ref.tileX + 0.5f) * Vars.tilesize; }
    private static float centerY(BuildingRef ref){ return (ref.tileY + 0.5f) * Vars.tilesize; }
}
