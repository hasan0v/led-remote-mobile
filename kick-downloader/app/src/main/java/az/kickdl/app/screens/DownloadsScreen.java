package az.kickdl.app.screens;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import az.kickdl.app.MainActivity;
import az.kickdl.app.R;
import az.kickdl.app.data.Media;
import az.kickdl.app.dl.DownloadManager;
import az.kickdl.app.dl.DownloadTask;
import az.kickdl.app.dl.Storage;
import az.kickdl.app.ui.Fmt;
import az.kickdl.app.ui.ImageLoader;
import az.kickdl.app.ui.ProgressBarView;
import az.kickdl.app.ui.Segmented;
import az.kickdl.app.ui.Sheet;
import az.kickdl.app.ui.Toaster;
import az.kickdl.app.ui.Ui;

/** Active downloads with live progress, plus the finished history. */
public class DownloadsScreen extends Screen implements DownloadManager.Listener {
    private static final int F_ALL = 0, F_VIDEO = 1, F_CLIP = 2, F_AUDIO = 3, F_LIVE = 4;

    private FrameLayout root;
    private LinearLayout headerTop;
    private ListView list;
    private LinearLayout header;
    private Segmented seg;
    private TextView storageText;
    private ProgressBarView storageBar;
    private LinearLayout filters;
    private LinearLayout emptyBox;
    private final List<DownloadTask> items = new ArrayList<>();
    private final Adapter adapter = new Adapter();
    private int page = 0;
    private int filter = F_ALL;
    private int lastAnimated = -1;
    private int lastStructure = -1;

    public DownloadsScreen(MainActivity a) { super(a); }

    @Override protected View build() {
        Context c = a;
        root = new FrameLayout(c);
        list = new ListView(c);
        list.setDivider(null);
        list.setSelector(new android.graphics.drawable.ColorDrawable(0));
        list.setClipToPadding(false);
        list.setVerticalScrollBarEnabled(false);
        root.addView(list, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));

        header = Ui.col(c);
        header.setPadding(Ui.dp(18), 0, Ui.dp(18), 0);
        headerTop = Ui.row(c);
        headerTop.addView(Ui.title(c, a.getString(R.string.tab_downloads)), Ui.lpw(0, Ui.WRAP, 1));
        ImageView more = Ui.iconButton(c, R.drawable.ic_more, 44, 22);
        more.setOnClickListener(v -> menu());
        headerTop.addView(more);
        header.addView(headerTop, Ui.margins(Ui.lp(Ui.MATCH, Ui.WRAP), 0, 8, 0, 16));

        // storage card
        LinearLayout st = Ui.col(c);
        st.setBackground(Ui.glass(20));
        st.setPadding(Ui.dp(16), Ui.dp(14), Ui.dp(16), Ui.dp(14));
        LinearLayout sr = Ui.row(c);
        sr.addView(Ui.icon(c, R.drawable.ic_folder, 18, Ui.ACCENT), Ui.margins(Ui.lp(Ui.dp(18), Ui.dp(18)), 0, 0, 10, 0));
        storageText = Ui.single(Ui.text(c, "", 13, Ui.TEXT, Ui.MEDIUM));
        sr.addView(storageText, Ui.lpw(0, Ui.WRAP, 1));
        st.addView(sr);
        storageBar = new ProgressBarView(c);
        storageBar.setColors(0xFF8B6CFF, 0xFF2BD9F0);
        st.addView(storageBar, Ui.margins(Ui.lp(Ui.MATCH, Ui.dp(6)), 0, 12, 0, 0));
        TextView loc = Ui.single(Ui.text(c, a.getString(R.string.saved_to, Storage.locationLabel(c, false)), 11, Ui.TEXT3, Ui.REGULAR));
        loc.setTag("loc");
        st.addView(loc, Ui.margins(Ui.lp(Ui.MATCH, Ui.WRAP), 0, 8, 0, 0));
        header.addView(st, Ui.margins(Ui.lp(Ui.MATCH, Ui.WRAP), 0, 0, 0, 16));

        seg = new Segmented(c, a.getString(R.string.active), a.getString(R.string.history));
        seg.setListener(i -> {
            page = i;
            lastAnimated = -1;
            filters.setVisibility(page == 1 ? View.VISIBLE : View.GONE);
            rebuild(true);
        });
        header.addView(seg, Ui.margins(Ui.lp(Ui.MATCH, Ui.WRAP), 0, 0, 0, 12));

        HorizontalScrollView hs = new HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        filters = Ui.row(c);
        hs.addView(filters);
        filters.setVisibility(View.GONE);
        header.addView(hs, Ui.margins(Ui.lp(Ui.MATCH, Ui.WRAP), 0, 0, 0, 6));
        buildFilters();

        emptyBox = Ui.col(c);
        header.addView(emptyBox, Ui.lp(Ui.MATCH, Ui.WRAP));

        list.addHeaderView(header, null, false);
        list.setAdapter(adapter);
        rebuild(true);
        return root;
    }

    private void buildFilters() {
        filters.removeAllViews();
        String[] labels = {a.getString(R.string.f_all), a.getString(R.string.f_videos), a.getString(R.string.f_clips),
                a.getString(R.string.f_audio), a.getString(R.string.f_live)};
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            boolean on = filter == i;
            TextView t = Ui.text(a, labels[i], 13, on ? Ui.ON_ACCENT : Ui.TEXT2, on ? Ui.BOLD : Ui.MEDIUM);
            t.setPadding(Ui.dp(14), Ui.dp(8), Ui.dp(14), Ui.dp(8));
            t.setBackground(Ui.ripple(on ? Ui.solid(Ui.ACCENT, 16) : Ui.glass(16), 16));
            t.setOnClickListener(v -> {
                Ui.tick(v);
                filter = idx;
                buildFilters();
                lastAnimated = -1;
                rebuild(true);
            });
            filters.addView(t, Ui.margins(Ui.lp(Ui.WRAP, Ui.WRAP), 0, 0, 8, 0));
        }
    }

    private boolean matches(DownloadTask t) {
        switch (filter) {
            case F_VIDEO: return t.kind == Media.VOD && !t.audioOnly;
            case F_CLIP: return t.kind == Media.CLIP;
            case F_AUDIO: return t.audioOnly;
            case F_LIVE: return t.live;
            default: return true;
        }
    }

    private void rebuild(boolean force) {
        List<DownloadTask> all = DownloadManager.get().all();
        List<DownloadTask> next = new ArrayList<>();
        int active = 0, hist = 0;
        for (DownloadTask t : all) {
            boolean isHist = t.status == DownloadTask.DONE;
            if (isHist) hist++; else active++;
            if (page == 0 && !isHist) next.add(t);
            if (page == 1 && isHist && matches(t)) next.add(t);
        }
        seg.setLabel(0, a.getString(R.string.active) + (active > 0 ? "  " + active : ""));
        seg.setLabel(1, a.getString(R.string.history) + (hist > 0 ? "  " + hist : ""));
        int structure = next.size() * 31 + page * 7 + filter;
        for (DownloadTask t : next) structure = structure * 17 + t.id.hashCode() + t.status;
        if (force || structure != lastStructure) {
            lastStructure = structure;
            items.clear();
            items.addAll(next);
            adapter.notifyDataSetChanged();
            updateEmpty();
        } else {
            // same rows: refresh visible ones in place for smooth progress animations
            for (int i = 0; i < list.getChildCount(); i++) {
                View v = list.getChildAt(i);
                if (v instanceof Row) ((Row) v).bind(((Row) v).task);
            }
        }
        updateStorage();
    }

    private void updateEmpty() {
        emptyBox.removeAllViews();
        if (!items.isEmpty()) return;
        if (page == 0) {
            emptyBox.addView(Views.empty(a, R.drawable.ic_download, a.getString(R.string.no_active),
                    a.getString(R.string.no_active_hint), a.getString(R.string.find_channel), v -> a.switchTab(MainActivity.TAB_HOME)));
        } else {
            emptyBox.addView(Views.empty(a, R.drawable.ic_library, a.getString(R.string.no_history), null, null, null));
        }
    }

    private void updateStorage() {
        long free = Storage.freeBytes(a), total = Storage.totalBytes(a);
        if (free >= 0 && total > 0) {
            storageText.setText(a.getString(R.string.storage_fmt, Fmt.size(free), Fmt.size(total)));
            storageBar.setProgress(1f - free / (float) total, false);
        }
    }

    private void menu() {
        final DownloadManager dm = DownloadManager.get();
        new Sheet(a).title(a.getString(R.string.tab_downloads), null)
                .item(R.drawable.ic_pause, a.getString(R.string.pause_all), dm::pauseAll)
                .item(R.drawable.ic_play, a.getString(R.string.resume_all), dm::resumeAll)
                .item(R.drawable.ic_delete, a.getString(R.string.clear_history), Ui.LIVE, () -> {
                    dm.clearFinished();
                    Toaster.show(a, R.drawable.ic_check, a.getString(R.string.history_cleared));
                })
                .show();
    }

    private void taskMenu(final DownloadTask t) {
        final DownloadManager dm = DownloadManager.get();
        Sheet s = new Sheet(a).title(t.title, t.outputName != null ? t.outputName : t.channelName);
        if (t.status == DownloadTask.DONE) {
            s.item(R.drawable.ic_play, a.getString(R.string.play), () -> MediaActions.playTask(a, t));
            s.item(R.drawable.ic_open, a.getString(R.string.player_external), () ->
                    MediaActions.external(a, android.net.Uri.parse(t.outputUri), t.mime, t.title));
            s.item(R.drawable.ic_share, a.getString(R.string.share_file), () -> MediaActions.shareFile(a, t));
        }
        if (t.channelSlug != null && !t.channelSlug.isEmpty())
            s.item(R.drawable.ic_people, a.getString(R.string.open_channel), () -> a.openChannel(t.channelSlug, null));
        if (t.status == DownloadTask.FAILED || t.status == DownloadTask.PAUSED)
            s.item(R.drawable.ic_refresh, a.getString(R.string.retry), () -> dm.resume(t.id));
        if (t.status == DownloadTask.DONE) {
            s.item(R.drawable.ic_close, a.getString(R.string.remove_from_list), () -> dm.remove(t.id, false));
            s.item(R.drawable.ic_delete, a.getString(R.string.delete_file), Ui.LIVE, () -> confirmDelete(t));
        } else {
            s.item(R.drawable.ic_close, a.getString(R.string.cancel_download), Ui.LIVE, () -> dm.cancel(t.id));
        }
        s.show();
    }

    private void confirmDelete(final DownloadTask t) {
        new Sheet(a).title(a.getString(R.string.delete_confirm), t.outputName)
                .item(R.drawable.ic_delete, a.getString(R.string.delete_file), Ui.LIVE, () -> {
                    DownloadManager.get().remove(t.id, true);
                    Toaster.show(a, R.drawable.ic_delete, a.getString(R.string.deleted));
                })
                .item(R.drawable.ic_close, a.getString(R.string.cancel), null)
                .show();
    }

    // ------------------------------------------------------------------ lifecycle

    @Override public void onInsets(int top, int bottom) {
        if (header != null) header.setPadding(Ui.dp(18), top + Ui.dp(8), Ui.dp(18), 0);
        if (list != null) list.setPadding(0, 0, 0, bottom + Ui.dp(16));
    }

    @Override public void onShow() {
        DownloadManager.get().addListener(this);
        if (seg != null) {
            View loc = header.findViewWithTag("loc");
            if (loc instanceof TextView) ((TextView) loc).setText(a.getString(R.string.saved_to, Storage.locationLabel(a, false)));
            rebuild(true);
        }
    }

    @Override public void onHide() { DownloadManager.get().removeListener(this); }

    @Override public void onDestroy() { DownloadManager.get().removeListener(this); }

    @Override public void onDownloadsChanged() { if (seg != null) rebuild(false); }

    @Override public void scrollToTop() { if (list != null) list.smoothScrollToPosition(0); }

    /** Shows the history tab if nothing is running (used when opened from a notification). */
    public void showBestPage() {
        if (seg == null) return;
        boolean anyActive = false;
        for (DownloadTask t : DownloadManager.get().all()) if (t.status != DownloadTask.DONE) anyActive = true;
        int p = anyActive ? 0 : 1;
        if (p != page) {
            page = p;
            seg.select(p, true);
            filters.setVisibility(page == 1 ? View.VISIBLE : View.GONE);
            rebuild(true);
        }
    }

    // ------------------------------------------------------------------ rows

    private class Adapter extends BaseAdapter {
        @Override public int getCount() { return items.size(); }

        @Override public Object getItem(int p) { return items.get(p); }

        @Override public long getItemId(int p) { return items.get(p).id.hashCode(); }

        @Override public boolean hasStableIds() { return true; }

        @Override public View getView(int p, View convert, ViewGroup parent) {
            Row r = convert instanceof Row ? (Row) convert : new Row(a);
            r.bind(items.get(p));
            if (p > lastAnimated) {
                lastAnimated = p;
                Ui.fadeIn(r, Math.min(p, 6) * 45L);
            }
            return r;
        }
    }

    private class Row extends LinearLayout {
        DownloadTask task;
        final ImageView thumb;
        final TextView title, status, stats, badge;
        final ProgressBarView bar;
        final ImageView primary, secondary;
        final View playIcon;
        final LinearLayout card;

        Row(Context c) {
            super(c);
            setOrientation(VERTICAL);
            setPadding(Ui.dp(18), 0, Ui.dp(18), Ui.dp(10));
            card = Ui.col(c);
            card.setBackground(Ui.ripple(Ui.glass(20), 20));
            card.setPadding(Ui.dp(12), Ui.dp(12), Ui.dp(12), Ui.dp(12));
            LinearLayout top = Ui.row(c);
            top.setGravity(Gravity.TOP);
            FrameLayout tb = new az.kickdl.app.ui.RoundFrame(c, 12);
            tb.setBackground(Ui.solid(0xFF14161D, 12));
            thumb = new ImageView(c);
            thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
            tb.addView(thumb, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
            FrameLayout po = new FrameLayout(c);
            po.setBackground(Ui.oval(0x80000000));
            po.addView(Ui.icon(c, R.drawable.ic_play, 18, 0xFFFFFFFF), new FrameLayout.LayoutParams(Ui.dp(18), Ui.dp(18), Gravity.CENTER));
            playIcon = po;
            tb.addView(po, new FrameLayout.LayoutParams(Ui.dp(34), Ui.dp(34), Gravity.CENTER));
            badge = Ui.chip(c, "", 0xCC0B0D12, Ui.ACCENT);
            badge.setTextSize(10);
            FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(Ui.WRAP, Ui.WRAP, Gravity.BOTTOM | Gravity.END);
            bl.setMargins(0, 0, Ui.dp(4), Ui.dp(4));
            tb.addView(badge, bl);
            top.addView(tb, Ui.lp(Ui.dp(112), Ui.dp(63)));
            LinearLayout col = Ui.col(c);
            title = Ui.lines(Ui.text(c, 14, Ui.TEXT, Ui.MEDIUM), 2);
            col.addView(title);
            status = Ui.single(Ui.text(c, 12, Ui.TEXT2, Ui.MEDIUM));
            col.addView(status, Ui.margins(Ui.lp(Ui.MATCH, Ui.WRAP), 0, 6, 0, 0));
            top.addView(col, Ui.margins(Ui.lpw(0, Ui.WRAP, 1), 12, 0, 6, 0));
            LinearLayout btns = Ui.col(c);
            primary = Ui.flatIcon(c, R.drawable.ic_pause, 38, 20, Ui.TEXT);
            secondary = Ui.flatIcon(c, R.drawable.ic_close, 38, 18, Ui.TEXT2);
            btns.addView(primary);
            btns.addView(secondary);
            top.addView(btns);
            card.addView(top);
            bar = new ProgressBarView(c);
            card.addView(bar, Ui.margins(Ui.lp(Ui.MATCH, Ui.dp(6)), 0, 12, 0, 0));
            stats = Ui.single(Ui.text(c, 11, Ui.TEXT3, Ui.MEDIUM));
            card.addView(stats, Ui.margins(Ui.lp(Ui.MATCH, Ui.WRAP), 0, 8, 0, 0));
            addView(card, new LayoutParams(Ui.MATCH, Ui.WRAP));

            card.setOnClickListener(v -> {
                if (task == null) return;
                if (task.status == DownloadTask.DONE) MediaActions.playTask(a, task);
                else taskMenu(task);
            });
            card.setOnLongClickListener(v -> {
                if (task != null) { Ui.haptic(v); taskMenu(task); }
                return true;
            });
            primary.setOnClickListener(v -> {
                if (task == null) return;
                Ui.haptic(v);
                DownloadManager dm = DownloadManager.get();
                switch (task.status) {
                    case DownloadTask.RUNNING:
                        if (task.live) dm.stopRecording(task.id); else dm.pause(task.id);
                        break;
                    case DownloadTask.QUEUED:
                    case DownloadTask.WAITING_NET:
                        dm.pause(task.id);
                        break;
                    case DownloadTask.PAUSED:
                    case DownloadTask.FAILED:
                        dm.resume(task.id);
                        break;
                    case DownloadTask.DONE:
                        MediaActions.shareFile(a, task);
                        break;
                }
                Ui.bounce(primary);
            });
            secondary.setOnClickListener(v -> {
                if (task == null) return;
                if (task.status == DownloadTask.DONE) taskMenu(task);
                else {
                    final DownloadTask t = task;
                    DownloadManager.get().cancel(t.id);
                    Toaster.show(a, R.drawable.ic_close, a.getString(R.string.canceled));
                }
            });
        }

        void bind(DownloadTask t) {
            boolean changed = task != t;
            task = t;
            Context c = getContext();
            if (changed) {
                ImageLoader.load(thumb, t.thumb, Ui.dp(224));
                title.setText(t.title);
            }
            badge.setText(t.audioOnly ? c.getString(R.string.audio_only_short) : t.live ? "LIVE · " + t.quality : t.quality);
            badge.setVisibility(t.quality == null || t.quality.isEmpty() && !t.audioOnly ? GONE : VISIBLE);
            playIcon.setVisibility(t.status == DownloadTask.DONE ? VISIBLE : GONE);
            double p = t.progress();
            String pct = Fmt.percent(p);
            bar.setVisibility(VISIBLE);
            secondary.setImageResource(t.status == DownloadTask.DONE ? R.drawable.ic_more : R.drawable.ic_close);
            switch (t.status) {
                case DownloadTask.RUNNING:
                    if (t.live) {
                        status.setText("● " + c.getString(R.string.recording) + " · " + Fmt.duration(t.recordedSec));
                        status.setTextColor(Ui.LIVE);
                        bar.setColors(0xFFFF3B5C, 0xFFFF8A3D);
                        bar.setState(true, true);
                        stats.setText(Fmt.size(t.bytesDone) + "  ·  " + Fmt.speed(t.speed));
                        primary.setImageResource(R.drawable.ic_stop);
                    } else {
                        status.setText(c.getString(R.string.status_downloading) + " · " + pct);
                        status.setTextColor(Ui.ACCENT);
                        bar.setColors(0xFF62FF2B, 0xFF2BD9F0);
                        bar.setState(true, p <= 0);
                        bar.setProgress((float) p, true);
                        StringBuilder s = new StringBuilder();
                        s.append(Fmt.size(t.bytesDone));
                        if (t.totalBytes > 0) s.append(" / ").append(Fmt.size(t.totalBytes));
                        s.append("  ·  ").append(Fmt.speed(t.speed));
                        String eta = Fmt.eta(c, t.etaSeconds());
                        if (!eta.isEmpty()) s.append("  ·  ").append(eta);
                        if (t.segTotal > 0) s.append("  ·  ").append(t.segDone).append('/').append(t.segTotal);
                        stats.setText(s.toString());
                        primary.setImageResource(R.drawable.ic_pause);
                    }
                    break;
                case DownloadTask.MERGING:
                    status.setText(c.getString(R.string.status_merging) + (t.mergeProgress > 0 ? " · " + Fmt.percent(t.mergeProgress) : ""));
                    status.setTextColor(Ui.VIOLET);
                    bar.setColors(0xFF8B6CFF, 0xFF2BD9F0);
                    bar.setState(true, t.mergeProgress <= 0);
                    bar.setProgress((float) t.mergeProgress, true);
                    stats.setText(c.getString(R.string.merging_hint));
                    primary.setImageResource(R.drawable.ic_clock);
                    break;
                case DownloadTask.QUEUED:
                    status.setText(c.getString(R.string.status_queued));
                    status.setTextColor(Ui.TEXT2);
                    bar.setColors(0xFF6B7180, 0xFFA3A9B8);
                    bar.setState(false, false);
                    bar.setProgress((float) p, false);
                    stats.setText(t.totalBytes > 0 ? "≈ " + Fmt.size(t.totalBytes) : "");
                    primary.setImageResource(R.drawable.ic_pause);
                    break;
                case DownloadTask.WAITING_NET:
                    status.setText(c.getString(R.string.status_wifi));
                    status.setTextColor(Ui.WARN);
                    bar.setColors(0xFFFFB020, 0xFFFF7A1A);
                    bar.setState(false, false);
                    bar.setProgress((float) p, false);
                    stats.setText(Fmt.size(t.bytesDone) + (t.totalBytes > 0 ? " / " + Fmt.size(t.totalBytes) : ""));
                    primary.setImageResource(R.drawable.ic_pause);
                    break;
                case DownloadTask.PAUSED:
                    status.setText(c.getString(R.string.status_paused) + " · " + pct);
                    status.setTextColor(Ui.WARN);
                    bar.setColors(0xFFFFB020, 0xFFFF7A1A);
                    bar.setState(false, false);
                    bar.setProgress((float) p, true);
                    stats.setText(Fmt.size(t.bytesDone) + (t.totalBytes > 0 ? " / " + Fmt.size(t.totalBytes) : ""));
                    primary.setImageResource(R.drawable.ic_play);
                    break;
                case DownloadTask.FAILED:
                    status.setText(c.getString(R.string.status_failed));
                    status.setTextColor(Ui.LIVE);
                    bar.setColors(0xFFFF3B5C, 0xFFFF3B5C);
                    bar.setState(false, false);
                    bar.setProgress((float) p, false);
                    stats.setText(t.error != null ? t.error : "");
                    primary.setImageResource(R.drawable.ic_refresh);
                    break;
                case DownloadTask.DONE:
                    bar.setVisibility(GONE);
                    status.setText(t.channelName);
                    status.setTextColor(Ui.TEXT2);
                    StringBuilder s = new StringBuilder();
                    if (t.outputSize > 0) s.append(Fmt.size(t.outputSize)).append("  ·  ");
                    double dur = t.live ? t.recordedSec : t.clipDuration();
                    if (dur > 0) s.append(Fmt.duration(dur)).append("  ·  ");
                    s.append(Fmt.ago(c, t.finishedAt));
                    if (t.outputName != null && t.outputName.endsWith(".ts")) s.append("  ·  TS");
                    stats.setText(s.toString());
                    primary.setImageResource(R.drawable.ic_share);
                    break;
                default:
                    status.setText("");
            }
        }
    }
}
