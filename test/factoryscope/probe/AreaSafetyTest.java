package factoryscope.probe;

import arc.struct.*;
import factoryscope.area.*;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.world.*;
import org.junit.jupiter.api.*;

import java.util.*;

import static mindustry.Vars.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The two properties an area selection must never lose: it may not show a player anything they could
 * not already see, and it must agree exactly with the footprint rule it claims to implement.
 *
 * <p>These are separated from {@code AreaProbeTest} because they are the tests that would matter most
 * if the spatial query were ever rewritten - a leak here is not a wrong number, it is a cheat.
 */
class AreaSafetyTest{
    @BeforeAll
    static void boot(){
        HeadlessGame.start();
    }

    @BeforeEach
    void freshWorld(){
        HeadlessGame.newWorld(64);
    }

    // ------------------------------------------------------------------ information safety

    @Test
    void anEnemyBuildingInsideTheSelectionIsNeverCollected(){
        Building mine = place(Blocks.siliconSmelter, 10, 10, Team.sharded);
        Building theirs = place(Blocks.siliconSmelter, 14, 10, Team.crux);
        AreaSelection area = AreaSelection.of(0, 0, 40, 40);

        Seq<Building> found = AreaProbe.collect(area, Team.sharded);

        assertTrue(found.contains(mine));
        assertFalse(found.contains(theirs), "an area selection must not survey the enemy");
        assertEquals(1, found.size);
    }

    @Test
    void fogChangesNothingAboutWhatTheCollectorReturns(){
        place(Blocks.siliconSmelter, 10, 10, Team.sharded);
        place(Blocks.siliconSmelter, 14, 10, Team.crux);
        AreaSelection area = AreaSelection.of(0, 0, 40, 40);

        state.rules.fog = false;
        int withoutFog = AreaProbe.collect(area, Team.sharded).size;
        state.rules.fog = true;
        int withFog = AreaProbe.collect(area, Team.sharded).size;

        //the query is rooted at one team's index, so the enemy building is never visited in either
        //case; fog is a second line of defence, not the first
        assertEquals(1, withoutFog);
        assertEquals(1, withFog);
    }

    @Test
    void aScanNeverReportsABuildingOutsideTheViewingTeam(){
        for(int i = 0; i < 4; i++) place(Blocks.siliconSmelter, 8 + i * 4, 20, Team.crux);
        place(Blocks.siliconSmelter, 8, 30, Team.sharded);

        AreaDiagnosticResult result = AreaProbe.scan(AreaSelection.of(0, 0, 60, 60), Team.sharded);

        assertEquals(1, result.summary.analyzed);
        for(AreaEntry entry : result.entries){
            assertEquals(Team.sharded.id, entry.ref.teamId, "a foreign building reached the report");
        }
    }

    @Test
    void derelictBuildingsAreNotCollectedByAPlayerWhoHasNotCapturedThem(){
        //deliberate: the query is per team, and a derelict block belongs to nobody. Under-inclusion is
        //the safe direction, and this pins the behaviour so a future change has to be a decision
        place(Blocks.siliconSmelter, 10, 10, Team.derelict);

        assertEquals(0, AreaProbe.collect(AreaSelection.of(0, 0, 40, 40), Team.sharded).size);
    }

    // ------------------------------------------------------------------ footprint agreement

    /**
     * The query rectangle is grown slightly before the exact tile test is applied, so that a footprint
     * lying exactly on the boundary cannot be lost to floating point. Growing it can only add
     * candidates, never remove them, and the exact test removes the extras - but "can only" is an
     * argument, and this is the check.
     *
     * <p>Every selection edge is swept across a block of each size and compared against a brute-force
     * walk of the world. Any disagreement is an off-by-one in the footprint rule or a candidate the
     * quadtree failed to return.
     */
    @Test
    void collectionAgreesWithBruteForceForEveryBlockSizeAndEveryEdge(){
        //one block of every footprint size the game uses, so both the odd and the even centring
        //offsets are exercised: -(size - 1) / 2 is integer division and is not symmetric
        Block[] blocks = {Blocks.copperWall, Blocks.siliconSmelter, Blocks.surgeSmelter,
            Blocks.impactReactor, Blocks.eruptionDrill};
        List<String> disagreements = new ArrayList<>();

        assertEquals(List.of(1, 2, 3, 4, 5), Seq.with(blocks).map(b -> b.size).list(),
            "the sweep must cover every footprint size");

        for(Block block : blocks){
            HeadlessGame.newWorld(64);
            place(block, 30, 30, Team.sharded);

            for(int edge = 24; edge <= 36; edge++){
                for(AreaSelection area : new AreaSelection[]{
                    AreaSelection.of(10, 28, edge, 32),   //growing from the left
                    AreaSelection.of(edge, 28, 50, 32),   //shrinking from the right
                    AreaSelection.of(28, 10, 32, edge),   //growing from below
                    AreaSelection.of(28, edge, 32, 50)}){ //shrinking from above

                    Set<String> expected = bruteForce(area, Team.sharded);
                    Set<String> actual = new HashSet<>();
                    for(Building build : AreaProbe.collect(area, Team.sharded)){
                        assertTrue(actual.add(build.tile.x + "," + build.tile.y),
                            block.name + " was collected twice for " + area);
                    }

                    if(!expected.equals(actual)){
                        disagreements.add(block.name + " " + area + ": wanted " + expected + " got " + actual);
                    }
                }
            }
        }

        assertEquals(List.of(), disagreements, "the spatial query disagreed with the footprint rule");
    }

    /** Every distinct building whose footprint covers at least one tile of the area, found the slow way. */
    private static Set<String> bruteForce(AreaSelection area, Team team){
        Set<String> found = new HashSet<>();
        for(int x = Math.max(0, area.minX); x <= Math.min(world.width() - 1, area.maxX); x++){
            for(int y = Math.max(0, area.minY); y <= Math.min(world.height() - 1, area.maxY); y++){
                Tile tile = world.tile(x, y);
                if(tile == null || tile.build == null) continue;
                if(tile.build.team != team) continue;
                found.add(tile.build.tile.x + "," + tile.build.tile.y);
            }
        }
        return found;
    }

    // ------------------------------------------------------------------ identity

    @Test
    void aReferenceResolvesToAnIdenticalBlockRebuiltOnTheSameTile(){
        //deliberate, and the reason is a product one: the report line reads "Silicon Smelter at 10,10",
        //and opening it shows live diagnostics for whatever silicon smelter stands at 10,10. Refusing
        //would say "no longer exists" while the player is looking straight at one
        Building original = place(Blocks.siliconSmelter, 10, 10, Team.sharded);
        BuildingRef ref = AreaProbe.refOf(original);

        original.tile.remove();
        Building rebuilt = place(Blocks.siliconSmelter, 10, 10, Team.sharded);

        assertNotSame(original, rebuilt);
        assertSame(rebuilt, AreaProbe.resolve(ref));
    }

    @Test
    void aReferenceNeverResolvesAcrossAWorldChange(){
        Building original = place(Blocks.siliconSmelter, 10, 10, Team.sharded);
        BuildingRef ref = AreaProbe.refOf(original);

        HeadlessGame.newWorld(64);

        assertNull(AreaProbe.resolve(ref), "an empty tile in a new world is not the old building");
    }

    @Test
    void aReferenceIntoATileTheNewWorldDoesNotHaveResolvesToNothing(){
        Building far = place(Blocks.siliconSmelter, 50, 50, Team.sharded);
        BuildingRef ref = AreaProbe.refOf(far);

        HeadlessGame.newWorld(16);

        assertNull(AreaProbe.resolve(ref));
    }

    // ------------------------------------------------------------------ helpers

    private static Building place(Block block, int x, int y, Team team){
        Tile tile = world.tile(x, y);
        tile.setBlock(block, team, 0);
        Building build = tile.build;
        assertNotNull(build, "failed to place " + block.name);
        if(build.block.update) build.updateConsumption();
        return build;
    }
}
