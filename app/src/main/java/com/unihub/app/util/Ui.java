package com.unihub.app.util;

import android.graphics.drawable.GradientDrawable;
import com.google.firebase.firestore.FirebaseFirestoreException;
import android.widget.TextView;

/** Formal color palette and small helpers for tags. */
public class Ui {
    public static final int BLUE = 0xFF1F6FB2, TEAL = 0xFF00796B, SLATE = 0xFF455A64, PLUM = 0xFF6A4C93,
            AMBER = 0xFFB26A00, GREEN = 0xFF1B7F4B, RED = 0xFFB3261E, GRAY = 0xFF7A8594, WHITE = 0xFFFFFFFF;
    private static final int[] PALETTE = {BLUE, TEAL, SLATE, PLUM, AMBER, GREEN};

    /** The same text always gets the same color (used per course). */
    public static int colorFor(String key) {
        return key == null ? BLUE : PALETTE[(key.hashCode() & 0x7fffffff) % PALETTE.length];
    }

    /** Solid rectangular tag. */
    public static void solid(TextView tv, int bg, int fg) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(bg);
        g.setCornerRadius(2 * tv.getResources().getDisplayMetrics().density);
        tv.setBackground(g);
        tv.setTextColor(fg);
    }

    /** Light tinted tag with strong text. */
    public static void tag(TextView tv, int color) { solid(tv, (color & 0x00FFFFFF) | 0x1F000000, color); }

    public static String nz(String s) { return s == null ? "" : s; }

    /** Readable message for Firebase errors, so problems are not hidden. */
    public static String firebaseError(Exception e) {
        if (e instanceof FirebaseFirestoreException) {
            FirebaseFirestoreException.Code c = ((FirebaseFirestoreException) e).getCode();
            if (c == FirebaseFirestoreException.Code.PERMISSION_DENIED)
                return "Permission denied. Publish the Firestore rules from the README and make sure you are logged in.";
            if (c == FirebaseFirestoreException.Code.UNAVAILABLE)
                return "No connection to Firebase. Check the internet connection.";
        }
        return e.getMessage();
    }

    /** Dropdown that always lists every option (a normal one hides the others after a choice is made). */
    public static void dropdown(android.content.Context c, android.widget.AutoCompleteTextView v, final java.util.List<String> items, String selected) {
        android.widget.ArrayAdapter<String> a = new android.widget.ArrayAdapter<String>(c, android.R.layout.simple_list_item_1, items) {
            @Override public android.widget.Filter getFilter() {
                return new android.widget.Filter() {
                    @Override protected FilterResults performFiltering(CharSequence cs) {
                        FilterResults r = new FilterResults(); r.values = items; r.count = items.size(); return r;
                    }
                    @Override protected void publishResults(CharSequence cs, FilterResults r) { notifyDataSetChanged(); }
                };
            }
        };
        v.setAdapter(a);
        if (selected != null) v.setText(selected, false);
    }
}
