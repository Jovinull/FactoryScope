package factoryscope.area;

import factoryscope.analysis.*;
import factoryscope.model.*;

/**
 * Fixtures for the area aggregation tests.
 *
 * <p>Every entry here is produced by running a real {@link FactorySnapshot} through the real
 * {@link FactoryAnalyzer}. Hand-written {@code DiagnosticResult}s would let the aggregation tests pass
 * against semantics the diagnostic engine does not actually have, which is exactly the sort of drift
 * that makes a test suite worse than none.
 */
final class Areas{
    private Areas(){
    }

    static final AreaSelection AREA = AreaSelection.of(0, 0, 40, 40);

    private static int nextTile = 1;

    /** A fresh position for each fixture, so no two entries accidentally share an identity. */
    static BuildingRef ref(String blockId, String blockName){
        int tile = nextTile++;
        return new BuildingRef(tile, tile, blockId, blockName, 1, 1);
    }

    static BuildingRef smelterRef(){
        return ref("silicon-smelter", "Silicon Smelter");
    }

    static AreaEntry entry(BuildingRef ref, FactorySnapshot snapshot){
        return new AreaEntry(ref, snapshot.support, FactoryAnalyzer.analyze(snapshot));
    }

    static AreaEntry entry(FactorySnapshot snapshot){
        return entry(smelterRef(), snapshot);
    }

    // ------------------------------------------------------------------ snapshots

    private static FactorySnapshot.Builder crafter(){
        return FactorySnapshot.builder("Silicon Smelter")
            .support(SupportLevel.full)
            .enabled(true)
            .shouldConsume(true)
            .productionValid(true)
            .efficiency(1f, 1f)
            .blockEfficiencyScale(1f)
            .craftSpeedMultiplier(1f)
            .craftTimeSeconds(0.667f);
    }

    static FactorySnapshot healthy(){
        return crafter()
            .input(item("sand", "Sand", 1f))
            .input(power(1f))
            .output(silicon(false))
            .build();
    }

    /** Stopped because the named item is absent; power is present so the blame is unambiguous. */
    static FactorySnapshot itemShortage(String id, String name){
        return crafter()
            .efficiency(0f, 0f)
            .blockEfficiencyScale(Float.NaN)
            .input(item(id, name, 0f))
            .input(power(1f))
            .output(silicon(false))
            .build();
    }

    static FactorySnapshot liquidShortage(String id, String name){
        return crafter()
            .efficiency(0f, 0f)
            .blockEfficiencyScale(Float.NaN)
            .input(liquid(id, name, 0f))
            .input(power(1f))
            .output(silicon(false))
            .build();
    }

    static FactorySnapshot powerShortage(){
        return crafter()
            .efficiency(0f, 0f)
            .blockEfficiencyScale(Float.NaN)
            .input(item("sand", "Sand", 1f))
            .input(power(0f))
            .output(silicon(false))
            .build();
    }

    /**
     * An item and power both at zero. The engine takes efficiency as the minimum over consumers, so
     * both are equally responsible and the analyser emits a finding for each.
     */
    static FactorySnapshot itemAndPowerShortage(String id, String name){
        return crafter()
            .efficiency(0f, 0f)
            .blockEfficiencyScale(Float.NaN)
            .input(item(id, name, 0f))
            .input(power(0f))
            .output(silicon(false))
            .build();
    }

    static FactorySnapshot outputBlocked(){
        return crafter()
            .shouldConsume(false)
            .efficiency(0f, 1f)
            .blockEfficiencyScale(Float.NaN)
            .outputBufferFull(true)
            .input(item("sand", "Sand", 1f))
            .input(power(1f))
            .output(silicon(true))
            .build();
    }

    static FactorySnapshot disabled(){
        return crafter()
            .enabled(false)
            .efficiency(0f, 0f)
            .blockEfficiencyScale(Float.NaN)
            .input(item("sand", "Sand", 1f))
            .output(silicon(false))
            .build();
    }

    /** A block with no consumers FactoryScope can reason about, such as a wall. */
    static FactorySnapshot limitedSupport(){
        return FactorySnapshot.builder("Titanium Wall")
            .support(SupportLevel.minimal)
            .efficiencyTracked(false)
            .build();
    }

    // ------------------------------------------------------------------ pieces

    static ResourceState item(String id, String name, float satisfaction){
        return ResourceState.of(ResourceKind.item, name)
            .contentId(id)
            .satisfaction(satisfaction)
            .amounts(satisfaction >= 1f ? 6f : 0f, 5f, RateUnit.perCraft)
            .build();
    }

    static ResourceState liquid(String id, String name, float satisfaction){
        return ResourceState.of(ResourceKind.liquid, name)
            .contentId(id)
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

    static OutputState silicon(boolean bufferFull){
        return new OutputState(ResourceKind.item, "Silicon", "silicon",
            0.75f, bufferFull ? 0f : 0.75f, bufferFull ? 10f : 3f, 10f, bufferFull);
    }
}
