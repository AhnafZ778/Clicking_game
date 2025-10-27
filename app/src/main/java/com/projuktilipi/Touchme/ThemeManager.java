package com.projuktilipi.Touchme;

import android.content.SharedPreferences;

public final class ThemeManager {
    private static final String KEY_THEME = "theme";
    public enum Theme { CLASSIC, SUNSET, OCEAN }

    public static Theme getTheme(SharedPreferences p) {
        int ord = p.getInt(KEY_THEME, Theme.CLASSIC.ordinal());
        return Theme.values()[ord];
    }

    public static void setTheme(SharedPreferences p, Theme t) {
        p.edit().putInt(KEY_THEME, t.ordinal()).apply();
    }
}
