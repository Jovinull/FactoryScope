package factoryscope.probe;

import arc.struct.Seq;
import factoryscope.area.*;
import factoryscope.liquid.*;
import factoryscope.model.*;
import factoryscope.network.*;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.world.blocks.distribution.DirectionLiquidBridge;
import mindustry.world.blocks.liquid.*;
import mindustry.world.blocks.power.ConsumeGenerator;
import mindustry.world.blocks.power.ThermalGenerator;
import mindustry.world.blocks.production.GenericCrafter;
import mindustry.world.blocks.production.Pump;
import mindustry.type.Liquid;

import java.util.*;

/** Converts known Mindustry liquid routing rules into an area-scoped structural graph. */
public final class MindustryLiquidProbe{
    private static final Comparator<BuildingRef> REF_ORDER = Comparator
        .comparingInt((BuildingRef ref) -> ref.tileX).thenComparingInt(ref -> ref.tileY)
        .thenComparing(ref -> ref.blockId).thenComparingInt(ref -> ref.teamId);

    private MindustryLiquidProbe(){ }

    public static LiquidNetwork scan(AreaSelection selection, Team viewer, Map<BuildingRef, FactorySnapshot> snapshots){
        Seq<Building> selected = AreaProbe.collect(selection, viewer);
        Map<BuildingRef, Building> buildings = new TreeMap<>(REF_ORDER);
        Map<Building, BuildingRef> refs = new IdentityHashMap<>();
        for(Building build : selected){
            BuildingRef ref = AreaProbe.refOf(build);
            buildings.put(ref, build);
            refs.put(build, ref);
        }

        List<NetworkPort> ports = new ArrayList<>();
        List<LiquidNetworkEdge> edges = new ArrayList<>();
        Set<NetworkPort> boundaryOut = new TreeSet<>(), boundaryIn = new TreeSet<>();
        Map<NetworkPort, LiquidConstraint> boundaryOutConstraints = new HashMap<>(), boundaryInConstraints = new HashMap<>();
        Set<BuildingRef> unsupported = new TreeSet<>(REF_ORDER), storage = new TreeSet<>(REF_ORDER);
        Set<BuildingRef> incompleteInputs = new TreeSet<>(REF_ORDER), incompleteOutputs = new TreeSet<>(REF_ORDER);
        Set<LiquidInterruption> interruptions = new TreeSet<>();
        Set<LiquidUncertainty> incompleteConnections = new TreeSet<>();
        Map<BuildingRef, FactorySnapshot> resolvedSnapshots = new HashMap<>(snapshots);
        List<ResourceRef> resources = new ArrayList<>();

        for(var entry : buildings.entrySet()){
            Building build = entry.getValue();
            BuildingRef ref = entry.getKey();
            FactorySnapshot snapshot = snapshot(build, ref, resolvedSnapshots);
            if(snapshot != null) addResources(resources, snapshot);

            if(build.block instanceof GenericCrafter crafter && crafter.outputLiquids != null){
                for(int i = 0; i < crafter.outputLiquids.length; i++){
                    int direction = crafter.liquidOutputDirections != null && crafter.liquidOutputDirections.length > i
                        ? crafter.liquidOutputDirections[i] : -1;
                    if(direction < -1 || direction > 3){
                        // The engine reserves exactly -1 for unrestricted output. Invalid modded
                        // direction values are not normalized into plausible routes.
                        incompleteOutputs.add(ref);
                        break;
                    }
                }
            }

            // If an otherwise supported, output-capable building could not be snapshotted, its
            // product identity is unknown. Preserve that as incomplete evidence at adjacent
            // resource-compatible routes instead of silently turning it into a dead end.
            if(snapshot == null && isKnownEndpoint(build) && build.block.outputsLiquid){
                incompleteOutputs.add(ref);
            }

            if(isUnsupportedTransport(build, snapshot)){
                unsupported.add(ref);
                continue;
            }

            boolean transport = isTransport(build);
            boolean producer = snapshot != null && !snapshot.producedLiquids.isEmpty();
            boolean consumer = snapshot != null && snapshot.inputs.stream().anyMatch(MindustryLiquidProbe::isLiquidInput);
            boolean isStorage = isLiquidStorage(build);
            if(isStorage) storage.add(ref);

            if(snapshot != null){
                if(!snapshot.liquidInputsComplete) incompleteInputs.add(ref);
                if(!snapshot.liquidOutputsComplete) incompleteOutputs.add(ref);
            }

            if(transport || producer || consumer || isStorage){
                if(transport){
                    for(NetworkSide side : inputSides(build)) ports.add(in(ref, side));
                    for(NetworkSide side : outputSides(build, snapshot)) ports.add(out(ref, side));
                    addInternal(edges, build, ref, snapshot);
                }else{
                    if(consumer) for(NetworkSide side : NetworkSide.values()) ports.add(in(ref, side));
                    if(producer) for(NetworkSide side : outputSides(build, snapshot)) ports.add(out(ref, side));
                }
            }
        }

        // External structural connections. No acceptLiquid() calls are made here: its answer depends on
        // the transient contents/capacity of the destination module, not just on its topology.
        for(var entry : buildings.entrySet()){
            Building source = entry.getValue();
            BuildingRef sourceRef = entry.getKey();
            FactorySnapshot sourceSnapshot = resolvedSnapshots.get(sourceRef);
            for(Adjacent adjacent : adjacent(source)){
                Building neighbor = adjacent.building;
                if(neighbor == null || neighbor.team != viewer || !MindustryFactoryProbe.canInspect(neighbor, viewer)) continue;
                NetworkSide sourceSide = adjacent.side;
                if(!outputSides(source, sourceSnapshot).contains(sourceSide)) continue;
                LiquidConstraint sourceConstraint = outputConstraint(source, sourceSnapshot, sourceSide);
                if(sourceConstraint == null) continue;
                BuildingRef targetRef = refs.get(neighbor);
                if(targetRef == null){
                    FactorySnapshot outsideSnapshot = snapshot(neighbor, AreaProbe.refOf(neighbor), resolvedSnapshots);
                    LiquidConstraint targetConstraint = inputConstraint(neighbor, outsideSnapshot, sourceSide.opposite());
                    LiquidConstraint compatible = targetConstraint == null ? null : sourceConstraint.intersection(targetConstraint);
                    if(compatible != null){
                        NetworkPort sourcePort = out(sourceRef, sourceSide);
                        boundaryOut.add(sourcePort);
                        merge(boundaryOutConstraints, sourcePort, compatible);
                    }else if(outsideSnapshot != null && !outsideSnapshot.liquidInputsComplete){
                        incompleteConnections.add(new LiquidUncertainty(out(sourceRef, sourceSide),
                            AreaProbe.refOf(neighbor), LiquidUncertainty.Direction.outgoing,
                            LiquidUncertainty.Kind.incompleteRequirement, sourceConstraint));
                    }else if(neighbor.block.hasLiquids && isUnsupportedTransport(neighbor, outsideSnapshot)){
                        // A visible but unsupported outside transport is not a proven area boundary:
                        // it may continue, terminate, or route the liquid in unmodeled ways.
                        interruptions.add(new LiquidInterruption(out(sourceRef, sourceSide),
                            AreaProbe.refOf(neighbor), LiquidInterruption.Direction.outgoing, sourceConstraint));
                    }
                    continue;
                }
                if(isUnsupportedTransport(neighbor, resolvedSnapshots.get(targetRef))){
                    interruptions.add(new LiquidInterruption(out(sourceRef, sourceSide), targetRef,
                        LiquidInterruption.Direction.outgoing, sourceConstraint));
                    continue;
                }
                FactorySnapshot targetSnapshot = resolvedSnapshots.get(targetRef);
                LiquidConstraint targetConstraint = inputConstraint(neighbor, targetSnapshot, sourceSide.opposite());
                if(targetConstraint == null){
                    if(incompleteInputs.contains(targetRef)) incompleteConnections.add(new LiquidUncertainty(
                        out(sourceRef, sourceSide), targetRef, LiquidUncertainty.Direction.outgoing,
                        LiquidUncertainty.Kind.incompleteRequirement, sourceConstraint));
                    continue;
                }
                LiquidConstraint compatible = sourceConstraint.intersection(targetConstraint);
                if(compatible != null) edges.add(new LiquidNetworkEdge(out(sourceRef, sourceSide), in(targetRef, sourceSide.opposite()), compatible));
            }
        }

        // A producer whose declared products could not be enumerated is an uncertain source, not an
        // empty source. Mark only adjacent, resource-compatible input ports so unrelated traces stay complete.
        for(var entry : buildings.entrySet()){
            if(!incompleteOutputs.contains(entry.getKey())) continue;
            Building source = entry.getValue();
            for(Adjacent adjacent : adjacent(source)){
                Building target = adjacent.building;
                BuildingRef targetRef = refs.get(target);
                if(targetRef == null || unsupported.contains(targetRef)) continue;
                LiquidConstraint targetConstraint = inputConstraint(target, resolvedSnapshots.get(targetRef),
                    adjacent.side.opposite());
                if(targetConstraint != null) incompleteConnections.add(new LiquidUncertainty(
                    in(targetRef, adjacent.side.opposite()), entry.getKey(), LiquidUncertainty.Direction.incoming,
                    LiquidUncertainty.Kind.incompleteProduct, targetConstraint));
            }
        }

        // Unsupported transport has no modeled output ports, so it cannot appear as the source of
        // the pass above. Preserve the uncertainty from the receiving side as well: otherwise a
        // consumer directly beside an ArmoredConduit could be incorrectly reported as having no
        // structural route simply because the unknown block was omitted from the graph.
        for(var entry : buildings.entrySet()){
            Building target = entry.getValue();
            BuildingRef targetRef = entry.getKey();
            FactorySnapshot targetSnapshot = resolvedSnapshots.get(targetRef);
            if(isUnsupportedTransport(target, targetSnapshot)) continue;
            for(Adjacent adjacent : adjacent(target)){
                Building source = adjacent.building;
                if(source == null || source.team != viewer || !MindustryFactoryProbe.canInspect(source, viewer)) continue;
                BuildingRef sourceRef = refs.get(source);
                if(sourceRef == null || !unsupported.contains(sourceRef)) continue;
                NetworkSide targetSide = adjacent.side;
                LiquidConstraint targetConstraint = inputConstraint(target, targetSnapshot, targetSide);
                if(targetConstraint == null) continue;
                interruptions.add(new LiquidInterruption(in(targetRef, targetSide), sourceRef,
                    LiquidInterruption.Direction.incoming, targetConstraint));
            }
        }

        // Immediate outside neighbors are inspected only to establish a boundary continuation, never traversed.
        for(var entry : buildings.entrySet()){
            Building target = entry.getValue();
            BuildingRef targetRef = entry.getKey();
            FactorySnapshot targetSnapshot = resolvedSnapshots.get(targetRef);
            if(isUnsupportedTransport(target, targetSnapshot)) continue;
            for(Adjacent adjacent : adjacent(target)){
                Building source = adjacent.building;
                if(source == null || source.team != viewer || refs.containsKey(source)
                    || !MindustryFactoryProbe.canInspect(source, viewer)) continue;
                NetworkSide targetSide = adjacent.side;
                LiquidConstraint targetConstraint = inputConstraint(target, targetSnapshot, targetSide);
                if(targetConstraint == null || !source.block.hasLiquids) continue;
                BuildingRef sourceRef = AreaProbe.refOf(source);
                FactorySnapshot sourceSnapshot = null;
                try{ sourceSnapshot = MindustryFactoryProbe.probe(source); }catch(Exception ignored){ }
                LiquidConstraint sourceConstraint = outputConstraint(source, sourceSnapshot, targetSide.opposite());
                if(isTransport(source) && !outputSides(source, sourceSnapshot).contains(targetSide.opposite())) continue;
                if(isKnownEndpoint(source) && !outputSides(source, sourceSnapshot).contains(targetSide.opposite())) continue;
                if(isUnsupportedTransport(source, sourceSnapshot)){
                    interruptions.add(new LiquidInterruption(in(targetRef, targetSide), sourceRef,
                        LiquidInterruption.Direction.incoming, targetConstraint));
                    continue;
                }
                if(sourceConstraint == null){
                    if(sourceSnapshot != null && !sourceSnapshot.liquidOutputsComplete
                        || sourceSnapshot == null && isKnownEndpoint(source)){
                        incompleteConnections.add(new LiquidUncertainty(in(targetRef, targetSide), sourceRef,
                            LiquidUncertainty.Direction.incoming, LiquidUncertainty.Kind.incompleteProduct,
                            targetConstraint));
                        continue;
                    }
                    // A known liquid-capable but unmodeled outside block is an unknown continuation.
                    if(!isKnownEndpoint(source) && !isTransport(source)) sourceConstraint = LiquidConstraint.any();
                    else continue;
                }
                if(sourceConstraint.intersection(targetConstraint) == null) continue;
                NetworkPort targetPort = in(targetRef, targetSide);
                boundaryIn.add(targetPort);
                merge(boundaryInConstraints, targetPort, sourceConstraint);
            }
        }

        return new LiquidNetwork(new LiquidNetworkGraph(ports, edges), boundaryOut, boundaryIn,
            boundaryOutConstraints, boundaryInConstraints, unsupported, interruptions, storage,
            incompleteInputs, incompleteOutputs, incompleteConnections, resources);
    }

    private static FactorySnapshot snapshot(Building build, BuildingRef ref, Map<BuildingRef, FactorySnapshot> snapshots){
        FactorySnapshot snapshot = snapshots.get(ref);
        if(snapshot != null) return snapshot;
        try{
            snapshot = MindustryFactoryProbe.probe(build);
            snapshots.put(ref, snapshot);
            return snapshot;
        }catch(Exception ignored){
            return null;
        }
    }

    private static void addResources(List<ResourceRef> resources, FactorySnapshot snapshot){
        resources.addAll(snapshot.producedLiquids);
        for(ResourceState input : snapshot.inputs){
            if(input.kind == ResourceKind.liquid){
                if(input.ref().id != null) resources.add(input.ref());
                resources.addAll(input.acceptedResources);
            }
        }
        for(StoredLiquidState stored : snapshot.storedLiquids) resources.add(stored.liquid);
    }

    private static boolean isLiquidInput(ResourceState input){
        return input.kind == ResourceKind.liquid;
    }

    private static boolean isTransport(Building build){
        return usesRecognizedLiquidTransportBuild(build) && (build instanceof Conduit.ConduitBuild && !(build.block instanceof ArmoredConduit)
            || build instanceof LiquidJunction.LiquidJunctionBuild
            || build instanceof LiquidRouter.LiquidRouterBuild);
    }

    private static boolean usesRecognizedLiquidTransportBuild(Building build){
        Class<?> type = build.getClass();
        return type == Conduit.ConduitBuild.class
            || type == LiquidJunction.LiquidJunctionBuild.class
            || type == LiquidRouter.LiquidRouterBuild.class;
    }

    private static boolean isUnsupportedTransport(Building build, FactorySnapshot snapshot){
        if(build.block instanceof ArmoredConduit || build instanceof DirectionLiquidBridge.DuctBridgeBuild) return true;
        // ItemBridge-backed LiquidBridge has state-dependent local routing: with a valid remote link,
        // updates transmit to that endpoint instead of locally dumping; when unlinked, local
        // acceptance/output use different incoming-link rules. Until those ports are represented
        // exactly, expose the family as a local partial interruption rather than a router.
        if(isTransport(build) || isKnownEndpoint(build)) return false;
        if(!build.block.hasLiquids && !build.block.outputsLiquid) return false;
        boolean declaredConsumer = snapshot != null && snapshot.inputs.stream().anyMatch(MindustryLiquidProbe::isLiquidInput);
        boolean declaredProducer = snapshot != null && !snapshot.producedLiquids.isEmpty();
        // A standard declared consumer with no liquid-output capability is a known terminal input,
        // not an unknown transport. An unmodeled output-capable block may route internally, so do
        // not connect through it based only on module/output metadata.
        return build.block.outputsLiquid || !declaredConsumer && !declaredProducer;
    }

    private static boolean isKnownEndpoint(Building build){
        return build instanceof GenericCrafter.GenericCrafterBuild || build instanceof Pump.PumpBuild
            || build.block instanceof ConsumeGenerator || build.block instanceof ThermalGenerator;
    }

    private static boolean isLiquidStorage(Building build){
        return build instanceof LiquidRouter.LiquidRouterBuild;
    }

    private static EnumSet<NetworkSide> inputSides(Building build){
        if(build instanceof Conduit.ConduitBuild){
            EnumSet<NetworkSide> result = EnumSet.allOf(NetworkSide.class);
            result.remove(NetworkSide.rotation(build.rotation));
            return result;
        }
        return EnumSet.allOf(NetworkSide.class);
    }

    private static EnumSet<NetworkSide> outputSides(Building build, FactorySnapshot snapshot){
        if(build instanceof Conduit.ConduitBuild) return EnumSet.of(NetworkSide.rotation(build.rotation));
        if(build instanceof GenericCrafter.GenericCrafterBuild && build.block instanceof GenericCrafter crafter){
            EnumSet<NetworkSide> result = EnumSet.noneOf(NetworkSide.class);
            int count = crafter.outputLiquids == null ? 0 : crafter.outputLiquids.length;
            for(int i = 0; i < count; i++){
                int direction = crafter.liquidOutputDirections != null && crafter.liquidOutputDirections.length > i
                    ? crafter.liquidOutputDirections[i] : -1;
                if(direction == -1){ result.addAll(EnumSet.allOf(NetworkSide.class)); break; }
                if(direction >= 0 && direction <= 3) result.add(NetworkSide.rotation(build.rotation + direction));
            }
            return result;
        }
        if(snapshot != null && !snapshot.producedLiquids.isEmpty()) return EnumSet.allOf(NetworkSide.class);
        if(isTransport(build)) return EnumSet.allOf(NetworkSide.class);
        return EnumSet.noneOf(NetworkSide.class);
    }

    private static void addInternal(List<LiquidNetworkEdge> edges, Building build, BuildingRef ref, FactorySnapshot snapshot){
        if(build instanceof Conduit.ConduitBuild){
            NetworkSide front = NetworkSide.rotation(build.rotation);
            for(NetworkSide incoming : inputSides(build)){
                edges.add(new LiquidNetworkEdge(in(ref, incoming), out(ref, front), LiquidConstraint.any()));
            }
        }else if(build instanceof LiquidJunction.LiquidJunctionBuild){
            // getLiquidDestination preserves the incoming channel and continues straight through.
            for(NetworkSide incoming : NetworkSide.values()){
                edges.add(new LiquidNetworkEdge(in(ref, incoming), out(ref, incoming.opposite()), LiquidConstraint.any()));
            }
        }else if(build instanceof LiquidRouter.LiquidRouterBuild){
            for(NetworkSide incoming : NetworkSide.values()) for(NetworkSide destination : NetworkSide.values())
                if(incoming != destination) edges.add(new LiquidNetworkEdge(in(ref, incoming), out(ref, destination), LiquidConstraint.any()));
        }
    }

    private static LiquidConstraint outputConstraint(Building build, FactorySnapshot snapshot, NetworkSide side){
        if(isTransport(build)) return LiquidConstraint.any();
        if(snapshot == null || snapshot.producedLiquids.isEmpty()) return null;
        if(build.block instanceof GenericCrafter crafter){
            List<ResourceRef> products = new ArrayList<>();
            if(crafter.outputLiquids == null) return null;
            for(int i = 0; i < crafter.outputLiquids.length; i++){
                int direction = crafter.liquidOutputDirections != null && crafter.liquidOutputDirections.length > i
                    ? crafter.liquidOutputDirections[i] : -1;
                if(direction == -1 || direction >= 0 && direction <= 3
                    && NetworkSide.rotation(build.rotation + direction) == side){
                    ResourceRef product = ref(crafter.outputLiquids[i].liquid);
                    if(snapshot.producedLiquids.contains(product)) products.add(product);
                }
            }
            return products.isEmpty() ? null : LiquidConstraint.oneOf(products);
        }
        return LiquidConstraint.oneOf(snapshot.producedLiquids);
    }

    private static LiquidConstraint inputConstraint(Building build, FactorySnapshot snapshot, NetworkSide side){
        if(isUnsupportedTransport(build, snapshot)) return null;
        if(isTransport(build)){
            return inputSides(build).contains(side) ? LiquidConstraint.any() : null;
        }
        if(snapshot == null) return null;
        List<ResourceRef> accepted = new ArrayList<>();
        for(ResourceState input : snapshot.inputs){
            if(input.kind != ResourceKind.liquid) continue;
            if(input.ref().id != null) accepted.add(input.ref());
            accepted.addAll(input.acceptedResources);
        }
        return accepted.isEmpty() ? null : LiquidConstraint.oneOf(accepted);
    }

    private static void merge(Map<NetworkPort, LiquidConstraint> constraints, NetworkPort port, LiquidConstraint value){
        LiquidConstraint existing = constraints.get(port);
        constraints.put(port, existing == null ? value : existing.union(value));
    }

    private static List<Adjacent> adjacent(Building build){
        List<Adjacent> result = new ArrayList<>();
        if(build.tile == null) return result;
        int offset = -(build.block.size - 1) / 2;
        int minX = build.tile.x + offset, minY = build.tile.y + offset;
        int maxX = minX + build.block.size - 1, maxY = minY + build.block.size - 1;
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

    private static ResourceRef ref(Liquid liquid){
        return new ResourceRef(ResourceKind.liquid, liquid.name, liquid.localizedName);
    }

    private static NetworkPort in(BuildingRef ref, NetworkSide side){ return new NetworkPort(ref, side, "in"); }
    private static NetworkPort out(BuildingRef ref, NetworkSide side){ return new NetworkPort(ref, side, "out"); }

    private static final class Adjacent{
        final Building building;
        final NetworkSide side;

        Adjacent(Building building, NetworkSide side){
            this.building = building;
            this.side = side;
        }
    }
}
