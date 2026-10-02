package factoryscope.power;

import factoryscope.analysis.DiagnosticResult;
import factoryscope.area.BuildingRef;

import java.util.Objects;

/** Immutable visible member of the engine graph; one building may occupy more than one role. */
public final class PowerMemberSnapshot{
    public final BuildingRef ref;
    public final boolean producer;
    public final boolean consumer;
    public final boolean battery;
    /** Current producer output in power/second; meaningful only when {@link #producer}. */
    public final float generationPerSecond;
    /** Current graph request in power/second; meaningful only when {@link #consumer}. */
    public final float demandPerSecond;
    /** Stored energy and capacity; meaningful only when {@link #battery}. */
    public final float batteryStored;
    public final float batteryCapacity;
    /** Null when FactoryAnalyzer could not or did not diagnose this member. */
    public final DiagnosticResult diagnostic;

    public PowerMemberSnapshot(BuildingRef ref, boolean producer, boolean consumer, boolean battery,
                               float generationPerSecond, float demandPerSecond,
                               float batteryStored, float batteryCapacity,
                               DiagnosticResult diagnostic){
        this.ref = Objects.requireNonNull(ref, "ref");
        this.producer = producer;
        this.consumer = consumer;
        this.battery = battery;
        this.generationPerSecond = generationPerSecond;
        this.demandPerSecond = demandPerSecond;
        this.batteryStored = batteryStored;
        this.batteryCapacity = batteryCapacity;
        this.diagnostic = diagnostic;
    }
}
