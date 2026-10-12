package az.kickdl.app.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;

/** Player timeline: buffered range, gradient progress and a thumb that grows while dragging. */
public class SeekBarView extends View {
    public interface Listener {
        void onSeekStart();

        void onSeekMove(float fraction);

        void onSeekEnd(float fraction);
    }

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint buffer = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint prog = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumb = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private float progress, buffered;
    private boolean dragging;
    private float thumbScale = 1f;
    private Listener listener;
    private ValueAnimator scaleAnim;

    public SeekBarView(Context c) {
        super(c);
        track.setColor(0x33FFFFFF);
        buffer.setColor(0x55FFFFFF);
        thumb.setColor(0xFFFFFFFF);
        thumb.setShadowLayer(Ui.dp(3), 0, 0, 0x6653FC18);
        halo.setColor(0x3353FC18);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    public void setListener(Listener l) { listener = l; }

    public void setProgress(float p) {
        if (dragging) return;
        progress = clamp(p);
        invalidate();
    }

    public void setBuffered(float b) {
        buffered = clamp(b);
        invalidate();
    }

    public boolean isDragging() { return dragging; }

    private static float clamp(float v) { return Math.max(0, Math.min(1, v)); }

    private float pad() { return Ui.dp(12); }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        prog.setShader(new LinearGradient(pad(), 0, w - pad(), 0, 0xFF62FF2B, 0xFF2BD9F0, Shader.TileMode.CLAMP));
    }

    @Override protected void onDraw(Canvas c) {
        float cy = getHeight() / 2f;
        float th = Ui.dp(dragging ? 6 : 4);
        float x0 = pad(), x1 = getWidth() - pad(), w = x1 - x0;
        r.set(x0, cy - th / 2, x1, cy + th / 2);
        c.drawRoundRect(r, th, th, track);
        r.set(x0, cy - th / 2, x0 + w * Math.max(buffered, progress), cy + th / 2);
        c.drawRoundRect(r, th, th, buffer);
        float px = x0 + w * progress;
        r.set(x0, cy - th / 2, px, cy + th / 2);
        c.drawRoundRect(r, th, th, prog);
        if (dragging) c.drawCircle(px, cy, Ui.dp(16) * thumbScale, halo);
        c.drawCircle(px, cy, Ui.dp(7) * thumbScale, thumb);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        float x0 = pad(), w = getWidth() - 2 * pad();
        float f = clamp((e.getX() - x0) / w);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragging = true;
                getParent().requestDisallowInterceptTouchEvent(true);
                animateThumb(1.5f);
                progress = f;
                if (listener != null) { listener.onSeekStart(); listener.onSeekMove(f); }
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                progress = f;
                if (listener != null) listener.onSeekMove(f);
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                animateThumb(1f);
                progress = f;
                if (listener != null) listener.onSeekEnd(f);
                invalidate();
                return true;
        }
        return false;
    }

    private void animateThumb(float to) {
        if (scaleAnim != null) scaleAnim.cancel();
        scaleAnim = ValueAnimator.ofFloat(thumbScale, to);
        scaleAnim.setDuration(160);
        scaleAnim.addUpdateListener(a -> {
            thumbScale = (Float) a.getAnimatedValue();
            invalidate();
        });
        scaleAnim.start();
    }
}
