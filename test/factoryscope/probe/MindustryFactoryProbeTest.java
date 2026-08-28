package factoryscope.probe;

import factoryscope.analysis.*;
import factoryscope.model.*;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.world.*;
import org.junit.jupiter.api.*;

import static mindustry.Vars.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises the probe against a real headless Mindustry, with real blocks and real consumers.
 *
 * <p>These tests are what keep the formulas honest: the pure unit tests can only prove the engine
 * behaves as designed, while these prove the design matches the game.
 */
class MindustryFactoryProbeTest{
    private static final float TOLERANCE = 0.001f;

    @BeforeAll
    static void boot(){
        HeadlessGame.start();
    }

    @BeforeEach
    void freshWorld(){
        HeadlessGame.newWorld(16);
    }

    @Test
    void aStarvedSmelterReportsBothMissingItems(){
        //powered but empty, so items are unambiguously the only thing missing
        Building smelter = place(Blocks.siliconSmelter, 4, 4);
        smelter.power.status = 1f;
        smelter.updateConsumption();

        FactorySnapshot snapshot = MindustryFactoryProbe.probe(smelter);
        DiagnosticResult result = FactoryAnalyzer.analyze(snapshot);

        assertEquals(SupportLevel.full, snapshot.support);
        assertEquals(DiagnosticReason.missingItemInput, result.reason());
        assertEquals(Severity.stopped, result.severity());
        assertTrue(result.primary.resourceNames().contains(Items.sand.localizedName));
        assertTrue(result.primary.resourceNames().contains(Items.coal.localizedName));
    }

    @Test
    void theSmelterReportsTheRealVanillaOutputRate(){
        Building smelter = place(Blocks.siliconSmelter, 4, 4);

        FactorySnapshot snapshot = MindustryFactoryProbe.probe(smelter);
        OutputState silicon = snapshot.outputs.get(0);

        //vanilla silicon smelter: craftTime 40 ticks, one silicon per cycle -> 1.5 items per second
        assertEquals(Items.silicon.localizedName, silicon.name);
        assertEquals(1.5f, silicon.theoreticalPerSecond, TOLERANCE);
        assertEquals(0f, silicon.expectedPerSecond, TOLERANCE, "a starved smelter produces nothing");
        assertEquals(40f / 60f, snapshot.craftTimeSeconds, TOLERANCE);
    }

    @Test
    void aSuppliedSmelterRunsAtFullEfficiencyAndFullRate(){
        Building smelter = supplied(place(Blocks.siliconSmelter, 4, 4));

        FactorySnapshot snapshot = MindustryFactoryProbe.probe(smelter);
        DiagnosticResult result = FactoryAnalyzer.analyze(snapshot);

        assertEquals(1f, snapshot.efficiency, TOLERANCE);
        assertEquals(DiagnosticReason.active, result.reason());
        assertEquals(1.5f, snapshot.outputs.get(0).expectedPerSecond, TOLERANCE);
    }

    @Test
    void aFullOutputBufferIsDiagnosedAsBlocked(){
        Building smelter = supplied(place(Blocks.siliconSmelter, 4, 4));
        smelter.items.add(Items.silicon, smelter.block.itemCapacity);
        smelter.updateConsumption();

        FactorySnapshot snapshot = MindustryFactoryProbe.probe(smelter);
        DiagnosticResult result = FactoryAnalyzer.analyze(snapshot);

        assertFalse(smelter.shouldConsume(), "the game itself must consider the crafter blocked");
        assertTrue(snapshot.outputBufferFull);
        assertEquals(DiagnosticReason.outputBlocked, result.reason());
        assertTrue(result.primary.resourceNames().contains(Items.silicon.localizedName));
    }

    @Test
    void aDisabledSmelterIsDiagnosedAsDisabled(){
        Building smelter = supplied(place(Blocks.siliconSmelter, 4, 4));
        smelter.enabled = false;
        smelter.updateConsumption();

        DiagnosticResult result = FactoryAnalyzer.analyze(MindustryFactoryProbe.probe(smelter));

        assertEquals(DiagnosticReason.disabled, result.reason());
    }

    @Test
    void aBuildingOutsideThePlayableAreaIsReportedAsInoperable(){
        //this is the case Ground Zero produces: the game disables anything built outside the limited area
        Building smelter = supplied(place(Blocks.siliconSmelter, 12, 12));
        state.rules.limitMapArea = true;
        state.rules.disableOutsideArea = true;
        state.rules.limitX = 0;
        state.rules.limitY = 0;
        state.rules.limitWidth = 4;
        state.rules.limitHeight = 4;
        smelter.checkAllowUpdate();
        smelter.updateConsumption();

        FactorySnapshot snapshot = MindustryFactoryProbe.probe(smelter);

        assertFalse(smelter.enabled, "the game itself should have disabled the building");
        assertFalse(snapshot.updateAllowed);
        assertEquals(DiagnosticReason.inoperableHere, FactoryAnalyzer.analyze(snapshot).reason());
    }

    @Test
    void aDisabledSmelterAndAnInoperableOneAreToldApart(){
        Building smelter = supplied(place(Blocks.siliconSmelter, 4, 4));
        smelter.enabled = false;
        smelter.updateConsumption();

        FactorySnapshot snapshot = MindustryFactoryProbe.probe(smelter);

        assertTrue(snapshot.updateAllowed, "nothing stops this building from running where it stands");
        assertEquals(DiagnosticReason.disabled, FactoryAnalyzer.analyze(snapshot).reason());
    }

    @Test
    void anUnpoweredCrafterBlamesPowerRatherThanItems(){
        //the kiln needs sand, lead and power; with materials present only the grid can be at fault
        Building kiln = place(Blocks.kiln, 4, 4);
        kiln.items.add(Items.sand, 10);
        kiln.items.add(Items.lead, 10);
        kiln.updateConsumption();

        FactorySnapshot snapshot = MindustryFactoryProbe.probe(kiln);
        DiagnosticResult result = FactoryAnalyzer.analyze(snapshot);

        assertNotNull(snapshot.power, "a kiln consumes power");
        assertEquals(0f, snapshot.power.satisfaction, TOLERANCE);
        assertEquals(DiagnosticReason.insufficientPower, result.reason());
    }

    @Test
    void aLiquidConsumerIsRecognisedAndPricedPerSecond(){
        //the cryofluid mixer consumes water and titanium and outputs a liquid
        Building mixer = place(Blocks.cryofluidMixer, 4, 4);

        FactorySnapshot snapshot = MindustryFactoryProbe.probe(mixer);

        ResourceState water = snapshot.inputs.stream()
            .filter(input -> input.kind == ResourceKind.liquid)
            .findFirst()
            .orElseThrow(() -> new AssertionError("the mixer should expose a liquid input"));

        assertTrue(water.recognised);
        assertEquals(RateUnit.perSecond, water.unit);
        assertEquals(Liquids.water.localizedName, water.name);
        assertEquals(0f, water.satisfaction, TOLERANCE, "an empty mixer has no water");

        assertEquals(DiagnosticReason.missingItemInput,
            FactoryAnalyzer.analyze(snapshot).reason(),
            "an empty mixer is short of everything, and items are reported first");
    }

    @Test
    void aLiquidOutputRateUsesPerTickUnits(){
        Building mixer = place(Blocks.cryofluidMixer, 4, 4);

        OutputState cryofluid = MindustryFactoryProbe.probe(mixer)
            .outputs.stream()
            .filter(output -> output.kind == ResourceKind.liquid)
            .findFirst()
            .orElseThrow(() -> new AssertionError("the mixer should produce a liquid"));

        //vanilla cryofluid mixer outputs 0.2 units per tick, which the game displays as 12/s
        assertEquals(12f, cryofluid.theoreticalPerSecond, TOLERANCE);
    }

    @Test
    void aBlockWithoutConsumersDegradesToLimitedDiagnostics(){
        Building wall = place(Blocks.copperWall, 4, 4);

        FactorySnapshot snapshot = MindustryFactoryProbe.probe(wall);
        DiagnosticResult result = FactoryAnalyzer.analyze(snapshot);

        assertEquals(SupportLevel.minimal, snapshot.support);
        assertTrue(snapshot.inputs.isEmpty());
        assertTrue(snapshot.outputs.isEmpty());
        assertEquals(DiagnosticReason.limitedSupport, result.reason());
    }

    @Test
    void aNonCrafterWithConsumersStillReportsItsInputs(){
        //an overdrive projector consumes power but has no production model FactoryScope knows
        Building projector = place(Blocks.overdriveProjector, 4, 4);

        FactorySnapshot snapshot = MindustryFactoryProbe.probe(projector);

        assertEquals(SupportLevel.basic, snapshot.support);
        assertNotNull(snapshot.power);
        assertTrue(snapshot.outputs.isEmpty(), "no production model means no invented rates");
    }

    @Test
    void probingNeverMutatesTheBuilding(){
        Building smelter = supplied(place(Blocks.siliconSmelter, 4, 4));
        int sandBefore = smelter.items.get(Items.sand);
        int coalBefore = smelter.items.get(Items.coal);
        float progressBefore = smelter.progress();

        for(int i = 0; i < 10; i++){
            MindustryFactoryProbe.probe(smelter);
        }

        assertEquals(sandBefore, smelter.items.get(Items.sand), "the probe must not consume inputs");
        assertEquals(coalBefore, smelter.items.get(Items.coal));
        assertEquals(progressBefore, smelter.progress(), TOLERANCE, "the probe must not advance production");
    }

    @Test
    void optionalBoostersAreNotTreatedAsShortages(){
        //a supplied smelter with an empty optional slot is still a healthy factory
        Building smelter = supplied(place(Blocks.siliconSmelter, 4, 4));

        FactorySnapshot snapshot = MindustryFactoryProbe.probe(smelter);

        for(ResourceState optional : snapshot.optionalInputs()){
            assertTrue(optional.optional);
        }
        assertEquals(DiagnosticReason.active, FactoryAnalyzer.analyze(snapshot).reason());
    }

    @Test
    void aPartiallyFilledLiquidBufferMatchesTheEfficiencyTheGameComputes(){
        //cross-check for the replicated ConsumeLiquid formula. The reading is deliberately taken while the
        //building still sits at zero efficiency, which is exactly the case where calling
        //Consume.efficiency() directly would have reported a false shortage.
        Building mixer = place(Blocks.cryofluidMixer, 4, 4);
        for(var consume : mixer.block.consumers){
            if(consume instanceof mindustry.world.consumers.ConsumeItems items){
                for(var stack : items.items) mixer.items.add(stack.item, stack.amount * 4);
            }
        }
        if(mixer.power != null) mixer.power.status = 1f;

        float requiredPerSecond = waterInput(MindustryFactoryProbe.probe(mixer)).required;
        mixer.liquids.add(Liquids.water, requiredPerSecond / 60f * 0.5f);

        FactorySnapshot snapshot = MindustryFactoryProbe.probe(mixer);
        float derived = waterInput(snapshot).satisfaction;

        //updateConsumption both computes the efficiency and drains the liquid, so it runs after the probe
        mixer.updateConsumption();

        assertEquals(0.5f, derived, 0.01f);
        assertEquals(mixer.efficiency, derived, 0.01f,
            "the derived satisfaction must agree with the efficiency the game computed");

        DiagnosticResult result = FactoryAnalyzer.analyze(snapshot);
        assertEquals(DiagnosticReason.missingLiquidInput, result.reason());
        assertEquals(Severity.reduced, result.severity());
    }

    @Test
    void theSandboxGamemodeDoesNotExemptFactoriesFromTheirInputs(){
        //infiniteResources only makes construction free; crafters still need their materials
        state.rules.infiniteResources = true;
        Building smelter = place(Blocks.siliconSmelter, 4, 4);
        smelter.power.status = 1f;
        smelter.updateConsumption();

        FactorySnapshot snapshot = MindustryFactoryProbe.probe(smelter);

        assertFalse(snapshot.consumersBypassed);
        assertEquals(DiagnosticReason.missingItemInput, FactoryAnalyzer.analyze(snapshot).reason());
    }

    @Test
    void theTeamCheatRuleBypassesConsumersAndIsReportedAsSuch(){
        //this is the rule Building.cheating() actually reads, and it short-circuits updateConsumption
        state.rules.teams.get(Team.sharded).cheat = true;
        Building smelter = place(Blocks.siliconSmelter, 4, 4);
        smelter.updateConsumption();

        FactorySnapshot snapshot = MindustryFactoryProbe.probe(smelter);

        assertTrue(snapshot.consumersBypassed);
        assertEquals(1f, smelter.efficiency, TOLERANCE, "the game itself runs the crafter at full speed");
        assertEquals(DiagnosticReason.active, FactoryAnalyzer.analyze(snapshot).reason());
        assertEquals(1.5f, snapshot.outputs.get(0).expectedPerSecond, TOLERANCE);
    }

    // ------------------------------------------------------------------ helpers

    private static ResourceState waterInput(FactorySnapshot snapshot){
        return snapshot.inputs.stream()
            .filter(input -> input.kind == ResourceKind.liquid)
            .findFirst()
            .orElseThrow(() -> new AssertionError("expected a liquid input"));
    }

    private static Building place(Block block, int x, int y){
        Tile tile = world.tile(x, y);
        tile.setBlock(block, Team.sharded, 0);
        Building build = tile.build;
        assertNotNull(build, "failed to place " + block.name);
        settle(build);
        return build;
    }

    /**
     * Brings the building to the state the engine would have left it in.
     *
     * <p>Deliberately mirrors {@code Tile.setBlock}, which only registers a building for updates when
     * {@code Block.update} is set: forcing {@code updateConsumption()} on a wall would give it an
     * efficiency the real game never computes, and hide the very case the analyser has to handle.
     */
    private static void settle(Building build){
        if(build.block.update) build.updateConsumption();
    }

    /**
     * Satisfies every requirement of the building: items in the buffer and a grid delivering in full.
     *
     * <p>The power status is set directly because no power graph runs in these tests; it is the same
     * value {@code PowerGraph.distributePower} would have written for a grid with surplus.
     */
    private static Building supplied(Building build){
        for(var consume : build.block.consumers){
            if(consume instanceof mindustry.world.consumers.ConsumeItems items){
                for(var stack : items.items){
                    build.items.add(stack.item, stack.amount * 4);
                }
            }
        }
        if(build.power != null) build.power.status = 1f;
        settle(build);
        return build;
    }
}
