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

/** FactoryScope against the block shapes other mods actually ship. */
class ModCompatibilityTest{
    private static final float TOLERANCE = 0.001f;

    @BeforeAll
    static void boot(){
        HeadlessGame.start();
    }

    @BeforeEach
    void freshWorld(){
        HeadlessGame.newWorld(16);
        ModdedBlocks.ConsumeMystery.satisfaction = 1f;
    }

    @Test
    void aConventionalModdedCrafterGetsFullDiagnostics(){
        Building crafter = place(ModdedBlocks.conventional);

        FactorySnapshot snapshot = MindustryFactoryProbe.probe(crafter);

        assertEquals(SupportLevel.full, snapshot.support);
        assertEquals(1f, snapshot.craftTimeSeconds, TOLERANCE);
        assertEquals(1, snapshot.outputs.size());
        assertEquals(Items.graphite.localizedName, snapshot.outputs.get(0).name);
        assertEquals(2f, snapshot.outputs.get(0).theoreticalPerSecond, TOLERANCE, "2 graphite per 60-tick cycle");
        assertNotNull(snapshot.power);

        assertEquals(DiagnosticReason.missingItemInput, FactoryAnalyzer.analyze(snapshot).reason());
    }

    @Test
    void aConventionalModdedCrafterRunsWhenSupplied(){
        Building crafter = place(ModdedBlocks.conventional);
        crafter.items.add(Items.copper, 10);
        crafter.items.add(Items.lead, 10);
        crafter.power.status = 1f;
        crafter.updateConsumption();

        FactorySnapshot snapshot = MindustryFactoryProbe.probe(crafter);

        assertEquals(DiagnosticReason.active, FactoryAnalyzer.analyze(snapshot).reason());
        assertEquals(2f, snapshot.outputs.get(0).expectedPerSecond, TOLERANCE);
    }

    @Test
    void anEmptyBoostSlotDoesNotStarveTheFactory(){
        Building crafter = place(ModdedBlocks.boosted);
        crafter.items.add(Items.sand, 10);
        crafter.updateConsumption();

        FactorySnapshot snapshot = MindustryFactoryProbe.probe(crafter);

        assertFalse(snapshot.optionalInputs().isEmpty(), "the booster should be listed as optional");
        for(ResourceState optional : snapshot.optionalInputs()){
            assertTrue(optional.optional);
        }
        assertEquals(1f, crafter.efficiency, TOLERANCE, "the game runs it at full speed without the boost");
        assertEquals(DiagnosticReason.active, FactoryAnalyzer.analyze(snapshot).reason(),
            "an unmet boost is an opportunity, never a shortage");
    }

    @Test
    void anUnknownConsumerIsReportedWithoutCrashingOrAsserting(){
        ModdedBlocks.ConsumeMystery.satisfaction = 0f;
        Building crafter = place(ModdedBlocks.exotic);
        crafter.items.add(Items.copper, 10);
        crafter.updateConsumption();

        FactorySnapshot snapshot = MindustryFactoryProbe.probe(crafter);
        DiagnosticResult result = FactoryAnalyzer.analyze(snapshot);

        ResourceState mystery = snapshot.inputs.stream()
            .filter(input -> input.kind == ResourceKind.other)
            .findFirst()
            .orElseThrow(() -> new AssertionError("the unknown consumer should still be listed"));

        assertFalse(mystery.recognised);
        assertEquals(DiagnosticReason.otherConsumerLimited, result.reason());
        assertFalse(result.primary.certain,
            "a reading taken while the building is stopped must be hedged, not asserted");
    }

    @Test
    void anUnknownConsumerThatIsSatisfiedNeverBlamesAnything(){
        Building crafter = place(ModdedBlocks.exotic);
        crafter.items.add(Items.copper, 10);
        crafter.updateConsumption();

        FactorySnapshot snapshot = MindustryFactoryProbe.probe(crafter);

        assertEquals(1f, crafter.efficiency, TOLERANCE);
        assertEquals(DiagnosticReason.active, FactoryAnalyzer.analyze(snapshot).reason());
    }

    @Test
    void anUnusualBuildingTypeGetsHedgedDiagnosticsRatherThanGuesses(){
        //refuses to consume, but is not a GenericCrafter, so "output blocked" would be an invention
        Building odd = place(ModdedBlocks.oddBuilding);
        odd.items.add(Items.coal, 10);
        odd.updateConsumption();

        FactorySnapshot snapshot = MindustryFactoryProbe.probe(odd);
        DiagnosticResult result = FactoryAnalyzer.analyze(snapshot);

        assertEquals(SupportLevel.basic, snapshot.support);
        assertFalse(snapshot.shouldConsume);
        assertTrue(snapshot.outputs.isEmpty(), "no production model means no invented rates");
        assertEquals(DiagnosticReason.haltedUnknownCause, result.reason());
        assertFalse(result.primary.certain);
    }

    private static Building place(Block block){
        Tile tile = world.tile(6, 6);
        tile.setBlock(block, Team.sharded, 0);
        Building build = tile.build;
        assertNotNull(build, "failed to place " + block.name);
        if(block.update) build.updateConsumption();
        return build;
    }
}
