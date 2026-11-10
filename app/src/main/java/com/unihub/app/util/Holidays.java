package com.unihub.app.util;

import com.unihub.app.data.CustomHoliday;
import com.unihub.app.data.RoutineEntry;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * A CR holiday (one date or a range), or a government holiday, cancels the weekly classes of those dates automatically.
 * Nothing is written per class: the holiday list is checked each time, so a 10 day Eid break needs one entry,
 * and removing the holiday brings the classes back. An extra class that the CR added on purpose is still held.
 */
public final class Holidays {
    private Holidays() { }

    /**
     * The holiday that covers this date (yyyy-MM-dd), or null. A holiday set by the CR comes first; when several of
     * them cover the date the choice is fixed (earliest start, then earliest created, then id), never the random
     * Firestore order. If the CR set none, a government (public) holiday of that date counts the same way
     * (see PublicHolidays), so classes on public holidays are cancelled automatically.
     */
    public static CustomHoliday on(List<CustomHoliday> list, String dateKey) {
        if (dateKey == null) return null;
        CustomHoliday best = null;
        if (list != null) {
            for (CustomHoliday h : list) {
                if (h == null || !h.covers(dateKey)) continue;
                if (best == null || before(h, best)) best = h;
            }
        }
        return best != null ? best : PublicHolidays.on(dateKey);
    }

    private static boolean before(CustomHoliday a, CustomHoliday b) {
        int c = a.startDate.compareTo(b.startDate);
        if (c != 0) return c < 0;
        if (a.createdAt != b.createdAt) return a.createdAt < b.createdAt;
        String x = a.id == null ? "" : a.id, y = b.id == null ? "" : b.id;
        return x.compareTo(y) < 0;
    }

    // ------------------------------------------------------------------ date helpers

    /** "2026-11-20" + 1 -> "2026-11-21". */
    public static String addDays(String key, int n) {
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            Calendar c = Calendar.getInstance();
            c.setTime(f.parse(key));
            c.add(Calendar.DAY_OF_MONTH, n);
            return f.format(c.getTime());
        } catch (Exception e) { return key; }
    }

    // ------------------------------------------------------------------ overlap handling

    /** Result of taking a date range out of the holiday list: which days really become class days again. */
    public static final class Split {
        /** Days (ranges {from, to}) that no other holiday covers: classes are held again. */
        public final List<String[]> freed = new ArrayList<>();
        /** Days (ranges {from, to}) that another holiday still covers: still no class. */
        public final List<String[]> kept = new ArrayList<>();
        /** Purposes of the holidays that still cover the kept days. */
        public final Set<String> keptPurposes = new LinkedHashSet<>();
    }

    /**
     * Splits the range from..to (inclusive) into the days that no holiday of "others" covers (freed) and the days that
     * another holiday still covers (kept). "others" must NOT contain the holiday that is being removed.
     */
    public static Split split(List<CustomHoliday> others, String from, String to) {
        Split out = new Split();
        String freedFrom = null, keptFrom = null, last = null;
        String d = from;
        for (int guard = 0; d.compareTo(to) <= 0 && guard < 3700; guard++) {
            CustomHoliday o = on(others, d);
            if (o == null) {
                if (keptFrom != null) { out.kept.add(new String[]{keptFrom, last}); keptFrom = null; }
                if (freedFrom == null) freedFrom = d;
            } else {
                out.keptPurposes.add(purpose(o));
                if (freedFrom != null) { out.freed.add(new String[]{freedFrom, last}); freedFrom = null; }
                if (keptFrom == null) keptFrom = d;
            }
            last = d;
            d = addDays(d, 1);
        }
        if (freedFrom != null) out.freed.add(new String[]{freedFrom, last});
        if (keptFrom != null) out.kept.add(new String[]{keptFrom, last});
        return out;
    }

    /** "Thu, 12 Nov 2026" / "Sun, 15 Nov 2026 to Thu, 19 Nov 2026", several joined with commas and "and". */
    public static String rangesText(List<String[]> ranges) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ranges.size(); i++) {
            String[] r = ranges.get(i);
            if (i > 0) sb.append(i == ranges.size() - 1 ? " and " : ", ");
            sb.append(r[0].equals(r[1]) ? TimeUtil.prettyDate(r[0])
                    : TimeUtil.prettyDate(r[0]) + " to " + TimeUtil.prettyDate(r[1]));
        }
        return sb.toString();
    }

    /** The holiday that cancels this class on this date, or null (extra classes are never cancelled by a holiday). */
    public static CustomHoliday blocking(List<CustomHoliday> list, RoutineEntry r, String dateKey) {
        if (r == null || r.oneTime()) return null;
        return on(list, dateKey);
    }

    /** Purpose to show, e.g. "Winter Break". */
    public static String purpose(CustomHoliday h) {
        return h == null || h.purpose == null || h.purpose.trim().isEmpty() ? "Holiday" : h.purpose.trim();
    }
}
