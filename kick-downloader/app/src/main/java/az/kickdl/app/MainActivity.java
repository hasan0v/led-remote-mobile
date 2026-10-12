package az.kickdl.app;

import android.Manifest;
import android.animation.LayoutTransition;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import az.kickdl.app.data.Media;
import az.kickdl.app.data.Prefs;
import az.kickdl.app.dl.DownloadManager;
import az.kickdl.app.dl.DownloadTask;
import az.kickdl.app.net.CfBridge;
import az.kickdl.app.net.KickApi;
import az.kickdl.app.screens.ChannelScreen;
import az.kickdl.app.screens.DownloadsScreen;
import az.kickdl.app.screens.FollowingScreen;
import az.kickdl.app.screens.HomeScreen;
import az.kickdl.app.screens.MediaActions;
import az.kickdl.app.screens.Screen;
import az.kickdl.app.screens.SettingsScreen;
import az.kickdl.app.ui.Bg;
import az.kickdl.app.ui.GlassBackground;
import az.kickdl.app.ui.Sheet;
import az.kickdl.app.ui.Toaster;
import az.kickdl.app.ui.Ui;

public class MainActivity extends Activity implements DownloadManager.Listener {
    public static final String EXTRA_TAB = "tab";
    public static final int TAB_HOME = 0, TAB_FOLLOWING = 1, TAB_DOWNLOADS = 2, TAB_SETTINGS = 3;

    private FrameLayout root;
    private GlassBackground bg;
    private FrameLayout content;
    private LinearLayout nav;
    private final Screen[] tabs = new Screen[4];
    private final List<Screen> stack = new ArrayList<>();
    private int tab = TAB_HOME;
    private final NavItem[] navItems = new NavItem[4];
    private int insetTop, insetBottom;
    private boolean keyboard;
    private boolean askedNotif, askedStorage;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        if (Build.VERSION.SDK_INT >= 29) getWindow().setNavigationBarContrastEnforced(false);

        root = new FrameLayout(this);
        bg = new GlassBackground(this);
        root.addView(bg, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        content = new FrameLayout(this);
        root.addView(content, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        buildNav();
        setContentView(root);

        root.setOnApplyWindowInsetsListener((v, insets) -> {
            onInsets(insets);
            return insets.consumeSystemWindowInsets();
        });

        CfBridge.get(this).attach(root);
        showTab(TAB_HOME, false);
        handleIntent(getIntent());
        DownloadManager.get().addListener(this);
        updateBadge();
    }

    // ------------------------------------------------------------------ insets

    private void onInsets(WindowInsets wi) {
        insetTop = wi.getSystemWindowInsetTop();
        int bottom = wi.getSystemWindowInsetBottom();
        boolean kb = bottom > Ui.dp(140);
        keyboard = kb;
        insetBottom = kb ? 0 : bottom;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) nav.getLayoutParams();
        lp.bottomMargin = Ui.dp(12) + insetBottom;
        lp.leftMargin = Ui.dp(14) + wi.getSystemWindowInsetLeft();
        lp.rightMargin = Ui.dp(14) + wi.getSystemWindowInsetRight();
        nav.setLayoutParams(lp);
        nav.animate().translationY(kb ? Ui.dp(140) : 0).alpha(kb ? 0f : 1f).setDuration(220).start();
        content.setPadding(wi.getSystemWindowInsetLeft(), 0, wi.getSystemWindowInsetRight(), kb ? bottom : 0);
        dispatchInsets();
    }

    private int bottomArea() { return keyboard ? Ui.dp(8) : Ui.dp(64 + 12 + 8) + insetBottom; }

    private void dispatchInsets() {
        for (Screen s : tabs) if (s != null && s.isBuilt()) s.onInsets(insetTop, bottomArea());
        for (Screen s : stack) if (s.isBuilt()) s.onInsets(insetTop, bottomArea());
    }

    // ------------------------------------------------------------------ navigation bar

    private static class NavItem {
        LinearLayout box;
        ImageView icon;
        TextView label;
        TextView badge;
    }

    private void buildNav() {
        nav = Ui.row(this);
        nav.setBackground(Ui.glassStrong(32));
        nav.setElevation(Ui.dp(18));
        nav.setPadding(Ui.dp(6), Ui.dp(6), Ui.dp(6), Ui.dp(6));
        LayoutTransition lt = new LayoutTransition();
        lt.enableTransitionType(LayoutTransition.CHANGING);
        lt.setDuration(260);
        lt.setInterpolator(LayoutTransition.CHANGING, new PathInterpolator(0.3f, 1.25f, 0.4f, 1f));
        nav.setLayoutTransition(lt);
        int[] icons = {R.drawable.ic_home, R.drawable.ic_heart, R.drawable.ic_download, R.drawable.ic_tune};
        int[] labels = {R.string.tab_home, R.string.tab_following, R.string.tab_downloads, R.string.tab_settings};
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            NavItem it = new NavItem();
            it.box = Ui.row(this);
            it.box.setGravity(Gravity.CENTER);
            it.box.setPadding(Ui.dp(14), 0, Ui.dp(14), 0);
            LayoutTransition ilt = new LayoutTransition();
            ilt.enableTransitionType(LayoutTransition.CHANGING);
            ilt.setDuration(220);
            it.box.setLayoutTransition(ilt);
            FrameLayout iconBox = new FrameLayout(this);
            it.icon = Ui.icon(this, icons[i], 22, Ui.TEXT2);
            iconBox.addView(it.icon, new FrameLayout.LayoutParams(Ui.dp(22), Ui.dp(22), Gravity.CENTER));
            it.badge = Ui.chip(this, "", Ui.LIVE, 0xFFFFFFFF);
            it.badge.setTextSize(9);
            it.badge.setPadding(Ui.dp(4), Ui.dp(1), Ui.dp(4), Ui.dp(1));
            it.badge.setVisibility(View.GONE);
            FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(Ui.WRAP, Ui.WRAP, Gravity.TOP | Gravity.END);
            bl.setMargins(0, -Ui.dp(4), -Ui.dp(8), 0);
            iconBox.addView(it.badge, bl);
            iconBox.setClipChildren(false);
            it.box.setClipChildren(false);
            it.box.addView(iconBox, Ui.lp(Ui.dp(26), Ui.dp(26)));
            it.label = Ui.text(this, getString(labels[i]), 13, Ui.ON_ACCENT, Ui.BOLD);
            it.label.setSingleLine(true);
            it.label.setVisibility(View.GONE);
            it.box.addView(it.label, Ui.margins(Ui.lp(Ui.WRAP, Ui.WRAP), 8, 0, 0, 0));
            it.box.setOnClickListener(v -> {
                Ui.tick(v);
                if (idx == tab && stack.isEmpty()) {
                    tabs[idx].scrollToTop();
                } else {
                    switchTab(idx);
                }
            });
            Ui.pressable(it.box);
            navItems[i] = it;
            nav.addView(it.box, new LinearLayout.LayoutParams(0, Ui.dp(52), 1));
        }
        nav.setClipChildren(false);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Ui.MATCH, Ui.dp(64), Gravity.BOTTOM);
        lp.setMargins(Ui.dp(14), 0, Ui.dp(14), Ui.dp(12));
        root.addView(nav, lp);
        selectNav(TAB_HOME);
    }

    private void selectNav(int sel) {
        for (int i = 0; i < 4; i++) {
            NavItem it = navItems[i];
            boolean on = i == sel;
            it.box.setBackground(on ? Ui.accentGradient(26) : null);
            it.icon.setImageTintList(ColorStateList.valueOf(on ? Ui.ON_ACCENT : Ui.TEXT2));
            it.label.setVisibility(on ? View.VISIBLE : View.GONE);
            LinearLayout.LayoutParams p = (LinearLayout.LayoutParams) it.box.getLayoutParams();
            if (p != null) {
                p.weight = on ? 2.1f : 1f;
                it.box.setLayoutParams(p);
            }
            if (on) Ui.bounce(it.icon);
        }
        if (navItems[TAB_DOWNLOADS] != null && DownloadManager.get() != null) updateBadge();
    }

    private void updateBadge() {
        int n = 0;
        for (DownloadTask t : DownloadManager.get().all()) if (t.isActive()) n++;
        NavItem it = navItems[TAB_DOWNLOADS];
        if (n > 0) {
            boolean was = it.badge.getVisibility() == View.VISIBLE;
            it.badge.setText(String.valueOf(n));
            boolean sel = tab == TAB_DOWNLOADS && stack.isEmpty();
            it.badge.setBackground(Ui.solid(sel ? 0xFF0B0D12 : Ui.ACCENT, 8));
            it.badge.setTextColor(sel ? Ui.ACCENT : Ui.ON_ACCENT);
            it.badge.setVisibility(View.VISIBLE);
            if (!was) Ui.popIn(it.badge);
        } else {
            it.badge.setVisibility(View.GONE);
        }
    }

    @Override public void onDownloadsChanged() { updateBadge(); }

    // ------------------------------------------------------------------ screens

    private Screen tabScreen(int i) {
        if (tabs[i] == null) {
            switch (i) {
                case TAB_FOLLOWING: tabs[i] = new FollowingScreen(this); break;
                case TAB_DOWNLOADS: tabs[i] = new DownloadsScreen(this); break;
                case TAB_SETTINGS: tabs[i] = new SettingsScreen(this); break;
                default: tabs[i] = new HomeScreen(this);
            }
        }
        return tabs[i];
    }

    public Screen top() { return stack.isEmpty() ? tabScreen(tab) : stack.get(stack.size() - 1); }

    private void attach(Screen s) {
        View v = s.view();
        if (v.getParent() == null) {
            content.addView(v, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
            s.onInsets(insetTop, bottomArea());
        }
        v.setVisibility(View.VISIBLE);
        v.setTranslationX(0);
        v.setAlpha(1f);
    }

    private void showTab(int i, boolean animate) {
        Screen old = top();
        tab = i;
        Screen s = tabScreen(i);
        attach(s);
        selectNav(i);
        if (old != s && old.isBuilt()) {
            old.onHide();
            final View ov = old.view();
            if (animate) {
                ov.animate().alpha(0f).translationY(Ui.dp(10)).setDuration(160).withEndAction(() -> {
                    ov.setVisibility(View.GONE);
                    ov.setTranslationY(0);
                    ov.setAlpha(1f);
                }).start();
            } else {
                ov.setVisibility(View.GONE);
            }
        }
        View v = s.view();
        v.bringToFront();
        if (animate) {
            v.setAlpha(0f);
            v.setTranslationY(Ui.dp(16));
            v.animate().alpha(1f).translationY(0).setDuration(300).setStartDelay(60)
                    .setInterpolator(new DecelerateInterpolator(2f)).start();
        }
        s.onShow();
    }

    public void switchTab(int i) {
        Sheet.dismissAll(this);
        if (!stack.isEmpty()) {
            Screen cur = stack.get(stack.size() - 1);
            final List<Screen> drop = new ArrayList<>(stack);
            stack.clear();
            cur.onHide();
            final View cv = cur.view();
            cv.animate().alpha(0f).setDuration(180).withEndAction(() -> {
                for (Screen s : drop) {
                    s.onDestroy();
                    content.removeView(s.view());
                }
            }).start();
            tab = -1;
        }
        if (i == tab) return;
        if (tab < 0) tab = i;
        showTab(i, true);
    }

    public void push(Screen s) {
        Screen prev = top();
        stack.add(s);
        attach(s);
        final View v = s.view();
        v.bringToFront();
        nav.bringToFront();
        prev.onHide();
        final View pv = prev.view();
        v.setTranslationX(content.getWidth() * 0.3f);
        v.setAlpha(0f);
        v.animate().translationX(0).alpha(1f).setDuration(340).setInterpolator(new PathInterpolator(0.2f, 0.9f, 0.1f, 1f))
                .withEndAction(() -> { if (stack.contains(s)) pv.setVisibility(View.INVISIBLE); }).start();
        pv.animate().translationX(-content.getWidth() * 0.08f).alpha(0.4f).setDuration(340).start();
        s.onShow();
    }

    public void pop() {
        if (stack.isEmpty()) return;
        final Screen s = stack.remove(stack.size() - 1);
        Screen prev = top();
        View pv = prev.view();
        pv.setVisibility(View.VISIBLE);
        pv.animate().translationX(0).alpha(1f).setDuration(300).start();
        s.onHide();
        final View v = s.view();
        v.animate().translationX(content.getWidth() * 0.35f).alpha(0f).setDuration(260)
                .setInterpolator(new DecelerateInterpolator()).withEndAction(() -> {
                    s.onDestroy();
                    content.removeView(v);
                }).start();
        prev.onShow();
    }

    public void openChannel(String slug, az.kickdl.app.data.Channel prefetched) {
        if (slug == null || slug.isEmpty()) return;
        Sheet.dismissAll(this);
        Screen t = top();
        if (t instanceof ChannelScreen && ((ChannelScreen) t).slug().equalsIgnoreCase(slug)) return;
        push(new ChannelScreen(this, slug, prefetched));
    }

    public void openLink(final KickApi.Link l) {
        if (l == null) return;
        if (l.type == 0) {
            openChannel(l.channel, null);
            return;
        }
        Toaster.show(this, R.drawable.ic_link, getString(R.string.opening_link));
        Bg.run(() -> l.type == 1 ? KickApi.video(l.id) : KickApi.clip(l.id), (Media m) -> {
            if (l.channel != null && (m.channelSlug == null || m.channelSlug.isEmpty())) m.channelSlug = l.channel;
            if (m.channelSlug != null && !m.channelSlug.isEmpty()) openChannel(m.channelSlug, null);
            MediaActions.showSheet(this, m);
        }, e -> {
            if (l.channel != null) openChannel(l.channel, null);
            Toaster.show(this, R.drawable.ic_error, getString(R.string.link_failed));
        });
    }

    public void restartBackground() { bg.restart(); }

    // ------------------------------------------------------------------ intents & permissions

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent i) {
        if (i == null) return;
        if (i.hasExtra(EXTRA_TAB)) {
            int t = i.getIntExtra(EXTRA_TAB, TAB_HOME);
            i.removeExtra(EXTRA_TAB);
            switchTab(t);
            if (t == TAB_DOWNLOADS && tabs[TAB_DOWNLOADS] instanceof DownloadsScreen)
                ((DownloadsScreen) tabs[TAB_DOWNLOADS]).showBestPage();
            return;
        }
        String text = null;
        if (Intent.ACTION_SEND.equals(i.getAction())) text = i.getStringExtra(Intent.EXTRA_TEXT);
        else if (Intent.ACTION_VIEW.equals(i.getAction()) && i.getData() != null) text = i.getData().toString();
        if (text == null) return;
        i.setAction(null);
        KickApi.Link l = KickApi.parseLink(text);
        if (l != null) {
            final KickApi.Link fl = l;
            root.post(() -> openLink(fl));
        } else {
            switchTab(TAB_HOME);
            final String q = text;
            root.post(() -> ((HomeScreen) tabScreen(TAB_HOME)).setQuery(q));
        }
    }

    /** Asks for notification (13+) and legacy storage (8–9) permissions when downloads start. */
    public void ensurePermissions() {
        List<String> need = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 33 && !askedNotif
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            need.add(Manifest.permission.POST_NOTIFICATIONS);
            askedNotif = true;
        }
        if (Build.VERSION.SDK_INT < 29 && Prefs.gallery() && !askedStorage
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            need.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            askedStorage = true;
        }
        if (!need.isEmpty()) requestPermissions(need.toArray(new String[0]), 7);
    }

    // ------------------------------------------------------------------ lifecycle

    @Override public void onBackPressed() {
        if (Sheet.handleBack(this)) return;
        if (top().onBack()) return;
        if (!stack.isEmpty()) {
            pop();
            return;
        }
        if (tab != TAB_HOME) {
            switchTab(TAB_HOME);
            return;
        }
        super.onBackPressed();
    }

    @Override protected void onResume() {
        super.onResume();
        Screen t = top();
        if (t.isBuilt()) t.onShow();
        updateBadge();
    }

    @Override protected void onPause() {
        super.onPause();
        Screen t = top();
        if (t.isBuilt()) t.onHide();
    }

    @Override protected void onDestroy() {
        DownloadManager.get().removeListener(this);
        CfBridge.get(this).detach(root);
        Sheet.forget(this);
        for (Screen s : tabs) if (s != null) s.onDestroy();
        for (Screen s : stack) s.onDestroy();
        super.onDestroy();
    }
}
