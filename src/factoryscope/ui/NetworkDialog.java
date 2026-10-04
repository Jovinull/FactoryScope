package factoryscope.ui;

import arc.Core;
import arc.scene.ui.layout.*;
import factoryscope.*;
import factoryscope.analysis.*;
import factoryscope.area.*;
import factoryscope.model.*;
import factoryscope.network.*;
import factoryscope.probe.AreaProbe;
import factoryscope.trace.*;
import mindustry.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.ui.*;
import mindustry.ui.dialogs.*;

import java.util.*;
import java.util.function.Consumer;

/** Area-scoped static item topology. It deliberately never reports measured transfer. */
final class NetworkDialog extends BaseDialog{
    private AreaDiagnosticResult areaResult;
    private ItemNetwork network;
    private ResourceRef selected;
    private BuildingRef selectedBuilding;
    private SupplyTrace trace;
    private int shownBuildings = 40;
    private Runnable onViewWorld;
    private Runnable onRefresh;
    private Consumer<BuildingRef> onLocate;
    private boolean keepOverlay;
    private Table body;
    private Cell<Table> bodyCell;

    NetworkDialog(){
        super("");
        name = "factoryscope-network-dialog";
        title.setText(FsBundle.get("network.title"));
        cont.pane(outer -> {
            outer.top();
            bodyCell = outer.add(body = new Table()).top();
        }).grow().with(pane -> pane.setScrollingDisabled(true, false));
        addCloseButton();
        hidden(() -> {
            if(!keepOverlay) FactoryScopeUI.stopNetworkOverlay();
        });
        buttons.button(FsBundle.ref("network.view-world"), Icon.eye, this::viewInWorld)
            .size(220f, 64f).name("factoryscope-network-view-world");
        buttons.button(FsBundle.ref("area.refresh"), Icon.refresh, () -> {
            if(onRefresh != null) onRefresh.run();
        }).size(180f, 64f).name("factoryscope-network-refresh");
    }

    void show(AreaDiagnosticResult areaResult){
        this.areaResult = areaResult;
        this.network = areaResult == null ? null : areaResult.network;
        this.selected = null;
        this.selectedBuilding = null;
        this.trace = null;
        this.shownBuildings = 40;
        this.title.setText(FsBundle.get("network.title"));
        FactoryScopeUI.showNetworkOverlay(network, null);
        rebuild();
        show();
    }

    void showTrace(AreaDiagnosticResult areaResult, BuildingRef target, ResourceRef item){
        this.areaResult = areaResult;
        this.network = areaResult == null ? null : areaResult.network;
        this.selected = item;
        this.selectedBuilding = target;
        this.trace = TraceAnalyzer.input(areaResult, target, item);
        this.shownBuildings = 40;
        this.title.setText(FsBundle.get("trace.title"));
        FactoryScopeUI.showNetworkOverlay(network, selected, trace);
        rebuild();
        show();
    }

    void setOnViewWorld(Runnable onViewWorld){
        this.onViewWorld = onViewWorld;
    }

    void setOnRefresh(Runnable onRefresh){
        this.onRefresh = onRefresh;
    }

    void setOnLocate(Consumer<BuildingRef> onLocate){
        this.onLocate = onLocate;
    }

    SupplyTrace trace(){
        return trace;
    }

    void refresh(AreaDiagnosticResult areaResult){
        this.areaResult = areaResult;
        this.network = areaResult == null ? null : areaResult.network;
        if(trace != null && network != null){
            trace = trace.direction == TraceDirection.input
                ? TraceAnalyzer.input(areaResult, trace.target, trace.item)
                : TraceAnalyzer.output(areaResult, trace.target, trace.item);
            selected = trace.item;
            FactoryScopeUI.showNetworkOverlay(network, selected, trace);
        }
        if(network != null) rebuild();
    }

    void clearReport(){
        areaResult = null;
        network = null;
        selected = null;
        selectedBuilding = null;
        trace = null;
        keepOverlay = false;
        hide();
    }

    ResourceRef selected(){
        return selected;
    }

    void reopen(){
        keepOverlay = false;
        if(network != null){
            FactoryScopeUI.showNetworkOverlay(network, selected, trace);
            title.setText(FsBundle.get(trace == null ? "network.title" : "trace.title"));
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
        body.clear();
        body.top().defaults().growX().left();
        float available = Core.scene.getWidth() / Scl.scl() - 40f;
        bodyCell.width(Math.min(Math.max(1f, available), 900f));
        if(network == null) return;
        body.add(FsBundle.get("snapshot.note")).color(Pal.gray).wrap().padBottom(6f).row();
        body.add(FsBundle.get("network.static-note")).color(Pal.lightishGray).wrap().padBottom(10f).row();
        if(trace != null){
            buildTrace();
            return;
        }
        body.table(Tex.pane, table -> {
            table.margin(8f).defaults().growX().left();
            value(table, "network.ports", Integer.toString(network.graph.ports.size()));
            value(table, "network.edges", Integer.toString(network.graph.edges.size()));
            value(table, "network.components", Integer.toString(network.graph.weakComponents().size()));
            value(table, "network.boundary", Integer.toString(network.boundaryPorts.size()));
        }).row();
        if(!network.resources.isEmpty()){
            body.add(FsBundle.get("network.resource")).color(Pal.accent).padTop(10f).row();
            body.table(table -> {
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
            body.add(FsBundle.get("network.partial")).color(Pal.lightOrange).wrap().padTop(10f).row();
        }
        if(!network.boundaryPorts.isEmpty()) body.add(FsBundle.get("network.boundary-note")).color(Pal.accent).wrap().padTop(6f).row();
    }

    private void buildDetails(){
        var buildings = activeBuildings();
        if(buildings.isEmpty()) return;
        body.add(FsBundle.get("network.buildings")).color(Pal.accent).padTop(10f).row();
        body.table(Tex.pane, table -> {
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
        body.add(selectedBuilding.blockName).color(Pal.accent).padTop(10f).row();
        body.table(Tex.pane, table -> {
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
            AreaEntry entry = entry(selectedBuilding);
            if(entry != null && entry.snapshot != null){
                Set<String> seen = new HashSet<>();
                for(ResourceState input : entry.snapshot.inputs){
                    if(input.kind != ResourceKind.item || input.contentId == null || !seen.add(input.ref().key())) continue;
                    table.button(FsBundle.format("trace.input-action", input.name), Styles.flatt,
                        () -> showTrace(areaResult, selectedBuilding, input.ref()))
                        .height(36f).name("factoryscope-network-trace-input").row();
                }
                for(ResourceRef output : entry.snapshot.producedItems){
                    if(!seen.add(output.key())) continue;
                    table.button(FsBundle.format("trace.output-action", output.name), Styles.flatt,
                        () -> showOutputTrace(areaResult, selectedBuilding, output))
                        .height(36f).name("factoryscope-network-trace-output").row();
                }
            }
        }).growX().left().row();
    }

    private void buildTrace(){
        body.add(FsBundle.format("trace.target", trace.target.blockName)).color(Pal.lightishGray).wrap().row();
        body.add(trace.item.name).color(Pal.accent).padTop(4f).row();
        if(!trace.targetUsesItem){
            body.add(FsBundle.get("trace.target-missing")).color(Pal.lightOrange).wrap().padTop(6f).row();
        }
        if(trace.noRouteProven){
            body.add(FsBundle.get(trace.direction == TraceDirection.input ? "trace.no-route" : "trace.no-downstream"))
                .color(Pal.lightOrange).wrap().padTop(8f).row();
        }
        if(!trace.boundaryContinuations.isEmpty()){
            String boundaryKey = trace.boundaryContinuations.size() == 1 ? "trace.boundary-one" : "trace.boundary-many";
            body.add(FsBundle.format(boundaryKey, trace.boundaryContinuations.size()))
                .color(Pal.accent).wrap().padTop(8f).row();
            for(NetworkPort port : trace.boundaryContinuations){
                body.add(FsBundle.format("trace.boundary-at", port.building.tileX, port.building.tileY))
                    .color(Pal.lightishGray).padLeft(8f).row();
            }
        }
        if(!trace.unsupportedInArea.isEmpty()){
            String key = trace.unsupportedInArea.size() == 1
                ? "trace.unsupported-area-one" : "trace.unsupported-area-many";
            body.add(FsBundle.format(key, trace.unsupportedInArea.size()))
                .color(Pal.lightOrange).wrap().padTop(8f).row();
        }
        for(NetworkInterruption interruption : trace.unsupportedInterruptions){
            body.table(row -> {
                row.add(FsBundle.format("trace.unsupported-at", interruption.transport.blockName,
                    interruption.transport.tileX, interruption.transport.tileY))
                    .color(Pal.lightOrange).growX().left().wrap().minWidth(0f);
                row.button(Icon.zoomSmall, Styles.emptyi, () -> locate(interruption.transport)).size(34f)
                    .tooltip(FsBundle.ref("area.locate")).name("factoryscope-trace-unsupported-locate");
            }).growX().padTop(4f).row();
        }
        if(trace.diagnosticsIncomplete){
            body.add(FsBundle.get("trace.incomplete-diagnostics")).color(Pal.lightOrange).wrap().padTop(6f).row();
        }
        if(trace.topologyIncomplete){
            body.add(FsBundle.get("trace.incomplete-target-topology")).color(Pal.lightOrange).wrap().padTop(6f).row();
        }
        if(!trace.endpoints.isEmpty()){
            body.add(FsBundle.format("trace.endpoints", trace.endpoints.size())).color(Pal.accent).padTop(10f).row();
            body.table(Tex.pane, table -> {
                table.margin(6f).left().defaults().growX().left();
                int limit = Math.min(shownBuildings, trace.endpoints.size());
                for(int i = 0; i < limit; i++) traceEndpoint(table, trace.endpoints.get(i));
            }).growX().row();
            if(shownBuildings < trace.endpoints.size()){
                body.button(FsBundle.ref("area.show-more"), Icon.downOpen, Styles.flatt, () -> {
                    shownBuildings += 40;
                    rebuild();
                }).height(38f).name("factoryscope-trace-more").row();
            }
        }else if(!trace.boundaryContinuations.isEmpty()){
            body.add(FsBundle.get(trace.direction == TraceDirection.input
                ? "trace.no-in-area-producer" : "trace.no-in-area-consumer"))
                .color(Pal.lightishGray).wrap().padTop(6f).row();
        }else if(trace.endpoints.isEmpty() && trace.complete && !trace.noRouteProven){
            body.add(FsBundle.get(trace.direction == TraceDirection.input ? "trace.no-producer" : "trace.no-consumer"))
                .color(Pal.lightishGray).wrap().padTop(6f).row();
        }
        if(!trace.structuralDeadEnds.isEmpty()){
            body.add(FsBundle.format("trace.dead-ends", trace.structuralDeadEnds.size()))
                .color(Pal.accent).padTop(10f).row();
            int limit = Math.min(shownBuildings, trace.structuralDeadEnds.size());
            for(int i = 0; i < limit; i++){
                BuildingRef ref = trace.structuralDeadEnds.get(i);
                body.table(row -> {
                    row.add(FsBundle.format("trace.dead-end", ref.blockName, ref.tileX, ref.tileY))
                        .growX().left().ellipsis(true).minWidth(0f);
                    row.button(Icon.zoomSmall, Styles.emptyi, () -> locate(ref)).size(34f)
                        .tooltip(FsBundle.ref("area.locate")).name("factoryscope-trace-locate");
                }).growX().row();
            }
            if(limit < trace.structuralDeadEnds.size()){
                body.button(FsBundle.ref("area.show-more"), Icon.downOpen, Styles.flatt, () -> {
                    shownBuildings += 40;
                    rebuild();
                }).height(38f).name("factoryscope-trace-dead-ends-more").row();
            }
        }
        body.button(FsBundle.ref("trace.back-network"), Icon.left, Styles.flatt, () -> {
            trace = null;
            selectedBuilding = null;
            title.setText(FsBundle.get("network.title"));
            FactoryScopeUI.showNetworkOverlay(network, selected);
            rebuild();
        }).height(42f).padTop(10f).name("factoryscope-trace-back").row();
    }

    private void traceEndpoint(Table table, TraceEndpoint endpoint){
        table.table(row -> {
            row.add(endpoint.building.blockName).growX().left().ellipsis(true).minWidth(0f);
            if(endpoint.diagnostic != null){
                AreaStatus status = AreaStatus.of(endpoint.diagnostic.reason());
                row.add(AreaText.status(status)).color(AreaText.color(status)).right().padLeft(8f);
            }else{
                row.add(FsBundle.get("trace.storage")).color(Pal.lightishGray).right().padLeft(8f);
            }
            if(endpoint.diagnostic != null){
                row.button(Icon.zoomSmall, Styles.emptyi, () -> inspect(endpoint.building)).size(34f)
                    .tooltip(FsBundle.ref("trace.inspect")).name("factoryscope-trace-inspect");
            }
        }).growX().name("factoryscope-trace-endpoint-" + endpoint.building.tileX + "-" + endpoint.building.tileY).row();
        String path = pathText(endpoint.path);
        if(!path.isEmpty()) table.add(path).color(Pal.gray).wrap().padLeft(8f).padBottom(4f).row();
        if(endpoint.path.conditional()) table.add(FsBundle.get("trace.conditional-path"))
            .color(Pal.lightOrange).padLeft(8f).padBottom(4f).row();
    }

    private String pathText(TracePath path){
        List<String> names = new ArrayList<>();
        BuildingRef previous = null;
        for(NetworkPort port : path.ports()){
            if(!port.building.equals(previous)) names.add(port.building.blockName);
            previous = port.building;
        }
        return String.join(" > ", names);
    }

    private void inspect(BuildingRef ref){
        Building build = FactoryScopeUI.visibleTarget(ref);
        if(build == null){
            FactoryScopeUI.showTargetUnavailable();
        }else{
            FactoryScopeUI.inspect(build);
        }
    }

    private void locate(BuildingRef ref){
        if(FactoryScopeUI.visibleTarget(ref) == null){
            FactoryScopeUI.showTargetUnavailable();
        }else if(onLocate != null){
            hide();
            onLocate.accept(ref);
        }
    }

    private void showOutputTrace(AreaDiagnosticResult areaResult, BuildingRef target, ResourceRef item){
        this.areaResult = areaResult;
        this.network = areaResult.network;
        this.selected = item;
        this.selectedBuilding = target;
        this.trace = TraceAnalyzer.output(areaResult, target, item);
        this.title.setText(FsBundle.get("trace.output-title"));
        FactoryScopeUI.showNetworkOverlay(network, selected, trace);
        rebuild();
    }

    private AreaEntry entry(BuildingRef ref){
        if(areaResult == null) return null;
        for(AreaEntry entry : areaResult.entries) if(entry.ref.equals(ref)) return entry;
        return null;
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
