package factoryscope.ui;

import arc.scene.ui.layout.*;
import factoryscope.*;
import factoryscope.model.*;
import factoryscope.network.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.ui.*;
import mindustry.ui.dialogs.*;

/** Area-scoped static item topology. It deliberately never reports measured transfer. */
final class NetworkDialog extends BaseDialog{
    private ItemNetwork network;
    private ResourceRef selected;

    NetworkDialog(){
        super("");
        name = "factoryscope-network-dialog";
        title.setText(FsBundle.get("network.title"));
        addCloseButton();
        hidden(FactoryScopeUI::stopNetworkOverlay);
    }

    void show(ItemNetwork network){
        this.network = network;
        this.selected = null;
        FactoryScopeUI.showNetworkOverlay(network, null);
        rebuild();
        show();
    }

    private void rebuild(){
        cont.clear();
        cont.top().defaults().growX().left();
        if(network == null) return;
        cont.add(FsBundle.get("network.static-note")).color(Pal.lightishGray).wrap().padBottom(10f).row();
        cont.table(Tex.pane, table -> {
            table.margin(8f).defaults().growX().left();
            value(table, "network.ports", Integer.toString(network.graph.ports.size()));
            value(table, "network.edges", Integer.toString(network.graph.edges.size()));
            value(table, "network.components", Integer.toString(network.graph.weakComponents().size()));
            value(table, "network.boundary", Integer.toString(network.boundaryPorts.size()));
        }).row();
        if(!network.resources.isEmpty()){
            cont.add(FsBundle.get("network.resource")).color(Pal.accent).padTop(10f).row();
            cont.table(table -> {
                table.left().defaults().height(42f).growX().left();
                table.button(FsBundle.get("network.all-items"), Styles.flatt, () -> { selected = null; FactoryScopeUI.showNetworkOverlay(network, null); rebuild(); })
                    .checked(button -> selected == null).name("factoryscope-network-all").row();
                for(ResourceRef item : network.resources){
                    table.button(item.name, Styles.flatt, () -> { selected = item; FactoryScopeUI.showNetworkOverlay(network, item); rebuild(); })
                        .checked(button -> item.equals(selected)).name("factoryscope-network-item-" + item.id).row();
                }
            }).growX().left().row();
        }
        if(network.completeness == NetworkCompleteness.partialUnsupportedTransport){
            cont.add(FsBundle.get("network.partial")).color(Pal.lightOrange).wrap().padTop(10f).row();
        }
        if(!network.boundaryPorts.isEmpty()) cont.add(FsBundle.get("network.boundary-note")).color(Pal.accent).wrap().padTop(6f).row();
    }

    private static void value(Table table, String key, String value){
        table.table(row -> {
            row.add(FsBundle.get(key)).growX().left();
            row.add(value).color(Pal.lightishGray).right();
        }).row();
    }
}
