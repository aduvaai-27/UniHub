package com.unihub.app.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import com.google.android.material.snackbar.Snackbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ServerValue;
import com.unihub.app.R;
import com.unihub.app.adapter.ChatAdapter;
import com.unihub.app.data.ChatMessage;
import com.unihub.app.databinding.ActivityChatBinding;
import com.unihub.app.util.ChatPrefs;
import com.unihub.app.util.Notifier;
import com.unihub.app.util.Session;
import java.util.HashMap;
import java.util.Map;

/**
 * Group chat for the whole class (students and CR) on Firebase Realtime Database.
 * New messages arrive live through a ChildEventListener on chat/messages.
 * Firestore stays the place for the main data; Realtime Database is used only for this live feature.
 */
public class ChatActivity extends BaseActivity {
    private static final int LAST_N = 100;                       // load only the latest 100 messages

    private ActivityChatBinding b;
    private ChatAdapter adapter;
    private Query query;
    private DatabaseReference messages;
    private ChildEventListener listener;
    private static final int REQ_NOTIF = 21;
    private String myUid;
    private boolean chatReady = false;                           // Firebase + sign-in are fine, chat can be used

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityChatBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        showMuteState();
        b.btnMute.setOnClickListener(v -> {
            boolean mute = !ChatPrefs.isMuted(this);
            ChatPrefs.setMuted(this, mute);
            showMuteState();
            if (!mute) askNotificationPermission();                  // turning alerts back on: make sure they can show
        });
        if (!ChatPrefs.isMuted(this)) askNotificationPermission();

        FirebaseUser u = FirebaseApp.getApps(this).isEmpty() ? null : FirebaseAuth.getInstance().getCurrentUser();
        if (u == null) {
            b.tvSub.setText("Chat needs Firebase and a signed-in account");
            b.btnSend.setEnabled(false); b.etMessage.setEnabled(false);
            return;
        }
        myUid = u.getUid();

        try {
            FirebaseDatabase db = ChatPrefs.database();
            messages = db.getReference("chat").child("messages");
        } catch (Exception e) {
            b.tvSub.setText("Realtime Database is not set up. See the README (Group Chat).");
            b.btnSend.setEnabled(false); b.etMessage.setEnabled(false);
            return;
        }

        adapter = new ChatAdapter(myUid);
        LinearLayoutManager lm = new LinearLayoutManager(this);
        lm.setStackFromEnd(true);                                // newest message at the bottom
        b.recycler.setLayoutManager(lm);
        b.recycler.setAdapter(adapter);
        b.tvEmpty.setVisibility(View.VISIBLE);
        b.btnSend.setOnClickListener(v -> send());
        chatReady = true;
    }

    @Override protected View offlineBar() { return b == null ? null : b.tvOffline; }

    /** Offline: the red bar is shown (by BaseActivity) and the message box and the Send button are blocked. */
    @Override
    protected void onConnectivityChanged(boolean on) {
        if (!chatReady) return;                                  // chat was already disabled for another reason
        b.etMessage.setEnabled(on);
        setNeedsInternet(b.btnSend, on);
        b.etMessage.setHint(on ? R.string.hint_type_a_message : R.string.hint_offline_typing);
        if (!on) {
            b.etMessage.clearFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(b.etMessage.getWindowToken(), 0);
        }
    }

    private void showMuteState() { b.btnMute.setText(ChatPrefs.isMuted(this) ? "Unmute" : "Mute"); }

    /** Android 13+: ask for the notification permission with requestPermissions(). */
    private void askNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33) return;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return;
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
    }

    @Override
    public void onRequestPermissionsResult(int code, @NonNull String[] perms, @NonNull int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code != REQ_NOTIF || (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED)) return;
        Snackbar.make(b.getRoot(), "Chat notifications are blocked by Android.", Snackbar.LENGTH_LONG)
                .setAction("Settings", v -> startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName())))
                .show();
    }

    @Override
    protected void onStart() {
        super.onStart();
        ChatPrefs.chatVisible = true;
        NotificationManagerCompat.from(this).cancel(Notifier.ID_CHAT);   // clear the alert once the chat is opened
        if (messages == null || listener != null) return;
        listener = new ChildEventListener() {
            @Override public void onChildAdded(@NonNull DataSnapshot s, @Nullable String prev) {
                ChatMessage m = read(s);
                if (m == null) return;
                b.tvEmpty.setVisibility(View.GONE);
                b.recycler.scrollToPosition(adapter.add(m));
            }
            @Override public void onChildChanged(@NonNull DataSnapshot s, @Nullable String prev) {
                ChatMessage m = read(s);                         // e.g. server time replaced the local estimate
                if (m != null) adapter.update(m);
            }
            @Override public void onChildRemoved(@NonNull DataSnapshot s) { if (s.getKey() != null) adapter.remove(s.getKey()); }
            @Override public void onChildMoved(@NonNull DataSnapshot s, @Nullable String prev) { }
            @Override public void onCancelled(@NonNull DatabaseError e) {
                b.tvSub.setText("Chat error: " + e.getMessage());
            }
        };
        query = messages.limitToLast(LAST_N);                    // push ids are time ordered
        query.addChildEventListener(listener);
    }

    @Override
    protected void onStop() {
        ChatPrefs.chatVisible = false;
        if (query != null && listener != null) { query.removeEventListener(listener); listener = null; }
        // the list is rebuilt from the database when the screen starts again
        if (adapter != null) { adapter = new ChatAdapter(myUid); b.recycler.setAdapter(adapter); }
        super.onStop();
    }

    private ChatMessage read(DataSnapshot s) {
        ChatMessage m = s.getValue(ChatMessage.class);
        if (m == null) return null;
        m.key = s.getKey();
        if (s.child("timestamp").getValue() instanceof Long) m.timestamp = (Long) s.child("timestamp").getValue();
        return m;
    }

    private void send() {
        String text = b.etMessage.getText().toString().trim();
        if (text.isEmpty()) return;
        if (!requireOnline()) return;
        Map<String, Object> m = new HashMap<>();
        m.put("uid", myUid);
        m.put("name", Session.name(this));
        m.put("roll", Session.roll(this));
        m.put("role", Session.role(this));
        m.put("text", text);
        m.put("timestamp", ServerValue.TIMESTAMP);               // set by the server, same clock for everyone
        b.etMessage.setText("");
        messages.push().setValue(m).addOnFailureListener(e -> {
            b.etMessage.setText(text);
            Toast.makeText(this, "Could not send: " + e.getMessage(), Toast.LENGTH_LONG).show();
        });
    }
}
