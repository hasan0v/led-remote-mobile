package az.kickdl.app.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Glass segmented control with a sliding indicator. */
public class Segmented extends FrameLayout {
    public interface Listener { void onSelected(int index); }

    private final View indicator;
    private final LinearLayout row;
    private final List<TextView> labels = new ArrayList<>();
    private int selected;
    private Listener listener;

    public Segmented(Context c, String... items) {
        super(c);
        setBackground(Ui.glass(22));
        setPadding(Ui.dp(4), Ui.dp(4), Ui.dp(4), Ui.dp(4));
        indicator = new View(c);
        indicator.setBackground(Ui.accentGradient(18));
        addView(indicator, new LayoutParams(0, Ui.dp(36)));
        row = new LinearLayout(c);
        addView(row, new LayoutParams(Ui.MATCH, Ui.dp(36)));
        for (int i = 0; i < items.length; i++) {
            final int idx = i;
            TextView t = Ui.text(c, items[i], 14, Ui.TEXT2, Ui.MEDIUM);
            t.setGravity(Gravity.CENTER);
            t.setSingleLine(true);
            t.setOnClickListener(new OnClickListener() {
                @Override public void onClick(View v) {
                    if (idx == selected) return;
                    Ui.tick(v);
                    select(idx, true);
                    if (listener != null) listener.onSelected(idx);
                }
            });
            labels.add(t);
            row.addView(t, new LinearLayout.LayoutParams(0, Ui.MATCH, 1));
        }
        select(0, false);
    }

    public void setListener(Listener l) { listener = l; }

    public int selected() { return selected; }

    public void setLabel(int i, String s) { labels.get(i).setText(s); }

    public void select(final int i, final boolean animate) {
        selected = i;
        for (int k = 0; k < labels.size(); k++) {
            labels.get(k).setTextColor(k == i ? Ui.ON_ACCENT : Ui.TEXT2);
            labels.get(k).setTypeface(k == i ? Ui.BOLD : Ui.MEDIUM);
        }
        post(new Runnable() {
            @Override public void run() {
                int w = row.getWidth() / Math.max(1, labels.size());
                LayoutParams lp = (LayoutParams) indicator.getLayoutParams();
                if (lp.width != w) {
                    lp.width = w;
                    indicator.setLayoutParams(lp);
                }
                float x = w * i;
                if (animate) indicator.animate().translationX(x).setDuration(320)
                        .setInterpolator(new PathInterpolator(0.3f, 1.3f, 0.4f, 1f)).start();
                else indicator.setTranslationX(x);
            }
        });
    }
}
