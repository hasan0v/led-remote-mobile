package az.kickdl.app.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/** Wraps children onto multiple lines (chips). */
public class FlowLayout extends ViewGroup {
    private final int hGap, vGap;

    public FlowLayout(Context c, int hGapDp, int vGapDp) {
        super(c);
        hGap = Ui.dp(hGapDp);
        vGap = Ui.dp(vGapDp);
    }

    @Override protected void onMeasure(int wSpec, int hSpec) {
        int maxW = MeasureSpec.getSize(wSpec) - getPaddingLeft() - getPaddingRight();
        int x = 0, y = 0, lineH = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View ch = getChildAt(i);
            if (ch.getVisibility() == GONE) continue;
            ch.measure(MeasureSpec.makeMeasureSpec(maxW, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            int w = ch.getMeasuredWidth(), h = ch.getMeasuredHeight();
            if (x > 0 && x + w > maxW) {
                x = 0;
                y += lineH + vGap;
                lineH = 0;
            }
            x += w + hGap;
            lineH = Math.max(lineH, h);
        }
        setMeasuredDimension(MeasureSpec.getSize(wSpec), y + lineH + getPaddingTop() + getPaddingBottom());
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int maxW = r - l - getPaddingLeft() - getPaddingRight();
        int x = 0, y = 0, lineH = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View ch = getChildAt(i);
            if (ch.getVisibility() == GONE) continue;
            int w = ch.getMeasuredWidth(), h = ch.getMeasuredHeight();
            if (x > 0 && x + w > maxW) {
                x = 0;
                y += lineH + vGap;
                lineH = 0;
            }
            ch.layout(getPaddingLeft() + x, getPaddingTop() + y, getPaddingLeft() + x + w, getPaddingTop() + y + h);
            x += w + hGap;
            lineH = Math.max(lineH, h);
        }
    }
}
