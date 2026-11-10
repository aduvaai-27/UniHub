package com.unihub.app.util;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Content Provider example: reads the phone's built-in Contacts provider through a ContentResolver.
 * readAll() returns names and numbers (Cursor over ContactsContract.CommonDataKinds.Phone.CONTENT_URI). Needs READ_CONTACTS,
 * which the screen asks for at runtime before calling it.
 */
public final class ContactsHelper {
    private ContactsHelper() { }

    public static class Entry {
        public final String name, phone;
        public Entry(String name, String phone) { this.name = name; this.phone = phone; }
    }

    /** All contacts that have a phone number, sorted by name. Same person and number is listed once. */
    public static List<Entry> readAll(ContentResolver cr) {
        List<Entry> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        String[] projection = { Phone.DISPLAY_NAME, Phone.NUMBER };
        try (Cursor c = cr.query(Phone.CONTENT_URI, projection, null, null, Phone.DISPLAY_NAME + " COLLATE NOCASE ASC")) {
            if (c == null) return out;
            int iName = c.getColumnIndexOrThrow(Phone.DISPLAY_NAME);
            int iNum = c.getColumnIndexOrThrow(Phone.NUMBER);
            while (c.moveToNext() && out.size() < 1000) {
                String name = c.getString(iName), num = c.getString(iNum);
                if (num == null || num.trim().isEmpty()) continue;
                if (name == null || name.trim().isEmpty()) name = num;
                String key = name.toLowerCase() + "|" + num.replaceAll("[^0-9+]", "");
                if (seen.add(key)) out.add(new Entry(name.trim(), num.trim()));
            }
        }
        return out;
    }

    /**
     * Opens the phone's own "Add contact" screen with everything filled in; the user presses Save there.
     * The full name goes into the first name field (as one piece, so titles like "Dr." are not split up).
     * Needs no permission. Empty values are left out.
     */
    public static void openAddContact(Activity a, String fullName, String phone, String email,
                                      String company, String jobTitle, String notes) {
        Intent i = new Intent(Intent.ACTION_INSERT, ContactsContract.Contacts.CONTENT_URI);
        ArrayList<ContentValues> data = new ArrayList<>();
        ContentValues name = new ContentValues();
        name.put(ContactsContract.Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE);
        name.put(StructuredName.GIVEN_NAME, fullName == null ? "" : fullName.trim());
        data.add(name);
        i.putParcelableArrayListExtra(ContactsContract.Intents.Insert.DATA, data);
        if (!isEmpty(phone)) {
            i.putExtra(ContactsContract.Intents.Insert.PHONE, phone.trim());
            i.putExtra(ContactsContract.Intents.Insert.PHONE_TYPE, Phone.TYPE_MOBILE);
        }
        if (!isEmpty(email)) {
            i.putExtra(ContactsContract.Intents.Insert.EMAIL, email.trim());
            i.putExtra(ContactsContract.Intents.Insert.EMAIL_TYPE, Email.TYPE_WORK);
        }
        if (!isEmpty(company)) i.putExtra(ContactsContract.Intents.Insert.COMPANY, company.trim());
        if (!isEmpty(jobTitle)) i.putExtra(ContactsContract.Intents.Insert.JOB_TITLE, jobTitle.trim());
        if (!isEmpty(notes)) i.putExtra(ContactsContract.Intents.Insert.NOTES, notes.trim());
        try { a.startActivity(i); }
        catch (ActivityNotFoundException ex) { Toast.makeText(a, "No Contacts app found on this phone.", Toast.LENGTH_SHORT).show(); }
    }

    private static boolean isEmpty(String s) { return s == null || s.trim().isEmpty(); }

    /**
     * Checks the phone's Contacts for this number (Cursor over a PhoneLookup Uri; number formats like
     * "+880 17..." and "017..." are matched by the provider). Needs READ_CONTACTS.
     * @return the saved contact's name, or null if the number is not saved.
     */
    public static String findNameByPhone(ContentResolver cr, String phone) {
        if (isEmpty(phone)) return null;
        Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phone.trim()));
        try (Cursor c = cr.query(uri, new String[]{ ContactsContract.PhoneLookup.DISPLAY_NAME }, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                String n = c.getString(0);
                return n == null || n.trim().isEmpty() ? phone.trim() : n.trim();
            }
        } catch (RuntimeException e) { /* invalid number or provider error: treat as not saved */ }
        return null;
    }
}
