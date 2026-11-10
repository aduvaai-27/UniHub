package com.unihub.app.util;

import com.unihub.app.data.ClassSession;
import com.unihub.app.data.CustomHoliday;
import com.unihub.app.data.RoutineEntry;
import java.util.Calendar;
import java.util.List;
import java.util.Set;

/**
 * Finds the class that is running now, or the next one that will be held, for one lab group.
 * Looks 7 days ahead (today + 6) and skips classes the CR marked as "not held". Used by the foreground service
 * to keep its notification up to date.
 */
public final class NextClass {
    private NextClass() { }

    /** Today and the six days after it, the same 7 days as the Next 7 Days screen. */
    public static final int WINDOW_DAYS = 7;

    public RoutineEntry r;
    public long start, end;        // millis
    public int dayOffset;          // 0 = today, 1 = tomorrow ...
    public boolean inProgress;

    /** @param cancelled session keys ("yyyy-MM-dd_routineId") of classes that were not held. May be null. */
    public static NextClass find(List<RoutineEntry> routine, String group, Set<String> cancelled, long now) {
        return find(routine, group, cancelled, null, now);
    }

    /** Same, and also skips weekly classes that fall on a CR holiday. */
    public static NextClass find(List<RoutineEntry> routine, String group, Set<String> cancelled,
                                 List<CustomHoliday> holidays, long now) {
        if (routine == null || routine.isEmpty()) return null;
        Calendar c = Calendar.getInstance();
        for (int off = 0; off < WINDOW_DAYS; off++) {         // changes further than 7 days away are not shown yet
            c.setTimeInMillis(now);
            c.add(Calendar.DAY_OF_YEAR, off);
            long ds = TimeUtil.dayStart(c.getTimeInMillis());
            int dow = c.get(Calendar.DAY_OF_WEEK);
            String date = TimeUtil.dateKey(ds);
            NextClass best = null;
            for (RoutineEntry r : routine) {
                if (r.id == null || !r.occursOn(date, dow) || !ClassSession.appliesTo(r, group)) continue;
                if (ds < TimeUtil.dayStart(r.createdAt)) continue;
                if (cancelled != null && cancelled.contains(date + "_" + r.id)) continue;
                if (Holidays.blocking(holidays, r, date) != null) continue;                // holiday: no class
                long s = ds + r.startMin * 60000L, e = ds + r.endMin * 60000L;
                if (e <= now) continue;                                   // already finished
                if (best == null || s < best.start) {
                    best = new NextClass();
                    best.r = r; best.start = s; best.end = e; best.dayOffset = off;
                    best.inProgress = now >= s;
                }
            }
            if (best != null) return best;
        }
        return null;
    }

    /** Short line for the notification, for example "Next class: CSE 2201 at 10:00 AM (in 25 min)". */
    public String headline(long now) {
        String course = Ui.nz(r.course).isEmpty() ? Ui.nz(r.title) : Ui.nz(r.course);
        if (inProgress) return "Now: " + course + " until " + TimeUtil.formatMin(r.endMin);
        String when;
        if (dayOffset == 0) {
            when = "at " + TimeUtil.formatMin(r.startMin);
            long min = Math.max(0, (start - now) / 60000);
            if (min <= 60) when += " (in " + min + " min)";
        } else if (dayOffset == 1) {
            when = "tomorrow at " + TimeUtil.formatMin(r.startMin);
        } else {
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(start);
            when = TimeUtil.shortDay(c.get(Calendar.DAY_OF_WEEK)) + " at " + TimeUtil.formatMin(r.startMin);
        }
        return "Next class: " + course + " " + when;
    }

    /** Second line: course title, room and teacher when known. */
    public String detail() {
        StringBuilder sb = new StringBuilder();
        if (!Ui.nz(r.title).isEmpty()) sb.append(r.title);
        if (!Ui.nz(r.room).isEmpty()) sb.append(sb.length() > 0 ? "  |  " : "").append("Room: ").append(r.room);
        if (!Ui.nz(r.teacher).isEmpty()) sb.append(sb.length() > 0 ? "  |  " : "").append(r.teacher);
        return sb.toString();
    }
}
