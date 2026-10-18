package factoryscope.acceptance;

import factoryscope.ui.FactoryScopeUI;
import factoryscope.analysis.DiagnosticReason;
import factoryscope.analysis.FactoryAnalyzer;
import factoryscope.model.FactorySnapshot;
import factoryscope.probe.MindustryFactoryProbe;
import mindustry.content.Blocks;

/** Exercises the HUD toggle, picker and direct single-building inspection path. */
final class HudInspectionScenarios{
    private final AcceptanceHarness harness;

    HudInspectionScenarios(AcceptanceHarness harness){
        this.harness = harness;
    }

    void queue(){
        harness.chatVisibilityScenarios();
        harness.hudVisibilityScenarios();
        harness.hudAnchorRebuildScenario();
        queueToggle();
        harness.verticalSelection("above", true);
        harness.verticalSelection("below", false);
        queueEmptyTerrain();
        queueUnsupportedBlock();
    }

    private void queueToggle(){
        harness.scenario("the HUD toggle activates the picker");
        harness.queue(harness::closeAnyDialog);
        harness.queue(harness::ensurePickerOff);
        harness.queue(harness::clickToggleButton);
        harness.queue(() -> {
            harness.check("picker is active after clicking the HUD button", FactoryScopeUI.picking());
            harness.check("exactly one HUD toggle exists", harness.countNamed("factoryscope-toggle") == 1);
            harness.check("exactly one picker overlay exists", harness.countNamed("factoryscope-picker") == 1);
            harness.checkToggleFollowsHudLayout("default window");
        });
        harness.queue(harness::clickToggleButton);
        harness.queue(() -> harness.check("picker is inactive after clicking again", !FactoryScopeUI.picking()));
    }

    private void queueEmptyTerrain(){
        harness.scenario("clicking empty terrain cancels without opening a panel");
        harness.queue(harness::closeAnyDialog);
        harness.queue(harness::clickToggleButton);
        harness.queue(() -> harness.clickTile(harness.emptyTileNearCamera()));
        harness.queue(() -> {
            harness.check("no panel opened for empty terrain", FactoryScopeUI.inspected() == null);
            harness.check("picker exited after clicking empty terrain", !FactoryScopeUI.picking());
        });
    }

    private void queueUnsupportedBlock(){
        harness.scenario("an unsupported block gives limited diagnostics, not an invented fault");
        harness.queue(harness::closeAnyDialog);
        harness.queue(() -> harness.target = harness.place(Blocks.titaniumWall, harness.tileX() + 5, harness.tileY() + 2));
        harness.queue(harness::clickToggleButton);
        harness.queue(() -> harness.clickBuilding(harness.target));
        harness.queue(() -> {
            harness.check("panel opened for a wall", FactoryScopeUI.inspected() == harness.target);
            FactorySnapshot snapshot = MindustryFactoryProbe.probe(harness.target);
            harness.check("a wall is not claimed to be efficiency-tracked", !snapshot.efficiencyTracked);
            harness.check("a wall reports limited diagnostics",
                FactoryAnalyzer.analyze(snapshot).reason() == DiagnosticReason.limitedSupport);
            harness.check("a wall is given no production model", snapshot.outputs.isEmpty());
        });
    }
}
