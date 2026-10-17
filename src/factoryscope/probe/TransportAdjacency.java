package factoryscope.probe;

import factoryscope.network.NetworkSide;
import mindustry.gen.Building;

import java.util.ArrayList;
import java.util.List;

/**
 * Edge-adjacent buildings with the side of the source building that touches each neighbor.
 *
 * <p>Mindustry reports every tile on a multi-tile building's edge. Keeping this conversion in one
 * adapter helper prevents item and liquid topology from disagreeing about which side that tile represents.
 */
final class TransportAdjacency{
    private TransportAdjacency(){
    }

    static List<Neighbor> of(Building build){
        List<Neighbor> result = new ArrayList<>();
        if(build.tile == null) return result;

        int offset = -(build.block.size - 1) / 2;
        int minX = build.tile.x + offset;
        int minY = build.tile.y + offset;
        int maxX = minX + build.block.size - 1;
        int maxY = minY + build.block.size - 1;
        build.eachEdge(tile -> {
            Building neighbor = tile.build;
            if(neighbor == null || neighbor == build) return;
            if(tile.x < minX) result.add(new Neighbor(neighbor, NetworkSide.west));
            else if(tile.x > maxX) result.add(new Neighbor(neighbor, NetworkSide.east));
            else if(tile.y < minY) result.add(new Neighbor(neighbor, NetworkSide.south));
            else if(tile.y > maxY) result.add(new Neighbor(neighbor, NetworkSide.north));
        });
        return result;
    }

    static final class Neighbor{
        final Building building;
        final NetworkSide side;

        Neighbor(Building building, NetworkSide side){
            this.building = building;
            this.side = side;
        }
    }
}
