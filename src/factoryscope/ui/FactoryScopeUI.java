package factoryscope.ui;

import arc.*;
import arc.input.*;
import arc.math.geom.*;
import arc.scene.event.*;
import arc.scene.ui.layout.*;
import factoryscope.*;
import factoryscope.area.*;
import factoryscope.probe.*;
import mindustry.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.ui.*;

/**
 * Everything that puts FactoryScope on screen: the HUD toggle, the selection overlay, and the two
 * reports it can open.
 *
 * <h2>One entry point, two gestures</h2>
 * The HUD button arms a single overlay. A click on it inspects one building, exactly as in 0.1.x; a
 * drag selects a rectangle and reports on everything inside it. Adding a second HUD button for the
 * second gesture would have made the simpler of the two harder to find, and the overlay already owns
 * the pointer, so the gesture is where the distinction belongs. {@link InspectionOverlay} holds the
 * rules for telling one from the other.
 */
public final class FactoryScopeUI{
    private static final float BUTTON_SIZE = 48f;
    /** Clears the HUD's own bottom-left furniture (chat, saving indicator). */
    private static final float BUTTON_BOTTOM_PAD = 70f;

    private static final Vec2 scratch = new Vec2();

    private static FactoryScopePanel panel;
    private static AreaDiagnosticsDialog areaDialog;
    private static InspectionOverlay picker;
    private static Table hint;
    private static boolean initialized;

    private FactoryScopeUI(){
    }

    /** Called once from {@code ClientLoadEvent}, when {@code Vars.ui} is guaranteed to exist. */
    public static void init(){
        if(initialized) return;
        initialized = true;

        panel = new FactoryScopePanel();
        areaDialog = new AreaDiagnosticsDialog(FactoryScopeUI::startPicking, FactoryScopeUI::inspect);
        buildToggle();

        //the selection rectangle belongs to the world, not to the scene, so it is drawn from the world
        //render pass; registered once, and inert whenever nothing is being dragged
        Events.run(Trigger.drawOver, FactoryScopeUI::drawSelection);

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

        InspectionOverlay overlay = new InspectionOverlay(
            FactoryScopeUI::pickPoint, FactoryScopeUI::pickArea, FactoryScopeUI::stopPicking);
        //the overlay is meaningless outside a running game, and the player must always have a way out
        overlay.update(() -> {
            if(!Vars.state.isGame()){
                stopPicking();
            }else if(Core.input.keyTap(KeyCode.escape) || Core.input.keyTap(KeyCode.back)){
                stopPicking();
            }
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

    private static void drawSelection(){
        if(picker != null) picker.drawWorld();
    }

    /** Resolves a position in scene coordinates to a building. */
    static Building buildingAt(float stageX, float stageY){
        WorldCoords.fromStage(stageX, stageY, scratch);
        return Vars.world.buildWorld(scratch.x, scratch.y);
    }

    private static void pickPoint(Vec2 world){
        stopPicking();

        Building build = Vars.world.buildWorld(world.x, world.y);

        //tapping empty ground is how the player cancels, so it is not an error worth reporting
        if(build == null) return;
        if(!inspect(build)){
            Vars.ui.showInfoToast(FsBundle.get("inspect.not-visible"), 2f);
        }
    }

    private static void pickArea(AreaSelection selection){
        stopPicking();
        if(areaDialog == null || !Vars.state.isGame()) return;

        try{
            areaDialog.show(selection, AreaProbe.scan(selection, viewerTeam()));
        }catch(Exception e){
            FsLog.warnOnce("area-scan", "could not analyse the selected area", e);
            Vars.ui.showInfoToast(FsBundle.get("area.scan-failed"), 3f);
        }
    }

    private static mindustry.game.Team viewerTeam(){
        return Vars.player == null ? null : Vars.player.team();
    }

    /**
     * Opens the diagnostic panel for a building.
     *
     * @return false when the building may not be inspected, which for now means the local player
     * cannot legitimately see it
     */
    public static boolean inspect(Building build){
        if(panel == null || build == null) return false;
        if(!MindustryFactoryProbe.canInspect(build, viewerTeam())){
            return false;
        }

        panel.inspect(build);
        return true;
    }

    /** The building the diagnostic panel is currently showing, or null. */
    public static Building inspected(){
        return panel == null ? null : panel.inspected();
    }

    /** The area report on screen right now, or null. */
    public static AreaDiagnosticResult areaReport(){
        return areaDialog == null ? null : areaDialog.result();
    }

    /** The bounds the area report on screen was taken from, or null. */
    public static AreaSelection areaBounds(){
        return areaDialog == null || !areaDialog.showing() ? null : areaDialog.selection();
    }

    /** Re-runs the area report over the same bounds; the Refresh button does exactly this. */
    public static void refreshArea(){
        if(areaDialog != null && areaDialog.showing()) areaDialog.refresh();
    }

    /** Drops every transient reference; safe to call at any time. */
    public static void reset(){
        stopPicking();
        if(panel != null && panel.isShown()) panel.hide();
        if(areaDialog != null) areaDialog.clear();
        FsLog.reset();
    }
}
