package az.kickdl.app.screens;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import az.kickdl.app.BuildInfo;
import az.kickdl.app.MainActivity;
import az.kickdl.app.R;
import az.kickdl.app.VerifyActivity;
import az.kickdl.app.data.Prefs;
import az.kickdl.app.dl.DownloadManager;
import az.kickdl.app.dl.Storage;
import az.kickdl.app.ui.Fmt;
import az.kickdl.app.ui.ImageLoader;
import az.kickdl.app.ui.Sheet;
import az.kickdl.app.ui.Toaster;
import az.kickdl.app.ui.Ui;

/** All user preferences, grouped in glass cards. */
public class SettingsScreen extends Screen {
    private ScrollView scroll;
    private LinearLayout content;

    public SettingsScreen(MainActivity a) { super(a); }

    interface Value { String get(); }

    interface Choose { void set(int index); }

    @Override protected View build() {
        scroll = new ScrollView(a);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        content = Ui.col(a);
        content.setPadding(Ui.dp(18), 0, Ui.dp(18), 0);
        scroll.addView(content, new FrameLayout.LayoutParams(Ui.MATCH, Ui.WRAP));
        fill();
        return scroll;
    }

    private void fill() {
        content.removeAllViews();
        final Context c = a;
        content.addView(Ui.title(c, a.getString(R.string.tab_settings)), Ui.margins(Ui.lp(Ui.MATCH, Ui.WRAP), 0, 8, 0, 18));

        // ---------------------------------------------------------- downloads
        LinearLayout dl = group(a.getString(R.string.s_downloads));
        final String[] qKeys = {"ask", "best", "1080", "720", "480", "360", "160", "audio"};
        final String[] qLabels = {a.getString(R.string.q_ask), a.getString(R.string.q_best), "1080p", "720p", "480p", "360p", "160p",
                a.getString(R.string.audio_only)};
        dl.addView(choice(R.drawable.ic_hd, a.getString(R.string.s_quality), qLabels, () -> qLabels[indexOf(qKeys, Prefs.quality())],
                i -> Prefs.quality(qKeys[i]), indexOf(qKeys, Prefs.quality())));
        final String[] fKeys = {"mp4", "ts"};
        final String[] fLabels = {a.getString(R.string.format_mp4_long), a.getString(R.string.format_ts_long)};
        dl.addView(choice(R.drawable.ic_movie, a.getString(R.string.format), fLabels, () -> fLabels[indexOf(fKeys, Prefs.format())],
                i -> Prefs.format(fKeys[i]), indexOf(fKeys, Prefs.format())));
        dl.addView(stepper(R.drawable.ic_download, a.getString(R.string.s_concurrent), a.getString(R.string.s_concurrent_hint),
                Prefs.concurrent(), 1, 4, v -> {
                    Prefs.concurrent(v);
                    DownloadManager.get().schedule();
                }));
        dl.addView(stepper(R.drawable.ic_speed, a.getString(R.string.s_connections), a.getString(R.string.s_connections_hint),
                Prefs.connections(), 1, 10, Prefs::connections));
        dl.addView(MediaActions.toggleRow(c, R.drawable.ic_folder, a.getString(R.string.s_gallery),
                a.getString(R.string.s_gallery_hint, "Movies/" + Storage.FOLDER), Prefs.gallery(), on -> {
                    Prefs.gallery(on);
                    if (on) a.ensurePermissions();
                }));
        dl.addView(MediaActions.toggleRow(c, R.drawable.ic_info, a.getString(R.string.s_wifi), a.getString(R.string.s_wifi_hint),
                Prefs.wifiOnly(), on -> {
                    Prefs.wifiOnly(on);
                    DownloadManager.get().schedule();
                }));
        dl.addView(MediaActions.toggleRow(c, R.drawable.ic_refresh, a.getString(R.string.s_autoresume), a.getString(R.string.s_autoresume_hint),
                Prefs.autoResume(), Prefs::autoResume));
        dl.addView(MediaActions.toggleRow(c, R.drawable.ic_check, a.getString(R.string.s_notify), null, Prefs.notifyDone(), Prefs::notifyDone));
        if (Build.VERSION.SDK_INT >= 23) {
            dl.addView(action(R.drawable.ic_speed, a.getString(R.string.s_battery), a.getString(R.string.s_battery_hint), () -> {
                try {
                    Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + a.getPackageName()));
                    a.startActivity(i);
                } catch (Exception e) {
                    try { a.startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)); } catch (Exception ignored) {}
                }
            }));
        }

        // ---------------------------------------------------------- player
        LinearLayout pl = group(a.getString(R.string.s_player));
        final String[] pKeys = {"internal", "external", "ask"};
        final String[] pLabels = {a.getString(R.string.player_internal), a.getString(R.string.player_external), a.getString(R.string.q_ask)};
        pl.addView(choice(R.drawable.ic_play, a.getString(R.string.s_player_choice), pLabels, () -> pLabels[indexOf(pKeys, Prefs.player())],
                i -> Prefs.player(pKeys[i]), indexOf(pKeys, Prefs.player())));
        final int[] steps = {5, 10, 15, 30};
        final String[] stepLabels = {"5 s", "10 s", "15 s", "30 s"};
        pl.addView(choice(R.drawable.ic_fwd, a.getString(R.string.s_seek_step), stepLabels, () -> Prefs.seekStep() + " s",
                i -> Prefs.seekStep(steps[i]), indexOfInt(steps, Prefs.seekStep())));
        final float[] speeds = {0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f};
        final String[] speedLabels = {"0.5×", "0.75×", "1×", "1.25×", "1.5×", "2×"};
        pl.addView(choice(R.drawable.ic_speed, a.getString(R.string.s_default_speed), speedLabels,
                () -> speedLabels[indexOfFloat(speeds, Prefs.defaultSpeed())], i -> Prefs.defaultSpeed(speeds[i]),
                indexOfFloat(speeds, Prefs.defaultSpeed())));
        pl.addView(MediaActions.toggleRow(c, R.drawable.ic_brightness, a.getString(R.string.s_gestures), a.getString(R.string.s_gestures_hint),
                Prefs.gestures(), Prefs::gestures));
        pl.addView(MediaActions.toggleRow(c, R.drawable.ic_rotate, a.getString(R.string.s_autorotate), a.getString(R.string.s_autorotate_hint),
                Prefs.autoRotate(), Prefs::autoRotate));
        pl.addView(MediaActions.toggleRow(c, R.drawable.ic_history, a.getString(R.string.s_resume), a.getString(R.string.s_resume_hint),
                Prefs.resumePlayback(), Prefs::resumePlayback));

        // ---------------------------------------------------------- appearance
        LinearLayout ap = group(a.getString(R.string.s_appearance));
        ap.addView(MediaActions.toggleRow(c, R.drawable.ic_aspect, a.getString(R.string.s_anim_bg), a.getString(R.string.s_anim_bg_hint),
                Prefs.animatedBg(), on -> {
                    Prefs.animatedBg(on);
                    a.restartBackground();
                }));
        ap.addView(MediaActions.toggleRow(c, R.drawable.ic_tune, a.getString(R.string.s_haptics), null, Prefs.haptics(), Prefs::haptics));

        // ---------------------------------------------------------- data
        LinearLayout da = group(a.getString(R.string.s_data));
        da.addView(action(R.drawable.ic_verified, a.getString(R.string.s_verify), a.getString(R.string.s_verify_hint), () -> VerifyActivity.open(a)));
        da.addView(action(R.drawable.ic_delete, a.getString(R.string.s_cache), Fmt.size(ImageLoader.diskSize()), () -> {
            ImageLoader.clear();
            Toaster.show(a, R.drawable.ic_check, a.getString(R.string.cache_cleared));
            fill();
        }));
        da.addView(action(R.drawable.ic_history, a.getString(R.string.s_clear_search), null, () -> {
            Prefs.clearSearches();
            Prefs.clearPositions();
            Toaster.show(a, R.drawable.ic_check, a.getString(R.string.cleared));
        }));

        // ---------------------------------------------------------- about
        LinearLayout ab = group(a.getString(R.string.s_about));
        LinearLayout about = Ui.col(c);
        about.setPadding(Ui.dp(16), Ui.dp(14), Ui.dp(16), Ui.dp(16));
        LinearLayout ar = Ui.row(c);
        ImageView logo = new ImageView(c);
        logo.setImageResource(R.mipmap.ic_launcher);
        ar.addView(logo, Ui.lp(Ui.dp(48), Ui.dp(48)));
        LinearLayout ac = Ui.col(c);
        ac.addView(Ui.text(c, a.getString(R.string.app_name), 16, Ui.TEXT, Ui.BOLD));
        ac.addView(Ui.text(c, a.getString(R.string.version_fmt, BuildInfo.VERSION), 12, Ui.TEXT3, Ui.REGULAR),
                Ui.margins(Ui.lp(Ui.WRAP, Ui.WRAP), 0, 4, 0, 0));
        ar.addView(ac, Ui.margins(Ui.lpw(0, Ui.WRAP, 1), 14, 0, 0, 0));
        about.addView(ar);
        TextView disc = Ui.text(c, a.getString(R.string.disclaimer), 12, Ui.TEXT2, Ui.REGULAR);
        about.addView(disc, Ui.margins(Ui.lp(Ui.MATCH, Ui.WRAP), 0, 12, 0, 0));
        ab.addView(about);

        content.addView(Ui.space(c, 1, 12));
    }

    private LinearLayout group(String title) {
        content.addView(Views.section(a, title), Ui.margins(Ui.lp(Ui.WRAP, Ui.WRAP), 6, 8, 0, 10));
        LinearLayout g = Ui.col(a);
        g.setBackground(Ui.glass(22));
        g.setPadding(Ui.dp(4), Ui.dp(4), Ui.dp(4), Ui.dp(4));
        // child rows bring their own glass; flatten them inside the group
        g.setOnHierarchyChangeListener(new android.view.ViewGroup.OnHierarchyChangeListener() {
            @Override public void onChildViewAdded(View parent, View child) {
                child.setBackground(Ui.ripple(Ui.solid(0, 18), 18));
            }

            @Override public void onChildViewRemoved(View parent, View child) {}
        });
        content.addView(g, Ui.margins(Ui.lp(Ui.MATCH, Ui.WRAP), 0, 0, 0, 18));
        return g;
    }

    private View rowBase(int icon, String title, String sub) {
        LinearLayout r = Ui.row(a);
        r.setPadding(Ui.dp(14), Ui.dp(12), Ui.dp(12), Ui.dp(12));
        r.setMinimumHeight(Ui.dp(56));
        r.addView(Ui.icon(a, icon, 20, Ui.TEXT2), Ui.margins(Ui.lp(Ui.dp(20), Ui.dp(20)), 0, 0, 14, 0));
        LinearLayout col = Ui.col(a);
        col.addView(Ui.text(a, title, 14, Ui.TEXT, Ui.MEDIUM));
        if (sub != null) col.addView(Ui.lines(Ui.text(a, sub, 12, Ui.TEXT3, Ui.REGULAR), 3), Ui.margins(Ui.lp(Ui.WRAP, Ui.WRAP), 0, 3, 0, 0));
        r.addView(col, Ui.lpw(0, Ui.WRAP, 1));
        return r;
    }

    private View choice(int icon, final String title, final String[] labels, final Value value, final Choose choose, int selected) {
        final LinearLayout r = (LinearLayout) rowBase(icon, title, null);
        final TextView v = Ui.text(a, value.get(), 13, Ui.ACCENT, Ui.MEDIUM);
        r.addView(v);
        r.addView(Ui.icon(a, R.drawable.ic_chevron, 18, Ui.TEXT3), Ui.margins(Ui.lp(Ui.dp(18), Ui.dp(18)), 6, 0, 0, 0));
        r.setOnClickListener(x -> {
            Ui.tick(x);
            Sheet s = new Sheet(a).title(title, null);
            String cur = value.get();
            for (int i = 0; i < labels.length; i++) {
                final int idx = i;
                boolean on = labels[i].equals(cur);
                s.item(on ? R.drawable.ic_check : 0, labels[i], on ? Ui.ACCENT : Ui.TEXT, () -> {
                    choose.set(idx);
                    v.setText(value.get());
                    Ui.bounce(v);
                });
            }
            s.show();
        });
        return r;
    }

    interface IntSet { void set(int v); }

    private View stepper(int icon, String title, String sub, int initial, final int min, final int max, final IntSet set) {
        LinearLayout r = (LinearLayout) rowBase(icon, title, sub);
        final int[] val = {initial};
        LinearLayout st = Ui.row(a);
        st.setBackground(Ui.glass(16));
        final TextView vt = Ui.text(a, String.valueOf(initial), 15, Ui.TEXT, Ui.BOLD);
        vt.setGravity(Gravity.CENTER);
        TextView minus = Ui.text(a, "−", 18, Ui.TEXT, Ui.BOLD);
        minus.setGravity(Gravity.CENTER);
        TextView plus = Ui.text(a, "+", 18, Ui.TEXT, Ui.BOLD);
        plus.setGravity(Gravity.CENTER);
        minus.setOnClickListener(x -> {
            if (val[0] <= min) return;
            Ui.tick(x);
            val[0]--;
            vt.setText(String.valueOf(val[0]));
            Ui.bounce(vt);
            set.set(val[0]);
        });
        plus.setOnClickListener(x -> {
            if (val[0] >= max) return;
            Ui.tick(x);
            val[0]++;
            vt.setText(String.valueOf(val[0]));
            Ui.bounce(vt);
            set.set(val[0]);
        });
        st.addView(minus, Ui.lp(Ui.dp(34), Ui.dp(34)));
        st.addView(vt, Ui.lp(Ui.dp(26), Ui.dp(34)));
        st.addView(plus, Ui.lp(Ui.dp(34), Ui.dp(34)));
        r.addView(st);
        return r;
    }

    private View action(int icon, String title, String sub, final Runnable run) {
        LinearLayout r = (LinearLayout) rowBase(icon, title, sub);
        r.addView(Ui.icon(a, R.drawable.ic_chevron, 18, Ui.TEXT3), Ui.lp(Ui.dp(18), Ui.dp(18)));
        r.setOnClickListener(x -> {
            Ui.tick(x);
            run.run();
        });
        return r;
    }

    private static int indexOf(String[] arr, String v) {
        for (int i = 0; i < arr.length; i++) if (arr[i].equals(v)) return i;
        return 0;
    }

    private static int indexOfInt(int[] arr, int v) {
        for (int i = 0; i < arr.length; i++) if (arr[i] == v) return i;
        return 0;
    }

    private static int indexOfFloat(float[] arr, float v) {
        for (int i = 0; i < arr.length; i++) if (Math.abs(arr[i] - v) < 0.01f) return i;
        return 2;
    }

    @Override public void onInsets(int top, int bottom) {
        if (scroll != null) scroll.setPadding(0, top + Ui.dp(8), 0, bottom + Ui.dp(16));
    }

    @Override public void onShow() { if (content != null) fill(); }

    @Override public void scrollToTop() { if (scroll != null) scroll.smoothScrollTo(0, 0); }
}
