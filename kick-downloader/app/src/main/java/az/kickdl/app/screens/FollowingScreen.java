package az.kickdl.app.screens;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import az.kickdl.app.MainActivity;
import az.kickdl.app.R;
import az.kickdl.app.data.Channel;
import az.kickdl.app.data.Follows;
import az.kickdl.app.data.Media;
import az.kickdl.app.net.CfBridge;
import az.kickdl.app.net.KickApi;
import az.kickdl.app.ui.Bg;
import az.kickdl.app.ui.Fmt;
import az.kickdl.app.ui.Sheet;
import az.kickdl.app.ui.Toaster;
import az.kickdl.app.ui.Ui;

/** Locally followed channels with live status and "new videos" badges. */
public class FollowingScreen extends Screen implements Follows.Listener {
    private FrameLayout root;
    private LinearLayout header;
    private GridView grid;
    private TextView subtitle;
    private ImageView refreshBtn;
    private ObjectAnimator spin;
    private final List<Follows.Item> items = new ArrayList<>();
    private final Adapter adapter = new Adapter();
    private boolean refreshing;
    private int lastAnimated = -1;
    private FrameLayout emptyHolder;

    public FollowingScreen(MainActivity a) { super(a); }

    @Override protected View build() {
        Context c = a;
        root = new FrameLayout(c);
        grid = new GridView(c);
        grid.setNumColumns(2);
        grid.setHorizontalSpacing(Ui.dp(10));
        grid.setVerticalSpacing(Ui.dp(10));
        grid.setSelector(new android.graphics.drawable.ColorDrawable(0));
        grid.setClipToPadding(false);
        grid.setVerticalScrollBarEnabled(false);
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setAdapter(adapter);
        root.addView(grid, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));

        emptyHolder = new FrameLayout(c);
        root.addView(emptyHolder, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));

        header = Ui.col(c);
        header.setPadding(Ui.dp(18), 0, Ui.dp(18), Ui.dp(12));
        header.setBackground(new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xF507080C, 0xD907080C, 0x0007080C}));
        LinearLayout tr = Ui.row(c);
        LinearLayout tc = Ui.col(c);
        tc.addView(Ui.title(c, a.getString(R.string.tab_following)));
        subtitle = Ui.text(c, "", 13, Ui.TEXT2, Ui.MEDIUM);
        tc.addView(subtitle, Ui.margins(Ui.lp(Ui.WRAP, Ui.WRAP), 0, 4, 0, 0));
        tr.addView(tc, Ui.lpw(0, Ui.WRAP, 1));
        refreshBtn = Ui.iconButton(c, R.drawable.ic_refresh, 44, 22);
        refreshBtn.setOnClickListener(v -> refresh(true));
        tr.addView(refreshBtn);
        header.addView(tr, Ui.margins(Ui.lp(Ui.MATCH, Ui.WRAP), 0, 8, 0, 0));
        root.addView(header, new FrameLayout.LayoutParams(Ui.MATCH, Ui.WRAP, Gravity.TOP));

        Follows.addListener(this);
        reloadItems();
        return root;
    }

    private void reloadItems() {
        items.clear();
        items.addAll(Follows.all());
        // live first, then channels with new videos, then the user's order
        final List<Follows.Item> order = Follows.all();
        Collections.sort(items, (x, y) -> {
            if (x.live != y.live) return x.live ? -1 : 1;
            boolean nx = x.newVods() > 0, ny = y.newVods() > 0;
            if (nx != ny) return nx ? -1 : 1;
            return order.indexOf(x) - order.indexOf(y);
        });
        adapter.notifyDataSetChanged();
        int live = 0;
        for (Follows.Item i : items) if (i.live) live++;
        subtitle.setText(items.isEmpty() ? a.getString(R.string.following_empty_sub)
                : a.getResources().getQuantityString(R.plurals.channels_count, items.size(), items.size())
                + (live > 0 ? " · " + a.getString(R.string.live_count, live) : ""));
        emptyHolder.removeAllViews();
        if (items.isEmpty()) {
            LinearLayout e = Views.empty(a, R.drawable.ic_heart_outline, a.getString(R.string.following_empty),
                    a.getString(R.string.following_empty_hint), a.getString(R.string.find_channel), v -> a.switchTab(MainActivity.TAB_HOME));
            emptyHolder.addView(e, new FrameLayout.LayoutParams(Ui.MATCH, Ui.WRAP, Gravity.CENTER));
        }
    }

    /** Checks live status and video counts for every followed channel. */
    private void refresh(final boolean manual) {
        if (refreshing) return;
        final List<Follows.Item> all = Follows.all();
        if (all.isEmpty()) return;
        refreshing = true;
        spin = ObjectAnimator.ofFloat(refreshBtn, View.ROTATION, 0f, 360f);
        spin.setDuration(800);
        spin.setRepeatCount(ValueAnimator.INFINITE);
        spin.setInterpolator(new LinearInterpolator());
        spin.start();
        final AtomicInteger left = new AtomicInteger(all.size());
        final AtomicInteger challenged = new AtomicInteger();
        final ExecutorService pool = Executors.newFixedThreadPool(3);
        for (final Follows.Item it : all) {
            pool.execute(() -> {
                try {
                    Channel ch = KickApi.channel(it.slug);
                    int count = -1;
                    long last = 0;
                    try {
                        List<Media> vods = KickApi.videos(ch);
                        count = vods.size();
                        for (Media m : vods) last = Math.max(last, m.createdAt);
                    } catch (Exception ignored) {}
                    Follows.update(ch, count, last, false);
                } catch (CfBridge.ChallengeException e) {
                    challenged.incrementAndGet();
                } catch (Exception ignored) {
                }
                if (left.decrementAndGet() == 0) {
                    pool.shutdown();
                    Bg.main(() -> {
                        refreshing = false;
                        if (spin != null) spin.cancel();
                        refreshBtn.animate().rotation(0).setDuration(200).start();
                        if (challenged.get() > 0) {
                            Toaster.show(a, R.drawable.ic_error, a.getString(R.string.err_challenge), a.getString(R.string.verify),
                                    () -> az.kickdl.app.VerifyActivity.open(a));
                        } else if (manual) {
                            Toaster.show(a, R.drawable.ic_check, a.getString(R.string.updated));
                        }
                    });
                }
            });
        }
    }

    private void itemMenu(final Follows.Item it) {
        new Sheet(a).title(it.name, "kick.com/" + it.slug)
                .item(R.drawable.ic_people, a.getString(R.string.open_channel), () -> a.openChannel(it.slug, null))
                .item(R.drawable.ic_open, a.getString(R.string.open_in_browser), () -> {
                    try { a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://kick.com/" + it.slug))); } catch (Exception ignored) {}
                })
                .item(R.drawable.ic_heart_outline, a.getString(R.string.unfollow), Ui.LIVE, () -> {
                    Follows.unfollow(it.slug);
                    Toaster.show(a, R.drawable.ic_heart_outline, a.getString(R.string.unfollowed_toast, it.name),
                            a.getString(R.string.undo), () -> {
                                Channel c = new Channel();
                                c.slug = it.slug;
                                c.name = it.name;
                                c.avatar = it.avatar;
                                c.followers = it.followers;
                                c.verified = it.verified;
                                Follows.follow(c);
                            });
                })
                .show();
    }

    @Override public void onInsets(int top, int bottom) {
        if (header == null) return;
        header.setPadding(Ui.dp(18), top + Ui.dp(8), Ui.dp(18), Ui.dp(12));
        header.post(() -> grid.setPadding(Ui.dp(18), header.getHeight(), Ui.dp(18), bottom + Ui.dp(16)));
    }

    @Override public void onShow() {
        if (grid == null) return;
        reloadItems();
        long oldest = Long.MAX_VALUE;
        for (Follows.Item i : items) oldest = Math.min(oldest, i.checkedAt);
        if (!items.isEmpty() && System.currentTimeMillis() - oldest > 10 * 60_000L) refresh(false);
    }

    @Override public void onDestroy() { Follows.removeListener(this); }

    @Override public void onFollowsChanged() { if (grid != null) reloadItems(); }

    @Override public void scrollToTop() { if (grid != null) grid.smoothScrollToPosition(0); }

    private class Adapter extends BaseAdapter {
        @Override public int getCount() { return items.size(); }

        @Override public Object getItem(int p) { return items.get(p); }

        @Override public long getItemId(int p) { return items.get(p).slug.hashCode(); }

        @Override public View getView(int p, View convert, ViewGroup parent) {
            final Follows.Item it = items.get(p);
            Context c = a;
            LinearLayout card = Ui.col(c);
            card.setGravity(Gravity.CENTER_HORIZONTAL);
            card.setPadding(Ui.dp(12), Ui.dp(18), Ui.dp(12), Ui.dp(16));
            card.setBackground(Ui.ripple(it.live ? Ui.stroke(0x12FF3B5C, 0x55FF3B5C, 22) : Ui.glass(22), 22));
            FrameLayout av = Views.avatar(c, it.avatar, 72, it.live, it.name);
            card.addView(av);
            LinearLayout nr = Ui.row(c);
            nr.setGravity(Gravity.CENTER);
            nr.addView(Ui.single(Ui.text(c, it.name, 15, Ui.TEXT, Ui.BOLD)), Ui.lp(Ui.WRAP, Ui.WRAP));
            if (it.verified) nr.addView(Ui.icon(c, R.drawable.ic_verified, 14, Ui.ACCENT), Ui.margins(Ui.lp(Ui.dp(14), Ui.dp(14)), 4, 0, 0, 0));
            card.addView(nr, Ui.margins(Ui.lp(Ui.MATCH, Ui.WRAP), 0, 12, 0, 0));
            String sub = it.followers >= 0 ? a.getString(R.string.followers_fmt, Fmt.count(it.followers)) : "kick.com/" + it.slug;
            TextView st = Ui.single(Ui.text(c, sub, 12, Ui.TEXT3, Ui.REGULAR));
            st.setGravity(Gravity.CENTER);
            card.addView(st, Ui.margins(Ui.lp(Ui.MATCH, Ui.WRAP), 0, 4, 0, 0));
            LinearLayout badges = Ui.row(c);
            badges.setGravity(Gravity.CENTER);
            if (it.live) {
                TextView lb = Views.liveBadge(c);
                badges.addView(lb);
                Ui.pulse(lb, 0.6f, 900);
            }
            if (it.newVods() > 0) {
                TextView nb = Ui.chip(c, a.getString(R.string.new_videos, it.newVods()), Ui.ACCENT, Ui.ON_ACCENT);
                nb.setTypeface(Ui.BOLD);
                badges.addView(nb, Ui.margins(Ui.lp(Ui.WRAP, Ui.WRAP), it.live ? 6 : 0, 0, 0, 0));
            }
            if (badges.getChildCount() == 0 && it.lastVodAt > 0) {
                badges.addView(Ui.text(c, a.getString(R.string.last_video, Fmt.ago(c, it.lastVodAt)), 11, Ui.TEXT3, Ui.REGULAR));
            }
            card.addView(badges, Ui.margins(Ui.lp(Ui.MATCH, Ui.dp(22)), 0, 10, 0, 0));
            if (it.live && it.liveTitle != null) {
                TextView lt = Ui.lines(Ui.text(c, it.liveTitle, 11, Ui.TEXT2, Ui.REGULAR), 2);
                lt.setGravity(Gravity.CENTER);
                card.addView(lt, Ui.margins(Ui.lp(Ui.MATCH, Ui.WRAP), 0, 6, 0, 0));
            }
            card.setOnClickListener(v -> a.openChannel(it.slug, null));
            card.setOnLongClickListener(v -> {
                Ui.haptic(v);
                itemMenu(it);
                return true;
            });
            Ui.pressable(card);
            if (p > lastAnimated) {
                lastAnimated = p;
                Ui.fadeIn(card, Math.min(p, 8) * 40L);
            }
            return card;
        }
    }
}
