package az.kickdl.app.screens;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import az.kickdl.app.MainActivity;
import az.kickdl.app.PlayerActivity;
import az.kickdl.app.R;
import az.kickdl.app.data.Channel;
import az.kickdl.app.data.Media;
import az.kickdl.app.data.Prefs;
import az.kickdl.app.dl.DownloadManager;
import az.kickdl.app.dl.DownloadTask;
import az.kickdl.app.dl.Storage;
import az.kickdl.app.hls.Hls;
import az.kickdl.app.net.Http;
import az.kickdl.app.ui.Bg;
import az.kickdl.app.ui.Fmt;
import az.kickdl.app.ui.ImageLoader;
import az.kickdl.app.ui.RangeSlider;
import az.kickdl.app.ui.Segmented;
import az.kickdl.app.ui.Sheet;
import az.kickdl.app.ui.Toaster;
import az.kickdl.app.ui.Toggle;
import az.kickdl.app.ui.Ui;

/** Play / download / share flows for a media item, including the quality picker sheet. */
public final class MediaActions {
    private MediaActions() {}

    private static final long AUDIO_BPS = 160_000;

    // ------------------------------------------------------------------ playback

    public static void play(MainActivity a, Media m) {
        DownloadTask t = DownloadManager.get().forMedia(m.id);
        if (t != null && t.isFinished() && t.outputUri != null) {
            playTask(a, t);
            return;
        }
        stream(a, m, m.source);
    }

    public static void stream(Activity a, Media m, String url) {
        String mime = m.isDirectFile() ? "video/mp4" : "application/x-mpegURL";
        choosePlayer(a, Uri.parse(url), m.title, mime, "media:" + m.id, null, m.kind == Media.LIVE);
    }

    public static void playTask(Activity a, DownloadTask t) {
        if (!Storage.exists(a, t.outputUri)) {
            Toaster.show(a, R.drawable.ic_error, a.getString(R.string.file_missing));
            return;
        }
        choosePlayer(a, Uri.parse(t.outputUri), t.title, t.mime, "file:" + t.id, t.id, false);
    }

    static void choosePlayer(final Activity a, final Uri uri, final String title, final String mime,
                             final String posKey, final String taskId, final boolean live) {
        String p = Prefs.player();
        if ("external".equals(p)) {
            external(a, uri, mime, title);
        } else if ("ask".equals(p)) {
            new Sheet(a).title(a.getString(R.string.open_with), title)
                    .item(R.drawable.ic_play, a.getString(R.string.player_internal), () -> internal(a, uri, title, posKey, taskId, live))
                    .item(R.drawable.ic_open, a.getString(R.string.player_external), () -> external(a, uri, mime, title))
                    .show();
        } else {
            internal(a, uri, title, posKey, taskId, live);
        }
    }

    public static void internal(Activity a, Uri uri, String title, String posKey, String taskId, boolean live) {
        Intent i = new Intent(a, PlayerActivity.class)
                .setData(uri)
                .putExtra(PlayerActivity.EXTRA_TITLE, title)
                .putExtra(PlayerActivity.EXTRA_POS_KEY, posKey)
                .putExtra(PlayerActivity.EXTRA_TASK, taskId)
                .putExtra(PlayerActivity.EXTRA_LIVE, live)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        a.startActivity(i);
        a.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    public static void external(Activity a, Uri uri, String mime, String title) {
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(uri, mime != null ? mime : "video/*");
        i.putExtra("title", title);
        i.putExtra(Intent.EXTRA_TITLE, title);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            a.startActivity(Intent.createChooser(i, a.getString(R.string.open_with)));
        } catch (Exception e) {
            Toaster.show(a, R.drawable.ic_error, a.getString(R.string.no_player));
        }
    }

    public static void copy(Activity a, String label, String text) {
        ClipboardManager cm = (ClipboardManager) a.getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText(label, text));
        Toaster.show(a, R.drawable.ic_copy, a.getString(R.string.copied));
    }

    public static void shareText(Activity a, String title, String text) {
        Intent i = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                .putExtra(Intent.EXTRA_SUBJECT, title);
        a.startActivity(Intent.createChooser(i, title));
    }

    public static void shareFile(Activity a, DownloadTask t) {
        if (t.outputUri == null) return;
        try {
            a.startActivity(DownloadManager.shareIntent(t));
        } catch (Exception e) {
            Toaster.show(a, R.drawable.ic_error, e.getMessage());
        }
    }

    // ------------------------------------------------------------------ menus

    public static void menu(final MainActivity a, final Media m) {
        Sheet s = new Sheet(a).title(m.title, m.channelName);
        final DownloadTask t = DownloadManager.get().forMedia(m.id);
        if (t != null && t.isFinished()) {
            s.item(R.drawable.ic_play, a.getString(R.string.play_downloaded), () -> playTask(a, t));
            s.item(R.drawable.ic_share, a.getString(R.string.share_file), () -> shareFile(a, t));
        }
        s.item(R.drawable.ic_download, a.getString(R.string.download_choose), () -> showSheet(a, m));
        s.item(R.drawable.ic_play, a.getString(R.string.watch_online), () -> stream(a, m, m.source));
        s.item(R.drawable.ic_open, a.getString(R.string.player_external),
                () -> external(a, Uri.parse(m.source), m.isDirectFile() ? "video/mp4" : "application/x-mpegURL", m.title));
        s.item(R.drawable.ic_link, a.getString(R.string.copy_stream_link), () -> copy(a, m.title, m.source));
        s.item(R.drawable.ic_share, a.getString(R.string.share_link), () -> shareText(a, m.title, m.webUrl()));
        if (m.channelSlug != null && !m.channelSlug.isEmpty() && !(a.top() instanceof ChannelScreen))
            s.item(R.drawable.ic_people, a.getString(R.string.open_channel), () -> a.openChannel(m.channelSlug, null));
        s.show();
    }

    /** Tap on the ring button of a list item. */
    public static void downloadTapped(MainActivity a, Media m) {
        DownloadManager dm = DownloadManager.get();
        DownloadTask t = dm.forMedia(m.id);
        if (t != null) {
            switch (t.status) {
                case DownloadTask.RUNNING:
                    if (t.live) dm.stopRecording(t.id); else dm.pause(t.id);
                    return;
                case DownloadTask.QUEUED:
                case DownloadTask.WAITING_NET:
                    dm.pause(t.id);
                    return;
                case DownloadTask.PAUSED:
                case DownloadTask.FAILED:
                    dm.resume(t.id);
                    return;
                case DownloadTask.MERGING:
                    return;
                case DownloadTask.DONE:
                    if (Storage.exists(a, t.outputUri)) { playTask(a, t); return; }
                    break; // file deleted: download again
            }
        }
        String q = Prefs.quality();
        if ("ask".equals(q)) showSheet(a, m);
        else quick(a, m, q);
    }

    /** Downloads straight away with the remembered quality. */
    static void quick(final MainActivity a, final Media m, final String pref) {
        if (m.isDirectFile()) {
            enqueue(a, m, directVariant(m), false, -1, -1, Prefs.format());
            return;
        }
        Toaster.show(a, R.drawable.ic_download, a.getString(R.string.preparing));
        Bg.run(() -> Hls.parseMaster(Http.text(m.source), m.source), vs -> {
            if (vs.isEmpty()) { showSheet(a, m); return; }
            boolean audio = "audio".equals(pref);
            enqueue(a, m, pick(vs, pref), audio, -1, -1, Prefs.format());
        }, e -> Toaster.show(a, R.drawable.ic_error, Errors.message(a, e)));
    }

    static Hls.Variant pick(List<Hls.Variant> vs, String pref) {
        if (vs.isEmpty()) return null;
        if ("best".equals(pref) || "ask".equals(pref) || "audio".equals(pref)) return vs.get(0);
        int want;
        try { want = Integer.parseInt(pref); } catch (Exception e) { return vs.get(0); }
        Hls.Variant best = null;
        for (Hls.Variant v : vs) {
            if (v.height > 0 && v.height <= want && (best == null || v.height > best.height)) best = v;
        }
        return best != null ? best : vs.get(vs.size() - 1);
    }

    private static Hls.Variant directVariant(Media m) {
        Hls.Variant v = new Hls.Variant();
        v.name = "Original";
        v.url = m.source;
        return v;
    }

    static void enqueue(final MainActivity a, Media m, Hls.Variant v, boolean audio, double start, double end, String format) {
        DownloadTask t = new DownloadTask();
        t.kind = m.kind;
        t.mediaId = m.id;
        t.title = m.title;
        t.channelSlug = m.channelSlug;
        t.channelName = m.channelName;
        t.thumb = m.thumb;
        t.durationSec = m.durationSec;
        t.direct = m.isDirectFile();
        t.url = v != null && !t.direct ? v.url : m.source;
        t.quality = v != null ? v.label() : "";
        t.audioOnly = audio;
        t.trimStart = start;
        t.trimEnd = end;
        t.format = format;
        t.live = m.kind == Media.LIVE;
        if (v != null && !t.live) {
            double frac = 1;
            if (start >= 0 && end > 0 && m.durationSec > 0) frac = (end - start) / m.durationSec;
            long est = audio ? (long) (AUDIO_BPS / 8.0 * m.durationSec * frac) : estimate(v, m.durationSec, frac);
            if (est > 0) t.totalBytes = est;
        }
        a.ensurePermissions();
        DownloadManager.get().enqueue(t);
        String label = t.live ? a.getString(R.string.recording_started) : a.getString(R.string.download_started,
                audio ? a.getString(R.string.audio_only_short) : t.quality);
        Toaster.show(a, t.live ? R.drawable.ic_rec : R.drawable.ic_download, label, a.getString(R.string.show),
                () -> a.switchTab(MainActivity.TAB_DOWNLOADS));
    }

    static long estimate(Hls.Variant v, double dur, double frac) {
        if (v.estimatedBytes > 0) return (long) (v.estimatedBytes * frac);
        if (v.bitrate() > 0 && dur > 0) return (long) (v.bitrate() / 8.0 * dur * frac);
        return -1;
    }

    // ------------------------------------------------------------------ quality sheet

    private static class State {
        List<Hls.Variant> variants = new ArrayList<>();
        int selected = 0; // index into variants; -1 = audio only
        boolean trim;
        double start, end;
        double duration;
        String format = Prefs.format();
        boolean remember;
        final List<View> rows = new ArrayList<>();
        final List<TextView> sizes = new ArrayList<>();
        TextView audioSize;
        View audioRow;
        View formatRow;
    }

    public static void showSheet(final MainActivity a, final Media m) {
        final Sheet sheet = new Sheet(a);
        final State st = new State();
        st.duration = m.durationSec;
        LinearLayout b = sheet.body();

        // header
        LinearLayout head = Ui.row(a);
        FrameLayout thumbBox = new az.kickdl.app.ui.RoundFrame(a, 12);
        thumbBox.setBackground(Ui.solid(0xFF14161D, 12));
        ImageView thumb = new ImageView(a);
        thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        thumbBox.addView(thumb, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        ImageLoader.load(thumb, m.thumb, Ui.dp(240));
        if (m.durationSec > 0) {
            TextView d = Ui.chip(a, Fmt.duration(m.durationSec), 0xCC000000, 0xFFFFFFFF);
            FrameLayout.LayoutParams dl = new FrameLayout.LayoutParams(Ui.WRAP, Ui.WRAP, Gravity.BOTTOM | Gravity.END);
            dl.setMargins(0, 0, Ui.dp(4), Ui.dp(4));
            thumbBox.addView(d, dl);
        }
        head.addView(thumbBox, new LinearLayout.LayoutParams(Ui.dp(124), Ui.dp(70)));
        LinearLayout ht = Ui.col(a);
        TextView title = Ui.lines(Ui.text(a, m.title, 15, Ui.TEXT, Ui.BOLD), 2);
        ht.addView(title);
        TextView sub = Ui.single(Ui.text(a, m.channelName + (m.kind == Media.CLIP ? " · " + a.getString(R.string.clip) : ""), 12, Ui.TEXT2, Ui.REGULAR));
        ht.addView(sub, Ui.margins(Ui.lp(Ui.MATCH, Ui.WRAP), 0, 6, 0, 0));
        head.addView(ht, Ui.margins(Ui.lpw(0, Ui.WRAP, 1), 14, 0, 0, 0));
        b.addView(head, Ui.margins(Ui.lp(Ui.MATCH, Ui.WRAP), 0, 6, 0, 18));

        b.addView(Views.section(a, a.getString(R.string.quality)), Ui.margins(Ui.lp(Ui.WRAP, Ui.WRAP), 4, 0, 0, 10));
        final LinearLayout list = Ui.col(a);
        for (int i = 0; i < 3; i++) list.addView(Views.shimmerRow(a, 58));
        b.addView(list, Ui.lp(Ui.MATCH, Ui.WRAP));

        final LinearLayout extras = Ui.col(a);
        extras.setVisibility(View.GONE);
        b.addView(extras, Ui.lp(Ui.MATCH, Ui.WRAP));

        final LinearLayout dlBtn = Ui.pill(a, R.drawable.ic_download, a.getString(R.string.download), true);
        dlBtn.setMinimumHeight(Ui.dp(54));
        dlBtn.setAlpha(0.5f);
        dlBtn.setEnabled(false);
        b.addView(dlBtn, Ui.margins(Ui.lp(Ui.MATCH, Ui.dp(54)), 0, 18, 0, 0));
        final TextView warn = Ui.text(a, "", 12, Ui.WARN, Ui.MEDIUM);
        warn.setGravity(Gravity.CENTER);
        warn.setVisibility(View.GONE);
        b.addView(warn, Ui.margins(Ui.lp(Ui.MATCH, Ui.WRAP), 0, 8, 0, 0));

        LinearLayout sec = Ui.row(a);
        LinearLayout watch = Ui.pill(a, R.drawable.ic_play, a.getString(R.string.watch), false);
        LinearLayout ext = Ui.pill(a, R.drawable.ic_open, a.getString(R.string.external_short), false);
        LinearLayout link = Ui.pill(a, R.drawable.ic_link, null, false);
        sec.addView(watch, Ui.lpw(0, Ui.dp(44), 1));
        sec.addView(ext, Ui.margins(Ui.lpw(0, Ui.dp(44), 1), 8, 0, 0, 0));
        sec.addView(link, Ui.margins(Ui.lp(Ui.dp(52), Ui.dp(44)), 8, 0, 0, 0));
        b.addView(sec, Ui.margins(Ui.lp(Ui.MATCH, Ui.WRAP), 0, 10, 0, 0));

        final Runnable refresh = () -> updateSheet(a, m, st, dlBtn, warn);

        watch.setOnClickListener(v -> {
            sheet.dismiss();
            stream(a, m, currentUrl(m, st));
        });
        ext.setOnClickListener(v -> {
            sheet.dismiss();
            external(a, Uri.parse(currentUrl(m, st)), m.isDirectFile() ? "video/mp4" : "application/x-mpegURL", m.title);
        });
        link.setOnClickListener(v -> copy(a, m.title, currentUrl(m, st)));
        dlBtn.setOnClickListener(v -> {
            if (st.variants.isEmpty()) return;
            Ui.haptic(v);
            boolean audio = st.selected < 0;
            Hls.Variant var = audio ? st.variants.get(st.variants.size() - 1) : st.variants.get(st.selected);
            if (st.remember) {
                Prefs.quality(audio ? "audio" : var.height > 0 ? String.valueOf(var.height) : "best");
            }
            boolean cut = st.trim && (st.start > 0.5 || st.end < st.duration - 0.5);
            double s = cut ? st.start : -1, e = cut ? st.end : -1;
            sheet.dismiss();
            enqueue(a, m, var, audio, s, e, st.format);
        });

        sheet.show();

        Bg.run(() -> {
            if (m.isDirectFile()) {
                Hls.Variant v = directVariant(m);
                v.estimatedBytes = Http.contentLength(m.source);
                List<Hls.Variant> one = new ArrayList<>();
                one.add(v);
                return one;
            }
            return Hls.parseMaster(Http.text(m.source), m.source);
        }, vs -> {
            if (!sheet.isShowing()) return;
            st.variants = vs;
            if (vs.isEmpty()) {
                list.removeAllViews();
                list.addView(errorRow(a, a.getString(R.string.err_empty)));
                return;
            }
            Hls.Variant def = pick(vs, Prefs.quality());
            st.selected = Math.max(0, vs.indexOf(def));
            if ("audio".equals(Prefs.quality()) && !m.isDirectFile()) st.selected = -1;
            st.start = 0;
            st.end = st.duration;
            buildRows(a, m, st, list, refresh);
            buildExtras(a, m, st, extras, refresh);
            dlBtn.setEnabled(true);
            dlBtn.animate().alpha(1f).setDuration(200).start();
            refresh.run();
            if (!m.isDirectFile()) refineSizes(sheet, st, refresh);
        }, e -> {
            if (!sheet.isShowing()) return;
            list.removeAllViews();
            list.addView(errorRow(a, Errors.message(a, e)));
        });
    }

    private static String currentUrl(Media m, State st) {
        if (st.selected >= 0 && st.selected < st.variants.size() && !m.isDirectFile()) return st.variants.get(st.selected).url;
        return m.source;
    }

    private static View errorRow(Context c, String msg) {
        LinearLayout r = Ui.row(c);
        r.setBackground(Ui.stroke(0x14FF3B5C, 0x40FF3B5C, 14));
        r.setPadding(Ui.dp(14), Ui.dp(14), Ui.dp(14), Ui.dp(14));
        r.addView(Ui.icon(c, R.drawable.ic_error, 20, Ui.LIVE), Ui.margins(Ui.lp(Ui.dp(20), Ui.dp(20)), 0, 0, 12, 0));
        r.addView(Ui.text(c, msg, 13, Ui.TEXT, Ui.MEDIUM), Ui.lpw(0, Ui.WRAP, 1));
        return r;
    }

    private static void buildRows(final MainActivity a, final Media m, final State st, LinearLayout list, final Runnable refresh) {
        list.removeAllViews();
        st.rows.clear();
        st.sizes.clear();
        for (int i = 0; i < st.variants.size(); i++) {
            final int idx = i;
            Hls.Variant v = st.variants.get(i);
            StringBuilder sub = new StringBuilder();
            if (v.width > 0) sub.append(v.width).append('×').append(v.height);
            if (v.bandwidth > 0) {
                if (sub.length() > 0) sub.append(" · ");
                sub.append(String.format(Locale.US, "%.1f Mbps", v.bitrate() / 1_000_000.0));
            }
            if (m.isDirectFile()) sub.append("MP4");
            String badge = v.height >= 1080 ? "FHD" : v.height >= 720 ? "HD" : null;
            View row = qualityRow(a, v.label(), sub.toString(), badge, R.drawable.ic_hd, st, idx);
            row.setOnClickListener(vw -> {
                Ui.tick(vw);
                st.selected = idx;
                refresh.run();
            });
            list.addView(row);
            st.rows.add(row);
            Ui.fadeIn(row, i * 40L);
        }
        if (!m.isDirectFile()) {
            View row = qualityRow(a, a.getString(R.string.audio_only), "M4A · AAC", null, R.drawable.ic_music, st, -1);
            row.setOnClickListener(vw -> {
                Ui.tick(vw);
                st.selected = -1;
                refresh.run();
            });
            list.addView(row);
            st.audioRow = row;
            Ui.fadeIn(row, st.variants.size() * 40L);
        }
    }

    private static View qualityRow(Context c, String label, String sub, String badge, int icon, State st, int idx) {
        LinearLayout row = Ui.row(c);
        row.setPadding(Ui.dp(14), Ui.dp(10), Ui.dp(14), Ui.dp(10));
        row.setMinimumHeight(Ui.dp(58));
        // radio
        FrameLayout radio = new FrameLayout(c);
        radio.setTag("radio");
        View dot = new View(c);
        dot.setTag("dot");
        dot.setBackground(Ui.oval(Ui.ACCENT));
        radio.addView(dot, new FrameLayout.LayoutParams(Ui.dp(10), Ui.dp(10), Gravity.CENTER));
        row.addView(radio, Ui.margins(Ui.lp(Ui.dp(22), Ui.dp(22)), 0, 0, 14, 0));
        LinearLayout col = Ui.col(c);
        LinearLayout top = Ui.row(c);
        TextView l = Ui.text(c, label, 15, Ui.TEXT, Ui.BOLD);
        top.addView(l);
        if (badge != null) {
            TextView bd = Ui.chip(c, badge, 0x2653FC18, Ui.ACCENT);
            bd.setTextSize(10);
            top.addView(bd, Ui.margins(Ui.lp(Ui.WRAP, Ui.WRAP), 8, 0, 0, 0));
        }
        if (idx < 0) top.addView(Ui.icon(c, icon, 14, Ui.TEXT2), Ui.margins(Ui.lp(Ui.dp(14), Ui.dp(14)), 8, 0, 0, 0));
        col.addView(top);
        if (sub != null && !sub.isEmpty()) {
            TextView s = Ui.text(c, sub, 12, Ui.TEXT3, Ui.REGULAR);
            col.addView(s, Ui.margins(Ui.lp(Ui.WRAP, Ui.WRAP), 0, 4, 0, 0));
        }
        row.addView(col, Ui.lpw(0, Ui.WRAP, 1));
        TextView size = Ui.text(c, "", 14, Ui.TEXT2, Ui.MEDIUM);
        size.setTag("size");
        row.addView(size);
        if (idx >= 0) st.sizes.add(size);
        else st.audioSize = size;
        LinearLayout.LayoutParams lp = Ui.lp(Ui.MATCH, Ui.WRAP);
        lp.bottomMargin = Ui.dp(8);
        row.setLayoutParams(lp);
        return row;
    }

    private static void buildExtras(final MainActivity a, final Media m, final State st, LinearLayout extras, final Runnable refresh) {
        extras.removeAllViews();
        extras.setVisibility(View.VISIBLE);
        // Trim (VODs with a known duration)
        if (m.kind == Media.VOD && st.duration > 60) {
            final LinearLayout trimBox = Ui.col(a);
            trimBox.setVisibility(View.GONE);
            final RangeSlider slider = new RangeSlider(a);
            slider.setMax(st.duration);
            final TextView startL = Ui.text(a, "", 13, Ui.TEXT, Ui.MEDIUM);
            final TextView endL = Ui.text(a, "", 13, Ui.TEXT, Ui.MEDIUM);
            final TextView lenL = Ui.text(a, "", 12, Ui.ACCENT, Ui.BOLD);
            final Runnable labels = () -> {
                startL.setText(Fmt.duration(st.start));
                endL.setText(Fmt.duration(st.end));
                lenL.setText(a.getString(R.string.trim_length, Fmt.duration(st.end - st.start)));
            };
            slider.setListener((s, e, user) -> {
                st.start = s;
                st.end = e;
                labels.run();
                refresh.run();
            });
            LinearLayout lrow = Ui.row(a);
            lrow.addView(stepper(a, startL, () -> nudge(st, slider, true, -10, labels, refresh), () -> nudge(st, slider, true, 10, labels, refresh)));
            lrow.addView(Ui.flex(a));
            lrow.addView(lenL);
            lrow.addView(Ui.flex(a));
            lrow.addView(stepper(a, endL, () -> nudge(st, slider, false, -10, labels, refresh), () -> nudge(st, slider, false, 10, labels, refresh)));
            trimBox.addView(slider, Ui.margins(Ui.lp(Ui.MATCH, Ui.WRAP), 0, 6, 0, 2));
            trimBox.addView(lrow, Ui.lp(Ui.MATCH, Ui.WRAP));
            View toggleRow = toggleRow(a, R.drawable.ic_cut, a.getString(R.string.trim), a.getString(R.string.trim_hint), false, on -> {
                st.trim = on;
                if (on) {
                    trimBox.setVisibility(View.VISIBLE);
                    Ui.fadeIn(trimBox, 0);
                } else {
                    trimBox.setVisibility(View.GONE);
                }
                labels.run();
                refresh.run();
            });
            extras.addView(toggleRow, Ui.margins(Ui.lp(Ui.MATCH, Ui.WRAP), 0, 8, 0, 0));
            extras.addView(trimBox, Ui.lp(Ui.MATCH, Ui.WRAP));
            labels.run();
        }
        // Format
        if (!m.isDirectFile()) {
            LinearLayout fr = Ui.row(a);
            st.formatRow = fr;
            fr.addView(Ui.text(a, a.getString(R.string.format), 14, Ui.TEXT, Ui.MEDIUM), Ui.lpw(0, Ui.WRAP, 1));
            Segmented seg = new Segmented(a, "MP4", a.getString(R.string.format_ts));
            seg.select("ts".equals(st.format) ? 1 : 0, false);
            seg.setListener(i -> st.format = i == 1 ? "ts" : "mp4");
            fr.addView(seg, Ui.lp(Ui.dp(190), Ui.WRAP));
            extras.addView(fr, Ui.margins(Ui.lp(Ui.MATCH, Ui.WRAP), 0, 14, 0, 0));
        }
        // Remember
        extras.addView(toggleRow(a, R.drawable.ic_check, a.getString(R.string.remember_quality),
                a.getString(R.string.remember_quality_hint), false, on -> st.remember = on), Ui.margins(Ui.lp(Ui.MATCH, Ui.WRAP), 0, 10, 0, 0));
    }

    private static void nudge(State st, RangeSlider s, boolean start, int delta, Runnable labels, Runnable refresh) {
        if (start) s.setRange(st.start + delta, st.end);
        else s.setRange(st.start, st.end + delta);
        st.start = s.start();
        st.end = s.end();
        labels.run();
        refresh.run();
    }

    private static View stepper(Context c, TextView label, final Runnable minus, final Runnable plus) {
        LinearLayout r = Ui.row(c);
        r.setBackground(Ui.glass(16));
        TextView m = Ui.text(c, "−", 18, Ui.TEXT, Ui.BOLD);
        m.setGravity(Gravity.CENTER);
        m.setOnClickListener(v -> { Ui.tick(v); minus.run(); });
        TextView p = Ui.text(c, "+", 18, Ui.TEXT, Ui.BOLD);
        p.setGravity(Gravity.CENTER);
        p.setOnClickListener(v -> { Ui.tick(v); plus.run(); });
        r.addView(m, Ui.lp(Ui.dp(32), Ui.dp(34)));
        label.setGravity(Gravity.CENTER);
        r.addView(label, Ui.lp(Ui.WRAP, Ui.WRAP));
        r.addView(p, Ui.lp(Ui.dp(32), Ui.dp(34)));
        return r;
    }

    interface OnToggle { void on(boolean checked); }

    static View toggleRow(Context c, int icon, String title, String sub, boolean initial, final OnToggle cb) {
        LinearLayout r = Ui.row(c);
        r.setPadding(Ui.dp(14), Ui.dp(12), Ui.dp(12), Ui.dp(12));
        r.setBackground(Ui.ripple(Ui.glass(16), 16));
        if (icon != 0) r.addView(Ui.icon(c, icon, 20, Ui.TEXT2), Ui.margins(Ui.lp(Ui.dp(20), Ui.dp(20)), 0, 0, 14, 0));
        LinearLayout col = Ui.col(c);
        col.addView(Ui.text(c, title, 14, Ui.TEXT, Ui.MEDIUM));
        if (sub != null) col.addView(Ui.lines(Ui.text(c, sub, 12, Ui.TEXT3, Ui.REGULAR), 2), Ui.margins(Ui.lp(Ui.WRAP, Ui.WRAP), 0, 3, 0, 0));
        r.addView(col, Ui.lpw(0, Ui.WRAP, 1));
        final Toggle tg = new Toggle(c);
        tg.setChecked(initial, false);
        r.addView(tg);
        r.setOnClickListener(v -> {
            Ui.tick(v);
            tg.setChecked(!tg.isChecked(), true);
            cb.on(tg.isChecked());
        });
        return r;
    }

    private static void updateSheet(Context c, Media m, State st, LinearLayout dlBtn, TextView warn) {
        double frac = 1;
        if (st.trim && st.duration > 0) frac = Math.max(0, st.end - st.start) / st.duration;
        long chosen = -1;
        for (int i = 0; i < st.rows.size(); i++) {
            boolean sel = i == st.selected;
            styleRow(st.rows.get(i), sel);
            long est = estimate(st.variants.get(i), st.duration, frac);
            TextView sz = st.sizes.get(i);
            sz.setText(est > 0 ? "≈ " + Fmt.size(est) : "");
            sz.setTextColor(sel ? Ui.ACCENT : Ui.TEXT2);
            if (sel) chosen = est;
        }
        if (st.audioRow != null) {
            boolean sel = st.selected < 0;
            styleRow(st.audioRow, sel);
            long est = st.duration > 0 ? (long) (AUDIO_BPS / 8.0 * st.duration * frac) : -1;
            st.audioSize.setText(est > 0 ? "≈ " + Fmt.size(est) : "");
            st.audioSize.setTextColor(sel ? Ui.ACCENT : Ui.TEXT2);
            if (sel) chosen = est;
            if (st.formatRow != null) st.formatRow.setAlpha(sel ? 0.35f : 1f);
        }
        String label = c.getString(R.string.download) + (chosen > 0 ? "  ·  ≈ " + Fmt.size(chosen) : "");
        Ui.setPillLabel(dlBtn, label);
        long free = Storage.freeBytes(c);
        if (chosen > 0 && free > 0 && chosen * 2.05 > free) {
            warn.setText(c.getString(R.string.low_space, Fmt.size(free)));
            warn.setVisibility(View.VISIBLE);
        } else {
            warn.setVisibility(View.GONE);
        }
    }

    private static void styleRow(View row, boolean sel) {
        row.setBackground(Ui.ripple(sel ? Ui.stroke(0x1A53FC18, 0x8053FC18, 16) : Ui.glass(16), 16));
        View radio = row.findViewWithTag("radio");
        View dot = row.findViewWithTag("dot");
        GradientDrawable ring = new GradientDrawable();
        ring.setShape(GradientDrawable.OVAL);
        ring.setStroke(Ui.dp(2), sel ? Ui.ACCENT : 0x55FFFFFF);
        radio.setBackground(ring);
        float target = sel ? 1f : 0f;
        if (dot.getScaleX() != target) dot.animate().scaleX(target).scaleY(target).setDuration(220).start();
    }

    /** Fetches each rendition's playlist and samples segment sizes for an accurate size estimate. */
    private static void refineSizes(final Sheet sheet, final State st, final Runnable refresh) {
        for (final Hls.Variant v : new ArrayList<>(st.variants)) {
            Bg.run(() -> {
                Hls.MediaPlaylist pl = Hls.parseMedia(Http.text(v.url), v.url);
                int n = pl.segments.size();
                if (n == 0) return new double[]{-1, 0};
                int[] picks = n >= 3 ? new int[]{Math.min(1, n - 1), n / 2, Math.max(0, n - 2)} : new int[]{0};
                double bytes = 0, secs = 0;
                for (int p : picks) {
                    Hls.Segment s = pl.segments.get(p);
                    long len = s.byteLength > 0 ? s.byteLength : Http.contentLength(s.url);
                    if (len > 0 && s.duration > 0) {
                        bytes += len;
                        secs += s.duration;
                    }
                }
                if (secs <= 0) return new double[]{-1, pl.totalDuration};
                return new double[]{bytes / secs * pl.totalDuration, pl.totalDuration};
            }, r -> {
                if (r[0] > 0) v.estimatedBytes = (long) r[0];
                if (st.duration <= 0 && r[1] > 0) {
                    st.duration = r[1];
                    st.end = r[1];
                }
                if (sheet.isShowing()) refresh.run();
            }, e -> { });
        }
    }

    // ------------------------------------------------------------------ live recording

    public static void recordLive(final MainActivity a, final Channel c) {
        if (c.playbackUrl == null) {
            Toaster.show(a, R.drawable.ic_error, a.getString(R.string.err_no_stream));
            return;
        }
        final Media m = liveMedia(c);
        final Sheet sheet = new Sheet(a).title(a.getString(R.string.record_live_title, c.displayName()), c.liveTitle);
        final LinearLayout list = Ui.col(a);
        for (int i = 0; i < 3; i++) list.addView(Views.shimmerRow(a, 52));
        sheet.body().addView(list);
        TextView note = Ui.text(a, a.getString(R.string.record_note), 12, Ui.TEXT3, Ui.REGULAR);
        sheet.body().addView(note, Ui.margins(Ui.lp(Ui.MATCH, Ui.WRAP), 4, 8, 4, 0));
        sheet.show();
        Bg.run(() -> Hls.parseMaster(Http.text(c.playbackUrl), c.playbackUrl), vs -> {
            if (!sheet.isShowing()) return;
            list.removeAllViews();
            for (int i = 0; i < vs.size(); i++) {
                final Hls.Variant v = vs.get(i);
                LinearLayout row = Ui.row(a);
                row.setPadding(Ui.dp(14), 0, Ui.dp(14), 0);
                row.setMinimumHeight(Ui.dp(52));
                row.setBackground(Ui.ripple(Ui.glass(16), 16));
                row.addView(Ui.icon(a, R.drawable.ic_rec, 16, Ui.LIVE), Ui.margins(Ui.lp(Ui.dp(16), Ui.dp(16)), 0, 0, 14, 0));
                row.addView(Ui.text(a, v.label(), 15, Ui.TEXT, Ui.BOLD), Ui.lpw(0, Ui.WRAP, 1));
                if (v.bitrate() > 0) row.addView(Ui.text(a, a.getString(R.string.per_hour, Fmt.size(v.bitrate() / 8 * 3600)), 12, Ui.TEXT2, Ui.REGULAR));
                row.setOnClickListener(vw -> {
                    sheet.dismiss();
                    enqueue(a, m, v, false, -1, -1, Prefs.format());
                });
                list.addView(row, Ui.margins(Ui.lp(Ui.MATCH, Ui.WRAP), 0, 0, 0, 8));
                Ui.fadeIn(row, i * 40L);
            }
        }, e -> {
            if (!sheet.isShowing()) return;
            list.removeAllViews();
            list.addView(errorRow(a, Errors.message(a, e)));
        });
    }

    public static Media liveMedia(Channel c) {
        Media m = new Media();
        m.kind = Media.LIVE;
        m.id = "live:" + c.slug;
        m.title = c.liveTitle != null && !c.liveTitle.isEmpty() ? c.liveTitle : c.displayName() + " live";
        m.thumb = c.liveThumb != null ? c.liveThumb : c.avatar;
        m.channelSlug = c.slug;
        m.channelName = c.displayName();
        m.channelAvatar = c.avatar;
        m.source = c.playbackUrl;
        m.isLive = true;
        m.category = c.liveCategory;
        m.views = c.liveViewers;
        return m;
    }
}
