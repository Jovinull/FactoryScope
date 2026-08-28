package factoryscope.area;

/**
 * A normalized, inclusive rectangle of <em>tile</em> coordinates.
 *
 * <p>Every coordinate here is a tile index, never a world position: mixing the two silently is the
 * single easiest way to reintroduce the class of defect that shipped in 0.1.0. Conversion happens at
 * the edges, in the overlay that reads the pointer and in the probe that queries the world.
 *
 * <p>Both endpoints are included, and the four drag directions all normalize to the same selection.
 */
public final class AreaSelection{
    public final int minX, minY, maxX, maxY;

    private AreaSelection(int minX, int minY, int maxX, int maxY){
        this.minX = minX;
        this.minY = minY;
        this.maxX = maxX;
        this.maxY = maxY;
    }

    /** Normalizes a drag from any corner to any other corner. */
    public static AreaSelection of(int x1, int y1, int x2, int y2){
        return new AreaSelection(
            Math.min(x1, x2), Math.min(y1, y2),
            Math.max(x1, x2), Math.max(y1, y2));
    }

    /** The selection clamped to a world of the given tile dimensions, or null when it lies entirely outside. */
    public AreaSelection clampedTo(int worldWidth, int worldHeight){
        if(worldWidth <= 0 || worldHeight <= 0) return null;
        int cminX = Math.max(minX, 0), cminY = Math.max(minY, 0);
        int cmaxX = Math.min(maxX, worldWidth - 1), cmaxY = Math.min(maxY, worldHeight - 1);
        if(cminX > cmaxX || cminY > cmaxY) return null;
        return new AreaSelection(cminX, cminY, cmaxX, cmaxY);
    }

    public int width(){
        return maxX - minX + 1;
    }

    public int height(){
        return maxY - minY + 1;
    }

    public int tileCount(){
        return width() * height();
    }

    /** True for a drag that never left its starting tile, which is a click rather than an area. */
    public boolean single(){
        return tileCount() == 1;
    }

    public boolean contains(int tileX, int tileY){
        return tileX >= minX && tileX <= maxX && tileY >= minY && tileY <= maxY;
    }

    /**
     * Whether a block footprint overlaps this selection.
     *
     * <p>Mindustry centres a multiblock on its origin tile with the offset {@code -(size - 1) / 2},
     * which is integer division and so is not symmetric for even sizes - a 2x2 block occupies its own
     * tile and the one above and to the right. This mirrors {@code Tile.setBlock} exactly rather than
     * re-deriving the centering.
     */
    public boolean intersectsFootprint(int originTileX, int originTileY, int size){
        int offset = -(size - 1) / 2;
        int fromX = originTileX + offset, fromY = originTileY + offset;
        return fromX <= maxX && fromX + size - 1 >= minX
            && fromY <= maxY && fromY + size - 1 >= minY;
    }

    @Override
    public boolean equals(Object other){
        if(this == other) return true;
        if(!(other instanceof AreaSelection area)) return false;
        return minX == area.minX && minY == area.minY && maxX == area.maxX && maxY == area.maxY;
    }

    @Override
    public int hashCode(){
        return ((minX * 31 + minY) * 31 + maxX) * 31 + maxY;
    }

    @Override
    public String toString(){
        return "(" + minX + "," + minY + ")-(" + maxX + "," + maxY + ")";
    }
}
