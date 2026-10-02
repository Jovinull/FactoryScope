package factoryscope.power;

import factoryscope.area.BuildingRef;

import java.util.Objects;

/** Conditional, directional relation between two separate PowerGraphs; no transfer amount is claimed. */
public final class PowerDiodeLink{
    public final BuildingRef diode;
    public final int fromGrid;
    public final int toGrid;
    /** Capacity presence is unavailable when either complete visible graph could not be inspected. */
    public final PowerDiodeBatteryState batteryState;

    public PowerDiodeLink(BuildingRef diode, int fromGrid, int toGrid, PowerDiodeBatteryState batteryState){
        this.diode = Objects.requireNonNull(diode, "diode");
        if(fromGrid == toGrid) throw new IllegalArgumentException("a diode relation must connect distinct grids");
        this.fromGrid = fromGrid;
        this.toGrid = toGrid;
        this.batteryState = Objects.requireNonNull(batteryState, "batteryState");
    }
}
