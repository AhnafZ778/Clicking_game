package com.projuktilipi.Touchme;

public class GameConfig {
    public final int baseSpawnMs;
    public final int baseLifeMs;
    public final int minRadius;
    public final int maxRadius;
    public final long roundMillis;
    public final boolean chill;
    public final boolean failOnMiss;

    /** NEW: divides score when ramping difficulty so Chill/Endless ramp slower. */
    public final int scaleDiv;

    /** 7-arg constructor (legacy). Defaults scaleDiv = 1. */
    public GameConfig(int spawn, int life, int minR, int maxR, long roundMs, boolean chill, boolean failOnMiss) {
        this(spawn, life, minR, maxR, roundMs, chill, failOnMiss, 1);
    }

    /** 8-arg constructor with explicit scaleDiv. */
    public GameConfig(int spawn, int life, int minR, int maxR, long roundMs, boolean chill, boolean failOnMiss, int scaleDiv) {
        this.baseSpawnMs = spawn;
        this.baseLifeMs  = life;
        this.minRadius   = minR;
        this.maxRadius   = maxR;
        this.roundMillis = roundMs;
        this.chill       = chill;
        this.failOnMiss  = failOnMiss;
        this.scaleDiv    = Math.max(1, scaleDiv);
    }

    public static GameConfig forMode(GameMode m) {
        switch (m) {
            case CHILL:
                // Slower spawn, longer life, bigger targets, slow difficulty ramp
                return new GameConfig(650, 1200, 54, 90, 60_000L, true,  false, 2);
            case ENDLESS:
                // Miss does nothing; ramp very slowly
                return new GameConfig(600, 1000, 48, 90, Long.MAX_VALUE, true,  false, 3);
            case HARDCORE:
                // Fast, miss ends; ramp fast
                return new GameConfig(500, 900, 42, 80, Long.MAX_VALUE, false, true, 1);
            case STORY:
                // LevelManager will override numbers; keep defaults reasonable
                return new GameConfig(580, 950, 46, 86, 45_000L, false, false, 2);
            case TIME_ATTACK:
            default:
                return new GameConfig(550, 900, 42, 80, 60_000L, false, false, 1);
        }
    }
}
