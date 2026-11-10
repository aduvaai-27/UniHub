package com.unihub.app.data;

import com.unihub.app.util.Ui;

/**
 * One rule for every attendance percentage in the app (course summary, class records, ring):
 *   90% or more  -> Excellent
 *   80% to 89%   -> Good
 *   60% to 79%   -> Okay
 *   below 60%    -> Critical
 * The whole-number percentage that is shown on screen is the one that is judged, so the label always matches the number.
 */
public class AttendanceLevel {
    public static final String LEGEND = "Excellent 90%+   |   Good 80%+   |   Okay 60%+   |   Critical below 60%";

    public static String label(int pct) {
        return pct >= 90 ? "Excellent" : pct >= 80 ? "Good" : pct >= 60 ? "Okay" : "Critical";
    }

    public static int color(int pct) {
        return pct >= 90 ? Ui.GREEN : pct >= 80 ? Ui.BLUE : pct >= 60 ? Ui.AMBER : Ui.RED;
    }

    /** present out of total as a whole-number percentage (0 when total is 0). */
    public static int percent(int present, int total) {
        return total <= 0 ? 0 : Math.round(present * 100f / total);
    }
}
