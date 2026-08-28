package factoryscope.area;

import factoryscope.analysis.*;
import org.junit.jupiter.api.*;

import java.util.*;

import static factoryscope.area.Areas.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The aggregation rules.
 *
 * <p>These are the tests that matter most for 0.2: the counts and the groupings are what the player
 * reads first, and a wrong number here is far more damaging than a wrong pixel anywhere else.
 */
class AreaAnalyzerTest{
    @Test
    void anEmptyAreaProducesAnEmptyResultRatherThanAFailure(){
        AreaDiagnosticResult result = AreaAnalyzer.analyze(AREA, 0, List.of());

        assertTrue(result.empty());
        assertFalse(result.healthy(), "an empty area is neither healthy nor unhealthy");
        assertEquals(0, result.summary.selected);
        assertEquals(0, result.summary.analyzed);
        assertEquals(List.of(), result.issues);
    }

    @Test
    void anAreaOfHealthyFactoriesReportsNoIssues(){
        AreaDiagnosticResult result = analyze(entry(healthy()), entry(healthy()), entry(healthy()));

        assertTrue(result.healthy());
        assertEquals(3, result.summary.operating);
        assertEquals(0, result.summary.problems);
        assertEquals(List.of(), result.issues);
        assertEquals(Map.of(AreaStatus.operating, 3), result.summary.byStatus);
    }

    @Test
    void theSameItemShortageAcrossManyBuildingsBecomesOneGroup(){
        AreaDiagnosticResult result = analyze(
            entry(itemShortage("sand", "Sand")),
            entry(itemShortage("sand", "Sand")),
            entry(itemShortage("sand", "Sand")));

        assertEquals(1, result.issues.size());
        AreaIssueGroup group = result.issues.get(0);
        assertEquals(DiagnosticReason.missingItemInput, group.issue.reason);
        assertEquals("sand", group.issue.resource.id);
        assertEquals(3, group.buildingCount());
        assertEquals(3, result.summary.byStatus.get(AreaStatus.itemShortage));
    }

    @Test
    void differentItemShortagesStayApart(){
        AreaDiagnosticResult result = analyze(
            entry(itemShortage("sand", "Sand")),
            entry(itemShortage("sand", "Sand")),
            entry(itemShortage("coal", "Coal")));

        assertEquals(2, result.issues.size());
        assertEquals("sand", result.issues.get(0).issue.resource.id);
        assertEquals(2, result.issues.get(0).buildingCount());
        assertEquals("coal", result.issues.get(1).issue.resource.id);
        assertEquals(1, result.issues.get(1).buildingCount());
    }

    @Test
    void resourcesAreGroupedByContentIdNotByDisplayName(){
        //two mods can ship items whose localized names collide; identity must still keep them apart
        AreaDiagnosticResult result = analyze(
            entry(itemShortage("mod-a-alloy", "Alloy")),
            entry(itemShortage("mod-b-alloy", "Alloy")));

        assertEquals(2, result.issues.size(), "identical display names must not merge two resources");
        Set<String> ids = new HashSet<>();
        for(AreaIssueGroup group : result.issues) ids.add(group.issue.resource.id);
        assertEquals(Set.of("mod-a-alloy", "mod-b-alloy"), ids);
    }

    @Test
    void liquidShortagesGroupSeparatelyFromItemShortages(){
        AreaDiagnosticResult result = analyze(
            entry(liquidShortage("water", "Water")),
            entry(liquidShortage("water", "Water")),
            entry(itemShortage("sand", "Sand")));

        assertEquals(2, result.issues.size());
        assertEquals(DiagnosticReason.missingLiquidInput, result.issues.get(0).issue.reason);
        assertEquals(2, result.issues.get(0).buildingCount());
        assertEquals(2, result.summary.byStatus.get(AreaStatus.liquidShortage));
        assertEquals(1, result.summary.byStatus.get(AreaStatus.itemShortage));
    }

    @Test
    void powerLimitationGroupsAcrossBuildings(){
        AreaDiagnosticResult result = analyze(
            entry(powerShortage()), entry(powerShortage()), entry(powerShortage()), entry(healthy()));

        assertEquals(1, result.issues.size());
        assertEquals(DiagnosticReason.insufficientPower, result.issues.get(0).issue.reason);
        assertEquals(3, result.issues.get(0).buildingCount());
        assertEquals(3, result.summary.problems);
        assertEquals(1, result.summary.operating);
    }

    @Test
    void blockedOutputsGroupByTheProductThatCannotLeave(){
        AreaDiagnosticResult result = analyze(entry(outputBlocked()), entry(outputBlocked()));

        assertEquals(1, result.issues.size());
        AreaIssueGroup group = result.issues.get(0);
        assertEquals(DiagnosticReason.outputBlocked, group.issue.reason);
        assertEquals("silicon", group.issue.resource.id);
        assertEquals(2, group.buildingCount());
        assertEquals(2, result.summary.byStatus.get(AreaStatus.outputBlocked));
    }

    @Test
    void disabledBuildingsAreCountedButNotCalledAProblem(){
        AreaDiagnosticResult result = analyze(entry(disabled()), entry(disabled()), entry(healthy()));

        assertEquals(2, result.summary.byStatus.get(AreaStatus.disabled));
        assertEquals(0, result.summary.problems, "switching a building off is a choice, not a fault");
        assertEquals(2, result.summary.informational);
        //it still appears in the issue list, because the player may not have meant to leave it off
        assertEquals(1, result.issues.size());
        assertEquals(DiagnosticReason.disabled, result.issues.get(0).issue.reason);
    }

    @Test
    void blocksWithNoProductionModelAreReportedAsSuchAndInventNoProblems(){
        AreaDiagnosticResult result = analyze(
            entry(limitedSupport()), entry(limitedSupport()), entry(healthy()));

        assertEquals(2, result.summary.byStatus.get(AreaStatus.limitedDiagnostics));
        assertEquals(0, result.summary.problems);
        assertEquals(List.of(), result.issues, "an unsupported block is not evidence of a fault");
        assertEquals(1, result.summary.production, "only the crafter has a production model");
    }

    // ------------------------------------------------------------------ secondary findings

    @Test
    void aBuildingWithTwoProblemsCountsOnceButAppearsInBothGroups(){
        AreaEntry entry = entry(itemAndPowerShortage("sand", "Sand"));
        assertTrue(entry.result.findings.size() >= 2, "the fixture must actually produce two findings");

        AreaDiagnosticResult result = analyze(entry);

        assertEquals(1, result.summary.analyzed);
        assertEquals(1, result.summary.problems, "one building is one building, however many findings it has");
        assertEquals(1, result.summary.byStatus.get(AreaStatus.itemShortage));
        assertNull(result.summary.byStatus.get(AreaStatus.powerLimited),
            "the status comes from the primary finding alone");

        Set<DiagnosticReason> reasons = new HashSet<>();
        for(AreaIssueGroup group : result.issues) reasons.add(group.issue.reason);
        assertEquals(Set.of(DiagnosticReason.missingItemInput, DiagnosticReason.insufficientPower), reasons);
        for(AreaIssueGroup group : result.issues){
            assertEquals(1, group.buildingCount());
        }
    }

    @Test
    void aBuildingIsNeverListedTwiceInsideOneGroup(){
        //two mandatory item inputs at the same satisfaction produce one finding naming both, and the
        //same building must not be added once per resource within a single group
        AreaEntry entry = entry(Areas.itemShortage("sand", "Sand"));
        AreaDiagnosticResult result = AreaAnalyzer.analyze(AREA, 1, List.of(entry, entry));

        for(AreaIssueGroup group : result.issues){
            assertEquals(new HashSet<>(group.buildings).size(), group.buildings.size(),
                "group " + group.issue + " lists a building twice");
            assertEquals(1, group.buildingCount(), "the same building twice is still one building");
        }
    }

    // ------------------------------------------------------------------ ordering and counting

    @Test
    void issuesAreOrderedBySeverityThenByHowManyBuildingsTheyAffect(){
        List<AreaEntry> entries = new ArrayList<>();
        for(int i = 0; i < 5; i++) entries.add(entry(itemShortage("sand", "Sand")));
        for(int i = 0; i < 2; i++) entries.add(entry(powerShortage()));
        entries.add(entry(outputBlocked()));

        AreaDiagnosticResult result = AreaAnalyzer.analyze(AREA, entries.size(), entries);

        assertEquals(3, result.issues.size());
        assertEquals(5, result.issues.get(0).buildingCount());
        assertEquals(2, result.issues.get(1).buildingCount());
        assertEquals(1, result.issues.get(2).buildingCount());
    }

    @Test
    void orderingIsStableAcrossRuns(){
        List<AreaEntry> entries = List.of(
            entry(itemShortage("titanium", "Titanium")),
            entry(itemShortage("sand", "Sand")),
            entry(itemShortage("coal", "Coal")),
            entry(liquidShortage("water", "Water")),
            entry(powerShortage()));

        List<String> first = keys(AreaAnalyzer.analyze(AREA, entries.size(), entries));
        for(int run = 0; run < 20; run++){
            assertEquals(first, keys(AreaAnalyzer.analyze(AREA, entries.size(), entries)),
                "the ordering must not depend on hash iteration");
        }
        //all five affect one building each, so the tie is broken by reason priority and then identity
        assertEquals(5, first.size());
    }

    @Test
    void aMixedAreaCountsEachPopulationSeparately(){
        AreaDiagnosticResult result = analyze(
            entry(healthy()), entry(healthy()),
            entry(itemShortage("sand", "Sand")),
            entry(outputBlocked()),
            entry(powerShortage()),
            entry(disabled()),
            entry(limitedSupport()));

        assertEquals(7, result.summary.selected);
        assertEquals(7, result.summary.analyzed);
        assertEquals(6, result.summary.production, "the wall has no production model");
        assertEquals(3, result.summary.problems);
        assertEquals(2, result.summary.informational);
        assertEquals(2, result.summary.operating);
        assertEquals(result.summary.problems + result.summary.informational + result.summary.operating,
            result.summary.analyzed, "every analysed building falls into exactly one kind");
    }

    @Test
    void buildingsThatCouldNotBeAnalysedAreReportedRatherThanHidden(){
        AreaDiagnosticResult result = AreaAnalyzer.analyze(AREA, 5, List.of(entry(healthy())));

        assertEquals(5, result.summary.selected);
        assertEquals(1, result.summary.analyzed);
        assertEquals(4, result.summary.skipped());
    }

    @Test
    void statusCountsAreListedInTheDeclaredStatusOrder(){
        AreaDiagnosticResult result = analyze(
            entry(disabled()), entry(itemShortage("sand", "Sand")), entry(healthy()));

        assertEquals(List.of(AreaStatus.operating, AreaStatus.itemShortage, AreaStatus.disabled),
            new ArrayList<>(result.summary.byStatus.keySet()));
    }

    @Test
    void everyDiagnosticReasonMapsToAStatus(){
        for(DiagnosticReason reason : DiagnosticReason.values()){
            assertNotNull(AreaStatus.of(reason), reason + " has no area status");
        }
    }

    // ------------------------------------------------------------------ helpers

    private static AreaDiagnosticResult analyze(AreaEntry... entries){
        return AreaAnalyzer.analyze(AREA, entries.length, List.of(entries));
    }

    private static List<String> keys(AreaDiagnosticResult result){
        List<String> keys = new ArrayList<>();
        for(AreaIssueGroup group : result.issues) keys.add(group.issue.key());
        return keys;
    }
}
