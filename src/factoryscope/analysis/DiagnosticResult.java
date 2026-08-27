package factoryscope.analysis;

import java.util.*;

/**
 * Outcome of analysing one snapshot.
 *
 * <p>The MVP interface only emphasises {@link #primary}, but every finding is retained so later
 * versions can show a factory with more than one bottleneck.
 */
public final class DiagnosticResult{
    public final Finding primary;
    /** Every finding, most important first, including {@link #primary}. */
    public final List<Finding> findings;

    public DiagnosticResult(List<Finding> findings){
        if(findings.isEmpty()) throw new IllegalArgumentException("a diagnostic result needs at least one finding");
        List<Finding> sorted = new ArrayList<>(findings);
        sorted.sort(Comparator.comparingInt(f -> f.reason.priority()));
        this.findings = List.copyOf(sorted);
        this.primary = this.findings.get(0);
    }

    public DiagnosticReason reason(){
        return primary.reason;
    }

    public Severity severity(){
        return primary.severity;
    }

    /** Findings other than the primary one, in priority order. */
    public List<Finding> secondary(){
        return findings.subList(1, findings.size());
    }

    @Override
    public String toString(){
        return findings.toString();
    }
}
