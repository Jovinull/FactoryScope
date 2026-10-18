package factoryscope.acceptance;

import arc.util.Log;
import mindustry.content.Blocks;
import mindustry.content.Items;
import mindustry.gen.Building;
import mindustry.world.blocks.power.PowerGraph;

import java.util.Arrays;

import static mindustry.Vars.state;

/** Repeats the isolated PowerScope fixture without coupling its lifecycle to unrelated UI scenarios. */
final class PowerFixtureStress{
    private final AcceptanceHarness harness;
    private final int repetitions;
    private Building[] current = new Building[4];
    private Building node;
    private PowerGraph graph;
    private Building[] previous = new Building[0];
    private PowerGraph previousGraph;
    private double setupTick;
    private int iteration;

    PowerFixtureStress(AcceptanceHarness harness, int repetitions){
        this.harness = harness;
        this.repetitions = repetitions;
    }

    void start(){
        harness.scenarioNow("isolated PowerScope fixture repeated " + repetitions + " times");
        harness.queue(this::prepare);
        harness.pump();
    }

    private void prepare(){
        harness.clearRegion();
        int x = harness.rx(), y = harness.ry();
        current = new Building[6];
        current[0] = harness.placeAt(Blocks.rtgGenerator, x + 3, y + 7);
        current[1] = harness.placeAt(Blocks.batteryLarge, x + 7, y + 4);
        current[2] = harness.placeAt(Blocks.siliconSmelter, x + 11, y + 7);
        current[3] = harness.placeAt(Blocks.combustionGenerator, x + 7, y + 11);
        current[4] = harness.placeAt(Blocks.forceProjector, x + 11, y + 2);
        current[5] = harness.placeAt(Blocks.forceProjector, x + 11, y + 12);
        node = harness.placeAt(Blocks.powerNodeLarge, x + 7, y + 7);
        if(current[0] != null){
            current[0].items.add(Items.thorium, 10);
            current[0].updateConsumption();
        }
        if(current[2] != null){
            current[2].items.add(Items.sand, 30);
            current[2].items.add(Items.coal, 30);
            current[2].updateConsumption();
        }
        if(current[3] != null) current[3].updateConsumption();
        if(node != null){
            for(Building endpoint : current) if(endpoint != null) node.configureAny(endpoint.pos());
        }
        if(current[1] != null) current[1].power.status = 1f;
        graph = current[0] == null ? null : current[0].power.graph;
        setupTick = state.tick;
        harness.context.nextActionDelay = 1f;
        harness.context.actions.insert(0, this::checkReady);
    }

    private void checkReady(){
        long engineUpdates = Math.max(0L, Math.round(state.tick - setupTick));
        boolean ready = current[0] != null && current[0].getPowerProduction() > 0f
            && current[2] != null && current[2].shouldConsumePower;
        if(!ready && engineUpdates < 120){
            harness.context.nextActionDelay = 1f;
            harness.context.actions.insert(0, this::checkReady);
            return;
        }
        if(graph != null) graph.update();
        boolean sameGraph = graph != null && node != null && node.power != null && node.power.graph == graph
            && Arrays.stream(current).allMatch(build -> build != null && build.power != null && build.power.graph == graph);
        boolean priorBuildingsRemoved = Arrays.stream(previous).noneMatch(build -> build != null && build.isValid());
        boolean newGraph = previousGraph == null || graph != previousGraph;
        boolean metricsValid = graph != null && graph.getPowerProduced() > 0f
            && graph.getPowerProduced() < graph.getPowerNeeded() && graph.getSatisfaction() >= 0.999f
            && current[1] != null && current[1].power.status > 0f;
        String details = "engineUpdates=" + engineUpdates + ", ready=" + ready
            + ", produced=" + (graph == null ? -1f : graph.getPowerProduced())
            + ", needed=" + (graph == null ? -1f : graph.getPowerNeeded())
            + ", satisfaction=" + (graph == null ? -1f : graph.getSatisfaction())
            + ", balanceSamples=" + (graph != null && graph.hasPowerBalanceSamples())
            + ", members=" + (graph == null ? -1 : graph.all.size)
            + ", sameGraph=" + sameGraph + ", priorBuildingsRemoved=" + priorBuildingsRemoved
            + ", newGraph=" + newGraph;
        harness.check("PowerScope repeated fixture " + (iteration + 1) + "/" + repetitions + " has valid current engine metrics",
            ready && sameGraph && metricsValid && graph.all.size == 7 && priorBuildingsRemoved && newGraph, details);
        Log.info(AcceptanceHarness.TAG + "   INFO Power fixture @/@ @", iteration + 1, repetitions, details);
        previous = current.clone();
        previousGraph = graph;
        iteration++;
        if(iteration < repetitions){
            harness.context.nextActionDelay = 1f;
            harness.context.actions.insert(0, this::prepare);
        }else{
            harness.context.actions.add(harness::finish);
        }
    }
}
