package com.projuktilipi.Touchme;

import android.content.Intent;
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

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import com.google.android.gms.games.PlayGamesSdk;

public class MainActivity extends AppCompatActivity implements BillingManager.Listener {

    // ===== UI =====
    private FrameLayout game_container;
    private View overlay;
    private View tutorialOverlay; // optional
    private TextView score_text, time_text, high_text, mode_text; // mode_text optional
    private Button start_button, pause_button, tutorialGotIt;     // optional
    private ImageButton settings_button;                          // optional
    private ImageButton changeModeButton;                         // optional, if present in layout

    // ===== Game =====
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
    private boolean musicEnabled   = false; // default off
    private boolean sfxEnabled     = true;

    private boolean adsRemoved = false;
    private GameMode mode = GameMode.TIME_ATTACK;

    // Systems
    private AudioEngine audio;
    private AdsManager ads;
    private BillingManager billing;
    private SocialClient social;

    // Play Games prompt-once key
    private static final String PREF_PGS_ASKED_ONCE = "pgs_asked_once";

    // Ads control
    private long lastEndlessAdAt = 0L;       // last time we showed an ad in Endless
    private int hardcoreDeathsSinceAd = 0;   // count deaths in Hardcore

    // Story progress (placeholder)
    private static final String PREF_STORY_LEVEL = "story_level";
    private int storyLevel = 1; // start at level 1

    // Mode change flow
    private GameMode modeBeforePicker = null;
    private final ActivityResultLauncher<Intent> modePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    String name = result.getData().getStringExtra(ModeSelectActivity.EXTRA_SELECTED_MODE);
                    if (name != null) {
                        GameMode picked;
                        try { picked = GameMode.valueOf(name); }
                        catch (IllegalArgumentException e) { picked = mode; }

                        final GameMode pickedMode = picked; // effectively final for lambdas

                        if (pickedMode == modeBeforePicker) {
                            // Same mode chosen; ask to resume or restart
                            new AlertDialog.Builder(this)
                                    .setTitle("Same mode selected")
                                    .setMessage("Do you want to resume your paused run or start fresh?")
                                    .setPositiveButton("Resume", (d,w) -> resumeGame())
                                    .setNegativeButton("Start fresh", (d,w) -> {
                                        mode = pickedMode;
                                        prefs.edit().putInt("mode", mode.ordinal()).apply();
                                        if (mode_text != null) mode_text.setText(modeToLabel(mode));
                                        startGame();
                                    })
                                    .setCancelable(false)
                                    .show();
                        } else {
                            // Different mode selected; confirm discard of current run
                            new AlertDialog.Builder(this)
                                    .setTitle("Switch mode?")
                                    .setMessage("Switching to " + modeToLabel(pickedMode) + " will end the current run.")
                                    .setPositiveButton("Switch", (d,w) -> {
                                        mode = pickedMode;
                                        prefs.edit().putInt("mode", mode.ordinal()).apply();
                                        if (mode_text != null) mode_text.setText(modeToLabel(mode));
                                        startGame();
                                    })
                                    .setNegativeButton("Cancel", (d,w) -> resumeGame())
                                    .setCancelable(false)
                                    .show();
                        }
                    } else {
                        // No selection returned; just resume
                        resumeGame();
                    }
                } else {
                    // Cancelled; resume the current run
                    resumeGame();
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
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
        changeModeButton= findViewById(R.id.change_mode_button); // optional

        // Prefs
        prefs = getSharedPreferences("touch_me_prefs", MODE_PRIVATE);
        best = prefs.getInt("best", 0);
        hapticsEnabled = prefs.getBoolean("haptics_enabled", true);
        musicEnabled   = prefs.getBoolean("music_enabled", false);
        sfxEnabled     = prefs.getBoolean("sfx_enabled", true);
        adsRemoved     = prefs.getBoolean("ads_removed", false);
        storyLevel     = prefs.getInt(PREF_STORY_LEVEL, 1);

        // Determine mode:
        // 1) If launched from ModeSelectActivity (launcher path), EXTRA_MODE is present.
        // 2) Otherwise, fall back to saved preference.
        int savedModeOrdinal = prefs.getInt("mode", GameMode.TIME_ATTACK.ordinal());
        GameMode[] modesAll = GameMode.values();
        mode = (savedModeOrdinal >= 0 && savedModeOrdinal < modesAll.length) ? modesAll[savedModeOrdinal] : GameMode.TIME_ATTACK;

        String extraMode = getIntent().getStringExtra(ModeSelectActivity.EXTRA_MODE);
        if (extraMode != null) {
            try {
                mode = GameMode.valueOf(extraMode);
                prefs.edit().putInt("mode", mode.ordinal()).apply();
            } catch (IllegalArgumentException ignored) { /* keep previous */ }
        }

        if (high_text != null) high_text.setText("Best: " + best);
        if (mode_text != null) mode_text.setText(modeToLabel(mode));
        if (time_text != null) time_text.setText(mode == GameMode.TIME_ATTACK ? "60s" : "∞");

        // Audio
        audio = new AudioEngine(this);
        audio.setSfxEnabled(sfxEnabled);
        audio.setMusicEnabled(musicEnabled);
        audio.prewarm();
        // Start menu music immediately (if enabled)
        if (musicEnabled) audio.startMusic(R.raw.music_menu, true);

        // Ads
        ads = new AdsManager();
        ads.init(this);

        // Billing
        billing = new BillingManager(this, this);
        billing.start();

        // Social (Play Games)
        social = new SocialGpgs();

        // GameView
        gameView = new GameView(this, new GameView.GameEvents() {
            @Override public void onHit(int points, int streak, boolean fever) {
                if(!running || paused) return;
                score += Math.max(points, 1);
                if (score_text != null) score_text.setText("Score: " + score);
                gameView.setScoreForDifficulty(score);
                if(score > best) {
                    best = score;
                    if (high_text != null) high_text.setText("Best: " + best);
                }
                maybeUnlockAchievement("ach_streak_10", streak >= 10);
                maybeUnlockAchievement("ach_score_50", score  >= 50);
            }
            @Override public void onMiss() {
                if (mode == GameMode.HARDCORE) {
                    hardcoreDeathsSinceAd++;
                    finishGame(); // ends immediately on miss
                }
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

        // Optional dedicated "Change Modes" icon
        if (changeModeButton != null) changeModeButton.setOnClickListener(v -> confirmChangeMode());

        // Tutorial
        if (tutorialOverlay != null && tutorialGotIt != null) {
            if (!prefs.getBoolean("tutorial_seen", false)) tutorialOverlay.setVisibility(View.VISIBLE);
            tutorialGotIt.setOnClickListener(v -> {
                tutorialOverlay.setVisibility(View.GONE);
                prefs.edit().putBoolean("tutorial_seen", true).apply();
            });
        }

        // Ask ONCE on first launch for Play Games sign-in
        maybeAskForPlayGamesOnce();
    }

    /** Ask once at first launch; no auto sign-in. */
    private void maybeAskForPlayGamesOnce() {
        boolean asked = prefs.getBoolean(PREF_PGS_ASKED_ONCE, false);
        if (asked) return;

        new AlertDialog.Builder(this)
                .setTitle("Play Games")
                .setMessage("Sign in to Google Play Games to compete on leaderboards and earn achievements?")
                .setPositiveButton("Sign in", (d,w) -> {
                    prefs.edit().putBoolean(PREF_PGS_ASKED_ONCE, true).apply();
                    social.signIn(this, null);
                })
                .setNegativeButton("Not now", (d,w) ->
                        prefs.edit().putBoolean(PREF_PGS_ASKED_ONCE, true).apply())
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

        if (mode == GameMode.TIME_ATTACK) {
            endTimeMs = System.currentTimeMillis() + cfg.roundMillis;
        } else {
            endTimeMs = Long.MAX_VALUE;
        }

        // switch to gameplay music
        if (musicEnabled) audio.startMusic(R.raw.music_game, true);

        // ads timers
        if (mode == GameMode.ENDLESS) {
            lastEndlessAdAt = System.currentTimeMillis(); // start 5-min window
        }

        gameView.reset();
        gameView.start();

        handler.removeCallbacks(tick);
        handler.post(tick);
    }

    private void pauseGame(boolean showDialog) {
        if (!running || paused) return;
        paused = true;
        pauseStartedMs = System.currentTimeMillis();
        handler.removeCallbacks(tick);
        gameView.pause();

        // play menu music on pause
        if (musicEnabled) audio.startMusic(R.raw.music_menu, true);

        if (showDialog) showPauseDialog();
    }

    private void resumeGame() {
        if (!running || !paused) return;
        long pausedDur = System.currentTimeMillis() - pauseStartedMs;
        endTimeMs += pausedDur;
        paused = false;
        gameView.resume();

        // back to game music
        if (musicEnabled) audio.startMusic(R.raw.music_game, true);

        handler.removeCallbacks(tick);
        handler.post(tick);
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if(!running || paused) return;
            long now = System.currentTimeMillis();
            long left = endTimeMs - now;
            if(left < 0) left = 0;

            if (time_text != null) {
                if (mode == GameMode.TIME_ATTACK) {
                    int sec = (int)((left + 500) / 1000);
                    time_text.setText(sec + "s");
                } else {
                    time_text.setText("∞");
                }
            }

            // Endless: timed interstitial every 5 minutes (if ads not removed)
            if (!adsRemoved && mode == GameMode.ENDLESS) {
                long fiveMin = 5L * 60L * 1000L;
                if (now - lastEndlessAdAt >= fiveMin) {
                    pauseGame(false);
                    ads.showInterstitial(MainActivity.this, () -> {
                        lastEndlessAdAt = System.currentTimeMillis();
                        resumeGame();
                    });
                    return; // wait for ad flow
                }
            }

            if (mode == GameMode.TIME_ATTACK && left == 0) {
                finishGame();
            } else {
                handler.postDelayed(this, 200);
            }
        }
    };

    private void finishGame() {
        running = false;
        paused  = false;
        gameView.stop();
        // back to menu music
        if (musicEnabled) audio.startMusic(R.raw.music_menu, true);
        if (overlay != null) overlay.setVisibility(View.VISIBLE);

        // Persist best
        int savedBest = prefs.getInt("best", 0);
        if(best >= savedBest) prefs.edit().putInt("best", best).apply();

        // Leaderboard submit (if signed in)
        if (social != null && social.isSignedIn()) {
            String lbId;
            switch (mode) {
                case ENDLESS:     lbId = getStringSafe("lb_endless_id"); break;
                case HARDCORE:    lbId = getStringSafe("lb_hardcore_id"); break;
                case CHILL:       lbId = getStringSafe("lb_chill_id");   break;
                case STORY:       lbId = getStringSafe("lb_story_id");   break;
                default:          lbId = getStringSafe("lb_time_attack_id");
            }
            if (lbId != null) social.submitScore(this, lbId, best);
        }

        // Ads policy
        if (!adsRemoved) {
            switch (mode) {
                case TIME_ATTACK:
                    ads.showInterstitial(this, null); // after each round
                    break;
                case HARDCORE:
                    if (hardcoreDeathsSinceAd >= 15) {
                        hardcoreDeathsSinceAd = 0;
                        ads.showInterstitial(this, null);
                    }
                    break;
                case ENDLESS:
                case CHILL:
                case STORY:
                default:
                    // Endless handled by timer; nothing here
                    break;
            }
        }

        // Story progress placeholder
        if (mode == GameMode.STORY) {
            storyLevel = Math.max(1, storyLevel + 1);
            prefs.edit().putInt(PREF_STORY_LEVEL, storyLevel).apply();
        }

        if (start_button != null) start_button.setText("RESTART");
    }

    // ===== Menus =====

    private void showPauseDialog() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        container.setPadding(pad, pad, pad, pad);

        // Quick Play Games sign-in icon/button when not signed in
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

        // Toggles
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
            if (isChecked) audio.startMusic(paused ? R.raw.music_menu : R.raw.music_game, true);
            else audio.stopMusic();
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

        // Change Modes button
        Button changeMode = new Button(this);
        changeMode.setText("Change Mode");
        changeMode.setOnClickListener(v -> confirmChangeMode());
        container.addView(changeMode);

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

    private void confirmChangeMode() {
        if (running && !paused) pauseGame(false);
        new AlertDialog.Builder(this)
                .setTitle("Change Mode")
                .setMessage("Do you want to change the game mode? Current progress will be paused.")
                .setPositiveButton("Yes", (d,w) -> {
                    modeBeforePicker = mode;
                    Intent i = new Intent(this, ModeSelectActivity.class);
                    i.putExtra(ModeSelectActivity.EXTRA_RETURN_RESULT, true);
                    modePickerLauncher.launch(i);
                })
                .setNegativeButton("No", (d,w) -> resumeGame())
                .setCancelable(false)
                .show();
    }

    private void showSettings() {
        boolean signed = social != null && social.isSignedIn();
        java.util.ArrayList<String> items = new java.util.ArrayList<>();
        items.add("Leaderboards");
        items.add("Achievements");
        items.add(adsRemoved ? "Ads already removed" : "Remove Ads");
        items.add("Restore Purchases");
        if (!signed) items.add("Sign in to Play Games");
        items.add("Change Mode");

        new AlertDialog.Builder(this)
                .setTitle("Settings")
                .setItems(items.toArray(new CharSequence[0]), (d, which) -> {
                    String sel = items.get(which);
                    switch (sel) {
                        case "Leaderboards":
                            if (social != null && social.isSignedIn()) social.showLeaderboards(this);
                            else confirmPlayGamesSignIn();
                            break;
                        case "Achievements":
                            if (social != null && social.isSignedIn()) social.showAchievements(this);
                            else confirmPlayGamesSignIn();
                            break;
                        case "Remove Ads":
                            if (!adsRemoved) billing.buyRemoveAds(this);
                            break;
                        case "Restore Purchases":
                            billing.restore();
                            break;
                        case "Sign in to Play Games":
                            confirmPlayGamesSignIn();
                            break;
                        case "Change Mode":
                            confirmChangeMode();
                            break;
                    }
                })
                .show();
    }

    /** Ask user before interactive Play Games sign-in. */
    private void confirmPlayGamesSignIn() {
        new AlertDialog.Builder(this)
                .setTitle("Google Play Games")
                .setMessage("Sign in to view leaderboards and earn achievements?")
                .setPositiveButton("Sign in", (d,w) -> social.signIn(this, null))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private String modeToLabel(GameMode m) {
        switch (m) {
            case ENDLESS:     return "Endless";
            case HARDCORE:    return "Hardcore";
            case CHILL:       return "Chill";
            case STORY:       return "Story";
            default:          return "Time Attack";
        }
    }

    // ===== Billing callbacks =====
    @Override public void onBillingReady() { /* enable shop UI if needed */ }

    @Override public void onAdsRemoved() {
        adsRemoved = true;
        prefs.edit().putBoolean("ads_removed", true).apply();
    }

    @Override public void onPurchaseFailed(String reason) { /* you may toast message */ }

    // ===== Lifecycle =====
    @Override protected void onPause() {
        super.onPause();
        if (running && !paused) pauseGame(false);
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (audio != null) audio.release();
    }

    // ===== Helpers =====
    private int dp(int v) {
        float d = getResources().getDisplayMetrics().density;
        return Math.round(v * d);
    }

    /** Safe string fetch from strings.xml by name; returns null if not found. */
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
