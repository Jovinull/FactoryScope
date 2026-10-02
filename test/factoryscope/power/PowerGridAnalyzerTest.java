package factoryscope.power;

import factoryscope.area.BuildingRef;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PowerGridAnalyzerTest{
    private static final BuildingRef ANCHOR = new BuildingRef(1, 2, "power-node", "Power Node", 1, 1);

    @Test
    void satisfactionAndGenerationDeficitRemainSeparateFacts(){
        PowerGridResult result = analyze(grid(false, true, 40f, 100f, 1f, 15f, 60f, 100f, true));

        assertEquals(PowerGridState.generationBelowDemand, result.state);
        assertTrue(result.has(PowerFinding.GENERATION_BELOW_DEMAND));
        assertTrue(result.has(PowerFinding.BATTERY_RESERVES_PRESENT));
        assertFalse(result.has(PowerFinding.GRID_UNDERPOWERED));
    }

    @Test
    void batteryStorageIsNotCountedAsGeneration(){
        PowerGridResult result = analyze(grid(false, true, 0f, 100f, 0f, 40f, 100f, 100f, true));

        assertEquals(0f, result.snapshot.generationPerSecond);
        assertEquals(PowerGridState.noGeneration, result.state);
        assertTrue(result.has(PowerFinding.NO_GENERATION));
        assertTrue(result.has(PowerFinding.GRID_UNDERPOWERED));
        assertFalse(result.has(PowerFinding.GENERATION_SURPLUS));
    }

    @Test
    void lowSatisfactionIsTheEngineAuthorityForUnderpoweredState(){
        PowerGridResult result = analyze(grid(false, true, 70f, 100f, 0.7f, 0f, 0f, 0f, true));

        assertEquals(PowerGridState.underpowered, result.state);
        assertTrue(result.has(PowerFinding.GRID_UNDERPOWERED));
        assertFalse(result.has(PowerFinding.BATTERY_RESERVES_PRESENT));
    }

    @Test
    void noDemandDoesNotBecomeInfiniteSurplus(){
        PowerGridResult result = analyze(grid(false, true, 100f, 0f, 1f, 0f, 10f, 10f, true));

        assertEquals(PowerGridState.noCurrentDemand, result.state);
        assertTrue(result.has(PowerFinding.NO_CURRENT_DEMAND));
        assertFalse(result.has(PowerFinding.GENERATION_SURPLUS));
    }

    @Test
    void generationSurplusIsReportedWithoutCallingItWaste(){
        PowerGridResult result = analyze(grid(false, true, 120f, 80f, 1f, 0f, 10f, 30f, true));

        assertEquals(PowerGridState.surplus, result.state);
        assertTrue(result.has(PowerFinding.GENERATION_SURPLUS));
    }

    @Test
    void cheatPoweredGridDoesNotExposeStaleRatesAsMeasurements(){
        PowerGridResult result = analyze(grid(true, false, 1f, 1f, 1f, 5f, 10f, 10f, false));

        assertEquals(PowerGridState.cheatPowered, result.state);
        assertTrue(result.has(PowerFinding.CHEAT_POWER_RULE));
        assertFalse(result.has(PowerFinding.BALANCE_COLLECTING));
    }

    @Test
    void failedMetricReadIsUnavailableRatherThanHealthy(){
        PowerGridResult result = analyze(grid(false, false, 0f, 0f, 0f, 0f, 0f, 0f, false));

        assertEquals(PowerGridState.unavailable, result.state);
        assertTrue(result.has(PowerFinding.METRICS_UNAVAILABLE));
        assertFalse(result.has(PowerFinding.NO_GENERATION));
    }

    @Test
    void unavailableSnapshotCannotExposePartialOrStaleAggregateMetrics(){
        PowerGridSnapshot snapshot = grid(false, false, 90f, 120f, 0.75f, 40f, 80f, -30f, true);

        assertEquals(0f, snapshot.generationPerSecond);
        assertEquals(0f, snapshot.demandPerSecond);
        assertEquals(0f, snapshot.satisfaction);
        assertEquals(0f, snapshot.batteryStored);
        assertEquals(0f, snapshot.batteryCapacity);
        assertFalse(snapshot.balanceReliable);
    }

    private static PowerGridResult analyze(PowerGridSnapshot snapshot){
        return PowerGridAnalyzer.analyze(snapshot);
    }

    private static PowerGridSnapshot grid(boolean cheat, boolean metrics, float generation, float demand,
                                          float satisfaction, float stored, float capacity, float balance,
                                          boolean balanceReliable){
        return new PowerGridSnapshot(0, ANCHOR, List.of(), List.of(), List.of(), List.of(), List.of(),
            1, false, cheat, true, metrics, generation, demand, satisfaction, balance, balanceReliable,
            stored, capacity);
    }
}
