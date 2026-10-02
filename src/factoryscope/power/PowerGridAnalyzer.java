package factoryscope.power;

import factoryscope.area.AreaStatus;

import java.util.*;

/**
 * Interprets immutable engine snapshots. Grid membership and all measured values come from Mindustry;
 * this class only assigns concise factual labels to them.
 */
public final class PowerGridAnalyzer{
    private static final float EPSILON = 0.0001f;

    private PowerGridAnalyzer(){
    }

    public static PowerGridResult analyze(PowerGridSnapshot snapshot){
        Objects.requireNonNull(snapshot, "snapshot");
        List<PowerFinding> findings = new ArrayList<>();
        int generatorProblems = 0, generatorOperating = 0, generatorStopped = 0, generatorUnclassified = 0;
        for(PowerMemberSnapshot producer : snapshot.producers){
            if(producer.diagnostic == null){
                generatorUnclassified++;
                continue;
            }
            AreaStatus status = AreaStatus.of(producer.diagnostic.reason());
            if(status == AreaStatus.operating) generatorOperating++;
            else if(status == AreaStatus.disabled || status == AreaStatus.inoperable) generatorStopped++;
            else if(status.problem()) generatorProblems++;
            else generatorUnclassified++;
        }

        PowerGridState state;
        if(snapshot.cheatPowered){
            state = PowerGridState.cheatPowered;
            findings.add(PowerFinding.CHEAT_POWER_RULE);
        }else if(!snapshot.hasMetrics){
            state = PowerGridState.unavailable;
            findings.add(PowerFinding.METRICS_UNAVAILABLE);
            if(!snapshot.visibilityComplete) findings.add(PowerFinding.VISIBILITY_INCOMPLETE);
        }else if(snapshot.demandPerSecond <= EPSILON){
            state = PowerGridState.noCurrentDemand;
            findings.add(PowerFinding.NO_CURRENT_DEMAND);
        }else if(snapshot.generationPerSecond <= EPSILON){
            state = PowerGridState.noGeneration;
            findings.add(PowerFinding.NO_GENERATION);
            if(snapshot.satisfaction < 0.999f){
                findings.add(PowerFinding.GRID_UNDERPOWERED);
            }else{
                //A current output reading may sit on the boundary after a battery/diode-supported update.
                findings.add(PowerFinding.GENERATION_BELOW_DEMAND);
            }
        }else if(snapshot.satisfaction < 0.999f){
            state = PowerGridState.underpowered;
            findings.add(PowerFinding.GRID_UNDERPOWERED);
        }else if(snapshot.generationPerSecond + EPSILON < snapshot.demandPerSecond){
            state = PowerGridState.generationBelowDemand;
            findings.add(PowerFinding.GENERATION_BELOW_DEMAND);
        }else if(snapshot.generationPerSecond > snapshot.demandPerSecond + EPSILON){
            state = PowerGridState.surplus;
            findings.add(PowerFinding.GENERATION_SURPLUS);
        }else{
            state = PowerGridState.balanced;
        }

        if(snapshot.hasMetrics && snapshot.demandPerSecond > EPSILON
            && snapshot.generationPerSecond + EPSILON < snapshot.demandPerSecond
            && snapshot.batteryStored > EPSILON && !findings.contains(PowerFinding.BATTERY_RESERVES_PRESENT)){
            findings.add(PowerFinding.BATTERY_RESERVES_PRESENT);
        }
        if(generatorProblems > 0) findings.add(PowerFinding.GENERATOR_DIAGNOSTIC_PROBLEMS);
        if(snapshot.hasMetrics && !snapshot.balanceReliable) findings.add(PowerFinding.BALANCE_COLLECTING);
        if(!snapshot.visibilityComplete && !findings.contains(PowerFinding.VISIBILITY_INCOMPLETE)){
            findings.add(PowerFinding.VISIBILITY_INCOMPLETE);
        }
        return new PowerGridResult(snapshot, state, findings, generatorProblems,
            generatorOperating, generatorStopped, generatorUnclassified);
    }
}
