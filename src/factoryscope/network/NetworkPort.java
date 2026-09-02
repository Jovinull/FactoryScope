package factoryscope.network;

import factoryscope.area.*;

import java.util.*;

/** A transport endpoint. The channel distinguishes independent paths inside one building. */
public final class NetworkPort implements Comparable<NetworkPort>{
    public final BuildingRef building;
    public final NetworkSide side;
    public final String channel;

    public NetworkPort(BuildingRef building, NetworkSide side, String channel){
        this.building = Objects.requireNonNull(building, "building");
        this.side = Objects.requireNonNull(side, "side");
        this.channel = channel == null ? "" : channel;
    }

    @Override
    public int compareTo(NetworkPort other){
        int byX = Integer.compare(building.tileX, other.building.tileX);
        if(byX != 0) return byX;
        int byY = Integer.compare(building.tileY, other.building.tileY);
        if(byY != 0) return byY;
        int byTeam = Integer.compare(building.teamId, other.building.teamId);
        if(byTeam != 0) return byTeam;
        int byBlock = building.blockId.compareTo(other.building.blockId);
        if(byBlock != 0) return byBlock;
        int bySide = Integer.compare(side.ordinal(), other.side.ordinal());
        return bySide != 0 ? bySide : channel.compareTo(other.channel);
    }

    @Override
    public boolean equals(Object other){
        if(this == other) return true;
        if(!(other instanceof NetworkPort port)) return false;
        return building.equals(port.building) && side == port.side && channel.equals(port.channel);
    }

    @Override
    public int hashCode(){
        return Objects.hash(building, side, channel);
    }
}
