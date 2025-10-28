package com.projuktilipi.Touchme;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

public class LevelManager {

    public static class Level {
        public final String name;
        public final int bgColor;
        public final int baseSpawnMs;
        public final int baseLifeMs;
        public final int minR, maxR;
        public final int goalHits;       // hits required to complete level
        public final long timeLimitMs;   // per-level time limit

        public Level(String name, int bgColor, int baseSpawnMs, int baseLifeMs, int minR, int maxR,
                     int goalHits, long timeLimitMs) {
            this.name = name;
            this.bgColor = bgColor;
            this.baseSpawnMs = baseSpawnMs;
            this.baseLifeMs = baseLifeMs;
            this.minR = minR;
            this.maxR = maxR;
            this.goalHits = goalHits;
            this.timeLimitMs = timeLimitMs;
        }
    }

    // Simple 10-level curve (tweak freely)
    private static final Level[] LEVELS = new Level[]{
            new Level("Level 1", Color.parseColor("#0B132B"), 650, 1200, 56, 96,  20, 45_000L),
            new Level("Level 2", Color.parseColor("#1C2541"), 620, 1150, 54, 94,  24, 45_000L),
            new Level("Level 3", Color.parseColor("#3A506B"), 600, 1100, 52, 92,  28, 45_000L),
            new Level("Level 4", Color.parseColor("#5BC0BE"), 580, 1050, 50, 90,  32, 45_000L),
            new Level("Level 5", Color.parseColor("#1C7C7D"), 560, 1000, 48, 88,  36, 45_000L),
            new Level("Level 6", Color.parseColor("#162447"), 540,  970, 46, 86,  40, 45_000L),
            new Level("Level 7", Color.parseColor("#1F4068"), 520,  940, 44, 84,  44, 45_000L),
            new Level("Level 8", Color.parseColor("#2B4C7E"), 500,  910, 42, 82,  48, 45_000L),
            new Level("Level 9", Color.parseColor("#394867"), 480,  880, 40, 80,  52, 45_000L),
            new Level("Level 10",Color.parseColor("#212A3E"), 460,  850, 38, 78,  56, 45_000L),
    };

    public static int getLevelCount() { return LEVELS.length; }
    public static Level getLevel(int index) {
        if (index < 0) index = 0;
        if (index >= LEVELS.length) index = LEVELS.length - 1;
        return LEVELS[index];
    }

    private static final String PREFS = "touch_me_prefs";
    private static final String KEY_STORY_LEVEL = "story_level_index"; // next level to play (0-based)
    private static final String KEY_STORY_HIGHEST = "story_highest";   // highest reached (for UI badges etc)

    public static int loadCurrentLevel(Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return p.getInt(KEY_STORY_LEVEL, 0);
    }

    public static void saveCurrentLevel(Context ctx, int idx) {
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        p.edit().putInt(KEY_STORY_LEVEL, idx).apply();
        int highest = p.getInt(KEY_STORY_HIGHEST, 0);
        if (idx > highest) p.edit().putInt(KEY_STORY_HIGHEST, idx).apply();
    }
}
