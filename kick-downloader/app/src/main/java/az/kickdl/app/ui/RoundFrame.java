package az.kickdl.app.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.RectF;
import android.widget.FrameLayout;

/** FrameLayout that clips its children to a rounded rect or circle (works with software rendering too). */
public class RoundFrame extends FrameLayout {
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private final float radius;
    private final boolean oval;

    public RoundFrame(Context c, float radiusDp) {
        super(c);
        radius = Ui.dp(radiusDp);
        oval = false;
        setWillNotDraw(false);
    }

    private RoundFrame(Context c) {
        super(c);
        radius = 0;
        oval = true;
        setWillNotDraw(false);
    }

    public static RoundFrame oval(Context c) { return new RoundFrame(c); }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        path.reset();
        rect.set(0, 0, w, h);
        if (oval) path.addOval(rect, Path.Direction.CW);
        else path.addRoundRect(rect, radius, radius, Path.Direction.CW);
    }

    @Override protected void dispatchDraw(Canvas c) {
        int save = c.save();
        c.clipPath(path);
        super.dispatchDraw(c);
        c.restoreToCount(save);
    }
}
