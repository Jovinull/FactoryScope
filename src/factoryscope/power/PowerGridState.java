package factoryscope.power;

/** Factual summary state derived from one immutable PowerGraph snapshot. */
public enum PowerGridState{
    cheatPowered,
    unavailable,
    noCurrentDemand,
    underpowered,
    noGeneration,
    generationBelowDemand,
    surplus,
    balanced
}
