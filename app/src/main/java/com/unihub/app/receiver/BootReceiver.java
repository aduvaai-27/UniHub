package com.unihub.app.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import androidx.core.content.ContextCompat;
import com.unihub.app.service.NoticeService;
import com.unihub.app.util.Session;

/** Restarts the background update service after the phone restarts (if someone is logged in). */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) && Session.isLoggedIn(context)) {
            ContextCompat.startForegroundService(context, new Intent(context, NoticeService.class));
        }
    }
}
