package com.projuktilipi.Touchme;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.os.Handler;
import android.os.Looper;

/**
 * Robust SFX/BGM AudioEngine:
 * - Safe loads (optional raw files won't crash)
 * - Queues first play until SoundPool is ready (fixes "first tap is silent")
 * - Simple toggles for SFX/Music
 */
public class AudioEngine {
    private final SoundPool pool;
    private final Handler main = new Handler(Looper.getMainLooper());

    private int idTap = 0, idMiss = 0, idPower = 0;
    private volatile boolean ready = false;

    private MediaPlayer bgm;
    private boolean sfxEnabled = true, musicEnabled = false;

    public AudioEngine(Context ctx) {
        Context app = ctx.getApplicationContext();

        AudioAttributes aa = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();

        pool = new SoundPool.Builder()
                .setMaxStreams(4)
                .setAudioAttributes(aa)
                .build();

        pool.setOnLoadCompleteListener((sp, sampleId, status) -> {
            if (status == 0) ready = true;
        });

        // Required SFX
        idTap   = safeLoad(app, R.raw.sfx_tap);

        // Optional SFX (safe even if missing)
        idMiss  = safeLoad(app, R.raw.sfx_miss);
        idPower = safeLoad(app, R.raw.sfx_powerup);

        // Optional BGM (start disabled by default)
        try {
            bgm = MediaPlayer.create(app, R.raw.music_loop);
            if (bgm != null) {
                bgm.setLooping(true);
                bgm.setVolume(0.45f, 0.45f);
            }
        } catch (Exception ignored) { bgm = null; }
    }

    private int safeLoad(Context c, int resId) {
        try { return pool.load(c, resId, 1); }
        catch (Exception ignored) { return 0; }
    }

    /** Call once after creating the engine to help the decoder warm up. */
    public void prewarm() {
        // Play muted once after load; if not ready yet, retry shortly.
        if (ready && idTap != 0) {
            pool.play(idTap, 0f, 0f, 0, 0, 1f);
        } else {
            main.postDelayed(this::prewarm, 60);
        }
    }

    public void setSfxEnabled(boolean v) { sfxEnabled = v; }
    public void setMusicEnabled(boolean v) {
        musicEnabled = v;
        if (v) playMusic(); else pauseMusic();
    }

    public void playTap()   { playQueued(idTap); }
    public void playMiss()  { playQueued(idMiss); }
    public void playPower() { playQueued(idPower); }

    private void playQueued(final int soundId) {
        if (!sfxEnabled || soundId == 0) return;
        playWhenReady(soundId, 14); // ~14 * 70ms ≈ 1s max wait
    }

    private void playWhenReady(final int soundId, final int attemptsLeft) {
        if (!sfxEnabled || soundId == 0 || attemptsLeft <= 0) return;
        if (ready) {
            pool.play(soundId, 1f, 1f, 1, 0, 1f); // use 1.0 rate (easier to hear)
        } else {
            main.postDelayed(() -> playWhenReady(soundId, attemptsLeft - 1), 70);
        }
    }

    public void playMusic() {
        if (musicEnabled && bgm != null && !bgm.isPlaying()) {
            try { bgm.start(); } catch (Exception ignored) {}
        }
    }
    public void pauseMusic() {
        if (bgm != null && bgm.isPlaying()) {
            try { bgm.pause(); } catch (Exception ignored) {}
        }
    }

    public void release() {
        try { pool.release(); } catch (Exception ignored) {}
        if (bgm != null) { try { bgm.release(); } catch (Exception ignored) {} bgm = null; }
    }
}
