package az.kickdl.app;

import android.app.Application;
import android.webkit.WebSettings;

import az.kickdl.app.data.Follows;
import az.kickdl.app.data.Prefs;
import az.kickdl.app.dl.DownloadManager;
import az.kickdl.app.net.Http;
import az.kickdl.app.net.KickApi;
import az.kickdl.app.ui.ImageLoader;
import az.kickdl.app.ui.Ui;

public class App extends Application {
    @Override public void onCreate() {
        super.onCreate();
        Ui.init(this);
        Prefs.init(this);
        Follows.init(this);
        KickApi.init(this);
        ImageLoader.init(this);
        try {
            Http.UA = WebSettings.getDefaultUserAgent(this);
        } catch (Throwable ignored) {}
        DownloadService.createChannels(this);
        DownloadManager.init(this);
    }
}
