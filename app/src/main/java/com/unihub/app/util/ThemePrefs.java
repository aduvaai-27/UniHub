package com.unihub.app.util;

import android.content.Context;
import android.content.res.Configuration;
import androidx.appcompat.app.AppCompatDelegate;

/**
 * Light / dark mode, switched by every user (student and CR) with the icon on the home screen: one tap = dark,
 * the next tap = light. The choice is kept on the phone in its own preferences, so it survives logout and restarts.
 */
public final class ThemePrefs {
    private ThemePrefs() { }

    private static final String FILE = "unihub_theme", KEY = "dark";

    private static boolean saved(Context c) { return c.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY, false); }

    /** Applies the saved mode. Called once from UniHubApp before any screen is created. Default: light. */
    public static void apply(Context c) {
        AppCompatDelegate.setDefaultNightMode(saved(c) ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
    }

    /** True when the screen is showing dark mode right now. */
    public static boolean isDark(Context c) {
        return (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    /** Switches dark <-> light; the screen is recreated in the new mode at once. */
    public static void toggle(Context c) {
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY, !isDark(c)).apply();
        apply(c);
    }
}
