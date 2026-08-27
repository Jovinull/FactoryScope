package factoryscope.ui;

import arc.*;
import arc.input.*;
import arc.math.geom.*;
import arc.scene.*;
import arc.scene.event.*;
import arc.scene.ui.layout.*;
import arc.util.*;
import factoryscope.*;
import factoryscope.probe.*;
import mindustry.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.ui.*;

/**
 * Everything that puts FactoryScope on screen: the HUD toggle and the one-shot building picker.
 *
 * <h2>Why a picker overlay rather than TapEvent</h2>
 * Mindustry fires {@code TapEvent} only after the world has already handled the tap, so a configurable
 * block would open its own dialog underneath the diagnostic panel, and the event travels through
 * {@code Call.tileTap}, a networked remote. A transparent element in the HUD instead swallows the tap
 * before the world sees it - {@code Core.scene.hasMouse()} is what gates world input - which gives
 * one-shot selection with no double UI and no packets. It is removed the moment a choice is made, so
 * nothing of FactoryScope remains in the input path while the mod is idle.
 */
public final class FactoryScopeUI{
    private static final float BUTTON_SIZE = 48f;
    /** Clears the HUD's own bottom-left furniture (chat, saving indicator). */
    private static final float BUTTON_BOTTOM_PAD = 70f;

    private static final Vec2 scratch = new Vec2();

    private static FactoryScopePanel panel;
    private static Element picker;
    private static Table hint;
    private static boolean initialized;

    private FactoryScopeUI(){
    }

    /** Called once from {@code ClientLoadEvent}, when {@code Vars.ui} is guaranteed to exist. */
    public static void init(){
        if(initialized) return;
        initialized = true;

        panel = new FactoryScopePanel();
        buildToggle();

        //a world change invalidates any pending selection, and leaves nothing behind to leak
        Events.on(WorldLoadEvent.class, event -> reset());
        Events.on(ResetEvent.class, event -> reset());
    }

    private static void buildToggle(){
        Vars.ui.hudGroup.fill(table -> {
            table.name = "factoryscope";
            table.bottom().left();
            table.button(Icon.production, Styles.clearTogglei, FactoryScopeUI::toggle)
                .size(BUTTON_SIZE)
                .checked(button -> picking())
                .tooltip(FsBundle.ref("inspect.tooltip"))
                .name("factoryscope-toggle")
                .padLeft(6f).padBottom(BUTTON_BOTTOM_PAD);
        });
    }

    public static boolean picking(){
        return picker != null;
    }

    private static void toggle(){
        if(picking()){
            stopPicking();
        }else{
            startPicking();
        }
    }

    private static void startPicking(){
        if(picking() || !Vars.state.isGame()) return;

        Element overlay = new Element();
        overlay.name = "factoryscope-picker";
        overlay.setFillParent(true);
        overlay.touchable = Touchable.enabled;
        overlay.addListener(new InputListener(){
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button){
                return true;
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, KeyCode button){
                pick(event.stageX, event.stageY);
            }
        });
        //the picker is meaningless outside a running game
        overlay.update(() -> {
            if(!Vars.state.isGame()) stopPicking();
        });

        picker = overlay;
        Vars.ui.hudGroup.addChild(overlay);
        showHint();
    }

    private static void stopPicking(){
        if(picker != null){
            picker.remove();
            picker = null;
        }
        if(hint != null){
            hint.remove();
            hint = null;
        }
    }

    private static void showHint(){
        Table root = new Table();
        root.name = "factoryscope-hint";
        root.setFillParent(true);
        root.top();
        root.touchable = Touchable.disabled;
        root.table(Tex.buttonEdge3, inner -> {
            inner.margin(10f);
            inner.add(FsBundle.get("inspect.hint")).color(Pal.accent);
        }).padTop(120f);

        hint = root;
        Vars.ui.hudGroup.addChild(root);
    }

    /**
     * Resolves a tap in scene coordinates to a building and hands it to the panel.
     *
     * <p>The scene viewport projects stage coordinates back to Arc screen coordinates, which have their
     * origin at the bottom left, and the world camera unprojects those. {@code Scene.stageToScreenCoordinates}
     * looks like the obvious choice and is not: it flips Y to a top-left origin for the sake of platform
     * input APIs, which would mirror every selection about the middle of the screen.
     */
    static Building buildingAt(float stageX, float stageY){
        Core.scene.getViewport().project(scratch.set(stageX, stageY));
        Core.camera.unproject(scratch);
        return Vars.world.buildWorld(scratch.x, scratch.y);
    }

    private static void pick(float stageX, float stageY){
        stopPicking();

        Building build = buildingAt(stageX, stageY);

        //tapping empty ground is how the player cancels, so it is not an error worth reporting
        if(build == null) return;
        if(!inspect(build)){
            Vars.ui.showInfoToast(FsBundle.get("inspect.not-visible"), 2f);
        }
    }

    /**
     * Opens the diagnostic panel for a building.
     *
     * @return false when the building may not be inspected, which for now means the local player
     * cannot legitimately see it
     */
    public static boolean inspect(Building build){
        if(panel == null || build == null) return false;
        if(!MindustryFactoryProbe.canInspect(build, Vars.player == null ? null : Vars.player.team())){
            return false;
        }

        panel.inspect(build);
        return true;
    }

    /** The building the diagnostic panel is currently showing, or null. */
    public static Building inspected(){
        return panel == null ? null : panel.inspected();
    }

    /** Drops every transient reference; safe to call at any time. */
    public static void reset(){
        stopPicking();
        if(panel != null && panel.isShown()) panel.hide();
        FsLog.reset();
    }
}
