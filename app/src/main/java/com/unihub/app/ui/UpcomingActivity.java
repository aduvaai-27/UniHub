package com.unihub.app.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.core.content.ContextCompat;
import com.google.firebase.FirebaseApp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.unihub.app.data.Cancellation;
import com.unihub.app.data.ClassSession;
import com.unihub.app.data.CustomHoliday;
import com.unihub.app.data.RoutineEntry;
import com.unihub.app.databinding.ActivityUpcomingBinding;
import com.unihub.app.databinding.ItemClassChangeBinding;
import com.unihub.app.databinding.ItemDayHeadBinding;
import com.unihub.app.util.Holidays;
import com.unihub.app.util.PublicHolidays;
import com.unihub.app.util.Session;
import com.unihub.app.util.TimeUtil;
import com.unihub.app.util.TrustedTime;
import com.unihub.app.util.Ui;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * "Next 7 Days" (opened by tapping the Next Class card on the home screen): today and the six days after it,
 * for the student's own section and lab group, with the time of every class. It is drawn from the same data as
 * the rest of the app, so it always agrees with them:
 *   - a holiday (set by the CR, or a government holiday) marks the day and its classes as "Holiday";
 *   - an extra class added by the CR shows as "Extra class";
 *   - a class the CR cancelled shows as "Cancelled".
 */
public class UpcomingActivity extends BaseActivity {
    private static final int DAYS = 7;

    private ActivityUpcomingBinding b;
    private final List<ListenerRegistration> regs = new ArrayList<>();
    private List<RoutineEntry> routine = new ArrayList<>();
    private final Set<String> cancelled = new HashSet<>();
    private List<CustomHoliday> holidays = new ArrayList<>();
    private boolean routineLoaded = false;
    private String group;
    private final Runnable onPublicHolidays = this::render;               // public holidays arrived: redraw

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityUpcomingBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        group = Session.group(this).isEmpty() ? "A1" : Session.group(this);
        b.tvSub.setText("Section " + group.substring(0, 1) + "   |   Group " + group);

        if (FirebaseApp.getApps(this).isEmpty()) {
            b.tvLoading.setText("Firebase is not connected.");
            return;
        }
        FirebaseFirestore fs = FirebaseFirestore.getInstance();
        regs.add(fs.collection("routine").addSnapshotListener((snap, e) -> {
            if (e != null) { b.tvLoading.setText(Ui.firebaseError(e)); return; }
            if (snap == null) return;
            routine = snap.toObjects(RoutineEntry.class);
            routineLoaded = true;
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
        PublicHolidays.addListener(onPublicHolidays);
    }

    @Override protected View offlineBar() { return b == null ? null : b.tvOffline; }

    // ------------------------------------------------------------------ drawing

    private void render() {
        if (b == null || !routineLoaded) return;
        b.tvLoading.setVisibility(View.GONE);
        b.list.removeAllViews();

        long today = TimeUtil.dayStart(TrustedTime.now());
        String todayKey = TimeUtil.dateKey(today);
        int nowMin = TimeUtil.nowMin();
        SimpleDateFormat fmt = new SimpleDateFormat("EEEE, dd MMM yyyy", Locale.ENGLISH);
        Calendar c = Calendar.getInstance();

        for (int i = 0; i < DAYS; i++) {
            c.setTimeInMillis(today);
            c.add(Calendar.DAY_OF_MONTH, i);
            long ds = TimeUtil.dayStart(c.getTimeInMillis());
            int dow = c.get(Calendar.DAY_OF_WEEK);
            String key = TimeUtil.dateKey(ds);

            List<RoutineEntry> list = classesOf(key, dow, ds);
            CustomHoliday dayHoliday = Holidays.on(holidays, key);
            boolean classDay = isClassDay(dow);

            ItemDayHeadBinding h = ItemDayHeadBinding.inflate(getLayoutInflater(), b.list, false);
            h.tvDay.setText(fmt.format(c.getTime()) + (i == 0 ? "  (Today)" : i == 1 ? "  (Tomorrow)" : ""));
            if (dayHoliday != null) {                                       // a holiday: marked on the day
                h.tvTag.setVisibility(View.VISIBLE);
                h.tvTag.setText("Holiday");
                Ui.solid(h.tvTag, Ui.PLUM, Ui.WHITE);
                h.tvNote.setVisibility(View.VISIBLE);
                h.tvNote.setText(Holidays.purpose(dayHoliday) + ". No weekly class is held on this day.");
            } else if (!classDay && list.isEmpty()) {
                h.tvTag.setVisibility(View.VISIBLE);
                h.tvTag.setText("Weekend");
                Ui.tag(h.tvTag, Ui.SLATE);
            } else if (list.isEmpty()) {
                h.tvNote.setVisibility(View.VISIBLE);
                h.tvNote.setText("No class for your group.");
            }
            b.list.addView(h.getRoot());

            for (RoutineEntry r : list) b.list.addView(classRow(r, key, i == 0, nowMin));
        }
    }

    /** Classes of one date that concern this section / lab group, earliest first. */
    private List<RoutineEntry> classesOf(String key, int dow, long ds) {
        List<RoutineEntry> out = new ArrayList<>();
        for (RoutineEntry r : routine) {
            if (r.id == null || !r.occursOn(key, dow) || !ClassSession.appliesTo(r, group)) continue;
            if (ds < TimeUtil.dayStart(r.createdAt)) continue;               // the class did not exist yet
            out.add(r);
        }
        Collections.sort(out, (x, y) -> x.startMin != y.startMin ? Integer.compare(x.startMin, y.startMin) : Integer.compare(x.endMin, y.endMin));
        return out;
    }

    private static boolean isClassDay(int dow) {
        for (int d : TimeUtil.WEEK) if (d == dow) return true;
        return false;
    }

    private View classRow(RoutineEntry r, String key, boolean isToday, int nowMin) {
        ItemClassChangeBinding v = ItemClassChangeBinding.inflate(getLayoutInflater(), b.list, false);
        CustomHoliday hol = Holidays.blocking(holidays, r, key);           // a holiday cancels the weekly class
        boolean off = cancelled.contains(key + "_" + r.id);                // the CR cancelled this class
        boolean extra = r.oneTime();

        v.tvTime.setText(TimeUtil.formatMin(r.startMin) + " - " + TimeUtil.formatMin(r.endMin));
        v.tvCourse.setText(Ui.nz(r.course) + (Ui.nz(r.title).isEmpty() ? "" : "  -  " + r.title));
        StringBuilder meta = new StringBuilder(r.groupOf().isEmpty() ? "Whole section" : "Lab Group " + r.groupOf());
        if (!Ui.nz(r.teacher).isEmpty()) meta.append("   |   ").append(r.teacher);
        if (!Ui.nz(r.room).isEmpty()) meta.append("   |   Room: ").append(r.room);
        if (extra && (off || hol != null)) meta.append("   |   Extra class");
        v.tvMeta.setText(meta);

        String tag = null; int color = Ui.GRAY;
        if (hol != null) { tag = "Holiday: " + Holidays.purpose(hol); color = Ui.PLUM; }
        else if (off) { tag = "Cancelled"; color = Ui.RED; }
        else if (extra) { tag = "Extra class"; color = Ui.AMBER; }
        else if (isToday && nowMin >= r.endMin) { tag = "Finished"; color = Ui.GRAY; }
        else if (isToday && nowMin >= r.startMin) { tag = "In progress"; color = Ui.BLUE; }
        if (tag != null) { v.tvTag.setVisibility(View.VISIBLE); v.tvTag.setText(tag); Ui.solid(v.tvTag, color, Ui.WHITE); }

        boolean notHeld = hol != null || off;
        v.tvCourse.setPaintFlags(notHeld ? v.tvCourse.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG
                : v.tvCourse.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
        v.getRoot().setAlpha(notHeld ? 0.7f : 1f);
        return v.getRoot();
    }

    // ------------------------------------------------------------------ clock: "In progress / Finished" of today

    private boolean clockRegistered = false;
    private final BroadcastReceiver clockReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { render(); }
    };

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter f = new IntentFilter(Intent.ACTION_TIME_TICK);                    // every minute
        f.addAction(Intent.ACTION_DATE_CHANGED);
        f.addAction(Intent.ACTION_TIME_CHANGED);
        f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        ContextCompat.registerReceiver(this, clockReceiver, f, ContextCompat.RECEIVER_NOT_EXPORTED);
        clockRegistered = true;
        render();                                                                      // the date may have changed while away
    }

    @Override
    protected void onStop() {
        if (clockRegistered) { unregisterReceiver(clockReceiver); clockRegistered = false; }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        PublicHolidays.removeListener(onPublicHolidays);
        for (ListenerRegistration r : regs) r.remove();
        super.onDestroy();
    }
}
