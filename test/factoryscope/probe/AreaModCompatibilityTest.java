package factoryscope.probe;

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
 * Area aggregation over the block shapes other mods actually ship.
 *
 * <p>An area is where a mod's odd block is most likely to reach FactoryScope: the player selects a
 * region and everything in it is analysed, wanted or not. Nothing here may throw, and nothing may be
 * accused of a fault it does not have.
 */
class AreaModCompatibilityTest{
    @BeforeAll
    static void boot(){
        HeadlessGame.start();
    }

    @BeforeEach
    void freshWorld(){
        HeadlessGame.newWorld(64);
        ModdedBlocks.ConsumeMystery.satisfaction = 1f;
    }

    @Test
    void anAreaOfMixedVanillaAndModdedBlocksIsCountedCorrectly(){
        //a vanilla crafter, a conventional modded crafter, one with an optional boost, one with a
        //consumer type FactoryScope has never seen, and a block with no production model at all
        supplied(place(Blocks.siliconSmelter, 10, 10));
        supplied(place(ModdedBlocks.conventional, 14, 10));
        supplied(place(ModdedBlocks.boosted, 18, 10));
        supplied(place(ModdedBlocks.exotic, 22, 10));
        place(ModdedBlocks.oddBuilding, 26, 10);
        place(Blocks.titaniumWall, 30, 10);

        AreaDiagnosticResult result = AreaProbe.scan(AreaSelection.of(5, 5, 40, 15), Team.sharded);

        assertEquals(6, result.summary.selected);
        assertEquals(6, result.summary.analyzed);
        assertEquals(4, result.summary.production, "four crafters have a full production model");
        assertEquals(4, result.summary.operating, "every supplied crafter runs");
        assertEquals(1, result.summary.byStatus.get(AreaStatus.limitedDiagnostics));
        assertEquals(1, result.summary.byStatus.get(AreaStatus.notConsuming));
        assertEquals(0, result.summary.problems);
    }

    @Test
    void anUnrecognisedConsumerLimitingProductionIsGroupedByItsOwnIdentity(){
        ModdedBlocks.ConsumeMystery.satisfaction = 0f;
        supplied(place(ModdedBlocks.exotic, 10, 10));
        supplied(place(ModdedBlocks.exotic, 14, 10));

        AreaDiagnosticResult result = AreaProbe.scan(AreaSelection.of(5, 5, 20, 20), Team.sharded);

        assertEquals(2, result.summary.analyzed);
        assertEquals(2, result.summary.problems);
        assertEquals(1, result.issues.size(), "the same unknown requirement is one issue, not two");
        AreaIssueGroup group = result.issues.get(0);
        assertEquals(DiagnosticReason.otherConsumerLimited, group.issue.reason);
        assertEquals(2, group.buildingCount());
    }

    @Test
    void aModdedShortageGroupsSeparatelyFromAVanillaOneEvenAtTheSameReason(){
        //the modded crafter wants copper and lead, the vanilla one sand and coal; four distinct items
        place(Blocks.siliconSmelter, 10, 10).power.status = 1f;
        place(ModdedBlocks.conventional, 14, 10).power.status = 1f;
        for(Building build : new Building[]{world.tile(10, 10).build, world.tile(14, 10).build}){
            build.updateConsumption();
        }

        AreaDiagnosticResult result = AreaProbe.scan(AreaSelection.of(5, 5, 20, 20), Team.sharded);

        assertEquals(4, result.issues.size(), "four different missing items are four different issues");
        for(AreaIssueGroup group : result.issues){
            assertEquals(DiagnosticReason.missingItemInput, group.issue.reason);
            assertEquals(1, group.buildingCount());
            assertNotNull(group.issue.resource.id, "a real item always has content identity");
        }
    }

    @Test
    void aBoostConsumerNeverBecomesAnAreaIssue(){
        //the boost item is absent on purpose; an optional consumer must never stop or accuse anything
        Building boosted = place(ModdedBlocks.boosted, 10, 10);
        boosted.items.add(Items.sand, 20);
        boosted.updateConsumption();

        AreaDiagnosticResult result = AreaProbe.scan(AreaSelection.of(5, 5, 20, 20), Team.sharded);

        assertEquals(1, result.summary.operating);
        assertEquals(0, result.summary.problems);
        assertEquals(0, result.issues.size());
    }

    // ------------------------------------------------------------------ helpers

    private static Building place(Block block, int x, int y){
        Tile tile = world.tile(x, y);
        tile.setBlock(block, Team.sharded, 0);
        Building build = tile.build;
        assertNotNull(build, "failed to place " + block.name);
        if(build.block.update) build.updateConsumption();
        return build;
    }

    private static Building supplied(Building build){
        for(var consume : build.block.consumers){
            if(consume instanceof mindustry.world.consumers.ConsumeItems items){
                for(var stack : items.items) build.items.add(stack.item, stack.amount * 4);
            }
        }
        if(build.power != null) build.power.status = 1f;
        if(build.block.update) build.updateConsumption();
        return build;
    }
}
