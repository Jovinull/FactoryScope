package factoryscope.acceptance;

import arc.*;
import arc.math.*;
import arc.math.geom.*;
import arc.input.*;
import arc.scene.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import factoryscope.*;
import factoryscope.analysis.*;
import factoryscope.area.*;
import factoryscope.model.*;
import factoryscope.liquid.*;
import factoryscope.network.*;
import factoryscope.probe.*;
import factoryscope.trace.*;
import factoryscope.ui.*;
import mindustry.content.*;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.environment.Floor;
import mindustry.world.blocks.payloads.*;
import mindustry.world.blocks.units.UnitCargoUnloadPoint;

import java.util.*;

import static mindustry.Vars.*;

/** Item Network and Supply Trace scenarios, including incomplete-topology evidence. */
final class ItemNetworkScenarios{
    private final AcceptanceHarness harness;
    private Building target, producer, disabledProducer, disabledRouteBreak;
    private AreaDiagnosticResult traceSnapshotBeforeRefresh;
    private final Seq<Building> patch = new Seq<>();

    ItemNetworkScenarios(AcceptanceHarness harness){ this.harness = harness; }

    void queueBeforeScopes(){
        singlePanelSupplyTrace();
        armoredItemTransportTrace();
        traceCompletenessScenarios();
        outputTraceBoundary();
        outputTraceDeadEnd();
    }

    void queueTransportFamilies(){ transportFamilyScenarios(); }
    void queueResourceTransportShowcase(){
        payloadNetworkView();
        reconstructorPayloadTrace();
        unitCargoTrace();
    }
    void queueUnsupportedLocate(){ unsupportedTraceLocate(); }
    void queueTinyDrag(){ tinyDragIsAClick(); }
    void queueAfterTinyDrag(){
        mixedProblems();
        itemNetworkView();
    }

    void singlePanelSupplyTrace(){
        scenario("a missing item can be traced from single-building diagnostics");
        queue(this::closeAnyDialog);
        queue(() -> {
            clearRegion();
            for(int x = rx() + 2; x <= rx() + 3; x++){
                for(int y = ry() + 3; y <= ry() + 4; y++){
                    Tile tile = world.tile(x, y);
                    tile.setFloor((Floor)Blocks.sand);
                    tile.clearOverlay();
                }
            }
            producer = placeAt(Blocks.mechanicalDrill, rx() + 2, ry() + 3);
            placeAt(Blocks.conveyor, rx() + 4, ry() + 3);
            placeAt(Blocks.conveyor, rx() + 5, ry() + 3);
            placeAt(Blocks.conveyor, rx() + 6, ry() + 3);
            target = placeAt(Blocks.siliconSmelter, rx() + 7, ry() + 3);

            for(int x = rx() + 2; x <= rx() + 3; x++){
                for(int y = ry() + 5; y <= ry() + 6; y++){
                    Tile tile = world.tile(x, y);
                    tile.setFloor((Floor)Blocks.sand);
                    tile.clearOverlay();
                }
            }
            disabledProducer = placeAt(Blocks.mechanicalDrill, rx() + 2, ry() + 5);
            disabledProducer.enabled = false;
            placeAt(Blocks.conveyor, rx() + 4, ry() + 5);
            placeAt(Blocks.conveyor, rx() + 5, ry() + 5);
            disabledRouteBreak = placeAt(Blocks.conveyor, rx() + 6, ry() + 5);
            placeAt(Blocks.conveyor, rx() + 7, ry() + 5, 3);
        });
        queue(this::armPicker);
        queue(() -> clickBuilding(target));
        queue(() -> {
            check("a missing item offers Supply Trace", Core.scene.find("factoryscope-trace-input-sand") != null);
            check("single-building diagnostics remains open", FactoryScopeUI.inspected() == target);
        });
        queue(() -> clickNamed("factoryscope-trace-input-sand"));
        queue(() -> {
            check("Trace asks for an area when none is active", FactoryScopeUI.picking());
            check("the single-building panel leaves room for the area selection", FactoryScopeUI.inspected() == null);
        });
        queue(() -> dragTiles(rx() + 2, ry() + 2, rx() + 7, ry() + 7));
        queue(() -> {
            check("the Supply Trace opens after area selection", Core.scene.find("factoryscope-trace-back") != null);
            check("the trace keeps the analyzed area available", FactoryScopeUI.areaReportHeld()
                && FactoryScopeUI.areaReport() != null && FactoryScopeUI.areaReport().network != null);
            AreaDiagnosticResult report = FactoryScopeUI.areaReport();
            ResourceRef sand = new ResourceRef(ResourceKind.item, "sand", "Sand");
            AreaEntry drillEntry = report == null ? null : report.entries.stream()
                .filter(entry -> entry.ref.equals(AreaProbe.refOf(producer))).findFirst().orElse(null);
            check("the area snapshot recognizes the drill's mined item",
                drillEntry != null && drillEntry.snapshot != null && drillEntry.snapshot.producedItems.contains(sand),
                drillEntry == null || drillEntry.snapshot == null ? "no drill snapshot"
                    : drillEntry.snapshot.producedItems.toString());
            SupplyTrace trace = report == null ? null : TraceAnalyzer.input(report, AreaProbe.refOf(target), sand);
            check("the area topology reaches the drill for Sand",
                trace != null && trace.producers().stream().anyMatch(endpoint -> endpoint.building.equals(AreaProbe.refOf(producer))),
                trace == null ? "no trace" : "complete=" + trace.complete + " targetUsesResource=" + trace.targetUsesResource
                    + " diagnosticsIncomplete=" + trace.diagnosticsIncomplete + " edges=" + report.network.graph.edges.size());
            check("each reachable Sand producer appears once",
                trace != null && trace.producers().size() == 2
                    && trace.producers().stream().map(endpoint -> endpoint.building).distinct().count() == 2,
                trace == null ? "no trace" : trace.producers().stream().map(endpoint -> endpoint.building.toString()).toList().toString());
            TraceEndpoint disabled = trace == null ? null : trace.producers().stream()
                .filter(endpoint -> endpoint.building.equals(AreaProbe.refOf(disabledProducer))).findFirst().orElse(null);
            check("the disabled reachable producer retains its diagnostic state",
                disabled != null && disabled.diagnostic != null && disabled.diagnostic.reason() == DiagnosticReason.disabled);
            check("the real drill is listed as a reachable Sand producer",
                Core.scene.find("factoryscope-trace-endpoint-" + producer.tile.x + "-" + producer.tile.y) != null);
            check("the disabled producer is also listed in the trace",
                Core.scene.find("factoryscope-trace-endpoint-" + disabledProducer.tile.x + "-" + disabledProducer.tile.y) != null);
            TraceEndpoint active = trace == null ? null : trace.producers().stream()
                .filter(endpoint -> endpoint.building.equals(AreaProbe.refOf(producer))).findFirst().orElse(null);
            check("the operating source retains its diagnostic state",
                active != null && active.diagnostic != null && active.diagnostic.reason() != DiagnosticReason.disabled);
            check("the trace displays that producer's diagnostic label",
                active != null && active.diagnostic != null
                    && dialogShows(AreaText.status(AreaStatus.of(active.diagnostic.reason()))));
            check("the trace annotates a disabled reachable producer",
                dialogShows(FsBundle.get("area.status.disabled")));
            check("the reachable producer can be inspected",
                Core.scene.find("factoryscope-trace-inspect") != null);
        });
        queue(() -> capture("supply-trace-producer"));
        queue(() -> {
            disabledRouteBreak.tile.remove();
            check("the selected route segment was removed before Refresh", disabledRouteBreak.tile.build == null);
        });
        queue(() -> {
            AreaDiagnosticResult beforeRefresh = FactoryScopeUI.areaReport();
            traceSnapshotBeforeRefresh = beforeRefresh;
            SupplyTrace trace = beforeRefresh == null ? null
                : TraceAnalyzer.input(beforeRefresh, AreaProbe.refOf(target), new ResourceRef(ResourceKind.item, "sand", "Sand"));
            check("the open trace remains a snapshot until Refresh", trace != null && trace.producers().size() == 2,
                trace == null ? "no snapshot" : trace.producers().stream().map(endpoint -> endpoint.building.toString()).toList().toString());
        });
        queue(() -> clickNamed("factoryscope-network-refresh"));
        queue(() -> {
            AreaDiagnosticResult refreshed = FactoryScopeUI.areaReport();
            check("Refresh replaces the area report snapshot", refreshed != traceSnapshotBeforeRefresh,
                refreshed == null ? "report missing" : "same report instance");
            SupplyTrace trace = refreshed == null ? null
                : TraceAnalyzer.input(refreshed, AreaProbe.refOf(target), new ResourceRef(ResourceKind.item, "sand", "Sand"));
            check("Refresh rebuilds the trace after a route is removed", trace != null && trace.producers().size() == 1,
                trace == null ? "no refreshed trace" : trace.producers().stream()
                    .map(endpoint -> endpoint.building + " path=" + endpoint.path.ports().stream()
                        .map(port -> port.building + "/" + port.side + "/" + port.channel).toList()).toList().toString());
            check("Refresh removes the no-longer-reachable producer row",
                Core.scene.find("factoryscope-trace-endpoint-" + disabledProducer.tile.x + "-" + disabledProducer.tile.y) == null);
        });
        queue(() -> {
            Scl.setProduct(2f);
            Core.scene.resize(1280, 720);
        });
        queue(() -> {
            ScrollPane pane = findPane(Core.scene.getDialog());
            if(pane != null){
                pane.setScrollPercentY(0f);
                pane.updateVisualScroll();
            }
            checkFits("Supply Trace at 1280x720 @ 2.0x");
            Element endpoint = Core.scene.find("factoryscope-trace-endpoint-" + producer.tile.x + "-" + producer.tile.y);
            float minimumRowWidth = (Core.scene.getWidth() / Scl.scl() - 40f) * 0.85f;
            check("Supply Trace uses the available width at 2.0x",
                endpoint != null && endpoint.getWidth() >= minimumRowWidth,
                endpoint == null ? "producer row missing" : "row width " + endpoint.getWidth() + ", expected at least " + minimumRowWidth);
        });
        queue(() -> Core.scene.resize(Core.graphics.getWidth(), Core.graphics.getHeight()));
        queue(() -> capture("supply-trace-high-ui-scale"));
        queue(this::restoreLayout);
        queue(() -> clickNamed("factoryscope-trace-inspect"));
        queue(() -> check("Inspect opens diagnostics for the reachable drill", FactoryScopeUI.inspected() == producer));
        queue(this::closeAnyDialog);
        queue(() -> check("closing producer diagnostics returns to the same trace",
            Core.scene.find("factoryscope-trace-back") != null));
        queue(() -> clickNamed("factoryscope-trace-back"));
        queue(() -> check("return from Supply Trace restores the Network view",
            Core.scene.find("factoryscope-network-dialog") != null));
        queue(this::closeAnyDialog);
    }

    void armoredItemTransportTrace(){
        int x = rx() + 2, y = ry() + 3;
        Building[] drills = new Building[2], transports = new Building[2], targets = new Building[2];
        Block[] transportBlocks = {Blocks.armoredConveyor, Blocks.armoredDuct};

        scenario("Armored Conveyor and Armored Duct expose their proven structural routes");
        queue(this::closeAnyDialog);
        queue(this::ensurePickerOff);
        queue(() -> {
            clearRegion();
            for(int route = 0; route < 2; route++){
                int routeY = y + route * 6;
                for(int floorX = x; floorX <= x + 1; floorX++) for(int floorY = routeY; floorY <= routeY + 1; floorY++){
                    Tile tile = world.tile(floorX, floorY);
                    tile.setFloor((Floor)Blocks.sand);
                    tile.clearOverlay();
                }
                drills[route] = placeAt(Blocks.mechanicalDrill, x, routeY);
                transports[route] = placeAt(transportBlocks[route], x + 2, routeY, 0);
                placeAt(Blocks.conveyor, x + 3, routeY, 0);
                placeAt(Blocks.conveyor, x + 4, routeY, 0);
                targets[route] = placeAt(Blocks.siliconSmelter, x + 5, routeY);
                check(transportBlocks[route].name + " accepts the aligned drill in Mindustry",
                    transports[route].acceptItem(drills[route], Items.sand));
            }
        });
        queue(this::armPicker);
        queue(() -> dragTiles(x - 1, y - 1, x + 9, y + 10));
        queue(() -> {
            AreaDiagnosticResult report = FactoryScopeUI.areaReport();
            ResourceRef sand = new ResourceRef(ResourceKind.item, "sand", "Sand");
            check("the real area-selection drag ends picker mode", !FactoryScopeUI.picking());
            check("the real area-selection drag opens Area Diagnostics",
                Core.scene.getDialog() != null && Core.scene.getDialog().name.equals("factoryscope-area-dialog"));
            check("the selected area contains both armored routes",
                report != null && report.network != null
                    && report.entries.stream().anyMatch(entry -> entry.ref.equals(AreaProbe.refOf(targets[0])))
                    && report.entries.stream().anyMatch(entry -> entry.ref.equals(AreaProbe.refOf(targets[1]))));
            for(int route = 0; route < 2; route++){
                int index = route;
                SupplyTrace trace = report == null ? null : TraceAnalyzer.input(report, AreaProbe.refOf(targets[index]), sand);
                boolean producer = trace != null && trace.producers().stream()
                    .anyMatch(endpoint -> endpoint.building.equals(AreaProbe.refOf(drills[index])));
                boolean includesTransport = trace != null && trace.producers().stream().anyMatch(endpoint -> endpoint.path.ports().stream()
                    .anyMatch(port -> port.building.equals(AreaProbe.refOf(transports[index]))));
                check(transportBlocks[index].localizedName + " has a complete structural trace from its drill",
                    trace != null && trace.complete && producer && includesTransport,
                    trace == null ? "no trace" : "complete=" + trace.complete + " producers=" + trace.producers().size());
            }
        });
        queue(() -> {
            check("Area Diagnostics exposes its Network view", dialogHasNamed("factoryscope-area-network"));
            if(dialogHasNamed("factoryscope-area-network")) clickNamedInDialog("factoryscope-area-network");
        });
        queue(() -> check("the active dialog is the production Network view",
            Core.scene.getDialog() != null && Core.scene.getDialog().name.equals("factoryscope-network-dialog")));
        queue(() -> {
            check("Network offers the Sand resource filter", dialogHasNamed("factoryscope-network-resource-sand"));
            if(dialogHasNamed("factoryscope-network-resource-sand")) clickNamedInDialog("factoryscope-network-resource-sand");
        });
        queue(this::scrollReportToBottom);
        queue(() -> {
            if(dialogHasNamed("factoryscope-network-more")) clickNamedInDialog("factoryscope-network-more");
        });
        queue(this::scrollReportToBottom);
        queue(() -> {
            AreaDiagnosticResult report = FactoryScopeUI.areaReport();
            List<BuildingRef> buildings = report == null || report.network == null ? List.of()
                : report.network.graph.edges.stream()
                    .flatMap(edge -> java.util.stream.Stream.of(edge.from.building, edge.to.building))
                    .distinct()
                    .sorted(Comparator.comparingInt((BuildingRef ref) -> ref.tileX)
                        .thenComparingInt(ref -> ref.tileY).thenComparing(ref -> ref.blockId).thenComparingInt(ref -> ref.teamId))
                    .toList();
            int targetIndex = buildings.indexOf(AreaProbe.refOf(targets[0]));
            check("the Armored Conveyor consumer appears in Network", targetIndex >= 0, "buildings=" + buildings);
            if(targetIndex >= 0) clickNamedInDialog("factoryscope-network-building", targetIndex);
        });
        for(int route = 0; route < 2; route++){
            int index = route;
            if(route == 1){
                queue(() -> {
                    check("return from Armored Conveyor trace restores Network details",
                        Core.scene.getDialog() != null && Core.scene.getDialog().name.equals("factoryscope-network-dialog"));
                    if(dialogHasNamed("factoryscope-trace-back")) clickNamedInDialog("factoryscope-trace-back");
                });
                queue(this::scrollReportToBottom);
                queue(() -> {
                    AreaDiagnosticResult report = FactoryScopeUI.areaReport();
                    List<BuildingRef> buildings = report == null || report.network == null ? List.of()
                        : report.network.graph.edges.stream()
                            .flatMap(edge -> java.util.stream.Stream.of(edge.from.building, edge.to.building))
                            .distinct()
                            .sorted(Comparator.comparingInt((BuildingRef ref) -> ref.tileX)
                                .thenComparingInt(ref -> ref.tileY).thenComparing(ref -> ref.blockId).thenComparingInt(ref -> ref.teamId))
                            .toList();
                    int targetIndex = buildings.indexOf(AreaProbe.refOf(targets[1]));
                    check("the Armored Duct consumer appears in Network", targetIndex >= 0, "buildings=" + buildings);
                    if(targetIndex >= 0) clickNamedInDialog("factoryscope-network-building", targetIndex);
                });
            }
            queue(() -> {
                AreaDiagnosticResult report = FactoryScopeUI.areaReport();
                ResourceRef sand = new ResourceRef(ResourceKind.item, "sand", "Sand");
                int occurrence = traceInputOccurrence(report, AreaProbe.refOf(targets[index]), sand);
                check(transportBlocks[index].localizedName + " detail offers its Sand trace",
                    occurrence >= 0 && dialogHasNamed("factoryscope-network-trace-input"), "occurrence=" + occurrence);
                if(occurrence >= 0 && dialogHasNamed("factoryscope-network-trace-input")){
                    scrollReportToBottom();
                    clickNamedInDialog("factoryscope-network-trace-input", occurrence);
                }
            });
            queue(() -> {
                AreaDiagnosticResult report = FactoryScopeUI.areaReport();
                ResourceRef sand = new ResourceRef(ResourceKind.item, "sand", "Sand");
                SupplyTrace trace = report == null ? null : TraceAnalyzer.input(report, AreaProbe.refOf(targets[index]), sand);
                check(transportBlocks[index].localizedName + " Network action opens the selected trace",
                    Core.scene.getDialog() != null && Core.scene.getDialog().name.equals("factoryscope-network-dialog")
                        && dialogShows(FsBundle.format("trace.target", AreaProbe.refOf(targets[index]).blockName))
                        && dialogShows(Items.sand.localizedName));
                check(transportBlocks[index].localizedName + " route is complete from its engine producer",
                    trace != null && trace.complete
                        && trace.producers().stream().anyMatch(endpoint -> endpoint.building.equals(AreaProbe.refOf(drills[index]))),
                    trace == null ? "no trace" : "complete=" + trace.complete + " producers=" + trace.producers().size());
                check(transportBlocks[index].localizedName + " trace renders its producer endpoint row",
                    dialogHasNamed("factoryscope-trace-endpoint-" + drills[index].tile.x + "-" + drills[index].tile.y));
                check(transportBlocks[index].localizedName + " trace exposes producer Inspect",
                    dialogHasNamed("factoryscope-trace-inspect"));
            });
            queue(() -> capture("armored-route-" + index));
            queue(this::scrollReportToBottom);
            queue(() -> clickNamedInDialog("factoryscope-trace-inspect"));
            queue(() -> check("Inspect opens the producer for " + transportBlocks[index].localizedName,
                FactoryScopeUI.inspected() == drills[index]));
            queue(this::closeAnyDialog);
            if(route == 0) queue(() -> capture("armored-item-transport-traces"));
        }
        queue(this::closeAnyDialog);
    }

    int traceInputOccurrence(AreaDiagnosticResult report, BuildingRef targetRef, ResourceRef resource){
        if(report == null) return -1;
        for(AreaEntry entry : report.entries){
            if(!entry.ref.equals(targetRef) || entry.snapshot == null) continue;
            java.util.Set<String> seen = new java.util.HashSet<>();
            int occurrence = 0;
            for(ResourceState input : entry.snapshot.inputs){
                if(input.kind != ResourceKind.item || input.contentId == null || !seen.add(input.ref().key())) continue;
                if(input.ref().equals(resource)) return occurrence;
                occurrence++;
            }
            return -1;
        }
        return -1;
    }

    void traceCompletenessScenarios(){
        int x = rx() + 7, boundaryX = rx() + 12, unsupportedX = rx() + 5, y = ry() + 3;
        ResourceRef sand = new ResourceRef(ResourceKind.item, "sand", "Sand");

        scenario("Supply Trace proves no route only for a complete isolated target");
        queue(this::closeAnyDialog);
        queue(this::closeAnyDialog);
        queue(() -> {
            clearRegion();
            target = placeAt(Blocks.siliconSmelter, x, y);
        });
        queue(this::armPicker);
        queue(() -> clickBuilding(target));
        queue(() -> check("the isolated target offers an item trace",
            Core.scene.find("factoryscope-trace-input-sand") != null));
        queue(() -> clickNamed("factoryscope-trace-input-sand"));
        queue(() -> delayNextAction(30f));
        queue(() -> dragTiles(x, y, x + 1, y + 1));
        queue(() -> {
            AreaDiagnosticResult report = FactoryScopeUI.areaReport();
            SupplyTrace trace = report == null ? null : TraceAnalyzer.input(report, AreaProbe.refOf(target), sand);
            check("a complete isolated input proves no structural route", trace != null && trace.noRouteProven);
            check("the UI reports no route", dialogShows(FsBundle.get("trace.no-route")));
            check("the isolated target has no boundary continuation", trace != null && trace.boundaryContinuations.isEmpty());
        });
        queue(() -> capture("supply-trace-no-route"));
        queue(this::closeAnyDialog);

        scenario("Supply Trace preserves an incoming area boundary");
        queue(this::closeAnyDialog);
        queue(this::closeAnyDialog);
        queue(() -> {
            clearRegion();
            placeAt(Blocks.conveyor, boundaryX - 1, y, 0);
            target = placeAt(Blocks.siliconSmelter, boundaryX, y);
        });
        queue(this::armPicker);
        queue(() -> clickBuilding(target));
        queue(() -> clickNamed("factoryscope-trace-input-sand"));
        queue(() -> delayNextAction(30f));
        queue(() -> dragTiles(boundaryX, y, boundaryX + 1, y + 1));
        queue(() -> {
            AreaDiagnosticResult report = FactoryScopeUI.areaReport();
            SupplyTrace trace = report == null ? null : TraceAnalyzer.input(report, AreaProbe.refOf(target), sand);
            check("the route is marked as continuing outside the selection",
                trace != null && !trace.boundaryContinuations.isEmpty()
                    && dialogShows(FsBundle.format("trace.boundary-one", trace.boundaryContinuations.size())));
            check("a boundary is not shown as no route", trace != null && !trace.noRouteProven
                && !dialogShows(FsBundle.get("trace.no-route")));
            check("the boundary is not reported as a structural dead end", trace != null && trace.structuralDeadEnds.isEmpty());
        });
        queue(() -> capture("supply-trace-boundary"));
        queue(this::closeAnyDialog);

        scenario("Supply Trace marks an unsupported transport interruption");
        queue(this::closeAnyDialog);
        queue(this::closeAnyDialog);
        queue(() -> {
            clearRegion();
            //every vanilla item transport is modeled; the sandbox Item Source is in the transportation group
            //without a transport model, so it stands in for an unknown modded transport
            placeAt(Blocks.itemSource, unsupportedX - 1, y, 1);
            target = placeAt(Blocks.siliconSmelter, unsupportedX, y);
            check("unsupported-route fixture keeps both buildings", target != null
                && world.tile(unsupportedX - 1, y).build != null
                && world.tile(unsupportedX - 1, y).build.block == Blocks.itemSource,
                "transport=" + world.tile(unsupportedX - 1, y).build + ", target=" + target);
        });
        queue(this::armPicker);
        queue(() -> clickBuilding(target));
        queue(() -> clickNamed("factoryscope-trace-input-sand"));
        queue(() -> delayNextAction(30f));
        queue(() -> dragTiles(unsupportedX - 1, y, unsupportedX + 1, y + 1));
        queue(() -> {
            AreaDiagnosticResult report = FactoryScopeUI.areaReport();
            SupplyTrace trace = report == null ? null : TraceAnalyzer.input(report, AreaProbe.refOf(target), sand);
            check("the unsupported transport makes the trace incomplete",
                trace != null && !trace.complete && !trace.unsupportedInterruptions.isEmpty(),
                trace == null ? "no trace; picker=" + FactoryScopeUI.picking() + ", report=" + (report != null)
                    : "complete=" + trace.complete + ", unsupported=" + trace.unsupportedInterruptions.size()
                        + ", bounds=" + (report == null ? "none" : report.selection));
            check("the UI reports unsupported topology", dialogShows(FsBundle.get("trace.unsupported-area-one")));
            check("unsupported topology is not shown as a dead end or no route", trace != null
                && !trace.noRouteProven && trace.structuralDeadEnds.isEmpty()
                && !dialogShows(FsBundle.get("trace.no-route")));
        });
        queue(() -> capture("supply-trace-unsupported"));
        queue(this::closeAnyDialog);
    }

    /** Supply Trace and LiquidScope across the linked, unloading and armored transports. */
    void transportFamilyScenarios(){
        scenario("Supply Trace follows a duct bridge and a sorting unloader back to storage");
        queue(this::closeAnyDialog);
        queue(() -> {
            clearRegion();
            int x = rx(), y = ry();
            //the container is empty, so the smelter keeps its sand shortage and offers the trace
            producer = placeAt(Blocks.container, x + 2, y + 3);
            Building unloader = placeAt(Blocks.unloader, x + 4, y + 3);
            if(unloader != null) unloader.configure(Items.sand);
            placeAt(Blocks.ductBridge, x + 5, y + 3, 0);
            placeAt(Blocks.ductBridge, x + 8, y + 3, 0);
            target = placeAt(Blocks.siliconSmelter, x + 9, y + 3);
        });
        queue(this::armPicker);
        queue(() -> clickBuilding(target));
        queue(() -> check("missing sand offers Supply Trace", Core.scene.find("factoryscope-trace-input-sand") != null));
        queue(() -> clickNamed("factoryscope-trace-input-sand"));
        queue(() -> dragTiles(rx() + 1, ry() + 1, rx() + 11, ry() + 7));
        queue(() -> {
            AreaDiagnosticResult report = FactoryScopeUI.areaReport();
            SupplyTrace trace = report == null ? null
                : TraceAnalyzer.input(report, AreaProbe.refOf(target), new ResourceRef(ResourceKind.item, "sand", "Sand"));
            check("the trace crosses the bridge and the unloader to the container", trace != null && trace.complete
                && trace.endpoints.stream().anyMatch(endpoint -> endpoint.kind == TraceEndpointKind.storage
                    && endpoint.building.equals(AreaProbe.refOf(producer))),
                trace == null ? "no trace" : "complete=" + trace.complete + ", endpoints=" + trace.endpoints.size()
                    + ", unsupported=" + trace.unsupportedInterruptions.size());
            check("none of these transports is reported as unsupported", trace != null && trace.unsupportedInArea.isEmpty()
                && !dialogShows(FsBundle.get("trace.unsupported-area-one")));
            capture("supply-trace-unloader-bridge");
        });
        //closing the trace shows the area report again
        closeDialogStack();

        scenario("LiquidScope follows a bridge conduit and a plated conduit back to a pump");
        closeDialogStack();
        queue(() -> {
            clearRegion();
            int x = rx(), y = ry();
            for(int dx = 2; dx <= 3; dx++) for(int dy = 2; dy <= 3; dy++){
                Tile floor = world.tile(x + dx, y + dy);
                floor.setFloor((Floor)Blocks.water);
                floor.clearOverlay();
            }
            producer = placeAt(Blocks.mechanicalPump, x + 3, y + 3);
            Building first = placeAt(Blocks.bridgeConduit, x + 4, y + 3);
            Building last = placeAt(Blocks.bridgeConduit, x + 7, y + 3);
            if(first != null && last != null) first.configure(last.tile.pos());
            placeAt(Blocks.platedConduit, x + 8, y + 3, 0);
            target = placeAt(Blocks.cryofluidMixer, x + 9, y + 3);
        });
        queue(() -> {
            //keep the mixer's water shortage, as the conduit scenario does
            producer.enabled = false;
            for(int dx = 3; dx <= 8; dx++){
                Building build = world.tile(rx() + dx, ry() + 3).build;
                if(build != null && build.liquids != null) build.liquids.clear();
            }
            target.liquids.clear();
        });
        queue(this::armPicker);
        queue(() -> clickBuilding(target));
        queue(() -> check("missing water offers LiquidScope", Core.scene.find("factoryscope-trace-liquid-water") != null));
        queue(() -> clickNamed("factoryscope-trace-liquid-water"));
        queue(() -> dragTiles(rx() + 1, ry() + 1, rx() + 11, ry() + 7));
        queue(() -> {
            LiquidTrace trace = FactoryScopeUI.liquidTrace();
            check("the water trace crosses the bridge and the plated conduit to the pump", trace != null && trace.complete
                && trace.producers().stream().anyMatch(endpoint -> endpoint.building.equals(AreaProbe.refOf(producer))),
                trace == null ? "no trace" : "complete=" + trace.complete + ", endpoints=" + trace.endpoints.size()
                    + ", unsupported=" + trace.unsupportedTransports.size());
            check("neither liquid transport is reported as unsupported", trace != null && trace.unsupportedTransports.isEmpty());
            capture("liquid-trace-bridge-plated");
        });
        closeDialogStack();
    }

    void unsupportedTraceLocate(){
        int x = rx() + 5, y = ry() + 4;
        scenario("an unsupported transport interruption can be located and returned to");
        queue(this::closeAnyDialog);
        queue(() -> {
            clearRegion();
            placeAt(Blocks.itemSource, x - 1, y, 1);
            target = placeAt(Blocks.siliconSmelter, x, y);
        });
        queue(this::armPicker);
        queue(() -> clickBuilding(target));
        queue(() -> clickNamed("factoryscope-trace-input-sand"));
        queue(() -> delayNextAction(30f));
        queue(() -> dragTiles(x - 1, y, x + 1, y + 1));
        queue(() -> check("the unsupported route opens Supply Trace",
            Core.scene.find("factoryscope-trace-unsupported-locate") != null));
        queue(() -> {
            if(Core.scene.find("factoryscope-trace-unsupported-locate") != null){
                clickNamed("factoryscope-trace-unsupported-locate");
            }
        });
        queue(() -> check("Locate marks the unsupported transport and keeps a return path",
            FactoryScopeUI.locating() && Core.scene.find("factoryscope-locate-return") != null));
        queue(() -> {
            if(Core.scene.find("factoryscope-locate-return") != null) clickNamed("factoryscope-locate-return");
        });
        queue(() -> check("return from the unsupported transport restores its trace",
            !FactoryScopeUI.locating() && Core.scene.find("factoryscope-trace-back") != null));
        queue(this::closeAnyDialog);
    }

    void outputTraceBoundary(){
        int x = rx() + 2, y = ry() + 2;
        scenario("an output trace describes an outside continuation as having no in-area consumer");
        queue(this::closeAnyDialog);
        queue(() -> {
            clearRegion();
            target = placeAt(Blocks.graphitePress, x, y);
            placeAt(Blocks.conveyor, x + 2, y);
            placeAt(Blocks.conveyor, x + 3, y);
        });
        queue(this::armPicker);
        queue(() -> dragTiles(x - 1, y - 1, x + 2, y + 2));
        queue(() -> check("the output-trace selection produced an area report", FactoryScopeUI.areaReport() != null));
        queue(() -> clickNamed("factoryscope-area-network"));
        queue(() -> check("the produced item is available as a Network filter",
            Core.scene.find("factoryscope-network-resource-graphite") != null));
        queue(() -> clickNamed("factoryscope-network-resource-graphite"));
        queue(() -> clickNamed("factoryscope-network-building", 0));
        queue(() -> check("the producer exposes an output trace action",
            Core.scene.find("factoryscope-network-trace-output") != null));
        queue(() -> clickNamed("factoryscope-network-trace-output"));
        queue(() -> {
            AreaDiagnosticResult report = FactoryScopeUI.areaReport();
            SupplyTrace trace = report == null ? null : TraceAnalyzer.output(report, AreaProbe.refOf(target),
                new ResourceRef(ResourceKind.item, "graphite", "Graphite"));
            check("the structural output path reaches the area boundary",
                trace != null && !trace.boundaryContinuations.isEmpty() && trace.endpoints.isEmpty());
            check("the boundary uses consumer wording for an output trace",
                dialogShows(FsBundle.get("trace.no-in-area-consumer")));
            check("the boundary does not claim there is no producer",
                !dialogShows(FsBundle.get("trace.no-in-area-producer")));
        });
        queue(() -> capture("supply-trace-output-boundary"));
        queue(this::closeAnyDialog);
    }

    void outputTraceDeadEnd(){
        int x = rx() + 2, y = ry() + 2;
        scenario("an output trace distinguishes a known dead end from a missing route");
        queue(this::closeAnyDialog);
        queue(() -> {
            clearRegion();
            target = placeAt(Blocks.graphitePress, x, y);
            placeAt(Blocks.conveyor, x + 2, y, 0);
        });
        queue(this::armPicker);
        queue(() -> dragTiles(x - 1, y - 1, x + 3, y + 1));
        queue(() -> check("the output dead-end selection produced an area report", FactoryScopeUI.areaReport() != null));
        queue(() -> clickNamed("factoryscope-area-network"));
        queue(() -> clickNamed("factoryscope-network-resource-graphite"));
        queue(() -> clickNamed("factoryscope-network-building", 0));
        queue(() -> clickNamed("factoryscope-network-trace-output"));
        queue(() -> {
            AreaDiagnosticResult report = FactoryScopeUI.areaReport();
            SupplyTrace trace = report == null ? null : TraceAnalyzer.output(report, AreaProbe.refOf(target),
                new ResourceRef(ResourceKind.item, "graphite", "Graphite"));
            check("the route ends at the reachable conveyor", trace != null
                && trace.structuralDeadEnds.stream().anyMatch(ref -> ref.blockId.equals("conveyor")));
            check("a known output dead end is not reported as no structural route", trace != null
                && !trace.noRouteProven && !dialogShows(FsBundle.get("trace.no-downstream")));
            check("the output trace describes the missing in-area consumer", dialogShows(FsBundle.get("trace.no-consumer")));
        });
        queue(() -> capture("supply-trace-output-dead-end"));
        queue(this::closeAnyDialog);
    }

    /** A press that wanders a few pixels is a click, not a one-tile area report. */
    void tinyDragIsAClick(){
        scenario("a drag shorter than the threshold is treated as a click");
        queue(this::closeAnyDialog);
        queue(this::armPicker);
        queue(() -> {
            Vec2 screen = Core.camera.project(new Vec2(target.x, target.y));
            int sx = Mathf.round(screen.x), sy = Mathf.round(screen.y);
            Core.scene.touchDown(sx, sy, 0, KeyCode.mouseLeft);
            Core.scene.touchDragged(sx + 2, sy + 1, 0);
            Core.scene.touchDragged(sx + 3, sy + 2, 0);
            Core.scene.touchUp(sx + 3, sy + 2, 0, KeyCode.mouseLeft);
        });
        queue(() -> {
            check("a two-pixel wobble still inspected one building", FactoryScopeUI.inspected() == target);
            check("a two-pixel wobble opened no area report", FactoryScopeUI.areaBounds() == null);
        });
    }

    /**
     * The scenario the feature exists for: one selection over a patch with several different faults,
     * checked against the counts and the grouping the player will read.
     */
    void mixedProblems(){
        int x1 = rx(), y1 = ry(), x2 = rx() + 16, y2 = ry() + 11;

        scenario("a mixed area is summarised and grouped correctly");
        queue(this::closeAnyDialog);
        queue(() -> {
            clearRegion();
            patch.clear();
            //running: coal in the hopper and room for the output
            patch.add(supply(placeAt(Blocks.graphitePress, rx() + 2, ry() + 2), Items.coal, 30));
            //starved of coal
            patch.add(placeAt(Blocks.graphitePress, rx() + 7, ry() + 2));
            //output buffer full, so the block refuses to start another cycle; coal is present, so the
            //blocked output is the only thing wrong with it
            patch.add(supply(placeAt(Blocks.graphitePress, rx() + 12, ry() + 2), Items.graphite, 10));
            supply(patch.peek(), Items.coal, 30);
            //switched off by hand
            patch.add(supply(placeAt(Blocks.graphitePress, rx() + 2, ry() + 7), Items.coal, 30));
            patch.peek().enabled = false;
            //no sand, no coal and no power grid: one building, three separate findings
            patch.add(placeAt(Blocks.siliconSmelter, rx() + 7, ry() + 7));
            //no production model at all
            patch.add(placeAt(Blocks.titaniumWall, rx() + 12, ry() + 7));
        });
        queue(() -> check("the mixed patch was built", standing(patch) == 6, "standing " + standing(patch)));
        queue(this::armPicker);
        queue(() -> dragTiles(x1, y1, x2, y2));
        queue(() -> {
            AreaDiagnosticResult report = FactoryScopeUI.areaReport();
            check("the mixed area opened a report", report != null);
            if(report == null) return;

            check("six buildings were analysed", report.summary.analyzed == 6,
                "analysed " + report.summary.analyzed + " of " + report.summary.selected);
            check("five of them have a production model", report.summary.production == 5,
                "production " + report.summary.production);
            check("one is running", report.summary.operating == 1, "operating " + report.summary.operating);
            check("three are in a problem state", report.summary.problems == 3,
                "problems " + report.summary.problems);
            check("the disabled block and the wall are informational, not faults",
                report.summary.informational == 2, "informational " + report.summary.informational);
            check("every analysed building is in exactly one status bucket",
                report.summary.operating + report.summary.problems + report.summary.informational
                    == report.summary.analyzed);
            Integer limited = report.summary.byStatus.get(AreaStatus.limitedDiagnostics);
            check("the wall is reported as limited diagnostics", limited != null && limited == 1,
                String.valueOf(limited));

            //the smelter is short of sand, short of coal and unpowered: it must appear in three groups
            //and still be counted as one building
            int appearances = 0;
            for(AreaIssueGroup group : report.issues){
                for(BuildingRef ref : group.buildings){
                    if(ref.blockId.equals(Blocks.siliconSmelter.name)) appearances++;
                }
                check("no building is listed twice inside " + group.issue.key(),
                    new java.util.HashSet<>(group.buildings).size() == group.buildings.size());
            }
            check("the building with three findings appears in three groups", appearances == 3,
                "appeared in " + appearances + " groups");

            //coal is missing from both the empty press and the smelter, so it is the biggest group
            AreaIssueGroup first = report.issues.isEmpty() ? null : report.issues.get(0);
            check("the issue affecting the most buildings is listed first",
                first != null && first.buildingCount() == 2, first == null ? "no issues" : first.toString());
            for(int i = 1; i < report.issues.size(); i++){
                check("issue " + i + " is not ranked above the one before it",
                    report.issues.get(i).buildingCount() <= report.issues.get(i - 1).buildingCount());
            }
        });
        queue(() -> capture("mixed-area"));
    }

    void itemNetworkView(){
        int x1 = rx(), y1 = ry(), x2 = rx() + 14, y2 = ry() + 4;

        scenario("the Network view opens through the production area report");
        queue(this::closeAnyDialog);
        queue(() -> {
            clearRegion();
            renderer.targetscale = renderer.camerascale = 3f;
            for(int x = 1; x <= 8; x++) placeAt(Blocks.conveyor, rx() + x, ry() + 2);
            Building sorter = placeAt(Blocks.sorter, rx() + 9, ry() + 2);
            sorter.configure(Items.copper);
            placeAt(Blocks.conveyor, rx() + 10, ry() + 2);
        });
        queue(this::armPicker);
        queue(() -> dragTiles(x1, y1, x2, y2));
        queue(() -> check("the network patch opened an area report", FactoryScopeUI.areaReport() != null));
        queue(() -> clickNamed("factoryscope-area-network"));
        queue(() -> {
            check("the Network view opened", Core.scene.find("factoryscope-network-dialog") != null);
            check("the configured item is offered as a filter", Core.scene.find("factoryscope-network-resource-copper") != null);
        });
        queue(() -> clickNamed("factoryscope-network-resource-copper"));
        queue(() -> check("the Network view remains open after selecting an item", Core.scene.find("factoryscope-network-dialog") != null));
        queue(() -> clickNamed("factoryscope-network-building"));
        queue(() -> check("a network building opens topology detail", Core.scene.find("factoryscope-network-dialog") != null));
        queue(() -> capture("network-view"));
        queue(() -> clickNamed("factoryscope-network-view-world"));
        queue(() -> delayNextAction(30f));
        queue(() -> {
            check("the static routes can be viewed over the world", Core.scene.find("factoryscope-network-viewing") != null);
            check("the Network view keeps its return control", Core.scene.find("factoryscope-network-return") != null);
        });
        queue(() -> capture("network-world"));
        queue(() -> clickNamed("factoryscope-network-return"));
        queue(() -> delayNextAction(30f));
        queue(() -> check("return restores the Network view", Core.scene.find("factoryscope-network-dialog") != null));
        queue(() -> clickNamed("factoryscope-network-view-world"));
        queue(() -> delayNextAction(30f));
        queue(() -> clickNamed("factoryscope-network-dismiss"));
        queue(() -> delayNextAction(30f));
        queue(() -> {
            check("dismissing the world overlay removes its return control", Core.scene.find("factoryscope-network-viewing") == null);
            check("dismissing the world overlay drops the held report", !FactoryScopeUI.areaReportHeld());
        });
        queue(() -> renderer.targetscale = renderer.camerascale = 1.5f);
    }

    private void payloadNetworkView(){
        int x = rx() + 2, y = ry() + 4;
        scenario("Network filters a whole-block payload route independently from item routes");
        queue(this::closeAnyDialog);
        queue(this::closeAnyDialog);
        queue(this::ensurePickerOff);
        queue(() -> {
            clearRegion();
            renderer.targetscale = renderer.camerascale = 1.5f;
            Building source = placeAt(Blocks.payloadSource, x, y);
            ((PayloadSource.PayloadSourceBuild)source).configBlock = Blocks.copperWall;
            Building conveyor = placeAt(Blocks.payloadConveyor, adjacentX(source, NetworkSide.east, Blocks.payloadConveyor.size), y, 0);
            Building router = placeAt(Blocks.payloadRouter, adjacentX(conveyor, NetworkSide.east, Blocks.payloadRouter.size), y, 0);
            ((PayloadRouter.PayloadRouterBuild)router).sorted = Blocks.copperWall;
            placeAt(Blocks.payloadConveyor, adjacentX(router, NetworkSide.east, Blocks.payloadConveyor.size), y, 0);
        });
        queue(this::armPicker);
        queue(() -> dragTiles(x - 1, y - 2, x + 17, y + 3));
        queue(() -> clickNamed("factoryscope-area-network"));
        queue(() -> {
            check("Network lists the identified block payload", Core.scene.find("factoryscope-network-resource-copper-wall") != null);
            check("Network retains the selected area report", FactoryScopeUI.areaReport() != null
                && FactoryScopeUI.areaReport().network != null);
        });
        queue(() -> clickNamed("factoryscope-network-resource-copper-wall"));
        queue(() -> check("the payload filter keeps Network open", Core.scene.find("factoryscope-network-dialog") != null));
        queue(() -> clickNamed("factoryscope-network-view-world"));
        queue(() -> delayNextAction(30f));
        queue(() -> check("the block payload routes can be viewed over the world", Core.scene.find("factoryscope-network-viewing") != null));
        queue(() -> capture("payload-network"));
        queue(() -> clickNamed("factoryscope-network-return"));
        queue(() -> delayNextAction(30f));
        queue(this::closeAnyDialog);
        queue(this::closeAnyDialog);
    }

    private void unitCargoTrace(){
        int x = rx() + 2, y = ry() + 5;
        scenario("Supply Trace follows an item through a configured Unit Cargo route");
        queue(this::closeAnyDialog);
        queue(this::closeAnyDialog);
        queue(this::ensurePickerOff);
        queue(() -> {
            clearRegion();
            for(int floorX = x; floorX <= x + 1; floorX++) for(int floorY = y; floorY <= y + 1; floorY++){
                Tile tile = world.tile(floorX, floorY);
                tile.setFloor((Floor)Blocks.sand);
                tile.clearOverlay();
            }
            producer = placeAt(Blocks.mechanicalDrill, x, y);
            Building loader = placeAt(Blocks.unitCargoLoader,
                adjacentX(producer, NetworkSide.east, Blocks.unitCargoLoader.size), y, 0);
            Building point = placeAt(Blocks.unitCargoUnloadPoint,
                adjacentX(loader, NetworkSide.east, Blocks.unitCargoUnloadPoint.size), y, 0);
            ((UnitCargoUnloadPoint.UnitCargoUnloadPointBuild)point).item = Items.sand;
            target = placeAt(Blocks.siliconSmelter,
                adjacentX(point, NetworkSide.east, Blocks.siliconSmelter.size), y, 0);
        });
        queue(this::clickToggleButton);
        queue(() -> clickBuilding(target));
        queue(() -> check("the silicon smelter offers a Sand trace", Core.scene.find("factoryscope-trace-input-sand") != null));
        queue(() -> clickNamed("factoryscope-trace-input-sand"));
        queue(() -> check("the trace picker opens for the Unit Cargo factory", FactoryScopeUI.picking()));
        queue(() -> dragTiles(x - 1, y - 3, x + 15, y + 3));
        queue(() -> {
            AreaDiagnosticResult report = FactoryScopeUI.areaReport();
            ResourceRef sand = new ResourceRef(ResourceKind.item, "sand", "Sand");
            SupplyTrace trace = report == null ? null : TraceAnalyzer.input(report, AreaProbe.refOf(target), sand);
            TraceEndpoint endpoint = trace == null ? null : trace.producers().stream()
                .filter(candidate -> candidate.building.equals(AreaProbe.refOf(producer))).findFirst().orElse(null);
            boolean crossesCargo = endpoint != null && endpoint.path.ports().stream().anyMatch(port -> port.building.blockId.equals(Blocks.unitCargoLoader.name))
                && endpoint.path.ports().stream().anyMatch(port -> port.building.blockId.equals(Blocks.unitCargoUnloadPoint.name));
            check("the Unit Cargo trace reaches the real Sand drill", trace != null && trace.complete && endpoint != null,
                trace == null ? "no trace" : "complete=" + trace.complete + " producers=" + trace.producers().size());
            check("the representative path includes the loader and configured unload point", crossesCargo,
                endpoint == null ? "no producer path" : endpoint.path.ports().toString());
            check("the Supply Trace dialog remains open", Core.scene.find("factoryscope-trace-back") != null);
        });
        queue(() -> capture("unit-cargo-trace"));
        queue(this::closeAnyDialog);
        queue(this::closeAnyDialog);
    }

    private void reconstructorPayloadTrace(){
        int x = rx() + 2, y = ry() + 6;
        scenario("Supply Trace follows a unit payload to a reconstructor that accepts it");
        queue(this::closeAnyDialog);
        queue(this::ensurePickerOff);
        queue(() -> {
            clearRegion();
            producer = placeAt(Blocks.payloadSource, x, y);
            ((PayloadSource.PayloadSourceBuild)producer).unit = UnitTypes.dagger;
            Building conveyor = placeAt(Blocks.payloadConveyor,
                adjacentX(producer, NetworkSide.east, Blocks.payloadConveyor.size), y, 0);
            target = placeAt(Blocks.additiveReconstructor,
                adjacentX(conveyor, NetworkSide.east, Blocks.additiveReconstructor.size), y, 0);
        });
        queue(this::clickToggleButton);
        queue(() -> clickBuilding(target));
        queue(() -> check("the Reconstructor lists Dagger as an accepted payload input",
            Core.scene.find("factoryscope-trace-payload-input-dagger") != null));
        queue(() -> clickNamed("factoryscope-trace-payload-input-dagger"));
        queue(() -> check("the unit payload trace picker opens", FactoryScopeUI.picking()));
        queue(() -> dragTiles(x - 2, y - 3, x + 9, y + 3));
        queue(() -> {
            AreaDiagnosticResult report = FactoryScopeUI.areaReport();
            ResourceRef dagger = new ResourceRef(ResourceKind.unit, UnitTypes.dagger.name, UnitTypes.dagger.localizedName);
            SupplyTrace trace = report == null ? null : TraceAnalyzer.input(report, AreaProbe.refOf(target), dagger);
            TraceEndpoint endpoint = trace == null ? null : trace.producers().stream()
                .filter(candidate -> candidate.building.equals(AreaProbe.refOf(producer))).findFirst().orElse(null);
            boolean hasConveyor = endpoint != null && endpoint.path.ports().stream()
                .anyMatch(port -> port.building.blockId.equals(Blocks.payloadConveyor.name));
            check("the unit payload trace recognizes the Reconstructor input", trace != null && trace.targetUsesResource);
            check("the trace reaches the configured Dagger payload source", trace != null && endpoint != null,
                trace == null ? "no trace" : "complete=" + trace.complete + " producers=" + trace.producers().size());
            check("the representative route includes the payload conveyor", hasConveyor,
                endpoint == null ? "no producer path" : endpoint.path.ports().toString());
            check("the trace dialog remains open", Core.scene.find("factoryscope-trace-back") != null);
        });
        queue(() -> capture("reconstructor-payload-trace"));
        queue(this::closeAnyDialog);
    }

    private static int adjacentX(Building source, NetworkSide side, int targetSize){
        int offset = -(source.block.size - 1) / 2;
        int targetOffset = -(targetSize - 1) / 2;
        if(side == NetworkSide.east) return source.tile.x + offset + source.block.size - targetOffset;
        if(side == NetworkSide.west) return source.tile.x + offset - 1 - targetOffset;
        return source.tile.x;
    }



    private void scenario(String name){ harness.scenario(name); }
    private void queue(Runnable action){ harness.queue(action); }
    private void check(String what, boolean ok){ harness.check(what, ok); }
    private void check(String what, boolean ok, String detail){ harness.check(what, ok, detail); }
    private void closeAnyDialog(){ harness.closeAnyDialog(); }
    private void clearRegion(){ harness.clearRegion(); }
    private int rx(){ return harness.rx(); }
    private int ry(){ return harness.ry(); }
    private void armPicker(){ harness.armPicker(); }
    private void clickToggleButton(){ harness.clickToggleButton(); }
    private void clickBuilding(Building building){ harness.clickBuilding(building); }
    private void clickNamed(String name){ harness.clickNamed(name); }
    private void clickNamed(String name, int occurrence){ harness.clickNamed(name, occurrence); }
    private void dragTiles(int x1, int y1, int x2, int y2){ harness.dragTiles(x1, y1, x2, y2); }
    private void capture(String name){ harness.capture(name); }
    private Building placeAt(Block block, int x, int y){ return harness.placeAt(block, x, y); }
    private Building placeAt(Block block, int x, int y, int rotation){ return harness.placeAt(block, x, y, rotation); }
    private Building supply(Building building, Item item, int amount){ return harness.supply(building, item, amount); }
    private boolean dialogHasNamed(String name){ return harness.dialogHasNamed(name); }
    private ScrollPane findPane(Group group){ return harness.findPane(group); }
    private void delayNextAction(float ticks){ harness.delayNextAction(ticks); }
    private void restoreCamera(){ harness.restoreCamera(); }
    private void closeDialogStack(){ harness.closeDialogStack(); }
    private boolean dialogShows(String text){ return harness.dialogShows(text); }
    private void checkFits(String label){ harness.checkFits(label); }
    private void restoreLayout(){ harness.restoreLayout(); }
    private void ensurePickerOff(){ harness.ensurePickerOff(); }
    private void clickNamedInDialog(String name){ harness.clickNamedInDialog(name); }
    private void clickNamedInDialog(String name, int occurrence){ harness.clickNamedInDialog(name, occurrence); }
    private void scrollReportToBottom(){ harness.scrollReportToBottom(); }
    private int standing(Seq<Building> buildings){ return harness.standing(buildings); }
}
