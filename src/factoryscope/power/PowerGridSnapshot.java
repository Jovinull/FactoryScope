package factoryscope.power;

import factoryscope.area.BuildingRef;

import java.util.*;

/**
 * Immutable, FactoryScope-owned copy of one current Mindustry PowerGraph. It never retains a live graph
 * or Building. Rates are power/second; battery values are stored energy/capacity, not rates.
 */
public final class PowerGridSnapshot{
    /** Stable only within this report: the deterministic ordinal after sorting grids by their anchor ref. */
    public final int id;
    public final BuildingRef anchor;
    public final List<PowerMemberSnapshot> members;
    public final List<PowerMemberSnapshot> producers;
    public final List<PowerMemberSnapshot> consumers;
    public final List<PowerMemberSnapshot> batteries;
    public final List<PowerConnection> connections;
    /** Count of visible graph members whose footprints intersect the selected area. */
    public final int selectedMemberCount;
    public final boolean extendsOutsideSelection;
    public final boolean cheatPowered;
    public final boolean visibilityComplete;
    public final boolean hasMetrics;
    public final float generationPerSecond;
    public final float demandPerSecond;
    /** Mindustry's most recent delivered satisfaction; buffered consumer status is not used here. */
    public final float satisfaction;
    /** Windowed graph balance; unlike generation and demand, this is averaged over engine samples. */
    public final float balancePerSecond;
    public final boolean balanceReliable;
    /** Recomputed read-only from currently enabled batteries. Units are stored power, not power/second. */
    public final float batteryStored;
    public final float batteryCapacity;

    public PowerGridSnapshot(int id, BuildingRef anchor, Collection<PowerMemberSnapshot> members,
                             Collection<PowerMemberSnapshot> producers,
                             Collection<PowerMemberSnapshot> consumers,
                             Collection<PowerMemberSnapshot> batteries,
                             Collection<PowerConnection> connections, int selectedMemberCount,
                             boolean extendsOutsideSelection, boolean cheatPowered, boolean visibilityComplete,
                             boolean hasMetrics,
                             float generationPerSecond, float demandPerSecond, float satisfaction,
                             float balancePerSecond, boolean balanceReliable,
                             float batteryStored, float batteryCapacity){
        this.id = id;
        this.anchor = Objects.requireNonNull(anchor, "anchor");
        this.members = List.copyOf(members);
        this.producers = List.copyOf(producers);
        this.consumers = List.copyOf(consumers);
        this.batteries = List.copyOf(batteries);
        this.connections = List.copyOf(connections);
        this.selectedMemberCount = selectedMemberCount;
        this.extendsOutsideSelection = extendsOutsideSelection;
        this.cheatPowered = cheatPowered;
        this.visibilityComplete = visibilityComplete;
        this.hasMetrics = hasMetrics;
        this.generationPerSecond = hasMetrics ? generationPerSecond : 0f;
        this.demandPerSecond = hasMetrics ? demandPerSecond : 0f;
        this.satisfaction = hasMetrics ? satisfaction : 0f;
        this.balancePerSecond = hasMetrics ? balancePerSecond : 0f;
        this.balanceReliable = hasMetrics && balanceReliable;
        this.batteryStored = hasMetrics ? batteryStored : 0f;
        this.batteryCapacity = hasMetrics ? batteryCapacity : 0f;
    }
}
