package com.unihub.app.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.provider.Settings;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.HttpHeaderParser;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import org.json.JSONObject;

/**
 * The one clock the whole app uses. It does NOT trust the phone's date and time (the user can change them).
 *
 * How it works
 *  1. When the phone is online, the real time is read from a time API with Volley (JSON). Three sources are tried in order:
 *       a) worldtimeapi.org   -> "unixtime"            (JSON)
 *       b) timeapi.io         -> "dateTime"            (JSON)
 *       c) HTTP "Date" header of a Google server       (last resort)
 *  2. The answer is remembered together with SystemClock.elapsedRealtime(). That clock counts every millisecond since
 *     the phone booted and ignores the date/time setting, so changing the phone clock cannot move our time.
 *  3. now() = time from the API + (elapsedRealtime now - elapsedRealtime at the sync). It works without internet.
 *  4. The anchor is saved. It stays valid until the phone reboots (checked with Settings.Global.BOOT_COUNT);
 *     after a reboot the app needs one new sync. Until the first sync ever, the phone clock is used and
 *     isVerified() is false so the screens can say so.
 */
public final class TrustedTime {
    private TrustedTime() { }

    private static final String PREFS = "unihub_time";
    private static final long STALE_MS = 10 * 60 * 1000L;      // ask the API again after 10 minutes

    private static final String[] URLS = {
            "https://worldtimeapi.org/api/timezone/Asia/Dhaka",
            "https://timeapi.io/api/time/current/zone?timeZone=Asia%2FDhaka",
            "https://www.google.com/generate_204"
    };

    private static boolean loaded = false, syncing = false;
    private static long anchorTime = 0, anchorElapsed = 0, lastSyncElapsed = 0;
    private static int anchorBoot = -1;
    private static RequestQueue queue;
    private static final List<Callback> waiting = new ArrayList<>();      // everyone waiting for the running sync

    public interface Callback { void onDone(boolean verified); }

    // ---------------------------------------------------------------- reading the time

    /** Current real time in milliseconds (UTC epoch). */
    public static synchronized long now() {
        if (anchorValid()) return anchorTime + (SystemClock.elapsedRealtime() - anchorElapsed);
        return System.currentTimeMillis();                     // never synced since boot: phone clock
    }

    /** True when now() comes from the internet time, false when it is only the phone clock. */
    public static synchronized boolean isVerified() { return anchorValid(); }

    /** How many minutes the phone clock is ahead (+) or behind (-) the real time. 0 when not verified. */
    public static synchronized long phoneOffsetMinutes() {
        if (!anchorValid()) return 0;
        return Math.round((System.currentTimeMillis() - now()) / 60000.0);
    }

    private static boolean anchorValid() {
        return anchorTime > 0 && anchorElapsed <= SystemClock.elapsedRealtime();
    }

    // ---------------------------------------------------------------- loading / saving the anchor

    /** Call once from Application.onCreate(): loads the saved anchor if the phone has not rebooted since. */
    public static synchronized void init(Context c) {
        if (loaded) return;
        loaded = true;
        SharedPreferences p = c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long t = p.getLong("time", 0), e = p.getLong("elapsed", 0);
        int boot = p.getInt("boot", -1);
        if (t > 0 && boot == bootCount(c) && e <= SystemClock.elapsedRealtime()) {
            anchorTime = t; anchorElapsed = e; anchorBoot = boot;
            lastSyncElapsed = e;
        }
    }

    private static int bootCount(Context c) {
        try { return Settings.Global.getInt(c.getContentResolver(), Settings.Global.BOOT_COUNT, -1); }
        catch (Exception ex) { return -1; }
    }

    private static synchronized void store(Context c, long apiTime, long elapsedMid) {
        anchorTime = apiTime;
        anchorElapsed = elapsedMid;
        anchorBoot = bootCount(c);
        lastSyncElapsed = SystemClock.elapsedRealtime();
        c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putLong("time", anchorTime).putLong("elapsed", anchorElapsed).putInt("boot", anchorBoot).apply();
    }

    // ---------------------------------------------------------------- syncing with the API

    /** Syncs only if there is no anchor yet or the last sync is older than 10 minutes. */
    public static void syncIfNeeded(Context c, Callback cb) {
        boolean fresh;
        synchronized (TrustedTime.class) {
            fresh = anchorValid() && SystemClock.elapsedRealtime() - lastSyncElapsed < STALE_MS;
        }
        if (fresh) { if (cb != null) cb.onDone(true); return; }
        sync(c, cb);
    }

    /** Asks the time API for the real time. The callback runs on the main thread (true = the clock is now verified). */
    public static void sync(Context c, Callback cb) {
        final Context app = c.getApplicationContext();
        init(app);
        synchronized (TrustedTime.class) {
            if (cb != null) waiting.add(cb);
            if (syncing) return;                                // a sync is already running: cb is called when it ends
            syncing = true;
        }
        if (!OfflineWatcher.isOnline(app)) { finish(); return; }
        if (queue == null) queue = Volley.newRequestQueue(app);
        trySource(app, 0);
    }

    /** Ends the running sync and tells every screen that was waiting for it. */
    private static void finish() {
        List<Callback> todo;
        synchronized (TrustedTime.class) {
            syncing = false;
            todo = new ArrayList<>(waiting);
            waiting.clear();
        }
        boolean ok = isVerified();
        for (Callback c : todo) c.onDone(ok);
    }

    private static void trySource(final Context app, final int index) {
        if (index >= URLS.length) { finish(); return; }
        final long t0 = SystemClock.elapsedRealtime();
        final Response.ErrorListener next = (VolleyError e) -> trySource(app, index + 1);

        if (index == 2) {                                       // HTTP Date header
            Request<Long> r = new DateHeaderRequest(URLS[index], (Long apiTime) -> {
                long t1 = SystemClock.elapsedRealtime();
                store(app, apiTime, (t0 + t1) / 2);
                finish();
            }, next);
            r.setShouldCache(false);
            queue.add(r);
            return;
        }

        JsonObjectRequest r = new JsonObjectRequest(Request.Method.GET, URLS[index], null, (JSONObject res) -> {
            long apiTime = index == 0 ? fromWorldTimeApi(res) : fromTimeApiIo(res);
            if (apiTime <= 0) { trySource(app, index + 1); return; }
            long t1 = SystemClock.elapsedRealtime();
            store(app, apiTime, (t0 + t1) / 2);                // the answer was made about half way through the request
            finish();
        }, next);
        r.setShouldCache(false);                                // a cached time would be wrong
        queue.add(r);
    }

    /** {"unixtime": 1739962532, ...} seconds since 1970. */
    private static long fromWorldTimeApi(JSONObject o) {
        long sec = o.optLong("unixtime", 0);
        return sec > 0 ? sec * 1000L : 0;
    }

    /** {"dateTime": "2026-11-08T19:21:45.1234567", ...} in the requested zone (Asia/Dhaka). */
    private static long fromTimeApiIo(JSONObject o) {
        try {
            String s = o.optString("dateTime", "");
            if (s.length() < 19) return 0;
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            f.setTimeZone(TimeZone.getTimeZone("Asia/Dhaka"));
            Date d = f.parse(s.substring(0, 19));
            return d == null ? 0 : d.getTime();
        } catch (Exception e) { return 0; }
    }

    /** Reads the "Date" header of any HTTPS answer (accurate to one second). */
    private static class DateHeaderRequest extends Request<Long> {
        private final Response.Listener<Long> ok;

        DateHeaderRequest(String url, Response.Listener<Long> ok, Response.ErrorListener err) {
            super(Request.Method.GET, url, err);
            this.ok = ok;
        }

        @Override
        protected Response<Long> parseNetworkResponse(NetworkResponse r) {
            try {
                String h = r.headers == null ? null : r.headers.get("Date");
                if (h == null) return Response.error(new VolleyError("no Date header"));
                SimpleDateFormat f = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US);
                f.setTimeZone(TimeZone.getTimeZone("GMT"));
                Date d = f.parse(h);
                if (d == null) return Response.error(new VolleyError("bad Date header"));
                return Response.success(d.getTime(), HttpHeaderParser.parseCacheHeaders(r));
            } catch (Exception e) { return Response.error(new VolleyError(e)); }
        }

        @Override protected void deliverResponse(Long t) { ok.onResponse(t); }
    }

    /** The same instant one calendar day later (used for "tomorrow"). */
    public static long dayAfter(long t) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTimeInMillis(t);
        c.add(java.util.Calendar.DAY_OF_MONTH, 1);
        return c.getTimeInMillis();
    }
}
