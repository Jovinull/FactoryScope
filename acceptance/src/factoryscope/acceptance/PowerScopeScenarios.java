package factoryscope.acceptance;

import arc.*;
import arc.math.*;
import arc.scene.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.util.*;
import factoryscope.*;
import factoryscope.analysis.*;
import factoryscope.area.*;
import factoryscope.power.*;
import factoryscope.probe.*;
import factoryscope.ui.*;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.world.*;

import java.util.Arrays;

import static mindustry.Vars.*;

/** PowerScope scenarios, including engine-grid lifecycle and measured-evidence boundaries. */
final class PowerScopeScenarios{
    private final AcceptanceHarness harness;
    private Building target;
    private PowerGridReport refreshedPowerSnapshot;

    PowerScopeScenarios(AcceptanceHarness harness){ this.harness = harness; }

    void queue(){ powerScopeScenarios(); }

    void checkBatterySupportedFixtureReady(Building[] grid, Building node,
                                           mindustry.world.blocks.power.PowerGraph graph,
                                           double setupTick){
        Building generator = grid[0], battery = grid[1], consumer = grid[2];
        boolean engineReady = generator != null && generator.getPowerProduction() > 0f
            && consumer != null && consumer.shouldConsumePower;
        long engineUpdates = Math.max(0L, Math.round(state.tick - setupTick));
        if(!engineReady && generator != null && consumer != null && engineUpdates < 120){
            harness.context.nextActionDelay = 1f;
            harness.context.actions.insert(0, () -> checkBatterySupportedFixtureReady(grid, node, graph, setupTick));
            return;
        }

        if(graph != null) graph.update();
        boolean sameGraph = node != null && graph != null && node.power != null && node.power.graph == graph
            && Arrays.stream(grid).allMatch(endpoint -> endpoint != null && endpoint.power != null && endpoint.power.graph == graph);
        boolean deficit = graph != null && graph.getPowerProduced() > 0f
            && graph.getPowerProduced() < graph.getPowerNeeded()
            && graph.getSatisfaction() >= 0.999f && battery != null && battery.power.status > 0f;
        Log.info(AcceptanceHarness.TAG + "   INFO battery-supported fixture engineUpdates=@ ready=@ produced=@ needed=@ satisfaction=@ balanceSamples=@",
            engineUpdates, engineReady, graph == null ? -1f : graph.getPowerProduced(),
            graph == null ? -1f : graph.getPowerNeeded(), graph == null ? -1f : graph.getSatisfaction(),
            graph != null && graph.hasPowerBalanceSamples());
        check("the real engine grid has a generation deficit covered by stored battery power",
            engineReady && sameGraph && deficit,
            "engineReady=" + engineReady + ", engineUpdates=" + engineUpdates
                + ", produced=" + (graph == null ? -1f : graph.getPowerProduced())
                + ", needed=" + (graph == null ? -1f : graph.getPowerNeeded())
                + ", satisfaction=" + (graph == null ? -1f : graph.getSatisfaction())
                + ", batteryStatus=" + (battery == null ? -1f : battery.power.status)
                + ", balanceSamples=" + (graph != null && graph.hasPowerBalanceSamples())
                + ", members=" + (graph == null ? -1 : graph.all.size)
                + ", sameGraph=" + sameGraph);
    }

    void powerScopeScenarios(){
        scenario("a single-building diagnostic opens its complete PowerGraph snapshot");
        queue(this::closeAnyDialog);
        queue(() -> {
            clearRegion();
            target = placeAt(Blocks.solarPanel, rx() + 5, ry() + 5);
        });
        queue(this::armPicker);
        queue(() -> clickBuilding(target));
        queue(() -> check("a power-capable building offers grid inspection",
            Core.scene.find("factoryscope-inspect-power-grid") != null));
        queue(() -> clickNamed("factoryscope-inspect-power-grid"));
        queue(() -> {
            PowerGridReport report = FactoryScopeUI.powerReport();
            check("single-building PowerScope opened", Core.scene.find("factoryscope-power-dialog") != null);
            check("PowerScope states that this building view reports the complete engine grid",
                dialogShows(FsBundle.get("power.scope-note")));
            check("the selected generator belongs to one engine grid", report != null && report.grids.size() == 1,
                report == null ? "no report" : "grids " + report.grids.size());
            check("the generator is discovered by PowerGraph membership",
                report != null && report.grids.get(0).snapshot.producers.stream()
                    .anyMatch(member -> member.ref.equals(AreaProbe.refOf(target))));
        });
        queue(() -> clickNamed("factoryscope-power-refresh"));
        queue(() -> check("PowerScope refresh keeps a valid snapshot",
            FactoryScopeUI.powerReport() != null && FactoryScopeUI.powerReport().grids.size() == 1));
        queue(() -> capture("power-single-building"));
        queue(() -> clickNamed("factoryscope-power-back"));
        queue(() -> check("single-building PowerScope Return restores its inspector",
            FactoryScopeUI.inspected() == target && FactoryScopeUI.powerReport() == null));
        scenario("PowerScope never renders cheat-rule placeholders as measured zeroes");
        queue(() -> {
            state.rules.teams.get(Team.sharded).cheat = true;
            Building consumer = placeAt(Blocks.siliconSmelter, target.tileX() + 1, target.tileY());
            if(consumer != null) consumer.updateConsumption();
        });
        queue(() -> clickNamed("factoryscope-inspect-power-grid"));
        queue(() -> {
            PowerGridReport report = FactoryScopeUI.powerReport();
            PowerGridResult result = report == null || report.grids.isEmpty() ? null : report.grids.get(0);
            check("team cheat power is marked and aggregate metrics are unavailable",
                result != null && result.state == PowerGridState.cheatPowered && !result.snapshot.hasMetrics);
            check("unavailable grid values are not rendered as generation or demand zeroes",
                dialogShows(FsBundle.get("power.metrics-unavailable"))
                    && !dialogShows(FsBundle.get("power.generation"))
                    && !dialogShows(FsBundle.get("power.demand")));
        });
        queue(() -> clickNamed("factoryscope-power-back"));
        queue(() -> state.rules.teams.get(Team.sharded).cheat = false);
        queue(this::closeAnyDialog);

        scenario("PowerScope separates a battery-supported generation deficit from an underpowered grid");
        int batteryX = rx(), batteryY = ry(), batteryX2 = batteryX + 14, batteryY2 = batteryY + 14;
        Building[] supportedGrid = new Building[6];
        Building[] supportedNode = new Building[1];
        mindustry.world.blocks.power.PowerGraph[] supportedGraph = new mindustry.world.blocks.power.PowerGraph[1];
        double[] powerSetupTick = new double[1];
        queue(() -> {
            clearRegion();
            supportedGrid[0] = placeAt(Blocks.rtgGenerator, batteryX + 3, batteryY + 7);
            supportedGrid[1] = placeAt(Blocks.batteryLarge, batteryX + 7, batteryY + 4);
            supportedGrid[2] = placeAt(Blocks.siliconSmelter, batteryX + 11, batteryY + 7);
            supportedGrid[3] = placeAt(Blocks.combustionGenerator, batteryX + 7, batteryY + 11);
            supportedGrid[4] = placeAt(Blocks.forceProjector, batteryX + 11, batteryY + 2);
            supportedGrid[5] = placeAt(Blocks.forceProjector, batteryX + 11, batteryY + 12);
            supportedNode[0] = placeAt(Blocks.powerNodeLarge, batteryX + 7, batteryY + 7);
            if(supportedGrid[2] != null){
                supportedGrid[2].items.add(Items.sand, 30);
                supportedGrid[2].items.add(Items.coal, 30);
                supportedGrid[2].updateConsumption();
            }
            if(supportedGrid[0] != null){
                supportedGrid[0].items.add(Items.thorium, 10);
                supportedGrid[0].updateConsumption();
            }
            if(supportedGrid[3] != null) supportedGrid[3].updateConsumption();
            if(supportedNode[0] != null){
                for(Building endpoint : supportedGrid){
                    if(endpoint != null) supportedNode[0].configureAny(endpoint.pos());
                }
            }
            if(supportedGrid[1] != null) supportedGrid[1].power.status = 1f;
            supportedGraph[0] = supportedGrid[0] == null ? null : supportedGrid[0].power.graph;
            powerSetupTick[0] = state.tick;
            harness.context.nextActionDelay = 1f;
        });
        queue(() -> checkBatterySupportedFixtureReady(supportedGrid, supportedNode[0], supportedGraph[0], powerSetupTick[0]));
        queue(this::armPicker);
        queue(() -> dragTiles(batteryX + 1, batteryY + 1, batteryX2, batteryY2));
        queue(() -> check("the battery-supported area exposes PowerScope",
            FactoryScopeUI.areaReport() != null && Core.scene.find("factoryscope-area-power") != null));
        queue(() -> {
            // Refill immediately before taking the snapshot so the UI assertion tests the
            // battery-covered state rather than how long the preceding real-client steps took.
            supportedGrid[1].power.status = 1f;
            supportedGraph[0].update();
            clickNamed("factoryscope-area-power");
        });
        queue(() -> {
            PowerGridReport report = FactoryScopeUI.powerReport();
            PowerGridResult result = report == null || report.grids.isEmpty() ? null : report.grids.get(0);
            String resultDetails = result == null ? "no PowerGridResult"
                : "state=" + result.state + ", generation=" + result.snapshot.generationPerSecond
                    + ", demand=" + result.snapshot.demandPerSecond + ", satisfaction=" + result.snapshot.satisfaction
                    + ", battery=" + result.snapshot.batteryStored + "/" + result.snapshot.batteryCapacity
                    + ", generatorProblems=" + result.generatorsWithProblems
                    + ", producers=" + result.snapshot.producers.stream().map(member -> member.ref + ":"
                        + (member.diagnostic == null ? "unclassified" : member.diagnostic.reason())).collect(java.util.stream.Collectors.joining(","));
            check("PowerScope distinguishes generation below demand while the grid is satisfied",
                result != null && result.state == PowerGridState.generationBelowDemand
                    && result.snapshot.generationPerSecond < result.snapshot.demandPerSecond
                    && result.snapshot.satisfaction >= 0.999f
                    && result.has(PowerFinding.BATTERY_RESERVES_PRESENT), resultDetails);
            check("the UI explicitly labels the battery-supported deficit",
                dialogShows(FsBundle.get("power.battery-reserves")));
            check("a fuel-starved connected generator retains its FactoryAnalyzer diagnostic",
                result != null && result.generatorsWithProblems == 1
                    && result.snapshot.producers.stream().anyMatch(member -> member.ref.equals(AreaProbe.refOf(supportedGrid[3]))
                        && member.diagnostic != null
                        && member.diagnostic.reason() == DiagnosticReason.missingItemInput), resultDetails);
            check("the grid summary reports a generator problem without assigning a cause",
                result != null && dialogShows(FsBundle.format("power.generator-problems", 1)), resultDetails);
            check("the generator member list is available for navigation",
                Core.scene.find("factoryscope-power-list-toggle") != null);
            capture("power-battery-supported-deficit");
        });
        queue(() -> clickNamed("factoryscope-power-list-toggle"));
        queue(() -> clickNamed("factoryscope-power-locate"));
        queue(() -> check("Locate opens the generator marker while retaining PowerScope state",
            FactoryScopeUI.locating() && Core.scene.find("factoryscope-locate-return") != null));
        queue(() -> clickNamed("factoryscope-locate-return"));
        queue(() -> check("Return restores the same battery-supported grid snapshot",
            FactoryScopeUI.powerReport() != null && FactoryScopeUI.powerReport().grids.size() == 1
                && FactoryScopeUI.powerReport().grids.get(0).state == PowerGridState.generationBelowDemand,
            FactoryScopeUI.powerReport() == null || FactoryScopeUI.powerReport().grids.isEmpty() ? "no report"
                : "state=" + FactoryScopeUI.powerReport().grids.get(0).state));
        queue(this::restoreCamera);
        queue(() -> {
            supportedGrid[1].power.status = 0f;
            supportedGraph[0].update();
        });
        queue(() -> clickNamed("factoryscope-power-refresh"));
        queue(() -> {
            PowerGridReport report = FactoryScopeUI.powerReport();
            PowerGridResult result = report == null || report.grids.isEmpty() ? null : report.grids.get(0);
            check("Refresh reports the now-underpowered grid after its battery is emptied",
                result != null && result.state == PowerGridState.underpowered
                    && result.snapshot.satisfaction < 0.999f
                    && !result.has(PowerFinding.BATTERY_RESERVES_PRESENT));
            check("an empty battery is not described as reserve support",
                result != null && !dialogShows(FsBundle.get("power.battery-reserves")));
            capture("power-underpowered-empty-battery");
        });
        queue(() -> clickNamed("factoryscope-power-back"));
        queue(this::closeAnyDialog);

        scenario("the Area Power view keeps multiple engine grids separate");
        int x1 = rx(), y1 = ry(), x2 = rx() + 40, y2 = ry() + 12;
        Building[] gridProducers = new Building[2];
        Building[] gridConnector = new Building[1];
        //The single-building Power dialog is stacked over its diagnostic panel. Let its close fade
        //finish before dismissing that panel, so the synthetic drag reaches the world picker.
        queue(() -> {});
        queue(() -> {});
        queue(() -> {
            clearRegion();
            gridProducers[0] = placeAt(Blocks.solarPanel, rx() + 2, ry() + 2);
            placeAt(Blocks.battery, rx() + 3, ry() + 2);
            gridProducers[1] = placeAt(Blocks.solarPanel, rx() + 10, ry() + 2);
        });
        queue(this::closeAnyDialog);
        queue(this::armPicker);
        queue(() -> dragTiles(x1, y1, x2, y2));
        queue(() -> check("the area selection opened diagnostics before PowerScope",
            FactoryScopeUI.areaReport() != null && Core.scene.find("factoryscope-area-power") != null));
        queue(() -> clickNamed("factoryscope-area-power"));
        queue(() -> {
            PowerGridReport report = FactoryScopeUI.powerReport();
            check("the area Power view opened", Core.scene.find("factoryscope-power-dialog") != null);
            check("two disconnected engine PowerGraphs remain two reports",
                report != null && report.grids.size() == 2,
                report == null ? "no report" : "grids " + report.grids.stream()
                    .map(grid -> grid.snapshot.members.stream().map(member -> member.ref.toString())
                        .collect(java.util.stream.Collectors.joining(", ")))
                    .collect(java.util.stream.Collectors.joining(" | ")));
            check("the battery remains distinct from generator membership",
                report != null && report.grids.stream().flatMap(grid -> grid.snapshot.batteries.stream())
                    .anyMatch(member -> member.ref.blockId.equals(Blocks.battery.name))
                    && report.grids.stream().flatMap(grid -> grid.snapshot.producers.stream())
                    .noneMatch(member -> member.ref.blockId.equals(Blocks.battery.name)));
            check("the selection scope is explicit for each whole-grid summary",
                report != null && report.grids.stream().allMatch(grid -> grid.snapshot.selectedMemberCount > 0));
        });
        queue(() -> capture("power-area-multiple-grids"));
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
            checkFits("PowerScope at 1280x720 @ 2.0x");
        });
        queue(() -> Core.scene.resize(Core.graphics.getWidth(), Core.graphics.getHeight()));
        queue(() -> capture("power-area-high-ui-scale"));
        queue(this::restoreLayout);
        queue(() -> check("PowerScope remains open after high-scale layout validation",
            FactoryScopeUI.powerReport() != null && Core.scene.find("factoryscope-power-dialog") != null));
        queue(() -> clickNamed("factoryscope-power-view-world"));
        queue(() -> check("PowerScope world view marks electrical connections without flow claims",
            Core.scene.find("factoryscope-power-viewing") != null
                && Core.scene.find("factoryscope-power-return") != null));
        queue(() -> clickNamed("factoryscope-power-return"));
        queue(() -> check("return restores the same PowerScope report",
            Core.scene.find("factoryscope-power-dialog") != null
                && FactoryScopeUI.powerReport() != null && FactoryScopeUI.powerReport().grids.size() == 2,
            "dialog=" + (Core.scene.getDialog() == null ? "none" : Core.scene.getDialog().getClass().getSimpleName())
                + ", report=" + (FactoryScopeUI.powerReport() == null ? "none" : FactoryScopeUI.powerReport().grids.size())
                + ", overlay=" + (Core.scene.find("factoryscope-power-viewing") != null)));
        queue(() -> {
            gridConnector[0] = placeAt(Blocks.powerNode, x1 + 6, y1 + 2);
            if(gridConnector[0] != null){
                gridConnector[0].configureAny(gridProducers[0].pos());
                gridConnector[0].configureAny(gridProducers[1].pos());
            }
            check("a newly placed PowerNode merges the two live engine grids",
                gridConnector[0] != null && gridProducers[0].power.graph == gridProducers[1].power.graph
                    && gridConnector[0].power.graph == gridProducers[0].power.graph);
        });
        queue(() -> clickNamed("factoryscope-power-refresh"));
        queue(() -> {
            refreshedPowerSnapshot = FactoryScopeUI.powerReport();
            check("Refresh replaces the stale two-grid snapshot with the merged engine grid",
                FactoryScopeUI.areaReportHeld() && refreshedPowerSnapshot != null
                    && refreshedPowerSnapshot.grids.size() == 1);
        });
        queue(() -> capture("power-area-refresh"));
        queue(() -> clickNamed("factoryscope-power-back"));
        queue(() -> check("PowerScope Return restores the held Area Diagnostics report",
            Core.scene.find("factoryscope-area-power") != null && FactoryScopeUI.areaReport() != null
                && FactoryScopeUI.areaReport().power == refreshedPowerSnapshot
                && FactoryScopeUI.powerReport() == null));
        queue(() -> {
            gridConnector[0].configureAny(gridProducers[0].pos());
            gridConnector[0].configureAny(gridProducers[1].pos());
            check("removing both established PowerNode links splits the live engine grid",
                gridProducers[0].power.graph != gridProducers[1].power.graph);
        });
        queue(() -> clickNamed("factoryscope-area-power"));
        queue(() -> clickNamed("factoryscope-power-refresh"));
        queue(() -> {
            PowerGridReport report = FactoryScopeUI.powerReport();
            long firstProducerGrids = report == null ? 0 : report.grids.stream()
                .filter(grid -> grid.snapshot.members.stream()
                    .anyMatch(member -> member.ref.equals(AreaProbe.refOf(gridProducers[0])))).count();
            long secondProducerGrids = report == null ? 0 : report.grids.stream()
                .filter(grid -> grid.snapshot.members.stream()
                    .anyMatch(member -> member.ref.equals(AreaProbe.refOf(gridProducers[1])))).count();
            long connectorGrids = report == null ? 0 : report.grids.stream()
                .filter(grid -> grid.snapshot.members.stream()
                    .anyMatch(member -> member.ref.equals(AreaProbe.refOf(gridConnector[0])))).count();
            check("Refresh replaces the merged snapshot with both source grids and the isolated PowerNode grid",
                report != null && report.grids.size() == 3 && firstProducerGrids == 1
                    && secondProducerGrids == 1 && connectorGrids == 1,
                "grids=" + (report == null ? "none" : report.grids.size())
                    + ", producer memberships=" + firstProducerGrids + "/" + secondProducerGrids
                    + ", connector memberships=" + connectorGrids);
        });
        queue(() -> clickNamed("factoryscope-power-back"));
        queue(this::closeAnyDialog);

        scenario("PowerScope uses the BeamNode connection maintained by Mindustry");
        int beamX = rx(), beamY = ry();
        Building[] beamFixture = new Building[2];
        queue(() -> {
            clearRegion();
            beamFixture[0] = placeAt(Blocks.solarPanel, beamX + 4, beamY + 5);
            beamFixture[1] = placeAt(Blocks.beamNode, beamX + 5, beamY + 5);
        });
        queue(this::armPicker);
        queue(() -> dragTiles(beamX + 2, beamY + 3, beamX + 7, beamY + 7));
        queue(() -> clickNamed("factoryscope-area-power"));
        queue(() -> {
            PowerGridReport report = FactoryScopeUI.powerReport();
            PowerConnection expected = new PowerConnection(AreaProbe.refOf(beamFixture[0]),
                AreaProbe.refOf(beamFixture[1]));
            check("the real BeamNode and solar panel share Mindustry's established engine grid",
                report != null && report.grids.size() == 1
                    && beamFixture[0].power.graph == beamFixture[1].power.graph
                    && report.grids.get(0).snapshot.connections.contains(expected));
        });
        queue(() -> clickNamed("factoryscope-power-view-world"));
        queue(() -> check("the BeamNode grid can be viewed through the production overlay",
            Core.scene.find("factoryscope-power-viewing") != null
                && Core.scene.find("factoryscope-power-return") != null));
        queue(() -> capture("power-beam-node-world"));
        queue(() -> clickNamed("factoryscope-power-return"));
        queue(() -> clickNamed("factoryscope-power-back"));
        queue(this::closeAnyDialog);

        scenario("PowerScope presents a selected Power Diode as a conditional link between separate grids");
        int diodeX = rx(), diodeY = ry();
        Building[] diodeFixture = new Building[3];
        queue(() -> {
            clearRegion();
            diodeFixture[0] = placeAt(Blocks.battery, diodeX + 4, diodeY + 5);
            diodeFixture[1] = placeAt(Blocks.diode, diodeX + 5, diodeY + 5);
            diodeFixture[2] = placeAt(Blocks.battery, diodeX + 6, diodeY + 5);
        });
        queue(this::armPicker);
        queue(() -> dragTiles(diodeX + 2, diodeY + 3, diodeX + 8, diodeY + 7));
        queue(() -> check("the area selection includes both diode endpoint grids and the diode",
            FactoryScopeUI.areaReport() != null && FactoryScopeUI.areaReport().power != null));
        queue(() -> clickNamed("factoryscope-area-power"));
        queue(() -> {
            PowerGridReport report = FactoryScopeUI.powerReport();
            boolean separate = report != null && report.grids.size() == 2;
            PowerDiodeLink link = report == null || report.diodeLinks.size() != 1
                ? null : report.diodeLinks.get(0);
            boolean direction = link != null && link.fromGrid != link.toGrid
                && report.grids.get(link.fromGrid).snapshot.members.stream()
                    .anyMatch(member -> member.ref.equals(AreaProbe.refOf(diodeFixture[0])))
                && report.grids.get(link.toGrid).snapshot.members.stream()
                    .anyMatch(member -> member.ref.equals(AreaProbe.refOf(diodeFixture[2])));
            check("the Power Diode remains a relation, not a merged PowerGraph",
                separate && diodeFixture[0].power.graph != diodeFixture[2].power.graph
                    && report.grids.stream().noneMatch(grid -> grid.snapshot.members.stream()
                        .anyMatch(member -> member.ref.equals(AreaProbe.refOf(diodeFixture[1])))));
            check("the displayed Power Diode relation follows Mindustry back-to-front direction", direction);
            check("the Power Diode UI explicitly withholds current activity and transfer amount",
                link != null && link.batteryState == PowerDiodeBatteryState.bothEndpointsHaveCapacity
                    && dialogShows(FsBundle.get("power.diodes"))
                    && dialogShows(FsBundle.format("power.diode-link", link.diode.blockName,
                        link.fromGrid + 1, link.toGrid + 1))
                    && dialogShows(FsBundle.get("power.diode-unmeasured")));
        });
        queue(() -> {
            ScrollPane pane = findPane(Core.scene.getDialog());
            if(pane != null){
                pane.setScrollPercentY(1f);
                pane.updateVisualScroll();
            }
        });
        queue(() -> capture("power-diode-cross-grid"));
        queue(() -> clickNamed("factoryscope-power-back"));
        queue(this::closeAnyDialog);

        scenario("world change clears the PowerScope snapshot and electrical overlay");
        int lifecycleX = rx(), lifecycleY = ry();
        queue(() -> {
            clearRegion();
            placeAt(Blocks.solarPanel, lifecycleX + 3, lifecycleY + 3);
        });
        queue(this::armPicker);
        queue(() -> dragTiles(lifecycleX + 1, lifecycleY + 1, lifecycleX + 5, lifecycleY + 5));
        queue(() -> check("the refreshed area report is available for the lifecycle check",
            FactoryScopeUI.areaReport() != null && Core.scene.find("factoryscope-area-power") != null));
        queue(() -> clickNamed("factoryscope-area-power"));
        queue(() -> clickNamed("factoryscope-power-view-world"));
        queue(() -> check("the electrical overlay is active before world change",
            Core.scene.find("factoryscope-power-viewing") != null));
        queue(() -> Events.fire(new WorldLoadEvent()));
        queue(() -> check("world change releases the grid report, area report and overlay",
            FactoryScopeUI.powerReport() == null && FactoryScopeUI.areaReport() == null
                && !FactoryScopeUI.areaReportHeld() && Core.scene.getDialog() == null
                && Core.scene.find("factoryscope-power-viewing") == null));
    }



    private void scenario(String name){ harness.scenario(name); }
    private void queue(Runnable action){ harness.queue(action); }
    private void check(String what, boolean ok){ harness.check(what, ok); }
    private void check(String what, boolean ok, String detail){ harness.check(what, ok, detail); }
    private void closeAnyDialog(){ harness.closeAnyDialog(); }
    private void clearRegion(){ harness.clearRegion(); }
    private Building placeAt(Block block, int x, int y){ return harness.placeAt(block, x, y); }
    private int rx(){ return harness.rx(); }
    private int ry(){ return harness.ry(); }
    private void armPicker(){ harness.armPicker(); }
    private void clickBuilding(Building building){ harness.clickBuilding(building); }
    private void clickNamed(String name){ harness.clickNamed(name); }
    private void dragTiles(int x1, int y1, int x2, int y2){ harness.dragTiles(x1, y1, x2, y2); }
    private void capture(String name){ harness.capture(name); }
    private void restoreCamera(){ harness.restoreCamera(); }
    private void restoreLayout(){ harness.restoreLayout(); }
    private void checkFits(String label){ harness.checkFits(label); }
    private ScrollPane findPane(Group group){ return harness.findPane(group); }
    private boolean dialogShows(String text){ return harness.dialogShows(text); }
}
