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
import com.unihub.app.data.CustomHoliday;
import com.unihub.app.data.RoutineEntry;
import com.unihub.app.data.Notice;
import com.unihub.app.databinding.ActivityHolidayBinding;
import com.unihub.app.databinding.ItemClassChangeBinding;
import com.unihub.app.util.Holidays;
import com.unihub.app.util.Session;
import com.unihub.app.util.TimeUtil;
import com.unihub.app.util.TrustedTime;
import com.unihub.app.util.Ui;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.List;

/**
 * CR only: set a holiday (or vacation). Steps: purpose, then one date or a range of dates (tomorrow or later: never today).
 * Holiday and vacation are the same thing.

 * Classes on these dates are cancelled automatically (see Holidays): no absent, shown as "Holiday - purpose".
 * It is saved in the "holidays" collection (shown in the Calendar of everyone) and a notice is posted, which the
 * notice service turns into a notification on every other account.
 */
public class HolidayActivity extends BaseActivity {
    private ActivityHolidayBinding b;
    private FirebaseFirestore fs;
    private ListenerRegistration reg;
    private boolean range = false;
    private String fromKey = null, toKey = null;
    private long fromMs = 0, toMs = 0;
    private List<CustomHoliday> existing = new ArrayList<>();
    private List<RoutineEntry> routine = new ArrayList<>();
    private final Set<String> cancelled = new HashSet<>();
    private final List<ListenerRegistration> extraRegs = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityHolidayBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        if (!Session.isStaff(this)) { finish(); return; }
        if (FirebaseApp.getApps(this).isEmpty()) {
            Toast.makeText(this, "Firebase is not connected.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        fs = FirebaseFirestore.getInstance();

        b.toggleSpan.addOnButtonCheckedListener((g, id, checked) -> {
            if (!checked) return;
            range = id == R.id.btnRange;
            b.btnTo.setVisibility(range ? View.VISIBLE : View.GONE);
            if (!range) { toKey = null; toMs = 0; }
            drawDates();
        });
        b.toggleSpan.check(R.id.btnSingle);
        b.btnFrom.setOnClickListener(v -> pick(true));
        b.btnTo.setOnClickListener(v -> pick(false));
        b.btnSave.setOnClickListener(v -> save());

        reg = fs.collection("holidays").addSnapshotListener((snap, e) -> {
            if (e != null) { Toast.makeText(this, Ui.firebaseError(e), Toast.LENGTH_LONG).show(); return; }
            if (snap == null) return;
            existing = snap.toObjects(CustomHoliday.class);
            drawList();
        });
        extraRegs.add(fs.collection("routine").addSnapshotListener((snap, e) -> {
            if (snap != null) routine = snap.toObjects(RoutineEntry.class);
        }));
        extraRegs.add(fs.collection("cancellations").addSnapshotListener((snap, e) -> {
            if (snap == null) return;
            cancelled.clear();
            for (Cancellation c : snap.toObjects(Cancellation.class)) if (c.sessionKey != null) cancelled.add(c.sessionKey);
        }));
        drawDates();
    }

    @Override protected View offlineBar() { return b == null ? null : b.tvOffline; }

    @Override
    protected void onConnectivityChanged(boolean on) {
        setNeedsInternet(b.btnSave, on);
    }

    // ------------------------------------------------------------------ dates

    private void drawDates() {
        b.btnFrom.setText(fromKey == null ? (range ? "Select start date" : "Select date")
                : (range ? "From: " : "") + TimeUtil.prettyDate(fromKey));
        b.btnTo.setText(toKey == null ? "Select end date" : "To: " + TimeUtil.prettyDate(toKey));
    }

    private void pick(boolean isFrom) {
        if (!requireOnline()) return;
        if (b.etPurpose.getText() == null || b.etPurpose.getText().toString().trim().isEmpty()) {
            b.etPurpose.setError("Set the purpose first");
            b.etPurpose.requestFocus();
            Toast.makeText(this, "Set the purpose first, then choose the date.", Toast.LENGTH_SHORT).show();
            return;
        }
        long tomorrow = TimeUtil.dayStart(TrustedTime.dayAfter(TrustedTime.now()));
        long min = isFrom ? tomorrow : Math.max(tomorrow, fromMs);         // a holiday cannot be set for today: tomorrow and later only; the end is not before the start
        long current = isFrom ? (fromMs > 0 ? fromMs : tomorrow) : (toMs > 0 ? toMs : min);
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(current);
        DatePickerDialog d = new DatePickerDialog(this, (view, y, m, day) -> {
            Calendar p = Calendar.getInstance();
            p.set(y, m, day, 0, 0, 0);
            p.set(Calendar.MILLISECOND, 0);
            long ds = TimeUtil.dayStart(p.getTimeInMillis());
            if (isFrom) {
                fromMs = ds; fromKey = TimeUtil.dateKey(ds);
                if (toMs > 0 && toMs < fromMs) { toMs = 0; toKey = null; }  // the old end is now before the start
            } else {
                toMs = ds; toKey = TimeUtil.dateKey(ds);
            }
            drawDates();
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
        d.getDatePicker().setMinDate(min);
        d.show();
    }

    // ------------------------------------------------------------------ set

    private void save() {
        if (!requireRealTime()) return;
        String purpose = b.etPurpose.getText() == null ? "" : b.etPurpose.getText().toString().trim();
        if (purpose.isEmpty()) { b.etPurpose.setError("Required"); return; }
        if (fromKey == null) { Toast.makeText(this, range ? "Select the start date." : "Select the date.", Toast.LENGTH_SHORT).show(); return; }
        if (range && toKey == null) { Toast.makeText(this, "Select the end date.", Toast.LENGTH_SHORT).show(); return; }
        // a holiday cannot be set for today (or the past): classes of today may already be running or finished.
        // Checked again here because the date may have been picked before midnight.
        if (fromKey.compareTo(todayKey()) <= 0) {
            fromKey = null; toKey = null; fromMs = 0; toMs = 0; drawDates();
            Toast.makeText(this, "A holiday cannot be set for today. Choose tomorrow or a later date.", Toast.LENGTH_LONG).show();
            return;
        }

        // an extra class (held on one date) must not fall inside the new holiday: the CR has to undo it first
        String lastKey = range ? toKey : fromKey;
        List<RoutineEntry> clash = new ArrayList<>();
        for (RoutineEntry r : routine) {
            if (r.id == null || !r.oneTime() || r.date == null) continue;
            if (r.date.compareTo(fromKey) < 0 || r.date.compareTo(lastKey) > 0) continue;
            if (cancelled.contains(r.date + "_" + r.id)) continue;
            clash.add(r);
        }
        if (!clash.isEmpty()) {
            Collections.sort(clash, (x, y) -> x.date.equals(y.date) ? Integer.compare(x.startMin, y.startMin) : x.date.compareTo(y.date));
            StringBuilder sb = new StringBuilder("An extra class is already set on these dates:\n");
            for (RoutineEntry r : clash) {
                sb.append("\n").append(TimeUtil.prettyDate(r.date)).append(": ").append(Ui.nz(r.course)).append(" (").append(r.scopeLabel())
                        .append(", ").append(TimeUtil.formatMin(r.startMin)).append(" - ").append(TimeUtil.formatMin(r.endMin)).append(")");
            }
            sb.append("\n\nUndo the extra class first (Class Change > Extra Class), or choose other dates.");
            new AlertDialog.Builder(this).setTitle("Cannot set holiday").setMessage(sb.toString())
                    .setPositiveButton("OK", null).show();
            return;
        }

        CustomHoliday h = new CustomHoliday();
        h.purpose = purpose;
        h.startDate = fromKey;
        h.endDate = range ? toKey : fromKey;
        h.by = Session.name(this);
        h.createdAt = TrustedTime.now();

        Notice n = new Notice(h.label() + ": " + purpose,
                "No classes will be held " + whenText(h) + ". Purpose: " + purpose + ".",
                Session.name(this) + " (CR)");
        WriteBatch batch = fs.batch();
        batch.set(fs.collection("holidays").document(), h);
        batch.set(fs.collection("notices").document(), n);
        b.btnSave.setEnabled(false);
        batch.commit()
                .addOnSuccessListener(x -> {
                    setNeedsInternet(b.btnSave, online);
                    Toast.makeText(this, h.label() + " set. Students have been notified.", Toast.LENGTH_LONG).show();
                    b.etPurpose.setText("");
                    fromKey = null; toKey = null; fromMs = 0; toMs = 0;
                    drawDates();
                })
                .addOnFailureListener(e -> {
                    setNeedsInternet(b.btnSave, online);
                    Toast.makeText(this, Ui.firebaseError(e), Toast.LENGTH_LONG).show();
                });
    }

    /** "on Thu, 12 Nov 2026" or "from Sun, 15 Nov 2026 to Thu, 19 Nov 2026 (5 days)". */
    private static String whenText(CustomHoliday h) {
        if (!h.isRange()) return "on " + TimeUtil.prettyDate(h.startDate);
        return "from " + TimeUtil.prettyDate(h.startDate) + " to " + TimeUtil.prettyDate(h.endOf()) + " (" + days(h) + " days)";
    }

    private static long days(CustomHoliday h) {
        try {
            java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US);
            return Math.round((f.parse(h.endOf()).getTime() - f.parse(h.startDate).getTime()) / 86400000.0) + 1;
        } catch (Exception e) { return 1; }
    }

    // ------------------------------------------------------------------ the list (with Remove)

    private String todayKey() { return TimeUtil.dateKey(TimeUtil.dayStart(TrustedTime.now())); }

    /** A holiday that has already begun (today or earlier): the days up to today are fixed and cannot be cancelled. */
    private boolean started(CustomHoliday h) { return h.startDate.compareTo(todayKey()) <= 0; }

    /** First day that may still be cancelled: the start for a future holiday, tomorrow for one that has begun. */
    private String firstCancellable(CustomHoliday h) {
        return started(h) ? Holidays.addDays(todayKey(), 1) : h.startDate;
    }

    /** True when at least one day of this holiday is still in the future. */
    private boolean canCancel(CustomHoliday h) { return firstCancellable(h).compareTo(h.endOf()) <= 0; }

    private void drawList() {
        b.listSet.removeAllViews();
        String today = todayKey();
        List<CustomHoliday> list = new ArrayList<>();
        for (CustomHoliday h : existing) if (h.id != null && h.startDate != null && h.endOf().compareTo(today) >= 0) list.add(h);
        Collections.sort(list, (x, y) -> x.startDate.compareTo(y.startDate));
        b.tvNone.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
        for (CustomHoliday h : list) {
            ItemClassChangeBinding v = ItemClassChangeBinding.inflate(getLayoutInflater(), b.listSet, false);
            v.tvTime.setText(h.isRange() ? TimeUtil.prettyDate(h.startDate) + "  to  " + TimeUtil.prettyDate(h.endOf())
                    : TimeUtil.prettyDate(h.startDate));
            v.tvTag.setVisibility(View.VISIBLE);
            v.tvTag.setText(started(h) ? h.label() + " (running)" : h.label());
            Ui.solid(v.tvTag, Ui.PLUM, Ui.WHITE);
            v.tvCourse.setText(Ui.nz(h.purpose));
            v.tvMeta.setText(h.isRange() ? days(h) + " days   |   Set by " + Ui.nz(h.by) : "Set by " + Ui.nz(h.by));
            v.btnAction.setVisibility(View.VISIBLE);
            if (!canCancel(h)) {                                    // only today is left: it is fixed
                v.btnAction.setText("Today's " + h.label().toLowerCase() + " cannot be cancelled");
                v.btnAction.setEnabled(false);
            } else if (started(h)) {                                // running range: only the coming days can go
                v.btnAction.setText("Cancel from " + TimeUtil.prettyDate(firstCancellable(h)));
                v.btnAction.setOnClickListener(x -> confirmRemove(h));
            } else {
                v.btnAction.setText("Remove " + h.label());
                v.btnAction.setOnClickListener(x -> confirmRemove(h));
            }
            b.listSet.addView(v.getRoot());
        }
    }

    /** The other holidays (overlaps are allowed), i.e. everything except the one being changed. */
    private List<CustomHoliday> othersThan(CustomHoliday h) {
        List<CustomHoliday> o = new ArrayList<>();
        for (CustomHoliday x : existing) if (x != null && x.id != null && !x.id.equals(h.id) && x.startDate != null) o.add(x);
        return o;
    }

    /** What the students are told: only the days that really become class days again, and the days still covered. */
    private String changeText(CustomHoliday h, String from, Holidays.Split sp) {
        StringBuilder sb = new StringBuilder();
        sb.append("The ").append(h.label().toLowerCase()).append(" \"").append(Ui.nz(h.purpose)).append("\" has been cancelled for ")
                .append(Holidays.rangesText(java.util.Collections.singletonList(new String[]{from, h.endOf()}))).append(". ");
        if (!sp.freed.isEmpty()) sb.append("Classes will be held as usual on ").append(Holidays.rangesText(sp.freed)).append(". ");
        if (!sp.kept.isEmpty()) {
            sb.append("No classes on ").append(Holidays.rangesText(sp.kept)).append(" (").append(String.join(", ", sp.keptPurposes))
                    .append(" still applies). ");
        }
        if (started(h)) sb.append("The ").append(h.label().toLowerCase()).append(" up to ").append(TimeUtil.prettyDate(todayKey())).append(" is unchanged.");
        return sb.toString().trim();
    }

    private void confirmRemove(CustomHoliday h) {
        if (!requireOnline()) return;
        if (!canCancel(h)) {
            Toast.makeText(this, "Today's " + h.label().toLowerCase() + " cannot be cancelled.", Toast.LENGTH_SHORT).show();
            return;
        }
        final String from = firstCancellable(h);
        final boolean trim = started(h);
        final Holidays.Split sp = Holidays.split(othersThan(h), from, h.endOf());
        StringBuilder msg = new StringBuilder();
        msg.append(Ui.nz(h.purpose)).append(": ");
        if (trim) msg.append("the days up to ").append(TimeUtil.prettyDate(todayKey())).append(" stay as they are. ");
        msg.append("The ").append(h.label().toLowerCase()).append(" will be cancelled for ")
                .append(Holidays.rangesText(java.util.Collections.singletonList(new String[]{from, h.endOf()}))).append(".");
        if (!sp.kept.isEmpty()) {
            msg.append("\n\nStill no classes on ").append(Holidays.rangesText(sp.kept)).append(" because of ")
                    .append(String.join(", ", sp.keptPurposes)).append(".");
        }
        if (!sp.freed.isEmpty()) msg.append("\n\nClasses return on ").append(Holidays.rangesText(sp.freed)).append(".");
        else msg.append("\n\nNo class day comes back, because other holidays cover all these days.");
        msg.append("\n\nThe students will be notified.");
        new AlertDialog.Builder(this)
                .setTitle((trim ? "Cancel the rest of this " : "Remove ") + h.label().toLowerCase() + "?")
                .setMessage(msg.toString())
                .setPositiveButton(trim ? "Cancel it" : "Remove", (d, w) -> {
                    if (!requireRealTime()) return;
                    // the day may have changed while the dialog was open: check again
                    if (!canCancel(h) || !from.equals(firstCancellable(h))) {
                        Toast.makeText(this, "The date changed. Please try again.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Notice n = new Notice(h.label() + (trim ? " Shortened: " : " Cancelled: ") + Ui.nz(h.purpose),
                            changeText(h, from, sp), Session.name(this) + " (CR)");
                    WriteBatch batch = fs.batch();
                    if (trim) batch.update(fs.collection("holidays").document(h.id), "endDate", todayKey());
                    else batch.delete(fs.collection("holidays").document(h.id));
                    batch.set(fs.collection("notices").document(), n);
                    batch.commit()
                            .addOnSuccessListener(x -> Toast.makeText(this, h.label() + (trim ? " cancelled from tomorrow." : " removed.")
                                    + " Students have been notified.", Toast.LENGTH_SHORT).show())
                            .addOnFailureListener(e -> Toast.makeText(this, Ui.firebaseError(e), Toast.LENGTH_LONG).show());
                })
                .setNegativeButton("Back", null)
                .show();
    }

    @Override
    protected void onDestroy() {
        if (reg != null) reg.remove();
        for (ListenerRegistration r : extraRegs) r.remove();
        super.onDestroy();
    }
}
