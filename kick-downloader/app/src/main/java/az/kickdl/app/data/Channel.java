package az.kickdl.app.data;

import org.json.JSONObject;

/** A Kick channel (from search or channel endpoints). */
public class Channel {
    public long id;
    public String slug = "";
    public String name = "";
    public String avatar;
    public String banner;
    public String bio;
    public long followers = -1;
    public boolean verified;
    public boolean live;
    public String liveTitle;
    public long liveViewers;
    public String liveThumb;
    public String liveCategory;
    public long liveStartedAt;
    public String playbackUrl;
    public boolean vodEnabled = true;

    public String displayName() { return name != null && !name.isEmpty() ? name : slug; }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("slug", slug);
            o.put("name", name);
            o.put("avatar", avatar);
            o.put("followers", followers);
            o.put("verified", verified);
        } catch (Exception ignored) {}
        return o;
    }
}
