package com.unihub.app.data;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

/** Content Provider exposing the attendance marks: content://com.unihub.app.provider/marks */
public class UniHubProvider extends ContentProvider {
    public static final String AUTHORITY = "com.unihub.app.provider";
    public static final Uri MARKS_URI = Uri.parse("content://" + AUTHORITY + "/marks");
    private static final int MARKS = 1, MARK_ID = 2;
    private static final UriMatcher MATCHER = new UriMatcher(UriMatcher.NO_MATCH);
    static {
        MATCHER.addURI(AUTHORITY, "marks", MARKS);
        MATCHER.addURI(AUTHORITY, "marks/#", MARK_ID);
    }

    private DbHelper helper;

    @Override public boolean onCreate() { helper = new DbHelper(getContext()); return true; }

    private String withId(Uri uri, String sel) {
        return MATCHER.match(uri) == MARK_ID
                ? "_id=" + uri.getLastPathSegment() + (sel == null ? "" : " AND (" + sel + ")") : sel;
    }

    @Override
    public Cursor query(Uri uri, String[] proj, String sel, String[] args, String sort) {
        return helper.getReadableDatabase().query(DbHelper.T_MARKS, proj, withId(uri, sel), args, null, null, sort);
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        long id = helper.getWritableDatabase().insertWithOnConflict(DbHelper.T_MARKS, null, values,
                SQLiteDatabase.CONFLICT_REPLACE);
        return ContentUris.withAppendedId(MARKS_URI, id);
    }

    @Override
    public int delete(Uri uri, String sel, String[] args) {
        return helper.getWritableDatabase().delete(DbHelper.T_MARKS, withId(uri, sel), args);
    }

    @Override
    public int update(Uri uri, ContentValues v, String sel, String[] args) {
        return helper.getWritableDatabase().update(DbHelper.T_MARKS, v, withId(uri, sel), args);
    }

    @Override
    public String getType(Uri uri) {
        return MATCHER.match(uri) == MARK_ID
                ? "vnd.android.cursor.item/vnd.unihub.mark" : "vnd.android.cursor.dir/vnd.unihub.mark";
    }
}
