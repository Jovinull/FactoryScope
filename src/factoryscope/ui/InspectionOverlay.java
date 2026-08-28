package factoryscope.ui;

import arc.*;
import arc.func.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.input.*;
import arc.math.geom.*;
import arc.scene.*;
import arc.scene.event.*;
import factoryscope.area.*;
import mindustry.*;
import mindustry.graphics.*;
import mindustry.ui.*;

/**
 * The transparent element that owns the pointer while FactoryScope is selecting.
 *
 * <h2>Why an overlay rather than a game input hook</h2>
 * Mindustry fires {@code TapEvent} only after the world has already handled the tap, so a configurable
 * block would open its own dialog underneath the diagnostic panel, and the event travels through
 * {@code Call.tileTap}, a networked remote. A touchable element in the HUD instead swallows the gesture
 * before the world sees it - {@code Core.scene.hasMouse()} is what gates world input, and it is also
 * what keeps {@code MobileInput} from setting the flag it needs to pan the camera - so the overlay owns
 * the whole gesture and normal play resumes the moment it is removed.
 *
 * <h2>Click or drag</h2>
 * A press that never travels {@value #DRAG_THRESHOLD} scene units, or that never leaves its starting
 * tile, is a click and selects one building, exactly as it did before area selection existed. Anything
 * else is an area. The threshold is in scene units, which are design pixels: it therefore means the
 * same physical distance whatever the resolution and UI scale.
 */
final class InspectionOverlay extends Element{
    /** Far enough that a shaky click is still a click, short enough that a deliberate drag is instant. */
    static final float DRAG_THRESHOLD = 16f;

    private final Vec2 scratch = new Vec2();
    private final Vec2 pressed = new Vec2();

    private final Cons<Vec2> onPoint;
    private final Cons<AreaSelection> onArea;
    private final Runnable onCancel;

    private boolean pressing;
    private boolean dragging;
    private int startTileX, startTileY, currentTileX, currentTileY;

    InspectionOverlay(Cons<Vec2> onPoint, Cons<AreaSelection> onArea, Runnable onCancel){
        this.onPoint = onPoint;
        this.onArea = onArea;
        this.onCancel = onCancel;

        name = "factoryscope-picker";
        setFillParent(true);
        touchable = Touchable.enabled;
        addListener(new InputListener(){
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button){
                //only the first pointer selects; a second finger must not start a rival gesture
                if(pointer != 0) return false;
                if(button == KeyCode.mouseRight){
                    reset();
                    onCancel.run();
                    return false;
                }
                begin(event.stageX, event.stageY);
                return true;
            }

            @Override
            public void touchDragged(InputEvent event, float x, float y, int pointer){
                if(pointer != 0) return;
                extend(event.stageX, event.stageY);
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, KeyCode button){
                if(pointer != 0) return;
                release(event.stageX, event.stageY);
            }
        });
    }

    private void begin(float stageX, float stageY){
        WorldCoords.fromStage(stageX, stageY, scratch);
        pressed.set(stageX, stageY);
        startTileX = currentTileX = WorldCoords.tileX(scratch);
        startTileY = currentTileY = WorldCoords.tileY(scratch);
        pressing = true;
        dragging = false;
    }

    private void extend(float stageX, float stageY){
        if(!pressing) return;
        WorldCoords.fromStage(stageX, stageY, scratch);
        currentTileX = WorldCoords.tileX(scratch);
        currentTileY = WorldCoords.tileY(scratch);
        if(!dragging && pressed.dst(stageX, stageY) >= DRAG_THRESHOLD) dragging = true;
    }

    private void release(float stageX, float stageY){
        if(!pressing){
            //the press was consumed elsewhere; treat the release as nothing rather than as a selection
            return;
        }
        extend(stageX, stageY);

        boolean wasDragging = dragging;
        AreaSelection selection = selection();
        WorldCoords.fromStage(stageX, stageY, scratch);
        reset();

        //a drag that stayed inside one tile is still a click: the player pointed at one building
        if(wasDragging && !selection.single()){
            onArea.get(selection);
        }else{
            onPoint.get(scratch);
        }
    }

    private void reset(){
        pressing = false;
        dragging = false;
    }

    AreaSelection selection(){
        return AreaSelection.of(startTileX, startTileY, currentTileX, currentTileY);
    }

    boolean dragging(){
        return pressing && dragging;
    }

    /**
     * Draws the selection rectangle in world space.
     *
     * <p>Called from {@code Trigger.drawOver}, not from {@link #draw()}: the scene draws in UI
     * coordinates, and a rectangle that must line up with the tile grid has to be drawn with the world
     * camera. The colours and the 2px stroke are the ones the game uses for its own region selections,
     * so the rectangle reads as part of Mindustry rather than as something bolted on.
     */
    void drawWorld(){
        if(!dragging()) return;

        AreaSelection selection = selection();
        float x = WorldCoords.worldEdge(selection.minX);
        float y = WorldCoords.worldEdge(selection.minY);
        float width = selection.width() * Vars.tilesize;
        float height = selection.height() * Vars.tilesize;

        Draw.z(Layer.overlayUI);
        Lines.stroke(2f);
        Draw.color(Pal.accentBack);
        Lines.rect(x, y - 1f, width, height);
        Draw.color(Pal.accent);
        Lines.rect(x, y, width, height);

        drawSizeLabel(selection, x + width, y + height);
        Draw.reset();
    }

    /** The same "WxH (area)" readout the game shows while dragging a schematic region. */
    private void drawSizeLabel(AreaSelection selection, float x, float y){
        float scale = Vars.renderer.camerascale;
        Font font = Fonts.outline;
        boolean integers = font.usesIntegerPositions();
        font.setUseIntegerPositions(false);
        font.getData().setScale(1f / scale);
        font.setColor(Pal.accent);

        float offset = 5f / scale * Vars.tilesize;
        font.draw(selection.width() + "x" + selection.height() + " (" + selection.tileCount() + ")",
            x + offset / 2f, y + offset / 2f);

        font.setColor(Color.white);
        font.getData().setScale(1f);
        font.setUseIntegerPositions(integers);
    }
}
