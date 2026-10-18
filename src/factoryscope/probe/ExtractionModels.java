package factoryscope.probe;

import arc.math.geom.*;
import arc.struct.*;
import factoryscope.analysis.*;
import factoryscope.model.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.production.*;
import mindustry.world.consumers.*;

import java.util.*;

import static factoryscope.probe.ProductionModels.*;
import static mindustry.Vars.world;

/**
 * Drills, wall crushers and pumps: blocks whose output depends on what they stand on or face.
 *
 * <p>The current rate is always the one the engine computed for its own status bar
 * ({@code lastDrillSpeed}, {@code lastEfficiency}, {@code lastPump}), so warm-up, boosts and the drill's
 * squared efficiency curve are included as the game applies them. The nominal rate - what the same block
 * would produce here at 100% efficiency without a booster - is derived from the block's declared timing.
 */
final class ExtractionModels{
    private ExtractionModels(){
    }

    // ------------------------------------------------------------------ Drill, BurstDrill

    static void drill(Context c, Drill.DrillBuild build){
        Drill block = (Drill)build.block;
        boolean burst = build instanceof BurstDrill.BurstDrillBuild;
        FactorySnapshot.Builder s = c.snapshot.family(ProductionFamily.drill);
        s.fact(Fact.of("fact.drill-tier", Fact.Format.count, block.tier));

        Item item = build.dominantItem;
        if(item == null){
            //DrillBuild.shouldConsume() is false without a dominant item; say why there is none
            List<ResourceRef> tooHard = tooHardOres(build, block);
            s.gate(tooHard.isEmpty() ? ProductionGate.noResource : ProductionGate.resourceTooHard, tooHard);
            return;
        }

        int count = build.dominantItems;
        float delay = block.getDrillTime(item);
        float nominal = delay > 0f ? count * ProductionRates.TICKS_PER_SECOND * c.timeScale / delay : 0f;
        //the value DrillBuild's own speed bar shows: lastDrillSpeed * 60 * timeScale
        float current = build.efficiency > 0f ? build.lastDrillSpeed * ProductionRates.TICKS_PER_SECOND * c.timeScale : 0f;
        if(!finite(current) || current < 0f) current = 0f;

        int stored = build.items == null ? 0 : build.items.total();
        //Drill stops once its buffer is full; BurstDrill needs room for a whole burst
        boolean full = burst ? stored > block.itemCapacity - count : stored >= block.itemCapacity;
        //no block speed multiplier: warm-up and the booster are shown as their own details instead
        s.output(new OutputState(ResourceKind.item, item.localizedName, item.name, nominal, current,
            stored, block.itemCapacity, full));

        s.fact(Fact.resource("fact.ore", itemRef(item)));
        s.fact(Fact.amountOf("fact.ore-tiles", count, block.size * block.size));
        if(hasLiquidBooster(block) && block.liquidBoostIntensity != 1f){
            //Drill's warm-up chases the boosted speed, so a boost acts twice; BurstDrill applies it once
            float boost = burst ? block.liquidBoostIntensity : block.liquidBoostIntensity * block.liquidBoostIntensity;
            s.fact(Fact.of("fact.max-boost", Fact.Format.multiplier, boost));
        }
        if(!burst && build.efficiency > 0f){
            float target = arc.math.Mathf.lerp(1f, block.liquidBoostIntensity, build.optionalEfficiency) * build.efficiency;
            if(target > 0f && build.warmup < target * 0.99f){
                s.fact(Fact.of("fact.warmup", Fact.Format.percent, Math.min(build.warmup / target, 1f)));
            }
        }

        if(full && !c.shouldConsume) s.outputBufferFull(true).gate(ProductionGate.outputFull, List.of(itemRef(item)));
    }

    /** Ores under the drill that it cannot mine because they are too hard for its tier or excluded from it. */
    private static List<ResourceRef> tooHardOres(Drill.DrillBuild build, Drill block){
        LinkedHashSet<ResourceRef> result = new LinkedHashSet<>();
        for(Tile tile : build.tile.getLinkedTilesAs(block, new Seq<>())){
            Item drop = tile.drop();
            if(drop != null && !block.canMine(tile)) result.add(itemRef(drop));
        }
        return new ArrayList<>(result);
    }

    private static boolean hasLiquidBooster(mindustry.world.Block block){
        return block.findConsumer(consume -> consume instanceof ConsumeLiquidBase && consume.booster) != null;
    }

    // ------------------------------------------------------------------ BeamDrill

    static void beamDrill(Context c, BeamDrill.BeamDrillBuild build){
        BeamDrill block = (BeamDrill)build.block;
        FactorySnapshot.Builder s = c.snapshot.family(ProductionFamily.beamDrill);
        s.fact(Fact.of("fact.drill-tier", Fact.Format.count, block.tier));
        //the lasers and the walls they hit are only resolved by the building's own first update
        if(build.lasers == null || build.lasers.length == 0 || build.lasers[0] == null) return;

        LinkedHashMap<Item, Integer> facing = new LinkedHashMap<>();
        for(Tile tile : build.facing){
            Item drop = tile == null ? null : tile.wallDrop();
            if(drop != null) facing.merge(drop, 1, Integer::sum);
        }
        if(build.facingAmount <= 0 || facing.isEmpty()){
            List<ResourceRef> tooHard = tooHardWalls(build, block);
            s.gate(tooHard.isEmpty() ? ProductionGate.noResource : ProductionGate.resourceTooHard, tooHard);
            return;
        }

        //BeamDrillBuild times every laser with the drill time of lastItem, which is null for mixed walls
        float delay = block.getDrillTime(build.lastItem);
        //lastDrillSpeed already includes the time scale, the booster and efficiency
        float total = build.efficiency > 0f ? build.lastDrillSpeed * ProductionRates.TICKS_PER_SECOND : 0f;
        if(!finite(total) || total < 0f) total = 0f;

        int stored = build.items == null ? 0 : build.items.total();
        boolean full = stored >= block.itemCapacity;
        List<ResourceRef> produced = new ArrayList<>();
        for(var entry : facing.entrySet()){
            Item item = entry.getKey();
            int lasers = entry.getValue();
            float nominal = delay > 0f ? lasers * ProductionRates.TICKS_PER_SECOND * c.timeScale / delay : 0f;
            s.output(new OutputState(ResourceKind.item, item.localizedName, item.name, nominal,
                total * lasers / build.facingAmount, stored, block.itemCapacity, full));
            produced.add(itemRef(item));
        }
        s.fact(Fact.amountOf("fact.ore-walls", build.facingAmount, block.size));
        if(hasLiquidBooster(block) && block.optionalBoostIntensity != 1f){
            s.fact(Fact.of("fact.max-boost", Fact.Format.multiplier, block.optionalBoostIntensity));
        }

        if(full && !c.shouldConsume) s.outputBufferFull(true).gate(ProductionGate.outputFull, produced);
    }

    /** Walls the lasers hit that this drill cannot mine; mirrors BeamDrillBuild.updateFacing(). */
    private static List<ResourceRef> tooHardWalls(BeamDrill.BeamDrillBuild build, BeamDrill block){
        LinkedHashSet<ResourceRef> result = new LinkedHashSet<>();
        int dx = Geometry.d4x(build.rotation), dy = Geometry.d4y(build.rotation);
        for(Point2 laser : build.lasers){
            if(laser == null) continue;
            for(int i = 0; i < block.range; i++){
                Tile other = world.tile(laser.x + dx * i, laser.y + dy * i);
                if(other == null || !other.solid()) continue;
                Item drop = other.wallDrop();
                boolean mineable = drop != null && drop.hardness <= block.tier
                    && (block.blockedItems == null || !block.blockedItems.contains(drop));
                if(drop != null && !mineable) result.add(itemRef(drop));
                break;
            }
        }
        return new ArrayList<>(result);
    }

    // ------------------------------------------------------------------ WallCrafter

    static void wallCrafter(Context c, WallCrafter.WallCrafterBuild build){
        WallCrafter block = (WallCrafter)build.block;
        FactorySnapshot.Builder s = c.snapshot.family(ProductionFamily.wallCrafter);
        if(block.output == null) return;

        float yield = wallYield(build, block);
        if(!(yield > 0f)){
            s.gate(ProductionGate.noResource);
            return;
        }

        float nominal = block.drillTime > 0f ? yield * ProductionRates.TICKS_PER_SECOND * c.timeScale / block.drillTime : 0f;
        //the value WallCrafter's own speed bar shows: lastEfficiency * 60 / drillTime
        float current = build.efficiency > 0f && block.drillTime > 0f
            ? build.lastEfficiency * ProductionRates.TICKS_PER_SECOND / block.drillTime : 0f;
        if(!finite(current) || current < 0f) current = 0f;

        int stored = build.items == null ? 0 : build.items.get(block.output);
        boolean full = stored >= block.itemCapacity;
        s.output(new OutputState(ResourceKind.item, block.output.localizedName, block.output.name, nominal, current,
            stored, block.itemCapacity, full));
        s.fact(Fact.of("fact.wall-yield", Fact.Format.percent, yield));
        if(block.itemConsumer != null && block.itemBoostIntensity != 1f){
            s.fact(Fact.of("fact.max-boost", Fact.Format.multiplier, block.itemBoostIntensity));
        }else if(block.hasLiquidBooster && block.liquidBoostIntensity != 1f){
            s.fact(Fact.of("fact.max-boost", Fact.Format.multiplier, block.liquidBoostIntensity));
        }

        if(full && !c.shouldConsume) s.outputBufferFull(true).gate(ProductionGate.outputFull, List.of(itemRef(block.output)));
    }

    /**
     * The summed attribute of the solid tiles along the block's front edge, which is the efficiency
     * {@code WallCrafter.getEfficiency()} computes before any booster is applied. That method is not
     * visible outside the engine's package, so its geometry is repeated here; it only reads tiles.
     */
    static float wallYield(WallCrafter.WallCrafterBuild build, WallCrafter block){
        float yield = 0f;
        int size = block.size;
        int cornerX = build.tile.x - (size - 1) / 2, cornerY = build.tile.y - (size - 1) / 2;
        for(int i = 0; i < size; i++){
            int rx, ry;
            switch(build.rotation){
                case 0 -> { rx = cornerX + size; ry = cornerY + i; }
                case 1 -> { rx = cornerX + i; ry = cornerY + size; }
                case 2 -> { rx = cornerX - 1; ry = cornerY + i; }
                default -> { rx = cornerX + i; ry = cornerY - 1; }
            }
            Tile other = world.tile(rx, ry);
            if(other != null && other.solid()) yield += other.block().attributes.get(block.attribute);
        }
        return yield;
    }

    // ------------------------------------------------------------------ Pump

    static void pump(Context c, Pump.PumpBuild build){
        Pump block = (Pump)build.block;
        FactorySnapshot.Builder s = c.snapshot.family(ProductionFamily.pump);
        Liquid liquid = build.liquidDrop;
        if(liquid == null){
            s.gate(ProductionGate.noResource);
            return;
        }

        float stored = build.liquids == null ? 0f : build.liquids.get(liquid);
        float capacity = block.liquidCapacity;
        float nominal = build.amount * block.pumpAmount * ProductionRates.TICKS_PER_SECOND * c.timeScale;
        //PumpBuild.updateTile(): min(liquidCapacity - stored, amount * pumpAmount * edelta()) per frame
        float perFrame = Math.min(Math.max(capacity - stored, 0f),
            build.amount * block.pumpAmount * build.efficiency * c.frameTicks * c.timeScale);
        float current = build.efficiency > 0f ? perFrame / c.frameTicks * ProductionRates.TICKS_PER_SECOND : 0f;
        if(!finite(current) || current < 0f) current = 0f;
        boolean full = stored >= capacity - 0.01f;

        s.output(new OutputState(ResourceKind.liquid, liquid.localizedName, liquid.name, nominal, current,
            stored, capacity, full));
        s.fact(Fact.of("fact.tile-yield", Fact.Format.multiplier, build.amount));

        if(full && !c.shouldConsume) s.outputBufferFull(true).gate(ProductionGate.outputFull, List.of(liquidRef(liquid)));
    }

    // ------------------------------------------------------------------ SolidPump, Fracker

    static void solidPump(Context c, SolidPump.SolidPumpBuild build){
        SolidPump block = (SolidPump)build.block;
        FactorySnapshot.Builder s = c.snapshot.family(ProductionFamily.solidPump);
        Liquid liquid = block.result;
        if(liquid == null) return;

        float fraction = Math.max(build.validTiles + build.boost + (block.attribute == null ? 0f : block.attribute.env()), 0f);
        float nominal = block.pumpAmount * fraction * ProductionRates.TICKS_PER_SECOND * c.timeScale;
        //the value SolidPump's own speed bar shows: lastPump * 60
        float current = build.efficiency > 0f ? build.lastPump * ProductionRates.TICKS_PER_SECOND : 0f;
        if(!finite(current) || current < 0f) current = 0f;

        float stored = build.liquids == null ? 0f : build.liquids.get(liquid);
        boolean full = stored >= block.liquidCapacity - 0.01f;
        s.output(new OutputState(ResourceKind.liquid, liquid.localizedName, liquid.name, nominal, current,
            stored, block.liquidCapacity, full));
        s.fact(Fact.of("fact.ground-yield", Fact.Format.percent, fraction));

        if(full && !c.shouldConsume) s.outputBufferFull(true).gate(ProductionGate.outputFull, List.of(liquidRef(liquid)));
    }
}
