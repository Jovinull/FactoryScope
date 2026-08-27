package factoryscope.model;

/**
 * Power condition of the inspected building.
 *
 * <p>All rates are per second. Mindustry stores power per tick internally; the probe converts once so
 * that nothing downstream has to know about ticks.
 */
public final class PowerState{
    /** Fraction of requested power the grid is currently delivering, from {@code PowerModule.status}. */
    public final float satisfaction;
    /** What this building asks for, per second. */
    public final float usagePerSecond;
    /** Whether the consumer charges an internal buffer instead of drawing continuously. */
    public final boolean buffered;
    public final float graphProducedPerSecond;
    public final float graphNeededPerSecond;
    /** Averaged grid balance per second; negative means the grid is running a deficit. */
    public final float graphBalancePerSecond;
    /** True when enough samples exist for {@link #graphBalancePerSecond} to be meaningful. */
    public final boolean balanceReliable;
    public final float batteryStored;
    public final float batteryCapacity;

    public PowerState(float satisfaction, float usagePerSecond, boolean buffered,
                      float graphProducedPerSecond, float graphNeededPerSecond,
                      float graphBalancePerSecond, boolean balanceReliable,
                      float batteryStored, float batteryCapacity){
        this.satisfaction = satisfaction;
        this.usagePerSecond = usagePerSecond;
        this.buffered = buffered;
        this.graphProducedPerSecond = graphProducedPerSecond;
        this.graphNeededPerSecond = graphNeededPerSecond;
        this.graphBalancePerSecond = graphBalancePerSecond;
        this.balanceReliable = balanceReliable;
        this.batteryStored = batteryStored;
        this.batteryCapacity = batteryCapacity;
    }

    public boolean hasBatteries(){
        return batteryCapacity > 0f;
    }
}
