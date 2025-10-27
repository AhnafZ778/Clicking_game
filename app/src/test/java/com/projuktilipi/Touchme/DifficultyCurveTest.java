package com.projuktilipi.Touchme;
import org.junit.Test;
import static org.junit.Assert.*;

public class DifficultyCurveTest {
    @Test public void spawnIntervalDecreasesWithScore() {
        int base = 550;
        assertTrue(DifficultyCurve.spawnIntervalMs(base, 0) > DifficultyCurve.spawnIntervalMs(base, 50));
    }
    @Test public void lifeNeverBelowMin() {
        int min = DifficultyCurve.targetLifeMs(900, 9999);
        assertTrue(min >= 450);
    }
    @Test public void radiusShrinksButClamped() {
        int r = DifficultyCurve.radiusPx(42, 80, 1000);
        assertTrue(r >= 30);
    }
}
