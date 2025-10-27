package com.projuktilipi.Touchme;

/** Simple score-based difficulty scaling. Keep math cheap. */
public class DifficultyCurve {

    public static int spawnIntervalMs(int base, int score) {
        // -5 ms per point, clamp to 280 ms
        int v = base - (score * 5);
        return Math.max(280, v);
    }

    public static int targetLifeMs(int base, int score) {
        // -3 ms per point, clamp to 450 ms
        int v = base - (score * 3);
        return Math.max(450, v);
    }

    public static int radiusPx(int min, int max, int score) {
        // shrink 1px every 3 points, clamp to min
        int shrink = score / 3;
        return Math.max(min, max - shrink);
    }
}
