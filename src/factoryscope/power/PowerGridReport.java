package factoryscope.power;

import java.util.*;

/** Snapshot report for all engine grids touched by an area, plus visible PowerDiode relations. */
public final class PowerGridReport{
    public final List<PowerGridResult> grids;
    public final List<PowerDiodeLink> diodeLinks;

    public PowerGridReport(Collection<PowerGridResult> grids, Collection<PowerDiodeLink> diodeLinks){
        this.grids = List.copyOf(grids);
        this.diodeLinks = List.copyOf(diodeLinks);
    }

    public static PowerGridReport empty(){
        return new PowerGridReport(List.of(), List.of());
    }
}
