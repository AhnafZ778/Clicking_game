package com.projuktilipi.Touchme;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;

public class GameView extends View {

    public interface GameEvents {
        /** points gained, current streak, fever-active? */
        void onHit(int points, int streak, boolean fever);
        /** called when a target expires (used for HARDCORE) */
        void onMiss();
    }

    // Paints
    private final Paint bgPaint = new Paint();
    private final Paint targetPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint particlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Random rnd = new Random();
    private final ArrayList<Target> targets = new ArrayList<>();
    private final ArrayList<Explosion> explosions = new ArrayList<>();

    // Timing
    private long nextSpawnAt = 0;
    private boolean running = false;
    private boolean paused = false;
    private long pauseStartedAt = 0;

    // Size
    private int widthPx = 0;
    private int heightPx = 0;

    private final GameEvents events;
    private boolean hapticsEnabled = true;

    // Config + audio hooks
    private GameConfig config = GameConfig.forMode(GameMode.TIME_ATTACK);
    private int scoreRef = 0; // main pushes current score to adjust difficulty
    private AudioEngine audio; // optional; set from activity

    // Simple streak/fever
    private int streak = 0;
    private long lastHitAt = 0;
    private boolean fever = false;

    public GameView(Context c, GameEvents e) {
        super(c);
        this.events = e;
        init();
    }
    public GameView(Context c, AttributeSet a) {
        super(c, a);
        this.events = null;
        init();
    }

    private void init() {
        bgPaint.setColor(Color.BLACK);
        targetPaint.setStyle(Paint.Style.FILL);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(6f);
        particlePaint.setStyle(Paint.Style.FILL);
        setClickable(true);
        setHapticFeedbackEnabled(true);
    }

    public void setConfig(GameConfig cfg) { this.config = cfg; }
    public void setAudioEngine(AudioEngine ae) { this.audio = ae; }
    public void setScoreForDifficulty(int score) { this.scoreRef = Math.max(0, score); }
    public void setHapticsEnabled(boolean enabled) { this.hapticsEnabled = enabled; }

    /** NEW: allow MainActivity to tint bg (Story mode, etc). */
    public void setBgColor(int color) {
        bgPaint.setColor(color);
        invalidate();
    }

    public void start() {
        running = true;
        paused = false;
        long now = SystemClock.uptimeMillis();
        nextSpawnAt = now + 400;
        post(frameTick);
    }

    public void pause() {
        if (!running || paused) return;
        paused = true;
        pauseStartedAt = SystemClock.uptimeMillis();
        removeCallbacks(frameTick);
        invalidate();
    }

    public void resume() {
        if (!running || !paused) return;
        long now = SystemClock.uptimeMillis();
        long pausedDur = now - pauseStartedAt;
        nextSpawnAt += pausedDur;
        for (int i = 0; i < targets.size(); i++) targets.get(i).bornAt += pausedDur;
        for (int i = 0; i < explosions.size(); i++) explosions.get(i).bornAt += pausedDur;
        paused = false;
        post(frameTick);
    }

    public void stop() {
        running = false;
        paused = false;
        removeCallbacks(frameTick);
    }

    public void reset() {
        targets.clear();
        explosions.clear();
        streak = 0;
        fever = false;
        invalidate();
    }

    private final Runnable frameTick = new Runnable() {
        @Override public void run() {
            if(!running || paused) return;

            long now = SystemClock.uptimeMillis();

            // Spawn
            if(now >= nextSpawnAt) {
                spawnTarget();

                // Smoother difficulty ramp that respects scaleDiv
                int scaled = (config.scaleDiv <= 1) ? scoreRef : (scoreRef / config.scaleDiv);
                int dynSpawn = DifficultyCurve.spawnIntervalMs(config.baseSpawnMs, scaled);
                int jitter = 100 + rnd.nextInt(180);
                nextSpawnAt = now + dynSpawn + jitter;
            }

            // Update & cull
            int scaled = (config.scaleDiv <= 1) ? scoreRef : (scoreRef / config.scaleDiv);
            int life = DifficultyCurve.targetLifeMs(config.baseLifeMs, scaled);

            for(Iterator<Target> it = targets.iterator(); it.hasNext(); ) {
                Target t = it.next();
                // subtle movement
                t.cx += t.vx; t.cy += t.vy;
                if (t.cx < t.r) { t.cx = t.r; t.vx = -t.vx; }
                if (t.cy < t.r) { t.cy = t.r; t.vy = -t.vy; }
                if (t.cx > widthPx - t.r) { t.cx = widthPx - t.r; t.vx = -t.vx; }
                if (t.cy > heightPx - t.r) { t.cy = heightPx - t.r; t.vy = -t.vy; }

                if(now - t.bornAt >= life) {
                    it.remove();
                    // miss handling only if the mode wants it (e.g., HARDCORE)
                    if (events != null && config.failOnMiss) events.onMiss();
                    if (audio != null && config.failOnMiss) audio.playMiss();
                }
            }

            // Explosions lifetime
            for (Iterator<Explosion> it = explosions.iterator(); it.hasNext();) {
                Explosion e = it.next();
                if (now - e.bornAt > 380) it.remove();
            }

            invalidate();
            GameView.this.postOnAnimation(frameTick);
        }
    };

    private void spawnTarget() {
        if(widthPx == 0 || heightPx == 0) return;

        int scaled = (config.scaleDiv <= 1) ? scoreRef : (scoreRef / config.scaleDiv);
        int r = DifficultyCurve.radiusPx(config.minRadius, config.maxRadius, scaled);

        int x = r + rnd.nextInt(Math.max(1, widthPx - 2*r));
        int y = r + rnd.nextInt(Math.max(1, heightPx - 2*r));

        int color = Color.rgb(120 + rnd.nextInt(136), 60 + rnd.nextInt(120), 120 + rnd.nextInt(136));
        int ring = Color.WHITE;

        Target t = new Target(x, y, r, color, ring, SystemClock.uptimeMillis());
        // small chance of moving target (not in CHILL)
        if (!config.chill && rnd.nextFloat() < 0.15f) {
            t.vx = rnd.nextBoolean()? 2: -2;
            t.vy = rnd.nextBoolean()? 2: -2;
        }
        targets.add(t);
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        widthPx = w;
        heightPx = h;
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawRect(0, 0, getWidth(), getHeight(), bgPaint);

        long now = SystemClock.uptimeMillis();
        int scaled = (config.scaleDiv <= 1) ? scoreRef : (scoreRef / config.scaleDiv);
        int life = DifficultyCurve.targetLifeMs(config.baseLifeMs, scaled);

        // Draw targets (with spawn scale-in + ring fade)
        for(Target t : targets) {
            float p = (float)(now - t.bornAt) / (float)life;
            if(p < 0) p = 0; if(p > 1) p = 1;

            // Spawn scale-in: first 140ms
            float spawnDur = 140f;
            float spawnP = Math.min(1f, (now - t.bornAt) / spawnDur);
            float scale = 0.7f + 0.3f * spawnP;

            targetPaint.setColor(t.color);
            canvas.drawCircle(t.cx, t.cy, t.r * scale, targetPaint);

            ringPaint.setColor(t.ringColor);
            float rr = t.r + 6;
            ringPaint.setAlpha((int)(255 * (1f - p)));
            canvas.drawCircle(t.cx, t.cy, rr * scale, ringPaint);
            ringPaint.setAlpha(255);
        }

        // Draw particles
        long exNow = SystemClock.uptimeMillis();
        for (Explosion e : explosions) {
            float exP = (exNow - e.bornAt) / 380f;
            if (exP < 0) exP = 0; if (exP > 1) exP = 1;
            int alpha = (int)(255 * (1f - exP));
            particlePaint.setColor(e.color);
            particlePaint.setAlpha(alpha);
            for (int i = 0; i < e.px.length; i++) {
                e.px[i] += e.vx[i];
                e.py[i] += e.vy[i];
                canvas.drawCircle(e.px[i], e.py[i], 3f, particlePaint);
            }
            particlePaint.setAlpha(255);
        }
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if(event.getAction() == MotionEvent.ACTION_DOWN) {
            float x = event.getX(), y = event.getY();

            for(int i = targets.size() - 1; i >= 0; i--) {
                Target t = targets.get(i);
                float dx = x - t.cx, dy = y - t.cy;
                if(dx*dx + dy*dy <= t.r * t.r) {
                    targets.remove(i);

                    // Hit feedback
                    if (hapticsEnabled) performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    if (audio != null) audio.playTap();

                    // scoring: maintain streak if within 1.2s window
                    long now = SystemClock.uptimeMillis();
                    if (now - lastHitAt <= 1200) streak++; else streak = 1;
                    lastHitAt = now;

                    // simple fever when streak >= 10
                    fever = streak >= 10;
                    int points = fever ? 2 : 1;

                    if (events != null) events.onHit(points, streak, fever);
                    spawnExplosion(t.cx, t.cy, t.color);

                    invalidate();
                    return true;
                }
            }

            // Optional: tap miss feedback (penalty only if config.failOnMiss)
            if (audio != null && config.failOnMiss) audio.playMiss();
        }
        return super.onTouchEvent(event);
    }

    private void spawnExplosion(float cx, float cy, int color) {
        Explosion e = new Explosion(cx, cy, color, SystemClock.uptimeMillis());
        explosions.add(e);
    }

    private static class Target {
        int cx, cy, r;
        int color, ringColor;
        long bornAt;
        int vx = 0, vy = 0; // small movement

        Target(int x, int y, int rr, int c, int rc, long t) {
            cx = x; cy = y; r = rr; color = c; ringColor = rc; bornAt = t;
        }
    }

    private static class Explosion {
        final float[] px = new float[12];
        final float[] py = new float[12];
        final float[] vx = new float[12];
        final float[] vy = new float[12];
        final int color;
        long bornAt;
        Explosion(float cx, float cy, int color, long t) {
            this.color = color; this.bornAt = t;
            Random r = new Random();
            for (int i = 0; i < 12; i++) {
                px[i] = cx; py[i] = cy;
                double ang = (Math.PI * 2) * (i / 12.0) + r.nextFloat() * 0.3 - 0.15;
                float speed = 3.5f + r.nextFloat() * 2.2f;
                vx[i] = (float)(Math.cos(ang) * speed);
                vy[i] = (float)(Math.sin(ang) * speed);
            }
        }
    }
}
