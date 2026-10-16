package factoryscope.probe;

import arc.math.geom.*;
import factoryscope.*;
import factoryscope.analysis.*;
import factoryscope.model.*;
import mindustry.*;
import mindustry.ctype.*;
import mindustry.entities.*;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.world.blocks.defense.turrets.*;
import mindustry.world.blocks.payloads.*;
import mindustry.world.blocks.power.*;
import mindustry.world.blocks.production.*;
import mindustry.world.blocks.units.*;

import java.util.*;

import static mindustry.Vars.tilesize;

/**
 * Production models for the block families that are not conventional crafters.
 *
 * <p>Every model reads the values the engine itself keeps for the block - the drill speed its own bar
 * shows, the power a generator reports to its graph - and only derives what the engine does not store,
 * using the formulas of the Mindustry v160.7 sources. Nothing here writes to the building or calls a
 * method with side effects; see {@code docs/mindustry-notes.md} for the formulas.
 *
 * <p>A model is applied only when the building's runtime class is the engine's own. A mod that reuses a
 * vanilla build class gets the vanilla model; a mod that replaces it may have changed exactly the rules
 * the model relies on, so its building keeps the general diagnostics.
 */
final class ProductionModels{
    private ProductionModels(){
    }

    /** Values the probe already read once for this snapshot. */
    static final class Context{
        final Building build;
        final FactorySnapshot.Builder snapshot;
        final float frameTicks;
        final float timeScale;
        final boolean shouldConsume;

        Context(Building build, FactorySnapshot.Builder snapshot, float frameTicks, float timeScale, boolean shouldConsume){
            this.build = build;
            this.snapshot = snapshot;
            this.frameTicks = frameTicks;
            this.timeScale = timeScale;
            this.shouldConsume = shouldConsume;
        }
    }

    static void apply(Context context){
        Building build = context.build;
        if(!engineBuild(build)) return;

        //subclasses first: Fracker is a SolidPump is a Pump, BurstDrill is a Drill, and so on
        if(build instanceof Separator.SeparatorBuild separator){
            separator(context, separator);
        }else if(build instanceof Drill.DrillBuild drill){
            ExtractionModels.drill(context, drill);
        }else if(build instanceof BeamDrill.BeamDrillBuild drill){
            ExtractionModels.beamDrill(context, drill);
        }else if(build instanceof WallCrafter.WallCrafterBuild crusher){
            ExtractionModels.wallCrafter(context, crusher);
        }else if(build instanceof SolidPump.SolidPumpBuild pump){
            ExtractionModels.solidPump(context, pump);
        }else if(build instanceof Pump.PumpBuild pump){
            ExtractionModels.pump(context, pump);
        }else if(build instanceof PowerGenerator.GeneratorBuild generator){
            GeneratorModels.generator(context, generator);
        }else if(build instanceof UnitFactory.UnitFactoryBuild factory){
            UnitModels.factory(context, factory);
        }else if(build instanceof Reconstructor.ReconstructorBuild reconstructor){
            UnitModels.reconstructor(context, reconstructor);
        }else if(build instanceof UnitAssembler.UnitAssemblerBuild assembler){
            UnitModels.assembler(context, assembler);
        }else if(build instanceof BaseTurret.BaseTurretBuild turret){
            TurretModels.turret(context, turret);
        }
    }

    /** Whether the building runs the engine's own code rather than a mod's replacement. */
    static boolean engineBuild(Building build){
        return build.getClass().getName().startsWith("mindustry.");
    }

    // ------------------------------------------------------------------ separator

    /**
     * A {@code Separator} crafts on the same progress model as a crafter, but each cycle yields a single
     * item drawn at random, weighted by the result stack amounts. The cycle rate is the engine's; the
     * per-item rates are its expected long-run shares, and are reported as estimates.
     */
    private static void separator(Context c, Separator.SeparatorBuild build){
        Separator block = (Separator)build.block;
        FactorySnapshot.Builder s = c.snapshot.family(ProductionFamily.separator);
        if(block.results == null || block.results.length == 0) return;

        float craftsNow = ProductionRates.perSecondFromProgress(build.getProgressIncrease(block.craftTime), c.frameTicks);
        float craftsNominal = ProductionRates.nominalCraftsPerSecond(block.craftTime, c.timeScale);
        s.craftTimeSeconds(block.craftTime / ProductionRates.TICKS_PER_SECOND);
        s.craftSpeedMultiplier(ProductionRates.speedMultiplier(craftsNow, craftsNominal, build.efficiency));

        int weights = 0;
        for(ItemStack stack : block.results) weights += Math.max(stack.amount, 0);
        if(weights <= 0) return;

        List<ResourceRef> held = new ArrayList<>();
        for(ItemStack stack : block.results){
            float share = Math.max(stack.amount, 0) / (float)weights;
            int stored = build.items == null ? 0 : build.items.get(stack.item);
            s.output(new OutputState(ResourceKind.item, stack.item.localizedName, stack.item.name,
                share * craftsNominal, share * craftsNow, stored, block.itemCapacity, false, true));
            if(stored > 0) held.add(itemRef(stack.item));
        }
        s.note("note.separator-random");

        //mirrors SeparatorBuild.shouldConsume(): results held in the shared buffer stop the next cycle
        if(!c.shouldConsume && build.enabled && build.items != null && nonInputItems(build) >= block.itemCapacity){
            s.outputBufferFull(true).gate(ProductionGate.outputFull, held);
        }
    }

    private static int nonInputItems(Separator.SeparatorBuild build){
        int total = build.items.total();
        var consItems = build.block.findConsumer(consume -> consume instanceof mindustry.world.consumers.ConsumeItems);
        if(consItems instanceof mindustry.world.consumers.ConsumeItems items){
            for(ItemStack stack : items.items) total -= build.items.get(stack.item);
        }
        return total;
    }

    // ------------------------------------------------------------------ shared helpers

    static ResourceRef itemRef(Item item){
        return new ResourceRef(ResourceKind.item, item.name, item.localizedName);
    }

    static ResourceRef liquidRef(Liquid liquid){
        return new ResourceRef(ResourceKind.liquid, liquid.name, liquid.localizedName);
    }

    static ResourceRef unitRef(UnitType unit){
        return new ResourceRef(ResourceKind.unit, unit.name, unit.localizedName);
    }

    static ResourceRef payloadRef(UnlockableContent content){
        ResourceKind kind = content instanceof UnitType ? ResourceKind.unit : ResourceKind.block;
        return new ResourceRef(kind, content.name, content.localizedName);
    }

    static ResourceRef powerRef(){
        return ResourceRef.unidentified(ResourceKind.power, FsBundle.get("input.power"));
    }

    /**
     * Whether a finished unit payload has reached the edge of its block, which is where
     * {@code PayloadBlockBuild.moveOutPayload()} tries to release it. Before that point it is still
     * moving out normally and nothing is blocked.
     */
    static boolean payloadAtExit(PayloadBlock.PayloadBlockBuild<?> build){
        Vec2 position = build.payVector;
        float half = build.block.size * tilesize / 2f;
        float angle = build.rotdeg() * arc.math.Mathf.degreesToRadians;
        float dx = (float)Math.cos(angle) * half - position.x;
        float dy = (float)Math.sin(angle) * half - position.y;
        //moveOutPayload() releases the payload once it is within 0.001 world units of that point
        return dx * dx + dy * dy <= 0.001f * 0.001f;
    }

    /** How many units of a type the team fields against the cap that applies to it. */
    static void unitCapFact(FactorySnapshot.Builder s, Building build, UnitType type){
        if(type == null || !type.useUnitCap) return;
        int cap = Units.getCap(build.team);
        if(cap == Integer.MAX_VALUE) return;
        int count = build.team.data().countType(type);
        s.fact(Fact.amountOf("fact.unit-count", count, cap)
            .tone(count >= cap ? Fact.Tone.warning : Fact.Tone.normal));
    }

    /** Engine units per tick to units per minute. */
    static float perMinute(float perSecond){
        return perSecond * 60f;
    }

    static float unitBuildSpeed(Building build){
        float speed = Vars.state.rules.unitBuildSpeed(build.team);
        return Float.isNaN(speed) || Float.isInfinite(speed) ? 0f : speed;
    }

    static boolean finite(float value){
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }
}
