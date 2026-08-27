package factoryscope.probe;

import arc.util.*;
import factoryscope.*;
import factoryscope.analysis.*;
import factoryscope.model.*;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.power.*;
import mindustry.world.blocks.production.*;
import mindustry.world.consumers.*;
import mindustry.world.modules.*;

/**
 * Reads a live {@link Building} and produces a {@link FactorySnapshot}.
 *
 * <p>This is the only class that knows about Mindustry types, and it only ever reads: no field is
 * written, no method with side effects is called, nothing is sent over the network. The consumer
 * inspection is driven by {@code block.consumers} rather than by a list of known blocks, so modded
 * crafters built on the standard consumers are analysed the same way vanilla ones are.
 *
 * <p>All formulas here were taken from the Mindustry v159.7 sources; the reasoning behind the
 * non-obvious ones is in {@code docs/mindustry-notes.md}.
 */
public final class MindustryFactoryProbe{
    /** Guards against a zero-length frame, which happens on the first frame and while the game is paused. */
    private static final float MIN_FRAME_TICKS = 0.0001f;

    private MindustryFactoryProbe(){
    }

    /**
     * Whether the local player may legitimately see this building.
     *
     * <p>FactoryScope must never turn into a fog-of-war cheat, so anything hidden from the player is
     * refused outright rather than analysed and hidden later.
     */
    public static boolean canInspect(Building build, mindustry.game.Team viewer){
        if(build == null || !build.isValid() || build.block == null) return false;
        return viewer == null || !build.inFogTo(viewer);
    }

    public static FactorySnapshot probe(Building build){
        Block block = build.block;
        float frameTicks = Math.max(Time.delta, MIN_FRAME_TICKS);
        float timeScale = build.timeScale();
        boolean crafter = block instanceof GenericCrafter;

        boolean gateOpen = build.enabled && build.shouldConsume() && build.productionValid();

        FactorySnapshot.Builder snapshot = FactorySnapshot.builder(block.localizedName)
            .position(build.tile.x, build.tile.y)
            .support(crafter ? SupportLevel.full : block.hasConsumers ? SupportLevel.basic : SupportLevel.minimal)
            .enabled(build.enabled)
            .efficiency(build.efficiency, build.potentialEfficiency, build.optionalEfficiency)
            .shouldConsume(build.shouldConsume())
            .productionValid(build.productionValid())
            .timeScale(timeScale)
            .infiniteResources(build.cheating())
            .hasConsumers(block.hasConsumers)
            .blockEfficiencyScale(ProductionRates.blockEfficiencyScale(
                build.efficiency, build.potentialEfficiency, gateOpen));

        addInputs(build, block, snapshot, frameTicks, timeScale);
        if(block.consPower != null) snapshot.power(readPower(build, block.consPower));
        if(crafter) addCrafterProduction(build, (GenericCrafter)block, snapshot, frameTicks, timeScale);

        return snapshot.build();
    }

    // ------------------------------------------------------------------ inputs

    private static void addInputs(Building build, Block block, FactorySnapshot.Builder snapshot,
                                  float frameTicks, float timeScale){
        for(Consume consume : block.consumers){
            try{
                //ignore() consumers are excluded from the game's own efficiency calculation, so they can
                //never be the cause of a shortage; buffered power is the vanilla case.
                boolean optional = consume.optional || consume.ignore();
                readConsumer(build, consume, optional, snapshot, frameTicks, timeScale);
            }catch(Exception e){
                FsLog.warnOnce("consumer:" + consume.getClass().getName(),
                    "failed to read consumer " + consume.getClass().getSimpleName()
                        + " on block " + block.name, e);
                snapshot.input(ResourceState.of(ResourceKind.other, consume.getClass().getSimpleName())
                    .optional(true).recognised(false).provisional(true).satisfaction(1f).build());
            }
        }
    }

    private static void readConsumer(Building build, Consume consume, boolean optional,
                                     FactorySnapshot.Builder snapshot, float frameTicks, float timeScale){
        float multiplier = consume.multiplier.get(build);

        if(consume instanceof ConsumeItems items){
            for(ItemStack stack : items.items){
                snapshot.input(itemInput(build, stack.item, Math.round(stack.amount * multiplier), optional));
            }
        }else if(consume instanceof ConsumeItemDynamic dynamic){
            for(ItemStack stack : dynamic.items.get(build)){
                snapshot.input(itemInput(build, stack.item, Math.round(stack.amount * multiplier), optional));
            }
        }else if(consume instanceof ConsumeItemFilter filter){
            snapshot.input(itemFilterInput(build, filter, optional));
        }else if(consume instanceof ConsumeLiquid liquid){
            snapshot.input(liquidInput(build, liquid.liquid, liquid.amount * multiplier, optional, frameTicks, timeScale));
        }else if(consume instanceof ConsumeLiquids liquids){
            for(LiquidStack stack : liquids.liquids){
                snapshot.input(liquidInput(build, stack.liquid, stack.amount * multiplier, optional, frameTicks, timeScale));
            }
        }else if(consume instanceof ConsumeLiquidFilter filter){
            //covers ConsumeCoolant and anything else that accepts a family of liquids
            Liquid current = filter.getConsumed(build);
            if(current != null){
                snapshot.input(liquidInput(build, current, filter.amount * multiplier, optional, frameTicks, timeScale));
            }else{
                snapshot.input(ResourceState.of(ResourceKind.liquid, FsBundle.get("input.any-accepted-liquid"))
                    .optional(optional)
                    .satisfaction(0f)
                    .amounts(0f, ProductionRates.perTickToPerSecond(filter.amount * multiplier, timeScale), RateUnit.perSecond)
                    .build());
            }
        }else if(consume instanceof ConsumePower power){
            snapshot.input(powerInput(build, power, optional));
        }else{
            snapshot.input(unknownInput(build, consume, optional));
        }
    }

    private static ResourceState itemInput(Building build, Item item, int required, boolean optional){
        ItemModule module = build.items;
        int stored = module == null ? 0 : module.get(item);
        return ResourceState.of(ResourceKind.item, item.localizedName)
            .contentId(item.name)
            .optional(optional)
            .satisfaction(required <= 0 || stored >= required ? 1f : 0f)
            .amounts(stored, required, RateUnit.perCraft)
            .build();
    }

    private static ResourceState itemFilterInput(Building build, ConsumeItemFilter filter, boolean optional){
        Item consumed = filter.getConsumed(build);
        String name = consumed != null ? consumed.localizedName : FsBundle.get("input.any-accepted-item");
        return ResourceState.of(ResourceKind.item, name)
            .contentId(consumed != null ? consumed.name : null)
            .optional(optional)
            .satisfaction(consumed != null ? 1f : 0f)
            .amounts(consumed != null && build.items != null ? build.items.get(consumed) : -1f, -1f, RateUnit.none)
            .build();
    }

    /**
     * Liquid consumers are satisfied per frame, not per craft.
     *
     * <p>{@code ConsumeLiquid.efficiency()} divides the stored amount by {@code amount * edelta()}, and
     * {@code updateConsumption()} evaluates it with efficiency pinned to 1. That is reproduced here rather
     * than calling {@code efficiency()} directly, because calling it from outside the update loop would
     * read the building's <em>current</em> efficiency and report every liquid as missing the moment the
     * factory stops for any other reason.
     *
     * <p>{@code efficiencyScale()} is part of the game's divisor but is deliberately clamped away when it
     * is zero: a heat-starved crafter is a block condition, not a liquid shortage, and is reported as such.
     */
    private static ResourceState liquidInput(Building build, Liquid liquid, float amountPerTick,
                                             boolean optional, float frameTicks, float timeScale){
        LiquidModule module = build.liquids;
        float stored = module == null ? 0f : module.get(liquid);

        float scale = build.efficiencyScale();
        if(!(scale > 0f) || Float.isNaN(scale)) scale = 1f;
        float neededThisFrame = amountPerTick * frameTicks * Math.max(timeScale, MIN_FRAME_TICKS) * scale;

        float satisfaction = neededThisFrame <= 0f ? 1f : Math.min(stored / neededThisFrame, 1f);
        return ResourceState.of(ResourceKind.liquid, liquid.localizedName)
            .contentId(liquid.name)
            .optional(optional)
            .satisfaction(satisfaction)
            .amounts(stored, ProductionRates.perTickToPerSecond(amountPerTick, timeScale), RateUnit.perSecond)
            .build();
    }

    private static ResourceState powerInput(Building build, ConsumePower power, boolean optional){
        float status = build.power == null ? 0f : build.power.status;
        return ResourceState.of(ResourceKind.power, FsBundle.get("input.power"))
            .optional(optional)
            .satisfaction(status)
            .amounts(-1f, ProductionRates.perTickToPerSecond(power.usage, build.timeScale()), RateUnit.perSecond)
            .build();
    }

    /**
     * Fallback for consumer types FactoryScope does not model, including those added by other mods.
     *
     * <p>{@code Consume.efficiency()} is a read-only query in every vanilla implementation, so it is safe
     * to call. Its result is marked provisional while the building sits at zero efficiency, because
     * frame-scaled consumers derive their answer from {@code edelta()} and would then report zero
     * regardless of whether they are the real cause.
     */
    private static ResourceState unknownInput(Building build, Consume consume, boolean optional){
        float satisfaction;
        try{
            satisfaction = consume.efficiency(build);
        }catch(Exception e){
            FsLog.warnOnce("efficiency:" + consume.getClass().getName(),
                "consumer " + consume.getClass().getSimpleName() + " threw while reporting efficiency", e);
            satisfaction = 1f;
        }
        return ResourceState.of(ResourceKind.other, consume.getClass().getSimpleName())
            .optional(optional)
            .recognised(false)
            .provisional(build.efficiency <= 0f)
            .satisfaction(satisfaction)
            .build();
    }

    // ------------------------------------------------------------------ power

    private static PowerState readPower(Building build, ConsumePower consume){
        if(build.power == null){
            return new PowerState(0f, 0f, consume.buffered, 0f, 0f, 0f, false, 0f, 0f);
        }

        PowerGraph graph = build.power.graph;
        float perSecond = ProductionRates.TICKS_PER_SECOND;
        return new PowerState(
            build.power.status,
            ProductionRates.perTickToPerSecond(consume.usage, build.timeScale()),
            consume.buffered,
            graph == null ? 0f : graph.getLastScaledPowerIn() * perSecond,
            graph == null ? 0f : graph.getLastScaledPowerOut() * perSecond,
            graph == null ? 0f : graph.getPowerBalance() * perSecond,
            graph != null && graph.hasPowerBalanceSamples(),
            graph == null ? 0f : graph.getLastPowerStored(),
            graph == null ? 0f : graph.getLastCapacity());
    }

    // ------------------------------------------------------------------ production

    /**
     * Derives the production model of a {@code GenericCrafter}.
     *
     * <p>The current rate comes from {@code getProgressIncrease}, the same call the game uses to advance
     * the craft, so subclass behaviour such as the terrain boost of an {@code AttributeCrafter} or the
     * liquid-fullness throttle of the base crafter is included automatically instead of being guessed at.
     */
    private static void addCrafterProduction(Building build, GenericCrafter crafter,
                                             FactorySnapshot.Builder snapshot, float frameTicks, float timeScale){
        float craftTime = crafter.craftTime;
        snapshot.craftTimeSeconds(craftTime / ProductionRates.TICKS_PER_SECOND);

        float craftsNow = ProductionRates.perSecondFromProgress(build.getProgressIncrease(craftTime), frameTicks);
        float craftsNominal = ProductionRates.nominalCraftsPerSecond(craftTime, timeScale);
        float liquidProgressNow = ProductionRates.perSecondFromProgress(build.getProgressIncrease(1f), frameTicks);
        snapshot.craftSpeedMultiplier(ProductionRates.speedMultiplier(craftsNow, craftsNominal, build.efficiency));

        boolean blocked = false;

        if(crafter.outputItems != null){
            for(ItemStack stack : crafter.outputItems){
                int stored = build.items == null ? 0 : build.items.get(stack.item);
                //mirrors GenericCrafterBuild.shouldConsume(): a cycle cannot start if its result would overflow
                boolean full = stored + stack.amount > crafter.itemCapacity;
                blocked |= full;
                snapshot.output(new OutputState(ResourceKind.item, stack.item.localizedName, stack.item.name,
                    stack.amount * craftsNominal, stack.amount * craftsNow,
                    stored, crafter.itemCapacity, full));
            }
        }

        if(crafter.outputLiquids != null){
            boolean anyFull = false, allFull = true;
            for(LiquidStack stack : crafter.outputLiquids){
                float stored = build.liquids == null ? 0f : build.liquids.get(stack.liquid);
                boolean full = stored >= crafter.liquidCapacity - 0.001f;
                anyFull |= full;
                allFull &= full;
                snapshot.output(new OutputState(ResourceKind.liquid, stack.liquid.localizedName, stack.liquid.name,
                    ProductionRates.perTickToPerSecond(stack.amount, timeScale),
                    stack.amount * liquidProgressNow,
                    stored, crafter.liquidCapacity, full));
            }
            //dumpExtraLiquid lets a crafter keep going while only some outputs are full
            if(!crafter.ignoreLiquidFullness) blocked |= crafter.dumpExtraLiquid ? allFull : anyFull;
        }

        snapshot.outputBufferFull(blocked);
    }
}
