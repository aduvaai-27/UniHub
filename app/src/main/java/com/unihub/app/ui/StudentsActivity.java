package com.unihub.app.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.FirebaseApp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.MetadataChanges;
import com.google.firebase.firestore.WriteBatch;
import com.unihub.app.R;
import com.unihub.app.data.ProfilePhotos;
import com.unihub.app.data.StudentData;
import com.unihub.app.data.StudentInfo;
import com.unihub.app.databinding.ActivityStudentsBinding;
import com.unihub.app.databinding.ItemStudentBinding;
import com.unihub.app.util.ContactsAccess;
import com.unihub.app.util.RollValidator;
import com.unihub.app.util.Session;
import com.unihub.app.util.Ui;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Student cards, roll wise. Everyone can read; the CR adds students and edits a whole card with one Edit. */
public class StudentsActivity extends BaseActivity {
    private ActivityStudentsBinding b;
    private boolean staff, seeding;
    private final List<StudentInfo> items = new ArrayList<>();           // what is on screen (after the search filter)
    private final List<StudentInfo> all = new ArrayList<>();             // every student
    private String query = "";
    private List<StudentInfo> stored = new ArrayList<>();
    private RecyclerView.Adapter<VH> adapter;
    private ListenerRegistration reg, photoReg;
    private FirebaseFirestore fs;
    private final ContactsAccess contacts = new ContactsAccess(this);     // Content Provider (Contacts)

    private static final String[] GROUPS = {"A1", "A2", "B1", "B2"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityStudentsBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        staff = Session.isStaff(this);
        adapter = buildAdapter();
        b.recycler.setLayoutManager(new LinearLayoutManager(this));
        b.recycler.setAdapter(adapter);
        b.etSearch.addTextChangedListener(new TextWatcher() {            // live search: name or roll
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) { }
            @Override public void onTextChanged(CharSequence s, int st, int bf, int c) { }
            @Override public void afterTextChanged(Editable s) { query = s.toString(); applyFilter(); }
        });

        if (FirebaseApp.getApps(this).isEmpty()) { show(); return; }       // built-in list, read only
        fs = FirebaseFirestore.getInstance();
        if (staff) {
            b.btnAddStudent.setVisibility(View.VISIBLE);
            b.btnAddStudent.setOnClickListener(v -> edit(null));
        }
        photoReg = ProfilePhotos.watchAll(this, () -> adapter.notifyDataSetChanged());   // pictures appear / change live
        reg = fs.collection("students").addSnapshotListener(MetadataChanges.INCLUDE, (snap, e) -> {
            if (snap == null) {
                if (e != null) Toast.makeText(this, Ui.firebaseError(e), Toast.LENGTH_LONG).show();
                show();                                    // still show the built-in list
                return;
            }
            stored = snap.toObjects(StudentInfo.class);
            // CR: if the server has nothing yet, store the built-in student list (once)
            if (staff && stored.isEmpty() && !snap.getMetadata().isFromCache() && !seeding) {
                seeding = true;
                WriteBatch batch = fs.batch();
                for (String[] s : StudentData.ALL)
                    batch.set(fs.collection("students").document(s[0]), new StudentInfo(s[0], s[1], s[2], s[3]));
                batch.commit().addOnCompleteListener(t -> seeding = false);
            }
            show();
        });
    }

    private void show() {
        all.clear();
        if (stored.isEmpty()) {
            for (String[] s : StudentData.ALL) all.add(new StudentInfo(s[0], s[1], s[2], s[3]));
        } else {
            all.addAll(stored);
            Collections.sort(all, (x, y) -> Ui.nz(x.roll).compareTo(Ui.nz(y.roll)));
        }
        applyFilter();
    }

    /**
     * Keeps the students that match the search. Every word typed must appear in the name or in the roll,
     * so "rohan", "2307054", "054" and "md rohan" all work. An empty search shows everyone.
     */
    private void applyFilter() {
        String[] words = query.trim().toLowerCase(Locale.ROOT).split("\\s+");
        items.clear();
        for (StudentInfo s : all) {
            String name = Ui.nz(s.name).toLowerCase(Locale.ROOT), roll = Ui.nz(s.roll);
            boolean ok = true;
            for (String w : words) if (!w.isEmpty() && !name.contains(w) && !roll.contains(w)) { ok = false; break; }
            if (ok) items.add(s);
        }
        adapter.notifyDataSetChanged();
        boolean searching = !query.trim().isEmpty();
        b.tvCount.setText(searching ? "Found " + items.size() + " of " + all.size() : all.size() + " students");
        b.tvEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private static class VH extends RecyclerView.ViewHolder {
        final ItemStudentBinding v;
        VH(ItemStudentBinding v) { super(v.getRoot()); this.v = v; }
    }

    private RecyclerView.Adapter<VH> buildAdapter() {
        return new RecyclerView.Adapter<VH>() {
            @NonNull @Override
            public VH onCreateViewHolder(@NonNull ViewGroup p, int t) {
                return new VH(ItemStudentBinding.inflate(getLayoutInflater(), p, false));
            }

            @Override
            public void onBindViewHolder(@NonNull VH h, int pos) {
                StudentInfo s = items.get(pos);
                ItemStudentBinding v = h.v;
                boolean hasGroup = s.group != null && !s.group.isEmpty();
                String roll = "Roll " + Ui.nz(s.roll) + (hasGroup ? ", Group " + s.group : "");
                v.tvRoll.setText("Roll " + Ui.nz(s.roll) + (hasGroup ? "   |   Group " + s.group : ""));
                v.tvName.setText(Ui.nz(s.name));
                v.tvPhone.setText("Phone: " + dash(s.phone));
                v.tvEmail.setText("Email: " + dash(s.email));

                // profile picture: saved one, or the default. Tap -> PhotoViewActivity (this screen goes to onPause)
                String key = ProfilePhotos.studentKey(Ui.nz(s.roll));
                v.avatar.show(ProfilePhotos.thumb(key));
                v.avatar.setOnClickListener(x -> startActivity(PhotoViewActivity.intent(StudentsActivity.this, key, Ui.nz(s.name), roll)));

                boolean canSave = !Ui.nz(s.phone).isEmpty() || !Ui.nz(s.email).isEmpty();
                v.tvSave.setVisibility(canSave ? View.VISIBLE : View.GONE);
                v.tvSave.setOnClickListener(x -> contacts.saveContact(Ui.nz(s.name), s.phone, s.email,
                        "KUET", "Student", roll));
                v.tvEdit.setVisibility(staff ? View.VISIBLE : View.GONE);
                v.tvEdit.setOnClickListener(x -> edit(s));
            }

            @Override public int getItemCount() { return items.size(); }
        };
    }

    // ---------------- CR: add / edit one whole card ----------------
    @Override protected android.view.View offlineBar() { return b == null ? null : b.tvOffline; }

    /** Offline: adding, editing and deleting students is blocked. */
    @Override
    protected void onConnectivityChanged(boolean on) {
        setNeedsInternet(b.btnAddStudent, on);
    }

    private void edit(StudentInfo s) {
        if (!requireOnline()) return;
        if (fs == null || (s != null && stored.isEmpty())) { toast("Setting up, try again."); return; }
        boolean isNew = s == null;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), dp(8));
        EditText etRoll = field("Roll", isNew ? "" : Ui.nz(s.roll));
        etRoll.setEnabled(isNew);
        etRoll.setInputType(InputType.TYPE_CLASS_NUMBER);
        EditText etName = field("Name", isNew ? "" : Ui.nz(s.name));
        EditText etGroup = field("Group (A1, A2, B1 or B2)", isNew ? "" : Ui.nz(s.group));
        EditText etPhone = field("Phone", isNew ? "" : Ui.nz(s.phone));
        EditText etEmail = field("Email", isNew ? "" : Ui.nz(s.email));
        etEmail.setInputType(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        for (EditText e : new EditText[]{etRoll, etName, etGroup, etPhone, etEmail}) box.addView(e);
        ScrollView sv = new ScrollView(this);
        sv.addView(box);

        AlertDialog.Builder d = new AlertDialog.Builder(this).setTitle(isNew ? "Add Student" : "Edit Student").setView(sv)
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null);
        if (!isNew) d.setNeutralButton("Delete", (x, w) -> new AlertDialog.Builder(this)
                .setTitle("Delete " + s.name + "?")
                .setPositiveButton("Delete", (y, z) -> { if (requireOnline()) fs.collection("students").document(s.id).delete()
                        .addOnFailureListener(e -> Toast.makeText(this, Ui.firebaseError(e), Toast.LENGTH_LONG).show()); })
                .setNegativeButton("Cancel", null).show());
        AlertDialog dlg = d.create();
        dlg.show();
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v0 -> {
                    if (!requireOnline()) return;

                    String roll = etRoll.getText().toString().trim();
                    String group = etGroup.getText().toString().trim().toUpperCase();
                    if (isNew && !RollValidator.isValid(roll)) { toast("Roll must be between 2307001 and 2307123."); return; }
                    if (etName.getText().toString().trim().isEmpty()) { toast("Name is required."); return; }
                    if (!group.isEmpty() && !java.util.Arrays.asList(GROUPS).contains(group)) { toast("Group must be A1, A2, B1 or B2."); return; }
                    StudentInfo n = new StudentInfo();
                    n.roll = isNew ? roll : s.roll;
                    n.name = etName.getText().toString().trim();
                    n.group = group;
                    n.phone = etPhone.getText().toString().trim();
                    n.email = etEmail.getText().toString().trim();

                    if (!isNew) { save(n); dlg.dismiss(); return; }

                    // New student: the roll must not exist yet. Quick check on the list on screen first...
                    for (StudentInfo x : all) {
                        if (roll.equals(Ui.nz(x.roll))) { duplicateRoll(etRoll, roll); return; }
                    }
                    // ...then ask the server too, in case the list on screen is not up to date.
                    dlg.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                    fs.collection("students").document(roll).get().addOnCompleteListener(t -> {
                        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                        if (!t.isSuccessful()) {
                            Exception e = t.getException();
                            Toast.makeText(this, e != null ? Ui.firebaseError(e) : "Could not check the roll. Try again.", Toast.LENGTH_LONG).show();
                            return;
                        }
                        if (t.getResult() != null && t.getResult().exists()) { duplicateRoll(etRoll, roll); return; }
                        save(n);
                        dlg.dismiss();
                    });
        });
    }

    /** Toast + mark the roll field, the dialog stays open so the CR can fix the number. */
    private void duplicateRoll(EditText etRoll, String roll) {
        toast("Roll " + roll + " already exists.");
        etRoll.setError("Roll already exists");
        etRoll.requestFocus();
    }

    private void save(StudentInfo n) {
        fs.collection("students").document(n.roll).set(n)
                .addOnSuccessListener(y -> toast("Saved."))
                .addOnFailureListener(e -> Toast.makeText(this, Ui.firebaseError(e), Toast.LENGTH_LONG).show());
    }

    private EditText field(String hint, String value) {
        EditText et = new EditText(this);
        et.setHint(hint); et.setText(value);
        et.setInputType(InputType.TYPE_CLASS_TEXT);
        return et;
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
