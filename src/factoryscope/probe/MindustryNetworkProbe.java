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
        return scan(selection, viewer, Map.of());
    }

    public static ItemNetwork scan(AreaSelection selection, Team viewer, Map<BuildingRef, FactorySnapshot> snapshots){
        Seq<Building> selected = AreaProbe.collect(selection, viewer);
        Map<BuildingRef, Building> buildings = new TreeMap<>(Comparator
            .comparingInt((BuildingRef ref) -> ref.tileX).thenComparingInt(ref -> ref.tileY)
            .thenComparing(ref -> ref.blockId).thenComparingInt(ref -> ref.teamId));
        for(Building build : selected) buildings.put(AreaProbe.refOf(build), build);

        List<NetworkPort> ports = new ArrayList<>();
        List<NetworkEdge> edges = new ArrayList<>();
        List<NetworkPort> boundary = new ArrayList<>();
        List<NetworkPort> boundaryInputs = new ArrayList<>();
        List<BuildingRef> unsupported = new ArrayList<>();
        List<NetworkInterruption> interruptions = new ArrayList<>();
        List<BuildingRef> storage = new ArrayList<>();
        List<ResourceRef> resources = new ArrayList<>();
        Set<Building> itemSources = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<Building> itemSinks = Collections.newSetFromMap(new IdentityHashMap<>());
        Map<Building, List<ResourceRef>> itemProducts = new IdentityHashMap<>();
        Map<Building, BuildingRef> refs = new IdentityHashMap<>();
        buildings.forEach((ref, build) -> refs.put(build, ref));

        for(var entry : buildings.entrySet()){
            Building build = entry.getValue();
            BuildingRef ref = entry.getKey();
            FactorySnapshot snapshot = snapshots.get(ref);
            if(snapshot == null){
                try{
                    snapshot = MindustryFactoryProbe.probe(build);
                }catch(Exception ignored){
                    //Topology can still be reported when a custom diagnostic consumer cannot be read.
                }
            }
            boolean storageEndpoint = build instanceof StorageBlock.StorageBuild || build instanceof CoreBlock.CoreBuild;
            boolean itemConsumer = snapshot != null && snapshot.inputs.stream()
                .anyMatch(input -> input.kind == ResourceKind.item);
            boolean itemProducer = snapshot != null && !snapshot.producedItems.isEmpty();
            if(isKnownTransport(build)){
                addPorts(ports, ref);
                addInternal(edges, build, ref, viewer);
            }else if(isEndpoint(build) || (!isUnknownTransport(build) && (itemConsumer || itemProducer))){
                addPorts(ports, ref);
                if(storageEndpoint) storage.add(ref);
            }else if(isUnknownTransport(build)){
                unsupported.add(ref);
            }
            if(itemProducer){
                itemSources.add(build);
                itemProducts.put(build, snapshot.producedItems);
            }
            if(itemConsumer || storageEndpoint) itemSinks.add(build);
            collectResources(build, snapshot, resources);
        }

        for(var entry : buildings.entrySet()){
            Building source = entry.getValue();
            BuildingRef sourceRef = entry.getKey();
            for(Adjacent adjacent : adjacent(source)){
                NetworkSide side = adjacent.side;
                if(!outputSides(source, viewer, itemSources).contains(side)) continue;
                Building neighbor = adjacent.building;
                if(neighbor == null || neighbor.team != viewer) continue;
                NetworkPort out = output(sourceRef, side);
                BuildingRef targetRef = refs.get(neighbor);
                if(isUnknownTransport(neighbor)){
                    interruptions.add(new NetworkInterruption(out, targetRef == null ? AreaProbe.refOf(neighbor) : targetRef,
                        NetworkInterruption.Direction.outgoing));
                }else if(inputSides(neighbor, viewer, itemSinks).contains(side.opposite()) && acceptsTopologyFrom(neighbor, source)){
                    if(targetRef == null){
                        boundary.add(out);
                    }else{
                        edges.add(new NetworkEdge(out, input(targetRef, side.opposite()), outputConstraint(source, itemProducts), false));
                    }
                }
            }
        }

        for(var entry : buildings.entrySet()){
            Building target = entry.getValue();
            BuildingRef targetRef = entry.getKey();
            if(!isKnownTransport(target) && !isEndpoint(target)) continue;
            for(Adjacent adjacent : adjacent(target)){
                Building neighbor = adjacent.building;
                if(neighbor == null || neighbor.team != viewer) continue;
                NetworkSide side = adjacent.side;
                if(!inputSides(target, viewer, itemSinks).contains(side)) continue;

                if(isUnknownTransport(neighbor)){
                    BuildingRef unsupportedRef = refs.getOrDefault(neighbor, AreaProbe.refOf(neighbor));
                    interruptions.add(new NetworkInterruption(input(targetRef, side), unsupportedRef,
                        NetworkInterruption.Direction.incoming));
                }else if(!refs.containsKey(neighbor)
                    && (outputSides(neighbor, viewer, itemSources).contains(side.opposite()) || hasItemOutput(neighbor, itemSources))){
                    boundaryInputs.add(input(targetRef, side));
                }
            }
        }

        addBridgeEdges(edges, boundary, boundaryInputs, buildings, refs, viewer);
        return new ItemNetwork(new NetworkGraph(ports, edges), boundary, boundaryInputs, unsupported,
            interruptions, storage, resources);
    }

    private static List<Adjacent> adjacent(Building build){
        List<Adjacent> result = new ArrayList<>();
        if(build.tile == null) return result;
        int offset = -(build.block.size - 1) / 2;
        int minX = build.tile.x + offset;
        int minY = build.tile.y + offset;
        int maxX = minX + build.block.size - 1;
        int maxY = minY + build.block.size - 1;
        build.eachEdge(tile -> {
            Building neighbor = tile.build;
            if(neighbor == null || neighbor == build) return;
            if(tile.x < minX) result.add(new Adjacent(neighbor, NetworkSide.west));
            else if(tile.x > maxX) result.add(new Adjacent(neighbor, NetworkSide.east));
            else if(tile.y < minY) result.add(new Adjacent(neighbor, NetworkSide.south));
            else if(tile.y > maxY) result.add(new Adjacent(neighbor, NetworkSide.north));
        });
        return result;
    }

    private static final class Adjacent{
        final Building building;
        final NetworkSide side;

        Adjacent(Building building, NetworkSide side){
            this.building = building;
            this.side = side;
        }
    }

    private static void addBridgeEdges(List<NetworkEdge> edges, List<NetworkPort> boundary,
                                       List<NetworkPort> boundaryInputs,
                                       Map<BuildingRef, Building> selected, Map<Building, BuildingRef> refs, Team viewer){
        for(var entry : selected.entrySet()){
            if(!(entry.getValue() instanceof ItemBridge.ItemBridgeBuild bridge)) continue;
            Building linked = validBridgeTarget(bridge, viewer);
            if(linked != null){
                BuildingRef target = refs.get(linked);
                NetworkSide direction = sideTo(bridge, linked);
                NetworkPort from = output(entry.getKey(), direction);
                if(target == null) boundary.add(from);
                else edges.add(new NetworkEdge(from, input(target, direction.opposite()), ItemConstraint.any(), false));
            }

            for(int i = 0; i < bridge.incoming.size; i++){
                Tile sourceTile = mindustry.Vars.world.tile(bridge.incoming.items[i]);
                Building source = sourceTile == null ? null : sourceTile.build;
                if(!(source instanceof ItemBridge.ItemBridgeBuild sourceBridge) || source.team != viewer
                    || refs.containsKey(source) || validBridgeTarget(sourceBridge, viewer) != bridge) continue;
                boundaryInputs.add(input(entry.getKey(), sideTo(bridge, source)));
            }
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

    private static void addInternal(List<NetworkEdge> edges, Building build, BuildingRef ref, Team viewer){
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
            boolean inverted = ((OverflowGate)build.block).invert;
            for(NetworkSide incoming : NetworkSide.values()){
                add(edges, ref, incoming, incoming.opposite(), ItemConstraint.any(), inverted);
                add(edges, ref, incoming, left(incoming), ItemConstraint.any(), !inverted);
                add(edges, ref, incoming, right(incoming), ItemConstraint.any(), !inverted);
            }
        }else if(build instanceof Duct.DuctBuild || build instanceof Conveyor.ConveyorBuild){
            NetworkSide forward = NetworkSide.rotation(build.rotation);
            for(NetworkSide input : NetworkSide.values()) if(input != forward) add(edges, ref, input, forward, ItemConstraint.any(), false);
        }else if(build instanceof ItemBridge.ItemBridgeBuild bridge){
            Building linked = validBridgeTarget(bridge, viewer);
            if(linked != null){
                NetworkSide direction = sideTo(bridge, linked);
                for(NetworkSide input : NetworkSide.values()) add(edges, ref, input, direction, ItemConstraint.any(), false);
            }else{
                for(NetworkSide input : NetworkSide.values()) for(NetworkSide out : NetworkSide.values()) if(out != input)
                    add(edges, ref, input, out, ItemConstraint.any(), true);
            }
        }else if(build instanceof OverflowDuct.OverflowDuctBuild){
            NetworkSide forward = NetworkSide.rotation(build.rotation);
            NetworkSide back = forward.opposite();
            boolean inverted = ((OverflowDuct)build.block).invert;
            add(edges, ref, back, forward, ItemConstraint.any(), inverted);
            add(edges, ref, back, left(forward), ItemConstraint.any(), !inverted);
            add(edges, ref, back, right(forward), ItemConstraint.any(), !inverted);
        }
    }

    private static void add(List<NetworkEdge> edges, BuildingRef ref, NetworkSide from, NetworkSide to,
                            ItemConstraint constraint, boolean conditional){
        edges.add(new NetworkEdge(input(ref, from), output(ref, to), constraint, conditional));
    }

    private static NetworkSide left(NetworkSide side){ return NetworkSide.rotation(side.ordinal() + 1); }
    private static NetworkSide right(NetworkSide side){ return NetworkSide.rotation(side.ordinal() - 1); }

    private static boolean isKnownTransport(Building build){
        return !isExplicitlyUnsupported(build) && (build instanceof Conveyor.ConveyorBuild || build instanceof Duct.DuctBuild || build instanceof Junction.JunctionBuild
            || build instanceof Router.RouterBuild || build instanceof Sorter.SorterBuild || build instanceof DuctRouter.DuctRouterBuild || build instanceof OverflowGate.OverflowGateBuild
            || build instanceof ItemBridge.ItemBridgeBuild
            || build instanceof OverflowDuct.OverflowDuctBuild);
    }

    private static boolean isEndpoint(Building build){
        return build instanceof GenericCrafter.GenericCrafterBuild || build instanceof Drill.DrillBuild
            || build instanceof StorageBlock.StorageBuild || build instanceof CoreBlock.CoreBuild;
    }

    private static boolean isUnknownTransport(Building build){
        return !isKnownTransport(build) && !isEndpoint(build)
            && (isExplicitlyUnsupported(build) || (build.block.group == BlockGroup.transportation && build.block.hasItems)
                || build instanceof MassDriver.MassDriverBuild || build instanceof Unloader.UnloaderBuild);
    }

    private static boolean isExplicitlyUnsupported(Building build){
        return build.block instanceof ArmoredConveyor || (build.block instanceof Duct duct && duct.armored);
    }

    private static EnumSet<NetworkSide> outputSides(Building build, Team viewer, Set<Building> itemSources){
        if(isExplicitlyUnsupported(build)) return EnumSet.noneOf(NetworkSide.class);
        if(build instanceof Conveyor.ConveyorBuild || build instanceof Duct.DuctBuild)
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
            || build instanceof OverflowGate.OverflowGateBuild)
            return EnumSet.allOf(NetworkSide.class);
        if(build instanceof ItemBridge.ItemBridgeBuild bridge)
            return validBridgeTarget(bridge, viewer) == null ? EnumSet.allOf(NetworkSide.class) : EnumSet.noneOf(NetworkSide.class);
        if(itemSources.contains(build))
            return EnumSet.allOf(NetworkSide.class);
        return EnumSet.noneOf(NetworkSide.class);
    }

    private static EnumSet<NetworkSide> inputSides(Building build, Team viewer, Set<Building> itemSinks){
        if(isExplicitlyUnsupported(build)) return EnumSet.noneOf(NetworkSide.class);
        if(build instanceof Conveyor.ConveyorBuild || build instanceof Duct.DuctBuild){
            EnumSet<NetworkSide> sides = EnumSet.allOf(NetworkSide.class);
            sides.remove(NetworkSide.rotation(build.rotation));
            return sides;
        }
        if(build instanceof OverflowDuct.OverflowDuctBuild) return EnumSet.of(NetworkSide.rotation(build.rotation).opposite());
        if(build instanceof DuctRouter.DuctRouterBuild) return EnumSet.of(NetworkSide.rotation(build.rotation).opposite());
        if(build instanceof Junction.JunctionBuild || build instanceof Sorter.SorterBuild || build instanceof Router.RouterBuild
            || build instanceof OverflowGate.OverflowGateBuild || itemSinks.contains(build)) return EnumSet.allOf(NetworkSide.class);
        if(build instanceof ItemBridge.ItemBridgeBuild bridge){
            Building linked = validBridgeTarget(bridge, viewer);
            if(linked == null) return EnumSet.noneOf(NetworkSide.class);
            EnumSet<NetworkSide> sides = EnumSet.allOf(NetworkSide.class);
            sides.remove(sideTo(bridge, linked));
            return sides;
        }
        return EnumSet.noneOf(NetworkSide.class);
    }

    private static Building validBridgeTarget(ItemBridge.ItemBridgeBuild bridge, Team viewer){
        if(bridge.link < 0 || bridge.tile == null || !(bridge.block instanceof ItemBridge block)) return null;
        Tile linkedTile = mindustry.Vars.world.tile(bridge.link);
        Building linked = linkedTile == null ? null : linkedTile.build;
        if(!(linked instanceof ItemBridge.ItemBridgeBuild target) || linked.team != viewer) return null;
        return block.linkValid(bridge.tile, linkedTile) && ((ItemBridge)target.block).linkValid(bridge.tile, linkedTile)
            ? linked : null;
    }

    private static NetworkSide sideTo(Building from, Building to){
        if(to.tile.x > from.tile.x) return NetworkSide.east;
        if(to.tile.x < from.tile.x) return NetworkSide.west;
        if(to.tile.y > from.tile.y) return NetworkSide.north;
        return NetworkSide.south;
    }

    private static ResourceRef itemRef(Item item){
        return item == null ? null : new ResourceRef(ResourceKind.item, item.name, item.localizedName);
    }

    private static ItemConstraint outputConstraint(Building build, Map<Building, List<ResourceRef>> itemProducts){
        List<ResourceRef> products = itemProducts.get(build);
        if(products != null && products.size() == 1) return ItemConstraint.only(products.get(0));
        if(products != null && !products.isEmpty()) return ItemConstraint.oneOf(products);
        return ItemConstraint.any();
    }

    private static void collectResources(Building build, FactorySnapshot snapshot, List<ResourceRef> resources){
        if(build instanceof Sorter.SorterBuild sorter && sorter.sortItem != null) resources.add(itemRef(sorter.sortItem));
        if(build instanceof DuctRouter.DuctRouterBuild router && router.sortItem != null) resources.add(itemRef(router.sortItem));
        if(snapshot == null) return;
        for(ResourceState input : snapshot.inputs){
            if(input.kind == ResourceKind.item && input.contentId != null) resources.add(input.ref());
        }
        resources.addAll(snapshot.producedItems);
    }

    private static boolean acceptsTopologyFrom(Building target, Building source){
        return true;
    }

    private static boolean hasItemOutput(Building build, Set<Building> itemSources){
        if(itemSources.contains(build)) return true;
        if(isUnknownTransport(build)) return false;
        try{
            return !MindustryFactoryProbe.probe(build).producedItems.isEmpty();
        }catch(Exception ignored){
            return false;
        }
    }
}
