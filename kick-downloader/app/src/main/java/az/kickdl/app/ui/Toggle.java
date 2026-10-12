package az.kickdl.app.ui;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.OvershootInterpolator;

/** Animated on/off switch. */
public class Toggle extends View {
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knob = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final ArgbEvaluator argb = new ArgbEvaluator();
    private boolean checked;
    private float pos;
    private ValueAnimator anim;

    public Toggle(Context c) {
        super(c);
        knob.setColor(0xFFFFFFFF);
        knob.setShadowLayer(Ui.dp(2), 0, Ui.dp(1), 0x55000000);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    public boolean isChecked() { return checked; }

    public void setChecked(boolean c, boolean animate) {
        checked = c;
        if (anim != null) anim.cancel();
        float target = c ? 1f : 0f;
        if (!animate) {
            pos = target;
            invalidate();
            return;
        }
        anim = ValueAnimator.ofFloat(pos, target);
        anim.setDuration(300);
        anim.setInterpolator(new OvershootInterpolator(1.4f));
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) {
                pos = (Float) a.getAnimatedValue();
                invalidate();
            }
        });
        anim.start();
    }

    @Override protected void onMeasure(int w, int h) {
        setMeasuredDimension(Ui.dp(50), Ui.dp(30));
    }

    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        float p = Math.max(0, Math.min(1, pos));
        track.setColor((Integer) argb.evaluate(p, 0x2EFFFFFF, Ui.ACCENT));
        r.set(Ui.dp(2), Ui.dp(3), w - Ui.dp(2), h - Ui.dp(3));
        c.drawRoundRect(r, r.height() / 2, r.height() / 2, track);
        float rad = (h - Ui.dp(10)) / 2f;
        float x0 = Ui.dp(5) + rad, x1 = w - Ui.dp(5) - rad;
        c.drawCircle(x0 + (x1 - x0) * pos, h / 2f, rad, knob);
    }
}
