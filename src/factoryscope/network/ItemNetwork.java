package factoryscope.network;

import factoryscope.area.*;
import factoryscope.model.*;

import java.util.*;

/** One area-scoped graph and the boundary/coverage facts needed to interpret it honestly. */
public final class ItemNetwork{
    public final NetworkGraph graph;
    public final List<NetworkPort> boundaryPorts;
    public final List<BuildingRef> unsupportedTransport;
    public final NetworkCompleteness completeness;
    /** Items whose configuration or production metadata makes them relevant to this selection. */
    public final List<ResourceRef> resources;

    public ItemNetwork(NetworkGraph graph, Collection<NetworkPort> boundaryPorts, Collection<BuildingRef> unsupportedTransport,
                       Collection<ResourceRef> resources){
        this.graph = Objects.requireNonNull(graph, "graph");
        this.boundaryPorts = ordered(boundaryPorts);
        this.unsupportedTransport = orderedRefs(unsupportedTransport);
        this.completeness = this.unsupportedTransport.isEmpty()
            ? NetworkCompleteness.complete : NetworkCompleteness.partialUnsupportedTransport;
        TreeSet<ResourceRef> orderedResources = new TreeSet<>(Comparator.comparing(ResourceRef::key));
        orderedResources.addAll(resources);
        this.resources = List.copyOf(orderedResources);
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
