package factoryscope.power;

/** Non-causal observations about one engine-maintained power grid. */
public enum PowerFinding{
    NO_CURRENT_DEMAND,
    NO_GENERATION,
    GRID_UNDERPOWERED,
    GENERATION_BELOW_DEMAND,
    BATTERY_RESERVES_PRESENT,
    GENERATION_SURPLUS,
    GENERATOR_DIAGNOSTIC_PROBLEMS,
    BALANCE_COLLECTING,
    CHEAT_POWER_RULE,
    METRICS_UNAVAILABLE,
    VISIBILITY_INCOMPLETE
}
