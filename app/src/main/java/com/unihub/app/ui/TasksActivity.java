package com.unihub.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.google.firebase.FirebaseApp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.MetadataChanges;
import com.google.firebase.firestore.WriteBatch;
import com.unihub.app.data.Notice;
import com.unihub.app.util.TimeUtil;
import com.unihub.app.R;
import com.unihub.app.adapter.TaskAdapter;
import com.unihub.app.data.TaskItem;
import com.unihub.app.databinding.ActivityTasksBinding;
import com.unihub.app.util.Session;
import com.unihub.app.util.Ui;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Shared assignments & exams. Everyone reads; only CR can add or delete. */
public class TasksActivity extends BaseActivity {
    private ActivityTasksBinding b;
    private TaskAdapter adapter;
    private ListenerRegistration reg;
    private List<TaskItem> all = new ArrayList<>();
    private String type = TaskItem.ASSIGNMENT;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityTasksBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        boolean staff = Session.isStaff(this);
        adapter = new TaskAdapter(staff, this::confirmDelete);
        b.recycler.setLayoutManager(new LinearLayoutManager(this));
        b.recycler.setAdapter(adapter);
        if (staff) {
            b.fabAdd.setVisibility(View.VISIBLE);
            b.tvSub.setText("You can post and remove items as CR");
            b.fabAdd.setOnClickListener(v -> {
                if (!requireOnline()) return;
                Intent i = new Intent(this, AddTaskActivity.class);
                i.putExtra(AddTaskActivity.EXTRA_TYPE, type);
                startActivity(i);
            });
        }
        b.toggleType.addOnButtonCheckedListener((g, id, checked) -> {
            if (!checked) return;
            type = id == R.id.btnExam ? TaskItem.EXAM : TaskItem.ASSIGNMENT;
            b.fabAdd.setText(type.equals(TaskItem.EXAM) ? "Post Exam" : "Post Assignment");
            refresh();
        });
        b.toggleType.check(R.id.btnAssign);

        if (FirebaseApp.getApps(this).isEmpty()) {
            b.tvEmpty.setText("Firebase is not connected.\nAdd google-services.json (see README).");
            b.tvEmpty.setVisibility(View.VISIBLE);
            return;
        }
        final String baseSub = b.tvSub.getText().toString();
        reg = FirebaseFirestore.getInstance().collection("tasks").addSnapshotListener(MetadataChanges.INCLUDE, (snap, e) -> {
            if (e != null) { b.tvEmpty.setText(Ui.firebaseError(e)); b.tvEmpty.setVisibility(View.VISIBLE); return; }
            if (snap == null) return;
            all = snap.toObjects(TaskItem.class);
            b.tvSub.setText(snap.getMetadata().isFromCache() ? "Offline: showing a saved copy, not synced with the server" : baseSub);
            refresh();
        });
    }

    @Override protected android.view.View offlineBar() { return b == null ? null : b.tvOffline; }

    /** Offline: posting and removing assignments / exams is blocked. */
    @Override
    protected void onConnectivityChanged(boolean on) {
        setNeedsInternet(b.fabAdd, on);
    }

    private void refresh() {
        List<TaskItem> list = new ArrayList<>();
        for (TaskItem t : all) if (type.equals(t.type)) list.add(t);
        Collections.sort(list, (x, y) -> Long.compare(x.due, y.due));
        adapter.submit(list);
        b.tvEmpty.setText(type.equals(TaskItem.EXAM) ? "No exams have been posted." : "No assignments have been posted.");
        b.tvEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void confirmDelete(TaskItem t) {
        if (!requireOnline()) return;
        boolean exam = TaskItem.EXAM.equals(t.type);
        new AlertDialog.Builder(this)
                .setTitle((exam ? "Cancel exam \"" : "Cancel assignment \"") + t.title + "\"?")
                .setMessage("It will disappear for every student, and they will be notified.")
                .setPositiveButton("Cancel it", (d, w) -> {
                    if (!requireOnline()) return;
                    // delete the task and write a "cancelled" notice in one batch; the notice notifies everyone
                    FirebaseFirestore fs = FirebaseFirestore.getInstance();
                    String course = Ui.nz(t.course).isEmpty() ? "" : t.course + ": ";
                    Notice n = new Notice((exam ? "Exam Cancelled: " : "Assignment Cancelled: ") + Ui.nz(t.title),
                            "The " + (exam ? "exam " : "assignment ") + course + Ui.nz(t.title) + " (" + TimeUtil.dateTime(t.due)
                                    + ") has been cancelled.", Session.name(this) + " (CR)");
                    WriteBatch batch = fs.batch();
                    batch.delete(fs.collection("tasks").document(t.id));
                    batch.set(fs.collection("notices").document(), n);
                    batch.commit().addOnFailureListener(e -> Toast.makeText(this, Ui.firebaseError(e), Toast.LENGTH_LONG).show());
                })
                .setNegativeButton("Back", null)
                .show();
    }

    @Override
    protected void onDestroy() {
        if (reg != null) reg.remove();
        super.onDestroy();
    }
}
