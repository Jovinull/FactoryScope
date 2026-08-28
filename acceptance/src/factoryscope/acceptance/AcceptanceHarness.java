package factoryscope.acceptance;

import arc.*;
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
 * <p>Results are written to the game log as {@code [HARNESS]} lines and the process exits when the
 * suite finishes; {@code scripts/acceptance-test.ps1} reads those lines and turns them into an exit
 * code. This is a separate mod: it is never part of the FactoryScope artifact.
 */
public class AcceptanceHarness extends Mod{
    static final float TICKS_BETWEEN_ACTIONS = 12f;
    static final String TAG = "[HARNESS]";
    /** Intermediate pointer positions per drag; a real pointer never jumps from one corner to the other. */
    static final int DRAG_STEPS = 6;

    final Seq<String> failures = new Seq<>();
    final Seq<Runnable> actions = new Seq<>();
    int checks;

    Building upper, lower, target;
    int baselineElements;
    final Seq<Building> patch = new Seq<>();
    final Seq<AreaSelection> bounds = new Seq<>();
    final Seq<String> members = new Seq<>();

    public AcceptanceHarness(){
        Events.on(ClientLoadEvent.class, event -> Time.runTask(120f, this::start));
    }

    void start(){
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

    void plan(){
        scenario("the HUD toggle activates the picker");
        queue(this::closeAnyDialog);
        queue(this::ensurePickerOff);
        queue(this::clickToggleButton);
        queue(() -> {
            check("picker is active after clicking the HUD button", FactoryScopeUI.picking());
            check("exactly one HUD toggle exists", countNamed("factoryscope-toggle") == 1);
            check("exactly one picker overlay exists", countNamed("factoryscope-picker") == 1);
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

        repeatedUse(8);
        layout(1280, 720, 1f);
        layout(1920, 1080, 1f);
        layout(2560, 1440, 1f);
        layout(1280, 720, 1.5f);
        layout(2560, 1440, 2f);
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
        });

        areaScenarios();

        actions.add(this::finish);
        pump();
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
            baselineElements = countElements();
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
            int now = countElements();
            check("scene element count returned to baseline", now <= baselineElements,
                "baseline " + baselineElements + " now " + now);
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
        return tileX() + 5;
    }

    int ry(){
        return tileY() - 7;
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
        tinyDragIsAClick();
        mixedProblems();
        healthyArea();
        emptyArea();
        configurableBlocks();
        refreshAfterAChange();
        drillDown();
        areaLayout(1280, 720, 1f);
        areaLayout(1920, 1080, 1f);
        areaLayout(2560, 1440, 1.5f);
        queue(this::restoreLayout);
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
        queue(this::clickToggleButton);
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
        queue(this::clickToggleButton);
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
        queue(this::clickToggleButton);
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
        queue(this::clickToggleButton);
        queue(() -> clickBuilding(target));
        queue(() -> {
            check("the single-building panel opened", FactoryScopeUI.inspected() == target);
            check("no area report was opened by a click", FactoryScopeUI.areaBounds() == null);
        });
    }

    /** A press that wanders a few pixels is a click, not a one-tile area report. */
    void tinyDragIsAClick(){
        scenario("a drag shorter than the threshold is treated as a click");
        queue(this::closeAnyDialog);
        queue(this::clickToggleButton);
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
        queue(this::clickToggleButton);
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
        queue(this::clickToggleButton);
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
    }

    void emptyArea(){
        int x1 = rx() + 2, y1 = ry() + 2, x2 = rx() + 10, y2 = ry() + 8;

        scenario("an area with nothing in it opens a report that says so");
        queue(this::closeAnyDialog);
        queue(this::clearRegion);
        queue(this::clickToggleButton);
        queue(() -> dragTiles(x1, y1, x2, y2));
        queue(() -> {
            AreaDiagnosticResult report = FactoryScopeUI.areaReport();
            check("a report opened for the empty area", report != null);
            if(report == null) return;
            check("it reports nothing selected", report.empty() && report.summary.selected == 0,
                report.summary.toString());
            check("an empty area is not called healthy", !report.healthy());
        });
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
        queue(this::clickToggleButton);
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
        queue(this::clickToggleButton);
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
        queue(this::clickToggleButton);
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
        queue(this::clickToggleButton);
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
            baselineElements = countElements();
        });

        for(int i = 0; i < cycles; i++){
            queue(this::clickToggleButton);
            queue(() -> dragTiles(x1, y1, x2, y2));
            queue(this::closeAnyDialog);
        }

        queue(() -> {
            check("the picker is off after the last area cycle", !FactoryScopeUI.picking());
            check("still exactly one HUD toggle", countNamed("factoryscope-toggle") == 1);
            check("no leftover selection overlay", countNamed("factoryscope-picker") == 0);
            check("no leftover hint", countNamed("factoryscope-hint") == 0);
            int now = countElements();
            check("scene element count returned to baseline after area use", now <= baselineElements,
                "baseline " + baselineElements + " now " + now);
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
        queue(this::clickToggleButton);
        queue(() -> dragTiles(x1, y1, x2, y2));
        queue(() -> check("a report is open before the world change", FactoryScopeUI.areaReport() != null));
        queue(() -> Events.fire(new WorldLoadEvent()));
        queue(() -> {
            check("the area report was cleared on world load", FactoryScopeUI.areaReport() == null);
            check("the bounds were cleared too", FactoryScopeUI.areaBounds() == null);
            check("the picker was cleared on world load", !FactoryScopeUI.picking());
        });

        scenario("a world change during a selection cancels it");
        queue(this::clickToggleButton);
        queue(() -> check("the picker is armed", FactoryScopeUI.picking()));
        queue(() -> Events.fire(new WorldLoadEvent()));
        queue(() -> check("the armed picker was cancelled", !FactoryScopeUI.picking()));
    }

    // ------------------------------------------------------------------ area helpers

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
        Element element = Core.scene.find(name);
        if(element == null){
            check("an element named " + name + " exists", false);
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
        Tile tile = world.tile(x, y);
        if(tile == null) return null;
        tile.setBlock(block, Team.sharded, 0);
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
        if(Core.scene.getDialog() != null) Core.scene.getDialog().hide();
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

    int countElements(){
        int[] count = {0};
        walk(Core.scene.root, element -> count[0]++);
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

    void pump(){
        if(actions.isEmpty()) return;
        Runnable next = actions.remove(0);
        try{
            next.run();
        }catch(Throwable t){
            failures.add("action threw " + t);
            Log.err(TAG + " action threw", t);
        }
        if(!actions.isEmpty()) Time.runTask(TICKS_BETWEEN_ACTIONS, this::pump);
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
