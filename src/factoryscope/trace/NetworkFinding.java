package factoryscope.trace;

import factoryscope.area.BuildingRef;

/** A topology fact reported by a trace; it is not a FactoryAnalyzer diagnosis or a causal claim. */
public final class NetworkFinding{
    public enum Kind{
        noStructuralInputRoute,
        noStructuralOutputRoute,
        structuralDeadEnd,
        routeContinuesOutsideArea,
        unsupportedTransport,
        noReachableInAreaProducer,
        noReachableInAreaConsumer,
        reachableProducerDisabled,
        reachableProducerProblem
    }

    public enum Certainty{proven, incomplete, informational}

    public final Kind kind;
    public final Certainty certainty;
    public final BuildingRef building;

    public NetworkFinding(Kind kind, Certainty certainty, BuildingRef building){
        this.kind = kind;
        this.certainty = certainty;
        this.building = building;
    }
}
