package com.projuktilipi.Touchme;

import org.junit.Test;
import static org.junit.Assert.*;

public class DifficultyCurveTest {
    @Test public void spawnClamps() {
        assertEquals(280, DifficultyCurve.spawnIntervalMs(550, 1000));
    }
    @Test public void lifeClamps() {
        assertEquals(450, DifficultyCurve.targetLifeMs(900, 1000));
    }
    @Test public void radiusShrinks() {
        int r0 = DifficultyCurve.radiusPx(36, 80, 0);
        int r30 = DifficultyCurve.radiusPx(36, 80, 90); // 90/3=30px shrink
        assertTrue(r30 <= r0);
        assertTrue(r30 >= 36);
    }
}
