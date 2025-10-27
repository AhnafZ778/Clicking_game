package com.projuktilipi.Touchme;

/** Tracks streaks, multiplier, and temporary "fever" mode. */
public class ComboMeter {
    private int streak = 0;
    private int multiplier = 1;
    private boolean fever = false;

    private long lastHitAt = 0L;
    private long feverUntil = 0L;

    // reset streak if you wait too long between hits
    private static final long COMBO_WINDOW_MS = 2200;
    // fever duration (refreshes with continued hits during fever)
    private static final long FEVER_MS = 6000;

    /** Call when a target is hit. Returns true if fever just started. */
    public boolean onHit(long nowMs) {
        if (nowMs - lastHitAt > COMBO_WINDOW_MS) {
            streak = 0; multiplier = 1; fever = false; feverUntil = 0;
        }
        lastHitAt = nowMs;
        streak++;

        // multipliers at streak thresholds
        if (streak >= 25)      multiplier = 5;
        else if (streak >= 15) multiplier = 3;
        else if (streak >= 8)  multiplier = 2;
        else                   multiplier = 1;

        boolean feverStarted = false;
        if (!fever && streak >= 10) {
            fever = true;
            feverStarted = true;
            feverUntil = nowMs + FEVER_MS;
        }
        if (fever) feverUntil = nowMs + FEVER_MS; // refresh
        return feverStarted;
    }

    /** Call when a target expires (or for ENDLESS taps on empty). */
    public void onMiss() {
        streak = 0; multiplier = 1; fever = false; feverUntil = 0;
    }

    /** Call each frame. */
    public void update(long nowMs) {
        if (streak > 0 && nowMs - lastHitAt > COMBO_WINDOW_MS) {
            streak = 0; multiplier = 1; fever = false; feverUntil = 0;
        }
        if (fever && nowMs > feverUntil) fever = false;
    }

    public int streak() { return streak; }
    public int multiplier() { return multiplier; }
    public boolean fever() { return fever; }

    /** Points for a single hit. Base point is 1. */
    public int pointsForHit() {
        return multiplier * (fever ? 2 : 1);
    }
}
