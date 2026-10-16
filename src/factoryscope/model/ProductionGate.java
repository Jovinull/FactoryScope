package factoryscope.model;

/**
 * A production-stopping condition that the probe verified against the rules of the building's own
 * family, as opposed to one inferred from efficiency alone.
 *
 * <p>Every value corresponds to a check the engine itself makes before the building produces, so a
 * gate is reported as a fact. Whatever the gate does not cover falls back to the generic diagnosis.
 */
public enum ProductionGate{
    none,
    /** The building's output storage cannot take what it would produce next. */
    outputFull,
    /** There is nothing under or in front of the building that it can extract. */
    noResource,
    /** The resource it stands on or faces is too hard for this block, or excluded from it. */
    resourceTooHard,
    /** A unit factory has no unit selected. */
    noPlan,
    /** The team cannot field another unit of this type. */
    unitCapReached,
    /** A reconstructor holds no unit it can upgrade. */
    waitingForUnit,
    /** A turret holds no usable ammunition. */
    noAmmo,
    /** A turret is loaded and reloaded but has nothing to shoot at; this is not a fault. */
    idle
}
