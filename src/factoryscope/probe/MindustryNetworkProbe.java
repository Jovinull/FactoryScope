package factoryscope.probe;

import arc.struct.Seq;
import factoryscope.area.*;
import factoryscope.model.*;
import factoryscope.network.*;
import mindustry.gen.*;
import mindustry.game.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.distribution.*;
import mindustry.world.blocks.production.*;
import mindustry.world.blocks.storage.*;
import mindustry.world.consumers.*;
import mindustry.world.meta.*;

import java.util.*;

/** Extracts verified item topology from a Mindustry world without calling transfer methods. */
public final class MindustryNetworkProbe{
    private MindustryNetworkProbe(){
    }

    public static ItemNetwork scan(AreaSelection selection, Team viewer){
        Seq<Building> selected = AreaProbe.collect(selection, viewer);
        Map<BuildingRef, Building> buildings = new TreeMap<>(Comparator
            .comparingInt((BuildingRef ref) -> ref.tileX).thenComparingInt(ref -> ref.tileY)
            .thenComparing(ref -> ref.blockId).thenComparingInt(ref -> ref.teamId));
        for(Building build : selected) buildings.put(AreaProbe.refOf(build), build);

        List<NetworkPort> ports = new ArrayList<>();
        List<NetworkEdge> edges = new ArrayList<>();
        List<NetworkPort> boundary = new ArrayList<>();
        List<BuildingRef> unsupported = new ArrayList<>();
        List<ResourceRef> resources = new ArrayList<>();
        Map<Building, BuildingRef> refs = new IdentityHashMap<>();
        buildings.forEach((ref, build) -> refs.put(build, ref));

        for(var entry : buildings.entrySet()){
            Building build = entry.getValue();
            BuildingRef ref = entry.getKey();
            if(isKnownTransport(build)){
                addPorts(ports, ref);
                addInternal(edges, build, ref);
            }else if(isEndpoint(build)){
                addPorts(ports, ref);
            }else if(isUnknownTransport(build)){
                unsupported.add(ref);
            }
            collectResources(build, resources);
        }

        for(var entry : buildings.entrySet()){
            Building source = entry.getValue();
            BuildingRef sourceRef = entry.getKey();
            for(NetworkSide side : outputSides(source)){
                Building neighbor = source.nearby(side.ordinal());
                if(neighbor == null || neighbor.team != viewer) continue;
                NetworkPort out = output(sourceRef, side);
                BuildingRef targetRef = refs.get(neighbor);
                if(targetRef == null){
                    boundary.add(out);
                }else if(inputSides(neighbor).contains(side.opposite()) && acceptsTopologyFrom(neighbor, source)){
                    edges.add(new NetworkEdge(out, input(targetRef, side.opposite()), outputConstraint(source), false));
                }
            }
        }

        addBridgeEdges(edges, boundary, buildings, refs, viewer);
        addDuctBridgeEdges(edges, boundary, buildings, refs, viewer);
        return new ItemNetwork(new NetworkGraph(ports, edges), boundary, unsupported, resources);
    }

    private static void addBridgeEdges(List<NetworkEdge> edges, List<NetworkPort> boundary,
                                       Map<BuildingRef, Building> selected, Map<Building, BuildingRef> refs, Team viewer){
        for(var entry : selected.entrySet()){
            if(!(entry.getValue() instanceof ItemBridge.ItemBridgeBuild bridge)) continue;
            if(bridge.link < 0) continue;
            Tile linkedTile = mindustry.Vars.world.tile(bridge.link);
            Building linked = linkedTile == null ? null : linkedTile.build;
            if(!(linked instanceof ItemBridge.ItemBridgeBuild) || linked.team != viewer) continue;
            BuildingRef target = refs.get(linked);
            NetworkPort from = output(entry.getKey(), NetworkSide.rotation(bridge.rotation));
            if(target == null) boundary.add(from);
            else edges.add(new NetworkEdge(from, input(target, NetworkSide.rotation(linked.rotation)), ItemConstraint.any(), false));
        }
    }

    private static void addDuctBridgeEdges(List<NetworkEdge> edges, List<NetworkPort> boundary,
                                           Map<BuildingRef, Building> selected, Map<Building, BuildingRef> refs, Team viewer){
        for(var entry : selected.entrySet()){
            if(!(entry.getValue() instanceof DirectionBridge.DirectionBridgeBuild bridge)) continue;
            DirectionBridge.DirectionBridgeBuild linked = bridge.findLink();
            if(linked == null || linked.team != viewer) continue;
            NetworkPort from = output(entry.getKey(), NetworkSide.rotation(bridge.rotation));
            BuildingRef target = refs.get(linked);
            if(target == null) boundary.add(from);
            else edges.add(new NetworkEdge(from, input(target, NetworkSide.rotation(linked.rotation).opposite()), ItemConstraint.any(), false));
        }
    }

    private static void addPorts(List<NetworkPort> ports, BuildingRef ref){
        for(NetworkSide side : NetworkSide.values()){
            ports.add(input(ref, side));
            ports.add(output(ref, side));
        }
    }

    private static NetworkPort input(BuildingRef ref, NetworkSide side){ return new NetworkPort(ref, side, "in"); }
    private static NetworkPort output(BuildingRef ref, NetworkSide side){ return new NetworkPort(ref, side, "out"); }

    private static void addInternal(List<NetworkEdge> edges, Building build, BuildingRef ref){
        if(build instanceof Junction.JunctionBuild){
            for(NetworkSide side : NetworkSide.values()) add(edges, ref, side, side.opposite(), ItemConstraint.any(), false);
        }else if(build instanceof Sorter.SorterBuild sorter){
            ResourceRef selected = itemRef(sorter.sortItem);
            for(NetworkSide incoming : NetworkSide.values()){
                NetworkSide straight = incoming.opposite();
                if(selected == null){
                    if(((Sorter)sorter.block).invert){
                        add(edges, ref, incoming, straight, ItemConstraint.any(), false);
                    }else{
                        add(edges, ref, incoming, left(incoming), ItemConstraint.any(), true);
                        add(edges, ref, incoming, right(incoming), ItemConstraint.any(), true);
                    }
                }else{
                    boolean inverted = ((Sorter)sorter.block).invert;
                    ItemConstraint straightItems = inverted ? ItemConstraint.except(selected) : ItemConstraint.only(selected);
                    ItemConstraint sideItems = inverted ? ItemConstraint.only(selected) : ItemConstraint.except(selected);
                    add(edges, ref, incoming, straight, straightItems, false);
                    add(edges, ref, incoming, left(incoming), sideItems, true);
                    add(edges, ref, incoming, right(incoming), sideItems, true);
                }
            }
        }else if(build instanceof DuctRouter.DuctRouterBuild router){
            NetworkSide forward = NetworkSide.rotation(build.rotation);
            NetworkSide back = forward.opposite();
            ResourceRef selected = itemRef(router.sortItem);
            if(selected == null){
                add(edges, ref, back, forward, ItemConstraint.any(), false);
                add(edges, ref, back, left(forward), ItemConstraint.any(), true);
                add(edges, ref, back, right(forward), ItemConstraint.any(), true);
            }else{
                add(edges, ref, back, forward, ItemConstraint.only(selected), false);
                add(edges, ref, back, left(forward), ItemConstraint.except(selected), true);
                add(edges, ref, back, right(forward), ItemConstraint.except(selected), true);
            }
        }else if(build instanceof Router.RouterBuild){
            for(NetworkSide incoming : NetworkSide.values()) for(NetworkSide out : NetworkSide.values()){
                if(out != incoming) add(edges, ref, incoming, out, ItemConstraint.any(), true);
            }
        }else if(build instanceof OverflowGate.OverflowGateBuild){
            //The gate chooses straight or side exits from receiver availability. All are structural possibilities.
            for(NetworkSide incoming : NetworkSide.values()){
                add(edges, ref, incoming, incoming.opposite(), ItemConstraint.any(), true);
                add(edges, ref, incoming, left(incoming), ItemConstraint.any(), true);
                add(edges, ref, incoming, right(incoming), ItemConstraint.any(), true);
            }
        }else if(build instanceof Duct.DuctBuild || build instanceof Conveyor.ConveyorBuild
            || build instanceof DirectionBridge.DirectionBridgeBuild){
            NetworkSide forward = NetworkSide.rotation(build.rotation);
            for(NetworkSide input : NetworkSide.values()) if(input != forward) add(edges, ref, input, forward, ItemConstraint.any(), false);
        }else if(build instanceof ItemBridge.ItemBridgeBuild){
            //The remote edge is handled from the stored link. Local dumping is structural but may use any side.
            for(NetworkSide input : NetworkSide.values()) for(NetworkSide out : NetworkSide.values()) if(out != input)
                add(edges, ref, input, out, ItemConstraint.any(), true);
        }else if(build instanceof Unloader.UnloaderBuild unloader){
            ItemConstraint items = unloader.sortItem == null ? ItemConstraint.any() : ItemConstraint.only(itemRef(unloader.sortItem));
            for(NetworkSide input : NetworkSide.values()) for(NetworkSide out : NetworkSide.values()) if(out != input)
                add(edges, ref, input, out, items, true);
        }else if(build instanceof OverflowDuct.OverflowDuctBuild){
            NetworkSide forward = NetworkSide.rotation(build.rotation);
            NetworkSide back = forward.opposite();
            add(edges, ref, back, forward, ItemConstraint.any(), true);
            add(edges, ref, back, left(forward), ItemConstraint.any(), true);
            add(edges, ref, back, right(forward), ItemConstraint.any(), true);
        }
    }

    private static void add(List<NetworkEdge> edges, BuildingRef ref, NetworkSide from, NetworkSide to,
                            ItemConstraint constraint, boolean conditional){
        edges.add(new NetworkEdge(input(ref, from), output(ref, to), constraint, conditional));
    }

    private static NetworkSide left(NetworkSide side){ return NetworkSide.rotation(side.ordinal() + 1); }
    private static NetworkSide right(NetworkSide side){ return NetworkSide.rotation(side.ordinal() - 1); }

    private static boolean isKnownTransport(Building build){
        return build instanceof Conveyor.ConveyorBuild || build instanceof Duct.DuctBuild || build instanceof Junction.JunctionBuild
            || build instanceof Router.RouterBuild || build instanceof Sorter.SorterBuild || build instanceof DuctRouter.DuctRouterBuild || build instanceof OverflowGate.OverflowGateBuild
            || build instanceof ItemBridge.ItemBridgeBuild || build instanceof DirectionBridge.DirectionBridgeBuild || build instanceof Unloader.UnloaderBuild
            || build instanceof OverflowDuct.OverflowDuctBuild;
    }

    private static boolean isEndpoint(Building build){
        return build instanceof GenericCrafter.GenericCrafterBuild || build instanceof Drill.DrillBuild
            || build instanceof StorageBlock.StorageBuild || build instanceof CoreBlock.CoreBuild;
    }

    private static boolean isUnknownTransport(Building build){
        return (build.block.group == BlockGroup.transportation && build.block.hasItems) || build instanceof MassDriver.MassDriverBuild;
    }

    private static EnumSet<NetworkSide> outputSides(Building build){
        if(build instanceof Conveyor.ConveyorBuild || build instanceof Duct.DuctBuild
            || build instanceof DirectionBridge.DirectionBridgeBuild)
            return EnumSet.of(NetworkSide.rotation(build.rotation));
        if(build instanceof OverflowDuct.OverflowDuctBuild){
            NetworkSide forward = NetworkSide.rotation(build.rotation);
            return EnumSet.of(forward, left(forward), right(forward));
        }
        if(build instanceof DuctRouter.DuctRouterBuild){
            NetworkSide forward = NetworkSide.rotation(build.rotation);
            return EnumSet.of(forward, left(forward), right(forward));
        }
        if(build instanceof Junction.JunctionBuild || build instanceof Sorter.SorterBuild || build instanceof Router.RouterBuild
            || build instanceof OverflowGate.OverflowGateBuild || build instanceof ItemBridge.ItemBridgeBuild)
            return EnumSet.allOf(NetworkSide.class);
        if(build instanceof GenericCrafter.GenericCrafterBuild || build instanceof Drill.DrillBuild
            || build instanceof StorageBlock.StorageBuild || build instanceof CoreBlock.CoreBuild || build instanceof Unloader.UnloaderBuild)
            return EnumSet.allOf(NetworkSide.class);
        return EnumSet.noneOf(NetworkSide.class);
    }

    private static EnumSet<NetworkSide> inputSides(Building build){
        if(build instanceof Conveyor.ConveyorBuild || build instanceof Duct.DuctBuild
            || build instanceof DirectionBridge.DirectionBridgeBuild){
            EnumSet<NetworkSide> sides = EnumSet.allOf(NetworkSide.class);
            sides.remove(NetworkSide.rotation(build.rotation));
            return sides;
        }
        if(build instanceof OverflowDuct.OverflowDuctBuild) return EnumSet.of(NetworkSide.rotation(build.rotation).opposite());
        if(build instanceof DuctRouter.DuctRouterBuild) return EnumSet.of(NetworkSide.rotation(build.rotation).opposite());
        if(build instanceof Junction.JunctionBuild || build instanceof Sorter.SorterBuild || build instanceof Router.RouterBuild
            || build instanceof OverflowGate.OverflowGateBuild || build instanceof ItemBridge.ItemBridgeBuild
            || build instanceof Unloader.UnloaderBuild || isEndpoint(build)) return EnumSet.allOf(NetworkSide.class);
        return EnumSet.noneOf(NetworkSide.class);
    }

    private static ResourceRef itemRef(Item item){
        return item == null ? null : new ResourceRef(ResourceKind.item, item.name, item.localizedName);
    }

    private static ItemConstraint outputConstraint(Building build){
        if(build instanceof Unloader.UnloaderBuild unloader && unloader.sortItem != null) return ItemConstraint.only(itemRef(unloader.sortItem));
        if(build instanceof GenericCrafter.GenericCrafterBuild crafter && crafter.block instanceof GenericCrafter block && block.outputItem != null)
            return ItemConstraint.only(itemRef(block.outputItem.item));
        return ItemConstraint.any();
    }

    private static void collectResources(Building build, List<ResourceRef> resources){
        if(build instanceof Sorter.SorterBuild sorter && sorter.sortItem != null) resources.add(itemRef(sorter.sortItem));
        if(build instanceof DuctRouter.DuctRouterBuild router && router.sortItem != null) resources.add(itemRef(router.sortItem));
        if(build instanceof Unloader.UnloaderBuild unloader && unloader.sortItem != null) resources.add(itemRef(unloader.sortItem));
        if(build.block instanceof GenericCrafter crafter && crafter.outputItem != null) resources.add(itemRef(crafter.outputItem.item));
        for(Consume consume : build.block.consumers){
            if(consume instanceof ConsumeItems items){
                for(ItemStack stack : items.items) resources.add(itemRef(stack.item));
            }else if(consume instanceof ConsumeItemDynamic dynamic){
                for(ItemStack stack : dynamic.items.get(build)) resources.add(itemRef(stack.item));
            }else if(consume instanceof ConsumeItemFilter filter){
                Item item = filter.getConsumed(build);
                if(item != null) resources.add(itemRef(item));
            }
        }
    }

    private static boolean acceptsTopologyFrom(Building target, Building source){
        if(target instanceof Unloader.UnloaderBuild){
            return source instanceof StorageBlock.StorageBuild || source instanceof CoreBlock.CoreBuild;
        }
        return true;
    }
}
