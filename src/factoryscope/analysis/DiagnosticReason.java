package factoryscope.analysis;

import java.util.*;

/**
 * Why a factory is in the state it is in.
 *
 * <p>The declaration order is the resolution order used by {@link FactoryAnalyzer}: when several
 * findings apply at once, the one declared first wins. That order mirrors how Mindustry itself gates
 * production in {@code Building.updateConsumption()} and {@code Building.status()} - a disabled
 * building never even evaluates its consumers, and a building that refuses to consume never looks at
 * whether its inputs are present.
 */
public enum DiagnosticReason{
    /**
     * The game switched the building off itself because it cannot operate where it stands - outside the
     * playable area, on an unsupported environment, or on an inactive team.
     */
    inoperableHere,
    /** The building is switched off, by a player or by a logic processor. */
    disabled,
    /** There is nothing under or in front of the building that it can extract. */
    noResource,
    /** What the building stands on or faces is too hard for it, or excluded from it. */
    resourceTooHard,
    /** A unit factory has no unit selected, so it has nothing to build. */
    noPlanSelected,
    /** The building will not start a new cycle because its own output buffer has nowhere to go. */
    outputBlocked,
    /** The team cannot field another unit of the type this building makes. */
    unitCapReached,
    /** A reconstructor has no unit inside that it can upgrade. */
    waitingForUnit,
    /** A turret holds no usable ammunition. */
    noAmmo,
    /**
     * The building is not drawing its inputs, and FactoryScope has no production model for the block
     * that would explain whether that is normal.
     */
    notConsuming,
    /** A mandatory item consumer cannot be satisfied. */
    missingItemInput,
    /** A mandatory liquid consumer cannot be satisfied. */
    missingLiquidInput,
    /** A mandatory payload consumer - units or blocks carried in - cannot be satisfied. */
    missingPayloadInput,
    /** The power grid is not covering this building's demand. */
    insufficientPower,
    /** A recognised consumer other than items, liquids, payloads or power is limiting production. */
    otherConsumerLimited,
    /** A block-specific multiplier (heat, light, fuel quality, terrain attributes, ...) is holding output down. */
    blockConditionLimited,
    /** Production has stopped and FactoryScope cannot attribute it to any input it understands. */
    haltedUnknownCause,
    /** The building runs, but FactoryScope has no production model for this block type. */
    limitedSupport,
    /** A turret is loaded and ready but has no target; nothing is wrong. */
    turretIdle,
    /** Everything the mod can check is satisfied. */
    active;

    /**
     * Family-specific wordings of the same reason. A finding that carries one of these selects the
     * sentence written for that family, while the headline stays shared.
     */
    private static final Map<DiagnosticReason, List<String>> VARIANTS = Map.of(
        noResource, List.of("drill", "beam-drill", "wall-crafter", "pump"),
        resourceTooHard, List.of("drill", "beam-drill")
    );

    /** Lower is more important. Used to order findings deterministically. */
    public int priority(){
        return ordinal();
    }

    /** The family wordings this reason can carry; empty when it has a single wording. */
    public List<String> variants(){
        return VARIANTS.getOrDefault(this, List.of());
    }

    /** {@code missingItemInput} becomes {@code missing-item-input}, so bundle keys stay readable. */
    public String slug(){
        return slug(name());
    }

    /** The bundle-key form of a camel-case identifier. */
    public static String slug(String camel){
        StringBuilder result = new StringBuilder(camel.length() + 4);
        for(char c : camel.toCharArray()){
            if(Character.isUpperCase(c)){
                result.append('-').append(Character.toLowerCase(c));
            }else{
                result.append(c);
            }
        }
        return result.toString();
    }
}
