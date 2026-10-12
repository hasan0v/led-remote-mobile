package az.kickdl.app.ui;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Glass bottom sheet overlay with drag-to-dismiss. Also used as an action menu. */
public class Sheet {

    public interface OnDismiss { void onDismiss(); }

    private static final List<Sheet> stack = new ArrayList<>();

    private final Activity activity;
    private final FrameLayout overlay;
    private final View scrim;
    private final LinearLayout panel;
    private final LinearLayout body;
    private final ScrollView scroll;
    private OnDismiss onDismiss;
    private boolean dismissing;

    public Sheet(Activity a) {
        activity = a;
        Context c = a;
        overlay = new FrameLayout(c);
        overlay.setClickable(true);
        scrim = new View(c);
        scrim.setBackgroundColor(Ui.SCRIM);
        scrim.setAlpha(0f);
        scrim.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { dismiss(); }
        });
        overlay.addView(scrim, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));

        panel = Ui.col(c);
        panel.setBackground(Ui.glassStrong(28));
        panel.setClickable(true);
        panel.setElevation(Ui.dp(24));
        View handle = new View(c);
        handle.setBackground(Ui.solid(0x40FFFFFF, 3));
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(Ui.dp(40), Ui.dp(5));
        hp.gravity = Gravity.CENTER_HORIZONTAL;
        hp.topMargin = Ui.dp(10);
        hp.bottomMargin = Ui.dp(6);
        panel.addView(handle, hp);
        scroll = new ScrollView(c);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setVerticalScrollBarEnabled(false);
        body = Ui.col(c);
        body.setPadding(Ui.dp(20), Ui.dp(4), Ui.dp(20), Ui.dp(20));
        scroll.addView(body, new FrameLayout.LayoutParams(Ui.MATCH, Ui.WRAP));
        panel.addView(scroll, new LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP));
        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(Ui.MATCH, Ui.WRAP, Gravity.BOTTOM);
        int side = Ui.dp(8);
        pp.setMargins(side, Ui.dp(48), side, side);
        overlay.addView(panel, pp);
        enableDrag(handle);
        enableDrag(panel);
    }

    public LinearLayout body() { return body; }

    public Activity activity() { return activity; }

    public Sheet onDismiss(OnDismiss d) { onDismiss = d; return this; }

    public Sheet title(String t, String subtitle) {
        TextView tv = Ui.text(activity, t, 19, Ui.TEXT, Ui.BOLD);
        Ui.lines(tv, 2);
        body.addView(tv, Ui.margins(Ui.lp(Ui.MATCH, Ui.WRAP), 2, 6, 2, 0));
        if (subtitle != null) {
            TextView st = Ui.text(activity, subtitle, 13, Ui.TEXT2, Ui.REGULAR);
            Ui.lines(st, 2);
            body.addView(st, Ui.margins(Ui.lp(Ui.MATCH, Ui.WRAP), 2, 4, 2, 0));
        }
        body.addView(Ui.space(activity, 1, 12));
        return this;
    }

    /** Adds a menu row. */
    public Sheet item(int iconRes, String label, int color, final Runnable action) {
        LinearLayout row = Ui.row(activity);
        row.setPadding(Ui.dp(12), 0, Ui.dp(12), 0);
        row.setMinimumHeight(Ui.dp(52));
        row.setBackground(Ui.ripple(Ui.solid(Color.TRANSPARENT, 14), 14));
        if (iconRes != 0) {
            ImageView iv = Ui.icon(activity, iconRes, 22, color);
            row.addView(iv, Ui.margins(Ui.lp(Ui.dp(22), Ui.dp(22)), 0, 0, 16, 0));
        }
        TextView tv = Ui.text(activity, label, 15, color, Ui.MEDIUM);
        row.addView(tv, Ui.lpw(0, Ui.WRAP, 1));
        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Ui.haptic(v);
                dismiss();
                if (action != null) v.postDelayed(action, 120);
            }
        });
        body.addView(row, Ui.lp(Ui.MATCH, Ui.WRAP));
        return this;
    }

    public Sheet item(int iconRes, String label, Runnable action) { return item(iconRes, label, Ui.TEXT, action); }

    public Sheet view(View v) {
        body.addView(v);
        return this;
    }

    public Sheet show() {
        ViewGroup root = activity.findViewById(android.R.id.content);
        root.addView(overlay, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        stack.add(this);
        ViewGroup.MarginLayoutParams pp = (ViewGroup.MarginLayoutParams) panel.getLayoutParams();
        int inset = navInset(root);
        pp.bottomMargin = Ui.dp(8) + inset;
        panel.setLayoutParams(pp);
        panel.setTranslationY(Ui.dp(600));
        scrim.animate().alpha(1f).setDuration(260).start();
        panel.post(new Runnable() {
            @Override public void run() {
                panel.setTranslationY(panel.getHeight() + Ui.dp(40));
                panel.animate().translationY(0).setDuration(420)
                        .setInterpolator(new PathInterpolator(0.2f, 0.9f, 0.1f, 1f)).start();
            }
        });
        return this;
    }

    private static int navInset(View root) {
        android.view.WindowInsets wi = root.getRootWindowInsets();
        return wi == null ? 0 : wi.getSystemWindowInsetBottom();
    }

    public void dismiss() {
        if (dismissing) return;
        dismissing = true;
        stack.remove(this);
        scrim.animate().alpha(0f).setDuration(220).start();
        panel.animate().translationY(panel.getHeight() + Ui.dp(40)).setDuration(240)
                .setInterpolator(new DecelerateInterpolator()).withEndAction(new Runnable() {
                    @Override public void run() {
                        ViewGroup p = (ViewGroup) overlay.getParent();
                        if (p != null) p.removeView(overlay);
                        if (onDismiss != null) onDismiss.onDismiss();
                    }
                }).start();
    }

    public boolean isShowing() { return !dismissing && overlay.getParent() != null; }

    /** @return true if a sheet consumed the back press. */
    public static boolean handleBack(Activity a) {
        for (int i = stack.size() - 1; i >= 0; i--) {
            Sheet s = stack.get(i);
            if (s.activity == a) {
                s.dismiss();
                return true;
            }
        }
        return false;
    }

    /** Drops references to a destroyed activity without animating. */
    public static void forget(Activity a) {
        for (int i = stack.size() - 1; i >= 0; i--) if (stack.get(i).activity == a) stack.remove(i);
    }

    public static void dismissAll(Activity a) {
        for (Sheet s : new ArrayList<>(stack)) if (s.activity == a) s.dismiss();
    }

    private void enableDrag(View v) {
        v.setOnTouchListener(new View.OnTouchListener() {
            float downY;
            boolean dragging;
            VelocityTracker vt;

            @Override public boolean onTouch(View view, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downY = e.getRawY();
                        dragging = false;
                        vt = VelocityTracker.obtain();
                        vt.addMovement(e);
                        return view != panel;
                    case MotionEvent.ACTION_MOVE:
                        if (vt != null) vt.addMovement(e);
                        float dy = e.getRawY() - downY;
                        if (!dragging && dy > Ui.dp(10) && scroll.getScrollY() == 0) dragging = true;
                        if (dragging) {
                            panel.setTranslationY(Math.max(0, dy));
                            scrim.setAlpha(1f - Math.min(1f, dy / Math.max(1f, panel.getHeight())));
                            return true;
                        }
                        return view != panel;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        float vel = 0;
                        if (vt != null) {
                            vt.computeCurrentVelocity(1000);
                            vel = vt.getYVelocity();
                            vt.recycle();
                            vt = null;
                        }
                        if (dragging) {
                            dragging = false;
                            if (panel.getTranslationY() > panel.getHeight() * 0.3f || vel > Ui.dp(900)) dismiss();
                            else {
                                panel.animate().translationY(0).setDuration(260).start();
                                scrim.animate().alpha(1f).setDuration(200).start();
                            }
                            return true;
                        }
                        return view != panel;
                }
                return false;
            }
        });
    }
}
