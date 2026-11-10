package com.unihub.app.util;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.Settings;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

/**
 * Contacts (Content Provider) access for a screen: asks for the READ_CONTACTS runtime permission and then
 *  - saveContact(): checks the phone's Contacts for the number first. If it is already saved, says so;
 *                   otherwise opens the phone's "Add contact" screen with everything filled in.
 *                   If the permission is refused, nothing is opened: the user gets the permission message instead.
 *  - withReadPermission(): runs any other Contacts reading (for example picking a number) once allowed.
 * Create it as a field of the activity (it registers the permission launcher, which must happen before the screen starts).
 */
public class ContactsAccess {
    private final AppCompatActivity a;
    private Runnable onGranted, onDenied;
    private final ActivityResultLauncher<String> launcher;

    public ContactsAccess(AppCompatActivity activity) {
        this.a = activity;
        launcher = a.registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            Runnable ok = onGranted, no = onDenied;
            onGranted = null; onDenied = null;
            if (granted) { if (ok != null) ok.run(); }
            else if (no != null) no.run();
        });
    }

    private boolean hasRead() {
        return ContextCompat.checkSelfPermission(a, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED;
    }

    /** Runs ok now if READ_CONTACTS is already granted, otherwise asks the user first (denied runs when the user says no). */
    public void withReadPermission(Runnable ok, Runnable denied) {
        if (hasRead()) { ok.run(); return; }
        onGranted = ok; onDenied = denied;
        launcher.launch(Manifest.permission.READ_CONTACTS);
    }

    /** Message after the user refused: a short toast, or a Settings shortcut when Android will not ask again. */
    public void explainDenied() {
        if (a.shouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS)) {
            Toast.makeText(a, "Contacts permission is needed for this feature.", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(a)
                .setTitle("Contacts permission needed")
                .setMessage("Allow Contacts access in Settings to use this feature.")
                .setPositiveButton("Open Settings", (d, w) -> a.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", a.getPackageName(), null))))
                .setNegativeButton("Cancel", null).show();
    }

    /** Save to Contacts: look the number up first; if it is saved already, say so, otherwise open the Add contact screen. */
    public void saveContact(String fullName, String phone, String email, String company, String jobTitle, String notes) {
        Runnable open = () -> ContactsHelper.openAddContact(a, fullName, phone, email, company, jobTitle, notes);
        if (phone == null || phone.trim().isEmpty()) { open.run(); return; }            // nothing to check
        withReadPermission(() -> {
            String saved = ContactsHelper.findNameByPhone(a.getContentResolver(), phone);
            if (saved == null) { open.run(); return; }
            new AlertDialog.Builder(a)
                    .setTitle("Already saved")
                    .setMessage(phone.trim() + " is already saved in your Contacts as \"" + saved + "\".")
                    .setPositiveButton("OK", null).show();
        }, this::explainDenied);              // permission refused: do not open the Add contact screen
    }
}
