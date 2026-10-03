package factoryscope.liquid;

import factoryscope.analysis.DiagnosticResult;
import factoryscope.area.BuildingRef;
import factoryscope.trace.TraceEndpointKind;

import java.util.Objects;

/** A unique in-area building endpoint reached by a liquid-specific structural search. */
public final class LiquidTraceEndpoint{
    public final BuildingRef building;
    public final TraceEndpointKind kind;
    public final DiagnosticResult diagnostic;
    public final LiquidTracePath path;

    public LiquidTraceEndpoint(BuildingRef building, TraceEndpointKind kind,
                               DiagnosticResult diagnostic, LiquidTracePath path){
        this.building = Objects.requireNonNull(building, "building");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.diagnostic = diagnostic;
        this.path = Objects.requireNonNull(path, "path");
    }
}
