package factoryscope.probe;

import arc.util.*;
import factoryscope.analysis.*;
import factoryscope.model.*;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.defense.turrets.*;
import mindustry.world.blocks.environment.*;
import mindustry.world.blocks.power.*;
import mindustry.world.blocks.production.*;
import mindustry.world.blocks.units.*;
import org.junit.jupiter.api.*;

import java.util.*;

import static mindustry.Vars.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The production models of non-crafter families, against a real headless Mindustry.
 *
 * <p>Every current rate is checked twice: against the value the engine itself computed for the block's
 * status bar or power graph, and against what the block actually produced while the simulation ran tick
 * by tick. Separator estimates are checked as long-run shares over hundreds of real cycles.
 */
class ProductionModelsTest{
    private static final float TOLERANCE = 0.001f;

    @BeforeAll
    static void boot(){
        HeadlessGame.start();
    }

    @BeforeEach
    void freshWorld(){
        HeadlessGame.newWorld(24);
    }

    // ------------------------------------------------------------------ drills

    @Test
    void aDrillReportsTheSpeedTheEngineComputesAndActuallyMines(){
        floor(Blocks.sand, 8, 8, 13, 13);
        Building build = place(Blocks.mechanicalDrill, 10, 10, 0);
        Drill.DrillBuild drill = (Drill.DrillBuild)build;
        assertEquals(Items.sand, drill.dominantItem, "the fixture must stand on sand");

        run(build, 600, true);
        FactorySnapshot snapshot = MindustryFactoryProbe.probe(build);
        OutputState sand = output(snapshot, Items.sand.name);

        float delay = ((Drill)build.block).getDrillTime(Items.sand);
        assertEquals(ProductionFamily.drill, snapshot.family);
        assertEquals(SupportLevel.full, snapshot.support);
        assertEquals(drill.dominantItems * 60f / delay, sand.theoreticalPerSecond, TOLERANCE);
        assertEquals(drill.lastDrillSpeed * 60f, sand.expectedPerSecond, TOLERANCE,
            "the current rate is the one the drill's own speed bar shows");
        assertFalse(sand.estimate);

        float measured = run(build, 6000, true) / 100f;
        assertEquals(sand.expectedPerSecond, measured, 0.03f, "the drill must actually mine at the reported rate");
        assertEquals(DiagnosticReason.active, FactoryAnalyzer.analyze(snapshot).reason());
        assertTrue(snapshot.producedItems.contains(ref(Items.sand)), "the drill stays a structural sand producer");
    }

    @Test
    void aDrillWithNoOreUnderItSaysSo(){
        Building build = place(Blocks.mechanicalDrill, 10, 10, 0);
        run(build, 10, false);

        FactorySnapshot snapshot = MindustryFactoryProbe.probe(build);
        DiagnosticResult result = FactoryAnalyzer.analyze(snapshot);

        assertEquals(ProductionGate.noResource, snapshot.gate);
        assertEquals(DiagnosticReason.noResource, result.reason());
        assertEquals("drill", result.primary.variant);
        assertTrue(snapshot.outputs.isEmpty());
    }

    @Test
    void anOreTooHardForTheDrillIsNamed(){
        floor(Blocks.stone, 8, 8, 13, 13);
        for(int x = 10; x <= 11; x++) for(int y = 10; y <= 11; y++) world.tile(x, y).setOverlay(Blocks.oreTitanium);
        Building build = place(Blocks.mechanicalDrill, 10, 10, 0);
        assertNull(((Drill.DrillBuild)build).dominantItem, "a mechanical drill cannot mine titanium");

        FactorySnapshot snapshot = MindustryFactoryProbe.probe(build);
        DiagnosticResult result = FactoryAnalyzer.analyze(snapshot);

        assertEquals(DiagnosticReason.resourceTooHard, result.reason());
        assertEquals(List.of(ref(Items.titanium)), result.primary.resources);
        assertTrue(snapshot.facts.stream().anyMatch(fact -> fact.labelKey.equals("fact.drill-tier")
            && fact.value == ((Drill)build.block).tier));
    }

    @Test
    void aDrillWithAFullBufferIsBlocked(){
        floor(Blocks.sand, 8, 8, 13, 13);
        Building build = place(Blocks.mechanicalDrill, 10, 10, 0);
        build.items.add(Items.sand, build.block.itemCapacity);
        build.update();

        FactorySnapshot snapshot = MindustryFactoryProbe.probe(build);

        assertFalse(build.shouldConsume(), "the engine must consider the drill blocked");
        assertEquals(DiagnosticReason.outputBlocked, FactoryAnalyzer.analyze(snapshot).reason());
        assertEquals(0f, output(snapshot, Items.sand.name).expectedPerSecond, TOLERANCE);
    }

    @Test
    void aBurstDrillReportsItsBurstRateAndNeedsRoomForAWholeBurst(){
        floor(Blocks.sand, 6, 6, 15, 15);
        Building build = place(Blocks.impactDrill, 10, 10, 0);
        Drill.DrillBuild drill = (Drill.DrillBuild)build;
        assertEquals(Items.sand, drill.dominantItem);

        run(build, 120, true);
        FactorySnapshot snapshot = MindustryFactoryProbe.probe(build);
        OutputState sand = output(snapshot, Items.sand.name);
        float nominal = drill.dominantItems * 60f / ((Drill)build.block).getDrillTime(Items.sand);

        assertEquals(nominal, sand.theoreticalPerSecond, TOLERANCE);
        assertEquals(drill.lastDrillSpeed * 60f, sand.expectedPerSecond, TOLERANCE);
        assertEquals(nominal, sand.expectedPerSecond, 0.001f, "a burst drill has no warm-up in its rate");
        float measured = run(build, 7200, true) / 120f;
        assertEquals(sand.expectedPerSecond, measured, 0.15f);

        build.items.clear();
        build.items.add(Items.sand, build.block.itemCapacity - drill.dominantItems + 1);
        supply(build);
        build.update();
        assertFalse(build.shouldConsume(), "less room than one burst stops the drill");
        assertEquals(DiagnosticReason.outputBlocked, FactoryAnalyzer.analyze(MindustryFactoryProbe.probe(build)).reason());
    }

    @Test
    void aBeamDrillReportsEachWallItReachesAtTheEngineRate(){
        wall(Blocks.stoneWall, Blocks.wallOreBeryllium, 12, 9, 13, 12);
        Building build = place(Blocks.plasmaBore, 10, 10, 0);
        BeamDrill.BeamDrillBuild drill = (BeamDrill.BeamDrillBuild)build;

        run(build, 120, true);
        FactorySnapshot snapshot = MindustryFactoryProbe.probe(build);
        OutputState beryllium = output(snapshot, Items.beryllium.name);

        assertEquals(ProductionFamily.beamDrill, snapshot.family);
        assertEquals(build.block.size, drill.facingAmount, "every laser must hit the ore wall");
        assertEquals(drill.facingAmount * 60f / ((BeamDrill)build.block).getDrillTime(Items.beryllium),
            beryllium.theoreticalPerSecond, TOLERANCE);
        assertEquals(drill.lastDrillSpeed * 60f, beryllium.expectedPerSecond, TOLERANCE);
        float measured = run(build, 4800, true) / 80f;
        assertEquals(beryllium.expectedPerSecond, measured, 0.05f);
        assertTrue(snapshot.producedItems.contains(ref(Items.beryllium)), "a beam drill is a structural producer");
    }

    @Test
    void aBeamDrillFacingTooHardWallsNamesThem(){
        wall(Blocks.stoneWall, Blocks.wallOreTungsten, 12, 9, 13, 12);
        Building build = place(Blocks.plasmaBore, 10, 10, 0);
        run(build, 5, false);

        DiagnosticResult result = FactoryAnalyzer.analyze(MindustryFactoryProbe.probe(build));

        assertEquals(DiagnosticReason.resourceTooHard, result.reason());
        assertEquals("beam-drill", result.primary.variant);
        assertEquals(List.of(ref(Items.tungsten)), result.primary.resources);
    }

    @Test
    void aBeamDrillWithNothingInFrontSaysSo(){
        Building build = place(Blocks.plasmaBore, 10, 10, 0);
        run(build, 5, false);

        DiagnosticResult result = FactoryAnalyzer.analyze(MindustryFactoryProbe.probe(build));

        assertEquals(DiagnosticReason.noResource, result.reason());
        assertEquals("beam-drill", result.primary.variant);
    }

    @Test
    void aWallCrusherUsesTheWallsInFrontAndTheEngineRate(){
        wall(Blocks.duneWall, null, 12, 9, 12, 12);
        Building build = place(Blocks.cliffCrusher, 10, 10, 0);
        WallCrafter.WallCrafterBuild crusher = (WallCrafter.WallCrafterBuild)build;
        WallCrafter block = (WallCrafter)build.block;

        run(build, 120, true);
        FactorySnapshot snapshot = MindustryFactoryProbe.probe(build);
        OutputState sand = output(snapshot, Items.sand.name);
        float yield = ExtractionModels.wallYield(crusher, block);

        assertEquals(ProductionFamily.wallCrafter, snapshot.family);
        assertEquals(crusher.lastEfficiency, yield, TOLERANCE,
            "without a booster the repeated geometry must equal the engine's own efficiency");
        assertEquals(yield * 60f / block.drillTime, sand.theoreticalPerSecond, TOLERANCE);
        assertEquals(crusher.lastEfficiency * 60f / block.drillTime, sand.expectedPerSecond, TOLERANCE);
        float measured = run(build, 6000, true) / 100f;
        assertEquals(sand.expectedPerSecond, measured, 0.05f);
        assertTrue(snapshot.producedItems.contains(ref(Items.sand)), "a wall crusher is a structural producer");
    }

    @Test
    void aWallCrusherFacingNothingSaysSo(){
        Building build = place(Blocks.cliffCrusher, 10, 10, 0);
        run(build, 5, false);

        DiagnosticResult result = FactoryAnalyzer.analyze(MindustryFactoryProbe.probe(build));

        assertEquals(DiagnosticReason.noResource, result.reason());
        assertEquals("wall-crafter", result.primary.variant);
    }

    // ------------------------------------------------------------------ pumps

    @Test
    void aPumpReportsWhatItActuallyPumps(){
        floor(Blocks.water, 9, 9, 11, 11);
        Building build = place(Blocks.mechanicalPump, 10, 10, 0);
        Pump.PumpBuild pump = (Pump.PumpBuild)build;
        Pump block = (Pump)build.block;

        run(build, 30, false);
        build.liquids.clear();
        FactorySnapshot snapshot = MindustryFactoryProbe.probe(build);
        OutputState water = output(snapshot, Liquids.water.name);

        assertEquals(ProductionFamily.pump, snapshot.family);
        assertEquals(pump.amount * block.pumpAmount * 60f, water.theoreticalPerSecond, TOLERANCE);
        assertEquals(water.theoreticalPerSecond, water.expectedPerSecond, TOLERANCE);

        float pumped = 0f;
        for(int i = 0; i < 600; i++){
            build.liquids.clear();
            build.update();
            pumped += build.liquids.get(Liquids.water);
        }
        assertEquals(water.expectedPerSecond, pumped / 10f, 0.01f);
    }

    @Test
    void aFullPumpAndADryPumpAreToldApart(){
        floor(Blocks.water, 9, 9, 11, 11);
        Building wet = place(Blocks.mechanicalPump, 10, 10, 0);
        wet.liquids.add(Liquids.water, wet.block.liquidCapacity);
        wet.update();
        assertEquals(DiagnosticReason.outputBlocked, FactoryAnalyzer.analyze(MindustryFactoryProbe.probe(wet)).reason());

        Building dry = place(Blocks.mechanicalPump, 4, 4, 0);
        dry.update();
        DiagnosticResult result = FactoryAnalyzer.analyze(MindustryFactoryProbe.probe(dry));
        assertEquals(DiagnosticReason.noResource, result.reason());
        assertEquals("pump", result.primary.variant);
    }

    @Test
    void aWaterExtractorReportsTheRateItsBarShows(){
        Building build = place(Blocks.waterExtractor, 10, 10, 0);
        SolidPump.SolidPumpBuild pump = (SolidPump.SolidPumpBuild)build;

        float pumped = 0f;
        for(int i = 0; i < 600; i++){
            build.liquids.clear();
            supply(build);
            build.update();
            pumped += build.liquids.get(Liquids.water);
        }
        FactorySnapshot snapshot = MindustryFactoryProbe.probe(build);
        OutputState water = output(snapshot, Liquids.water.name);

        assertEquals(ProductionFamily.solidPump, snapshot.family);
        assertEquals(pump.lastPump * 60f, water.expectedPerSecond, TOLERANCE);
        assertTrue(water.expectedPerSecond > 0f);
        assertEquals(water.expectedPerSecond, pumped / 10f, 0.05f);
    }

    // ------------------------------------------------------------------ separator

    @Test
    void separatorRatesAreExpectedSharesOfTheExactCycleRate(){
        Building build = place(Blocks.separator, 10, 10, 0);
        Separator block = (Separator)build.block;
        Map<Item, Integer> produced = new HashMap<>();
        int cycles = 0;
        for(int i = 0; i < 35 * 600; i++){
            supply(build);
            build.liquids.add(Liquids.slag, 1f);
            build.update();
            for(ItemStack stack : block.results){
                int amount = build.items.get(stack.item);
                if(amount > 0){
                    produced.merge(stack.item, amount, Integer::sum);
                    cycles += amount;
                    build.items.remove(stack.item, amount);
                }
            }
        }

        FactorySnapshot snapshot = MindustryFactoryProbe.probe(build);
        float craftsPerSecond = 60f / block.craftTime;
        int weights = Arrays.stream(block.results).mapToInt(stack -> stack.amount).sum();

        assertEquals(ProductionFamily.separator, snapshot.family);
        assertTrue(cycles > 500, "the fixture must run hundreds of real cycles, ran " + cycles);
        for(ItemStack stack : block.results){
            OutputState output = output(snapshot, stack.item.name);
            float share = stack.amount / (float)weights;
            assertTrue(output.estimate, "a random output can only be an estimate");
            assertEquals(share * craftsPerSecond, output.theoreticalPerSecond, TOLERANCE);
            assertEquals(share, produced.getOrDefault(stack.item, 0) / (float)cycles, 0.05f,
                stack.item.name + " must come out at its weighted share");
        }
        assertTrue(snapshot.hasEstimates());
        assertTrue(snapshot.notes.contains("note.separator-random"));
        assertTrue(snapshot.producedItems.containsAll(Arrays.stream(block.results).map(stack -> ref(stack.item)).toList()),
            "a separator is a structural producer of every possible result");
    }

    // ------------------------------------------------------------------ generators

    @Test
    void aCombustionGeneratorReportsThePowerItFeedsItsGraph(){
        Building build = place(Blocks.combustionGenerator, 10, 10, 0);
        PowerGenerator.GeneratorBuild generator = (PowerGenerator.GeneratorBuild)build;
        ConsumeGenerator block = (ConsumeGenerator)build.block;
        build.items.add(Items.coal, 10);
        run(build, 60, false);

        FactorySnapshot snapshot = MindustryFactoryProbe.probe(build);
        OutputState power = snapshot.outputs.stream().filter(output -> output.kind == ResourceKind.power).findFirst().orElseThrow();

        assertEquals(ProductionFamily.generator, snapshot.family);
        assertEquals(block.powerProduction * 60f, power.theoreticalPerSecond, TOLERANCE);
        assertEquals(generator.getPowerProduction() * 60f, power.expectedPerSecond, TOLERANCE);
        assertTrue(power.expectedPerSecond > 0f);
        Fact fuel = fact(snapshot, "fact.fuel-use");
        assertEquals(60f / block.itemDuration, fuel.value, TOLERANCE);

        int coalBefore = build.items.get(Items.coal);
        run(build, (int)(block.itemDuration * 4), false);
        assertEquals(4, coalBefore - build.items.get(Items.coal), "one coal per item duration while running");
    }

    @Test
    void anExplosionWatchIsNotListedAsAFuelRequirement(){
        Building build = place(Blocks.combustionGenerator, 10, 10, 0);
        build.update();

        FactorySnapshot snapshot = MindustryFactoryProbe.probe(build);
        DiagnosticResult result = FactoryAnalyzer.analyze(snapshot);

        assertEquals(1, snapshot.inputs.stream().filter(input -> input.kind == ResourceKind.item).count(),
            "ConsumeItemExplode consumes nothing and is never a requirement");
        assertEquals(DiagnosticReason.missingItemInput, result.reason());
        assertEquals(1, result.primary.resources.size());
    }

    @Test
    void anUnlitSolarPanelIsLimitedByItsBlockCondition(){
        state.rules.lighting = true;
        state.rules.ambientLight.a = 0.75f;
        Building build = place(Blocks.solarPanel, 10, 10, 0);
        run(build, 5, false);

        FactorySnapshot snapshot = MindustryFactoryProbe.probe(build);
        DiagnosticResult result = FactoryAnalyzer.analyze(snapshot);

        assertEquals(0.25f, fact(snapshot, "fact.light").value, TOLERANCE);
        assertEquals(DiagnosticReason.blockConditionLimited, result.reason());
        assertEquals(Severity.reduced, result.severity());
    }

    @Test
    void aHotThoriumReactorShowsItsHeatAsADanger(){
        Building build = place(Blocks.thoriumReactor, 10, 10, 0);
        NuclearReactor.NuclearReactorBuild reactor = (NuclearReactor.NuclearReactorBuild)build;
        build.items.add(Items.thorium, 15);
        reactor.heat = 0.6f;

        FactorySnapshot snapshot = MindustryFactoryProbe.probe(build);

        assertEquals(0.5f, fact(snapshot, "fact.fuel-fullness").value, TOLERANCE);
        Fact heat = fact(snapshot, "fact.reactor-heat");
        assertEquals(0.6f, heat.value, TOLERANCE);
        assertEquals(Fact.Tone.danger, heat.tone, "above the engine's flash threshold");
        assertTrue(snapshot.notes.contains("note.reactor-heat"));
    }

    // ------------------------------------------------------------------ units

    @Test
    void aUnitFactoryWithNothingSelectedSaysSo(){
        Building build = place(Blocks.groundFactory, 10, 10, 0);
        ((UnitFactory.UnitFactoryBuild)build).currentPlan = -1;
        build.update();

        DiagnosticResult result = FactoryAnalyzer.analyze(MindustryFactoryProbe.probe(build));

        assertEquals(DiagnosticReason.noPlanSelected, result.reason());
    }

    @Test
    void aUnitFactoryBuildsAtThePlanRateAndWaitsAtTheUnitCap(){
        state.rules.unitCapVariable = false;
        state.rules.unitCap = 0;
        Building build = place(Blocks.groundFactory, 10, 10, 0);
        UnitFactory.UnitFactoryBuild factory = (UnitFactory.UnitFactoryBuild)build;
        factory.currentPlan = 0;
        UnitFactory.UnitPlan plan = ((UnitFactory)build.block).plans.get(0);
        build.items.add(Items.silicon, 100);
        build.items.add(Items.lead, 100);
        run(build, 10, false);

        FactorySnapshot building = MindustryFactoryProbe.probe(build);
        OutputState unit = output(building, plan.unit.name);
        assertEquals(ProductionFamily.unitFactory, building.family);
        assertEquals(ResourceKind.unit, unit.kind);
        assertEquals(60f / plan.time, unit.theoreticalPerSecond, TOLERANCE);
        assertEquals(build.efficiency * 60f / plan.time, unit.expectedPerSecond, TOLERANCE);

        float before = factory.progress;
        run(build, 60, false);
        assertEquals(unit.expectedPerSecond, (factory.progress - before) / plan.time, 0.0005f,
            "progress must advance at the reported rate");

        run(build, (int)plan.time + 600, false);
        assertNotNull(factory.payload, "the finished unit must be held while the cap is reached");
        FactorySnapshot waiting = MindustryFactoryProbe.probe(build);
        DiagnosticResult result = FactoryAnalyzer.analyze(waiting);
        assertEquals(DiagnosticReason.unitCapReached, result.reason());
        assertEquals(List.of(new ResourceRef(ResourceKind.unit, plan.unit.name, plan.unit.localizedName)),
            result.primary.resources);
    }

    @Test
    void anEmptyReconstructorWaitsForAUnitItAccepts(){
        Building build = place(Blocks.additiveReconstructor, 10, 10, 0);
        build.update();

        DiagnosticResult result = FactoryAnalyzer.analyze(MindustryFactoryProbe.probe(build));

        assertEquals(DiagnosticReason.waitingForUnit, result.reason());
        assertTrue(result.primary.resources.contains(
            new ResourceRef(ResourceKind.unit, UnitTypes.dagger.name, UnitTypes.dagger.localizedName)));
    }

    // ------------------------------------------------------------------ turrets

    @Test
    void anEmptyTurretHasNoAmmunitionAndNamesWhatItAccepts(){
        Building build = place(Blocks.duo, 10, 10, 0);
        build.update();

        FactorySnapshot snapshot = MindustryFactoryProbe.probe(build);
        DiagnosticResult result = FactoryAnalyzer.analyze(snapshot);

        assertEquals(ProductionFamily.turret, snapshot.family);
        assertEquals(DiagnosticReason.noAmmo, result.reason());
        assertTrue(result.primary.resources.contains(ref(Items.copper)));
    }

    @Test
    void aLoadedTurretWithNoTargetIsReadyNotStarved(){
        Building build = place(Blocks.duo, 10, 10, 0);
        build.handleItem(null, Items.copper);
        run(build, 60, false);

        FactorySnapshot snapshot = MindustryFactoryProbe.probe(build);
        DiagnosticResult result = FactoryAnalyzer.analyze(snapshot);

        assertFalse(build.shouldConsume(), "a reloaded turret without a target declines to consume");
        assertEquals(DiagnosticReason.turretIdle, result.reason());
        assertEquals(Severity.normal, result.severity());
        assertTrue(result.findings.stream().noneMatch(finding -> finding.reason == DiagnosticReason.missingItemInput),
            "ammunition held in the turret is not an item shortage");
        assertTrue(fact(snapshot, "fact.fire-rate").estimate, "fire rate depends on targets");
        assertTrue(fact(snapshot, "fact.ammo-use").estimate);
        Fact ammo = fact(snapshot, "fact.ammo");
        assertEquals(((ItemTurret.ItemTurretBuild)build).totalAmmo, ammo.value, TOLERANCE);
        assertEquals(((Turret)build.block).maxAmmo, ammo.total, TOLERANCE);
    }

    @Test
    void probingATurretNeverReordersItsAmmunition(){
        Building build = place(Blocks.duo, 10, 10, 0);
        ItemTurret.ItemTurretBuild turret = (ItemTurret.ItemTurretBuild)build;
        build.handleItem(null, Items.graphite);
        build.handleItem(null, Items.copper);
        run(build, 30, false);
        //the top entry can no longer fire a shot, which is exactly when Turret.hasAmmo() reorders the list
        turret.ammo.peek().amount = 0;
        List<String> before = ammoOrder(turret);

        for(int i = 0; i < 5; i++) MindustryFactoryProbe.probe(build);

        assertEquals(before, ammoOrder(turret), "Turret.hasAmmo() would have moved graphite to the top");
        assertNotEquals(ProductionGate.noAmmo, MindustryFactoryProbe.probe(build).gate,
            "a turret holding a usable entry deeper in the list is not out of ammunition");
    }

    // ------------------------------------------------------------------ helpers

    private static List<String> ammoOrder(ItemTurret.ItemTurretBuild turret){
        List<String> order = new ArrayList<>();
        for(int i = 0; i < turret.ammo.size; i++){
            var entry = (ItemTurret.ItemEntry)turret.ammo.get(i);
            order.add(entry.item.name + ":" + entry.amount);
        }
        return order;
    }

    private static OutputState output(FactorySnapshot snapshot, String contentId){
        return snapshot.outputs.stream().filter(output -> contentId.equals(output.contentId)).findFirst()
            .orElseThrow(() -> new AssertionError("no output " + contentId + " in " + snapshot.outputs));
    }

    private static Fact fact(FactorySnapshot snapshot, String key){
        return snapshot.facts.stream().filter(fact -> fact.labelKey.equals(key)).findFirst()
            .orElseThrow(() -> new AssertionError("no fact " + key + " in " + snapshot.facts));
    }

    private static ResourceRef ref(Item item){
        return new ResourceRef(ResourceKind.item, item.name, item.localizedName);
    }

    private static void floor(Block floor, int x1, int y1, int x2, int y2){
        for(int x = x1; x <= x2; x++) for(int y = y1; y <= y2; y++) world.tile(x, y).setFloor((Floor)floor);
    }

    private static void wall(Block wall, Block ore, int x1, int y1, int x2, int y2){
        for(int x = x1; x <= x2; x++){
            for(int y = y1; y <= y2; y++){
                Tile tile = world.tile(x, y);
                tile.setBlock(wall);
                if(ore != null) tile.setOverlay(ore);
            }
        }
    }

    private static Building place(Block block, int x, int y, int rotation){
        Tile tile = world.tile(x, y);
        tile.setBlock(block, Team.sharded, rotation);
        Building build = tile.build;
        assertNotNull(build, "failed to place " + block.name);
        build.onProximityUpdate();
        return build;
    }

    /** Full power and the liquids the fixture blocks need, as a supplied grid and pipe would provide. */
    private static void supply(Building build){
        if(build.power != null) build.power.status = 1f;
        if(build.liquids != null && build.block == Blocks.impactDrill) build.liquids.add(Liquids.water, 1f);
    }

    /**
     * Advances the building by whole engine ticks, optionally emptying its item buffer after each one
     * as an output conveyor would, and returns how many items it produced.
     */
    private static int run(Building build, int ticks, boolean drainItems){
        int produced = 0;
        for(int i = 0; i < ticks; i++){
            //block timers read Time.time, which only advances when the clock is updated
            Time.update();
            supply(build);
            build.update();
            if(drainItems && build.items != null){
                produced += build.items.total();
                build.items.clear();
            }
        }
        return produced;
    }
}
