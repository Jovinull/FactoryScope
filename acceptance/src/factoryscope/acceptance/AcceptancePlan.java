package factoryscope.acceptance;

import factoryscope.ui.FactoryScopeUI;
import factoryscope.analysis.DiagnosticReason;
import factoryscope.analysis.FactoryAnalyzer;
import factoryscope.model.FactorySnapshot;
import factoryscope.probe.MindustryFactoryProbe;
import arc.Events;
import mindustry.content.Blocks;
import mindustry.game.EventType.WorldLoadEvent;

/** Orders independent acceptance scenario groups while keeping tick ordering explicit. */
final class AcceptancePlan{
    private final AcceptanceHarness harness;

    AcceptancePlan(AcceptanceHarness harness){
        this.harness = harness;
    }

    void queue(){
        harness.context.captureAreaOrigin(harness.tileX() + 5, harness.tileY() - 7);
        new HudInspectionScenarios(harness).queue();
        new ProductionScenarios(harness).queue();
        queueDestroyedTarget();
        new VisibilityScenarios(harness).queue();
        queueLayoutAndLocalization();
        queueWorldChange();
        harness.area.queue();
        harness.itemNetwork.queueUnsupportedLocate();
        harness.context.actions.add(harness::finish);
        harness.pump();
    }

    private void queueDestroyedTarget(){
        harness.scenario("a destroyed target is released while the panel is open");
        harness.queue(harness::closeAnyDialog);
        harness.queue(() -> harness.target = harness.place(Blocks.kiln, harness.tileX() + 8, harness.tileY() + 4));
        harness.queue(harness::clickToggleButton);
        harness.queue(() -> harness.clickBuilding(harness.target));
        harness.queue(() -> harness.check("panel opened for the victim", FactoryScopeUI.inspected() == harness.target));
        harness.queue(() -> harness.target.tile.remove());
        harness.queue(() -> harness.check("panel released the destroyed building", FactoryScopeUI.inspected() == null));
    }

    private void queueLayoutAndLocalization(){
        harness.repeatedUse(8);
        int[][] sizes = {{1280, 720}, {1920, 1080}, {2560, 1440}};
        for(int[] size : sizes) harness.layout(size[0], size[1], 1f);
        harness.layout(1280, 720, 2f);
        harness.layout(1280, 720, 1.5f);
        harness.layout(1920, 1080, 1.5f);
        harness.layout(1920, 1080, 2f);
        harness.layout(2560, 1440, 1.5f);
        harness.layout(2560, 1440, 2f);
        harness.layout(720, 1280, 1.5f);
        harness.layout(720, 1280, 2f);
        harness.queue(harness::restoreLayout);
        harness.queue(harness::checkLocalization);
    }

    private void queueWorldChange(){
        harness.scenario("a world change clears the panel");
        harness.queue(harness::closeAnyDialog);
        harness.queue(() -> harness.target = harness.place(Blocks.siliconSmelter, harness.tileX() + 10, harness.tileY()));
        harness.queue(harness::clickToggleButton);
        harness.queue(() -> harness.clickBuilding(harness.target));
        harness.queue(() -> harness.check("panel opened before the world change", FactoryScopeUI.inspected() == harness.target));
        harness.queue(() -> Events.fire(new WorldLoadEvent()));
        harness.queue(() -> {
            harness.check("panel cleared on world load", FactoryScopeUI.inspected() == null);
            harness.check("picker cleared on world load", !FactoryScopeUI.picking());
            harness.checkToggleFollowsHudLayout("after world load");
        });
    }
}
