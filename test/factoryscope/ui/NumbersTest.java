package factoryscope.ui;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

class NumbersTest{

    @Test
    void accumulatedFloatErrorIsNotShownToThePlayer(){
        assertEquals("0.75", Numbers.rate(0.74999994f));
        assertEquals("1.5", Numbers.rate(1.4999999f));
    }

    @Test
    void precisionFollowsMagnitude(){
        assertEquals("0.375", Numbers.rate(0.375f));
        assertEquals("12.35", Numbers.rate(12.345f));
        assertEquals("123.5", Numbers.rate(123.46f));
        assertEquals("1235", Numbers.rate(1234.5f));
    }

    @Test
    void wholeRatesLoseTheirTrailingZeros(){
        assertEquals("2", Numbers.rate(2f));
        assertEquals("0", Numbers.rate(0f));
    }

    @Test
    void amountsKeepWholeNumbersWhole(){
        assertEquals("6", Numbers.amount(6f));
        assertEquals("6", Numbers.amount(5.999f));
        assertEquals("6.25", Numbers.amount(6.25f));
    }

    @Test
    void percentagesRoundButNeverLie(){
        assertEquals("63%", Numbers.percent(0.6333f));
        assertEquals("100%", Numbers.percent(1f));
        assertEquals("0%", Numbers.percent(0f));
    }

    @Test
    void nearlyFullAndNearlyEmptyStayDistinctFromTheExtremes(){
        assertEquals(">99%", Numbers.percent(0.9999f), "an almost-full factory is not a full one");
        assertEquals("<1%", Numbers.percent(0.0001f), "a barely-running factory is not a stopped one");
    }

    @Test
    void undefinedValuesAreMarkedRatherThanPrinted(){
        assertEquals("?", Numbers.rate(Float.NaN));
        assertEquals("?", Numbers.percent(Float.POSITIVE_INFINITY));
        assertEquals("?", Numbers.multiplier(Float.NaN));
    }

    @Test
    void multipliersAndSignedRatesReadNaturally(){
        assertEquals("1.5x", Numbers.multiplier(1.5f));
        assertEquals("+12", Numbers.signedRate(12f));
        assertEquals("-12", Numbers.signedRate(-12f));
    }
}
