package factoryscope.network;

/** A cardinal side of a tile, using Mindustry's rotation order. */
public enum NetworkSide{
    east(1, 0), north(0, 1), west(-1, 0), south(0, -1);

    public final int dx;
    public final int dy;

    NetworkSide(int dx, int dy){
        this.dx = dx;
        this.dy = dy;
    }

    public NetworkSide opposite(){
        return values()[(ordinal() + 2) % 4];
    }

    public static NetworkSide rotation(int rotation){
        return values()[Math.floorMod(rotation, 4)];
    }
}
