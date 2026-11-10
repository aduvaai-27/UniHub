package com.unihub.app.util;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

/**
 * Tells a screen when the phone goes offline or comes back online, so it can block the features that need internet.
 *
 * Two sources are used together:
 *  1. System broadcast ACTION_AIRPLANE_MODE_CHANGED (dynamic Broadcast Receiver): airplane mode switched on / off.
 *  2. ConnectivityManager network callback: Wi-Fi or mobile data switched off, signal lost, network without internet.
 *
 * Usage: create it in onStart(), call start(); call stop() in onStop(). The listener always runs on the main thread
 * and is called once with the current state when start() is called, then only when the state changes.
 */
public class OfflineWatcher {
    public interface Listener { void onChanged(boolean online); }

    private final Context ctx;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private ConnectivityManager.NetworkCallback callback;
    private boolean running = false;
    private Boolean last = null;                       // last state given to the listener

    // 1) System broadcast: airplane mode
    private final BroadcastReceiver airplaneReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean airplaneOn = intent.getBooleanExtra("state", false);
            // airplane ON  -> offline right now. airplane OFF -> wait for the network callback to say "online".
            deliver(airplaneOn ? false : isOnline(context));
        }
    };

    public OfflineWatcher(Context context, Listener listener) {
        this.ctx = context;
        this.listener = listener;
    }

    public void start() {
        if (running) return;
        running = true;
        last = null;
        ContextCompat.registerReceiver(ctx, airplaneReceiver,
                new IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED);

        // 2) Network callback: Wi-Fi / mobile data on-off
        ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            callback = new ConnectivityManager.NetworkCallback() {
                @Override public void onCapabilitiesChanged(@NonNull Network n, @NonNull NetworkCapabilities caps) {
                    post(hasInternet(caps));
                }
                @Override public void onLost(@NonNull Network n) { post(false); }
            };
            cm.registerDefaultNetworkCallback(callback);
        }
        deliver(isOnline(ctx));                        // current state right away
    }

    public void stop() {
        if (!running) return;
        running = false;
        try { ctx.unregisterReceiver(airplaneReceiver); } catch (IllegalArgumentException ignored) { }
        ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null && callback != null) {
            try { cm.unregisterNetworkCallback(callback); } catch (IllegalArgumentException ignored) { }
        }
        callback = null;
    }

    private void post(boolean online) {
        main.post(() -> { if (running) deliver(online); });
    }

    private void deliver(boolean online) {
        if (last != null && last == online) return;    // nothing changed
        last = online;
        listener.onChanged(online);
    }

    private static boolean hasInternet(NetworkCapabilities caps) {
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }

    /** For code outside an Activity: true when online, otherwise shows the "you are offline" message and returns false. */
    public static boolean requireOnline(Context c) {
        if (isOnline(c)) return true;
        android.widget.Toast.makeText(c, "Failed: you are offline. This needs an internet connection.", android.widget.Toast.LENGTH_SHORT).show();
        return false;
    }

    /** One-time check: is there a working internet connection right now? */
    public static boolean isOnline(Context c) {
        ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        Network n = cm.getActiveNetwork();
        if (n == null) return false;
        NetworkCapabilities caps = cm.getNetworkCapabilities(n);
        return caps != null && hasInternet(caps);
    }
}
