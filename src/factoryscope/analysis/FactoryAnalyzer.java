package factoryscope.analysis;

import factoryscope.model.*;

import java.util.*;

/**
 * Turns a {@link FactorySnapshot} into a diagnosis. Pure logic - no Mindustry, no Arc, no UI.
 *
 * <h2>Why the checks are ordered the way they are</h2>
 * The order is not a preference, it follows how Mindustry gates production in
 * {@code Building.updateConsumption()} (v160.5):
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
 *
 * <p>A building covered by a production model can also carry a {@link ProductionGate}: a stopping
 * condition the probe checked against that family's own rules (no ore under a drill, a unit factory with
 * nothing selected, a turret without ammunition). It is evaluated right after the enabled check, because
 * each one is a condition the engine tests before the building produces anything.
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
            //the game disables a building it will not run at all; that is not the player switching it off
            findings.add(Finding.of(s.updateAllowed
                ? DiagnosticReason.disabled
                : DiagnosticReason.inoperableHere, Severity.stopped));
            return;
        }

        if(s.gate != ProductionGate.none){
            findings.add(gateFinding(s));
            return;
        }

        if(!s.shouldConsume){
            if(s.support == SupportLevel.full && s.outputBufferFull){
                findings.add(Finding.of(DiagnosticReason.outputBlocked, Severity.stopped, blockedOutputs(s)));
            }else{
                //without a verified gate there is no telling why a building declines to consume
                findings.add(Finding.of(DiagnosticReason.notConsuming, Severity.stopped));
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
     * <p>Skipped entirely when the team rules bypass consumers: the game short-circuits every consumer
     * in that case, so reporting a shortage would contradict the efficiency the player can see.
     */
    private static void addInputFindings(FactorySnapshot s, List<Finding> findings){
        if(s.consumersBypassed) return;

        List<ResourceState> mandatory = new ArrayList<>(s.mandatoryInputs());
        //a turret's ammunition is declared as an input; when the ammunition gate already explains the
        //state, the same shortage is not repeated as an input finding
        if(s.gate == ProductionGate.noAmmo) mandatory.removeIf(input -> isAmmunition(s, input));
        if(mandatory.isEmpty()) return;

        float min = 1f;
        for(ResourceState input : mandatory){
            min = Math.min(min, input.satisfaction);
        }
        if(min >= FULL) return;

        Severity severity = min <= EPSILON ? Severity.stopped : Severity.reduced;

        //efficiency is a minimum, so everything sitting at the minimum is equally responsible
        Map<DiagnosticReason, List<ResourceRef>> grouped = new LinkedHashMap<>();
        Set<DiagnosticReason> uncertain = new HashSet<>();
        for(ResourceState input : mandatory){
            if(input.satisfaction > min + EPSILON) continue;
            DiagnosticReason reason = reasonFor(input.kind);
            grouped.computeIfAbsent(reason, k -> new ArrayList<>()).add(input.ref());
            //an unnamed consumer can still be blamed with confidence; only a distorted reading cannot
            if(input.provisional) uncertain.add(reason);
        }

        grouped.forEach((reason, refs) ->
            findings.add(new Finding(reason, severity, refs, !uncertain.contains(reason))));
    }

    /** A multiplier such as heat or terrain attributes holding the building below full speed. */
    private static void addBlockConditionFinding(FactorySnapshot s, List<Finding> findings){
        if(!s.efficiencyTracked) return;
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
        if(!s.efficiencyTracked) return;
        //a verified gate already explains the state, including the idle turret whose efficiency is zero
        if(s.gate != ProductionGate.none) return;
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
            case unit, block -> DiagnosticReason.missingPayloadInput;
            case power -> DiagnosticReason.insufficientPower;
            case other -> DiagnosticReason.otherConsumerLimited;
        };
    }

    private static boolean isAmmunition(FactorySnapshot s, ResourceState input){
        if(input.kind != ResourceKind.item && input.kind != ResourceKind.liquid) return false;
        return input.contentId == null || s.gateResources.contains(input.ref());
    }

    private static Finding gateFinding(FactorySnapshot s){
        return switch(s.gate){
            case outputFull -> Finding.of(DiagnosticReason.outputBlocked, Severity.stopped,
                s.gateResources.isEmpty() ? blockedOutputs(s) : s.gateResources);
            case noResource -> new Finding(DiagnosticReason.noResource, Severity.stopped, s.gateResources, true,
                variant(DiagnosticReason.noResource, s.family));
            case resourceTooHard -> new Finding(DiagnosticReason.resourceTooHard, Severity.stopped, s.gateResources, true,
                variant(DiagnosticReason.resourceTooHard, s.family));
            case noPlan -> Finding.of(DiagnosticReason.noPlanSelected, Severity.stopped);
            case unitCapReached -> Finding.of(DiagnosticReason.unitCapReached, Severity.stopped, s.gateResources);
            case waitingForUnit -> Finding.of(DiagnosticReason.waitingForUnit, Severity.stopped, s.gateResources);
            case noAmmo -> Finding.of(DiagnosticReason.noAmmo, Severity.stopped, s.gateResources);
            case idle -> Finding.of(DiagnosticReason.turretIdle, Severity.normal);
            case none -> throw new IllegalStateException("no gate");
        };
    }

    /** The family wording for a reason, or null when the family has none and the shared sentence applies. */
    private static String variant(DiagnosticReason reason, ProductionFamily family){
        String variant = family == ProductionFamily.solidPump ? "pump" : DiagnosticReason.slug(family.name());
        return reason.variants().contains(variant) ? variant : null;
    }

    private static List<ResourceRef> blockedOutputs(FactorySnapshot s){
        List<ResourceRef> refs = new ArrayList<>();
        for(OutputState output : s.outputs){
            if(output.bufferFull) refs.add(output.ref());
        }
        return refs;
    }
}
