package az.kickdl.app.screens;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.ColorStateList;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

import az.kickdl.app.MainActivity;
import az.kickdl.app.R;
import az.kickdl.app.VerifyActivity;
import az.kickdl.app.data.Channel;
import az.kickdl.app.data.Follows;
import az.kickdl.app.data.Prefs;
import az.kickdl.app.dl.DownloadManager;
import az.kickdl.app.dl.DownloadTask;
import az.kickdl.app.net.KickApi;
import az.kickdl.app.ui.Bg;
import az.kickdl.app.ui.FlowLayout;
import az.kickdl.app.ui.Fmt;
import az.kickdl.app.ui.ImageLoader;
import az.kickdl.app.ui.Toaster;
import az.kickdl.app.ui.Ui;

/** Search-first home: search bar on top, results / followed channels / recent searches below. */
public class HomeScreen extends Screen implements Follows.Listener {
    private FrameLayout root;
    private LinearLayout topArea;
    private EditText input;
    private ImageView clearBtn;
    private ProgressBar spinner;
    private ScrollView scroll;
    private LinearLayout content;
    private int searchToken;
    private String lastQuery = "";
    private final Runnable debounced = this::searchNow;
    private int bottomPad;

    public HomeScreen(MainActivity a) { super(a); }

    @Override protected View build() {
        Context c = a;
        root = new FrameLayout(c);
        scroll = new ScrollView(c);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        scroll.setFillViewport(true);
        content = Ui.col(c);
        content.setPadding(Ui.dp(18), 0, Ui.dp(18), 0);
        scroll.addView(content, new FrameLayout.LayoutParams(Ui.MATCH, Ui.WRAP));
        root.addView(scroll, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));

        topArea = Ui.col(c);
        topArea.setPadding(Ui.dp(18), 0, Ui.dp(18), Ui.dp(10));
        topArea.setBackground(new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xF507080C, 0xD007080C, 0x0007080C}));

        LinearLayout titleRow = Ui.row(c);
        LinearLayout tcol = Ui.col(c);
        TextView hello = Ui.text(c, a.getString(R.string.home_kicker), 12, Ui.ACCENT, Ui.BOLD);
        hello.setLetterSpacing(0.14f);
        tcol.addView(hello);
        tcol.addView(Ui.title(c, a.getString(R.string.home_title)), Ui.margins(Ui.lp(Ui.WRAP, Ui.WRAP), 0, 4, 0, 0));
        titleRow.addView(tcol, Ui.lpw(0, Ui.WRAP, 1));
        ImageView paste = Ui.iconButton(c, R.drawable.ic_paste, 44, 20);
        paste.setOnClickListener(v -> pasteLink());
        titleRow.addView(paste);
        topArea.addView(titleRow, Ui.margins(Ui.lp(Ui.MATCH, Ui.WRAP), 0, 8, 0, 14));

        // search bar
        LinearLayout bar = Ui.row(c);
        bar.setBackground(Ui.glass(26));
        bar.setPadding(Ui.dp(16), 0, Ui.dp(6), 0);
        bar.addView(Ui.icon(c, R.drawable.ic_search, 22, Ui.TEXT2), Ui.lp(Ui.dp(22), Ui.dp(22)));
        input = new EditText(c);
        input.setBackground(null);
        input.setHint(R.string.search_hint);
        input.setHintTextColor(Ui.TEXT3);
        input.setTextColor(Ui.TEXT);
        input.setTextSize(16);
        input.setTypeface(Ui.MEDIUM);
        input.setSingleLine(true);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setPadding(Ui.dp(12), 0, Ui.dp(8), 0);
        bar.addView(input, Ui.lpw(0, Ui.dp(54), 1));
        spinner = new ProgressBar(c);
        spinner.setIndeterminateTintList(ColorStateList.valueOf(Ui.ACCENT));
        spinner.setVisibility(View.GONE);
        bar.addView(spinner, Ui.margins(Ui.lp(Ui.dp(22), Ui.dp(22)), 0, 0, 8, 0));
        clearBtn = Ui.flatIcon(c, R.drawable.ic_close, 40, 20, Ui.TEXT2);
        clearBtn.setVisibility(View.GONE);
        clearBtn.setOnClickListener(v -> {
            input.setText("");
            showIdle();
        });
        bar.addView(clearBtn);
        topArea.addView(bar, Ui.lp(Ui.MATCH, Ui.dp(54)));
        root.addView(topArea, new FrameLayout.LayoutParams(Ui.MATCH, Ui.WRAP, Gravity.TOP));

        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a1, int b, int c1) {}

            @Override public void onTextChanged(CharSequence s, int a1, int b, int c1) {}

            @Override public void afterTextChanged(Editable s) {
                String q = s.toString().trim();
                clearBtn.setVisibility(q.isEmpty() ? View.GONE : View.VISIBLE);
                Bg.MAIN.removeCallbacks(debounced);
                if (q.isEmpty()) {
                    showIdle();
                } else if (KickApi.parseLink(q) == null && q.length() >= 2) {
                    Bg.MAIN.postDelayed(debounced, 450);
                }
            }
        });
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                submit();
                return true;
            }
            return false;
        });

        Follows.addListener(this);
        showIdle();
        return root;
    }

    // ------------------------------------------------------------------ search

    private void submit() {
        String q = input.getText().toString().trim();
        if (q.isEmpty()) return;
        hideKeyboard();
        KickApi.Link link = KickApi.parseLink(q);
        if (link != null) {
            a.openLink(link);
            return;
        }
        Prefs.addSearch(q);
        Bg.MAIN.removeCallbacks(debounced);
        // Exact slug on enter: open the channel directly when it matches.
        searchNow();
    }

    public void setQuery(String q) {
        if (input == null) return;
        input.setText(q);
        input.setSelection(q.length());
        submit();
    }

    private void searchNow() {
        final String q = input.getText().toString().trim();
        if (q.length() < 2) return;
        lastQuery = q;
        final int token = ++searchToken;
        spinner.setVisibility(View.VISIBLE);
        clearBtn.setVisibility(View.GONE);
        if (content.getChildCount() == 0 || !"results".equals(content.getTag())) showSkeleton();
        Bg.run(() -> KickApi.search(q), res -> {
            if (token != searchToken) return;
            spinner.setVisibility(View.GONE);
            clearBtn.setVisibility(View.VISIBLE);
            showResults(q, res);
        }, e -> {
            if (token != searchToken) return;
            spinner.setVisibility(View.GONE);
            clearBtn.setVisibility(View.VISIBLE);
            showError(e);
        });
    }

    private void showSkeleton() {
        content.removeAllViews();
        content.setTag("results");
        content.addView(Ui.space(a, 1, 1));
        for (int i = 0; i < 5; i++) content.addView(Views.shimmerRow(a, 68));
    }

    private void showResults(String q, List<Channel> res) {
        content.removeAllViews();
        content.setTag("results");
        if (res.isEmpty()) {
            content.addView(Views.empty(a, R.drawable.ic_search, a.getString(R.string.no_results),
                    a.getString(R.string.no_results_hint, q), null, null));
            return;
        }
        content.addView(Views.section(a, a.getString(R.string.channels)), Ui.margins(Ui.lp(Ui.WRAP, Ui.WRAP), 4, 0, 0, 10));
        for (int i = 0; i < res.size(); i++) {
            View row = channelRow(res.get(i));
            content.addView(row, Ui.margins(Ui.lp(Ui.MATCH, Ui.WRAP), 0, 0, 0, 8));
            Ui.fadeIn(row, Math.min(i, 8) * 35L);
        }
    }

    private void showError(final Exception e) {
        content.removeAllViews();
        content.setTag("results");
        final boolean ch = Errors.isChallenge(e);
        content.addView(Views.empty(a, R.drawable.ic_error, a.getString(R.string.search_failed), Errors.message(a, e),
                a.getString(ch ? R.string.verify : R.string.retry), v -> {
                    if (ch) VerifyActivity.open(a);
                    else searchNow();
                }));
    }

    private View channelRow(final Channel ch) {
        Context c = a;
        LinearLayout row = Ui.row(c);
        row.setPadding(Ui.dp(12), Ui.dp(10), Ui.dp(8), Ui.dp(10));
        row.setBackground(Ui.ripple(Ui.glass(20), 20));
        row.addView(Views.avatar(c, ch.avatar, 50, ch.live, ch.displayName()));
        LinearLayout col = Ui.col(c);
        LinearLayout nameRow = Ui.row(c);
        nameRow.addView(Ui.single(Ui.text(c, ch.displayName(), 16, Ui.TEXT, Ui.BOLD)), Ui.lp(Ui.WRAP, Ui.WRAP));
        if (ch.verified) nameRow.addView(Ui.icon(c, R.drawable.ic_verified, 16, Ui.ACCENT), Ui.margins(Ui.lp(Ui.dp(16), Ui.dp(16)), 6, 0, 0, 0));
        if (ch.live) {
            TextView lb = Views.liveBadge(c);
            lb.setTextSize(9);
            nameRow.addView(lb, Ui.margins(Ui.lp(Ui.WRAP, Ui.WRAP), 8, 0, 0, 0));
        }
        col.addView(nameRow);
        String sub = ch.followers >= 0 ? a.getString(R.string.followers_fmt, Fmt.count(ch.followers)) : "kick.com/" + ch.slug;
        if (ch.live && ch.liveTitle != null) sub = ch.liveTitle;
        col.addView(Ui.single(Ui.text(c, sub, 12, Ui.TEXT2, Ui.REGULAR)), Ui.margins(Ui.lp(Ui.MATCH, Ui.WRAP), 0, 4, 0, 0));
        row.addView(col, Ui.margins(Ui.lpw(0, Ui.WRAP, 1), 14, 0, 8, 0));
        final ImageView heart = Ui.flatIcon(c, Follows.isFollowed(ch.slug) ? R.drawable.ic_heart : R.drawable.ic_heart_outline, 44, 22,
                Follows.isFollowed(ch.slug) ? Ui.LIVE : Ui.TEXT2);
        heart.setOnClickListener(v -> {
            Ui.haptic(v);
            boolean f = Follows.toggle(ch);
            heart.setImageResource(f ? R.drawable.ic_heart : R.drawable.ic_heart_outline);
            heart.setImageTintList(ColorStateList.valueOf(f ? Ui.LIVE : Ui.TEXT2));
            Ui.bounce(heart);
        });
        row.addView(heart);
        row.setOnClickListener(v -> {
            hideKeyboard();
            Prefs.addSearch(lastQuery.isEmpty() ? ch.slug : lastQuery);
            a.openChannel(ch.slug, ch);
        });
        return row;
    }

    // ------------------------------------------------------------------ idle content

    private void showIdle() {
        searchToken++;
        if (spinner != null) spinner.setVisibility(View.GONE);
        content.removeAllViews();
        content.setTag("idle");
        Context c = a;

        // followed channels rail
        List<Follows.Item> follows = Follows.all();
        if (!follows.isEmpty()) {
            LinearLayout head = Ui.row(c);
            head.addView(Views.section(c, a.getString(R.string.your_channels)), Ui.lpw(0, Ui.WRAP, 1));
            TextView all = Ui.text(c, a.getString(R.string.see_all), 13, Ui.ACCENT, Ui.MEDIUM);
            all.setOnClickListener(v -> a.switchTab(MainActivity.TAB_FOLLOWING));
            head.addView(all);
            content.addView(head, Ui.margins(Ui.lp(Ui.MATCH, Ui.WRAP), 4, 4, 4, 12));
            HorizontalScrollView hs = new HorizontalScrollView(c);
            hs.setHorizontalScrollBarEnabled(false);
            hs.setClipToPadding(false);
            LinearLayout rail = Ui.row(c);
            rail.setGravity(Gravity.TOP);
            for (int i = 0; i < follows.size(); i++) {
                final Follows.Item f = follows.get(i);
                LinearLayout b = Ui.col(c);
                b.setGravity(Gravity.CENTER_HORIZONTAL);
                FrameLayout av = Views.avatar(c, f.avatar, 64, f.live, f.name);
                b.addView(av);
                TextView n = Ui.single(Ui.text(c, f.name, 12, Ui.TEXT, Ui.MEDIUM));
                n.setGravity(Gravity.CENTER);
                b.addView(n, Ui.margins(Ui.lp(Ui.dp(76), Ui.WRAP), 0, 8, 0, 0));
                if (f.newVods() > 0) {
                    TextView badge = Ui.chip(c, a.getString(R.string.new_count, f.newVods()), Ui.ACCENT, Ui.ON_ACCENT);
                    badge.setTextSize(10);
                    b.addView(badge, Ui.margins(Ui.lp(Ui.WRAP, Ui.WRAP), 0, 4, 0, 0));
                } else if (f.live) {
                    TextView badge = Views.liveBadge(c);
                    badge.setTextSize(9);
                    b.addView(badge, Ui.margins(Ui.lp(Ui.WRAP, Ui.WRAP), 0, 4, 0, 0));
                }
                b.setOnClickListener(v -> a.openChannel(f.slug, null));
                Ui.pressable(b);
                rail.addView(b, Ui.margins(Ui.lp(Ui.dp(76), Ui.WRAP), 0, 0, 10, 0));
                Ui.fadeIn(b, Math.min(i, 6) * 40L);
            }
            hs.addView(rail);
            content.addView(hs, Ui.margins(Ui.lp(Ui.MATCH, Ui.WRAP), 0, 0, 0, 22));
        }

        // recent searches
        List<String> recent = Prefs.searches();
        if (!recent.isEmpty()) {
            LinearLayout head = Ui.row(c);
            head.addView(Views.section(c, a.getString(R.string.recent_searches)), Ui.lpw(0, Ui.WRAP, 1));
            TextView clr = Ui.text(c, a.getString(R.string.clear), 13, Ui.TEXT2, Ui.MEDIUM);
            clr.setOnClickListener(v -> {
                Prefs.clearSearches();
                showIdle();
            });
            head.addView(clr);
            content.addView(head, Ui.margins(Ui.lp(Ui.MATCH, Ui.WRAP), 4, 0, 4, 12));
            FlowLayout flow = new FlowLayout(c, 8, 8);
            for (final String q : recent) {
                LinearLayout chip = Ui.row(c);
                chip.setBackground(Ui.ripple(Ui.glass(18), 18));
                chip.setPadding(Ui.dp(14), Ui.dp(8), Ui.dp(8), Ui.dp(8));
                chip.addView(Ui.icon(c, R.drawable.ic_history, 15, Ui.TEXT3), Ui.margins(Ui.lp(Ui.dp(15), Ui.dp(15)), 0, 0, 8, 0));
                chip.addView(Ui.text(c, q, 14, Ui.TEXT, Ui.MEDIUM));
                ImageView x = Ui.icon(c, R.drawable.ic_close, 14, Ui.TEXT3);
                x.setOnClickListener(v -> {
                    Prefs.removeSearch(q);
                    showIdle();
                });
                x.setPadding(Ui.dp(2), Ui.dp(2), Ui.dp(2), Ui.dp(2));
                chip.addView(x, Ui.margins(Ui.lp(Ui.dp(20), Ui.dp(20)), 8, 0, 0, 0));
                chip.setOnClickListener(v -> setQuery(q));
                flow.addView(chip);
            }
            content.addView(flow, Ui.margins(Ui.lp(Ui.MATCH, Ui.WRAP), 0, 0, 0, 24));
        }

        // recent downloads
        int shown = 0;
        LinearLayout recentDl = Ui.col(c);
        for (final DownloadTask t : DownloadManager.get().all()) {
            if (t.status != DownloadTask.DONE || shown >= 3) continue;
            shown++;
            LinearLayout r = Ui.row(c);
            r.setBackground(Ui.ripple(Ui.glass(18), 18));
            r.setPadding(Ui.dp(10), Ui.dp(10), Ui.dp(14), Ui.dp(10));
            FrameLayout tb = new az.kickdl.app.ui.RoundFrame(c, 10);
            tb.setBackground(Ui.solid(0xFF14161D, 10));
            ImageView iv = new ImageView(c);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            tb.addView(iv, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
            ImageLoader.load(iv, t.thumb, Ui.dp(180));
            tb.addView(Ui.icon(c, R.drawable.ic_play, 22, 0xEEFFFFFF), new FrameLayout.LayoutParams(Ui.dp(22), Ui.dp(22), Gravity.CENTER));
            r.addView(tb, Ui.lp(Ui.dp(88), Ui.dp(50)));
            LinearLayout col = Ui.col(c);
            col.addView(Ui.lines(Ui.text(c, t.title, 13, Ui.TEXT, Ui.MEDIUM), 2));
            col.addView(Ui.single(Ui.text(c, t.channelName + " · " + Fmt.size(t.outputSize), 11, Ui.TEXT3, Ui.REGULAR)),
                    Ui.margins(Ui.lp(Ui.MATCH, Ui.WRAP), 0, 4, 0, 0));
            r.addView(col, Ui.margins(Ui.lpw(0, Ui.WRAP, 1), 12, 0, 0, 0));
            r.setOnClickListener(v -> MediaActions.playTask(a, t));
            recentDl.addView(r, Ui.margins(Ui.lp(Ui.MATCH, Ui.WRAP), 0, 0, 0, 8));
        }
        if (shown > 0) {
            content.addView(Views.section(c, a.getString(R.string.recent_downloads)), Ui.margins(Ui.lp(Ui.WRAP, Ui.WRAP), 4, 0, 0, 12));
            content.addView(recentDl, Ui.margins(Ui.lp(Ui.MATCH, Ui.WRAP), 0, 0, 0, 22));
        }

        // tips
        content.addView(tip(c, R.drawable.ic_search, a.getString(R.string.tip_search_title), a.getString(R.string.tip_search)),
                Ui.margins(Ui.lp(Ui.MATCH, Ui.WRAP), 0, 0, 0, 10));
        content.addView(tip(c, R.drawable.ic_share, a.getString(R.string.tip_share_title), a.getString(R.string.tip_share)),
                Ui.margins(Ui.lp(Ui.MATCH, Ui.WRAP), 0, 0, 0, 10));
        content.addView(tip(c, R.drawable.ic_rec, a.getString(R.string.tip_live_title), a.getString(R.string.tip_live)),
                Ui.margins(Ui.lp(Ui.MATCH, Ui.WRAP), 0, 0, 0, 10));
        for (int i = 0; i < content.getChildCount(); i++) Ui.fadeIn(content.getChildAt(i), Math.min(i, 8) * 30L);
    }

    private View tip(Context c, int icon, String title, String body) {
        LinearLayout r = Ui.row(c);
        r.setGravity(Gravity.TOP);
        r.setBackground(Ui.glass(20));
        r.setPadding(Ui.dp(16), Ui.dp(14), Ui.dp(16), Ui.dp(14));
        FrameLayout ic = new FrameLayout(c);
        ic.setBackground(Ui.oval(0x1F53FC18));
        ic.addView(Ui.icon(c, icon, 18, Ui.ACCENT), new FrameLayout.LayoutParams(Ui.dp(18), Ui.dp(18), Gravity.CENTER));
        r.addView(ic, Ui.lp(Ui.dp(36), Ui.dp(36)));
        LinearLayout col = Ui.col(c);
        col.addView(Ui.text(c, title, 14, Ui.TEXT, Ui.BOLD));
        col.addView(Ui.text(c, body, 13, Ui.TEXT2, Ui.REGULAR), Ui.margins(Ui.lp(Ui.MATCH, Ui.WRAP), 0, 4, 0, 0));
        r.addView(col, Ui.margins(Ui.lpw(0, Ui.WRAP, 1), 14, 0, 0, 0));
        return r;
    }

    private void pasteLink() {
        ClipboardManager cm = (ClipboardManager) a.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = cm.getPrimaryClip();
        String text = clip != null && clip.getItemCount() > 0 && clip.getItemAt(0).getText() != null
                ? clip.getItemAt(0).getText().toString() : null;
        if (text == null || text.trim().isEmpty()) {
            Toaster.show(a, R.drawable.ic_paste, a.getString(R.string.clipboard_empty));
            return;
        }
        KickApi.Link l = KickApi.parseLink(text);
        if (l != null) {
            a.openLink(l);
        } else {
            input.setText(text.trim());
            input.setSelection(input.getText().length());
            submit();
        }
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) a.getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
        input.clearFocus();
    }

    // ------------------------------------------------------------------ lifecycle

    @Override public void onInsets(int top, int bottom) {
        bottomPad = bottom;
        if (topArea != null) {
            topArea.setPadding(Ui.dp(18), top + Ui.dp(8), Ui.dp(18), Ui.dp(14));
            topArea.post(() -> scroll.setPadding(0, topArea.getHeight() + Ui.dp(2), 0, bottomPad + Ui.dp(16)));
        }
    }

    @Override public void onShow() {
        if (content != null && "idle".equals(content.getTag())) showIdle();
    }

    @Override public void onHide() { if (input != null) hideKeyboard(); }

    @Override public void onDestroy() { Follows.removeListener(this); }

    @Override public void onFollowsChanged() {
        if (content != null && "idle".equals(content.getTag())) showIdle();
    }

    @Override public boolean onBack() {
        if (input != null && input.getText().length() > 0) {
            input.setText("");
            showIdle();
            return true;
        }
        return false;
    }

    @Override public void scrollToTop() {
        if (scroll != null) scroll.smoothScrollTo(0, 0);
        if (input != null) {
            input.requestFocus();
            InputMethodManager imm = (InputMethodManager) a.getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showSoftInput(input, 0);
        }
    }
}
