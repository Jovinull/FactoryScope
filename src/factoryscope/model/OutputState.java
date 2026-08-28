package factoryscope.model;

/**
 * One product of the inspected factory, with the rates FactoryScope was able to derive.
 *
 * <p>Both rates are computed from live game state, not measured over time. {@link #expectedPerSecond}
 * is what the building is producing at this instant; {@link #theoreticalPerSecond} is what the same
 * building would produce at 100% efficiency under its current time scale.
 */
public final class OutputState{
    public final ResourceKind kind;
    public final String name;
    /** Internal Mindustry content id, used by the panel to resolve an icon. */
    public final String contentId;
    public final float theoreticalPerSecond;
    public final float expectedPerSecond;
    /** Amount sitting in the building's own output buffer, or -1 when unknown. */
    public final float stored;
    /** Buffer size, or -1 when unknown. */
    public final float capacity;
    /** True when this specific product is what stops the building from starting a new cycle. */
    public final boolean bufferFull;

    public OutputState(ResourceKind kind, String name, String contentId,
                       float theoreticalPerSecond, float expectedPerSecond,
                       float stored, float capacity, boolean bufferFull){
        this.kind = kind;
        this.name = name;
        this.contentId = contentId;
        this.theoreticalPerSecond = theoreticalPerSecond;
        this.expectedPerSecond = expectedPerSecond;
        this.stored = stored;
        this.capacity = capacity;
        this.bufferFull = bufferFull;
    }

    /** Stable identity plus display name, for findings that need to name this product. */
    public ResourceRef ref(){
        return new ResourceRef(kind, contentId, name);
    }

    public boolean hasBuffer(){
        return stored >= 0f && capacity > 0f;
    }

    @Override
    public String toString(){
        return name + " " + expectedPerSecond + "/" + theoreticalPerSecond + "/s";
    }
}
