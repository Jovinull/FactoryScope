package factoryscope.network;

import factoryscope.area.BuildingRef;

import java.util.Objects;

/** A known port that touches a transport whose internal routing is not modeled. */
public final class NetworkInterruption implements Comparable<NetworkInterruption>{
    public enum Direction{incoming, outgoing}

    public final NetworkPort port;
    public final BuildingRef transport;
    public final Direction direction;

    public NetworkInterruption(NetworkPort port, BuildingRef transport, Direction direction){
        this.port = Objects.requireNonNull(port, "port");
        this.transport = Objects.requireNonNull(transport, "transport");
        this.direction = Objects.requireNonNull(direction, "direction");
    }

    @Override
    public int compareTo(NetworkInterruption other){
        int byPort = port.compareTo(other.port);
        if(byPort != 0) return byPort;
        int byDirection = direction.compareTo(other.direction);
        if(byDirection != 0) return byDirection;
        int byBuilding = Integer.compare(transport.tileX, other.transport.tileX);
        if(byBuilding != 0) return byBuilding;
        byBuilding = Integer.compare(transport.tileY, other.transport.tileY);
        return byBuilding != 0 ? byBuilding : transport.blockId.compareTo(other.transport.blockId);
    }
}
