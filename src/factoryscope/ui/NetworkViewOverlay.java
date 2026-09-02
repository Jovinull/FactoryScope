package factoryscope.ui;

import arc.scene.ui.layout.*;
import factoryscope.*;
import factoryscope.model.*;
import mindustry.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.ui.*;

/** Small HUD return control while the static route overlay is visible in the world. */
final class NetworkViewOverlay{
    private final Table bar;

    NetworkViewOverlay(ResourceRef item, Runnable onReturn, Runnable onDismiss){
        bar = new Table();
        bar.name = "factoryscope-network-viewing";
        bar.setFillParent(true);
        bar.top();
        bar.table(Tex.buttonEdge3, inner -> {
            inner.margin(8f);
            inner.image(Icon.list).size(24f).padRight(6f);
            inner.add(FsBundle.get("network.viewing")).color(Pal.accent).padRight(8f);
            if(item != null) inner.add(item.name).color(Pal.lightishGray).padRight(12f);
            inner.button(FsBundle.ref("area.return"), Icon.left, Styles.flatt, onReturn::run)
                .size(210f, 44f).padRight(4f).name("factoryscope-network-return");
            inner.button(Icon.cancelSmall, Styles.emptyi, onDismiss::run).size(36f)
                .name("factoryscope-network-dismiss");
        }).padTop(90f);
        Vars.ui.hudGroup.addChild(bar);
    }

    void remove(){
        bar.remove();
    }
}
