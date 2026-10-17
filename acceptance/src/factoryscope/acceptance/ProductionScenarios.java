package factoryscope.acceptance;

import arc.func.Prov;
import factoryscope.FsBundle;
import factoryscope.model.FactorySnapshot;
import factoryscope.model.ProductionFamily;
import factoryscope.probe.MindustryFactoryProbe;
import factoryscope.ui.FactoryScopeUI;
import mindustry.content.Blocks;
import mindustry.content.Items;
import mindustry.gen.Building;
import mindustry.world.Tile;
import mindustry.world.blocks.environment.Floor;

/** Acceptance coverage for single-building production models and their localized panel evidence. */
final class ProductionScenarios{
    private final AcceptanceHarness harness;
    private Building target;

    ProductionScenarios(AcceptanceHarness harness){
        this.harness = harness;
    }

    void queue(){
        productionModels();
        productionLocalization();
    }

    private void productionModels(){
        productionPanel("a drill on sand shows its mining model", () -> {
            int x = harness.tileX() - 8, y = harness.tileY() + 6;
            for(int dx = -1; dx <= 2; dx++) for(int dy = -1; dy <= 2; dy++){
                Tile tile = mindustry.Vars.world.tile(x + dx, y + dy);
                if(tile == null) continue;
                if(tile.block() != Blocks.air) tile.remove();
                tile.setFloor((Floor)Blocks.sand);
            }
            return harness.place(Blocks.mechanicalDrill, x, y);
        }, ProductionFamily.drill, null, "production-drill", "section.production", "section.details", "fact.ore", "fact.ore-tiles", "fact.drill-tier");
        productionPanel("random separator outputs are shown as estimates", () -> harness.place(Blocks.separator, harness.tileX() - 4, harness.tileY() + 6), ProductionFamily.separator, "status.missing-liquid-input", "production-separator", "section.production", "panel.estimate-note", "note.separator-random");
        productionPanel("an unfuelled generator shows its power model and fuel shortage", () -> harness.place(Blocks.combustionGenerator, harness.tileX(), harness.tileY() + 6), ProductionFamily.generator, "status.missing-item-input", "production-generator", "section.production", "section.details", "fact.production-efficiency", "fact.fuel-use");
        productionPanel("a unit factory with nothing selected says so", () -> {
            Building factory = harness.place(Blocks.groundFactory, harness.tileX() + 5, harness.tileY() + 7);
            ((mindustry.world.blocks.units.UnitFactory.UnitFactoryBuild)factory).currentPlan = -1;
            return factory;
        }, ProductionFamily.unitFactory, "status.no-plan-selected", "production-unit-factory-empty");
        productionPanel("a unit factory shows the unit it builds", () -> {
            Building factory = harness.place(Blocks.groundFactory, harness.tileX() + 5, harness.tileY() + 7);
            ((mindustry.world.blocks.units.UnitFactory.UnitFactoryBuild)factory).currentPlan = 0;
            return factory;
        }, ProductionFamily.unitFactory, "status.missing-item-input", "production-unit-factory", "section.production", "section.details", "fact.unit", "fact.progress");
        productionPanel("an empty turret has no ammunition", () -> harness.place(Blocks.duo, harness.tileX() + 9, harness.tileY() + 6), ProductionFamily.turret, "status.no-ammo", "production-turret-empty", "section.details", "fact.ammo", "note.turret-estimates");
        productionPanel("a loaded turret without a target is ready", () -> {
            Building duo = harness.place(Blocks.duo, harness.tileX() + 9, harness.tileY() + 6);
            duo.handleItem(null, Items.copper);
            return duo;
        }, ProductionFamily.turret, "status.turret-idle", "production-turret-idle", "fact.ammo-type", "fact.fire-rate", "fact.ammo-use", "panel.estimate-note");
    }

    private void productionPanel(String name, Prov<Building> placer, ProductionFamily family, String statusKey, String shot, String... shownKeys){
        harness.scenario(name);
        harness.queue(harness::closeAnyDialog);
        harness.queue(() -> target = placer.get());
        harness.queue(harness::clickToggleButton);
        harness.queue(() -> harness.clickBuilding(target));
        harness.queue(() -> {
            harness.check("the panel opened for it", FactoryScopeUI.inspected() == target);
            FactorySnapshot snapshot = MindustryFactoryProbe.probe(target);
            harness.check("FactoryScope applies the " + family + " model", snapshot.family == family, String.valueOf(snapshot.family));
            if(statusKey != null) harness.check("the panel headline reads " + statusKey, harness.dialogShows(FsBundle.get(statusKey)), FsBundle.get(statusKey));
            for(String key : shownKeys) harness.check("the panel shows " + key, harness.dialogShows(FsBundle.get(key)), FsBundle.get(key));
            harness.check("the panel does not call a modeled block limited", !harness.dialogShows(FsBundle.get("panel.limited-support")));
        });
        harness.queue(() -> harness.capture(shot));
        harness.queue(harness::closeAnyDialog);
    }

    private void productionLocalization(){
        harness.scenarioNow("every production-model string resolves in the active locale");
        String[] keys = {"status.no-resource", "status.resource-too-hard", "status.no-plan-selected", "status.unit-cap-reached", "status.waiting-for-unit", "status.no-ammo", "status.missing-payload-input", "status.turret-idle", "diagnosis.no-resource.stopped", "diagnosis.no-resource.drill.stopped", "diagnosis.no-resource.beam-drill.stopped", "diagnosis.no-resource.wall-crafter.stopped", "diagnosis.no-resource.pump.stopped", "diagnosis.no-plan-selected.stopped", "diagnosis.turret-idle.normal", "section.details", "value.yes", "value.unknown", "panel.estimate-note", "note.separator-random", "note.turret-estimates", "note.reactor-heat", "note.impact-warmup", "note.flux-reactor", "note.explodes-when-full", "fact.drill-tier", "fact.ore", "fact.ore-tiles", "fact.ore-walls", "fact.max-boost", "fact.warmup", "fact.wall-yield", "fact.tile-yield", "fact.ground-yield", "fact.production-efficiency", "fact.heat-output", "fact.light", "fact.heat-yield", "fact.fuel-fullness", "fact.reactor-heat", "fact.heat-input", "fact.instability", "fact.fuel-use", "fact.output-tank", "fact.unit", "fact.unit-input", "fact.progress", "fact.unit-count", "fact.drones-ready", "fact.reload", "fact.ammo", "fact.ammo-type", "fact.fire-rate", "fact.ammo-use", "fact.engaging", "area.status.no-resource", "area.status.no-ammo", "area.status.unit-cap-reached", "area.status.payload-shortage", "area.status.not-configured", "area.issue.no-resource", "area.issue.no-ammo"};
        for(String key : keys){
            String text = FsBundle.get(key);
            harness.check(key + " resolves", !text.startsWith(FsBundle.PREFIX) && !text.contains("???"), text);
        }
        String[] formatted = {"diagnosis.resource-too-hard.drill.stopped", "diagnosis.resource-too-hard.beam-drill.stopped", "diagnosis.unit-cap-reached.stopped", "diagnosis.waiting-for-unit.stopped", "diagnosis.no-ammo.stopped", "diagnosis.missing-payload-input.stopped", "value.power-rate-pair", "value.rate-per-minute", "value.estimate", "value.items-per-second", "value.liquid-per-second", "value.times-per-second", "value.heat", "area.issue.resource-too-hard.resource", "area.issue.unit-cap-reached.resource"};
        for(String key : formatted){
            String text = FsBundle.format(key, "SENTINEL", "SENTINEL2");
            harness.check(key + " formats its subject", text.contains("SENTINEL") && !text.contains("{"), text);
        }
    }
}
