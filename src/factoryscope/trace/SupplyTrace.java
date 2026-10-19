package factoryscope.trace;

import factoryscope.area.BuildingRef;
import factoryscope.model.ResourceRef;
import factoryscope.network.*;

import java.util.*;

/** Immutable result for one resource trace within one area snapshot. */
public final class SupplyTrace{
    public final BuildingRef target;
    public final ResourceRef resource;
    public final TraceDirection direction;
    public final boolean targetUsesResource;
    public final boolean complete;
    public final boolean noRouteProven;
    public final boolean diagnosticsIncomplete;
    public final boolean topologyIncomplete;
    /** Every resource-compatible edge reached by the directional search, including alternate branches. */
    public final List<NetworkEdge> traversedEdges;
    public final List<TraceEndpoint> endpoints;
    public final List<NetworkPort> boundaryContinuations;
    public final List<NetworkInterruption> unsupportedInterruptions;
    public final List<BuildingRef> unsupportedInArea;
    public final List<BuildingRef> structuralDeadEnds;
    /** Buildings this trace reaches whose state could not be read while the topology was built. */
    public final List<BuildingRef> unreadableBuildings;
    public final List<NetworkFinding> findings;

    SupplyTrace(BuildingRef target, ResourceRef resource, TraceDirection direction, boolean targetUsesResource,
                boolean complete, boolean noRouteProven, boolean diagnosticsIncomplete, boolean topologyIncomplete,
                Collection<NetworkEdge> traversedEdges, Collection<TraceEndpoint> endpoints,
                Collection<NetworkPort> boundaryContinuations,
                Collection<NetworkInterruption> unsupportedInterruptions, Collection<BuildingRef> unsupportedInArea,
                Collection<BuildingRef> structuralDeadEnds, Collection<BuildingRef> unreadableBuildings,
                Collection<NetworkFinding> findings){
        this.target = target;
        this.resource = resource;
        this.direction = direction;
        this.targetUsesResource = targetUsesResource;
        this.complete = complete;
        this.noRouteProven = noRouteProven;
        this.diagnosticsIncomplete = diagnosticsIncomplete;
        this.topologyIncomplete = topologyIncomplete;
        this.traversedEdges = List.copyOf(traversedEdges);
        this.endpoints = List.copyOf(endpoints);
        this.boundaryContinuations = List.copyOf(boundaryContinuations);
        this.unsupportedInterruptions = List.copyOf(unsupportedInterruptions);
        this.unsupportedInArea = List.copyOf(unsupportedInArea);
        this.structuralDeadEnds = List.copyOf(structuralDeadEnds);
        this.unreadableBuildings = List.copyOf(unreadableBuildings);
        this.findings = List.copyOf(findings);
    }

    public List<TraceEndpoint> producers(){
        return endpoints.stream().filter(endpoint -> endpoint.kind == TraceEndpointKind.producer).toList();
    }
}
