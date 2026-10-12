package az.kickdl.app.net;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Kick's web API sits behind Cloudflare, which rejects most non-browser clients.
 * This bridge keeps a hidden WebView on the kick.com origin (a real Chromium that passes
 * the JS challenge) and performs API requests through window.fetch inside it.
 */
public final class CfBridge {

    public static class ChallengeException extends IOException {
        public ChallengeException() { super("Cloudflare verification required"); }
    }

    private static final int IDLE = 0, LOADING = 1, READY = 2, CHALLENGE = 3;
    private static final long CHALLENGE_TIMEOUT_MS = 22000;

    private static CfBridge instance;

    public static synchronized CfBridge get(Context c) {
        if (instance == null) instance = new CfBridge(c.getApplicationContext());
        return instance;
    }

    private static class Pending {
        final CountDownLatch latch = new CountDownLatch(1);
        final String url;
        int status;
        String body;
        String error;
        boolean challenge;
        boolean retried;

        Pending(String url) { this.url = url; }
    }

    private final Context app;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<Integer, Pending> pending = new ConcurrentHashMap<>();
    private final List<Integer> waiting = new ArrayList<>();
    private final AtomicInteger ids = new AtomicInteger();
    private WebView web;
    private volatile int state = IDLE;
    private long loadStarted;
    private int loadToken;

    private CfBridge(Context app) { this.app = app; }

    /** Keeps the WebView inside a live window so timers/JS are not throttled. */
    public void attach(final ViewGroup root) {
        main.post(new Runnable() {
            @Override public void run() {
                ensureWeb();
                if (web.getParent() == root) return;
                if (web.getParent() instanceof ViewGroup) ((ViewGroup) web.getParent()).removeView(web);
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(2, 2);
                web.setAlpha(0.01f);
                root.addView(web, 0, lp);
            }
        });
    }

    public void detach(final ViewGroup root) {
        main.post(new Runnable() {
            @Override public void run() {
                if (web != null && web.getParent() == root) root.removeView(web);
            }
        });
    }

    /** Call after the user passed an interactive check so the next request reloads. */
    public void reset() {
        main.post(new Runnable() {
            @Override public void run() { state = IDLE; }
        });
    }

    public boolean isChallenged() { return state == CHALLENGE; }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void ensureWeb() {
        if (web != null) return;
        web = new WebView(app);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setLoadsImagesAutomatically(false);
        s.setBlockNetworkImage(true);
        s.setMediaPlaybackRequiresUserGesture(true);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);
        Http.UA = s.getUserAgentString();
        web.addJavascriptInterface(new JsApi(), "KDL");
        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                if (state == LOADING) check(loadToken);
            }
        });
    }

    /** Blocking request (call from a background thread). */
    public Http.Resp fetch(final String url, long timeoutMs) throws IOException {
        if (Looper.myLooper() == Looper.getMainLooper()) throw new IllegalStateException("main thread");
        final int id = ids.incrementAndGet();
        final Pending p = new Pending(url);
        pending.put(id, p);
        main.post(new Runnable() {
            @Override public void run() { enqueue(id); }
        });
        try {
            if (!p.latch.await(timeoutMs, TimeUnit.MILLISECONDS)) throw new IOException("Timeout");
        } catch (InterruptedException e) {
            throw new IOException("Interrupted");
        } finally {
            pending.remove(id);
        }
        if (p.challenge) throw new ChallengeException();
        if (p.error != null) throw new IOException(p.error);
        Http.Resp r = new Http.Resp();
        r.code = p.status;
        r.body = p.body == null ? new byte[0] : p.body.getBytes(StandardCharsets.UTF_8);
        return r;
    }

    private void enqueue(int id) {
        ensureWeb();
        Pending p = pending.get(id);
        if (p == null) return;
        if (state == READY) {
            runFetch(id, p.url);
        } else {
            waiting.add(id);
            if (state != LOADING) startLoad(p.url);
        }
    }

    private void startLoad(String url) {
        state = LOADING;
        loadStarted = SystemClock.elapsedRealtime();
        final int token = ++loadToken;
        web.loadUrl(url);
        main.postDelayed(new Runnable() {
            @Override public void run() { check(token); }
        }, 1200);
    }

    private void check(final int token) {
        if (token != loadToken || state != LOADING) return;
        web.evaluateJavascript(
                "(function(){try{return location.hostname+'\\u0001'+document.readyState+'\\u0001'+document.title+'\\u0001'+(document.body?document.body.innerText.substring(0,300):'');}catch(e){return ''}})()",
                new ValueCallback<String>() {
                    @Override public void onReceiveValue(String value) { onChecked(token, decode(value)); }
                });
    }

    private void onChecked(final int token, String v) {
        if (token != loadToken || state != LOADING) return;
        String[] parts = v.split("\u0001", -1);
        String host = parts.length > 0 ? parts[0] : "";
        String ready = parts.length > 1 ? parts[1] : "";
        String title = parts.length > 2 ? parts[2] : "";
        String body = parts.length > 3 ? parts[3].trim() : "";
        boolean onKick = host.endsWith("kick.com");
        boolean challenge = looksLikeChallenge(title + " " + body);
        if (onKick && !challenge && "complete".equals(ready)) {
            state = READY;
            flush();
            return;
        }
        if (SystemClock.elapsedRealtime() - loadStarted > CHALLENGE_TIMEOUT_MS) {
            state = CHALLENGE;
            failWaiting();
            return;
        }
        main.postDelayed(new Runnable() {
            @Override public void run() { check(token); }
        }, 700);
    }

    private void flush() {
        List<Integer> copy = new ArrayList<>(waiting);
        waiting.clear();
        for (int id : copy) {
            Pending p = pending.get(id);
            if (p != null) runFetch(id, p.url);
        }
    }

    private void failWaiting() {
        List<Integer> copy = new ArrayList<>(waiting);
        waiting.clear();
        for (int id : copy) {
            Pending p = pending.get(id);
            if (p != null) {
                p.challenge = true;
                p.latch.countDown();
            }
        }
    }

    private void runFetch(int id, String url) {
        String js = "(function(){var id=" + id + ";fetch(" + JSONObject.quote(url)
                + ",{credentials:'include',headers:{'Accept':'application/json, text/plain, */*'}})"
                + ".then(function(r){return r.text().then(function(t){KDL.done(id,r.status,t);});})"
                + ".catch(function(e){KDL.fail(id,String(e));});})();";
        web.evaluateJavascript(js, null);
    }

    static boolean looksLikeChallenge(String s) {
        if (s == null) return false;
        String l = s.toLowerCase();
        return l.contains("just a moment") || l.contains("attention required") || l.contains("verify you are human")
                || l.contains("checking your browser") || l.contains("cf-chl") || l.contains("challenge-platform")
                || l.contains("enable javascript and cookies");
    }

    private static String decode(String jsValue) {
        if (jsValue == null || "null".equals(jsValue)) return "";
        try {
            return new JSONArray("[" + jsValue + "]").getString(0);
        } catch (Exception e) {
            return jsValue;
        }
    }

    private class JsApi {
        @JavascriptInterface
        public void done(final int id, int status, String body) {
            final Pending p = pending.get(id);
            if (p == null) return;
            if ((status == 403 || status == 503) && looksLikeChallenge(body)) {
                if (!p.retried) {
                    // Clearance expired: navigate again (solves the JS challenge) and retry once.
                    p.retried = true;
                    main.post(new Runnable() {
                        @Override public void run() {
                            if (state == READY) state = IDLE;
                            enqueue(id);
                        }
                    });
                    return;
                }
                p.challenge = true;
            }
            p.status = status;
            p.body = body;
            p.latch.countDown();
        }

        @JavascriptInterface
        public void fail(int id, String error) {
            Pending p = pending.get(id);
            if (p == null) return;
            p.error = error;
            p.latch.countDown();
        }
    }
}
