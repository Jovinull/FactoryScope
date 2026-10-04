package factoryscope.ui;

import arc.*;
import arc.input.*;
import arc.math.geom.*;
import arc.scene.Element;
import arc.scene.event.*;
import arc.scene.ui.layout.*;
import factoryscope.*;
import factoryscope.area.*;
import factoryscope.liquid.LiquidNetwork;
import factoryscope.model.ResourceRef;
import factoryscope.probe.*;
import factoryscope.power.PowerGridReport;
import factoryscope.trace.*;
import mindustry.*;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.ui.*;

/**
 * Everything that puts FactoryScope on screen: the HUD toggle, the selection overlay, and the reports
 * it can open.
 *
 * <h2>One entry point, two gestures</h2>
 * The HUD button arms a single overlay. A click on it inspects one building, exactly as in 0.1.x; a
 * drag selects a rectangle and reports on everything inside it. Adding a second HUD button for the
 * second gesture would have made the simpler of the two harder to find, and the overlay already owns
 * the pointer, so the gesture is where the distinction belongs. {@link InspectionOverlay} holds the
 * rules for telling one from the other.
 *
 * <h2>Navigation</h2>
 * Reports are snapshots with bounded navigation: single-building PowerScope returns to its inspector,
 * and area PowerScope returns to its area report. There is no general history stack, and transient state
 * is dropped when the world changes.
 */
public final class FactoryScopeUI{
    private static final float BUTTON_SIZE = 48f;

    private static FactoryScopePanel panel;
    private static AreaDiagnosticsDialog areaDialog;
    private static PowerDialog powerDialog;
    private static InspectionOverlay picker;
    private static LocateOverlay locate;
    private static NetworkOverlay networkOverlay;
    private static NetworkViewOverlay networkView;
    private static PowerOverlay powerOverlay;
    private static PowerViewOverlay powerView;
    private static LiquidOverlay liquidOverlay;
    private static LiquidViewOverlay liquidView;
    private static ResourceRef liquidSelected;
    private static Table hint;
    private static Element toggleAnchor;
    private static final Vec2 togglePosition = new Vec2();
    private static TraceRequest pendingTrace;
    private static LiquidTraceRequest pendingLiquidTrace;
    private static boolean initialized;
    private static boolean powerFromArea;

    private FactoryScopeUI(){
    }

    /** Called once from {@code ClientLoadEvent}, when {@code Vars.ui} is guaranteed to exist. */
    public static void init(){
        if(initialized) return;
        initialized = true;

        panel = new FactoryScopePanel();
        panel.setOnTrace(FactoryScopeUI::traceInput);
        panel.setOnTraceLiquid(FactoryScopeUI::traceLiquidInput);
        panel.setOnInspectPower(FactoryScopeUI::inspectPowerGrid);
        powerDialog = new PowerDialog();
        areaDialog = new AreaDiagnosticsDialog(
            FactoryScopeUI::startPicking, FactoryScopeUI::inspect, FactoryScopeUI::startLocating);
        powerDialog.setOnViewWorld(FactoryScopeUI::viewPowerInWorld);
        buildToggle();

        //the selection rectangle and the locate marker belong to the world, not to the scene, so they
        //are drawn from the world render pass; registered once, and inert when nothing needs drawing
        Events.run(Trigger.drawOver, FactoryScopeUI::drawWorld);

        //a world change invalidates any pending selection, and leaves nothing behind to leak
        Events.on(WorldLoadEvent.class, event -> reset());
        Events.on(ResetEvent.class, event -> reset());
    }

    private static void buildToggle(){
        Table table = new Table();
        table.name = "factoryscope";
        table.setSize(BUTTON_SIZE, BUTTON_SIZE);
        table.color.a = 0f;
        table.touchable = Touchable.disabled;
        table.button(Icon.production, Styles.clearTogglei, FactoryScopeUI::toggle)
            .size(BUTTON_SIZE)
            .checked(button -> picking())
            .tooltip(FsBundle.ref("inspect.tooltip"))
            .name("factoryscope-toggle");
        table.update(() -> {
            // HudFragment reserves this named slot for its top-left wave/editor panel on every
            // platform. Place the toggle directly below that live layout element, so resizing and
            // UI scaling move it with the HUD rather than relying on an assumed bottom pixel pad.
            // HudFragment may replace its child tables when HUD settings, scaling or the world
            // layout changes. Re-resolve the stable name so we never keep a still-parented but
            // obsolete element from the previous layout.
            toggleAnchor = Vars.ui.hudGroup.find("waves/editor");
            if(toggleAnchor == null || !Vars.ui.hudfrag.shown()){
                //Keep this updater attached and acting so the toggle can recover when the HUD
                //returns. Hiding the element itself would stop Arc from acting it on some parents.
                table.color.a = 0f;
                table.touchable = Touchable.disabled;
                return;
            }
            table.color.a = 1f;
            table.touchable = Touchable.enabled;
            toggleAnchor.localToStageCoordinates(togglePosition.set(0f, 0f));
            Vars.ui.hudGroup.stageToLocalCoordinates(togglePosition);
            table.setPosition(togglePosition.x, togglePosition.y - table.getHeight());
        });
        Vars.ui.hudGroup.addChild(table);
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
        //a new selection replaces whatever the player was looking at; nothing stacks
        stopLocating();

        InspectionOverlay overlay = new InspectionOverlay(
            FactoryScopeUI::pickPoint, FactoryScopeUI::pickArea, FactoryScopeUI::stopPicking);
        //the overlay is meaningless outside a running game, and the player must always have a way out.
        //Escape and the Android back key also open the pause menu, as they do everywhere in Mindustry;
        //cancelling as well is what leaves a sane state behind once that menu is closed again.
        overlay.update(() -> {
            if(!Vars.state.isGame() || Core.input.keyTap(KeyCode.escape) || Core.input.keyTap(KeyCode.back)){
                stopPicking();
            }
        });

        picker = overlay;
        Vars.ui.hudGroup.addChild(overlay);
        showHint();
    }

    private static void stopPicking(){
        pendingTrace = null;
        pendingLiquidTrace = null;
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

    private static void drawWorld(){
        if(picker != null) picker.drawWorld();
        if(locate != null) locate.drawWorld();
        if(networkOverlay != null) networkOverlay.draw();
        if(powerOverlay != null) powerOverlay.draw();
        if(liquidOverlay != null) liquidOverlay.draw();
    }

    // ------------------------------------------------------------------ selection

    private static void pickPoint(Vec2 world){
        stopPicking();

        Building build = Vars.world.buildWorld(world.x, world.y);

        //tapping empty ground is how the player cancels, so it is not an error worth reporting
        if(build == null) return;
        //Fogged buildings remain in the client world model. Do not confirm their presence by
        //showing a special toast when the player taps their otherwise empty-looking tile.
        if(!visibleToCurrentPlayer(build)) return;
        inspect(build);
    }

    private static void pickArea(AreaSelection selection){
        TraceRequest request = pendingTrace;
        LiquidTraceRequest liquidRequest = pendingLiquidTrace;
        pendingTrace = null;
        pendingLiquidTrace = null;
        stopPicking();
        stopLocating();
        if(areaDialog == null || !Vars.state.isGame()) return;

        //a drag that ran off the edge of the map reports the part of it that exists, rather than
        //claiming an area larger than the world; a drag entirely outside it simply finds nothing
        AreaSelection bounded = selection.clampedTo(Vars.world.width(), Vars.world.height());
        AreaSelection area = bounded == null ? selection : bounded;

        try{
            areaDialog.show(area, AreaProbe.scan(area, viewerTeam()));
            if(request != null){
                if(panel != null && panel.isShown()) panel.hide();
                areaDialog.showTrace(request.target, request.item);
            }else if(liquidRequest != null){
                if(panel != null && panel.isShown()) panel.hide();
                areaDialog.showLiquidTrace(liquidRequest.target, liquidRequest.liquid, TraceDirection.input);
            }
        }catch(Exception e){
            FsLog.warnOnce("area-scan", "could not analyse the selected area", e);
            Vars.ui.showInfoToast(FsBundle.get("area.scan-failed"), 3f);
        }
    }

    private static Team viewerTeam(){
        return Vars.player == null ? null : Vars.player.team();
    }

    /** A navigation marker may only be started or retained while a local viewer can see its target. */
    static boolean visibleToCurrentPlayer(Building build){
        return Vars.player != null && MindustryFactoryProbe.canInspect(build, Vars.player.team());
    }

    /** Resolves a report reference only for a navigation action allowed by current fog visibility. */
    static Building visibleTarget(BuildingRef ref){
        Building build = AreaProbe.resolve(ref);
        return build != null && visibleToCurrentPlayer(build) ? build : null;
    }

    /** Hidden and stale targets intentionally share one response; distinguishing them leaks live state. */
    static void showTargetUnavailable(){
        Vars.ui.showInfoToast(FsBundle.get("target.unavailable"), 2f);
    }

    /** A denied Locate was invoked from a report already animating closed; reopen only after it exits. */
    private static void returnAfterDialogHides(Runnable action){
        if(action == null) return;
        arc.util.Time.runTask(30f, () -> {
            if(Core.scene != null && Core.scene.getDialog() == null) action.run();
        });
    }

    // ------------------------------------------------------------------ locating

    /** Uncovers the world and marks one building the area report pointed at. */
    private static void startLocating(BuildingRef ref){
        stopLocating();

        Building build = visibleTarget(ref);
        if(build == null){
            //The target may be gone or merely hidden; a frozen report must not distinguish those states.
            showTargetUnavailable();
            returnAfterDialogHides(FactoryScopeUI::returnToReport);
            return;
        }

        locate = new LocateOverlay(ref, build, FactoryScopeUI::returnToReport, FactoryScopeUI::stopLocating);
    }

    static void locateFromNetwork(BuildingRef ref, Runnable onReturn){
        stopLocating();
        Building build = visibleTarget(ref);
        if(build == null){
            showTargetUnavailable();
            returnAfterDialogHides(onReturn);
            return;
        }
        locate = new LocateOverlay(ref, build, () -> {
            stopLocating();
            if(onReturn != null) onReturn.run();
        }, FactoryScopeUI::stopLocating);
    }

    private static void stopLocating(){
        if(locate != null){
            locate.remove();
            locate = null;
        }
    }

    private static void returnToReport(){
        stopLocating();
        if(areaDialog == null) return;
        if(areaDialog.isShown()){
            //A target can become hidden while the report is still animating closed. Reopening during
            //that transition is ignored by BaseDialog, so wait for its hide animation to finish.
            returnAfterDialogHides(() -> {
                if(areaDialog != null) areaDialog.reopen();
            });
        }else{
            areaDialog.reopen();
        }
    }

    /** True while the world is uncovered with a located building marked. */
    public static boolean locating(){
        return locate != null;
    }

    static void showNetworkOverlay(factoryscope.network.ItemNetwork network, factoryscope.model.ResourceRef item){
        networkOverlay = network == null ? null : new NetworkOverlay(network, item);
    }

    static void showNetworkOverlay(factoryscope.network.ItemNetwork network, factoryscope.model.ResourceRef item,
                                   SupplyTrace trace){
        networkOverlay = network == null ? null : new NetworkOverlay(network, item, trace);
    }

    static void stopNetworkOverlay(){
        networkOverlay = null;
    }

    static void showLiquidOverlay(LiquidNetwork network, ResourceRef liquid, factoryscope.liquid.LiquidTrace trace){
        liquidSelected = liquid;
        liquidOverlay = network == null ? null : new LiquidOverlay(network, liquid, trace);
    }

    static void stopLiquidOverlay(){
        liquidOverlay = null;
        liquidSelected = null;
    }

    static void viewNetworkInWorld(factoryscope.network.ItemNetwork network, factoryscope.model.ResourceRef item,
                                   SupplyTrace trace,
                                   Runnable onReturn, Runnable onDismiss){
        stopNetworkView();
        showNetworkOverlay(network, item, trace);
        networkView = new NetworkViewOverlay(item, trace, () -> {
            stopNetworkView();
            onReturn.run();
        }, () -> {
            stopNetworkView();
            stopNetworkOverlay();
            onDismiss.run();
        });
    }

    private static void stopNetworkView(){
        if(networkView != null){
            networkView.remove();
            networkView = null;
        }
    }

    // ------------------------------------------------------------------ reports

    /**
     * Opens the diagnostic panel for a building.
     *
     * @return false when the building may not be inspected, which for now means the local player
     * cannot legitimately see it
     */
    public static boolean inspect(Building build){
        if(panel == null || build == null) return false;
        if(!MindustryFactoryProbe.canInspect(build, viewerTeam())) return false;

        panel.inspect(build);
        return true;
    }

    static boolean inspect(Building build, Runnable onDismiss){
        if(panel == null || build == null) return false;
        if(!MindustryFactoryProbe.canInspect(build, viewerTeam())) return false;

        panel.inspect(build, onDismiss);
        return true;
    }

    private static void inspectPowerGrid(Building build){
        if(powerDialog == null || build == null || build.power == null) return;
        powerFromArea = false;
        BuildingRef ref = AreaProbe.refOf(build);
        powerDialog.show(MindustryPowerProbe.scan(build, viewerTeam()), () -> {
            Building current = AreaProbe.resolve(ref);
            if(current == null || !MindustryFactoryProbe.canInspect(current, viewerTeam())){
                showTargetUnavailable();
                powerDialog.clearReport();
                return;
            }
            powerDialog.refresh(MindustryPowerProbe.scan(current, viewerTeam()));
        }, FactoryScopeUI::inspect, FactoryScopeUI::locatePowerMember, null);
    }

    static void showAreaPower(factoryscope.power.PowerGridReport report, Runnable refreshArea){
        if(powerDialog == null) return;
        powerFromArea = true;
        if(areaDialog != null) areaDialog.hide();
        powerDialog.show(report, () -> {
            if(refreshArea != null) refreshArea.run();
            AreaDiagnosticResult refreshed = areaDialog == null ? null : areaDialog.heldResult();
            if(refreshed != null) powerDialog.refresh(refreshed.power);
        }, FactoryScopeUI::inspect, FactoryScopeUI::locatePowerMember, () -> {
            powerFromArea = false;
            if(areaDialog != null) areaDialog.reopen();
        });
    }

    private static void locatePowerMember(BuildingRef ref){
        if(powerDialog != null) powerDialog.hide();
        locateFromNetwork(ref, () -> {
            if(powerDialog != null) powerDialog.reopen();
        });
    }

    private static void viewPowerInWorld(){
        if(powerDialog == null || powerDialog.report() == null) return;
        stopPowerView();
        PowerGridReport report = powerDialog.report();
        powerOverlay = new PowerOverlay(report);
        powerView = new PowerViewOverlay(() -> {
            stopPowerView();
            powerDialog.reopen();
        }, () -> {
            boolean returnToArea = powerFromArea;
            powerFromArea = false;
            stopPowerView();
            powerDialog.clearReport();
            if(returnToArea && areaDialog != null) areaDialog.clear();
        });
    }

    private static void stopPowerView(){
        powerOverlay = null;
        if(powerView != null){
            powerView.remove();
            powerView = null;
        }
    }

    static void viewLiquidInWorld(LiquidNetwork network, Runnable onReturn, Runnable onDismiss){
        stopLiquidView();
        showLiquidOverlay(network, liquidSelected, null);
        liquidView = new LiquidViewOverlay(liquidSelected, () -> {
            stopLiquidView();
            onReturn.run();
        }, () -> {
            stopLiquidView();
            stopLiquidOverlay();
            onDismiss.run();
        });
    }

    private static void stopLiquidView(){
        if(liquidView != null){
            liquidView.remove();
            liquidView = null;
        }
    }

    /** The building the diagnostic panel is currently showing, or null. */
    public static Building inspected(){
        return panel == null ? null : panel.inspected();
    }

    /** The area report on screen right now, or null. */
    public static AreaDiagnosticResult areaReport(){
        return areaDialog == null ? null : areaDialog.result();
    }

    /** Immutable power-grid snapshot currently held by PowerScope, or null when no report is open. */
    public static PowerGridReport powerReport(){
        return powerDialog == null ? null : powerDialog.report();
    }

    /** Immutable liquid-trace snapshot currently held by LiquidScope, or null outside a trace. */
    public static factoryscope.liquid.LiquidTrace liquidTrace(){
        return areaDialog == null ? null : areaDialog.liquidTrace();
    }

    /** True when a report is being held for the player to come back to, whether on screen or not. */
    public static boolean areaReportHeld(){
        return areaDialog != null && areaDialog.hasReport();
    }

    /** The bounds the area report on screen was taken from, or null. */
    public static AreaSelection areaBounds(){
        return areaDialog == null || !areaDialog.showing() ? null : areaDialog.selection();
    }

    /** Re-runs the area report over the same bounds; the Refresh button does exactly this. */
    public static void refreshArea(){
        if(areaDialog != null && areaDialog.showing()) areaDialog.refresh();
    }

    private static void traceInput(Building build, ResourceRef item){
        if(build == null || item == null || areaDialog == null) return;
        BuildingRef ref = AreaProbe.refOf(build);
        AreaDiagnosticResult held = areaDialog.showing() ? areaDialog.heldResult() : null;
        if(held != null && held.entries.stream().anyMatch(entry -> entry.ref.equals(ref))){
            if(panel != null && panel.isShown()){
                panel.cancelOnDismiss();
                panel.hide();
            }
            areaDialog.showTrace(ref, item);
            return;
        }

        pendingTrace = new TraceRequest(ref, item);
        if(panel != null && panel.isShown()){
            panel.cancelOnDismiss();
            panel.hide();
        }
        startPicking();
        Vars.ui.showInfoToast(FsBundle.get("trace.select-area"), 4f);
    }

    private static void traceLiquidInput(Building build, ResourceRef liquid){
        if(build == null || liquid == null || areaDialog == null) return;
        BuildingRef ref = AreaProbe.refOf(build);
        AreaDiagnosticResult held = areaDialog.showing() ? areaDialog.heldResult() : null;
        if(held != null && held.entries.stream().anyMatch(entry -> entry.ref.equals(ref))){
            if(panel != null && panel.isShown()){
                panel.cancelOnDismiss();
                panel.hide();
            }
            areaDialog.showLiquidTrace(ref, liquid, TraceDirection.input);
            return;
        }
        pendingLiquidTrace = new LiquidTraceRequest(ref, liquid);
        if(panel != null && panel.isShown()){
            panel.cancelOnDismiss();
            panel.hide();
        }
        startPicking();
        Vars.ui.showInfoToast(FsBundle.get("liquid.select-area"), 4f);
    }

    /** Drops every transient reference; safe to call at any time. */
    public static void reset(){
        pendingTrace = null;
        pendingLiquidTrace = null;
        stopPicking();
        stopLocating();
        stopNetworkView();
        stopNetworkOverlay();
        stopPowerView();
        stopLiquidView();
        stopLiquidOverlay();
        if(panel != null) panel.cancelOnDismiss();
        if(panel != null && panel.isShown()) panel.hide();
        if(areaDialog != null) areaDialog.clear();
        if(powerDialog != null) powerDialog.clearReport();
        powerFromArea = false;
        FsLog.reset();
    }

    private static final class TraceRequest{
        final BuildingRef target;
        final ResourceRef item;

        TraceRequest(BuildingRef target, ResourceRef item){
            this.target = target;
            this.item = item;
        }
    }

    private static final class LiquidTraceRequest{
        final BuildingRef target;
        final ResourceRef liquid;
        LiquidTraceRequest(BuildingRef target, ResourceRef liquid){
            this.target = target;
            this.liquid = liquid;
        }
    }
}
