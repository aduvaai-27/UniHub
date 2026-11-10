package com.unihub.app.service;

import com.unihub.app.util.TrustedTime;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;
import com.google.firebase.FirebaseApp;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.unihub.app.data.Cancellation;
import com.unihub.app.data.CustomHoliday;
import com.unihub.app.data.Notice;
import com.unihub.app.data.RoutineEntry;
import com.unihub.app.data.TaskItem;
import com.unihub.app.ui.ChatActivity;
import com.unihub.app.ui.NoticesActivity;
import com.unihub.app.ui.TasksActivity;
import com.unihub.app.util.ChatPrefs;
import com.unihub.app.util.NextClass;
import com.unihub.app.util.NextDeadline;
import com.unihub.app.util.Notifier;
import com.unihub.app.util.Reminders;
import com.unihub.app.util.Session;
import com.unihub.app.util.TimeUtil;
import com.unihub.app.util.Ui;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Foreground Service that keeps two live Firestore listeners, so students are alerted as soon as the CR posts:
 *  - notices: notification for every new notice
 *  - tasks:   AlarmManager reminders (1 day and 1 hour before). The "new / cancelled" alert itself comes from the notice the CR's post writes
 *  - routine + cancellations + tasks: keep the permanent notification live, e.g. "Next class: CSE 2201 at 10:00 AM",
 *    with the nearest assignment and exam deadline in the expanded view.
 *    It is refreshed every minute (ACTION_TIME_TICK) and whenever the routine or a cancellation changes.
 * The list of already-seen documents is remembered, so posts made while the phone was offline are still announced.
 */
public class NoticeService extends Service {
    private static final int FG_ID = 1;
    private ListenerRegistration noticeReg, taskReg, routineReg, cancelReg, holidayReg;
    private List<CustomHoliday> holidays = new ArrayList<>();
    private List<RoutineEntry> routine = new ArrayList<>();
    private List<TaskItem> tasks = new ArrayList<>();
    private final Set<String> cancelled = new HashSet<>();
    private String liveText = Notifier.DEFAULT_PERSISTENT_TEXT, liveDetail = "";
    private boolean clockRegistered;
    private SharedPreferences prefs;
    private Query chatQuery;
    private ChildEventListener chatListener;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        ServiceCompat.startForeground(this, FG_ID, Notifier.persistent(this, liveText, liveDetail),
                Build.VERSION.SDK_INT >= 34 ? ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE : 0);
        if (FirebaseApp.getApps(this).isEmpty() || !Session.isLoggedIn(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        TrustedTime.syncIfNeeded(this, v -> updateLiveNotification());     // next-class line uses the internet time
        if (noticeReg != null) return START_STICKY;
        prefs = getSharedPreferences("unihub_notify", MODE_PRIVATE);
        FirebaseFirestore fs = FirebaseFirestore.getInstance();

        noticeReg = fs.collection("notices").addSnapshotListener((snap, e) -> {
            if (snap == null) return;
            boolean seeded = prefs.getBoolean("n_seeded", false);
            if (!seeded && snap.getMetadata().isFromCache()) return;     // wait for the first real server answer
            Set<String> seen = new HashSet<>(prefs.getStringSet("n_seen", new HashSet<>()));
            for (DocumentChange c : snap.getDocumentChanges()) {
                if (c.getType() != DocumentChange.Type.ADDED) continue;
                String id = c.getDocument().getId();
                if (!seen.add(id)) continue;
                boolean own = c.getDocument().getMetadata().hasPendingWrites();   // posted from this phone
                if (seeded && !own) {
                    Notice n = c.getDocument().toObject(Notice.class);
                    if (ChatPrefs.noticesVisible) continue;                  // the user is in the Notices screen: no alert
                    Notifier.show(this, id.hashCode(), Notifier.CH_NOTICES, "New Notice",
                            Notifier.clip(Ui.nz(n.title), 60),
                            Ui.nz(n.title) + "\n" + Ui.nz(n.body) + "\nPosted by " + Ui.nz(n.author), NoticesActivity.class);
                }
            }
            prefs.edit().putStringSet("n_seen", seen).putBoolean("n_seeded", true).apply();
        });

        taskReg = fs.collection("tasks").addSnapshotListener((snap, e) -> {
            if (snap == null) return;
            tasks = snap.toObjects(TaskItem.class);                     // live notification: next assignment / exam
            updateLiveNotification();
            boolean seeded = prefs.getBoolean("t_seeded", false);
            if (!seeded && snap.getMetadata().isFromCache()) return;
            Set<String> seen = new HashSet<>(prefs.getStringSet("t_seen", new HashSet<>()));
            for (DocumentChange c : snap.getDocumentChanges()) {
                String id = c.getDocument().getId();
                int code = id.hashCode() & 0x3fffffff;
                if (c.getType() == DocumentChange.Type.REMOVED) { Reminders.cancel(this, code); continue; }
                TaskItem t = c.getDocument().toObject(TaskItem.class);
                boolean exam = TaskItem.EXAM.equals(t.type);
                String detail = (t.course == null || t.course.isEmpty() ? "" : t.course + "  |  ")
                        + (exam ? "Exam: " : "Deadline: ") + TimeUtil.dateTime(t.due);
                Reminders.schedule(this, code, (exam ? "Exam: " : "Assignment due: ") + Ui.nz(t.title), detail, t.due);

                // No "New Exam / New Assignment" alert here: the CR's post also writes a notice, and the notice
                // listener above turns that notice into the notification. This listener only keeps the reminders.
                if (c.getType() == DocumentChange.Type.ADDED) seen.add(id);
            }
            prefs.edit().putStringSet("t_seen", seen).putBoolean("t_seeded", true).apply();
        });
        startChatListener();
        startLiveNotification(fs);
        return START_STICKY;
    }

    // ---------------- Live (updating) foreground notification ----------------

    /** Listens to the routine and the "not held" list, and to the clock, so the permanent notification always shows the next class. */
    private void startLiveNotification(FirebaseFirestore fs) {
        routineReg = fs.collection("routine").addSnapshotListener((snap, e) -> {
            if (snap == null) return;
            routine = snap.toObjects(RoutineEntry.class);
            updateLiveNotification();
        });
        cancelReg = fs.collection("cancellations").addSnapshotListener((snap, e) -> {
            if (snap == null) return;
            cancelled.clear();
            for (Cancellation c : snap.toObjects(Cancellation.class)) if (c.sessionKey != null) cancelled.add(c.sessionKey);
            updateLiveNotification();
        });
        holidayReg = fs.collection("holidays").addSnapshotListener((snap, e) -> {
            if (snap == null) return;
            holidays = snap.toObjects(CustomHoliday.class);
            updateLiveNotification();
        });
        IntentFilter f = new IntentFilter(Intent.ACTION_TIME_TICK);          // every minute
        f.addAction(Intent.ACTION_TIME_CHANGED);
        f.addAction(Intent.ACTION_DATE_CHANGED);
        f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        ContextCompat.registerReceiver(this, clockReceiver, f, ContextCompat.RECEIVER_NOT_EXPORTED);
        clockRegistered = true;
    }

    private final BroadcastReceiver clockReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            TrustedTime.syncIfNeeded(context, null);           // refreshes the internet time only when it is 10+ minutes old
            updateLiveNotification();
        }
    };

    /** Works out the current class line and re-posts the permanent notification (same id = update, no sound). */
    private void updateLiveNotification() {
        long now = TrustedTime.now();
        NextClass n = NextClass.find(routine, Session.group(this), cancelled, holidays, now);
        List<String> lines = new ArrayList<>();                                  // expanded view
        String text;
        if (n != null) {
            text = n.headline(now);
            if (!n.detail().isEmpty()) lines.add(n.detail());
            lines.addAll(NextDeadline.lines(tasks, now));
        } else {
            List<String> due = NextDeadline.lines(tasks, now);
            if (!due.isEmpty()) { text = due.get(0); lines.addAll(due.subList(1, due.size())); }
            else text = routine.isEmpty() ? Notifier.DEFAULT_PERSISTENT_TEXT : "No upcoming classes";
        }
        String detail = android.text.TextUtils.join("\n", lines);
        if (text.equals(liveText) && detail.equals(liveDetail)) return;          // nothing changed
        liveText = text; liveDetail = detail;
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(FG_ID, Notifier.persistent(this, text, detail));
    }

    /**
     * Group chat alerts. Push ids are time ordered, so we remember the last message key we handled and listen only
     * for keys after it. First run: seed with the newest existing message so old history never notifies.
     */
    private void startChatListener() {
        if (chatListener != null) return;
        try {
            DatabaseReference ref = ChatPrefs.database().getReference("chat").child("messages");
            String last = ChatPrefs.lastKey(this);
            if (last != null) { attachChat(ref, last); return; }
            ref.limitToLast(1).get().addOnCompleteListener(t -> {
                if (!t.isSuccessful() || chatListener != null) return;      // offline on first run: try again next start
                String k = null;
                for (DataSnapshot s : t.getResult().getChildren()) k = s.getKey();
                if (k != null) ChatPrefs.setLastKey(this, k);
                attachChat(ref, k);
            });
        } catch (Exception e) {
            Log.w("UniHubChat", "Chat notifications off: " + e.getMessage());   // Realtime Database not set up
        }
    }

    private void attachChat(DatabaseReference ref, String after) {
        Query q = ref.orderByKey();
        if (after != null) q = q.startAfter(after);
        chatQuery = q;
        chatListener = new ChildEventListener() {
            @Override public void onChildAdded(@NonNull DataSnapshot s, @Nullable String prev) {
                if (s.getKey() == null) return;
                ChatPrefs.setLastKey(NoticeService.this, s.getKey());
                FirebaseUser me = FirebaseAuth.getInstance().getCurrentUser();
                String uid = s.child("uid").getValue(String.class);
                if (me == null || me.getUid().equals(uid)) return;           // my own message
                if (ChatPrefs.isMuted(NoticeService.this) || ChatPrefs.chatVisible) return;
                String name = s.child("name").getValue(String.class);
                String text = s.child("text").getValue(String.class);
                Notifier.show(NoticeService.this, Notifier.ID_CHAT, Notifier.CH_CHAT,
                        "Group Chat: " + Ui.nz(name), Ui.nz(text), ChatActivity.class);
            }
            @Override public void onChildChanged(@NonNull DataSnapshot s, @Nullable String prev) { }
            @Override public void onChildRemoved(@NonNull DataSnapshot s) { }
            @Override public void onChildMoved(@NonNull DataSnapshot s, @Nullable String prev) { }
            @Override public void onCancelled(@NonNull DatabaseError e) { Log.w("UniHubChat", e.getMessage()); }
        };
        q.addChildEventListener(chatListener);
    }

    @Override
    public void onDestroy() {
        if (chatQuery != null && chatListener != null) chatQuery.removeEventListener(chatListener);
        if (noticeReg != null) noticeReg.remove();
        if (taskReg != null) taskReg.remove();
        if (routineReg != null) routineReg.remove();
        if (cancelReg != null) cancelReg.remove();
        if (holidayReg != null) holidayReg.remove();
        if (clockRegistered) { unregisterReceiver(clockReceiver); clockRegistered = false; }
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
