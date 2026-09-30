package az.ledremote.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * One physical-looking key: drop shadow, domed gradient body, rim light and label.
 * Pressing sinks the key into the case (shadow collapses, body darkens, inner shadow appears)
 * and lights a colored glow; releasing springs it back.
 */
final class RemoteButton extends View {
    static final int KIND_COLOR = 0;
    static final int KIND_TEXT = 1;
    static final int KIND_SUN_BIG = 2;
    static final int KIND_SUN_SMALL = 3;

    interface Listener {
        void onDown(RemoteButton b);
        void onUp(RemoteButton b);
    }

    final int index;
    final String label;
    private final int kind;
    private final int top;
    private final int bottom;
    private Listener listener;

    private final Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint body = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint inner = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dim = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float cx, cy, r, depth;
    private float press;              // 0 = up, 1 = fully pressed
    private ValueAnimator anim;
    private boolean down;

    RemoteButton(Context c, int index, int kind, String label, int top, int bottom, int textColor) {
        super(c);
        this.index = index;
        this.kind = kind;
        this.label = label;
        this.top = top;
        this.bottom = bottom;
        rim.setStyle(Paint.Style.STROKE);
        ink.setColor(textColor);
        ink.setTextAlign(Paint.Align.CENTER);
        ink.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        ink.setStrokeCap(Paint.Cap.ROUND);
        dim.setColor(Color.BLACK);
        setClickable(true);
        setFocusable(true);
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    void setListener(Listener l) { listener = l; }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec);
        setMeasuredDimension(w, w);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        float dp = getResources().getDisplayMetrics().density;
        depth = 3.5f * dp;
        cx = w / 2f;
        r = Math.min(w, h) / 2f * 0.80f;
        cy = h / 2f - depth / 2f;

        body.setShader(new LinearGradient(cx - r, cy - r, cx + r, cy + r, top, bottom, Shader.TileMode.CLAMP));
        rim.setStrokeWidth(1.2f * dp);
        rim.setShader(new LinearGradient(cx, cy - r, cx, cy + r,
                new int[]{0xCCFFFFFF, 0x1AFFFFFF, 0x40FFFFFF}, null, Shader.TileMode.CLAMP));
        sheen.setShader(new RadialGradient(cx - r * 0.3f, cy - r * 0.55f, r * 1.05f,
                new int[]{0x40FFFFFF, 0x0DFFFFFF, 0x00FFFFFF}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        inner.setShader(new LinearGradient(cx, cy - r, cx, cy - r * 0.2f,
                0x66000000, 0x00000000, Shader.TileMode.CLAMP));

        int g = (luma(top) > 0.85f) ? 0xFFFFFFFF : mix(top, Color.WHITE, 0.2f);
        float outer = Math.min(w, h) / 2f;
        glow.setShader(new RadialGradient(0, 0, outer,
                new int[]{(g & 0xFFFFFF) | 0xB0000000, (g & 0xFFFFFF) | 0x40000000, 0x00000000},
                new float[]{r / outer * 0.9f, r / outer * 1.08f, 1f}, Shader.TileMode.CLAMP));
    }

    private static float luma(int c) {
        return (0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)) / 255f;
    }

    private static int mix(int a, int b, float t) {
        return Color.rgb((int) (Color.red(a) + (Color.red(b) - Color.red(a)) * t),
                (int) (Color.green(a) + (Color.green(b) - Color.green(a)) * t),
                (int) (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float dp = getResources().getDisplayMetrics().density;
        float p = Math.max(0f, Math.min(1f, press));
        float y = cy + depth * p;

        // drop shadow: long and soft when up, tight when pressed into the case
        float sOff = depth * (1f - p) + dp;
        float sR = r * (1.06f - 0.05f * p);
        shadow.setShader(new RadialGradient(cx, y + sOff, sR,
                new int[]{0x99000000, 0x55000000, 0x00000000}, new float[]{0.75f, 0.9f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, y + sOff, sR, shadow);

        if (p > 0f) {
            glow.setAlpha((int) (200 * p));
            canvas.save();
            canvas.translate(cx, y);
            canvas.drawCircle(0, 0, Math.min(getWidth(), getHeight()) / 2f, glow);
            canvas.restore();
        }

        canvas.save();
        canvas.translate(0, depth * p);
        float scale = 1f - 0.03f * p;
        canvas.scale(scale, scale, cx, cy);

        canvas.drawCircle(cx, cy, r, body);
        sheen.setAlpha((int) (255 * (1f - 0.6f * p)));
        canvas.drawCircle(cx, cy, r, sheen);
        if (p > 0f) {
            dim.setAlpha((int) (24 * p));
            canvas.drawCircle(cx, cy, r, dim);
            inner.setAlpha((int) (190 * p));
            canvas.drawCircle(cx, cy, r, inner);
        }
        rim.setAlpha((int) (255 * (1f - 0.5f * p)));
        canvas.drawCircle(cx, cy, r - rim.getStrokeWidth() / 2f, rim);

        if (kind == KIND_TEXT) {
            boolean word = label.length() > 2;
            ink.setStyle(Paint.Style.FILL);
            ink.setTextSize(r * (label.length() == 1 ? 0.72f : word ? (label.length() > 5 ? 0.38f : 0.42f) : 0.62f));
            ink.setLetterSpacing(word ? 0.05f : 0f);
            ink.setFakeBoldText(word);
            canvas.drawText(label, cx, cy - (ink.ascent() + ink.descent()) / 2f, ink);
        } else if (kind == KIND_SUN_BIG || kind == KIND_SUN_SMALL) {
            drawSun(canvas, kind == KIND_SUN_BIG ? r * 0.26f : r * 0.17f, kind == KIND_SUN_BIG, dp);
        }
        canvas.restore();
    }

    private void drawSun(Canvas canvas, float core, boolean big, float dp) {
        ink.setStyle(Paint.Style.STROKE);
        ink.setStrokeWidth(Math.max(1.6f * dp, r * (big ? 0.06f : 0.05f)));
        canvas.drawCircle(cx, cy, core, ink);
        float from = core * (big ? 1.6f : 1.75f), to = core * (big ? 2.25f : 2.35f);
        for (int i = 0; i < 8; i++) {
            double a = Math.toRadians(i * 45);
            float cos = (float) Math.cos(a), sin = (float) Math.sin(a);
            canvas.drawLine(cx + cos * from, cy + sin * from, cx + cos * to, cy + sin * to, ink);
        }
    }

    private void animateTo(float target, long ms) {
        if (anim != null) anim.cancel();
        anim = ValueAnimator.ofFloat(press, target);
        anim.setDuration(ms);
        anim.setInterpolator(new DecelerateInterpolator(target > press ? 1.5f : 2f));
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) {
                press = (Float) a.getAnimatedValue();
                invalidate();
            }
        });
        anim.start();
    }

    private void pressDown() {
        if (down) return;
        down = true;
        setPressed(true);
        animateTo(1f, 45);
        if (listener != null) listener.onDown(this);
    }

    private void release() {
        if (!down) return;
        down = false;
        setPressed(false);
        animateTo(0f, 220);
        if (listener != null) listener.onUp(this);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int action = e.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            getParent().requestDisallowInterceptTouchEvent(true);
            pressDown();
        } else if (action == MotionEvent.ACTION_MOVE) {
            // sliding far off the key releases it, like lifting a finger off a real button
            float slop = r * 0.35f;
            if (e.getX() < -slop || e.getY() < -slop || e.getX() > getWidth() + slop || e.getY() > getHeight() + slop) {
                release();
            }
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            release();
        }
        return true;
    }

    /** Accessibility (TalkBack double-tap) path: a full press and release. */
    @Override
    public boolean performClick() {
        super.performClick();
        pressDown();
        postDelayed(new Runnable() {
            @Override public void run() { release(); }
        }, 120);
        return true;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (anim != null) anim.cancel();
        down = false;
    }
}
