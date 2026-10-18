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
import factoryscope.ui.*;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.world.*;

import static mindustry.Vars.*;

/** Area Diagnostics interaction, layout, lifecycle and localization scenarios. */
final class AreaScenarios{
    private static final String TAG = AcceptanceHarness.TAG;
    private final AcceptanceHarness harness;
    private Building target;
    private int baselineFactoryScopeElements;
    private final Seq<Building> patch = new Seq<>();
    private final Seq<AreaSelection> bounds = new Seq<>();
    private final Seq<String> members = new Seq<>();

    AreaScenarios(AcceptanceHarness harness){ this.harness = harness; }

    void queue(){ areaScenarios(); }

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
                onScreen(rx(), ry()) && onScreen(rx() + AcceptanceHarness.REGION_WIDTH, ry() + AcceptanceHarness.REGION_HEIGHT),
                "camera " + (int)Core.camera.width + "x" + (int)Core.camera.height
                    + " scale " + renderer.camerascale);
        });

        dragDirections();
        multiTileEdge();
        singleClickStillInspects();
        harness.itemNetwork.queueBeforeScopes();
        harness.itemNetwork.queueTinyDrag();
        harness.itemNetwork.queueAfterTinyDrag();
        new PowerScopeScenarios(harness).queue();
        new LiquidScopeScenarios(harness).queue();
        harness.itemNetwork.queueTransportFamilies();
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
        queue(() -> capture("single-building-inspector"));
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
            //normal findings never form an issue group, so these reasons have no issue headline
            if(reason == DiagnosticReason.active || reason == DiagnosticReason.limitedSupport
                || reason == DiagnosticReason.turretIdle) continue;
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
            {"trace.unsupported-at", Blocks.plastaniumConveyor.localizedName, "123", "61"}, {"trace.endpoints", "2"},
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
        check("single-member PowerScope counts avoid plural agreement errors", oneMember.equals("1 / 1"), oneMember);
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
        int x1 = rx(), y1 = ry(), x2 = rx() + AcceptanceHarness.REGION_WIDTH, y2 = ry() + 13;

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


    private void scenario(String name){ harness.scenario(name); }
    private void scenarioNow(String name){ harness.scenarioNow(name); }
    private void queue(Runnable action){ harness.queue(action); }
    private void check(String what, boolean ok){ harness.check(what, ok); }
    private void check(String what, boolean ok, String detail){ harness.check(what, ok, detail); }
    private void delayNextAction(float ticks){ harness.delayNextAction(ticks); }
    private void closeAnyDialog(){ harness.closeAnyDialog(); }
    private void ensurePickerOff(){ harness.ensurePickerOff(); }
    private void clearRegion(){ harness.clearRegion(); }
    private int rx(){ return harness.rx(); }
    private int ry(){ return harness.ry(); }
    private void clickBuilding(Building building){ harness.clickBuilding(building); }
    private void clickToggleButton(){ harness.clickToggleButton(); }
    private void clickNamed(String name){ harness.clickNamed(name); }
    private void dragTiles(int x1, int y1, int x2, int y2){ harness.dragTiles(x1, y1, x2, y2); }
    private void capture(String name){ harness.capture(name); }
    private Building placeAt(Block block, int x, int y){ return harness.placeAt(block, x, y); }
    private Building placeAt(Block block, int x, int y, int rotation){ return harness.placeAt(block, x, y, rotation); }
    private boolean onScreen(int x, int y){ return harness.onScreen(x, y); }
    private boolean dialogShows(String text){ return harness.dialogShows(text); }
    private boolean sceneShows(String text){ return harness.sceneShows(text); }
    private boolean dialogHasNamed(String name){ return harness.dialogHasNamed(name); }
    private void scrollReportToBottom(){ harness.scrollReportToBottom(); }
    private void restoreLayout(){ harness.restoreLayout(); }
    private void checkFits(String label){ harness.checkFits(label); }
    private ScrollPane findPane(Group group){ return harness.findPane(group); }
    private int standing(Seq<Building> buildings){ return harness.standing(buildings); }
    private String membersOf(AreaDiagnosticResult report){ return harness.membersOf(report); }
    private Building supply(Building building, mindustry.type.Item item, int amount){ return harness.supply(building, item, amount); }
    private int countNamed(String name){ return harness.countNamed(name); }
    private int countFactoryScopeElements(){ return harness.countFactoryScopeElements(); }
    private void clickNamed(String name, int occurrence){ harness.clickNamed(name, occurrence); }
    private void touchButton(int x, int y, KeyCode button){ harness.touchButton(x, y, button); }
    private boolean breakingBlocks(){ return harness.breakingBlocks(); }
    private String describe(Building building){ return harness.describe(building); }
}
