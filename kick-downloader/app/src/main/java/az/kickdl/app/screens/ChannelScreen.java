package az.kickdl.app.screens;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import az.kickdl.app.MainActivity;
import az.kickdl.app.R;
import az.kickdl.app.VerifyActivity;
import az.kickdl.app.data.Channel;
import az.kickdl.app.data.Follows;
import az.kickdl.app.data.Media;
import az.kickdl.app.data.Prefs;
import az.kickdl.app.dl.DownloadManager;
import az.kickdl.app.net.KickApi;
import az.kickdl.app.ui.Bg;
import az.kickdl.app.ui.Fmt;
import az.kickdl.app.ui.ImageLoader;
import az.kickdl.app.ui.Segmented;
import az.kickdl.app.ui.Sheet;
import az.kickdl.app.ui.Toaster;
import az.kickdl.app.ui.Ui;

/** Channel page: header with follow/live actions, VOD and clip lists. */
public class ChannelScreen extends Screen implements DownloadManager.Listener {
    private static final int TAB_VODS = 0, TAB_CLIPS = 1;
    private static final int SORT_NEW = 0, SORT_VIEWS = 1, SORT_LONG = 2;

    private final String slug;
    private Channel channel;
    private final List<Media> vods = new ArrayList<>();
    private final List<Media> clips = new ArrayList<>();
    private String clipCursor;
    private boolean clipsLoaded, clipsLoading, vodsLoaded, loadingVods;
    private int tab = TAB_VODS;
    private int vodSort = SORT_NEW;
    private Exception vodError, clipError;

    private FrameLayout root;
    private ListView list;
    private Adapter adapter;
    private LinearLayout header;
    private View topBar;
    private TextView topTitle;
    private FrameLayout banner;
    private ImageView bannerImg;
    private LinearLayout infoBox;
    private LinearLayout actions;
    private LinearLayout followBtn;
    private LinearLayout liveCard;
    private Segmented tabs;
    private LinearLayout sortRow;
    private LinearLayout stateBox;
    private int topInset;
    private int lastAnimated = -1;

    public ChannelScreen(MainActivity a, String slug, Channel prefetched) {
        super(a);
        this.slug = slug;
        this.channel = prefetched;
    }

    public String slug() { return slug; }

    @Override protected View build() {
        Context c = a;
        root = new FrameLayout(c);
        list = new ListView(c);
        list.setDivider(null);
        list.setSelector(new android.graphics.drawable.ColorDrawable(0));
        list.setCacheColorHint(0);
        list.setClipToPadding(false);
        list.setVerticalScrollBarEnabled(false);
        list.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        root.addView(list, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));

        header = Ui.col(c);
        buildHeader(c);
        list.addHeaderView(header, null, false);
        adapter = new Adapter();
        list.setAdapter(adapter);
        list.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override public void onScrollStateChanged(AbsListView v, int s) {}

            @Override public void onScroll(AbsListView v, int first, int visible, int total) {
                updateTopBar();
                if (tab == TAB_CLIPS && clipCursor != null && !clipsLoading && first + visible >= total - 3) loadClips(false);
            }
        });

        // floating top bar
        LinearLayout tb = Ui.row(c);
        tb.setPadding(Ui.dp(12), 0, Ui.dp(12), Ui.dp(8));
        ImageView back = Ui.iconButton(c, R.drawable.ic_back, 42, 22);
        back.setOnClickListener(v -> a.pop());
        tb.addView(back);
        topTitle = Ui.single(Ui.text(c, slug, 17, Ui.TEXT, Ui.BOLD));
        topTitle.setAlpha(0f);
        tb.addView(topTitle, Ui.margins(Ui.lpw(0, Ui.WRAP, 1), 12, 0, 12, 0));
        ImageView share = Ui.iconButton(c, R.drawable.ic_share, 42, 20);
        share.setOnClickListener(v -> MediaActions.shareText(a, slug, "https://kick.com/" + slug));
        tb.addView(share);
        ImageView more = Ui.iconButton(c, R.drawable.ic_more, 42, 22);
        more.setOnClickListener(v -> moreMenu());
        tb.addView(more, Ui.margins(Ui.lp(Ui.dp(42), Ui.dp(42)), 8, 0, 0, 0));
        topBar = tb;
        root.addView(tb, new FrameLayout.LayoutParams(Ui.MATCH, Ui.WRAP, Gravity.TOP));

        if (channel != null) bindChannel();
        load();
        return root;
    }

    private void buildHeader(Context c) {
        banner = new FrameLayout(c);
        bannerImg = new ImageView(c);
        bannerImg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        banner.addView(bannerImg, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        View fade = new View(c);
        fade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x66000000, 0x22000000, 0xFF07080C}));
        banner.addView(fade, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        header.addView(banner, new LinearLayout.LayoutParams(Ui.MATCH, Ui.dp(190)));

        infoBox = Ui.col(c);
        infoBox.setPadding(Ui.dp(18), 0, Ui.dp(18), 0);
        header.addView(infoBox, Ui.margins(Ui.lp(Ui.MATCH, Ui.WRAP), 0, -56, 0, 0));

        actions = Ui.row(c);
        actions.setPadding(Ui.dp(18), 0, Ui.dp(18), 0);
        header.addView(actions, Ui.margins(Ui.lp(Ui.MATCH, Ui.WRAP), 0, 16, 0, 0));

        liveCard = Ui.col(c);
        liveCard.setVisibility(View.GONE);
        header.addView(liveCard, Ui.margins(Ui.lp(Ui.MATCH, Ui.WRAP), 18, 16, 18, 0));

        tabs = new Segmented(c, a.getString(R.string.tab_videos), a.getString(R.string.tab_clips));
        tabs.setListener(i -> {
            tab = i;
            lastAnimated = -1;
            updateSortRow();
            if (tab == TAB_CLIPS && !clipsLoaded && !clipsLoading) loadClips(true);
            refreshState();
            adapter.notifyDataSetChanged();
        });
        header.addView(tabs, Ui.margins(Ui.lp(Ui.MATCH, Ui.WRAP), 18, 20, 18, 0));

        sortRow = Ui.row(c);
        sortRow.setPadding(Ui.dp(18), 0, Ui.dp(18), 0);
        header.addView(sortRow, Ui.margins(Ui.lp(Ui.MATCH, Ui.WRAP), 0, 12, 0, 4));
        updateSortRow();

        stateBox = Ui.col(c);
        stateBox.setPadding(Ui.dp(18), Ui.dp(8), Ui.dp(18), 0);
        header.addView(stateBox, Ui.lp(Ui.MATCH, Ui.WRAP));
    }

    private void updateSortRow() {
        sortRow.removeAllViews();
        Context c = a;
        if (tab == TAB_VODS) {
            String[] labels = {a.getString(R.string.sort_new), a.getString(R.string.sort_views), a.getString(R.string.sort_long)};
            for (int i = 0; i < labels.length; i++) {
                final int idx = i;
                sortRow.addView(sortChip(c, labels[i], vodSort == i, () -> {
                    vodSort = idx;
                    sortVods();
                    updateSortRow();
                    lastAnimated = -1;
                    adapter.notifyDataSetChanged();
                }), Ui.margins(Ui.lp(Ui.WRAP, Ui.WRAP), 0, 0, 8, 0));
            }
        } else {
            boolean byViews = !"date".equals(Prefs.clipSort());
            sortRow.addView(sortChip(c, a.getString(R.string.sort_popular), byViews, () -> setClipSort("view")),
                    Ui.margins(Ui.lp(Ui.WRAP, Ui.WRAP), 0, 0, 8, 0));
            sortRow.addView(sortChip(c, a.getString(R.string.sort_new), !byViews, () -> setClipSort("date")),
                    Ui.margins(Ui.lp(Ui.WRAP, Ui.WRAP), 0, 0, 8, 0));
        }
    }

    private void setClipSort(String s) {
        if (s.equals(Prefs.clipSort())) return;
        Prefs.clipSort(s);
        updateSortRow();
        loadClips(true);
    }

    private static View sortChip(Context c, String label, boolean on, final Runnable click) {
        TextView t = Ui.text(c, label, 13, on ? Ui.ON_ACCENT : Ui.TEXT2, on ? Ui.BOLD : Ui.MEDIUM);
        t.setPadding(Ui.dp(14), Ui.dp(8), Ui.dp(14), Ui.dp(8));
        t.setBackground(Ui.ripple(on ? Ui.solid(Ui.ACCENT, 16) : Ui.glass(16), 16));
        t.setOnClickListener(v -> {
            Ui.tick(v);
            click.run();
        });
        return t;
    }

    private void bindChannel() {
        Context c = a;
        Channel ch = channel;
        topTitle.setText(ch.displayName());
        ImageLoader.load(bannerImg, ch.banner != null ? ch.banner : ch.liveThumb != null ? ch.liveThumb : ch.avatar, Ui.dp(400));
        if (ch.banner == null && ch.liveThumb == null) bannerImg.setAlpha(0.35f);

        infoBox.removeAllViews();
        LinearLayout row = Ui.row(c);
        row.setGravity(Gravity.BOTTOM);
        FrameLayout av = Views.avatar(c, ch.avatar, 96, ch.live, ch.displayName());
        row.addView(av);
        if (ch.live) {
            TextView lb = Views.liveBadge(c);
            row.addView(lb, Ui.margins(Ui.lp(Ui.WRAP, Ui.WRAP), -34, 0, 0, 4));
            Ui.pulse(lb, 0.55f, 900);
        }
        infoBox.addView(row);
        LinearLayout nameRow = Ui.row(c);
        TextView name = Ui.single(Ui.text(c, ch.displayName(), 26, Ui.TEXT, Ui.BOLD));
        name.setLetterSpacing(-0.02f);
        nameRow.addView(name, Ui.lp(Ui.WRAP, Ui.WRAP));
        if (ch.verified) nameRow.addView(Ui.icon(c, R.drawable.ic_verified, 20, Ui.ACCENT), Ui.margins(Ui.lp(Ui.dp(20), Ui.dp(20)), 8, 0, 0, 0));
        infoBox.addView(nameRow, Ui.margins(Ui.lp(Ui.MATCH, Ui.WRAP), 0, 12, 0, 0));
        StringBuilder stats = new StringBuilder();
        if (ch.followers >= 0) stats.append(a.getString(R.string.followers_fmt, Fmt.count(ch.followers)));
        if (vodsLoaded) {
            if (stats.length() > 0) stats.append(" · ");
            stats.append(a.getResources().getQuantityString(R.plurals.videos_count, vods.size(), vods.size()));
        }
        TextView st = Ui.text(c, stats.toString(), 13, Ui.TEXT2, Ui.MEDIUM);
        infoBox.addView(st, Ui.margins(Ui.lp(Ui.MATCH, Ui.WRAP), 0, 6, 0, 0));
        if (ch.bio != null && !ch.bio.trim().isEmpty()) {
            final TextView bio = Ui.lines(Ui.text(c, ch.bio.trim(), 13, Ui.TEXT2, Ui.REGULAR), 2);
            bio.setOnClickListener(v -> bio.setMaxLines(bio.getMaxLines() == 2 ? 30 : 2));
            infoBox.addView(bio, Ui.margins(Ui.lp(Ui.MATCH, Ui.WRAP), 0, 10, 0, 0));
        }

        actions.removeAllViews();
        followBtn = Ui.pill(c, 0, "", true);
        bindFollow(false);
        followBtn.setOnClickListener(v -> {
            Ui.haptic(v);
            boolean now = Follows.toggle(channel);
            if (now && vodsLoaded) Follows.update(channel, vods.size(), latestVod(), true);
            bindFollow(true);
            Toaster.show(a, now ? R.drawable.ic_heart : R.drawable.ic_heart_outline,
                    a.getString(now ? R.string.followed_toast : R.string.unfollowed_toast, channel.displayName()));
        });
        actions.addView(followBtn, Ui.lpw(0, Ui.dp(46), 1));
        if (ch.live && ch.playbackUrl != null) {
            LinearLayout watch = Ui.pill(c, R.drawable.ic_play, a.getString(R.string.watch_live), false);
            watch.setOnClickListener(v -> MediaActions.stream(a, MediaActions.liveMedia(channel), channel.playbackUrl));
            actions.addView(watch, Ui.margins(Ui.lpw(0, Ui.dp(46), 1), 10, 0, 0, 0));
            LinearLayout rec = Ui.pill(c, R.drawable.ic_rec, null, false);
            ((ImageView) rec.getChildAt(0)).setImageTintList(android.content.res.ColorStateList.valueOf(Ui.LIVE));
            rec.setOnClickListener(v -> MediaActions.recordLive(a, channel));
            actions.addView(rec, Ui.margins(Ui.lp(Ui.dp(54), Ui.dp(46)), 10, 0, 0, 0));
        }
        bindLiveCard();
    }

    private void bindLiveCard() {
        final Channel ch = channel;
        liveCard.removeAllViews();
        if (!ch.live) {
            liveCard.setVisibility(View.GONE);
            return;
        }
        Context c = a;
        liveCard.setVisibility(View.VISIBLE);
        liveCard.setBackground(Ui.stroke(0x14FF3B5C, 0x40FF3B5C, 20));
        liveCard.setPadding(Ui.dp(14), Ui.dp(14), Ui.dp(14), Ui.dp(14));
        LinearLayout top = Ui.row(c);
        View dot = new View(c);
        dot.setBackground(Ui.oval(Ui.LIVE));
        top.addView(dot, Ui.margins(Ui.lp(Ui.dp(8), Ui.dp(8)), 0, 0, 8, 0));
        Ui.pulse(dot, 0.3f, 700);
        String head = a.getString(R.string.live_now) + (ch.liveViewers > 0 ? " · " + a.getString(R.string.viewers_fmt, Fmt.count(ch.liveViewers)) : "");
        if (ch.liveStartedAt > 0) head += " · " + Fmt.durationMs(System.currentTimeMillis() - ch.liveStartedAt);
        top.addView(Ui.text(c, head, 12, Ui.LIVE, Ui.BOLD));
        liveCard.addView(top);
        if (ch.liveTitle != null) liveCard.addView(Ui.lines(Ui.text(c, ch.liveTitle, 14, Ui.TEXT, Ui.MEDIUM), 2), Ui.margins(Ui.lp(Ui.MATCH, Ui.WRAP), 0, 8, 0, 0));
        if (ch.liveCategory != null) liveCard.addView(Ui.text(c, ch.liveCategory, 12, Ui.TEXT2, Ui.REGULAR), Ui.margins(Ui.lp(Ui.MATCH, Ui.WRAP), 0, 4, 0, 0));
        liveCard.setOnClickListener(v -> MediaActions.stream(a, MediaActions.liveMedia(channel), channel.playbackUrl));
        Ui.fadeIn(liveCard, 120);
    }

    private void bindFollow(boolean animate) {
        boolean f = Follows.isFollowed(slug);
        followBtn.setBackground(Ui.ripple(f ? Ui.glass(23) : Ui.accentGradient(23), 23));
        followBtn.removeAllViews();
        ImageView iv = Ui.icon(a, f ? R.drawable.ic_heart : R.drawable.ic_heart_outline, 18, f ? Ui.LIVE : Ui.ON_ACCENT);
        followBtn.addView(iv, Ui.margins(Ui.lp(Ui.dp(18), Ui.dp(18)), 0, 0, 8, 0));
        followBtn.addView(Ui.text(a, a.getString(f ? R.string.following : R.string.follow), 14, f ? Ui.TEXT : Ui.ON_ACCENT, Ui.BOLD));
        if (animate) Ui.bounce(iv);
    }

    private long latestVod() {
        long t = 0;
        for (Media m : vods) t = Math.max(t, m.createdAt);
        return t;
    }

    private void moreMenu() {
        Sheet s = new Sheet(a).title(channel != null ? channel.displayName() : slug, "kick.com/" + slug);
        s.item(R.drawable.ic_refresh, a.getString(R.string.refresh), this::reload);
        s.item(R.drawable.ic_open, a.getString(R.string.open_in_browser), () -> {
            try { a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://kick.com/" + slug))); } catch (Exception ignored) {}
        });
        s.item(R.drawable.ic_link, a.getString(R.string.copy_link), () -> MediaActions.copy(a, slug, "https://kick.com/" + slug));
        if (vodsLoaded && !vods.isEmpty()) {
            s.item(R.drawable.ic_download, a.getString(R.string.download_latest), () -> MediaActions.downloadTapped(a, newest()));
        }
        s.show();
    }

    private Media newest() {
        Media best = vods.get(0);
        for (Media m : vods) if (m.createdAt > best.createdAt) best = m;
        return best;
    }

    // ------------------------------------------------------------------ loading

    private void reload() {
        vodsLoaded = false;
        clipsLoaded = false;
        vods.clear();
        clips.clear();
        clipCursor = null;
        load();
        if (tab == TAB_CLIPS) loadClips(true);
    }

    private void load() {
        loadingVods = true;
        vodError = null;
        refreshState();
        Bg.run(() -> {
            Channel ch = KickApi.channel(slug);
            List<Media> v = KickApi.videos(ch);
            return new Object[]{ch, v};
        }, r -> {
            loadingVods = false;
            channel = (Channel) r[0];
            @SuppressWarnings("unchecked") List<Media> v = (List<Media>) r[1];
            vods.clear();
            vods.addAll(v);
            vodsLoaded = true;
            sortVods();
            Follows.update(channel, vods.size(), latestVod(), true);
            if (isBuilt()) {
                bindChannel();
                refreshState();
                lastAnimated = -1;
                adapter.notifyDataSetChanged();
            }
        }, e -> {
            loadingVods = false;
            vodError = e;
            if (isBuilt()) refreshState();
        });
    }

    private void loadClips(final boolean reset) {
        if (channel == null) {
            Bg.later(() -> { if (channel != null) loadClips(reset); }, 600);
            return;
        }
        clipsLoading = true;
        clipError = null;
        if (reset) {
            clips.clear();
            clipCursor = null;
            adapter.notifyDataSetChanged();
        }
        refreshState();
        final String cursor = reset ? null : clipCursor;
        final String sort = "date".equals(Prefs.clipSort()) ? "date" : "view";
        Bg.run(() -> KickApi.clips(channel, sort, "all", cursor), page -> {
            clipsLoading = false;
            clipsLoaded = true;
            clips.addAll(page.items);
            clipCursor = page.items.isEmpty() ? null : page.nextCursor;
            refreshState();
            adapter.notifyDataSetChanged();
        }, e -> {
            clipsLoading = false;
            clipError = e;
            refreshState();
        });
    }

    private void sortVods() {
        Collections.sort(vods, (x, y) -> {
            switch (vodSort) {
                case SORT_VIEWS: return Long.compare(y.views, x.views);
                case SORT_LONG: return Double.compare(y.durationSec, x.durationSec);
                default: return Long.compare(y.createdAt, x.createdAt);
            }
        });
    }

    private void refreshState() {
        if (stateBox == null) return;
        stateBox.removeAllViews();
        Context c = a;
        boolean loading = tab == TAB_VODS ? loadingVods && vods.isEmpty() : clipsLoading && clips.isEmpty();
        Exception err = tab == TAB_VODS ? vodError : clipError;
        List<Media> items = tab == TAB_VODS ? vods : clips;
        if (loading) {
            for (int i = 0; i < 2; i++) stateBox.addView(Views.shimmerCard(c));
        } else if (err != null && items.isEmpty()) {
            final boolean ch = Errors.isChallenge(err);
            stateBox.addView(Views.empty(c, R.drawable.ic_error, a.getString(err instanceof KickApi.NotFoundException
                            ? R.string.channel_not_found : R.string.load_failed), Errors.message(c, err),
                    a.getString(ch ? R.string.verify : R.string.retry), v -> {
                        if (ch) VerifyActivity.open(a);
                        else if (tab == TAB_VODS) load();
                        else loadClips(true);
                    }));
        } else if (tab == TAB_VODS && vodsLoaded && vods.isEmpty()) {
            stateBox.addView(Views.empty(c, R.drawable.ic_library, a.getString(R.string.no_videos), a.getString(R.string.no_videos_hint), null, null));
        } else if (tab == TAB_CLIPS && clipsLoaded && clips.isEmpty()) {
            stateBox.addView(Views.empty(c, R.drawable.ic_movie, a.getString(R.string.no_clips), null, null, null));
        } else if (tab == TAB_CLIPS && clipsLoading) {
            // paging indicator lives in footer via shimmer
            stateBox.addView(Ui.space(c, 1, 1));
        }
    }

    private void updateTopBar() {
        if (topBar == null || header == null) return;
        int y = -header.getTop();
        if (list.getFirstVisiblePosition() > 0) y = Ui.dp(400);
        float f = Math.max(0, Math.min(1, (y - Ui.dp(120)) / (float) Ui.dp(60)));
        topTitle.setAlpha(f);
        if (f > 0) {
            if (!(topBar.getBackground() instanceof GradientDrawable)) {
                GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xF207080C, 0xD907080C});
                topBar.setBackground(g);
            }
            topBar.getBackground().setAlpha((int) (255 * f));
        } else {
            topBar.setBackground(null);
        }
    }

    // ------------------------------------------------------------------ lifecycle

    @Override public void onInsets(int top, int bottom) {
        topInset = top;
        if (topBar != null) topBar.setPadding(Ui.dp(12), top + Ui.dp(8), Ui.dp(12), Ui.dp(8));
        if (list != null) list.setPadding(0, 0, 0, bottom + Ui.dp(12));
        if (banner != null) {
            banner.getLayoutParams().height = Ui.dp(170) + top;
            banner.requestLayout();
        }
    }

    @Override public void onShow() {
        DownloadManager.get().addListener(this);
        if (adapter != null) adapter.notifyDataSetChanged();
        if (followBtn != null) bindFollow(false);
    }

    @Override public void onHide() { DownloadManager.get().removeListener(this); }

    @Override public void onDestroy() { DownloadManager.get().removeListener(this); }

    @Override public void onDownloadsChanged() {
        // Rebind only the visible cards: cheap and keeps animations smooth.
        if (list == null) return;
        for (int i = 0; i < list.getChildCount(); i++) {
            View v = list.getChildAt(i);
            if (v instanceof MediaCard) {
                int pos = list.getPositionForView(v) - list.getHeaderViewsCount();
                List<Media> items = current();
                if (pos >= 0 && pos < items.size()) ((MediaCard) v).bind(items.get(pos), thumbHeight());
            }
        }
    }

    @Override public void scrollToTop() { if (list != null) list.smoothScrollToPosition(0); }

    private List<Media> current() { return tab == TAB_VODS ? vods : clips; }

    private int thumbHeight() {
        int w = a.getResources().getDisplayMetrics().widthPixels - Ui.dp(36);
        return Math.min(w * 9 / 16, Ui.dp(260));
    }

    private class Adapter extends BaseAdapter {
        @Override public int getCount() { return current().size(); }

        @Override public Object getItem(int p) { return current().get(p); }

        @Override public long getItemId(int p) { return p; }

        @Override public View getView(int p, View convert, ViewGroup parent) {
            MediaCard card = convert instanceof MediaCard ? (MediaCard) convert : new MediaCard(a);
            card.setPadding(Ui.dp(18), Ui.dp(6), Ui.dp(18), Ui.dp(14));
            card.bind(current().get(p), thumbHeight());
            if (p > lastAnimated) {
                lastAnimated = p;
                Ui.fadeIn(card, Math.min(p, 4) * 50L);
            }
            return card;
        }
    }
}
