package factoryscope.probe;

import arc.util.Time;
import mindustry.gen.Building;
import mindustry.world.blocks.power.PowerGraph;
import mindustry.world.consumers.ConsumePower;

/** Shared read-only conversion of Mindustry's frame-integrated PowerGraph values. */
final class PowerGraphMetrics{
    private static final float MIN_FRAME_TICKS = 0.0001f;

    private PowerGraphMetrics(){
    }

    static boolean cheatPowered(PowerGraph graph){
        return graph != null && !graph.consumers.isEmpty() && graph.consumers.first().cheating();
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
