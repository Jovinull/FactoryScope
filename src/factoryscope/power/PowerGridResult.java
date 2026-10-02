package factoryscope.power;

import java.util.*;

/** Pure interpretation of PowerGraph values; it never infers a cause or a repair. */
public final class PowerGridResult{
    public final PowerGridSnapshot snapshot;
    public final PowerGridState state;
    public final List<PowerFinding> findings;
    public final int generatorsWithProblems;
    public final int generatorsOperating;
    public final int generatorsStopped;
    public final int generatorsUnclassified;

    PowerGridResult(PowerGridSnapshot snapshot, PowerGridState state,
                    Collection<PowerFinding> findings, int generatorsWithProblems,
                    int generatorsOperating, int generatorsStopped, int generatorsUnclassified){
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
        this.state = Objects.requireNonNull(state, "state");
        this.findings = List.copyOf(findings);
        this.generatorsWithProblems = generatorsWithProblems;
        this.generatorsOperating = generatorsOperating;
        this.generatorsStopped = generatorsStopped;
        this.generatorsUnclassified = generatorsUnclassified;
    }

    public boolean has(PowerFinding finding){
        return findings.contains(finding);
    }
}
