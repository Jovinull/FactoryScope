package factoryscope.power;

import factoryscope.area.BuildingRef;

import java.util.Objects;

/** Undirected established electrical connection. It does not represent directional power flow. */
public final class PowerConnection{
    public final BuildingRef first;
    public final BuildingRef second;

    public PowerConnection(BuildingRef a, BuildingRef b){
        Objects.requireNonNull(a, "a");
        Objects.requireNonNull(b, "b");
        if(compare(a, b) <= 0){
            first = a;
            second = b;
        }else{
            first = b;
            second = a;
        }
    }

    private static int compare(BuildingRef a, BuildingRef b){
        int x = Integer.compare(a.tileX, b.tileX);
        if(x != 0) return x;
        int y = Integer.compare(a.tileY, b.tileY);
        if(y != 0) return y;
        int block = a.blockId.compareTo(b.blockId);
        return block != 0 ? block : Integer.compare(a.teamId, b.teamId);
    }

    @Override
    public boolean equals(Object other){
        return other instanceof PowerConnection edge && first.equals(edge.first) && second.equals(edge.second);
    }

    @Override
    public int hashCode(){
        return Objects.hash(first, second);
    }
}
