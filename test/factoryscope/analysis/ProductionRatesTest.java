package factoryscope.analysis;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

class ProductionRatesTest{
    private static final float TOLERANCE = 0.0001f;

    @Test
    void siliconSmelterProducesThreeQuartersOfAnItemPerSecond(){
        //vanilla silicon smelter: craftTime 40 ticks, one silicon per cycle
        float craftsPerSecond = ProductionRates.nominalCraftsPerSecond(40f, 1f);

        assertEquals(1.5f, craftsPerSecond, TOLERANCE);
        assertEquals(0.75f, craftsPerSecond * 0.5f, TOLERANCE, "a half-output crafter halves the rate");
    }

    @Test
    void overdriveScalesTheNominalRate(){
        assertEquals(2.25f, ProductionRates.nominalCraftsPerSecond(40f, 1.5f), TOLERANCE);
    }

    @Test
    void progressIncrementIsIndependentOfFrameLength(){
        //the same real rate must come out whether the game ran one long frame or two short ones
        float atSixtyFps = ProductionRates.perSecondFromProgress(0.025f, 1f);
        float atThirtyFps = ProductionRates.perSecondFromProgress(0.05f, 2f);

        assertEquals(1.5f, atSixtyFps, TOLERANCE);
        assertEquals(atSixtyFps, atThirtyFps, TOLERANCE);
    }

    @Test
    void aPausedFrameYieldsNoRateInsteadOfInfinity(){
        assertEquals(0f, ProductionRates.perSecondFromProgress(0.025f, 0f), TOLERANCE);
    }

    @Test
    void liquidStackAmountsAreTicksNotSeconds(){
        //GenericCrafter adds outputAmount * getProgressIncrease(1f) per frame, so 0.1/tick is 6/second
        assertEquals(6f, ProductionRates.perTickToPerSecond(0.1f, 1f), TOLERANCE);
        assertEquals(9f, ProductionRates.perTickToPerSecond(0.1f, 1.5f), TOLERANCE);
    }

    @Test
    void speedMultiplierRecoversATerrainBoost(){
        //a crafter running at 60% efficiency but 1.2x boosted produces 1.2 * 0.6 of nominal
        float nominal = 1.5f;
        float current = nominal * 0.6f * 1.2f;

        assertEquals(1.2f, ProductionRates.speedMultiplier(current, nominal, 0.6f), TOLERANCE);
    }

    @Test
    void speedMultiplierIsUnknownWhileStopped(){
        assertTrue(Float.isNaN(ProductionRates.speedMultiplier(0f, 1.5f, 0f)),
            "a stopped building carries no information about its speed multiplier");
    }

    @Test
    void blockEfficiencyScaleIsTheRatioOfEfficiencyToPotential(){
        assertEquals(0.5f, ProductionRates.blockEfficiencyScale(0.25f, 0.5f, true), TOLERANCE);
    }

    @Test
    void blockEfficiencyScaleIsUnknownWhileProductionIsGatedOff(){
        //the gate forces efficiency to zero, which destroys the relationship with potentialEfficiency
        assertTrue(Float.isNaN(ProductionRates.blockEfficiencyScale(0f, 1f, false)));
        assertTrue(Float.isNaN(ProductionRates.blockEfficiencyScale(0f, 0f, true)));
    }
}
