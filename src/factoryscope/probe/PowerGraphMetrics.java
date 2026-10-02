package factoryscope.probe;

import arc.util.Time;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.world.blocks.power.PowerGraph;
import mindustry.world.consumers.ConsumePower;

import java.util.*;

/** Shared read-only conversion of Mindustry's frame-integrated PowerGraph values. */
final class PowerGraphMetrics{
    private static final float MIN_FRAME_TICKS = 0.0001f;

    private PowerGraphMetrics(){
    }

    static boolean cheatPowered(PowerGraph graph){
        return graph != null && !graph.consumers.isEmpty() && graph.consumers.first().cheating();
    }

    /** Fails closed before exposing a pooled graph metric to a single-building inspector. */
    static boolean completelyVisible(PowerGraph graph, Team viewer){
        if(graph == null || viewer == null) return false;
        Set<Building> members = Collections.newSetFromMap(new IdentityHashMap<>());
        for(Building build : graph.all){
            if(build == null || !build.isValid() || build.power == null || build.power.graph != graph
                || build.team != viewer || !MindustryFactoryProbe.canInspect(build, viewer)) return false;
            members.add(build);
        }
        if(members.isEmpty()) return false;
        return containsOnlyMembers(graph.producers, members)
            && containsOnlyMembers(graph.consumers, members)
            && containsOnlyMembers(graph.batteries, members);
    }

    private static boolean containsOnlyMembers(arc.struct.Seq<Building> role, Set<Building> members){
        for(Building build : role) if(!members.contains(build)) return false;
        return true;
    }

    static float generationPerSecond(PowerGraph graph){
        float fallback = 0f;
        if(Time.delta <= MIN_FRAME_TICKS){
            for(Building producer : graph.producers){
                fallback += generationPerSecond(producer) / 60f;
            }
        }
        return normalize(graph.getPowerProduced(), fallback);
    }

    static float demandPerSecond(PowerGraph graph){
        float fallback = 0f;
        if(Time.delta <= MIN_FRAME_TICKS){
            for(Building consumer : graph.consumers){
                if(!consumer.shouldConsumePower) continue;
                fallback += demandPerSecond(consumer) / 60f;
            }
        }
        return normalize(graph.getPowerNeeded(), fallback);
    }

    static float balancePerSecond(PowerGraph graph){
        return graph.getPowerBalance() * 60f;
    }

    static float batteryStored(PowerGraph graph){
        return graph.getBatteryStored();
    }

    static float batteryCapacity(PowerGraph graph){
        return graph.getTotalBatteryCapacity();
    }

    static float satisfaction(PowerGraph graph){
        return graph.getSatisfaction();
    }

    static boolean balanceReliable(PowerGraph graph){
        return graph.hasPowerBalanceSamples();
    }

    static boolean finiteMetrics(float generation, float demand, float satisfaction,
                                 float balance, float stored, float capacity){
        return finite(generation) && finite(demand) && finite(satisfaction)
            && finite(balance) && finite(stored) && finite(capacity);
    }

    static float generationPerSecond(Building producer){
        float integrated = producer.getPowerProduction() * producer.delta();
        return normalize(integrated, producer.getPowerProduction() * producer.timeScale());
    }

    static float demandPerSecond(Building consumer){
        if(!consumer.shouldConsumePower) return 0f;
        ConsumePower power = consumer.block.consPower;
        float requested = power.requestedPower(consumer);
        return normalize(requested * consumer.delta(), requested * consumer.timeScale());
    }

    private static float normalize(float frameIntegrated, float pausedFallback){
        float frameTicks = Time.delta;
        //Only a finite, non-negative zero delta represents a paused simulation. NaN/Infinity (or a
        //negative delta supplied by a mod) is not a valid basis for manufacturing a zero rate.
        if(!finite(frameTicks) || frameTicks < 0f) return Float.NaN;
        if(frameTicks > MIN_FRAME_TICKS && finite(frameTicks)){
            if(!finite(frameIntegrated)) return Float.NaN;
            float normalized = frameIntegrated / frameTicks * 60f;
            return finite(normalized) ? normalized : Float.NaN;
        }
        if(!finite(pausedFallback)) return Float.NaN;
        float normalized = pausedFallback * 60f;
        return finite(normalized) ? normalized : Float.NaN;
    }

    private static boolean finite(float value){
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }
}
