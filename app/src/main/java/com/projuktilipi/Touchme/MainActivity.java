package com.projuktilipi.Touchme;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

public class MainActivity extends AppCompatActivity {

    // UI
    private FrameLayout game_container;
    private View overlay;
    private View tutorialOverlay; // optional
    private TextView score_text, time_text, high_text, mode_text; // mode_text optional
    private Button start_button, pause_button, tutorialGotIt;     // tutorialGotIt optional
    private ImageButton settings_button;                          // optional

    // Game
    private GameView gameView;
    private final Handler handler = new Handler(Looper.getMainLooper());

    // State
    private boolean running = false;
    private boolean paused  = false;
    private int score = 0;
    private int best  = 0;
    private long endTimeMs = 0;
    private long pauseStartedMs = 0L;

    // Prefs
    private SharedPreferences prefs;
    private boolean hapticsEnabled = true;
    private boolean musicEnabled = false; // start with music off to avoid edge-cases
    private boolean sfxEnabled    = true;

    private GameMode mode = GameMode.TIME_ATTACK;
    private AudioEngine audio;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setVolumeControlStream(android.media.AudioManager.STREAM_MUSIC);

        // --- find views (some are optional; guard them) ---
        game_container  = findViewById(R.id.game_container);
        overlay         = findViewById(R.id.overlay);
        tutorialOverlay = findViewById(R.id.tutorial_overlay);
        score_text      = findViewById(R.id.score_text);
        time_text       = findViewById(R.id.time_text);
        high_text       = findViewById(R.id.high_text);
        mode_text       = findViewById(R.id.mode_text);
        start_button    = findViewById(R.id.start_button);
        pause_button    = findViewById(R.id.pause_button);
        tutorialGotIt   = findViewById(R.id.tutorial_got_it);
        settings_button = findViewById(R.id.settings_button);

        if (game_container == null) {
            throw new IllegalStateException("activity_main.xml must include @+id/game_container");
        }

        // --- prefs ---
        prefs = getSharedPreferences("touch_me_prefs", MODE_PRIVATE);
        best = prefs.getInt("best", 0);
        hapticsEnabled = prefs.getBoolean("haptics_enabled", true);
        musicEnabled   = prefs.getBoolean("music_enabled", false); // default off initially
        sfxEnabled     = prefs.getBoolean("sfx_enabled", true);
        int savedModeOrdinal = prefs.getInt("mode", GameMode.TIME_ATTACK.ordinal());
        mode = GameMode.values()[savedModeOrdinal];

        if (high_text != null) high_text.setText("Best: " + best);
        if (mode_text != null) mode_text.setText(modeToLabel(mode));
        if (time_text != null) time_text.setText(mode == GameMode.TIME_ATTACK ? "60s" : "∞");

        // --- audio ---
        audio = new AudioEngine(this);
        audio.setSfxEnabled(true);     // force ON for now to verify SFX
        audio.setMusicEnabled(false);  // keep BGM off until SFX is verified
        audio.prewarm();               // warm up SoundPool

        // --- game view (create FIRST, then configure) ---
        gameView = new GameView(this, new GameView.GameEvents() {
            @Override public void onHit(int points, int streak, boolean fever) {
                if(!running || paused) return;
                score += points;
                if (score_text != null) score_text.setText("Score: " + score);
                gameView.setScoreForDifficulty(score);
                if(score > best) {
                    best = score;
                    if (high_text != null) high_text.setText("Best: " + best);
                }
                // (optional) visual hint for fever; you already see it via background pulse
            }
            @Override public void onMiss() {
                if (mode == GameMode.ENDLESS) finishGame();
            }
        });
        gameView.setConfig(GameConfig.forMode(mode));
        gameView.setHapticsEnabled(hapticsEnabled);
        gameView.setAudioEngine(audio);
        game_container.addView(gameView);
        // --- start / pause ---
        if (start_button != null) start_button.setOnClickListener(v -> startGame());
        if (pause_button != null) pause_button.setOnClickListener(v -> {
            if (running && !paused) pauseGame(true);
        });

        // --- settings (optional) ---
        if (settings_button != null) settings_button.setOnClickListener(v -> showSettings());

        // --- tutorial (optional) ---
        if (tutorialOverlay != null && tutorialGotIt != null) {
            if (!prefs.getBoolean("tutorial_seen", false)) {
                tutorialOverlay.setVisibility(View.VISIBLE);
            }
            tutorialGotIt.setOnClickListener(v -> {
                tutorialOverlay.setVisibility(View.GONE);
                prefs.edit().putBoolean("tutorial_seen", true).apply();
            });
        }
    }


    private void startGame() {
        score = 0;
        if (score_text != null) score_text.setText("Score: 0");
        running = true;
        paused = false;
        if (overlay != null) overlay.setVisibility(View.GONE);

        GameConfig cfg = GameConfig.forMode(mode);
        gameView.setConfig(cfg);
        gameView.setScoreForDifficulty(0);

        if (mode == GameMode.TIME_ATTACK) {
            endTimeMs = System.currentTimeMillis() + cfg.roundMillis;
        } else {
            endTimeMs = Long.MAX_VALUE;
        }

        gameView.reset();
        gameView.start();

        handler.postDelayed(() -> {
            if (audio != null) audio.playTap(); // should be audible after 1s
        }, 1000);

        // enable BGM only if you want now; keep off for first sanity run
        // audio.setMusicEnabled(true);
        // audio.playMusic();

        handler.removeCallbacks(tick);
        handler.post(tick);
    }

    private void pauseGame(boolean showDialog) {
        if (!running || paused) return;
        paused = true;
        pauseStartedMs = System.currentTimeMillis();
        handler.removeCallbacks(tick);
        gameView.pause();
        audio.pauseMusic();
        if (showDialog) showPauseDialog();
    }

    private void resumeGame() {
        if (!running || !paused) return;
        long pausedDur = System.currentTimeMillis() - pauseStartedMs;
        endTimeMs += pausedDur; // freeze countdown while paused
        paused = false;
        gameView.resume();
        // audio.playMusic();
        handler.removeCallbacks(tick);
        handler.post(tick);
    }

    private void showPauseDialog() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        container.setPadding(pad, pad, pad, pad);

        SwitchCompat hapticSwitch = new SwitchCompat(this);
        hapticSwitch.setChecked(hapticsEnabled);
        hapticSwitch.setText("Haptics");
        hapticSwitch.setOnCheckedChangeListener((b, isChecked) -> {
            hapticsEnabled = isChecked;
            prefs.edit().putBoolean("haptics_enabled", isChecked).apply();
            gameView.setHapticsEnabled(isChecked);
        });

        SwitchCompat musicSwitch = new SwitchCompat(this);
        musicSwitch.setChecked(musicEnabled);
        musicSwitch.setText("Music");
        musicSwitch.setOnCheckedChangeListener((b, isChecked) -> {
            musicEnabled = isChecked;
            prefs.edit().putBoolean("music_enabled", isChecked).apply();
            audio.setMusicEnabled(isChecked);
        });

        SwitchCompat sfxSwitch = new SwitchCompat(this);
        sfxSwitch.setChecked(sfxEnabled);
        sfxSwitch.setText("Sound Effects");
        sfxSwitch.setOnCheckedChangeListener((b, isChecked) -> {
            sfxEnabled = isChecked;
            prefs.edit().putBoolean("sfx_enabled", isChecked).apply();
            audio.setSfxEnabled(isChecked);
        });

        container.addView(hapticSwitch);
        container.addView(musicSwitch);
        container.addView(sfxSwitch);

        new AlertDialog.Builder(this)
                .setTitle("Paused")
                .setView(container)
                .setPositiveButton("Resume", (d, w) -> resumeGame())
                .setNegativeButton("Restart", (d, w) -> {
                    if (paused) resumeGame();
                    startGame();
                })
                .setCancelable(false)
                .show();
    }

    private void showSettings() {
        // optional dialog; safe no-op if you haven't added SettingsActivity yet
        new AlertDialog.Builder(this)
                .setTitle("Settings")
                .setItems(new CharSequence[]{"Change Mode"}, (d, which) -> {
                    if (which == 0) showModeChooser();
                })
                .show();
    }

    private void showModeChooser() {
        GameMode[] modes = GameMode.values();
        String[] labels = new String[modes.length];
        for (int i = 0; i < modes.length; i++) labels[i] = modeToLabel(modes[i]);
        new AlertDialog.Builder(this)
                .setTitle("Select Mode")
                .setSingleChoiceItems(labels, mode.ordinal(), (dlg, which) -> mode = modes[which])
                .setPositiveButton("Apply", (d,w) -> {
                    prefs.edit().putInt("mode", mode.ordinal()).apply();
                    if (mode_text != null) mode_text.setText(modeToLabel(mode));
                    if (running) { pauseGame(false); startGame(); }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private String modeToLabel(GameMode m) {
        switch (m) {
            case ENDLESS: return "Endless";
            case CHILL:   return "Chill";
            default:      return "Time Attack";
        }
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if(!running || paused) return;
            long left = endTimeMs - System.currentTimeMillis();
            if(left < 0) left = 0;
            if (time_text != null) {
                if (mode == GameMode.TIME_ATTACK) {
                    int sec = (int)((left + 500) / 1000);
                    time_text.setText(sec + "s");
                } else {
                    time_text.setText("∞");
                }
            }
            if(left == 0) finishGame();
            else handler.postDelayed(this, 200);
        }
    };

    private void finishGame() {
        running = false;
        paused = false;
        gameView.stop();
        audio.pauseMusic();
        if (overlay != null) overlay.setVisibility(View.VISIBLE);

        int savedBest = prefs.getInt("best", 0);
        if(best >= savedBest) prefs.edit().putInt("best", best).apply();
        if (start_button != null) start_button.setText("RESTART");
    }

    @Override protected void onPause() {
        super.onPause();
        if (running && !paused) pauseGame(false);
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (audio != null) audio.release();
    }

    private int dp(int v) {
        float d = getResources().getDisplayMetrics().density;
        return Math.round(v * d);
    }
}
