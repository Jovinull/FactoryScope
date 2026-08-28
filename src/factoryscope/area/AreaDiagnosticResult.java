package factoryscope.area;

import java.util.*;

/** The complete outcome of analysing one area: what was in it, how it is doing, and what is wrong. */
public final class AreaDiagnosticResult{
    public final AreaSelection selection;
    public final AreaSummary summary;
    /** One per analysed building, in collection order. */
    public final List<AreaEntry> entries;
    /** Issue groups, most important first. See {@code AreaAnalyzer} for the ordering. */
    public final List<AreaIssueGroup> issues;

    AreaDiagnosticResult(AreaSelection selection, AreaSummary summary,
                         List<AreaEntry> entries, List<AreaIssueGroup> issues){
        this.selection = selection;
        this.summary = summary;
        this.entries = List.copyOf(entries);
        this.issues = List.copyOf(issues);
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
