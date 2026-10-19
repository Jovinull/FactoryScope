package factoryscope.trace;

import factoryscope.analysis.*;
import factoryscope.area.*;
import factoryscope.model.*;
import factoryscope.network.*;

import java.util.*;

/** Correlates existing diagnostic snapshots with the area's resource topology. */
public final class TraceAnalyzer{
    private static final Comparator<BuildingRef> BUILDING_ORDER = Comparator
        .comparingInt((BuildingRef ref) -> ref.tileX)
        .thenComparingInt(ref -> ref.tileY)
        .thenComparing(ref -> ref.blockId)
        .thenComparingInt(ref -> ref.teamId);

    private TraceAnalyzer(){
    }

    public static SupplyTrace input(AreaDiagnosticResult area, BuildingRef target, ResourceRef resource){
        return analyze(area, target, resource, TraceDirection.input);
    }

    public static SupplyTrace output(AreaDiagnosticResult area, BuildingRef target, ResourceRef resource){
        return analyze(area, target, resource, TraceDirection.output);
    }

    private static SupplyTrace analyze(AreaDiagnosticResult area, BuildingRef target, ResourceRef resource,
                                       TraceDirection direction){
        Objects.requireNonNull(area, "area");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(resource, "resource");
        if(resource.id == null || !traceable(resource)){
            throw new IllegalArgumentException("a trace requires an identified item, unit, or block");
        }

        ItemNetwork network = area.network;
        if(network == null){
            return empty(target, resource, direction, false, true);
        }

        AreaEntry targetEntry = findEntry(area.entries, target);
        boolean targetUsesResource = targetEntry != null && targetEntry.snapshot != null && (direction == TraceDirection.input
            ? targetEntry.snapshot.inputs.stream().anyMatch(input -> input.kind == resource.kind && input.accepts(resource))
                || targetEntry.snapshot.acceptedPayloads.contains(resource)
            : products(targetEntry.snapshot, resource).contains(resource));
        String rootChannel = channel(resource, direction == TraceDirection.input);
        List<NetworkPort> roots = ports(network.graph, target, rootChannel);
        if(!targetUsesResource){
            return empty(target, resource, direction, false, targetEntry == null || targetEntry.snapshot == null, roots.isEmpty());
        }
        boolean topologyIncomplete = roots.isEmpty();

        Search search = direction == TraceDirection.input
            ? reverseSearch(network.graph, roots, resource)
            : forwardSearch(network.graph, roots, resource);
        int unlocatedSkipped = Math.max(0, area.summary.skipped() - area.skippedBuildings.size());
        SkippedBuildingIndex skippedIndex = new SkippedBuildingIndex(area.skippedBuildings);
        List<BuildingRef> relevantSkipped = relevantSkipped(skippedIndex, search.visited, target, resource, direction);
        SkippedBuildingIndex relevantSkippedIndex = new SkippedBuildingIndex(relevantSkipped);
        boolean diagnosticsIncomplete = unlocatedSkipped > 0 || !relevantSkipped.isEmpty()
            || targetEntry == null || targetEntry.snapshot == null;

        Map<BuildingRef, List<NetworkPort>> portsByBuilding = portsByBuilding(network.graph);
        List<TraceEndpoint> endpoints = endpoints(area.entries, network, portsByBuilding, target, resource, direction, search);
        List<NetworkPort> boundaries = relevantBoundaries(network, search.visited, resource, direction);
        List<NetworkInterruption> interruptions = relevantInterruptions(network, search.visited, direction);
        List<TopologyProbeFailure> probeFailures = relevantProbeFailures(network, search.visited, target);
        List<BuildingRef> unsupportedInArea = relevantUnsupported(network, target, interruptions);
        List<BuildingRef> deadEnds = deadEnds(network.graph, search.visited, target, resource, direction, endpoints,
            boundaries, interruptions, relevantSkippedIndex, unlocatedSkipped > 0, probeFailures);

        boolean complete = !diagnosticsIncomplete && !topologyIncomplete && unsupportedInArea.isEmpty()
            && interruptions.isEmpty() && boundaries.isEmpty() && probeFailures.isEmpty();
        boolean noRouteProven = endpoints.isEmpty() && complete && search.traversedEdges.isEmpty();

        List<NetworkFinding> findings = findings(direction, targetUsesResource, complete, endpoints, boundaries,
            unsupportedInArea, deadEnds, noRouteProven);
        return new SupplyTrace(target, resource, direction, targetUsesResource, complete, noRouteProven,
            diagnosticsIncomplete, topologyIncomplete, search.traversedEdges, endpoints, boundaries, interruptions, unsupportedInArea,
            deadEnds, probeFailures.stream().map(failure -> failure.building).distinct().toList(), findings);
    }

    private static SupplyTrace empty(BuildingRef target, ResourceRef resource, TraceDirection direction,
                                     boolean targetUsesResource, boolean diagnosticsIncomplete){
        return empty(target, resource, direction, targetUsesResource, diagnosticsIncomplete, true);
    }

    private static SupplyTrace empty(BuildingRef target, ResourceRef resource, TraceDirection direction,
                                     boolean targetUsesResource, boolean diagnosticsIncomplete, boolean topologyIncomplete){
        return new SupplyTrace(target, resource, direction, targetUsesResource, false, false, diagnosticsIncomplete,
            topologyIncomplete, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private static AreaEntry findEntry(List<AreaEntry> entries, BuildingRef ref){
        for(AreaEntry entry : entries) if(entry.ref.equals(ref)) return entry;
        return null;
    }

    private static List<NetworkPort> ports(NetworkGraph graph, BuildingRef ref, String channel){
        List<NetworkPort> result = new ArrayList<>();
        for(NetworkPort port : graph.ports){
            if(port.building.equals(ref) && port.channel.equals(channel)) result.add(port);
        }
        return result;
    }

    private static Map<BuildingRef, List<NetworkPort>> portsByBuilding(NetworkGraph graph){
        Map<BuildingRef, List<NetworkPort>> result = new HashMap<>();
        for(NetworkPort port : graph.ports){
            result.computeIfAbsent(port.building, ignored -> new ArrayList<>()).add(port);
        }
        result.replaceAll((ref, ports) -> List.copyOf(ports));
        return result;
    }

    private static Search reverseSearch(NetworkGraph graph, Collection<NetworkPort> roots, ResourceRef resource){
        Search search = new Search();
        for(NetworkPort root : roots){
            if(search.visited.add(root)){
                search.pending.addLast(root);
                search.pathRoots.put(root, root);
            }
        }
        while(!search.pending.isEmpty()){
            NetworkPort current = search.pending.removeFirst();
            for(NetworkEdge edge : graph.incoming(current)){
                if(!edge.resources.allows(resource)) continue;
                search.traversedEdges.add(edge);
                if(!search.visited.add(edge.from)) continue;
                search.nextToTarget.put(edge.from, edge);
                search.pathRoots.put(edge.from, search.pathRoots.get(current));
                search.pending.addLast(edge.from);
            }
        }
        search.pathEdges = Map.copyOf(search.nextToTarget);
        search.pathRoots = Map.copyOf(search.pathRoots);
        return search;
    }

    private static Search forwardSearch(NetworkGraph graph, Collection<NetworkPort> roots, ResourceRef resource){
        Search search = new Search();
        for(NetworkPort root : roots){
            if(search.visited.add(root)){
                search.pending.addLast(root);
                search.pathRoots.put(root, root);
            }
        }
        while(!search.pending.isEmpty()){
            NetworkPort current = search.pending.removeFirst();
            for(NetworkEdge edge : graph.outgoing(current)){
                if(!edge.resources.allows(resource)) continue;
                search.traversedEdges.add(edge);
                if(!search.visited.add(edge.to)) continue;
                search.fromSource.put(edge.to, edge);
                search.pathRoots.put(edge.to, search.pathRoots.get(current));
                search.pending.addLast(edge.to);
            }
        }
        search.pathEdges = Map.copyOf(search.fromSource);
        search.pathRoots = Map.copyOf(search.pathRoots);
        return search;
    }

    private static List<TraceEndpoint> endpoints(List<AreaEntry> entries, ItemNetwork network,
                                                  Map<BuildingRef, List<NetworkPort>> portsByBuilding, BuildingRef target,
                                                  ResourceRef resource, TraceDirection direction, Search search){
        Map<BuildingRef, TraceEndpoint> found = new TreeMap<>(BUILDING_ORDER);
        for(AreaEntry entry : entries){
            if(entry.snapshot == null || entry.ref.equals(target)) continue;
            boolean matches = direction == TraceDirection.input
                ? products(entry.snapshot, resource).contains(resource)
                : entry.snapshot.inputs.stream().anyMatch(input -> input.kind == resource.kind && input.accepts(resource))
                    || entry.snapshot.acceptedPayloads.contains(resource);
            if(!matches) continue;

            TracePath path = bestPath(portsByBuilding.getOrDefault(entry.ref, List.of()), resource, direction, search);
            if(path != null){
                TraceEndpointKind kind = direction == TraceDirection.input ? TraceEndpointKind.producer : TraceEndpointKind.consumer;
                found.putIfAbsent(entry.ref, new TraceEndpoint(entry.ref, kind, entry.result, path));
            }
        }

        for(BuildingRef ref : network.storageEndpoints){
            if(resource.kind != ResourceKind.item) break;
            if(ref.equals(target) || found.containsKey(ref)) continue;
            TracePath path = bestPath(portsByBuilding.getOrDefault(ref, List.of()), resource, direction, search);
            if(path != null) found.putIfAbsent(ref, new TraceEndpoint(ref, TraceEndpointKind.storage, null, path));
        }
        return List.copyOf(found.values());
    }

    private static TracePath bestPath(List<NetworkPort> ports, ResourceRef resource, TraceDirection direction, Search search){
        String channel = channel(resource, direction == TraceDirection.output);
        TracePath best = null;
        for(NetworkPort port : ports){
            if(!port.channel.equals(channel) || !search.visited.contains(port)) continue;
            TracePath candidate = direction == TraceDirection.input
                ? pathToTarget(port, search) : pathFromSource(port, search);
            if(candidate != null && (best == null || comparePaths(candidate, best) < 0)) best = candidate;
        }
        return best;
    }

    private static int comparePaths(TracePath left, TracePath right){
        int byLength = Integer.compare(left.edges().size(), right.edges().size());
        if(byLength != 0) return byLength;
        List<NetworkPort> leftPorts = left.ports();
        List<NetworkPort> rightPorts = right.ports();
        for(int i = 0; i < Math.min(leftPorts.size(), rightPorts.size()); i++){
            int byPort = leftPorts.get(i).compareTo(rightPorts.get(i));
            if(byPort != 0) return byPort;
        }
        return Integer.compare(leftPorts.size(), rightPorts.size());
    }

    private static boolean traceable(ResourceRef resource){
        return resource.kind == ResourceKind.item || resource.kind == ResourceKind.unit || resource.kind == ResourceKind.block;
    }

    private static List<ResourceRef> products(FactorySnapshot snapshot, ResourceRef resource){
        return resource.kind == ResourceKind.item ? snapshot.producedItems : snapshot.producedPayloads;
    }

    private static String channel(ResourceRef resource, boolean input){
        if(resource.kind == ResourceKind.item) return input ? "in" : "out";
        return input ? "payload-in" : "payload-out";
    }

    private static ResourceConstraint defaultConstraint(ResourceRef resource){
        return resource.kind == ResourceKind.item ? ResourceConstraint.anyItems() : ResourceConstraint.anyPayloads();
    }

    private static TracePath pathToTarget(NetworkPort source, Search search){
        NetworkPort target = search.pathRoots.get(source);
        return target == null ? null : TracePath.toTarget(source, target, search.pathEdges);
    }

    private static TracePath pathFromSource(NetworkPort target, Search search){
        NetworkPort source = search.pathRoots.get(target);
        return source == null ? null : TracePath.fromSource(source, target, search.pathEdges);
    }

    private static List<NetworkPort> relevantBoundaries(ItemNetwork network, Set<NetworkPort> visited,
                                                         ResourceRef resource, TraceDirection direction){
        Collection<NetworkPort> candidates = direction == TraceDirection.input ? network.boundaryInputs : network.boundaryPorts;
        Map<NetworkPort, ResourceConstraint> constraints = direction == TraceDirection.input
            ? network.boundaryInputConstraints : network.boundaryOutputConstraints;
        TreeSet<NetworkPort> result = new TreeSet<>();
        for(NetworkPort port : candidates){
            if(visited.contains(port) && constraints.getOrDefault(port, defaultConstraint(resource)).allows(resource)) result.add(port);
        }
        return List.copyOf(result);
    }

    private static List<NetworkInterruption> relevantInterruptions(ItemNetwork network, Set<NetworkPort> visited,
                                                                    TraceDirection direction){
        NetworkInterruption.Direction wanted = direction == TraceDirection.input
            ? NetworkInterruption.Direction.incoming : NetworkInterruption.Direction.outgoing;
        TreeSet<NetworkInterruption> result = new TreeSet<>();
        for(NetworkInterruption interruption : network.unsupportedConnections){
            if(interruption.direction == wanted && visited.contains(interruption.port)) result.add(interruption);
        }
        return List.copyOf(result);
    }

    private static List<BuildingRef> relevantUnsupported(ItemNetwork network, BuildingRef target,
                                                           List<NetworkInterruption> interruptions){
        TreeSet<BuildingRef> result = new TreeSet<>(BUILDING_ORDER);
        for(BuildingRef ref : network.unsupportedTransport){
            if(ref.equals(target)) result.add(ref);
        }
        for(NetworkInterruption interruption : interruptions) result.add(interruption.transport);
        return List.copyOf(result);
    }

    private static List<TopologyProbeFailure> relevantProbeFailures(ItemNetwork network, Set<NetworkPort> visited,
                                                                     BuildingRef target){
        TreeSet<TopologyProbeFailure> result = new TreeSet<>();
        for(TopologyProbeFailure failure : network.probeFailures){
            if(failure.building.equals(target)) result.add(failure);
            for(NetworkPort port : visited) if(port.building.equals(failure.building)) result.add(failure);
        }
        return List.copyOf(result);
    }

    private static List<BuildingRef> relevantSkipped(SkippedBuildingIndex skipped, Set<NetworkPort> visited,
                                                      BuildingRef target, ResourceRef resource, TraceDirection direction){
        String terminalChannel = channel(resource, direction == TraceDirection.input);
        TreeSet<BuildingRef> result = new TreeSet<>(BUILDING_ORDER);
        result.addAll(skipped.buildingsAt(visited));
        if(skipped.contains(target)) result.add(target);
        for(NetworkPort port : visited){
            if(port.channel.equals(terminalChannel)){
                result.addAll(skipped.adjacentTo(port));
            }
        }
        return List.copyOf(result);
    }

    private static boolean touchesSide(BuildingRef building, NetworkSide side, BuildingRef other){
        int buildingSize = Math.max(1, building.size);
        int otherSize = Math.max(1, other.size);
        int buildingOffset = -(buildingSize - 1) / 2;
        int otherOffset = -(otherSize - 1) / 2;
        int minX = building.tileX + buildingOffset, maxX = minX + buildingSize - 1;
        int minY = building.tileY + buildingOffset, maxY = minY + buildingSize - 1;
        int otherMinX = other.tileX + otherOffset, otherMaxX = otherMinX + otherSize - 1;
        int otherMinY = other.tileY + otherOffset, otherMaxY = otherMinY + otherSize - 1;
        return switch(side){
            case east -> otherMinX == maxX + 1 && overlaps(minY, maxY, otherMinY, otherMaxY);
            case west -> otherMaxX + 1 == minX && overlaps(minY, maxY, otherMinY, otherMaxY);
            case north -> otherMinY == maxY + 1 && overlaps(minX, maxX, otherMinX, otherMaxX);
            case south -> otherMaxY + 1 == minY && overlaps(minX, maxX, otherMinX, otherMaxX);
        };
    }

    private static boolean overlaps(int start, int end, int otherStart, int otherEnd){
        return start <= otherEnd && otherStart <= end;
    }

    private static List<BuildingRef> deadEnds(NetworkGraph graph, Set<NetworkPort> visited, BuildingRef target,
                                               ResourceRef resource, TraceDirection direction, List<TraceEndpoint> endpoints,
                                               List<NetworkPort> boundaries, List<NetworkInterruption> interruptions,
                                               SkippedBuildingIndex skipped, boolean hasUnlocatedSkipped,
                                               List<TopologyProbeFailure> probeFailures){
        if(hasUnlocatedSkipped) return List.of();
        Set<BuildingRef> endpointRefs = new HashSet<>();
        for(TraceEndpoint endpoint : endpoints) endpointRefs.add(endpoint.building);
        Set<NetworkPort> continuedOutside = new HashSet<>(boundaries);
        for(NetworkInterruption interruption : interruptions) continuedOutside.add(interruption.port);
        Set<BuildingRef> failed = new HashSet<>();
        for(TopologyProbeFailure failure : probeFailures) failed.add(failure.building);
        TreeSet<BuildingRef> result = new TreeSet<>(BUILDING_ORDER);
        result.addAll(RouteDeadEnds.find(visited,
            port -> direction == TraceDirection.input
                ? graph.incoming(port).stream().filter(edge -> edge.resources.allows(resource)).map(edge -> edge.from).toList()
                : graph.outgoing(port).stream().filter(edge -> edge.resources.allows(resource)).map(edge -> edge.to).toList(),
            port -> continuedOutside.contains(port) || !skipped.adjacentTo(port).isEmpty(),
            building -> building.equals(target) || endpointRefs.contains(building) || failed.contains(building)
                || skipped.contains(building)));
        return List.copyOf(result);
    }

    /** Spatially narrows skipped-building checks to buildings that occupy the port's adjacent edge. */
    private static final class SkippedBuildingIndex{
        private final Set<BuildingRef> buildings;
        private final Map<SkippedTile, List<BuildingRef>> byTile;

        SkippedBuildingIndex(Collection<BuildingRef> skipped){
            Set<BuildingRef> refs = new HashSet<>(skipped);
            Map<SkippedTile, List<BuildingRef>> index = new HashMap<>();
            for(BuildingRef ref : refs){
                int size = Math.max(1, ref.size);
                int offset = -(size - 1) / 2;
                int minX = ref.tileX + offset, minY = ref.tileY + offset;
                for(int x = minX; x < minX + size; x++){
                    for(int y = minY; y < minY + size; y++){
                        index.computeIfAbsent(new SkippedTile(ref.teamId, x, y), ignored -> new ArrayList<>()).add(ref);
                    }
                }
            }
            index.replaceAll((tile, refsAtTile) -> List.copyOf(refsAtTile));
            buildings = Set.copyOf(refs);
            byTile = Map.copyOf(index);
        }

        boolean contains(BuildingRef ref){
            return buildings.contains(ref);
        }

        Set<BuildingRef> buildingsAt(Set<NetworkPort> ports){
            Set<BuildingRef> found = new HashSet<>();
            for(NetworkPort port : ports){
                if(buildings.contains(port.building)) found.add(port.building);
            }
            return found;
        }

        List<BuildingRef> adjacentTo(NetworkPort port){
            BuildingRef building = port.building;
            int size = Math.max(1, building.size);
            int offset = -(size - 1) / 2;
            int minX = building.tileX + offset, maxX = minX + size - 1;
            int minY = building.tileY + offset, maxY = minY + size - 1;
            int x = switch(port.side){
                case east -> maxX + 1;
                case west -> minX - 1;
                default -> building.tileX;
            };
            int y = switch(port.side){
                case north -> maxY + 1;
                case south -> minY - 1;
                default -> building.tileY;
            };
            int from = (port.side == NetworkSide.east || port.side == NetworkSide.west) ? minY : minX;
            int to = (port.side == NetworkSide.east || port.side == NetworkSide.west) ? maxY : maxX;
            TreeSet<BuildingRef> found = new TreeSet<>(BUILDING_ORDER);
            for(int coordinate = from; coordinate <= to; coordinate++){
                SkippedTile tile = port.side == NetworkSide.east || port.side == NetworkSide.west
                    ? new SkippedTile(building.teamId, x, coordinate)
                    : new SkippedTile(building.teamId, coordinate, y);
                for(BuildingRef candidate : byTile.getOrDefault(tile, List.of())){
                    if(touchesSide(building, port.side, candidate)) found.add(candidate);
                }
            }
            return List.copyOf(found);
        }
    }

    private record SkippedTile(int teamId, int x, int y){ }

    private static List<NetworkFinding> findings(TraceDirection direction, boolean targetUsesResource, boolean complete,
                                                  List<TraceEndpoint> endpoints, List<NetworkPort> boundaries,
                                                  List<BuildingRef> unsupported,
                                                  List<BuildingRef> deadEnds, boolean noRouteProven){
        List<NetworkFinding> result = new ArrayList<>();
        if(!boundaries.isEmpty()) result.add(new NetworkFinding(NetworkFinding.Kind.routeContinuesOutsideArea,
            NetworkFinding.Certainty.informational, boundaries.get(0).building));
        for(BuildingRef ref : unsupported){
            result.add(new NetworkFinding(NetworkFinding.Kind.unsupportedTransport,
                NetworkFinding.Certainty.incomplete, ref));
        }
        for(BuildingRef ref : deadEnds){
            result.add(new NetworkFinding(NetworkFinding.Kind.structuralDeadEnd, NetworkFinding.Certainty.proven, ref));
        }
        if(noRouteProven){
            result.add(new NetworkFinding(direction == TraceDirection.input
                ? NetworkFinding.Kind.noStructuralInputRoute : NetworkFinding.Kind.noStructuralOutputRoute,
                NetworkFinding.Certainty.proven, null));
        }
        if(targetUsesResource && complete && endpoints.isEmpty()){
            result.add(new NetworkFinding(direction == TraceDirection.input
                ? NetworkFinding.Kind.noReachableInAreaProducer : NetworkFinding.Kind.noReachableInAreaConsumer,
                NetworkFinding.Certainty.proven, null));
        }
        for(TraceEndpoint endpoint : endpoints){
            if(endpoint.kind != TraceEndpointKind.producer || endpoint.diagnostic == null) continue;
            if(endpoint.diagnostic.reason() == DiagnosticReason.disabled){
                result.add(new NetworkFinding(NetworkFinding.Kind.reachableProducerDisabled,
                    NetworkFinding.Certainty.informational, endpoint.building));
            }else if(endpoint.diagnostic.severity() != Severity.normal){
                result.add(new NetworkFinding(NetworkFinding.Kind.reachableProducerProblem,
                    NetworkFinding.Certainty.informational, endpoint.building));
            }
        }
        return List.copyOf(result);
    }

    private static final class Search{
        final Set<NetworkPort> visited = new TreeSet<>();
        final ArrayDeque<NetworkPort> pending = new ArrayDeque<>();
        final Map<NetworkPort, NetworkEdge> nextToTarget = new HashMap<>();
        final Map<NetworkPort, NetworkEdge> fromSource = new HashMap<>();
        final Set<NetworkEdge> traversedEdges = new TreeSet<>();
        Map<NetworkPort, NetworkEdge> pathEdges = Map.of();
        Map<NetworkPort, NetworkPort> pathRoots = new HashMap<>();
    }
}
