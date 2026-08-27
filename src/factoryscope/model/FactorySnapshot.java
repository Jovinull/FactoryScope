package factoryscope.model;

import java.util.*;

/**
 * Immutable picture of one building at one instant, expressed without any Mindustry types.
 *
 * <p>Everything the diagnostic engine needs must be recorded here by the probe, which keeps the engine
 * testable and keeps game-API knowledge in a single place.
 */
public final class FactorySnapshot{
    /** Localized block name. */
    public final String blockName;
    /** Tile coordinates, purely for display. */
    public final int tileX, tileY;
    public final SupportLevel support;

    /** {@code Building.enabled} - false when switched off, whether by a player, by logic, or by the game. */
    public final boolean enabled;
    /**
     * {@code Building.allowUpdate()} - false when the game itself refuses to run the building, which is
     * what {@code checkAllowUpdate()} turns into {@code enabled = false}. Distinguishing the two is the
     * only way to avoid telling a player that a block outside the playable area was switched off by hand.
     */
    public final boolean updateAllowed;
    /** {@code Building.efficiency} - the value that actually scales production this tick. */
    public final float efficiency;
    /** {@code Building.potentialEfficiency} - efficiency ignoring the shouldConsume()/productionValid() gate. */
    public final float potentialEfficiency;
    /** {@code Building.optionalEfficiency} - includes optional/boost consumers. */
    public final float optionalEfficiency;
    /** {@code Building.shouldConsume()} - false means the building refuses to start a new cycle. */
    public final boolean shouldConsume;
    /** {@code Building.productionValid()} - a block-specific precondition, true for all vanilla blocks. */
    public final boolean productionValid;
    /** {@code Building.timeScale()} - overdrive multiplier; 1 when not boosted. */
    public final float timeScale;
    /**
     * True when the team rule {@code cheat} is on ("blocks do not require power or resources").
     * {@code updateConsumption()} then short-circuits every consumer, so no input can be a cause.
     * Note this is <em>not</em> the sandbox gamemode: sandbox only makes construction free.
     */
    public final boolean consumersBypassed;
    public final boolean hasConsumers;

    /**
     * Ratio of {@code efficiency} to {@code potentialEfficiency}, i.e. the block-specific multiplier applied
     * by {@code Building.efficiencyScale()} (heat, terrain attributes, and so on). {@link Float#NaN} when it
     * cannot be isolated, which is the case whenever production is gated off for another reason.
     */
    public final float blockEfficiencyScale;
    /**
     * Extra speed multiplier applied by the block on top of efficiency, derived from
     * {@code getProgressIncrease}. {@link Float#NaN} when it cannot be isolated.
     */
    public final float craftSpeedMultiplier;

    /** True only when FactoryScope verified, against the rules of the block itself, that outputs cannot be stored. */
    public final boolean outputBufferFull;
    /** Seconds per production cycle at 100% efficiency and normal time scale, or -1 when unknown. */
    public final float craftTimeSeconds;

    public final List<ResourceState> inputs;
    public final List<OutputState> outputs;
    /** Null when the block does not consume power. */
    public final PowerState power;

    private FactorySnapshot(Builder b){
        this.blockName = b.blockName;
        this.tileX = b.tileX;
        this.tileY = b.tileY;
        this.support = b.support;
        this.enabled = b.enabled;
        this.updateAllowed = b.updateAllowed;
        this.efficiency = b.efficiency;
        this.potentialEfficiency = b.potentialEfficiency;
        this.optionalEfficiency = b.optionalEfficiency;
        this.shouldConsume = b.shouldConsume;
        this.productionValid = b.productionValid;
        this.timeScale = b.timeScale;
        this.consumersBypassed = b.consumersBypassed;
        this.hasConsumers = b.hasConsumers;
        this.blockEfficiencyScale = b.blockEfficiencyScale;
        this.craftSpeedMultiplier = b.craftSpeedMultiplier;
        this.outputBufferFull = b.outputBufferFull;
        this.craftTimeSeconds = b.craftTimeSeconds;
        this.inputs = List.copyOf(b.inputs);
        this.outputs = List.copyOf(b.outputs);
        this.power = b.power;
    }

    /** Mandatory inputs only; optional and boost consumers never stop a factory. */
    public List<ResourceState> mandatoryInputs(){
        List<ResourceState> result = new ArrayList<>(inputs.size());
        for(ResourceState input : inputs){
            if(!input.optional) result.add(input);
        }
        return result;
    }

    public List<ResourceState> optionalInputs(){
        List<ResourceState> result = new ArrayList<>();
        for(ResourceState input : inputs){
            if(input.optional) result.add(input);
        }
        return result;
    }

    public boolean hasKnownProduction(){
        return support == SupportLevel.full && !outputs.isEmpty();
    }

    public static Builder builder(String blockName){
        return new Builder(blockName);
    }

    public static final class Builder{
        private final String blockName;
        private int tileX, tileY;
        private SupportLevel support = SupportLevel.minimal;
        private boolean enabled = true;
        private boolean updateAllowed = true;
        private float efficiency;
        private float potentialEfficiency;
        private float optionalEfficiency;
        private boolean shouldConsume = true;
        private boolean productionValid = true;
        private float timeScale = 1f;
        private boolean consumersBypassed;
        private boolean hasConsumers;
        private float blockEfficiencyScale = Float.NaN;
        private float craftSpeedMultiplier = Float.NaN;
        private boolean outputBufferFull;
        private float craftTimeSeconds = -1f;
        private final List<ResourceState> inputs = new ArrayList<>();
        private final List<OutputState> outputs = new ArrayList<>();
        private PowerState power;

        private Builder(String blockName){
            this.blockName = blockName;
        }

        public Builder position(int x, int y){
            this.tileX = x;
            this.tileY = y;
            return this;
        }

        public Builder support(SupportLevel support){
            this.support = support;
            return this;
        }

        public Builder enabled(boolean enabled){
            this.enabled = enabled;
            return this;
        }

        public Builder updateAllowed(boolean updateAllowed){
            this.updateAllowed = updateAllowed;
            return this;
        }

        public Builder efficiency(float efficiency, float potentialEfficiency, float optionalEfficiency){
            this.efficiency = efficiency;
            this.potentialEfficiency = potentialEfficiency;
            this.optionalEfficiency = optionalEfficiency;
            return this;
        }

        public Builder shouldConsume(boolean shouldConsume){
            this.shouldConsume = shouldConsume;
            return this;
        }

        public Builder productionValid(boolean productionValid){
            this.productionValid = productionValid;
            return this;
        }

        public Builder timeScale(float timeScale){
            this.timeScale = timeScale;
            return this;
        }

        public Builder consumersBypassed(boolean consumersBypassed){
            this.consumersBypassed = consumersBypassed;
            return this;
        }

        public Builder hasConsumers(boolean hasConsumers){
            this.hasConsumers = hasConsumers;
            return this;
        }

        public Builder blockEfficiencyScale(float scale){
            this.blockEfficiencyScale = scale;
            return this;
        }

        public Builder craftSpeedMultiplier(float multiplier){
            this.craftSpeedMultiplier = multiplier;
            return this;
        }

        public Builder outputBufferFull(boolean full){
            this.outputBufferFull = full;
            return this;
        }

        public Builder craftTimeSeconds(float seconds){
            this.craftTimeSeconds = seconds;
            return this;
        }

        public Builder input(ResourceState state){
            this.inputs.add(state);
            return this;
        }

        public Builder output(OutputState state){
            this.outputs.add(state);
            return this;
        }

        public Builder power(PowerState power){
            this.power = power;
            return this;
        }

        public FactorySnapshot build(){
            return new FactorySnapshot(this);
        }
    }
}
