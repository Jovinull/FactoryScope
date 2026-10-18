package factoryscope.area;

import java.util.Objects;

/**
 * Evidence that FactoryScope could not inspect a live building while constructing topology.
 *
 * <p>This is deliberately distinct from unsupported transport: the block may be a supported
 * vanilla block, but a probe operation failed unexpectedly. The exception remains in the log;
 * reports retain only deterministic, player-safe context.
 */
public final class TopologyProbeFailure implements Comparable<TopologyProbeFailure>{
    public final BuildingRef building;
    public final String operation;

    public TopologyProbeFailure(BuildingRef building, String operation){
        this.building = Objects.requireNonNull(building, "building");
        this.operation = Objects.requireNonNull(operation, "operation");
    }

    @Override
    public int compareTo(TopologyProbeFailure other){
        int result = Integer.compare(building.tileX, other.building.tileX);
        if(result != 0) return result;
        result = Integer.compare(building.tileY, other.building.tileY);
        if(result != 0) return result;
        result = building.blockId.compareTo(other.building.blockId);
        if(result != 0) return result;
        result = Integer.compare(building.teamId, other.building.teamId);
        return result != 0 ? result : operation.compareTo(other.operation);
    }

    @Override
    public boolean equals(Object object){
        return object instanceof TopologyProbeFailure other
            && building.equals(other.building) && operation.equals(other.operation);
    }

    @Override
    public int hashCode(){
        return Objects.hash(building, operation);
    }
}
