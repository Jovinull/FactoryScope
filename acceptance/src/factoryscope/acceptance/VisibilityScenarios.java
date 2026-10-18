package factoryscope.acceptance;

import arc.*;
import arc.scene.*;
import arc.util.*;
import factoryscope.*;
import factoryscope.area.*;
import factoryscope.liquid.*;
import factoryscope.model.*;
import factoryscope.probe.*;
import factoryscope.ui.*;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.world.*;
import mindustry.world.blocks.environment.Floor;

import static mindustry.Vars.*;

/** Fog and team-visibility transitions for held reports and live inspection. */
final class VisibilityScenarios{
    private final AcceptanceHarness harness;
    private Building fogTarget, fogSource;
    private Building fogLiquidProducer, fogLiquidFirstConduit, fogLiquidSecondConduit, fogLiquidConsumer;
    private boolean fogWasEnabled, staticFogWasEnabled;

    VisibilityScenarios(AcceptanceHarness harness){
        this.harness = harness;
    }

    void queue(){
        liveFogInspectionScenario();
        reportLocateFogTransitionScenario();
        reportLiquidFogTransitionScenario();
    }

    void liveFogInspectionScenario(){
        scenario("a live inspector stops following an enemy building after vision is lost");
        queue(this::closeAnyDialog);
        queue(() -> {
            fogWasEnabled = state.rules.fog;
            staticFogWasEnabled = state.rules.staticFog;
            state.rules.fog = true;
            state.rules.staticFog = false;
            fogControl.resetFog();

            int x = world.width() - 10, y = world.height() - 10;
            Tile targetTile = world.tile(x, y);
            Tile sourceTile = world.tile(x - 10, y - 10);
            if(targetTile != null && targetTile.block() != Blocks.air) targetTile.remove();
            if(sourceTile != null && sourceTile.block() != Blocks.air) sourceTile.remove();
            if(targetTile != null) targetTile.setBlock(Blocks.graphitePress, Team.crux, 0);
            fogTarget = targetTile == null ? null : targetTile.build;
            fogSource = placeAt(Blocks.coreShard, x - 10, y - 10);
            delayNextAction(120f);
        });
        queue(() -> {
            check("the controlled fog source reveals the enemy factory",
                fogTarget != null && !fogTarget.inFogTo(player.team()),
                fogTarget == null ? "target missing" : "inFog=" + fogTarget.inFogTo(player.team()));
            check("the visible enemy factory can be inspected",
                fogTarget != null && FactoryScopeUI.inspect(fogTarget));
            check("the live inspector selects that enemy factory", FactoryScopeUI.inspected() == fogTarget);
            if(fogSource != null && fogSource.isValid()) fogSource.tile.remove();
            delayNextAction(120f);
        });
        queue(() -> {
            check("the enemy factory becomes hidden after the fog source is removed",
                fogTarget != null && fogTarget.inFogTo(player.team()),
                fogTarget == null ? "target missing" : "inFog=" + fogTarget.inFogTo(player.team()));
            check("the inspector releases a now-hidden enemy factory",
                FactoryScopeUI.inspected() != fogTarget,
                "inspected=" + describe(FactoryScopeUI.inspected()));
            if(fogTarget != null && fogTarget.isValid()){
                fogSource = placeAt(Blocks.coreShard, fogTarget.tile.x - 10, fogTarget.tile.y - 10);
            }
            delayNextAction(120f);
        });
        queue(() -> {
            check("the same enemy factory becomes visible again when the controlled fog source returns",
                fogTarget != null && fogTarget.isValid() && !fogTarget.inFogTo(player.team()),
                fogTarget == null ? "target missing" : "inFog=" + fogTarget.inFogTo(player.team()));
            check("the live inspector stays closed until the player explicitly opens it again",
                FactoryScopeUI.inspected() != fogTarget);
            check("explicitly inspecting the visible factory still works after the fog transition",
                fogTarget != null && FactoryScopeUI.inspect(fogTarget));
            check("the explicit reinspection selects the visible factory",
                FactoryScopeUI.inspected() == fogTarget);
            if(fogSource != null && fogSource.isValid()) fogSource.tile.remove();
            delayNextAction(120f);
        });
        queue(() -> {
            check("the re-inspected factory becomes hidden when the restored fog source is removed",
                fogTarget != null && fogTarget.inFogTo(player.team()),
                fogTarget == null ? "target missing" : "inFog=" + fogTarget.inFogTo(player.team()));
            check("the re-opened live inspector also releases its target on the next hidden transition",
                FactoryScopeUI.inspected() != fogTarget,
                "inspected=" + describe(FactoryScopeUI.inspected()));
            if(fogTarget != null && fogTarget.isValid()) fogTarget.tile.remove();
            state.rules.fog = fogWasEnabled;
            state.rules.staticFog = staticFogWasEnabled;
            fogControl.resetFog();
            closeAnyDialog();
        });
    }

    void reportLocateFogTransitionScenario(){
        Team originalTeam = player.team();
        boolean originalFog = state.rules.fog;
        boolean originalStaticFog = state.rules.staticFog;
        boolean originalPvp = state.rules.pvp;

        scenario("a held area report cannot Locate a building hidden from the current team");
        queue(this::closeAnyDialog);
        queue(() -> {
            state.rules.fog = true;
            state.rules.staticFog = false;
            fogControl.resetFog();
            clearRegion();
            Tile tile = world.tile(rx() + 4, ry() + 4);
            if(tile != null) tile.setBlock(Blocks.graphitePress, originalTeam, 0);
            fogTarget = tile == null ? null : tile.build;
            check("the report target starts visible to its owner",
                fogTarget != null && fogTarget.team == originalTeam && !fogTarget.inFogTo(originalTeam));
        });
        queue(this::armPicker);
        queue(() -> dragTiles(rx() + 1, ry() + 1, rx() + 8, ry() + 8));
        queue(() -> {
            AreaDiagnosticResult report = FactoryScopeUI.areaReport();
            check("the visible building is captured in the area snapshot", report != null && fogTarget != null
                && report.entries.stream().anyMatch(entry -> entry.ref.tileX == fogTarget.tile.x
                    && entry.ref.tileY == fogTarget.tile.y));
            check("Area Diagnostics labels its captured evidence as a snapshot",
                dialogShows(FsBundle.get("area.snapshot-note")));
        });
        queue(() -> clickNamed("factoryscope-area-issue"));
        queue(() -> check("the captured building has a Locate action",
            Core.scene.find("factoryscope-area-locate") != null));
        queue(() -> clickNamed("factoryscope-area-locate"));
        queue(() -> check("Locate starts while the building is currently visible", FactoryScopeUI.locating()));
        queue(() -> {
            //In this campaign map, non-default teams are AI and Mindustry deliberately treats
            //their fog as fully visible. PvP makes both teams human visibility contexts for this
            //controlled team-switch fixture.
            state.rules.pvp = true;
            player.team(Team.crux);
            fogControl.resetFog();
            delayNextAction(120f);
        });
        queue(() -> {
            boolean hidden = fogTarget != null && fogTarget.inFogTo(player.team());
            check("the same target is hidden from the changed viewer team", hidden,
                fogTarget == null ? "target missing" : "inFog=" + fogTarget.inFogTo(player.team())
                    + ", viewer=" + player.team().name + ", viewerIsAI=" + player.team().isAI()
                    + ", fog=" + state.rules.fog);
            if(hidden){
                check("an active Locate marker stops when its target leaves current vision",
                    !FactoryScopeUI.locating(), "locating=" + FactoryScopeUI.locating());
                if(FactoryScopeUI.locating() && Core.scene.find("factoryscope-locate-return") != null){
                    clickNamed("factoryscope-locate-return");
                }
            }
            delayNextAction(30f);
        });
        queue(() -> {
            if(Core.scene.find("factoryscope-area-issue") != null) clickNamed("factoryscope-area-issue");
            delayNextAction(30f);
        });
        queue(() -> {
            if(Core.scene.find("factoryscope-area-locate") != null) clickNamed("factoryscope-area-locate");
            check("a stale-report Locate is rejected before a new marker starts", !FactoryScopeUI.locating(),
                "locating=" + FactoryScopeUI.locating());
            delayNextAction(60f);
        });
        queue(() -> {
            check("Locate does not navigate to a target hidden from the current team",
                !FactoryScopeUI.locating(), "locating=" + FactoryScopeUI.locating());
            check("the inert old area snapshot remains available after denied navigation",
                FactoryScopeUI.areaReportHeld() && Core.scene.find("factoryscope-area-dialog") != null,
                "held=" + FactoryScopeUI.areaReportHeld() + ", dialog="
                    + Core.scene.find("factoryscope-area-dialog"));
            if(Core.scene.find("factoryscope-area-building") != null){
                clickNamed("factoryscope-area-building");
            }
            delayNextAction(30f);
        });
        queue(() -> {
            check("Inspect from a stale area snapshot does not open the hidden building",
                FactoryScopeUI.inspected() == null
                    && Core.scene.find("factoryscope-area-dialog") != null
                    && sceneShows(FsBundle.get("target.unavailable"))
                    && !sceneShows(FsBundle.get("area.building-gone")),
                "inspected=" + FactoryScopeUI.inspected());
            delayNextAction(140f);
        });
        queue(() -> {
            check("the hidden target still exists in the client model for the controlled picker test",
                fogTarget != null && fogTarget.isValid()
                    && world.buildWorld(fogTarget.x, fogTarget.y) == fogTarget
                    && fogTarget.inFogTo(player.team()));
            clickToggleButton();
            clickTile(fogTarget.tile);
        });
        queue(() -> check("tapping a hidden building does not reveal its presence through a special toast",
            FactoryScopeUI.inspected() == null && !FactoryScopeUI.picking()
                && !sceneShows(FsBundle.get("inspect.not-visible"))
                && !sceneShows(FsBundle.get("target.unavailable"))));
        queue(() -> {
            player.team(originalTeam);
            fogControl.resetFog();
            check("the old target becomes visible again before the destruction race",
                fogTarget != null && fogTarget.isValid() && !fogTarget.inFogTo(player.team()));
            clickNamed("factoryscope-area-locate");
            check("Locate starts again while the same target is visible", FactoryScopeUI.locating());
            check("the Area Diagnostics dialog is still in its close transition",
                Core.scene.find("factoryscope-area-dialog") != null);
            player.team(Team.crux);
            fogControl.resetFog();
            boolean hiddenBeforeDestroy = fogTarget != null && fogTarget.inFogTo(player.team());
            if(fogTarget != null && fogTarget.isValid()) fogTarget.tile.remove();
            check("the target is proven hidden immediately before hidden destruction", hiddenBeforeDestroy);
        });
        queue(() -> {
            check("an active Locate ends safely if its hidden target is destroyed before the next update",
                !FactoryScopeUI.locating(), "locating=" + FactoryScopeUI.locating());
            delayNextAction(45f);
        });
        queue(() -> {
            check("the old Area Diagnostics report returns after hidden destruction",
                Core.scene.find("factoryscope-area-dialog") != null);
            if(Core.scene.find("factoryscope-area-issue") != null) clickNamed("factoryscope-area-issue");
        });
        queue(() -> {
            check("the stale report still retains its historical row after hidden destruction",
                Core.scene.find("factoryscope-area-building") != null);
            clickNamed("factoryscope-area-building");
        });
        queue(() -> {
            check("hidden destruction and hidden presence receive the same unavailable response",
                FactoryScopeUI.inspected() == null
                    && sceneShows(FsBundle.get("target.unavailable"))
                    && !sceneShows(FsBundle.get("area.building-gone")));
            if(Core.scene.find("factoryscope-area-refresh") != null) clickNamed("factoryscope-area-refresh");
        });
        queue(() -> {
            AreaDiagnosticResult refreshed = FactoryScopeUI.areaReport();
            boolean reacquired = refreshed != null && fogTarget != null && refreshed.entries.stream()
                .anyMatch(entry -> entry.ref.tileX == fogTarget.tile.x && entry.ref.tileY == fogTarget.tile.y
                    && entry.ref.teamId == originalTeam.id);
            check("Refresh does not reacquire a building hidden from the current team", !reacquired,
                "reacquired=" + reacquired + ", entries=" + (refreshed == null ? "null" : refreshed.entries.size()));
        });
        queue(() -> {
            player.team(originalTeam);
            if(fogTarget != null && fogTarget.isValid()) fogTarget.tile.remove();
            state.rules.fog = originalFog;
            state.rules.staticFog = originalStaticFog;
            state.rules.pvp = originalPvp;
            fogControl.resetFog();
            FactoryScopeUI.reset();
            closeAnyDialog();
        });
    }

    void reportLiquidFogTransitionScenario(){
        Team originalTeam = player.team();
        boolean originalFog = state.rules.fog;
        boolean originalStaticFog = state.rules.staticFog;
        boolean originalPvp = state.rules.pvp;

        scenario("LiquidScope navigation does not reacquire hidden endpoint state");
        queue(this::closeAnyDialog);
        queue(() -> {
            state.rules.fog = true;
            state.rules.staticFog = false;
            fogControl.resetFog();
            clearRegion();
            int x = rx(), y = ry();
            for(int dx = 2; dx <= 3; dx++) for(int dy = 2; dy <= 3; dy++){
                Tile floor = world.tile(x + dx, y + dy);
                floor.setFloor((Floor)Blocks.water);
                floor.clearOverlay();
            }
            fogLiquidProducer = placeAt(Blocks.mechanicalPump, x + 3, y + 3);
            fogLiquidFirstConduit = placeAt(Blocks.conduit, x + 4, y + 3, 0);
            fogLiquidSecondConduit = placeAt(Blocks.conduit, x + 5, y + 3, 0);
            fogLiquidConsumer = placeAt(Blocks.cryofluidMixer, x + 6, y + 3);
            delayNextAction(90f);
        });
        queue(() -> {
            FactorySnapshot pump = fogLiquidProducer == null ? null : MindustryFactoryProbe.probe(fogLiquidProducer);
            ResourceRef water = new ResourceRef(ResourceKind.liquid, Liquids.water.name, Liquids.water.localizedName);
            check("the controlled producer resolves to exact Water before snapshot capture",
                pump != null && pump.producedLiquids.contains(water),
                pump == null ? "pump missing" : pump.producedLiquids.toString());
            check("the producer starts visible to its owner",
                fogLiquidProducer != null && !fogLiquidProducer.inFogTo(originalTeam));
            clickToggleButton();
        });
        queue(() -> dragTiles(rx() + 1, ry() + 1, rx() + 9, ry() + 8));
        queue(() -> {
            AreaDiagnosticResult report = FactoryScopeUI.areaReport();
            check("the area snapshot includes the liquid producer and Water resource",
                report != null && fogLiquidProducer != null
                    && report.entries.stream().anyMatch(entry -> entry.ref.equals(AreaProbe.refOf(fogLiquidProducer)))
                    && report.liquids.resources.stream().anyMatch(resource -> resource.id.equals(Liquids.water.name)));
            clickNamed("factoryscope-area-liquids");
        });
        queue(() -> {
            check("LiquidScope offers Water from the frozen area snapshot",
                Core.scene.find("factoryscope-liquid-select-water") != null);
            clickNamed("factoryscope-liquid-select-water");
        });
        queue(() -> check("the captured Water producer has a snapshot Inspect action",
            Core.scene.find("factoryscope-liquid-inspect") != null));
        queue(() -> clickNamed("factoryscope-liquid-view-world"));
        queue(() -> {
            check("the LiquidScope world overlay is active before vision changes",
                Core.scene.find("factoryscope-liquid-viewing") != null);
            state.rules.pvp = true;
            player.team(Team.crux);
            fogControl.resetFog();
            boolean hidden = fogLiquidProducer != null && fogLiquidProducer.inFogTo(player.team());
            check("Mindustry reports the captured producer hidden from the new viewer", hidden,
                fogLiquidProducer == null ? "producer missing" : "inFog=" + fogLiquidProducer.inFogTo(player.team()));
            if(fogLiquidProducer != null && fogLiquidProducer.isValid()) fogLiquidProducer.tile.remove();
        });
        queue(() -> {
            check("the prior structural overlay remains inert historical evidence until Return",
                Core.scene.find("factoryscope-liquid-viewing") != null);
            clickNamed("factoryscope-liquid-return");
        });
        queue(() -> {
            check("Return restores the old Water snapshot without resolving the hidden producer",
                Core.scene.find("factoryscope-liquid-dialog") != null
                    && Core.scene.find("factoryscope-liquid-select-water") != null
                    && Core.scene.find("factoryscope-liquid-inspect") != null);
            if(Core.scene.find("factoryscope-liquid-inspect") != null) clickNamed("factoryscope-liquid-inspect");
        });
        queue(() -> {
            check("hidden destruction does not remove an Inspect action from the frozen LiquidScope snapshot",
                Core.scene.find("factoryscope-liquid-dialog") != null
                    && Core.scene.find("factoryscope-liquid-inspect") != null);
            check("the stale LiquidScope row retains a navigable action that will revalidate at use time",
                Core.scene.find("factoryscope-liquid-locate") != null);
            clickNamed("factoryscope-liquid-locate");
            delayNextAction(45f);
        });
        queue(() -> {
            check("hidden LiquidScope Locate is rejected and returns to its old report",
                !FactoryScopeUI.locating()
                    && Core.scene.find("factoryscope-liquid-dialog") != null
                    && sceneShows(FsBundle.get("target.unavailable")));
            if(Core.scene.find("factoryscope-liquid-inspect") != null) clickNamed("factoryscope-liquid-inspect");
        });
        queue(() -> {
            check("Inspect remains inert after hidden destruction and reports only generic unavailability",
                FactoryScopeUI.inspected() == null
                    && Core.scene.find("factoryscope-liquid-dialog") != null
                    && sceneShows(FsBundle.get("target.unavailable"))
                    && !sceneShows(FsBundle.get("area.building-gone")));
            clickNamed("factoryscope-liquid-refresh");
        });
        queue(() -> check("LiquidScope Refresh removes hidden endpoint evidence from the new snapshot",
            Core.scene.find("factoryscope-liquid-select-water") == null
                && Core.scene.find("factoryscope-liquid-inspect") == null,
            "water action=" + Core.scene.find("factoryscope-liquid-select-water")
                + ", inspect=" + Core.scene.find("factoryscope-liquid-inspect")));
        queue(() -> {
            player.team(originalTeam);
            for(Building build : new Building[]{fogLiquidFirstConduit, fogLiquidSecondConduit, fogLiquidConsumer}){
                if(build != null && build.isValid()) build.tile.remove();
            }
            state.rules.fog = originalFog;
            state.rules.staticFog = originalStaticFog;
            state.rules.pvp = originalPvp;
            fogControl.resetFog();
            FactoryScopeUI.reset();
            closeAnyDialog();
        });
    }



    private void scenario(String name){ harness.scenario(name); }
    private void queue(Runnable action){ harness.queue(action); }
    private void check(String what, boolean ok){ harness.check(what, ok); }
    private void check(String what, boolean ok, String detail){ harness.check(what, ok, detail); }
    private void delayNextAction(float ticks){ harness.delayNextAction(ticks); }
    private void closeAnyDialog(){ harness.closeAnyDialog(); }
    private void clearRegion(){ harness.clearRegion(); }
    private int rx(){ return harness.rx(); }
    private int ry(){ return harness.ry(); }
    private void armPicker(){ harness.armPicker(); }
    private void dragTiles(int x1, int y1, int x2, int y2){ harness.dragTiles(x1, y1, x2, y2); }
    private void clickNamed(String name){ harness.clickNamed(name); }
    private void clickToggleButton(){ harness.clickToggleButton(); }
    private void clickTile(Tile tile){ harness.clickTile(tile); }
    private Building placeAt(Block block, int x, int y){ return harness.placeAt(block, x, y); }
    private Building placeAt(Block block, int x, int y, int rotation){ return harness.placeAt(block, x, y, rotation); }
    private String describe(Building building){ return harness.describe(building); }
    private boolean dialogShows(String text){ return harness.dialogShows(text); }
    private boolean sceneShows(String text){ return harness.sceneShows(text); }
}
