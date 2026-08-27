package factoryscope.probe;

import arc.struct.*;
import factoryscope.analysis.*;
import factoryscope.model.*;
import factoryscope.ui.*;
import mindustry.ctype.*;
import mindustry.type.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.world.*;
import mindustry.world.blocks.environment.*;
import mindustry.world.blocks.production.*;
import org.junit.jupiter.api.*;

import java.util.*;

import static mindustry.Vars.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Runs the whole pipeline over every placeable block the game ships, on both planets.
 *
 * <p>The named tests cover the interesting cases; this one covers the unglamorous requirement that
 * nothing in vanilla content makes FactoryScope throw, produce a number the panel cannot render, or
 * state a cause it has not established. Drills, pumps, generators, separators, unit factories,
 * reconstructors and the Erekir heat chain are all in here without being named individually.
 */
class VanillaSweepTest{
    /** Anything larger than this is not a placeable block in vanilla. */
    private static final int WORLD_SIZE = 24;

    private static Seq<Block> placeable;

    @BeforeAll
    static void boot(){
        HeadlessGame.start();
        placeable = content.blocks().select(block ->
            !(block instanceof Floor) && !(block instanceof OverlayFloor)
                && !(block instanceof StaticWall) && !(block instanceof AirBlock)
                && block.size > 0 && block.size <= 5);
        assertTrue(placeable.size > 200, "expected the vanilla block set, found " + placeable.size);
    }

    @Test
    void everyPlaceableBlockSurvivesInspection(){
        List<String> problems = new ArrayList<>();
        int inspected = 0;

        for(Block block : placeable){
            Building build = tryPlace(block, problems);
            if(build == null) continue;
            inspected++;

            try{
                FactorySnapshot snapshot = MindustryFactoryProbe.probe(build);
                DiagnosticResult result = FactoryAnalyzer.analyze(snapshot);
                checkInvariants(block, snapshot, result, problems);
            }catch(Exception e){
                problems.add(block.name + ": threw " + e);
            }
        }

        assertTrue(inspected > 150, "only " + inspected + " blocks could be placed");
        assertEquals(List.of(), problems, "blocks FactoryScope could not handle");
    }

    @Test
    void inspectingNeverChangesTheGameState(){
        //the whole mod is observational; this is the guard that keeps it that way across all content
        List<String> problems = new ArrayList<>();

        for(Block block : placeable){
            Building build = tryPlace(block, problems);
            if(build == null) continue;

            String before = mutableState(build);
            for(int i = 0; i < 3; i++){
                MindustryFactoryProbe.probe(build);
            }
            String after = mutableState(build);

            if(!before.equals(after)){
                problems.add(block.name + ": state changed from [" + before + "] to [" + after + "]");
            }
        }

        assertEquals(List.of(), problems, "blocks whose state FactoryScope disturbed");
    }

    /** Everything an observational tool must leave alone, rendered as text so a diff is readable. */
    private static String mutableState(Building build){
        StringBuilder text = new StringBuilder();
        text.append("enabled=").append(build.enabled)
            .append(" efficiency=").append(build.efficiency)
            .append(" progress=").append(build.progress())
            .append(" totalProgress=").append(build.totalProgress())
            .append(" health=").append(build.health);
        if(build.items != null){
            for(Item item : content.items()) text.append(' ').append(item.name).append('=').append(build.items.get(item));
        }
        if(build.liquids != null){
            for(Liquid liquid : content.liquids()) text.append(' ').append(liquid.name).append('=').append(build.liquids.get(liquid));
        }
        if(build.power != null){
            text.append(" power=").append(build.power.status).append(" links=").append(build.power.links.size);
        }
        return text.toString();
    }

    @Test
    void everyCrafterExposesAUsableProductionModel(){
        List<String> problems = new ArrayList<>();
        int crafters = 0;

        for(Block block : placeable){
            if(!(block instanceof GenericCrafter crafter)) continue;
            Building build = tryPlace(block, problems);
            if(build == null) continue;
            crafters++;

            FactorySnapshot snapshot = MindustryFactoryProbe.probe(build);
            if(snapshot.support != SupportLevel.full){
                problems.add(block.name + ": a GenericCrafter should be fully supported");
            }
            if(snapshot.craftTimeSeconds <= 0f){
                problems.add(block.name + ": no cycle time");
            }
            boolean declaresOutput = crafter.outputItems != null || crafter.outputLiquids != null;
            if(declaresOutput && snapshot.outputs.isEmpty()){
                problems.add(block.name + ": declared outputs were not read");
            }
            for(OutputState output : snapshot.outputs){
                if(output.theoreticalPerSecond <= 0f){
                    problems.add(block.name + ": " + output.name + " has no theoretical rate");
                }
            }
        }

        assertTrue(crafters > 20, "expected the vanilla crafter set, found " + crafters);
        assertEquals(List.of(), problems, "crafters with an unusable production model");
    }

    private static void checkInvariants(Block block, FactorySnapshot snapshot,
                                        DiagnosticResult result, List<String> problems){
        for(OutputState output : snapshot.outputs){
            requireRate(block, output.name + " theoretical", output.theoreticalPerSecond, problems);
            requireRate(block, output.name + " expected", output.expectedPerSecond, problems);

            if(snapshot.efficiency <= 0f && output.expectedPerSecond > 0f){
                problems.add(block.name + ": produces " + output.expectedPerSecond
                    + " while stopped at zero efficiency");
            }
            //a boost legitimately puts current output above nominal, so only compare when there is none
            boolean boosted = !Float.isNaN(snapshot.craftSpeedMultiplier)
                && snapshot.craftSpeedMultiplier > 1.001f;
            if(!boosted && output.expectedPerSecond > output.theoreticalPerSecond * 1.001f){
                problems.add(block.name + ": expected " + output.expectedPerSecond
                    + " exceeds theoretical " + output.theoreticalPerSecond + " without a boost");
            }
            if(output.contentId == null){
                problems.add(block.name + ": output " + output.name + " has no content id");
            }else if(resolve(output.kind, output.contentId) == null){
                problems.add(block.name + ": output content id " + output.contentId + " does not resolve");
            }
        }

        for(ResourceState input : snapshot.inputs){
            if(input.name == null || input.name.isBlank()){
                problems.add(block.name + ": an input has no name");
            }
            if(!finite(input.satisfaction) || input.satisfaction < 0f || input.satisfaction > 1f){
                problems.add(block.name + ": input " + input.name + " satisfaction " + input.satisfaction);
            }
            if(input.hasAmounts() && !finite(input.required)){
                problems.add(block.name + ": input " + input.name + " has a non-finite requirement");
            }
        }

        if(!(block instanceof GenericCrafter) && !snapshot.outputs.isEmpty()){
            problems.add(block.name + ": invented a production model for a non-crafter");
        }
        if(result.reason() == DiagnosticReason.haltedUnknownCause && result.primary.certain){
            problems.add(block.name + ": stated an unestablished cause as fact");
        }
        if(result.reason() == DiagnosticReason.outputBlocked && snapshot.support != SupportLevel.full){
            problems.add(block.name + ": claimed a blocked output without a verified production model");
        }
        if(snapshot.power != null && !finite(snapshot.power.satisfaction)){
            problems.add(block.name + ": non-finite power satisfaction");
        }
    }

    private static void requireRate(Block block, String what, float value, List<String> problems){
        if(!finite(value) || value < 0f){
            problems.add(block.name + ": " + what + " is " + value);
            return;
        }
        String rendered = Numbers.rate(value);
        if(rendered.contains("?") || rendered.contains("N") || rendered.contains("Inf")){
            problems.add(block.name + ": " + what + " renders as '" + rendered + "'");
        }
    }

    private static Building tryPlace(Block block, List<String> problems){
        HeadlessGame.newWorld(WORLD_SIZE);
        try{
            Tile tile = world.tile(WORLD_SIZE / 2, WORLD_SIZE / 2);
            tile.setBlock(block, Team.sharded, 0);
            Building build = tile.build;
            if(build == null) return null;
            if(block.update) build.updateConsumption();
            return build;
        }catch(Exception e){
            problems.add(block.name + ": could not be placed - " + e);
            return null;
        }
    }

    private static UnlockableContent resolve(ResourceKind kind, String contentId){
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
