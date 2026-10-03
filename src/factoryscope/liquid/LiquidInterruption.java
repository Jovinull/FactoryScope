package factoryscope.liquid;

import factoryscope.area.BuildingRef;
import factoryscope.model.ResourceRef;
import factoryscope.network.NetworkPort;

import java.util.Objects;

/** Resource-scoped evidence that a route touches a transport whose internal liquid semantics are unknown. */
public final class LiquidInterruption implements Comparable<LiquidInterruption>{
    public enum Direction{incoming, outgoing}

    public final NetworkPort port;
    public final BuildingRef transport;
    public final Direction direction;
    /** Constraint established by the modeled side; this does not claim what the unsupported side can carry. */
    public final LiquidConstraint liquids;

    public LiquidInterruption(NetworkPort port, BuildingRef transport, Direction direction, LiquidConstraint liquids){
        this.port = Objects.requireNonNull(port, "port");
        this.transport = Objects.requireNonNull(transport, "transport");
        this.direction = Objects.requireNonNull(direction, "direction");
        this.liquids = Objects.requireNonNull(liquids, "liquids");
    }

    public boolean allows(ResourceRef liquid){ return liquids.allows(liquid); }

    @Override
    public int compareTo(LiquidInterruption other){
        int result = port.compareTo(other.port);
        if(result != 0) return result;
        result = direction.compareTo(other.direction);
        if(result != 0) return result;
        result = Integer.compare(transport.tileX, other.transport.tileX);
        if(result != 0) return result;
        result = Integer.compare(transport.tileY, other.transport.tileY);
        if(result != 0) return result;
        result = transport.blockId.compareTo(other.transport.blockId);
        return result != 0 ? result : liquids.toString().compareTo(other.liquids.toString());
    }
}
