package factoryscope.ui;

import arc.scene.ui.layout.*;
import factoryscope.*;
import factoryscope.model.ResourceRef;
import factoryscope.trace.TraceDirection;
import mindustry.Vars;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.ui.*;

/** Small HUD control while a structural liquid topology overlay is visible. */
final class LiquidViewOverlay{
    private final Table bar;

    LiquidViewOverlay(ResourceRef liquid, Runnable onReturn, Runnable onDismiss){
        bar = new Table();
        bar.name = "factoryscope-liquid-viewing";
        bar.setFillParent(true);
        bar.top();
        bar.table(Tex.buttonEdge3, inner -> {
            inner.margin(8f);
            inner.image(Icon.liquid).size(24f).padRight(6f);
            inner.add(FsBundle.get("liquid.viewing-structural")).color(Pal.accent).padRight(8f);
            if(liquid != null) inner.add(liquid.name).color(Pal.lightishGray).padRight(12f);
            inner.button(FsBundle.ref("area.return"), Icon.left, Styles.flatt, onReturn::run)
                .size(210f, 44f).padRight(4f).name("factoryscope-liquid-return");
            inner.button(Icon.cancelSmall, Styles.emptyi, onDismiss::run).size(36f)
                .name("factoryscope-liquid-dismiss");
        }).padTop(90f);
        Vars.ui.hudGroup.addChild(bar);
    }

    void remove(){ bar.remove(); }
}
