package com.unihub.app.util;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import com.unihub.app.R;
import com.unihub.app.ui.MainActivity;

/** Creates notification channels and shows notifications. */
public class Notifier {
    public static final String CH_REMINDERS = "reminders";
    public static final String CH_NOTICES = "notices";
    public static final String CH_SYNC = "sync";
    public static final String CH_CHAT = "chat_v2";      // new id: Android will not change the settings of an already created channel
    public static final int ID_CHAT = 4242;                      // one id: a new chat message replaces the previous one

    public static void createChannels(Context c) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = c.getSystemService(NotificationManager.class);
            nm.createNotificationChannel(new NotificationChannel(CH_REMINDERS, "Deadline and exam reminders", NotificationManager.IMPORTANCE_HIGH));
            nm.createNotificationChannel(new NotificationChannel(CH_NOTICES, "New notices, assignments and exams", NotificationManager.IMPORTANCE_HIGH));
            NotificationChannel chat = new NotificationChannel(CH_CHAT, "Group chat messages", NotificationManager.IMPORTANCE_HIGH);
            chat.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);     // allowed on the lock screen, full content
            nm.createNotificationChannel(chat);
            nm.deleteNotificationChannel("chat");                              // old channel from the first version
            nm.createNotificationChannel(new NotificationChannel(CH_SYNC, "Background updates", NotificationManager.IMPORTANCE_MIN));
        }
    }

    public static final String DEFAULT_PERSISTENT_TEXT = "Receiving notices, assignments and exam updates";

    /** Silent, always-on notification required for the background update service (default text). */
    public static Notification persistent(Context c) { return persistent(c, DEFAULT_PERSISTENT_TEXT, ""); }

    /**
     * The service's permanent notification. The service rebuilds it every minute, so the text stays live
     * (for example "Next class: CSE 2201 at 10:00 AM"). Posting it again with the same id only updates it.
     */
    public static Notification persistent(Context c, String text, String detail) {
        PendingIntent tap = PendingIntent.getActivity(c, 0, new Intent(c, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        String big = detail == null || detail.isEmpty() ? text : text + "\n" + detail;
        return new NotificationCompat.Builder(c, CH_SYNC)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("UniHub")
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(big))
                .setSubText("Live updates on")
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setOngoing(true)
                .setContentIntent(tap)
                .build();
    }

    public static void show(Context c, int id, String channel, String title, String text) {
        show(c, id, channel, title, text, MainActivity.class);
    }

    /** Shows a notification; tapping it opens the given screen. */
    public static void show(Context c, int id, String channel, String title, String text, Class<?> target) {
        show(c, id, channel, title, text, text, target);
    }

    /** Cuts a long text to one tidy line (adds ...), so the collapsed notification never runs out of its box. */
    public static String clip(String s, int max) {
        if (s == null) return "";
        s = s.replace('\n', ' ').trim();
        return s.length() <= max ? s : s.substring(0, Math.max(0, max - 1)).trim() + "\u2026";
    }

    /** Same, with a short collapsed line (shortText) and the full text for the expanded view (bigText). */
    public static void show(Context c, int id, String channel, String title, String shortText, String bigText, Class<?> target) {
        String text = shortText;
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(c,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        PendingIntent tap = PendingIntent.getActivity(c, id, new Intent(c, target),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Builder nb = new NotificationCompat.Builder(c, channel)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(title)
                .setContentText(clip(text, 60))
                .setStyle(new NotificationCompat.BigTextStyle().bigText(bigText))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setColor(0xFF0B2A4A)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)   // show full content on the lock screen
                .setAutoCancel(true)
                .setContentIntent(tap);
        if (CH_CHAT.equals(channel)) nb.setCategory(NotificationCompat.CATEGORY_MESSAGE);
        NotificationManagerCompat.from(c).notify(id, nb.build());
    }
}
