package factoryscope.ui;

import arc.graphics.g2d.*;
import factoryscope.model.*;
import factoryscope.network.*;
import mindustry.*;
import mindustry.graphics.*;

/** World-space drawing for structural routes. It is inert unless Network view asks for it. */
final class NetworkOverlay{
    private static final int MAX_DRAWN_EDGES = 750;
    private final ItemNetwork network;
    private final ResourceRef item;

    NetworkOverlay(ItemNetwork network, ResourceRef item){
        this.network = network;
        this.item = item;
    }

    void draw(){
        Draw.z(Layer.overlayUI - 0.01f);
        int drawn = 0;
        for(NetworkEdge edge : network.graph.edges){
            if(drawn >= MAX_DRAWN_EDGES || (item != null && !edge.items.allows(item))) continue;
            float x1 = worldX(edge.from), y1 = worldY(edge.from);
            float x2 = worldX(edge.to), y2 = worldY(edge.to);
            Draw.color(edge.conditional ? Pal.lightOrange : Pal.accent);
            Lines.stroke(edge.conditional ? 1.4f : 2f);
            Lines.line(x1, y1, x2, y2);
            arrow(x1, y1, x2, y2);
            drawn++;
        }
        Draw.color(Pal.accent);
        for(NetworkPort port : network.boundaryPorts){
            float x = worldX(port), y = worldY(port);
            float dx = port.side.dx * Vars.tilesize * 0.3f, dy = port.side.dy * Vars.tilesize * 0.3f;
            Lines.stroke(2f);
            Lines.line(x, y, x + dx, y + dy);
            Fill.circle(x + dx, y + dy, 2.5f);
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
