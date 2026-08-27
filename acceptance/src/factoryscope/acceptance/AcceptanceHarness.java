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

    final Seq<String> failures = new Seq<>();
    final Seq<Runnable> actions = new Seq<>();
    int checks;

    Building upper, lower, target;
    int baselineElements;

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
