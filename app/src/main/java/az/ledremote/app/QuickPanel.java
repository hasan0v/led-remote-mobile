package az.ledremote.app;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Compact remote shown on top of the control center (tile dialog or its fallback activity). */
final class QuickPanel {
    private final Context context;
    private final IrController ir;
    private final Feedback feedback;
    private final KeySender sender;
    private RemoteHead head;
    final View view;

    QuickPanel(Context c, final Runnable openApp) {
        context = c;
        Prefs prefs = new Prefs(c);
        ir = new IrController(c);
        feedback = new Feedback(c);
        sender = new KeySender(c, prefs, ir, feedback, new Runnable() {
            @Override public void run() { if (head != null) head.flash(); }
        });
        view = build(openApp);
    }

    private int dp(float v) {
        return Math.round(v * context.getResources().getDisplayMetrics().density);
    }

    private View build(final Runnable openApp) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(10), dp(4), dp(10), dp(12));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xF223262C, 0xF217191D});
        bg.setCornerRadius(dp(32));
        bg.setStroke(dp(1), 0xFF2F333A);
        card.setBackground(bg);
        card.setClipChildren(false);

        head = new RemoteHead(context);
        card.addView(head, new LinearLayout.LayoutParams(-1, -2));

        Keypad.addRows(context, card, new RemoteButton.Listener() {
            @Override public void onDown(RemoteButton v) {
                if (!sender.down(v.index)) {
                    android.widget.Toast.makeText(context, R.string.ir_missing_toast, android.widget.Toast.LENGTH_SHORT).show();
                }
            }
            @Override public void onUp(RemoteButton v) { sender.up(); }
        });

        TextView open = new TextView(context);
        open.setText(R.string.open_app);
        open.setTextSize(13);
        open.setTextColor(0xFF6FB1FF);
        open.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        open.setGravity(Gravity.CENTER);
        open.setPadding(dp(16), dp(10), dp(16), dp(2));
        open.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openApp.run(); }
        });
        card.addView(open, new LinearLayout.LayoutParams(-1, -2));
        return card;
    }

    int width() {
        return Math.min(dp(340), context.getResources().getDisplayMetrics().widthPixels - dp(28));
    }

    static Intent appIntent(Context c) {
        return new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    void destroy() {
        sender.stop();
        ir.shutdown();
        feedback.destroy();
    }
}
