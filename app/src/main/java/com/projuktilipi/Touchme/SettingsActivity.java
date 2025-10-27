package com.projuktilipi.Touchme;

import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;

public class SettingsActivity extends AppCompatActivity {
    private SharedPreferences prefs;
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("touch_me_prefs", MODE_PRIVATE);
        String[] items = {"Classic", "Sunset", "Ocean"};
        ThemeManager.Theme current = ThemeManager.getTheme(prefs);
        new AlertDialog.Builder(this)
                .setTitle("Choose Theme")
                .setSingleChoiceItems(items, current.ordinal(), (d, which) -> {
                    ThemeManager.setTheme(prefs, ThemeManager.Theme.values()[which]);
                }).setPositiveButton("OK", (d,w) -> finish())
                .setNegativeButton("Cancel", (d,w) -> finish())
                .show();
    }
}
