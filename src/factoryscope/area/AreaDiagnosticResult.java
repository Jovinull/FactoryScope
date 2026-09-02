package factoryscope.area;

import java.util.*;
import factoryscope.network.*;

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

    AreaDiagnosticResult(AreaSelection selection, AreaSummary summary,
                         List<AreaEntry> entries, List<AreaIssueGroup> issues){
        this(selection, summary, entries, issues, null);
    }

    private AreaDiagnosticResult(AreaSelection selection, AreaSummary summary,
                                 List<AreaEntry> entries, List<AreaIssueGroup> issues, ItemNetwork network){
        this.selection = selection;
        this.summary = summary;
        this.entries = List.copyOf(entries);
        this.issues = List.copyOf(issues);
        this.network = network;
    }

    /** Adds the adapter result without making the pure area aggregation depend on Mindustry. */
    public AreaDiagnosticResult withNetwork(ItemNetwork network){
        return new AreaDiagnosticResult(selection, summary, entries, issues, network);
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
