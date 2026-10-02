package factoryscope.trace;

import factoryscope.analysis.DiagnosticResult;
import factoryscope.area.BuildingRef;

import java.util.Objects;

/** A unique building endpoint reached by a resource-specific structural search. */
public final class TraceEndpoint{
    public final BuildingRef building;
    public final TraceEndpointKind kind;
    public final DiagnosticResult diagnostic;
    public final TracePath path;

    public TraceEndpoint(BuildingRef building, TraceEndpointKind kind, DiagnosticResult diagnostic, TracePath path){
        this.building = Objects.requireNonNull(building, "building");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.diagnostic = diagnostic;
        this.path = Objects.requireNonNull(path, "path");
    }
}
