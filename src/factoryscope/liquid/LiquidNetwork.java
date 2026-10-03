package factoryscope.liquid;

import factoryscope.area.BuildingRef;
import factoryscope.model.ResourceRef;
import factoryscope.network.*;

import java.util.*;

/** Area-scoped liquid graph plus evidence that affects how completely it can be interpreted. */
public final class LiquidNetwork{
    public final LiquidNetworkGraph graph;
    /** Known outgoing structural continuations beyond the selected area. */
    public final List<NetworkPort> boundaryPorts;
    /** Known incoming structural continuations from beyond the selected area. */
    public final List<NetworkPort> boundaryInputs;
    public final Map<NetworkPort, LiquidConstraint> boundaryOutputConstraints;
    public final Map<NetworkPort, LiquidConstraint> boundaryInputConstraints;
    public final List<BuildingRef> unsupportedTransport;
    public final List<LiquidInterruption> unsupportedConnections;
    public final List<BuildingRef> storageEndpoints;
    public final List<BuildingRef> incompleteRequirements;
    public final List<BuildingRef> incompleteProducts;
    /** Resource-scoped continuation markers where a known route touches incomplete producer/consumer metadata. */
    public final List<LiquidUncertainty> incompleteConnections;
    public final List<ResourceRef> resources;

    public LiquidNetwork(LiquidNetworkGraph graph, Collection<NetworkPort> boundaryPorts,
                         Collection<NetworkPort> boundaryInputs,
                         Map<NetworkPort, LiquidConstraint> boundaryOutputConstraints,
                         Map<NetworkPort, LiquidConstraint> boundaryInputConstraints,
                         Collection<BuildingRef> unsupportedTransport,
                         Collection<LiquidInterruption> unsupportedConnections,
                         Collection<BuildingRef> storageEndpoints,
                         Collection<BuildingRef> incompleteRequirements,
                         Collection<BuildingRef> incompleteProducts,
                         Collection<ResourceRef> resources){
        this(graph, boundaryPorts, boundaryInputs, boundaryOutputConstraints, boundaryInputConstraints,
            unsupportedTransport, unsupportedConnections, storageEndpoints, incompleteRequirements,
            incompleteProducts, List.of(), resources);
    }

    public LiquidNetwork(LiquidNetworkGraph graph, Collection<NetworkPort> boundaryPorts,
                         Collection<NetworkPort> boundaryInputs,
                         Map<NetworkPort, LiquidConstraint> boundaryOutputConstraints,
                         Map<NetworkPort, LiquidConstraint> boundaryInputConstraints,
                         Collection<BuildingRef> unsupportedTransport,
                         Collection<LiquidInterruption> unsupportedConnections,
                         Collection<BuildingRef> storageEndpoints,
                         Collection<BuildingRef> incompleteRequirements,
                         Collection<BuildingRef> incompleteProducts,
                         Collection<LiquidUncertainty> incompleteConnections,
                         Collection<ResourceRef> resources){
        this.graph = Objects.requireNonNull(graph, "graph");
        this.boundaryPorts = orderedPorts(boundaryPorts);
        this.boundaryInputs = orderedPorts(boundaryInputs);
        this.boundaryOutputConstraints = orderedConstraints(this.boundaryPorts, boundaryOutputConstraints);
        this.boundaryInputConstraints = orderedConstraints(this.boundaryInputs, boundaryInputConstraints);
        this.unsupportedTransport = orderedRefs(unsupportedTransport);
        List<LiquidInterruption> interruptions = new ArrayList<>(unsupportedConnections);
        Collections.sort(interruptions);
        this.unsupportedConnections = List.copyOf(new LinkedHashSet<>(interruptions));
        this.storageEndpoints = orderedRefs(storageEndpoints);
        this.incompleteRequirements = orderedRefs(incompleteRequirements);
        this.incompleteProducts = orderedRefs(incompleteProducts);
        List<LiquidUncertainty> orderedIncomplete = new ArrayList<>(incompleteConnections);
        Collections.sort(orderedIncomplete);
        this.incompleteConnections = List.copyOf(new LinkedHashSet<>(orderedIncomplete));
        TreeMap<String, ResourceRef> orderedResources = new TreeMap<>();
        for(ResourceRef resource : resources){
            if(resource != null && resource.kind == factoryscope.model.ResourceKind.liquid && resource.id != null){
                orderedResources.putIfAbsent(resource.key(), resource);
            }
        }
        this.resources = List.copyOf(orderedResources.values());
    }

    public boolean partial(){
        return !unsupportedTransport.isEmpty() || !unsupportedConnections.isEmpty()
            || !boundaryPorts.isEmpty() || !boundaryInputs.isEmpty() || !incompleteRequirements.isEmpty()
            || !incompleteProducts.isEmpty() || !incompleteConnections.isEmpty();
    }

    private static List<NetworkPort> orderedPorts(Collection<NetworkPort> ports){
        return List.copyOf(new TreeSet<>(ports));
    }

    private static Map<NetworkPort, LiquidConstraint> orderedConstraints(Collection<NetworkPort> ports,
            Map<NetworkPort, LiquidConstraint> constraints){
        Map<NetworkPort, LiquidConstraint> result = new TreeMap<>();
        for(NetworkPort port : ports){
            LiquidConstraint value = constraints.get(port);
            if(value != null) result.put(port, value);
        }
        return Collections.unmodifiableMap(result);
    }

    private static List<BuildingRef> orderedRefs(Collection<BuildingRef> refs){
        TreeSet<BuildingRef> ordered = new TreeSet<>(Comparator
            .comparingInt((BuildingRef ref) -> ref.tileX).thenComparingInt(ref -> ref.tileY)
            .thenComparing(ref -> ref.blockId).thenComparingInt(ref -> ref.teamId));
        ordered.addAll(refs);
        return List.copyOf(ordered);
    }
}
