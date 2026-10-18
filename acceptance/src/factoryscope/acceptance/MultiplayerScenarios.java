package factoryscope.acceptance;

import arc.*;
import arc.struct.*;
import arc.util.*;
import factoryscope.*;
import factoryscope.area.*;
import factoryscope.model.*;
import factoryscope.mpshared.*;
import factoryscope.power.*;
import factoryscope.probe.*;
import factoryscope.liquid.*;
import factoryscope.trace.*;
import factoryscope.ui.*;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;

import static mindustry.Vars.*;

/** Dedicated-server parity scenarios, including team and fog isolation. */
final class MultiplayerScenarios{
    private final AcceptanceHarness harness;

    MultiplayerScenarios(AcceptanceHarness harness){
        this.harness = harness;
    }

    /** Digests computed by the authoritative dedicated server, keyed by fixture name. */
    private java.util.Map<String, String> serverTruth;
    private boolean planned;

    /**
     * Joins the dedicated server the launcher started, instead of loading a local map. Everything the client
     * inspects there was built by the server and reached the client only through Mindustry's own sync.
     */
    void start(String address){
        checkOnlyHarnessModsLoaded();
        int split = address.lastIndexOf(':');
        String host = address.substring(0, split);
        int port = Integer.parseInt(address.substring(split + 1));
        Core.settings.put("name", "fs-acceptance");
        player.name("fs-acceptance");
        netClient.addPacketHandler(MultiplayerDigest.TRUTH, content -> serverTruth = MultiplayerDigest.parse(content));
        Events.on(WorldLoadEvent.class, event -> {
            if(net.client() && !planned){
                planned = true;
                //let the core spawn the player's unit and the first block snapshots arrive
                Time.runTask(300f, this::multiplayerPlan);
            }
        });
        Time.runTask(60f * 90f, () -> {
            if(!planned){
                check("the client joined the dedicated server", false, "no world received after 90 s");
                finish();
            }
        });
        Log.info(AcceptanceHarness.TAG + " joining dedicated server @:@", host, port);
        ui.join.connect(host, port);
    }

    void multiplayerPlan(){
        scenario("the client plays on a dedicated server");
        queue(() -> {
            check("the client is connected to a remote server", net.client() && !net.server(),
                "client=" + net.client() + ", server=" + net.server());
            check("the player is on the fixture's team", player.team() == Team.sharded, String.valueOf(player.team()));
            check("the server's rules enable fog of war", state.rules.fog);
            Seq<String> missing = new Seq<>();
            for(String name : MultiplayerDigest.FIXTURES.keySet()) if(fixture(name) == null) missing.add(name);
            check("every fixture the server built reached the client", missing.isEmpty(), missing.toString(", "));
            Call.serverPacketReliable(MultiplayerDigest.TRUTH, "");
            delayNextAction(120f);
        });
        queue(() -> check("the server answered with its own diagnoses",
            serverTruth != null && serverTruth.containsKey(MultiplayerDigest.AREA_KEY)));

        for(String name : MultiplayerDigest.OWN) multiplayerPanel(name);
        multiplayerPanel(MultiplayerDigest.VISIBLE_ENEMY);
        multiplayerHiddenEnemy();
        multiplayerArea();
        multiplayerTrace();
        multiplayerPower();
        multiplayerLiquid();
        multiplayerInspectFromReport();
        multiplayerLocate();

        harness.context.actions.add(harness::finish);
        harness.pump();
    }

    Building fixture(String name){
        int[] position = MultiplayerDigest.FIXTURES.get(name);
        return world.build(position[0], position[1]);
    }

    /** Opens a fixture through the real click path and compares the client's diagnosis with the server's. */
    void multiplayerPanel(String name){
        scenario("multiplayer building diagnostics: " + name);
        closeDialogStack();
        queue(this::armPicker);
        queue(() -> {
            Building build = fixture(name);
            check(name + " is on screen", build != null && onScreen(build.tileX(), build.tileY()));
            if(build != null) clickBuilding(build);
        });
        queue(() -> {
            Building build = fixture(name);
            check(name + ": the panel opened through the real click", build != null && FactoryScopeUI.inspected() == build);
            String client = build == null ? "missing" : MultiplayerDigest.building(build);
            String server = serverTruth == null ? null : serverTruth.get(name);
            check(name + ": the client reaches the server's diagnosis", client.equals(server),
                "client=" + client + " server=" + server);
            capture("multiplayer-" + name);
        });
        closeDialogStack();
    }

    void multiplayerHiddenEnemy(){
        scenario("multiplayer fog: a hidden enemy building stays out of reach");
        closeDialogStack();
        queue(() -> {
            Building hidden = fixture(MultiplayerDigest.HIDDEN_ENEMY);
            check("the client holds the hidden enemy building, as every vanilla client does",
                hidden != null && hidden.team == Team.crux);
            check("its synced contents reached the client as well",
                hidden != null && hidden.items != null && hidden.items.total() > 0,
                hidden == null || hidden.items == null ? "none" : String.valueOf(hidden.items.total()));
            check("Mindustry reports it hidden from the player's team", hidden != null && hidden.inFogTo(player.team()));
            check("FactoryScope refuses to inspect it", hidden != null && !MindustryFactoryProbe.canInspect(hidden, player.team()));
            boolean opened = hidden != null && FactoryScopeUI.inspect(hidden);
            check("an inspect request for it opens nothing", !opened && FactoryScopeUI.inspected() == null);
        });
    }

    void multiplayerArea(){
        scenario("multiplayer Area Diagnostics stays on the player's team and matches the server");
        closeDialogStack();
        queue(this::armPicker);
        queue(() -> dragArea(MultiplayerDigest.AREA));
        queue(() -> {
            AreaDiagnosticResult report = FactoryScopeUI.areaReport();
            check("the area report opened", report != null);
            if(report == null) return;
            int team = player.team().id;
            check("every building in the report belongs to the player's team",
                report.entries.stream().allMatch(entry -> entry.ref.teamId == team), membersOf(report));
            check("the visible enemy building inside the rectangle is not in the report",
                MultiplayerDigest.refAt(report, MultiplayerDigest.VISIBLE_ENEMY) == null);
            check("every power grid member belongs to the player's team", report.power != null
                && report.power.grids.stream().allMatch(grid -> grid.snapshot.members.stream().allMatch(member -> member.ref.teamId == team)));
            String client = MultiplayerDigest.area(report);
            String server = serverTruth == null ? null : serverTruth.get(MultiplayerDigest.AREA_KEY);
            check("the client's area report, network, traces and grids match the server's", client.equals(server),
                "client=" + client + " server=" + server);
            capture("multiplayer-area");
        });
    }

    void multiplayerTrace(){
        scenario("multiplayer Supply Trace from the starved smelter");
        closeDialogStack();
        queue(this::armPicker);
        queue(() -> clickBuilding(fixture(MultiplayerDigest.SMELTER_STARVED)));
        queue(() -> check("missing sand offers Supply Trace", Core.scene.find("factoryscope-trace-input-sand") != null));
        queue(() -> clickNamed("factoryscope-trace-input-sand"));
        queue(() -> dragArea(MultiplayerDigest.TRACE_AREA));
        queue(() -> {
            check("the Supply Trace opened", Core.scene.find("factoryscope-trace-back") != null);
            AreaDiagnosticResult report = FactoryScopeUI.areaReport();
            BuildingRef starved = report == null ? null : MultiplayerDigest.refAt(report, MultiplayerDigest.SMELTER_STARVED);
            SupplyTrace trace = starved == null ? null
                : TraceAnalyzer.input(report, starved, new ResourceRef(ResourceKind.item, "sand", "sand"));
            String endpoints = trace == null ? "no trace" : MultiplayerDigest.endpoints(trace);
            check("the trace reaches the unpowered laser drill as the structural producer",
                endpoints.contains("producer@19,25:laser-drill"), endpoints);
            capture("multiplayer-trace");
        });
    }

    void multiplayerPower(){
        scenario("multiplayer PowerScope reads the client's engine graph");
        closeDialogStack();
        queue(this::armPicker);
        queue(() -> clickBuilding(fixture(MultiplayerDigest.KILN)));
        queue(() -> check("the kiln panel opened", FactoryScopeUI.inspected() == fixture(MultiplayerDigest.KILN)));
        queue(() -> clickNamed("factoryscope-inspect-power-grid"));
        queue(() -> {
            PowerGridReport report = FactoryScopeUI.powerReport();
            check("PowerScope opened for the kiln's grid", report != null && !report.grids.isEmpty());
            if(report == null || report.grids.isEmpty()) return;
            PowerGridSnapshot grid = report.grids.get(0).snapshot;
            Seq<String> members = new Seq<>();
            for(PowerMemberSnapshot member : grid.members) members.add(member.ref.blockId);
            members.sort();
            check("the grid holds exactly the kiln and its unfuelled generator",
                members.toString(",").equals("combustion-generator,kiln"), members.toString(","));
            check("the grid delivers nothing, as on the server", grid.satisfaction < 0.01f, String.valueOf(grid.satisfaction));
            capture("multiplayer-power");
        });
    }

    void multiplayerLiquid(){
        scenario("multiplayer LiquidScope from the mixer");
        closeDialogStack();
        queue(this::armPicker);
        queue(() -> clickBuilding(fixture(MultiplayerDigest.MIXER)));
        queue(() -> check("missing water offers LiquidScope", Core.scene.find("factoryscope-trace-liquid-water") != null));
        queue(() -> clickNamed("factoryscope-trace-liquid-water"));
        queue(() -> dragArea(MultiplayerDigest.LIQUID_AREA));
        queue(() -> {
            LiquidTrace trace = FactoryScopeUI.liquidTrace();
            check("LiquidScope opened", Core.scene.find("factoryscope-liquid-dialog") != null && trace != null);
            String endpoints = trace == null ? "no trace" : MultiplayerDigest.liquidEndpoints(trace);
            check("the water trace reaches the unpowered rotary pump as the structural producer",
                endpoints.contains("producer@18,36:rotary-pump"), endpoints);
            capture("multiplayer-liquid");
        });
    }

    void multiplayerInspectFromReport(){
        scenario("multiplayer Inspect from an area report");
        closeDialogStack();
        queue(this::armPicker);
        queue(() -> dragArea(MultiplayerDigest.AREA));
        queue(() -> clickNamed("factoryscope-area-issue"));
        queue(() -> clickNamed("factoryscope-area-building"));
        queue(() -> {
            Building build = FactoryScopeUI.inspected();
            check("Inspect opens a building of the player's team", build != null && build.team == player.team(),
                build == null ? "nothing" : build.block.name + " " + build.team);
        });
        closeDialogStack();
    }

    void multiplayerLocate(){
        scenario("multiplayer Locate and Return from an area report");
        closeDialogStack();
        queue(this::armPicker);
        queue(() -> dragArea(MultiplayerDigest.AREA));
        queue(() -> clickNamed("factoryscope-area-issue"));
        queue(() -> clickNamed("factoryscope-area-locate"));
        queue(() -> check("Locate moves the view to the building", FactoryScopeUI.locating()));
        queue(() -> clickNamed("factoryscope-locate-return"));
        queue(() -> check("Return brings the report back", FactoryScopeUI.areaReport() != null && !FactoryScopeUI.locating()));
        closeDialogStack();
    }



    private void scenario(String name){ harness.scenario(name); }
    private void queue(Runnable action){ harness.queue(action); }
    private void check(String what, boolean ok){ harness.check(what, ok); }
    private void check(String what, boolean ok, String detail){ harness.check(what, ok, detail); }
    private void capture(String name){ harness.capture(name); }
    private void armPicker(){ harness.armPicker(); }
    private void dragTiles(int x1, int y1, int x2, int y2){ harness.dragTiles(x1, y1, x2, y2); }
    private void clickBuilding(Building building){ harness.clickBuilding(building); }
    private void clickNamed(String name){ harness.clickNamed(name); }
    private void closeAnyDialog(){ harness.closeAnyDialog(); }
    private void checkOnlyHarnessModsLoaded(){ harness.checkOnlyHarnessModsLoaded(); }
    private void finish(){ harness.finish(); }
    private void pump(){ harness.pump(); }
    private void delayNextAction(float ticks){ harness.delayNextAction(ticks); }
    private boolean onScreen(int x, int y){ return harness.onScreen(x, y); }
    private void closeDialogStack(){ for(int i = 0; i < 4; i++) queue(harness::closeAnyDialog); }
    private void dragArea(AreaSelection area){ harness.dragTiles(area.minX, area.minY, area.maxX, area.maxY); }
    private String membersOf(AreaDiagnosticResult report){ return harness.membersOf(report); }
}
