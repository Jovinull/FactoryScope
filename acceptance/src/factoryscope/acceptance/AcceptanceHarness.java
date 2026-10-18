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
import factoryscope.mpshared.*;
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

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

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

    final AcceptanceContext context = new AcceptanceContext();
    final MultiplayerScenarios multiplayer = new MultiplayerScenarios(this);
    final ItemNetworkScenarios itemNetwork = new ItemNetworkScenarios(this);
    final AreaScenarios area = new AreaScenarios(this);

    Building upper, lower, target;
    int baselineFactoryScopeElements;

    public AcceptanceHarness(){
        Events.on(ClientLoadEvent.class, event -> Time.runTask(120f, this::start));
    }

    void start(){
        String server = System.getProperty("factoryscope.acceptance.server");
        if(server != null){
            multiplayer.start(server);
            return;
        }
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

    void checkOnlyHarnessModsLoaded(){
        Seq<String> unexpected = new Seq<>();
        for(Mods.LoadedMod mod : mods.list()){
            if(!mod.name.equals("factory-scope") && !mod.name.equals("factory-scope-acceptance")){
                unexpected.add(mod.name);
            }
        }
        check("the sandbox loaded no external mods", unexpected.isEmpty(), unexpected.toString(", "));
    }

    void plan(){
        int powerFixtureRepetitions = Integer.getInteger("factoryscope.power-fixture-repetitions", 0);
        if(powerFixtureRepetitions > 0){
            new PowerFixtureStress(this, powerFixtureRepetitions).start();
            return;
        }
        new AcceptancePlan(this).queue();
    }

    void armPicker(){ area.armPicker(); }

    void restoreCamera(){ area.restoreCamera(); }

    void checkLiquidLocalization(){ area.checkLiquidLocalization(); }

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
        checkRequestedLocaleResolution();

        //format() resolves through I18NBundle.get(), which renders an absent key as ???key???
        String absent = FsBundle.format("definitely.not.a.key", 1);
        check("an absent key never leaks ??? into the panel", !absent.contains("???"), absent);
    }

    /**
     * Opens the single-building panel of one block per production family through the real click path and
     * checks what the panel renders: the model applied, the status headline, and the sections, details and
     * notes that family adds. Every expected string is looked up in the active locale, so the same checks
     * prove the translated panel in each acceptance run.
     */
    void productionPanel(String name, arc.func.Prov<Building> placer, ProductionFamily family, String statusKey,
                         String shot, String... shownKeys){
        scenario(name);
        queue(this::closeAnyDialog);
        queue(() -> target = placer.get());
        queue(this::clickToggleButton);
        queue(() -> clickBuilding(target));
        queue(() -> {
            check("the panel opened for it", FactoryScopeUI.inspected() == target);
            FactorySnapshot snapshot = MindustryFactoryProbe.probe(target);
            check("FactoryScope applies the " + family + " model", snapshot.family == family, String.valueOf(snapshot.family));
            if(statusKey != null){
                check("the panel headline reads " + statusKey, dialogShows(FsBundle.get(statusKey)), FsBundle.get(statusKey));
            }
            for(String key : shownKeys){
                check("the panel shows " + key, dialogShows(FsBundle.get(key)), FsBundle.get(key));
            }
            check("the panel does not call a modeled block limited", !dialogShows(FsBundle.get("panel.limited-support")));
        });
        queue(() -> capture(shot));
        queue(this::closeAnyDialog);
    }


    // ------------------------------------------------------------------ multiplayer

    /**
     * Closing a FactoryScope dialog can bring back the one it was opened from (a trace returns to its network,
     * a network to its area report), so a scenario that needs the world clear closes the whole stack.
     */
    void closeDialogStack(){
        for(int i = 0; i < 4; i++) queue(this::closeAnyDialog);
    }

    void dragArea(AreaSelection area){
        dragTiles(area.minX, area.minY, area.maxX, area.maxY);
    }

    void checkRequestedLocaleResolution(){
        String requested = System.getProperty("factoryscope.acceptance.locale");
        if(requested == null) return;

        java.util.Map<String, String> activeText = java.util.Map.of(
            "en", "Running", "en-us", "Running", "pt-br", "Em funcionamento",
            "ru", "Работает", "zh-cn", "正在运行", "ko", "작동 중", "es", "En funcionamiento"
        );
        String requestedTag = requested.replace('_', '-').toLowerCase(Locale.ROOT);
        String requestedLanguage = Locale.forLanguageTag(requestedTag).getLanguage();
        String resolved = String.valueOf(Core.bundle.getLocale());
        if(resolved.equals("null") || resolved.isBlank()) resolved = "en";
        String resolvedTag = resolved.replace('_', '-').toLowerCase(Locale.ROOT);
        String resolvedLanguage = Locale.forLanguageTag(resolvedTag).getLanguage();
        boolean localeMatches = requestedTag.contains("-")
            ? requestedTag.equals(resolvedTag)
            : requestedLanguage.equals(resolvedLanguage);

        check("Mindustry resolves the requested acceptance locale",
            localeMatches, requested + " -> " + resolved);
        String expected = activeText.get(requestedTag);
        String actual = FsBundle.get("status.active");
        check("FactoryScope loads the requested locale bundle",
            expected != null && expected.equals(actual), "requested=" + requested + ", status.active=" + actual);
        Log.info(TAG + " locale evidence: requested @, resolved @, area.title '@', network.title '@', trace.title '@', power.title '@', liquid.title '@'",
            requested, resolved, FsBundle.get("area.title"), FsBundle.get("network.title"), FsBundle.get("trace.title"),
            FsBundle.get("power.title"), FsBundle.get("liquid.title"));
    }


    // ------------------------------------------------------------------ area diagnostics

    /**
     * Every area scenario is built in the same patch of world, cleared before each one.
     *
     * <p>It sits beside the core rather than on it - the core cannot be removed - and close enough to
     * the camera that both corners of any drag are on screen at the zoom the scenarios set.
     */
    int rx(){
        return context.areaOriginX(tileX() + 5);
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
        return context.areaOriginY(tileY() - 7);
    }

    static final int REGION_WIDTH = 16;
    static final int REGION_HEIGHT = 14;

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

    boolean dialogHasNamed(String name){
        Dialog dialog = Core.scene.getDialog();
        if(dialog == null) return false;
        boolean[] found = {false};
        walk(dialog, element -> { if(name.equals(element.name)) found[0] = true; });
        return found[0];
    }

    void clickNamedInDialog(String name){
        clickNamedInDialog(name, 0);
    }

    void clickNamedInDialog(String name, int occurrence){
        Dialog dialog = Core.scene.getDialog();
        Element[] found = {null};
        int[] matches = {0};
        if(dialog != null) walk(dialog, element -> {
            if(found[0] == null && name.equals(element.name) && matches[0]++ == occurrence) found[0] = element;
        });
        Element element = found[0];
        check("dialog element " + name + " exists at index " + occurrence, element != null);
        if(element == null) return;
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
        context.actions.add(() -> Log.info(TAG + " --- @", name));
    }

    void scenarioNow(String name){
        Log.info(TAG + " --- @", name);
    }

    void queue(Runnable action){
        context.actions.add(action);
    }

    void delayNextAction(float ticks){
        context.nextActionDelay = Math.max(context.nextActionDelay, ticks);
    }

    void pump(){
        if(context.actions.isEmpty()) return;
        Runnable next = context.actions.remove(0);
        try{
            next.run();
        }catch(Throwable t){
            context.failures.add("action threw " + t);
            Log.err(TAG + " action threw", t);
        }
        if(!context.actions.isEmpty()){
            float delay = context.nextActionDelay;
            context.nextActionDelay = TICKS_BETWEEN_ACTIONS;
            Time.runTask(delay, this::pump);
        }
    }

    void check(String what, boolean ok){
        check(what, ok, "");
    }

    void check(String what, boolean ok, String detail){
        context.checks++;
        if(ok){
            Log.info(TAG + "   PASS @", what);
        }else{
            context.failures.add(what + (detail.isEmpty() ? "" : " [" + detail + "]"));
            Log.err(TAG + "   FAIL " + what + (detail.isEmpty() ? "" : " [" + detail + "]"));
        }
    }

    void finish(){
        Log.info(TAG + " ===== @ checks, @ failures =====", context.checks, context.failures.size);
        for(String failure : context.failures) Log.err(TAG + " FAILURE: " + failure);
        Log.info(context.failures.isEmpty() ? TAG + " RESULT PASS" : TAG + " RESULT FAIL");
        Core.app.post(() -> Core.app.exit());
    }
}
