package com.unihub.app.util;

import android.content.Context;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Asks two public REST APIs (Volley, JSON) about one date:
 *   1. which day of the week it is  -> Aladhan   https://api.aladhan.com/v1/gToH/{dd-MM-yyyy}
 *   2. is it a public holiday?      -> Nager.Date https://date.nager.at/api/v3/PublicHolidays/{year}/BD
 * If an API cannot be reached, the weekday is worked out on the phone and the holiday check is reported as "not checked".
 * The callback always runs on the main thread. Friday and Saturday (not class days) are reported through weekend().
 */
public final class DateInfo {
    private DateInfo() { }

    public static class Result {
        public int dayOfWeek;              // Calendar.SUNDAY ... Calendar.SATURDAY
        public boolean weekdayFromApi;     // false = worked out on the phone
        public String holiday;             // name of the public holiday, or null
        public boolean holidayChecked;     // false = the holiday API could not be reached

        /** Not a class day (Sunday to Thursday are class days). */
        public boolean weekend() {
            for (int d : TimeUtil.WEEK) if (d == dayOfWeek) return false;
            return true;
        }
    }

    public interface Callback { void onResult(Result r); }

    private static final String[] NAMES = {"", "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};
    private static final Map<Integer, Map<String, String>> HOLIDAYS = new HashMap<>();     // year -> date -> name (kept after the first load)
    private static RequestQueue queue;

    public static void lookup(Context context, long dayStart, Callback cb) {
        if (queue == null) queue = Volley.newRequestQueue(context.getApplicationContext());
        final Calendar c = Calendar.getInstance();
        c.setTimeInMillis(dayStart);
        final Result res = new Result();
        res.dayOfWeek = c.get(Calendar.DAY_OF_WEEK);                      // local answer first, replaced by the API answer
        final String key = TimeUtil.dateKey(dayStart);
        final int year = c.get(Calendar.YEAR);
        final int[] pending = {2};
        final Runnable done = () -> { if (--pending[0] == 0) cb.onResult(res); };

        // 1) weekday
        String url = "https://api.aladhan.com/v1/gToH/" + new SimpleDateFormat("dd-MM-yyyy", Locale.ENGLISH).format(new Date(dayStart));
        queue.add(new JsonObjectRequest(Request.Method.GET, url, null,
                (JSONObject o) -> {
                    try {
                        String name = o.getJSONObject("data").getJSONObject("gregorian").getJSONObject("weekday").getString("en");
                        for (int i = 1; i <= 7; i++) if (NAMES[i].equalsIgnoreCase(name)) { res.dayOfWeek = i; res.weekdayFromApi = true; }
                    } catch (Exception ignored) { /* keep the local weekday */ }
                    done.run();
                },
                err -> done.run()));

        // 2) public holiday
        Map<String, String> cached = HOLIDAYS.get(year);
        if (cached != null) {
            res.holiday = cached.get(key); res.holidayChecked = true;
            done.run();
        } else {
            queue.add(new JsonArrayRequest(Request.Method.GET, "https://date.nager.at/api/v3/PublicHolidays/" + year + "/BD", null,
                    (JSONArray arr) -> {
                        Map<String, String> m = new HashMap<>();
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject h = arr.optJSONObject(i);
                            if (h == null || h.optString("date").isEmpty()) continue;
                            String old = m.get(h.optString("date")), name = h.optString("name");
                            m.put(h.optString("date"), old == null || old.equals(name) ? name : old + " / " + name);
                        }
                        HOLIDAYS.put(year, m);
                        res.holiday = m.get(key); res.holidayChecked = true;
                        done.run();
                    },
                    err -> done.run()));
        }
    }
}
