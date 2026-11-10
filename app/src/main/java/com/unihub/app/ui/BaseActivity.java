package com.unihub.app.ui;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.unihub.app.util.OfflineWatcher;
import com.unihub.app.util.TrustedTime;

/**
 * Logs every lifecycle callback (filter Logcat by tag "UniHubLifecycle") and gives every screen the same
 * "you are offline" behaviour.
 *
 * A screen that needs internet only has to override two methods:
 *   offlineBar()            -> the red bar (TextView) of that screen. The base class shows / hides it.
 *   onConnectivityChanged() -> disable or enable the buttons that need internet.
 * The base class starts an OfflineWatcher (airplane mode broadcast + network callback) in onStart() and stops it in onStop().
 */
public abstract class BaseActivity extends AppCompatActivity {
    /** True while the phone has a working internet connection. Kept up to date while the screen is visible. */
    protected boolean online = true;
    private OfflineWatcher offlineWatcher;

    private void log(String cb) { Log.d("UniHubLifecycle", getClass().getSimpleName() + " -> " + cb); }

    /** The offline bar of this screen, or null if the screen does not use the internet. */
    protected View offlineBar() { return null; }

    /** Called when the phone goes offline / online, and once when the screen becomes visible. */
    protected void onConnectivityChanged(boolean online) { }

    /** Use at the start of an action that needs internet: shows a message and returns false while offline. */
    protected boolean requireOnline() {
        if (OfflineWatcher.isOnline(this)) return true;
        Toast.makeText(this, "Failed: you are offline. This needs an internet connection.", Toast.LENGTH_SHORT).show();
        return false;
    }

    /**
     * A button that needs internet. Offline it is only dimmed, not disabled, so a tap still reaches the
     * action, which calls requireOnline() and shows "Failed: you are offline".
     */
    protected void setNeedsInternet(View v, boolean on) {
        if (v == null) return;
        v.setEnabled(true);
        v.setAlpha(on ? 1f : 0.5f);
    }

    /**
     * Like requireOnline(), and the real time must also be known. After a reboot the phone clock is not trusted,
     * so an action that depends on the date or the class time waits until the internet time is read.
     */
    protected boolean requireRealTime() {
        if (!requireOnline()) return false;
        if (!TrustedTime.isVerified()) {
            TrustedTime.sync(this, null);
            Toast.makeText(this, "Checking the real time. Please try again in a moment.", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    @Override protected void onCreate(@Nullable Bundle s) {
        super.onCreate(s);
        online = OfflineWatcher.isOnline(this);
        log("onCreate");
    }

    @Override protected void onStart() {
        super.onStart();
        log("onStart");
        TrustedTime.syncIfNeeded(this, null);            // real time from the time API (only when it is out of date)
        final View bar = offlineBar();
        if (bar == null) return;
        offlineWatcher = new OfflineWatcher(this, on -> {
            online = on;
            if (on) TrustedTime.syncIfNeeded(this, null);   // internet is back: refresh the trusted clock
            bar.setVisibility(on ? View.GONE : View.VISIBLE);
            onConnectivityChanged(on);
        });
        offlineWatcher.start();
    }

    @Override protected void onResume()  { super.onResume();  log("onResume"); }
    @Override protected void onPause()   { super.onPause();   log("onPause"); }

    @Override protected void onStop() {
        if (offlineWatcher != null) { offlineWatcher.stop(); offlineWatcher = null; }
        super.onStop();
        log("onStop");
    }

    @Override protected void onRestart() { super.onRestart(); log("onRestart"); }
    @Override protected void onDestroy() { super.onDestroy(); log("onDestroy"); }
}
