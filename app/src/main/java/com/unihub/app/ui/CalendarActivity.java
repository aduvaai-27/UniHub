package com.unihub.app.ui;

import com.unihub.app.util.TrustedTime;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.android.volley.Request;
import com.google.firebase.FirebaseApp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.unihub.app.data.CustomHoliday;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.Volley;
import com.unihub.app.R;
import com.unihub.app.databinding.ActivityCalendarBinding;
import com.unihub.app.util.TimeUtil;
import com.unihub.app.util.Ui;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Calendar service: a month calendar that only marks public holidays.
 * Volley: HTTP GET (REST) with a RequestQueue; the JSON response (Nager.Date, country BD) is turned into
 * a date -> holiday name map. Nothing about assignments or exams is shown here.
 */
public class CalendarActivity extends BaseActivity {
    private static final String TAG = "holidays";
    private static final int HOLIDAY_RED = 0xFFB3261E, HOLIDAY_BG = 0x1FB3261E;      // public holiday (API)
    private static final int CR_PLUM = 0xFF6A4C93, CR_BG = 0x1F6A4C93;                // holiday / vacation set by the CR

    private ActivityCalendarBinding b;
    private RequestQueue queue;                                           // RequestQueue
    private final Map<String, String> holidays = new HashMap<>();         // "yyyy-MM-dd" -> holiday name
    private final Set<Integer> loadedYears = new HashSet<>();
    private final Set<Integer> loadingYears = new HashSet<>();
    private final Calendar shown = TimeUtil.calendarNow();                // month on screen
    private long selected = TimeUtil.dayStart(TrustedTime.now());
    private boolean loadFailed = false;
    private boolean picked = false;                                           // the user tapped a day in this month
    private List<CustomHoliday> custom = new ArrayList<>();                   // set by the CR (Firestore "holidays")
    private ListenerRegistration customReg;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityCalendarBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        queue = Volley.newRequestQueue(this);
        shown.set(Calendar.DAY_OF_MONTH, 1);

        b.btnPrev.setOnClickListener(v -> moveMonth(-1));
        b.btnNext.setOnClickListener(v -> moveMonth(1));
        autoSelect();
        if (!FirebaseApp.getApps(this).isEmpty()) {
            customReg = FirebaseFirestore.getInstance().collection("holidays").addSnapshotListener((snap, e) -> {
                if (snap == null) return;
                custom = snap.toObjects(CustomHoliday.class);
                autoSelect();
                render();
            });
        }
        render();
        loadYear(shown.get(Calendar.YEAR));
    }

    /** Holidays / vacations set by the CR that cover this date. */
    private List<CustomHoliday> customOn(String key) {
        List<CustomHoliday> out = new ArrayList<>();
        for (CustomHoliday h : custom) if (h.covers(key)) out.add(h);
        return out;
    }

    @Override protected android.view.View offlineBar() { return b == null ? null : b.tvOffline; }

    /** Offline bar is handled by BaseActivity. Back online: load the holidays again. */
    @Override
    protected void onConnectivityChanged(boolean on) {
        if (on) { loadFailed = false; loadYear(shown.get(Calendar.YEAR)); }
        render();
    }

    private void moveMonth(int delta) {
        shown.add(Calendar.MONTH, delta);
        picked = false;
        autoSelect();
        render();
        loadYear(shown.get(Calendar.YEAR));
    }

    /**
     * Keeps the selected date inside the month on screen, so the top card and the month list never disagree:
     * the first holiday of the month (public or set by the CR) if there is one, otherwise today (current month)
     * or the 1st. Not used once the user taps a day.
     */
    private void autoSelect() {
        if (picked) return;
        Calendar c = (Calendar) shown.clone();
        Calendar now = TimeUtil.calendarNow();
        boolean thisMonth = c.get(Calendar.YEAR) == now.get(Calendar.YEAR) && c.get(Calendar.MONTH) == now.get(Calendar.MONTH);
        long todayStart = TimeUtil.dayStart(TrustedTime.now());
        long pick = 0;
        for (int d = 1; d <= c.getActualMaximum(Calendar.DAY_OF_MONTH); d++) {
            c.set(Calendar.DAY_OF_MONTH, d);
            long ds = TimeUtil.dayStart(c.getTimeInMillis());
            String key = TimeUtil.dateKey(ds);
            if (thisMonth && ds < todayStart) continue;               // current month: look for today or a later holiday
            if (holidays.containsKey(key) || !customOn(key).isEmpty()) { pick = ds; break; }
        }
        if (pick == 0) {
            c.set(Calendar.DAY_OF_MONTH, thisMonth ? now.get(Calendar.DAY_OF_MONTH) : 1);
            pick = TimeUtil.dayStart(c.getTimeInMillis());
        }
        selected = pick;
    }

    /** Volley GET: https://date.nager.at/api/v3/PublicHolidays/{year}/BD  (JSON array of holidays). */
    private void loadYear(int year) {
        if (loadedYears.contains(year) || loadingYears.contains(year)) return;
        if (!online) { loadFailed = true; render(); return; }            // feature blocked while offline
        loadingYears.add(year);
        String url = "https://date.nager.at/api/v3/PublicHolidays/" + year + "/BD";
        JsonArrayRequest req = new JsonArrayRequest(Request.Method.GET, url, null,
                (JSONArray res) -> {                                       // response listener
                    loadingYears.remove(year);
                    loadedYears.add(year);
                    loadFailed = false;
                    for (int i = 0; i < res.length(); i++) {
                        JSONObject h = res.optJSONObject(i);
                        if (h == null) continue;
                        String date = h.optString("date"), name = h.optString("name");
                        if (date.isEmpty()) continue;
                        String old = holidays.get(date);
                        holidays.put(date, old == null || old.equals(name) ? name : old + " / " + name);
                    }
                    autoSelect();
                    render();
                },
                err -> {                                                   // error listener (offline etc.)
                    loadingYears.remove(year);
                    loadFailed = true;
                    render();
                });
        req.setTag(TAG);
        queue.add(req);
    }

    private void render() {
        b.tvMonth.setText(new SimpleDateFormat("MMMM yyyy", Locale.ENGLISH).format(shown.getTime()));
        drawGrid();
        drawSelected();
        drawMonthList();
    }

    private void drawGrid() {
        GridLayout g = b.grid;
        g.removeAllViews();
        String[] heads = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
        for (String h : heads) g.addView(cell(h, true, 0, false));

        Calendar c = (Calendar) shown.clone();
        int offset = c.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY;      // blank cells before day 1
        int days = c.getActualMaximum(Calendar.DAY_OF_MONTH);
        for (int i = 0; i < offset; i++) g.addView(cell("", false, 0, false));

        long today = TimeUtil.dayStart(TrustedTime.now());
        for (int d = 1; d <= days; d++) {
            c.set(Calendar.DAY_OF_MONTH, d);
            long start = TimeUtil.dayStart(c.getTimeInMillis());
            String key = TimeUtil.dateKey(start);
            int accent = holidays.containsKey(key) ? HOLIDAY_RED : !customOn(key).isEmpty() ? CR_PLUM : 0;
            TextView tv = cell(String.valueOf(d), false, accent, start == selected);
            if (start == today && accent == 0) tv.setTypeface(null, Typeface.BOLD);
            final long pick = start;
            tv.setOnClickListener(v -> { selected = pick; picked = true; render(); });
            g.addView(tv);
        }
    }

    private TextView cell(String text, boolean header, int accent, boolean isSelected) {
        boolean holiday = accent != 0;
        TextView tv = new TextView(this);
        int w = (getResources().getDisplayMetrics().widthPixels - dp(20 + 8 + 32)) / 7;
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = w; lp.height = header ? dp(32) : dp(44);
        lp.setMargins(0, dp(1), 0, dp(1));
        tv.setLayoutParams(lp);
        tv.setGravity(Gravity.CENTER);
        tv.setText(text);
        tv.setTextSize(header ? 12 : 15);
        if (header) { tv.setTextColor(getColor(R.color.muted)); tv.setTypeface(null, Typeface.BOLD); return tv; }
        tv.setTextColor(holiday ? accent : getColor(R.color.ink));
        if (holiday) tv.setTypeface(null, Typeface.BOLD);
        if (!text.isEmpty() && (holiday || isSelected)) {
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setCornerRadius(dp(6));
            bg.setColor(holiday ? (accent & 0x00FFFFFF) | 0x1F000000 : 0x00000000);
            if (isSelected) bg.setStroke(dp(2), holiday ? accent : getColor(R.color.ink));
            tv.setBackground(bg);
        }
        return tv;
    }

    /** Selected date: shows the holiday name, or "Not a holiday". */
    private void drawSelected() {
        b.tvDate.setText(new SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.ENGLISH).format(new Date(selected)));
        b.list.removeAllViews();
        String selKey = TimeUtil.dateKey(selected);
        String name = holidays.get(selKey);
        List<CustomHoliday> mine = customOn(selKey);
        if (name != null) b.list.addView(holidayCard("HOLIDAY", R.color.danger, name, dateText(new Date(selected))));
        for (CustomHoliday h : mine) b.list.addView(customCard(h));
        if (name == null && mine.isEmpty()) {
            Calendar sel = Calendar.getInstance();
            sel.setTimeInMillis(selected);
            String msg;
            if (!online && !loadedYears.contains(sel.get(Calendar.YEAR))) msg = "You are offline. Holidays cannot be loaded.";
            else if (loadFailed) msg = "Could not load holidays. Check the internet connection.";
            else msg = loadedYears.isEmpty() ? "Loading holidays..." : "Not a holiday.";
            b.list.addView(line(msg, R.color.muted, 14, false));
        }
    }

    /** All holidays of the month on screen, as a short list. */
    private void drawMonthList() {
        List<String> keys = new ArrayList<>();
        String prefix = new SimpleDateFormat("yyyy-MM", Locale.US).format(shown.getTime());
        for (String k : holidays.keySet()) if (k.startsWith(prefix)) keys.add(k);
        java.util.Collections.sort(keys);
        String monthFrom = prefix + "-01", monthTo = prefix + "-31";
        List<CustomHoliday> crList = new ArrayList<>();
        for (CustomHoliday h : custom) if (h.overlaps(monthFrom, monthTo)) crList.add(h);
        java.util.Collections.sort(crList, (x, y) -> x.startDate.compareTo(y.startDate));
        if (keys.isEmpty() && crList.isEmpty()) return;
        b.list.addView(line("HOLIDAYS THIS MONTH", R.color.muted, 11, true));
        for (String k : keys) {
            try {
                Date d = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(k);
                b.list.addView(holidayCard("HOLIDAY", R.color.danger, holidays.get(k), dateText(d)));
            } catch (Exception ignored) { }
        }
        for (CustomHoliday h : crList) b.list.addView(customCard(h));
    }

    private static String dateText(Date d) { return new SimpleDateFormat("EEE, dd MMM yyyy", Locale.ENGLISH).format(d); }

    /** A holiday or vacation set by the CR: purple tag, purpose, date or date range. */
    private LinearLayout customCard(CustomHoliday h) {
        String when = h.isRange() ? TimeUtil.prettyDate(h.startDate) + "  to  " + TimeUtil.prettyDate(h.endOf())
                : TimeUtil.prettyDate(h.startDate);
        LinearLayout card = holidayCard(h.label().toUpperCase() + " (SET BY CR)", R.color.plum, Ui.nz(h.purpose), when);
        return card;
    }

    private LinearLayout holidayCard(String tagText, int tagColor, String name, String dateLine) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_card);
        card.setPadding(dp(16), dp(12), dp(16), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(dp(4), dp(6), dp(4), dp(2));
        card.setLayoutParams(lp);
        TextView tag = line(tagText, tagColor, 11, true);
        card.addView(tag);
        card.addView(line(name, R.color.ink, 16, true));
        card.addView(line(dateLine, R.color.muted, 13, false));
        return card;
    }

    private TextView line(String s, int color, int sp, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(s); tv.setTextSize(sp); tv.setTextColor(getColor(color));
        if (bold) tv.setTypeface(null, Typeface.BOLD);
        tv.setPadding(dp(6), dp(2), dp(6), dp(2));
        return tv;
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    @Override
    protected void onDestroy() {
        if (queue != null) queue.cancelAll(TAG);
        if (customReg != null) customReg.remove();
        super.onDestroy();
    }
}
