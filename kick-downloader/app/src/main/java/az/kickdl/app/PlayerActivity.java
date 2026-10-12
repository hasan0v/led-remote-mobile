package az.kickdl.app;

import android.app.Activity;
import android.app.PendingIntent;
import android.app.PictureInPictureParams;
import android.app.RemoteAction;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Icon;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.media.audiofx.LoudnessEnhancer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Rational;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import az.kickdl.app.data.Prefs;
import az.kickdl.app.hls.Hls;
import az.kickdl.app.net.Http;
import az.kickdl.app.ui.Bg;
import az.kickdl.app.ui.Fmt;
import az.kickdl.app.ui.SeekBarView;
import az.kickdl.app.ui.Sheet;
import az.kickdl.app.ui.Toaster;
import az.kickdl.app.ui.Ui;

/**
 * Full featured player on top of the platform MediaPlayer: gestures (brightness, volume, scrub),
 * double-tap seek, pinch zoom, aspect modes, rotation lock, speed, quality switch for streams,
 * picture-in-picture, screenshots, sleep timer, audio boost and resume position.
 */
public class PlayerActivity extends Activity implements TextureView.SurfaceTextureListener {
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_POS_KEY = "pos_key";
    public static final String EXTRA_TASK = "task";
    public static final String EXTRA_LIVE = "live";
    private static final String ACTION_PIP = "az.kickdl.app.PIP";

    private static final int AR_FIT = 0, AR_FILL = 1, AR_STRETCH = 2, AR_16_9 = 3, AR_4_3 = 4;
    private static final int ROT_AUTO = 0, ROT_LANDSCAPE = 1, ROT_PORTRAIT = 2;
    private static final float[] SPEEDS = {0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f};

    private final Handler h = new Handler(Looper.getMainLooper());
    private FrameLayout root;
    private TextureView video;
    private Surface surface;
    private MediaPlayer mp;
    private boolean prepared, wantPlay = true, completed, released = true;
    private Uri uri;
    private String title, posKey;
    private boolean live, isStream;
    private int videoW, videoH;
    private long resumeAt = -1;
    private boolean announceResume;

    // controls
    private FrameLayout controls;
    private View topBar, bottomBar, centerRow;
    private TextView titleView, subView, timeCur, timeDur, speedBtn, qualityBtn, liveChip;
    private ImageView playBtn, lockBtn, rotBtn, pipBtn;
    private SeekBarView seek;
    private ProgressBar loading;
    private LinearLayout indicator;
    private ImageView indicatorIcon;
    private TextView indicatorText;
    private View indicatorBar;
    private FrameLayout indicatorTrack;
    private TextView leftRipple, rightRipple;
    private TextView speedBadge;
    private ImageView lockedIcon;
    private LinearLayout errorBox;
    private TextView errorText;
    private View audioArt;
    private boolean controlsShown = true, locked;

    // state
    private int aspect = AR_FIT;
    private float zoom = 1f, panX, panY;
    private int rotMode = ROT_AUTO;
    private float speed = 1f;
    private float boostDb = 0;
    private LoudnessEnhancer enhancer;
    private List<Hls.Variant> variants;
    private String currentStreamUrl;
    private long sleepAt;
    private boolean inPip;
    private BroadcastReceiver pipReceiver;
    private AudioManager audio;

    // gestures
    private GestureDetector gd;
    private ScaleGestureDetector sgd;
    private int gestureMode; // 0 none, 1 seek, 2 brightness, 3 volume
    private float startVal;
    private long scrubTarget = -1, scrubStartPos;
    private int seekAccum;
    private int seekSide;
    private long lastDoubleTap;
    private boolean longPressSpeed;
    private float volFloat;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(lp);
        }
        speed = Prefs.defaultSpeed();
        buildUi();
        setContentView(root);
        immersive();
        load(getIntent());
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        savePosition();
        releasePlayer();
        load(intent);
        if (surface != null) openPlayer();
    }

    private void load(Intent i) {
        uri = i.getData();
        title = i.getStringExtra(EXTRA_TITLE);
        posKey = i.getStringExtra(EXTRA_POS_KEY);
        live = i.getBooleanExtra(EXTRA_LIVE, false);
        if (uri == null) { finish(); return; }
        if (title == null) title = uri.getLastPathSegment();
        if (posKey == null) posKey = "uri:" + uri.toString().hashCode();
        String scheme = uri.getScheme();
        isStream = "http".equals(scheme) || "https".equals(scheme);
        currentStreamUrl = isStream ? uri.toString() : null;
        variants = null;
        completed = false;
        wantPlay = true;
        titleView.setText(title);
        subView.setText(live ? "" : isStream ? getString(R.string.streaming) : "");
        liveChip.setVisibility(live ? View.VISIBLE : View.GONE);
        seek.setVisibility(live ? View.INVISIBLE : View.VISIBLE);
        timeDur.setVisibility(live ? View.GONE : View.VISIBLE);
        centerRow.findViewWithTag("rew").setVisibility(live ? View.INVISIBLE : View.VISIBLE);
        centerRow.findViewWithTag("fwd").setVisibility(live ? View.INVISIBLE : View.VISIBLE);
        qualityBtn.setVisibility(View.GONE);
        resumeAt = !live && Prefs.resumePlayback() ? Prefs.position(posKey) : -1;
        announceResume = resumeAt > 3000;
        if (isStream && !uri.toString().toLowerCase(Locale.US).contains(".mp4")) loadVariants(uri.toString());
    }

    // ------------------------------------------------------------------ UI

    private void buildUi() {
        Context c = this;
        root = new FrameLayout(c);
        root.setBackgroundColor(0xFF000000);
        video = new TextureView(c);
        video.setSurfaceTextureListener(this);
        root.addView(video, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));

        audioArt = buildAudioArt(c);
        audioArt.setVisibility(View.GONE);
        root.addView(audioArt, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));

        // gesture layer
        View touch = new View(c);
        root.addView(touch, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        setupGestures(touch);

        // double tap ripples
        leftRipple = ripple(c, true);
        rightRipple = ripple(c, false);
        root.addView(leftRipple, new FrameLayout.LayoutParams(Ui.dp(150), Ui.dp(150), Gravity.CENTER_VERTICAL | Gravity.START));
        root.addView(rightRipple, new FrameLayout.LayoutParams(Ui.dp(150), Ui.dp(150), Gravity.CENTER_VERTICAL | Gravity.END));

        // controls overlay
        controls = new FrameLayout(c);
        root.addView(controls, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        View topGrad = new View(c);
        topGrad.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xB3000000, 0x00000000}));
        controls.addView(topGrad, new FrameLayout.LayoutParams(Ui.MATCH, Ui.dp(140), Gravity.TOP));
        View botGrad = new View(c);
        botGrad.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, new int[]{0xCC000000, 0x00000000}));
        controls.addView(botGrad, new FrameLayout.LayoutParams(Ui.MATCH, Ui.dp(170), Gravity.BOTTOM));

        // top bar
        LinearLayout top = Ui.row(c);
        top.setPadding(Ui.dp(12), Ui.dp(14), Ui.dp(12), 0);
        ImageView back = Ui.flatIcon(c, R.drawable.ic_back, 44, 24, 0xFFFFFFFF);
        back.setOnClickListener(v -> finish());
        top.addView(back);
        LinearLayout tcol = Ui.col(c);
        LinearLayout trow = Ui.row(c);
        liveChip = Ui.chip(c, getString(R.string.live_badge), Ui.LIVE, 0xFFFFFFFF);
        liveChip.setTypeface(Ui.BOLD);
        trow.addView(liveChip, Ui.margins(Ui.lp(Ui.WRAP, Ui.WRAP), 0, 0, 8, 0));
        titleView = Ui.single(Ui.text(c, 16, 0xFFFFFFFF, Ui.BOLD));
        trow.addView(titleView, Ui.lpw(0, Ui.WRAP, 1));
        tcol.addView(trow);
        subView = Ui.single(Ui.text(c, 12, 0xB3FFFFFF, Ui.REGULAR));
        tcol.addView(subView, Ui.margins(Ui.lp(Ui.MATCH, Ui.WRAP), 0, 3, 0, 0));
        top.addView(tcol, Ui.margins(Ui.lpw(0, Ui.WRAP, 1), 6, 0, 8, 0));
        qualityBtn = Ui.chip(c, "AUTO", 0x33FFFFFF, 0xFFFFFFFF);
        qualityBtn.setTypeface(Ui.BOLD);
        qualityBtn.setPadding(Ui.dp(10), Ui.dp(6), Ui.dp(10), Ui.dp(6));
        qualityBtn.setOnClickListener(v -> qualityMenu());
        top.addView(qualityBtn, Ui.margins(Ui.lp(Ui.WRAP, Ui.WRAP), 0, 0, 6, 0));
        pipBtn = Ui.flatIcon(c, R.drawable.ic_pip, 44, 22, 0xFFFFFFFF);
        pipBtn.setOnClickListener(v -> enterPip());
        top.addView(pipBtn);
        rotBtn = Ui.flatIcon(c, R.drawable.ic_rotate, 44, 22, 0xFFFFFFFF);
        rotBtn.setOnClickListener(v -> cycleRotation());
        top.addView(rotBtn);
        ImageView more = Ui.flatIcon(c, R.drawable.ic_more, 44, 22, 0xFFFFFFFF);
        more.setOnClickListener(v -> moreMenu());
        top.addView(more);
        topBar = top;
        controls.addView(top, new FrameLayout.LayoutParams(Ui.MATCH, Ui.WRAP, Gravity.TOP));

        // center controls
        LinearLayout center = Ui.row(c);
        center.setGravity(Gravity.CENTER);
        View rew = seekButton(c, false);
        rew.setTag("rew");
        rew.setOnClickListener(v -> seekBy(-Prefs.seekStep() * 1000L, true));
        center.addView(rew);
        playBtn = new ImageView(c);
        playBtn.setImageResource(R.drawable.ic_pause);
        playBtn.setImageTintList(ColorStateList.valueOf(0xFFFFFFFF));
        playBtn.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        playBtn.setPadding(Ui.dp(20), Ui.dp(20), Ui.dp(20), Ui.dp(20));
        GradientDrawable pb = new GradientDrawable();
        pb.setShape(GradientDrawable.OVAL);
        pb.setColor(0x40FFFFFF);
        pb.setStroke(Ui.dp(1), 0x40FFFFFF);
        playBtn.setBackground(Ui.rippleOval(pb));
        playBtn.setOnClickListener(v -> togglePlay());
        Ui.pressable(playBtn);
        center.addView(playBtn, Ui.margins(Ui.lp(Ui.dp(76), Ui.dp(76)), 36, 0, 36, 0));
        View fwd = seekButton(c, true);
        fwd.setTag("fwd");
        fwd.setOnClickListener(v -> seekBy(Prefs.seekStep() * 1000L, true));
        center.addView(fwd);
        centerRow = center;
        controls.addView(center, new FrameLayout.LayoutParams(Ui.MATCH, Ui.WRAP, Gravity.CENTER));

        // bottom bar
        LinearLayout bottom = Ui.col(c);
        bottom.setPadding(Ui.dp(12), 0, Ui.dp(12), Ui.dp(14));
        LinearLayout times = Ui.row(c);
        times.setPadding(Ui.dp(12), 0, Ui.dp(12), 0);
        timeCur = Ui.text(c, "0:00", 13, 0xFFFFFFFF, Ui.MEDIUM);
        timeDur = Ui.text(c, "0:00", 13, 0xB3FFFFFF, Ui.MEDIUM);
        times.addView(timeCur);
        times.addView(Ui.flex(c));
        times.addView(timeDur);
        bottom.addView(times);
        seek = new SeekBarView(c);
        seek.setListener(new SeekBarView.Listener() {
            @Override public void onSeekStart() {
                h.removeCallbacks(hideControls);
                scrubStartPos = position();
            }

            @Override public void onSeekMove(float f) {
                long d = duration();
                if (d > 0) {
                    timeCur.setText(Fmt.durationMs((long) (d * f)));
                    showIndicator(R.drawable.ic_clock, Fmt.durationMs((long) (d * f)) + " / " + Fmt.durationMs(d), -1);
                }
            }

            @Override public void onSeekEnd(float f) {
                long d = duration();
                if (d > 0) seekTo((long) (d * f), true);
                hideIndicatorSoon();
                scheduleHide();
            }
        });
        bottom.addView(seek, Ui.lp(Ui.MATCH, Ui.dp(36)));
        LinearLayout tools = Ui.row(c);
        lockBtn = Ui.flatIcon(c, R.drawable.ic_lock_open, 42, 20, 0xFFFFFFFF);
        lockBtn.setOnClickListener(v -> setLocked(true));
        tools.addView(lockBtn);
        speedBtn = Ui.text(c, "1×", 14, 0xFFFFFFFF, Ui.BOLD);
        speedBtn.setGravity(Gravity.CENTER);
        speedBtn.setBackground(Ui.ripple(Ui.solid(0x26FFFFFF, 14), 14));
        speedBtn.setPadding(Ui.dp(12), Ui.dp(6), Ui.dp(12), Ui.dp(6));
        speedBtn.setOnClickListener(v -> speedMenu());
        tools.addView(speedBtn, Ui.margins(Ui.lp(Ui.WRAP, Ui.WRAP), 6, 0, 0, 0));
        tools.addView(Ui.flex(c));
        ImageView shot = Ui.flatIcon(c, R.drawable.ic_camera, 42, 20, 0xFFFFFFFF);
        shot.setOnClickListener(v -> screenshot());
        tools.addView(shot);
        ImageView ar = Ui.flatIcon(c, R.drawable.ic_aspect, 42, 20, 0xFFFFFFFF);
        ar.setOnClickListener(v -> cycleAspect());
        tools.addView(ar);
        bottom.addView(tools, Ui.margins(Ui.lp(Ui.MATCH, Ui.WRAP), 0, 2, 0, 0));
        bottomBar = bottom;
        controls.addView(bottom, new FrameLayout.LayoutParams(Ui.MATCH, Ui.WRAP, Gravity.BOTTOM));

        // loading spinner
        loading = new ProgressBar(c);
        loading.setIndeterminateTintList(ColorStateList.valueOf(Ui.ACCENT));
        root.addView(loading, new FrameLayout.LayoutParams(Ui.dp(56), Ui.dp(56), Gravity.CENTER));

        // gesture indicator
        indicator = Ui.col(c);
        indicator.setGravity(Gravity.CENTER);
        indicator.setBackground(Ui.glassStrong(22));
        indicator.setPadding(Ui.dp(18), Ui.dp(14), Ui.dp(18), Ui.dp(14));
        indicatorIcon = Ui.icon(c, R.drawable.ic_brightness, 26, 0xFFFFFFFF);
        indicator.addView(indicatorIcon);
        indicatorText = Ui.text(c, "", 15, 0xFFFFFFFF, Ui.BOLD);
        indicator.addView(indicatorText, Ui.margins(Ui.lp(Ui.WRAP, Ui.WRAP), 0, 8, 0, 0));
        indicatorTrack = new FrameLayout(c);
        indicatorTrack.setBackground(Ui.solid(0x33FFFFFF, 3));
        indicatorBar = new View(c);
        indicatorBar.setBackground(Ui.accentGradient(3));
        indicatorTrack.addView(indicatorBar, new FrameLayout.LayoutParams(0, Ui.MATCH));
        indicator.addView(indicatorTrack, Ui.margins(Ui.lp(Ui.dp(120), Ui.dp(5)), 0, 10, 0, 0));
        indicator.setAlpha(0f);
        FrameLayout.LayoutParams il = new FrameLayout.LayoutParams(Ui.WRAP, Ui.WRAP, Gravity.CENTER_HORIZONTAL | Gravity.TOP);
        il.topMargin = Ui.dp(80);
        root.addView(indicator, il);

        speedBadge = Ui.chip(c, "2× ▶▶", 0xCC000000, 0xFFFFFFFF);
        speedBadge.setTextSize(13);
        speedBadge.setPadding(Ui.dp(14), Ui.dp(6), Ui.dp(14), Ui.dp(6));
        speedBadge.setVisibility(View.GONE);
        FrameLayout.LayoutParams sl = new FrameLayout.LayoutParams(Ui.WRAP, Ui.WRAP, Gravity.CENTER_HORIZONTAL | Gravity.TOP);
        sl.topMargin = Ui.dp(28);
        root.addView(speedBadge, sl);

        lockedIcon = Ui.iconButton(c, R.drawable.ic_lock, 52, 24);
        lockedIcon.setVisibility(View.GONE);
        lockedIcon.setOnClickListener(v -> setLocked(false));
        FrameLayout.LayoutParams ll = new FrameLayout.LayoutParams(Ui.dp(52), Ui.dp(52), Gravity.BOTTOM | Gravity.START);
        ll.setMargins(Ui.dp(24), 0, 0, Ui.dp(28));
        root.addView(lockedIcon, ll);

        // error box
        errorBox = Ui.col(c);
        errorBox.setGravity(Gravity.CENTER_HORIZONTAL);
        errorBox.setBackground(Ui.glassStrong(22));
        errorBox.setPadding(Ui.dp(22), Ui.dp(18), Ui.dp(22), Ui.dp(18));
        errorBox.addView(Ui.icon(c, R.drawable.ic_error, 30, Ui.LIVE));
        errorText = Ui.text(c, "", 14, 0xFFFFFFFF, Ui.MEDIUM);
        errorText.setGravity(Gravity.CENTER);
        errorBox.addView(errorText, Ui.margins(Ui.lp(Ui.WRAP, Ui.WRAP), 0, 10, 0, 14));
        LinearLayout eb = Ui.row(c);
        LinearLayout retry = Ui.pill(c, R.drawable.ic_refresh, getString(R.string.retry), true);
        retry.setOnClickListener(v -> {
            errorBox.setVisibility(View.GONE);
            resumeAt = Math.max(resumeAt, 0);
            releasePlayer();
            openPlayer();
        });
        eb.addView(retry);
        LinearLayout ext = Ui.pill(c, R.drawable.ic_open, getString(R.string.external_short), false);
        ext.setOnClickListener(v -> openExternal());
        eb.addView(ext, Ui.margins(Ui.lp(Ui.WRAP, Ui.WRAP), 10, 0, 0, 0));
        errorBox.addView(eb);
        errorBox.setVisibility(View.GONE);
        root.addView(errorBox, new FrameLayout.LayoutParams(Ui.WRAP, Ui.WRAP, Gravity.CENTER));

        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int l = insets.getSystemWindowInsetLeft(), t = insets.getSystemWindowInsetTop();
            int r = insets.getSystemWindowInsetRight(), b = insets.getSystemWindowInsetBottom();
            if (Build.VERSION.SDK_INT >= 28 && insets.getDisplayCutout() != null) {
                l = Math.max(l, insets.getDisplayCutout().getSafeInsetLeft());
                r = Math.max(r, insets.getDisplayCutout().getSafeInsetRight());
                t = Math.max(t, insets.getDisplayCutout().getSafeInsetTop());
            }
            topBar.setPadding(Ui.dp(12) + l, Ui.dp(14) + t, Ui.dp(12) + r, 0);
            bottomBar.setPadding(Ui.dp(12) + l, 0, Ui.dp(12) + r, Ui.dp(14) + b);
            return insets;
        });
    }

    private View buildAudioArt(Context c) {
        FrameLayout f = new FrameLayout(c);
        f.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF0B1A10, 0xFF07080C, 0xFF161028}));
        FrameLayout disc = new FrameLayout(c);
        disc.setBackground(Ui.glass(80));
        disc.addView(Ui.icon(c, R.drawable.ic_music, 64, Ui.ACCENT), new FrameLayout.LayoutParams(Ui.dp(64), Ui.dp(64), Gravity.CENTER));
        f.addView(disc, new FrameLayout.LayoutParams(Ui.dp(160), Ui.dp(160), Gravity.CENTER));
        return f;
    }

    private TextView ripple(Context c, boolean left) {
        TextView t = Ui.text(c, "", 15, 0xFFFFFFFF, Ui.BOLD);
        t.setGravity(Gravity.CENTER);
        t.setBackground(Ui.oval(0x33FFFFFF));
        t.setCompoundDrawablesWithIntrinsicBounds(0, left ? R.drawable.ic_rew : R.drawable.ic_fwd, 0, 0);
        t.setCompoundDrawableTintList(ColorStateList.valueOf(0xFFFFFFFF));
        t.setCompoundDrawablePadding(Ui.dp(4));
        t.setAlpha(0f);
        return t;
    }

    private View seekButton(Context c, boolean forward) {
        FrameLayout f = new FrameLayout(c);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(0x26FFFFFF);
        f.setBackground(Ui.rippleOval(bg));
        LinearLayout col = Ui.col(c);
        col.setGravity(Gravity.CENTER);
        col.addView(Ui.icon(c, forward ? R.drawable.ic_fwd : R.drawable.ic_rew, 24, 0xFFFFFFFF));
        TextView t = Ui.text(c, String.valueOf(Prefs.seekStep()), 11, 0xFFFFFFFF, Ui.BOLD);
        col.addView(t);
        f.addView(col, new FrameLayout.LayoutParams(Ui.WRAP, Ui.WRAP, Gravity.CENTER));
        f.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(58), Ui.dp(58)));
        Ui.pressable(f);
        return f;
    }

    // ------------------------------------------------------------------ player

    @Override public void onSurfaceTextureAvailable(SurfaceTexture st, int w, int hgt) {
        surface = new Surface(st);
        if (mp != null) {
            mp.setSurface(surface);
        } else {
            openPlayer();
        }
        applyTransform();
    }

    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture st, int w, int hgt) { applyTransform(); }

    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture st) {
        if (mp != null) {
            try { mp.setSurface(null); } catch (Exception ignored) {}
        }
        if (surface != null) surface.release();
        surface = null;
        return true;
    }

    @Override public void onSurfaceTextureUpdated(SurfaceTexture st) {}

    private void openPlayer() {
        if (uri == null || surface == null) return;
        released = false;
        prepared = false;
        loading.setVisibility(View.VISIBLE);
        errorBox.setVisibility(View.GONE);
        mp = new MediaPlayer();
        try {
            Uri src = isStream && currentStreamUrl != null ? Uri.parse(currentStreamUrl) : uri;
            if (isStream) {
                Map<String, String> headers = new HashMap<>();
                headers.put("User-Agent", Http.UA);
                mp.setDataSource(this, src, headers);
            } else {
                mp.setDataSource(this, src);
            }
            mp.setSurface(surface);
            mp.setScreenOnWhilePlaying(true);
            mp.setOnPreparedListener(this::onPrepared);
            mp.setOnVideoSizeChangedListener((p, w, hh) -> {
                videoW = w;
                videoH = hh;
                audioArt.setVisibility(w == 0 || hh == 0 ? View.VISIBLE : View.GONE);
                applyTransform();
                autoRotate();
                updatePipParams();
            });
            mp.setOnBufferingUpdateListener((p, pct) -> seek.setBuffered(pct / 100f));
            mp.setOnInfoListener((p, what, extra) -> {
                if (what == MediaPlayer.MEDIA_INFO_BUFFERING_START) loading.setVisibility(View.VISIBLE);
                else if (what == MediaPlayer.MEDIA_INFO_BUFFERING_END || what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START)
                    loading.setVisibility(View.GONE);
                return false;
            });
            mp.setOnCompletionListener(p -> {
                if (live) return;
                completed = true;
                Prefs.position(posKey, 0);
                updatePlayIcon();
                showControls(false);
                if (sleepAt == -2) { sleepAt = 0; finish(); }
            });
            mp.setOnErrorListener((p, what, extra) -> {
                loading.setVisibility(View.GONE);
                showError(getString(R.string.player_error, what + "/" + extra));
                return true;
            });
            mp.prepareAsync();
        } catch (Exception e) {
            showError(e.getMessage() != null ? e.getMessage() : getString(R.string.err_unknown));
        }
    }

    private void onPrepared(MediaPlayer p) {
        prepared = true;
        loading.setVisibility(View.GONE);
        videoW = p.getVideoWidth();
        videoH = p.getVideoHeight();
        audioArt.setVisibility(videoW == 0 || videoH == 0 ? View.VISIBLE : View.GONE);
        applyTransform();
        autoRotate();
        if (boostDb > 0) applyBoost();
        long d = duration();
        timeDur.setText(Fmt.durationMs(d));
        if (resumeAt > 3000 && d > 0 && resumeAt < d - 5000) {
            final long at = resumeAt;
            p.seekTo(at, MediaPlayer.SEEK_PREVIOUS_SYNC);
            if (announceResume) {
                Toaster.show(this, R.drawable.ic_history, getString(R.string.resumed_at, Fmt.durationMs(at)),
                        getString(R.string.from_start), () -> seekTo(0, false));
            }
        }
        resumeAt = -1;
        announceResume = false;
        if (wantPlay) start();
        else updatePlayIcon();
        h.post(ticker);
        scheduleHide();
    }

    private void start() {
        if (mp == null || !prepared) return;
        if (completed) {
            completed = false;
            mp.seekTo(0);
        }
        mp.start();
        applySpeed();
        wantPlay = true;
        updatePlayIcon();
        updatePipParams();
    }

    private void pause() {
        if (mp == null || !prepared) return;
        if (mp.isPlaying()) mp.pause();
        wantPlay = false;
        updatePlayIcon();
        savePosition();
        updatePipParams();
    }

    private void togglePlay() {
        if (mp == null || !prepared) return;
        if (mp.isPlaying()) pause();
        else start();
        Ui.bounce(playBtn);
        scheduleHide();
    }

    private void updatePlayIcon() {
        boolean playing = mp != null && prepared && mp.isPlaying();
        playBtn.setImageResource(completed ? R.drawable.ic_refresh : playing ? R.drawable.ic_pause : R.drawable.ic_play);
    }

    private long position() {
        try { return mp != null && prepared ? mp.getCurrentPosition() : 0; } catch (Exception e) { return 0; }
    }

    private long duration() {
        try { return mp != null && prepared ? Math.max(0, mp.getDuration()) : 0; } catch (Exception e) { return 0; }
    }

    private void seekTo(long ms, boolean precise) {
        if (mp == null || !prepared || live) return;
        long d = duration();
        ms = Math.max(0, d > 0 ? Math.min(d - 300, ms) : ms);
        completed = false;
        mp.seekTo(ms, precise && !isStream ? MediaPlayer.SEEK_CLOSEST : MediaPlayer.SEEK_PREVIOUS_SYNC);
        seek.setProgress(d > 0 ? ms / (float) d : 0);
        timeCur.setText(Fmt.durationMs(ms));
        updatePlayIcon();
    }

    private void seekBy(long delta, boolean showRipple) {
        if (live) return;
        seekTo(position() + delta, false);
        if (showRipple) {
            Ui.tick(root);
            flashRipple(delta > 0, (int) (delta / 1000));
        }
        scheduleHide();
    }

    private void flashRipple(boolean right, int seconds) {
        TextView r = right ? rightRipple : leftRipple;
        r.setText((seconds > 0 ? "+" : "") + seconds + "s");
        r.animate().cancel();
        r.setAlpha(1f);
        r.setScaleX(0.85f);
        r.setScaleY(0.85f);
        r.animate().scaleX(1f).scaleY(1f).setDuration(200).setInterpolator(new OvershootInterpolator()).start();
        r.animate().alpha(0f).setStartDelay(650).setDuration(300).start();
    }

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (mp != null && prepared) {
                long d = duration(), p = position();
                if (!seek.isDragging() && gestureMode != 1) {
                    seek.setProgress(d > 0 ? p / (float) d : 0);
                    timeCur.setText(live ? getString(R.string.live_badge) : Fmt.durationMs(p));
                }
                if (d > 0) timeDur.setText(Fmt.durationMs(d));
                updatePlayIcon();
                if (sleepAt > 0 && System.currentTimeMillis() >= sleepAt) {
                    sleepAt = 0;
                    pause();
                    Toaster.show(PlayerActivity.this, R.drawable.ic_timer, getString(R.string.sleep_done));
                }
            }
            h.postDelayed(this, 500);
        }
    };

    private void savePosition() {
        if (mp == null || !prepared || live) return;
        long p = position(), d = duration();
        if (d > 0 && p > 3000 && p < d - 5000) Prefs.position(posKey, p);
        else if (d > 0 && p >= d - 5000) Prefs.position(posKey, 0);
    }

    private void releasePlayer() {
        h.removeCallbacks(ticker);
        if (enhancer != null) {
            try { enhancer.release(); } catch (Exception ignored) {}
            enhancer = null;
        }
        if (mp != null) {
            try { mp.release(); } catch (Exception ignored) {}
        }
        mp = null;
        prepared = false;
        released = true;
    }

    private void showError(String msg) {
        errorText.setText(msg);
        errorBox.setVisibility(View.VISIBLE);
        Ui.popIn(errorBox);
        showControls(false);
    }

    private void applySpeed() {
        if (mp == null || !prepared) return;
        try {
            if (mp.isPlaying()) {
                PlaybackParams pp = mp.getPlaybackParams();
                pp.setSpeed(longPressSpeed ? 2f : speed);
                mp.setPlaybackParams(pp);
            }
        } catch (Exception ignored) {}
        speedBtn.setText(fmtSpeed(speed));
    }

    private static String fmtSpeed(float s) {
        String v = s == (int) s ? String.valueOf((int) s) : String.valueOf(s);
        return v + "×";
    }

    private void applyBoost() {
        try {
            if (enhancer == null && mp != null) enhancer = new LoudnessEnhancer(mp.getAudioSessionId());
            if (enhancer != null) {
                enhancer.setTargetGain((int) (boostDb * 100));
                enhancer.setEnabled(boostDb > 0);
            }
        } catch (Exception e) {
            Toaster.show(this, R.drawable.ic_error, getString(R.string.boost_unsupported));
        }
    }

    // ------------------------------------------------------------------ video geometry

    private void applyTransform() {
        int vw = video.getWidth(), vh = video.getHeight();
        if (vw == 0 || vh == 0 || videoW == 0 || videoH == 0) return;
        float contentAspect;
        switch (aspect) {
            case AR_16_9: contentAspect = 16f / 9f; break;
            case AR_4_3: contentAspect = 4f / 3f; break;
            default: contentAspect = videoW / (float) videoH;
        }
        float viewAspect = vw / (float) vh;
        float sx, sy;
        if (aspect == AR_STRETCH) {
            sx = 1;
            sy = 1;
        } else if (aspect == AR_FILL) {
            if (contentAspect > viewAspect) { sy = 1; sx = contentAspect / viewAspect; } else { sx = 1; sy = viewAspect / contentAspect; }
        } else {
            if (contentAspect > viewAspect) { sx = 1; sy = viewAspect / contentAspect; } else { sy = 1; sx = contentAspect / viewAspect; }
        }
        sx *= zoom;
        sy *= zoom;
        Matrix m = new Matrix();
        m.setScale(sx, sy, vw / 2f, vh / 2f);
        // keep panning inside the scaled content
        float maxPx = Math.max(0, (vw * sx - vw) / 2f), maxPy = Math.max(0, (vh * sy - vh) / 2f);
        panX = Math.max(-maxPx, Math.min(maxPx, panX));
        panY = Math.max(-maxPy, Math.min(maxPy, panY));
        m.postTranslate(panX, panY);
        video.setTransform(m);
        video.invalidate();
    }

    private void cycleAspect() {
        aspect = (aspect + 1) % 5;
        zoom = 1f;
        panX = panY = 0;
        applyTransform();
        int[] names = {R.string.ar_fit, R.string.ar_fill, R.string.ar_stretch, R.string.ar_16_9, R.string.ar_4_3};
        showIndicator(R.drawable.ic_aspect, getString(names[aspect]), -1);
        hideIndicatorSoon();
        scheduleHide();
    }

    // ------------------------------------------------------------------ rotation & system UI

    private void autoRotate() {
        if (!Prefs.autoRotate() || rotMode != ROT_AUTO || videoW == 0 || videoH == 0 || inPip) return;
        setRequestedOrientation(videoW >= videoH ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                : ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
    }

    private void cycleRotation() {
        rotMode = (rotMode + 1) % 3;
        int label;
        switch (rotMode) {
            case ROT_LANDSCAPE:
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
                label = R.string.rot_landscape;
                break;
            case ROT_PORTRAIT:
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                label = R.string.rot_portrait;
                break;
            default:
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_USER);
                label = R.string.rot_auto;
        }
        rotBtn.animate().rotationBy(90).setDuration(300).start();
        showIndicator(R.drawable.ic_rotate, getString(label), -1);
        hideIndicatorSoon();
        scheduleHide();
    }

    private void immersive() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN);
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) immersive();
    }

    @Override public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        video.post(this::applyTransform);
    }

    // ------------------------------------------------------------------ controls visibility

    private final Runnable hideControls = () -> hideControls(true);

    private void scheduleHide() {
        h.removeCallbacks(hideControls);
        if (mp != null && prepared && mp.isPlaying()) h.postDelayed(hideControls, 3500);
    }

    private void showControls(boolean autoHide) {
        if (locked || inPip) return;
        controlsShown = true;
        controls.setVisibility(View.VISIBLE);
        controls.animate().alpha(1f).setDuration(200).start();
        topBar.animate().translationY(0).setDuration(250).setInterpolator(new DecelerateInterpolator()).start();
        bottomBar.animate().translationY(0).setDuration(250).setInterpolator(new DecelerateInterpolator()).start();
        if (autoHide) scheduleHide();
    }

    private void hideControls(boolean animate) {
        controlsShown = false;
        if (!animate) {
            controls.setAlpha(0f);
            controls.setVisibility(View.GONE);
            return;
        }
        controls.animate().alpha(0f).setDuration(250).withEndAction(() -> {
            if (!controlsShown) controls.setVisibility(View.GONE);
        }).start();
        topBar.animate().translationY(-Ui.dp(20)).setDuration(250).start();
        bottomBar.animate().translationY(Ui.dp(20)).setDuration(250).start();
        immersive();
    }

    private void toggleControls() {
        if (controlsShown) hideControls(true);
        else showControls(true);
    }

    private void setLocked(boolean l) {
        locked = l;
        Ui.haptic(root);
        if (l) {
            hideControls(true);
            flashLock();
            Toaster.show(this, R.drawable.ic_lock, getString(R.string.locked_hint));
        } else {
            lockedIcon.setVisibility(View.GONE);
            showControls(true);
        }
    }

    private void flashLock() {
        lockedIcon.setVisibility(View.VISIBLE);
        lockedIcon.setAlpha(1f);
        Ui.popIn(lockedIcon);
        h.removeCallbacks(fadeLock);
        h.postDelayed(fadeLock, 2500);
    }

    private final Runnable fadeLock = () -> lockedIcon.animate().alpha(0f).setDuration(300)
            .withEndAction(() -> { if (locked) lockedIcon.setVisibility(View.GONE); }).start();

    // ------------------------------------------------------------------ indicator

    private void showIndicator(int icon, String text, float level) {
        h.removeCallbacks(hideIndicator);
        indicatorIcon.setImageResource(icon);
        indicatorText.setText(text);
        if (level >= 0) {
            indicatorTrack.setVisibility(View.VISIBLE);
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) indicatorBar.getLayoutParams();
            lp.width = (int) (Ui.dp(120) * Math.max(0, Math.min(1, level)));
            indicatorBar.setLayoutParams(lp);
        } else {
            indicatorTrack.setVisibility(View.GONE);
        }
        if (indicator.getAlpha() < 1f) indicator.animate().alpha(1f).setDuration(120).start();
    }

    private final Runnable hideIndicator = () -> indicator.animate().alpha(0f).setDuration(250).start();

    private void hideIndicatorSoon() {
        h.removeCallbacks(hideIndicator);
        h.postDelayed(hideIndicator, 700);
    }

    // ------------------------------------------------------------------ gestures

    private void setupGestures(View touch) {
        sgd = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScaleBegin(ScaleGestureDetector d) { return !locked; }

            @Override public boolean onScale(ScaleGestureDetector d) {
                zoom = Math.max(1f, Math.min(4f, zoom * d.getScaleFactor()));
                applyTransform();
                showIndicator(R.drawable.ic_aspect, String.format(Locale.US, "%.1f×", zoom), (zoom - 1) / 3f);
                return true;
            }

            @Override public void onScaleEnd(ScaleGestureDetector d) {
                if (zoom < 1.08f) {
                    zoom = 1f;
                    panX = panY = 0;
                    applyTransform();
                }
                hideIndicatorSoon();
            }
        });
        gd = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) {
                gestureMode = 0;
                return true;
            }

            @Override public boolean onSingleTapUp(MotionEvent e) {
                // Inside a double-tap seek streak, each extra tap seeks again on the same side.
                if (!locked && !live && System.currentTimeMillis() - lastDoubleTap < 700) {
                    int side = sideOf(e.getX());
                    if (side != 0 && side == seekSide) {
                        doubleTapSeek(side);
                        return true;
                    }
                }
                return false;
            }

            @Override public boolean onSingleTapConfirmed(MotionEvent e) {
                if (System.currentTimeMillis() - lastDoubleTap < 700) return true;
                if (locked) {
                    flashLock();
                    return true;
                }
                toggleControls();
                return true;
            }

            @Override public boolean onDoubleTap(MotionEvent e) {
                if (locked) return true;
                int side = sideOf(e.getX());
                if (side == 0 || live) {
                    togglePlay();
                    return true;
                }
                if (System.currentTimeMillis() - lastDoubleTap > 700 || side != seekSide) seekAccum = 0;
                doubleTapSeek(side);
                return true;
            }

            @Override public void onLongPress(MotionEvent e) {
                if (locked || live || mp == null || !prepared || !mp.isPlaying()) return;
                longPressSpeed = true;
                applySpeed();
                speedBadge.setVisibility(View.VISIBLE);
                Ui.popIn(speedBadge);
                Ui.haptic(root);
            }

            @Override public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) {
                if (locked || e1 == null || sgd.isInProgress() || e2.getPointerCount() > 1) return false;
                float W = root.getWidth(), H = root.getHeight();
                if (zoom > 1.01f) { // pan the zoomed picture
                    panX -= dx;
                    panY -= dy;
                    applyTransform();
                    return true;
                }
                if (gestureMode == 0) {
                    if (e1.getY() < H * 0.08f || e1.getY() > H * 0.92f) return false; // system gesture zones
                    float tx = e2.getX() - e1.getX(), ty = e2.getY() - e1.getY();
                    if (Math.abs(tx) < Ui.dp(12) && Math.abs(ty) < Ui.dp(12)) return false;
                    if (Math.abs(tx) > Math.abs(ty)) {
                        if (live || !Prefs.gestures()) return false;
                        gestureMode = 1;
                        scrubStartPos = position();
                    } else {
                        if (!Prefs.gestures()) return false;
                        gestureMode = e1.getX() < W / 2 ? 2 : 3;
                        if (gestureMode == 2) startVal = currentBrightness();
                        else {
                            int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                            volFloat = audio.getStreamVolume(AudioManager.STREAM_MUSIC) / (float) max;
                            startVal = volFloat;
                        }
                    }
                }
                float totalX = e2.getX() - e1.getX(), totalY = e1.getY() - e2.getY();
                if (gestureMode == 1) {
                    long d = duration();
                    long range = Math.min(d > 0 ? d : 600_000, 600_000); // full width = up to 10 min
                    long delta = (long) (totalX / W * range);
                    scrubTarget = Math.max(0, Math.min(d, scrubStartPos + delta));
                    seek.setProgress(d > 0 ? scrubTarget / (float) d : 0);
                    timeCur.setText(Fmt.durationMs(scrubTarget));
                    showIndicator(delta >= 0 ? R.drawable.ic_fwd : R.drawable.ic_rew,
                            (delta >= 0 ? "+" : "−") + Fmt.durationMs(Math.abs(delta)) + "\n" + Fmt.durationMs(scrubTarget) + " / " + Fmt.durationMs(d),
                            d > 0 ? scrubTarget / (float) d : 0);
                } else if (gestureMode == 2) {
                    float v = Math.max(0.01f, Math.min(1f, startVal + totalY / (H * 0.7f)));
                    WindowManager.LayoutParams lp = getWindow().getAttributes();
                    lp.screenBrightness = v;
                    getWindow().setAttributes(lp);
                    showIndicator(R.drawable.ic_brightness, Math.round(v * 100) + "%", v);
                } else if (gestureMode == 3) {
                    int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                    float v = Math.max(0f, Math.min(1f, startVal + totalY / (H * 0.7f)));
                    int idx = Math.round(v * max);
                    if (idx != audio.getStreamVolume(AudioManager.STREAM_MUSIC)) {
                        audio.setStreamVolume(AudioManager.STREAM_MUSIC, idx, 0);
                        Ui.tick(root);
                    }
                    showIndicator(idx == 0 ? R.drawable.ic_volume_off : R.drawable.ic_volume, Math.round(v * 100) + "%", v);
                }
                return true;
            }
        });
        gd.setIsLongpressEnabled(true);
        touch.setOnTouchListener((v, e) -> {
            sgd.onTouchEvent(e);
            gd.onTouchEvent(e);
            int act = e.getActionMasked();
            if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) {
                if (gestureMode == 1 && scrubTarget >= 0) {
                    seekTo(scrubTarget, true);
                    scrubTarget = -1;
                }
                if (gestureMode != 0) hideIndicatorSoon();
                gestureMode = 0;
                if (longPressSpeed) {
                    longPressSpeed = false;
                    applySpeed();
                    speedBadge.animate().alpha(0f).setDuration(200).withEndAction(() -> {
                        speedBadge.setVisibility(View.GONE);
                        speedBadge.setAlpha(1f);
                    }).start();
                }
            }
            return true;
        });
    }

    private int sideOf(float x) {
        float W = root.getWidth();
        if (x < W * 0.38f) return -1;
        if (x > W * 0.62f) return 1;
        return 0;
    }

    private void doubleTapSeek(int side) {
        lastDoubleTap = System.currentTimeMillis();
        seekSide = side;
        int step = Prefs.seekStep();
        seekAccum += step;
        seekTo(position() + side * step * 1000L, false);
        flashRipple(side > 0, side * seekAccum);
        Ui.tick(root);
    }

    private float currentBrightness() {
        float b = getWindow().getAttributes().screenBrightness;
        if (b >= 0) return b;
        try {
            return android.provider.Settings.System.getInt(getContentResolver(), android.provider.Settings.System.SCREEN_BRIGHTNESS) / 255f;
        } catch (Exception e) {
            return 0.5f;
        }
    }

    // ------------------------------------------------------------------ menus

    private void speedMenu() {
        Sheet s = new Sheet(this).title(getString(R.string.speed), null);
        for (final float sp : SPEEDS) {
            boolean on = Math.abs(sp - speed) < 0.01f;
            s.item(on ? R.drawable.ic_check : 0, fmtSpeed(sp) + (sp == 1f ? "  · " + getString(R.string.normal) : ""),
                    on ? Ui.ACCENT : Ui.TEXT, () -> {
                        speed = sp;
                        applySpeed();
                        Ui.bounce(speedBtn);
                    });
        }
        s.show();
        h.removeCallbacks(hideControls);
    }

    private void loadVariants(final String master) {
        Bg.run(() -> Hls.parseMaster(Http.text(master), master), vs -> {
            if (vs.size() > 1) {
                variants = vs;
                qualityBtn.setVisibility(View.VISIBLE);
                qualityBtn.setText("AUTO");
            }
        }, e -> { });
    }

    private void qualityMenu() {
        if (variants == null) return;
        Sheet s = new Sheet(this).title(getString(R.string.quality), null);
        final String masterUrl = uri.toString();
        boolean auto = masterUrl.equals(currentStreamUrl);
        s.item(auto ? R.drawable.ic_check : 0, getString(R.string.auto), auto ? Ui.ACCENT : Ui.TEXT, () -> switchStream(masterUrl, "AUTO"));
        for (final Hls.Variant v : variants) {
            boolean on = v.url.equals(currentStreamUrl);
            s.item(on ? R.drawable.ic_check : 0, v.label(), on ? Ui.ACCENT : Ui.TEXT, () -> switchStream(v.url, v.label()));
        }
        s.show();
    }

    private void switchStream(String url, String label) {
        if (url.equals(currentStreamUrl)) return;
        resumeAt = live ? -1 : position();
        wantPlay = true;
        currentStreamUrl = url;
        qualityBtn.setText(label.length() > 8 ? label.substring(0, 8) : label);
        releasePlayer();
        openPlayer();
    }

    private void moreMenu() {
        h.removeCallbacks(hideControls);
        Sheet s = new Sheet(this).title(title, subView.getText().toString().isEmpty() ? null : subView.getText().toString());
        s.item(R.drawable.ic_volume, getString(R.string.audio_boost) + (boostDb > 0 ? " · +" + (int) boostDb + " dB" : ""), this::boostMenu);
        s.item(R.drawable.ic_timer, getString(R.string.sleep_timer) + (sleepAt > 0 ? " · " + Fmt.durationMs(sleepAt - System.currentTimeMillis()) : ""), this::sleepMenu);
        s.item(R.drawable.ic_lock, getString(R.string.lock_screen), () -> setLocked(true));
        s.item(R.drawable.ic_open, getString(R.string.player_external), this::openExternal);
        if (!isStream) s.item(R.drawable.ic_share, getString(R.string.share_file), () -> {
            Intent i = new Intent(Intent.ACTION_SEND).setType("video/*").putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(i, title));
        });
        s.item(R.drawable.ic_info, getString(R.string.video_info), this::infoSheet);
        s.show();
    }

    private void boostMenu() {
        Sheet s = new Sheet(this).title(getString(R.string.audio_boost), getString(R.string.audio_boost_hint));
        float[] opts = {0, 3, 6, 10, 15};
        for (final float o : opts) {
            boolean on = Math.abs(o - boostDb) < 0.1f;
            s.item(on ? R.drawable.ic_check : 0, o == 0 ? getString(R.string.off) : "+" + (int) o + " dB", on ? Ui.ACCENT : Ui.TEXT, () -> {
                boostDb = o;
                applyBoost();
            });
        }
        s.show();
    }

    private void sleepMenu() {
        Sheet s = new Sheet(this).title(getString(R.string.sleep_timer), null);
        int[] mins = {0, 15, 30, 45, 60, 90};
        for (final int m : mins) {
            s.item(0, m == 0 ? getString(R.string.off) : getString(R.string.minutes_fmt, m), () -> {
                sleepAt = m == 0 ? 0 : System.currentTimeMillis() + m * 60_000L;
                if (m > 0) Toaster.show(this, R.drawable.ic_timer, getString(R.string.sleep_set, m));
            });
        }
        if (!live) s.item(0, getString(R.string.end_of_video), () -> {
            sleepAt = -2;
            Toaster.show(this, R.drawable.ic_timer, getString(R.string.sleep_end_set));
        });
        s.show();
    }

    private void infoSheet() {
        Sheet s = new Sheet(this).title(getString(R.string.video_info), null);
        StringBuilder b = new StringBuilder();
        if (videoW > 0) b.append(getString(R.string.info_resolution)).append(": ").append(videoW).append(" × ").append(videoH).append('\n');
        long d = duration();
        if (d > 0) b.append(getString(R.string.info_duration)).append(": ").append(Fmt.durationMs(d)).append('\n');
        b.append(getString(R.string.speed)).append(": ").append(fmtSpeed(speed)).append('\n');
        b.append(getString(R.string.info_source)).append(": ").append(isStream ? currentStreamUrl : uri.toString());
        TextView t = Ui.text(this, b.toString(), 13, Ui.TEXT2, Ui.REGULAR);
        t.setTextIsSelectable(true);
        s.view(t);
        s.show();
    }

    private void openExternal() {
        Intent i = new Intent(Intent.ACTION_VIEW);
        String mime = isStream ? (currentStreamUrl != null && currentStreamUrl.contains(".mp4") ? "video/mp4" : "application/x-mpegURL") : "video/*";
        i.setDataAndType(isStream && currentStreamUrl != null ? Uri.parse(currentStreamUrl) : uri, mime);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        i.putExtra("position", (int) position());
        i.putExtra("title", title);
        try {
            pause();
            startActivity(Intent.createChooser(i, getString(R.string.open_with)));
        } catch (Exception e) {
            Toaster.show(this, R.drawable.ic_error, getString(R.string.no_player));
        }
    }

    private void screenshot() {
        final Bitmap bmp = video.getBitmap();
        if (bmp == null) return;
        root.animate().alpha(0.4f).setDuration(80).withEndAction(() -> root.animate().alpha(1f).setDuration(200).start()).start();
        Ui.haptic(root);
        final String name = "KickDL_" + System.currentTimeMillis() + ".jpg";
        Bg.run(() -> {
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues v = new ContentValues();
                v.put(MediaStore.Images.Media.DISPLAY_NAME, name);
                v.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                v.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/KickDL");
                Uri u = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
                if (u == null) throw new Exception("insert");
                try (OutputStream os = getContentResolver().openOutputStream(u)) {
                    bmp.compress(Bitmap.CompressFormat.JPEG, 95, os);
                }
                return "Pictures/KickDL";
            }
            File dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            File f = new File(dir, name);
            try (FileOutputStream os = new FileOutputStream(f)) {
                bmp.compress(Bitmap.CompressFormat.JPEG, 95, os);
            }
            return f.getParent();
        }, where -> Toaster.show(this, R.drawable.ic_camera, getString(R.string.screenshot_saved, where)),
                e -> Toaster.show(this, R.drawable.ic_error, getString(R.string.screenshot_failed)));
    }

    // ------------------------------------------------------------------ picture in picture

    private void enterPip() {
        if (!getPackageManager().hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            Toaster.show(this, R.drawable.ic_error, getString(R.string.pip_unsupported));
            return;
        }
        try {
            enterPictureInPictureMode(pipParams());
        } catch (Exception e) {
            Toaster.show(this, R.drawable.ic_error, getString(R.string.pip_unsupported));
        }
    }

    private PictureInPictureParams pipParams() {
        PictureInPictureParams.Builder b = new PictureInPictureParams.Builder();
        if (videoW > 0 && videoH > 0) {
            float r = videoW / (float) videoH;
            r = Math.max(1 / 2.3f, Math.min(2.3f, r));
            b.setAspectRatio(new Rational(Math.round(r * 1000), 1000));
        }
        List<RemoteAction> actions = new ArrayList<>();
        boolean playing = mp != null && prepared && mp.isPlaying();
        if (!live) actions.add(pipAction(1, R.drawable.ic_rew, getString(R.string.back10)));
        actions.add(pipAction(0, playing ? R.drawable.ic_pause : R.drawable.ic_play, getString(playing ? R.string.pause : R.string.play)));
        if (!live) actions.add(pipAction(2, R.drawable.ic_fwd, getString(R.string.fwd10)));
        b.setActions(actions);
        return b.build();
    }

    private RemoteAction pipAction(int code, int icon, String label) {
        Intent i = new Intent(ACTION_PIP).setPackage(getPackageName()).putExtra("code", code);
        PendingIntent pi = PendingIntent.getBroadcast(this, code, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new RemoteAction(Icon.createWithResource(this, icon), label, label, pi);
    }

    private void updatePipParams() {
        if (Build.VERSION.SDK_INT >= 26 && inPip) {
            try { setPictureInPictureParams(pipParams()); } catch (Exception ignored) {}
        }
    }

    @Override protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (mp != null && prepared && mp.isPlaying() && !isFinishing()
                && getPackageManager().hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            try { enterPictureInPictureMode(pipParams()); } catch (Exception ignored) {}
        }
    }

    @Override public void onPictureInPictureModeChanged(boolean pip, Configuration cfg) {
        super.onPictureInPictureModeChanged(pip, cfg);
        inPip = pip;
        if (pip) {
            hideControls(false);
            Sheet.dismissAll(this);
            pipReceiver = new BroadcastReceiver() {
                @Override public void onReceive(Context c, Intent i) {
                    int code = i.getIntExtra("code", 0);
                    if (code == 0) togglePlay();
                    else if (code == 1) seekBy(-Prefs.seekStep() * 1000L, false);
                    else seekBy(Prefs.seekStep() * 1000L, false);
                    updatePipParams();
                }
            };
            IntentFilter f = new IntentFilter(ACTION_PIP);
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(pipReceiver, f, Context.RECEIVER_NOT_EXPORTED);
            else registerReceiver(pipReceiver, f);
            updatePipParams();
        } else {
            if (pipReceiver != null) {
                try { unregisterReceiver(pipReceiver); } catch (Exception ignored) {}
                pipReceiver = null;
            }
            showControls(true);
        }
    }

    // ------------------------------------------------------------------ keys & lifecycle

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                seekBy(-Prefs.seekStep() * 1000L, true);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                seekBy(Prefs.seekStep() * 1000L, true);
                return true;
            case KeyEvent.KEYCODE_SPACE:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_HEADSETHOOK:
                togglePlay();
                return true;
            case KeyEvent.KEYCODE_MEDIA_PLAY:
                start();
                return true;
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                pause();
                return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override public void onBackPressed() {
        if (Sheet.handleBack(this)) return;
        if (locked) {
            flashLock();
            return;
        }
        super.onBackPressed();
    }

    @Override protected void onPause() {
        super.onPause();
        if (!isInPictureInPictureMode()) {
            boolean was = mp != null && prepared && mp.isPlaying();
            pause();
            wantPlay = was;
        }
    }

    @Override protected void onResume() {
        super.onResume();
        immersive();
        if (mp != null && prepared && wantPlay && !completed) start();
    }

    @Override protected void onStop() {
        super.onStop();
        savePosition();
        if (mp != null && prepared && !live) resumeAt = position();
        if (isFinishing() || !isInPictureInPictureMode()) {
            boolean was = wantPlay;
            releasePlayer();
            wantPlay = was;
        }
    }

    @Override protected void onStart() {
        super.onStart();
        if (released && surface != null && uri != null) openPlayer();
    }

    @Override protected void onDestroy() {
        savePosition();
        releasePlayer();
        h.removeCallbacksAndMessages(null);
        Sheet.forget(this);
        if (pipReceiver != null) {
            try { unregisterReceiver(pipReceiver); } catch (Exception ignored) {}
        }
        super.onDestroy();
    }

    @Override public void finish() {
        savePosition();
        super.finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }
}
