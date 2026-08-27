package factoryscope.analysis;

import factoryscope.model.*;

import java.util.*;

/**
 * Turns a {@link FactorySnapshot} into a diagnosis. Pure logic - no Mindustry, no Arc, no UI.
 *
 * <h2>Why the checks are ordered the way they are</h2>
 * The order is not a preference, it follows how Mindustry gates production in
 * {@code Building.updateConsumption()} (v159.7):
 * <ol>
 *   <li>{@code !enabled} zeroes efficiency <em>and</em> potentialEfficiency before any consumer is
 *       evaluated, so a disabled building tells you nothing about its inputs.</li>
 *   <li>{@code update = shouldConsume() && productionValid()}; when that is false efficiency is forced
 *       to zero even though every input may be present. For a {@code GenericCrafter},
 *       {@code shouldConsume()} is false exactly when the output buffer cannot take another cycle.</li>
 *   <li>Only then is {@code efficiency} the minimum of every non-optional {@code Consume.efficiency()},
 *       which is why the limiting input is the one with the lowest satisfaction.</li>
 *   <li>Finally {@code updateEfficiencyMultiplier()} scales the result by {@code efficiencyScale()},
 *       a block-specific multiplier (heat, terrain attributes).</li>
 * </ol>
 */
public final class FactoryAnalyzer{
    private static final float EPSILON = ResourceState.EPSILON;
    private static final float FULL = 1f - EPSILON;

    private FactoryAnalyzer(){
    }

    public static DiagnosticResult analyze(FactorySnapshot snapshot){
        List<Finding> findings = new ArrayList<>(4);

        addGateFinding(snapshot, findings);
        addInputFindings(snapshot, findings);
        addBlockConditionFinding(snapshot, findings);
        addSupportFinding(snapshot, findings);
        addFallbackFinding(snapshot, findings);

        if(findings.isEmpty()) findings.add(Finding.of(DiagnosticReason.active, Severity.normal));
        return new DiagnosticResult(findings);
    }

    /** Conditions that stop production before consumers are ever considered. */
    private static void addGateFinding(FactorySnapshot s, List<Finding> findings){
        if(!s.enabled){
            findings.add(Finding.of(DiagnosticReason.disabled, Severity.stopped));
            return;
        }

        if(!s.shouldConsume){
            if(s.support == SupportLevel.full && s.outputBufferFull){
                findings.add(Finding.of(DiagnosticReason.outputBlocked, Severity.stopped, blockedOutputNames(s)));
            }else{
                //the building refuses to consume, but only a GenericCrafter has verified semantics for why
                findings.add(Finding.uncertain(DiagnosticReason.haltedUnknownCause, Severity.stopped, List.of()));
            }
            return;
        }

        if(!s.productionValid){
            findings.add(Finding.uncertain(DiagnosticReason.haltedUnknownCause, Severity.stopped, List.of()));
        }
    }

    /**
     * Attributes a shortfall to the consumers that are actually limiting.
     *
     * <p>Skipped entirely under infinite resources: the game short-circuits every consumer in that
     * mode, so reporting a shortage would contradict the efficiency the player can see.
     */
    private static void addInputFindings(FactorySnapshot s, List<Finding> findings){
        if(s.infiniteResources) return;

        List<ResourceState> mandatory = s.mandatoryInputs();
        if(mandatory.isEmpty()) return;

        float min = 1f;
        for(ResourceState input : mandatory){
            min = Math.min(min, input.satisfaction);
        }
        if(min >= FULL) return;

        Severity severity = min <= EPSILON ? Severity.stopped : Severity.reduced;

        //efficiency is a minimum, so everything sitting at the minimum is equally responsible
        Map<DiagnosticReason, List<String>> grouped = new LinkedHashMap<>();
        Set<DiagnosticReason> uncertain = new HashSet<>();
        for(ResourceState input : mandatory){
            if(input.satisfaction > min + EPSILON) continue;
            DiagnosticReason reason = reasonFor(input.kind);
            grouped.computeIfAbsent(reason, k -> new ArrayList<>()).add(input.name);
            //an unnamed consumer can still be blamed with confidence; only a distorted reading cannot
            if(input.provisional) uncertain.add(reason);
        }

        grouped.forEach((reason, names) ->
            findings.add(new Finding(reason, severity, names, !uncertain.contains(reason))));
    }

    /** A multiplier such as heat or terrain attributes holding the building below full speed. */
    private static void addBlockConditionFinding(FactorySnapshot s, List<Finding> findings){
        float scale = s.blockEfficiencyScale;
        if(Float.isNaN(scale) || scale >= FULL) return;

        findings.add(Finding.of(DiagnosticReason.blockConditionLimited,
            scale <= EPSILON ? Severity.stopped : Severity.reduced));
    }

    private static void addSupportFinding(FactorySnapshot s, List<Finding> findings){
        if(s.support == SupportLevel.minimal){
            findings.add(Finding.of(DiagnosticReason.limitedSupport, Severity.normal));
        }
    }

    /**
     * Catches the case where the game reports a shortfall FactoryScope could not attribute to anything
     * it understands. Saying so is the whole point - guessing here would be worse than admitting it.
     */
    private static void addFallbackFinding(FactorySnapshot s, List<Finding> findings){
        if(hasSeverity(findings, Severity.stopped) || hasSeverity(findings, Severity.reduced)) return;
        if(s.efficiency >= FULL) return;

        findings.add(Finding.uncertain(DiagnosticReason.haltedUnknownCause,
            s.efficiency <= EPSILON ? Severity.stopped : Severity.reduced, List.of()));
    }

    private static boolean hasSeverity(List<Finding> findings, Severity severity){
        for(Finding finding : findings){
            if(finding.severity == severity) return true;
        }
        return false;
    }

    private static DiagnosticReason reasonFor(ResourceKind kind){
        return switch(kind){
            case item -> DiagnosticReason.missingItemInput;
            case liquid -> DiagnosticReason.missingLiquidInput;
            case power -> DiagnosticReason.insufficientPower;
            case other -> DiagnosticReason.otherConsumerLimited;
        };
    }

    private static List<String> blockedOutputNames(FactorySnapshot s){
        List<String> names = new ArrayList<>();
        for(OutputState output : s.outputs){
            if(output.bufferFull) names.add(output.name);
        }
        return names;
    }
}
