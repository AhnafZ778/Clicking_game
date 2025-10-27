package com.projuktilipi.Touchme;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;

/** Small "juice": rising +points text. */
public class FloatingText {
    public float x, y;
    public final String text;
    public final int color;
    public long bornAt;          // <-- no longer final
    public final long lifeMs;

    public FloatingText(float x, float y, String text, int color, long lifeMs) {
        this.x = x; this.y = y; this.text = text; this.color = color;
        this.lifeMs = lifeMs;
        this.bornAt = SystemClock.uptimeMillis();
    }

    /** Shift birth time forward (used when resuming after a pause). */
    public void shiftBorn(long deltaMs) {
        bornAt += deltaMs;
    }

    /** @return true if still alive */
    public boolean draw(Canvas c, Paint p) {
        long now = SystemClock.uptimeMillis();
        float t = Math.min(1f, (now - bornAt) / (float) lifeMs);
        if (t >= 1f) return false;

        // rise up ~30px and fade
        float dy = -30f * t;
        int alpha = (int) (255 * (1f - t));
        p.setColor(color);
        p.setAlpha(alpha);
        p.setTextSize(28f);
        p.setFakeBoldText(true);
        c.drawText(text, x, y + dy, p);
        p.setAlpha(255);
        return true;
    }
}
