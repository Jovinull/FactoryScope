package factoryscope.analysis;

import factoryscope.model.*;
import org.junit.jupiter.api.*;

import java.util.*;

import static factoryscope.analysis.Factories.*;
import static org.junit.jupiter.api.Assertions.*;

class FactoryAnalyzerTest{

    @Test
    void healthyFactoryReportsNoProblem(){
        DiagnosticResult result = FactoryAnalyzer.analyze(healthySmelter().build());

        assertEquals(DiagnosticReason.active, result.reason());
        assertEquals(Severity.normal, result.severity());
        assertTrue(result.secondary().isEmpty(), "a healthy factory should produce a single finding");
    }

    @Test
    void disabledFactoryOutranksItsOtherProblems(){
        //Mindustry zeroes efficiency before looking at consumers, so the shortage is real but secondary
        DiagnosticResult result = FactoryAnalyzer.analyze(healthySmelter()
            .enabled(false)
            .efficiency(0f, 0f, 0f)
            .blockEfficiencyScale(Float.NaN)
            .input(item("Sand", 0f))
            .build());

        assertEquals(DiagnosticReason.disabled, result.reason());
        assertEquals(Severity.stopped, result.severity());
        assertTrue(reasons(result).contains(DiagnosticReason.missingItemInput),
            "the item shortage should be retained as a secondary finding");
    }

    @Test
    void missingItemIsNamedInTheFinding(){
        DiagnosticResult result = FactoryAnalyzer.analyze(smelterMissing(item("Sand", 0f)));

        assertEquals(DiagnosticReason.missingItemInput, result.reason());
        assertEquals(Severity.stopped, result.severity());
        assertEquals(List.of("Sand"), result.primary.resources);
        assertTrue(result.primary.certain);
    }

    @Test
    void severalMissingItemsAreGroupedIntoOneFinding(){
        FactorySnapshot snapshot = FactorySnapshot.builder(SILICON_SMELTER)
            .support(SupportLevel.full).hasConsumers(true)
            .efficiency(0f, 0f, 0f).blockEfficiencyScale(Float.NaN)
            .input(item("Sand", 0f))
            .input(item("Coal", 0f))
            .build();

        DiagnosticResult result = FactoryAnalyzer.analyze(snapshot);

        assertEquals(DiagnosticReason.missingItemInput, result.reason());
        assertEquals(List.of("Sand", "Coal"), result.primary.resources);
    }

    @Test
    void missingLiquidIsReportedSeparatelyFromItems(){
        DiagnosticResult result = FactoryAnalyzer.analyze(smelterMissing(liquid("Water", 0f)));

        assertEquals(DiagnosticReason.missingLiquidInput, result.reason());
        assertEquals(List.of("Water"), result.primary.resources);
    }

    @Test
    void partiallySuppliedLiquidReducesRatherThanStops(){
        FactorySnapshot snapshot = FactorySnapshot.builder("Phase Weaver")
            .support(SupportLevel.full).hasConsumers(true)
            .efficiency(0.4f, 0.4f, 0.4f).blockEfficiencyScale(1f)
            .input(item("Thorium", 1f))
            .input(liquid("Cryofluid", 0.4f))
            .build();

        DiagnosticResult result = FactoryAnalyzer.analyze(snapshot);

        assertEquals(DiagnosticReason.missingLiquidInput, result.reason());
        assertEquals(Severity.reduced, result.severity());
    }

    @Test
    void insufficientPowerIsReportedWhenPowerIsTheLowestInput(){
        FactorySnapshot snapshot = healthySmelter()
            .efficiency(0.63f, 0.63f, 0.63f)
            .input(power(0.63f))
            .build();

        DiagnosticResult result = FactoryAnalyzer.analyze(snapshot);

        assertEquals(DiagnosticReason.insufficientPower, result.reason());
        assertEquals(Severity.reduced, result.severity());
    }

    @Test
    void theLowestInputWinsWhenSeveralAreShort(){
        //efficiency is a minimum over consumers, so a missing item beats a half-supplied grid
        FactorySnapshot snapshot = FactorySnapshot.builder(SILICON_SMELTER)
            .support(SupportLevel.full).hasConsumers(true)
            .efficiency(0f, 0f, 0f).blockEfficiencyScale(Float.NaN)
            .input(item("Sand", 0f))
            .input(power(0.5f))
            .build();

        DiagnosticResult result = FactoryAnalyzer.analyze(snapshot);

        assertEquals(DiagnosticReason.missingItemInput, result.reason());
        assertFalse(reasons(result).contains(DiagnosticReason.insufficientPower),
            "a consumer above the minimum is not limiting and must not be blamed");
    }

    @Test
    void simultaneousShortagesOfDifferentKindsAreAllRetained(){
        FactorySnapshot snapshot = FactorySnapshot.builder("Phase Weaver")
            .support(SupportLevel.full).hasConsumers(true)
            .efficiency(0f, 0f, 0f).blockEfficiencyScale(Float.NaN)
            .input(item("Thorium", 0f))
            .input(liquid("Cryofluid", 0f))
            .input(power(0f))
            .build();

        DiagnosticResult result = FactoryAnalyzer.analyze(snapshot);

        assertEquals(DiagnosticReason.missingItemInput, result.reason());
        assertEquals(List.of(DiagnosticReason.missingItemInput, DiagnosticReason.missingLiquidInput,
            DiagnosticReason.insufficientPower), reasons(result));
    }

    @Test
    void blockedOutputIsDiagnosedFromShouldConsume(){
        FactorySnapshot snapshot = healthySmelter()
            .shouldConsume(false)
            .outputBufferFull(true)
            .efficiency(0f, 0f, 0f)
            .blockEfficiencyScale(Float.NaN)
            .output(silicon(true))
            .build();

        DiagnosticResult result = FactoryAnalyzer.analyze(snapshot);

        assertEquals(DiagnosticReason.outputBlocked, result.reason());
        assertEquals(Severity.stopped, result.severity());
        assertTrue(result.primary.resources.contains("Silicon"));
    }

    @Test
    void refusalToConsumeIsNotCalledOutputBlockedOnUnmodelledBlocks(){
        //shouldConsume() only has verified output semantics for GenericCrafter
        FactorySnapshot snapshot = FactorySnapshot.builder("Some Modded Reactor")
            .support(SupportLevel.basic).hasConsumers(true)
            .shouldConsume(false)
            .efficiency(0f, 0f, 0f).blockEfficiencyScale(Float.NaN)
            .input(item("Thorium", 1f))
            .build();

        DiagnosticResult result = FactoryAnalyzer.analyze(snapshot);

        assertEquals(DiagnosticReason.haltedUnknownCause, result.reason());
        assertFalse(result.primary.certain, "an unverified cause must not be stated as fact");
    }

    @Test
    void optionalInputsNeverStopAFactory(){
        FactorySnapshot snapshot = healthySmelter()
            .input(booster("Pyratite", 0f))
            .build();

        DiagnosticResult result = FactoryAnalyzer.analyze(snapshot);

        assertEquals(DiagnosticReason.active, result.reason());
    }

    @Test
    void unknownConsumerIsBlamedButOnlyAsAPossibility(){
        FactorySnapshot snapshot = FactorySnapshot.builder("Modded Crafter")
            .support(SupportLevel.full).hasConsumers(true)
            .efficiency(0f, 0f, 0f).blockEfficiencyScale(Float.NaN)
            .input(item("Copper", 1f))
            .input(unknownConsumer("ConsumeSomethingExotic", 0f, true))
            .build();

        DiagnosticResult result = FactoryAnalyzer.analyze(snapshot);

        assertEquals(DiagnosticReason.otherConsumerLimited, result.reason());
        assertFalse(result.primary.certain);
        assertEquals(List.of("ConsumeSomethingExotic"), result.primary.resources);
    }

    @Test
    void unknownConsumerReadWhileRunningIsStatedWithConfidence(){
        FactorySnapshot snapshot = FactorySnapshot.builder("Modded Crafter")
            .support(SupportLevel.full).hasConsumers(true)
            .efficiency(0.25f, 0.25f, 0.25f).blockEfficiencyScale(1f)
            .input(item("Copper", 1f))
            .input(unknownConsumer("ConsumeSomethingExotic", 0.25f, false))
            .build();

        DiagnosticResult result = FactoryAnalyzer.analyze(snapshot);

        assertEquals(DiagnosticReason.otherConsumerLimited, result.reason());
        assertEquals(Severity.reduced, result.severity());
        assertTrue(result.primary.certain);
    }

    @Test
    void unsupportedBuildingFallsBackToLimitedDiagnostics(){
        FactorySnapshot snapshot = FactorySnapshot.builder("Copper Wall")
            .support(SupportLevel.minimal)
            .hasConsumers(false)
            .efficiency(1f, 1f, 1f)
            .build();

        DiagnosticResult result = FactoryAnalyzer.analyze(snapshot);

        assertEquals(DiagnosticReason.limitedSupport, result.reason());
        assertEquals(Severity.normal, result.severity());
    }

    @Test
    void blockConditionIsReportedWhenNoInputExplainsTheShortfall(){
        //a heat-starved crafter: every consumer is satisfied but efficiencyScale() halves the result
        FactorySnapshot snapshot = healthySmelter()
            .efficiency(0.5f, 1f, 0.5f)
            .blockEfficiencyScale(0.5f)
            .build();

        DiagnosticResult result = FactoryAnalyzer.analyze(snapshot);

        assertEquals(DiagnosticReason.blockConditionLimited, result.reason());
        assertEquals(Severity.reduced, result.severity());
    }

    @Test
    void unexplainedShortfallIsAdmittedRatherThanGuessed(){
        FactorySnapshot snapshot = healthySmelter()
            .efficiency(0.3f, 0.3f, 0.3f)
            .blockEfficiencyScale(1f)
            .build();

        DiagnosticResult result = FactoryAnalyzer.analyze(snapshot);

        assertEquals(DiagnosticReason.haltedUnknownCause, result.reason());
        assertEquals(Severity.reduced, result.severity());
        assertFalse(result.primary.certain);
    }

    @Test
    void bypassedConsumersSuppressInputBlame(){
        //with the team cheat rule the game bypasses consumers, so an empty input is not a shortage
        FactorySnapshot snapshot = healthySmelter()
            .consumersBypassed(true)
            .input(item("Sand", 0f))
            .build();

        DiagnosticResult result = FactoryAnalyzer.analyze(snapshot);

        assertEquals(DiagnosticReason.active, result.reason());
    }

    @Test
    void bypassedConsumersStillReportBlockedOutput(){
        FactorySnapshot snapshot = healthySmelter()
            .consumersBypassed(true)
            .shouldConsume(false)
            .outputBufferFull(true)
            .efficiency(0f, 0f, 0f)
            .blockEfficiencyScale(Float.NaN)
            .output(silicon(true))
            .build();

        assertEquals(DiagnosticReason.outputBlocked, FactoryAnalyzer.analyze(snapshot).reason());
    }

    private static FactorySnapshot smelterMissing(ResourceState missing){
        return FactorySnapshot.builder(SILICON_SMELTER)
            .support(SupportLevel.full).hasConsumers(true)
            .efficiency(0f, 0f, 0f).blockEfficiencyScale(Float.NaN)
            .input(item("Coal", 1f))
            .input(missing)
            .build();
    }

    private static List<DiagnosticReason> reasons(DiagnosticResult result){
        List<DiagnosticReason> reasons = new ArrayList<>();
        result.findings.forEach(finding -> reasons.add(finding.reason));
        return reasons;
    }
}
