package factoryscope.ui;

import arc.graphics.g2d.*;
import arc.math.geom.Vec2;
import factoryscope.area.BuildingRef;
import factoryscope.power.*;
import mindustry.Vars;
import mindustry.graphics.*;

import java.util.*;

/** Draws established, undirected electrical connections; line weight and color never encode flow. */
final class PowerOverlay{
    private static final int MAX_CONNECTIONS = 1200;
    private final PowerGridReport report;
    private final Map<BuildingRef, Vec2> positions = new HashMap<>();

    PowerOverlay(PowerGridReport report){
        this.report = report;
        for(PowerGridResult result : report.grids){
            for(PowerConnection connection : result.snapshot.connections){
                capture(connection.first);
                capture(connection.second);
            }
        }
        for(PowerDiodeLink diode : report.diodeLinks) capture(diode.diode);
    }

    private void capture(BuildingRef ref){
        if(positions.containsKey(ref)) return;
        //The report is a snapshot. Re-resolving here would let a later hidden destruction alter its
        //overlay. Mindustry v160.5 places a Building at Tile.drawx/drawy: tile world coordinates plus
        //Block.offset, whose afterPatch value is derived from the captured block size.
        float offset = ((ref.size + 1) % 2) * Vars.tilesize / 2f;
        positions.put(ref, new Vec2(ref.tileX * Vars.tilesize + offset, ref.tileY * Vars.tilesize + offset));
    }

    void draw(){
        Draw.z(Layer.overlayUI - 0.01f);
        Draw.color(Pal.accent);
        Lines.stroke(1.6f);
        int drawn = 0;
        for(PowerGridResult result : report.grids){
            for(PowerConnection connection : result.snapshot.connections){
                if(drawn >= MAX_CONNECTIONS) break;
                Vec2 first = positions.get(connection.first), second = positions.get(connection.second);
                if(first == null || second == null) continue;
                Lines.line(first.x, first.y, second.x, second.y);
                drawn++;
            }
            if(drawn >= MAX_CONNECTIONS) break;
        }
        //A diode is marked as a special cross-grid connector, not drawn as a normal PowerGraph edge.
        Draw.color(Pal.lightOrange);
        for(PowerDiodeLink diode : report.diodeLinks){
            Vec2 point = positions.get(diode.diode);
            if(point != null) Fill.circle(point.x, point.y, 3.5f);
        }
        Draw.reset();
    }
}
