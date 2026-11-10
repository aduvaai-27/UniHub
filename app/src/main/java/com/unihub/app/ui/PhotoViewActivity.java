package com.unihub.app.ui;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import com.google.firebase.FirebaseApp;
import com.google.firebase.firestore.ListenerRegistration;
import com.unihub.app.R;
import com.unihub.app.data.ProfilePhotos;
import com.unihub.app.databinding.ActivityPhotoViewBinding;
import com.unihub.app.util.Session;

/**
 * Full-screen profile picture of one person (yourself, a student or a teacher).
 * It is a separate screen, so the screen that opened it goes to onPause() (see the "UniHubLifecycle" log).
 *
 * Who may change the picture (the Firestore rules check the same thing):
 *   - the CR: every student and every teacher
 *   - a student: only his or her own picture
 * Before the gallery opens, the photo permission is requested (READ_MEDIA_IMAGES on Android 13+, storage on older).
 */
public class PhotoViewActivity extends BaseActivity {
    private static final String X_KEY = "key", X_NAME = "name", X_SUB = "sub";

    public static Intent intent(Context c, String key, String name, String subtitle) {
        return new Intent(c, PhotoViewActivity.class).putExtra(X_KEY, key).putExtra(X_NAME, name).putExtra(X_SUB, subtitle);
    }

    private ActivityPhotoViewBinding b;
    private String key;
    private boolean canEdit, busy;
    private Bitmap full;                                        // the big picture, once it has arrived
    private ListenerRegistration fullReg, thumbReg;

    private final ActivityResultLauncher<PickVisualMediaRequest> picker =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), this::onPicked);

    private final ActivityResultLauncher<String> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) openPicker();
                else showDeniedDialog();
            });

    private String photoPermission() {
        return Build.VERSION.SDK_INT >= 33 ? Manifest.permission.READ_MEDIA_IMAGES
                : Manifest.permission.READ_EXTERNAL_STORAGE;
    }

    /** Asks for the photo permission (if not yet allowed), then opens the gallery. */
    private void requestPhotoAccess() {
        if (ContextCompat.checkSelfPermission(this, photoPermission()) == PackageManager.PERMISSION_GRANTED) {
            openPicker();
        } else {
            permissionLauncher.launch(photoPermission());
        }
    }

    private void openPicker() {
        picker.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE).build());
    }

    private void showDeniedDialog() {
        boolean permanent = !shouldShowRequestPermissionRationale(photoPermission());
        AlertDialog.Builder d = new AlertDialog.Builder(this)
                .setTitle("Photo permission needed")
                .setMessage("Allow access to your photos to choose a profile picture.")
                .setNegativeButton("Cancel", null);
        if (permanent) {
            d.setPositiveButton("Open settings", (x, w) -> startActivity(
                    new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", getPackageName(), null))));
        } else {
            d.setPositiveButton("Try again", (x, w) -> permissionLauncher.launch(photoPermission()));
        }
        d.show();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityPhotoViewBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        key = getIntent().getStringExtra(X_KEY);
        if (key == null) { finish(); return; }
        b.tvTitle.setText(getIntent().getStringExtra(X_NAME));
        b.tvSub.setText(getIntent().getStringExtra(X_SUB));

        boolean firebase = !FirebaseApp.getApps(this).isEmpty();
        canEdit = firebase && (Session.isStaff(this) || key.equals(ProfilePhotos.studentKey(Session.roll(this))));
        b.editBar.setVisibility(canEdit ? View.VISIBLE : View.GONE);

        b.btnClose.setOnClickListener(v -> finish());
        b.frame.setOnClickListener(v -> finish());
        b.btnChange.setOnClickListener(v -> {
            if (!requireOnline()) return;
            requestPhotoAccess();
        });
        b.btnRemove.setOnClickListener(v -> confirmRemove());
        render();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (key == null) return;
        thumbReg = ProfilePhotos.watchThumb(this, key, this::render);                // shows at once, then the big one replaces it
        fullReg = ProfilePhotos.watchFull(this, key, bmp -> {
            if (isDestroyed()) return;
            full = bmp;
            render();
        });
    }

    @Override
    protected void onStop() {
        if (thumbReg != null) { thumbReg.remove(); thumbReg = null; }
        if (fullReg != null) { fullReg.remove(); fullReg = null; }
        super.onStop();
    }

    @Override protected View offlineBar() { return b == null ? null : b.tvOffline; }

    @Override protected void onConnectivityChanged(boolean on) { refreshButtons(); }

    // ---------------- show ----------------
    private void render() {
        Bitmap img = full != null ? full : ProfilePhotos.thumb(key);
        if (img != null) b.photo.setImageBitmap(img);
        else b.photo.setImageResource(R.drawable.default_avatar);
        b.btnChange.setText(img != null ? "Change photo" : "Add photo");
        b.btnRemove.setVisibility(img != null ? View.VISIBLE : View.GONE);
        refreshButtons();
    }

    private void refreshButtons() {
        boolean enabled = online && !busy;
        b.btnChange.setEnabled(enabled);
        b.btnRemove.setEnabled(enabled);
        b.btnRemove.setAlpha(enabled ? 1f : 0.5f);
    }

    private void setBusy(boolean on) {
        busy = on;
        b.progress.setVisibility(on ? View.VISIBLE : View.GONE);
        refreshButtons();
    }

    // ---------------- change / remove ----------------
    private void onPicked(Uri uri) {
        if (uri == null || !requireOnline()) return;                // the picker was closed without a choice
        setBusy(true);
        ProfilePhotos.save(this, key, uri, error -> {
            if (isDestroyed()) return;
            setBusy(false);
            Toast.makeText(this, error == null ? "Profile picture saved." : error,
                    error == null ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG).show();
        });
    }

    private void confirmRemove() {
        if (!requireOnline()) return;
        new AlertDialog.Builder(this).setTitle("Remove this profile picture?")
                .setMessage("The default picture will be shown instead.")
                .setPositiveButton("Remove", (d, w) -> {
                    if (!requireOnline()) return;
                    setBusy(true);
                    ProfilePhotos.remove(this, key, error -> {
                        if (isDestroyed()) return;
                        setBusy(false);
                        full = null;
                        render();
                        if (error != null) Toast.makeText(this, error, Toast.LENGTH_LONG).show();
                    });
                })
                .setNegativeButton("Cancel", null).show();
    }
}
