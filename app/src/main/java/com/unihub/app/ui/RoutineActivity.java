package com.unihub.app.ui;

import com.unihub.app.util.TrustedTime;
import android.content.Intent;
import android.os.Bundle;
import android.util.SparseIntArray;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.FirebaseApp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.MetadataChanges;
import com.unihub.app.adapter.RoutineAdapter;
import com.unihub.app.data.ClassSession;
import com.unihub.app.data.RoutineEntry;
import com.unihub.app.data.RoutinePreset;
import com.unihub.app.databinding.ActivityRoutineBinding;
import com.unihub.app.util.Session;
import com.unihub.app.util.TimeUtil;
import com.unihub.app.util.Ui;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Weekly routine. Anyone can view the routine of any group (A1, A2, B1, B2); it opens on your own group.
 * The CR can add or delete classes of both sections and import the printed routines.
 * Only the permanent weekly routine is shown: an extra class, a cancellation or a holiday never changes it
 * (they appear only in the Next 7 Days screen, the home card, Attendance and the Calendar).
 */
public class RoutineActivity extends BaseActivity {
    private ActivityRoutineBinding b;
    private RoutineAdapter adapter;
    private ListenerRegistration reg;
    private boolean staff, missingA, missingB, loading;
    private static final String[] GROUPS = {"A1", "A2", "B1", "B2"};
    private String viewGroup;
    private List<RoutineEntry> all = new ArrayList<>();
    private final int today = TimeUtil.calendarNow().get(Calendar.DAY_OF_WEEK);
    // on Friday / Saturday (no classes) start on Sunday, the next class day
    private int selectedDay = isClassDay(today) ? today : Calendar.SUNDAY;

    private static boolean isClassDay(int d) {
        for (int x : TimeUtil.WEEK) if (x == d) return true;
        return false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityRoutineBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        staff = Session.isStaff(this);
        viewGroup = Session.group(this).isEmpty() ? GROUPS[0] : Session.group(this);
        adapter = new RoutineAdapter(staff, this::confirmDelete);
        b.recycler.setLayoutManager(new LinearLayoutManager(this));
        b.recycler.setAdapter(adapter);
        if (staff) {
            b.fabAdd.setVisibility(View.VISIBLE);
            b.fabAdd.setOnClickListener(v -> { if (requireOnline()) startActivity(new Intent(this, AddRoutineActivity.class)); });
            b.fabAdd.setOnLongClickListener(v -> { loadPreset(); return true; });      // quick fill for the CR, nothing shown on screen
        }
        b.tvSub.setVisibility(View.GONE);

        // one chip per group: anyone can look at the routine of any group
        SparseIntArray idToGroup = new SparseIntArray();
        int selectedGroupBtn = 0;
        for (int i = 0; i < GROUPS.length; i++) {
            MaterialButton gb = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            gb.setId(View.generateViewId());
            gb.setAllCaps(false);
            gb.setText("Group " + GROUPS[i]);
            b.toggleGroups.addView(gb);
            idToGroup.put(gb.getId(), i);
            if (GROUPS[i].equals(viewGroup)) selectedGroupBtn = gb.getId();
        }
        b.toggleGroups.addOnButtonCheckedListener((g, id, checked) -> {
            if (checked) { viewGroup = GROUPS[idToGroup.get(id)]; refresh(); }
        });
        b.toggleGroups.check(selectedGroupBtn);

        // one chip per class day (Sunday to Thursday)
        SparseIntArray idToDay = new SparseIntArray();
        int selectedBtn = 0;
        for (int d : TimeUtil.WEEK) {
            MaterialButton btn = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            btn.setId(View.generateViewId());
            btn.setAllCaps(false);
            btn.setText(TimeUtil.shortDay(d));
            b.toggleDays.addView(btn);
            idToDay.put(btn.getId(), d);
            if (d == selectedDay) selectedBtn = btn.getId();
        }
        b.toggleDays.addOnButtonCheckedListener((g, id, checked) -> {
            if (checked) { selectedDay = idToDay.get(id); refresh(); }
        });
        b.toggleDays.check(selectedBtn);

        if (FirebaseApp.getApps(this).isEmpty()) {
            b.tvEmpty.setText("Firebase is not connected.\nAdd google-services.json (see README).");
            b.tvEmpty.setVisibility(View.VISIBLE);
            return;
        }
        reg = FirebaseFirestore.getInstance().collection("routine").addSnapshotListener(MetadataChanges.INCLUDE, (snap, e) -> {
            if (e != null) { b.tvEmpty.setText(Ui.firebaseError(e)); b.tvEmpty.setVisibility(View.VISIBLE); return; }
            if (snap == null) return;
            all = snap.toObjects(RoutineEntry.class);
            b.tvSub.setText("Offline: showing a saved copy");
            b.tvSub.setVisibility(snap.getMetadata().isFromCache() ? View.VISIBLE : View.GONE);
            boolean hasA = false, hasB = false;
            String todayK = TimeUtil.dateKey(TimeUtil.dayStart(TrustedTime.now()));
            for (RoutineEntry r : all) { if (r.oneTime() || r.endedBy(todayK)) continue; if (r.sectionOf().equals("A")) hasA = true; else hasB = true; }
            missingA = !hasA; missingB = !hasB; loading = false;
            refresh();
        });
    }

    /** Next date (today or later) that falls on the selected weekday: the date a weekly class would be cancelled for. */
    private String dateOfSelectedDay() {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(TimeUtil.dayStart(TrustedTime.now()));
        for (int i = 0; i < 7 && c.get(Calendar.DAY_OF_WEEK) != selectedDay; i++) c.add(Calendar.DAY_OF_YEAR, 1);
        return TimeUtil.dateKey(c.getTimeInMillis());
    }

    private void refresh() {
        List<RoutineEntry> list = new ArrayList<>();
        String todayKey = TimeUtil.dateKey(TimeUtil.dayStart(TrustedTime.now()));
        for (RoutineEntry r : all) {
            if (r.day != selectedDay || !ClassSession.appliesTo(r, viewGroup)) continue;
            if (r.endedBy(todayKey)) continue;                                      // removed class: only its history is kept
            if (r.oneTime()) continue;                                              // extra classes, cancellations and holidays never change the weekly routine
            list.add(r);
        }
        Collections.sort(list, (x, y) -> {                                       // earliest class first
            if (x.startMin != y.startMin) return Integer.compare(x.startMin, y.startMin);
            if (x.endMin != y.endMin) return Integer.compare(x.endMin, y.endMin);
            int byDate = Ui.nz(x.date).compareTo(Ui.nz(y.date));
            return byDate != 0 ? byDate : x.groupOf().compareTo(y.groupOf());
        });
        adapter.submit(list, selectedDay == today, new HashSet<>(), new ArrayList<>(), dateOfSelectedDay());
        String name = selectedDay == today ? TimeUtil.dayName(selectedDay) + " (Today)" : TimeUtil.dayName(selectedDay);
        b.tvDayTitle.setText("Group " + viewGroup + "   |   " + name + "   |   " + list.size() + (list.size() == 1 ? " class" : " classes"));
        b.tvEmpty.setText(all.isEmpty() ? "The routine has not been added yet."
                : "No classes are scheduled on " + TimeUtil.dayName(selectedDay) + ".");
        b.tvEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
    }

    @Override protected android.view.View offlineBar() { return b == null ? null : b.tvOffline; }

    /** Offline: adding and removing classes is blocked. */
    @Override
    protected void onConnectivityChanged(boolean on) {
        setNeedsInternet(b.fabAdd, on);
    }

    /** CR: long-press "+" fills the sections that have no routine yet, saved like hand-added classes. */
    private void loadPreset() {
        if (!requireOnline() || loading || (!missingA && !missingB)) return;
        loading = true;                                           // cleared when the routine list updates
        RoutinePreset.upload(missingA, missingB, Session.name(this))
                .addOnSuccessListener(x -> Toast.makeText(this, "Routine added.", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> { loading = false; Toast.makeText(this, Ui.firebaseError(e), Toast.LENGTH_LONG).show(); });
    }

    private void confirmDelete(RoutineEntry r) {
        if (!requireRealTime()) return;
        new AlertDialog.Builder(this)
                .setTitle("Remove " + r.course + "?")
                .setMessage("It will disappear from the routine for every student from today. Past classes, attendance and records are kept.")
                .setPositiveButton("Remove", (d, w) -> { if (!requireRealTime()) return; FirebaseFirestore.getInstance().collection("routine").document(r.id)
                        .update("endDate", TimeUtil.dateKey(TimeUtil.dayStart(TrustedTime.now())))      // not deleted: old days keep their time and attendance
                        .addOnFailureListener(e -> Toast.makeText(this, Ui.firebaseError(e), Toast.LENGTH_LONG).show()); })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    protected void onDestroy() {
        if (reg != null) reg.remove();
        super.onDestroy();
    }
}
