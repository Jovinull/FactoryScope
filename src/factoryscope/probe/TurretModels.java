package factoryscope.probe;

import factoryscope.analysis.*;
import factoryscope.model.*;
import mindustry.*;
import mindustry.entities.bullet.*;
import mindustry.type.*;
import mindustry.world.blocks.defense.turrets.*;

import java.util.*;

import static factoryscope.probe.ProductionModels.*;

/**
 * Turrets, as consumers of ammunition, power and coolant.
 *
 * <p>Whether a turret can fire, how much ammunition it holds and whether it is engaging are engine state
 * and reported as such. How fast it fires and how much ammunition that uses depend on there being
 * something to shoot at, so those figures assume continuous fire at full efficiency without coolant and
 * are reported as estimates.
 *
 * <p>{@code Turret.TurretBuild.hasAmmo()} reorders the ammunition list as a side effect, so it is never
 * called; the same answer is computed from the list without touching it. Subclass implementations known
 * to be pure are called directly, and an unknown override is not called at all.
 */
final class TurretModels{
    /** Vanilla turret builds whose {@code hasAmmo()} only reads state. */
    private static final Set<Class<?>> PURE_HAS_AMMO = Set.of(
        LiquidTurret.LiquidTurretBuild.class,
        PowerTurret.PowerTurretBuild.class,
        ContinuousTurret.ContinuousTurretBuild.class,
        ContinuousLiquidTurret.ContinuousLiquidTurretBuild.class
    );

    private TurretModels(){
    }

    static void turret(Context c, BaseTurret.BaseTurretBuild build){
        FactorySnapshot.Builder s = c.snapshot.family(ProductionFamily.turret);
        if(build instanceof Turret.TurretBuild turret){
            turret(c, s, turret, (Turret)build.block);
        }else if(build instanceof ReloadTurret.ReloadTurretBuild reload && ((ReloadTurret)build.block).reload > 0f){
            s.fact(Fact.of("fact.reload", Fact.Format.percent,
                Math.min(Math.max(reload.reloadCounter / ((ReloadTurret)build.block).reload, 0f), 1f)));
        }
    }

    private static void turret(Context c, FactorySnapshot.Builder s, Turret.TurretBuild build, Turret block){
        Boolean hasAmmo = hasAmmo(build);
        BulletType bullet = hasAmmo == null ? null : peekAmmo(build);

        if(block instanceof ItemTurret){
            s.fact(Fact.amountOf("fact.ammo", build.totalAmmo, block.maxAmmo));
            if(!build.ammo.isEmpty() && build.ammo.peek() instanceof ItemTurret.ItemEntry entry && entry.item != null){
                s.fact(Fact.resource("fact.ammo-type", itemRef(entry.item)));
            }
        }else if((block instanceof LiquidTurret || block instanceof ContinuousLiquidTurret) && build.liquids != null
            && build.liquids.currentAmount() > 0f){
            s.fact(Fact.resource("fact.ammo-type", liquidRef(build.liquids.current())));
        }

        if(!(block instanceof ContinuousTurret) && block.reload > 0f){
            float reloadMultiplier = bullet == null ? 1f : bullet.reloadMultiplier;
            //reloadCounter += delta() * ammoReloadMultiplier() * efficiency; one shoot() per reload
            float cycles = ProductionRates.TICKS_PER_SECOND * c.timeScale * reloadMultiplier / block.reload;
            float shots = cycles * Math.max(block.shoot.shots, 1);
            if(finite(shots) && shots > 0f) s.fact(Fact.of("fact.fire-rate", Fact.Format.perSecond, shots).estimate());

            float ammoPerSecond = ammoPerSecond(block, bullet, cycles);
            if(ammoPerSecond > 0f){
                Fact.Format format = block instanceof ItemTurret ? Fact.Format.itemsPerSecond : Fact.Format.liquidPerSecond;
                s.fact(Fact.of("fact.ammo-use", format, ammoPerSecond).estimate());
            }
            s.fact(Fact.of("fact.reload", Fact.Format.percent, Math.min(Math.max(build.reloadCounter / block.reload, 0f), 1f)));
        }
        if(block.heatRequirement > 0f){
            s.fact(Fact.amountOf("fact.heat-input", build.heatReq, block.heatRequirement)
                .tone(build.heatReq < block.heatRequirement ? Fact.Tone.warning : Fact.Tone.normal));
        }
        s.fact(Fact.of("fact.engaging", Fact.Format.flag, build.isShooting || build.wasShooting ? 1f : 0f));
        s.note("note.turret-estimates");

        if(hasAmmo == null) return;
        if(!hasAmmo){
            s.gate(ProductionGate.noAmmo, acceptedAmmo(block));
        }else if(!c.shouldConsume){
            //TurretBuild.shouldConsume() is isShooting || reloadCounter < reload: loaded, reloaded, nothing to shoot
            s.gate(ProductionGate.idle);
        }
    }

    /**
     * Ammunition used per second of continuous fire. {@code useAmmo()} runs once per shot pattern when
     * {@code consumeAmmoOnce} is set and once per bullet otherwise; an item adds {@code ammoMultiplier}
     * rounds and a unit of liquid ammunition {@code ammoMultiplier} rounds.
     */
    private static float ammoPerSecond(Turret block, BulletType bullet, float cycles){
        if(bullet == null || !(bullet.ammoMultiplier > 0f)) return 0f;
        int uses = block.consumeAmmoOnce ? 1 : Math.max(block.shoot.shots, 1);
        if(block instanceof ItemTurret){
            return cycles * uses * block.ammoPerShot / bullet.ammoMultiplier;
        }
        if(block instanceof LiquidTurret){
            //LiquidTurretBuild.useAmmo() removes 1 / ammoMultiplier of the current liquid
            return cycles * uses / bullet.ammoMultiplier;
        }
        return 0f;
    }

    /** {@code hasAmmo()} without side effects, or null when the turret's own rule cannot be evaluated safely. */
    static Boolean hasAmmo(Turret.TurretBuild build){
        Class<?> declaring;
        try{
            declaring = build.getClass().getMethod("hasAmmo").getDeclaringClass();
        }catch(NoSuchMethodException | SecurityException e){
            return null;
        }
        if(declaring == Turret.TurretBuild.class){
            //the base rule: canConsume() and some entry holding a full shot; hasAmmo() would also move it to the top
            if(!build.canConsume() || build.ammo.isEmpty()) return false;
            if(build.cheating()) return true;
            int perShot = ((Turret)build.block).ammoPerShot;
            //indexed rather than iterated: Arc collections hand out shared iterator instances
            for(int i = 0; i < build.ammo.size; i++){
                Turret.AmmoEntry entry = build.ammo.get(i);
                if(entry != null && entry.amount >= perShot) return true;
            }
            return false;
        }
        return PURE_HAS_AMMO.contains(declaring) ? build.hasAmmo() : null;
    }

    private static BulletType peekAmmo(Turret.TurretBuild build){
        try{
            return build.peekAmmo();
        }catch(RuntimeException e){
            return null;
        }
    }

    private static List<ResourceRef> acceptedAmmo(Turret block){
        //looked up per content entry: ObjectMap.keys() would reuse the map's own iterator
        List<ResourceRef> result = new ArrayList<>();
        if(block instanceof ItemTurret items){
            for(Item item : Vars.content.items()) if(items.ammoTypes.containsKey(item)) result.add(itemRef(item));
        }else if(block instanceof LiquidTurret liquids){
            for(Liquid liquid : Vars.content.liquids()) if(liquids.ammoTypes.containsKey(liquid)) result.add(liquidRef(liquid));
        }else if(block instanceof ContinuousLiquidTurret liquids){
            for(Liquid liquid : Vars.content.liquids()) if(liquids.ammoTypes.containsKey(liquid)) result.add(liquidRef(liquid));
        }else if(block instanceof PowerTurret || block instanceof ContinuousTurret){
            result.add(powerRef());
        }
        return result;
    }
}
