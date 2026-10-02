package factoryscope.trace;

import factoryscope.area.BuildingRef;
import factoryscope.model.ResourceRef;
import factoryscope.network.*;

import java.util.*;

/** Immutable result for one item trace within one area snapshot. */
public final class SupplyTrace{
    public final BuildingRef target;
    public final ResourceRef item;
    public final TraceDirection direction;
    public final boolean targetUsesItem;
    public final boolean complete;
    public final boolean noRouteProven;
    public final boolean diagnosticsIncomplete;
    public final boolean topologyIncomplete;
    public final List<TraceEndpoint> endpoints;
    public final List<NetworkPort> boundaryContinuations;
    public final List<NetworkInterruption> unsupportedInterruptions;
    public final List<BuildingRef> unsupportedInArea;
    public final List<BuildingRef> structuralDeadEnds;
    public final List<NetworkFinding> findings;

    SupplyTrace(BuildingRef target, ResourceRef item, TraceDirection direction, boolean targetUsesItem,
                boolean complete, boolean noRouteProven, boolean diagnosticsIncomplete, boolean topologyIncomplete,
                Collection<TraceEndpoint> endpoints, Collection<NetworkPort> boundaryContinuations,
                Collection<NetworkInterruption> unsupportedInterruptions, Collection<BuildingRef> unsupportedInArea,
                Collection<BuildingRef> structuralDeadEnds, Collection<NetworkFinding> findings){
        this.target = target;
        this.item = item;
        this.direction = direction;
        this.targetUsesItem = targetUsesItem;
        this.complete = complete;
        this.noRouteProven = noRouteProven;
        this.diagnosticsIncomplete = diagnosticsIncomplete;
        this.topologyIncomplete = topologyIncomplete;
        this.endpoints = List.copyOf(endpoints);
        this.boundaryContinuations = List.copyOf(boundaryContinuations);
        this.unsupportedInterruptions = List.copyOf(unsupportedInterruptions);
        this.unsupportedInArea = List.copyOf(unsupportedInArea);
        this.structuralDeadEnds = List.copyOf(structuralDeadEnds);
        this.findings = List.copyOf(findings);
    }

    public List<TraceEndpoint> producers(){
        return endpoints.stream().filter(endpoint -> endpoint.kind == TraceEndpointKind.producer).toList();
    }
}
