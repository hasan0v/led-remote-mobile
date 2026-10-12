package az.kickdl.app.data;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Locally followed channels (no Kick account needed). */
public final class Follows {
    private Follows() {}

    public static class Item {
        public String slug;
        public String name;
        public String avatar;
        public long followers = -1;
        public boolean verified;
        public long followedAt;
        public boolean live;
        public String liveTitle;
        /** Number of VODs the user has already seen (for the "N new" badge). */
        public int seenVods = -1;
        public int knownVods = -1;
        public long lastVodAt;
        public long checkedAt;

        JSONObject toJson() throws Exception {
            JSONObject o = new JSONObject();
            o.put("slug", slug);
            o.put("name", name);
            o.put("avatar", avatar);
            o.put("followers", followers);
            o.put("verified", verified);
            o.put("followedAt", followedAt);
            o.put("live", live);
            o.put("liveTitle", liveTitle);
            o.put("seenVods", seenVods);
            o.put("knownVods", knownVods);
            o.put("lastVodAt", lastVodAt);
            o.put("checkedAt", checkedAt);
            return o;
        }

        static Item fromJson(JSONObject o) {
            Item i = new Item();
            i.slug = o.optString("slug");
            i.name = o.optString("name", i.slug);
            i.avatar = o.isNull("avatar") ? null : o.optString("avatar", null);
            i.followers = o.optLong("followers", -1);
            i.verified = o.optBoolean("verified");
            i.followedAt = o.optLong("followedAt");
            i.live = o.optBoolean("live");
            i.liveTitle = o.isNull("liveTitle") ? null : o.optString("liveTitle", null);
            i.seenVods = o.optInt("seenVods", -1);
            i.knownVods = o.optInt("knownVods", -1);
            i.lastVodAt = o.optLong("lastVodAt");
            i.checkedAt = o.optLong("checkedAt");
            return i;
        }

        public int newVods() {
            if (seenVods < 0 || knownVods < 0) return 0;
            return Math.max(0, knownVods - seenVods);
        }
    }

    public interface Listener { void onFollowsChanged(); }

    private static final android.os.Handler MAIN = new android.os.Handler(android.os.Looper.getMainLooper());
    private static SharedPreferences sp;
    private static final List<Item> items = new ArrayList<>();
    private static final List<Listener> listeners = new CopyOnWriteArrayList<>();

    public static void init(Context c) {
        sp = c.getSharedPreferences("follows", Context.MODE_PRIVATE);
        items.clear();
        try {
            JSONArray a = new JSONArray(sp.getString("items", "[]"));
            for (int i = 0; i < a.length(); i++) items.add(Item.fromJson(a.getJSONObject(i)));
        } catch (Exception ignored) {}
    }

    public static synchronized List<Item> all() { return new ArrayList<>(items); }

    public static synchronized Item get(String slug) {
        for (Item i : items) if (i.slug.equalsIgnoreCase(slug)) return i;
        return null;
    }

    public static boolean isFollowed(String slug) { return get(slug) != null; }

    public static synchronized void follow(Channel c) {
        if (get(c.slug) != null) return;
        Item i = new Item();
        i.slug = c.slug;
        i.name = c.displayName();
        i.avatar = c.avatar;
        i.followers = c.followers;
        i.verified = c.verified;
        i.live = c.live;
        i.liveTitle = c.liveTitle;
        i.followedAt = System.currentTimeMillis();
        items.add(0, i);
        save();
    }

    public static synchronized void unfollow(String slug) {
        Item i = get(slug);
        if (i != null) items.remove(i);
        save();
    }

    /** Toggles and returns the new state. */
    public static boolean toggle(Channel c) {
        if (isFollowed(c.slug)) { unfollow(c.slug); return false; }
        follow(c);
        return true;
    }

    /** Updates cached data from a freshly loaded channel. */
    public static synchronized void update(Channel c, int vodCount, long lastVodAt, boolean markSeen) {
        Item i = get(c.slug);
        if (i == null) return;
        i.name = c.displayName();
        if (c.avatar != null) i.avatar = c.avatar;
        if (c.followers >= 0) i.followers = c.followers;
        i.verified = c.verified;
        i.live = c.live;
        i.liveTitle = c.liveTitle;
        i.checkedAt = System.currentTimeMillis();
        if (vodCount >= 0) {
            i.knownVods = vodCount;
            if (i.seenVods < 0 || markSeen) i.seenVods = vodCount;
        }
        if (lastVodAt > 0) i.lastVodAt = lastVodAt;
        save();
    }

    public static synchronized void move(int from, int to) {
        if (from < 0 || from >= items.size() || to < 0 || to >= items.size()) return;
        items.add(to, items.remove(from));
        save();
    }

    private static void save() {
        JSONArray a = new JSONArray();
        try {
            for (Item i : items) a.put(i.toJson());
        } catch (Exception ignored) {}
        sp.edit().putString("items", a.toString()).apply();
        MAIN.post(new Runnable() {
            @Override public void run() { for (Listener l : listeners) l.onFollowsChanged(); }
        });
    }

    public static void addListener(Listener l) { listeners.add(l); }
    public static void removeListener(Listener l) { listeners.remove(l); }
}
