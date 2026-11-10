package com.unihub.app.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.unihub.app.util.Notifier;

/** Broadcast Receiver triggered by AlarmManager to show a reminder notification. */
public class ReminderReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Notifier.show(context, intent.getIntExtra("id", 0), Notifier.CH_REMINDERS,
                intent.getStringExtra("title"), intent.getStringExtra("text"));
    }
}
