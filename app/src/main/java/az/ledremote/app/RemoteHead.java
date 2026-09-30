package az.ledremote.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/** Top of the remote: the smoked IR emitter window with a small LED that blinks while sending. */
final class RemoteHead extends View {
    private final Paint window = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glass = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint led = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float dp;
    private boolean lit;
    private final Runnable off = new Runnable() {
        @Override public void run() { lit = false; invalidate(); }
    };

    RemoteHead(Context c) {
        super(c);
        dp = c.getResources().getDisplayMetrics().density;
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(dp);
        edge.setColor(0xFF34383F);
    }

    @Override
    protected void onMeasure(int w, int h) {
        setMeasuredDimension(MeasureSpec.getSize(w), Math.round(30 * dp));
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        float ww = 58 * dp, wh = 13 * dp;
        rect.set(w / 2f - ww / 2, h / 2f - wh / 2, w / 2f + ww / 2, h / 2f + wh / 2);
        window.setShader(new LinearGradient(0, rect.top, 0, rect.bottom, 0xFF050506, 0xFF1A0E10, Shader.TileMode.CLAMP));
        glass.setShader(new LinearGradient(0, rect.top, 0, rect.centerY(), 0x30FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
    }

    /** Blink the LED for one transmission. */
    void flash() {
        lit = true;
        invalidate();
        removeCallbacks(off);
        postDelayed(off, 110);
    }

    @Override
    protected void onDraw(Canvas c) {
        float rr = rect.height() / 2;
        c.drawRoundRect(rect, rr, rr, window);
        c.drawRoundRect(rect, rr, rr, glass);
        c.drawRoundRect(rect, rr, rr, edge);

        float lx = rect.centerX(), ly = rect.centerY(), lr = 2.6f * dp;
        if (lit) {
            halo.setShader(new RadialGradient(lx, ly, lr * 5, 0xAAFF3B30, 0x00FF3B30, Shader.TileMode.CLAMP));
            c.drawCircle(lx, ly, lr * 5, halo);
            led.setShader(new RadialGradient(lx - lr * 0.3f, ly - lr * 0.3f, lr * 1.3f, 0xFFFFD0C8, 0xFFFF3B30, Shader.TileMode.CLAMP));
        } else {
            led.setShader(new RadialGradient(lx - lr * 0.3f, ly - lr * 0.3f, lr * 1.3f, 0xFF5A2A2A, 0xFF2A1214, Shader.TileMode.CLAMP));
        }
        c.drawCircle(lx, ly, lr, led);
    }
}
