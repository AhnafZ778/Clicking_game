package com.projuktilipi.Touchme;

import org.junit.Test;
import static org.junit.Assert.*;

public class ComboMeterTest {
    @Test public void multiplierAndFever() {
        ComboMeter c = new ComboMeter();
        long t = 1000;
        boolean fever = false;
        for (int i = 0; i < 10; i++) {
            fever = c.onHit(t += 100);
        }
        assertTrue(c.fever());
        assertTrue(fever); // started on 10th hit
        assertTrue(c.multiplier() >= 2);
        // points double during fever
        assertTrue(c.pointsForHit() >= 4);
    }
}
