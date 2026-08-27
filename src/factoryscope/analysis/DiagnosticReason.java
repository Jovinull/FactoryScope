package factoryscope.analysis;

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
    /** The building is switched off, by a player or by a logic processor. */
    disabled,
    /** The building will not start a new cycle because its own output buffer has nowhere to go. */
    outputBlocked,
    /** A mandatory item consumer cannot be satisfied. */
    missingItemInput,
    /** A mandatory liquid consumer cannot be satisfied. */
    missingLiquidInput,
    /** The power grid is not covering this building's demand. */
    insufficientPower,
    /** A recognised consumer other than items, liquids or power is limiting production. */
    otherConsumerLimited,
    /** A block-specific multiplier (heat, terrain attributes, ...) is holding efficiency down. */
    blockConditionLimited,
    /** Production has stopped and FactoryScope cannot attribute it to any input it understands. */
    haltedUnknownCause,
    /** The building runs, but FactoryScope has no production model for this block type. */
    limitedSupport,
    /** Everything the mod can check is satisfied. */
    active;

    /** Lower is more important. Used to order findings deterministically. */
    public int priority(){
        return ordinal();
    }

    /** {@code missingItemInput} becomes {@code missing-item-input}, so bundle keys stay readable. */
    public String slug(){
        StringBuilder result = new StringBuilder(name().length() + 4);
        for(char c : name().toCharArray()){
            if(Character.isUpperCase(c)){
                result.append('-').append(Character.toLowerCase(c));
            }else{
                result.append(c);
            }
        }
        return result.toString();
    }
}
