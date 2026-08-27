package factoryscope.probe;

import arc.struct.*;
import factoryscope.analysis.*;
import factoryscope.model.*;
import factoryscope.ui.*;
import mindustry.ctype.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.world.*;
import mindustry.world.blocks.production.*;
import org.junit.jupiter.api.*;

import java.util.*;

import static mindustry.Vars.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Runs the whole pipeline over every crafting block the game ships, on both planets.
 *
 * <p>Individual tests cover the interesting cases; this one covers the unglamorous requirement that
 * nothing in the vanilla content set makes FactoryScope throw, produce a nonsensical number, or print a
 * value the panel cannot render. Heat crafters, attribute crafters, multi-output liquid crafters and
 * blocks with several consumers of the same kind are all in here without being named.
 */
class AllCraftersSweepTest{

    @BeforeAll
    static void boot(){
        HeadlessGame.start();
    }

    @Test
    void everyVanillaCrafterCanBeAnalysedAndRendered(){
        Seq<Block> crafters = content.blocks().select(block -> block instanceof GenericCrafter);
        assertTrue(crafters.size > 20, "expected the vanilla crafter set, found " + crafters.size);

        List<String> problems = new ArrayList<>();

        for(Block block : crafters){
            HeadlessGame.newWorld(block.size + 8);
            Tile tile = world.tile(block.size + 2, block.size + 2);
            tile.setBlock(block, Team.sharded, 0);
            Building build = tile.build;
            if(build == null){
                problems.add(block.name + ": could not be placed");
                continue;
            }

            try{
                build.updateConsumption();
                FactorySnapshot snapshot = MindustryFactoryProbe.probe(build);
                DiagnosticResult result = FactoryAnalyzer.analyze(snapshot);

                assertNotNull(result.primary, block.name);
                checkSnapshot(block, snapshot, problems);
            }catch(Exception e){
                problems.add(block.name + ": threw " + e);
            }
        }

        assertEquals(List.of(), problems, "blocks FactoryScope could not handle");
    }

    private static void checkSnapshot(Block block, FactorySnapshot snapshot, List<String> problems){
        if(snapshot.support != SupportLevel.full){
            problems.add(block.name + ": a GenericCrafter should be fully supported");
        }
        if(snapshot.craftTimeSeconds <= 0f){
            problems.add(block.name + ": no cycle time");
        }

        for(ResourceState input : snapshot.inputs){
            if(input.name == null || input.name.isBlank()){
                problems.add(block.name + ": an input has no name");
            }
            if(!finite(input.satisfaction)){
                problems.add(block.name + ": input " + input.name + " has satisfaction " + input.satisfaction);
            }
            if(input.hasAmounts() && !finite(input.required)){
                problems.add(block.name + ": input " + input.name + " has a non-finite requirement");
            }
            if(input.contentId != null && input.kind != ResourceKind.other){
                assertNotNull(resolve(input.kind, input.contentId),
                    block.name + ": input content id " + input.contentId + " does not resolve");
            }
        }

        for(OutputState output : snapshot.outputs){
            if(!finite(output.theoreticalPerSecond) || !finite(output.expectedPerSecond)){
                problems.add(block.name + ": output " + output.name + " has a non-finite rate");
            }
            if(output.theoreticalPerSecond <= 0f){
                problems.add(block.name + ": output " + output.name + " has no theoretical rate");
            }
            //what the panel would actually print must never be a placeholder
            String rendered = Numbers.rate(output.theoreticalPerSecond);
            if(rendered.contains("?") || rendered.contains("N")){
                problems.add(block.name + ": output rate renders as '" + rendered + "'");
            }
            assertNotNull(resolve(output.kind, output.contentId),
                block.name + ": output content id " + output.contentId + " does not resolve");
        }

        //a crafter that produces nothing at all would mean the production model failed to read
        if(snapshot.outputs.isEmpty() && (((GenericCrafter)block).outputItems != null
            || ((GenericCrafter)block).outputLiquids != null)){
            problems.add(block.name + ": declared outputs were not read");
        }
    }

    private static UnlockableContent resolve(ResourceKind kind, String contentId){
        if(contentId == null) return null;
        return switch(kind){
            case item -> content.item(contentId);
            case liquid -> content.liquid(contentId);
            default -> null;
        };
    }

    private static boolean finite(float value){
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }
}
