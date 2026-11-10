package com.unihub.app.data;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import androidx.annotation.Nullable;
import com.google.firebase.FirebaseApp;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.WriteBatch;
import com.unihub.app.util.ImageUtil;
import com.unihub.app.util.Ui;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Profile pictures of students, the CR and teachers, kept in Firestore (works on the free Spark plan,
 * no Cloud Storage billing needed).
 *
 *   photoThumbs/{key}  { thumb: Base64 JPEG 160px,  updatedAt }   small, listened to by every list screen
 *   photoFull/{key}    { full:  Base64 JPEG 800px,  updatedAt }   big, read only by the full-screen viewer
 *
 * Both documents are written in one batch, so they never disagree. If there is no document, the screens show
 * the default picture (drawable/default_avatar).
 *
 * key:  student or CR -> "s_" + roll        teacher -> "t_" + name (letters and digits)
 * The size limits below are repeated in firestore.rules - change both together.
 */
public final class ProfilePhotos {
    public static final String THUMBS = "photoThumbs", FULL = "photoFull";
    private static final int THUMB_PX = 160, FULL_PX = 800;
    private static final int THUMB_MAX_CHARS = 40_000, FULL_MAX_CHARS = 600_000;

    private ProfilePhotos() { }

    // ---------------- keys ----------------
    public static String studentKey(String roll) { return "s_" + Ui.nz(roll).trim(); }

    public static String teacherKey(TeacherInfo t) {
        String base = Ui.nz(t.name).trim().isEmpty() ? Ui.nz(t.shortName) : Ui.nz(t.name);
        String slug = base.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        return "t_" + (slug.isEmpty() ? "unknown" : slug);
    }

    // ---------------- cache (main thread only) ----------------
    private static final Map<String, String> raw = new HashMap<>();                       // key -> Base64 of the thumbnail
    private static final LruCache<String, Bitmap> bitmaps = new LruCache<>(200);          // key -> decoded thumbnail
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    /** The small picture of this person, or null when none is saved (show default_avatar then). */
    @Nullable
    public static Bitmap thumb(String key) {
        Bitmap b = bitmaps.get(key);
        if (b != null) return b;
        String data = raw.get(key);
        if (data == null) return null;
        b = ImageUtil.fromBase64(data);
        if (b != null) bitmaps.put(key, b);
        return b;
    }

    private static void apply(String key, @Nullable String base64) {
        if (base64 == null || base64.isEmpty()) { raw.remove(key); bitmaps.remove(key); }
        else if (!base64.equals(raw.get(key))) { raw.put(key, base64); bitmaps.remove(key); }
    }

    private static boolean ready(Context c) { return !FirebaseApp.getApps(c).isEmpty(); }
    private static FirebaseFirestore db() { return FirebaseFirestore.getInstance(); }

    // ---------------- live listeners (remove them in onDestroy / onStop) ----------------
    /** Every thumbnail (student and teacher lists). onChange runs on the main thread. Returns null without Firebase. */
    @Nullable
    public static ListenerRegistration watchAll(Context c, Runnable onChange) {
        if (!ready(c)) return null;
        return db().collection(THUMBS).addSnapshotListener((snap, e) -> {
            if (snap == null) return;
            for (DocumentChange ch : snap.getDocumentChanges())
                apply(ch.getDocument().getId(),
                        ch.getType() == DocumentChange.Type.REMOVED ? null : ch.getDocument().getString("thumb"));
            onChange.run();
        });
    }

    /** One person's thumbnail (home screen avatar, viewer). */
    @Nullable
    public static ListenerRegistration watchThumb(Context c, String key, Runnable onChange) {
        if (!ready(c)) return null;
        return db().collection(THUMBS).document(key).addSnapshotListener((d, e) -> {
            if (d == null) return;
            apply(key, d.exists() ? d.getString("thumb") : null);
            onChange.run();
        });
    }

    public interface BitmapCallback { void onBitmap(@Nullable Bitmap bitmap); }

    /** One person's big picture (null when there is none). The JPEG is decoded off the main thread. */
    @Nullable
    public static ListenerRegistration watchFull(Context c, String key, BitmapCallback cb) {
        if (!ready(c)) return null;
        return db().collection(FULL).document(key).addSnapshotListener((d, e) -> {
            if (d == null) return;
            String data = d.exists() ? d.getString("full") : null;
            if (data == null || data.isEmpty()) { cb.onBitmap(null); return; }
            IO.execute(() -> {
                Bitmap b = ImageUtil.fromBase64(data);
                MAIN.post(() -> cb.onBitmap(b));
            });
        });
    }

    // ---------------- save / remove ----------------
    public interface Result { void done(@Nullable String error); }

    /** Shrinks the picked picture, then stores thumbnail and big picture in one batch. Result runs on the main thread. */
    public static void save(Context c, String key, Uri picture, Result result) {
        if (!ready(c)) { result.done("Firebase is not connected."); return; }
        final Context app = c.getApplicationContext();
        IO.execute(() -> {
            String thumb, full;
            try {
                Bitmap big = ImageUtil.decodeSquare(app.getContentResolver(), picture, FULL_PX);
                Bitmap small = ImageUtil.scaleSquare(big, THUMB_PX);
                thumb = ImageUtil.toBase64Jpeg(small, THUMB_MAX_CHARS);
                full = ImageUtil.toBase64Jpeg(big, FULL_MAX_CHARS);
            } catch (Exception ex) {
                String m = ex.getMessage();
                MAIN.post(() -> result.done(m == null ? "Could not use this picture." : m));
                return;
            }
            Map<String, Object> t = new HashMap<>();
            t.put("thumb", thumb); t.put("updatedAt", FieldValue.serverTimestamp());
            Map<String, Object> f = new HashMap<>();
            f.put("full", full); f.put("updatedAt", FieldValue.serverTimestamp());
            WriteBatch batch = db().batch();
            batch.set(db().collection(THUMBS).document(key), t);
            batch.set(db().collection(FULL).document(key), f);
            batch.commit()
                    .addOnSuccessListener(x -> result.done(null))
                    .addOnFailureListener(ex -> result.done(Ui.firebaseError(ex)));
        });
    }

    /** Removes the picture; the default picture shows again. */
    public static void remove(Context c, String key, Result result) {
        if (!ready(c)) { result.done("Firebase is not connected."); return; }
        WriteBatch batch = db().batch();
        batch.delete(db().collection(THUMBS).document(key));
        batch.delete(db().collection(FULL).document(key));
        batch.commit()
                .addOnSuccessListener(x -> result.done(null))
                .addOnFailureListener(ex -> result.done(Ui.firebaseError(ex)));
    }
}
