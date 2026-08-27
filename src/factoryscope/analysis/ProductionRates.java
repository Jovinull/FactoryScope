package factoryscope.analysis;

/**
 * Rate arithmetic for Mindustry production, kept separate from the game so it can be tested.
 *
 * <h2>Units</h2>
 * Mindustry works in ticks. One second of game time at normal speed is {@value #TICKS_PER_SECOND}
 * ticks, and {@code Time.delta} is the length of the current frame expressed in ticks (so it is 1 at
 * 60 FPS, 2 at 30 FPS, and so on).
 *
 * <p>A {@code GenericCrafter} adds {@code getProgressIncrease(craftTime)} to its progress each frame and
 * crafts when progress reaches 1. Converting that per-frame increment into a per-second rate therefore
 * means dividing by the frame length and multiplying by the tick rate - see
 * {@link #perSecondFromProgress}.
 *
 * <p>Liquid outputs work differently: {@code updateTile} adds {@code outputAmount * getProgressIncrease(1f)}
 * every frame, so the stack amount is already a per-tick figure. That is why the game shows liquid rates
 * as {@code amount * 60} per second.
 */
public final class ProductionRates{
    public static final float TICKS_PER_SECOND = 60f;

    private ProductionRates(){
    }

    /**
     * Converts a per-frame progress increment into a per-second rate.
     *
     * @param progressPerFrame value returned by {@code Building.getProgressIncrease(baseTime)}
     * @param deltaTicks       length of the frame in ticks, i.e. {@code Time.delta}
     */
    public static float perSecondFromProgress(float progressPerFrame, float deltaTicks){
        if(deltaTicks <= 0f || !finite(progressPerFrame) || !finite(deltaTicks)) return 0f;
        return progressPerFrame / deltaTicks * TICKS_PER_SECOND;
    }

    /** Cycles per second the block would run at 100% efficiency under the given time scale. */
    public static float nominalCraftsPerSecond(float craftTimeTicks, float timeScale){
        if(craftTimeTicks <= 0f || !finite(craftTimeTicks) || !finite(timeScale)) return 0f;
        return TICKS_PER_SECOND / craftTimeTicks * timeScale;
    }

    /**
     * Converts a per-tick amount into a per-second rate at 100% efficiency.
     *
     * <p>Liquid stacks and {@code ConsumePower.usage} are both stored per tick, and both are multiplied
     * by {@code delta()} - which already includes the time scale - when the game applies them.
     */
    public static float perTickToPerSecond(float amountPerTick, float timeScale){
        if(!finite(amountPerTick) || !finite(timeScale)) return 0f;
        return amountPerTick * TICKS_PER_SECOND * timeScale;
    }

    /**
     * Recovers the block-specific speed multiplier the game applies on top of efficiency, such as the
     * terrain boost of an {@code AttributeCrafter}. Returns {@link Float#NaN} when it cannot be isolated,
     * which is the case whenever the building is already stopped.
     */
    public static float speedMultiplier(float currentCraftsPerSecond, float nominalCraftsPerSecond, float efficiency){
        if(efficiency <= 0f || nominalCraftsPerSecond <= 0f) return Float.NaN;
        float multiplier = currentCraftsPerSecond / (nominalCraftsPerSecond * efficiency);
        return finite(multiplier) ? multiplier : Float.NaN;
    }

    /**
     * Isolates {@code Building.efficiencyScale()}.
     *
     * <p>{@code updateConsumption()} assigns {@code potentialEfficiency = efficiency} <em>before</em>
     * {@code updateEfficiencyMultiplier()} scales {@code efficiency}, so their ratio is exactly that
     * multiplier - but only while production is not gated off for another reason, because the gate
     * forces {@code efficiency} to zero and destroys the relationship.
     */
    public static float blockEfficiencyScale(float efficiency, float potentialEfficiency, boolean productionGateOpen){
        if(!productionGateOpen || potentialEfficiency <= 0f) return Float.NaN;
        float scale = efficiency / potentialEfficiency;
        return finite(scale) ? scale : Float.NaN;
    }

    private static boolean finite(float value){
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }
}
