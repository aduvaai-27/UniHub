package com.unihub.app.data;

import com.google.firebase.firestore.DocumentId;

/** Firestore "holidays" document: a holiday (or vacation, the same thing) set by the CR, for one date or a range of dates. */
public class CustomHoliday {
    @DocumentId public String id;
    public String purpose;              // why (Eid, winter break, vacation, ...)
    public String startDate, endDate;   // "yyyy-MM-dd"; endDate == startDate for a single date
    public String by;
    public long createdAt;

    public CustomHoliday() { }

    public String endOf() { return endDate == null || endDate.isEmpty() ? startDate : endDate; }

    public boolean isRange() { return startDate != null && !startDate.equals(endOf()); }

    /** Does this holiday / vacation cover the given date (yyyy-MM-dd)? */
    public boolean covers(String key) {
        return startDate != null && key != null && key.compareTo(startDate) >= 0 && key.compareTo(endOf()) <= 0;
    }

    /** Does it touch any date between two keys (inclusive)? */
    public boolean overlaps(String fromKey, String toKey) {
        return startDate != null && startDate.compareTo(toKey) <= 0 && endOf().compareTo(fromKey) >= 0;
    }

    public String label() { return "Holiday"; }
}
