package az.kickdl.app.net;

import android.content.Context;
import android.net.Uri;
import android.os.SystemClock;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import az.kickdl.app.data.Channel;
import az.kickdl.app.data.Media;
import az.kickdl.app.ui.Fmt;

/** Unofficial kick.com web API (the same endpoints the website uses). */
public final class KickApi {
    private KickApi() {}

    public static class NotFoundException extends IOException {
        public NotFoundException() { super("Not found"); }
    }

    public static class ClipPage {
        public List<Media> items = new ArrayList<>();
        public String nextCursor;
    }

    private static Context app;
    /** API root; overridable for tests. */
    public static String base = "https://kick.com/api/";
    /** Direct HTTP gets blocked by Cloudflare on most networks; remember it for a while. */
    private static volatile long directBlockedUntil;

    public static void init(Context c) { app = c.getApplicationContext(); }

    // ------------------------------------------------------------------ transport

    public static Object getJson(String path) throws IOException {
        String url = path.startsWith("http") ? path : base + path;
        Http.Resp r = null;
        if (SystemClock.elapsedRealtime() > directBlockedUntil) {
            try {
                Map<String, String> h = new HashMap<>();
                h.put("Accept", "application/json, text/plain, */*");
                h.put("Referer", "https://kick.com/");
                String ck = Http.kickCookies();
                if (ck != null) h.put("Cookie", ck);
                r = Http.get(url, h, 15000);
                if (!looksJson(r)) {
                    if (r.code != 404) directBlockedUntil = SystemClock.elapsedRealtime() + 10 * 60_000L;
                    r = null;
                }
            } catch (IOException e) {
                r = null;
            }
        }
        if (r == null) r = CfBridge.get(app).fetch(url, 45000);
        if (r.code == 404) throw new NotFoundException();
        if (!r.ok()) {
            if (CfBridge.looksLikeChallenge(r.text())) throw new CfBridge.ChallengeException();
            throw new Http.HttpException(r.code, null);
        }
        String t = r.text().trim();
        if (t.isEmpty()) throw new IOException("Empty response");
        try {
            return new JSONTokener(t).nextValue();
        } catch (Exception e) {
            if (CfBridge.looksLikeChallenge(t)) throw new CfBridge.ChallengeException();
            throw new IOException("Bad JSON");
        }
    }

    private static boolean looksJson(Http.Resp r) {
        if (r.body == null || r.body.length == 0) return false;
        String t = r.text().trim();
        return (r.ok() || r.code == 404) && (t.startsWith("{") || t.startsWith("["));
    }

    // ------------------------------------------------------------------ endpoints

    public static Channel channel(String slug) throws IOException {
        Object o = getJson("v2/channels/" + enc(slug));
        if (!(o instanceof JSONObject)) throw new NotFoundException();
        JSONObject j = (JSONObject) o;
        if (j.has("message") && !j.has("slug")) throw new NotFoundException();
        return parseChannel(j);
    }

    public static List<Media> videos(Channel c) throws IOException {
        Object o = getJson("v2/channels/" + enc(c.slug) + "/videos");
        JSONArray a = o instanceof JSONArray ? (JSONArray) o
                : o instanceof JSONObject ? firstArray((JSONObject) o, "data", "videos", "items") : null;
        List<Media> out = new ArrayList<>();
        if (a == null) return out;
        for (int i = 0; i < a.length(); i++) {
            JSONObject v = a.optJSONObject(i);
            if (v == null) continue;
            Media m = parseVod(v, c);
            if (m != null) out.add(m);
        }
        return out;
    }

    /** sort: "view" or "date"; time: "all", "month", "week", "day". */
    public static ClipPage clips(Channel c, String sort, String time, String cursor) throws IOException {
        StringBuilder p = new StringBuilder("v2/channels/").append(enc(c.slug)).append("/clips?");
        p.append("cursor=").append(cursor == null ? "0" : enc(cursor));
        p.append("&sort=").append(sort == null ? "view" : sort);
        p.append("&time=").append(time == null ? "all" : time);
        Object o = getJson(p.toString());
        ClipPage page = new ClipPage();
        JSONArray a = null;
        if (o instanceof JSONObject) {
            JSONObject j = (JSONObject) o;
            a = firstArray(j, "clips", "data", "items");
            String next = str(j, "nextCursor", "next_cursor", "cursor");
            if (next != null && !next.isEmpty() && !"null".equals(next) && !next.equals(cursor)) page.nextCursor = next;
        } else if (o instanceof JSONArray) {
            a = (JSONArray) o;
        }
        if (a != null) {
            for (int i = 0; i < a.length(); i++) {
                JSONObject v = a.optJSONObject(i);
                if (v == null) continue;
                Media m = parseClip(v, c);
                if (m != null) page.items.add(m);
            }
        }
        return page;
    }

    public static List<Channel> search(String q) throws IOException {
        LinkedHashMap<String, Channel> out = new LinkedHashMap<>();
        IOException first = null;
        try {
            Object o = getJson("search?searched_word=" + enc(q));
            collectChannels(o, out, 0);
        } catch (CfBridge.ChallengeException e) {
            throw e;
        } catch (IOException e) {
            first = e;
        }
        if (out.isEmpty()) {
            // The site search can be flaky; also try the exact slug.
            String slug = slugify(q);
            if (!slug.isEmpty()) {
                try {
                    Channel c = channel(slug);
                    out.put(c.slug.toLowerCase(Locale.US), c);
                } catch (NotFoundException ignored) {
                } catch (IOException e) {
                    if (first == null) first = e;
                }
            }
        }
        if (out.isEmpty() && first != null && !(first instanceof NotFoundException)) throw first;
        return new ArrayList<>(out.values());
    }

    public static Media video(String uuid) throws IOException {
        Object o = getJson("v1/video/" + enc(uuid));
        if (!(o instanceof JSONObject)) throw new NotFoundException();
        JSONObject j = (JSONObject) o;
        JSONObject ls = j.optJSONObject("livestream");
        Channel c = new Channel();
        if (ls != null) {
            JSONObject ch = ls.optJSONObject("channel");
            if (ch != null) c = parseChannel(ch);
        }
        Media m = parseVod(ls != null ? merge(ls, j) : j, c);
        if (m == null) throw new NotFoundException();
        m.id = uuid;
        if (j.optString("source", "").length() > 0) m.source = j.optString("source");
        return m;
    }

    public static Media clip(String id) throws IOException {
        Object o = getJson("v2/clips/" + enc(id));
        if (!(o instanceof JSONObject)) throw new NotFoundException();
        JSONObject j = ((JSONObject) o).optJSONObject("clip");
        if (j == null) j = (JSONObject) o;
        Channel c = new Channel();
        JSONObject ch = j.optJSONObject("channel");
        if (ch != null) c = parseChannel(ch);
        Media m = parseClip(j, c);
        if (m == null) throw new NotFoundException();
        return m;
    }

    // ------------------------------------------------------------------ links

    /** Parsed kick.com link: type 0 channel, 1 vod, 2 clip. */
    public static class Link {
        public int type;
        public String channel;
        public String id;
    }

    public static Link parseLink(String text) {
        if (text == null) return null;
        String t = text.trim();
        int i = t.toLowerCase(Locale.US).indexOf("kick.com/");
        if (i < 0) return null;
        String rest = t.substring(i + "kick.com/".length());
        int sp = indexOfAny(rest, " \n\t");
        if (sp >= 0) rest = rest.substring(0, sp);
        Uri u = Uri.parse("https://kick.com/" + rest);
        List<String> seg = u.getPathSegments();
        Link l = new Link();
        String clipQ = u.getQueryParameter("clip");
        if (seg.isEmpty()) return null;
        l.channel = seg.get(0);
        if (clipQ != null && clipQ.startsWith("clip_")) {
            l.type = 2;
            l.id = clipQ;
        } else if (seg.size() >= 3 && "videos".equals(seg.get(1))) {
            l.type = 1;
            l.id = seg.get(2);
        } else if (seg.size() >= 3 && "clips".equals(seg.get(1))) {
            l.type = 2;
            l.id = seg.get(2);
        } else if ("video".equals(seg.get(0)) && seg.size() >= 2) {
            l.type = 1;
            l.channel = null;
            l.id = seg.get(1);
        } else {
            String s = seg.get(0).toLowerCase(Locale.US);
            if (s.equals("categories") || s.equals("search") || s.equals("auth") || s.equals("following")) return null;
            l.type = 0;
        }
        return l;
    }

    private static int indexOfAny(String s, String chars) {
        for (int i = 0; i < s.length(); i++) if (chars.indexOf(s.charAt(i)) >= 0) return i;
        return -1;
    }

    public static String slugify(String q) {
        String s = q.trim().toLowerCase(Locale.US).replace('_', '-');
        StringBuilder b = new StringBuilder();
        for (char ch : s.toCharArray()) if (Character.isLetterOrDigit(ch) || ch == '-') b.append(ch);
        return b.toString();
    }

    // ------------------------------------------------------------------ parsing

    public static Channel parseChannel(JSONObject j) {
        Channel c = new Channel();
        c.id = j.optLong("id");
        c.slug = str(j, "slug", "channel_slug");
        if (c.slug == null) c.slug = "";
        JSONObject user = j.optJSONObject("user");
        c.name = str(j, "username", "name");
        if (user != null) {
            String un = str(user, "username", "name");
            if (un != null) c.name = un;
            c.avatar = url(str(user, "profile_pic", "profilepic", "profilePic", "avatar"));
            c.bio = str(user, "bio");
        }
        if (c.avatar == null) c.avatar = url(str(j, "profile_pic", "profilePic", "profile_picture", "avatar"));
        if (c.name == null) c.name = c.slug;
        if (c.slug.isEmpty() && c.name != null) c.slug = slugify(c.name);
        if (c.bio == null) c.bio = str(j, "bio", "description");
        c.followers = lng(j, -1, "followers_count", "followersCount", "followers");
        Object ver = j.opt("verified");
        c.verified = (ver instanceof Boolean && (Boolean) ver) || ver instanceof JSONObject
                || j.optBoolean("is_verified");
        JSONObject banner = j.optJSONObject("banner_image");
        if (banner != null) c.banner = url(str(banner, "url", "src"));
        if (c.banner == null) c.banner = url(str(j, "banner_image", "offline_banner_image"));
        if (c.banner == null) {
            JSONObject off = j.optJSONObject("offline_banner_image");
            if (off != null) c.banner = url(str(off, "src", "url"));
        }
        c.playbackUrl = str(j, "playback_url");
        c.vodEnabled = j.optBoolean("vod_enabled", true);
        JSONObject ls = j.optJSONObject("livestream");
        if (ls != null) {
            c.live = ls.optBoolean("is_live", true);
            c.liveTitle = str(ls, "session_title", "title");
            c.liveViewers = lng(ls, 0, "viewer_count", "viewers");
            JSONObject th = ls.optJSONObject("thumbnail");
            c.liveThumb = th != null ? url(str(th, "url", "src")) : url(str(ls, "thumbnail"));
            JSONArray cats = ls.optJSONArray("categories");
            if (cats != null && cats.length() > 0) c.liveCategory = str(cats.optJSONObject(0), "name");
            c.liveStartedAt = Fmt.parseTime(str(ls, "start_time", "created_at"));
        } else {
            Object live = j.opt("is_live");
            if (live == null) live = j.opt("isLive");
            c.live = live instanceof Boolean && (Boolean) live;
        }
        return c;
    }

    static Media parseVod(JSONObject v, Channel c) {
        Media m = new Media();
        m.kind = Media.VOD;
        JSONObject video = v.optJSONObject("video");
        m.id = video != null ? str(video, "uuid") : null;
        if (m.id == null) m.id = str(v, "uuid", "slug", "id");
        m.title = str(v, "session_title", "title");
        if (m.title == null || m.title.isEmpty()) m.title = "Stream";
        m.source = str(v, "source", "playback_url");
        if (m.source == null && video != null) m.source = str(video, "source");
        Object th = v.opt("thumbnail");
        if (th instanceof JSONObject) m.thumb = url(str((JSONObject) th, "src", "url"));
        else if (th instanceof String) m.thumb = url((String) th);
        if (m.thumb == null && video != null) m.thumb = url(str(video, "thumb", "thumbnail"));
        double dur = v.optDouble("duration", 0);
        m.durationSec = dur / 1000.0; // livestream durations are reported in milliseconds
        m.views = video != null ? lng(video, -1, "views") : -1;
        if (m.views < 0) m.views = lng(v, -1, "views", "view_count", "viewer_count");
        m.createdAt = Fmt.parseTime(str(v, "start_time", "created_at"));
        JSONArray cats = v.optJSONArray("categories");
        if (cats != null && cats.length() > 0) m.category = str(cats.optJSONObject(0), "name");
        m.isLive = v.optBoolean("is_live");
        m.channelSlug = c.slug;
        m.channelName = c.displayName();
        m.channelAvatar = c.avatar;
        if (m.source == null) return null;
        return m;
    }

    static Media parseClip(JSONObject v, Channel c) {
        Media m = new Media();
        m.kind = Media.CLIP;
        m.id = str(v, "id");
        m.title = str(v, "title");
        if (m.title == null || m.title.isEmpty()) m.title = "Clip";
        m.source = str(v, "clip_url", "video_url", "source");
        m.thumb = url(str(v, "thumbnail_url", "thumbnail"));
        m.durationSec = v.optDouble("duration", 0);
        m.views = lng(v, -1, "views", "view_count");
        m.createdAt = Fmt.parseTime(str(v, "created_at"));
        JSONObject cat = v.optJSONObject("category");
        if (cat != null) m.category = str(cat, "name");
        JSONObject cr = v.optJSONObject("creator");
        if (cr != null) m.creator = str(cr, "username");
        JSONObject ch = v.optJSONObject("channel");
        if (ch != null && (c == null || c.slug.isEmpty())) c = parseChannel(ch);
        if (c != null) {
            m.channelSlug = c.slug;
            m.channelName = c.displayName();
            m.channelAvatar = c.avatar;
        }
        if (m.source == null || m.id == null) return null;
        return m;
    }

    /** Walks any search payload shape and picks out channel-like objects. */
    private static void collectChannels(Object o, Map<String, Channel> out, int depth) {
        if (o == null || depth > 4 || out.size() >= 30) return;
        if (o instanceof JSONArray) {
            JSONArray a = (JSONArray) o;
            for (int i = 0; i < a.length(); i++) collectChannels(a.opt(i), out, depth + 1);
            return;
        }
        if (!(o instanceof JSONObject)) return;
        JSONObject j = (JSONObject) o;
        JSONObject doc = j.optJSONObject("document");
        if (doc != null) j = doc;
        if (j.has("slug") && (j.has("user") || j.has("followers_count") || j.has("followersCount")
                || j.has("playback_url") || j.has("is_live") || j.has("isLive") || j.has("username"))) {
            Channel c = parseChannel(j);
            if (!c.slug.isEmpty()) {
                String k = c.slug.toLowerCase(Locale.US);
                if (!out.containsKey(k)) out.put(k, c);
            }
            return;
        }
        // Prefer channel collections first.
        String[] keys = {"channels", "channel", "hits", "data", "results"};
        for (String k : keys) if (j.has(k)) collectChannels(j.opt(k), out, depth + 1);
    }

    // ------------------------------------------------------------------ json helpers

    private static JSONObject merge(JSONObject a, JSONObject b) {
        JSONObject out = new JSONObject();
        try {
            JSONArray n = b.names();
            if (n != null) for (int i = 0; i < n.length(); i++) out.put(n.getString(i), b.get(n.getString(i)));
            n = a.names();
            if (n != null) for (int i = 0; i < n.length(); i++) out.put(n.getString(i), a.get(n.getString(i)));
        } catch (Exception ignored) {}
        return out;
    }

    static JSONArray firstArray(JSONObject j, String... keys) {
        for (String k : keys) {
            JSONArray a = j.optJSONArray(k);
            if (a != null) return a;
            JSONObject o = j.optJSONObject(k);
            if (o != null) {
                JSONArray inner = o.optJSONArray("data");
                if (inner != null) return inner;
            }
        }
        return null;
    }

    static String str(JSONObject j, String... keys) {
        if (j == null) return null;
        for (String k : keys) {
            if (!j.has(k) || j.isNull(k)) continue;
            Object v = j.opt(k);
            if (v instanceof String || v instanceof Number || v instanceof Boolean) {
                String s = String.valueOf(v);
                if (!s.isEmpty()) return s;
            }
        }
        return null;
    }

    static long lng(JSONObject j, long def, String... keys) {
        for (String k : keys) {
            if (!j.has(k) || j.isNull(k)) continue;
            Object v = j.opt(k);
            if (v instanceof Number) return ((Number) v).longValue();
            try { return Long.parseLong(String.valueOf(v)); } catch (Exception ignored) {}
        }
        return def;
    }

    private static String url(String s) {
        if (s == null || s.isEmpty() || "null".equals(s)) return null;
        s = s.trim();
        int sp = s.indexOf(' ');
        if (sp > 0) s = s.substring(0, sp); // srcset fragments
        if (s.startsWith("//")) return "https:" + s;
        if (s.startsWith("/")) return "https://kick.com" + s;
        return s.startsWith("http") ? s : null;
    }

    static String enc(String s) {
        try { return URLEncoder.encode(s, "UTF-8"); } catch (Exception e) { return s; }
    }
}
