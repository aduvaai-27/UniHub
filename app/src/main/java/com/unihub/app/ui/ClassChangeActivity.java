package com.unihub.app.ui;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import com.google.firebase.FirebaseApp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.WriteBatch;
import com.unihub.app.R;
import com.unihub.app.data.Cancellation;
import com.unihub.app.data.ClassSession;
import com.unihub.app.data.CustomHoliday;
import com.unihub.app.data.Notice;
import com.unihub.app.data.RoutineEntry;
import com.unihub.app.databinding.ActivityClassChangeBinding;
import com.unihub.app.databinding.ItemClassChangeBinding;
import com.unihub.app.util.ClassUndo;
import com.unihub.app.util.DateInfo;
import com.unihub.app.util.Holidays;
import com.unihub.app.util.Session;
import com.unihub.app.util.TimeUtil;
import com.unihub.app.util.TrustedTime;
import com.unihub.app.util.Ui;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * CR only. Two services for ONE date:
 *   Extra Class        - adds a class that is held on that date only (a routine entry with a "date"); it appears in
 *                        Today, Attendance and Class Records of the students of that section / group like any other class.
 *   Class Cancellation - marks a class of that date as "not held" (the "cancellations" collection used by the whole app).
 *
 * Steps: 1. option  2. date (weekday from a REST API; any date is allowed)
 *        3. section / group  4. the routine of that day (Firebase) and the action.
 */
public class ClassChangeActivity extends BaseActivity {
    private static final int MODE_NONE = 0, MODE_EXTRA = 1, MODE_CANCEL = 2;

    private ActivityClassChangeBinding b;
    private FirebaseFirestore fs;
    private final List<ListenerRegistration> regs = new ArrayList<>();
    private List<RoutineEntry> routine = new ArrayList<>();
    private final Set<String> cancelled = new HashSet<>();
    private List<CustomHoliday> holidays = new ArrayList<>();
    private final Runnable onPublicHolidays = this::render;               // public holidays arrived: redraw

    private int mode = MODE_NONE;
    private long dayStart = 0;                 // chosen date (midnight)
    private String dateKey = null;             // "yyyy-MM-dd"
    private int dow = 0;                       // weekday of the chosen date
    private boolean dateOk = false;            // a date was picked and its weekday is known
    private String dateInfoText = "";          // weekday line of the chosen date
    private String scope = null;               // "Whole Section A", "A1", ...
    private int lookupToken = 0;               // ignores the answer of an older date lookup

    private static final List<String> SCOPES = Arrays.asList("Whole Section A", "Whole Section B", "A1", "A2", "B1", "B2");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityClassChangeBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        if (!Session.isStaff(this)) { finish(); return; }
        if (FirebaseApp.getApps(this).isEmpty()) {
            Toast.makeText(this, "Firebase is not connected.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        fs = FirebaseFirestore.getInstance();

        Ui.dropdown(this, b.acScope, SCOPES, null);
        b.acScope.setOnItemClickListener((p, v, pos, id) -> { scope = SCOPES.get(pos); render(); });
        b.twStart.setMinutes(9 * 60);
        b.twEnd.setMinutes(10 * 60);

        b.toggleMode.addOnButtonCheckedListener((g, id, checked) -> {
            if (!checked) return;
            mode = id == com.unihub.app.R.id.btnModeExtra ? MODE_EXTRA : MODE_CANCEL;
            render();
        });
        b.btnDate.setOnClickListener(v -> pickDate());
        b.btnSave.setOnClickListener(v -> saveExtra());

        regs.add(fs.collection("routine").addSnapshotListener((snap, e) -> {
            if (e != null) { Toast.makeText(this, Ui.firebaseError(e), Toast.LENGTH_LONG).show(); return; }
            if (snap == null) return;
            routine = snap.toObjects(RoutineEntry.class);
            render();
        }));
        regs.add(fs.collection("cancellations").addSnapshotListener((snap, e) -> {
            if (snap == null) return;
            cancelled.clear();
            for (Cancellation c : snap.toObjects(Cancellation.class)) if (c.sessionKey != null) cancelled.add(c.sessionKey);
            render();
        }));
        regs.add(fs.collection("holidays").addSnapshotListener((snap, e) -> {
            if (snap == null) return;
            holidays = snap.toObjects(CustomHoliday.class);
            render();
        }));
        com.unihub.app.util.PublicHolidays.addListener(onPublicHolidays);
        render();
    }

    @Override protected View offlineBar() { return b == null ? null : b.tvOffline; }

    @Override
    protected void onConnectivityChanged(boolean on) {
        setNeedsInternet(b.btnSave, on);
    }

    // ------------------------------------------------------------------ step 2: the date

    private void pickDate() {
        if (!requireOnline()) return;
        long today = TimeUtil.dayStart(TrustedTime.now());
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(dayStart > 0 ? dayStart : today);
        DatePickerDialog d = new DatePickerDialog(this, (view, y, m, day) -> {
            Calendar p = Calendar.getInstance();
            p.set(y, m, day, 0, 0, 0);
            p.set(Calendar.MILLISECOND, 0);
            onDatePicked(TimeUtil.dayStart(p.getTimeInMillis()));
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
        d.getDatePicker().setMinDate(today);                 // only today and the future
        d.show();
    }

    private void onDatePicked(long ds) {
        dayStart = ds;
        dateKey = TimeUtil.dateKey(ds);
        dateOk = false;
        b.btnDate.setText(TimeUtil.prettyDate(dateKey));
        b.tvDateInfo.setVisibility(View.VISIBLE);
        b.tvDateInfo.setTextColor(getColor(com.unihub.app.R.color.ink));
        b.tvDateInfo.setText("Checking the date...");
        render();
        final int token = ++lookupToken;
        DateInfo.lookup(this, ds, r -> {
            if (token != lookupToken) return;                // the CR picked another date meanwhile
            dow = r.dayOfWeek;
            String day = TimeUtil.dayName(dow);
            CustomHoliday crHol = Holidays.on(holidays, dateKey);
            String reason = r.weekend() ? day + " is a weekend holiday (Friday and Saturday are off)"
                    : crHol != null ? "Holiday: " + Holidays.purpose(crHol)
                    : r.holiday != null ? "Public holiday: " + r.holiday : null;
            if (reason != null) {                            // holidays are not accepted: say why and clear the date
                String pretty = TimeUtil.prettyDate(dateKey);
                resetDate();
                Toast.makeText(this, reason + " (" + pretty + "). Choose another date.", Toast.LENGTH_LONG).show();
                return;
            }
            dateOk = true;
            dateInfoText = day + ", " + TimeUtil.prettyDate(dateKey).substring(5);
            render();
        });
    }

    /** Forgets the picked date (it was a holiday) so the next steps stay hidden. */
    private void resetDate() {
        dayStart = 0; dateKey = null; dateOk = false; dateInfoText = "";
        b.btnDate.setText("Select date");
        b.tvDateInfo.setVisibility(View.GONE);
        render();
    }

    // ------------------------------------------------------------------ drawing the steps

    private void render() {
        // an extra class cannot be added on a CR holiday / vacation
        CustomHoliday blockHol = dateOk && dateKey != null ? Holidays.on(holidays, dateKey) : null;
        if (dateOk) {
            if (blockHol != null) {
                b.tvDateInfo.setTextColor(Ui.RED);
                b.tvDateInfo.setText(dateInfoText + "\nHoliday: " + Holidays.purpose(blockHol)
                        + ". Classes cannot be added or cancelled on a holiday. Choose another date.");
            } else {
                b.tvDateInfo.setTextColor(Ui.GREEN);
                b.tvDateInfo.setText(dateInfoText);
            }
        }
        b.stepDate.setVisibility(mode != MODE_NONE ? View.VISIBLE : View.GONE);
        b.stepScope.setVisibility(mode != MODE_NONE && dateOk && blockHol == null ? View.VISIBLE : View.GONE);
        boolean ready = mode != MODE_NONE && dateOk && scope != null && blockHol == null;
        b.stepClasses.setVisibility(ready ? View.VISIBLE : View.GONE);
        b.formExtra.setVisibility(ready && mode == MODE_EXTRA ? View.VISIBLE : View.GONE);
        b.listClasses.removeAllViews();
        if (!ready) return;

        List<RoutineEntry> list = classesOfDay();
        b.tvListTitle.setText((mode == MODE_CANCEL ? "Choose the class to cancel" : "Routine of that day (for reference)")
                + "   |   " + TimeUtil.dayName(dow) + "   |   " + scope);
        b.tvNoClasses.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
        b.tvNoClasses.setText(mode == MODE_CANCEL ? "There is no class to cancel for " + scope + " on this date."
                : "No class is scheduled for " + scope + " on this date.");
        for (RoutineEntry r : list) b.listClasses.addView(classRow(r));
    }

    /** Classes of the chosen date that concern the chosen section / group, earliest first. */
    private List<RoutineEntry> classesOfDay() {
        List<RoutineEntry> out = new ArrayList<>();
        for (RoutineEntry r : routine) {
            if (r.id == null || !r.occursOn(dateKey, dow)) continue;
            if (!r.oneTime() && dayStart < TimeUtil.dayStart(r.createdAt)) continue;       // the class did not exist yet
            if (!inScope(r)) continue;
            out.add(r);
        }
        Collections.sort(out, (x, y) -> x.startMin != y.startMin ? Integer.compare(x.startMin, y.startMin) : Integer.compare(x.endMin, y.endMin));
        return out;
    }

    /** "Whole Section B" = every class of section B (whole and B1 / B2). "B1" = whole-section classes and B1's own. */
    private boolean inScope(RoutineEntry r) {
        if (scope.startsWith("Whole")) return r.sectionOf().equals(scope.substring(scope.length() - 1));
        return ClassSession.appliesTo(r, scope);
    }

    private View classRow(RoutineEntry r) {
        ItemClassChangeBinding v = ItemClassChangeBinding.inflate(getLayoutInflater(), b.listClasses, false);
        boolean off = cancelled.contains(dateKey + "_" + r.id);
        CustomHoliday hol = Holidays.blocking(holidays, r, dateKey);      // a holiday already cancels this class
        v.tvTime.setText(TimeUtil.formatMin(r.startMin) + " - " + TimeUtil.formatMin(r.endMin));
        v.tvCourse.setText(Ui.nz(r.course) + (Ui.nz(r.title).isEmpty() ? "" : "  -  " + r.title));
        StringBuilder meta = new StringBuilder(r.scopeLabel());
        if (!Ui.nz(r.teacher).isEmpty()) meta.append("   |   ").append(r.teacher);
        if (!Ui.nz(r.room).isEmpty()) meta.append("   |   Room: ").append(r.room);
        v.tvMeta.setText(meta);

        String tag = hol != null ? "Holiday: " + Holidays.purpose(hol) : off ? "Cancelled" : r.oneTime() ? "Extra class" : null;
        v.tvTag.setVisibility(tag == null ? View.GONE : View.VISIBLE);
        if (tag != null) { v.tvTag.setText(tag); Ui.solid(v.tvTag, hol != null ? Ui.PLUM : off ? Ui.RED : Ui.AMBER, Ui.WHITE); }

        if (r.oneTime() || off) {                                   // tap an extra or cancelled class: undo
            v.getRoot().setOnClickListener(x -> {
                if (!requireOnline()) return;
                if (off) ClassUndo.confirmUndoCancel(this, r, dateKey);
                else ClassUndo.confirmUndoExtra(this, r, null);
            });
        }
        if (mode == MODE_EXTRA && r.oneTime()) {
            v.btnAction.setVisibility(View.VISIBLE);
            v.btnAction.setText("Undo Extra Class");
            v.btnAction.setOnClickListener(x -> { if (requireOnline()) ClassUndo.confirmUndoExtra(this, r, null); });
        }
        if (mode == MODE_CANCEL) {
            v.btnAction.setVisibility(View.VISIBLE);
            boolean finished = dateKey.equals(TimeUtil.dateKey(TimeUtil.dayStart(TrustedTime.now()))) && r.endMin <= TimeUtil.nowMin();
            if (hol != null) {
                v.btnAction.setText("Holiday: class is not held");
                v.btnAction.setEnabled(false);
            } else if (off) {
                v.btnAction.setText("Undo Cancellation");
                v.btnAction.setOnClickListener(x -> { if (requireOnline()) ClassUndo.confirmUndoCancel(this, r, dateKey); });
            } else if (finished) {
                v.btnAction.setText("Class already finished");
                v.btnAction.setEnabled(false);
            } else {
                v.btnAction.setText("Cancel This Class");
                v.btnAction.setOnClickListener(x -> confirmCancel(r));
            }
        }
        return v.getRoot();
    }

    // ------------------------------------------------------------------ Class Cancellation

    private void confirmCancel(RoutineEntry r) {
        if (!requireOnline()) return;
        new AlertDialog.Builder(this)
                .setTitle("Cancel " + Ui.nz(r.course) + "?")
                .setMessage(r.scopeLabel() + " will have no " + Ui.nz(r.course) + " class on " + TimeUtil.prettyDate(dateKey) + " ("
                        + TimeUtil.formatMin(r.startMin) + " - " + TimeUtil.formatMin(r.endMin)
                        + "). It will not count in attendance, and the students will be notified.")
                .setPositiveButton("Cancel Class", (d, w) -> doCancel(r))
                .setNegativeButton("Back", null)
                .show();
    }

    private void doCancel(RoutineEntry r) {
        if (!requireOnline()) return;
        String key = dateKey + "_" + r.id;
        Map<String, Object> c = new HashMap<>();
        c.put("sessionKey", key); c.put("date", dateKey); c.put("routineId", r.id); c.put("by", Session.name(this));
        Notice n = new Notice("Class Cancelled: " + Ui.nz(r.course),
                "The " + Ui.nz(r.course) + " class of " + r.scopeLabel() + " on " + TimeUtil.prettyDate(dateKey) + " ("
                        + TimeUtil.formatMin(r.startMin) + " - " + TimeUtil.formatMin(r.endMin) + ") will not be held.",
                Session.name(this) + " (CR)");
        WriteBatch batch = fs.batch();
        batch.set(fs.collection("cancellations").document(key), c);
        batch.set(fs.collection("notices").document(), n);
        batch.commit()
                .addOnSuccessListener(x -> Toast.makeText(this, "Class cancelled. Students have been notified.", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Toast.makeText(this, Ui.firebaseError(e), Toast.LENGTH_LONG).show());
    }

    // ------------------------------------------------------------------ Extra Class

    private void saveExtra() {
        if (!requireOnline()) return;
        if (!dateOk || scope == null) return;
        if (dow == Calendar.FRIDAY || dow == Calendar.SATURDAY) {
            Toast.makeText(this, TimeUtil.dayName(dow) + " is a weekend holiday. An extra class cannot be added.", Toast.LENGTH_LONG).show();
            return;
        }
        CustomHoliday hol = Holidays.on(holidays, dateKey);
        if (hol != null) {
            Toast.makeText(this, "Holiday: " + Holidays.purpose(hol) + ". An extra class cannot be added on "
                    + TimeUtil.prettyDate(dateKey) + ".", Toast.LENGTH_LONG).show();
            return;
        }
        String course = b.etCourse.getText().toString().trim();
        if (course.isEmpty()) { b.etCourse.setError("Required"); return; }
        if (b.rgType.getCheckedRadioButtonId() == -1) { Toast.makeText(this, "Choose the class type: Theory or Sessional.", Toast.LENGTH_SHORT).show(); return; }
        int start = b.twStart.getMinutes(), end = b.twEnd.getMinutes();
        if (end <= start) { Toast.makeText(this, "End time must be later than the start time.", Toast.LENGTH_SHORT).show(); return; }
        boolean today = dateKey.equals(TimeUtil.dateKey(TimeUtil.dayStart(TrustedTime.now())));
        if (today && start <= TimeUtil.nowMin()) {
            Toast.makeText(this, "The start time has already passed. Choose a later time for today.", Toast.LENGTH_LONG).show();
            return;
        }

        RoutineEntry r = new RoutineEntry();
        r.course = course;
        r.title = b.rgType.getCheckedRadioButtonId() == R.id.rbSessional ? "Sessional" : "Theory";
        r.teacher = b.etTeacher.getText().toString().trim();
        r.room = b.etRoom.getText().toString().trim();
        boolean whole = scope.startsWith("Whole");
        r.group = whole ? "" : scope;
        r.section = whole ? scope.substring(scope.length() - 1) : scope.substring(0, 1);
        r.day = dow;
        r.date = dateKey;                                    // makes it a one-date class
        r.startMin = start; r.endMin = end;
        r.createdAt = TrustedTime.now();
        r.createdBy = Session.name(this);

        // same clash rules as the weekly routine; a class that is cancelled on this date frees its time
        for (RoutineEntry x : routine) {
            if (x.id == null || !x.occursOn(dateKey, dow)) continue;
            if (!x.oneTime() && dayStart < TimeUtil.dayStart(x.createdAt)) continue;
            if (cancelled.contains(dateKey + "_" + x.id)) continue;
            if (r.clashesWith(x)) {
                Toast.makeText(this, "Time clash: " + x.scopeLabel() + " already has " + Ui.nz(x.course) + " on "
                        + TimeUtil.prettyDate(dateKey) + ", " + TimeUtil.formatMin(x.startMin) + " - " + TimeUtil.formatMin(x.endMin)
                        + ", which overlaps with this class for " + r.scopeLabel()
                        + ". Cancel that class for this date first, or choose a different time.", Toast.LENGTH_LONG).show();
                return;
            }
        }

        Notice n = new Notice("Extra Class: " + course,
                "An extra " + course + " class for " + r.scopeLabel() + " will be held on " + TimeUtil.prettyDate(dateKey) + ", "
                        + TimeUtil.formatMin(start) + " - " + TimeUtil.formatMin(end)
                        + (r.room.isEmpty() ? "" : ", Room: " + r.room) + (r.teacher.isEmpty() ? "" : ", Teacher: " + r.teacher) + ".",
                Session.name(this) + " (CR)");
        WriteBatch batch = fs.batch();
        batch.set(fs.collection("routine").document(), r);
        batch.set(fs.collection("notices").document(), n);
        b.btnSave.setEnabled(false);
        batch.commit()
                .addOnSuccessListener(x -> {
                    setNeedsInternet(b.btnSave, online);
                    Toast.makeText(this, "Extra class added for " + TimeUtil.prettyDate(dateKey) + ". Students have been notified.", Toast.LENGTH_LONG).show();
                    b.etCourse.setText(""); b.rgType.clearCheck(); b.etTeacher.setText(""); b.etRoom.setText("");
                })
                .addOnFailureListener(e -> {
                    setNeedsInternet(b.btnSave, online);
                    Toast.makeText(this, Ui.firebaseError(e), Toast.LENGTH_LONG).show();
                });
    }

    @Override
    protected void onDestroy() {
        com.unihub.app.util.PublicHolidays.removeListener(onPublicHolidays);
        for (ListenerRegistration r : regs) r.remove();
        super.onDestroy();
    }
}
