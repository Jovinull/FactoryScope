package factoryscope.power;

import factoryscope.area.BuildingRef;

import java.util.Objects;

/** Conditional, directional relation between two separate PowerGraphs; no transfer amount is claimed. */
public final class PowerDiodeLink{
    public final BuildingRef diode;
    public final int fromGrid;
    public final int toGrid;
    /** False when either endpoint graph has no enabled battery capacity, so Mindustry cannot transfer. */
    public final boolean transferPossible;

    public PowerDiodeLink(BuildingRef diode, int fromGrid, int toGrid, boolean transferPossible){
        this.diode = Objects.requireNonNull(diode, "diode");
        if(fromGrid == toGrid) throw new IllegalArgumentException("a diode relation must connect distinct grids");
        this.fromGrid = fromGrid;
        this.toGrid = toGrid;
        this.transferPossible = transferPossible;
    }
}
