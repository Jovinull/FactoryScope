package factoryscope.ui;

import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.*;
import arc.scene.ui.layout.*;
import arc.util.*;
import factoryscope.*;
import factoryscope.area.*;
import factoryscope.probe.*;
import mindustry.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.ui.*;

/**
 * What the player sees after asking an area report where a building is.
 *
 * <p>Moving the camera behind a full-screen dialog helps nobody, so the report steps aside: the world
 * is uncovered, the target is marked with the game's own block-selection brackets, and a small bar
 * stays on the HUD offering the way back. That bar is the only navigation state FactoryScope keeps -
 * one step out and one step back, no history stack.
 */
final class LocateOverlay{
    /** The brackets have done their job once the eye has found them; the way back has not. */
    private static final float HIGHLIGHT_SECONDS = 10f;

    private final BuildingRef ref;
    private final Color tint = new Color();
    private final Table bar;
    private float remaining = HIGHLIGHT_SECONDS;

    /**
     * Uncovers the world, moves the view to the building and marks it.
     *
     * @param onReturn reopens the report the player came from
     * @param onDismiss drops this overlay without reopening anything
     */
    LocateOverlay(BuildingRef ref, Building build, Runnable onReturn, Runnable onDismiss){
        this.ref = ref;

        //panCamera is the game's own way of moving the view: it touches the camera and nothing else,
        //and on desktop it also stops the view snapping straight back to the player unit
        if(Vars.control != null && Vars.control.input != null){
            Vars.control.input.panCamera(new Vec2(build.x, build.y));
        }

        bar = new Table();
        bar.name = "factoryscope-locate";
        bar.setFillParent(true);
        bar.top();
        bar.update(this::update);
        bar.table(Tex.buttonEdge3, inner -> {
            inner.margin(8f);
            inner.image(Icon.zoomSmall).size(24f).padRight(6f);
            inner.add(build.block.localizedName).color(Pal.accent).padRight(4f);
            inner.add(FsBundle.format("area.coordinates", ref.tileX, ref.tileY)).color(Pal.gray).padRight(12f);
            //sized rather than left to shrink: an unsized icon-and-text button squeezes the two together
            inner.button(FsBundle.ref("area.return"), Icon.left, Styles.flatt, onReturn::run)
                .size(210f, 44f).padRight(4f).name("factoryscope-locate-return");
            inner.button(Icon.cancelSmall, Styles.emptyi, onDismiss::run).size(36f)
                .name("factoryscope-locate-dismiss");
        }).padTop(90f);

        Vars.ui.hudGroup.addChild(bar);
    }

    /** Counts the highlight down; the bar itself stays until the player uses it. */
    private void update(){
        if(remaining > 0f) remaining -= Time.delta / 60f;
    }

    /**
     * Marks the target with {@code Drawf.selected}, the same brackets the game draws around a block
     * the player has selected. It follows the block footprint, costs one sprite, and changes nothing.
     *
     * <p>The reference is resolved every frame rather than held: if the building is destroyed while
     * the player is looking at it, the marker has to stop pointing at empty ground - and must never
     * start pointing at whatever gets built there next.
     */
    void drawWorld(){
        if(remaining <= 0f) return;

        Building build = AreaProbe.resolve(ref);
        if(build == null){
            remaining = 0f;
            return;
        }

        //full strength until the last second, then out; the pulse is what catches the eye on a busy map
        float fade = Math.min(1f, remaining);
        Draw.z(Layer.overlayUI);
        Drawf.selected(build, tint.set(Pal.accent).a(fade * (0.6f + Mathf.absin(Time.time, 6f, 0.4f))));
        Draw.reset();
    }

    void remove(){
        bar.remove();
    }
}
