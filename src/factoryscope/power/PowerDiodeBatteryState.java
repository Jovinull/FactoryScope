package factoryscope.power;

/** What a PowerScope snapshot can safely say about enabled battery capacity at both diode endpoints. */
public enum PowerDiodeBatteryState{
    bothEndpointsHaveCapacity,
    atLeastOneEndpointLacksCapacity,
    unavailable
}
