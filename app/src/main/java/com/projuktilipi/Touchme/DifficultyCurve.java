package com.projuktilipi.Touchme;

public final class DifficultyCurve {
    private DifficultyCurve() {}

    /** Spawn interval decreases with score (harder as score rises). */
    public static int spawnIntervalMs(int base, int score) {
        int min = Math.max(220, base - Math.min(300, score * 4));
        return min;
    }

    /** Target life shortens slowly with score. */
    public static int targetLifeMs(int baseLife, int score) {
        int min = Math.max(450, baseLife - Math.min(350, score * 3));
        return min;
    }

    /** Radius range narrows downward slightly with score (smaller later). */
    public static int radiusPx(int minR, int maxR, int score) {
        int shrink = Math.min(12, score / 8);
        return clamp(minR - shrink, 30, maxR);
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
}
