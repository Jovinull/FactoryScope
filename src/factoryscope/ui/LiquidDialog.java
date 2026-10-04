package factoryscope.ui;

import arc.Core;
import arc.func.Cons;
import arc.scene.ui.layout.*;
import factoryscope.*;
import factoryscope.analysis.*;
import factoryscope.area.*;
import factoryscope.liquid.*;
import factoryscope.model.*;
import factoryscope.network.NetworkPort;
import factoryscope.trace.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.ui.*;
import mindustry.ui.dialogs.*;

import java.util.*;
import java.util.function.*;

/** Area-scoped structural liquid topology, traces and current buffer facts. */
final class LiquidDialog extends BaseDialog{
    private static final int PAGE = 40;
    private AreaDiagnosticResult area;
    private ResourceRef selected;
    private BuildingRef selectedBuilding;
    private LiquidTrace trace;
    private int shown = PAGE, shownDetails = PAGE, shownStored = PAGE;
    private boolean keepOverlay;
    private Table body;
    private Cell<Table> bodyCell;
    private Runnable onViewWorld, onRefresh;
    private Consumer<BuildingRef> onLocate;
    private BiConsumer<BuildingRef, ResourceRef> onTraceInput, onTraceOutput;
    private Consumer<BuildingRef> onInspect;

    LiquidDialog(){
        super("");
        name = "factoryscope-liquid-dialog";
        title.setText(FsBundle.get("liquid.title"));
        cont.pane(outer -> {
            outer.top();
            bodyCell = outer.add(body = new Table()).top();
        }).grow().with(pane -> pane.setScrollingDisabled(true, false));
        addCloseButton();
        hidden(() -> { if(!keepOverlay) FactoryScopeUI.stopLiquidOverlay(); });
        buttons.button(FsBundle.ref("liquid.view-world"), Icon.eye, this::viewInWorld)
            .size(220f, 64f).name("factoryscope-liquid-view-world");
        buttons.button(FsBundle.ref("area.refresh"), Icon.refresh, () -> { if(onRefresh != null) onRefresh.run(); })
            .size(180f, 64f).name("factoryscope-liquid-refresh");
    }

    void setActions(Runnable viewWorld, Runnable refresh, Consumer<BuildingRef> inspect,
                    Consumer<BuildingRef> locate, BiConsumer<BuildingRef, ResourceRef> traceInput,
                    BiConsumer<BuildingRef, ResourceRef> traceOutput){
        onViewWorld = viewWorld;
        onRefresh = refresh;
        onInspect = inspect;
        onLocate = locate;
        onTraceInput = traceInput;
        onTraceOutput = traceOutput;
    }

    void show(AreaDiagnosticResult area){
        this.area = area;
        this.selected = null;
        this.selectedBuilding = null;
        this.trace = null;
        this.shown = this.shownDetails = this.shownStored = PAGE;
        this.title.setText(FsBundle.get("liquid.title"));
        FactoryScopeUI.showLiquidOverlay(area == null ? null : area.liquids, null, null);
        rebuild();
        show();
    }

    void showTrace(AreaDiagnosticResult area, BuildingRef target, ResourceRef liquid, TraceDirection direction){
        this.area = area;
        this.selected = liquid;
        this.selectedBuilding = target;
        this.trace = direction == TraceDirection.input
            ? LiquidTraceAnalyzer.input(area, target, liquid) : LiquidTraceAnalyzer.output(area, target, liquid);
        this.title.setText(FsBundle.get(direction == TraceDirection.input ? "liquid.trace-title" : "liquid.output-title"));
        FactoryScopeUI.showLiquidOverlay(area == null ? null : area.liquids, selected, trace);
        rebuild();
        show();
    }

    void refresh(AreaDiagnosticResult updated){
        TraceDirection direction = trace == null ? null : trace.direction;
        BuildingRef target = trace == null ? null : trace.target;
        ResourceRef liquid = trace == null ? null : trace.liquid;
        this.area = updated;
        if(direction != null){
            trace = direction == TraceDirection.input ? LiquidTraceAnalyzer.input(updated, target, liquid)
                : LiquidTraceAnalyzer.output(updated, target, liquid);
            selected = liquid;
            selectedBuilding = target;
            FactoryScopeUI.showLiquidOverlay(updated.liquids, selected, trace);
        }
        if(area != null) rebuild();
    }

    void clearReport(){
        area = null;
        selected = null;
        selectedBuilding = null;
        trace = null;
        keepOverlay = false;
        FactoryScopeUI.stopLiquidOverlay();
        hide();
    }

    void reopen(){
        keepOverlay = false;
        if(area == null) return;
        FactoryScopeUI.showLiquidOverlay(area.liquids, selected, trace);
        rebuild();
        show();
    }

    boolean showing(){ return isShown() && area != null; }
    LiquidTrace trace(){ return trace; }

    private void viewInWorld(){
        if(area == null || area.liquids == null || onViewWorld == null) return;
        keepOverlay = true;
        hide();
        onViewWorld.run();
    }

    private void rebuild(){
        if(body == null) return;
        body.clear();
        body.top().defaults().growX().left();
        bodyCell.width(Math.min(Core.scene.getWidth() / Scl.scl() - 40f, 620f));
        if(area == null || area.liquids == null){
            body.add(FsBundle.get("liquid.unavailable")).color(Pal.lightOrange).wrap().row();
            return;
        }
        body.labelWrap(FsBundle.get("snapshot.note")).color(Pal.gray).padBottom(6f).growX().row();
        if(trace != null){ buildTrace(); return; }
        buildResourcePicker();
        if(selected != null) buildResourceDetails();
        buildStoredLiquids();
    }

    private void buildResourcePicker(){
        section("liquid.resource");
        body.table(Tex.pane, table -> {
            table.margin(8f).left().defaults().left();
            if(area.liquids.resources.isEmpty()){
                table.add(FsBundle.get("liquid.no-resources")).color(Pal.lightishGray).wrap().row();
                return;
            }
            int shownRows = 0;
            for(ResourceRef liquid : area.liquids.resources){
                if(shownRows++ >= shown) break;
                table.button(button -> {
                    button.left();
                    ContentIcons.add(button, ResourceKind.liquid, liquid.id, 24f);
                    button.add(liquid.name).color(liquid.equals(selected) ? Pal.accent : Pal.lightishGray)
                        .growX().left().ellipsis(true).minWidth(0f);
                }, Styles.flatt, () -> select(liquid)).height(38f).growX().name("factoryscope-liquid-select-" + liquid.id).row();
            }
        }).growX().row();
        if(area.liquids.resources.size() > shown){
            body.button(FsBundle.get("area.show-more"), Styles.flatt, () -> { shown += PAGE; rebuild(); })
                .name("factoryscope-liquid-resource-more").row();
        }
    }

    private void select(ResourceRef liquid){
        selected = liquid;
        selectedBuilding = null;
        trace = null;
        shownDetails = shownStored = PAGE;
        FactoryScopeUI.showLiquidOverlay(area.liquids, selected, null);
        rebuild();
    }

    private void buildResourceDetails(){
        section("liquid.structural-network");
        body.labelWrap(FsBundle.get("liquid.static-note")).color(Pal.gray).growX().row();
        if(area.liquids.partial()) body.labelWrap(FsBundle.get("liquid.partial"))
            .color(Pal.lightOrange).padTop(4f).growX().row();
        int producerCount = 0, consumerCount = 0, storageCount = 0;
        for(AreaEntry entry : area.entries){
            if(entry.snapshot == null) continue;
            if(entry.snapshot.producedLiquids.contains(selected)) producerCount++;
            if(entry.snapshot.inputs.stream().anyMatch(input -> input.kind == ResourceKind.liquid && input.accepts(selected))) consumerCount++;
            if(area.liquids.storageEndpoints.contains(entry.ref)) storageCount++;
        }
        final int producersInArea = producerCount, consumersInArea = consumerCount, storageInArea = storageCount;
        body.table(Tex.pane, table -> {
            table.margin(8f).left().defaults().growX().left();
            row(table, "liquid.producers", Integer.toString(producersInArea));
            row(table, "liquid.consumers", Integer.toString(consumersInArea));
            row(table, "liquid.storage", Integer.toString(storageInArea));
            row(table, "liquid.boundaries", Integer.toString(area.liquids.boundaryInputs.size() + area.liquids.boundaryPorts.size()));
            row(table, "liquid.unsupported", Integer.toString(area.liquids.unsupportedTransport.size()));
        }).growX().padTop(4f).row();

        List<AreaEntry> relevant = new ArrayList<>();
        for(AreaEntry entry : area.entries){
            if(entry.snapshot == null) continue;
            boolean consumes = entry.snapshot.inputs.stream().anyMatch(input -> input.kind == ResourceKind.liquid && input.accepts(selected));
            boolean produces = entry.snapshot.producedLiquids.contains(selected);
            if(!consumes && !produces && !area.liquids.storageEndpoints.contains(entry.ref)) continue;
            relevant.add(entry);
        }
        relevant.sort(Comparator.comparingInt((AreaEntry entry) -> entry.ref.tileX)
            .thenComparingInt(entry -> entry.ref.tileY).thenComparing(entry -> entry.ref.blockId)
            .thenComparingInt(entry -> entry.ref.teamId));
        int limit = Math.min(shownDetails, relevant.size());
        for(int i = 0; i < limit; i++){
            AreaEntry entry = relevant.get(i);
            boolean consumes = entry.snapshot.inputs.stream().anyMatch(input -> input.kind == ResourceKind.liquid && input.accepts(selected));
            boolean produces = entry.snapshot.producedLiquids.contains(selected);
            body.table(row -> {
                row.add(entry.ref.blockName).color(Pal.lightishGray).growX().left().ellipsis(true).minWidth(0f);
            }).growX().padTop(3f).row();
            body.table(row -> {
                if(consumes && onTraceInput != null) row.button(FsBundle.ref("liquid.trace-input-short"), Icon.list, Styles.flatt,
                    () -> onTraceInput.accept(entry.ref, selected)).height(34f).tooltip(FsBundle.ref("liquid.trace-input"))
                    .name("factoryscope-liquid-trace-input");
                if(produces && onTraceOutput != null) row.button(FsBundle.ref("liquid.trace-output-short"), Icon.list, Styles.flatt,
                    () -> onTraceOutput.accept(entry.ref, selected)).height(34f).tooltip(FsBundle.ref("liquid.trace-output"))
                    .name("factoryscope-liquid-trace-output");
                if(onLocate != null) row.button(Icon.zoomSmall, Styles.emptyi, () -> onLocate.accept(entry.ref))
                    .size(34f).tooltip(FsBundle.ref("area.locate")).name("factoryscope-liquid-locate");
                if(onInspect != null){
                    row.button(Icon.eye, Styles.emptyi, () -> onInspect.accept(entry.ref))
                        .size(34f).tooltip(FsBundle.ref("trace.inspect")).name("factoryscope-liquid-inspect");
                }
            }).growX().padTop(1f).row();
        }
        if(limit < relevant.size()) body.button(FsBundle.get("area.show-more"), Styles.flatt,
            () -> { shownDetails += PAGE; rebuild(); }).name("factoryscope-liquid-details-more").row();
    }

    private void buildStoredLiquids(){
        List<Map.Entry<AreaEntry, StoredLiquidState>> stored = new ArrayList<>();
        for(AreaEntry entry : area.entries){
            if(entry.snapshot == null) continue;
            for(StoredLiquidState state : entry.snapshot.storedLiquids) if(selected == null || state.liquid.equals(selected))
                stored.add(new AbstractMap.SimpleImmutableEntry<>(entry, state));
        }
        stored.sort(Comparator.comparingInt((Map.Entry<AreaEntry, StoredLiquidState> entry) -> entry.getKey().ref.tileX)
            .thenComparingInt(entry -> entry.getKey().ref.tileY).thenComparing(entry -> entry.getKey().ref.blockId)
            .thenComparing(entry -> entry.getValue().liquid.key()));
        if(stored.isEmpty()) return;
        section("liquid.current-storage");
        body.table(Tex.pane, table -> {
            table.margin(8f).left().defaults().growX().left();
            table.add(FsBundle.get("liquid.storage-capacity-note")).color(Pal.gray).wrap().padBottom(4f).row();
            int limit = Math.min(stored.size(), shownStored);
            for(int i = 0; i < limit; i++){
                var entry = stored.get(i);
                table.table(row -> {
                    ContentIcons.add(row, ResourceKind.liquid, entry.getValue().liquid.id, 22f);
                    row.add(entry.getKey().ref.blockName).growX().left().ellipsis(true).minWidth(0f);
                    row.add(FsBundle.format("value.of", Numbers.amount(entry.getValue().amount),
                        Numbers.amount(entry.getValue().capacity))).color(Pal.lightishGray).right();
                }).growX().row();
            }
            if(stored.size() > limit) table.button(FsBundle.get("area.show-more"), Styles.flatt,
                () -> { shownStored += PAGE; rebuild(); }).name("factoryscope-liquid-storage-more").row();
        }).growX().row();
    }

    private void buildTrace(){
        body.add(FsBundle.format("liquid.trace-target", trace.target.blockName)).color(Pal.lightishGray).wrap().row();
        body.add(trace.liquid.name).color(Pal.accent).padTop(4f).row();
        body.add(FsBundle.get(trace.direction == TraceDirection.input ? "liquid.trace-structural-input" : "liquid.trace-structural-output"))
            .color(Pal.gray).wrap().padTop(4f).row();
        if(!trace.targetIncluded){
            body.add(FsBundle.get("liquid.target-outside-snapshot"))
                .color(Pal.lightOrange).wrap().padTop(6f).row();
            return;
        }
        if(!trace.targetUsesLiquid){
            body.add(FsBundle.get(trace.requirementsIncomplete
                ? "liquid.target-requirement-incomplete" : "liquid.trace-target-missing"))
                .color(Pal.lightOrange).wrap().padTop(6f).row();
            return;
        }
        if(trace.noRouteProven) body.add(FsBundle.get(trace.direction == TraceDirection.input
            ? "liquid.no-input-route" : "liquid.no-output-route"))
            .color(Pal.lightOrange).wrap().padTop(6f).row();
        if(!trace.complete) body.add(FsBundle.get("liquid.trace-incomplete"))
            .color(Pal.lightOrange).wrap().padTop(6f).row();
        if(!trace.boundaryContinuations.isEmpty()){
            int count = trace.boundaryContinuations.size();
            body.add(count == 1 ? FsBundle.get("liquid.trace-boundary-one")
                : FsBundle.format("liquid.trace-boundary-many", count))
                .color(Pal.accent).wrap().padTop(6f).row();
        }
        for(NetworkPort port : trace.boundaryContinuations) endpointRow(port.building, "boundary");
        if(!trace.unsupportedTransports.isEmpty()){
            body.add(FsBundle.get("liquid.trace-unsupported")).color(Pal.lightOrange).wrap().padTop(6f).row();
            for(BuildingRef unsupported : trace.unsupportedTransports) endpointRow(unsupported, "unsupported");
        }
        if(!trace.incompleteConnections.isEmpty()){
            body.add(FsBundle.get("liquid.trace-incomplete-connections")).color(Pal.lightOrange).wrap().padTop(6f).row();
            for(LiquidUncertainty uncertainty : trace.incompleteConnections) endpointRow(uncertainty.building, "incomplete");
        }
        if(!trace.structuralDeadEnds.isEmpty()){
            body.add(FsBundle.get("liquid.trace-dead-ends")).color(Pal.lightOrange).wrap().padTop(6f).row();
            for(BuildingRef ref : trace.structuralDeadEnds) endpointRow(ref, "dead-end");
        }
        boolean hasProducer = trace.endpoints.stream().anyMatch(endpoint -> endpoint.kind == TraceEndpointKind.producer);
        boolean hasConsumer = trace.endpoints.stream().anyMatch(endpoint -> endpoint.kind == TraceEndpointKind.consumer);
        if(trace.direction == TraceDirection.input && !hasProducer && trace.incompleteConnections.isEmpty()){
            body.add(FsBundle.get("liquid.no-in-area-producer")).color(Pal.lightishGray).wrap().padTop(6f).row();
        }else if(trace.direction == TraceDirection.output && !hasConsumer && trace.incompleteConnections.isEmpty()){
            body.add(FsBundle.get("liquid.no-in-area-consumer")).color(Pal.lightishGray).wrap().padTop(6f).row();
        }
        if(!trace.endpoints.isEmpty()){
            section(trace.direction == TraceDirection.input ? "liquid.reachable-sources" : "liquid.reachable-destinations");
            for(LiquidTraceEndpoint endpoint : trace.endpoints){
                body.table(row -> {
                    row.add(endpoint.building.blockName).growX().left().ellipsis(true).minWidth(0f);
                    row.add(FsBundle.get(endpoint.kind == TraceEndpointKind.storage ? "liquid.storage-endpoint" :
                        endpoint.kind == TraceEndpointKind.producer ? "liquid.producer" : "liquid.consumer"))
                        .color(Pal.lightishGray).right();
                }).growX().padTop(3f).row();
                if(endpoint.diagnostic != null) body.add(Diagnostics.status(endpoint.diagnostic.reason()))
                    .color(Diagnostics.color(endpoint.diagnostic.reason())).wrap().growX().left().padTop(1f).row();
                body.table(row -> {
                    String role = endpoint.kind == TraceEndpointKind.storage ? "storage" :
                        endpoint.kind == TraceEndpointKind.producer ? "producer" : "consumer";
                    actionButtons(row, endpoint.building, role);
                }).growX().padTop(1f).row();
            }
        }
        body.add(FsBundle.format("liquid.trace-edges", trace.traversedEdges.size()))
            .color(Pal.gray).wrap().growX().left().padTop(8f).row();
    }

    private void endpointRow(BuildingRef ref, String role){
        body.table(row -> {
            row.add(ref.blockName).color(Pal.lightOrange)
                .growX().left().ellipsis(true).minWidth(0f);
            row.add("(" + ref.tileX + ", " + ref.tileY + ")").color(Pal.lightishGray).right().padRight(5f);
        }).growX().padTop(3f).row();
        body.table(row -> {
            actionButtons(row, ref, role);
        }).growX().padTop(1f).row();
    }

    private void actionButtons(Table row, BuildingRef ref, String role){
        if(onLocate != null) row.button(Icon.zoomSmall, Styles.emptyi, () -> onLocate.accept(ref)).size(34f)
            .tooltip(FsBundle.ref("area.locate")).name("factoryscope-liquid-" + role + "-locate");
        if(onInspect != null){
            row.button(Icon.eye, Styles.emptyi, () -> onInspect.accept(ref))
                .size(34f).tooltip(FsBundle.ref("trace.inspect")).name("factoryscope-liquid-" + role + "-inspect");
        }
    }

    private void row(Table table, String key, String value){
        table.table(row -> {
            row.add(FsBundle.get(key)).growX().left();
            row.add(value).color(Pal.accent).right();
        }).padBottom(2f).row();
    }

    private void section(String key){
        body.add(FsBundle.get(key)).color(Pal.accent).padTop(10f).padBottom(3f).left().row();
        body.image(Tex.whiteui).height(3f).color(Pal.accent).growX().padBottom(4f).row();
    }
}
