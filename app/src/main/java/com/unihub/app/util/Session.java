package com.unihub.app.util;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.unihub.app.data.UniHubProvider;

/** Remembers who is logged in (name, role, roll) on top of Firebase Auth. Roles: student or CR. */
public class Session {
    public static final String STUDENT = "student", CR = "cr";

    private static SharedPreferences p(Context c) { return c.getSharedPreferences("unihub_session", Context.MODE_PRIVATE); }

    public static boolean isLoggedIn(Context c) {
        if (!p(c).contains("role")) return false;
        if (FirebaseApp.getApps(c).isEmpty()) return true;          // local mode (no Firebase)
        return FirebaseAuth.getInstance().getCurrentUser() != null;
    }

    /**
     * Deletes the local copy of attendance marks (SQLite, through the Content Provider).
     * The table belongs to whoever is signed in, so it must be emptied when the account changes;
     * otherwise the next person on this phone would see the previous person's "Classes Attended".
     */
    public static void clearLocalMarks(Context c) {
        try { c.getContentResolver().delete(UniHubProvider.MARKS_URI, null, null); }
        catch (RuntimeException ignored) { }
    }

    public static void save(Context c, String name, String role, String roll, String group) {
        clearLocalMarks(c);                       // fresh sign-in / registration: start with an empty local copy
        p(c).edit().putString("name", name).putString("role", role).putString("id", roll)
                .putString("group", group == null ? "" : group).apply();
    }

    public static String group(Context c) { return p(c).getString("group", ""); }
    public static void setGroup(Context c, String g) { p(c).edit().putString("group", g).apply(); }

    public static String name(Context c) { return p(c).getString("name", ""); }
    public static String role(Context c) { return p(c).getString("role", STUDENT); }
    public static String roll(Context c) { return p(c).getString("id", ""); }
    public static boolean isStaff(Context c) { return CR.equals(role(c)); }

    public static String roleLabel(Context c) {
        String g = group(c);
        return (isStaff(c) ? "Class Representative" : "Student") + "  |  Roll " + roll(c)
                + (g.isEmpty() ? "" : "  |  Group " + g);
    }

    public static void logout(Context c) {
        if (!FirebaseApp.getApps(c).isEmpty()) FirebaseAuth.getInstance().signOut();
        clearLocalMarks(c);                       // do not leave this user's attendance on the phone
        p(c).edit().clear().apply();
        c.getSharedPreferences("unihub_notify", Context.MODE_PRIVATE).edit().clear().apply();
    }
}
