package az.ledremote.app;

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

/** One round key of the remote: gradient body, soft halo, label or sun icon. */
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
    private final int kind;
    private final String label;
    private final int top;
    private final int bottom;
    private final int textColor;
    private Listener listener;

    private final Paint body = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float cx, cy, r;

    RemoteButton(Context c, int index, int kind, String label, int top, int bottom, int textColor) {
        super(c);
        this.index = index;
        this.kind = kind;
        this.label = label;
        this.top = top;
        this.bottom = bottom;
        this.textColor = textColor;
        rim.setStyle(Paint.Style.STROKE);
        ink.setColor(textColor);
        ink.setTextAlign(Paint.Align.CENTER);
        ink.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        ink.setStyle(Paint.Style.STROKE);
        ink.setStrokeCap(Paint.Cap.ROUND);
        dim.setColor(Color.BLACK);
        setClickable(true);
        setFocusable(true);
    }

    void setListener(Listener l) { listener = l; }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec);
        setMeasuredDimension(w, w);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        cx = w / 2f;
        cy = h / 2f;
        r = Math.min(w, h) / 2f * 0.84f;
        float dp = getResources().getDisplayMetrics().density;

        body.setShader(new LinearGradient(cx - r, cy - r, cx + r, cy + r, top, bottom, Shader.TileMode.CLAMP));
        rim.setStrokeWidth(1.5f * dp);
        rim.setShader(new LinearGradient(cx, cy - r, cx, cy + r,
                new int[]{0xB3FFFFFF, 0x26FFFFFF, 0x59FFFFFF}, null, Shader.TileMode.CLAMP));
        sheen.setShader(new RadialGradient(cx - r * 0.35f, cy - r * 0.45f, r * 1.1f,
                0x33FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        updateHalo(false);
    }

    private void updateHalo(boolean pressed) {
        if (r == 0) return;
        int glow = pressed ? (mix(top, Color.WHITE, 0.25f) & 0xFFFFFF) | 0xD0000000 : 0x55FFFFFF;
        float outer = Math.min(getWidth(), getHeight()) / 2f;
        halo.setShader(new RadialGradient(cx, cy, outer,
                new int[]{0x00000000, glow, 0x00000000},
                new float[]{r / outer * 0.96f, r / outer * 1.02f, 1f}, Shader.TileMode.CLAMP));
    }

    private static int mix(int a, int b, float t) {
        int ar = Color.red(a), ag = Color.green(a), ab = Color.blue(a);
        return Color.rgb((int) (ar + (Color.red(b) - ar) * t),
                (int) (ag + (Color.green(b) - ag) * t),
                (int) (ab + (Color.blue(b) - ab) * t));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float dp = getResources().getDisplayMetrics().density;
        canvas.drawCircle(cx, cy, Math.min(getWidth(), getHeight()) / 2f, halo);
        canvas.drawCircle(cx, cy, r, body);
        canvas.drawCircle(cx, cy, r, sheen);
        canvas.drawCircle(cx, cy, r - rim.getStrokeWidth() / 2f, rim);

        if (kind == KIND_TEXT) {
            boolean word = label.length() > 1 && !label.equals("ON");
            ink.setStyle(Paint.Style.FILL);
            ink.setTextSize(r * (label.length() == 1 ? 0.72f : (word ? (label.length() > 5 ? 0.40f : 0.44f) : 0.68f)));
            ink.setLetterSpacing(word ? 0.04f : 0f);
            ink.setFakeBoldText(word);
            float y = cy - (ink.ascent() + ink.descent()) / 2f;
            canvas.drawText(label, cx, y, ink);
        } else if (kind == KIND_SUN_BIG || kind == KIND_SUN_SMALL) {
            drawSun(canvas, kind == KIND_SUN_BIG ? r * 0.36f : r * 0.22f, dp);
        }

        if (isPressed()) canvas.drawCircle(cx, cy, r, dimPaint());
    }

    private Paint dimPaint() {
        dim.setAlpha(45);
        return dim;
    }

    private void drawSun(Canvas canvas, float core, float dp) {
        ink.setStyle(Paint.Style.STROKE);
        ink.setStrokeWidth(Math.max(1.6f * dp, r * 0.055f));
        canvas.drawCircle(cx, cy, core, ink);
        float from = core * 1.55f, to = core * 2.05f;
        for (int i = 0; i < 8; i++) {
            double a = Math.toRadians(i * 45);
            float cos = (float) Math.cos(a), sin = (float) Math.sin(a);
            canvas.drawLine(cx + cos * from, cy + sin * from, cx + cos * to, cy + sin * to, ink);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                setPressed(true);
                updateHalo(true);
                animate().scaleX(0.92f).scaleY(0.92f).setDuration(70).start();
                invalidate();
                if (listener != null) listener.onDown(this);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                setPressed(false);
                updateHalo(false);
                animate().scaleX(1f).scaleY(1f).setDuration(120).start();
                invalidate();
                if (listener != null) listener.onUp(this);
                return true;
            default:
                return true;
        }
    }
}
