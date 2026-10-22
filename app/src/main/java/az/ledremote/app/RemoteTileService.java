package az.ledremote.app;

import android.app.Dialog;
import android.app.PendingIntent;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.view.Window;

/** "LED Remote" tile: opens the full 24-key remote right on top of the control center. */
public class RemoteTileService extends TileService {

    @Override
    public void onStartListening() {
        Tile t = getQsTile();
        if (t == null) return;
        t.setState(Tile.STATE_INACTIVE);
        t.setLabel(getString(R.string.tile_remote));
        t.updateTile();
    }

    @Override
    public void onClick() {
        if (isLocked()) {
            unlockAndRun(new Runnable() {
                @Override public void run() { showRemote(); }
            });
        } else {
            showRemote();
        }
    }

    private void showRemote() {
        if (new Prefs(this).tileWindow()) {
            openActivity(new Intent(this, QuickRemoteActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return;
        }
        final Dialog d = new Dialog(this, R.style.DialogTheme);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        final QuickPanel panel = new QuickPanel(this, new Runnable() {
            @Override public void run() {
                d.dismiss();
                openActivity(QuickPanel.appIntent(RemoteTileService.this));
            }
        });
        d.setContentView(panel.view);
        Window w = d.getWindow();
        w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        w.setLayout(panel.width(), -2);
        w.setDimAmount(0.35f);
        d.setCanceledOnTouchOutside(true);
        d.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override public void onDismiss(DialogInterface x) { panel.destroy(); }
        });
        try {
            showDialog(d);
        } catch (RuntimeException e) {
            // Some OEM control centers refuse tile dialogs: fall back to a floating activity.
            panel.destroy();
            openActivity(new Intent(this, QuickRemoteActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
    }

    @SuppressWarnings("deprecation")
    private void openActivity(Intent intent) {
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent,
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
            } else {
                startActivityAndCollapse(intent);
            }
        } catch (RuntimeException e) {
            startActivity(intent);
        }
    }
}
