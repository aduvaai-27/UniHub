package com.unihub.app.data;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import java.util.List;

/** Keeps a local copy of the student's own attendance in SQLite, written through the Content Provider. */
public class MarksCache {
    public static void sync(Context c, List<AttendanceRecord> records) {
        ContentResolver cr = c.getContentResolver();
        for (AttendanceRecord r : records) {
            ContentValues v = new ContentValues();
            v.put("mark_key", r.sessionKey);
            v.put("course", r.course);
            v.put("date", r.date);
            v.put("status", "P");
            cr.insert(UniHubProvider.MARKS_URI, v);        // replaces an existing row with the same key
        }
    }
}
