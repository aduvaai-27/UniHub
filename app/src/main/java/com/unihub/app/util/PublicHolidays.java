package com.unihub.app.util;

import android.content.Context;
import android.content.SharedPreferences;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.Volley;
import com.unihub.app.data.CustomHoliday;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Government (public) holidays of Bangladesh, from the Nager.Date REST API
 *   https://date.nager.at/api/v3/PublicHolidays/{year}/BD
 * Loaded once at app start for last year, this year and next year, and saved on the phone, so the answer is
 * also available offline (and right after a restart). Classes on these dates are cancelled automatically:
 * {@link Holidays#on} falls back to this list when the CR has not set a holiday for the date.
 * Until the first successful load nothing is treated as a public holiday.
 */
public final class PublicHolidays {
    private PublicHolidays() { }

    private static final String PREFS = "public_holidays";
    private static final Map<String, String> NAMES = new HashMap<>();             // "yyyy-MM-dd" -> name
    private static final List<Runnable> LISTENERS = new CopyOnWriteArrayList<>();
    private static RequestQueue queue;
    private static SharedPreferences prefs;

    /** Call once at app start: loads the saved copy, then refreshes it from the API. */
    public static synchronized void init(Context context) {
        Context app = context.getApplicationContext();
        prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (queue == null) queue = Volley.newRequestQueue(app);
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(TrustedTime.now());
        int year = c.get(Calendar.YEAR);
        for (int y = year - 1; y <= year + 1; y++) {
            loadSaved(y);
            fetch(y);
        }
    }

    /** The public holiday on this date (yyyy-MM-dd) as a one-day holiday, or null. */
    public static synchronized CustomHoliday on(String dateKey) {
        String name = dateKey == null ? null : NAMES.get(dateKey);
        if (name == null) return null;
        CustomHoliday h = new CustomHoliday();
        h.id = "public_" + dateKey;
        h.purpose = name;
        h.startDate = dateKey;
        h.endDate = dateKey;
        h.by = "Public holiday";
        h.createdAt = 0;
        return h;
    }

    /** Called (on the main thread) whenever new public holidays arrive, so open screens can redraw. */
    public static void addListener(Runnable r) { if (r != null && !LISTENERS.contains(r)) LISTENERS.add(r); }
    public static void removeListener(Runnable r) { LISTENERS.remove(r); }

    private static synchronized void loadSaved(int year) {
        try {
            String s = prefs.getString("y" + year, null);
            if (s == null) return;
            JSONObject o = new JSONObject(s);
            for (Iterator<String> it = o.keys(); it.hasNext(); ) { String k = it.next(); NAMES.put(k, o.getString(k)); }
        } catch (Exception ignored) { /* bad saved copy: the API answer replaces it */ }
    }

    private static void fetch(int year) {
        queue.add(new JsonArrayRequest(Request.Method.GET, "https://date.nager.at/api/v3/PublicHolidays/" + year + "/BD", null,
                (JSONArray arr) -> {
                    Map<String, String> m = new HashMap<>();
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject h = arr.optJSONObject(i);
                        if (h == null || h.optString("date").isEmpty()) continue;
                        JSONArray types = h.optJSONArray("types");
                        if (types != null && types.length() > 0 && !contains(types, "Public")) continue;   // only real public holidays
                        String date = h.optString("date"), name = h.optString("name"), old = m.get(date);
                        m.put(date, old == null || old.equals(name) ? name : old + " / " + name);
                    }
                    if (m.isEmpty()) return;                                   // an empty answer must not wipe a good saved copy
                    synchronized (PublicHolidays.class) {
                        String prefix = year + "-";
                        NAMES.keySet().removeIf(k -> k.startsWith(prefix));    // the API answer replaces the saved copy of that year
                        NAMES.putAll(m);
                        prefs.edit().putString("y" + year, new JSONObject(m).toString()).apply();
                    }
                    for (Runnable r : LISTENERS) r.run();
                },
                err -> { /* offline or API down: keep the saved copy */ }));
    }

    private static boolean contains(JSONArray a, String v) {
        for (int i = 0; i < a.length(); i++) if (v.equalsIgnoreCase(a.optString(i))) return true;
        return false;
    }
}
