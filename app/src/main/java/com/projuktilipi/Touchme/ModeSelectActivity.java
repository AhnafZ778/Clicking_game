package com.projuktilipi.Touchme;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
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
 *  - EXTRA_CURRENT_MODE    : String  (the caller’s current mode; enables confirmations here)
 *
 * Result extras:
 *  - EXTRA_SELECTED_MODE   : String  (the picked mode)
 *  - EXTRA_DECISION        : String  (one of DECISION_RESUME / DECISION_START_FRESH / DECISION_SWITCH)
 */
public class ModeSelectActivity extends AppCompatActivity {

    public static final String EXTRA_MODE = "extra_mode";
    public static final String EXTRA_SELECTED_MODE = "extra_selected_mode";
    public static final String EXTRA_RETURN_RESULT = "extra_return_result";
    public static final String EXTRA_CURRENT_MODE  = "extra_current_mode";

    public static final String EXTRA_DECISION      = "extra_decision";
    public static final String DECISION_RESUME     = "resume";
    public static final String DECISION_START_FRESH= "start_fresh";
    public static final String DECISION_SWITCH     = "switch";

    private SharedPreferences prefs;
    private GameMode currentModeForConfirm; // only set when returning a result

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mode_select);

        prefs = getSharedPreferences("touch_me_prefs", MODE_PRIVATE);

        final boolean returnResultOnly =
                getIntent() != null && getIntent().getBooleanExtra(EXTRA_RETURN_RESULT, false);

        if (returnResultOnly) {
            String cur = getIntent().getStringExtra(EXTRA_CURRENT_MODE);
            try { currentModeForConfirm = GameMode.valueOf(cur); }
            catch (Exception e) { currentModeForConfirm = null; }
        }

        Button btnTimeAttack = findViewById(R.id.btn_time_attack);
        Button btnEndless    = findViewById(R.id.btn_endless);
        Button btnHardcore   = findViewById(R.id.btn_hardcore);
        Button btnChill      = findViewById(R.id.btn_chill);
        Button btnStory      = findViewById(R.id.btn_story);

        View.OnClickListener pick = v -> {
            GameMode picked;
            int id = v.getId();
            if (id == R.id.btn_time_attack)      picked = GameMode.TIME_ATTACK;
            else if (id == R.id.btn_endless)     picked = GameMode.ENDLESS;
            else if (id == R.id.btn_hardcore)    picked = GameMode.HARDCORE;
            else if (id == R.id.btn_chill)       picked = GameMode.CHILL;
            else                                  picked = GameMode.STORY;

            // Persist the last choice for launcher starts
            prefs.edit().putInt("mode", picked.ordinal()).apply();

            if (returnResultOnly) {
                handleReturnResultFlow(picked);
            } else {
                // Launcher path: simple handoff to game, then close ourselves
                Intent i = new Intent(ModeSelectActivity.this, MainActivity.class);
                i.putExtra(EXTRA_MODE, picked.name());
                startActivity(i);
                finish();
            }
        };

        btnTimeAttack.setOnClickListener(pick);
        btnEndless.setOnClickListener(pick);
        btnHardcore.setOnClickListener(pick);
        btnChill.setOnClickListener(pick);
        btnStory.setOnClickListener(pick);
    }

    private void handleReturnResultFlow(GameMode picked) {
        // If caller provided a current mode, do confirmations HERE (not in MainActivity)
        if (currentModeForConfirm != null) {
            if (picked == currentModeForConfirm) {
                // Same mode: resume or start fresh?
                new AlertDialog.Builder(this)
                        .setTitle("Same mode selected")
                        .setMessage("Do you want to resume your paused run or start fresh?")
                        .setPositiveButton("Start fresh", (d,w) -> {
                            Intent data = new Intent();
                            data.putExtra(EXTRA_SELECTED_MODE, picked.name());
                            data.putExtra(EXTRA_DECISION, DECISION_START_FRESH);
                            setResult(RESULT_OK, data);
                            finish();
                        })
                        .setNegativeButton("Resume", (d,w) -> {
                            Intent data = new Intent();
                            data.putExtra(EXTRA_SELECTED_MODE, picked.name());
                            data.putExtra(EXTRA_DECISION, DECISION_RESUME);
                            setResult(RESULT_OK, data);
                            finish();
                        })
                        .setCancelable(true)
                        .show();
                return;
            } else {
                // Different mode: confirm switch here
                new AlertDialog.Builder(this)
                        .setTitle("Switch mode?")
                        .setMessage("Switching to " + picked.name() + " will end the current run.")
                        .setPositiveButton("Switch", (d,w) -> {
                            Intent data = new Intent();
                            data.putExtra(EXTRA_SELECTED_MODE, picked.name());
                            data.putExtra(EXTRA_DECISION, DECISION_SWITCH);
                            setResult(RESULT_OK, data);
                            finish();
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
                return;
            }
        }

        // Fallback (shouldn’t happen for returnResultOnly): just return the mode
        Intent data = new Intent();
        data.putExtra(EXTRA_SELECTED_MODE, picked.name());
        data.putExtra(EXTRA_DECISION, DECISION_SWITCH);
        setResult(RESULT_OK, data);
        finish();
    }
}
