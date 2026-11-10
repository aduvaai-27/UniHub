package com.unihub.app.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Toast;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.unihub.app.R;
import com.unihub.app.adapter.SessionAdapter;
import com.unihub.app.adapter.StatAdapter;
import com.unihub.app.data.AttendanceLevel;
import com.unihub.app.data.AttendanceRecord;
import com.unihub.app.data.Cancellation;
import com.unihub.app.data.ClassSession;
import com.unihub.app.data.CourseStat;
import com.unihub.app.data.CustomHoliday;
import com.unihub.app.data.RoutineEntry;
import com.unihub.app.databinding.ActivityAttendanceBinding;
import com.unihub.app.util.Holidays;
import com.unihub.app.util.Session;
import com.unihub.app.util.TimeUtil;
import com.unihub.app.util.TrustedTime;
import com.unihub.app.util.Ui;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Attendance Tracker (student and CR, each sees only their OWN attendance):
 *  - class sessions are generated automatically from the routine;
 *  - attendance can be given only while that class is in progress;
 *  - past and future classes cannot be changed; a class without attendance counts as absent;
 *  - nobody can see another person's attendance (the Firestore rules allow reading only your own records).
 *
 * Absent is never stored. It is worked out from the clock: a class that has ended and has no attendance record is
 * absent. The clock is TrustedTime (internet time), not the phone's date / time setting.
 *
 * Today tab:   today's classes and the Present button while a class is running.
 * Summary tab: one card per course with its ring, percentage, level (Excellent 90+, Good 80+, Okay 60+, Critical).
 */
public class AttendanceActivity extends BaseActivity {
    private static final int TAB_TODAY = 0, TAB_SUMMARY = 1;
    private static final long DAY = 86400000L;

    private ActivityAttendanceBinding b;
    private FirebaseFirestore fs;
    private String uid;
    private int tab = TAB_TODAY;

    private SessionAdapter todayAdapter;
    private StatAdapter statAdapter;
    private final List<ListenerRegistration> regs = new ArrayList<>();

    private List<RoutineEntry> routine = new ArrayList<>();
    private final Map<String, AttendanceRecord> mine = new HashMap<>();
    private final Set<String> cancelled = new HashSet<>();
    private List<CustomHoliday> holidays = new ArrayList<>();            // set by the CR: classes on these dates are not held

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override public void run() { reload(); handler.postDelayed(this, 30000); }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityAttendanceBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        FirebaseUser user = FirebaseApp.getApps(this).isEmpty() ? null : FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            b.tvSub.setText("Please sign in with Firebase to use attendance.");
            b.toggleView.setVisibility(View.GONE);
            return;
        }
        uid = user.getUid();
        fs = FirebaseFirestore.getInstance();

        todayAdapter = new SessionAdapter(this::giveAttendance);
        statAdapter = new StatAdapter();
        b.recycler.setLayoutManager(new LinearLayoutManager(this));
        b.recycler.setAdapter(todayAdapter);
        b.toggleView.addOnButtonCheckedListener((g, id, checked) -> {
            if (!checked) return;
            tab = id == R.id.btnSummary ? TAB_SUMMARY : TAB_TODAY;
            b.recycler.setAdapter(tab == TAB_SUMMARY ? statAdapter : todayAdapter);
            reload();
        });
        b.toggleView.check(R.id.btnToday);
        regs.add(fs.collection("routine").addSnapshotListener((snap, e) -> {
            if (fail(e) || snap == null) return;
            routine = snap.toObjects(RoutineEntry.class);
            reload();
        }));
        regs.add(fs.collection("attendance").whereEqualTo("uid", uid).addSnapshotListener((snap, e) -> {
            if (fail(e) || snap == null) return;
            mine.clear();
            for (AttendanceRecord r : snap.toObjects(AttendanceRecord.class)) if (r.sessionKey != null) mine.put(r.sessionKey, r);
            reload();
        }));
        regs.add(fs.collection("cancellations").addSnapshotListener((snap, e) -> {
            if (fail(e) || snap == null) return;
            cancelled.clear();
            for (Cancellation c : snap.toObjects(Cancellation.class)) if (c.sessionKey != null) cancelled.add(c.sessionKey);
            reload();
        }));
        regs.add(fs.collection("holidays").addSnapshotListener((snap, e) -> {
            if (fail(e) || snap == null) return;
            holidays = snap.toObjects(CustomHoliday.class);
            reload();
        }));
    }

    private boolean fail(Exception e) {
        if (e == null) return false;
        b.tvEmpty.setText(Ui.firebaseError(e));
        b.tvEmpty.setVisibility(View.VISIBLE);
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (fs != null) {
            handler.removeCallbacks(tick); handler.post(tick);                    // refresh every 30 s: class start / end
            TrustedTime.syncIfNeeded(this, verified -> reload());                 // real time from the time API
        }
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(tick);
        super.onPause();
    }

    private void reload() {
        if (fs == null) return;
        long now = TrustedTime.now();                 // internet time, not the phone clock
        boolean trusted = TrustedTime.isVerified();   // false only after a reboot with no internet yet: then no present / absent is shown
        long today = TimeUtil.dayStart(now);
        String group = Session.group(this);

        // whole history (for the summary) of the classes that concern this student
        List<ClassSession> history = ClassSession.build(routine, group, today - 120 * DAY, today);
        Map<String, CourseStat> stats = new TreeMap<>();
        List<ClassSession> todays = new ArrayList<>();
        ClassSession live = null;
        for (ClassSession s : history) {
            if (s.start >= today) {
                todays.add(s);
                if (trusted && now >= s.start && now < s.end && !isOff(s) && !mine.containsKey(s.key())) live = s;
            }
            // Absent = the class is over, it was held, and there is no attendance record. Nothing has to be running for this.
            if (!trusted || s.start > now || isOff(s)) continue;     // not started yet, cancelled or holiday: never counts
            boolean attended = mine.containsKey(s.key());
            if (s.end > now && !attended) continue;                  // running and not given yet: not absent until it ends
            CourseStat st = stats.get(s.r.course);                   // finished classes, plus a running class you already attended (counts instantly)
            if (st == null) { st = new CourseStat(); st.name = s.r.course; stats.put(s.r.course, st); }
            st.total++;
            if (attended) st.present++;
        }
        Collections.sort(todays, (x, y) -> Long.compare(x.start, y.start));
        List<CourseStat> statList = new ArrayList<>(stats.values());

        todayAdapter.submit(todays, mine, cancelled, holidays);
        statAdapter.submit(statList);

        if (tab == TAB_TODAY) {
            String msg = live != null ? "In progress: " + live.r.course + ". Give your attendance below."
                    : "Attendance can be given only while a class is in progress.";
            b.tvSub.setText(msg + clockNote());
        } else {
            b.tvSub.setText(AttendanceLevel.LEGEND + (online ? "" : "\nOffline: showing the last saved attendance."));
        }
        boolean empty = tab == TAB_TODAY ? todays.isEmpty() : statList.isEmpty();
        if (!trusted && tab != TAB_TODAY) {
            b.tvEmpty.setText("Connect to the internet once so UniHub can check the real time. Attendance figures are shown after that.");
            b.tvEmpty.setVisibility(View.VISIBLE);
            return;
        }
        b.tvEmpty.setText(tab == TAB_TODAY ? "No classes are scheduled for today." : "No classes have been completed yet.");
        b.tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
    }

    /** A class that is not held: the CR cancelled it, or it falls on a holiday. It never counts as absent. */
    private boolean isOff(ClassSession s) {
        return cancelled.contains(s.key()) || Holidays.blocking(holidays, s.r, s.date) != null;
    }

    /** Small note under the header about the clock: not verified yet, or the phone clock is wrong. */
    private String clockNote() {
        if (!TrustedTime.isVerified()) return "\nInternet time not checked yet. Using the phone clock until you connect.";
        long off = TrustedTime.phoneOffsetMinutes();
        if (Math.abs(off) >= 2) return "\nYour phone clock is " + Math.abs(off) + " min " + (off > 0 ? "fast" : "slow")
                + ". UniHub uses the internet time.";
        return "";
    }

    @Override protected android.view.View offlineBar() { return b == null ? null : b.tvOffline; }

    /** Offline: the "Present" buttons are disabled (the server must check the class time, so it needs internet). */
    @Override
    protected void onConnectivityChanged(boolean on) {
        if (todayAdapter != null) todayAdapter.setBlocked(!on);
        if (on && fs != null) TrustedTime.syncIfNeeded(this, verified -> reload());
        else reload();                                     // offline: refresh the summary text, data stays as loaded
    }

    /**
     * Writes the attendance record. The time is checked on the phone with the internet clock, and again by the
     * server rules (the server uses its own clock), so changing the phone's time gives nothing.
     */
    private void giveAttendance(ClassSession s) {
        if (!requireOnline()) return;
        if (!TrustedTime.isVerified()) {                                 // no internet time yet: ask the time API first
            TrustedTime.sync(this, verified -> {
                if (verified) writeAttendance(s);
                else Toast.makeText(this, "The real time could not be checked. Try again when the internet is working.", Toast.LENGTH_LONG).show();
                reload();
            });
            return;
        }
        writeAttendance(s);
    }

    private void writeAttendance(ClassSession s) {
        long now = TrustedTime.now();
        if (now < s.start || now >= s.end) {
            Toast.makeText(this, "Attendance can be given only during the class.", Toast.LENGTH_LONG).show();
            reload();
            return;
        }
        String roll = Session.roll(this);
        Map<String, Object> m = new HashMap<>();
        m.put("uid", uid);
        m.put("roll", roll);
        m.put("name", Session.name(this));
        m.put("group", Session.group(this));
        m.put("date", s.date);
        m.put("routineId", s.r.id);
        m.put("sessionKey", s.key());
        m.put("course", s.r.course);
        m.put("markedAt", FieldValue.serverTimestamp());
        fs.collection("attendance").document(s.key() + "_" + roll).set(m)
                .addOnSuccessListener(x -> Toast.makeText(this, "Attendance recorded.", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Toast.makeText(this,
                        "Attendance could not be recorded. It is accepted only during the class period. " + Ui.firebaseError(e),
                        Toast.LENGTH_LONG).show());
    }

    @Override
    protected void onDestroy() {
        for (ListenerRegistration r : regs) r.remove();
        super.onDestroy();
    }
}
