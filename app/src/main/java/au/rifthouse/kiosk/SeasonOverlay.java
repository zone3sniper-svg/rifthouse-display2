package au.rifthouse.kiosk;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

import java.util.Random;

/**
 * Seasonal decoration drawn over the web page: a string of lights or bunting along the top edge and gentle falling or flying pieces.
 * It never takes a touch, so the page underneath works exactly as normal.
 */
final class SeasonOverlay extends View {
    private int season = Season.NONE;
    private boolean animate = true;
    private final float dp;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF oval = new RectF();
    private final Random rnd = new Random();
    private long start = System.nanoTime(), last = start;

    private static final int MAX = 80;
    private int n;
    private final float[] x = new float[MAX], y = new float[MAX], vx = new float[MAX], vy = new float[MAX], sz = new float[MAX], ph = new float[MAX], rot = new float[MAX], vr = new float[MAX];
    private final int[] col = new int[MAX];

    // New Year fireworks
    private static final int BURSTS = 4, SPARKS = 28;
    private final float[] bx = new float[BURSTS], by = new float[BURSTS], bage = new float[BURSTS], bseed = new float[BURSTS];
    private final int[] bcol = new int[BURSTS];
    private final boolean[] bon = new boolean[BURSTS];
    private float nextBurst = 1f;

    private static final int[] PASTEL = {0xFFFFB3C7, 0xFFFFE08A, 0xFFB8EBC8, 0xFFC9B8F2, 0xFF9FD3F5};
    private static final int[] CONFETTI = {0xFFFFC83D, 0xFFE8E8F0, 0xFFFF5C8A, 0xFF4DB6FF, 0xFFB388FF, 0xFF5CE0A0};
    private static final int[] XMAS_LIGHTS = {0xFFE53935, 0xFFFFC107, 0xFF43A047, 0xFF1E88E5};
    private static final int[] NY_LIGHTS = {0xFFFFD54F, 0xFFFFFFFF, 0xFFC0C6D0};
    private static final int[] HALLOWEEN_LIGHTS = {0xFFFF7A00, 0xFF8E44AD, 0xFF7ED957};

    SeasonOverlay(Context c) {
        super(c);
        dp = c.getResources().getDisplayMetrics().density;
        setClickable(false);
        setFocusable(false);
        setWillNotDraw(false);
    }

    /** Which theme to show (Season.NONE hides it) and whether the pieces move. */
    void set(int newSeason, boolean moving) {
        season = newSeason;
        animate = moving;
        setVisibility(season == Season.NONE ? GONE : VISIBLE);
        populate();
        last = System.nanoTime();
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        populate();
    }

    private float r() { return rnd.nextFloat(); }

    private void populate() {
        float w = Math.max(getWidth(), 1), h = Math.max(getHeight(), 1);
        n = 0;
        for (int i = 0; i < BURSTS; i++) bon[i] = false;
        switch (season) {
            case Season.CHRISTMAS:
                n = 70;
                for (int i = 0; i < n; i++) { x[i] = r() * w; y[i] = r() * h; sz[i] = (1.5f + r() * 3.5f) * dp; vy[i] = (25 + r() * 55) * dp; vx[i] = (r() - .5f) * 10 * dp; ph[i] = r() * 6.28f; vr[i] = .8f + r() * 1.2f; }
                break;
            case Season.NEWYEAR:
                n = 55;
                for (int i = 0; i < n; i++) { x[i] = r() * w; y[i] = r() * h; sz[i] = (5 + r() * 5) * dp; vy[i] = (40 + r() * 70) * dp; vx[i] = (r() - .5f) * 30 * dp; rot[i] = r() * 360; vr[i] = (r() - .5f) * 360; col[i] = CONFETTI[rnd.nextInt(CONFETTI.length)]; }
                break;
            case Season.EASTER:
                n = 18;
                for (int i = 0; i < n; i++) { x[i] = r() * w; y[i] = r() * h; sz[i] = (16 + r() * 12) * dp; vy[i] = (18 + r() * 25) * dp; vx[i] = (r() - .5f) * 6 * dp; ph[i] = r() * 6.28f; vr[i] = .5f + r() * .8f; col[i] = PASTEL[rnd.nextInt(PASTEL.length)]; }
                break;
            case Season.HALLOWEEN:
                n = 37; // 0..6 are bats, the rest are embers
                for (int i = 0; i < n; i++) {
                    x[i] = r() * w; ph[i] = r() * 6.28f;
                    if (i < 7) { sz[i] = (18 + r() * 18) * dp; vx[i] = (25 + r() * 70) * dp * (r() < .5f ? -1 : 1); y[i] = h * (.1f + r() * .55f); }
                    else { sz[i] = (1 + r() * 2.5f) * dp; vy[i] = -(10 + r() * 25) * dp; y[i] = r() * h; }
                }
                break;
            default:
                break;
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        if (season == Season.NONE) return;
        long now = System.nanoTime();
        float dt = Math.min(0.05f, (now - last) / 1e9f), t = (now - start) / 1e9f;
        last = now;
        float w = getWidth(), h = getHeight();
        if (animate) update(dt, w, h);
        switch (season) {
            case Season.CHRISTMAS: drawSnow(c); drawLights(c, w, XMAS_LIGHTS, t); break;
            case Season.NEWYEAR: drawConfetti(c); drawFireworks(c); drawLights(c, w, NY_LIGHTS, t); break;
            case Season.EASTER: drawEggs(c); drawBunting(c, w); break;
            case Season.HALLOWEEN: drawEmbers(c); drawBats(c, t); drawLights(c, w, HALLOWEEN_LIGHTS, t); break;
            default: break;
        }
        if (animate) postInvalidateDelayed(33); // about 30 frames a second is plenty and easy on the tablet
    }

    private void update(float dt, float w, float h) {
        float m = 30 * dp;
        for (int i = 0; i < n; i++) {
            switch (season) {
                case Season.CHRISTMAS:
                case Season.EASTER:
                    y[i] += vy[i] * dt; x[i] += vx[i] * dt; ph[i] += vr[i] * dt;
                    if (y[i] > h + m) { y[i] = -m; x[i] = r() * w; }
                    break;
                case Season.NEWYEAR:
                    y[i] += vy[i] * dt; x[i] += vx[i] * dt; rot[i] += vr[i] * dt;
                    if (y[i] > h + m) { y[i] = -m; x[i] = r() * w; }
                    break;
                case Season.HALLOWEEN:
                    if (i < 7) { x[i] += vx[i] * dt; ph[i] += dt * 2.2f; if (x[i] > w + 3 * m) x[i] = -3 * m; if (x[i] < -3 * m) x[i] = w + 3 * m; }
                    else { y[i] += vy[i] * dt; ph[i] += dt; if (y[i] < -m) { y[i] = h + m; x[i] = r() * w; } }
                    break;
                default: break;
            }
        }
        if (season == Season.NEWYEAR) {
            nextBurst -= dt;
            if (nextBurst <= 0) {
                nextBurst = 1.6f + r() * 1.6f;
                for (int b = 0; b < BURSTS; b++) if (!bon[b]) { bon[b] = true; bage[b] = 0; bx[b] = w * (.12f + r() * .76f); by[b] = h * (.12f + r() * .4f); bseed[b] = r() * 6.28f; bcol[b] = CONFETTI[rnd.nextInt(CONFETTI.length)]; break; }
            }
            for (int b = 0; b < BURSTS; b++) if (bon[b]) { bage[b] += dt; if (bage[b] > 1.6f) bon[b] = false; }
        }
    }

    // ---- pieces ----
    private void drawSnow(Canvas c) {
        p.setStyle(Paint.Style.FILL);
        for (int i = 0; i < n; i++) { p.setColor(Color.argb(200, 255, 255, 255)); c.drawCircle(x[i] + (float) Math.sin(ph[i]) * 14 * dp, y[i], sz[i], p); }
    }

    private void drawConfetti(Canvas c) {
        p.setStyle(Paint.Style.FILL);
        for (int i = 0; i < n; i++) { c.save(); c.translate(x[i], y[i]); c.rotate(rot[i]); p.setColor(col[i]); c.drawRect(-sz[i] / 2, -sz[i] / 4, sz[i] / 2, sz[i] / 4, p); c.restore(); }
    }

    private void drawFireworks(Canvas c) {
        p.setStyle(Paint.Style.FILL);
        for (int b = 0; b < BURSTS; b++) {
            if (!bon[b]) continue;
            float age = bage[b], fade = Math.max(0, 1 - age / 1.6f);
            for (int s = 0; s < SPARKS; s++) {
                float a = bseed[b] + s * 6.2832f / SPARKS, sp = (80 + (s % 3) * 45) * dp;
                float px = bx[b] + (float) Math.cos(a) * sp * age, py = by[b] + (float) Math.sin(a) * sp * age + 60 * dp * age * age / 2;
                p.setColor(bcol[b]); p.setAlpha((int) (255 * fade));
                c.drawCircle(px, py, 2.2f * dp, p);
            }
        }
        p.setAlpha(255);
    }

    private void drawEggs(Canvas c) {
        p.setStyle(Paint.Style.FILL);
        for (int i = 0; i < n; i++) {
            float eh = sz[i], ew = eh * .76f;
            c.save();
            c.translate(x[i] + (float) Math.sin(ph[i]) * 18 * dp, y[i]);
            c.rotate((float) Math.sin(ph[i] * 1.3f) * 25);
            oval.set(-ew / 2, -eh / 2, ew / 2, eh / 2);
            p.setColor(col[i]); c.drawOval(oval, p);
            path.reset(); path.addOval(oval, Path.Direction.CW);
            c.save(); c.clipPath(path);
            p.setColor(Color.argb(190, 255, 255, 255)); c.drawRect(-ew / 2, -eh * .12f, ew / 2, eh * .06f, p);
            p.setColor(Color.argb(120, 255, 255, 255)); c.drawRect(-ew / 2, eh * .2f, ew / 2, eh * .3f, p);
            c.restore();
            c.restore();
        }
    }

    private void drawEmbers(Canvas c) {
        p.setStyle(Paint.Style.FILL);
        for (int i = 7; i < n; i++) { p.setColor(Color.argb(150, 255, 140, 30)); c.drawCircle(x[i] + (float) Math.sin(ph[i]) * 10 * dp, y[i], sz[i], p); }
    }

    private void drawBats(Canvas c, float t) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.argb(235, 22, 14, 30));
        for (int i = 0; i < 7 && i < n; i++) {
            float s = sz[i], flap = .35f + .65f * (.5f + .5f * (float) Math.sin(ph[i] * 6)), yy = y[i] + (float) Math.sin(ph[i]) * 24 * dp;
            c.save();
            c.translate(x[i], yy);
            if (vx[i] < 0) c.scale(-1, 1);
            oval.set(-.2f * s, -.28f * s, .2f * s, .28f * s); c.drawOval(oval, p);
            path.reset(); path.moveTo(-.18f * s, -.22f * s); path.lineTo(-.12f * s, -.5f * s); path.lineTo(-.02f * s, -.26f * s); path.close(); c.drawPath(path, p);
            path.reset(); path.moveTo(.18f * s, -.22f * s); path.lineTo(.12f * s, -.5f * s); path.lineTo(.02f * s, -.26f * s); path.close(); c.drawPath(path, p);
            for (int side = 0; side < 2; side++) {
                c.save(); if (side == 1) c.scale(-1, 1);
                path.reset(); path.moveTo(.12f * s, 0); path.quadTo(.5f * s, -.7f * s * flap, s, -.25f * s * flap);
                path.quadTo(.8f * s, .05f * s, .72f * s, .3f * s); path.quadTo(.55f * s, .1f * s, .4f * s, .28f * s); path.quadTo(.3f * s, .1f * s, .12f * s, .2f * s); path.close();
                c.drawPath(path, p); c.restore();
            }
            p.setColor(Color.argb(255, 255, 190, 60)); c.drawCircle(-.08f * s, -.08f * s, .035f * s, p); c.drawCircle(.08f * s, -.08f * s, .035f * s, p);
            p.setColor(Color.argb(235, 22, 14, 30));
            c.restore();
        }
    }

    // ---- the strip along the top edge ----
    private float wireY(float px) { return 5 * dp + 4 * dp * (float) Math.sin(px / (80 * dp)); }

    private void drawWire(Canvas c, float w) {
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.5f * dp); p.setColor(Color.argb(200, 30, 60, 40));
        path.reset(); path.moveTo(0, wireY(0));
        for (float px = 8 * dp; px <= w + 8 * dp; px += 8 * dp) path.lineTo(px, wireY(px));
        c.drawPath(path, p);
        p.setStyle(Paint.Style.FILL);
    }

    private void drawLights(Canvas c, float w, int[] colours, float t) {
        drawWire(c, w);
        int i = 0;
        for (float px = 14 * dp; px < w; px += 26 * dp, i++) {
            int base = colours[i % colours.length];
            int a = animate ? (int) (150 + 105 * Math.sin(t * 3 + i * 1.7)) : 230;
            float py = wireY(px) + 5 * dp;
            p.setColor(Color.argb(60, Color.red(base), Color.green(base), Color.blue(base))); c.drawCircle(px, py, 8 * dp, p);
            p.setColor(Color.argb(a, Color.red(base), Color.green(base), Color.blue(base))); c.drawCircle(px, py, 4 * dp, p);
        }
    }

    private void drawBunting(Canvas c, float w) {
        drawWire(c, w);
        int i = 0;
        for (float px = 10 * dp; px < w; px += 26 * dp, i++) {
            float y0 = wireY(px);
            path.reset(); path.moveTo(px - 11 * dp, y0); path.lineTo(px + 11 * dp, y0 + 1 * dp); path.lineTo(px, y0 + 20 * dp); path.close();
            p.setColor(PASTEL[i % PASTEL.length]); c.drawPath(path, p);
        }
    }
}
