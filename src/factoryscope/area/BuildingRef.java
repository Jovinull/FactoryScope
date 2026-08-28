package factoryscope.area;

import java.util.*;

/**
 * Enough immutable information to find one building again, and to notice when what is standing there
 * is no longer the same building.
 *
 * <p>An area report outlives the buildings it describes: the player reads it while the factory keeps
 * running, and a block can be destroyed, rebuilt or captured in between. Holding live {@code Building}
 * references in the result would keep dead entities reachable and would let a rebuilt block silently
 * take the place of the one that was analysed, so the result stores coordinates and identity instead
 * and resolves the live building only at the moment it is needed.
 */
public final class BuildingRef{
    public final int tileX, tileY;
    /** Internal Mindustry block name, e.g. {@code "silicon-smelter"}. */
    public final String blockId;
    /** Localized block name, for display only. */
    public final String blockName;
    /** Block footprint in tiles. */
    public final int size;
    public final int teamId;

    public BuildingRef(int tileX, int tileY, String blockId, String blockName, int size, int teamId){
        this.tileX = tileX;
        this.tileY = tileY;
        this.blockId = Objects.requireNonNull(blockId, "blockId");
        this.blockName = Objects.requireNonNull(blockName, "blockName");
        this.size = size;
        this.teamId = teamId;
    }

    @Override
    public boolean equals(Object other){
        if(this == other) return true;
        if(!(other instanceof BuildingRef ref)) return false;
        return tileX == ref.tileX && tileY == ref.tileY && teamId == ref.teamId && blockId.equals(ref.blockId);
    }

    @Override
    public int hashCode(){
        return Objects.hash(tileX, tileY, teamId, blockId);
    }

    @Override
    public String toString(){
        return blockId + "@" + tileX + "," + tileY;
    }
}
