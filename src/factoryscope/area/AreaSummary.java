package factoryscope.area;

import java.util.*;

/**
 * The counts a player reads at the top of an area report.
 *
 * <p>Every number here counts <em>buildings</em>, never findings, and the four populations are kept
 * apart deliberately: a single figure that quietly means all of them at once is how a report starts
 * lying.
 */
public final class AreaSummary{
    /** Buildings whose footprint intersects the selection and that the player is allowed to see. */
    public final int selected;
    /** Of those, the ones FactoryScope successfully probed and analysed. */
    public final int analyzed;
    /** Of the analysed ones, the ones with a full production model - conventional crafting blocks. */
    public final int production;
    /** Analysed buildings with a problem, i.e. whose primary status is a {@link AreaStatus.Kind#problem}. */
    public final int problems;
    /** Analysed buildings whose primary status is informational: disabled, unsupported, and so on. */
    public final int informational;
    /** Analysed buildings running with nothing FactoryScope can check holding them back. */
    public final int operating;
    /** Counts per status, in enum declaration order; statuses with no buildings are absent. */
    public final Map<AreaStatus, Integer> byStatus;

    AreaSummary(int selected, int analyzed, int production, int problems, int informational,
                int operating, Map<AreaStatus, Integer> byStatus){
        this.selected = selected;
        this.analyzed = analyzed;
        this.production = production;
        this.problems = problems;
        this.informational = informational;
        this.operating = operating;
        this.byStatus = Collections.unmodifiableMap(new LinkedHashMap<>(byStatus));
    }

    /** Buildings that were selected but could not be analysed at all; normally zero. */
    public int skipped(){
        return selected - analyzed;
    }

    @Override
    public String toString(){
        return selected + " selected, " + analyzed + " analyzed, " + problems + " with problems";
    }
}
