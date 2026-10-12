package az.kickdl.app.ui;

import android.app.Activity;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Glass snackbar with optional action. */
public final class Toaster {
    private Toaster() {}

    private static View current;

    public static void show(Activity a, int iconRes, String msg) { show(a, iconRes, msg, null, null); }

    public static void show(final Activity a, int iconRes, String msg, String action, final Runnable onAction) {
        if (a == null || a.isFinishing()) return;
        final ViewGroup root = a.findViewById(android.R.id.content);
        if (current != null && current.getParent() != null) ((ViewGroup) current.getParent()).removeView(current);
        final LinearLayout bar = Ui.row(a);
        bar.setBackground(Ui.glassStrong(18));
        bar.setPadding(Ui.dp(16), Ui.dp(12), Ui.dp(10), Ui.dp(12));
        bar.setElevation(Ui.dp(16));
        if (iconRes != 0) {
            ImageView iv = Ui.icon(a, iconRes, 20, Ui.ACCENT);
            bar.addView(iv, Ui.margins(Ui.lp(Ui.dp(20), Ui.dp(20)), 0, 0, 12, 0));
        }
        TextView tv = Ui.text(a, msg, 14, Ui.TEXT, Ui.MEDIUM);
        Ui.lines(tv, 3);
        bar.addView(tv, Ui.lpw(0, Ui.WRAP, 1));
        if (action != null) {
            TextView act = Ui.text(a, action, 14, Ui.ACCENT, Ui.BOLD);
            act.setPadding(Ui.dp(12), Ui.dp(6), Ui.dp(8), Ui.dp(6));
            act.setBackground(Ui.ripple(Ui.solid(0, 10), 10));
            act.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    hide(bar);
                    if (onAction != null) onAction.run();
                }
            });
            bar.addView(act);
        } else {
            bar.setPadding(Ui.dp(16), Ui.dp(14), Ui.dp(16), Ui.dp(14));
        }
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Ui.MATCH, Ui.WRAP, Gravity.BOTTOM);
        int inset = root.getRootWindowInsets() != null ? root.getRootWindowInsets().getSystemWindowInsetBottom() : 0;
        lp.setMargins(Ui.dp(14), 0, Ui.dp(14), Ui.dp(96) + inset);
        root.addView(bar, lp);
        current = bar;
        bar.setTranslationY(Ui.dp(40));
        bar.setAlpha(0f);
        bar.animate().translationY(0).alpha(1f).setDuration(360).setInterpolator(new OvershootInterpolator(1.2f)).start();
        bar.postDelayed(new Runnable() {
            @Override public void run() { hide(bar); }
        }, action != null ? 4500 : 2600);
    }

    private static void hide(final View bar) {
        bar.animate().translationY(Ui.dp(30)).alpha(0f).setDuration(220).withEndAction(new Runnable() {
            @Override public void run() {
                ViewGroup p = (ViewGroup) bar.getParent();
                if (p != null) p.removeView(bar);
                if (current == bar) current = null;
            }
        }).start();
    }
}
