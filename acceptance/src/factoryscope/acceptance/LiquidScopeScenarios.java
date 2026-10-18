package factoryscope.acceptance;

import arc.*;
import arc.scene.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.util.*;
import factoryscope.*;
import factoryscope.analysis.*;
import factoryscope.area.*;
import factoryscope.liquid.*;
import factoryscope.model.*;
import factoryscope.probe.*;
import factoryscope.trace.*;
import factoryscope.ui.*;
import mindustry.content.*;
import mindustry.gen.*;
import mindustry.game.EventType.*;
import mindustry.world.*;
import mindustry.world.blocks.environment.Floor;
import mindustry.world.consumers.ConsumeCoolant;

import static mindustry.Vars.*;

/** LiquidScope and Liquid Trace scenarios, including conservative snapshot navigation. */
final class LiquidScopeScenarios{
    private final AcceptanceHarness harness;
    private Building target, producer;
    private ConsumeCoolant acceptanceCoolant;
    private boolean acceptanceCoolantWasOptional;

    LiquidScopeScenarios(AcceptanceHarness harness){ this.harness = harness; }

    void queue(){ liquidScopeScenarios(); }

    void liquidScopeScenarios(){
        scenario("a missing Water input traces through the production UI to a real pump");
        queue(this::closeAnyDialog);
        queue(() -> {
            clearRegion();
            int x = rx(), y = ry();
            for(int dx = 2; dx <= 3; dx++) for(int dy = 2; dy <= 3; dy++){
                Tile floor = world.tile(x + dx, y + dy);
                floor.setFloor((Floor)Blocks.water);
                floor.clearOverlay();
            }
            producer = placeAt(Blocks.mechanicalPump, x + 3, y + 3);
            placeAt(Blocks.conduit, x + 4, y + 3, 0);
            placeAt(Blocks.conduit, x + 5, y + 3, 0);
            //Cryofluid Mixer is 2x2; its west footprint starts at x+6, beside the second conduit.
            target = placeAt(Blocks.cryofluidMixer, x + 6, y + 3);
        });
        queue(() -> {
            //Let the pump resolve its placement-specific liquidDrop, then preserve a real shortage.
            //Drain both line buffers too: stored contents never define static topology, but the
            //single-building button intentionally appears only while the diagnostic input is empty.
            producer.enabled = false;
            if(producer.liquids != null) producer.liquids.clear();
            for(int dx = 4; dx <= 5; dx++){
                Building conduit = world.tile(rx() + dx, ry() + 3).build;
                if(conduit != null && conduit.liquids != null) conduit.liquids.clear();
            }
            target.liquids.clear();
        });
        queue(this::armPicker);
        queue(() -> clickBuilding(target));
        queue(() -> check("the missing Water input offers a direct Liquid Trace action",
            Core.scene.find("factoryscope-trace-liquid-water") != null));
        queue(() -> clickNamed("factoryscope-trace-liquid-water"));
        queue(() -> check("single-building Liquid Trace asks for an explicit area",
            FactoryScopeUI.picking() && FactoryScopeUI.inspected() == null));
        queue(() -> dragTiles(rx() + 1, ry() + 1, rx() + 9, ry() + 8));
        queue(() -> {
            LiquidTrace trace = FactoryScopeUI.liquidTrace();
            check("the production LiquidScope dialog opens from the single-building path",
                Core.scene.find("factoryscope-liquid-dialog") != null && trace != null);
            check("the structural Water trace reaches the placed mechanical pump",
                trace != null && trace.producers().stream().anyMatch(endpoint -> endpoint.building.equals(AreaProbe.refOf(producer))),
                trace == null ? "no trace" : "complete=" + trace.complete + ", endpoints=" + trace.endpoints);
            check("the trace claims structure, not liquid flow",
                dialogShows(FsBundle.get("liquid.trace-structural-input")));
            capture("liquid-water-trace");
        });
        queue(() -> {
            Scl.setProduct(2f);
            Core.scene.resize(1280, 720);
        });
        queue(() -> checkFits("LiquidScope at 1280x720 @ 2x"));
        queue(() -> capture("liquid-water-trace-high-ui-scale"));
        queue(() -> {
            Element inspect = Core.scene.find("factoryscope-liquid-producer-inspect");
            boolean withinRow = inspect != null && inspect.parent != null
                && inspect.x + inspect.getWidth() <= inspect.parent.getWidth() + 0.5f;
            check("the liquid producer Inspect action stays compact at high UI scale",
                inspect instanceof ImageButton && inspect.getWidth() <= 34.5f && withinRow,
                inspect == null ? "missing action" : inspect.getClass().getSimpleName() + " x=" + inspect.x
                    + ", width=" + inspect.getWidth() + ", parent width="
                    + (inspect.parent == null ? "none" : inspect.parent.getWidth()));
        });
        queue(this::restoreLayout);
        queue(() -> clickNamed("factoryscope-liquid-view-world"));
        queue(() -> check("the liquid overlay identifies structural routes without flow claims",
            Core.scene.find("factoryscope-liquid-viewing") != null));
        queue(() -> clickNamed("factoryscope-liquid-return"));
        queue(() -> check("Return restores the same Liquid Trace context",
            Core.scene.find("factoryscope-liquid-dialog") != null && FactoryScopeUI.liquidTrace() != null
                && FactoryScopeUI.liquidTrace().target.equals(AreaProbe.refOf(target))
                && FactoryScopeUI.liquidTrace().liquid.equals(new ResourceRef(ResourceKind.liquid,
                    Liquids.water.name, Liquids.water.localizedName))
                && FactoryScopeUI.liquidTrace().direction == TraceDirection.input
                && FactoryScopeUI.liquidTrace().producers().stream()
                    .anyMatch(endpoint -> endpoint.building.equals(AreaProbe.refOf(producer)))));
        queue(() -> clickNamed("factoryscope-liquid-producer-inspect"));
        queue(() -> check("Inspect opens the actual pump while retaining the trace dialog",
            FactoryScopeUI.inspected() == producer,
            "expected " + describe(producer) + ", inspected " + describe(FactoryScopeUI.inspected())
                + ", trace dialog " + (Core.scene.find("factoryscope-liquid-dialog") != null)
                + ", trace " + (FactoryScopeUI.liquidTrace() == null ? "null" : FactoryScopeUI.liquidTrace().target)));
        queue(this::closeAnyDialog);
        queue(() -> check("closing pump diagnostics returns to the same Liquid Trace",
            Core.scene.find("factoryscope-liquid-dialog") != null && FactoryScopeUI.liquidTrace() != null));
        queue(() -> producer.enabled = true);
        queue(() -> clickNamed("factoryscope-liquid-refresh"));
        queue(() -> check("Refresh rebuilds the liquid trace snapshot coherently",
            FactoryScopeUI.liquidTrace() != null
                && FactoryScopeUI.liquidTrace().producers().stream().anyMatch(endpoint -> endpoint.building.equals(AreaProbe.refOf(producer)))));
        queue(() -> check("Refresh also rebuilds the producer diagnostic annotation",
            FactoryScopeUI.liquidTrace() != null && FactoryScopeUI.liquidTrace().producers().stream()
                .anyMatch(endpoint -> endpoint.building.equals(AreaProbe.refOf(producer))
                    && endpoint.diagnostic != null && endpoint.diagnostic.reason() != DiagnosticReason.disabled)));
        queue(() -> clickNamed("factoryscope-liquid-producer-inspect"));
        queue(() -> check("the pump inspector is open before a world change", FactoryScopeUI.inspected() == producer));
        queue(this::closeAnyDialog);
        queue(() -> check("Return restores Liquid Trace before a topology change",
            Core.scene.find("factoryscope-liquid-dialog") != null && FactoryScopeUI.liquidTrace() != null));
        queue(() -> {
            Building firstConduit = world.tile(rx() + 4, ry() + 3).build;
            if(firstConduit != null) firstConduit.rotation = 1;
        });
        queue(() -> clickNamed("factoryscope-liquid-refresh"));
        queue(() -> check("Refresh removes a producer after its structural Conduit route is rotated away",
            FactoryScopeUI.liquidTrace() != null && FactoryScopeUI.liquidTrace().producers().stream()
                .noneMatch(endpoint -> endpoint.building.equals(AreaProbe.refOf(producer)))));
        queue(() -> clickNamed("factoryscope-liquid-view-world"));
        queue(() -> check("the liquid world overlay is active before a world change",
            Core.scene.find("factoryscope-liquid-viewing") != null));
        queue(() -> Events.fire(new WorldLoadEvent()));
        queue(() -> check("world change clears the Liquid Trace snapshot and overlay",
            FactoryScopeUI.inspected() == null && FactoryScopeUI.liquidTrace() == null
                && Core.scene.find("factoryscope-liquid-dialog") == null
                && Core.scene.find("factoryscope-liquid-viewing") == null));
        queue(this::closeAnyDialog);

        scenario("filter consumers let the player choose an accepted liquid before tracing");
        queue(() -> {
            clearRegion();
            acceptanceCoolant = Blocks.foreshadow.findConsumer(consume -> consume instanceof ConsumeCoolant);
            check("vanilla Foreshadow exposes its coolant filter", acceptanceCoolant != null);
            if(acceptanceCoolant != null){
                acceptanceCoolantWasOptional = acceptanceCoolant.optional;
                // The acceptance-only condition makes the vanilla filter a required diagnostic input.
                // This changes only the probe-facing field for the test; it does not update the engine's
                // precomputed optional-consumer arrays or alter simulation behavior.
                acceptanceCoolant.optional = false;
            }
            target = placeAt(Blocks.foreshadow, rx() + 4, ry() + 4);
            FactorySnapshot snapshot = MindustryFactoryProbe.probe(target);
            boolean filterInput = snapshot.inputs.stream().anyMatch(input -> input.kind == ResourceKind.liquid
                && input.contentId == null && !input.optional
                && input.acceptedResources.stream().anyMatch(resource -> resource.id.equals(Liquids.cryofluid.name)));
            check("the vanilla filter snapshot exposes Cryofluid as a required accepted alternative", filterInput,
                "optional=" + (acceptanceCoolant == null ? "missing" : acceptanceCoolant.optional)
                    + ", inputs=" + snapshot.inputs);
        });
        queue(this::armPicker);
        queue(() -> clickBuilding(target));
        queue(() -> check("the filter target opens in the real inspector", FactoryScopeUI.inspected() == target,
            "target=" + MindustryFactoryProbe.describe(target) + ", inspected="
                + (FactoryScopeUI.inspected() == null ? "null" : MindustryFactoryProbe.describe(FactoryScopeUI.inspected()))));
        queue(() -> check("an empty filter consumer offers a liquid-choice action",
            Core.scene.find("factoryscope-trace-liquid-choice") != null));
        queue(() -> clickNamed("factoryscope-trace-liquid-choice"));
        queue(() -> check("the choice dialog lists the filter's accepted resources",
            Core.scene.find("factoryscope-liquid-filter-option-water") != null
                && Core.scene.find("factoryscope-liquid-filter-option-cryofluid") != null
                && Core.scene.find("factoryscope-liquid-filter-option-oil") == null));
        queue(() -> capture("liquid-filter-choice"));
        queue(() -> clickNamed("factoryscope-liquid-filter-option-cryofluid"));
        queue(() -> check("choosing Cryofluid requests an explicit area for that exact resource",
            FactoryScopeUI.picking() && Core.scene.find("factoryscope-liquid-dialog") == null));
        queue(() -> dragTiles(rx() + 2, ry() + 2, rx() + 6, ry() + 6));
        queue(() -> {
            LiquidTrace trace = FactoryScopeUI.liquidTrace();
            check("filter selection opens a Cryofluid trace without inventing a single filter product",
                trace != null && trace.liquid.equals(new ResourceRef(ResourceKind.liquid,
                    Liquids.cryofluid.name, Liquids.cryofluid.localizedName)) && trace.targetUsesLiquid
                    && trace.target.equals(AreaProbe.refOf(target)));
            if(acceptanceCoolant != null){
                acceptanceCoolant.optional = acceptanceCoolantWasOptional;
            }
        });
        queue(this::closeAnyDialog);

        scenario("an unsupported liquid transport is an incomplete, locatable trace row");
        queue(() -> {
            clearRegion();
            target = placeAt(Blocks.cryofluidMixer, rx() + 6, ry() + 4);
            //the sandbox Liquid Source is the vanilla liquid block without a transport model
            placeAt(Blocks.liquidSource, rx() + 5, ry() + 4);
        });
        queue(this::armPicker);
        queue(() -> dragTiles(rx() + 2, ry() + 2, rx() + 9, ry() + 7));
        queue(() -> clickNamed("factoryscope-area-liquids"));
        queue(() -> clickNamed("factoryscope-liquid-select-water"));
        queue(() -> clickNamed("factoryscope-liquid-trace-input"));
        queue(() -> check("the unsupported transport is shown as incomplete rather than a dead end",
            FactoryScopeUI.liquidTrace() != null && !FactoryScopeUI.liquidTrace().complete
                && !FactoryScopeUI.liquidTrace().noRouteProven
                && !FactoryScopeUI.liquidTrace().unsupportedInterruptions.isEmpty()
                && Core.scene.find("factoryscope-liquid-unsupported-locate") != null));
        queue(() -> capture("liquid-unsupported"));
        queue(() -> clickNamed("factoryscope-liquid-unsupported-locate"));
        queue(() -> check("Locate opens for the actual unsupported liquid transport", FactoryScopeUI.locating()));
        queue(() -> clickNamed("factoryscope-locate-return"));
        queue(() -> check("Return restores the same unsupported Liquid Trace",
            Core.scene.find("factoryscope-liquid-dialog") != null && FactoryScopeUI.liquidTrace() != null
                && FactoryScopeUI.liquidTrace().target.equals(AreaProbe.refOf(target))
                && !FactoryScopeUI.liquidTrace().unsupportedInterruptions.isEmpty()));
        queue(this::closeAnyDialog);

        scenario("a liquid trace marks a route that continues beyond the selected area");
        queue(() -> {
            clearRegion();
            int x = rx(), y = ry();
            // The west conduit is deliberately outside the selected area; the east conduit
            // and consumer remain inside it. No producer is needed to prove continuation.
            placeAt(Blocks.conduit, x + 4, y + 3, 0);
            placeAt(Blocks.conduit, x + 5, y + 3, 0);
            target = placeAt(Blocks.cryofluidMixer, x + 6, y + 3);
            world.tile(x + 4, y + 3).build.liquids.clear();
            world.tile(x + 5, y + 3).build.liquids.clear();
            target.liquids.clear();
        });
        queue(this::armPicker);
        queue(() -> dragTiles(rx() + 5, ry() + 1, rx() + 9, ry() + 8));
        queue(() -> clickNamed("factoryscope-area-liquids"));
        queue(() -> clickNamed("factoryscope-liquid-select-water"));
        queue(() -> clickNamed("factoryscope-liquid-trace-input"));
        queue(() -> {
            LiquidTrace trace = FactoryScopeUI.liquidTrace();
            check("the trace preserves an external continuation without claiming an in-area producer",
                trace != null && !trace.boundaryContinuations.isEmpty() && trace.producers().isEmpty()
                    && !trace.noRouteProven,
                trace == null ? "trace unavailable" : "boundaries=" + trace.boundaryContinuations.size()
                    + ", producers=" + trace.producers().size() + ", dead ends=" + trace.structuralDeadEnds.size()
                    + ", noRoute=" + trace.noRouteProven);
            check("the liquid dialog uses selected-area boundary wording",
                trace != null && dialogShows(trace.boundaryContinuations.size() == 1
                    ? FsBundle.get("liquid.trace-boundary-one")
                    : FsBundle.format("liquid.trace-boundary-many", trace.boundaryContinuations.size())));
            capture("liquid-boundary");
        });
        queue(this::closeAnyDialog);

        scenario("the Area Liquids view filters Water and traces its consumer through production UI");
        queue(() -> {
            clearRegion();
            int x = rx(), y = ry();
            for(int dx = 2; dx <= 3; dx++) for(int dy = 2; dy <= 3; dy++)
                world.tile(x + dx, y + dy).setFloor((Floor)Blocks.water);
            producer = placeAt(Blocks.mechanicalPump, x + 3, y + 3);
            placeAt(Blocks.conduit, x + 4, y + 3, 0);
            placeAt(Blocks.conduit, x + 5, y + 3, 0);
            target = placeAt(Blocks.cryofluidMixer, x + 6, y + 3);
        });
        queue(this::armPicker);
        queue(() -> dragTiles(rx() + 1, ry() + 1, rx() + 9, ry() + 8));
        queue(() -> clickNamed("factoryscope-area-liquids"));
        queue(() -> check("Area Diagnostics opens the LiquidScope resource view",
            Core.scene.find("factoryscope-liquid-dialog") != null
                && Core.scene.find("factoryscope-liquid-select-water") != null));
        queue(() -> clickNamed("factoryscope-liquid-select-water"));
        queue(() -> capture("liquid-area-network"));
        queue(() -> clickNamed("factoryscope-liquid-trace-input"));
        queue(() -> {
            LiquidTrace trace = FactoryScopeUI.liquidTrace();
            check("Area Liquids traces from the actual selected consumer row",
                trace != null && trace.target.equals(AreaProbe.refOf(target))
                    && trace.producers().stream().anyMatch(endpoint -> endpoint.building.equals(AreaProbe.refOf(producer))));
            check("the Area entry path does not bypass the player-facing Trace action",
                Core.scene.find("factoryscope-liquid-dialog") != null
                    && Core.scene.find("factoryscope-liquid-producer-inspect") != null);
            capture("liquid-area-trace");
        });
        queue(this::closeAnyDialog);
        queue(this::armPicker);
        queue(() -> dragTiles(rx() + 1, ry() + 1, rx() + 9, ry() + 8));
        queue(() -> clickNamed("factoryscope-area-liquids"));
        queue(() -> clickNamed("factoryscope-liquid-select-water"));
        queue(() -> clickNamed("factoryscope-liquid-trace-output"));
        queue(() -> {
            LiquidTrace trace = FactoryScopeUI.liquidTrace();
            check("Area Liquids exposes a structural output trace from the pump",
                trace != null && trace.direction == TraceDirection.output
                    && trace.target.equals(AreaProbe.refOf(producer))
                    && trace.endpoints.stream().anyMatch(endpoint -> endpoint.building.equals(AreaProbe.refOf(target))));
            check("output wording remains distinct from upstream supply wording",
                dialogShows(FsBundle.get("liquid.trace-structural-output")));
            capture("liquid-output-trace");
        });
        queue(this::closeAnyDialog);

        scenario("an Erekir gas can be traced through a Liquid Junction without flow claims");
        queue(() -> {
            clearRegion();
            int x = rx(), y = ry();
            producer = placeAt(Blocks.electrolyzer, x + 3, y + 4);
            // Mindustry v160.5's Electrolyzer gas outputs are directed south.
            placeAt(Blocks.conduit, x + 3, y + 2, 3);
            placeAt(Blocks.liquidJunction, x + 3, y + 1);
            placeAt(Blocks.conduit, x + 3, y, 3);
        });
        queue(this::armPicker);
        queue(() -> dragTiles(rx() + 1, ry() + 1, rx() + 10, ry() + 8));
        queue(() -> clickNamed("factoryscope-area-liquids"));
        queue(() -> check("the Erekir Electrolyzer exposes Hydrogen in the liquid/gas filter",
            Core.scene.find("factoryscope-liquid-select-hydrogen") != null));
        queue(() -> clickNamed("factoryscope-liquid-select-hydrogen"));
        queue(() -> clickNamed("factoryscope-liquid-trace-output"));
        queue(() -> {
            LiquidTrace trace = FactoryScopeUI.liquidTrace();
            check("the gas output trace preserves Hydrogen identity and structural direction",
                trace != null && trace.direction == TraceDirection.output
                    && trace.liquid.equals(new ResourceRef(ResourceKind.liquid,
                        Liquids.hydrogen.name, Liquids.hydrogen.localizedName))
                    && trace.target.equals(AreaProbe.refOf(producer))
                    && !trace.traversedEdges.isEmpty(),
                trace == null ? "trace unavailable" : "direction=" + trace.direction + ", liquid=" + trace.liquid
                    + ", target=" + trace.target + ", expected=" + AreaProbe.refOf(producer)
                    + ", traversed=" + trace.traversedEdges.size() + ", endpoints=" + trace.endpoints.size());
        });
        queue(() -> clickNamed("factoryscope-liquid-view-world"));
        queue(() -> check("the gas trace can display its static route overlay",
            Core.scene.find("factoryscope-liquid-viewing") != null));
        queue(() -> capture("liquid-gas-junction-world"));
        queue(this::closeAnyDialog);
        queue(this::checkLiquidLocalization);
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
    private void clickBuilding(Building building){ harness.clickBuilding(building); }
    private void clickNamed(String name){ harness.clickNamed(name); }
    private void dragTiles(int x1, int y1, int x2, int y2){ harness.dragTiles(x1, y1, x2, y2); }
    private void capture(String name){ harness.capture(name); }
    private Building placeAt(Block block, int x, int y){ return harness.placeAt(block, x, y); }
    private Building placeAt(Block block, int x, int y, int rotation){ return harness.placeAt(block, x, y, rotation); }
    private boolean dialogShows(String text){ return harness.dialogShows(text); }
    private ScrollPane findPane(Group group){ return harness.findPane(group); }
    private void checkFits(String label){ harness.checkFits(label); }
    private void restoreLayout(){ harness.restoreLayout(); }
    private String describe(Building building){ return harness.describe(building); }
    private void checkLiquidLocalization(){ harness.checkLiquidLocalization(); }
}
