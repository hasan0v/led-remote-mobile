package az.ledremote.app;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int BG = 0xFF0F131A;
    private static final int CARD_TOP = 0xFF23262C;
    private static final int CARD_BOTTOM = 0xFF17191D;
    private static final int SHEET = 0xFF1A1C21;
    private static final int STROKE = 0xFF2F333A;
    private static final int TXT = 0xFFE8EAED;
    private static final int TXT_DIM = 0xFF8F96A1;
    private static final int ACCENT = 0xFF6FB1FF;
    private static final int WARN = 0xFFE58A4A;
    private static final long REPEAT_DELAY_MS = 450;
    private static final long REPEAT_PERIOD_MS = 108;

    private static final int DARK = 0xFF1E1E1E;
    private static final int LIGHT = 0xFFF4F4F4;
    private static final int MODE = 0xFFF1F3F5;

    private static final String[] NAMES = {
        "☀ +", "☀ −", "OFF", "ON", "R", "G", "B", "W",
        "Color 9", "Color 10", "Color 11", "FLASH", "Color 13", "Color 14", "Color 15", "STROBE",
        "Color 17", "Color 18", "Color 19", "FADE", "Color 21", "Color 22", "Color 23", "SMOOTH",
    };

    private Prefs prefs;
    private IrController ir;
    private Feedback feedback;
    private RemoteHead head;
    private TextView status;
    private boolean editMode;
    private long lastNoIrToast;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable repeater;
    private final Runnable restoreStatus = new Runnable() {
        @Override public void run() { refreshStatus(); }
    };

    // label, kind, top, bottom, text color
    private static final Object[][] KEYS = {
        {"", RemoteButton.KIND_SUN_BIG, 0xFFFFFFFF, 0xFFC4C4C4, DARK},
        {"", RemoteButton.KIND_SUN_SMALL, 0xFFFFFFFF, 0xFFC4C4C4, DARK},
        {"OFF", RemoteButton.KIND_TEXT, 0xFF363636, 0xFF0A0A0A, LIGHT},
        {"ON", RemoteButton.KIND_TEXT, 0xFFEA6666, 0xFFAE2A2A, LIGHT},

        {"R", RemoteButton.KIND_TEXT, 0xFFE35656, 0xFFAE2B2B, LIGHT},
        {"G", RemoteButton.KIND_TEXT, 0xFF66B274, 0xFF387B48, LIGHT},
        {"B", RemoteButton.KIND_TEXT, 0xFF4A7BC2, 0xFF22467C, LIGHT},
        {"W", RemoteButton.KIND_TEXT, 0xFFFFFFFF, 0xFFC4C4C4, DARK},

        {"", RemoteButton.KIND_COLOR, 0xFFEFB47B, 0xFFC5864E, 0},
        {"", RemoteButton.KIND_COLOR, 0xFF8AC88C, 0xFF5AA064, 0},
        {"", RemoteButton.KIND_COLOR, 0xFF6DA8DC, 0xFF477BAF, 0},
        {"FLASH", RemoteButton.KIND_TEXT, 0xFF97A4B1, 0xFF566472, MODE},

        {"", RemoteButton.KIND_COLOR, 0xFFF09F6A, 0xFFBC652B, 0},
        {"", RemoteButton.KIND_COLOR, 0xFF9ED7C6, 0xFF6EAA99, 0},
        {"", RemoteButton.KIND_COLOR, 0xFF7964B2, 0xFF36286E, 0},
        {"STROBE", RemoteButton.KIND_TEXT, 0xFF97A4B1, 0xFF566472, MODE},

        {"", RemoteButton.KIND_COLOR, 0xFFF8D266, 0xFFD29D3B, 0},
        {"", RemoteButton.KIND_COLOR, 0xFF57B1A7, 0xFF2B7E77, 0},
        {"", RemoteButton.KIND_COLOR, 0xFFA46CB2, 0xFF703D80, 0},
        {"FADE", RemoteButton.KIND_TEXT, 0xFF97A4B1, 0xFF566472, MODE},

        {"", RemoteButton.KIND_COLOR, 0xFFFCF872, 0xFFD9D141, 0},
        {"", RemoteButton.KIND_COLOR, 0xFF559DAF, 0xFF2B6A7C, 0},
        {"", RemoteButton.KIND_COLOR, 0xFFDD99C3, 0xFFB46F99, 0},
        {"SMOOTH", RemoteButton.KIND_TEXT, 0xFF97A4B1, 0xFF566472, MODE},
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = new Prefs(this);
        ir = new IrController(this);
        feedback = new Feedback(this);
        setContentView(buildUi(true));
        applyWindowPrefs();
        refreshStatus();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopRepeat();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ir.shutdown();
        feedback.destroy();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) applyWindowPrefs();
    }

    private void applyWindowPrefs() {
        Window w = getWindow();
        if (prefs.keepScreenOn()) w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else w.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        View d = w.getDecorView();
        d.setSystemUiVisibility(prefs.fullscreen()
                ? View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN
                  | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                : 0);
    }

    // ---------------------------------------------------------------- UI

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void rebuildUi() {
        stopRepeat();
        setContentView(buildUi(false));
        refreshStatus();
    }

    private View buildUi(boolean intro) {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BG);
        root.setFitsSystemWindows(true);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER);
        column.setPadding(dp(16), dp(76), dp(16), dp(28));
        column.setClipChildren(false);
        column.setClipToPadding(false);
        scroll.addView(column, new ViewGroup.LayoutParams(-1, -2));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(6), dp(12), dp(22));
        GradientDrawable cardBg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{CARD_TOP, CARD_BOTTOM});
        cardBg.setCornerRadius(dp(36));
        cardBg.setStroke(dp(1), STROKE);
        card.setBackground(cardBg);
        card.setElevation(dp(18));

        int[] widths = {300, 360, 430};
        int avail = getResources().getDisplayMetrics().widthPixels - dp(32);
        column.addView(card, new LinearLayout.LayoutParams(Math.min(dp(widths[prefs.size()]), avail), -2));

        head = new RemoteHead(this);
        card.addView(head, new LinearLayout.LayoutParams(-1, -2));
        View gap = new View(this);
        card.addView(gap, new LinearLayout.LayoutParams(-1, dp(6)));

        for (int row = 0; row < 6; row++) {
            LinearLayout line = new LinearLayout(this);
            line.setOrientation(LinearLayout.HORIZONTAL);
            line.setClipChildren(false);
            for (int col = 0; col < 4; col++) {
                int i = row * 4 + col;
                Object[] s = KEYS[i];
                RemoteButton b = new RemoteButton(this, i, (Integer) s[1], (String) s[0],
                        (Integer) s[2], (Integer) s[3], (Integer) s[4]);
                b.setContentDescription(NAMES[i]);
                b.setListener(new RemoteButton.Listener() {
                    @Override public void onDown(RemoteButton v) { onKeyDown(v); }
                    @Override public void onUp(RemoteButton v) { onKeyUp(); }
                });
                line.addView(b, new LinearLayout.LayoutParams(0, -2, 1f));
            }
            card.addView(line, new LinearLayout.LayoutParams(-1, -2));
        }

        status = new TextView(this);
        status.setTextSize(13);
        status.setGravity(Gravity.CENTER);
        status.setPadding(dp(14), dp(8), dp(14), dp(8));
        status.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (editMode) {
                    editMode = false;
                    refreshStatus();
                }
            }
        });
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-2, -2);
        slp.topMargin = dp(16);
        column.addView(status, slp);

        View gear = new SettingsIcon(this);
        GradientDrawable gbg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFF2A2E35, 0xFF1F2227});
        gbg.setCornerRadius(dp(16));
        gbg.setStroke(dp(1), STROKE);
        gear.setBackground(gbg);
        gear.setElevation(dp(6));
        gear.setContentDescription(getString(R.string.settings));
        gear.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showSettings(); }
        });
        FrameLayout.LayoutParams glp = new FrameLayout.LayoutParams(dp(50), dp(50), Gravity.TOP | Gravity.END);
        glp.setMargins(0, dp(14), dp(18), 0);
        root.addView(gear, glp);

        if (intro) {
            card.setAlpha(0f);
            card.setTranslationY(dp(28));
            card.animate().alpha(1f).translationY(0).setStartDelay(60).setDuration(420)
                    .setInterpolator(new DecelerateInterpolator(2f)).start();
        }
        return root;
    }

    private void refreshStatus() {
        handler.removeCallbacks(restoreStatus);
        GradientDrawable pill = new GradientDrawable();
        pill.setCornerRadius(dp(20));
        if (editMode) {
            status.setText("✎  " + getString(R.string.edit_mode_hint) + "   ✕");
            status.setTextColor(0xFFF0B429);
            pill.setColor(0x26F0B429);
        } else if (ir.isAvailable()) {
            status.setText("●  " + getString(R.string.ir_ready));
            status.setTextColor(0xFF5CC97A);
            pill.setColor(0x1A5CC97A);
        } else {
            status.setText("●  " + getString(R.string.ir_missing));
            status.setTextColor(WARN);
            pill.setColor(0x1AE58A4A);
        }
        status.setBackground(pill);
    }

    // ---------------------------------------------------------------- keys

    private void onKeyDown(final RemoteButton b) {
        feedback.press(prefs.vibration(), prefs.sound());
        if (editMode) {
            showCodeDialog(b.index);
            return;
        }
        if (!ir.isAvailable()) {
            long now = System.currentTimeMillis();
            if (now - lastNoIrToast > 3000) {
                lastNoIrToast = now;
                Toast.makeText(this, R.string.ir_missing_toast, Toast.LENGTH_SHORT).show();
            }
            status.animate().translationX(dp(6)).setDuration(50).withEndAction(new Runnable() {
                @Override public void run() { status.animate().translationX(0).setDuration(120).start(); }
            }).start();
            return;
        }
        int code = prefs.code(b.index);
        ir.send(code);
        blink();
        if (prefs.showCode()) {
            handler.removeCallbacks(restoreStatus);
            status.setText("↗  " + NAMES[b.index] + "   " + Codes.format(code));
            status.setTextColor(ACCENT);
            handler.postDelayed(restoreStatus, 1600);
        }
        if (prefs.holdRepeat() && b.index < 2) {
            stopRepeat();
            repeater = new Runnable() {
                @Override public void run() {
                    ir.repeat();
                    blink();
                    handler.postDelayed(this, REPEAT_PERIOD_MS);
                }
            };
            handler.postDelayed(repeater, REPEAT_DELAY_MS);
        }
    }

    private void onKeyUp() {
        stopRepeat();
        if (!editMode) feedback.release(prefs.vibration(), prefs.sound());
    }

    private void blink() {
        if (prefs.indicator()) head.flash();
    }

    private void stopRepeat() {
        if (repeater != null) {
            handler.removeCallbacks(repeater);
            repeater = null;
        }
    }

    // ---------------------------------------------------------------- settings sheet

    private TextView text(String s, float size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }

    private Button flat(String s, int color) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextColor(color);
        b.setTextSize(15);
        b.setBackground(null);
        b.setStateListAnimator(null);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        return b;
    }

    private LinearLayout section(LinearLayout parent, String title) {
        TextView t = text(title.toUpperCase(), 12, TXT_DIM, true);
        t.setLetterSpacing(0.08f);
        t.setPadding(dp(6), dp(18), 0, dp(8));
        parent.addView(t);
        LinearLayout group = new LinearLayout(this);
        group.setOrientation(LinearLayout.VERTICAL);
        group.setPadding(dp(16), dp(4), dp(16), dp(4));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF22252B);
        bg.setCornerRadius(dp(18));
        group.setBackground(bg);
        parent.addView(group, new LinearLayout.LayoutParams(-1, -2));
        return group;
    }

    private void divider(LinearLayout group) {
        View v = new View(this);
        v.setBackgroundColor(0xFF2C3037);
        group.addView(v, new LinearLayout.LayoutParams(-1, 1));
    }

    private void toggle(LinearLayout group, String label, boolean on, final CompoundButton.OnCheckedChangeListener l) {
        if (group.getChildCount() > 0) divider(group);
        Switch s = new Switch(this);
        s.setText(label);
        s.setTextSize(15);
        s.setTextColor(TXT);
        s.setChecked(on);
        s.setMinHeight(dp(52));
        s.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton b, boolean c) {
                feedback.press(prefs.vibration(), false);
                l.onCheckedChanged(b, c);
            }
        });
        group.addView(s, new LinearLayout.LayoutParams(-1, -2));
    }

    interface Choice { void onChoose(int i); }

    private void segmented(LinearLayout group, String label, String[] options, int selected, final Choice choice) {
        if (group.getChildCount() > 0) divider(group);
        TextView t = text(label, 15, TXT, false);
        t.setPadding(0, dp(14), 0, dp(10));
        group.addView(t);

        final LinearLayout bar = new LinearLayout(this);
        bar.setPadding(dp(3), dp(3), dp(3), dp(3));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF15171B);
        bg.setCornerRadius(dp(12));
        bar.setBackground(bg);
        final TextView[] cells = new TextView[options.length];
        for (int i = 0; i < options.length; i++) {
            final int idx = i;
            TextView c = text(options[i], 13.5f, TXT_DIM, true);
            c.setGravity(Gravity.CENTER);
            c.setSingleLine(true);
            c.setPadding(dp(4), dp(9), dp(4), dp(9));
            c.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    feedback.press(prefs.vibration(), false);
                    selectCell(cells, idx);
                    choice.onChoose(idx);
                }
            });
            cells[i] = c;
            bar.addView(c, new LinearLayout.LayoutParams(0, -2, 1f));
        }
        selectCell(cells, selected);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(14);
        group.addView(bar, lp);
    }

    private void selectCell(TextView[] cells, int sel) {
        for (int i = 0; i < cells.length; i++) {
            if (i == sel) {
                GradientDrawable on = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFF3A404B, 0xFF30353E});
                on.setCornerRadius(dp(10));
                cells[i].setBackground(on);
                cells[i].setTextColor(Color.WHITE);
                cells[i].setElevation(dp(2));
            } else {
                cells[i].setBackground(null);
                cells[i].setTextColor(TXT_DIM);
                cells[i].setElevation(0);
            }
        }
    }

    private void showSettings() {
        final Dialog d = new Dialog(this, R.style.DialogTheme);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(dp(18), dp(10), dp(18), dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(SHEET);
        float rad = dp(28);
        bg.setCornerRadii(new float[]{rad, rad, rad, rad, 0, 0, 0, 0});
        sheet.setBackground(bg);

        View grab = new View(this);
        GradientDrawable gb = new GradientDrawable();
        gb.setColor(0xFF4A4F58);
        gb.setCornerRadius(dp(3));
        grab.setBackground(gb);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(dp(40), dp(5));
        glp.gravity = Gravity.CENTER_HORIZONTAL;
        glp.bottomMargin = dp(10);
        sheet.addView(grab, glp);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(text(getString(R.string.settings), 22, TXT, true), new LinearLayout.LayoutParams(0, -2, 1f));
        Button done = flat(getString(R.string.close), ACCENT);
        done.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { d.dismiss(); }
        });
        header.addView(done);
        sheet.addView(header);

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(0, 0, 0, dp(8));
        scroll.addView(body);
        sheet.addView(scroll, new LinearLayout.LayoutParams(-1, -2));

        // Feedback
        LinearLayout fb = section(body, getString(R.string.sec_feedback));
        segmented(fb, getString(R.string.vibration), new String[]{
                getString(R.string.vib_off), getString(R.string.vib_light),
                getString(R.string.vib_medium), getString(R.string.vib_strong)}, prefs.vibration(), new Choice() {
            @Override public void onChoose(int i) {
                prefs.setVibration(i);
                feedback.press(i, false);
            }
        });
        toggle(fb, getString(R.string.sound), prefs.sound(), new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton b, boolean c) {
                prefs.setSound(c);
                if (c) feedback.press(Feedback.VIB_OFF, true);
            }
        });
        toggle(fb, getString(R.string.indicator), prefs.indicator(), new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton b, boolean c) {
                prefs.setIndicator(c);
                if (c) head.flash();
            }
        });

        // Remote
        LinearLayout rm = section(body, getString(R.string.sec_remote));
        segmented(rm, getString(R.string.preset), new String[]{
                getString(R.string.preset_std), getString(R.string.preset_alt)}, prefs.preset(), new Choice() {
            @Override public void onChoose(int i) { prefs.setPreset(i); }
        });
        toggle(rm, getString(R.string.hold_repeat), prefs.holdRepeat(), new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton b, boolean c) { prefs.setHoldRepeat(c); }
        });
        toggle(rm, getString(R.string.show_code), prefs.showCode(), new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton b, boolean c) { prefs.setShowCode(c); }
        });

        // Display
        LinearLayout ds = section(body, getString(R.string.sec_display));
        segmented(ds, getString(R.string.size), new String[]{
                getString(R.string.size_compact), getString(R.string.size_normal), getString(R.string.size_large)},
                prefs.size(), new Choice() {
            @Override public void onChoose(int i) {
                prefs.setSize(i);
                rebuildUi();
            }
        });
        toggle(ds, getString(R.string.keep_screen_on), prefs.keepScreenOn(), new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton b, boolean c) { prefs.setKeepScreenOn(c); applyWindowPrefs(); }
        });
        toggle(ds, getString(R.string.fullscreen), prefs.fullscreen(), new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton b, boolean c) { prefs.setFullscreen(c); applyWindowPrefs(); }
        });

        // Advanced
        LinearLayout adv = section(body, getString(R.string.sec_advanced));
        toggle(adv, getString(R.string.edit_mode), editMode, new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton b, boolean c) {
                editMode = c;
                refreshStatus();
                if (c) d.dismiss();
            }
        });
        divider(adv);
        Button reset = flat(getString(R.string.reset_codes), WARN);
        reset.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        reset.setPadding(0, 0, 0, 0);
        reset.setMinHeight(dp(52));
        reset.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                prefs.clearAllCodes();
                Toast.makeText(MainActivity.this, R.string.reset_done, Toast.LENGTH_SHORT).show();
            }
        });
        adv.addView(reset, new LinearLayout.LayoutParams(-1, -2));

        TextView about = text(getString(R.string.about), 12, TXT_DIM, false);
        about.setGravity(Gravity.CENTER);
        about.setPadding(0, dp(18), 0, dp(4));
        body.addView(about, new LinearLayout.LayoutParams(-1, -2));

        d.setContentView(sheet);
        Window w = d.getWindow();
        w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        w.setGravity(Gravity.BOTTOM);
        w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        w.setWindowAnimations(android.R.style.Animation_InputMethod);
        w.setDimAmount(0.55f);
        d.setCanceledOnTouchOutside(true);
        d.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override public void onDismiss(DialogInterface x) {
                refreshStatus();
                applyWindowPrefs();
            }
        });
        d.show();
        if (prefs.fullscreen()) w.getDecorView().setSystemUiVisibility(getWindow().getDecorView().getSystemUiVisibility());
    }

    // ---------------------------------------------------------------- code editor

    private void showCodeDialog(final int index) {
        final Dialog d = new Dialog(this, R.style.DialogTheme);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(SHEET);
        bg.setCornerRadius(dp(26));
        bg.setStroke(dp(1), STROKE);
        box.setBackground(bg);
        box.setPadding(dp(22), dp(20), dp(14), dp(10));

        box.addView(text(getString(R.string.code_title), 18, TXT, true));
        TextView key = text(NAMES[index] + (prefs.isCustom(index) ? "  ·  " + getString(R.string.custom) : ""), 13, TXT_DIM, false);
        key.setPadding(0, dp(4), 0, 0);
        box.addView(key);

        final EditText input = new EditText(this);
        input.setText(Codes.format(prefs.code(index)));
        input.setTextColor(TXT);
        input.setTextSize(24);
        input.setTypeface(Typeface.MONOSPACE);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(8), new InputFilter.AllCaps()});
        input.setSelectAllOnFocus(true);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(-1, -2);
        ilp.rightMargin = dp(8);
        box.addView(input, ilp);

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END);
        Button def = flat(getString(R.string.reset_one), WARN);
        Button cancel = flat(getString(R.string.cancel), TXT_DIM);
        Button save = flat(getString(R.string.save), ACCENT);
        actions.addView(def);
        actions.addView(cancel);
        actions.addView(save);
        box.addView(actions);

        def.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { prefs.clearCode(index); d.dismiss(); }
        });
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { d.dismiss(); }
        });
        save.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                long code = Codes.parse(input.getText().toString());
                if (code < 0) {
                    input.setError(getString(R.string.code_invalid));
                    return;
                }
                prefs.setCode(index, (int) code);
                d.dismiss();
            }
        });
        d.setContentView(box);
        Window w = d.getWindow();
        w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        w.setLayout(Math.min(dp(360), getResources().getDisplayMetrics().widthPixels - dp(32)), -2);
        w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        d.show();
    }

    // ---------------------------------------------------------------- icon

    /** Sliders ("tune") glyph for the settings button, with a press-in animation. */
    private static final class SettingsIcon extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        SettingsIcon(Context c) {
            super(c);
            p.setColor(0xFFE8EAED);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            setClickable(true);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_DOWN) animate().scaleX(0.9f).scaleY(0.9f).setDuration(60).start();
            else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL)
                animate().scaleX(1f).scaleY(1f).setDuration(160).start();
            return super.onTouchEvent(e);
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            float s = Math.min(w, h);
            p.setStrokeWidth(s * 0.055f);
            float left = w / 2 - s * 0.24f, right = w / 2 + s * 0.24f;
            float[] ys = {h / 2 - s * 0.17f, h / 2, h / 2 + s * 0.17f};
            float[] knob = {0.30f, 0.68f, 0.42f};
            for (int i = 0; i < 3; i++) {
                float kx = left + (right - left) * knob[i];
                c.drawLine(left, ys[i], kx - s * 0.06f, ys[i], p);
                c.drawLine(kx + s * 0.06f, ys[i], right, ys[i], p);
                c.drawLine(kx, ys[i] - s * 0.075f, kx, ys[i] + s * 0.075f, p);
            }
        }
    }
}
