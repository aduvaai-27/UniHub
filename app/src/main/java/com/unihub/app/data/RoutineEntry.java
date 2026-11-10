package com.unihub.app.data;

import com.google.firebase.firestore.DocumentId;

/** Firestore "routine" document: one weekly class. day = Calendar.DAY_OF_WEEK (1=Sun … 7=Sat). */
public class RoutineEntry {
    @DocumentId public String id;
    public String course, title, teacher, room, createdBy;
    public String group;      // "" = whole section, otherwise "A1", "A2", "B1" or "B2" (lab group)
    public String section;    // "A" or "B" (missing on old entries = "B")
    public String endDate;    // "yyyy-MM-dd": a removed weekly class stops on this date (it is kept, so old attendance stays); empty = still running
    public String date;       // "yyyy-MM-dd" for an EXTRA class held on that one date only; empty = weekly class
    public int day, startMin, endMin;      // minutes from midnight
    public long createdAt;

    public RoutineEntry() { }

    /** True for an extra class: it is held on one date only (the "date" field), not every week. */
    public boolean oneTime() { return date != null && !date.isEmpty(); }

    /** Does this entry take place on the given date? Weekly: same weekday. Extra class: exactly that date. */
    public boolean occursOn(String dateKey, int dayOfWeek) {
        if (oneTime()) return date.equals(dateKey);
        return day == dayOfWeek && !endedBy(dateKey);
    }

    /** A removed weekly class: it no longer takes place on or after its endDate. Dates before that keep their history. */
    public boolean endedBy(String dateKey) {
        return !oneTime() && endDate != null && !endDate.isEmpty() && dateKey.compareTo(endDate) >= 0;
    }

    /** Lab group of this class, tidy ("" = the whole section). */
    public String groupOf() { return group == null ? "" : group.trim().toUpperCase(); }

    /**
     * Would these two classes need the same students at the same time?
     * Same day + overlapping time + same section, and then:
     *   - a whole-section class (A or B) clashes with every class of that section (A1, A2 / B1, B2 or whole);
     *   - two lab-group classes clash only when they are the same group (B1 and B2 can run together).
     * Section A and Section B never clash with each other. Back-to-back classes (one ends when the next starts) are fine.
     */
    public boolean clashesWith(RoutineEntry o) {
        if (day != o.day || !(startMin < o.endMin && o.startMin < endMin)) return false;
        if (oneTime() && o.oneTime() && !date.equals(o.date)) return false;          // two extra classes on different dates
        if (!sectionOf().equals(o.sectionOf())) return false;
        String a = groupOf(), b = o.groupOf();
        return a.isEmpty() || b.isEmpty() || a.equals(b);
    }

    /** "Section B (whole section)" or "Group B1", for messages. */
    public String scopeLabel() {
        return groupOf().isEmpty() ? "Section " + sectionOf() + " (whole section)" : "Group " + groupOf();
    }

    /** Section this class belongs to. Older entries have no section and were all Section B. */
    public String sectionOf() {
        if (section != null && !section.isEmpty()) return section;
        if (group != null && !group.isEmpty()) return group.substring(0, 1);
        return "B";
    }
}
