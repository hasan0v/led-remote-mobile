package az.kickdl.app.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;

/** Rounded progress bar with eased value changes, a travelling shine and an indeterminate mode. */
public class ProgressBarView extends View {
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private float value, shown;
    private int c1 = 0xFF62FF2B, c2 = 0xFF2BD9F0;
    private boolean active, indeterminate;
    private float phase;
    private ValueAnimator valueAnim, loop;

    public ProgressBarView(Context c) {
        super(c);
        track.setColor(0x1AFFFFFF);
    }

    public void setColors(int a, int b) {
        if (a == c1 && b == c2) return;
        c1 = a;
        c2 = b;
        fill.setShader(null);
        invalidate();
    }

    public void setProgress(float v, boolean animate) {
        v = Math.max(0, Math.min(1, v));
        if (Math.abs(v - value) < 0.0005f && !(shown != v && !animate)) return;
        value = v;
        if (valueAnim != null) valueAnim.cancel();
        if (!animate || !isAttachedToWindow()) {
            shown = v;
            invalidate();
            return;
        }
        valueAnim = ValueAnimator.ofFloat(shown, v);
        valueAnim.setDuration(450);
        valueAnim.setInterpolator(new DecelerateInterpolator());
        valueAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) {
                shown = (Float) a.getAnimatedValue();
                invalidate();
            }
        });
        valueAnim.start();
    }

    /** Active bars show a moving highlight; indeterminate bars show a sliding segment. */
    public void setState(boolean active, boolean indeterminate) {
        if (this.active == active && this.indeterminate == indeterminate) return;
        this.active = active;
        this.indeterminate = indeterminate;
        updateLoop();
        invalidate();
    }

    private void updateLoop() {
        boolean need = (active || indeterminate) && isAttachedToWindow();
        if (need && loop == null) {
            loop = ValueAnimator.ofFloat(0f, 1f);
            loop.setDuration(1500);
            loop.setRepeatCount(ValueAnimator.INFINITE);
            loop.setInterpolator(new LinearInterpolator());
            loop.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override public void onAnimationUpdate(ValueAnimator a) {
                    phase = (Float) a.getAnimatedValue();
                    invalidate();
                }
            });
            loop.start();
        } else if (!need && loop != null) {
            loop.cancel();
            loop = null;
        }
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateLoop();
    }

    @Override protected void onDetachedFromWindow() {
        if (loop != null) { loop.cancel(); loop = null; }
        super.onDetachedFromWindow();
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) { fill.setShader(null); }

    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight(), rad = h / 2f;
        r.set(0, 0, w, h);
        c.drawRoundRect(r, rad, rad, track);
        if (fill.getShader() == null) fill.setShader(new LinearGradient(0, 0, w, 0, c1, c2, Shader.TileMode.CLAMP));
        if (indeterminate) {
            float seg = w * 0.35f;
            float x = -seg + (w + seg) * phase;
            r.set(Math.max(0, x), 0, Math.min(w, x + seg), h);
            if (r.width() > 0) c.drawRoundRect(r, rad, rad, fill);
            return;
        }
        float fw = Math.max(shown > 0 ? h : 0, w * shown);
        if (fw <= 0) return;
        r.set(0, 0, fw, h);
        c.drawRoundRect(r, rad, rad, fill);
        if (active) {
            float sw = Math.max(h * 6, w * 0.18f);
            float x = -sw + (fw + sw) * phase;
            shine.setShader(new LinearGradient(x, 0, x + sw, 0, new int[]{0x00FFFFFF, 0x66FFFFFF, 0x00FFFFFF},
                    null, Shader.TileMode.CLAMP));
            c.save();
            c.clipRect(0, 0, fw, h);
            c.drawRoundRect(r, rad, rad, shine);
            c.restore();
        }
    }
}
