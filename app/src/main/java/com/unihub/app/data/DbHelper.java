package com.unihub.app.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Local SQLite: the student's own Present/Absent answers for each class session. */
public class DbHelper extends SQLiteOpenHelper {
    public static final String T_MARKS = "marks";

    public DbHelper(Context c) { super(c, "unihub.db", null, 2); }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + T_MARKS + " (_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "mark_key TEXT UNIQUE, course TEXT, date TEXT, status TEXT)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int o, int n) {
        db.execSQL("DROP TABLE IF EXISTS items");
        db.execSQL("DROP TABLE IF EXISTS courses");
        db.execSQL("DROP TABLE IF EXISTS " + T_MARKS);
        onCreate(db);
    }

    /** Insert or replace the answer for one session (status "P" or "A"). */
    public void setMark(String key, String course, String date, String status) {
        ContentValues v = new ContentValues();
        v.put("mark_key", key); v.put("course", course); v.put("date", date); v.put("status", status);
        getWritableDatabase().insertWithOnConflict(T_MARKS, null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public Map<String, String> getMarks() {
        Map<String, String> map = new HashMap<>();
        Cursor c = getReadableDatabase().query(T_MARKS, new String[]{"mark_key", "status"}, null, null, null, null, null);
        while (c.moveToNext()) map.put(c.getString(0), c.getString(1));
        c.close();
        return map;
    }

    public List<CourseStat> getSummary() {
        List<CourseStat> list = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT course, SUM(CASE WHEN status='P' THEN 1 ELSE 0 END), COUNT(*) "
                + "FROM " + T_MARKS + " GROUP BY course ORDER BY course", null);
        while (c.moveToNext()) {
            CourseStat s = new CourseStat();
            s.name = c.getString(0); s.present = c.getInt(1); s.total = c.getInt(2);
            list.add(s);
        }
        c.close();
        return list;
    }
}
