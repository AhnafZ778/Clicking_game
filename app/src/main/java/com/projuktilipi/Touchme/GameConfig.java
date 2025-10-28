package com.projuktilipi.Touchme;

public class GameConfig {
    public final int baseSpawnMs;    // base spawn interval (lower -> harder)
    public final int baseLifeMs;     // target life baseline
    public final int minRadius;      // px
    public final int maxRadius;      // px
    public final long roundMillis;   // TIME_ATTACK only
    public final boolean failOnMiss; // ENDLESS ends on miss/expiry
    public final boolean chill;      // disables moving targets etc.
    public final int scaleDiv;       // difficulty scaling divisor (higher = gentler)

    public GameConfig(int baseSpawnMs, int baseLifeMs, int minR, int maxR,
                      long roundMillis, boolean failOnMiss, boolean chill, int scaleDiv) {
        this.baseSpawnMs = baseSpawnMs;
        this.baseLifeMs  = baseLifeMs;
        this.minRadius   = minR;
        this.maxRadius   = maxR;
        this.roundMillis = roundMillis;
        this.failOnMiss  = failOnMiss;
        this.chill       = chill;
        this.scaleDiv    = scaleDiv;
    }

    public static GameConfig forMode(GameMode m) {
        switch (m) {
            case ENDLESS:
                return new GameConfig(
                        600, 950, 40, 80,
                        Long.MAX_VALUE, true,  false, 1
                );
            case CHILL:
                return new GameConfig(
                        // a bit calmer than before
                        800, 1350, 48, 92,
                        Long.MAX_VALUE, false, true, 2
                );
            case EXTRA_CHILL:
                return new GameConfig(
                        // super relaxed: slow spawn, long life, big targets, very gentle scaling
                        1000, 2000, 54, 102,
                        Long.MAX_VALUE, false, true, 3
                );
            case TIME_ATTACK:
            default:
                return new GameConfig(
                        550, 900, 36, 80,
                        60_000L, false, false, 1
                );
        }
    }
}
