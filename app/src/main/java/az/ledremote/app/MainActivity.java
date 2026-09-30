package az.ledremote.app;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
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
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int BG = 0xFF0F131A;
    private static final int CARD = 0xFF1B1D21;
    private static final int CARD_STROKE = 0xFF2C2F36;
    private static final int TXT = 0xFFE8EAED;
    private static final int TXT_DIM = 0xFF9AA0AA;
    private static final long REPEAT_DELAY_MS = 450;
    private static final long REPEAT_PERIOD_MS = 108;

    private static final int DARK = 0xFF1E1E1E;
    private static final int LIGHT = 0xFFF4F4F4;

    private Prefs prefs;
    private IrController ir;
    private TextView status;
    private boolean editMode;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable repeater;

    // label, kind, top, bottom, text color
    private Object[][] layout() {
        return new Object[][]{
            {"", RemoteButton.KIND_SUN_BIG, 0xFFFFFFFF, 0xFFC9C9C9, DARK},
            {"", RemoteButton.KIND_SUN_SMALL, 0xFFFFFFFF, 0xFFC9C9C9, DARK},
            {"OFF", RemoteButton.KIND_TEXT, 0xFF2E2E2E, 0xFF0B0B0B, LIGHT},
            {"ON", RemoteButton.KIND_TEXT, 0xFFE86363, 0xFFB02B2B, LIGHT},

            {"R", RemoteButton.KIND_TEXT, 0xFFE05252, 0xFFB02C2C, LIGHT},
            {"G", RemoteButton.KIND_TEXT, 0xFF62AE70, 0xFF3A7D4A, LIGHT},
            {"B", RemoteButton.KIND_TEXT, 0xFF4676BD, 0xFF23487F, LIGHT},
            {"W", RemoteButton.KIND_TEXT, 0xFFFFFFFF, 0xFFC9C9C9, DARK},

            {"", RemoteButton.KIND_COLOR, 0xFFEDB077, 0xFFC98A52, 0},
            {"", RemoteButton.KIND_COLOR, 0xFF86C488, 0xFF5EA468, 0},
            {"", RemoteButton.KIND_COLOR, 0xFF69A4D8, 0xFF4B7FB3, 0},
            {"FLASH", RemoteButton.KIND_TEXT, 0xFF93A0AD, 0xFF5A6876, 0xFFF1F3F5},

            {"", RemoteButton.KIND_COLOR, 0xFFEE9B66, 0xFFC0692F, 0},
            {"", RemoteButton.KIND_COLOR, 0xFF9AD3C2, 0xFF72AE9D, 0},
            {"", RemoteButton.KIND_COLOR, 0xFF7560AE, 0xFF3A2C72, 0},
            {"STROBE", RemoteButton.KIND_TEXT, 0xFF93A0AD, 0xFF5A6876, 0xFFF1F3F5},

            {"", RemoteButton.KIND_COLOR, 0xFFF7CE62, 0xFFD6A13F, 0},
            {"", RemoteButton.KIND_COLOR, 0xFF53ADA3, 0xFF2F827B, 0},
            {"", RemoteButton.KIND_COLOR, 0xFFA068AE, 0xFF744184, 0},
            {"FADE", RemoteButton.KIND_TEXT, 0xFF93A0AD, 0xFF5A6876, 0xFFF1F3F5},

            {"", RemoteButton.KIND_COLOR, 0xFFFBF76E, 0xFFDDD545, 0},
            {"", RemoteButton.KIND_COLOR, 0xFF5199AB, 0xFF2F6E80, 0},
            {"", RemoteButton.KIND_COLOR, 0xFFDB95BF, 0xFFB8739D, 0},
            {"SMOOTH", RemoteButton.KIND_TEXT, 0xFF93A0AD, 0xFF5A6876, 0xFFF1F3F5},
        };
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = new Prefs(this);
        ir = new IrController(this);
        setContentView(buildUi());
        refreshStatus();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopRepeat();
    }

    // ---------------------------------------------------------------- UI

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private View buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BG);
        root.setFitsSystemWindows(true);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER);
        column.setPadding(dp(16), dp(72), dp(16), dp(24));
        scroll.addView(column, new ViewGroup.LayoutParams(-1, -2));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(18), dp(14), dp(18));
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(CARD);
        cardBg.setCornerRadius(dp(28));
        cardBg.setStroke(dp(1), CARD_STROKE);
        card.setBackground(cardBg);
        card.setElevation(dp(8));

        int maxCard = dp(360);
        int avail = getResources().getDisplayMetrics().widthPixels - dp(40);
        column.addView(card, new LinearLayout.LayoutParams(Math.min(maxCard, avail), -2));

        Object[][] spec = layout();
        for (int row = 0; row < 6; row++) {
            LinearLayout line = new LinearLayout(this);
            line.setOrientation(LinearLayout.HORIZONTAL);
            for (int col = 0; col < 4; col++) {
                int i = row * 4 + col;
                Object[] s = spec[i];
                RemoteButton b = new RemoteButton(this, i, (Integer) s[1], (String) s[0],
                        (Integer) s[2], (Integer) s[3], (Integer) s[4]);
                b.setContentDescription(describe(i, (String) s[0]));
                b.setListener(new RemoteButton.Listener() {
                    @Override public void onDown(RemoteButton v) { onKeyDown(v); }
                    @Override public void onUp(RemoteButton v) { stopRepeat(); }
                });
                line.addView(b, new LinearLayout.LayoutParams(0, -2, 1f));
            }
            card.addView(line, new LinearLayout.LayoutParams(-1, -2));
        }

        status = new TextView(this);
        status.setTextSize(13);
        status.setGravity(Gravity.CENTER);
        status.setPadding(dp(8), dp(18), dp(8), 0);
        column.addView(status, new LinearLayout.LayoutParams(-2, -2));

        View gear = new SettingsIcon(this);
        GradientDrawable gbg = new GradientDrawable();
        gbg.setColor(0xFF23262C);
        gbg.setCornerRadius(dp(14));
        gbg.setStroke(dp(1), CARD_STROKE);
        gear.setBackground(gbg);
        gear.setContentDescription(getString(R.string.settings));
        gear.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showSettings(); }
        });
        FrameLayout.LayoutParams glp = new FrameLayout.LayoutParams(dp(50), dp(50), Gravity.TOP | Gravity.END);
        glp.setMargins(0, dp(14), dp(18), 0);
        root.addView(gear, glp);
        return root;
    }

    private String describe(int i, String label) {
        if (!label.isEmpty()) return label;
        return i == 0 ? "Brightness +" : i == 1 ? "Brightness -" : "Color " + (i + 1);
    }

    private void refreshStatus() {
        if (editMode) {
            status.setText("✎  " + getString(R.string.edit_mode_hint));
            status.setTextColor(0xFFF0B429);
        } else if (ir.isAvailable()) {
            status.setText("●  " + getString(R.string.ir_ready));
            status.setTextColor(0xFF5CC97A);
        } else {
            status.setText("●  " + getString(R.string.ir_missing));
            status.setTextColor(0xFFE58A4A);
        }
    }

    // ---------------------------------------------------------------- keys

    private void onKeyDown(final RemoteButton b) {
        if (prefs.haptics()) b.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        if (editMode) {
            showCodeDialog(b.index);
            return;
        }
        if (!ir.isAvailable()) {
            Toast.makeText(this, R.string.ir_missing_toast, Toast.LENGTH_SHORT).show();
            return;
        }
        ir.send(prefs.code(b.index));
        if (prefs.holdRepeat() && b.index < 2) {
            stopRepeat();
            repeater = new Runnable() {
                @Override public void run() {
                    ir.repeat();
                    handler.postDelayed(this, REPEAT_PERIOD_MS);
                }
            };
            handler.postDelayed(repeater, REPEAT_DELAY_MS);
        }
    }

    private void stopRepeat() {
        if (repeater != null) {
            handler.removeCallbacks(repeater);
            repeater = null;
        }
    }

    // ---------------------------------------------------------------- dialogs

    private Dialog darkDialog(LinearLayout content) {
        Dialog d = new Dialog(this, android.R.style.Theme_Material_Dialog_NoActionBar);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD);
        bg.setCornerRadius(dp(24));
        bg.setStroke(dp(1), CARD_STROKE);
        content.setBackground(bg);
        content.setPadding(dp(22), dp(20), dp(22), dp(16));
        d.setContentView(content);
        d.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        d.getWindow().setLayout(Math.min(dp(340), getResources().getDisplayMetrics().widthPixels - dp(32)), -2);
        return d;
    }

    private TextView text(String s, float size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private Button flat(String s, int color) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextColor(color);
        b.setBackground(null);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        return b;
    }

    private Switch toggle(String label, boolean on, CompoundButton.OnCheckedChangeListener l) {
        Switch s = new Switch(this);
        s.setText(label);
        s.setTextSize(15);
        s.setTextColor(TXT);
        s.setChecked(on);
        s.setPadding(0, dp(10), 0, dp(10));
        s.setOnCheckedChangeListener(l);
        return s;
    }

    private void showSettings() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        final Dialog d = darkDialog(box);

        box.addView(text(getString(R.string.settings), 20, TXT, true));

        box.addView(toggle(getString(R.string.haptics), prefs.haptics(), new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton b, boolean c) { prefs.setHaptics(c); }
        }));
        box.addView(toggle(getString(R.string.hold_repeat), prefs.holdRepeat(), new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton b, boolean c) { prefs.setHoldRepeat(c); }
        }));

        TextView presetTitle = text(getString(R.string.preset), 13, TXT_DIM, false);
        presetTitle.setPadding(0, dp(12), 0, dp(2));
        box.addView(presetTitle);

        RadioGroup group = new RadioGroup(this);
        String[] names = {getString(R.string.preset_b), getString(R.string.preset_a)};
        for (int i = 0; i < names.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setId(100 + i);
            rb.setText(names[i]);
            rb.setTextColor(TXT);
            rb.setTextSize(14);
            group.addView(rb);
        }
        group.check(100 + prefs.preset());
        group.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(RadioGroup g, int id) { prefs.setPreset(id - 100); }
        });
        box.addView(group);

        box.addView(toggle(getString(R.string.edit_mode), editMode, new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton b, boolean c) {
                editMode = c;
                refreshStatus();
            }
        }));

        Button reset = flat(getString(R.string.reset_codes), 0xFFE58A4A);
        reset.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                prefs.clearAllCodes();
                Toast.makeText(MainActivity.this, R.string.reset_done, Toast.LENGTH_SHORT).show();
            }
        });
        box.addView(reset);

        TextView about = text(getString(R.string.about), 12, TXT_DIM, false);
        about.setGravity(Gravity.CENTER);
        about.setPadding(0, dp(6), 0, dp(4));
        box.addView(about);

        Button close = flat(getString(R.string.close), 0xFF6FB1FF);
        close.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { d.dismiss(); }
        });
        box.addView(close);
        d.setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
            @Override public void onDismiss(android.content.DialogInterface x) { refreshStatus(); }
        });
        d.show();
    }

    private void showCodeDialog(final int index) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        final Dialog d = darkDialog(box);

        box.addView(text(getString(R.string.code_title), 18, TXT, true));

        final EditText input = new EditText(this);
        input.setText(Codes.format(prefs.code(index)));
        input.setTextColor(TXT);
        input.setTextSize(22);
        input.setTypeface(Typeface.MONOSPACE);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(8), new InputFilter.AllCaps()});
        input.setSelectAllOnFocus(true);
        input.setHintTextColor(TXT_DIM);
        input.setPadding(0, dp(18), 0, dp(10));
        box.addView(input);

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END);
        Button def = flat(getString(R.string.reset_one), 0xFFE58A4A);
        Button cancel = flat(getString(R.string.cancel), TXT_DIM);
        Button save = flat(getString(R.string.save), 0xFF6FB1FF);
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
        d.show();
        d.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
    }

    // ---------------------------------------------------------------- icon

    /** Sliders ("tune") glyph for the settings button. */
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
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            float s = Math.min(w, h);
            p.setStrokeWidth(s * 0.06f);
            float left = w / 2 - s * 0.24f, right = w / 2 + s * 0.24f;
            float[] ys = {h / 2 - s * 0.17f, h / 2, h / 2 + s * 0.17f};
            float[] knob = {0.30f, 0.68f, 0.42f};
            for (int i = 0; i < 3; i++) {
                float kx = left + (right - left) * knob[i];
                p.setStyle(Paint.Style.STROKE);
                c.drawLine(left, ys[i], kx - s * 0.06f, ys[i], p);
                c.drawLine(kx + s * 0.06f, ys[i], right, ys[i], p);
                c.drawLine(kx, ys[i] - s * 0.075f, kx, ys[i] + s * 0.075f, p);
            }
        }
    }
}
