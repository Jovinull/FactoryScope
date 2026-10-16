package factoryscope.probe;

import factoryscope.analysis.*;
import factoryscope.model.*;
import mindustry.world.blocks.power.*;
import mindustry.world.consumers.*;


import static factoryscope.probe.ProductionModels.*;

/**
 * Power generators and reactors.
 *
 * <p>The current output is {@code getPowerProduction() * 60 * timeScale}, the figure the generator's own
 * bar shows and the one its power graph integrates; the nominal output is the declared
 * {@code powerProduction} at the same time scale. What differs between generators is what drives
 * {@code productionEfficiency} - fuel quality, light, heat under the block, stored fuel, warm-up - and
 * each of those is shown as a detail. Where the engine defines a danger threshold (a reactor's heat or
 * instability), that threshold is what colours the detail.
 */
final class GeneratorModels{
    /** Fill level of an explode-on-full output tank from which the detail is shown as a warning. */
    private static final float TANK_WARNING = 0.5f;
    private static final float TANK_DANGER = 0.8f;

    private GeneratorModels(){
    }

    static void generator(Context c, PowerGenerator.GeneratorBuild build){
        PowerGenerator block = (PowerGenerator)build.block;
        FactorySnapshot.Builder s = c.snapshot.family(ProductionFamily.generator);

        float nominal = block.powerProduction * ProductionRates.TICKS_PER_SECOND * c.timeScale;
        float current = build.getPowerProduction() * ProductionRates.TICKS_PER_SECOND * c.timeScale;
        if(!finite(current) || current < 0f) current = 0f;
        if(finite(nominal) && nominal > 0f){
            ResourceRef power = powerRef();
            s.output(new OutputState(ResourceKind.power, power.name, null, nominal, current, -1f, -1f, false));
        }
        if(finite(build.productionEfficiency)){
            s.fact(Fact.of("fact.production-efficiency", Fact.Format.percent, build.productionEfficiency));
        }

        if(build instanceof HeaterGenerator.HeaterGeneratorBuild heater){
            HeaterGenerator heaterBlock = (HeaterGenerator)block;
            s.fact(Fact.amountOf("fact.heat-output", heater.heat, heaterBlock.heatOutput));
        }
        if(build instanceof ConsumeGenerator.ConsumeGeneratorBuild generator){
            consumeGenerator(c, s, generator, (ConsumeGenerator)block);
        }else if(build instanceof SolarGenerator.SolarGeneratorBuild){
            //no consumers: efficiency is 1 while enabled, so productionEfficiency is the light level alone
            s.fact(Fact.of("fact.light", Fact.Format.percent, build.productionEfficiency));
            if(build.enabled) s.blockEfficiencyScale(build.productionEfficiency);
        }else if(build instanceof ThermalGenerator.ThermalGeneratorBuild thermal){
            ThermalGenerator thermalBlock = (ThermalGenerator)block;
            s.fact(Fact.of("fact.heat-yield", Fact.Format.percent, build.productionEfficiency));
            if(build.enabled) s.blockEfficiencyScale(build.productionEfficiency);
            if(thermalBlock.outputLiquid != null && block.hasLiquids){
                liquidOutput(c, s, build, thermalBlock.outputLiquid, false);
            }
        }else if(build instanceof NuclearReactor.NuclearReactorBuild reactor){
            NuclearReactor reactorBlock = (NuclearReactor)block;
            int fuel = reactor.items == null ? 0 : reactor.items.get(reactorBlock.fuelItem);
            s.fact(Fact.of("fact.fuel-fullness", Fact.Format.percent, fuel / (float)Math.max(reactorBlock.itemCapacity, 1)));
            Fact.Tone tone = reactor.heat >= reactorBlock.flashThreshold ? Fact.Tone.danger
                : reactor.heat >= reactorBlock.smokeThreshold ? Fact.Tone.warning : Fact.Tone.normal;
            s.fact(Fact.of("fact.reactor-heat", Fact.Format.percent, reactor.heat).tone(tone));
            s.note("note.reactor-heat");
        }else if(build instanceof ImpactReactor.ImpactReactorBuild reactor){
            s.fact(Fact.of("fact.warmup", Fact.Format.percent, reactor.warmup));
            s.note("note.impact-warmup");
        }else if(build instanceof VariableReactor.VariableReactorBuild reactor){
            VariableReactor reactorBlock = (VariableReactor)block;
            s.fact(Fact.amountOf("fact.heat-input", reactor.heat, reactorBlock.maxHeat));
            s.fact(Fact.of("fact.instability", Fact.Format.percent, reactor.instability)
                .tone(reactor.instability > 0f ? Fact.Tone.danger : Fact.Tone.normal));
            s.note("note.flux-reactor");
        }
    }

    private static void consumeGenerator(Context c, FactorySnapshot.Builder s, ConsumeGenerator.ConsumeGeneratorBuild build,
                                         ConsumeGenerator block){
        //productionEfficiency = efficiency * efficiencyMultiplier, where the multiplier is the fuel's quality
        if(build.efficiency > 0f && finite(build.efficiencyMultiplier)) s.blockEfficiencyScale(build.efficiencyMultiplier);

        boolean burnsItems = block.hasItems
            && block.findConsumer(consume -> consume instanceof ConsumeItemFilter || consume instanceof ConsumeItems) != null;
        float duration = block.itemDuration * build.itemDurationMultiplier;
        if(burnsItems && duration > 0f){
            //ConsumeGeneratorBuild consumes once each time generateTime, falling by delta()/duration, runs out
            s.fact(Fact.of("fact.fuel-use", Fact.Format.itemsPerSecond, ProductionRates.TICKS_PER_SECOND * c.timeScale / duration));
        }
        if(block.outputLiquid != null){
            liquidOutput(c, s, build, block.outputLiquid, block.explodeOnFull);
        }
    }

    /**
     * {@code min(productionEfficiency * delta() * amount, liquidCapacity - stored)} per frame, as both
     * {@code ConsumeGenerator} and {@code ThermalGenerator} add it.
     */
    private static void liquidOutput(Context c, FactorySnapshot.Builder s, PowerGenerator.GeneratorBuild build,
                                     mindustry.type.LiquidStack output, boolean explodesWhenFull){
        float capacity = build.block.liquidCapacity;
        float stored = build.liquids == null ? 0f : build.liquids.get(output.liquid);
        float perFrame = Math.min(build.productionEfficiency * c.frameTicks * c.timeScale * output.amount,
            Math.max(capacity - stored, 0f));
        float current = perFrame / c.frameTicks * ProductionRates.TICKS_PER_SECOND;
        if(!finite(current) || current < 0f) current = 0f;
        float nominal = ProductionRates.perTickToPerSecond(output.amount, c.timeScale);
        s.output(new OutputState(ResourceKind.liquid, output.liquid.localizedName, output.liquid.name, nominal, current,
            stored, capacity, false));

        if(explodesWhenFull && capacity > 0f){
            float fill = stored / capacity;
            Fact.Tone tone = fill >= TANK_DANGER ? Fact.Tone.danger : fill >= TANK_WARNING ? Fact.Tone.warning : Fact.Tone.normal;
            s.fact(Fact.amountOf("fact.output-tank", stored, capacity).tone(tone));
            s.note("note.explodes-when-full");
        }
    }
}
