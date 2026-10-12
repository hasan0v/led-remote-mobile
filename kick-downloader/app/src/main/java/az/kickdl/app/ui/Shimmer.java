package az.kickdl.app.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.LinearInterpolator;

/** Skeleton placeholder block with a sweeping highlight. */
public class Shimmer extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix m = new Matrix();
    private final RectF r = new RectF();
    private final float radius;
    private ValueAnimator anim;
    private float phase;

    public Shimmer(Context c, float radiusDp) {
        super(c);
        radius = Ui.dp(radiusDp);
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        p.setShader(new LinearGradient(0, 0, w, 0, new int[]{0x12FFFFFF, 0x26FFFFFF, 0x12FFFFFF},
                new float[]{0.3f, 0.5f, 0.7f}, Shader.TileMode.CLAMP));
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        anim = ValueAnimator.ofFloat(-1f, 1f);
        anim.setDuration(1300);
        anim.setRepeatCount(ValueAnimator.INFINITE);
        anim.setInterpolator(new LinearInterpolator());
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) {
                phase = (Float) a.getAnimatedValue();
                invalidate();
            }
        });
        anim.start();
    }

    @Override protected void onDetachedFromWindow() {
        if (anim != null) anim.cancel();
        super.onDetachedFromWindow();
    }

    @Override protected void onDraw(Canvas c) {
        if (p.getShader() != null) {
            m.setTranslate(phase * getWidth() * 1.5f, 0);
            p.getShader().setLocalMatrix(m);
        }
        r.set(0, 0, getWidth(), getHeight());
        c.drawRoundRect(r, radius, radius, p);
    }
}
