package az.ledremote.app;

import android.app.Activity;
import android.os.Bundle;
import android.widget.FrameLayout;

/** Floating remote used when the control center does not allow tile dialogs. */
public class QuickRemoteActivity extends Activity {
    private QuickPanel panel;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        panel = new QuickPanel(this, new Runnable() {
            @Override public void run() {
                startActivity(QuickPanel.appIntent(QuickRemoteActivity.this));
                finish();
            }
        });
        FrameLayout wrap = new FrameLayout(this);
        wrap.addView(panel.view, new FrameLayout.LayoutParams(panel.width(), -2));
        setContentView(wrap);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        panel.destroy();
    }
}
