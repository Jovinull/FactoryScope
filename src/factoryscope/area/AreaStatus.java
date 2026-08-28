package factoryscope.area;

import factoryscope.analysis.*;

/**
 * The player-facing bucket a building falls into in an area summary.
 *
 * <p>This is deliberately coarser than {@link DiagnosticReason}: a summary line is a headline, and the
 * full diagnosis is one click away in the single-building panel. The mapping is total, so a reason
 * added later has to be classified here rather than quietly disappearing from the counts.
 */
public enum AreaStatus{
    operating(Kind.normal),
    itemShortage(Kind.problem),
    liquidShortage(Kind.problem),
    powerLimited(Kind.problem),
    outputBlocked(Kind.problem),
    otherLimitation(Kind.problem),
    causeUnclear(Kind.problem),
    disabled(Kind.informational),
    inoperable(Kind.informational),
    notConsuming(Kind.informational),
    limitedDiagnostics(Kind.informational);

    /**
     * How a status should be presented. A manually switched-off building is not a fault, and neither is
     * a block FactoryScope has no production model for, so neither is counted as a problem.
     */
    public enum Kind{
        normal,
        problem,
        informational
    }

    public final Kind kind;

    AreaStatus(Kind kind){
        this.kind = kind;
    }

    public boolean problem(){
        return kind == Kind.problem;
    }

    public static AreaStatus of(DiagnosticReason reason){
        return switch(reason){
            case active -> operating;
            case missingItemInput -> itemShortage;
            case missingLiquidInput -> liquidShortage;
            case insufficientPower -> powerLimited;
            case outputBlocked -> outputBlocked;
            case otherConsumerLimited, blockConditionLimited -> otherLimitation;
            case haltedUnknownCause -> causeUnclear;
            case disabled -> disabled;
            case inoperableHere -> inoperable;
            case notConsuming -> notConsuming;
            case limitedSupport -> limitedDiagnostics;
        };
    }

    /** Bundle-key fragment, e.g. {@code item-shortage}. */
    public String slug(){
        return name().replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase(java.util.Locale.ROOT);
    }
}
