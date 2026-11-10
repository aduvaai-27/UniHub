package com.unihub.app.util;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import com.unihub.app.receiver.ReminderReceiver;

/** Two reminders per assignment/exam: 1 day before and 1 hour before (AlarmManager -> ReminderReceiver). */
public class Reminders {
    private static final long HOUR = 3600000L, DAY = 24 * HOUR;

    private static PendingIntent pi(Context c, int code, String title, String text) {
        Intent i = new Intent(c, ReminderReceiver.class);
        i.putExtra("id", code).putExtra("title", title).putExtra("text", text);
        return PendingIntent.getBroadcast(c, code, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    public static void schedule(Context c, int id, String title, String text, long eventTime) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        long now = TrustedTime.now();
        if (eventTime - DAY > now)
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, eventTime - DAY, pi(c, id, title, text + " (in 1 day)"));
        if (eventTime - HOUR > now)
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, eventTime - HOUR, pi(c, id + 1, title, text + " (in 1 hour)"));
    }

    public static void cancel(Context c, int id) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        am.cancel(pi(c, id, "", ""));
        am.cancel(pi(c, id + 1, "", ""));
    }
}
