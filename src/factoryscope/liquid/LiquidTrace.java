package factoryscope.liquid;

import factoryscope.area.BuildingRef;
import factoryscope.model.ResourceRef;
import factoryscope.network.*;
import factoryscope.trace.TraceDirection;

import java.util.*;

/** Immutable evidence for one resource-specific input or output trace. */
public final class LiquidTrace{
    public final BuildingRef target;
    public final ResourceRef liquid;
    public final TraceDirection direction;
    /** Whether the requested target had a readable snapshot inside the selected area. */
    public final boolean targetIncluded;
    public final boolean targetUsesLiquid;
    public final boolean complete;
    public final boolean noRouteProven;
    public final boolean requirementsIncomplete;
    public final boolean topologyIncomplete;
    public final List<LiquidNetworkEdge> traversedEdges;
    public final List<LiquidTraceEndpoint> endpoints;
    public final List<NetworkPort> boundaryContinuations;
    /** Unsupported transport buildings that affected this resource-specific trace, including a boundary-adjacent block. */
    public final List<BuildingRef> unsupportedTransports;
    public final List<LiquidInterruption> unsupportedInterruptions;
    public final List<LiquidUncertainty> incompleteConnections;
    public final List<BuildingRef> structuralDeadEnds;

    LiquidTrace(BuildingRef target, ResourceRef liquid, TraceDirection direction, boolean targetIncluded,
                boolean targetUsesLiquid,
                boolean complete, boolean noRouteProven, boolean requirementsIncomplete, boolean topologyIncomplete,
                Collection<LiquidNetworkEdge> traversedEdges,
                Collection<LiquidTraceEndpoint> endpoints, Collection<NetworkPort> boundaryContinuations,
                Collection<BuildingRef> unsupportedTransports,
                Collection<LiquidInterruption> unsupportedInterruptions,
                Collection<LiquidUncertainty> incompleteConnections, Collection<BuildingRef> structuralDeadEnds){
        this.target = target;
        this.liquid = liquid;
        this.direction = direction;
        this.targetIncluded = targetIncluded;
        this.targetUsesLiquid = targetUsesLiquid;
        this.complete = complete;
        this.noRouteProven = noRouteProven;
        this.requirementsIncomplete = requirementsIncomplete;
        this.topologyIncomplete = topologyIncomplete;
        this.traversedEdges = List.copyOf(traversedEdges);
        this.endpoints = List.copyOf(endpoints);
        this.boundaryContinuations = List.copyOf(boundaryContinuations);
        this.unsupportedTransports = List.copyOf(unsupportedTransports);
        this.unsupportedInterruptions = List.copyOf(unsupportedInterruptions);
        this.incompleteConnections = List.copyOf(incompleteConnections);
        this.structuralDeadEnds = List.copyOf(structuralDeadEnds);
    }

    public List<LiquidTraceEndpoint> producers(){
        return endpoints.stream().filter(endpoint -> endpoint.kind == factoryscope.trace.TraceEndpointKind.producer).toList();
    }
}
