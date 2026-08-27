package factoryscope.analysis;

import factoryscope.model.*;

/**
 * Snapshot fixtures for the diagnostic tests.
 *
 * <p>Values mirror what {@code Building.updateConsumption()} would have produced, so a test that sets
 * up a shortage also has to set the efficiency the game would have computed from it. That coupling is
 * deliberate: it keeps the fixtures honest.
 */
final class Factories{
    static final String SILICON_SMELTER = "Silicon Smelter";

    private Factories(){
    }

    /** A crafter with sand, coal and power, all satisfied, running at full speed. */
    static FactorySnapshot.Builder healthySmelter(){
        return FactorySnapshot.builder(SILICON_SMELTER)
            .support(SupportLevel.full)
            .enabled(true)
            .shouldConsume(true)
            .productionValid(true)
            .efficiency(1f, 1f)
            .blockEfficiencyScale(1f)
            .craftSpeedMultiplier(1f)
            .craftTimeSeconds(0.667f)
            .input(item("Sand", 1f))
            .input(item("Coal", 1f))
            .input(power(1f))
            .output(silicon(false));
    }

    static ResourceState item(String name, float satisfaction){
        return ResourceState.of(ResourceKind.item, name)
            .satisfaction(satisfaction)
            .amounts(satisfaction >= 1f ? 6f : 0f, 5f, RateUnit.perCraft)
            .build();
    }

    static ResourceState liquid(String name, float satisfaction){
        return ResourceState.of(ResourceKind.liquid, name)
            .satisfaction(satisfaction)
            .amounts(satisfaction * 10f, 12f, RateUnit.perSecond)
            .build();
    }

    static ResourceState power(float satisfaction){
        return ResourceState.of(ResourceKind.power, "Power")
            .satisfaction(satisfaction)
            .amounts(-1f, 30f, RateUnit.perSecond)
            .build();
    }

    static ResourceState booster(String name, float satisfaction){
        return ResourceState.of(ResourceKind.item, name)
            .optional(true)
            .satisfaction(satisfaction)
            .amounts(0f, 1f, RateUnit.perCraft)
            .build();
    }

    static ResourceState unknownConsumer(String name, float satisfaction, boolean provisional){
        return ResourceState.of(ResourceKind.other, name)
            .recognised(false)
            .provisional(provisional)
            .satisfaction(satisfaction)
            .build();
    }

    static OutputState silicon(boolean bufferFull){
        return new OutputState(ResourceKind.item, "Silicon", "silicon",
            0.75f, bufferFull ? 0f : 0.75f, bufferFull ? 10f : 3f, 10f, bufferFull);
    }
}
