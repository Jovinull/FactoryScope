package factoryscope.liquid;

import factoryscope.area.BuildingRef;
import factoryscope.network.NetworkPort;
import factoryscope.model.ResourceRef;

import java.util.Objects;

/** A resource-compatible route touches a building whose liquid role could not be fully enumerated. */
public final class LiquidUncertainty implements Comparable<LiquidUncertainty>{
    public enum Direction{incoming, outgoing}
    public enum Kind{incompleteRequirement, incompleteProduct, hiddenLink}

    /** The known-side port from which this uncertain continuation is relevant. */
    public final NetworkPort port;
    public final BuildingRef building;
    public final Direction direction;
    public final Kind kind;
    /** Constraint proved by the known side; this is not a claim that the unknown side accepts/produces it. */
    public final LiquidConstraint liquids;

    public LiquidUncertainty(NetworkPort port, BuildingRef building, Direction direction, Kind kind,
                             LiquidConstraint liquids){
        this.port = Objects.requireNonNull(port, "port");
        this.building = Objects.requireNonNull(building, "building");
        this.direction = Objects.requireNonNull(direction, "direction");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.liquids = Objects.requireNonNull(liquids, "liquids");
    }

    public boolean allows(ResourceRef liquid){ return liquids.allows(liquid); }

    @Override
    public int compareTo(LiquidUncertainty other){
        int result = port.compareTo(other.port);
        if(result != 0) return result;
        result = direction.compareTo(other.direction);
        if(result != 0) return result;
        result = Integer.compare(building.tileX, other.building.tileX);
        if(result != 0) return result;
        result = Integer.compare(building.tileY, other.building.tileY);
        if(result != 0) return result;
        result = building.blockId.compareTo(other.building.blockId);
        if(result != 0) return result;
        result = kind.compareTo(other.kind);
        return result != 0 ? result : liquids.toString().compareTo(other.liquids.toString());
    }
}
