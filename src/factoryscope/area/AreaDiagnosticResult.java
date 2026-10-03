package factoryscope.area;

import java.util.*;
import factoryscope.network.*;
import factoryscope.power.*;
import factoryscope.liquid.LiquidNetwork;

/** The complete outcome of analysing one area: what was in it, how it is doing, and what is wrong. */
public final class AreaDiagnosticResult{
    public final AreaSelection selection;
    public final AreaSummary summary;
    /** One per analysed building, in collection order. */
    public final List<AreaEntry> entries;
    /** Issue groups, most important first. See {@code AreaAnalyzer} for the ordering. */
    public final List<AreaIssueGroup> issues;
    /** Static item topology for this same snapshot area, or null for legacy pure aggregation tests. */
    public final ItemNetwork network;
    /** Snapshot of the engine-maintained power grids intersecting this area. */
    public final PowerGridReport power;
    /** Area-scoped static liquid topology for this same diagnostic snapshot. */
    public final LiquidNetwork liquids;
    /** Selected buildings for which the per-building probe or analysis did not produce an entry. */
    public final List<BuildingRef> skippedBuildings;

    AreaDiagnosticResult(AreaSelection selection, AreaSummary summary,
                         List<AreaEntry> entries, List<AreaIssueGroup> issues){
        this(selection, summary, entries, issues, null, PowerGridReport.empty(), null, List.of());
    }

    private AreaDiagnosticResult(AreaSelection selection, AreaSummary summary,
                                 List<AreaEntry> entries, List<AreaIssueGroup> issues, ItemNetwork network,
                                 PowerGridReport power, LiquidNetwork liquids,
                                 Collection<BuildingRef> skippedBuildings){
        this.selection = selection;
        this.summary = summary;
        this.entries = List.copyOf(entries);
        this.issues = List.copyOf(issues);
        this.network = network;
        this.power = power == null ? PowerGridReport.empty() : power;
        this.liquids = liquids;
        TreeSet<BuildingRef> ordered = new TreeSet<>(Comparator
            .comparingInt((BuildingRef ref) -> ref.tileX).thenComparingInt(ref -> ref.tileY)
            .thenComparing(ref -> ref.blockId).thenComparingInt(ref -> ref.teamId));
        ordered.addAll(skippedBuildings);
        this.skippedBuildings = List.copyOf(ordered);
    }

    /** Adds the adapter result without making the pure area aggregation depend on Mindustry. */
    public AreaDiagnosticResult withNetwork(ItemNetwork network){
        return new AreaDiagnosticResult(selection, summary, entries, issues, network, power, liquids, skippedBuildings);
    }

    public AreaDiagnosticResult withPower(PowerGridReport power){
        return new AreaDiagnosticResult(selection, summary, entries, issues, network, power, liquids, skippedBuildings);
    }

    public AreaDiagnosticResult withLiquids(LiquidNetwork liquids){
        return new AreaDiagnosticResult(selection, summary, entries, issues, network, power, liquids, skippedBuildings);
    }

    public AreaDiagnosticResult withSkippedBuildings(Collection<BuildingRef> skippedBuildings){
        return new AreaDiagnosticResult(selection, summary, entries, issues, network, power, liquids, skippedBuildings);
    }

    public boolean empty(){
        return entries.isEmpty();
    }

    /** True when nothing analysed in this area is in a problem state. */
    public boolean healthy(){
        return !entries.isEmpty() && summary.problems == 0;
    }

    @Override
    public String toString(){
        return selection + " " + summary + " " + issues;
    }
}
