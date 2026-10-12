package az.kickdl.app.screens;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import az.kickdl.app.MainActivity;
import az.kickdl.app.R;
import az.kickdl.app.data.Media;
import az.kickdl.app.dl.DownloadManager;
import az.kickdl.app.dl.DownloadTask;
import az.kickdl.app.ui.Fmt;
import az.kickdl.app.ui.ImageLoader;
import az.kickdl.app.ui.ProgressBarView;
import az.kickdl.app.ui.RingButton;
import az.kickdl.app.ui.RoundFrame;
import az.kickdl.app.ui.Ui;

/** VOD / clip card: big 16:9 thumbnail, badges, title, meta and a morphing download button. */
public class MediaCard extends LinearLayout {
    private final MainActivity a;
    private final FrameLayout thumbBox;
    private final ImageView thumb;
    private final TextView duration, liveBadge, quality;
    private final ProgressBarView bar;
    private final TextView title, meta;
    private final RingButton ring;
    private final View playOverlay;
    private Media media;

    public MediaCard(MainActivity a) {
        super(a);
        this.a = a;
        Context c = a;
        setOrientation(VERTICAL);
        setPadding(0, Ui.dp(6), 0, Ui.dp(14));

        thumbBox = new RoundFrame(c, 18);
        thumbBox.setBackground(Ui.solid(0xFF12141A, 18));
        thumb = new ImageView(c);
        thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        thumbBox.addView(thumb, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        View grad = new View(c);
        grad.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x00000000, 0x00000000, 0x99000000}));
        thumbBox.addView(grad, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));

        FrameLayout play = new FrameLayout(c);
        play.setBackground(Ui.oval(0x66000000));
        play.addView(Ui.icon(c, R.drawable.ic_play, 28, 0xFFFFFFFF), new FrameLayout.LayoutParams(Ui.dp(28), Ui.dp(28), Gravity.CENTER));
        playOverlay = play;
        play.setAlpha(0.9f);
        thumbBox.addView(play, new FrameLayout.LayoutParams(Ui.dp(54), Ui.dp(54), Gravity.CENTER));

        duration = Ui.chip(c, "", 0xCC000000, 0xFFFFFFFF);
        FrameLayout.LayoutParams dl = new FrameLayout.LayoutParams(Ui.WRAP, Ui.WRAP, Gravity.BOTTOM | Gravity.END);
        dl.setMargins(0, 0, Ui.dp(10), Ui.dp(12));
        thumbBox.addView(duration, dl);
        liveBadge = Views.liveBadge(c);
        FrameLayout.LayoutParams ll = new FrameLayout.LayoutParams(Ui.WRAP, Ui.WRAP, Gravity.TOP | Gravity.START);
        ll.setMargins(Ui.dp(10), Ui.dp(10), 0, 0);
        thumbBox.addView(liveBadge, ll);
        quality = Ui.chip(c, "", 0xCC0B0D12, Ui.ACCENT);
        FrameLayout.LayoutParams ql = new FrameLayout.LayoutParams(Ui.WRAP, Ui.WRAP, Gravity.TOP | Gravity.END);
        ql.setMargins(0, Ui.dp(10), Ui.dp(10), 0);
        thumbBox.addView(quality, ql);
        bar = new ProgressBarView(c);
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(Ui.MATCH, Ui.dp(4), Gravity.BOTTOM);
        bl.setMargins(Ui.dp(10), 0, Ui.dp(10), Ui.dp(5));
        thumbBox.addView(bar, bl);
        addView(thumbBox, new LayoutParams(Ui.MATCH, Ui.dp(200)));

        LinearLayout info = new LinearLayout(c);
        info.setOrientation(HORIZONTAL);
        info.setGravity(Gravity.TOP);
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(VERTICAL);
        title = Ui.lines(Ui.text(c, 15, Ui.TEXT, Ui.MEDIUM), 2);
        meta = Ui.single(Ui.text(c, 12, Ui.TEXT2, Ui.REGULAR));
        col.addView(title);
        col.addView(meta, Ui.margins(Ui.lp(Ui.MATCH, Ui.WRAP), 0, 6, 0, 0));
        info.addView(col, Ui.margins(Ui.lpw(0, Ui.WRAP, 1), 4, 12, 10, 0));
        ring = new RingButton(c);
        info.addView(ring, Ui.margins(Ui.lp(Ui.dp(44), Ui.dp(44)), 0, 8, 0, 0));
        addView(info, new LayoutParams(Ui.MATCH, Ui.WRAP));

        thumbBox.setOnClickListener(v -> { if (media != null) MediaActions.play(a, media); });
        thumbBox.setOnLongClickListener(v -> {
            if (media != null) { Ui.haptic(v); MediaActions.menu(a, media); }
            return true;
        });
        info.setOnLongClickListener(v -> {
            if (media != null) { Ui.haptic(v); MediaActions.menu(a, media); }
            return true;
        });
        info.setOnClickListener(v -> { if (media != null) MediaActions.play(a, media); });
        ring.setOnClickListener(v -> {
            if (media == null) return;
            Ui.haptic(v);
            MediaActions.downloadTapped(a, media);
        });
        ring.setOnLongClickListener(v -> {
            if (media != null) { Ui.haptic(v); MediaActions.showSheet(a, media); }
            return true;
        });
    }

    public void bind(Media m, int thumbHeightPx) {
        boolean same = media == m;
        media = m;
        if (thumbBox.getLayoutParams().height != thumbHeightPx) {
            thumbBox.getLayoutParams().height = thumbHeightPx;
            thumbBox.requestLayout();
        }
        if (!same) {
            ImageLoader.load(thumb, m.thumb, Math.max(320, thumbHeightPx * 16 / 9 / 2));
            title.setText(m.title);
            meta.setText(Views.meta(getContext(), m));
            duration.setText(m.durationSec > 0 ? Fmt.duration(m.durationSec) : "");
            duration.setVisibility(m.durationSec > 0 && !m.isLive ? VISIBLE : GONE);
            liveBadge.setVisibility(m.isLive ? VISIBLE : GONE);
        }
        bindTask(DownloadManager.get().forMedia(m.id));
    }

    private void bindTask(DownloadTask t) {
        if (t == null) {
            ring.setState(RingButton.IDLE, 0);
            bar.setVisibility(GONE);
            quality.setVisibility(GONE);
            return;
        }
        quality.setVisibility(VISIBLE);
        quality.setText(t.audioOnly ? a.getString(R.string.audio_only_short) : t.quality);
        float p = (float) t.progress();
        switch (t.status) {
            case DownloadTask.RUNNING:
                ring.setState(t.live ? RingButton.RECORDING : RingButton.PROGRESS, p);
                showBar(p, true, t.live);
                break;
            case DownloadTask.MERGING:
                ring.setState(RingButton.QUEUED, 1);
                showBar((float) t.mergeProgress, true, false);
                bar.setColors(0xFF8B6CFF, 0xFF2BD9F0);
                break;
            case DownloadTask.QUEUED:
            case DownloadTask.WAITING_NET:
                ring.setState(RingButton.QUEUED, p);
                showBar(p, false, false);
                break;
            case DownloadTask.PAUSED:
                ring.setState(RingButton.PAUSED, p);
                showBar(p, false, false);
                bar.setColors(0xFFFFB020, 0xFFFF7A1A);
                break;
            case DownloadTask.FAILED:
                ring.setState(RingButton.FAILED, p);
                bar.setVisibility(GONE);
                break;
            case DownloadTask.DONE:
                ring.setState(RingButton.DONE, 1);
                bar.setVisibility(GONE);
                break;
            default:
                ring.setState(RingButton.IDLE, 0);
                bar.setVisibility(GONE);
        }
    }

    private void showBar(float p, boolean active, boolean indeterminate) {
        bar.setVisibility(VISIBLE);
        bar.setColors(0xFF62FF2B, 0xFF2BD9F0);
        bar.setState(active, indeterminate);
        bar.setProgress(p, true);
    }
}
