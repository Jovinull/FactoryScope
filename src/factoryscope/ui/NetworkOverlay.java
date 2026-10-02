package factoryscope.ui;

import arc.graphics.g2d.*;
import factoryscope.model.*;
import factoryscope.network.*;
import factoryscope.trace.*;
import mindustry.*;
import mindustry.graphics.*;

import java.util.*;

/** World-space drawing for structural routes. It is inert unless Network view asks for it. */
final class NetworkOverlay{
    private static final int MAX_DRAWN_EDGES = 750;
    private final ItemNetwork network;
    private final ResourceRef item;
    private final SupplyTrace trace;
    private final Set<NetworkEdge> highlighted = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<NetworkEdge> traversed = Collections.newSetFromMap(new IdentityHashMap<>());

    NetworkOverlay(ItemNetwork network, ResourceRef item){
        this(network, item, null);
    }

    NetworkOverlay(ItemNetwork network, ResourceRef item, SupplyTrace trace){
        this.network = network;
        this.item = item;
        this.trace = trace;
        if(trace != null){
            traversed.addAll(trace.traversedEdges);
            for(TraceEndpoint endpoint : trace.endpoints){
                endpoint.path.addEdgesTo(highlighted);
            }
        }
    }

    void draw(){
        Draw.z(Layer.overlayUI - 0.01f);
        int drawn = 0;
        for(NetworkEdge edge : network.graph.edges){
            if(drawn >= MAX_DRAWN_EDGES || (item != null && !edge.items.allows(item))) continue;
            float x1 = worldX(edge.from), y1 = worldY(edge.from);
            float x2 = worldX(edge.to), y2 = worldY(edge.to);
            boolean selected = trace == null || traversed.contains(edge);
            boolean representative = highlighted.contains(edge);
            Draw.color(edge.conditional ? Pal.lightOrange : representative || trace == null ? Pal.accent : selected ? Pal.accent : Pal.gray);
            Draw.alpha(representative || trace == null ? 1f : selected ? 0.55f : 0.25f);
            Lines.stroke(edge.conditional ? representative ? 2f : 1.4f : representative || trace == null ? 2.4f : selected ? 1.5f : 1f);
            Lines.line(x1, y1, x2, y2);
            if(trace == null || representative) arrow(x1, y1, x2, y2);
            drawn++;
        }
        Collection<NetworkPort> outgoingBoundaries = trace == null ? network.boundaryPorts
            : trace.direction == TraceDirection.output ? trace.boundaryContinuations : List.of();
        Collection<NetworkPort> incomingBoundaries = trace == null ? network.boundaryInputs
            : trace.direction == TraceDirection.input ? trace.boundaryContinuations : List.of();
        Draw.color(Pal.accent);
        for(NetworkPort port : outgoingBoundaries){
            float x = worldX(port), y = worldY(port);
            float dx = port.side.dx * Vars.tilesize * 0.3f, dy = port.side.dy * Vars.tilesize * 0.3f;
            Lines.stroke(2f);
            Lines.line(x, y, x + dx, y + dy);
            Fill.circle(x + dx, y + dy, 2.5f);
        }
        for(NetworkPort port : incomingBoundaries){
            float x = worldX(port), y = worldY(port);
            float dx = -port.side.dx * Vars.tilesize * 0.3f, dy = -port.side.dy * Vars.tilesize * 0.3f;
            Lines.stroke(2f);
            Lines.line(x, y, x + dx, y + dy);
            Fill.circle(x + dx, y + dy, 2.5f);
        }
        if(trace != null){
            Draw.color(Pal.lightOrange);
            for(TraceEndpoint endpoint : trace.endpoints){
                float x = (endpoint.building.tileX + 0.5f) * Vars.tilesize;
                float y = (endpoint.building.tileY + 0.5f) * Vars.tilesize;
                Lines.stroke(2f);
                Lines.circle(x, y, Vars.tilesize * 0.35f);
            }
            Draw.color(Pal.remove);
            for(var building : trace.structuralDeadEnds){
                float x = (building.tileX + 0.5f) * Vars.tilesize;
                float y = (building.tileY + 0.5f) * Vars.tilesize;
                Lines.stroke(2f);
                Lines.line(x - 3f, y - 3f, x + 3f, y + 3f);
                Lines.line(x - 3f, y + 3f, x + 3f, y - 3f);
            }
            for(NetworkInterruption interruption : trace.unsupportedInterruptions){
                float x = worldX(interruption.port), y = worldY(interruption.port);
                Lines.stroke(2f);
                Lines.line(x - 3f, y - 3f, x + 3f, y + 3f);
                Lines.line(x - 3f, y + 3f, x + 3f, y - 3f);
            }
        }
        Draw.reset();
    }

    private static void arrow(float x1, float y1, float x2, float y2){
        float dx = x2 - x1, dy = y2 - y1;
        float length = (float)Math.sqrt(dx * dx + dy * dy);
        if(length < 0.1f) return;
        float ux = dx / length, uy = dy / length;
        float px = -uy, py = ux;
        float tipX = x2 - ux * 1.5f, tipY = y2 - uy * 1.5f;
        float baseX = tipX - ux * 4.5f, baseY = tipY - uy * 4.5f;
        Fill.tri(tipX, tipY, baseX + px * 2.6f, baseY + py * 2.6f, baseX - px * 2.6f, baseY - py * 2.6f);
    }

    private static float worldX(NetworkPort port){
        return (port.building.tileX + 0.5f + port.side.dx * 0.28f) * Vars.tilesize;
    }

    private static float worldY(NetworkPort port){
        return (port.building.tileY + 0.5f + port.side.dy * 0.28f) * Vars.tilesize;
    }
}
