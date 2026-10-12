package az.kickdl.app;

import org.robolectric.RuntimeEnvironment;

import az.kickdl.app.dl.DownloadManager;

/** Robolectric reuses static state between tests in one sandbox: rebuild singletons per test. */
final class Reset {
    static void all() throws Exception {
        java.lang.reflect.Field f = DownloadManager.class.getDeclaredField("instance");
        f.setAccessible(true);
        f.set(null, null);
        DownloadManager.init(RuntimeEnvironment.getApplication());
        az.kickdl.app.data.Follows.init(RuntimeEnvironment.getApplication());
    }
}
