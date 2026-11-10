package com.unihub.app.data;

import com.unihub.app.util.TimeUtil;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

/** One occurrence of a routine class on a specific date. Generated from the weekly routine, never stored. */
public class ClassSession {
    public RoutineEntry r;
    public String date;          // yyyy-MM-dd
    public long start, end;      // millis

    public String key() { return date + "_" + r.id; }

    /** Does this routine entry concern a student of the given lab group? ("" = no filter / whole section) */
    public static boolean appliesTo(RoutineEntry r, String group) {
        if (group == null || group.isEmpty()) return true;
        if (!r.sectionOf().equals(group.substring(0, 1))) return false;     // other section
        return r.group == null || r.group.isEmpty() || r.group.equals(group);
    }

    /** All sessions between two days (inclusive), newest first. */
    public static List<ClassSession> build(List<RoutineEntry> routine, String group, long fromDay, long toDay) {
        List<ClassSession> out = new ArrayList<>();
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(toDay);
        for (int guard = 0; guard < 400; guard++) {
            long ds = TimeUtil.dayStart(c.getTimeInMillis());
            if (ds < fromDay) break;
            int dow = c.get(Calendar.DAY_OF_WEEK);
            String date = TimeUtil.dateKey(ds);
            for (RoutineEntry r : routine) {
                if (r.id == null || !r.occursOn(date, dow) || !appliesTo(r, group)) continue;
                if (ds < TimeUtil.dayStart(r.createdAt)) continue;          // class did not exist yet
                ClassSession s = new ClassSession();
                s.r = r; s.date = date;
                s.start = ds + r.startMin * 60000L;
                s.end = ds + r.endMin * 60000L;
                out.add(s);
            }
            c.add(Calendar.DAY_OF_YEAR, -1);
        }
        Collections.sort(out, (a, b) -> Long.compare(b.start, a.start));
        return out;
    }
}
