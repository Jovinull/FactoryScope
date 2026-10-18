package factoryscope.probe;

import factoryscope.analysis.*;
import factoryscope.model.*;
import mindustry.ai.types.*;
import mindustry.entities.*;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.world.blocks.units.*;

import java.util.*;

import static factoryscope.probe.ProductionModels.*;

/**
 * Unit factories, reconstructors and unit assemblers.
 *
 * <p>Each advances {@code progress} by {@code edelta() * rules.unitBuildSpeed(team)} (scaled, for an
 * assembler, by the share of its drones in position) and finishes a unit when progress reaches the plan
 * time, so the rate follows directly from efficiency, time scale and the rule multiplier. A finished unit
 * waits as a payload at the block's edge until {@code UnitPayload.dump()} can release it: that wait is
 * reported as the unit cap when {@code Units.canCreate} refuses it, and as a blocked output otherwise.
 */
final class UnitModels{
    private UnitModels(){
    }

    static void factory(Context c, UnitFactory.UnitFactoryBuild build){
        UnitFactory block = (UnitFactory)build.block;
        FactorySnapshot.Builder s = c.snapshot.family(ProductionFamily.unitFactory);

        int index = build.currentPlan;
        if(index < 0 || index >= block.plans.size){
            s.gate(ProductionGate.noPlan);
            return;
        }
        UnitFactory.UnitPlan plan = block.plans.get(index);
        UnitType unit = plan.unit;
        float speed = unitBuildSpeed(build);
        float nominal = plan.time > 0f ? ProductionRates.TICKS_PER_SECOND * c.timeScale * speed / plan.time : 0f;
        float current = build.efficiency > 0f ? nominal * build.efficiency : 0f;
        boolean waiting = build.payload != null && payloadAtExit(build);

        s.output(new OutputState(ResourceKind.unit, unit.localizedName, unit.name, nominal, current, -1f, -1f, waiting));
        if(speed > 0f) s.craftTimeSeconds(plan.time / ProductionRates.TICKS_PER_SECOND / speed);
        s.fact(Fact.resource("fact.unit", unitRef(unit)));
        if(plan.time > 0f) s.fact(Fact.of("fact.progress", Fact.Format.percent, Math.min(build.progress / plan.time, 1f)));
        unitCapFact(s, build, unit);

        if(waiting) gateFinishedUnit(s, build, build.payload.unit.type);
    }

    static void reconstructor(Context c, Reconstructor.ReconstructorBuild build){
        Reconstructor block = (Reconstructor)build.block;
        FactorySnapshot.Builder s = c.snapshot.family(ProductionFamily.reconstructor);
        float speed = unitBuildSpeed(build);
        float nominal = block.constructTime > 0f ? ProductionRates.TICKS_PER_SECOND * c.timeScale * speed / block.constructTime : 0f;
        if(speed > 0f) s.craftTimeSeconds(block.constructTime / ProductionRates.TICKS_PER_SECOND / speed);

        if(build.payload == null || build.payload.unit == null){
            s.gate(ProductionGate.waitingForUnit, acceptedInputs(block));
            return;
        }

        UnitType inside = build.payload.unit.type;
        if(build.hasUpgrade(inside)){
            UnitType result = build.upgrade(inside);
            float current = build.efficiency > 0f ? nominal * build.efficiency : 0f;
            s.output(new OutputState(ResourceKind.unit, result.localizedName, result.name, nominal, current, -1f, -1f, false));
            s.fact(Fact.resource("fact.unit-input", unitRef(inside)));
            s.fact(Fact.resource("fact.unit", unitRef(result)));
            if(block.constructTime > 0f){
                s.fact(Fact.of("fact.progress", Fact.Format.percent, Math.min(build.progress / block.constructTime, 1f)));
            }
            unitCapFact(s, build, result);
        }else{
            //an upgraded unit, or one this block cannot upgrade, is on its way out
            s.fact(Fact.resource("fact.unit", unitRef(inside)));
            if(payloadAtExit(build)) gateFinishedUnit(s, build, inside);
        }
    }

    static void assembler(Context c, UnitAssembler.UnitAssemblerBuild build){
        UnitAssembler block = (UnitAssembler)build.block;
        FactorySnapshot.Builder s = c.snapshot.family(ProductionFamily.unitAssembler);
        if(block.plans.isEmpty()) return;

        UnitAssembler.AssemblerUnitPlan plan = build.plan();
        UnitType unit = plan.unit;
        float speed = unitBuildSpeed(build);
        int drones = Math.max(block.dronesCreated, 0);
        int ready = 0;
        for(int i = 0; i < build.units.size; i++){
            Unit drone = build.units.get(i);
            if(drone != null && drone.controller() instanceof AssemblerAI ai && ai.inPosition()) ready++;
        }
        boolean canCreate = Units.canCreate(build.team, unit);

        float nominal = plan.time > 0f ? ProductionRates.TICKS_PER_SECOND * c.timeScale * speed / plan.time : 0f;
        //progress += edelta() * unitBuildSpeed * (drones in position / dronesCreated) / plan.time
        float current = build.efficiency > 0f && !build.wasOccupied && canCreate && drones > 0
            ? nominal * build.efficiency * ready / drones : 0f;

        s.output(new OutputState(ResourceKind.unit, unit.localizedName, unit.name, nominal, current, -1f, -1f, build.wasOccupied));
        if(speed > 0f) s.craftTimeSeconds(plan.time / ProductionRates.TICKS_PER_SECOND / speed);
        s.fact(Fact.resource("fact.unit", unitRef(unit)));
        s.fact(Fact.amountOf("fact.drones-ready", ready, drones).tone(ready < drones ? Fact.Tone.warning : Fact.Tone.normal));
        s.fact(Fact.of("fact.progress", Fact.Format.percent, Math.min(Math.max(build.progress, 0f), 1f)));
        unitCapFact(s, build, unit);

        //UnitAssemblerBuild.shouldConsume() checks the spawn area and the unit cap before anything else
        if(!canCreate){
            s.gate(ProductionGate.unitCapReached, List.of(unitRef(unit)));
        }else if(build.wasOccupied){
            s.outputBufferFull(true).gate(ProductionGate.outputFull, List.of(unitRef(unit)));
        }
    }

    private static void gateFinishedUnit(FactorySnapshot.Builder s, Building build, UnitType type){
        ResourceRef unit = unitRef(type);
        if(!Units.canCreate(build.team, type)){
            s.gate(ProductionGate.unitCapReached, List.of(unit));
        }else{
            s.outputBufferFull(true).gate(ProductionGate.outputFull, List.of(unit));
        }
    }

    /** Unit types this reconstructor accepts, as {@code ReconstructorBuild.acceptPayload} filters them. */
    private static List<ResourceRef> acceptedInputs(Reconstructor block){
        List<ResourceRef> result = new ArrayList<>();
        for(int i = 0; i < block.upgrades.size; i++){
            UnitType[] upgrade = block.upgrades.get(i);
            if(upgrade == null || upgrade.length < 2 || upgrade[0] == null || upgrade[1] == null) continue;
            if(upgrade[1].isBanned() || upgrade[0].isBanned()) continue;
            ResourceRef input = unitRef(upgrade[0]);
            if(!result.contains(input)) result.add(input);
        }
        return result;
    }
}
