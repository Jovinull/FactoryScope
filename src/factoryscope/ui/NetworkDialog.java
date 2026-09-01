package factoryscope.ui;

import arc.scene.ui.layout.*;
import factoryscope.*;
import factoryscope.area.*;
import factoryscope.model.*;
import factoryscope.network.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.ui.*;
import mindustry.ui.dialogs.*;

import java.util.*;

/** Area-scoped static item topology. It deliberately never reports measured transfer. */
final class NetworkDialog extends BaseDialog{
    private ItemNetwork network;
    private ResourceRef selected;
    private BuildingRef selectedBuilding;
    private int shownBuildings = 40;
    private Runnable onViewWorld;
    private boolean keepOverlay;

    NetworkDialog(){
        super("");
        name = "factoryscope-network-dialog";
        title.setText(FsBundle.get("network.title"));
        addCloseButton();
        hidden(() -> {
            if(!keepOverlay) FactoryScopeUI.stopNetworkOverlay();
        });
        buttons.button(FsBundle.ref("network.view-world"), Icon.eye, this::viewInWorld)
            .size(220f, 64f).name("factoryscope-network-view-world");
    }

    void show(ItemNetwork network){
        this.network = network;
        this.selected = null;
        this.selectedBuilding = null;
        this.shownBuildings = 40;
        FactoryScopeUI.showNetworkOverlay(network, null);
        rebuild();
        show();
    }

    void setOnViewWorld(Runnable onViewWorld){
        this.onViewWorld = onViewWorld;
    }

    ResourceRef selected(){
        return selected;
    }

    void reopen(){
        keepOverlay = false;
        if(network != null){
            FactoryScopeUI.showNetworkOverlay(network, selected);
            rebuild();
            show();
        }
    }

    private void viewInWorld(){
        if(network == null || onViewWorld == null) return;
        keepOverlay = true;
        hide();
        onViewWorld.run();
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
        buildDetails();
        if(network.completeness == NetworkCompleteness.partialUnsupportedTransport){
            cont.add(FsBundle.get("network.partial")).color(Pal.lightOrange).wrap().padTop(10f).row();
        }
        if(!network.boundaryPorts.isEmpty()) cont.add(FsBundle.get("network.boundary-note")).color(Pal.accent).wrap().padTop(6f).row();
    }

    private void buildDetails(){
        var buildings = activeBuildings();
        if(buildings.isEmpty()) return;
        cont.add(FsBundle.get("network.buildings")).color(Pal.accent).padTop(10f).row();
        cont.table(Tex.pane, table -> {
            table.margin(6f).left().defaults().growX().left();
            int limit = Math.min(shownBuildings, buildings.size());
            for(int i = 0; i < limit; i++){
                BuildingRef ref = buildings.get(i);
                table.button(row -> row.add(ref.blockName + " (" + ref.tileX + ", " + ref.tileY + ")")
                    .growX().left(), Styles.flatt, () -> {
                        selectedBuilding = ref;
                        rebuild();
                    }).checked(button -> ref.equals(selectedBuilding)).name("factoryscope-network-building").left().row();
            }
            if(limit < buildings.size()) table.button(FsBundle.get("area.show-more"), Styles.flatt, () -> {
                shownBuildings += 40;
                rebuild();
            }).name("factoryscope-network-more").row();
        }).growX().left().row();
        if(selectedBuilding != null) buildBuildingDetail();
    }

    private void buildBuildingDetail(){
        cont.add(selectedBuilding.blockName).color(Pal.accent).padTop(10f).row();
        cont.table(Tex.pane, table -> {
            table.margin(8f).defaults().growX().left();
            table.add(FsBundle.format("area.coordinates", selectedBuilding.tileX, selectedBuilding.tileY)).color(Pal.lightishGray).row();
            if(selected == null){
                table.add(FsBundle.get("network.detail-select-item")).color(Pal.lightOrange).wrap().row();
                return;
            }
            Set<BuildingRef> upstream = new TreeSet<>(this::compareBuilding);
            Set<BuildingRef> downstream = new TreeSet<>(this::compareBuilding);
            for(NetworkPort port : network.graph.ports) if(port.building.equals(selectedBuilding)){
                for(NetworkPort source : network.graph.reaching(port, selected)) if(!source.building.equals(selectedBuilding)) upstream.add(source.building);
                for(NetworkPort target : network.graph.reachableFrom(port, selected)) if(!target.building.equals(selectedBuilding)) downstream.add(target.building);
            }
            value(table, "network.detail-upstream", Integer.toString(upstream.size()));
            value(table, "network.detail-downstream", Integer.toString(downstream.size()));
            long boundaries = network.boundaryPorts.stream().filter(port -> port.building.equals(selectedBuilding)).count();
            value(table, "network.detail-boundary", Long.toString(boundaries));
        }).growX().left().row();
    }

    private List<BuildingRef> activeBuildings(){
        TreeSet<BuildingRef> result = new TreeSet<>(this::compareBuilding);
        for(NetworkEdge edge : network.graph.edges){
            result.add(edge.from.building);
            result.add(edge.to.building);
        }
        return List.copyOf(result);
    }

    private int compareBuilding(BuildingRef left, BuildingRef right){
        int byX = Integer.compare(left.tileX, right.tileX);
        if(byX != 0) return byX;
        int byY = Integer.compare(left.tileY, right.tileY);
        if(byY != 0) return byY;
        int byBlock = left.blockId.compareTo(right.blockId);
        return byBlock != 0 ? byBlock : Integer.compare(left.teamId, right.teamId);
    }

    private static void value(Table table, String key, String value){
        table.table(row -> {
            row.add(FsBundle.get(key)).growX().left();
            row.add(value).color(Pal.lightishGray).right();
        }).row();
    }
}
