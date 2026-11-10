package com.unihub.app.util;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Time helpers. Times are shown in 12-hour format with AM / PM. */
public class TimeUtil {
    private static final String[] DAYS = {"", "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};
    /** Class days at KUET for this app: Sunday to Thursday. */
    public static final int[] WEEK = {Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY,
            Calendar.WEDNESDAY, Calendar.THURSDAY};

    public static String dayName(int dow) { return DAYS[dow]; }
    public static String shortDay(int dow) { return DAYS[dow].substring(0, 3); }

    public static final String[] MONTHS = {"January", "February", "March", "April", "May", "June", "July",
            "August", "September", "October", "November", "December"};

    public static final String[] MONTHS_SHORT = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};

    /** Minutes from midnight to "09:00 AM". */
    public static String formatMin(int m) {
        int h = m / 60;
        return String.format(Locale.ENGLISH, "%02d:%02d %s", h % 12 == 0 ? 12 : h % 12, m % 60, h >= 12 ? "PM" : "AM");
    }

    /** "09:00 AM" back to minutes from midnight (-1 if invalid). */
    public static int minutesOf(String label) {
        try {
            String[] a = label.trim().split("[: ]");
            int h = Integer.parseInt(a[0]) % 12, m = Integer.parseInt(a[1]);
            if (a[2].equalsIgnoreCase("PM")) h += 12;
            return h * 60 + m;
        } catch (Exception e) { return -1; }
    }

    /** Dropdown options such as "09:00 AM", from..to (minutes) every step minutes. */
    public static List<String> timeLabels(int from, int to, int step) {
        List<String> l = new ArrayList<>();
        for (int m = from; m <= to; m += step) l.add(formatMin(m));
        return l;
    }

    /** Start of the given calendar date in millis, or -1 if the date does not exist (e.g. 31 February). */
    public static long dateMillis(int day, int monthIndex, int year) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.setLenient(false);
        try {
            c.set(year, monthIndex, day, 0, 0, 0);
            return c.getTimeInMillis();
        } catch (IllegalArgumentException e) { return -1; }
    }

    /** A Calendar set to the real (internet) time, not the phone clock. */
    public static Calendar calendarNow() {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(TrustedTime.now());
        return c;
    }

    public static int nowMin() {
        Calendar c = calendarNow();
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
    }

    public static long dayStart(long t) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(t);
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    /** "2026-11-12" -> "Thu, 12 Nov 2026". */
    public static String prettyDate(String key) {
        try {
            Date d = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(key);
            return new SimpleDateFormat("EEE, dd MMM yyyy", Locale.ENGLISH).format(d);
        } catch (Exception e) { return key; }
    }

    public static String dateKey(long t) { return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(t)); }

    public static String dateTime(long t) {
        return new SimpleDateFormat("EEE, dd MMM yyyy, hh:mm a", Locale.ENGLISH).format(new Date(t));
    }

    public static String dayLabel(long dayStartMillis) {
        long diff = Math.round((dayStart(TrustedTime.now()) - dayStartMillis) / 86400000.0);
        if (diff == 0) return "Today";
        if (diff == 1) return "Yesterday";
        return new SimpleDateFormat("EEE, dd MMM", Locale.ENGLISH).format(new Date(dayStartMillis));
    }

    // ---------- deadline countdown ----------
    public static String countdownText(long due) {
        long diff = due - TrustedTime.now();
        if (diff < 0) return "Past due";
        long min = diff / 60000, hr = min / 60, days = hr / 24;
        if (min < 60) return "Due in " + min + " min";
        if (hr < 24) return "Due in " + hr + " hr";
        return days == 1 ? "Due tomorrow" : "Due in " + days + " days";
    }

    public static int countdownColor(long due) {
        long diff = due - TrustedTime.now();
        if (diff < 0) return Ui.GRAY;
        if (diff < 24L * 3600000) return Ui.RED;
        if (diff < 3L * 24 * 3600000) return Ui.AMBER;
        return Ui.GREEN;
    }
}
