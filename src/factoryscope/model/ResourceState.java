package factoryscope.model;

/**
 * State of a single consumer requirement at the moment of the snapshot.
 *
 * <p>An item consumer that accepts several stacks produces one {@code ResourceState} per stack, so the
 * UI can point at the specific item that is missing.
 */
public final class ResourceState{
    public final ResourceKind kind;
    /** Localized resource name, or a description of the consumer when the resource is unknown. */
    public final String name;
    /**
     * Internal Mindustry content id of the resource ("sand", "water", ...), or null when there is none.
     * Kept as a plain string so this package stays free of game types; the panel resolves it to an icon.
     */
    public final String contentId;
    /** Fraction of the requirement that is currently met, clamped to [0, 1]. */
    public final float satisfaction;
    /** Optional/booster consumers never stop production; they only add efficiency. */
    public final boolean optional;
    /** False when FactoryScope did not recognise the consumer type and had to fall back to {@code Consume.efficiency}. */
    public final boolean recognised;
    /**
     * True when {@link #satisfaction} was derived from a value that can be distorted by the building
     * already sitting at zero efficiency. Such a reading may not be used to assert a cause on its own.
     */
    public final boolean provisional;
    /** Amount currently held by the building, or -1 when not applicable. */
    public final float stored;
    /** Amount the consumer needs, in {@link #unit}, or -1 when not applicable. */
    public final float required;
    public final RateUnit unit;

    private ResourceState(Builder builder){
        this.kind = builder.kind;
        this.name = builder.name;
        this.contentId = builder.contentId;
        this.satisfaction = clamp(builder.satisfaction);
        this.optional = builder.optional;
        this.recognised = builder.recognised;
        this.provisional = builder.provisional;
        this.stored = builder.stored;
        this.required = builder.required;
        this.unit = builder.unit;
    }

    /** Stable identity plus display name, for findings that need to name this input. */
    public ResourceRef ref(){
        return new ResourceRef(kind, contentId, name);
    }

    public boolean satisfied(){
        return satisfaction >= 1f - EPSILON;
    }

    public boolean missing(){
        return satisfaction <= EPSILON;
    }

    public boolean hasAmounts(){
        return unit != RateUnit.none && required >= 0f;
    }

    @Override
    public String toString(){
        return kind + ":" + name + "@" + satisfaction;
    }

    /** Satisfaction values are compared with this tolerance; floats coming out of the game are never exact. */
    public static final float EPSILON = 0.0001f;

    private static float clamp(float value){
        if(Float.isNaN(value)) return 0f;
        return value < 0f ? 0f : value > 1f ? 1f : value;
    }

    public static Builder of(ResourceKind kind, String name){
        return new Builder(kind, name);
    }

    public static final class Builder{
        private final ResourceKind kind;
        private final String name;
        private String contentId;
        private float satisfaction = 1f;
        private boolean optional;
        private boolean recognised = true;
        private boolean provisional;
        private float stored = -1f;
        private float required = -1f;
        private RateUnit unit = RateUnit.none;

        private Builder(ResourceKind kind, String name){
            this.kind = kind;
            this.name = name;
        }

        public Builder contentId(String contentId){
            this.contentId = contentId;
            return this;
        }

        public Builder satisfaction(float satisfaction){
            this.satisfaction = satisfaction;
            return this;
        }

        public Builder optional(boolean optional){
            this.optional = optional;
            return this;
        }

        public Builder recognised(boolean recognised){
            this.recognised = recognised;
            return this;
        }

        public Builder provisional(boolean provisional){
            this.provisional = provisional;
            return this;
        }

        public Builder amounts(float stored, float required, RateUnit unit){
            this.stored = stored;
            this.required = required;
            this.unit = unit;
            return this;
        }

        public ResourceState build(){
            return new ResourceState(this);
        }
    }
}
