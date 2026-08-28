package factoryscope.ui;

import arc.*;
import arc.math.geom.*;
import mindustry.*;
import mindustry.core.*;

/**
 * The one place that turns a pointer position into a world position.
 *
 * <h2>Why this is not a one-liner</h2>
 * Arc reports raw input with the origin at the <em>bottom</em> left, which is what
 * {@code Camera.unproject} expects. {@code Scene.stageToScreenCoordinates} looks like the obvious way
 * to get there from a scene event and is not: it flips Y to a top-left origin for the sake of platform
 * input APIs, so feeding its output to the camera mirrors every position about the middle of the
 * screen. That defect shipped in FactoryScope 0.1.0. Projecting through the scene's own viewport keeps
 * the bottom-left origin and is the conversion that is correct at every resolution and UI scale.
 *
 * <p>Everything that reads the pointer goes through here, so there is exactly one implementation to be
 * wrong and exactly one to test.
 */
public final class WorldCoords{
    private WorldCoords(){
    }

    /** Scene/stage coordinates to world coordinates, written into {@code out}. */
    public static Vec2 fromStage(float stageX, float stageY, Vec2 out){
        Core.scene.getViewport().project(out.set(stageX, stageY));
        Core.camera.unproject(out);
        return out;
    }

    /** Tile X of a scene position. */
    public static int tileX(Vec2 world){
        return World.toTile(world.x);
    }

    /** Tile Y of a scene position. */
    public static int tileY(Vec2 world){
        return World.toTile(world.y);
    }

    /** Centre of a tile in world coordinates. */
    public static float worldCentre(int tile){
        return tile * Vars.tilesize;
    }

    /** Lower edge of a tile in world coordinates. */
    public static float worldEdge(int tile){
        return tile * Vars.tilesize - Vars.tilesize / 2f;
    }
}
