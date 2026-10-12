package az.kickdl.app.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

import az.kickdl.app.R;

/** Download button that morphs into a progress ring, a pause/resume control and a check mark. */
public class RingButton extends FrameLayout {
    public static final int IDLE = 0, PROGRESS = 1, PAUSED = 2, DONE = 3, FAILED = 4, QUEUED = 5, RECORDING = 6;

    private final ImageView icon;
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringBg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private int state = -1;
    private float progress, shown, spin;
    private ValueAnimator anim, spinner;

    public RingButton(Context c) {
        super(c);
        setWillNotDraw(false);
        icon = new ImageView(c);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        addView(icon, new LayoutParams(Ui.dp(20), Ui.dp(20), android.view.Gravity.CENTER));
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeCap(Paint.Cap.ROUND);
        ring.setStrokeWidth(Ui.dp(2.6f));
        ringBg.setStyle(Paint.Style.STROKE);
        ringBg.setStrokeWidth(Ui.dp(2.6f));
        ringBg.setColor(0x24FFFFFF);
        setBackground(Ui.rippleOval(Ui.glass(22)));
        Ui.pressable(this);
        setState(IDLE, 0);
    }

    public void setState(int s, float p) {
        boolean changed = s != state;
        state = s;
        int res;
        int tint = Ui.TEXT;
        switch (s) {
            case PROGRESS: res = R.drawable.ic_pause; ring.setColor(Ui.ACCENT); break;
            case QUEUED: res = R.drawable.ic_clock; ring.setColor(Ui.TEXT2); break;
            case PAUSED: res = R.drawable.ic_play; ring.setColor(Ui.WARN); break;
            case DONE: res = R.drawable.ic_check; tint = Ui.ON_ACCENT; break;
            case FAILED: res = R.drawable.ic_refresh; tint = Ui.LIVE; ring.setColor(Ui.LIVE); break;
            case RECORDING: res = R.drawable.ic_stop; tint = Ui.LIVE; ring.setColor(Ui.LIVE); break;
            default: res = R.drawable.ic_download;
        }
        if (changed) {
            icon.setImageResource(res);
            icon.setImageTintList(ColorStateList.valueOf(tint));
            setBackground(s == DONE ? Ui.rippleOval(Ui.oval(Ui.ACCENT)) : Ui.rippleOval(Ui.glass(22)));
            Ui.bounce(icon);
        }
        boolean spinning = s == QUEUED || s == RECORDING || (s == PROGRESS && p <= 0.001f);
        if (spinning && spinner == null) {
            spinner = ValueAnimator.ofFloat(0, 360);
            spinner.setDuration(1100);
            spinner.setRepeatCount(ValueAnimator.INFINITE);
            spinner.setInterpolator(new LinearInterpolator());
            spinner.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override public void onAnimationUpdate(ValueAnimator a) {
                    spin = (Float) a.getAnimatedValue();
                    invalidate();
                }
            });
            spinner.start();
        } else if (!spinning && spinner != null) {
            spinner.cancel();
            spinner = null;
        }
        setProgress(p);
    }

    private void setProgress(float p) {
        if (Math.abs(p - progress) < 0.001f) { invalidate(); return; }
        progress = p;
        if (anim != null) anim.cancel();
        anim = ValueAnimator.ofFloat(shown, p);
        anim.setDuration(400);
        anim.setInterpolator(new DecelerateInterpolator());
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) {
                shown = (Float) a.getAnimatedValue();
                invalidate();
            }
        });
        anim.start();
    }

    @Override protected void onDetachedFromWindow() {
        if (spinner != null) { spinner.cancel(); spinner = null; }
        super.onDetachedFromWindow();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        int s = state;
        state = -1;
        setState(s, progress);
    }

    @Override protected void dispatchDraw(Canvas c) {
        super.dispatchDraw(c);
        if (state == IDLE || state == DONE) return;
        float inset = Ui.dp(3);
        oval.set(inset, inset, getWidth() - inset, getHeight() - inset);
        c.drawArc(oval, 0, 360, false, ringBg);
        if (state == QUEUED || state == RECORDING || (state == PROGRESS && shown <= 0.001f)) {
            c.drawArc(oval, spin - 90, 70, false, ring);
        } else {
            c.drawArc(oval, -90, Math.max(4, 360 * shown), false, ring);
        }
    }

    public Drawable iconDrawable() { return icon.getDrawable(); }
}
