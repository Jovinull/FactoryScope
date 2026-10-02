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
    public final float gridGenerationPerSecond;
    public final float gridDemandPerSecond;
    /** Engine-windowed grid balance per second; includes PowerDiode transfer adjustments. */
    public final float graphBalancePerSecond;
    /** True when enough samples exist for {@link #graphBalancePerSecond} to be meaningful. */
    public final boolean balanceReliable;
    public final float batteryStored;
    public final float batteryCapacity;
    /** False for cheat-powered grids or when an engine metric could not be read safely. */
    public final boolean hasGridMetrics;

    public PowerState(float satisfaction, float usagePerSecond, boolean buffered,
                      float gridGenerationPerSecond, float gridDemandPerSecond,
                      float graphBalancePerSecond, boolean balanceReliable,
                      float batteryStored, float batteryCapacity, boolean hasGridMetrics){
        this.satisfaction = satisfaction;
        this.usagePerSecond = usagePerSecond;
        this.buffered = buffered;
        this.gridGenerationPerSecond = gridGenerationPerSecond;
        this.gridDemandPerSecond = gridDemandPerSecond;
        this.graphBalancePerSecond = graphBalancePerSecond;
        this.balanceReliable = balanceReliable;
        this.batteryStored = batteryStored;
        this.batteryCapacity = batteryCapacity;
        this.hasGridMetrics = hasGridMetrics;
    }

    public boolean hasBatteries(){
        return batteryCapacity > 0f;
    }
}
