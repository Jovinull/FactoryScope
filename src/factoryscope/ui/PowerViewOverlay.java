package factoryscope.ui;

import arc.scene.ui.layout.Table;
import factoryscope.FsBundle;
import mindustry.Vars;
import mindustry.gen.*;
import mindustry.graphics.Pal;
import mindustry.ui.*;

/** Return control shown while the snapshot's established electrical connections are overlaid. */
final class PowerViewOverlay{
    private final Table bar;

    PowerViewOverlay(Runnable onReturn, Runnable onDismiss){
        bar = new Table();
        bar.name = "factoryscope-power-viewing";
        bar.setFillParent(true);
        bar.top();
        bar.table(Tex.buttonEdge3, inner -> {
            inner.margin(8f);
            inner.image(Icon.power).size(24f).padRight(6f);
            inner.add(FsBundle.get("power.viewing")).color(Pal.accent).padRight(12f);
            inner.button(FsBundle.ref("area.return"), Icon.left, Styles.flatt, onReturn::run)
                .size(210f, 44f).padRight(4f).name("factoryscope-power-return");
            inner.button(Icon.cancelSmall, Styles.emptyi, onDismiss::run).size(36f)
                .name("factoryscope-power-dismiss");
        }).padTop(90f);
        Vars.ui.hudGroup.addChild(bar);
    }

    void remove(){
        bar.remove();
    }
}
