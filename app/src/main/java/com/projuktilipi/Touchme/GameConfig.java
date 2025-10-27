package com.projuktilipi.Touchme;

public class GameConfig {
    public final GameMode mode;
    public final long roundMillis;        // used in TIME_ATTACK
    public final boolean failOnMiss;      // ENDLESS true
    public final boolean chill;           // CHILL true
    public final int baseLifeMs;          // target lifetime baseline
    public final int minRadius;           // px
    public final int maxRadius;           // px
    public final int baseSpawnMs;         // baseline spawn interval

    private GameConfig(GameMode m, long round, boolean failMiss, boolean isChill,
                       int life, int minR, int maxR, int spawn) {
        mode = m; roundMillis = round; failOnMiss = failMiss; chill = isChill;
        baseLifeMs = life; minRadius = minR; maxRadius = maxR; baseSpawnMs = spawn;
    }

    public static GameConfig forMode(GameMode m) {
        switch (m) {
            case ENDLESS:
                return new GameConfig(m, 0L, true, false, 850, 34, 80, 520);
            case CHILL:
                return new GameConfig(m, 0L, false, true, 1200, 50, 90, 850);
            case TIME_ATTACK:
            default:
                return new GameConfig(m, 60_000L, false, false, 900, 42, 80, 550);
        }
    }
}
