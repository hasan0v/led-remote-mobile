package az.kickdl.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import az.kickdl.app.net.CfBridge;
import az.kickdl.app.ui.Ui;

/** Visible kick.com page so the user can pass an interactive Cloudflare check once. */
public class VerifyActivity extends Activity {

    public static void open(Context c) {
        c.startActivity(new Intent(c, VerifyActivity.class));
    }

    private WebView web;

    @SuppressLint("SetJavaScriptEnabled")
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        LinearLayout root = Ui.col(this);
        root.setBackgroundColor(Ui.BG);

        LinearLayout bar = Ui.row(this);
        bar.setBackground(Ui.glassStrong(0));
        bar.setPadding(Ui.dp(8), Ui.dp(8), Ui.dp(12), Ui.dp(8));
        ImageView close = Ui.flatIcon(this, R.drawable.ic_close, 44, 22, Ui.TEXT);
        close.setOnClickListener(v -> done());
        bar.addView(close);
        LinearLayout col = Ui.col(this);
        col.addView(Ui.text(this, getString(R.string.verify_title), 16, Ui.TEXT, Ui.BOLD));
        col.addView(Ui.lines(Ui.text(this, getString(R.string.verify_hint), 12, Ui.TEXT2, Ui.REGULAR), 2));
        bar.addView(col, Ui.margins(Ui.lpw(0, Ui.WRAP, 1), 6, 0, 8, 0));
        LinearLayout ok = Ui.pill(this, R.drawable.ic_check, getString(R.string.done), true);
        ok.setOnClickListener(v -> done());
        bar.addView(ok, Ui.lp(Ui.WRAP, Ui.dp(40)));
        root.addView(bar, Ui.lp(Ui.MATCH, Ui.WRAP));

        final ProgressBar progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));
        progress.setMax(100);
        root.addView(progress, Ui.lp(Ui.MATCH, Ui.dp(3)));

        FrameLayout holder = new FrameLayout(this);
        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int p) {
                progress.setProgress(p);
                progress.setVisibility(p >= 100 ? View.INVISIBLE : View.VISIBLE);
            }
        });
        holder.addView(web, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        root.addView(holder, Ui.lpw(Ui.MATCH, 0, 1));
        setContentView(root);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(0, insets.getSystemWindowInsetTop(), 0, insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });
        web.loadUrl("https://kick.com/");
    }

    private void done() {
        CookieManager.getInstance().flush();
        CfBridge.get(this).reset();
        finish();
    }

    @Override public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
        else done();
    }

    @Override protected void onDestroy() {
        if (web != null) web.destroy();
        super.onDestroy();
    }
}
