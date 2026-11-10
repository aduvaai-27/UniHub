package com.unihub.app.util;

import android.content.Context;
import android.content.SharedPreferences;

/** Chat notification settings kept on this phone: mute switch, last message already handled, and whether the chat screen is open. */
public class ChatPrefs {
    /**
     * Realtime Database URL (Firebase Console -> Realtime Database -> Data tab, shown at the top).
     * google-services.json has no firebase_url, so the URL is set here. Change it if yours is different,
     * e.g. https://unihub-3a25a-default-rtdb.asia-southeast1.firebasedatabase.app
     */
    public static final String DB_URL = "https://unihub-3a25a-default-rtdb.firebaseio.com";

    public static com.google.firebase.database.FirebaseDatabase database() {
        return DB_URL.isEmpty() ? com.google.firebase.database.FirebaseDatabase.getInstance()
                                : com.google.firebase.database.FirebaseDatabase.getInstance(DB_URL);
    }

    /** True while ChatActivity is on screen, so no notification is shown for messages the user is already reading. */
    public static volatile boolean chatVisible = false;

    /** True while the Notices screen is on screen: no alert for notices the user is already reading. */
    public static volatile boolean noticesVisible = false;

    private static SharedPreferences p(Context c) { return c.getSharedPreferences("unihub_chat", Context.MODE_PRIVATE); }

    public static boolean isMuted(Context c) { return p(c).getBoolean("muted", false); }
    public static void setMuted(Context c, boolean m) { p(c).edit().putBoolean("muted", m).apply(); }

    public static String lastKey(Context c) { return p(c).getString("last_key", null); }
    public static void setLastKey(Context c, String k) { p(c).edit().putString("last_key", k).apply(); }
}
