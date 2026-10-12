package az.kickdl.app.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import az.kickdl.app.data.Prefs;

/** Design system: colors, glass surfaces, typography and small view factories. */
public final class Ui {
    private Ui() {}

    public static final int BG = 0xFF07080C;
    public static final int ACCENT = 0xFF53FC18;
    public static final int ACCENT_DARK = 0xFF2E9E06;
    public static final int ON_ACCENT = 0xFF061003;
    public static final int TEXT = 0xFFF4F6FA;
    public static final int TEXT2 = 0xFFA3A9B8;
    public static final int TEXT3 = 0xFF6B7180;
    public static final int LIVE = 0xFFFF3B5C;
    public static final int WARN = 0xFFFFB020;
    public static final int VIOLET = 0xFF8B6CFF;
    public static final int CYAN = 0xFF2BD9F0;
    public static final int GLASS_TOP = 0x1FFFFFFF;
    public static final int GLASS_BOTTOM = 0x0AFFFFFF;
    public static final int STROKE = 0x1FFFFFFF;
    public static final int STROKE_HI = 0x38FFFFFF;
    public static final int SCRIM = 0xB3050608;

    public static float density = 3f;
    public static Typeface REGULAR = Typeface.DEFAULT;
    public static Typeface MEDIUM = Typeface.DEFAULT;
    public static Typeface BOLD = Typeface.DEFAULT_BOLD;

    public static void init(Context c) {
        density = c.getResources().getDisplayMetrics().density;
        REGULAR = Typeface.create("sans-serif", Typeface.NORMAL);
        MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);
        if (Build.VERSION.SDK_INT >= 28) {
            BOLD = Typeface.create(Typeface.create("sans-serif", Typeface.NORMAL), 700, false);
        } else {
            BOLD = Typeface.create("sans-serif", Typeface.BOLD);
        }
    }

    public static int dp(float v) { return Math.round(v * density); }

    public static int alpha(int color, float a) {
        return (Math.round(Color.alpha(color) * a) << 24) | (color & 0x00FFFFFF);
    }

    // ---------------------------------------------------------------- surfaces

    /** Frosted glass panel: soft vertical gradient + hairline border. */
    public static GradientDrawable glass(float radiusDp) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{GLASS_TOP, GLASS_BOTTOM});
        g.setCornerRadius(dp(radiusDp));
        g.setStroke(Math.max(1, dp(0.8f)), STROKE);
        return g;
    }

    /** Denser glass for floating elements (nav bar, sheets, menus). */
    public static GradientDrawable glassStrong(float radiusDp) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xE61A1D26, 0xF20E1016});
        g.setCornerRadius(dp(radiusDp));
        g.setStroke(Math.max(1, dp(0.8f)), STROKE_HI);
        return g;
    }

    public static GradientDrawable solid(int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    public static GradientDrawable stroke(int fill, int stroke, float radiusDp) {
        GradientDrawable g = solid(fill, radiusDp);
        g.setStroke(Math.max(1, dp(1)), stroke);
        return g;
    }

    public static GradientDrawable accentGradient(float radiusDp) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xFF62FF2B, 0xFF3FE00C});
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    public static GradientDrawable oval(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        return g;
    }

    /** Wraps a drawable with a ripple that respects its shape. */
    public static Drawable ripple(Drawable content, float radiusDp) {
        GradientDrawable mask = solid(Color.WHITE, radiusDp);
        return new RippleDrawable(ColorStateList.valueOf(0x30FFFFFF), content, mask);
    }

    public static Drawable rippleOval(Drawable content) {
        return new RippleDrawable(ColorStateList.valueOf(0x30FFFFFF), content, oval(Color.WHITE));
    }

    public static void roundClip(View v, final float radiusDp) {
        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(radiusDp));
            }
        });
        v.setClipToOutline(true);
    }

    public static void ovalClip(View v) {
        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        });
        v.setClipToOutline(true);
    }

    // ---------------------------------------------------------------- text

    public static TextView text(Context c, float sp, int color, Typeface tf) {
        TextView t = new TextView(c);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(tf);
        t.setIncludeFontPadding(false);
        t.setLineSpacing(0, 1.12f);
        return t;
    }

    public static TextView text(Context c, String s, float sp, int color, Typeface tf) {
        TextView t = text(c, sp, color, tf);
        t.setText(s);
        return t;
    }

    public static TextView title(Context c, String s) {
        TextView t = text(c, s, 26, TEXT, BOLD);
        t.setLetterSpacing(-0.02f);
        return t;
    }

    public static TextView single(TextView t) {
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    public static TextView lines(TextView t, int n) {
        t.setMaxLines(n);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    /** Small rounded label ("1080p60", "CANLI", duration badges). */
    public static TextView chip(Context c, String s, int bg, int fg) {
        TextView t = text(c, s, 11, fg, MEDIUM);
        t.setBackground(solid(bg, 8));
        t.setPadding(dp(7), dp(3), dp(7), dp(3));
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        return t;
    }

    // ---------------------------------------------------------------- icons & buttons

    public static ImageView icon(Context c, int res, int sizeDp, int tint) {
        ImageView iv = new ImageView(c);
        iv.setImageResource(res);
        iv.setImageTintList(ColorStateList.valueOf(tint));
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iv.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        return iv;
    }

    /** Round glass icon button with ripple and press scale. */
    public static ImageView iconButton(Context c, int res, int sizeDp, int iconDp) {
        ImageView iv = new ImageView(c);
        iv.setImageResource(res);
        iv.setImageTintList(ColorStateList.valueOf(TEXT));
        iv.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        int pad = dp((sizeDp - iconDp) / 2f);
        iv.setPadding(pad, pad, pad, pad);
        GradientDrawable bg = glass(sizeDp / 2f);
        iv.setBackground(rippleOval(bg));
        iv.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        pressable(iv);
        return iv;
    }

    /** Icon button without background (toolbar style). */
    public static ImageView flatIcon(Context c, int res, int sizeDp, int iconDp, int tint) {
        ImageView iv = new ImageView(c);
        iv.setImageResource(res);
        iv.setImageTintList(ColorStateList.valueOf(tint));
        iv.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        int pad = dp((sizeDp - iconDp) / 2f);
        iv.setPadding(pad, pad, pad, pad);
        iv.setBackground(new RippleDrawable(ColorStateList.valueOf(0x30FFFFFF), null, oval(Color.WHITE)));
        iv.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        pressable(iv);
        return iv;
    }

    /** Pill button. primary = Kick green filled, otherwise glass. */
    public static LinearLayout pill(Context c, int iconRes, String label, boolean primary) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER);
        l.setPadding(dp(16), 0, dp(18), 0);
        l.setMinimumHeight(dp(44));
        int fg = primary ? ON_ACCENT : TEXT;
        l.setBackground(ripple(primary ? accentGradient(22) : glass(22), 22));
        if (iconRes != 0) {
            ImageView iv = icon(c, iconRes, 18, fg);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(18), dp(18));
            p.rightMargin = label == null ? 0 : dp(8);
            l.addView(iv, p);
        }
        if (label != null) {
            TextView t = text(c, label, 14, fg, MEDIUM);
            t.setSingleLine(true);
            l.addView(t);
        }
        pressable(l);
        return l;
    }

    public static void setPillLabel(LinearLayout pill, String label) {
        for (int i = 0; i < pill.getChildCount(); i++) {
            if (pill.getChildAt(i) instanceof TextView) ((TextView) pill.getChildAt(i)).setText(label);
        }
    }

    public static void setPillIcon(LinearLayout pill, int res) {
        for (int i = 0; i < pill.getChildCount(); i++) {
            if (pill.getChildAt(i) instanceof ImageView) ((ImageView) pill.getChildAt(i)).setImageResource(res);
        }
    }

    /** Soft scale-down on touch, spring back on release. */
    public static void pressable(final View v) {
        v.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View view, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        view.animate().scaleX(0.94f).scaleY(0.94f).setDuration(110)
                                .setInterpolator(new DecelerateInterpolator()).start();
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        view.animate().scaleX(1f).scaleY(1f).setDuration(260)
                                .setInterpolator(new OvershootInterpolator(2.2f)).start();
                        break;
                }
                return false;
            }
        });
    }

    public static void haptic(View v) {
        if (Prefs.haptics()) v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
    }

    public static void tick(View v) {
        if (Prefs.haptics()) v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
    }

    // ---------------------------------------------------------------- layout params

    public static LinearLayout.LayoutParams lp(int w, int h) { return new LinearLayout.LayoutParams(w, h); }

    public static LinearLayout.LayoutParams lpw(int w, int h, float weight) {
        return new LinearLayout.LayoutParams(w, h, weight);
    }

    public static LinearLayout.LayoutParams margins(LinearLayout.LayoutParams p, int l, int t, int r, int b) {
        p.setMargins(dp(l), dp(t), dp(r), dp(b));
        return p;
    }

    public static FrameLayout.LayoutParams flp(int w, int h, int gravity) {
        return new FrameLayout.LayoutParams(w, h, gravity);
    }

    public static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    public static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    public static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    public static LinearLayout col(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public static View space(Context c, int wDp, int hDp) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(wDp), dp(hDp)));
        return v;
    }

    public static View flex(Context c) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
        return v;
    }

    // ---------------------------------------------------------------- motion

    public static void fadeIn(View v, long delay) {
        v.setAlpha(0f);
        v.setTranslationY(dp(14));
        v.animate().alpha(1f).translationY(0).setStartDelay(delay).setDuration(380)
                .setInterpolator(new DecelerateInterpolator(1.8f)).start();
    }

    public static void popIn(View v) {
        v.setScaleX(0.6f);
        v.setScaleY(0.6f);
        v.setAlpha(0f);
        v.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(320)
                .setInterpolator(new OvershootInterpolator(1.6f)).start();
    }

    public static void bounce(View v) {
        v.animate().cancel();
        v.setScaleX(0.7f);
        v.setScaleY(0.7f);
        v.animate().scaleX(1f).scaleY(1f).setDuration(420)
                .setInterpolator(new OvershootInterpolator(3f)).start();
    }

    /** Breathing alpha animation that lives exactly as long as the view is attached. */
    public static ValueAnimator pulse(final View v, float min, long period) {
        final ValueAnimator a = ValueAnimator.ofFloat(1f, min);
        a.setDuration(period);
        a.setRepeatMode(ValueAnimator.REVERSE);
        a.setRepeatCount(ValueAnimator.INFINITE);
        a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator va) { v.setAlpha((Float) va.getAnimatedValue()); }
        });
        v.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View x) { if (!a.isStarted()) a.start(); }

            @Override public void onViewDetachedFromWindow(View x) { a.cancel(); }
        });
        if (v.isAttachedToWindow()) a.start();
        return a;
    }
}
