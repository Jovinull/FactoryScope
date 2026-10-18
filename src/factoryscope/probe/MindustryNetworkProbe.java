package factoryscope.probe;

import arc.struct.Seq;
import factoryscope.FsLog;
import factoryscope.area.*;
import factoryscope.model.*;
import factoryscope.network.*;
import mindustry.gen.*;
import mindustry.game.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.defense.turrets.Turret;
import mindustry.world.blocks.distribution.*;
import mindustry.world.blocks.production.*;
import mindustry.world.blocks.storage.*;
import mindustry.world.meta.*;

import java.util.*;

/** Extracts verified item topology from a Mindustry world without calling transfer methods. */
public final class MindustryNetworkProbe{
    //StackConveyor's own state constants are protected; these mirror stateLoad and stateUnload
    private static final int stackLoad = 1, stackUnload = 2;

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
        Map<NetworkPort, ItemConstraint> boundaryOutputConstraints = new HashMap<>();
        Map<NetworkPort, ItemConstraint> boundaryInputConstraints = new HashMap<>();
        List<BuildingRef> unsupported = new ArrayList<>();
        List<NetworkInterruption> interruptions = new ArrayList<>();
        Set<TopologyProbeFailure> probeFailures = new TreeSet<>();
        List<BuildingRef> storage = new ArrayList<>();
        List<ResourceRef> resources = new ArrayList<>();
        Set<Building> itemSources = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<Building> itemSinks = Collections.newSetFromMap(new IdentityHashMap<>());
        Map<Building, List<ResourceRef>> itemProducts = new IdentityHashMap<>();
        Map<Building, FactorySnapshot> resolved = new IdentityHashMap<>();
        Map<Building, BuildingRef> refs = new IdentityHashMap<>();
        buildings.forEach((ref, build) -> refs.put(build, ref));

        for(var entry : buildings.entrySet()){
            Building build = entry.getValue();
            BuildingRef ref = entry.getKey();
            FactorySnapshot snapshot = snapshots.get(ref);
            if(snapshot == null){
                try{
                    snapshot = ProbeSnapshotReader.read(build);
                }catch(Exception e){
                    recordProbeFailure(probeFailures, ref, "snapshot", e);
                }
            }
            if(snapshot != null) resolved.put(build, snapshot);
            boolean storageEndpoint = isStorage(build);
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
                }else if(inputSides(neighbor, viewer, itemSinks).contains(side.opposite())
                    && acceptsTopologyFrom(neighbor, source)){
                    if(targetRef == null){
                        boundary.add(out);
                        mergeConstraint(boundaryOutputConstraints, out, outputConstraint(source, itemProducts));
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
                    if(!acceptsTopologyFrom(target, neighbor)) continue;
                    BuildingRef unsupportedRef = refs.getOrDefault(neighbor, AreaProbe.refOf(neighbor));
                    interruptions.add(new NetworkInterruption(input(targetRef, side), unsupportedRef,
                        NetworkInterruption.Direction.incoming));
                }else if(!refs.containsKey(neighbor)){
                    if(!acceptsTopologyFrom(target, neighbor)) continue;
                    ItemConstraint constraint = boundaryInputConstraint(neighbor, side.opposite(), viewer,
                        itemSources, itemProducts, probeFailures);
                    if(constraint != null){
                        NetworkPort port = input(targetRef, side);
                        boundaryInputs.add(port);
                        mergeConstraint(boundaryInputConstraints, port, constraint);
                    }
                }
            }
        }

        Links links = new Links(edges, boundary, boundaryInputs, boundaryOutputConstraints, boundaryInputConstraints, refs);
        addBridgeEdges(links, buildings, viewer);
        addDirectionBridgeEdges(links, buildings, viewer);
        addStackTransferEdges(links, buildings, viewer);
        addMassDriverEdges(links, buildings, viewer);
        addUnloaderSupply(links, interruptions, probeFailures, buildings, viewer, resolved);
        return new ItemNetwork(new NetworkGraph(ports, edges), boundary, boundaryInputs, unsupported,
            interruptions, storage, resources, boundaryOutputConstraints, boundaryInputConstraints, probeFailures);
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

    /** Where the edges between non-adjacent or non-accepting buildings are collected. */
    private static final class Links{
        final List<NetworkEdge> edges;
        final List<NetworkPort> boundary, boundaryInputs;
        final Map<NetworkPort, ItemConstraint> boundaryConstraints, boundaryInputConstraints;
        final Map<Building, BuildingRef> refs;

        Links(List<NetworkEdge> edges, List<NetworkPort> boundary, List<NetworkPort> boundaryInputs,
              Map<NetworkPort, ItemConstraint> boundaryConstraints, Map<NetworkPort, ItemConstraint> boundaryInputConstraints,
              Map<Building, BuildingRef> refs){
            this.edges = edges;
            this.boundary = boundary;
            this.boundaryInputs = boundaryInputs;
            this.boundaryConstraints = boundaryConstraints;
            this.boundaryInputConstraints = boundaryInputConstraints;
            this.refs = refs;
        }

        /** A transfer from {@code source}'s side to {@code target}, or an area exit when the target is not selected. */
        void send(BuildingRef source, NetworkSide side, Building target, NetworkSide targetSide){
            NetworkPort from = output(source, side);
            BuildingRef targetRef = refs.get(target);
            if(targetRef == null){
                boundary.add(from);
                mergeConstraint(boundaryConstraints, from, ItemConstraint.any());
            }else{
                edges.add(new NetworkEdge(from, input(targetRef, targetSide), ItemConstraint.any(), false));
            }
        }

        /** An entry into a selected building from a sender outside the area. */
        void enter(BuildingRef target, NetworkSide side, ItemConstraint items){
            NetworkPort port = input(target, side);
            boundaryInputs.add(port);
            mergeConstraint(boundaryInputConstraints, port, items);
        }
    }

    private static void addBridgeEdges(Links links, Map<BuildingRef, Building> selected, Team viewer){
        for(var entry : selected.entrySet()){
            if(!(entry.getValue() instanceof ItemBridge.ItemBridgeBuild bridge) || !isKnownTransport(bridge)) continue;
            ItemBridge.ItemBridgeBuild linked = TransportLinks.bridgeTarget(bridge, viewer);
            if(linked != null){
                NetworkSide direction = TransportLinks.sideTo(bridge, linked);
                links.send(entry.getKey(), direction, linked, direction.opposite());
            }
            for(ItemBridge.ItemBridgeBuild source : TransportLinks.bridgeSources(bridge, viewer)){
                if(!links.refs.containsKey(source)) links.enter(entry.getKey(), TransportLinks.sideTo(bridge, source), ItemConstraint.any());
            }
        }
    }

    private static void addDirectionBridgeEdges(Links links, Map<BuildingRef, Building> selected, Team viewer){
        for(var entry : selected.entrySet()){
            if(!(entry.getValue() instanceof DuctBridge.DuctBridgeBuild bridge) || !isKnownTransport(bridge)) continue;
            NetworkSide forward = NetworkSide.rotation(bridge.rotation);
            DirectionBridge.DirectionBridgeBuild linked = TransportLinks.directionTarget(bridge, viewer);
            if(linked != null) links.send(entry.getKey(), forward, linked, forward.opposite());
            for(DirectionBridge.DirectionBridgeBuild source : TransportLinks.directionSources(bridge, viewer)){
                if(!links.refs.containsKey(source)) links.enter(entry.getKey(), TransportLinks.sideTo(bridge, source), ItemConstraint.any());
            }
        }
    }

    /** A stack conveyor that is not unloading hands its whole stack to the stack conveyor in front of it. */
    private static void addStackTransferEdges(Links links, Map<BuildingRef, Building> selected, Team viewer){
        for(var entry : selected.entrySet()){
            if(!(entry.getValue() instanceof StackConveyor.StackConveyorBuild stack) || !isKnownTransport(stack)) continue;
            NetworkSide forward = NetworkSide.rotation(stack.rotation);
            StackConveyor.StackConveyorBuild next = stackTransferTarget(stack, viewer);
            if(next != null) links.send(entry.getKey(), forward, next, forward.opposite());
            for(Adjacent adjacent : adjacent(stack)){
                if(adjacent.building instanceof StackConveyor.StackConveyorBuild previous && !links.refs.containsKey(previous)
                    && stackTransferTarget(previous, viewer) == stack){
                    links.enter(entry.getKey(), adjacent.side, ItemConstraint.any());
                }
            }
        }
    }

    private static void addMassDriverEdges(Links links, Map<BuildingRef, Building> selected, Team viewer){
        for(var entry : selected.entrySet()){
            if(!(entry.getValue() instanceof MassDriver.MassDriverBuild driver) || !isKnownTransport(driver)) continue;
            MassDriver.MassDriverBuild target = TransportLinks.driverTarget(driver, viewer);
            if(target != null){
                links.send(entry.getKey(), TransportLinks.sideTo(driver, target), target, TransportLinks.sideTo(target, driver));
            }
            for(MassDriver.MassDriverBuild source : TransportLinks.driverSources(driver, viewer)){
                if(!links.refs.containsKey(source)) links.enter(entry.getKey(), TransportLinks.sideTo(driver, source), ItemConstraint.any());
            }
        }
    }

    /**
     * An unloader takes items out of its neighbors rather than receiving them, so its entries come from what each
     * neighbor holds: anything in storage, a block's products, and the items a block holds for its own use, which
     * reached it through its inputs. A transport neighbor passes on whatever entered it.
     */
    private static void addUnloaderSupply(Links links, List<NetworkInterruption> interruptions,
                                          Set<TopologyProbeFailure> probeFailures,
                                          Map<BuildingRef, Building> selected, Team viewer,
                                          Map<Building, FactorySnapshot> snapshots){
        for(var entry : selected.entrySet()){
            Building unloader = entry.getValue();
            if(!isUnloader(unloader) || !isKnownTransport(unloader)) continue;
            BuildingRef unloaderRef = entry.getKey();
            for(Adjacent adjacent : unloadSources(unloader, viewer)){
                Building provider = adjacent.building;
                NetworkSide side = adjacent.side;
                NetworkPort port = input(unloaderRef, side);
                BuildingRef providerRef = links.refs.get(provider);
                if(isUnknownTransport(provider)){
                    interruptions.add(new NetworkInterruption(port, providerRef == null ? AreaProbe.refOf(provider) : providerRef,
                        NetworkInterruption.Direction.incoming));
                    continue;
                }
                FactorySnapshot snapshot = snapshots.get(provider);
                if(snapshot == null && providerRef == null){
                    try{
                        snapshot = ProbeSnapshotReader.read(provider);
                    }catch(Exception e){
                        recordProbeFailure(probeFailures, AreaProbe.refOf(provider), "unloader supply snapshot", e);
                    }
                }
                if(providerRef == null){
                    ItemConstraint supply = unloadSupply(provider, snapshot);
                    if(supply != null) links.enter(unloaderRef, side, supply);
                    continue;
                }
                if(isStorage(provider)){
                    links.edges.add(new NetworkEdge(output(providerRef, side.opposite()), port, ItemConstraint.any(), false));
                    continue;
                }
                if(isKnownTransport(provider)){
                    for(NetworkSide entrySide : NetworkSide.values()){
                        links.edges.add(new NetworkEdge(input(providerRef, entrySide), port, ItemConstraint.any(), false));
                    }
                    continue;
                }
                if(snapshot == null) continue;
                if(!snapshot.producedItems.isEmpty()){
                    links.edges.add(new NetworkEdge(output(providerRef, side.opposite()), port,
                        ItemConstraint.oneOf(snapshot.producedItems), false));
                }
                ItemConstraint held = heldInputs(provider, snapshot);
                if(held != null){
                    for(NetworkSide entrySide : NetworkSide.values()){
                        links.edges.add(new NetworkEdge(input(providerRef, entrySide), port, held, false));
                    }
                }
            }
        }
    }

    /** The items {@code provider} may be emptied of, or null when it holds none an unloader can take. */
    private static ItemConstraint unloadSupply(Building provider, FactorySnapshot snapshot){
        if(isStorage(provider) || isKnownTransport(provider)) return ItemConstraint.any();
        if(snapshot == null) return null;
        List<ItemConstraint> supply = new ArrayList<>();
        if(!snapshot.producedItems.isEmpty()) supply.add(ItemConstraint.oneOf(snapshot.producedItems));
        ItemConstraint held = heldInputs(provider, snapshot);
        if(held != null) supply.add(held);
        return supply.isEmpty() ? null : ItemConstraint.anyOf(supply);
    }

    /** Item inputs a block keeps in its item module. A turret keeps item ammunition in a separate list. */
    private static ItemConstraint heldInputs(Building provider, FactorySnapshot snapshot){
        if(provider instanceof Turret.TurretBuild) return null;
        List<ResourceRef> items = new ArrayList<>();
        for(ResourceState input : snapshot.inputs){
            if(input.kind != ResourceKind.item) continue;
            if(input.contentId != null) items.add(input.ref());
            else if(input.acceptedResources.isEmpty()) return ItemConstraint.any();
            items.addAll(input.acceptedResources);
        }
        return items.isEmpty() ? null : ItemConstraint.oneOf(items);
    }

    private static boolean isUnloader(Building build){
        return build instanceof Unloader.UnloaderBuild || build instanceof DirectionalUnloader.DirectionalUnloaderBuild;
    }

    /** The neighbors an unloader takes items from: every side for an Unloader, the back for a Duct Unloader. */
    private static List<Adjacent> unloadSources(Building unloader, Team viewer){
        List<Adjacent> result = new ArrayList<>();
        if(unloader instanceof DirectionalUnloader.DirectionalUnloaderBuild directional){
            Building back = directional.back();
            if(unloadsFrom(directional, back, viewer)){
                result.add(new Adjacent(back, NetworkSide.rotation(directional.rotation).opposite()));
            }
            return result;
        }
        for(Adjacent adjacent : adjacent(unloader)) if(unloadsFrom(unloader, adjacent.building, viewer)) result.add(adjacent);
        return result;
    }

    /** {@code Unloader.onProximityUpdate} and {@code DirectionalUnloaderBuild.updateTile}'s source test. */
    private static boolean unloadsFrom(Building unloader, Building provider, Team viewer){
        if(provider == null || provider.team != viewer || provider.items == null) return false;
        boolean allowCoreUnload = unloader.block instanceof Unloader block ? block.allowCoreUnload
            : ((DirectionalUnloader)unloader.block).allowCoreUnload;
        boolean coreStorage = provider instanceof CoreBlock.CoreBuild
            || provider instanceof StorageBlock.StorageBuild storage && storage.linkedCore != null;
        if(!allowCoreUnload && coreStorage) return false;
        try{
            return provider.canUnload();
        }catch(Exception e){
            return false;
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
            boolean armoredDuct = build.block instanceof Duct duct && duct.armored;
            for(NetworkSide input : NetworkSide.values()) if(armoredDuct || input != forward)
                add(edges, ref, input, forward, ItemConstraint.any(), false);
        }else if(build instanceof ItemBridge.ItemBridgeBuild bridge){
            ItemBridge.ItemBridgeBuild linked = TransportLinks.bridgeTarget(bridge, viewer);
            if(linked != null){
                NetworkSide direction = TransportLinks.sideTo(bridge, linked);
                for(NetworkSide input : NetworkSide.values()) add(edges, ref, input, direction, ItemConstraint.any(), false);
            }else{
                EnumSet<NetworkSide> exits = outputSides(bridge, viewer, Set.of());
                for(NetworkSide input : NetworkSide.values()) for(NetworkSide out : exits) if(out != input)
                    add(edges, ref, input, out, ItemConstraint.any(), true);
            }
        }else if(build instanceof OverflowDuct.OverflowDuctBuild){
            NetworkSide forward = NetworkSide.rotation(build.rotation);
            NetworkSide back = forward.opposite();
            boolean inverted = ((OverflowDuct)build.block).invert;
            add(edges, ref, back, forward, ItemConstraint.any(), inverted);
            add(edges, ref, back, left(forward), ItemConstraint.any(), !inverted);
            add(edges, ref, back, right(forward), ItemConstraint.any(), !inverted);
        }else if(build instanceof DuctBridge.DuctBridgeBuild bridge){
            //linked: every entry continues to the remote bridge; unlinked: remote entries leave through the front
            NetworkSide forward = NetworkSide.rotation(bridge.rotation);
            EnumSet<NetworkSide> entries = directionBridgeInputs(bridge, viewer);
            for(DirectionBridge.DirectionBridgeBuild source : TransportLinks.directionSources(bridge, viewer)){
                entries.add(TransportLinks.sideTo(bridge, source));
            }
            for(NetworkSide entry : entries) add(edges, ref, entry, forward, ItemConstraint.any(), false);
        }else if(build instanceof Unloader.UnloaderBuild unloader){
            ItemConstraint items = unloader.sortItem == null ? ItemConstraint.any() : ItemConstraint.only(itemRef(unloader.sortItem));
            for(Adjacent source : unloadSources(unloader, viewer)) for(NetworkSide out : NetworkSide.values()){
                if(out != source.side) add(edges, ref, source.side, out, items, true);
            }
        }else if(build instanceof DirectionalUnloader.DirectionalUnloaderBuild unloader){
            NetworkSide forward = NetworkSide.rotation(unloader.rotation);
            ItemConstraint items = unloader.unloadItem == null ? ItemConstraint.any() : ItemConstraint.only(itemRef(unloader.unloadItem));
            if(!unloadSources(unloader, viewer).isEmpty()) add(edges, ref, forward.opposite(), forward, items, false);
        }else if(build instanceof StackConveyor.StackConveyorBuild stack){
            NetworkSide forward = NetworkSide.rotation(stack.rotation);
            EnumSet<NetworkSide> entries = inputSides(stack, viewer, Set.of());
            for(Adjacent adjacent : adjacent(stack)){
                if(adjacent.building instanceof StackConveyor.StackConveyorBuild previous
                    && stackTransferTarget(previous, viewer) == stack) entries.add(adjacent.side);
            }
            EnumSet<NetworkSide> exits = stack.state == stackUnload ? outputSides(stack, viewer, Set.of())
                : stackTransferTarget(stack, viewer) != null ? EnumSet.of(forward) : EnumSet.noneOf(NetworkSide.class);
            for(NetworkSide entry : entries) for(NetworkSide exit : exits){
                if(entry != exit) add(edges, ref, entry, exit, ItemConstraint.any(), exits.size() > 1);
            }
        }else if(build instanceof MassDriver.MassDriverBuild driver){
            //a linked driver accepts items and fires them; a driver fired at dumps what it receives, and a
            //relay does both
            MassDriver.MassDriverBuild target = TransportLinks.driverTarget(driver, viewer);
            EnumSet<NetworkSide> entries = inputSides(driver, viewer, Set.of());
            for(MassDriver.MassDriverBuild source : TransportLinks.driverSources(driver, viewer)){
                entries.add(TransportLinks.sideTo(driver, source));
            }
            EnumSet<NetworkSide> exits = outputSides(driver, viewer, Set.of());
            if(target != null) exits.add(TransportLinks.sideTo(driver, target));
            for(NetworkSide entry : entries) for(NetworkSide exit : exits){
                add(edges, ref, entry, exit, ItemConstraint.any(), exits.size() > 1);
            }
        }
    }

    private static void add(List<NetworkEdge> edges, BuildingRef ref, NetworkSide from, NetworkSide to,
                            ItemConstraint constraint, boolean conditional){
        edges.add(new NetworkEdge(input(ref, from), output(ref, to), constraint, conditional));
    }

    private static NetworkSide left(NetworkSide side){ return NetworkSide.rotation(side.ordinal() + 1); }
    private static NetworkSide right(NetworkSide side){ return NetworkSide.rotation(side.ordinal() - 1); }

    private static boolean isKnownTransport(Building build){
        return usesRecognizedTransportBuild(build) && (build instanceof Conveyor.ConveyorBuild || build instanceof Duct.DuctBuild || build instanceof Junction.JunctionBuild
            || build instanceof Router.RouterBuild || build instanceof Sorter.SorterBuild || build instanceof DuctRouter.DuctRouterBuild || build instanceof OverflowGate.OverflowGateBuild
            || build instanceof ItemBridge.ItemBridgeBuild
            || build instanceof OverflowDuct.OverflowDuctBuild
            || build instanceof DuctBridge.DuctBridgeBuild || build instanceof Unloader.UnloaderBuild
            || build instanceof DirectionalUnloader.DirectionalUnloaderBuild || build instanceof StackConveyor.StackConveyorBuild
            || build instanceof MassDriver.MassDriverBuild);
    }

    private static boolean usesRecognizedTransportBuild(Building build){
        Class<?> type = build.getClass();
        return type == Conveyor.ConveyorBuild.class
            || type == ArmoredConveyor.ArmoredConveyorBuild.class
            || type == Duct.DuctBuild.class
            || type == Junction.JunctionBuild.class
            || type == Router.RouterBuild.class
            || type == StackRouter.StackRouterBuild.class
            || type == Sorter.SorterBuild.class
            || type == DuctRouter.DuctRouterBuild.class
            || type == OverflowGate.OverflowGateBuild.class
            || type == ItemBridge.ItemBridgeBuild.class
            || type == BufferedItemBridge.BufferedItemBridgeBuild.class
            || type == OverflowDuct.OverflowDuctBuild.class
            || type == DuctBridge.DuctBridgeBuild.class
            || type == Unloader.UnloaderBuild.class
            || type == DirectionalUnloader.DirectionalUnloaderBuild.class
            || type == StackConveyor.StackConveyorBuild.class
            || type == MassDriver.MassDriverBuild.class;
    }

    private static boolean isEndpoint(Building build){
        return build instanceof GenericCrafter.GenericCrafterBuild || build instanceof Drill.DrillBuild
            || isStorage(build);
    }

    private static boolean isStorage(Building build){
        return build instanceof StorageBlock.StorageBuild || build instanceof CoreBlock.CoreBuild;
    }

    private static boolean isUnknownTransport(Building build){
        return !isKnownTransport(build) && !isEndpoint(build)
            && ((build.block.group == BlockGroup.transportation && build.block.hasItems)
                || build instanceof MassDriver.MassDriverBuild || build instanceof Unloader.UnloaderBuild);
    }

    private static EnumSet<NetworkSide> outputSides(Building build, Team viewer, Set<Building> itemSources){
        // A custom runtime transport may inherit a vanilla Build class while overriding its
        // routing behavior. Its adjacent connection is an interruption, not a modeled output.
        if(isUnknownTransport(build)) return EnumSet.noneOf(NetworkSide.class);
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
        if(build instanceof ItemBridge.ItemBridgeBuild bridge){
            if(TransportLinks.bridgeTarget(bridge, viewer) != null) return EnumSet.noneOf(NetworkSide.class);
            //ItemBridgeBuild.checkDump: an unlinked bridge does not dump toward a bridge linked into it
            EnumSet<NetworkSide> sides = EnumSet.allOf(NetworkSide.class);
            for(ItemBridge.ItemBridgeBuild source : TransportLinks.bridgeSources(bridge, viewer)) sides.remove(TransportLinks.sideTo(bridge, source));
            return sides;
        }
        if(build instanceof DuctBridge.DuctBridgeBuild bridge)
            return TransportLinks.directionTarget(bridge, viewer) == null ? EnumSet.of(NetworkSide.rotation(bridge.rotation))
                : EnumSet.noneOf(NetworkSide.class);
        if(build instanceof Unloader.UnloaderBuild) return EnumSet.allOf(NetworkSide.class);
        if(build instanceof DirectionalUnloader.DirectionalUnloaderBuild) return EnumSet.of(NetworkSide.rotation(build.rotation));
        if(build instanceof StackConveyor.StackConveyorBuild stack){
            //an unloading plastanium conveyor dumps like a router; an unloading surge conveyor only moves forward
            if(stack.state != stackUnload) return EnumSet.noneOf(NetworkSide.class);
            return ((StackConveyor)stack.block).outputRouter ? EnumSet.allOf(NetworkSide.class)
                : EnumSet.of(NetworkSide.rotation(stack.rotation));
        }
        if(build instanceof MassDriver.MassDriverBuild driver){
            //a driver with a valid link switches to shooting before it would dump, unless it is being fired at
            boolean dumps = TransportLinks.driverTarget(driver, viewer) == null || !TransportLinks.driverSources(driver, viewer).isEmpty();
            return dumps ? EnumSet.allOf(NetworkSide.class) : EnumSet.noneOf(NetworkSide.class);
        }
        if(itemSources.contains(build))
            return EnumSet.allOf(NetworkSide.class);
        return EnumSet.noneOf(NetworkSide.class);
    }

    private static EnumSet<NetworkSide> inputSides(Building build, Team viewer, Set<Building> itemSinks){
        if(build.block instanceof Duct duct && duct.armored) return EnumSet.allOf(NetworkSide.class);
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
            ItemBridge.ItemBridgeBuild linked = TransportLinks.bridgeTarget(bridge, viewer);
            if(linked == null) return EnumSet.noneOf(NetworkSide.class);
            EnumSet<NetworkSide> sides = EnumSet.allOf(NetworkSide.class);
            sides.remove(TransportLinks.sideTo(bridge, linked));
            return sides;
        }
        if(build instanceof DuctBridge.DuctBridgeBuild bridge) return directionBridgeInputs(bridge, viewer);
        if(build instanceof StackConveyor.StackConveyorBuild stack){
            //only a loading dock accepts items, and never from the block it faces
            if(stack.state != stackLoad) return EnumSet.noneOf(NetworkSide.class);
            EnumSet<NetworkSide> sides = EnumSet.allOf(NetworkSide.class);
            sides.remove(NetworkSide.rotation(stack.rotation));
            return sides;
        }
        if(build instanceof MassDriver.MassDriverBuild driver){
            //MassDriverBuild.acceptItem: only a driver with a valid link accepts items
            return TransportLinks.driverTarget(driver, viewer) != null ? EnumSet.allOf(NetworkSide.class) : EnumSet.noneOf(NetworkSide.class);
        }
        return EnumSet.noneOf(NetworkSide.class);
    }

    /**
     * {@code DuctBridgeBuild.acceptItem}: a bridge with a link accepts from every side except its front and the
     * sides a linked bridge arrives from; an unlinked bridge accepts nothing.
     */
    private static EnumSet<NetworkSide> directionBridgeInputs(DirectionBridge.DirectionBridgeBuild bridge, Team viewer){
        if(TransportLinks.directionTarget(bridge, viewer) == null) return EnumSet.noneOf(NetworkSide.class);
        EnumSet<NetworkSide> sides = EnumSet.allOf(NetworkSide.class);
        sides.remove(NetworkSide.rotation(bridge.rotation));
        for(DirectionBridge.DirectionBridgeBuild source : TransportLinks.directionSources(bridge, viewer)){
            sides.remove(TransportLinks.sideTo(bridge, source));
        }
        return sides;
    }

    /** The stack conveyor a non-unloading stack conveyor hands its stack to. */
    private static StackConveyor.StackConveyorBuild stackTransferTarget(StackConveyor.StackConveyorBuild stack, Team viewer){
        if(stack.team != viewer || stack.state == stackUnload || !isKnownTransport(stack)) return null;
        return stack.front() instanceof StackConveyor.StackConveyorBuild next && next.team == viewer && isKnownTransport(next)
            ? next : null;
    }

    private static ResourceRef itemRef(Item item){
        return item == null ? null : new ResourceRef(ResourceKind.item, item.name, item.localizedName);
    }

    private static ItemConstraint boundaryInputConstraint(Building build, NetworkSide outputSide, Team viewer,
                                                          Set<Building> itemSources,
                                                          Map<Building, List<ResourceRef>> itemProducts,
                                                          Set<TopologyProbeFailure> probeFailures){
        if(isKnownTransport(build)){
            if(!outputSides(build, viewer, itemSources).contains(outputSide)) return null;
            BuildingRef ref = AreaProbe.refOf(build);
            List<NetworkEdge> internal = new ArrayList<>();
            addInternal(internal, build, ref, viewer);
            List<ItemConstraint> constraints = internal.stream()
                .filter(edge -> edge.to.equals(output(ref, outputSide)))
                .map(edge -> edge.items).toList();
            return constraints.isEmpty() ? null : ItemConstraint.anyOf(constraints);
        }

        if(!isEndpoint(build)) return null;

        List<ResourceRef> products = itemProducts.get(build);
        if(products == null){
            try{
                products = ProbeSnapshotReader.read(build).producedItems;
            }catch(Exception e){
                recordProbeFailure(probeFailures, AreaProbe.refOf(build), "boundary producer snapshot", e);
                return null;
            }
        }
        return products.isEmpty() ? null : ItemConstraint.oneOf(products);
    }

    private static void mergeConstraint(Map<NetworkPort, ItemConstraint> constraints, NetworkPort port,
                                        ItemConstraint addition){
        ItemConstraint existing = constraints.get(port);
        if(existing == null) constraints.put(port, addition);
        else constraints.put(port, ItemConstraint.anyOf(List.of(existing, addition)));
    }

    private static void recordProbeFailure(Set<TopologyProbeFailure> failures, BuildingRef ref, String operation, Exception cause){
        failures.add(new TopologyProbeFailure(ref, operation));
        FsLog.warnOnce("item-topology:" + operation + ":" + ref,
            "item topology probe failed at " + ref + " while reading " + operation, cause);
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
        if(build instanceof Unloader.UnloaderBuild unloader && unloader.sortItem != null) resources.add(itemRef(unloader.sortItem));
        if(build instanceof DirectionalUnloader.DirectionalUnloaderBuild unloader && unloader.unloadItem != null)
            resources.add(itemRef(unloader.unloadItem));
        if(snapshot == null) return;
        for(ResourceState input : snapshot.inputs){
            if(input.kind == ResourceKind.item && input.contentId != null) resources.add(input.ref());
        }
        resources.addAll(snapshot.producedItems);
    }

    private static boolean acceptsTopologyFrom(Building target, Building source){
        //Unloader.onProximityUpdate: an unloader never loads into storage or a core
        if(source instanceof Unloader.UnloaderBuild && isStorage(target)) return false;
        if(target.block instanceof ArmoredConveyor){
            return source.block instanceof Conveyor || alignedArmoredInput(target, source);
        }
        if(target.block instanceof Duct duct && duct.armored){
            boolean ductInput = source.block.rotate && source.block.hasItems && source.block.isDuct
                && source.front() == target;
            return ductInput || alignedArmoredInput(target, source);
        }
        return true;
    }

    private static boolean alignedArmoredInput(Building target, Building source){
        if(source.tile == null || target.tile == null) return false;
        Tile facing = Edges.getFacingEdge(source.tile, target.tile);
        return facing != null && facing.relativeTo(target.tile) == target.rotation;
    }

}
