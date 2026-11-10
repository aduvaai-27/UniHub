package com.unihub.app.ui;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import org.json.JSONObject;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.unihub.app.R;
import com.unihub.app.adapter.TileAdapter;
import com.unihub.app.data.AttendanceRecord;
import com.unihub.app.data.Cancellation;
import com.unihub.app.data.CustomHoliday;
import com.unihub.app.data.ClassSession;
import com.unihub.app.data.MarksCache;
import com.unihub.app.data.ProfilePhotos;
import com.unihub.app.data.RoutineEntry;
import com.unihub.app.data.TaskItem;
import com.unihub.app.data.UniHubProvider;
import com.unihub.app.databinding.ActivityMainBinding;
import com.unihub.app.service.NoticeService;
import com.unihub.app.util.Holidays;
import com.unihub.app.util.PublicHolidays;
import com.unihub.app.util.Session;
import com.unihub.app.util.TimeUtil;
import com.unihub.app.util.TrustedTime;
import com.unihub.app.util.Ui;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends BaseActivity {
    private static final String KUET_URL = "https://www.kuet.ac.bd";
    private ActivityMainBinding b;
    private ListenerRegistration routineReg, tasksReg, attendanceReg, cancelReg, holidayReg, photoReg;
    private List<RoutineEntry> lastRoutine = new ArrayList<>();
    private final Set<String> cancelled = new HashSet<>();                  // "yyyy-MM-dd_routineId" of classes the CR cancelled
    private List<CustomHoliday> holidays = new ArrayList<>();
    private final Runnable onPublicHolidays = () -> { if (routineReg != null) showToday(); };
    private final ActivityResultLauncher<String> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> { });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!Session.isLoggedIn(this)) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        b = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        b.tvWelcome.setText("Welcome, " + Session.name(this));
        showRole();
        showMyPhoto();
        b.btnTheme.setImageResource(com.unihub.app.util.ThemePrefs.isDark(this) ? R.drawable.ic_theme_light : R.drawable.ic_theme_dark);
        b.btnTheme.setOnClickListener(v -> com.unihub.app.util.ThemePrefs.toggle(this));   // dark <-> light, any user
        b.btnLogout.setOnClickListener(v -> logout());
        // tap the Next Class card: this screen goes to onPause and the 7 day routine opens
        b.cardNext.setOnClickListener(v -> startActivity(new Intent(this, UpcomingActivity.class)));
        // implicit intent: open the KUET website in the browser
        b.btnPortal.setOnClickListener(v -> { if (requireOnline()) startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(KUET_URL))); });

        // service tiles: RecyclerView with a 2-column GridLayoutManager
        List<TileAdapter.Tile> tiles = new ArrayList<>();
        tiles.add(new TileAdapter.Tile("Group Chat", "Live class chat", Ui.TEAL, ChatActivity.class));
        tiles.add(new TileAdapter.Tile("Class Routine", "Weekly schedule", Ui.BLUE, RoutineActivity.class));
        if (Session.isStaff(this))        // CR only: extra class / class cancellation for one date
            tiles.add(new TileAdapter.Tile("Class Changes", "Extra class and cancellation", Ui.PLUM, ClassChangeActivity.class));
        tiles.add(new TileAdapter.Tile("Assignments and Exams", "Schedule and deadlines", Ui.AMBER, TasksActivity.class));
        tiles.add(new TileAdapter.Tile("Notices", "Announcements from the CR", Ui.TEAL, NoticesActivity.class));
        tiles.add(new TileAdapter.Tile("Attendance Tracker", "Track your own attendance", Ui.PLUM, AttendanceActivity.class));
        tiles.add(new TileAdapter.Tile("Teachers", "Contact info", Ui.SLATE, TeachersActivity.class));
        tiles.add(new TileAdapter.Tile("Students", "Contact info", Ui.GREEN, StudentsActivity.class));
        if (Session.isStaff(this))        // CR only: set a holiday or vacation (shown in the Calendar, students are notified)
            tiles.add(new TileAdapter.Tile("Holiday and Vacation", "Set for the class", Ui.RED, HolidayActivity.class));
        tiles.add(new TileAdapter.Tile("Calendar", "Holidays", Ui.BLUE, CalendarActivity.class));
        b.tiles.setLayoutManager(new GridLayoutManager(this, 2));
        TileAdapter tileAdapter = new TileAdapter(tiles);
        b.tiles.setAdapter(tileAdapter);
        fitTiles(tileAdapter, tiles.size());

        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
        ContextCompat.startForegroundService(this, new Intent(this, NoticeService.class));

        if (FirebaseApp.getApps(this).isEmpty()) {
            b.tvNextTitle.setText("Firebase is not connected");
            b.tvNextMeta.setText("Add google-services.json to load the shared routine");
            return;
        }
        FirebaseFirestore fs = FirebaseFirestore.getInstance();
        routineReg = fs.collection("routine").addSnapshotListener((snap, e) -> {
            if (snap == null) return;
            lastRoutine = snap.toObjects(RoutineEntry.class);
            if (Session.isStaff(this)) renameImported(snap);
            showToday();
        });
        cancelReg = fs.collection("cancellations").addSnapshotListener((snap, e) -> {
            if (snap == null) return;
            cancelled.clear();
            for (Cancellation c : snap.toObjects(Cancellation.class)) if (c.sessionKey != null) cancelled.add(c.sessionKey);
            showToday();
        });
        holidayReg = fs.collection("holidays").addSnapshotListener((snap, e) -> {
            if (snap == null) return;
            holidays = snap.toObjects(CustomHoliday.class);
            showToday();
        });
        PublicHolidays.addListener(onPublicHolidays);
        tasksReg = fs.collection("tasks").addSnapshotListener((snap, e) -> {
            if (snap != null) showDue(snap.toObjects(TaskItem.class));
        });
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        if (u != null) {
            // own attendance -> local SQLite copy (through the Content Provider) -> "Classes Attended"
            attendanceReg = fs.collection("attendance").whereEqualTo("uid", u.getUid()).addSnapshotListener((snap, e) -> {
                if (snap == null) return;
                MarksCache.sync(this, snap.toObjects(AttendanceRecord.class));
                refreshAttended();
            });
        }
    }


    /**
     * Volley: HTTP GET to a public REST API (Aladhan) with a RequestQueue and a JsonObjectRequest.
     * The date from the JSON response is shown in the "Today's Date" card.
     */
    private RequestQueue queue;

    @Override protected android.view.View offlineBar() { return b == null ? null : b.tvOffline; }

    /** Offline: the live date (Volley) and the KUET website button are blocked. Online: the date is requested again. */
    @Override
    protected void onConnectivityChanged(boolean on) {
        setNeedsInternet(b.btnPortal, on);
        showLocalDate(on);
        if (on) loadTodayDate();                           // Volley GET -> "Today's Date" card
    }

    /** Today's date from the trusted (internet) clock: shown first, and kept while there is no internet. */
    private void showLocalDate(boolean on) {
        b.tvCalTitle.setText(new SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.ENGLISH).format(new Date(TrustedTime.now())));
        Calendar cal = TimeUtil.calendarNow();
        b.tvCalMeta.setText("Day " + cal.get(Calendar.DAY_OF_YEAR) + " of the year  |  Week " + cal.get(Calendar.WEEK_OF_YEAR)
                + (on ? "" : "  |  Offline: last synced internet time"));
    }

    private void loadTodayDate() {
        Date now = new Date(TrustedTime.now());
        String url = "https://api.aladhan.com/v1/gToH/" + new SimpleDateFormat("dd-MM-yyyy", Locale.ENGLISH).format(now);
        if (queue == null) queue = Volley.newRequestQueue(this);          // RequestQueue
        JsonObjectRequest req = new JsonObjectRequest(Request.Method.GET, url, null,
                (JSONObject res) -> {                                      // response listener
                    try {
                        JSONObject g = res.getJSONObject("data").getJSONObject("gregorian");
                        b.tvCalTitle.setText(g.getJSONObject("weekday").getString("en") + ", "
                                + g.getString("day") + " " + g.getJSONObject("month").getString("en") + " " + g.getString("year"));
                    } catch (Exception ex) { /* keep the local date */ }
                },
                err -> { /* offline: keep the local date */ });             // error listener
        req.setTag("today");
        queue.add(req);
    }

    /** Gives the tiles RecyclerView the exact height of all its rows, so every service tile is visible inside the ScrollView. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void fitTiles(TileAdapter ad, int n) {
        final androidx.recyclerview.widget.RecyclerView.Adapter raw = ad;     // TileAdapter.VH is package-private, so use the raw adapter type
        b.tiles.post(() -> {
            int w = b.tiles.getWidth();
            if (w == 0) { fitTiles(ad, n); return; }
            float d = getResources().getDisplayMetrics().density;
            int itemW = w / 2 - Math.round(8 * d);                         // two columns, 4dp margin each side
            int total = 0;
            for (int i = 0; i < n; i += 2) {
                int rowH = 0;
                for (int j = i; j < Math.min(i + 2, n); j++) {
                    androidx.recyclerview.widget.RecyclerView.ViewHolder vh = raw.createViewHolder(b.tiles, 0);
                    raw.bindViewHolder(vh, j);
                    vh.itemView.measure(android.view.View.MeasureSpec.makeMeasureSpec(itemW, android.view.View.MeasureSpec.EXACTLY),
                            android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED));
                    rowH = Math.max(rowH, vh.itemView.getMeasuredHeight());
                }
                total += rowH + Math.round(8 * d);
            }
            android.view.ViewGroup.LayoutParams lp = b.tiles.getLayoutParams();
            lp.height = total;
            b.tiles.setLayoutParams(lp);
        });
    }

    private boolean renamedImported = false;

    /** CR only, once: routine classes saved as "Imported routine" are changed to the CR's name, as if the CR added them. */
    private void renameImported(com.google.firebase.firestore.QuerySnapshot snap) {
        if (renamedImported) return;
        renamedImported = true;
        com.google.firebase.firestore.WriteBatch wb = FirebaseFirestore.getInstance().batch();
        int n = 0;
        for (com.google.firebase.firestore.DocumentSnapshot d : snap.getDocuments()) {
            if (!"Imported routine".equals(d.getString("createdBy")) || n >= 450) continue;
            wb.update(d.getReference(), "createdBy", Session.name(this));
            n++;
        }
        if (n > 0) wb.commit();          // if the rules refuse, nothing changes and the app works as before
    }

    private void showRole() { b.tvRole.setText(Session.roleLabel(this)); }

    /**
     * Your own profile picture (default picture until one is saved). Tap it: this screen goes to onPause() and
     * PhotoViewActivity shows the picture big, where you can also change it.
     */
    private void showMyPhoto() {
        final String myKey = ProfilePhotos.studentKey(Session.roll(this));
        b.avatar.setRing(getColor(R.color.white));
        b.avatar.show(ProfilePhotos.thumb(myKey));
        b.avatar.setOnClickListener(v -> startActivity(
                PhotoViewActivity.intent(this, myKey, Session.name(this), Session.roleLabel(this))));
        photoReg = ProfilePhotos.watchThumb(this, myKey, () -> b.avatar.show(ProfilePhotos.thumb(myKey)));
    }

    private void showToday() {
        int dow = TimeUtil.calendarNow().get(Calendar.DAY_OF_WEEK);
        String group = Session.group(this);
        List<RoutineEntry> today = new ArrayList<>();
        String todayKey = TimeUtil.dateKey(TimeUtil.dayStart(TrustedTime.now()));
        CustomHoliday hol = Holidays.on(holidays, todayKey);
        boolean anyClass = false;
        for (RoutineEntry r : lastRoutine) {
            if (!r.occursOn(todayKey, dow) || !ClassSession.appliesTo(r, group)) continue;
            anyClass = true;
            if (cancelled.contains(todayKey + "_" + r.id) || Holidays.blocking(holidays, r, todayKey) != null) continue;   // not held
            today.add(r);
        }
        Collections.sort(today, (x, y) -> Integer.compare(x.startMin, y.startMin));
        b.tvStatToday.setText(String.valueOf(today.size()));

        int now = TimeUtil.nowMin();
        RoutineEntry next = null;
        for (RoutineEntry r : today) if (r.endMin > now) { next = r; break; }
        boolean running = next != null && now >= next.startMin;
        b.tvNextLabel.setText(running ? "CLASS IN PROGRESS" : "NEXT CLASS");
        if (next == null) {
            if (hol != null && anyClass && today.isEmpty()) b.tvNextTitle.setText("Holiday today: " + Holidays.purpose(hol));
            else b.tvNextTitle.setText(today.isEmpty() ? "No classes scheduled today" : "No further classes today");
            b.tvNextMeta.setText("");
        } else {
            b.tvNextTitle.setText(Ui.nz(next.course) + "  -  " + Ui.nz(next.title));
            b.tvNextMeta.setText((running ? "Ends " + TimeUtil.formatMin(next.endMin) + "  |  " : "")
                    + TimeUtil.formatMin(next.startMin) + " - " + TimeUtil.formatMin(next.endMin)
                    + (next.room == null || next.room.isEmpty() ? "" : "  |  Room: " + next.room));
        }
    }

    private void showDue(List<TaskItem> all) {
        long now = TrustedTime.now(), week = now + 7L * 24 * 3600000;
        int n = 0;
        for (TaskItem t : all) if (t.due >= now && t.due <= week) n++;
        b.tvStatDue.setText(String.valueOf(n));
    }

    // Dynamic Broadcast Receiver: "Classes Today" and "Next Class" depend on the clock, so they are
    // recalculated every minute and whenever the date, time or time zone changes while this screen is open.
    private boolean clockRegistered = false;
    private final BroadcastReceiver clockReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (routineReg != null) showToday();
        }
    };

    @Override
    protected void onStart() {
        super.onStart();
        if (b == null) return;
        IntentFilter f = new IntentFilter(Intent.ACTION_DATE_CHANGED);
        f.addAction(Intent.ACTION_TIME_CHANGED);
        f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        f.addAction(Intent.ACTION_TIME_TICK);                  // every minute: keeps "Next Class" correct while the screen is open
        ContextCompat.registerReceiver(this, clockReceiver, f, ContextCompat.RECEIVER_NOT_EXPORTED);
        clockRegistered = true;
    }

    @Override
    protected void onStop() {
        if (clockRegistered) { unregisterReceiver(clockReceiver); clockRegistered = false; }
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (b != null) {
            refreshAttended();
            if (routineReg != null) showToday();               // coming back to the app: the class may have ended meanwhile
        }
    }

    /** Reads the number of attended classes through the app's Content Provider. */
    private void refreshAttended() {
        Cursor c = getContentResolver().query(UniHubProvider.MARKS_URI, new String[]{"status"}, "status = ?", new String[]{"P"}, null);
        if (c == null) return;
        try { b.tvStatAtt.setText(String.valueOf(c.getCount())); } finally { c.close(); }
    }

    private void logout() {
        stopService(new Intent(this, NoticeService.class));
        if (attendanceReg != null) { attendanceReg.remove(); attendanceReg = null; }   // no late sync after the local copy is cleared
        Session.logout(this);
        Intent i = new Intent(this, LoginActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
    }

    @Override
    protected void onDestroy() {
        if (queue != null) queue.cancelAll("today");
        PublicHolidays.removeListener(onPublicHolidays);
        if (cancelReg != null) cancelReg.remove();
        if (holidayReg != null) holidayReg.remove();
        if (routineReg != null) routineReg.remove();
        if (tasksReg != null) tasksReg.remove();
        if (attendanceReg != null) attendanceReg.remove();
        if (photoReg != null) photoReg.remove();
        super.onDestroy();
    }
}
