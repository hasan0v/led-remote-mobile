package az.kickdl.app.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.LinearInterpolator;

import az.kickdl.app.data.Prefs;

/** Slowly drifting soft color blobs behind the glass surfaces. */
public class GlassBackground extends View {
    private static final int[] COLORS = {0xFF53FC18, 0xFF7C5CFF, 0xFF1FC8E6, 0xFF2BFF88};
    private static final float[] ALPHAS = {0.20f, 0.24f, 0.16f, 0.10f};
    private final Paint[] paints = new Paint[COLORS.length];
    private final Matrix m = new Matrix();
    private final Paint base = new Paint();
    private ValueAnimator anim;
    private float t;
    private long lastFrame;

    public GlassBackground(Context c) {
        super(c);
        for (int i = 0; i < COLORS.length; i++) {
            paints[i] = new Paint(Paint.ANTI_ALIAS_FLAG);
            int col = COLORS[i];
            int a = Math.round(255 * ALPHAS[i]);
            paints[i].setShader(new RadialGradient(0, 0, 1f,
                    new int[]{(a << 24) | (col & 0xFFFFFF), ((a / 3) << 24) | (col & 0xFFFFFF), col & 0xFFFFFF},
                    new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        }
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        base.setShader(new LinearGradient(0, 0, 0, h, new int[]{0xFF0A0C12, 0xFF07080C, 0xFF05060A},
                null, Shader.TileMode.CLAMP));
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        restart();
    }

    public void restart() {
        if (anim != null) anim.cancel();
        if (!Prefs.animatedBg()) { invalidate(); return; }
        anim = ValueAnimator.ofFloat(0f, (float) (Math.PI * 2));
        anim.setDuration(48000);
        anim.setRepeatCount(ValueAnimator.INFINITE);
        anim.setInterpolator(new LinearInterpolator());
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) {
                // ~30 fps is plenty for a slow drift and halves the GPU work.
                long now = android.os.SystemClock.uptimeMillis();
                if (now - lastFrame < 32) return;
                lastFrame = now;
                t = (Float) a.getAnimatedValue();
                invalidate();
            }
        });
        anim.start();
    }

    @Override protected void onDetachedFromWindow() {
        if (anim != null) anim.cancel();
        super.onDetachedFromWindow();
    }

    @Override protected void onVisibilityChanged(View changed, int visibility) {
        super.onVisibilityChanged(changed, visibility);
        if (anim == null) return;
        if (visibility == VISIBLE) { if (anim.isPaused()) anim.resume(); } else anim.pause();
    }

    @Override protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        c.drawRect(0, 0, w, h, base);
        float d = Math.max(w, h);
        draw(c, 0, w * (0.15f + 0.12f * (float) Math.sin(t)), h * (0.12f + 0.06f * (float) Math.cos(t * 2)), d * 0.62f);
        draw(c, 1, w * (0.92f + 0.10f * (float) Math.cos(t)), h * (0.36f + 0.10f * (float) Math.sin(t * 2 + 1)), d * 0.58f);
        draw(c, 2, w * (0.25f + 0.15f * (float) Math.sin(t * 3 + 2)), h * (0.86f + 0.05f * (float) Math.cos(t)), d * 0.55f);
        draw(c, 3, w * (0.70f + 0.18f * (float) Math.sin(t + 4)), h * (0.70f + 0.12f * (float) Math.cos(t * 2 + 3)), d * 0.40f);
        // vignette for depth
        c.drawColor(Color.argb(40, 0, 0, 0));
    }

    private void draw(Canvas c, int i, float x, float y, float r) {
        m.reset();
        m.setScale(r, r);
        m.postTranslate(x, y);
        paints[i].getShader().setLocalMatrix(m);
        c.drawCircle(x, y, r, paints[i]);
    }
}
