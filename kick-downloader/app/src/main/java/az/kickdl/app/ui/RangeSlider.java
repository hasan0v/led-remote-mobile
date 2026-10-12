package az.kickdl.app.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/** Two-thumb slider used to cut a part of a VOD (start/end in seconds). */
public class RangeSlider extends View {
    public interface Listener { void onRange(double start, double end, boolean fromUser); }

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint active = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumb = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbRing = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private double max = 1, start = 0, end = 1, minGap = 5;
    private int dragging = -1;
    private Listener listener;

    public RangeSlider(Context c) {
        super(c);
        track.setColor(0x24FFFFFF);
        active.setColor(Ui.ACCENT);
        thumb.setColor(0xFFFFFFFF);
        thumbRing.setColor(Ui.alpha(Ui.ACCENT, 0.35f));
        setMinimumHeight(Ui.dp(40));
    }

    public void setListener(Listener l) { listener = l; }

    public void setMax(double seconds) {
        max = Math.max(1, seconds);
        minGap = Math.min(5, max / 4);
        start = 0;
        end = max;
        invalidate();
    }

    public void setRange(double s, double e) {
        start = clamp(s, 0, max);
        end = clamp(e, start + minGap, max);
        invalidate();
    }

    public double start() { return start; }

    public double end() { return end; }

    public double max() { return max; }

    private float pad() { return Ui.dp(14); }

    private float xFor(double v) { return (float) (pad() + (getWidth() - 2 * pad()) * (v / max)); }

    private double valueFor(float x) { return clamp((x - pad()) / (getWidth() - 2 * pad()) * max, 0, max); }

    private static double clamp(double v, double a, double b) { return Math.max(a, Math.min(b, v)); }

    @Override protected void onMeasure(int w, int h) {
        super.onMeasure(w, MeasureSpec.makeMeasureSpec(Ui.dp(40), MeasureSpec.EXACTLY));
    }

    @Override protected void onDraw(Canvas c) {
        float cy = getHeight() / 2f, th = Ui.dp(5);
        r.set(pad(), cy - th / 2, getWidth() - pad(), cy + th / 2);
        c.drawRoundRect(r, th, th, track);
        float xs = xFor(start), xe = xFor(end);
        r.set(xs, cy - th / 2, xe, cy + th / 2);
        c.drawRect(r, active);
        drawThumb(c, xs, cy, dragging == 0);
        drawThumb(c, xe, cy, dragging == 1);
    }

    private void drawThumb(Canvas c, float x, float cy, boolean pressed) {
        if (pressed) c.drawCircle(x, cy, Ui.dp(16), thumbRing);
        c.drawCircle(x, cy, Ui.dp(10), thumb);
        c.drawCircle(x, cy, Ui.dp(4), active);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                float ds = Math.abs(x - xFor(start)), de = Math.abs(x - xFor(end));
                dragging = ds < de || (ds == de && x < xFor(start)) ? 0 : 1;
                getParent().requestDisallowInterceptTouchEvent(true);
                move(x);
                Ui.tick(this);
                return true;
            case MotionEvent.ACTION_MOVE:
                move(x);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragging = -1;
                invalidate();
                if (listener != null) listener.onRange(start, end, true);
                return true;
        }
        return false;
    }

    private void move(float x) {
        double v = Math.round(valueFor(x));
        if (dragging == 0) start = clamp(v, 0, end - minGap);
        else end = clamp(v, start + minGap, max);
        invalidate();
        if (listener != null) listener.onRange(start, end, true);
    }
}
