package com.unihub.app.data;

import com.unihub.app.util.TrustedTime;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;
import com.google.android.gms.tasks.Task;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The printed KUET Section A / B routine, as ready-made entries. The CR can load them in one go (long-press the
 * "+" button of the routine). They are saved exactly like classes added by hand: auto ids, createdBy = the CR,
 * createdAt one after the other. Only a section that has no running routine yet is added, so nothing doubles.
 */
public class RoutinePreset {
    // class start / end time in minutes from midnight, looked up while importing (the app itself shows only times)
    private static final int[] START = {0, 480, 530, 580, 640, 690, 740, 870, 920, 970};
    private static final int[] END   = {0, 530, 580, 630, 690, 740, 790, 920, 970, 1020};
    // Calendar days: Sunday = 1 ... Thursday = 5
    private static final int SUN = 1, MON = 2, TUE = 3, WED = 4, THU = 5;

    public static Map<String, RoutineEntry> entries() {
        Map<String, RoutineEntry> m = new LinkedHashMap<>();
        m.putAll(entriesB());
        m.putAll(entriesA());
        return m;
    }

    /** Section A (groups A1 / A2), from the printed sheet. */
    public static Map<String, RoutineEntry> entriesA() {
        Map<String, RoutineEntry> m = new LinkedHashMap<>();
        sec = "A";
        // Sunday
        add(m, SUN, 1, 3, "CSE 2200", "SA + EK", "A1");
        add(m, SUN, 4, 4, "MATH 2207", "ARJ", "");
        add(m, SUN, 5, 5, "ECE 2213", "FRH / FH", "");
        add(m, SUN, 6, 6, "CSE 2201c", "SH / MAN", "");
        add(m, SUN, 7, 9, "CSE 2202", "SN + MAN", "A2");
        // Monday
        add(m, MON, 1, 3, "ECE 2214", "MRM + MIA", "A1");
        add(m, MON, 4, 4, "MATH 2207", "HZ", "");
        add(m, MON, 5, 5, "CSE 2209", "NS", "");
        add(m, MON, 6, 6, "HUM 2207c", "RA / FA", "");
        // Tuesday
        add(m, TUE, 4, 4, "HUM 2207", "RA", "");
        add(m, TUE, 5, 5, "ECE 2213", "FH", "");
        add(m, TUE, 6, 6, "CSE 2209", "DD", "");
        add(m, TUE, 7, 9, "CSE 2200", "SA + EK", "A2");
        // Wednesday
        add(m, WED, 2, 2, "CSE 2201", "SH", "");
        add(m, WED, 3, 3, "MATH 2207", "ARJ + HZ", "");
        add(m, WED, 7, 9, "ECE 2214", "MRM + MIA", "A2");
        add(m, WED, 7, 9, "CSE 2208", "MI + LBM", "A1");
        // Thursday
        add(m, THU, 1, 1, "CSE 2201", "MAN", "");
        add(m, THU, 2, 2, "HUM 2207", "FA", "");
        add(m, THU, 3, 3, "ECE 2213", "FH", "");
        add(m, THU, 4, 6, "CSE 2208", "MI + LBM", "A2");
        add(m, THU, 7, 9, "CSE 2202", "SN + MAN", "A1");
        return m;
    }

    private static String sec = "B";

    public static Map<String, RoutineEntry> entriesB() {
        Map<String, RoutineEntry> m = new LinkedHashMap<>();
        sec = "B";
        // Sunday
        add(m, SUN, 2, 2, "CSE 2209", "DD", "");
        add(m, SUN, 3, 3, "MATH 2207", "ARJ", "");
        add(m, SUN, 4, 4, "HUM 2207c", "RA / FA", "");
        // Monday
        add(m, MON, 1, 1, "CSE 2201c", "SH / MAN", "");
        add(m, MON, 2, 2, "ECE 2213", "FRH / FH", "");
        add(m, MON, 3, 3, "MATH 2207", "HZ", "");
        add(m, MON, 4, 6, "CSE 2202", "SN + MAN", "B1");
        add(m, MON, 7, 9, "ECE 2214", "MRM + MIA", "B2");
        // Tuesday
        add(m, TUE, 1, 1, "CSE 2209", "NS", "");
        add(m, TUE, 2, 2, "HUM 2207", "RA", "");
        add(m, TUE, 3, 3, "ECE 2213", "FRH", "");
        add(m, TUE, 4, 6, "CSE 2208", "MI + LBM", "B1");
        add(m, TUE, 4, 6, "CSE 2202", "SN + MAN", "B2");
        // Wednesday
        add(m, WED, 1, 3, "ECE 2214", "MRM + MIA", "B1");
        add(m, WED, 4, 4, "MATH 2207", "ARJ / HZ", "");
        add(m, WED, 5, 5, "CSE 2201", "SH", "");
        add(m, WED, 7, 9, "CSE 2200", "SA + EK", "B2");
        // Thursday
        add(m, THU, 1, 3, "CSE 2208", "MI + LBM", "B2");
        add(m, THU, 1, 3, "CSE 2200", "SA + EK", "B1");
        add(m, THU, 4, 4, "HUM 2207", "FA", "");
        add(m, THU, 5, 5, "ECE 2213", "FRH", "");
        add(m, THU, 6, 6, "CSE 2201", "MAN", "");
        return m;
    }

    private static void add(Map<String, RoutineEntry> m, int day, int p1, int p2, String course, String teacher, String group) {
        RoutineEntry e = new RoutineEntry();
        e.course = course;
        e.title = isSessional(course) ? "Sessional" : "Theory";
        e.teacher = teacher;
        e.room = "";
        e.group = group;
        e.section = sec;
        e.day = day;
        e.startMin = START[p1];
        e.endMin = END[p2];
        e.createdBy = "";
        m.put("sec" + sec.toLowerCase() + "_" + day + "_p" + p1 + "_" + course.replace(" ", "") + (group.isEmpty() ? "" : "_" + group), e);
    }

    /** KUET course numbering: an even last digit is a sessional (lab) course. */
    private static boolean isSessional(String course) {
        String digits = course.replaceAll("\\D", "");
        return !digits.isEmpty() && (digits.charAt(digits.length() - 1) - '0') % 2 == 0;
    }

    /** Saves the chosen sections as normal routine documents, one after the other in time, in the CR's name. */
    public static Task<Void> upload(boolean a, boolean b, String crName) {
        FirebaseFirestore fs = FirebaseFirestore.getInstance();
        WriteBatch batch = fs.batch();
        Map<String, RoutineEntry> all = new LinkedHashMap<>();
        if (b) all.putAll(entriesB());
        if (a) all.putAll(entriesA());
        long now = TrustedTime.now();
        long dayStart = com.unihub.app.util.TimeUtil.dayStart(now);
        long t = Math.max(dayStart, now - all.size() * 15000L);          // 15 s apart, the last one is "now"
        for (RoutineEntry e : all.values()) {
            e.createdBy = crName;
            e.createdAt = t;
            t += 15000L;
            batch.set(fs.collection("routine").document(), e);          // new auto id, like AddRoutineActivity
        }
        return batch.commit();
    }
}
