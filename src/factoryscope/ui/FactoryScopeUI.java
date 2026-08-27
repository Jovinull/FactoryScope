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
 * <h2>Why a picker overlay instead of TapEvent</h2>
 * A transparent element added to the HUD swallows the tap before the world sees it, so selecting a
 * factory never also opens that block's own configuration window. It works identically for a mouse
 * click and a touch, and it disappears the instant a choice is made - nothing of FactoryScope stays in
 * the input path while the mod is idle.
 */
public final class FactoryScopeUI{
    private static final float BUTTON_SIZE = 48f;
    /** Clears the HUD's own bottom-left furniture (chat, saving indicator). */
    private static final float BUTTON_BOTTOM_PAD = 70f;

    private static FactoryScopePanel panel;
    private static Element picker;
    private static Table hint;

    private FactoryScopeUI(){
    }

    /** Called once from {@code ClientLoadEvent}, when {@code Vars.ui} is guaranteed to exist. */
    public static void init(){
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

    /** Resolves a tap in scene coordinates to a building and hands it to the panel. */
    private static void pick(float stageX, float stageY){
        stopPicking();

        Vec2 screen = Core.scene.stageToScreenCoordinates(Tmp.v1.set(stageX, stageY));
        Vec2 world = Core.input.mouseWorld(screen.x, screen.y);
        Building build = Vars.world.buildWorld(world.x, world.y);

        //tapping empty ground is how the player cancels, so it is not an error worth reporting
        if(build == null) return;
        if(!MindustryFactoryProbe.canInspect(build, Vars.player == null ? null : Vars.player.team())){
            Vars.ui.showInfoToast(FsBundle.get("inspect.not-visible"), 2f);
            return;
        }

        panel.inspect(build);
    }

    /** Drops every transient reference; safe to call at any time. */
    public static void reset(){
        stopPicking();
        if(panel != null && panel.isShown()) panel.hide();
        FsLog.reset();
    }
}
