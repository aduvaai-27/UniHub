package com.unihub.app.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import com.google.firebase.FirebaseApp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.MetadataChanges;
import com.google.firebase.firestore.Query;
import com.unihub.app.adapter.NoticeAdapter;
import com.unihub.app.data.Notice;
import com.unihub.app.databinding.ActivityNoticesBinding;
import com.unihub.app.databinding.DialogNoticeBinding;
import com.unihub.app.util.Session;
import com.unihub.app.util.Ui;
import java.util.List;

/** Realtime notice board. Everyone reads; only CR can post or delete. */
public class NoticesActivity extends BaseActivity {
    private ActivityNoticesBinding b;
    private NoticeAdapter adapter;
    private FirebaseFirestore firestore;
    private ListenerRegistration listener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityNoticesBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        boolean staff = Session.isStaff(this);
        adapter = new NoticeAdapter(this, staff, this::confirmDelete);
        b.list.setAdapter(adapter);              // ListView + ArrayAdapter
        b.tvSub.setText(staff ? "You can post notices as " + Session.name(this) : "Announcements from the Class Representative");

        if (FirebaseApp.getApps(this).isEmpty()) {
            b.tvEmpty.setText("Firebase is not connected.\nAdd google-services.json (see README).");
            b.tvEmpty.setVisibility(View.VISIBLE);
            return;
        }
        firestore = FirebaseFirestore.getInstance();
        if (staff) {
            b.fabPost.setVisibility(View.VISIBLE);
            b.fabPost.setOnClickListener(v -> showPostDialog());
        }
        final String baseSub = b.tvSub.getText().toString();
        listener = firestore.collection("notices").orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener(MetadataChanges.INCLUDE, (snap, e) -> {
                    if (e != null) { b.tvEmpty.setText(Ui.firebaseError(e)); b.tvEmpty.setVisibility(View.VISIBLE); return; }
                    if (snap == null) return;
                    List<Notice> list = snap.toObjects(Notice.class);
                    b.tvSub.setText(snap.getMetadata().isFromCache() ? "Offline: showing a saved copy, not synced with the server" : baseSub);
                    adapter.submit(list);
                    b.tvEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
                });
    }

    @Override protected android.view.View offlineBar() { return b == null ? null : b.tvOffline; }

    /** Offline: posting and deleting notices is blocked. */
    @Override
    protected void onConnectivityChanged(boolean on) {
        setNeedsInternet(b.fabPost, on);
    }

    private void showPostDialog() {
        if (!requireOnline()) return;
        DialogNoticeBinding d = DialogNoticeBinding.inflate(LayoutInflater.from(this));
        new AlertDialog.Builder(this)
                .setTitle("Post Notice")
                .setView(d.getRoot())
                .setPositiveButton("Post", (dlg, w) -> {
                    if (!requireOnline()) return;
                    String title = d.etTitle.getText().toString().trim();
                    if (title.isEmpty()) return;
                    String who = Session.name(this) + (Session.CR.equals(Session.role(this)) ? " (CR)" : "");
                    firestore.collection("notices").add(new Notice(title, d.etBody.getText().toString().trim(), who))
                            .addOnFailureListener(ex -> Toast.makeText(this, Ui.firebaseError(ex), Toast.LENGTH_LONG).show());
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmDelete(Notice n) {
        if (!requireOnline()) return;
        new AlertDialog.Builder(this)
                .setTitle("Delete this notice?")
                .setPositiveButton("Delete", (d, w) -> { if (requireOnline()) firestore.collection("notices").document(n.id).delete(); })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    protected void onStart() {
        super.onStart();
        com.unihub.app.util.ChatPrefs.noticesVisible = true;
    }

    @Override
    protected void onStop() {
        com.unihub.app.util.ChatPrefs.noticesVisible = false;
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (listener != null) listener.remove();
        super.onDestroy();
    }
}
