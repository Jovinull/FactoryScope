package factoryscope.trace;

import factoryscope.analysis.*;
import factoryscope.area.*;
import factoryscope.model.*;
import factoryscope.network.*;

import java.util.*;

/** Correlates existing diagnostic snapshots with the area's item topology. */
public final class TraceAnalyzer{
    private static final Comparator<BuildingRef> BUILDING_ORDER = Comparator
        .comparingInt((BuildingRef ref) -> ref.tileX)
        .thenComparingInt(ref -> ref.tileY)
        .thenComparing(ref -> ref.blockId)
        .thenComparingInt(ref -> ref.teamId);

    private TraceAnalyzer(){
    }

    public static SupplyTrace input(AreaDiagnosticResult area, BuildingRef target, ResourceRef item){
        return analyze(area, target, item, TraceDirection.input);
    }

    public static SupplyTrace output(AreaDiagnosticResult area, BuildingRef target, ResourceRef item){
        return analyze(area, target, item, TraceDirection.output);
    }

    private static SupplyTrace analyze(AreaDiagnosticResult area, BuildingRef target, ResourceRef item,
                                       TraceDirection direction){
        Objects.requireNonNull(area, "area");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(item, "item");
        if(item.kind != ResourceKind.item || item.id == null){
            throw new IllegalArgumentException("a trace requires an identified item");
        }

        ItemNetwork network = area.network;
        if(network == null){
            return empty(target, item, direction, false, true);
        }

        AreaEntry targetEntry = findEntry(area.entries, target);
        boolean targetUsesItem = targetEntry != null && targetEntry.snapshot != null && (direction == TraceDirection.input
            ? targetEntry.snapshot.inputs.stream().anyMatch(input -> input.kind == ResourceKind.item && item.equals(input.ref()))
            : targetEntry.snapshot.producedItems.contains(item));
        List<NetworkPort> roots = ports(network.graph, target, direction == TraceDirection.input ? "in" : "out");
        boolean diagnosticsIncomplete = area.summary.skipped() > 0 || targetEntry == null || targetEntry.snapshot == null;
        boolean topologyIncomplete = roots.isEmpty();

        Search search = direction == TraceDirection.input
            ? reverseSearch(network.graph, roots, item)
            : forwardSearch(network.graph, roots, item);

        Map<BuildingRef, List<NetworkPort>> portsByBuilding = portsByBuilding(network.graph);
        List<TraceEndpoint> endpoints = endpoints(area.entries, network, portsByBuilding, target, item, direction, search);
        List<NetworkPort> boundaries = relevantBoundaries(network, search.visited, direction);
        List<NetworkInterruption> interruptions = relevantInterruptions(network, search.visited, direction);
        List<BuildingRef> unsupportedInArea = network.unsupportedTransport;
        List<BuildingRef> deadEnds = deadEnds(network.graph, search.visited, target, item, direction, endpoints,
            boundaries, interruptions);

        boolean complete = !diagnosticsIncomplete && !topologyIncomplete && unsupportedInArea.isEmpty()
            && interruptions.isEmpty() && boundaries.isEmpty();
        boolean noRouteProven = targetUsesItem && endpoints.isEmpty() && complete;

        List<NetworkFinding> findings = findings(direction, targetUsesItem, endpoints, boundaries,
            interruptions, unsupportedInArea, deadEnds, noRouteProven, complete);
        return new SupplyTrace(target, item, direction, targetUsesItem, complete, noRouteProven,
            diagnosticsIncomplete, topologyIncomplete, endpoints, boundaries, interruptions, unsupportedInArea,
            deadEnds, findings);
    }

    private static SupplyTrace empty(BuildingRef target, ResourceRef item, TraceDirection direction,
                                     boolean targetUsesItem, boolean diagnosticsIncomplete){
        return new SupplyTrace(target, item, direction, targetUsesItem, false, false, diagnosticsIncomplete,
            true, List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
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

    private static Search reverseSearch(NetworkGraph graph, Collection<NetworkPort> roots, ResourceRef item){
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
                if(!edge.items.allows(item) || !search.visited.add(edge.from)) continue;
                search.nextToTarget.put(edge.from, edge);
                search.pathRoots.put(edge.from, search.pathRoots.get(current));
                search.pending.addLast(edge.from);
            }
        }
        search.pathEdges = Map.copyOf(search.nextToTarget);
        search.pathRoots = Map.copyOf(search.pathRoots);
        return search;
    }

    private static Search forwardSearch(NetworkGraph graph, Collection<NetworkPort> roots, ResourceRef item){
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
                if(!edge.items.allows(item) || !search.visited.add(edge.to)) continue;
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
                                                  ResourceRef item, TraceDirection direction, Search search){
        Map<BuildingRef, TraceEndpoint> found = new TreeMap<>(BUILDING_ORDER);
        for(AreaEntry entry : entries){
            if(entry.snapshot == null || entry.ref.equals(target)) continue;
            boolean matches = direction == TraceDirection.input
                ? entry.snapshot.producedItems.contains(item)
                : entry.snapshot.inputs.stream().anyMatch(input -> input.kind == ResourceKind.item && item.equals(input.ref()));
            if(!matches) continue;

            TracePath path = bestPath(portsByBuilding.getOrDefault(entry.ref, List.of()), direction, search);
            if(path != null){
                TraceEndpointKind kind = direction == TraceDirection.input ? TraceEndpointKind.producer : TraceEndpointKind.consumer;
                found.putIfAbsent(entry.ref, new TraceEndpoint(entry.ref, kind, entry.result, path));
            }
        }

        for(BuildingRef ref : network.storageEndpoints){
            if(ref.equals(target) || found.containsKey(ref)) continue;
            TracePath path = bestPath(portsByBuilding.getOrDefault(ref, List.of()), direction, search);
            if(path != null) found.putIfAbsent(ref, new TraceEndpoint(ref, TraceEndpointKind.storage, null, path));
        }
        return List.copyOf(found.values());
    }

    private static TracePath bestPath(List<NetworkPort> ports, TraceDirection direction, Search search){
        String channel = direction == TraceDirection.input ? "out" : "in";
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

    private static TracePath pathToTarget(NetworkPort source, Search search){
        NetworkPort target = search.pathRoots.get(source);
        return target == null ? null : TracePath.toTarget(source, target, search.pathEdges);
    }

    private static TracePath pathFromSource(NetworkPort target, Search search){
        NetworkPort source = search.pathRoots.get(target);
        return source == null ? null : TracePath.fromSource(source, target, search.pathEdges);
    }

    private static List<NetworkPort> relevantBoundaries(ItemNetwork network, Set<NetworkPort> visited,
                                                         TraceDirection direction){
        Collection<NetworkPort> candidates = direction == TraceDirection.input ? network.boundaryInputs : network.boundaryPorts;
        TreeSet<NetworkPort> result = new TreeSet<>();
        for(NetworkPort port : candidates) if(visited.contains(port)) result.add(port);
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

    private static List<BuildingRef> deadEnds(NetworkGraph graph, Set<NetworkPort> visited, BuildingRef target,
                                               ResourceRef item, TraceDirection direction, List<TraceEndpoint> endpoints,
                                               List<NetworkPort> boundaries, List<NetworkInterruption> interruptions){
        Set<BuildingRef> endpointRefs = new HashSet<>();
        for(TraceEndpoint endpoint : endpoints) endpointRefs.add(endpoint.building);
        Set<NetworkPort> continuedOutside = new HashSet<>(boundaries);
        for(NetworkInterruption interruption : interruptions) continuedOutside.add(interruption.port);
        TreeSet<BuildingRef> result = new TreeSet<>(BUILDING_ORDER);
        String terminalChannel = direction == TraceDirection.input ? "in" : "out";
        for(NetworkPort port : visited){
            if(!port.channel.equals(terminalChannel) || port.building.equals(target) || endpointRefs.contains(port.building)
                || continuedOutside.contains(port)) continue;
            boolean hasContinuation = direction == TraceDirection.input
                ? graph.incoming(port).stream().anyMatch(edge -> edge.items.allows(item))
                : graph.outgoing(port).stream().anyMatch(edge -> edge.items.allows(item));
            if(!hasContinuation) result.add(port.building);
        }
        return List.copyOf(result);
    }

    private static List<NetworkFinding> findings(TraceDirection direction, boolean targetUsesItem,
                                                  List<TraceEndpoint> endpoints, List<NetworkPort> boundaries,
                                                  List<NetworkInterruption> interruptions, List<BuildingRef> unsupported,
                                                  List<BuildingRef> deadEnds, boolean noRouteProven, boolean complete){
        List<NetworkFinding> result = new ArrayList<>();
        NetworkFinding.Certainty uncertainty = complete ? NetworkFinding.Certainty.proven : NetworkFinding.Certainty.incomplete;
        if(!boundaries.isEmpty()) result.add(new NetworkFinding(NetworkFinding.Kind.routeContinuesOutsideArea,
            NetworkFinding.Certainty.informational, boundaries.get(0).building));
        for(NetworkInterruption interruption : interruptions){
            result.add(new NetworkFinding(NetworkFinding.Kind.unsupportedTransport,
                NetworkFinding.Certainty.incomplete, interruption.transport));
        }
        for(BuildingRef ref : deadEnds){
            result.add(new NetworkFinding(NetworkFinding.Kind.structuralDeadEnd, uncertainty, ref));
        }
        if(endpoints.isEmpty() && targetUsesItem && boundaries.isEmpty()){
            if(!unsupported.isEmpty() || !interruptions.isEmpty()){
                result.add(new NetworkFinding(NetworkFinding.Kind.unsupportedTransport,
                    NetworkFinding.Certainty.incomplete, unsupported.isEmpty() ? interruptions.get(0).transport : unsupported.get(0)));
            }else if(noRouteProven){
                result.add(new NetworkFinding(direction == TraceDirection.input
                    ? NetworkFinding.Kind.noStructuralInputRoute : NetworkFinding.Kind.noStructuralOutputRoute,
                    NetworkFinding.Certainty.proven, null));
                if(direction == TraceDirection.input){
                    result.add(new NetworkFinding(NetworkFinding.Kind.noReachableInAreaProducer,
                        NetworkFinding.Certainty.proven, null));
                }
            }
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
        Map<NetworkPort, NetworkEdge> pathEdges = Map.of();
        Map<NetworkPort, NetworkPort> pathRoots = new HashMap<>();
    }
}
