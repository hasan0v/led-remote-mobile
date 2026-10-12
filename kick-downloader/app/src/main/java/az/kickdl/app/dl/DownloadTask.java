package az.kickdl.app.dl;

import org.json.JSONObject;

import java.util.UUID;

import az.kickdl.app.data.Media;

/** One download (or live recording) and its persisted progress. */
public class DownloadTask {
    public static final int QUEUED = 0, RUNNING = 1, PAUSED = 2, MERGING = 3, DONE = 4, FAILED = 5,
            CANCELED = 6, WAITING_NET = 7;

    public String id = UUID.randomUUID().toString();
    public int kind = Media.VOD;
    public String mediaId = "";
    public String title = "";
    public String channelSlug = "";
    public String channelName = "";
    public String thumb;
    /** Variant label, e.g. "1080p60". */
    public String quality = "";
    /** Media/master playlist URL, or a direct file URL when {@link #direct}. */
    public String url;
    public boolean direct;
    public boolean audioOnly;
    public boolean live;
    public double trimStart = -1, trimEnd = -1;
    public double durationSec;
    /** "mp4" or "ts" (user preference at enqueue time). */
    public String format = "mp4";

    public volatile int status = QUEUED;
    public volatile long bytesDone;
    public volatile long totalBytes = -1;
    public volatile int segDone, segTotal;
    public volatile double speed;
    public volatile double recordedSec;
    public volatile double mergeProgress;
    /** Container of the downloaded stream: "ts" or "mp4". */
    public String container = "ts";
    public String tempPath;
    public String outputUri;
    public String outputName;
    public String mime;
    public long outputSize = -1;
    public String error;
    public long createdAt = System.currentTimeMillis();
    public long finishedAt;

    public transient volatile boolean pauseRequested, cancelRequested, stopRequested;

    public boolean isActive() {
        return status == QUEUED || status == RUNNING || status == MERGING || status == WAITING_NET;
    }

    public boolean isFinished() { return status == DONE; }

    public double progress() {
        if (status == DONE) return 1;
        if (segTotal > 0) return Math.min(1, segDone / (double) segTotal);
        if (totalBytes > 0) return Math.min(1, bytesDone / (double) totalBytes);
        return 0;
    }

    public double clipDuration() {
        double s = trimStart > 0 ? trimStart : 0;
        double e = trimEnd > 0 ? trimEnd : durationSec;
        return Math.max(0, e - s);
    }

    public double etaSeconds() {
        if (speed <= 1 || totalBytes <= 0) return -1;
        return Math.max(0, (totalBytes - bytesDone) / speed);
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("id", id);
            o.put("kind", kind);
            o.put("mediaId", mediaId);
            o.put("title", title);
            o.put("channelSlug", channelSlug);
            o.put("channelName", channelName);
            o.put("thumb", thumb);
            o.put("quality", quality);
            o.put("url", url);
            o.put("direct", direct);
            o.put("audioOnly", audioOnly);
            o.put("live", live);
            o.put("trimStart", trimStart);
            o.put("trimEnd", trimEnd);
            o.put("durationSec", durationSec);
            o.put("format", format);
            o.put("status", status);
            o.put("bytesDone", bytesDone);
            o.put("totalBytes", totalBytes);
            o.put("segDone", segDone);
            o.put("segTotal", segTotal);
            o.put("recordedSec", recordedSec);
            o.put("container", container);
            o.put("tempPath", tempPath);
            o.put("outputUri", outputUri);
            o.put("outputName", outputName);
            o.put("mime", mime);
            o.put("outputSize", outputSize);
            o.put("error", error);
            o.put("createdAt", createdAt);
            o.put("finishedAt", finishedAt);
        } catch (Exception ignored) {}
        return o;
    }

    public static DownloadTask fromJson(JSONObject o) {
        DownloadTask t = new DownloadTask();
        t.id = o.optString("id", t.id);
        t.kind = o.optInt("kind");
        t.mediaId = o.optString("mediaId");
        t.title = o.optString("title");
        t.channelSlug = o.optString("channelSlug");
        t.channelName = o.optString("channelName");
        t.thumb = opt(o, "thumb");
        t.quality = o.optString("quality");
        t.url = opt(o, "url");
        t.direct = o.optBoolean("direct");
        t.audioOnly = o.optBoolean("audioOnly");
        t.live = o.optBoolean("live");
        t.trimStart = o.optDouble("trimStart", -1);
        t.trimEnd = o.optDouble("trimEnd", -1);
        t.durationSec = o.optDouble("durationSec", 0);
        t.format = o.optString("format", "mp4");
        t.status = o.optInt("status");
        t.bytesDone = o.optLong("bytesDone");
        t.totalBytes = o.optLong("totalBytes", -1);
        t.segDone = o.optInt("segDone");
        t.segTotal = o.optInt("segTotal");
        t.recordedSec = o.optDouble("recordedSec", 0);
        t.container = o.optString("container", "ts");
        t.tempPath = opt(o, "tempPath");
        t.outputUri = opt(o, "outputUri");
        t.outputName = opt(o, "outputName");
        t.mime = opt(o, "mime");
        t.outputSize = o.optLong("outputSize", -1);
        t.error = opt(o, "error");
        t.createdAt = o.optLong("createdAt");
        t.finishedAt = o.optLong("finishedAt");
        return t;
    }

    private static String opt(JSONObject o, String k) {
        return o.isNull(k) ? null : o.optString(k, null);
    }
}
