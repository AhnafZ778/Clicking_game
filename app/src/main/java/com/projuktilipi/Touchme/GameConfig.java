package com.projuktilipi.Touchme;

public class GameConfig {
    public final int baseSpawnMs;   // base spawn interval (lower -> harder)
    public final int baseLifeMs;    // how long a target stays alive
    public final int minRadius;     // min target radius
    public final int maxRadius;     // max target radius
    public final long roundMillis;  // only used by TIME_ATTACK
    public final boolean failOnMiss;// ENDLESS fails on a miss/expiry
    public final boolean chill;     // disables moving targets etc.

    public GameConfig(int baseSpawnMs, int baseLifeMs, int minR, int maxR,
                      long roundMillis, boolean failOnMiss, boolean chill) {
        this.baseSpawnMs = baseSpawnMs;
        this.baseLifeMs  = baseLifeMs;
        this.minRadius   = minR;
        this.maxRadius   = maxR;
        this.roundMillis = roundMillis;
        this.failOnMiss  = failOnMiss;
        this.chill       = chill;
    }

    public static GameConfig forMode(GameMode m) {
        switch (m) {
            case ENDLESS:
                return new GameConfig(
                        600, 950, 40, 80,
                        Long.MAX_VALUE, true, false
                );
            case CHILL:
                return new GameConfig(
                        700, 1100, 44, 90,
                        Long.MAX_VALUE, false, true
                );
            case TIME_ATTACK:
            default:
                return new GameConfig(
                        550, 900, 36, 80,
                        60_000L, false, false
                );
        }
    }
}
