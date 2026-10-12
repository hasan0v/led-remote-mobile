package az.kickdl.app.screens;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import az.kickdl.app.R;
import az.kickdl.app.data.Media;
import az.kickdl.app.ui.Fmt;
import az.kickdl.app.ui.ImageLoader;
import az.kickdl.app.ui.Shimmer;
import az.kickdl.app.ui.Ui;

/** Shared view builders used across screens. */
final class Views {
    private Views() {}

    static TextView section(Context c, String s) {
        TextView t = Ui.text(c, s.toUpperCase(), 12, Ui.TEXT3, Ui.BOLD);
        t.setLetterSpacing(0.12f);
        return t;
    }

    /** Circular avatar with optional live ring. */
    static FrameLayout avatar(Context c, String url, int sizeDp, boolean live, String fallbackName) {
        FrameLayout f = new FrameLayout(c);
        int ring = Ui.dp(sizeDp > 60 ? 3 : 2);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        if (live) bg.setColors(new int[]{0xFFFF3B5C, 0xFFFF8A3D});
        else bg.setColor(0x26FFFFFF);
        f.setBackground(bg);
        f.setPadding(ring, ring, ring, ring);
        FrameLayout inner = az.kickdl.app.ui.RoundFrame.oval(c);
        inner.setBackground(Ui.oval(0xFF1A1D26));
        TextView letter = Ui.text(c, initial(fallbackName), sizeDp * 0.38f, Ui.TEXT2, Ui.BOLD);
        letter.setGravity(Gravity.CENTER);
        inner.addView(letter, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        ImageView iv = new ImageView(c);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        inner.addView(iv, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        ImageLoader.load(iv, url, Ui.dp(sizeDp));
        f.addView(inner, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        f.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(sizeDp), Ui.dp(sizeDp)));
        return f;
    }

    static String initial(String s) {
        if (s == null || s.isEmpty()) return "?";
        return s.substring(0, 1).toUpperCase();
    }

    static TextView liveBadge(Context c) {
        TextView t = Ui.chip(c, c.getString(R.string.live_badge), Ui.LIVE, 0xFFFFFFFF);
        t.setTypeface(Ui.BOLD);
        t.setLetterSpacing(0.08f);
        return t;
    }

    static String meta(Context c, Media m) {
        StringBuilder b = new StringBuilder();
        if (m.createdAt > 0) b.append(Fmt.ago(c, m.createdAt));
        if (m.views >= 0) {
            if (b.length() > 0) b.append(" · ");
            b.append(c.getString(R.string.views_fmt, Fmt.count(m.views)));
        }
        if (m.category != null) {
            if (b.length() > 0) b.append(" · ");
            b.append(m.category);
        }
        return b.toString();
    }

    static View shimmerCard(Context c) {
        LinearLayout col = Ui.col(c);
        Shimmer thumb = new Shimmer(c, 18);
        col.addView(thumb, new LinearLayout.LayoutParams(Ui.MATCH, Ui.dp(190)));
        Shimmer l1 = new Shimmer(c, 6);
        col.addView(l1, Ui.margins(new LinearLayout.LayoutParams(Ui.MATCH, Ui.dp(14)), 4, 12, 60, 0));
        Shimmer l2 = new Shimmer(c, 6);
        col.addView(l2, Ui.margins(new LinearLayout.LayoutParams(Ui.dp(160), Ui.dp(12)), 4, 8, 0, 18));
        return col;
    }

    static View shimmerRow(Context c, int heightDp) {
        Shimmer s = new Shimmer(c, 14);
        s.setLayoutParams(Ui.margins(new LinearLayout.LayoutParams(Ui.MATCH, Ui.dp(heightDp)), 0, 0, 0, 8));
        return s;
    }

    /** Big icon + message + optional action. */
    static LinearLayout empty(Context c, int iconRes, String title, String msg, String action, View.OnClickListener onAction) {
        LinearLayout l = Ui.col(c);
        l.setGravity(Gravity.CENTER_HORIZONTAL);
        l.setPadding(Ui.dp(32), Ui.dp(40), Ui.dp(32), Ui.dp(40));
        FrameLayout circle = new FrameLayout(c);
        circle.setBackground(Ui.glass(44));
        ImageView iv = Ui.icon(c, iconRes, 38, Ui.ACCENT);
        circle.addView(iv, new FrameLayout.LayoutParams(Ui.dp(38), Ui.dp(38), Gravity.CENTER));
        l.addView(circle, new LinearLayout.LayoutParams(Ui.dp(88), Ui.dp(88)));
        TextView t = Ui.text(c, title, 18, Ui.TEXT, Ui.BOLD);
        t.setGravity(Gravity.CENTER);
        l.addView(t, Ui.margins(Ui.lp(Ui.WRAP, Ui.WRAP), 0, 18, 0, 0));
        if (msg != null) {
            TextView m = Ui.text(c, msg, 14, Ui.TEXT2, Ui.REGULAR);
            m.setGravity(Gravity.CENTER);
            l.addView(m, Ui.margins(Ui.lp(Ui.WRAP, Ui.WRAP), 0, 8, 0, 0));
        }
        if (action != null) {
            LinearLayout p = Ui.pill(c, 0, action, true);
            p.setOnClickListener(onAction);
            l.addView(p, Ui.margins(Ui.lp(Ui.WRAP, Ui.dp(44)), 0, 20, 0, 0));
        }
        Ui.fadeIn(l, 0);
        return l;
    }
}
