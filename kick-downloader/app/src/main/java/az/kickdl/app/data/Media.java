package az.kickdl.app.data;

/** A downloadable/playable item: a past broadcast (VOD), a clip or a live stream. */
public class Media {
    public static final int VOD = 0, CLIP = 1, LIVE = 2;

    public int kind = VOD;
    /** VOD uuid, clip id or channel slug for live. */
    public String id = "";
    public String title = "";
    public String thumb;
    public double durationSec;
    public long views = -1;
    public long createdAt;
    public String category;
    public String channelSlug = "";
    public String channelName = "";
    public String channelAvatar;
    /** HLS master/media playlist or direct mp4. */
    public String source;
    public boolean isLive;
    public String creator;

    public boolean isDirectFile() {
        if (source == null) return false;
        String p = source.toLowerCase();
        int q = p.indexOf('?');
        if (q > 0) p = p.substring(0, q);
        return p.endsWith(".mp4") || p.endsWith(".m4v") || p.endsWith(".mov") || p.endsWith(".webm");
    }

    public String webUrl() {
        switch (kind) {
            case CLIP: return "https://kick.com/" + channelSlug + "/clips/" + id;
            case LIVE: return "https://kick.com/" + channelSlug;
            default: return "https://kick.com/" + channelSlug + "/videos/" + id;
        }
    }
}
