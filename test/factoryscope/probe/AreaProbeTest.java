package factoryscope.probe;

import arc.struct.*;
import factoryscope.analysis.*;
import factoryscope.area.*;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.world.*;
import org.junit.jupiter.api.*;

import static mindustry.Vars.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Area collection against a real headless Mindustry.
 *
 * <p>The spatial query, the footprint rules and the team filtering are all engine behaviour, so they
 * are checked against the engine rather than against a model of it.
 */
class AreaProbeTest{
    @BeforeAll
    static void boot(){
        HeadlessGame.start();
    }

    @BeforeEach
    void freshWorld(){
        HeadlessGame.newWorld(64);
    }

    @Test
    void onlyBuildingsInsideTheAreaAreCollected(){
        Building inside = place(Blocks.siliconSmelter, 10, 10);
        Building outside = place(Blocks.siliconSmelter, 30, 30);

        Seq<Building> found = AreaProbe.collect(AreaSelection.of(5, 5, 15, 15), Team.sharded);

        assertTrue(found.contains(inside));
        assertFalse(found.contains(outside));
        assertEquals(1, found.size);
    }

    @Test
    void aMultiTileBuildingIsCollectedExactlyOnce(){
        //the surge smelter is 3x3, so a selection covering it touches nine tiles that all point at it
        Building smelter = place(Blocks.surgeSmelter, 20, 20);

        Seq<Building> found = AreaProbe.collect(AreaSelection.of(15, 15, 25, 25), Team.sharded);

        assertEquals(1, found.size, "a 3x3 building must not be collected once per occupied tile");
        assertSame(smelter, found.first());
    }

    @Test
    void aMultiTileBuildingIsCollectedWhenTheAreaOnlyClipsItsEdge(){
        Building smelter = place(Blocks.surgeSmelter, 20, 20);
        assertEquals(3, smelter.block.size);

        //the block covers 19..21; an area ending at 19 shares exactly one column with it
        Seq<Building> clipping = AreaProbe.collect(AreaSelection.of(10, 19, 19, 21), Team.sharded);
        assertEquals(1, clipping.size, "a footprint that overlaps by one tile is inside the area");

        //one tile further out and nothing of it is selected
        Seq<Building> missing = AreaProbe.collect(AreaSelection.of(10, 19, 18, 21), Team.sharded);
        assertEquals(0, missing.size, "a footprint that stops short of the area is outside it");
    }

    @Test
    void aSingleTileSelectionFindsTheBuildingStandingOnIt(){
        Building smelter = place(Blocks.siliconSmelter, 12, 8);

        Seq<Building> found = AreaProbe.collect(AreaSelection.of(12, 8, 12, 8), Team.sharded);

        assertEquals(1, found.size);
        assertSame(smelter, found.first());
    }

    @Test
    void anAreaWithNothingInItCollectsNothing(){
        place(Blocks.siliconSmelter, 40, 40);

        assertEquals(0, AreaProbe.collect(AreaSelection.of(2, 2, 6, 6), Team.sharded).size);
    }

    @Test
    void anotherTeamsBuildingsAreNeverCollected(){
        Building mine = place(Blocks.siliconSmelter, 10, 10);
        place(Blocks.siliconSmelter, 14, 10, Team.crux);

        Seq<Building> found = AreaProbe.collect(AreaSelection.of(5, 5, 20, 20), Team.sharded);

        assertEquals(1, found.size, "an area selection must not be a way to survey the enemy");
        assertSame(mine, found.first());
        //and the query is symmetric: neither team can see the other through it
        Seq<Building> theirs = AreaProbe.collect(AreaSelection.of(5, 5, 20, 20), Team.crux);
        assertEquals(1, theirs.size);
        assertNotSame(mine, theirs.first());
    }

    @Test
    void collectingWithNoViewingTeamReturnsNothing(){
        place(Blocks.siliconSmelter, 10, 10);

        assertEquals(0, AreaProbe.collect(AreaSelection.of(5, 5, 20, 20), null).size);
    }

    @Test
    void aSelectionReachingOutsideTheWorldIsClampedRatherThanRejected(){
        Building corner = place(Blocks.siliconSmelter, 1, 1);

        Seq<Building> found = AreaProbe.collect(AreaSelection.of(-40, -40, 4, 4), Team.sharded);

        assertEquals(1, found.size);
        assertSame(corner, found.first());
    }

    @Test
    void aSelectionEntirelyOutsideTheWorldCollectsNothingAndDoesNotThrow(){
        place(Blocks.siliconSmelter, 10, 10);

        assertEquals(0, AreaProbe.collect(AreaSelection.of(-90, -90, -80, -80), Team.sharded).size);
        assertEquals(0, AreaProbe.collect(AreaSelection.of(400, 400, 420, 420), Team.sharded).size);
    }

    @Test
    void collectionSeesBuildingsAddedAndRemovedSinceTheLastQuery(){
        AreaSelection area = AreaSelection.of(5, 5, 20, 20);
        assertEquals(0, AreaProbe.collect(area, Team.sharded).size);

        Building smelter = place(Blocks.siliconSmelter, 10, 10);
        assertEquals(1, AreaProbe.collect(area, Team.sharded).size);

        smelter.tile.remove();
        assertEquals(0, AreaProbe.collect(area, Team.sharded).size, "a refresh must not resurrect a dead building");
    }

    // ------------------------------------------------------------------ scanning

    @Test
    void scanningRunsTheSameDiagnosticEngineAsTheSingleBuildingPanel(){
        Building starved = place(Blocks.siliconSmelter, 10, 10);
        starved.power.status = 1f;
        starved.updateConsumption();

        AreaDiagnosticResult result = AreaProbe.scan(AreaSelection.of(5, 5, 15, 15), Team.sharded);

        assertEquals(1, result.summary.analyzed);
        AreaEntry entry = result.entries.get(0);
        //identical to what the panel would show for the same building
        assertEquals(FactoryAnalyzer.analyze(MindustryFactoryProbe.probe(starved)).reason(),
            entry.result.reason());
        assertEquals(AreaStatus.itemShortage, entry.status);
        assertEquals(Blocks.siliconSmelter.name, entry.ref.blockId);
        assertEquals(10, entry.ref.tileX);
        assertEquals(10, entry.ref.tileY);
    }

    @Test
    void manyStarvedSmeltersCollapseIntoOneIssuePerMissingItem(){
        for(int i = 0; i < 6; i++){
            Building smelter = place(Blocks.siliconSmelter, 10 + i * 2, 10);
            smelter.power.status = 1f;
            smelter.updateConsumption();
        }

        AreaDiagnosticResult result = AreaProbe.scan(AreaSelection.of(5, 5, 30, 15), Team.sharded);

        assertEquals(6, result.summary.analyzed);
        assertEquals(6, result.summary.problems);
        //the vanilla silicon smelter needs sand and coal, and both are absent
        assertEquals(2, result.issues.size());
        for(AreaIssueGroup group : result.issues){
            assertEquals(DiagnosticReason.missingItemInput, group.issue.reason);
            assertEquals(6, group.buildingCount());
        }
        Seq<String> ids = new Seq<>();
        for(AreaIssueGroup group : result.issues) ids.add(group.issue.resource.id);
        assertTrue(ids.contains(Items.sand.name));
        assertTrue(ids.contains(Items.coal.name));
    }

    @Test
    void anAreaOfWallsReportsLimitedDiagnosticsRatherThanInventedFaults(){
        place(Blocks.titaniumWall, 10, 10);
        place(Blocks.titaniumWall, 12, 10);

        AreaDiagnosticResult result = AreaProbe.scan(AreaSelection.of(5, 5, 20, 20), Team.sharded);

        assertEquals(2, result.summary.analyzed);
        assertEquals(0, result.summary.production);
        assertEquals(0, result.summary.problems);
        assertEquals(2, result.summary.byStatus.get(AreaStatus.limitedDiagnostics));
        assertEquals(0, result.issues.size());
    }

    @Test
    void anEmptyAreaScansToAnEmptyReport(){
        AreaDiagnosticResult result = AreaProbe.scan(AreaSelection.of(5, 5, 20, 20), Team.sharded);

        assertTrue(result.empty());
        assertEquals(0, result.summary.selected);
    }

    @Test
    void scanningDoesNotAlterTheWorld(){
        Building smelter = place(Blocks.siliconSmelter, 10, 10);
        float efficiency = smelter.efficiency;
        boolean enabled = smelter.enabled;
        int items = smelter.items == null ? 0 : smelter.items.total();

        AreaProbe.scan(AreaSelection.of(5, 5, 20, 20), Team.sharded);

        assertEquals(efficiency, smelter.efficiency);
        assertEquals(enabled, smelter.enabled);
        assertEquals(items, smelter.items == null ? 0 : smelter.items.total());
        assertTrue(smelter.isValid());
    }

    // ------------------------------------------------------------------ identity

    @Test
    void aReferenceResolvesBackToTheBuildingItWasTakenFrom(){
        Building smelter = place(Blocks.siliconSmelter, 10, 10);

        assertSame(smelter, AreaProbe.resolve(AreaProbe.refOf(smelter)));
    }

    @Test
    void aReferenceToADestroyedBuildingResolvesToNothing(){
        Building smelter = place(Blocks.siliconSmelter, 10, 10);
        BuildingRef ref = AreaProbe.refOf(smelter);

        smelter.tile.remove();

        assertNull(AreaProbe.resolve(ref));
    }

    @Test
    void aReferenceDoesNotResolveToADifferentBlockBuiltOnTheSameTile(){
        Building smelter = place(Blocks.siliconSmelter, 10, 10);
        BuildingRef ref = AreaProbe.refOf(smelter);

        smelter.tile.remove();
        Building replacement = place(Blocks.graphitePress, 10, 10);

        assertNull(AreaProbe.resolve(ref), "navigating to whatever now stands there would be quietly wrong");
        assertNotNull(replacement);
    }

    @Test
    void aReferenceDoesNotResolveAfterTheBuildingChangesTeam(){
        Building smelter = place(Blocks.siliconSmelter, 10, 10);
        BuildingRef ref = AreaProbe.refOf(smelter);

        smelter.changeTeam(Team.crux);

        assertNull(AreaProbe.resolve(ref));
    }

    // ------------------------------------------------------------------ helpers

    private static Building place(Block block, int x, int y){
        return place(block, x, y, Team.sharded);
    }

    private static Building place(Block block, int x, int y, Team team){
        Tile tile = world.tile(x, y);
        tile.setBlock(block, team, 0);
        Building build = tile.build;
        assertNotNull(build, "failed to place " + block.name);
        if(build.block.update) build.updateConsumption();
        return build;
    }
}
