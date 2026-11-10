package com.unihub.app;

import android.app.Application;
import com.unihub.app.util.Notifier;
import com.unihub.app.util.PublicHolidays;
import com.unihub.app.util.TrustedTime;
import java.util.TimeZone;

/** Application class: runs once at process start. */
public class UniHubApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        com.unihub.app.util.ThemePrefs.apply(this);        // light / dark mode chosen by the user (default light)
        // KUET classes run on Bangladesh time (the Firestore rules use UTC+6 too), so a changed phone time zone
        // can never move a class to another day.
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Dhaka"));
        Notifier.createChannels(this);
        TrustedTime.init(this);                 // load the last internet time (valid until the phone reboots)
        TrustedTime.sync(this, null);           // and refresh it from the time API when online
        PublicHolidays.init(this);              // government holidays: classes on those dates are cancelled automatically
    }
}
