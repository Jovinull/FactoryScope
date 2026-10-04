package factoryscope.acceptance;

import arc.*;
import arc.files.*;
import arc.graphics.*;
import arc.input.*;
import arc.math.*;
import arc.math.geom.*;
import arc.scene.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import factoryscope.*;
import factoryscope.analysis.*;
import factoryscope.area.*;
import factoryscope.model.*;
import factoryscope.probe.*;
import factoryscope.power.*;
import factoryscope.liquid.*;
import factoryscope.trace.*;
import factoryscope.ui.*;
import mindustry.content.*;
import mindustry.core.*;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.maps.*;
import mindustry.mod.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.environment.Floor;
import mindustry.world.consumers.ConsumeCoolant;

import static mindustry.Vars.*;

/**
 * Acceptance suite for the inspector, run inside a real Mindustry client.
 *
 * <p>It exists because the parts of FactoryScope that broke in practice are the ones no headless test
 * can reach: the HUD button, the picker overlay, and the arithmetic that turns a tap into a tile. Every
 * click here goes through {@code Core.scene.touchDown}/{@code touchUp}, the same entry points the SDL
 * backend calls, so the production listener and the production coordinate conversion are what run.
 *
 * <p>Each action is queued on its own game tick. An element added this frame is not sized until the
 * scene next lays out, and a dialog told to hide is still on screen until its fade finishes, so
 * back-to-back actions would only ever test the harness.
 *
 * <p>Results are written to the game log as {@code [HARNESS]} lines and the portable Java acceptance
 * launcher reads those lines and turns them into an exit code. This is a separate mod: it is never
 * part of the FactoryScope artifact.
 */
public class AcceptanceHarness extends Mod{
    static final float TICKS_BETWEEN_ACTIONS = 12f;
    static final String TAG = "[HARNESS]";
    /** Intermediate pointer positions per drag; a real pointer never jumps from one corner to the other. */
    static final int DRAG_STEPS = 6;

    final Seq<String> failures = new Seq<>();
    final Seq<Runnable> actions = new Seq<>();
    int checks;
    int areaOriginX, areaOriginY;
    boolean areaOriginCaptured;

    Building upper, lower, target, producer, disabledProducer, disabledRouteBreak;
    Building fogTarget, fogSource;
    Building fogLiquidProducer, fogLiquidFirstConduit, fogLiquidSecondConduit, fogLiquidConsumer;
    boolean fogWasEnabled, staticFogWasEnabled;
    AreaDiagnosticResult traceSnapshotBeforeRefresh;
    PowerGridReport refreshedPowerSnapshot;
    int baselineFactoryScopeElements;
    float nextActionDelay = TICKS_BETWEEN_ACTIONS;
    final Seq<Building> patch = new Seq<>();
    final Seq<AreaSelection> bounds = new Seq<>();
    final Seq<String> members = new Seq<>();
    ConsumeCoolant acceptanceCoolant;
    boolean acceptanceCoolantWasOptional;

    public AcceptanceHarness(){
        Events.on(ClientLoadEvent.class, event -> Time.runTask(120f, this::start));
    }

    void start(){
        checkOnlyHarnessModsLoaded();
        try{
            Map map = maps.loadInternalMap("serpulo/groundZero");
            Rules rules = map.applyRules(Gamemode.survival);
            //the campaign map restricts the playable area, which would disable anything built outside it
            rules.limitMapArea = false;
            control.playMap(map, rules);
            Time.runTask(240f, this::plan);
        }catch(Throwable t){
            Log.err(TAG + " could not start the map", t);
            finish();
        }
    }

    private void checkOnlyHarnessModsLoaded(){
        Seq<String> unexpected = new Seq<>();
        for(Mods.LoadedMod mod : mods.list()){
            if(!mod.name.equals("factory-scope") && !mod.name.equals("factory-scope-acceptance")){
                unexpected.add(mod.name);
            }
        }
        check("the sandbox loaded no external mods", unexpected.isEmpty(), unexpected.toString(", "));
    }

    void plan(){
        //Area fixtures and their queued drag coordinates must share one origin. Some production
        //scenarios intentionally pan the camera (Locate and off-world selection); following the
        //camera in rx()/ry() after those actions made later fixtures get built at a new location
        //while their already-queued screen gestures still pointed at the old one.
        areaOriginX = tileX() + 5;
        areaOriginY = tileY() - 7;
        areaOriginCaptured = true;
        chatVisibilityScenarios();
        hudVisibilityScenarios();
        hudAnchorRebuildScenario();
        scenario("the HUD toggle activates the picker");
        queue(this::closeAnyDialog);
        queue(this::ensurePickerOff);
        queue(this::clickToggleButton);
        queue(() -> {
            check("picker is active after clicking the HUD button", FactoryScopeUI.picking());
            check("exactly one HUD toggle exists", countNamed("factoryscope-toggle") == 1);
            check("exactly one picker overlay exists", countNamed("factoryscope-picker") == 1);
            checkToggleFollowsHudLayout("default window");
        });
        queue(this::clickToggleButton);
        queue(() -> check("picker is inactive after clicking again", !FactoryScopeUI.picking()));

        verticalSelection("above", true);
        verticalSelection("below", false);

        scenario("clicking empty terrain cancels without opening a panel");
        queue(this::closeAnyDialog);
        queue(this::clickToggleButton);
        queue(() -> clickTile(emptyTileNearCamera()));
        queue(() -> {
            check("no panel opened for empty terrain", FactoryScopeUI.inspected() == null);
            check("picker exited after clicking empty terrain", !FactoryScopeUI.picking());
        });

        scenario("an unsupported block gives limited diagnostics, not an invented fault");
        queue(this::closeAnyDialog);
        queue(() -> target = place(Blocks.titaniumWall, tileX() + 5, tileY() + 2));
        queue(this::clickToggleButton);
        queue(() -> clickBuilding(target));
        queue(() -> {
            check("panel opened for a wall", FactoryScopeUI.inspected() == target);
            FactorySnapshot snapshot = MindustryFactoryProbe.probe(target);
            check("a wall is not claimed to be efficiency-tracked", !snapshot.efficiencyTracked);
            check("a wall reports limited diagnostics",
                FactoryAnalyzer.analyze(snapshot).reason() == DiagnosticReason.limitedSupport);
            check("a wall is given no production model", snapshot.outputs.isEmpty());
        });

        scenario("a destroyed target is released while the panel is open");
        queue(this::closeAnyDialog);
        queue(() -> target = place(Blocks.kiln, tileX() + 8, tileY() + 4));
        queue(this::clickToggleButton);
        queue(() -> clickBuilding(target));
        queue(() -> check("panel opened for the victim", FactoryScopeUI.inspected() == target));
        queue(() -> target.tile.remove());
        queue(() -> check("panel released the destroyed building", FactoryScopeUI.inspected() == null));

        liveFogInspectionScenario();
        reportLocateFogTransitionScenario();
        reportLiquidFogTransitionScenario();

        repeatedUse(8);
        layout(1280, 720, 1f);
        layout(1920, 1080, 1f);
        layout(2560, 1440, 1f);
        layout(1280, 720, 2f);
        layout(1280, 720, 1.5f);
        layout(1920, 1080, 1.5f);
        layout(1920, 1080, 2f);
        layout(2560, 1440, 1.5f);
        layout(2560, 1440, 2f);
        layout(720, 1280, 1.5f);
        layout(720, 1280, 2f);
        queue(this::restoreLayout);
        queue(this::checkLocalization);

        scenario("a world change clears the panel");
        queue(this::closeAnyDialog);
        queue(() -> target = place(Blocks.siliconSmelter, tileX() + 10, tileY()));
        queue(this::clickToggleButton);
        queue(() -> clickBuilding(target));
        queue(() -> check("panel opened before the world change", FactoryScopeUI.inspected() == target));
        queue(() -> Events.fire(new WorldLoadEvent()));
        queue(() -> {
            check("panel cleared on world load", FactoryScopeUI.inspected() == null);
            check("picker cleared on world load", !FactoryScopeUI.picking());
            checkToggleFollowsHudLayout("after world load");
        });

        areaScenarios();
        unsupportedTraceLocate();

        actions.add(this::finish);
        pump();
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

    void chatVisibilityScenarios(){
        scenario("the HUD toggle stays aligned when chat is shown or hidden");
        queue(() -> {
            ui.chatfrag.hide();
            ui.chatfrag.toggle();
            check("the real chat fragment is shown", ui.chatfrag.shown());
            checkToggleFollowsHudLayout("chat visible");
        });
        queue(() -> {
            ui.chatfrag.hide();
            check("the real chat fragment is hidden", !ui.chatfrag.shown());
            checkToggleFollowsHudLayout("chat hidden");
        });
    }

    void hudVisibilityScenarios(){
        scenario("the FactoryScope toggle follows the vanilla HUD visibility");
        queue(() -> ui.hudfrag.shown = false);
        queue(() -> {
            Element slot = Core.scene.find("factoryscope");
            check("the toggle is hidden and non-interactive with the vanilla HUD",
                slot != null && slot.color.a == 0f && slot.touchable == arc.scene.event.Touchable.disabled);
        });
        queue(() -> ui.hudfrag.shown = true);
        queue(() -> {
            Element slot = Core.scene.find("factoryscope");
            check("the toggle returns when the vanilla HUD is shown",
                slot != null && slot.color.a == 1f && slot.touchable == arc.scene.event.Touchable.enabled);
            checkToggleFollowsHudLayout("HUD restored");
        });
    }

    void hudAnchorRebuildScenario(){
        Element anchor = ui.hudGroup.find("waves/editor");
        Element replacement = new Element();
        replacement.name = "waves/editor";
        replacement.setSize(120f, 60f);
        replacement.setPosition(25f, 620f);
        replacement.touchable = arc.scene.event.Touchable.disabled;
        scenario("the FactoryScope toggle recovers when the HUD layout anchor is rebuilt");
        queue(() -> {
            check("the expected vanilla layout anchor exists before rebuild", anchor != null);
            if(anchor != null) anchor.name = "factoryscope-test-stale-anchor";
        });
        queue(() -> {
            Element slot = Core.scene.find("factoryscope");
            check("the toggle is hidden while the HUD layout anchor is absent",
                slot != null && slot.color.a == 0f && slot.touchable == arc.scene.event.Touchable.disabled);
        });
        queue(() -> {
            ui.hudGroup.addChild(replacement);
        });
        queue(() -> {
            Element slot = Core.scene.find("factoryscope");
            check("the toggle follows a replacement instead of its still-parented stale anchor",
                slot != null && slot.color.a == 1f && slot.touchable == arc.scene.event.Touchable.enabled);
            checkToggleFollowsHudLayout("replacement HUD anchor");
        });
        queue(() -> {
            replacement.remove();
            if(anchor != null) anchor.name = "waves/editor";
        });
        queue(() -> {
            checkToggleFollowsHudLayout("original HUD anchor restored");
        });
    }

    /**
     * The regression test for the tap-to-tile conversion.
     *
     * <p>Two crafters sit the same distance above and below the camera. Arc reports input with the
     * origin at the bottom left and {@code Scene.stageToScreenCoordinates} flips it, so a conversion
     * that uses the wrong one resolves each of these clicks to the other building. That defect shipped
     * in 0.1.0 and this is what catches it coming back.
     */
    void verticalSelection(String label, boolean above){
        scenario("a real click selects the building " + label + " the camera");
        queue(this::closeAnyDialog);
        queue(() -> {
            upper = place(Blocks.siliconSmelter, tileX() - 6, tileY() + 6);
            lower = place(Blocks.graphitePress, tileX() - 6, tileY() - 6);
            target = above ? upper : lower;
        });
        queue(this::clickToggleButton);
        queue(() -> clickBuilding(target));
        queue(() -> {
            Building got = FactoryScopeUI.inspected();
            Building other = above ? lower : upper;
            check("panel opened for the " + label + " building", got != null);
            check("selected the intended building, not the mirrored one", got == target,
                "wanted " + describe(target) + " got " + describe(got)
                    + (got == other ? " -- Y IS MIRRORED" : ""));
        });
    }

    /** Full activate, select and close cycles, to expose listeners or widgets that are never released. */
    void repeatedUse(int cycles){
        scenario("repeated activate, select and close leaks nothing");
        queue(this::closeAnyDialog);
        queue(this::ensurePickerOff);
        queue(() -> {
            target = place(Blocks.siliconSmelter, tileX() + 12, tileY() - 4);
            baselineFactoryScopeElements = countFactoryScopeElements();
        });

        for(int i = 0; i < cycles; i++){
            queue(this::clickToggleButton);
            queue(() -> clickBuilding(target));
            queue(this::closeAnyDialog);
        }

        queue(() -> {
            check("picker is off after the last cycle", !FactoryScopeUI.picking());
            check("still exactly one HUD toggle", countNamed("factoryscope-toggle") == 1);
            check("no leftover picker overlay", countNamed("factoryscope-picker") == 0);
            check("no leftover hint", countNamed("factoryscope-hint") == 0);
            int now = countFactoryScopeElements();
            check("FactoryScope scene elements returned to baseline", now <= baselineFactoryScopeElements,
                "baseline " + baselineFactoryScopeElements + " now " + now);
        });
    }

    /**
     * Lays the scene out at a given size and UI scale and re-checks the panel. The window itself does
     * not move; what matters is that the dialog sizes from the scene, which is exactly what changes when
     * a player resizes the game or moves the UI scale slider.
     */
    void layout(int width, int height, float scale){
        scenario("the panel fits a " + width + "x" + height + " scene at " + scale + "x UI scale");
        queue(this::closeAnyDialog);
        queue(() -> {
            Scl.setProduct(scale);
            Core.scene.resize(width, height);
        });
        queue(() -> checkToggleFollowsHudLayout(width + "x" + height + " @ " + scale + "x"));
        //opened directly: the click path is covered above, and a synthetic click would otherwise mix
        //real window coordinates with a resized scene viewport
        queue(() -> target = place(Blocks.surgeSmelter, tileX() + 14, tileY() + 6));
        queue(() -> FactoryScopeUI.inspect(target));
        queue(() -> checkFits(width + "x" + height + " @ " + scale + "x"));
    }

    void restoreLayout(){
        Scl.setProduct(1f);
        Core.scene.resize(Core.graphics.getWidth(), Core.graphics.getHeight());
    }

    void checkFits(String label){
        Dialog dialog = Core.scene.getDialog();
        if(dialog == null){
            check(label + ": a dialog is on screen", false);
            return;
        }
        float sceneWidth = Core.scene.getWidth();
        float sceneHeight = Core.scene.getHeight();
        Log.info(TAG + " @ scene @x@ dialog @x@ at @,@", label, (int)sceneWidth, (int)sceneHeight,
            (int)dialog.getWidth(), (int)dialog.getHeight(), (int)dialog.x, (int)dialog.y);

        check(label + ": dialog stays within the scene",
            dialog.getWidth() <= sceneWidth + 1f && dialog.getHeight() <= sceneHeight + 1f
                && dialog.x >= -1f && dialog.y >= -1f,
            (int)dialog.getWidth() + "x" + (int)dialog.getHeight()
                + " at " + (int)dialog.x + "," + (int)dialog.y);

        ScrollPane pane = findPane(dialog);
        check(label + ": content is scrollable", pane != null && pane.getHeight() > 0f);
    }

    void checkLocalization(){
        scenarioNow("every user-facing string resolves");
        String[] keys = {"section.status", "status.active", "status.missing-item-input", "value.ok",
            "value.missing", "label.satisfaction", "panel.rate-note", "inspect.hint"};
        for(String key : keys){
            String text = FsBundle.get(key);
            check("'" + key + "' resolves", !text.startsWith(FsBundle.PREFIX) && !text.contains("???"), text);
        }
        Log.info(TAG + " locale @ -> status.active = '@'", Core.bundle.getLocale(), FsBundle.get("status.active"));

        //format() resolves through I18NBundle.get(), which renders an absent key as ???key???
        String absent = FsBundle.format("definitely.not.a.key", 1);
        check("an absent key never leaks ??? into the panel", !absent.contains("???"), absent);
    }


    // ------------------------------------------------------------------ area diagnostics

    /**
     * Every area scenario is built in the same patch of world, cleared before each one.
     *
     * <p>It sits beside the core rather than on it - the core cannot be removed - and close enough to
     * the camera that both corners of any drag are on screen at the zoom the scenarios set.
     */
    int rx(){
        return areaOriginCaptured ? areaOriginX : tileX() + 5;
    }

    void checkToggleFollowsHudLayout(String label){
        Element button = Core.scene.find("factoryscope-toggle");
        Element slot = Core.scene.find("factoryscope");
        Element anchor = ui.hudGroup.find("waves/editor");
        if(button == null || slot == null || anchor == null){
            check("HUD toggle and vanilla layout anchor exist at " + label, false,
                "toggle=" + button + " slot=" + slot + " anchor=" + anchor);
            return;
        }
        Vec2 buttonBottomLeft = button.localToStageCoordinates(new Vec2(0f, 0f));
        Vec2 anchorBottomLeft = anchor.localToStageCoordinates(new Vec2(0f, 0f));
        float xError = Math.abs(buttonBottomLeft.x - anchorBottomLeft.x);
        float yError = Math.abs(buttonBottomLeft.y + button.getHeight() - anchorBottomLeft.y);
        check("HUD toggle remains visible and aligned below the layout anchor at " + label,
            button.visible && slot.color.a == 1f && slot.touchable == arc.scene.event.Touchable.enabled
                && xError < 1f && yError < 1f,
            "toggle=" + buttonBottomLeft + " size=" + button.getWidth() + "x" + button.getHeight()
                + " anchor=" + anchorBottomLeft + " size=" + anchor.getWidth() + "x" + anchor.getHeight());
    }

    int ry(){
        return areaOriginCaptured ? areaOriginY : tileY() - 7;
    }

    static final int REGION_WIDTH = 16;
    static final int REGION_HEIGHT = 14;

    void areaScenarios(){
        scenario("area selection runs against a camera that is nowhere near the world origin");
        queue(() -> {
            //zoomed out far enough that the whole work region fits on screen at any window size
            renderer.targetscale = renderer.camerascale = 1.5f;
        });
        queue(() -> {
            check("the camera is far from the world origin",
                Core.camera.position.dst(0f, 0f) > 200f,
                "camera at " + (int)Core.camera.position.x + "," + (int)Core.camera.position.y);
            check("the whole work region is on screen",
                onScreen(rx(), ry()) && onScreen(rx() + REGION_WIDTH, ry() + REGION_HEIGHT),
                "camera " + (int)Core.camera.width + "x" + (int)Core.camera.height
                    + " scale " + renderer.camerascale);
        });

        dragDirections();
        multiTileEdge();
        singleClickStillInspects();
        singlePanelSupplyTrace();
        traceCompletenessScenarios();
        outputTraceBoundary();
        outputTraceDeadEnd();
        tinyDragIsAClick();
        mixedProblems();
        itemNetworkView();
        powerScopeScenarios();
        liquidScopeScenarios();
        healthyArea();
        emptyArea();
        configurableBlocks();
        refreshAfterAChange();
        drillDown();
        locateAndReturn();
        buildingDetailKeepsTheReport();
        secondaryButtonCancels();
        dragThresholdTracksUiScale();
        singleTileDragIsAClick();
        wallsOnlyArea();
        showMore();
        zoomLevels();
        offWorldDrag();
        areaLayout(1280, 720, 1f);
        areaLayout(1920, 1080, 1f);
        areaLayout(2560, 1440, 1.5f);
        crowdedReport(1280, 720, 2f);
        crowdedAtUiScale(1f);
        crowdedAtUiScale(2f);
        queue(this::restoreLayout);
        queue(this::checkAreaLocalization);
        queue(this::checkTraceLocalization);
        queue(this::checkPowerLocalization);
        repeatedAreaUse(6);
        areaWorldChange();
    }

    /**
     * The four-direction regression, and the strongest statement this suite makes about coordinates:
     * the tile rectangle FactoryScope reports must be exactly the one the pointer covered.
     *
     * <p>A mirrored Y axis, a camera offset dropped somewhere in the conversion, or a viewport read at
     * the wrong scale all move the reported rectangle somewhere else entirely, and the camera sits far
     * from the world origin so none of those errors can pass by accident. The two buildings just
     * outside the rectangle are there so that a selection that is merely too generous fails too.
     */
    void dragDirections(){
        int x1 = rx() + 1, y1 = ry() + 1, x2 = rx() + 13, y2 = ry() + 10;

        queue(() -> {
            clearRegion();
            patch.clear();
            patch.add(placeAt(Blocks.siliconSmelter, rx() + 3, ry() + 3));
            patch.add(placeAt(Blocks.siliconSmelter, rx() + 11, ry() + 3));
            patch.add(placeAt(Blocks.siliconSmelter, rx() + 3, ry() + 8));
            patch.add(placeAt(Blocks.siliconSmelter, rx() + 11, ry() + 8));
            //decoys just outside the rectangle, one beyond each axis
            placeAt(Blocks.siliconSmelter, rx() + 15, ry() + 3);
            placeAt(Blocks.siliconSmelter, rx() + 3, ry() + 13);
            bounds.clear();
            members.clear();
        });
        queue(() -> check("the four-corner patch was built", standing(patch) == 4,
            "standing " + standing(patch)));

        dragDirection("bottom-left to top-right", x1, y1, x2, y2);
        dragDirection("top-right to bottom-left", x2, y2, x1, y1);
        dragDirection("top-left to bottom-right", x1, y2, x2, y1);
        dragDirection("bottom-right to top-left", x2, y1, x1, y2);

        queue(() -> {
            check("all four drag directions normalized to the same bounds",
                bounds.size == 4 && bounds.count(b -> b.equals(bounds.first())) == 4, bounds.toString());
            check("all four drag directions selected the same buildings",
                members.size == 4 && members.count(m -> m.equals(members.first())) == 4, members.toString());
        });
    }

    void dragDirection(String label, int fromX, int fromY, int toX, int toY){
        scenario("dragging " + label + " selects the intended tiles");
        queue(this::closeAnyDialog);
        queue(this::armPicker);
        queue(() -> dragTiles(fromX, fromY, toX, toY));
        queue(() -> {
            AreaSelection expected = AreaSelection.of(fromX, fromY, toX, toY);
            AreaSelection got = FactoryScopeUI.areaBounds();
            AreaDiagnosticResult report = FactoryScopeUI.areaReport();

            check(label + ": an area report opened", got != null && report != null);
            if(got == null || report == null) return;

            check(label + ": the reported tiles are the tiles that were dragged", expected.equals(got),
                "wanted " + expected + " got " + got);
            check(label + ": the four buildings inside were selected, and only those",
                report.summary.analyzed == 4, "analysed " + report.summary.analyzed);
            check(label + ": the picker released the pointer", !FactoryScopeUI.picking());

            bounds.add(got);
            members.add(membersOf(report));
        });
    }

    /** A block whose footprint only clips the edge of the selection belongs to it, and only once. */
    void multiTileEdge(){
        int bx = rx() + 10, by = ry() + 5;

        scenario("a multi-tile building clipped by the edge appears exactly once");
        queue(this::closeAnyDialog);
        queue(() -> {
            clearRegion();
            //3x3, so it covers bx-1 .. bx+1
            target = placeAt(Blocks.surgeSmelter, bx, by);
        });
        queue(this::armPicker);
        queue(() -> dragTiles(rx() + 2, by - 1, bx - 1, by + 1));
        queue(() -> {
            AreaDiagnosticResult report = FactoryScopeUI.areaReport();
            check("the clipped 3x3 building was found", report != null && report.summary.analyzed == 1,
                report == null ? "no report" : "analysed " + report.summary.analyzed);
            if(report != null && report.summary.analyzed == 1){
                check("it was listed once, not once per occupied tile", report.entries.size() == 1);
                check("it is the block that was placed",
                    report.entries.get(0).ref.blockId.equals(Blocks.surgeSmelter.name));
            }
        });

        scenario("a selection that stops one tile short of a footprint excludes it");
        queue(this::closeAnyDialog);
        queue(this::armPicker);
        queue(() -> dragTiles(rx() + 2, by - 1, bx - 2, by + 1));
        queue(() -> {
            AreaDiagnosticResult report = FactoryScopeUI.areaReport();
            check("nothing was selected", report != null && report.summary.analyzed == 0,
                report == null ? "no report" : "analysed " + report.summary.analyzed);
        });
    }

    /** The 0.1.x behaviour has to survive: a plain click is still a plain click. */
    void singleClickStillInspects(){
        scenario("a plain click still opens the single-building panel");
        queue(this::closeAnyDialog);
        queue(() -> {
            clearRegion();
            target = placeAt(Blocks.siliconSmelter, rx() + 4, ry() + 4);
        });
        queue(this::armPicker);
        queue(() -> clickBuilding(target));
        queue(() -> {
            check("the single-building panel opened", FactoryScopeUI.inspected() == target);
            check("no area report was opened by a click", FactoryScopeUI.areaBounds() == null);
        });
    }

    void singlePanelSupplyTrace(){
        scenario("a missing item can be traced from single-building diagnostics");
        queue(this::closeAnyDialog);
        queue(() -> {
            clearRegion();
            for(int x = rx() + 2; x <= rx() + 3; x++){
                for(int y = ry() + 3; y <= ry() + 4; y++){
                    Tile tile = world.tile(x, y);
                    tile.setFloor((Floor)Blocks.sand);
                    tile.clearOverlay();
                }
            }
            producer = placeAt(Blocks.mechanicalDrill, rx() + 2, ry() + 3);
            placeAt(Blocks.conveyor, rx() + 4, ry() + 3);
            placeAt(Blocks.conveyor, rx() + 5, ry() + 3);
            placeAt(Blocks.conveyor, rx() + 6, ry() + 3);
            target = placeAt(Blocks.siliconSmelter, rx() + 7, ry() + 3);

            for(int x = rx() + 2; x <= rx() + 3; x++){
                for(int y = ry() + 5; y <= ry() + 6; y++){
                    Tile tile = world.tile(x, y);
                    tile.setFloor((Floor)Blocks.sand);
                    tile.clearOverlay();
                }
            }
            disabledProducer = placeAt(Blocks.mechanicalDrill, rx() + 2, ry() + 5);
            disabledProducer.enabled = false;
            placeAt(Blocks.conveyor, rx() + 4, ry() + 5);
            placeAt(Blocks.conveyor, rx() + 5, ry() + 5);
            disabledRouteBreak = placeAt(Blocks.conveyor, rx() + 6, ry() + 5);
            placeAt(Blocks.conveyor, rx() + 7, ry() + 5, 3);
        });
        queue(this::armPicker);
        queue(() -> clickBuilding(target));
        queue(() -> {
            check("a missing item offers Supply Trace", Core.scene.find("factoryscope-trace-input-sand") != null);
            check("single-building diagnostics remains open", FactoryScopeUI.inspected() == target);
        });
        queue(() -> clickNamed("factoryscope-trace-input-sand"));
        queue(() -> {
            check("Trace asks for an area when none is active", FactoryScopeUI.picking());
            check("the single-building panel leaves room for the area selection", FactoryScopeUI.inspected() == null);
        });
        queue(() -> dragTiles(rx() + 2, ry() + 2, rx() + 7, ry() + 7));
        queue(() -> {
            check("the Supply Trace opens after area selection", Core.scene.find("factoryscope-trace-back") != null);
            check("the trace keeps the analyzed area available", FactoryScopeUI.areaReportHeld()
                && FactoryScopeUI.areaReport() != null && FactoryScopeUI.areaReport().network != null);
            AreaDiagnosticResult report = FactoryScopeUI.areaReport();
            ResourceRef sand = new ResourceRef(ResourceKind.item, "sand", "Sand");
            AreaEntry drillEntry = report == null ? null : report.entries.stream()
                .filter(entry -> entry.ref.equals(AreaProbe.refOf(producer))).findFirst().orElse(null);
            check("the area snapshot recognizes the drill's mined item",
                drillEntry != null && drillEntry.snapshot != null && drillEntry.snapshot.producedItems.contains(sand),
                drillEntry == null || drillEntry.snapshot == null ? "no drill snapshot"
                    : drillEntry.snapshot.producedItems.toString());
            SupplyTrace trace = report == null ? null : TraceAnalyzer.input(report, AreaProbe.refOf(target), sand);
            check("the area topology reaches the drill for Sand",
                trace != null && trace.producers().stream().anyMatch(endpoint -> endpoint.building.equals(AreaProbe.refOf(producer))),
                trace == null ? "no trace" : "complete=" + trace.complete + " targetUsesItem=" + trace.targetUsesItem
                    + " diagnosticsIncomplete=" + trace.diagnosticsIncomplete + " edges=" + report.network.graph.edges.size());
            check("each reachable Sand producer appears once",
                trace != null && trace.producers().size() == 2
                    && trace.producers().stream().map(endpoint -> endpoint.building).distinct().count() == 2,
                trace == null ? "no trace" : trace.producers().stream().map(endpoint -> endpoint.building.toString()).toList().toString());
            TraceEndpoint disabled = trace == null ? null : trace.producers().stream()
                .filter(endpoint -> endpoint.building.equals(AreaProbe.refOf(disabledProducer))).findFirst().orElse(null);
            check("the disabled reachable producer retains its diagnostic state",
                disabled != null && disabled.diagnostic != null && disabled.diagnostic.reason() == DiagnosticReason.disabled);
            check("the real drill is listed as a reachable Sand producer",
                Core.scene.find("factoryscope-trace-endpoint-" + producer.tile.x + "-" + producer.tile.y) != null);
            check("the disabled producer is also listed in the trace",
                Core.scene.find("factoryscope-trace-endpoint-" + disabledProducer.tile.x + "-" + disabledProducer.tile.y) != null);
            TraceEndpoint active = trace == null ? null : trace.producers().stream()
                .filter(endpoint -> endpoint.building.equals(AreaProbe.refOf(producer))).findFirst().orElse(null);
            check("the operating source retains its diagnostic state",
                active != null && active.diagnostic != null && active.diagnostic.reason() != DiagnosticReason.disabled);
            check("the trace displays that producer's diagnostic label",
                active != null && active.diagnostic != null
                    && dialogShows(AreaText.status(AreaStatus.of(active.diagnostic.reason()))));
            check("the trace annotates a disabled reachable producer",
                dialogShows(FsBundle.get("area.status.disabled")));
            check("the reachable producer can be inspected",
                Core.scene.find("factoryscope-trace-inspect") != null);
        });
        queue(() -> capture("supply-trace-producer"));
        queue(() -> {
            disabledRouteBreak.tile.remove();
            check("the selected route segment was removed before Refresh", disabledRouteBreak.tile.build == null);
        });
        queue(() -> {
            AreaDiagnosticResult beforeRefresh = FactoryScopeUI.areaReport();
            traceSnapshotBeforeRefresh = beforeRefresh;
            SupplyTrace trace = beforeRefresh == null ? null
                : TraceAnalyzer.input(beforeRefresh, AreaProbe.refOf(target), new ResourceRef(ResourceKind.item, "sand", "Sand"));
            check("the open trace remains a snapshot until Refresh", trace != null && trace.producers().size() == 2,
                trace == null ? "no snapshot" : trace.producers().stream().map(endpoint -> endpoint.building.toString()).toList().toString());
        });
        queue(() -> clickNamed("factoryscope-network-refresh"));
        queue(() -> {
            AreaDiagnosticResult refreshed = FactoryScopeUI.areaReport();
            check("Refresh replaces the area report snapshot", refreshed != traceSnapshotBeforeRefresh,
                refreshed == null ? "report missing" : "same report instance");
            SupplyTrace trace = refreshed == null ? null
                : TraceAnalyzer.input(refreshed, AreaProbe.refOf(target), new ResourceRef(ResourceKind.item, "sand", "Sand"));
            check("Refresh rebuilds the trace after a route is removed", trace != null && trace.producers().size() == 1,
                trace == null ? "no refreshed trace" : trace.producers().stream()
                    .map(endpoint -> endpoint.building + " path=" + endpoint.path.ports().stream()
                        .map(port -> port.building + "/" + port.side + "/" + port.channel).toList()).toList().toString());
            check("Refresh removes the no-longer-reachable producer row",
                Core.scene.find("factoryscope-trace-endpoint-" + disabledProducer.tile.x + "-" + disabledProducer.tile.y) == null);
        });
        queue(() -> {
            Scl.setProduct(2f);
            Core.scene.resize(1280, 720);
        });
        queue(() -> {
            ScrollPane pane = findPane(Core.scene.getDialog());
            if(pane != null){
                pane.setScrollPercentY(0f);
                pane.updateVisualScroll();
            }
            checkFits("Supply Trace at 1280x720 @ 2.0x");
            Element endpoint = Core.scene.find("factoryscope-trace-endpoint-" + producer.tile.x + "-" + producer.tile.y);
            float minimumRowWidth = (Core.scene.getWidth() / Scl.scl() - 40f) * 0.85f;
            check("Supply Trace uses the available width at 2.0x",
                endpoint != null && endpoint.getWidth() >= minimumRowWidth,
                endpoint == null ? "producer row missing" : "row width " + endpoint.getWidth() + ", expected at least " + minimumRowWidth);
        });
        queue(() -> Core.scene.resize(Core.graphics.getWidth(), Core.graphics.getHeight()));
        queue(() -> capture("supply-trace-high-ui-scale"));
        queue(this::restoreLayout);
        queue(() -> clickNamed("factoryscope-trace-inspect"));
        queue(() -> check("Inspect opens diagnostics for the reachable drill", FactoryScopeUI.inspected() == producer));
        queue(this::closeAnyDialog);
        queue(() -> check("closing producer diagnostics returns to the same trace",
            Core.scene.find("factoryscope-trace-back") != null));
        queue(() -> clickNamed("factoryscope-trace-back"));
        queue(() -> check("return from Supply Trace restores the Network view",
            Core.scene.find("factoryscope-network-dialog") != null));
        queue(this::closeAnyDialog);
    }

    void traceCompletenessScenarios(){
        int x = rx() + 7, boundaryX = rx() + 12, unsupportedX = rx() + 5, y = ry() + 3;
        ResourceRef sand = new ResourceRef(ResourceKind.item, "sand", "Sand");

        scenario("Supply Trace proves no route only for a complete isolated target");
        queue(this::closeAnyDialog);
        queue(this::closeAnyDialog);
        queue(() -> {
            clearRegion();
            target = placeAt(Blocks.siliconSmelter, x, y);
        });
        queue(this::armPicker);
        queue(() -> clickBuilding(target));
        queue(() -> check("the isolated target offers an item trace",
            Core.scene.find("factoryscope-trace-input-sand") != null));
        queue(() -> clickNamed("factoryscope-trace-input-sand"));
        queue(() -> delayNextAction(30f));
        queue(() -> dragTiles(x, y, x + 1, y + 1));
        queue(() -> {
            AreaDiagnosticResult report = FactoryScopeUI.areaReport();
            SupplyTrace trace = report == null ? null : TraceAnalyzer.input(report, AreaProbe.refOf(target), sand);
            check("a complete isolated input proves no structural route", trace != null && trace.noRouteProven);
            check("the UI reports no route", dialogShows(FsBundle.get("trace.no-route")));
            check("the isolated target has no boundary continuation", trace != null && trace.boundaryContinuations.isEmpty());
        });
        queue(() -> capture("supply-trace-no-route"));
        queue(this::closeAnyDialog);

        scenario("Supply Trace preserves an incoming area boundary");
        queue(this::closeAnyDialog);
        queue(this::closeAnyDialog);
        queue(() -> {
            clearRegion();
            placeAt(Blocks.conveyor, boundaryX - 1, y, 0);
            target = placeAt(Blocks.siliconSmelter, boundaryX, y);
        });
        queue(this::armPicker);
        queue(() -> clickBuilding(target));
        queue(() -> clickNamed("factoryscope-trace-input-sand"));
        queue(() -> delayNextAction(30f));
        queue(() -> dragTiles(boundaryX, y, boundaryX + 1, y + 1));
        queue(() -> {
            AreaDiagnosticResult report = FactoryScopeUI.areaReport();
            SupplyTrace trace = report == null ? null : TraceAnalyzer.input(report, AreaProbe.refOf(target), sand);
            check("the route is marked as continuing outside the selection",
                trace != null && !trace.boundaryContinuations.isEmpty()
                    && dialogShows(FsBundle.format("trace.boundary-one", trace.boundaryContinuations.size())));
            check("a boundary is not shown as no route", trace != null && !trace.noRouteProven
                && !dialogShows(FsBundle.get("trace.no-route")));
            check("the boundary is not reported as a structural dead end", trace != null && trace.structuralDeadEnds.isEmpty());
        });
        queue(() -> capture("supply-trace-boundary"));
        queue(this::closeAnyDialog);

        scenario("Supply Trace marks an unsupported transport interruption");
        queue(this::closeAnyDialog);
        queue(this::closeAnyDialog);
        queue(() -> {
            clearRegion();
            placeAt(Blocks.armoredConveyor, unsupportedX - 1, y, 1);
            target = placeAt(Blocks.siliconSmelter, unsupportedX, y);
            check("unsupported-route fixture keeps both buildings", target != null
                && world.tile(unsupportedX - 1, y).build != null
                && world.tile(unsupportedX - 1, y).build.block == Blocks.armoredConveyor,
                "transport=" + world.tile(unsupportedX - 1, y).build + ", target=" + target);
        });
        queue(this::armPicker);
        queue(() -> clickBuilding(target));
        queue(() -> clickNamed("factoryscope-trace-input-sand"));
        queue(() -> delayNextAction(30f));
        queue(() -> dragTiles(unsupportedX - 1, y, unsupportedX + 1, y + 1));
        queue(() -> {
            AreaDiagnosticResult report = FactoryScopeUI.areaReport();
            SupplyTrace trace = report == null ? null : TraceAnalyzer.input(report, AreaProbe.refOf(target), sand);
            check("the unsupported transport makes the trace incomplete",
                trace != null && !trace.complete && !trace.unsupportedInterruptions.isEmpty(),
                trace == null ? "no trace; picker=" + FactoryScopeUI.picking() + ", report=" + (report != null)
                    : "complete=" + trace.complete + ", unsupported=" + trace.unsupportedInterruptions.size()
                        + ", bounds=" + (report == null ? "none" : report.selection));
            check("the UI reports unsupported topology", dialogShows(FsBundle.get("trace.unsupported-area-one")));
            check("unsupported topology is not shown as a dead end or no route", trace != null
                && !trace.noRouteProven && trace.structuralDeadEnds.isEmpty()
                && !dialogShows(FsBundle.get("trace.no-route")));
        });
        queue(() -> capture("supply-trace-unsupported"));
        queue(this::closeAnyDialog);
    }

    void unsupportedTraceLocate(){
        int x = rx() + 5, y = ry() + 4;
        scenario("an unsupported transport interruption can be located and returned to");
        queue(this::closeAnyDialog);
        queue(() -> {
            clearRegion();
            placeAt(Blocks.armoredConveyor, x - 1, y, 1);
            target = placeAt(Blocks.siliconSmelter, x, y);
        });
        queue(this::armPicker);
        queue(() -> clickBuilding(target));
        queue(() -> clickNamed("factoryscope-trace-input-sand"));
        queue(() -> delayNextAction(30f));
        queue(() -> dragTiles(x - 1, y, x + 1, y + 1));
        queue(() -> check("the unsupported route opens Supply Trace",
            Core.scene.find("factoryscope-trace-unsupported-locate") != null));
        queue(() -> {
            if(Core.scene.find("factoryscope-trace-unsupported-locate") != null){
                clickNamed("factoryscope-trace-unsupported-locate");
            }
        });
        queue(() -> check("Locate marks the unsupported transport and keeps a return path",
            FactoryScopeUI.locating() && Core.scene.find("factoryscope-locate-return") != null));
        queue(() -> {
            if(Core.scene.find("factoryscope-locate-return") != null) clickNamed("factoryscope-locate-return");
        });
        queue(() -> check("return from the unsupported transport restores its trace",
            !FactoryScopeUI.locating() && Core.scene.find("factoryscope-trace-back") != null));
        queue(this::closeAnyDialog);
    }

    void outputTraceBoundary(){
        int x = rx() + 2, y = ry() + 2;
        scenario("an output trace describes an outside continuation as having no in-area consumer");
        queue(this::closeAnyDialog);
        queue(() -> {
            clearRegion();
            target = placeAt(Blocks.graphitePress, x, y);
            placeAt(Blocks.conveyor, x + 2, y);
            placeAt(Blocks.conveyor, x + 3, y);
        });
        queue(this::armPicker);
        queue(() -> dragTiles(x - 1, y - 1, x + 2, y + 2));
        queue(() -> check("the output-trace selection produced an area report", FactoryScopeUI.areaReport() != null));
        queue(() -> clickNamed("factoryscope-area-network"));
        queue(() -> check("the produced item is available as a Network filter",
            Core.scene.find("factoryscope-network-item-graphite") != null));
        queue(() -> clickNamed("factoryscope-network-item-graphite"));
        queue(() -> clickNamed("factoryscope-network-building", 0));
        queue(() -> check("the producer exposes an output trace action",
            Core.scene.find("factoryscope-network-trace-output") != null));
        queue(() -> clickNamed("factoryscope-network-trace-output"));
        queue(() -> {
            AreaDiagnosticResult report = FactoryScopeUI.areaReport();
            SupplyTrace trace = report == null ? null : TraceAnalyzer.output(report, AreaProbe.refOf(target),
                new ResourceRef(ResourceKind.item, "graphite", "Graphite"));
            check("the structural output path reaches the area boundary",
                trace != null && !trace.boundaryContinuations.isEmpty() && trace.endpoints.isEmpty());
            check("the boundary uses consumer wording for an output trace",
                dialogShows(FsBundle.get("trace.no-in-area-consumer")));
            check("the boundary does not claim there is no producer",
                !dialogShows(FsBundle.get("trace.no-in-area-producer")));
        });
        queue(() -> capture("supply-trace-output-boundary"));
        queue(this::closeAnyDialog);
    }

    void outputTraceDeadEnd(){
        int x = rx() + 2, y = ry() + 2;
        scenario("an output trace distinguishes a known dead end from a missing route");
        queue(this::closeAnyDialog);
        queue(() -> {
            clearRegion();
            target = placeAt(Blocks.graphitePress, x, y);
            placeAt(Blocks.conveyor, x + 2, y, 0);
        });
        queue(this::armPicker);
        queue(() -> dragTiles(x - 1, y - 1, x + 3, y + 1));
        queue(() -> check("the output dead-end selection produced an area report", FactoryScopeUI.areaReport() != null));
        queue(() -> clickNamed("factoryscope-area-network"));
        queue(() -> clickNamed("factoryscope-network-item-graphite"));
        queue(() -> clickNamed("factoryscope-network-building", 0));
        queue(() -> clickNamed("factoryscope-network-trace-output"));
        queue(() -> {
            AreaDiagnosticResult report = FactoryScopeUI.areaReport();
            SupplyTrace trace = report == null ? null : TraceAnalyzer.output(report, AreaProbe.refOf(target),
                new ResourceRef(ResourceKind.item, "graphite", "Graphite"));
            check("the route ends at the reachable conveyor", trace != null
                && trace.structuralDeadEnds.stream().anyMatch(ref -> ref.blockId.equals("conveyor")));
            check("a known output dead end is not reported as no structural route", trace != null
                && !trace.noRouteProven && !dialogShows(FsBundle.get("trace.no-downstream")));
            check("the output trace describes the missing in-area consumer", dialogShows(FsBundle.get("trace.no-consumer")));
        });
        queue(() -> capture("supply-trace-output-dead-end"));
        queue(this::closeAnyDialog);
    }

    /** A press that wanders a few pixels is a click, not a one-tile area report. */
    void tinyDragIsAClick(){
        scenario("a drag shorter than the threshold is treated as a click");
        queue(this::closeAnyDialog);
        queue(this::armPicker);
        queue(() -> {
            Vec2 screen = Core.camera.project(new Vec2(target.x, target.y));
            int sx = Mathf.round(screen.x), sy = Mathf.round(screen.y);
            Core.scene.touchDown(sx, sy, 0, KeyCode.mouseLeft);
            Core.scene.touchDragged(sx + 2, sy + 1, 0);
            Core.scene.touchDragged(sx + 3, sy + 2, 0);
            Core.scene.touchUp(sx + 3, sy + 2, 0, KeyCode.mouseLeft);
        });
        queue(() -> {
            check("a two-pixel wobble still inspected one building", FactoryScopeUI.inspected() == target);
            check("a two-pixel wobble opened no area report", FactoryScopeUI.areaBounds() == null);
        });
    }

    /**
     * The scenario the feature exists for: one selection over a patch with several different faults,
     * checked against the counts and the grouping the player will read.
     */
    void mixedProblems(){
        int x1 = rx(), y1 = ry(), x2 = rx() + 16, y2 = ry() + 11;

        scenario("a mixed area is summarised and grouped correctly");
        queue(this::closeAnyDialog);
        queue(() -> {
            clearRegion();
            patch.clear();
            //running: coal in the hopper and room for the output
            patch.add(supply(placeAt(Blocks.graphitePress, rx() + 2, ry() + 2), Items.coal, 30));
            //starved of coal
            patch.add(placeAt(Blocks.graphitePress, rx() + 7, ry() + 2));
            //output buffer full, so the block refuses to start another cycle; coal is present, so the
            //blocked output is the only thing wrong with it
            patch.add(supply(placeAt(Blocks.graphitePress, rx() + 12, ry() + 2), Items.graphite, 10));
            supply(patch.peek(), Items.coal, 30);
            //switched off by hand
            patch.add(supply(placeAt(Blocks.graphitePress, rx() + 2, ry() + 7), Items.coal, 30));
            patch.peek().enabled = false;
            //no sand, no coal and no power grid: one building, three separate findings
            patch.add(placeAt(Blocks.siliconSmelter, rx() + 7, ry() + 7));
            //no production model at all
            patch.add(placeAt(Blocks.titaniumWall, rx() + 12, ry() + 7));
        });
        queue(() -> check("the mixed patch was built", standing(patch) == 6, "standing " + standing(patch)));
        queue(this::armPicker);
        queue(() -> dragTiles(x1, y1, x2, y2));
        queue(() -> {
            AreaDiagnosticResult report = FactoryScopeUI.areaReport();
            check("the mixed area opened a report", report != null);
            if(report == null) return;

            check("six buildings were analysed", report.summary.analyzed == 6,
                "analysed " + report.summary.analyzed + " of " + report.summary.selected);
            check("five of them have a production model", report.summary.production == 5,
                "production " + report.summary.production);
            check("one is running", report.summary.operating == 1, "operating " + report.summary.operating);
            check("three are in a problem state", report.summary.problems == 3,
                "problems " + report.summary.problems);
            check("the disabled block and the wall are informational, not faults",
                report.summary.informational == 2, "informational " + report.summary.informational);
            check("every analysed building is in exactly one status bucket",
                report.summary.operating + report.summary.problems + report.summary.informational
                    == report.summary.analyzed);
            Integer limited = report.summary.byStatus.get(AreaStatus.limitedDiagnostics);
            check("the wall is reported as limited diagnostics", limited != null && limited == 1,
                String.valueOf(limited));

            //the smelter is short of sand, short of coal and unpowered: it must appear in three groups
            //and still be counted as one building
            int appearances = 0;
            for(AreaIssueGroup group : report.issues){
                for(BuildingRef ref : group.buildings){
                    if(ref.blockId.equals(Blocks.siliconSmelter.name)) appearances++;
                }
                check("no building is listed twice inside " + group.issue.key(),
                    new java.util.HashSet<>(group.buildings).size() == group.buildings.size());
            }
            check("the building with three findings appears in three groups", appearances == 3,
                "appeared in " + appearances + " groups");

            //coal is missing from both the empty press and the smelter, so it is the biggest group
            AreaIssueGroup first = report.issues.isEmpty() ? null : report.issues.get(0);
            check("the issue affecting the most buildings is listed first",
                first != null && first.buildingCount() == 2, first == null ? "no issues" : first.toString());
            for(int i = 1; i < report.issues.size(); i++){
                check("issue " + i + " is not ranked above the one before it",
                    report.issues.get(i).buildingCount() <= report.issues.get(i - 1).buildingCount());
            }
        });
        queue(() -> capture("mixed-area"));
    }

    void itemNetworkView(){
        int x1 = rx(), y1 = ry(), x2 = rx() + 14, y2 = ry() + 4;

        scenario("the Network view opens through the production area report");
        queue(this::closeAnyDialog);
        queue(() -> {
            clearRegion();
            renderer.targetscale = renderer.camerascale = 3f;
            for(int x = 1; x <= 8; x++) placeAt(Blocks.conveyor, rx() + x, ry() + 2);
            Building sorter = placeAt(Blocks.sorter, rx() + 9, ry() + 2);
            sorter.configure(Items.copper);
            placeAt(Blocks.conveyor, rx() + 10, ry() + 2);
        });
        queue(this::armPicker);
        queue(() -> dragTiles(x1, y1, x2, y2));
        queue(() -> check("the network patch opened an area report", FactoryScopeUI.areaReport() != null));
        queue(() -> clickNamed("factoryscope-area-network"));
        queue(() -> {
            check("the Network view opened", Core.scene.find("factoryscope-network-dialog") != null);
            check("the configured item is offered as a filter", Core.scene.find("factoryscope-network-item-copper") != null);
        });
        queue(() -> clickNamed("factoryscope-network-item-copper"));
        queue(() -> check("the Network view remains open after selecting an item", Core.scene.find("factoryscope-network-dialog") != null));
        queue(() -> clickNamed("factoryscope-network-building"));
        queue(() -> check("a network building opens topology detail", Core.scene.find("factoryscope-network-dialog") != null));
        queue(() -> capture("network-view"));
        queue(() -> clickNamed("factoryscope-network-view-world"));
        queue(() -> delayNextAction(30f));
        queue(() -> {
            check("the static routes can be viewed over the world", Core.scene.find("factoryscope-network-viewing") != null);
            check("the Network view keeps its return control", Core.scene.find("factoryscope-network-return") != null);
        });
        queue(() -> capture("network-world"));
        queue(() -> clickNamed("factoryscope-network-return"));
        queue(() -> delayNextAction(30f));
        queue(() -> check("return restores the Network view", Core.scene.find("factoryscope-network-dialog") != null));
        queue(() -> clickNamed("factoryscope-network-view-world"));
        queue(() -> delayNextAction(30f));
        queue(() -> clickNamed("factoryscope-network-dismiss"));
        queue(() -> delayNextAction(30f));
        queue(() -> {
            check("dismissing the world overlay removes its return control", Core.scene.find("factoryscope-network-viewing") == null);
            check("dismissing the world overlay drops the held report", !FactoryScopeUI.areaReportHeld());
        });
        queue(() -> renderer.targetscale = renderer.camerascale = 1.5f);
    }

    void powerScopeScenarios(){
        scenario("a single-building diagnostic opens its complete PowerGraph snapshot");
        queue(this::closeAnyDialog);
        queue(() -> {
            clearRegion();
            target = placeAt(Blocks.solarPanel, rx() + 5, ry() + 5);
        });
        queue(this::armPicker);
        queue(() -> clickBuilding(target));
        queue(() -> check("a power-capable building offers grid inspection",
            Core.scene.find("factoryscope-inspect-power-grid") != null));
        queue(() -> clickNamed("factoryscope-inspect-power-grid"));
        queue(() -> {
            PowerGridReport report = FactoryScopeUI.powerReport();
            check("single-building PowerScope opened", Core.scene.find("factoryscope-power-dialog") != null);
            check("PowerScope states that this building view reports the complete engine grid",
                dialogShows(FsBundle.get("power.scope-note")));
            check("the selected generator belongs to one engine grid", report != null && report.grids.size() == 1,
                report == null ? "no report" : "grids " + report.grids.size());
            check("the generator is discovered by PowerGraph membership",
                report != null && report.grids.get(0).snapshot.producers.stream()
                    .anyMatch(member -> member.ref.equals(AreaProbe.refOf(target))));
        });
        queue(() -> clickNamed("factoryscope-power-refresh"));
        queue(() -> check("PowerScope refresh keeps a valid snapshot",
            FactoryScopeUI.powerReport() != null && FactoryScopeUI.powerReport().grids.size() == 1));
        queue(() -> capture("power-single-building"));
        queue(() -> clickNamed("factoryscope-power-back"));
        queue(() -> check("single-building PowerScope Return restores its inspector",
            FactoryScopeUI.inspected() == target && FactoryScopeUI.powerReport() == null));
        scenario("PowerScope never renders cheat-rule placeholders as measured zeroes");
        queue(() -> {
            state.rules.teams.get(Team.sharded).cheat = true;
            Building consumer = placeAt(Blocks.siliconSmelter, target.tileX() + 1, target.tileY());
            if(consumer != null) consumer.updateConsumption();
        });
        queue(() -> clickNamed("factoryscope-inspect-power-grid"));
        queue(() -> {
            PowerGridReport report = FactoryScopeUI.powerReport();
            PowerGridResult result = report == null || report.grids.isEmpty() ? null : report.grids.get(0);
            check("team cheat power is marked and aggregate metrics are unavailable",
                result != null && result.state == PowerGridState.cheatPowered && !result.snapshot.hasMetrics);
            check("unavailable grid values are not rendered as generation or demand zeroes",
                dialogShows(FsBundle.get("power.metrics-unavailable"))
                    && !dialogShows(FsBundle.get("power.generation"))
                    && !dialogShows(FsBundle.get("power.demand")));
        });
        queue(() -> clickNamed("factoryscope-power-back"));
        queue(() -> state.rules.teams.get(Team.sharded).cheat = false);
        queue(this::closeAnyDialog);

        scenario("PowerScope separates a battery-supported generation deficit from an underpowered grid");
        int batteryX = rx(), batteryY = ry(), batteryX2 = batteryX + 14, batteryY2 = batteryY + 14;
        Building[] supportedGrid = new Building[4];
        mindustry.world.blocks.power.PowerGraph[] supportedGraph = new mindustry.world.blocks.power.PowerGraph[1];
        queue(() -> {
            clearRegion();
            supportedGrid[0] = placeAt(Blocks.solarPanel, batteryX + 3, batteryY + 7);
            supportedGrid[1] = placeAt(Blocks.batteryLarge, batteryX + 7, batteryY + 4);
            supportedGrid[2] = placeAt(Blocks.siliconSmelter, batteryX + 11, batteryY + 7);
            supportedGrid[3] = placeAt(Blocks.combustionGenerator, batteryX + 7, batteryY + 11);
            Building node = placeAt(Blocks.powerNodeLarge, batteryX + 7, batteryY + 7);
            if(supportedGrid[2] != null){
                supportedGrid[2].items.add(Items.sand, 30);
                supportedGrid[2].items.add(Items.coal, 30);
                supportedGrid[2].updateConsumption();
            }
            if(supportedGrid[3] != null) supportedGrid[3].updateConsumption();
            if(node != null){
                for(Building endpoint : supportedGrid){
                    if(endpoint != null) node.configureAny(endpoint.pos());
                }
            }
            if(supportedGrid[1] != null) supportedGrid[1].power.status = 1f;
            supportedGraph[0] = supportedGrid[0] == null ? null : supportedGrid[0].power.graph;
            if(supportedGraph[0] != null) supportedGraph[0].update();
            boolean sameGraph = node != null && supportedGraph[0] != null
                && node.power.graph == supportedGraph[0]
                && supportedGrid[0] != null && supportedGrid[1] != null
                && supportedGrid[2] != null && supportedGrid[3] != null
                && supportedGrid[0].power.graph == supportedGraph[0]
                && supportedGrid[1].power.graph == supportedGraph[0]
                && supportedGrid[2].power.graph == supportedGraph[0]
                && supportedGrid[3].power.graph == supportedGraph[0];
            check("the real engine grid has a generation deficit covered by stored battery power",
                sameGraph && supportedGraph[0].getPowerProduced() < supportedGraph[0].getPowerNeeded()
                    && supportedGraph[0].getSatisfaction() >= 0.999f
                    && supportedGrid[1].power.status > 0f,
                "sameGraph=" + sameGraph + ", produced=" + (supportedGraph[0] == null ? -1f : supportedGraph[0].getPowerProduced())
                    + ", needed=" + (supportedGraph[0] == null ? -1f : supportedGraph[0].getPowerNeeded())
                    + ", satisfaction=" + (supportedGraph[0] == null ? -1f : supportedGraph[0].getSatisfaction())
                    + ", batteryStatus=" + (supportedGrid[1] == null ? -1f : supportedGrid[1].power.status)
                    + ", members=" + (supportedGraph[0] == null ? -1 : supportedGraph[0].all.size)
                    + ", producers=" + (supportedGraph[0] == null ? -1 : supportedGraph[0].producers.size)
                    + ", consumers=" + (supportedGraph[0] == null ? -1 : supportedGraph[0].consumers.size)
                    + ", batteries=" + (supportedGraph[0] == null ? -1 : supportedGraph[0].batteries.size)
                    + ", nodeLinks=" + (node == null ? -1 : node.power.links.size)
                    + ", graphMembers=" + (supportedGraph[0] == null ? "none" : supportedGraph[0].all.toString())
                    + ", placed=" + java.util.Arrays.stream(supportedGrid).map(build -> build == null ? "null"
                        : build.block.name + "@" + build.tileX() + "," + build.tileY() + "/power=" + (build.power != null)
                            + "/same=" + (build.power != null && build.power.graph == supportedGraph[0])
                            + "/connections=" + (build.power == null ? 0 : build.getPowerConnections(new arc.struct.Seq<>()).size))
                        .collect(java.util.stream.Collectors.joining("; ")));
        });
        queue(this::armPicker);
        queue(() -> dragTiles(batteryX + 1, batteryY + 1, batteryX2, batteryY2));
        queue(() -> check("the battery-supported area exposes PowerScope",
            FactoryScopeUI.areaReport() != null && Core.scene.find("factoryscope-area-power") != null));
        queue(() -> {
            // Refill immediately before taking the snapshot so the UI assertion tests the
            // battery-covered state rather than how long the preceding real-client steps took.
            supportedGrid[1].power.status = 1f;
            supportedGraph[0].update();
            clickNamed("factoryscope-area-power");
        });
        queue(() -> {
            PowerGridReport report = FactoryScopeUI.powerReport();
            PowerGridResult result = report == null || report.grids.isEmpty() ? null : report.grids.get(0);
            String resultDetails = result == null ? "no PowerGridResult"
                : "state=" + result.state + ", generation=" + result.snapshot.generationPerSecond
                    + ", demand=" + result.snapshot.demandPerSecond + ", satisfaction=" + result.snapshot.satisfaction
                    + ", battery=" + result.snapshot.batteryStored + "/" + result.snapshot.batteryCapacity
                    + ", generatorProblems=" + result.generatorsWithProblems
                    + ", producers=" + result.snapshot.producers.stream().map(member -> member.ref + ":"
                        + (member.diagnostic == null ? "unclassified" : member.diagnostic.reason())).collect(java.util.stream.Collectors.joining(","));
            check("PowerScope distinguishes generation below demand while the grid is satisfied",
                result != null && result.state == PowerGridState.generationBelowDemand
                    && result.snapshot.generationPerSecond < result.snapshot.demandPerSecond
                    && result.snapshot.satisfaction >= 0.999f
                    && result.has(PowerFinding.BATTERY_RESERVES_PRESENT), resultDetails);
            check("the UI explicitly labels the battery-supported deficit",
                dialogShows(FsBundle.get("power.battery-reserves")));
            check("a fuel-starved connected generator retains its FactoryAnalyzer diagnostic",
                result != null && result.generatorsWithProblems == 1
                    && result.snapshot.producers.stream().anyMatch(member -> member.ref.equals(AreaProbe.refOf(supportedGrid[3]))
                        && member.diagnostic != null
                        && member.diagnostic.reason() == DiagnosticReason.missingItemInput), resultDetails);
            check("the grid summary reports a generator problem without assigning a cause",
                result != null && dialogShows(FsBundle.format("power.generator-problems", 1)), resultDetails);
            check("the generator member list is available for navigation",
                Core.scene.find("factoryscope-power-list-toggle") != null);
            capture("power-battery-supported-deficit");
        });
        queue(() -> clickNamed("factoryscope-power-list-toggle"));
        queue(() -> clickNamed("factoryscope-power-locate"));
        queue(() -> check("Locate opens the generator marker while retaining PowerScope state",
            FactoryScopeUI.locating() && Core.scene.find("factoryscope-locate-return") != null));
        queue(() -> clickNamed("factoryscope-locate-return"));
        queue(() -> check("Return restores the same battery-supported grid snapshot",
            FactoryScopeUI.powerReport() != null && FactoryScopeUI.powerReport().grids.size() == 1
                && FactoryScopeUI.powerReport().grids.get(0).state == PowerGridState.generationBelowDemand,
            FactoryScopeUI.powerReport() == null || FactoryScopeUI.powerReport().grids.isEmpty() ? "no report"
                : "state=" + FactoryScopeUI.powerReport().grids.get(0).state));
        queue(this::restoreCamera);
        queue(() -> {
            supportedGrid[1].power.status = 0f;
            supportedGraph[0].update();
        });
        queue(() -> clickNamed("factoryscope-power-refresh"));
        queue(() -> {
            PowerGridReport report = FactoryScopeUI.powerReport();
            PowerGridResult result = report == null || report.grids.isEmpty() ? null : report.grids.get(0);
            check("Refresh reports the now-underpowered grid after its battery is emptied",
                result != null && result.state == PowerGridState.underpowered
                    && result.snapshot.satisfaction < 0.999f
                    && !result.has(PowerFinding.BATTERY_RESERVES_PRESENT));
            check("an empty battery is not described as reserve support",
                result != null && !dialogShows(FsBundle.get("power.battery-reserves")));
            capture("power-underpowered-empty-battery");
        });
        queue(() -> clickNamed("factoryscope-power-back"));
        queue(this::closeAnyDialog);

        scenario("the Area Power view keeps multiple engine grids separate");
        int x1 = rx(), y1 = ry(), x2 = rx() + 40, y2 = ry() + 12;
        Building[] gridProducers = new Building[2];
        Building[] gridConnector = new Building[1];
        //The single-building Power dialog is stacked over its diagnostic panel. Let its close fade
        //finish before dismissing that panel, so the synthetic drag reaches the world picker.
        queue(() -> {});
        queue(() -> {});
        queue(() -> {
            clearRegion();
            gridProducers[0] = placeAt(Blocks.solarPanel, rx() + 2, ry() + 2);
            placeAt(Blocks.battery, rx() + 3, ry() + 2);
            gridProducers[1] = placeAt(Blocks.solarPanel, rx() + 10, ry() + 2);
        });
        queue(this::closeAnyDialog);
        queue(this::armPicker);
        queue(() -> dragTiles(x1, y1, x2, y2));
        queue(() -> check("the area selection opened diagnostics before PowerScope",
            FactoryScopeUI.areaReport() != null && Core.scene.find("factoryscope-area-power") != null));
        queue(() -> clickNamed("factoryscope-area-power"));
        queue(() -> {
            PowerGridReport report = FactoryScopeUI.powerReport();
            check("the area Power view opened", Core.scene.find("factoryscope-power-dialog") != null);
            check("two disconnected engine PowerGraphs remain two reports",
                report != null && report.grids.size() == 2,
                report == null ? "no report" : "grids " + report.grids.stream()
                    .map(grid -> grid.snapshot.members.stream().map(member -> member.ref.toString())
                        .collect(java.util.stream.Collectors.joining(", ")))
                    .collect(java.util.stream.Collectors.joining(" | ")));
            check("the battery remains distinct from generator membership",
                report != null && report.grids.stream().flatMap(grid -> grid.snapshot.batteries.stream())
                    .anyMatch(member -> member.ref.blockId.equals(Blocks.battery.name))
                    && report.grids.stream().flatMap(grid -> grid.snapshot.producers.stream())
                    .noneMatch(member -> member.ref.blockId.equals(Blocks.battery.name)));
            check("the selection scope is explicit for each whole-grid summary",
                report != null && report.grids.stream().allMatch(grid -> grid.snapshot.selectedMemberCount > 0));
        });
        queue(() -> capture("power-area-multiple-grids"));
        queue(() -> {
            Scl.setProduct(2f);
            Core.scene.resize(1280, 720);
        });
        queue(() -> {
            ScrollPane pane = findPane(Core.scene.getDialog());
            if(pane != null){
                pane.setScrollPercentY(0f);
                pane.updateVisualScroll();
            }
            checkFits("PowerScope at 1280x720 @ 2.0x");
        });
        queue(() -> Core.scene.resize(Core.graphics.getWidth(), Core.graphics.getHeight()));
        queue(() -> capture("power-area-high-ui-scale"));
        queue(this::restoreLayout);
        queue(() -> check("PowerScope remains open after high-scale layout validation",
            FactoryScopeUI.powerReport() != null && Core.scene.find("factoryscope-power-dialog") != null));
        queue(() -> clickNamed("factoryscope-power-view-world"));
        queue(() -> check("PowerScope world view marks electrical connections without flow claims",
            Core.scene.find("factoryscope-power-viewing") != null
                && Core.scene.find("factoryscope-power-return") != null));
        queue(() -> clickNamed("factoryscope-power-return"));
        queue(() -> check("return restores the same PowerScope report",
            Core.scene.find("factoryscope-power-dialog") != null
                && FactoryScopeUI.powerReport() != null && FactoryScopeUI.powerReport().grids.size() == 2,
            "dialog=" + (Core.scene.getDialog() == null ? "none" : Core.scene.getDialog().getClass().getSimpleName())
                + ", report=" + (FactoryScopeUI.powerReport() == null ? "none" : FactoryScopeUI.powerReport().grids.size())
                + ", overlay=" + (Core.scene.find("factoryscope-power-viewing") != null)));
        queue(() -> {
            gridConnector[0] = placeAt(Blocks.powerNode, x1 + 6, y1 + 2);
            if(gridConnector[0] != null){
                gridConnector[0].configureAny(gridProducers[0].pos());
                gridConnector[0].configureAny(gridProducers[1].pos());
            }
            check("a newly placed PowerNode merges the two live engine grids",
                gridConnector[0] != null && gridProducers[0].power.graph == gridProducers[1].power.graph
                    && gridConnector[0].power.graph == gridProducers[0].power.graph);
        });
        queue(() -> clickNamed("factoryscope-power-refresh"));
        queue(() -> {
            refreshedPowerSnapshot = FactoryScopeUI.powerReport();
            check("Refresh replaces the stale two-grid snapshot with the merged engine grid",
                FactoryScopeUI.areaReportHeld() && refreshedPowerSnapshot != null
                    && refreshedPowerSnapshot.grids.size() == 1);
        });
        queue(() -> capture("power-area-refresh"));
        queue(() -> clickNamed("factoryscope-power-back"));
        queue(() -> check("PowerScope Return restores the held Area Diagnostics report",
            Core.scene.find("factoryscope-area-power") != null && FactoryScopeUI.areaReport() != null
                && FactoryScopeUI.areaReport().power == refreshedPowerSnapshot
                && FactoryScopeUI.powerReport() == null));
        queue(() -> {
            gridConnector[0].configureAny(gridProducers[0].pos());
            gridConnector[0].configureAny(gridProducers[1].pos());
            check("removing both established PowerNode links splits the live engine grid",
                gridProducers[0].power.graph != gridProducers[1].power.graph);
        });
        queue(() -> clickNamed("factoryscope-area-power"));
        queue(() -> clickNamed("factoryscope-power-refresh"));
        queue(() -> {
            PowerGridReport report = FactoryScopeUI.powerReport();
            long firstProducerGrids = report == null ? 0 : report.grids.stream()
                .filter(grid -> grid.snapshot.members.stream()
                    .anyMatch(member -> member.ref.equals(AreaProbe.refOf(gridProducers[0])))).count();
            long secondProducerGrids = report == null ? 0 : report.grids.stream()
                .filter(grid -> grid.snapshot.members.stream()
                    .anyMatch(member -> member.ref.equals(AreaProbe.refOf(gridProducers[1])))).count();
            long connectorGrids = report == null ? 0 : report.grids.stream()
                .filter(grid -> grid.snapshot.members.stream()
                    .anyMatch(member -> member.ref.equals(AreaProbe.refOf(gridConnector[0])))).count();
            check("Refresh replaces the merged snapshot with both source grids and the isolated PowerNode grid",
                report != null && report.grids.size() == 3 && firstProducerGrids == 1
                    && secondProducerGrids == 1 && connectorGrids == 1,
                "grids=" + (report == null ? "none" : report.grids.size())
                    + ", producer memberships=" + firstProducerGrids + "/" + secondProducerGrids
                    + ", connector memberships=" + connectorGrids);
        });
        queue(() -> clickNamed("factoryscope-power-back"));
        queue(this::closeAnyDialog);

        scenario("PowerScope uses the BeamNode connection maintained by Mindustry");
        int beamX = rx(), beamY = ry();
        Building[] beamFixture = new Building[2];
        queue(() -> {
            clearRegion();
            beamFixture[0] = placeAt(Blocks.solarPanel, beamX + 4, beamY + 5);
            beamFixture[1] = placeAt(Blocks.beamNode, beamX + 5, beamY + 5);
        });
        queue(this::armPicker);
        queue(() -> dragTiles(beamX + 2, beamY + 3, beamX + 7, beamY + 7));
        queue(() -> clickNamed("factoryscope-area-power"));
        queue(() -> {
            PowerGridReport report = FactoryScopeUI.powerReport();
            PowerConnection expected = new PowerConnection(AreaProbe.refOf(beamFixture[0]),
                AreaProbe.refOf(beamFixture[1]));
            check("the real BeamNode and solar panel share Mindustry's established engine grid",
                report != null && report.grids.size() == 1
                    && beamFixture[0].power.graph == beamFixture[1].power.graph
                    && report.grids.get(0).snapshot.connections.contains(expected));
        });
        queue(() -> clickNamed("factoryscope-power-view-world"));
        queue(() -> check("the BeamNode grid can be viewed through the production overlay",
            Core.scene.find("factoryscope-power-viewing") != null
                && Core.scene.find("factoryscope-power-return") != null));
        queue(() -> capture("power-beam-node-world"));
        queue(() -> clickNamed("factoryscope-power-return"));
        queue(() -> clickNamed("factoryscope-power-back"));
        queue(this::closeAnyDialog);

        scenario("PowerScope presents a selected Power Diode as a conditional link between separate grids");
        int diodeX = rx(), diodeY = ry();
        Building[] diodeFixture = new Building[3];
        queue(() -> {
            clearRegion();
            diodeFixture[0] = placeAt(Blocks.battery, diodeX + 4, diodeY + 5);
            diodeFixture[1] = placeAt(Blocks.diode, diodeX + 5, diodeY + 5);
            diodeFixture[2] = placeAt(Blocks.battery, diodeX + 6, diodeY + 5);
        });
        queue(this::armPicker);
        queue(() -> dragTiles(diodeX + 2, diodeY + 3, diodeX + 8, diodeY + 7));
        queue(() -> check("the area selection includes both diode endpoint grids and the diode",
            FactoryScopeUI.areaReport() != null && FactoryScopeUI.areaReport().power != null));
        queue(() -> clickNamed("factoryscope-area-power"));
        queue(() -> {
            PowerGridReport report = FactoryScopeUI.powerReport();
            boolean separate = report != null && report.grids.size() == 2;
            PowerDiodeLink link = report == null || report.diodeLinks.size() != 1
                ? null : report.diodeLinks.get(0);
            boolean direction = link != null && link.fromGrid != link.toGrid
                && report.grids.get(link.fromGrid).snapshot.members.stream()
                    .anyMatch(member -> member.ref.equals(AreaProbe.refOf(diodeFixture[0])))
                && report.grids.get(link.toGrid).snapshot.members.stream()
                    .anyMatch(member -> member.ref.equals(AreaProbe.refOf(diodeFixture[2])));
            check("the Power Diode remains a relation, not a merged PowerGraph",
                separate && diodeFixture[0].power.graph != diodeFixture[2].power.graph
                    && report.grids.stream().noneMatch(grid -> grid.snapshot.members.stream()
                        .anyMatch(member -> member.ref.equals(AreaProbe.refOf(diodeFixture[1])))));
            check("the displayed Power Diode relation follows Mindustry back-to-front direction", direction);
            check("the Power Diode UI explicitly withholds current activity and transfer amount",
                link != null && link.batteryState == PowerDiodeBatteryState.bothEndpointsHaveCapacity
                    && dialogShows(FsBundle.get("power.diodes"))
                    && dialogShows(FsBundle.format("power.diode-link", link.diode.blockName,
                        link.fromGrid + 1, link.toGrid + 1))
                    && dialogShows(FsBundle.get("power.diode-unmeasured")));
        });
        queue(() -> {
            ScrollPane pane = findPane(Core.scene.getDialog());
            if(pane != null){
                pane.setScrollPercentY(1f);
                pane.updateVisualScroll();
            }
        });
        queue(() -> capture("power-diode-cross-grid"));
        queue(() -> clickNamed("factoryscope-power-back"));
        queue(this::closeAnyDialog);

        scenario("world change clears the PowerScope snapshot and electrical overlay");
        int lifecycleX = rx(), lifecycleY = ry();
        queue(() -> {
            clearRegion();
            placeAt(Blocks.solarPanel, lifecycleX + 3, lifecycleY + 3);
        });
        queue(this::armPicker);
        queue(() -> dragTiles(lifecycleX + 1, lifecycleY + 1, lifecycleX + 5, lifecycleY + 5));
        queue(() -> check("the refreshed area report is available for the lifecycle check",
            FactoryScopeUI.areaReport() != null && Core.scene.find("factoryscope-area-power") != null));
        queue(() -> clickNamed("factoryscope-area-power"));
        queue(() -> clickNamed("factoryscope-power-view-world"));
        queue(() -> check("the electrical overlay is active before world change",
            Core.scene.find("factoryscope-power-viewing") != null));
        queue(() -> Events.fire(new WorldLoadEvent()));
        queue(() -> check("world change releases the grid report, area report and overlay",
            FactoryScopeUI.powerReport() == null && FactoryScopeUI.areaReport() == null
                && !FactoryScopeUI.areaReportHeld() && Core.scene.getDialog() == null
                && Core.scene.find("factoryscope-power-viewing") == null));
    }

    void liquidScopeScenarios(){
        scenario("a missing Water input traces through the production UI to a real pump");
        queue(this::closeAnyDialog);
        queue(() -> {
            clearRegion();
            int x = rx(), y = ry();
            for(int dx = 2; dx <= 3; dx++) for(int dy = 2; dy <= 3; dy++){
                Tile floor = world.tile(x + dx, y + dy);
                floor.setFloor((Floor)Blocks.water);
                floor.clearOverlay();
            }
            producer = placeAt(Blocks.mechanicalPump, x + 3, y + 3);
            placeAt(Blocks.conduit, x + 4, y + 3, 0);
            placeAt(Blocks.conduit, x + 5, y + 3, 0);
            //Cryofluid Mixer is 2x2; its west footprint starts at x+6, beside the second conduit.
            target = placeAt(Blocks.cryofluidMixer, x + 6, y + 3);
        });
        queue(() -> {
            //Let the pump resolve its placement-specific liquidDrop, then preserve a real shortage.
            //Drain both line buffers too: stored contents never define static topology, but the
            //single-building button intentionally appears only while the diagnostic input is empty.
            producer.enabled = false;
            if(producer.liquids != null) producer.liquids.clear();
            for(int dx = 4; dx <= 5; dx++){
                Building conduit = world.tile(rx() + dx, ry() + 3).build;
                if(conduit != null && conduit.liquids != null) conduit.liquids.clear();
            }
            target.liquids.clear();
        });
        queue(this::armPicker);
        queue(() -> clickBuilding(target));
        queue(() -> check("the missing Water input offers a direct Liquid Trace action",
            Core.scene.find("factoryscope-trace-liquid-water") != null));
        queue(() -> clickNamed("factoryscope-trace-liquid-water"));
        queue(() -> check("single-building Liquid Trace asks for an explicit area",
            FactoryScopeUI.picking() && FactoryScopeUI.inspected() == null));
        queue(() -> dragTiles(rx() + 1, ry() + 1, rx() + 9, ry() + 8));
        queue(() -> {
            LiquidTrace trace = FactoryScopeUI.liquidTrace();
            check("the production LiquidScope dialog opens from the single-building path",
                Core.scene.find("factoryscope-liquid-dialog") != null && trace != null);
            check("the structural Water trace reaches the placed mechanical pump",
                trace != null && trace.producers().stream().anyMatch(endpoint -> endpoint.building.equals(AreaProbe.refOf(producer))),
                trace == null ? "no trace" : "complete=" + trace.complete + ", endpoints=" + trace.endpoints);
            check("the trace claims structure, not liquid flow",
                dialogShows(FsBundle.get("liquid.trace-structural-input")));
            capture("liquid-water-trace");
        });
        queue(() -> {
            Scl.setProduct(2f);
            Core.scene.resize(1280, 720);
        });
        queue(() -> checkFits("LiquidScope at 1280x720 @ 2x"));
        queue(() -> capture("liquid-water-trace-high-ui-scale"));
        queue(() -> {
            Element inspect = Core.scene.find("factoryscope-liquid-producer-inspect");
            boolean withinRow = inspect != null && inspect.parent != null
                && inspect.x + inspect.getWidth() <= inspect.parent.getWidth() + 0.5f;
            check("the liquid producer Inspect action stays compact at high UI scale",
                inspect instanceof ImageButton && inspect.getWidth() <= 34.5f && withinRow,
                inspect == null ? "missing action" : inspect.getClass().getSimpleName() + " x=" + inspect.x
                    + ", width=" + inspect.getWidth() + ", parent width="
                    + (inspect.parent == null ? "none" : inspect.parent.getWidth()));
        });
        queue(this::restoreLayout);
        queue(() -> clickNamed("factoryscope-liquid-view-world"));
        queue(() -> check("the liquid overlay identifies structural routes without flow claims",
            Core.scene.find("factoryscope-liquid-viewing") != null));
        queue(() -> clickNamed("factoryscope-liquid-return"));
        queue(() -> check("Return restores the same Liquid Trace context",
            Core.scene.find("factoryscope-liquid-dialog") != null && FactoryScopeUI.liquidTrace() != null
                && FactoryScopeUI.liquidTrace().target.equals(AreaProbe.refOf(target))
                && FactoryScopeUI.liquidTrace().liquid.equals(new ResourceRef(ResourceKind.liquid,
                    Liquids.water.name, Liquids.water.localizedName))
                && FactoryScopeUI.liquidTrace().direction == TraceDirection.input
                && FactoryScopeUI.liquidTrace().producers().stream()
                    .anyMatch(endpoint -> endpoint.building.equals(AreaProbe.refOf(producer)))));
        queue(() -> clickNamed("factoryscope-liquid-producer-inspect"));
        queue(() -> check("Inspect opens the actual pump while retaining the trace dialog",
            FactoryScopeUI.inspected() == producer,
            "expected " + describe(producer) + ", inspected " + describe(FactoryScopeUI.inspected())
                + ", trace dialog " + (Core.scene.find("factoryscope-liquid-dialog") != null)
                + ", trace " + (FactoryScopeUI.liquidTrace() == null ? "null" : FactoryScopeUI.liquidTrace().target)));
        queue(this::closeAnyDialog);
        queue(() -> check("closing pump diagnostics returns to the same Liquid Trace",
            Core.scene.find("factoryscope-liquid-dialog") != null && FactoryScopeUI.liquidTrace() != null));
        queue(() -> producer.enabled = true);
        queue(() -> clickNamed("factoryscope-liquid-refresh"));
        queue(() -> check("Refresh rebuilds the liquid trace snapshot coherently",
            FactoryScopeUI.liquidTrace() != null
                && FactoryScopeUI.liquidTrace().producers().stream().anyMatch(endpoint -> endpoint.building.equals(AreaProbe.refOf(producer)))));
        queue(() -> check("Refresh also rebuilds the producer diagnostic annotation",
            FactoryScopeUI.liquidTrace() != null && FactoryScopeUI.liquidTrace().producers().stream()
                .anyMatch(endpoint -> endpoint.building.equals(AreaProbe.refOf(producer))
                    && endpoint.diagnostic != null && endpoint.diagnostic.reason() != DiagnosticReason.disabled)));
        queue(() -> clickNamed("factoryscope-liquid-producer-inspect"));
        queue(() -> check("the pump inspector is open before a world change", FactoryScopeUI.inspected() == producer));
        queue(this::closeAnyDialog);
        queue(() -> check("Return restores Liquid Trace before a topology change",
            Core.scene.find("factoryscope-liquid-dialog") != null && FactoryScopeUI.liquidTrace() != null));
        queue(() -> {
            Building firstConduit = world.tile(rx() + 4, ry() + 3).build;
            if(firstConduit != null) firstConduit.rotation = 1;
        });
        queue(() -> clickNamed("factoryscope-liquid-refresh"));
        queue(() -> check("Refresh removes a producer after its structural Conduit route is rotated away",
            FactoryScopeUI.liquidTrace() != null && FactoryScopeUI.liquidTrace().producers().stream()
                .noneMatch(endpoint -> endpoint.building.equals(AreaProbe.refOf(producer)))));
        queue(() -> clickNamed("factoryscope-liquid-view-world"));
        queue(() -> check("the liquid world overlay is active before a world change",
            Core.scene.find("factoryscope-liquid-viewing") != null));
        queue(() -> Events.fire(new WorldLoadEvent()));
        queue(() -> check("world change clears the Liquid Trace snapshot and overlay",
            FactoryScopeUI.inspected() == null && FactoryScopeUI.liquidTrace() == null
                && Core.scene.find("factoryscope-liquid-dialog") == null
                && Core.scene.find("factoryscope-liquid-viewing") == null));
        queue(this::closeAnyDialog);

        scenario("filter consumers let the player choose an accepted liquid before tracing");
        queue(() -> {
            clearRegion();
            acceptanceCoolant = Blocks.foreshadow.findConsumer(consume -> consume instanceof ConsumeCoolant);
            check("vanilla Foreshadow exposes its coolant filter", acceptanceCoolant != null);
            if(acceptanceCoolant != null){
                acceptanceCoolantWasOptional = acceptanceCoolant.optional;
                // The acceptance-only condition makes the vanilla filter a required diagnostic input.
                // This changes only the probe-facing field for the test; it does not update the engine's
                // precomputed optional-consumer arrays or alter simulation behavior.
                acceptanceCoolant.optional = false;
            }
            target = placeAt(Blocks.foreshadow, rx() + 4, ry() + 4);
            FactorySnapshot snapshot = MindustryFactoryProbe.probe(target);
            boolean filterInput = snapshot.inputs.stream().anyMatch(input -> input.kind == ResourceKind.liquid
                && input.contentId == null && !input.optional
                && input.acceptedResources.stream().anyMatch(resource -> resource.id.equals(Liquids.cryofluid.name)));
            check("the vanilla filter snapshot exposes Cryofluid as a required accepted alternative", filterInput,
                "optional=" + (acceptanceCoolant == null ? "missing" : acceptanceCoolant.optional)
                    + ", inputs=" + snapshot.inputs);
        });
        queue(this::armPicker);
        queue(() -> clickBuilding(target));
        queue(() -> check("the filter target opens in the real inspector", FactoryScopeUI.inspected() == target,
            "target=" + MindustryFactoryProbe.describe(target) + ", inspected="
                + (FactoryScopeUI.inspected() == null ? "null" : MindustryFactoryProbe.describe(FactoryScopeUI.inspected()))));
        queue(() -> check("an empty filter consumer offers a liquid-choice action",
            Core.scene.find("factoryscope-trace-liquid-choice") != null));
        queue(() -> clickNamed("factoryscope-trace-liquid-choice"));
        queue(() -> check("the choice dialog lists the filter's accepted resources",
            Core.scene.find("factoryscope-liquid-filter-option-water") != null
                && Core.scene.find("factoryscope-liquid-filter-option-cryofluid") != null
                && Core.scene.find("factoryscope-liquid-filter-option-oil") == null));
        queue(() -> capture("liquid-filter-choice"));
        queue(() -> clickNamed("factoryscope-liquid-filter-option-cryofluid"));
        queue(() -> check("choosing Cryofluid requests an explicit area for that exact resource",
            FactoryScopeUI.picking() && Core.scene.find("factoryscope-liquid-dialog") == null));
        queue(() -> dragTiles(rx() + 2, ry() + 2, rx() + 6, ry() + 6));
        queue(() -> {
            LiquidTrace trace = FactoryScopeUI.liquidTrace();
            check("filter selection opens a Cryofluid trace without inventing a single filter product",
                trace != null && trace.liquid.equals(new ResourceRef(ResourceKind.liquid,
                    Liquids.cryofluid.name, Liquids.cryofluid.localizedName)) && trace.targetUsesLiquid
                    && trace.target.equals(AreaProbe.refOf(target)));
            if(acceptanceCoolant != null){
                acceptanceCoolant.optional = acceptanceCoolantWasOptional;
            }
        });
        queue(this::closeAnyDialog);

        scenario("an unsupported liquid transport is an incomplete, locatable trace row");
        queue(() -> {
            clearRegion();
            target = placeAt(Blocks.cryofluidMixer, rx() + 6, ry() + 4);
            placeAt(Blocks.platedConduit, rx() + 5, ry() + 4);
        });
        queue(this::armPicker);
        queue(() -> dragTiles(rx() + 2, ry() + 2, rx() + 9, ry() + 7));
        queue(() -> clickNamed("factoryscope-area-liquids"));
        queue(() -> clickNamed("factoryscope-liquid-select-water"));
        queue(() -> clickNamed("factoryscope-liquid-trace-input"));
        queue(() -> check("the unsupported transport is shown as incomplete rather than a dead end",
            FactoryScopeUI.liquidTrace() != null && !FactoryScopeUI.liquidTrace().complete
                && !FactoryScopeUI.liquidTrace().noRouteProven
                && !FactoryScopeUI.liquidTrace().unsupportedInterruptions.isEmpty()
                && Core.scene.find("factoryscope-liquid-unsupported-locate") != null));
        queue(() -> capture("liquid-unsupported"));
        queue(() -> clickNamed("factoryscope-liquid-unsupported-locate"));
        queue(() -> check("Locate opens for the actual unsupported liquid transport", FactoryScopeUI.locating()));
        queue(() -> clickNamed("factoryscope-locate-return"));
        queue(() -> check("Return restores the same unsupported Liquid Trace",
            Core.scene.find("factoryscope-liquid-dialog") != null && FactoryScopeUI.liquidTrace() != null
                && FactoryScopeUI.liquidTrace().target.equals(AreaProbe.refOf(target))
                && !FactoryScopeUI.liquidTrace().unsupportedInterruptions.isEmpty()));
        queue(this::closeAnyDialog);

        scenario("a liquid trace marks a route that continues beyond the selected area");
        queue(() -> {
            clearRegion();
            int x = rx(), y = ry();
            // The west conduit is deliberately outside the selected area; the east conduit
            // and consumer remain inside it. No producer is needed to prove continuation.
            placeAt(Blocks.conduit, x + 4, y + 3, 0);
            placeAt(Blocks.conduit, x + 5, y + 3, 0);
            target = placeAt(Blocks.cryofluidMixer, x + 6, y + 3);
            world.tile(x + 4, y + 3).build.liquids.clear();
            world.tile(x + 5, y + 3).build.liquids.clear();
            target.liquids.clear();
        });
        queue(this::armPicker);
        queue(() -> dragTiles(rx() + 5, ry() + 1, rx() + 9, ry() + 8));
        queue(() -> clickNamed("factoryscope-area-liquids"));
        queue(() -> clickNamed("factoryscope-liquid-select-water"));
        queue(() -> clickNamed("factoryscope-liquid-trace-input"));
        queue(() -> {
            LiquidTrace trace = FactoryScopeUI.liquidTrace();
            check("the trace preserves an external continuation without claiming an in-area producer",
                trace != null && !trace.boundaryContinuations.isEmpty() && trace.producers().isEmpty()
                    && !trace.noRouteProven,
                trace == null ? "trace unavailable" : "boundaries=" + trace.boundaryContinuations.size()
                    + ", producers=" + trace.producers().size() + ", dead ends=" + trace.structuralDeadEnds.size()
                    + ", noRoute=" + trace.noRouteProven);
            check("the liquid dialog uses selected-area boundary wording",
                trace != null && dialogShows(trace.boundaryContinuations.size() == 1
                    ? FsBundle.get("liquid.trace-boundary-one")
                    : FsBundle.format("liquid.trace-boundary-many", trace.boundaryContinuations.size())));
            capture("liquid-boundary");
        });
        queue(this::closeAnyDialog);

        scenario("the Area Liquids view filters Water and traces its consumer through production UI");
        queue(() -> {
            clearRegion();
            int x = rx(), y = ry();
            for(int dx = 2; dx <= 3; dx++) for(int dy = 2; dy <= 3; dy++)
                world.tile(x + dx, y + dy).setFloor((Floor)Blocks.water);
            producer = placeAt(Blocks.mechanicalPump, x + 3, y + 3);
            placeAt(Blocks.conduit, x + 4, y + 3, 0);
            placeAt(Blocks.conduit, x + 5, y + 3, 0);
            target = placeAt(Blocks.cryofluidMixer, x + 6, y + 3);
        });
        queue(this::armPicker);
        queue(() -> dragTiles(rx() + 1, ry() + 1, rx() + 9, ry() + 8));
        queue(() -> clickNamed("factoryscope-area-liquids"));
        queue(() -> check("Area Diagnostics opens the LiquidScope resource view",
            Core.scene.find("factoryscope-liquid-dialog") != null
                && Core.scene.find("factoryscope-liquid-select-water") != null));
        queue(() -> clickNamed("factoryscope-liquid-select-water"));
        queue(() -> capture("liquid-area-network"));
        queue(() -> clickNamed("factoryscope-liquid-trace-input"));
        queue(() -> {
            LiquidTrace trace = FactoryScopeUI.liquidTrace();
            check("Area Liquids traces from the actual selected consumer row",
                trace != null && trace.target.equals(AreaProbe.refOf(target))
                    && trace.producers().stream().anyMatch(endpoint -> endpoint.building.equals(AreaProbe.refOf(producer))));
            check("the Area entry path does not bypass the player-facing Trace action",
                Core.scene.find("factoryscope-liquid-dialog") != null
                    && Core.scene.find("factoryscope-liquid-producer-inspect") != null);
            capture("liquid-area-trace");
        });
        queue(this::closeAnyDialog);
        queue(this::armPicker);
        queue(() -> dragTiles(rx() + 1, ry() + 1, rx() + 9, ry() + 8));
        queue(() -> clickNamed("factoryscope-area-liquids"));
        queue(() -> clickNamed("factoryscope-liquid-select-water"));
        queue(() -> clickNamed("factoryscope-liquid-trace-output"));
        queue(() -> {
            LiquidTrace trace = FactoryScopeUI.liquidTrace();
            check("Area Liquids exposes a structural output trace from the pump",
                trace != null && trace.direction == TraceDirection.output
                    && trace.target.equals(AreaProbe.refOf(producer))
                    && trace.endpoints.stream().anyMatch(endpoint -> endpoint.building.equals(AreaProbe.refOf(target))));
            check("output wording remains distinct from upstream supply wording",
                dialogShows(FsBundle.get("liquid.trace-structural-output")));
            capture("liquid-output-trace");
        });
        queue(this::closeAnyDialog);

        scenario("an Erekir gas can be traced through a Liquid Junction without flow claims");
        queue(() -> {
            clearRegion();
            int x = rx(), y = ry();
            producer = placeAt(Blocks.electrolyzer, x + 3, y + 4);
            // Mindustry v160.5's Electrolyzer gas outputs are directed south.
            placeAt(Blocks.conduit, x + 3, y + 2, 3);
            placeAt(Blocks.liquidJunction, x + 3, y + 1);
            placeAt(Blocks.conduit, x + 3, y, 3);
        });
        queue(this::armPicker);
        queue(() -> dragTiles(rx() + 1, ry() + 1, rx() + 10, ry() + 8));
        queue(() -> clickNamed("factoryscope-area-liquids"));
        queue(() -> check("the Erekir Electrolyzer exposes Hydrogen in the liquid/gas filter",
            Core.scene.find("factoryscope-liquid-select-hydrogen") != null));
        queue(() -> clickNamed("factoryscope-liquid-select-hydrogen"));
        queue(() -> clickNamed("factoryscope-liquid-trace-output"));
        queue(() -> {
            LiquidTrace trace = FactoryScopeUI.liquidTrace();
            check("the gas output trace preserves Hydrogen identity and structural direction",
                trace != null && trace.direction == TraceDirection.output
                    && trace.liquid.equals(new ResourceRef(ResourceKind.liquid,
                        Liquids.hydrogen.name, Liquids.hydrogen.localizedName))
                    && trace.target.equals(AreaProbe.refOf(producer))
                    && !trace.traversedEdges.isEmpty(),
                trace == null ? "trace unavailable" : "direction=" + trace.direction + ", liquid=" + trace.liquid
                    + ", target=" + trace.target + ", expected=" + AreaProbe.refOf(producer)
                    + ", traversed=" + trace.traversedEdges.size() + ", endpoints=" + trace.endpoints.size());
        });
        queue(() -> clickNamed("factoryscope-liquid-view-world"));
        queue(() -> check("the gas trace can display its static route overlay",
            Core.scene.find("factoryscope-liquid-viewing") != null));
        queue(() -> capture("liquid-gas-junction-world"));
        queue(this::closeAnyDialog);
        queue(this::checkLiquidLocalization);
    }

    void healthyArea(){
        int x1 = rx(), y1 = ry(), x2 = rx() + 14, y2 = ry() + 6;

        scenario("an area of supplied factories reports no problems");
        queue(this::closeAnyDialog);
        queue(() -> {
            clearRegion();
            patch.clear();
            for(int i = 0; i < 3; i++){
                patch.add(supply(placeAt(Blocks.graphitePress, rx() + 2 + i * 5, ry() + 2), Items.coal, 40));
            }
        });
        queue(() -> check("the healthy patch was built", standing(patch) == 3, "standing " + standing(patch)));
        queue(this::armPicker);
        queue(() -> dragTiles(x1, y1, x2, y2));
        queue(() -> {
            AreaDiagnosticResult report = FactoryScopeUI.areaReport();
            check("the healthy area opened a report", report != null);
            if(report == null) return;
            check("three factories were analysed", report.summary.analyzed == 3,
                "analysed " + report.summary.analyzed);
            check("no problems were reported", report.healthy(), report.summary.toString());
            check("no issue groups were invented", report.issues.isEmpty(), report.issues.toString());
        });
        queue(() -> capture("healthy-area"));
    }

    void emptyArea(){
        int x1 = rx() + 2, y1 = ry() + 2, x2 = rx() + 10, y2 = ry() + 8;

        scenario("an area with nothing in it opens a report that says so");
        queue(this::closeAnyDialog);
        queue(this::clearRegion);
        queue(this::armPicker);
        queue(() -> dragTiles(x1, y1, x2, y2));
        queue(() -> {
            AreaDiagnosticResult report = FactoryScopeUI.areaReport();
            check("a report opened for the empty area", report != null);
            if(report == null) return;
            check("it reports nothing selected", report.empty() && report.summary.selected == 0,
                report.summary.toString());
            check("an empty area is not called healthy", !report.healthy());
        });
        queue(() -> capture("empty-area"));
    }

    /** Dragging over blocks that have their own tap behaviour must not trigger any of it. */
    void configurableBlocks(){
        int x1 = rx(), y1 = ry(), x2 = rx() + 13, y2 = ry() + 6;

        scenario("dragging over configurable blocks opens no native configuration");
        queue(this::closeAnyDialog);
        queue(() -> {
            clearRegion();
            placeAt(Blocks.router, rx() + 2, ry() + 2);
            placeAt(Blocks.unloader, rx() + 6, ry() + 2);
            placeAt(Blocks.duo, rx() + 10, ry() + 2);
            if(control.input.config.isShown()) control.input.config.hideConfig();
        });
        queue(this::armPicker);
        queue(() -> dragTiles(x1, y1, x2, y2));
        queue(() -> {
            check("no block configuration was opened", !control.input.config.isShown());
            check("the area report is what came up instead",
                Core.scene.getDialog() != null && FactoryScopeUI.areaReport() != null);
        });
    }

    /** A refresh must re-run the query, not re-read the buildings it already knew about. */
    void refreshAfterAChange(){
        int x1 = rx(), y1 = ry(), x2 = rx() + 14, y2 = ry() + 8;

        scenario("refreshing an open report picks up buildings added and removed since");
        queue(this::closeAnyDialog);
        queue(() -> {
            clearRegion();
            patch.clear();
            for(int i = 0; i < 3; i++) patch.add(placeAt(Blocks.graphitePress, rx() + 2 + i * 5, ry() + 2));
        });
        queue(() -> check("the refresh patch was built", standing(patch) == 3, "standing " + standing(patch)));
        queue(this::armPicker);
        queue(() -> dragTiles(x1, y1, x2, y2));
        queue(() -> check("three buildings before the change",
            FactoryScopeUI.areaReport() != null && FactoryScopeUI.areaReport().summary.analyzed == 3,
            FactoryScopeUI.areaReport() == null ? "no report"
                : "analysed " + FactoryScopeUI.areaReport().summary.analyzed));
        queue(() -> {
            patch.get(0).tile.remove();
            placeAt(Blocks.graphitePress, rx() + 7, ry() + 6);
        });
        queue(() -> clickNamed("factoryscope-area-refresh"));
        queue(() -> {
            AreaDiagnosticResult report = FactoryScopeUI.areaReport();
            AreaSelection kept = FactoryScopeUI.areaBounds();
            check("the refresh kept the same bounds",
                kept != null && kept.equals(AreaSelection.of(x1, y1, x2, y2)), String.valueOf(kept));
            check("the refresh saw the change", report != null && report.summary.analyzed == 3,
                report == null ? "no report" : "analysed " + report.summary.analyzed);
            check("the removed building is gone from the report", report != null
                && report.entries.stream().noneMatch(e -> e.ref.tileX == rx() + 2 && e.ref.tileY == ry() + 2));
        });
    }

    /** From an aggregate line to one building, without losing the report behind it. */
    void drillDown(){
        int x1 = rx(), y1 = ry(), x2 = rx() + 12, y2 = ry() + 6;

        scenario("an issue group expands to its buildings and opens the single-building panel");
        queue(this::closeAnyDialog);
        queue(() -> {
            clearRegion();
            //two presses short of coal: one issue group, so the report is short enough to click through
            placeAt(Blocks.graphitePress, rx() + 2, ry() + 2);
            placeAt(Blocks.graphitePress, rx() + 7, ry() + 2);
        });
        queue(this::armPicker);
        queue(() -> dragTiles(x1, y1, x2, y2));
        queue(() -> {
            AreaDiagnosticResult report = FactoryScopeUI.areaReport();
            check("one issue group for the shared shortage",
                report != null && report.issues.size() == 1 && report.issues.get(0).buildingCount() == 2,
                report == null ? "no report" : report.issues.toString());
        });
        queue(() -> clickNamed("factoryscope-area-issue"));
        queue(() -> check("the group expanded to its building rows",
            countNamed("factoryscope-area-building") >= 1,
            "rows " + countNamed("factoryscope-area-building")));
        queue(() -> clickNamed("factoryscope-area-building"));
        queue(() -> {
            check("the single-building panel opened from the area report",
                FactoryScopeUI.inspected() != null);
            check("the area report is still there underneath", FactoryScopeUI.areaReport() != null);
        });
        queue(this::closeAnyDialog);
        queue(() -> check("closing the building panel leaves the area report open",
            FactoryScopeUI.areaReport() != null && FactoryScopeUI.inspected() == null));
    }

    /**
     * The area report has to fit the same window sizes and UI scales the panel does.
     *
     * <p>The selection is made first and the scene resized afterwards. Resizing first would leave the
     * scene claiming a screen size the real window does not have, and the drag would then be computed
     * from one screen and dispatched into another.
     */
    void areaLayout(int width, int height, float scale){
        int x1 = rx(), y1 = ry(), x2 = rx() + 12, y2 = ry() + 7;

        scenario("the area report fits a " + width + "x" + height + " scene at " + scale + "x UI scale");
        queue(this::closeAnyDialog);
        queue(this::restoreLayout);
        queue(() -> {
            clearRegion();
            placeAt(Blocks.siliconSmelter, rx() + 3, ry() + 3);
            placeAt(Blocks.graphitePress, rx() + 8, ry() + 3);
            placeAt(Blocks.titaniumWall, rx() + 3, ry() + 6);
        });
        queue(this::armPicker);
        queue(() -> dragTiles(x1, y1, x2, y2));
        queue(() -> check(width + "x" + height + " @ " + scale + "x: a report opened",
            FactoryScopeUI.areaReport() != null));
        queue(() -> {
            Scl.setProduct(scale);
            Core.scene.resize(width, height);
        });
        queue(() -> checkFits("area " + width + "x" + height + " @ " + scale + "x"));
    }

    /** Full select, read and close cycles, to expose overlays or draw hooks that are never released. */
    void repeatedAreaUse(int cycles){
        int x1 = rx(), y1 = ry(), x2 = rx() + 10, y2 = ry() + 6;

        scenario("repeated area selection leaks nothing");
        queue(this::closeAnyDialog);
        queue(this::ensurePickerOff);
        queue(() -> {
            clearRegion();
            placeAt(Blocks.graphitePress, rx() + 3, ry() + 3);
            baselineFactoryScopeElements = countFactoryScopeElements();
        });

        for(int i = 0; i < cycles; i++){
            queue(this::armPicker);
            queue(() -> dragTiles(x1, y1, x2, y2));
            queue(this::closeAnyDialog);
        }

        queue(() -> {
            check("the picker is off after the last area cycle", !FactoryScopeUI.picking());
            check("still exactly one HUD toggle", countNamed("factoryscope-toggle") == 1);
            check("no leftover selection overlay", countNamed("factoryscope-picker") == 0);
            check("no leftover hint", countNamed("factoryscope-hint") == 0);
            int now = countFactoryScopeElements();
            check("FactoryScope scene elements returned to baseline after area use", now <= baselineFactoryScopeElements,
                "baseline " + baselineFactoryScopeElements + " now " + now);
        });
    }

    void areaWorldChange(){
        int x1 = rx(), y1 = ry(), x2 = rx() + 10, y2 = ry() + 6;

        scenario("a world change clears an open area report");
        queue(this::closeAnyDialog);
        queue(() -> {
            clearRegion();
            placeAt(Blocks.graphitePress, rx() + 3, ry() + 3);
        });
        queue(this::armPicker);
        queue(() -> dragTiles(x1, y1, x2, y2));
        queue(() -> check("a report is open before the world change", FactoryScopeUI.areaReport() != null));
        queue(() -> Events.fire(new WorldLoadEvent()));
        queue(() -> delayNextAction(30f));
        queue(() -> {
            check("the area report was cleared on world load", FactoryScopeUI.areaReport() == null);
            check("the bounds were cleared too", FactoryScopeUI.areaBounds() == null);
            check("the picker was cleared on world load", !FactoryScopeUI.picking());
        });

        scenario("a world change clears an open Supply Trace");
        queue(this::closeAnyDialog);
        queue(this::closeAnyDialog);
        queue(() -> {
            clearRegion();
            target = placeAt(Blocks.siliconSmelter, rx() + 3, ry() + 3);
            placeAt(Blocks.conveyor, rx() + 2, ry() + 3);
        });
        queue(this::armPicker);
        queue(() -> dragTiles(rx() + 1, ry() + 2, rx() + 5, ry() + 6));
        queue(() -> clickNamed("factoryscope-area-network"));
        queue(() -> clickNamed("factoryscope-network-item-sand"));
        queue(() -> clickNamed("factoryscope-network-building", 1));
        queue(() -> check("the selected factory exposes its item trace",
            Core.scene.find("factoryscope-network-trace-input") != null));
        queue(() -> clickNamed("factoryscope-network-trace-input"));
        queue(() -> check("the Supply Trace is open before the world change",
            Core.scene.getDialog() != null && Core.scene.find("factoryscope-trace-back") != null));
        queue(() -> Events.fire(new WorldLoadEvent()));
        queue(() -> delayNextAction(30f));
        queue(() -> check("world change releases the trace and its area snapshot",
            FactoryScopeUI.areaReport() == null && !FactoryScopeUI.areaReportHeld()
                && FactoryScopeUI.areaBounds() == null && Core.scene.getDialog() == null));

        scenario("a world change during a selection cancels it");
        queue(this::armPicker);
        queue(() -> check("the picker is armed", FactoryScopeUI.picking()));
        queue(() -> Events.fire(new WorldLoadEvent()));
        queue(() -> check("the armed picker was cancelled", !FactoryScopeUI.picking()));
    }

    /**
     * The same rectangle selected at two very different zoom levels.
     *
     * <p>Zoom changes how many world units a screen pixel covers, so a conversion that folded the camera
     * scale in at the wrong point - or not at all - gives two different rectangles here while looking
     * perfectly correct at whatever zoom it was written against.
     */
    void zoomLevels(){
        int x1 = rx() + 1, y1 = ry() + 1, x2 = rx() + 9, y2 = ry() + 6;

        queue(() -> {
            clearRegion();
            patch.clear();
            patch.add(placeAt(Blocks.graphitePress, rx() + 3, ry() + 3));
            patch.add(placeAt(Blocks.graphitePress, rx() + 8, ry() + 5));
            //just outside, so a selection that grows with zoom is caught
            placeAt(Blocks.graphitePress, rx() + 12, ry() + 3);
            bounds.clear();
        });

        zoomLevel("zoomed out", 1f, x1, y1, x2, y2);
        zoomLevel("zoomed in", 4f, x1, y1, x2, y2);

        queue(() -> {
            check("both zoom levels selected the same tiles",
                bounds.size == 2 && bounds.first().equals(bounds.peek()), bounds.toString());
            renderer.targetscale = renderer.camerascale = 1.5f;
        });
    }

    void zoomLevel(String label, float scale, int x1, int y1, int x2, int y2){
        scenario("selecting an area " + label + " gives the same tiles");
        queue(this::closeAnyDialog);
        queue(() -> renderer.targetscale = renderer.camerascale = scale);
        queue(() -> check(label + ": both corners are on screen at " + scale + "x",
            onScreen(x1, y1) && onScreen(x2, y2),
            "camera " + (int)Core.camera.width + "x" + (int)Core.camera.height));
        queue(this::armPicker);
        queue(() -> dragTiles(x1, y1, x2, y2));
        queue(() -> {
            AreaSelection got = FactoryScopeUI.areaBounds();
            AreaDiagnosticResult report = FactoryScopeUI.areaReport();
            check(label + ": the reported tiles are the tiles that were dragged",
                AreaSelection.of(x1, y1, x2, y2).equals(got), String.valueOf(got));
            check(label + ": the two buildings inside were selected, and only those",
                report != null && report.summary.analyzed == 2,
                report == null ? "no report" : "analysed " + report.summary.analyzed);
            if(got != null) bounds.add(got);
        });
    }

    /**
     * A drag that runs off the edge of the map reports the part of the world that exists.
     *
     * <p>The view is moved to the corner of the map first, because the only place a player can point at
     * a tile that does not exist is next to an edge. It is put back afterwards.
     */
    void offWorldDrag(){
        scenario("a drag that runs off the map is clamped to the world");
        queue(this::closeAnyDialog);
        queue(this::ensurePickerOff);
        queue(() -> control.input.panCamera(new Vec2(6 * tilesize, 6 * tilesize)));
        queue(() -> check("the map corner is in view", onScreen(2, 2) && onScreen(-20, -20),
            "camera at " + (int)Core.camera.position.x + "," + (int)Core.camera.position.y));
        queue(this::armPicker);
        queue(() -> dragTiles(3, 3, -20, -20));
        queue(() -> {
            AreaSelection got = FactoryScopeUI.areaBounds();
            check("a report still opened", got != null && FactoryScopeUI.areaReport() != null);
            if(got != null){
                check("the reported area lies inside the world",
                    got.minX >= 0 && got.minY >= 0 && got.maxX < world.width() && got.maxY < world.height(),
                    got.toString());
            }
        });
        queue(this::closeAnyDialog);
        queue(this::restoreCamera);
        queue(() -> check("the view returned to the player",
            player != null && Core.camera.position.dst(player.x, player.y) < 200f,
            "camera at " + (int)Core.camera.position.x + "," + (int)Core.camera.position.y));
    }

    /** Arms the overlay through the real HUD button; harmless if a previous scenario left it armed. */
    void armPicker(){
        if(!FactoryScopeUI.picking()) clickToggleButton();
    }

    /** Puts the view back on the player after a scenario moved it. */
    void restoreCamera(){
        if(player != null) control.input.panCamera(new Vec2(player.x, player.y));
    }

    /**
     * The worst case for layout: many issue groups, on the smallest scene, at the largest UI scale, in
     * whatever language the game is running in. Long labels and a crowded list clip here first.
     */
    void crowdedReport(int width, int height, float scale){
        int x1 = rx(), y1 = ry(), x2 = rx() + 16, y2 = ry() + 11;

        scenario("a crowded report fits a " + width + "x" + height + " scene at " + scale + "x UI scale");
        queue(this::closeAnyDialog);
        queue(this::restoreLayout);
        queue(() -> {
            clearRegion();
            //five different shortages plus a disabled block and an unsupported one
            placeAt(Blocks.siliconSmelter, rx() + 2, ry() + 2);
            placeAt(Blocks.kiln, rx() + 7, ry() + 2);
            placeAt(Blocks.surgeSmelter, rx() + 12, ry() + 2);
            placeAt(Blocks.graphitePress, rx() + 2, ry() + 7);
            supply(placeAt(Blocks.graphitePress, rx() + 7, ry() + 7), Items.graphite, 10);
            placeAt(Blocks.titaniumWall, rx() + 12, ry() + 7);
            placeAt(Blocks.pneumaticDrill, rx() + 14, ry() + 9);
        });
        queue(this::armPicker);
        queue(() -> dragTiles(x1, y1, x2, y2));
        queue(() -> {
            AreaDiagnosticResult report = FactoryScopeUI.areaReport();
            check("the crowded area opened a report", report != null);
            if(report != null){
                check("it really is crowded", report.issues.size() >= 4,
                    "groups " + report.issues.size() + " analysed " + report.summary.analyzed);
            }
        });
        queue(() -> {
            Scl.setProduct(scale);
            Core.scene.resize(width, height);
        });
        queue(() -> checkFits("crowded " + width + "x" + height + " @ " + scale + "x"));
        queue(() -> capture("crowded-" + width + "x" + height + "-scale" + scale));
    }

    /** Every string the area report can show has to resolve in whatever language the game is in. */
    void checkAreaLocalization(){
        scenarioNow("every area string resolves in the active locale");
        Seq<String> keys = Seq.with("area.title", "area.section.summary", "area.section.status",
            "area.section.issues", "area.selected", "area.with-rates", "area.size", "area.skipped",
            "area.none", "area.no-problems", "area.issue-note", "area.snapshot-note", "area.building-gone",
            "area.scan-failed", "target.unavailable",
            "area.refresh", "area.select-another", "area.locate", "area.return", "area.show-more",
            "area.only-limited");
        for(AreaStatus status : AreaStatus.values()) keys.add("area.status." + status.slug());
        for(DiagnosticReason reason : DiagnosticReason.values()){
            if(reason == DiagnosticReason.active || reason == DiagnosticReason.limitedSupport) continue;
            keys.add("area.issue." + reason.slug());
        }

        for(String key : keys){
            String text = FsBundle.get(key);
            check("'" + key + "' resolves", !text.startsWith(FsBundle.PREFIX) && !text.contains("???"), text);
        }

        //the formatted ones go through a different path, and an absent key there renders as ???key???
        String[][] formatted = {
            {"area.size-value", "12", "9"}, {"area.affected", "8"}, {"area.coordinates", "123", "61"},
            {"area.listing-shown", "40", "120"}, {"area.issue.missing-item-input.resource", "Sand"},
            {"area.issue.missing-liquid-input.resource", "Water"},
            {"area.issue.output-blocked.resource", "Silicon"},
            {"area.issue.other-consumer-limited.resource", "Heat"}};
        for(String[] entry : formatted){
            Object[] args = new Object[entry.length - 1];
            System.arraycopy(entry, 1, args, 0, args.length);
            String text = FsBundle.format(entry[0], args);
            check("'" + entry[0] + "' formats", !text.startsWith(FsBundle.PREFIX) && !text.contains("???"), text);
        }

        Log.info(TAG + " locale @ -> area.title = '@'", Core.bundle.getLocale(), FsBundle.get("area.title"));
    }

    void checkTraceLocalization(){
        scenarioNow("every Supply Trace string resolves in the active locale");
        Seq<String> keys = Seq.with("snapshot.note", "network.open", "network.title", "network.static-note", "network.resource",
            "trace.open", "trace.select-area", "trace.title", "trace.output-title", "trace.target",
            "trace.target-missing", "trace.no-route", "trace.no-downstream", "trace.no-producer",
            "trace.no-consumer", "trace.no-in-area-producer", "trace.no-in-area-consumer",
            "trace.boundary-one", "trace.boundary-many", "trace.boundary-at",
            "trace.unsupported-area-one", "trace.unsupported-area-many", "trace.unsupported-at", "trace.incomplete-diagnostics", "trace.endpoints",
            "trace.incomplete-target-topology",
            "trace.no-in-area-producer", "trace.dead-ends", "trace.dead-end", "trace.storage", "trace.inspect",
            "trace.viewing", "trace.conditional-path", "trace.back-network", "trace.input-action", "trace.output-action");
        for(String key : keys){
            String text = FsBundle.get(key);
            check("'" + key + "' resolves", !text.startsWith(FsBundle.PREFIX) && !text.contains("???"), text);
        }
        for(String[] entry : new String[][]{
            {"trace.target", "Silicon Smelter"}, {"trace.boundary-many", "3"},
            {"trace.boundary-at", "123", "61"}, {"trace.unsupported-area-many", "2"},
            {"trace.unsupported-at", "Armored Conveyor", "123", "61"}, {"trace.endpoints", "2"},
            {"trace.dead-ends", "3"}, {"trace.dead-end", "Conveyor", "123", "61"}, {"trace.input-action", "Sand"},
            {"trace.output-action", "Silicon"}}){
            Object[] args = new Object[entry.length - 1];
            System.arraycopy(entry, 1, args, 0, args.length);
            String text = FsBundle.format(entry[0], args);
            check("'" + entry[0] + "' formats", !text.startsWith(FsBundle.PREFIX) && !text.contains("???"), text);
        }
    }

    void checkPowerLocalization(){
        scenarioNow("every PowerScope string resolves in the active locale");
        Seq<String> keys = Seq.with("snapshot.note", "power.title", "power.open", "power.inspect-grid", "power.grid-member", "power.scope-note",
            "label.power-usage-nominal",
            "power.no-grid", "power.grids", "power.grid", "power.status", "power.selected-members", "power.diode-scope",
            "power.members", "power.extends-outside", "power.satisfaction", "power.generation", "power.demand",
            "power.balance", "power.balance-collecting", "power.battery", "power.generators", "power.consumers",
            "power.batteries", "power.generator-list", "power.consumer-list", "power.battery-list", "power.inspect",
            "power.metrics-unavailable", "power.battery-reserves", "power.generator-problems", "power.generator-summary",
            "power.diodes", "power.diode-link", "power.diode-unmeasured", "power.diode-no-batteries",
            "power.visibility-incomplete", "power.view-world", "power.viewing", "power.state.cheatPowered",
            "power.state.unavailable", "power.state.noCurrentDemand", "power.state.underpowered", "power.state.noGeneration",
            "power.state.generationBelowDemand", "power.state.surplus", "power.state.balanced");
        for(String key : keys){
            String text = FsBundle.get(key);
            check("'" + key + "' resolves", !text.startsWith(FsBundle.PREFIX) && !text.contains("???"), text);
        }
        for(String[] entry : new String[][]{
            {"power.grid", "2"}, {"power.members", "3", "8"}, {"power.generator-problems", "2"},
            {"power.generator-summary", "3", "2", "1", "0"}, {"power.diode-link", "Power Diode", "1", "2"}}){
            Object[] args = new Object[entry.length - 1];
            System.arraycopy(entry, 1, args, 0, args.length);
            String text = FsBundle.format(entry[0], args);
            check("'" + entry[0] + "' formats", !text.startsWith(FsBundle.PREFIX) && !text.contains("???"), text);
        }
        String oneMember = FsBundle.format("power.members", 1, 1);
        String locale = String.valueOf(Core.bundle.getLocale()).toLowerCase(java.util.Locale.ROOT);
        if(locale.startsWith("pt")){
            check("single-member PowerScope counts use neutral Portuguese wording",
                oneMember.equals("1 / 1"), oneMember);
        }else{
            check("single-member PowerScope counts avoid plural agreement errors",
                oneMember.equals("1 / 1"), oneMember);
        }
    }

    void checkLiquidLocalization(){
        scenarioNow("every LiquidScope string resolves in the active locale");
        Seq<String> keys = Seq.with("liquid.title", "liquid.open", "liquid.view-world", "liquid.viewing-structural",
            "snapshot.note", "liquid.resource", "liquid.no-resources", "liquid.structural-network", "liquid.static-note", "liquid.partial",
            "liquid.producers", "liquid.consumers", "liquid.storage", "liquid.boundaries", "liquid.unsupported",
            "liquid.current-storage", "liquid.storage-capacity-note", "liquid.stored-more", "liquid.trace-title", "liquid.output-title",
            "liquid.trace-target", "liquid.trace-structural-input", "liquid.trace-structural-output",
            "liquid.trace-target-missing", "liquid.no-input-route", "liquid.no-output-route",
            "liquid.no-in-area-producer", "liquid.no-in-area-consumer", "liquid.trace-incomplete",
            "liquid.target-requirement-incomplete", "liquid.trace-incomplete-connections",
            "liquid.target-outside-snapshot",
            "liquid.trace-boundary-one", "liquid.trace-boundary-many", "liquid.boundary-at",
            "liquid.trace-unsupported", "liquid.unsupported-at",
            "liquid.dead-end", "liquid.reachable-sources", "liquid.reachable-destinations", "liquid.storage-endpoint",
            "liquid.producer", "liquid.consumer", "liquid.trace-edges", "liquid.trace-input", "liquid.trace-output",
            "liquid.trace-input-short", "liquid.trace-output-short",
            "liquid.choose-trace", "liquid.choose-title", "liquid.select-area", "liquid.unavailable");
        for(String key : keys){
            String text = FsBundle.get(key);
            check("'" + key + "' resolves", !text.startsWith(FsBundle.PREFIX) && !text.contains("???"), text);
        }
        for(String[] entry : new String[][]{
            {"liquid.stored-more", "4"}, {"liquid.trace-target", "Cryofluid Mixer"},
            {"liquid.trace-boundary-many", "2"},
            {"liquid.boundary-at", "Conduit", "123", "61"}, {"liquid.unsupported-at", "Armored Conduit", "123", "61"},
            {"liquid.dead-end", "Conduit", "123", "61"}, {"liquid.trace-edges", "5"}, {"value.of", "10", "40"}}){
            Object[] args = new Object[entry.length - 1];
            System.arraycopy(entry, 1, args, 0, args.length);
            String text = FsBundle.format(entry[0], args);
            check("'" + entry[0] + "' formats", !text.startsWith(FsBundle.PREFIX) && !text.contains("???"), text);
        }
    }


    /**
     * The reason Locate exists: the world has to become visible, the target has to be findable, and the
     * report has to still be there afterwards.
     */
    void locateAndReturn(){
        int x1 = rx(), y1 = ry(), x2 = rx() + 12, y2 = ry() + 6;

        scenario("locating a building uncovers the world and offers the way back");
        queue(this::closeAnyDialog);
        queue(() -> {
            clearRegion();
            placeAt(Blocks.graphitePress, rx() + 2, ry() + 2);
            placeAt(Blocks.graphitePress, rx() + 7, ry() + 2);
        });
        queue(this::armPicker);
        queue(() -> dragTiles(x1, y1, x2, y2));
        queue(() -> clickNamed("factoryscope-area-issue"));
        queue(() -> clickNamed("factoryscope-area-locate"));
        // BaseDialog hides through a short scene action; the next game update can still observe
        // the closing dialog before Arc has completed that transition.
        queue(() -> delayNextAction(30f));
        queue(() -> {
            check("the report stepped out of the way", Core.scene.getDialog() == null);
            check("the world is no longer covered by a report", FactoryScopeUI.areaReport() == null);
            check("the report is still being held for the player", FactoryScopeUI.areaReportHeld());
            check("the locate bar is on screen", FactoryScopeUI.locating());
            check("exactly one locate bar exists", countNamed("factoryscope-locate") == 1);
        });
        queue(() -> capture("locate-highlight"));
        queue(() -> clickNamed("factoryscope-locate-return"));
        queue(() -> {
            check("the same report came back", FactoryScopeUI.areaReport() != null);
            check("with the same bounds",
                AreaSelection.of(x1, y1, x2, y2).equals(FactoryScopeUI.areaBounds()),
                String.valueOf(FactoryScopeUI.areaBounds()));
            check("the locate bar was cleared", !FactoryScopeUI.locating());
            check("no leftover locate bar", countNamed("factoryscope-locate") == 0);
        });

        scenario("dismissing the locate bar leaves the game alone");
        queue(() -> clickNamed("factoryscope-area-issue"));
        queue(() -> clickNamed("factoryscope-area-locate"));
        queue(() -> check("locating again", FactoryScopeUI.locating()));
        queue(() -> clickNamed("factoryscope-locate-dismiss"));
        queue(() -> {
            check("the bar is gone", !FactoryScopeUI.locating());
            check("no report was forced back on screen", FactoryScopeUI.areaReport() == null);
            check("nothing of FactoryScope is left in the way",
                countNamed("factoryscope-locate") == 0 && countNamed("factoryscope-picker") == 0);
        });
        queue(this::closeAnyDialog);

        scenario("a world change during locate clears it");
        queue(this::armPicker);
        queue(() -> dragTiles(x1, y1, x2, y2));
        queue(() -> clickNamed("factoryscope-area-issue"));
        queue(() -> clickNamed("factoryscope-area-locate"));
        queue(() -> check("locating before the world change", FactoryScopeUI.locating()));
        queue(() -> Events.fire(new WorldLoadEvent()));
        queue(() -> {
            check("the locate bar was cleared on world load", !FactoryScopeUI.locating());
            check("no locate bar survived", countNamed("factoryscope-locate") == 0);
            check("the held report was dropped too", !FactoryScopeUI.areaReportHeld());
        });
    }

    /** Inspecting one building from the report must not cost the player the report. */
    void buildingDetailKeepsTheReport(){
        int x1 = rx(), y1 = ry(), x2 = rx() + 12, y2 = ry() + 6;

        scenario("opening a building from the report and closing it comes back to the same report");
        queue(this::closeAnyDialog);
        queue(() -> {
            clearRegion();
            placeAt(Blocks.graphitePress, rx() + 2, ry() + 2);
            placeAt(Blocks.graphitePress, rx() + 7, ry() + 2);
        });
        queue(this::armPicker);
        queue(() -> dragTiles(x1, y1, x2, y2));
        queue(() -> clickNamed("factoryscope-area-issue"));
        queue(() -> clickNamed("factoryscope-area-building"));
        queue(() -> {
            check("the building panel opened", FactoryScopeUI.inspected() != null);
            check("the report is still held underneath", FactoryScopeUI.areaReportHeld());
        });
        queue(this::closeAnyDialog);
        queue(() -> {
            check("the report is back on screen", FactoryScopeUI.areaReport() != null);
            check("with the same bounds",
                AreaSelection.of(x1, y1, x2, y2).equals(FactoryScopeUI.areaBounds()));
            check("the building panel is closed", FactoryScopeUI.inspected() == null);
        });
    }

    /**
     * The right mouse button is also {@code Binding.breakBlock}. Cancelling on the press would clear
     * {@code Core.scene.hasMouse()} while the tap was still fresh and hand the same click to the world
     * as the start of a demolition, so it is cancelled on the release instead.
     */
    void secondaryButtonCancels(){
        scenario("the secondary button cancels without starting to demolish anything");
        queue(this::closeAnyDialog);
        queue(() -> {
            clearRegion();
            target = placeAt(Blocks.graphitePress, rx() + 4, ry() + 4);
            control.input.block = null;
        });
        queue(this::armPicker);
        queue(() -> {
            Vec2 screen = Core.camera.project(new Vec2(target.x, target.y));
            touchButton(Mathf.round(screen.x), Mathf.round(screen.y), KeyCode.mouseRight);
        });
        queue(() -> {
            check("the selection was cancelled", !FactoryScopeUI.picking());
            check("no report was opened", FactoryScopeUI.areaReport() == null);
            check("no building panel was opened", FactoryScopeUI.inspected() == null);
            check("the game did not enter block-breaking mode", !breakingBlocks(),
                "input mode " + control.input);
            check("the target survived", target.isValid() && target.tile.build == target);
        });
    }

    /**
     * The same movement, in the same pixels, judged at two UI scales.
     *
     * <p>Arc's scene viewport is one unit per screen pixel, so a raw threshold would mean a smaller and
     * smaller physical distance as displays get denser - exactly when a player raises the UI scale and
     * needs it to grow. Twenty pixels must therefore be a deliberate drag at 1x and still a click at
     * 2x. Both presses land on one five-tile block, so the click has somewhere honest to resolve to.
     */
    void dragThresholdTracksUiScale(){
        scenario("the gesture threshold tracks the UI scale");
        queue(this::closeAnyDialog);
        queue(this::restoreLayout);
        queue(() -> {
            clearRegion();
            //at this zoom one tile is ten pixels, so twenty pixels crosses two tiles: enough to be an
            //area if it counts as a drag, and still inside a 5x5 footprint if it counts as a click
            renderer.targetscale = renderer.camerascale = 1.25f;
            target = placeAt(Blocks.eruptionDrill, rx() + 8, ry() + 7);
        });
        queue(() -> check("the test block is five tiles across", target != null && target.block.size == 5,
            target == null ? "not placed" : "size " + target.block.size));

        thresholdCase("1.0x", 1f, 20, false);
        thresholdCase("2.0x", 2f, 20, true);

        queue(() -> {
            restoreLayout();
            renderer.targetscale = renderer.camerascale = 1.5f;
        });
    }

    void thresholdCase(String label, float uiScale, int travel, boolean expectClick){
        queue(this::closeAnyDialog);
        queue(() -> Scl.setProduct(uiScale));
        queue(this::armPicker);
        queue(() -> {
            Vec2 screen = Core.camera.project(new Vec2(target.x, target.y));
            int sx = Mathf.round(screen.x), sy = Mathf.round(screen.y);
            Core.scene.touchDown(sx, sy, 0, KeyCode.mouseLeft);
            for(int i = 1; i <= 4; i++) Core.scene.touchDragged(sx + travel * i / 4, sy, 0);
            Core.scene.touchUp(sx + travel, sy, 0, KeyCode.mouseLeft);
            Log.info(TAG + " @ threshold @ px, moved @ px", label, Scl.scl(16f), travel);
        });
        queue(() -> {
            if(expectClick){
                check(label + ": " + travel + " px stays a click when the UI is scaled up",
                    FactoryScopeUI.inspected() == target && FactoryScopeUI.areaBounds() == null,
                    "inspected " + describe(FactoryScopeUI.inspected())
                        + " area " + FactoryScopeUI.areaBounds());
            }else{
                check(label + ": " + travel + " px is a deliberate drag at normal UI scale",
                    FactoryScopeUI.areaBounds() != null && FactoryScopeUI.inspected() == null,
                    "area " + FactoryScopeUI.areaBounds()
                        + " inspected " + describe(FactoryScopeUI.inspected()));
            }
        });
    }

    /**
     * A movement past the threshold that never leaves one tile.
     *
     * <p>At the zoom players actually use, one tile is thirty-two pixels, so a twenty-four pixel
     * wobble clears the drag threshold without ever pointing at a second tile. Reporting a one-tile
     * "area" there would be an odd answer to what was plainly a click, so the gesture falls back to
     * single-building inspection - and that fallback needs a test, because at the zoomed-out scale the
     * other scenarios run at, it is never reached.
     */
    void singleTileDragIsAClick(){
        scenario("a drag that never leaves one tile inspects that tile");
        queue(this::closeAnyDialog);
        queue(this::restoreLayout);
        queue(() -> {
            clearRegion();
            //one tile is 32 px at this zoom, so a tile spans 16 px either side of its centre
            renderer.targetscale = renderer.camerascale = 4f;
            target = placeAt(Blocks.surgeSmelter, rx() + 6, ry() + 6);
        });
        queue(this::armPicker);
        queue(() -> {
            Vec2 screen = Core.camera.project(new Vec2(target.x, target.y));
            int sx = Mathf.round(screen.x) - 12, sy = Mathf.round(screen.y);
            Core.scene.touchDown(sx, sy, 0, KeyCode.mouseLeft);
            for(int i = 1; i <= 4; i++) Core.scene.touchDragged(sx + 6 * i, sy, 0);
            Core.scene.touchUp(sx + 24, sy, 0, KeyCode.mouseLeft);
            Log.info(TAG + " moved 24 px inside one 32 px tile, threshold @ px", Scl.scl(16f));
        });
        queue(() -> {
            check("a 24 px move inside one tile opened the building panel",
                FactoryScopeUI.inspected() == target, describe(FactoryScopeUI.inspected()));
            check("it did not open a one-tile area report", FactoryScopeUI.areaBounds() == null,
                String.valueOf(FactoryScopeUI.areaBounds()));
        });
        queue(() -> renderer.targetscale = renderer.camerascale = 1.5f);
    }

    /**
     * The crowded report at a real UI scale, in the real window.
     *
     * <p>{@link #crowdedReport} resizes the scene, which is what a bounds assertion needs but makes a
     * screenshot lie: the scene believes it is one size while the framebuffer is another, and the
     * clipping no longer matches what a player would see. Moving only the UI scale is what actually
     * happens when the slider moves, so this is the version worth looking at.
     */
    void crowdedAtUiScale(float scale){
        int x1 = rx(), y1 = ry(), x2 = rx() + 16, y2 = ry() + 11;

        scenario("a crowded report renders at " + scale + "x UI scale in the real window");
        queue(this::closeAnyDialog);
        queue(() -> {
            clearRegion();
            placeAt(Blocks.siliconSmelter, rx() + 2, ry() + 2);
            placeAt(Blocks.kiln, rx() + 7, ry() + 2);
            placeAt(Blocks.surgeSmelter, rx() + 12, ry() + 2);
            placeAt(Blocks.graphitePress, rx() + 2, ry() + 7);
            supply(placeAt(Blocks.graphitePress, rx() + 7, ry() + 7), Items.graphite, 10);
            placeAt(Blocks.titaniumWall, rx() + 12, ry() + 7);
            //only the scale moves; the window and the scene stay exactly as the player has them
            Scl.setProduct(scale);
            Core.scene.resize(Core.graphics.getWidth(), Core.graphics.getHeight());
        });
        queue(this::armPicker);
        queue(() -> dragTiles(x1, y1, x2, y2));
        queue(() -> {
            check(scale + "x: a crowded report opened", FactoryScopeUI.areaReport() != null);
            checkFits("crowded at " + scale + "x in a " + Core.graphics.getWidth()
                + "x" + Core.graphics.getHeight() + " window");
        });
        queue(() -> capture("crowded-uiscale" + scale));
        queue(this::restoreLayout);
    }

    /** An area of walls has no problems in the same sense that it has no production. */
    void wallsOnlyArea(){
        int x1 = rx(), y1 = ry(), x2 = rx() + 10, y2 = ry() + 6;

        scenario("an area of walls says so rather than claiming a clean bill of health");
        queue(this::closeAnyDialog);
        queue(() -> {
            clearRegion();
            for(int i = 0; i < 4; i++) placeAt(Blocks.titaniumWall, rx() + 2 + i * 2, ry() + 3);
        });
        queue(this::armPicker);
        queue(() -> dragTiles(x1, y1, x2, y2));
        queue(() -> {
            AreaDiagnosticResult report = FactoryScopeUI.areaReport();
            check("a report opened for the walls", report != null);
            if(report == null) return;
            check("four walls were analysed", report.summary.analyzed == 4,
                "analysed " + report.summary.analyzed);
            check("none of them has production rates", report.summary.production == 0);
            check("no problems were invented", report.issues.isEmpty() && report.summary.problems == 0);
            check("the wording used is the limited-diagnostics one",
                dialogShows(FsBundle.get("area.only-limited")),
                "expected '" + FsBundle.get("area.only-limited") + "'");
            check("it does not claim a clean bill of health",
                !dialogShows(FsBundle.get("area.no-problems")));
        });
        queue(() -> capture("walls-only-area"));
    }

    /** A group larger than one page must say how much is hidden and be able to show the rest. */
    void showMore(){
        int x1 = rx(), y1 = ry(), x2 = rx() + REGION_WIDTH, y2 = ry() + 13;

        scenario("a long affected-building list pages rather than truncating silently");
        queue(this::closeAnyDialog);
        queue(() -> {
            clearRegion();
            //more than one page of starved presses, every one of them inside the rectangle below
            int placed = 0;
            for(int row = 0; row < 6; row++){
                for(int col = 0; col < 8; col++){
                    if(placeAt(Blocks.graphitePress, rx() + 1 + col * 2, ry() + 1 + row * 2) != null) placed++;
                }
            }
            Log.info(TAG + " placed @ presses for the paging check", placed);
        });
        queue(this::armPicker);
        queue(() -> dragTiles(x1, y1, x2, y2));
        queue(() -> {
            AreaDiagnosticResult report = FactoryScopeUI.areaReport();
            check("more than one page of buildings share the shortage",
                report != null && !report.issues.isEmpty() && report.issues.get(0).buildingCount() > 40,
                report == null ? "no report" : report.issues.toString());
        });
        queue(() -> clickNamed("factoryscope-area-issue"));
        queue(() -> {
            check("only the first page was built", countNamed("factoryscope-area-building") == 40,
                "rows " + countNamed("factoryscope-area-building"));
            check("a show-more button is offered", Core.scene.find("factoryscope-area-more") != null);
        });
        queue(this::scrollReportToBottom);
        queue(() -> clickNamed("factoryscope-area-more"));
        queue(() -> {
            check("the rest of the list appeared", countNamed("factoryscope-area-building") > 40,
                "rows " + countNamed("factoryscope-area-building"));
            check("nothing is left to show", Core.scene.find("factoryscope-area-more") == null);
        });
        queue(() -> capture("expanded-issue-group"));
    }


    // ------------------------------------------------------------------ area helpers

    /**
     * Scrolls the report to the bottom.
     *
     * <p>A synthetic click lands wherever its stage position lands, so a button below the fold has to
     * be brought into view first - exactly as a player would have to scroll to it.
     */
    void scrollReportToBottom(){
        Dialog dialog = Core.scene.getDialog();
        ScrollPane pane = dialog == null ? null : findPane(dialog);
        if(pane == null){
            check("the report has a scroll pane", false);
            return;
        }
        pane.setScrollPercentY(1f);
        pane.updateVisualScroll();
    }

    /**
     * Writes the last rendered frame to {@code saves/shots} so a human can look at the interface
     * rather than at assertions about its bounds.
     *
     * <p>Actions run during the game update, before this frame is drawn, so what is grabbed is the
     * frame that was on screen a tick ago - which is exactly the state the previous action left, since
     * every action is a tick apart.
     */
    void capture(String name){
        if(System.getProperty("factoryscope.capture") == null) return;
        try{
            Pixmap pixmap = ScreenUtils.getFrameBufferPixmap(0, 0, Core.graphics.getWidth(), Core.graphics.getHeight(), true);
            Fi file = Core.settings.getDataDirectory().child("shots").child(name + ".png");
            file.parent().mkdirs();
            PixmapIO.writePng(file, pixmap);
            pixmap.dispose();
            Log.info(TAG + " captured @", file.absolutePath());
        }catch(Throwable t){
            Log.err(TAG + " could not capture " + name, t);
        }
    }

    void touchButton(int screenX, int screenY, KeyCode button){
        Core.scene.touchDown(screenX, screenY, 0, button);
        Core.scene.touchUp(screenX, screenY, 0, button);
    }

    /** Whether the game has been put into its block-removal mode by a click FactoryScope should have eaten. */
    boolean breakingBlocks(){
        return control.input instanceof mindustry.input.DesktopInput desktop
            && desktop.mode == mindustry.input.PlaceMode.breaking;
    }

    /** Whether any label in the dialog on screen carries this exact text. */
    boolean dialogShows(String text){
        Dialog dialog = Core.scene.getDialog();
        if(dialog == null) return false;
        boolean[] found = {false};
        walk(dialog, element -> {
            if(element instanceof Label label && text.contentEquals(label.getText())) found[0] = true;
        });
        return found[0];
    }

    /** Whether the scene currently contains a label with this exact text (including Mindustry toasts). */
    boolean sceneShows(String text){
        boolean[] found = {false};
        walk(Core.scene.root, element -> {
            if(element instanceof Label label && text.contentEquals(label.getText())) found[0] = true;
        });
        return found[0];
    }

    String membersOf(AreaDiagnosticResult report){
        Seq<String> names = new Seq<>();
        for(AreaEntry entry : report.entries) names.add(entry.ref.toString());
        names.sort();
        return names.toString();
    }

    /** How many of the buildings a scenario placed are still standing where they were put. */
    int standing(Seq<Building> placed){
        int alive = 0;
        for(Building build : placed){
            if(build != null && build.isValid() && build.tile != null && build.tile.build == build) alive++;
        }
        return alive;
    }

    boolean onScreen(int tileX, int tileY){
        Vec2 screen = Core.camera.project(new Vec2(tileX * tilesize, tileY * tilesize));
        return screen.x >= 0f && screen.y >= 0f
            && screen.x <= Core.graphics.getWidth() && screen.y <= Core.graphics.getHeight();
    }

    /**
     * A drag through Arc's own input dispatch: press, several moves, release.
     *
     * <p>The intermediate moves matter. The overlay only enters area mode once the pointer has
     * travelled far enough, exactly as a real pointer does, so a press and release with nothing in
     * between would only ever test the click path.
     */
    void dragTiles(int fromX, int fromY, int toX, int toY){
        Vec2 from = Core.camera.project(new Vec2(fromX * tilesize, fromY * tilesize));
        int sx = Mathf.round(from.x), sy = Mathf.round(from.y);
        Vec2 to = Core.camera.project(new Vec2(toX * tilesize, toY * tilesize));
        int ex = Mathf.round(to.x), ey = Mathf.round(to.y);

        Core.scene.touchDown(sx, sy, 0, KeyCode.mouseLeft);
        for(int step = 1; step <= DRAG_STEPS; step++){
            float f = step / (float)DRAG_STEPS;
            Core.scene.touchDragged(Mathf.round(Mathf.lerp(sx, ex, f)), Mathf.round(Mathf.lerp(sy, ey, f)), 0);
        }
        Core.scene.touchUp(ex, ey, 0, KeyCode.mouseLeft);
    }

    void clickNamed(String name){
        clickNamed(name, 0);
    }

    void clickNamed(String name, int occurrence){
        Element[] found = {null};
        int[] matches = {0};
        walk(Core.scene.root, element -> {
            if(found[0] == null && name.equals(element.name) && matches[0]++ == occurrence) found[0] = element;
        });
        Element element = found[0];
        if(element == null){
            check("element " + name + " exists at index " + occurrence, false);
            return;
        }
        Vec2 stage = element.localToStageCoordinates(new Vec2(element.getWidth() / 2f, element.getHeight() / 2f));
        Vec2 screen = Core.scene.getViewport().project(stage);
        touch(Mathf.round(screen.x), Mathf.round(screen.y));
    }

    /** Empties the work region so a scenario starts from known ground. Cores are never touched. */
    void clearRegion(){
        for(int x = rx() - 2; x <= rx() + REGION_WIDTH + 2; x++){
            for(int y = ry() - 2; y <= ry() + REGION_HEIGHT + 2; y++){
                Tile tile = world.tile(x, y);
                if(tile == null || tile.block() == Blocks.air) continue;
                if(tile.block() instanceof mindustry.world.blocks.storage.CoreBlock) continue;
                tile.remove();
            }
        }
    }

    /**
     * Places a block on exactly its own footprint.
     *
     * <p>Unlike {@link #place}, nothing around it is cleared: an area scenario builds a whole patch at
     * once, and a helper that swept a margin would quietly demolish the neighbour placed a moment ago.
     */
    Building placeAt(Block block, int x, int y){
        return placeAt(block, x, y, 0);
    }

    Building placeAt(Block block, int x, int y, int rotation){
        Tile tile = world.tile(x, y);
        if(tile == null) return null;
        tile.setBlock(block, Team.sharded, rotation);
        return tile.build;
    }

    Building supply(Building build, Item item, int amount){
        if(build == null) return null;
        build.items.add(item, amount);
        build.updateConsumption();
        return build;
    }

    // ------------------------------------------------------------------ real input

    void clickToggleButton(){
        Element button = Core.scene.find("factoryscope-toggle");
        if(button == null){
            check("HUD toggle button exists", false);
            return;
        }
        Vec2 stage = button.localToStageCoordinates(new Vec2(button.getWidth() / 2f, button.getHeight() / 2f));
        Vec2 screen = Core.scene.getViewport().project(stage);
        touch(Mathf.round(screen.x), Mathf.round(screen.y));
    }

    void clickBuilding(Building build){
        clickWorld(build.x, build.y);
    }

    void clickTile(Tile tile){
        clickWorld(tile.worldx(), tile.worldy());
    }

    void clickWorld(float worldX, float worldY){
        Vec2 screen = Core.camera.project(new Vec2(worldX, worldY));
        touch(Mathf.round(screen.x), Mathf.round(screen.y));
    }

    void touch(int screenX, int screenY){
        Core.scene.touchDown(screenX, screenY, 0, KeyCode.mouseLeft);
        Core.scene.touchUp(screenX, screenY, 0, KeyCode.mouseLeft);
    }

    // ------------------------------------------------------------------ world helpers

    void ensurePickerOff(){
        if(FactoryScopeUI.picking()) clickToggleButton();
    }

    void closeAnyDialog(){
        if(Core.scene.getDialog() != null){
            Core.scene.getDialog().hide();
            // BaseDialog remains in the scene during its hide animation. Waiting one full
            // animation interval prevents the next synthetic input from hitting a stale modal
            // and prevents lifecycle counts from observing a dialog that is only fading out.
            delayNextAction(30f);
        }else if(FactoryScopeUI.picking()){
            clickToggleButton();
        }
    }

    int tileX(){
        return World.toTile(Core.camera.position.x);
    }

    int tileY(){
        return World.toTile(Core.camera.position.y);
    }

    Building place(Block block, int x, int y){
        for(int dx = -block.size; dx <= block.size; dx++){
            for(int dy = -block.size; dy <= block.size; dy++){
                Tile clear = world.tile(x + dx, y + dy);
                if(clear != null && clear.block() != Blocks.air) clear.remove();
            }
        }
        Tile tile = world.tile(x, y);
        tile.setBlock(block, Team.sharded, 0);
        return tile.build;
    }

    Tile emptyTileNearCamera(){
        for(int radius = 3; radius < 20; radius++){
            Tile tile = world.tile(tileX() - radius, tileY() - radius);
            if(tile != null && tile.block() == Blocks.air) return tile;
        }
        throw new IllegalStateException("no empty tile near the camera");
    }

    int countNamed(String name){
        int[] count = {0};
        walk(Core.scene.root, element -> {
            if(name.equals(element.name)) count[0]++;
        });
        return count[0];
    }

    int countFactoryScopeElements(){
        int[] count = {0};
        walk(Core.scene.root, element -> {
            if(element.name != null && element.name.startsWith("factoryscope")) count[0]++;
        });
        return count[0];
    }

    void walk(Group group, arc.func.Cons<Element> visitor){
        for(Element child : group.getChildren()){
            visitor.get(child);
            if(child instanceof Group inner) walk(inner, visitor);
        }
    }

    ScrollPane findPane(Group group){
        for(Element child : group.getChildren()){
            if(child instanceof ScrollPane pane) return pane;
            if(child instanceof Group inner){
                ScrollPane found = findPane(inner);
                if(found != null) return found;
            }
        }
        return null;
    }

    String describe(Building build){
        return build == null ? "null" : build.block.name + "@" + build.tile.x + "," + build.tile.y;
    }

    // ------------------------------------------------------------------ driver

    void scenario(String name){
        actions.add(() -> Log.info(TAG + " --- @", name));
    }

    void scenarioNow(String name){
        Log.info(TAG + " --- @", name);
    }

    void queue(Runnable action){
        actions.add(action);
    }

    void delayNextAction(float ticks){
        nextActionDelay = Math.max(nextActionDelay, ticks);
    }

    void pump(){
        if(actions.isEmpty()) return;
        Runnable next = actions.remove(0);
        try{
            next.run();
        }catch(Throwable t){
            failures.add("action threw " + t);
            Log.err(TAG + " action threw", t);
        }
        if(!actions.isEmpty()){
            float delay = nextActionDelay;
            nextActionDelay = TICKS_BETWEEN_ACTIONS;
            Time.runTask(delay, this::pump);
        }
    }

    void check(String what, boolean ok){
        check(what, ok, "");
    }

    void check(String what, boolean ok, String detail){
        checks++;
        if(ok){
            Log.info(TAG + "   PASS @", what);
        }else{
            failures.add(what + (detail.isEmpty() ? "" : " [" + detail + "]"));
            Log.err(TAG + "   FAIL " + what + (detail.isEmpty() ? "" : " [" + detail + "]"));
        }
    }

    void finish(){
        Log.info(TAG + " ===== @ checks, @ failures =====", checks, failures.size);
        for(String failure : failures) Log.err(TAG + " FAILURE: " + failure);
        Log.info(failures.isEmpty() ? TAG + " RESULT PASS" : TAG + " RESULT FAIL");
        Core.app.post(() -> Core.app.exit());
    }
}
