package com.unihub.app.util;

import com.unihub.app.data.TaskItem;
import java.util.ArrayList;
import java.util.List;

/**
 * Lines for the service notification about the nearest upcoming assignment and the nearest upcoming exam,
 * for example "Next assignment: CSE 2201 - Lab Report 3  |  Due tomorrow".
 */
public final class NextDeadline {
    private NextDeadline() { }

    /** Zero, one or two lines (assignment first, then exam). Past deadlines are ignored. */
    public static List<String> lines(List<TaskItem> tasks, long now) {
        List<String> out = new ArrayList<>();
        if (tasks == null) return out;
        TaskItem a = nearest(tasks, TaskItem.ASSIGNMENT, now), e = nearest(tasks, TaskItem.EXAM, now);
        if (a != null) out.add("Next assignment: " + label(a) + "  |  " + TimeUtil.countdownText(a.due));
        if (e != null) out.add("Next exam: " + label(e) + "  |  " + TimeUtil.countdownText(e.due).replaceFirst("^Due ", ""));
        return out;
    }

    private static TaskItem nearest(List<TaskItem> tasks, String type, long now) {
        TaskItem best = null;
        for (TaskItem t : tasks) {
            if (!type.equals(t.type) || t.due < now) continue;
            if (best == null || t.due < best.due) best = t;
        }
        return best;
    }

    private static String label(TaskItem t) {
        String title = Ui.nz(t.title);
        return Ui.nz(t.course).isEmpty() ? title : t.course + " - " + title;
    }
}
