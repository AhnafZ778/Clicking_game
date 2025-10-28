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

import com.google.android.gms.games.PlayGamesSdk;

public class MainActivity extends AppCompatActivity implements BillingManager.Listener {

    // UI
    private FrameLayout game_container;
    private View overlay;
    private View tutorialOverlay; // optional
    private TextView score_text, time_text, high_text, mode_text; // mode_text optional
    private Button start_button, pause_button, tutorialGotIt;     // optional
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
    private boolean musicEnabled   = false;
    private boolean sfxEnabled     = true;

    private boolean adsRemoved = false;
    private GameMode mode = GameMode.TIME_ATTACK;

    // Systems
    private AudioEngine audio;
    private AdsManager ads;
    private BillingManager billing;

    // Social (Play Games v2)
    private SocialClient social;

    // One-time prompt preference key
    private static final String PREF_PGS_ASKED_ONCE = "pgs_asked_once";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        PlayGamesSdk.initialize(this);

        setContentView(R.layout.activity_main);
        setVolumeControlStream(android.media.AudioManager.STREAM_MUSIC);

        // Views
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

        // Prefs
        prefs = getSharedPreferences("touch_me_prefs", MODE_PRIVATE);
        best = prefs.getInt("best", 0);
        hapticsEnabled = prefs.getBoolean("haptics_enabled", true);
        musicEnabled   = prefs.getBoolean("music_enabled", false);
        sfxEnabled     = prefs.getBoolean("sfx_enabled", true);
        adsRemoved     = prefs.getBoolean("ads_removed", false);

        int savedModeOrdinal = prefs.getInt("mode", GameMode.TIME_ATTACK.ordinal());
        GameMode[] modesAll = GameMode.values();
        if (savedModeOrdinal < 0 || savedModeOrdinal >= modesAll.length) mode = GameMode.TIME_ATTACK;
        else mode = modesAll[savedModeOrdinal];

        if (high_text != null) high_text.setText("Best: " + best);
        if (mode_text != null) mode_text.setText(modeToLabel(mode));
        if (time_text != null) time_text.setText(mode == GameMode.TIME_ATTACK ? "60s" : "∞");

        // Audio
        // Audio
        audio = new AudioEngine(this);
        audio.setSfxEnabled(sfxEnabled);

// honor the saved preference
        audio.setMusicEnabled(musicEnabled);
        audio.prewarm();

// optional: play menu music while the overlay is up
        if (musicEnabled) {
            audio.startMusic(R.raw.music_menu, true);   // or R.raw.music_game if you prefer
        }


        // Ads
        ads = new AdsManager();
        ads.init(this);

        // Billing
        billing = new BillingManager(this, this);
        billing.start();

        // Social (use real GPGS impl)
        social = new SocialGpgs();

        // GameView
        gameView = new GameView(this, new GameView.GameEvents() {
            @Override public void onHit(int points, int streak, boolean fever) {
                if(!running || paused) return;
                score += points > 0 ? points : 1;
                if (score_text != null) score_text.setText("Score: " + score);
                gameView.setScoreForDifficulty(score);
                if(score > best) {
                    best = score;
                    if (high_text != null) high_text.setText("Best: " + best);
                }
                // Optional: achievements (guarded by IDs existing)
                maybeUnlockAchievement("ach_streak_10", streak >= 10);
                maybeUnlockAchievement("ach_score_50", score  >= 50);
            }
            @Override public void onMiss() {
                if (mode == GameMode.ENDLESS) finishGame();
            }
        });
        gameView.setConfig(GameConfig.forMode(mode));
        gameView.setHapticsEnabled(hapticsEnabled);
        gameView.setAudioEngine(audio);
        game_container.addView(gameView);

        // Start / pause
        if (start_button != null) start_button.setOnClickListener(v -> startGame());
        if (pause_button != null) pause_button.setOnClickListener(v -> { if (running && !paused) pauseGame(true); });

        // Settings
        if (settings_button != null) settings_button.setOnClickListener(v -> showSettings());

        // Tutorial
        if (tutorialOverlay != null && tutorialGotIt != null) {
            if (!prefs.getBoolean("tutorial_seen", false)) tutorialOverlay.setVisibility(View.VISIBLE);
            tutorialGotIt.setOnClickListener(v -> {
                tutorialOverlay.setVisibility(View.GONE);
                prefs.edit().putBoolean("tutorial_seen", true).apply();
            });
        }

        // Ask ONCE on first launch whether the user wants to sign in.
        maybeAskForPlayGamesOnce();
    }

    /** Ask once on first launch (no auto sign-in). */
    private void maybeAskForPlayGamesOnce() {
        boolean asked = prefs.getBoolean(PREF_PGS_ASKED_ONCE, false);
        if (asked) return;

        new AlertDialog.Builder(this)
                .setTitle("Play Games")
                .setMessage("Sign in to Google Play Games to compete on leaderboards and earn achievements?")
                .setPositiveButton("Sign in", (d,w) -> {
                    prefs.edit().putBoolean(PREF_PGS_ASKED_ONCE, true).apply();
                    // Interactive sign-in only when you approved
                    social.signIn(this, null);
                })
                .setNegativeButton("Not now", (d,w) -> {
                    prefs.edit().putBoolean(PREF_PGS_ASKED_ONCE, true).apply();
                })
                .show();
    }

    // ===== Game flow =====
    private void startGame() {
        score = 0;
        if (score_text != null) score_text.setText("Score: 0");
        running = true;
        paused = false;
        if (overlay != null) overlay.setVisibility(View.GONE);

        GameConfig cfg = GameConfig.forMode(mode);
        gameView.setConfig(cfg);
        gameView.setScoreForDifficulty(0);

        endTimeMs = (mode == GameMode.TIME_ATTACK)
                ? System.currentTimeMillis() + cfg.roundMillis
                : Long.MAX_VALUE;

        gameView.reset();
        gameView.start();
// Start (or switch) music
        if (musicEnabled) {
            // Use your preferred track here:
            audio.startMusic(R.raw.music_game, true);  // falls back to music_loop if you keep that name
        }
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
        endTimeMs += pausedDur;
        paused = false;
        gameView.resume();
        if (musicEnabled) audio.resumeMusic();
        handler.removeCallbacks(tick);
        handler.post(tick);
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

        // Submit to leaderboard matching mode (only if already signed in)
        if (social != null && social.isSignedIn()) {
            String lbId;
            switch (mode) {
                case ENDLESS:     lbId = getStringSafe("lb_endless_id"); break;
                case CHILL:
                case EXTRA_CHILL: lbId = getStringSafe("lb_chill_id");   break;
                default:          lbId = getStringSafe("lb_time_attack_id"); break;
            }
            if (lbId != null) social.submitScore(this, lbId, best);
        }

        if (!adsRemoved) ads.showInterstitial(this, null);
        if (start_button != null) start_button.setText("RESTART");
        maybeOfferReward();
    }

    // ===== Menus =====
    private void maybeOfferReward() {
        if (mode == GameMode.TIME_ATTACK) {
            new AlertDialog.Builder(this)
                    .setTitle("Need a boost?")
                    .setMessage("Watch a short ad to get +10 seconds.")
                    .setPositiveButton("Watch", (d,w) -> ads.showRewarded(this, () -> {
                        endTimeMs += 10_000L;
                        if (!running) { running = true; paused = false; handler.post(tick); }
                        if (overlay != null) overlay.setVisibility(View.GONE);
                    }))
                    .setNegativeButton("No thanks", null)
                    .show();
        } else {
            new AlertDialog.Builder(this)
                    .setTitle("Free bonus?")
                    .setMessage("Watch a short ad to get +5 points.")
                    .setPositiveButton("Watch", (d,w) -> ads.showRewarded(this, () -> {
                        score += 5;
                        if (score_text != null) score_text.setText("Score: " + score);
                        if (overlay != null) overlay.setVisibility(View.GONE);
                    }))
                    .setNegativeButton("No thanks", null)
                    .show();
        }
    }

    private void showPauseDialog() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        container.setPadding(pad, pad, pad, pad);

        // Row 1: quick Play Games icon (only if NOT signed in)
        if (social != null && !social.isSignedIn()) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);

            int iconId = getResources().getIdentifier("ic_play_games_small", "drawable", getPackageName());
            if (iconId != 0) {
                ImageButton signInIcon = new ImageButton(this);
                signInIcon.setImageResource(iconId);
                signInIcon.setBackground(null);
                signInIcon.setContentDescription("Sign in to Play Games");
                int size = dp(32);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
                lp.bottomMargin = dp(8);
                signInIcon.setLayoutParams(lp);
                signInIcon.setOnClickListener(v -> confirmPlayGamesSignIn());
                row.addView(signInIcon);
            } else {
                Button signInBtn = new Button(this);
                signInBtn.setText("Sign in to Play Games");
                signInBtn.setOnClickListener(v -> confirmPlayGamesSignIn());
                row.addView(signInBtn);
            }
            container.addView(row);
        }

        // Row 2: toggles
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

            if (isChecked) {
                // Start or resume appropriate track immediately
                if (running && !paused) {
                    audio.startMusic(R.raw.music_game, true);
                } else {
                    audio.startMusic(R.raw.music_menu, true);
                }
            } else {
                audio.stopMusic();
            }
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

        // Remove Ads / Restore
        Button buyRemoveAds = new Button(this);
        buyRemoveAds.setText(adsRemoved ? "Ads removed" : "Remove Ads");
        buyRemoveAds.setEnabled(!adsRemoved);
        buyRemoveAds.setOnClickListener(v -> billing.buyRemoveAds(this));
        container.addView(buyRemoveAds);

        new AlertDialog.Builder(this)
                .setTitle("Paused")
                .setView(container)
                .setPositiveButton("Resume", (d, w) -> resumeGame())
                .setNegativeButton("Restart", (d, w) -> {
                    if (paused) resumeGame();
                    startGame();
                })
                .setNeutralButton("Leaderboards", (d,w) -> {
                    if (social != null && social.isSignedIn()) {
                        social.showLeaderboards(this);
                    } else {
                        confirmPlayGamesSignIn();
                    }
                })
                .setCancelable(false)
                .show();
    }

    /** Ask the user before triggering the interactive Play Games sign-in UI. */
    private void confirmPlayGamesSignIn() {
        new AlertDialog.Builder(this)
                .setTitle("Google Play Games")
                .setMessage("Sign in to view leaderboards and earn achievements?")
                .setPositiveButton("Sign in", (d,w) -> social.signIn(this, null))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showSettings() {
        boolean signed = social != null && social.isSignedIn();
        java.util.ArrayList<String> items = new java.util.ArrayList<>();
        items.add("Change Mode");
        items.add("Leaderboards");
        items.add("Achievements");
        items.add(adsRemoved ? "Ads already removed" : "Remove Ads");
        items.add("Restore Purchases");
        if (!signed) items.add("Sign in to Play Games");

        new AlertDialog.Builder(this)
                .setTitle("Settings")
                .setItems(items.toArray(new CharSequence[0]), (d, which) -> {
                    String sel = items.get(which);
                    switch (sel) {
                        case "Change Mode": showModeChooser(); break;
                        case "Leaderboards":
                            if (social != null && social.isSignedIn()) social.showLeaderboards(this);
                            else confirmPlayGamesSignIn();
                            break;
                        case "Achievements":
                            if (social != null && social.isSignedIn()) social.showAchievements(this);
                            else confirmPlayGamesSignIn();
                            break;
                        case "Remove Ads":   if (!adsRemoved) billing.buyRemoveAds(this); break;
                        case "Restore Purchases": billing.restore(); break;
                        case "Sign in to Play Games":
                            confirmPlayGamesSignIn();
                            break;
                    }
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
            case ENDLESS:     return "Endless";
            case CHILL:       return "Chill";
            case EXTRA_CHILL: return "Extra Chill";
            default:          return "Time Attack";
        }
    }

    // ===== Billing callbacks =====
    @Override public void onBillingReady() { }
    @Override public void onAdsRemoved() {
        adsRemoved = true;
        prefs.edit().putBoolean("ads_removed", true).apply();
    }
    @Override public void onPurchaseFailed(String reason) { }

    // ===== Lifecycle =====
    @Override protected void onPause() {
        super.onPause();
        if (running && !paused) pauseGame(false);
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (audio != null) audio.release();
    }

    // ==== Helpers ====
    private int dp(int v) {
        float d = getResources().getDisplayMetrics().density;
        return Math.round(v * d);
    }

    /** Returns the string value for name in res/values/strings.xml, or null if missing. */
    private String getStringSafe(String name) {
        int id = getResources().getIdentifier(name, "string", getPackageName());
        return id == 0 ? null : getString(id);
    }
    private void maybeUnlockAchievement(String resName, boolean condition) {
        if (!condition || social == null || !social.isSignedIn()) return;
        String achId = getStringSafe(resName);
        if (achId != null) social.unlock(this, achId);
    }
}
