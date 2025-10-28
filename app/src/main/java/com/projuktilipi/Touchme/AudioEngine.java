package com.projuktilipi.Touchme;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.RawRes;

/**
 * Background music + SFX helper
 * - SFX via SoundPool (kept from previous)
 * - BGM via MediaPlayer with audio focus + fade in/out + ducking
 */
public class AudioEngine {
    // ---------- SFX ----------
    private final android.media.SoundPool pool;
    private final Handler main = new Handler(Looper.getMainLooper());
    private int idTap = 0, idMiss = 0, idPower = 0;
    private volatile boolean sfxReady = false;
    private boolean sfxEnabled = true;

    // ---------- BGM ----------
    private final Context app;
    private final AudioManager am;
    private MediaPlayer bgm;
    private boolean musicEnabled = true;
    private float bgmTargetVol = 0.35f;   // your default music volume
    private float bgmCurrentVol = 0f;
    private boolean focusGranted = false;

    private AudioFocusRequest focusReq;

    public AudioEngine(Context ctx) {
        app = ctx.getApplicationContext();
        am = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);

        AudioAttributes sfxAA = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();

        pool = new android.media.SoundPool.Builder()
                .setMaxStreams(4)
                .setAudioAttributes(sfxAA)
                .build();

        pool.setOnLoadCompleteListener((sp, sampleId, status) -> {
            if (status == 0) sfxReady = true;
        });

        idTap   = safeLoad(R.raw.sfx_tap);
        idMiss  = safeLoad(R.raw.sfx_miss);
        idPower = safeLoad(R.raw.sfx_powerup);
    }

    private int safeLoad(@RawRes int resId) { try { return pool.load(app, resId, 1); } catch (Exception e) { return 0; } }

    // ---------- SFX API ----------
    public void prewarm() { if (sfxReady && idTap != 0) pool.play(idTap, 0,0,0,0,1); else main.postDelayed(this::prewarm, 60); }
    public void setSfxEnabled(boolean v) { sfxEnabled = v; }
    public void playTap()   { if (sfxEnabled && sfxReady && idTap   != 0) pool.play(idTap,1,1,1,0,1); }
    public void playMiss()  { if (sfxEnabled && sfxReady && idMiss  != 0) pool.play(idMiss,1,1,1,0,1); }
    public void playPower() { if (sfxEnabled && sfxReady && idPower != 0) pool.play(idPower,1,1,1,0,1); }

    // ---------- BGM internals ----------
    private final AudioManager.OnAudioFocusChangeListener focusListener = focusChange -> {
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_GAIN:
                focusGranted = true;
                // restore volume and resume if we had paused for loss
                fadeTo(bgmTargetVol, 300);
                if (bgm != null && !bgm.isPlaying() && musicEnabled) safeStart();
                break;
            case AudioManager.AUDIOFOCUS_LOSS:
                focusGranted = false;
                pauseMusic();
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                pauseMusic();
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                fadeTo(0.12f * bgmTargetVol, 150);
                break;
        }
    };

    private boolean requestFocus() {
        if (focusGranted) return true;
        int res;
        if (Build.VERSION.SDK_INT >= 26) {
            if (focusReq == null) {
                AudioAttributes aa = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build();
                focusReq = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setOnAudioFocusChangeListener(focusListener, main)
                        .setAudioAttributes(aa)
                        .setWillPauseWhenDucked(false)
                        .build();
            }
            res = am.requestAudioFocus(focusReq);
        } else {
            res = am.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        }
        focusGranted = (res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
        return focusGranted;
    }

    private void abandonFocus() {
        focusGranted = false;
        if (Build.VERSION.SDK_INT >= 26 && focusReq != null) {
            am.abandonAudioFocusRequest(focusReq);
        } else {
            am.abandonAudioFocus(focusListener);
        }
    }

    private void ensureBgm(@RawRes int resId, boolean loop) {
        if (bgm != null) {
            try { bgm.stop(); } catch (Exception ignored) {}
            try { bgm.release(); } catch (Exception ignored) {}
            bgm = null;
        }
        bgm = MediaPlayer.create(app, resId);
        if (bgm != null) {
            bgm.setLooping(loop);
            setPlayerVolume(0f);
        }
    }

    private void setPlayerVolume(float v) {
        bgmCurrentVol = Math.max(0f, Math.min(1f, v));
        if (bgm != null) bgm.setVolume(bgmCurrentVol, bgmCurrentVol);
    }

    private void fadeTo(float target, long ms) {
        final float start = bgmCurrentVol;
        final float delta = target - start;
        final long steps = Math.max(1, ms / 16);
        final long[] i = {0};
        main.removeCallbacks(fadeTick);
        fadeTarget = target;
        fadeSteps = steps;
        fadeStart = start;
        fadeDelta = delta;
        i[0] = 0;
        main.post(fadeTick);
    }

    private float fadeTarget, fadeStart, fadeDelta; private long fadeSteps;
    private final Runnable fadeTick = new Runnable() {
        long tick = 0;
        @Override public void run() {
            if (bgm == null) return;
            if (tick >= fadeSteps) { setPlayerVolume(fadeTarget); return; }
            float p = (float)tick / (float)fadeSteps;
            setPlayerVolume(fadeStart + fadeDelta * p);
            tick++;
            main.postDelayed(this, 16);
        }
    };

    private void safeStart() {
        try { if (bgm != null && !bgm.isPlaying()) bgm.start(); } catch (Exception ignored) {}
    }

    // ---------- BGM API ----------
    public void setMusicEnabled(boolean v) {
        musicEnabled = v;
        if (!musicEnabled) stopMusic();
    }

    /** Start (or switch to) a track, looped, with fade-in. */
    public void startMusic(@RawRes int resId, boolean loop) {
        if (!musicEnabled) return;
        if (!requestFocus()) return;
        ensureBgm(resId, loop);
        if (bgm == null) return;
        safeStart();
        fadeTo(bgmTargetVol, 400);
    }

    /** Pause (keep player) – used for in-app pause or focus loss. */
    public void pauseMusic() {
        main.removeCallbacks(fadeTick);
        try { if (bgm != null && bgm.isPlaying()) bgm.pause(); } catch (Exception ignored) {}
    }

    /** Resume if we still want music and have focus. */
    public void resumeMusic() {
        if (!musicEnabled) return;
        if (!requestFocus()) return;
        safeStart();
        fadeTo(bgmTargetVol, 250);
    }

    /** Stop + release focus with fade-out. */
    public void stopMusic() {
        main.removeCallbacks(fadeTick);
        fadeTo(0f, 200);
        main.postDelayed(() -> {
            try { if (bgm != null) { bgm.stop(); bgm.release(); } } catch (Exception ignored) {}
            bgm = null;
            abandonFocus();
        }, 220);
    }

    public void setMusicVolume(float v) {
        bgmTargetVol = Math.max(0f, Math.min(1f, v));
        if (bgm != null) setPlayerVolume(bgmTargetVol);
    }

    public void release() {
        try { pool.release(); } catch (Exception ignored) {}
        stopMusic();
    }
}
