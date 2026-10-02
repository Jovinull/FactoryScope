package factoryscope.area;

import factoryscope.analysis.*;
import factoryscope.model.*;

/**
 * One building that was probed and analysed, reduced to what an area report needs.
 *
 * <p>The full {@link DiagnosticResult} is kept, so nothing is lost: the aggregation only ever reads it,
 * never recomputes it.
 */
public final class AreaEntry{
    public final BuildingRef ref;
    public final SupportLevel support;
    /** Snapshot captured for this report, when probing succeeded. */
    public final FactorySnapshot snapshot;
    public final DiagnosticResult result;
    public final AreaStatus status;

    public AreaEntry(BuildingRef ref, SupportLevel support, DiagnosticResult result){
        this(ref, support, null, result);
    }

    public AreaEntry(BuildingRef ref, FactorySnapshot snapshot, DiagnosticResult result){
        this(ref, snapshot.support, snapshot, result);
    }

    private AreaEntry(BuildingRef ref, SupportLevel support, FactorySnapshot snapshot, DiagnosticResult result){
        this.ref = ref;
        this.support = support;
        this.snapshot = snapshot;
        this.result = result;
        this.status = AreaStatus.of(result.reason());
    }

    @Override
    public String toString(){
        return ref + " " + status;
    }
}
