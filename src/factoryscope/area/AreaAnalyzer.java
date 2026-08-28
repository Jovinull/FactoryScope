package factoryscope.area;

import factoryscope.analysis.*;
import factoryscope.model.*;

import java.util.*;

/**
 * Aggregates per-building diagnoses into one picture of an area. Pure logic - no Mindustry, no Arc,
 * no UI, no live building references.
 *
 * <h2>What this class deliberately does not do</h2>
 * It never decides <em>why</em> a building is in the state it is in. That is
 * {@link FactoryAnalyzer}'s job and duplicating any part of it here would let the two disagree. This
 * class only counts, groups and orders results that already exist.
 *
 * <p>It also does not infer causality between buildings. "Sand shortages affect eight buildings" is a
 * count of observations; it is not a claim that sand production is the root cause of anything. Root
 * cause needs a production-network model, which FactoryScope does not have.
 *
 * <h2>Counting rules</h2>
 * <ul>
 *   <li>A building contributes exactly one status, taken from its <em>primary</em> finding.</li>
 *   <li>A building may contribute to several issue groups, because a factory can be short of an item
 *       and short of power at the same time - but never twice to the same group.</li>
 *   <li>Every player-facing count is a count of distinct buildings, never of findings.</li>
 * </ul>
 */
public final class AreaAnalyzer{
    private AreaAnalyzer(){
    }

    /**
     * @param selected how many buildings the area collection found, which may exceed the number of
     * entries if a building could not be probed at all
     */
    public static AreaDiagnosticResult analyze(AreaSelection selection, int selected, List<AreaEntry> entries){
        Objects.requireNonNull(selection, "selection");

        return new AreaDiagnosticResult(selection, summarize(selected, entries), entries, group(entries));
    }

    private static AreaSummary summarize(int selected, List<AreaEntry> entries){
        EnumMap<AreaStatus, Integer> counts = new EnumMap<>(AreaStatus.class);
        int production = 0, problems = 0, informational = 0, operating = 0;

        for(AreaEntry entry : entries){
            counts.merge(entry.status, 1, Integer::sum);
            if(entry.support == SupportLevel.full) production++;
            switch(entry.status.kind){
                case problem -> problems++;
                case informational -> informational++;
                case normal -> operating++;
            }
        }

        //EnumMap already iterates in declaration order, which is the order the summary is displayed in
        return new AreaSummary(Math.max(selected, entries.size()), entries.size(),
            production, problems, informational, operating, counts);
    }

    private static List<AreaIssueGroup> group(List<AreaEntry> entries){
        //the ordering guarantee comes from the comparator below, which is total: it falls through to
        //the issue key, and no two groups share one. The map is insertion-ordered anyway so that a
        //debugger shows the groups in the order the area was walked
        Map<AreaIssue, Builder> builders = new LinkedHashMap<>();

        for(AreaEntry entry : entries){
            for(Finding finding : entry.result.findings){
                //a normal finding is not a problem; reporting it as one is how a report starts crying wolf
                if(finding.severity == Severity.normal) continue;

                if(finding.resources.isEmpty()){
                    add(builders, new AreaIssue(finding.reason, null), finding, entry.ref);
                }else{
                    for(ResourceRef resource : finding.resources){
                        add(builders, new AreaIssue(finding.reason, resource), finding, entry.ref);
                    }
                }
            }
        }

        List<AreaIssueGroup> groups = new ArrayList<>(builders.size());
        for(Builder builder : builders.values()) groups.add(builder.build());
        groups.sort(AreaAnalyzer::compare);
        return groups;
    }

    private static void add(Map<AreaIssue, Builder> builders, AreaIssue issue, Finding finding, BuildingRef ref){
        builders.computeIfAbsent(issue, Builder::new).add(finding, ref);
    }

    /**
     * Ordering is fully determined and never depends on hash iteration: worst severity first, then the
     * issue affecting the most buildings, then the engine's own reason priority, then content identity.
     */
    private static int compare(AreaIssueGroup a, AreaIssueGroup b){
        int bySeverity = Integer.compare(a.severity.ordinal(), b.severity.ordinal());
        if(bySeverity != 0) return bySeverity;

        int byCount = Integer.compare(b.buildingCount(), a.buildingCount());
        if(byCount != 0) return byCount;

        int byReason = Integer.compare(a.issue.reason.priority(), b.issue.reason.priority());
        if(byReason != 0) return byReason;

        return a.issue.key().compareTo(b.issue.key());
    }

    private static final class Builder{
        private final AreaIssue issue;
        //a set keyed on building identity is what stops one building being counted twice in one group
        private final LinkedHashSet<BuildingRef> buildings = new LinkedHashSet<>();
        private Severity severity = Severity.normal;
        private boolean certain = true;

        private Builder(AreaIssue issue){
            this.issue = issue;
        }

        private void add(Finding finding, BuildingRef ref){
            buildings.add(ref);
            if(finding.severity.ordinal() < severity.ordinal()) severity = finding.severity;
            if(!finding.certain) certain = false;
        }

        private AreaIssueGroup build(){
            return new AreaIssueGroup(issue, severity, certain, new ArrayList<>(buildings));
        }
    }
}
