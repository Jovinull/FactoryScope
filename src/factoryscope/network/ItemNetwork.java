package factoryscope.network;

import factoryscope.area.*;
import factoryscope.model.*;

import java.util.*;

/** One area-scoped graph and the boundary/coverage facts needed to interpret it honestly. */
public final class ItemNetwork{
    public final NetworkGraph graph;
    /** Known structural exits from the selected area. */
    public final List<NetworkPort> boundaryPorts;
    /** Input ports with a compatible known neighbor just outside the selected area. */
    public final List<NetworkPort> boundaryInputs;
    /** Item constraints imposed by known sources beyond each outgoing boundary port. */
    public final Map<NetworkPort, ItemConstraint> boundaryOutputConstraints;
    /** Item constraints imposed by known sources beyond each incoming boundary port. */
    public final Map<NetworkPort, ItemConstraint> boundaryInputConstraints;
    public final List<BuildingRef> unsupportedTransport;
    /** Known ports adjacent to unsupported transports, without inventing an edge through them. */
    public final List<NetworkInterruption> unsupportedConnections;
    public final List<BuildingRef> storageEndpoints;
    public final NetworkCompleteness completeness;
    /** Items whose configuration or production metadata makes them relevant to this selection. */
    public final List<ResourceRef> resources;

    public ItemNetwork(NetworkGraph graph, Collection<NetworkPort> boundaryPorts, Collection<BuildingRef> unsupportedTransport,
                       Collection<ResourceRef> resources){
        this(graph, boundaryPorts, List.of(), unsupportedTransport, List.of(), List.of(), resources);
    }

    public ItemNetwork(NetworkGraph graph, Collection<NetworkPort> boundaryPorts, Collection<NetworkPort> boundaryInputs,
                       Collection<BuildingRef> unsupportedTransport, Collection<NetworkInterruption> unsupportedConnections,
                       Collection<BuildingRef> storageEndpoints, Collection<ResourceRef> resources){
        this(graph, boundaryPorts, boundaryInputs, unsupportedTransport, unsupportedConnections, storageEndpoints,
            resources, Map.of(), Map.of());
    }

    public ItemNetwork(NetworkGraph graph, Collection<NetworkPort> boundaryPorts, Collection<NetworkPort> boundaryInputs,
                       Collection<BuildingRef> unsupportedTransport, Collection<NetworkInterruption> unsupportedConnections,
                       Collection<BuildingRef> storageEndpoints, Collection<ResourceRef> resources,
                       Map<NetworkPort, ItemConstraint> boundaryOutputConstraints,
                       Map<NetworkPort, ItemConstraint> boundaryInputConstraints){
        this.graph = Objects.requireNonNull(graph, "graph");
        this.boundaryPorts = ordered(boundaryPorts);
        this.boundaryInputs = ordered(boundaryInputs);
        this.boundaryOutputConstraints = constraints(this.boundaryPorts, boundaryOutputConstraints);
        this.boundaryInputConstraints = constraints(this.boundaryInputs, boundaryInputConstraints);
        this.unsupportedTransport = orderedRefs(unsupportedTransport);
        List<NetworkInterruption> interruptions = new ArrayList<>(unsupportedConnections);
        Collections.sort(interruptions);
        this.unsupportedConnections = List.copyOf(interruptions);
        this.storageEndpoints = orderedRefs(storageEndpoints);
        this.completeness = this.unsupportedTransport.isEmpty() && this.unsupportedConnections.isEmpty()
            ? NetworkCompleteness.complete : NetworkCompleteness.partialUnsupportedTransport;
        TreeSet<ResourceRef> orderedResources = new TreeSet<>(Comparator.comparing(ResourceRef::key));
        orderedResources.addAll(resources);
        this.resources = List.copyOf(orderedResources);
    }

    private static Map<NetworkPort, ItemConstraint> constraints(Collection<NetworkPort> ports,
                                                                  Map<NetworkPort, ItemConstraint> constraints){
        Map<NetworkPort, ItemConstraint> result = new TreeMap<>();
        for(NetworkPort port : ports){
            ItemConstraint constraint = constraints.get(port);
            if(constraint != null) result.put(port, constraint);
        }
        return Collections.unmodifiableMap(result);
    }

    private static List<NetworkPort> ordered(Collection<NetworkPort> ports){
        TreeSet<NetworkPort> result = new TreeSet<>(ports);
        return List.copyOf(result);
    }

    private static List<BuildingRef> orderedRefs(Collection<BuildingRef> refs){
        List<BuildingRef> result = new ArrayList<>(refs);
        result.sort(Comparator.comparingInt((BuildingRef ref) -> ref.tileX)
            .thenComparingInt(ref -> ref.tileY).thenComparing(ref -> ref.blockId).thenComparingInt(ref -> ref.teamId));
        return List.copyOf(result);
    }
}
