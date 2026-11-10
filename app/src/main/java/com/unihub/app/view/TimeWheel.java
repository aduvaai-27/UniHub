package com.unihub.app.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import java.util.Locale;

/** Custom View: three scroll wheels for time (hour, minute, AM/PM). */
public class TimeWheel extends LinearLayout {
    private final NumberPicker hour, minute, ampm;

    public TimeWheel(Context c, AttributeSet a) {
        super(c, a);
        setOrientation(HORIZONTAL);
        setGravity(android.view.Gravity.CENTER);
        hour = wheel(c, 1, 12);
        minute = wheel(c, 0, 59);
        ampm = wheel(c, 0, 1);
        hour.setFormatter(v -> String.format(Locale.ENGLISH, "%02d", v));
        minute.setFormatter(v -> String.format(Locale.ENGLISH, "%02d", v));
        ampm.setDisplayedValues(new String[]{"AM", "PM"});
        setMinutes(9 * 60);
    }

    private NumberPicker wheel(Context c, int min, int max) {
        NumberPicker p = new NumberPicker(c);
        p.setMinValue(min);
        p.setMaxValue(max);
        p.setWrapSelectorWheel(true);
        p.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);   // scroll only, no keyboard
        LayoutParams lp = new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        addView(p, lp);
        return p;
    }

    /** Selected time as minutes from midnight. */
    public int getMinutes() {
        int h = hour.getValue() % 12;
        if (ampm.getValue() == 1) h += 12;
        return h * 60 + minute.getValue();
    }

    public void setMinutes(int m) {
        int h = m / 60;
        ampm.setValue(h >= 12 ? 1 : 0);
        hour.setValue(h % 12 == 0 ? 12 : h % 12);
        minute.setValue(m % 60);
    }
}
