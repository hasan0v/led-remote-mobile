package az.ledremote.app;

import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** "LED" tile: one tap turns the strip on or off, the tile shows the last state sent. */
public class PowerTileService extends TileService {
    private IrController ir;
    private Prefs prefs;

    @Override
    public void onCreate() {
        super.onCreate();
        ir = new IrController(this);
        prefs = new Prefs(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        ir.shutdown();
    }

    @Override
    public void onStartListening() {
        refresh();
    }

    @Override
    public void onClick() {
        if (!ir.isAvailable()) return;
        boolean on = !prefs.powerOn();
        ir.send(prefs.code(on ? KeySender.KEY_ON : KeySender.KEY_OFF));
        prefs.setPowerOn(on);
        refresh();
    }

    private void refresh() {
        Tile t = getQsTile();
        if (t == null) return;
        boolean on = prefs.powerOn();
        t.setLabel(getString(R.string.tile_power));
        if (!ir.isAvailable()) {
            t.setState(Tile.STATE_UNAVAILABLE);
        } else {
            t.setState(on ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        }
        if (Build.VERSION.SDK_INT >= 29) {
            t.setSubtitle(getString(on ? R.string.power_on : R.string.power_off));
        }
        t.updateTile();
    }
}
