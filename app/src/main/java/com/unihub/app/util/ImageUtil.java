package com.unihub.app.util;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.util.Base64;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** Turns a picture chosen from the gallery into a small square JPEG (as Base64 text) that is cheap to store in Firestore. */
public final class ImageUtil {
    private ImageUtil() { }

    /** Reads the picture, fixes its rotation (EXIF) and crops it to a centred square of at most maxSide pixels. */
    public static Bitmap decodeSquare(ContentResolver cr, Uri uri, int maxSide) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = cr.openInputStream(uri)) {
            if (in == null) throw new IOException("Could not open the picture.");
            BitmapFactory.decodeStream(in, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw new IOException("This file is not a picture.");

        int shortSide = Math.min(bounds.outWidth, bounds.outHeight);
        int sample = 1;
        while (shortSide / (sample * 2) >= maxSide) sample *= 2;          // decode smaller: saves memory on big photos

        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        Bitmap src;
        try (InputStream in = cr.openInputStream(uri)) {
            src = in == null ? null : BitmapFactory.decodeStream(in, null, opts);
        }
        if (src == null) throw new IOException("Could not read the picture.");

        int side = Math.min(src.getWidth(), src.getHeight());
        int x = (src.getWidth() - side) / 2, y = (src.getHeight() - side) / 2;
        Matrix m = new Matrix();
        float scale = Math.min(1f, (float) maxSide / side);
        m.postScale(scale, scale);
        m.postRotate(exifRotation(cr, uri));
        Bitmap out = Bitmap.createBitmap(src, x, y, side, side, m, true);
        if (out != src) src.recycle();

        if (out.hasAlpha()) {                                              // PNG with transparency: JPEG has none, use white
            Bitmap flat = Bitmap.createBitmap(out.getWidth(), out.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(flat);
            c.drawColor(Color.WHITE);
            c.drawBitmap(out, 0, 0, null);
            out.recycle();
            out = flat;
        }
        return out;
    }

    /** Squares of the given size (used for the small list picture). */
    public static Bitmap scaleSquare(Bitmap src, int px) {
        return src.getWidth() <= px ? src : Bitmap.createScaledBitmap(src, px, px, true);
    }

    /** JPEG as Base64. Lowers the quality (then the size) until the text is no longer than maxChars. */
    public static String toBase64Jpeg(Bitmap src, int maxChars) {
        Bitmap b = src;
        int quality = 85;
        while (true) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            b.compress(Bitmap.CompressFormat.JPEG, quality, out);
            int chars = (out.size() + 2) / 3 * 4;
            if (chars <= maxChars) {
                if (b != src) b.recycle();
                return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
            }
            if (quality > 40) { quality -= 15; continue; }
            int smaller = Math.round(b.getWidth() * 0.8f);
            if (smaller < 64) throw new IllegalStateException("The picture is too detailed to store.");
            Bitmap s = Bitmap.createScaledBitmap(b, smaller, smaller, true);
            if (b != src) b.recycle();
            b = s;
            quality = 75;
        }
    }

    public static Bitmap fromBase64(String data) {
        try {
            byte[] bytes = Base64.decode(data, Base64.NO_WRAP);
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static int exifRotation(ContentResolver cr, Uri uri) {
        try (InputStream in = cr.openInputStream(uri)) {
            if (in == null) return 0;
            int o = new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            if (o == ExifInterface.ORIENTATION_ROTATE_90) return 90;
            if (o == ExifInterface.ORIENTATION_ROTATE_180) return 180;
            if (o == ExifInterface.ORIENTATION_ROTATE_270) return 270;
        } catch (Exception ignored) { }
        return 0;
    }
}
