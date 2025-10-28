package com.projuktilipi.Touchme;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Mode picker screen.
 *
 * Supports two use cases:
 *  1) Launcher entry: saves choice + launches MainActivity with EXTRA_MODE.
 *  2) Pick-for-result: returns the choice via setResult(...):
 *        - pass EXTRA_RETURN_RESULT=true when starting this activity.
 *
 * Public extras:
 *  - EXTRA_MODE            : String  (used when we launch MainActivity directly)
 *  - EXTRA_SELECTED_MODE   : String  (used when we return a result to caller)
 *  - EXTRA_RETURN_RESULT   : boolean (tell this screen to return a result instead of launching)
 */
public class ModeSelectActivity extends AppCompatActivity {

    public static final String EXTRA_MODE = "extra_mode";
    public static final String EXTRA_SELECTED_MODE = "extra_selected_mode";
    public static final String EXTRA_RETURN_RESULT = "extra_return_result";

    private SharedPreferences prefs;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mode_select);

        prefs = getSharedPreferences("touch_me_prefs", MODE_PRIVATE);

        final boolean returnResultOnly =
                getIntent() != null && getIntent().getBooleanExtra(EXTRA_RETURN_RESULT, false);

        Button btnTimeAttack = findViewById(R.id.btn_time_attack);
        Button btnEndless    = findViewById(R.id.btn_endless);
        Button btnHardcore   = findViewById(R.id.btn_hardcore);
        Button btnChill      = findViewById(R.id.btn_chill);
        Button btnStory      = findViewById(R.id.btn_story);

        View.OnClickListener pick = v -> {
            GameMode mode;
            int id = v.getId();
            if (id == R.id.btn_time_attack)      mode = GameMode.TIME_ATTACK;
            else if (id == R.id.btn_endless)     mode = GameMode.ENDLESS;
            else if (id == R.id.btn_hardcore)    mode = GameMode.HARDCORE;
            else if (id == R.id.btn_chill)       mode = GameMode.CHILL;
            else                                  mode = GameMode.STORY;

            // Persist for future launches
            prefs.edit().putInt("mode", mode.ordinal()).apply();

            if (returnResultOnly) {
                // Return choice to the caller (MainActivity)
                Intent data = new Intent();
                data.putExtra(EXTRA_SELECTED_MODE, mode.name());
                setResult(RESULT_OK, data);
                finish();
            } else {
                // Launcher path: start the game screen
                Intent i = new Intent(ModeSelectActivity.this, MainActivity.class);
                i.putExtra(EXTRA_MODE, mode.name());
                startActivity(i);
                finish(); // IMPORTANT: finish so Back doesn’t return here
            }
        };

        btnTimeAttack.setOnClickListener(pick);
        btnEndless.setOnClickListener(pick);
        btnHardcore.setOnClickListener(pick);
        btnChill.setOnClickListener(pick);
        btnStory.setOnClickListener(pick);
    }
}
