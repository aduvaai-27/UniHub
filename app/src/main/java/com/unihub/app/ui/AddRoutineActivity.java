package com.unihub.app.ui;

import com.unihub.app.util.TrustedTime;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Toast;
import com.google.firebase.firestore.FirebaseFirestore;
import com.unihub.app.R;
import com.unihub.app.data.RoutineEntry;
import com.unihub.app.databinding.ActivityAddRoutineBinding;
import com.unihub.app.util.Session;
import com.unihub.app.util.TimeUtil;
import com.unihub.app.util.Ui;
import java.util.ArrayList;
import java.util.List;

/** CR only: add a weekly class to the shared routine. The day is chosen from a list and times from scroll wheels. */
public class AddRoutineActivity extends BaseActivity {
    private ActivityAddRoutineBinding b;
    private final List<String> dayNames = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityAddRoutineBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        if (!Session.isStaff(this)) { finish(); return; }

        for (int d : TimeUtil.WEEK) dayNames.add(TimeUtil.dayName(d));
        fill(b.acDay, dayNames, dayNames.get(0));
        List<String> groups = new ArrayList<>(java.util.Arrays.asList("Whole Section A", "Whole Section B", "A1", "A2", "B1", "B2"));
        fill(b.acGroup, groups, groups.get(0));
        b.twStart.setMinutes(9 * 60);
        b.twEnd.setMinutes(10 * 60);
        b.btnSave.setOnClickListener(v -> save());
    }

    private void fill(AutoCompleteTextView view, List<String> items, String selected) {
        Ui.dropdown(this, view, items, selected);
    }

    @Override protected android.view.View offlineBar() { return b == null ? null : b.tvOffline; }

    /** Offline: saving is blocked. */
    @Override protected void onConnectivityChanged(boolean on) { setNeedsInternet(b.btnSave, on); }

    /** Professional message: who already has a class, which one, when, and what to do about it. */
    private static String clashMessage(RoutineEntry added, RoutineEntry existing) {
        return "Time clash: " + existing.scopeLabel() + " already has " + Ui.nz(existing.course) + " on "
                + TimeUtil.dayName(existing.day) + ", " + TimeUtil.formatMin(existing.startMin) + " - " + TimeUtil.formatMin(existing.endMin)
                + ", which overlaps with this class for " + added.scopeLabel()
                + ". Please remove that class first or choose a different time.";
    }

    private void save() {
        if (!requireOnline()) return;
        String course = b.etCourse.getText().toString().trim();
        if (course.isEmpty()) { b.etCourse.setError("Required"); return; }
        if (b.rgType.getCheckedRadioButtonId() == -1) { Toast.makeText(this, "Choose the class type: Theory or Sessional.", Toast.LENGTH_SHORT).show(); return; }
        int start = b.twStart.getMinutes();
        int end = b.twEnd.getMinutes();
        if (end <= start) {
            Toast.makeText(this, "End time must be later than the start time.", Toast.LENGTH_SHORT).show();
            return;
        }

        RoutineEntry r = new RoutineEntry();
        r.course = course;
        r.title = b.rgType.getCheckedRadioButtonId() == R.id.rbSessional ? "Sessional" : "Theory";
        r.teacher = b.etTeacher.getText().toString().trim();
        r.room = b.etRoom.getText().toString().trim();
        String g = b.acGroup.getText().toString();
        r.group = g.startsWith("Whole") ? "" : g;
        r.section = g.startsWith("Whole") ? g.substring(g.length() - 1) : g.substring(0, 1);
        r.day = TimeUtil.WEEK[Math.max(0, dayNames.indexOf(b.acDay.getText().toString()))];
        r.startMin = start; r.endMin = end;
        r.createdAt = TrustedTime.now();
        r.createdBy = Session.name(this);

        b.btnSave.setEnabled(false);
        FirebaseFirestore fs = FirebaseFirestore.getInstance();
        // one class at a time for the same students: refuse an overlapping class (rules: RoutineEntry.clashesWith)
        fs.collection("routine").get().addOnSuccessListener(snap -> {
            for (RoutineEntry x : snap.toObjects(RoutineEntry.class)) {
                if (x.oneTime()) continue;                       // an extra class of one date does not block a weekly class
                if (x.endedBy(TimeUtil.dateKey(TimeUtil.dayStart(TrustedTime.now())))) continue;   // removed class: its time is free
                if (r.clashesWith(x)) {
                    setNeedsInternet(b.btnSave, online);
                    Toast.makeText(this, clashMessage(r, x), Toast.LENGTH_LONG).show();
                    return;
                }
            }
            fs.collection("routine").add(r)
                    .addOnSuccessListener(y -> { Toast.makeText(this, "Class added successfully.", Toast.LENGTH_SHORT).show(); finish(); })
                    .addOnFailureListener(e -> { setNeedsInternet(b.btnSave, online);
                        Toast.makeText(this, Ui.firebaseError(e), Toast.LENGTH_LONG).show(); });
        }).addOnFailureListener(e -> { setNeedsInternet(b.btnSave, online);
            Toast.makeText(this, Ui.firebaseError(e), Toast.LENGTH_LONG).show(); });
    }
}
