package factoryscope.liquid;

import factoryscope.analysis.DiagnosticResult;
import factoryscope.area.*;
import factoryscope.model.*;
import factoryscope.network.*;
import factoryscope.trace.TraceDirection;
import factoryscope.trace.TraceEndpointKind;

import java.util.*;

/** Pure resource-aware correlation of a liquid topology snapshot with existing factory snapshots. */
public final class LiquidTraceAnalyzer{
    private static final Comparator<BuildingRef> BUILDING_ORDER = Comparator
        .comparingInt((BuildingRef ref) -> ref.tileX).thenComparingInt(ref -> ref.tileY)
        .thenComparing(ref -> ref.blockId).thenComparingInt(ref -> ref.teamId);

    private LiquidTraceAnalyzer(){ }

    public static LiquidTrace input(AreaDiagnosticResult area, BuildingRef target, ResourceRef liquid){
        return analyze(area, target, liquid, TraceDirection.input);
    }

    public static LiquidTrace output(AreaDiagnosticResult area, BuildingRef target, ResourceRef liquid){
        return analyze(area, target, liquid, TraceDirection.output);
    }

    private static LiquidTrace analyze(AreaDiagnosticResult area, BuildingRef target, ResourceRef liquid,
                                       TraceDirection direction){
        Objects.requireNonNull(area, "area");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(liquid, "liquid");
        if(liquid.kind != ResourceKind.liquid || liquid.id == null){
            throw new IllegalArgumentException("a liquid trace requires an identified liquid");
        }
        LiquidNetwork network = area.liquids;
        AreaEntry targetEntry = findEntry(area.entries, target);
        boolean targetFound = targetEntry != null && targetEntry.snapshot != null;
        boolean uses = targetFound && (direction == TraceDirection.input
            ? targetEntry.snapshot.inputs.stream().anyMatch(input -> input.kind == ResourceKind.liquid && input.accepts(liquid))
            : targetEntry.snapshot.producedLiquids.contains(liquid));
        boolean targetMetadataIncomplete = targetFound && (direction == TraceDirection.input
            ? !targetEntry.snapshot.liquidInputsComplete : !targetEntry.snapshot.liquidOutputsComplete);
        boolean requirementsIncomplete = targetFound && targetMetadataIncomplete || network != null
            && (direction == TraceDirection.input ? network.incompleteRequirements.contains(target)
                : network.incompleteProducts.contains(target));
        if(network == null || !uses){
            return new LiquidTrace(target, liquid, direction, targetFound, uses, false, false,
                requirementsIncomplete, network == null || !targetFound || requirementsIncomplete, List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of());
        }

        String rootChannel = direction == TraceDirection.input ? "in" : "out";
        List<NetworkPort> roots = ports(network.graph, target, rootChannel);
        if(roots.isEmpty()){
            return new LiquidTrace(target, liquid, direction, targetFound, true, false, false,
                requirementsIncomplete, true, List.of(), List.of(), List.of(),
                network.unsupportedTransport.contains(target) ? List.of(target) : List.of(),
                List.of(), List.of(), List.of());
        }

        Search search = direction == TraceDirection.input
            ? reverse(network.graph, roots, liquid) : forward(network.graph, roots, liquid);
        Map<BuildingRef, List<NetworkPort>> portsByBuilding = portsByBuilding(network.graph);
        List<LiquidTraceEndpoint> endpoints = endpoints(area.entries, network, portsByBuilding,
            target, liquid, direction, search);
        List<NetworkPort> boundaries = relevantBoundaries(network, search.visited, liquid, direction);
        List<LiquidInterruption> interruptions = relevantInterruptions(network, search.visited, liquid, direction);
        List<LiquidUncertainty> incompleteConnections = relevantIncompleteConnections(network, search.visited,
            liquid, direction);
        List<BuildingRef> unsupported = relevantUnsupported(network, target, interruptions);
        List<BuildingRef> deadEnds = deadEnds(network.graph, search.visited, target, liquid, direction,
            endpoints, boundaries, interruptions, incompleteConnections);

        boolean topologyIncomplete = requirementsIncomplete || !unsupported.isEmpty() || !interruptions.isEmpty()
            || !incompleteConnections.isEmpty() || !boundaries.isEmpty();
        boolean diagnosticsIncomplete = !area.skippedBuildings.isEmpty();
        boolean complete = !topologyIncomplete && !diagnosticsIncomplete;
        // A zero-edge conclusion is intentionally conservative. A route ending in storage/dead-end is not "no route".
        boolean noRouteProven = endpoints.isEmpty() && boundaries.isEmpty() && interruptions.isEmpty()
            && incompleteConnections.isEmpty() && unsupported.isEmpty() && !requirementsIncomplete && !diagnosticsIncomplete
            && search.traversedEdges.isEmpty() && deadEnds.isEmpty();

        return new LiquidTrace(target, liquid, direction, targetFound, true, complete, noRouteProven,
            requirementsIncomplete, topologyIncomplete, search.traversedEdges, endpoints, boundaries,
            unsupported, interruptions, incompleteConnections, deadEnds);
    }

    private static AreaEntry findEntry(List<AreaEntry> entries, BuildingRef ref){
        for(AreaEntry entry : entries) if(entry.ref.equals(ref)) return entry;
        return null;
    }

    private static List<NetworkPort> ports(LiquidNetworkGraph graph, BuildingRef building, String channel){
        List<NetworkPort> result = new ArrayList<>();
        for(NetworkPort port : graph.ports) if(port.building.equals(building) && port.channel.equals(channel)) result.add(port);
        return result;
    }

    private static Search reverse(LiquidNetworkGraph graph, Collection<NetworkPort> roots, ResourceRef liquid){
        Search search = new Search();
        for(NetworkPort root : new TreeSet<>(roots)) if(search.visited.add(root)){
            search.pending.addLast(root);
            search.rootByPort.put(root, root);
            search.distanceByPort.put(root, 0);
        }
        while(!search.pending.isEmpty()){
            NetworkPort current = search.pending.removeFirst();
            for(LiquidNetworkEdge edge : graph.incoming(current)){
                if(!edge.liquids.allows(liquid)) continue;
                search.traversedEdges.add(edge);
                if(search.visited.add(edge.from)){
                    search.nextToTarget.put(edge.from, edge);
                    search.rootByPort.put(edge.from, search.rootByPort.get(current));
                    search.distanceByPort.put(edge.from, search.distanceByPort.get(current) + 1);
                    search.pending.addLast(edge.from);
                }
            }
        }
        return search;
    }

    private static Search forward(LiquidNetworkGraph graph, Collection<NetworkPort> roots, ResourceRef liquid){
        Search search = new Search();
        for(NetworkPort root : new TreeSet<>(roots)) if(search.visited.add(root)){
            search.pending.addLast(root);
            search.rootByPort.put(root, root);
            search.distanceByPort.put(root, 0);
        }
        while(!search.pending.isEmpty()){
            NetworkPort current = search.pending.removeFirst();
            for(LiquidNetworkEdge edge : graph.outgoing(current)){
                if(!edge.liquids.allows(liquid)) continue;
                search.traversedEdges.add(edge);
                if(search.visited.add(edge.to)){
                    search.fromSource.put(edge.to, edge);
                    search.rootByPort.put(edge.to, search.rootByPort.get(current));
                    search.distanceByPort.put(edge.to, search.distanceByPort.get(current) + 1);
                    search.pending.addLast(edge.to);
                }
            }
        }
        return search;
    }

    private static List<LiquidTraceEndpoint> endpoints(List<AreaEntry> entries, LiquidNetwork network,
            Map<BuildingRef, List<NetworkPort>> portsByBuilding, BuildingRef target, ResourceRef liquid,
            TraceDirection direction, Search search){
        Map<BuildingRef, LiquidTraceEndpoint> found = new TreeMap<>(BUILDING_ORDER);
        for(AreaEntry entry : entries){
            if(entry.snapshot == null || entry.ref.equals(target)) continue;
            boolean matches = direction == TraceDirection.input
                ? entry.snapshot.producedLiquids.contains(liquid)
                : entry.snapshot.inputs.stream().anyMatch(input -> input.kind == ResourceKind.liquid && input.accepts(liquid));
            if(!matches) continue;
            LiquidTracePath path = bestPath(portsByBuilding.getOrDefault(entry.ref, List.of()), direction, search);
            if(path != null){
                TraceEndpointKind kind = direction == TraceDirection.input ? TraceEndpointKind.producer : TraceEndpointKind.consumer;
                found.put(entry.ref, new LiquidTraceEndpoint(entry.ref, kind, entry.result, path));
            }
        }
        for(BuildingRef ref : network.storageEndpoints){
            if(ref.equals(target) || found.containsKey(ref)) continue;
            LiquidTracePath path = bestPath(portsByBuilding.getOrDefault(ref, List.of()), direction, search);
            if(path != null) found.put(ref, new LiquidTraceEndpoint(ref, TraceEndpointKind.storage, null, path));
        }
        return List.copyOf(found.values());
    }

    private static LiquidTracePath bestPath(List<NetworkPort> ports, TraceDirection direction, Search search){
        String wanted = direction == TraceDirection.input ? "out" : "in";
        NetworkPort bestPort = null;
        int bestDistance = Integer.MAX_VALUE;
        for(NetworkPort port : ports){
            if(!port.channel.equals(wanted)) continue;
            Integer distance = search.distanceByPort.get(port);
            if(distance != null && distance < bestDistance){
                bestPort = port;
                bestDistance = distance;
            }
        }
        if(bestPort == null) return null;
        NetworkPort root = search.rootFor(bestPort);
        if(root == null) return null;
        // Port order and the sorted BFS adjacency lists define the deterministic tie-break. Rebuilding
        // a full candidate path for every producer would turn a many-producer trace quadratic.
        return direction == TraceDirection.input
            ? LiquidTracePath.toTarget(bestPort, root, search.nextToTarget)
            : LiquidTracePath.fromSource(root, bestPort, search.fromSource);
    }

    private static List<NetworkPort> relevantBoundaries(LiquidNetwork network, Set<NetworkPort> visited,
                                                         ResourceRef liquid, TraceDirection direction){
        Collection<NetworkPort> candidates = direction == TraceDirection.input ? network.boundaryInputs : network.boundaryPorts;
        Map<NetworkPort, LiquidConstraint> constraints = direction == TraceDirection.input
            ? network.boundaryInputConstraints : network.boundaryOutputConstraints;
        TreeSet<NetworkPort> result = new TreeSet<>();
        for(NetworkPort port : candidates){
            if(visited.contains(port) && constraints.getOrDefault(port, LiquidConstraint.any()).allows(liquid)) result.add(port);
        }
        return List.copyOf(result);
    }

    private static List<LiquidInterruption> relevantInterruptions(LiquidNetwork network, Set<NetworkPort> visited,
                                                                   ResourceRef liquid, TraceDirection direction){
        LiquidInterruption.Direction wanted = direction == TraceDirection.input
            ? LiquidInterruption.Direction.incoming : LiquidInterruption.Direction.outgoing;
        TreeSet<LiquidInterruption> result = new TreeSet<>();
        for(LiquidInterruption interruption : network.unsupportedConnections){
            if(interruption.direction == wanted && visited.contains(interruption.port) && interruption.allows(liquid)) result.add(interruption);
        }
        return List.copyOf(result);
    }

    private static List<LiquidUncertainty> relevantIncompleteConnections(LiquidNetwork network,
            Set<NetworkPort> visited, ResourceRef liquid, TraceDirection direction){
        LiquidUncertainty.Direction wanted = direction == TraceDirection.input
            ? LiquidUncertainty.Direction.incoming : LiquidUncertainty.Direction.outgoing;
        TreeSet<LiquidUncertainty> result = new TreeSet<>();
        for(LiquidUncertainty uncertainty : network.incompleteConnections){
            if(uncertainty.direction == wanted && visited.contains(uncertainty.port) && uncertainty.allows(liquid)){
                result.add(uncertainty);
            }
        }
        return List.copyOf(result);
    }

    private static List<BuildingRef> relevantUnsupported(LiquidNetwork network, BuildingRef target,
                                                           List<LiquidInterruption> interruptions){
        TreeSet<BuildingRef> result = new TreeSet<>(BUILDING_ORDER);
        if(network.unsupportedTransport.contains(target)) result.add(target);
        for(LiquidInterruption interruption : interruptions) result.add(interruption.transport);
        return List.copyOf(result);
    }

    private static List<BuildingRef> deadEnds(LiquidNetworkGraph graph, Set<NetworkPort> visited, BuildingRef target,
            ResourceRef liquid, TraceDirection direction, List<LiquidTraceEndpoint> endpoints,
            List<NetworkPort> boundaries, List<LiquidInterruption> interruptions,
            List<LiquidUncertainty> incompleteConnections){
        Set<BuildingRef> endpointRefs = new HashSet<>();
        for(LiquidTraceEndpoint endpoint : endpoints) endpointRefs.add(endpoint.building);
        Set<NetworkPort> continuations = new HashSet<>(boundaries);
        for(LiquidInterruption interruption : interruptions) continuations.add(interruption.port);
        for(LiquidUncertainty uncertainty : incompleteConnections) continuations.add(uncertainty.port);
        TreeSet<BuildingRef> result = new TreeSet<>(BUILDING_ORDER);
        for(NetworkPort port : visited){
            if(port.building.equals(target) || endpointRefs.contains(port.building) || continuations.contains(port)) continue;
            boolean more = direction == TraceDirection.input
                ? graph.incoming(port).stream().anyMatch(edge -> edge.liquids.allows(liquid))
                : graph.outgoing(port).stream().anyMatch(edge -> edge.liquids.allows(liquid));
            if(!more) result.add(port.building);
        }
        return List.copyOf(result);
    }

    private static Map<BuildingRef, List<NetworkPort>> portsByBuilding(LiquidNetworkGraph graph){
        Map<BuildingRef, List<NetworkPort>> result = new HashMap<>();
        for(NetworkPort port : graph.ports) result.computeIfAbsent(port.building, ignored -> new ArrayList<>()).add(port);
        result.replaceAll((ref, ports) -> List.copyOf(ports));
        return result;
    }

    private static final class Search{
        // Roots and each adjacency list are visited in deterministic sorted order. Hash membership
        // therefore avoids an extra log(V) factor without making predecessor choice nondeterministic.
        final Set<NetworkPort> visited = new HashSet<>();
        final ArrayDeque<NetworkPort> pending = new ArrayDeque<>();
        final Set<LiquidNetworkEdge> traversedEdges = new LinkedHashSet<>();
        final Map<NetworkPort, LiquidNetworkEdge> nextToTarget = new HashMap<>();
        final Map<NetworkPort, LiquidNetworkEdge> fromSource = new HashMap<>();
        final Map<NetworkPort, NetworkPort> rootByPort = new HashMap<>();
        final Map<NetworkPort, Integer> distanceByPort = new HashMap<>();
        NetworkPort rootFor(NetworkPort port){ return rootByPort.get(port); }
    }
}
