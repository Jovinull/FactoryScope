package factoryscope.probe;

import arc.math.geom.Geometry;
import factoryscope.network.NetworkSide;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.world.Tile;
import mindustry.world.blocks.distribution.*;

import java.util.*;

import static mindustry.Vars.*;

/**
 * The engine's rules for transports that move resources to a building they do not touch: item and liquid
 * bridges, directional (duct and reinforced liquid) bridges, and mass drivers.
 *
 * <p>Each target is the building the engine itself would hand resources to, decided from configuration and
 * placement only. Each source list is every building whose own target is the given one, so a receiving end
 * knows its remote entries without waiting for the engine's per-frame bookkeeping.
 */
final class TransportLinks{
    private static final Comparator<Building> POSITION = Comparator.comparingInt(Building::pos);

    private TransportLinks(){
    }

    /** The configured bridge an ItemBridge or LiquidBridge transfers to, as both endpoints' {@code linkValid} allow. */
    static ItemBridge.ItemBridgeBuild bridgeTarget(ItemBridge.ItemBridgeBuild bridge, Team viewer){
        if(bridge.link < 0 || bridge.tile == null || !(bridge.block instanceof ItemBridge block)) return null;
        Tile linkedTile = world.tile(bridge.link);
        Building linked = linkedTile == null ? null : linkedTile.build;
        if(!(linked instanceof ItemBridge.ItemBridgeBuild target) || linked.team != viewer || bridge.team != viewer) return null;
        return block.linkValid(bridge.tile, linkedTile) && ((ItemBridge)target.block).linkValid(bridge.tile, linkedTile)
            ? target : null;
    }

    /**
     * Bridges whose valid link is {@code bridge}: those the engine has registered as incoming, plus any bridge of
     * the same block in range on its axes, which the engine registers on that bridge's next update.
     */
    static List<ItemBridge.ItemBridgeBuild> bridgeSources(ItemBridge.ItemBridgeBuild bridge, Team viewer){
        Set<ItemBridge.ItemBridgeBuild> result = new TreeSet<>(POSITION);
        for(int i = 0; i < bridge.incoming.size; i++){
            Tile tile = world.tile(bridge.incoming.items[i]);
            if(tile != null && tile.build instanceof ItemBridge.ItemBridgeBuild source && bridgeTarget(source, viewer) == bridge){
                result.add(source);
            }
        }
        if(bridge.tile != null && bridge.block instanceof ItemBridge block){
            for(int direction = 0; direction < 4; direction++){
                for(int distance = 1; distance <= block.range; distance++){
                    Tile tile = bridge.tile.nearby(Geometry.d4x(direction) * distance, Geometry.d4y(direction) * distance);
                    if(tile != null && tile.build instanceof ItemBridge.ItemBridgeBuild source && tile.build != bridge
                        && bridgeTarget(source, viewer) == bridge){
                        result.add(source);
                    }
                }
            }
        }
        return List.copyOf(result);
    }

    /** The directional bridge a Duct Bridge or Reinforced Bridge Conduit sends to, from the engine's own search. */
    static DirectionBridge.DirectionBridgeBuild directionTarget(DirectionBridge.DirectionBridgeBuild bridge, Team viewer){
        if(bridge.tile == null || bridge.team != viewer) return null;
        DirectionBridge.DirectionBridgeBuild target = bridge.findLink();
        return target != null && target.team == viewer ? target : null;
    }

    /** Directional bridges whose forward search ends at {@code bridge}. At most one arrives from each side. */
    static List<DirectionBridge.DirectionBridgeBuild> directionSources(DirectionBridge.DirectionBridgeBuild bridge, Team viewer){
        List<DirectionBridge.DirectionBridgeBuild> result = new ArrayList<>();
        if(bridge.tile == null || !(bridge.block instanceof DirectionBridge block)) return result;
        for(int direction = 0; direction < 4; direction++){
            for(int distance = 1; distance <= block.range; distance++){
                Tile tile = bridge.tile.nearby(Geometry.d4x(direction) * distance, Geometry.d4y(direction) * distance);
                if(tile != null && tile.build instanceof DirectionBridge.DirectionBridgeBuild source && source.block == bridge.block
                    && source.rotation == (direction + 2) % 4 && directionTarget(source, viewer) == bridge){
                    result.add(source);
                    break;
                }
            }
        }
        result.sort(POSITION);
        return result;
    }

    /** The mass driver a driver fires at: {@code MassDriverBuild.linkValid()}, which is not accessible from here. */
    static MassDriver.MassDriverBuild driverTarget(MassDriver.MassDriverBuild driver, Team viewer){
        if(driver.link == -1 || driver.team != viewer || !(driver.block instanceof MassDriver block)) return null;
        Building linked = world.build(driver.link);
        return linked instanceof MassDriver.MassDriverBuild target && linked.block == driver.block && linked.team == viewer
            && driver.within(linked, block.range) ? target : null;
    }

    /** Mass drivers of the viewer's team that fire at {@code driver}. */
    static List<MassDriver.MassDriverBuild> driverSources(MassDriver.MassDriverBuild driver, Team viewer){
        List<MassDriver.MassDriverBuild> result = new ArrayList<>();
        if(driver.team != viewer || !(driver.block instanceof MassDriver block)) return result;
        indexer.eachBlock(viewer, driver.x, driver.y, block.range,
            other -> other != driver && other instanceof MassDriver.MassDriverBuild && other.block == driver.block,
            other -> {
                if(driverTarget((MassDriver.MassDriverBuild)other, viewer) == driver) result.add((MassDriver.MassDriverBuild)other);
            });
        result.sort(POSITION);
        return result;
    }

    /** The side of {@code from} facing {@code to}; for a diagonal link the horizontal side is used. */
    static NetworkSide sideTo(Building from, Building to){
        if(to.tile.x > from.tile.x) return NetworkSide.east;
        if(to.tile.x < from.tile.x) return NetworkSide.west;
        if(to.tile.y > from.tile.y) return NetworkSide.north;
        return NetworkSide.south;
    }
}
