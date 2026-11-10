package com.unihub.app.ui;

import android.os.Bundle;
import com.unihub.app.util.ContactsAccess;
import com.unihub.app.util.ContactsHelper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import com.google.firebase.FirebaseApp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.MetadataChanges;
import com.unihub.app.R;
import com.unihub.app.data.CourseInfo;
import com.unihub.app.data.ProfilePhotos;
import com.unihub.app.data.SeedTeachers;
import com.unihub.app.data.TeacherInfo;
import com.unihub.app.databinding.ActivityTeachersBinding;
import com.unihub.app.databinding.ItemTeacherBinding;
import com.unihub.app.view.AvatarView;
import com.unihub.app.util.Session;
import com.unihub.app.util.Ui;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * One card per course: code and title, then its teachers with contact info.
 * Students only read. The CR adds cards and edits a whole card (course and all its teachers) with one Edit.
 */
public class TeachersActivity extends BaseActivity {
    private ActivityTeachersBinding b;
    private boolean staff, seeding;
    private List<CourseInfo> cards = new ArrayList<>();
    private ListenerRegistration reg, photoReg;
    private FirebaseFirestore fs;

    /** Every picture on screen with the person it belongs to, so a changed picture can be refreshed without rebuilding the list. */
    private static class AvatarSlot {
        final AvatarView view; final String key;
        AvatarSlot(AvatarView view, String key) { this.view = view; this.key = key; }
    }
    private final List<AvatarSlot> slots = new ArrayList<>();

    // Content Provider (Contacts): runtime permission + number check + "Add contact" screen
    private final ContactsAccess contacts = new ContactsAccess(this);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityTeachersBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        staff = Session.isStaff(this);
        b.etSearch.addTextChangedListener(new TextWatcher() {            // live search by teacher name
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) { }
            @Override public void onTextChanged(CharSequence s, int st, int bf, int c) { }
            @Override public void afterTextChanged(Editable s) { query = s.toString(); render(); }
        });

        if (FirebaseApp.getApps(this).isEmpty()) { render(); return; }     // built-in cards, read only
        fs = FirebaseFirestore.getInstance();
        if (staff) {
            b.btnAddCard.setVisibility(View.VISIBLE);
            b.btnAddCard.setOnClickListener(v -> edit(null));
        }
        photoReg = ProfilePhotos.watchAll(this, this::refreshAvatars);      // pictures appear / change live
        reg = fs.collection("teacherCards").addSnapshotListener(MetadataChanges.INCLUDE, (snap, e) -> {
            if (snap == null) {
                if (e != null) Toast.makeText(this, Ui.firebaseError(e), Toast.LENGTH_LONG).show();
                render();                                  // still show the built-in cards
                return;
            }
            cards = snap.toObjects(CourseInfo.class);
            if (cards.isEmpty() && !snap.getMetadata().isFromCache() && !seeding) {
                seeding = true;
                loadLegacy();                              // bring over teacher info saved by the older version
            }
            render();
        });
    }

    private boolean usingDefaults() { return cards.isEmpty(); }

    /** Older versions kept teachers and courses in two collections. Rebuild the cards from them so nothing is lost. */
    private void loadLegacy() {
        fs.collection("courses").get().addOnCompleteListener(ct -> {
            if (!ct.isSuccessful() || ct.getResult().isEmpty()) { finishSeed(null); return; }
            fs.collection("teachers").get().addOnCompleteListener(tt -> {
                java.util.Map<String, TeacherInfo> tm = new java.util.HashMap<>();
                if (tt.isSuccessful()) for (com.google.firebase.firestore.DocumentSnapshot d : tt.getResult()) {
                    String sn = Ui.nz(d.getString("shortName"));
                    tm.put(sn.toUpperCase(), new TeacherInfo(sn, Ui.nz(d.getString("name")), Ui.nz(d.getString("dept")),
                            Ui.nz(d.getString("email")), Ui.nz(d.getString("phone"))));
                }
                List<CourseInfo> out = new ArrayList<>();
                for (com.google.firebase.firestore.DocumentSnapshot d : ct.getResult()) {
                    CourseInfo c = new CourseInfo();
                    c.code = Ui.nz(d.getString("code")); c.title = Ui.nz(d.getString("title"));
                    Object l = d.get("teachers");
                    if (l instanceof List) for (Object o : (List<?>) l) {
                        String sn = String.valueOf(o);
                        TeacherInfo ti = tm.get(sn.toUpperCase());
                        c.teachers.add(ti != null ? ti : new TeacherInfo(sn, sn, "", "", ""));
                    }
                    out.add(c);
                }
                finishSeed(out);
            });
        });
    }

    private void finishSeed(List<CourseInfo> legacy) {
        List<CourseInfo> src = legacy == null || legacy.isEmpty() ? SeedTeachers.courses() : legacy;
        if (staff) {
            com.google.firebase.firestore.WriteBatch batch = fs.batch();
            for (CourseInfo c : src) batch.set(fs.collection("teacherCards").document(SeedTeachers.courseId(c.code)), c);
            batch.commit().addOnCompleteListener(x -> seeding = false);
        } else {
            seeding = false;
            if (legacy != null && !legacy.isEmpty()) { shown = legacy; render(); }
        }
    }

    private List<CourseInfo> shown = new ArrayList<>();

    private String query = "";

    /** Every word typed must appear in the teacher's name (or short name). Empty search matches everyone. */
    private boolean matches(TeacherInfo t, String[] words) {
        String hay = (Ui.nz(t.name) + " " + Ui.nz(t.shortName)).toLowerCase(Locale.ROOT);
        for (String w : words) if (!w.isEmpty() && !hay.contains(w)) return false;
        return true;
    }

    /**
     * Draws the course cards. While searching, only the matching teachers stay in each card and courses
     * without a match are hidden (the course Edit button still edits the whole course).
     */
    private void render() {
        List<CourseInfo> cl = new ArrayList<>(!cards.isEmpty() ? cards : !shown.isEmpty() ? shown : SeedTeachers.courses());
        Collections.sort(cl, (x, y) -> Ui.nz(x.code).compareTo(Ui.nz(y.code)));
        boolean searching = !query.trim().isEmpty();
        String[] words = query.trim().toLowerCase(Locale.ROOT).split("\\s+");
        b.list.removeAllViews();
        slots.clear();
        java.util.Set<String> found = new java.util.HashSet<>();           // one teacher can teach several courses: count once
        for (CourseInfo c : cl) {
            List<TeacherInfo> match = new ArrayList<>();
            for (TeacherInfo t : c.teachers) if (matches(t, words)) { match.add(t); found.add(ProfilePhotos.teacherKey(t)); }
            if (searching && match.isEmpty()) continue;
            b.list.addView(card(c, match));
        }
        b.tvCount.setText(searching ? "Found " + found.size() + (found.size() == 1 ? " teacher" : " teachers")
                : cl.size() + " courses");
        b.tvEmpty.setVisibility(searching && found.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void refreshAvatars() {
        for (AvatarSlot s : slots) s.view.show(ProfilePhotos.thumb(s.key));
    }

    private View card(CourseInfo c, List<TeacherInfo> teachers) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_card);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(dp(4), dp(6), dp(4), dp(6));
        card.setLayoutParams(lp);

        LinearLayout head = new LinearLayout(this);
        head.addView(text(Ui.nz(c.code) + " - " + Ui.nz(c.title), 16, R.color.ink, true), new LinearLayout.LayoutParams(0, -2, 1f));
        if (staff) {
            TextView edit = text("Edit", 14, R.color.blue, true);
            edit.setPadding(dp(12), dp(2), 0, dp(2));
            edit.setOnClickListener(v -> edit(c));
            head.addView(edit);
        }
        card.addView(head);

        int n = 1;
        for (TeacherInfo t : teachers) {
            ItemTeacherBinding r = ItemTeacherBinding.inflate(getLayoutInflater(), card, false);
            String sn = Ui.nz(t.shortName).isEmpty() ? "" : " (" + t.shortName + ")";
            r.tvName.setText(n + ". " + Ui.nz(t.name) + sn);
            r.tvDept.setText("Department: " + dash(t.dept));
            r.tvEmail.setText("Email: " + dash(t.email));
            r.tvPhone.setText("Phone: " + dash(t.phone));
            boolean canSave = !Ui.nz(t.phone).isEmpty() || !Ui.nz(t.email).isEmpty();
            r.tvSave.setVisibility(canSave ? View.VISIBLE : View.GONE);
            r.tvSave.setOnClickListener(v -> saveToContacts(t));

            // profile picture: saved one, or the default. Tap -> PhotoViewActivity (this screen goes to onPause)
            final String key = ProfilePhotos.teacherKey(t);
            final String sub = "Teacher" + (Ui.nz(t.dept).isEmpty() ? "" : "  |  " + t.dept);
            slots.add(new AvatarSlot(r.avatar, key));
            r.avatar.show(ProfilePhotos.thumb(key));
            r.avatar.setOnClickListener(v -> startActivity(PhotoViewActivity.intent(this, key, Ui.nz(t.name), sub)));
            card.addView(r.getRoot());
            n++;
        }
        return card;
    }

    // ---------------- CR: add / edit one whole card ----------------
    @Override protected android.view.View offlineBar() { return b == null ? null : b.tvOffline; }

    /** Offline: adding, editing and deleting course cards is blocked. */
    @Override
    protected void onConnectivityChanged(boolean on) {
        setNeedsInternet(b.btnAddCard, on);
    }

    private void edit(CourseInfo c) {
        if (!requireOnline()) return;
        if (fs == null || (c != null && usingDefaults())) { toast("Setting up, try again."); return; }
        boolean isNew = c == null;

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), dp(8));
        EditText etCode = field("Course code", isNew ? "" : Ui.nz(c.code));
        EditText etTitle = field("Course title", isNew ? "" : Ui.nz(c.title));
        box.addView(etCode);
        box.addView(etTitle);
        LinearLayout teacherBox = new LinearLayout(this);
        teacherBox.setOrientation(LinearLayout.VERTICAL);
        box.addView(teacherBox);
        List<EditText[]> rows = new ArrayList<>();
        if (!isNew) for (TeacherInfo t : c.teachers) addRow(teacherBox, rows, t);
        else addRow(teacherBox, rows, null);
        TextView add = text("+ Add teacher", 14, R.color.blue, true);
        add.setPadding(0, dp(14), 0, dp(6));
        add.setOnClickListener(v -> addRow(teacherBox, rows, null));
        box.addView(add);
        ScrollView sv = new ScrollView(this);
        sv.addView(box);

        AlertDialog.Builder d = new AlertDialog.Builder(this).setTitle(isNew ? "Add Course" : "Edit Course").setView(sv)
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null);
        if (!isNew) d.setNeutralButton("Delete", (x, w) -> new AlertDialog.Builder(this)
                .setTitle("Delete " + c.code + "?")
                .setPositiveButton("Delete", (y, z) -> { if (requireOnline()) fs.collection("teacherCards").document(c.id).delete()
                        .addOnFailureListener(e -> Toast.makeText(this, Ui.firebaseError(e), Toast.LENGTH_LONG).show()); })
                .setNegativeButton("Cancel", null).show());
        AlertDialog dlg = d.create();
        dlg.show();
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v0 -> {
                    if (!requireOnline()) return;

                    String code = etCode.getText().toString().trim();
                    if (code.isEmpty()) { toast("Course code is required."); return; }
                    CourseInfo n = new CourseInfo();
                    n.code = code; n.title = etTitle.getText().toString().trim();
                    for (EditText[] r : rows) {
                        TeacherInfo t = new TeacherInfo(r[1].getText().toString().trim(), r[0].getText().toString().trim(),
                                r[2].getText().toString().trim(), r[3].getText().toString().trim(), r[4].getText().toString().trim());
                        if (!t.name.isEmpty()) n.teachers.add(t);
                    }
                    if (n.title.isEmpty()) { toast("Course title is required."); return; }
                    if (n.teachers.isEmpty()) { toast("Add at least one teacher name."); return; }
                    String id = isNew ? SeedTeachers.courseId(code) : c.id;
                    fs.collection("teacherCards").document(id).set(n)
                            .addOnSuccessListener(y -> toast("Saved."))
                            .addOnFailureListener(e -> Toast.makeText(this, Ui.firebaseError(e), Toast.LENGTH_LONG).show());
            dlg.dismiss();
        });
    }

    /** One teacher block inside the edit form: name, short name, department, email, phone. */
    private void addRow(LinearLayout parent, List<EditText[]> rows, TeacherInfo t) {
        LinearLayout g = new LinearLayout(this);
        g.setOrientation(LinearLayout.VERTICAL);
        g.setPadding(0, dp(14), 0, 0);
        EditText[] e = {
            field("Teacher name", t == null ? "" : Ui.nz(t.name)),
            field("Short name", t == null ? "" : Ui.nz(t.shortName)),
            field("Department", t == null ? "" : Ui.nz(t.dept)),
            field("Email", t == null ? "" : Ui.nz(t.email)),
            field("Phone", t == null ? "" : Ui.nz(t.phone))
        };
        g.addView(text("Teacher " + (rows.size() + 1), 13, R.color.muted, true));
        for (EditText x : e) g.addView(x);
        TextView pick = text("Pick number from Contacts", 14, R.color.blue, true);
        pick.setPadding(0, dp(8), 0, 0);
        pick.setOnClickListener(v -> contacts.withReadPermission(() -> pickFromContacts(e), contacts::explainDenied));
        g.addView(pick);
        parent.addView(g);
        rows.add(e);
    }

    private EditText field(String hint, String value) {
        EditText et = new EditText(this);
        et.setHint(hint); et.setText(value); et.setInputType(InputType.TYPE_CLASS_TEXT);
        return et;
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(s); tv.setTextSize(sp); tv.setTextColor(getColor(color));
        if (bold) tv.setTypeface(null, android.graphics.Typeface.BOLD);
        else tv.setPadding(0, dp(2), 0, 0);
        return tv;
    }

    // ---------------- Content Provider (Contacts) ----------------
    /** Reads the phone's contacts (ContentResolver + Cursor), lets the user choose one and fills the form. */
    private void pickFromContacts(EditText[] row) {
        final java.util.List<ContactsHelper.Entry> list;
        try { list = ContactsHelper.readAll(getContentResolver()); }
        catch (RuntimeException ex) { toast("Could not read Contacts."); return; }
        if (list.isEmpty()) { toast("No contacts with a phone number were found."); return; }
        String[] items = new String[list.size()];
        for (int i = 0; i < items.length; i++) items[i] = list.get(i).name + "\n" + list.get(i).phone;
        new AlertDialog.Builder(this).setTitle("Pick from Contacts")
                .setItems(items, (d, which) -> {
                    ContactsHelper.Entry c = list.get(which);
                    row[4].setText(c.phone);
                    if (row[0].getText().toString().trim().isEmpty()) row[0].setText(c.name);
                })
                .setNegativeButton("Cancel", null).show();
    }

    /** Checks Contacts for the number; if already saved says so, otherwise opens the "Add contact" screen with the details filled in. */
    private void saveToContacts(TeacherInfo t) {
        String name = Ui.nz(t.name).isEmpty() ? Ui.nz(t.shortName) : Ui.nz(t.name);
        contacts.saveContact(name, t.phone, t.email, t.dept, "Teacher", null);
    }

    private static String dash(String s) { return s == null || s.isEmpty() ? "-" : s; }
    private void toast(String m) { Toast.makeText(this, m, Toast.LENGTH_SHORT).show(); }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    @Override
    protected void onDestroy() {
        if (reg != null) reg.remove();
        if (photoReg != null) photoReg.remove();
        super.onDestroy();
    }
}
