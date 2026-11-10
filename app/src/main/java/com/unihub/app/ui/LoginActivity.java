package com.unihub.app.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.unihub.app.databinding.ActivityLoginBinding;
import com.unihub.app.util.RollValidator;
import com.unihub.app.util.Session;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

/**
 * One sign-in for everyone: roll number + password.
 * At registration a student chooses the lab group (A1 / A2 / B1 / B2). The Class Representative (CR) ticks
 * "I am the Class Representative" and enters the invitation code.
 */
public class LoginActivity extends BaseActivity {
    // Only the CR should know this code. Change it before sharing the app.
    private static final String CR_CODE = "UNI-CR-2026";
    private static final String ROLL_DOMAIN = "@student.unihub.app";

    private ActivityLoginBinding b;
    private FirebaseAuth auth;
    private FirebaseFirestore fs;
    private boolean firebaseReady, register = false, busy = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        firebaseReady = !FirebaseApp.getApps(this).isEmpty();
        if (firebaseReady) { auth = FirebaseAuth.getInstance(); fs = FirebaseFirestore.getInstance(); }
        if (Session.isLoggedIn(this)) { goMain(); return; }

        b.btnTheme.setImageResource(com.unihub.app.util.ThemePrefs.isDark(this) ? com.unihub.app.R.drawable.ic_theme_light : com.unihub.app.R.drawable.ic_theme_dark);
        b.btnTheme.setOnClickListener(v -> com.unihub.app.util.ThemePrefs.toggle(this));   // the choice stays after login

        com.unihub.app.util.Ui.dropdown(this, b.acGroup, java.util.Arrays.asList("A1", "A2", "B1", "B2"), null);
        b.tvSwitch.setOnClickListener(v -> { register = !register; render(); });
        b.cbCr.setOnCheckedChangeListener((btn, on) -> b.layoutCode.setVisibility(on ? View.VISIBLE : View.GONE));
        b.btnSubmit.setOnClickListener(v -> submit());
        render();
        if (!firebaseReady) {
            showError("Offline mode: accounts are saved on this phone only. Add google-services.json to connect to Firebase.");
        }
    }

    private void render() {
        b.layoutId.setError(null); b.layoutPass.setError(null);
        if (firebaseReady) b.tvStatus.setVisibility(View.GONE);
        b.tvFormTitle.setText(register ? "Student Registration" : "Sign In");
        b.layoutName.setVisibility(register ? View.VISIBLE : View.GONE);
        b.layoutGroup.setVisibility(register ? View.VISIBLE : View.GONE);
        b.crExtras.setVisibility(register ? View.VISIBLE : View.GONE);
        refreshSubmit();
        b.tvSwitch.setText(register ? "Already registered? Sign in" : "Not registered? Create an account");
    }

    @Override protected android.view.View offlineBar() { return b == null ? null : b.tvOffline; }

    /** Offline: signing in / registering with Firebase is blocked. (Local accounts, used without Firebase, still work.) */
    @Override protected void onConnectivityChanged(boolean on) { refreshSubmit(); }

    private void refreshSubmit() {
        boolean blocked = firebaseReady && !online;
        b.btnSubmit.setEnabled(!busy && !blocked);
        b.btnSubmit.setText(busy ? "Please wait..." : blocked ? "Offline - connect to continue"
                : register ? "Create Account" : "Sign In");
    }

    private void submit() {
        if (firebaseReady && !requireOnline()) return;
        String roll = b.etId.getText().toString().trim();
        String pass = b.etPassword.getText().toString();
        String name = b.etName.getText().toString().trim();
        String group = b.acGroup.getText().toString().trim();
        b.layoutId.setError(null); b.layoutPass.setError(null); b.layoutName.setError(null);
        b.layoutCode.setError(null); b.layoutGroup.setError(null);

        if (!RollValidator.isValid(roll)) {
            b.layoutId.setError("Roll must be between 2307001 and 2307123");
            return;
        }
        if (pass.length() < 6) { b.layoutPass.setError("At least 6 characters"); return; }
        if (register && name.isEmpty()) { b.layoutName.setError("Enter your name"); return; }
        if (register && group.isEmpty()) { b.layoutGroup.setError("Select your group"); return; }
        if (register && b.cbCr.isChecked() && !b.etCode.getText().toString().trim().equals(CR_CODE)) {
            b.layoutCode.setError("Wrong CR invitation code"); return;
        }

        String email = roll + ROLL_DOMAIN;
        setBusy(true);
        if (!firebaseReady) {
            if (register) localRegister(email, pass, name, roll, group); else localLogin(email, pass, roll);
        } else if (register) doRegister(email, pass, name, roll, group);
        else doLogin(email, pass, roll);
    }

    private String roleChoice() { return b.cbCr.isChecked() ? Session.CR : Session.STUDENT; }

    // ---------------- Firebase mode ----------------
    private void doRegister(String email, String pass, String name, String roll, String group) {
        auth.createUserWithEmailAndPassword(email, pass).addOnSuccessListener(r -> {
            String role = roleChoice();
            Map<String, Object> doc = new HashMap<>();
            doc.put("name", name); doc.put("id", roll); doc.put("role", role); doc.put("group", group);
            fs.collection("users").document(r.getUser().getUid()).set(doc)
                    .addOnSuccessListener(x -> { Session.save(this, name, role, roll, group); goMain(); })
                    .addOnFailureListener(this::fail);
        }).addOnFailureListener(e -> {
            // One roll = one account (the roll is the sign-in id), so a second sign-up with it is refused.
            if (e instanceof FirebaseAuthUserCollisionException) alreadyRegistered(roll);
            else fail(e);
        });
    }

    private void doLogin(String email, String pass, String roll) {
        auth.signInWithEmailAndPassword(email, pass).addOnSuccessListener(r ->
                fs.collection("users").document(r.getUser().getUid()).get().addOnSuccessListener((DocumentSnapshot d) -> {
                    String role = d.exists() && Session.CR.equals(d.getString("role")) ? Session.CR : Session.STUDENT;
                    String name = d.exists() && d.getString("name") != null ? d.getString("name") : roll;
                    String group = d.exists() && d.getString("group") != null ? d.getString("group") : "";
                    Session.save(this, name, role, roll, group);
                    goMain();
                }).addOnFailureListener(this::fail)
        ).addOnFailureListener(this::fail);
    }

    // ---------------- Local (offline) mode ----------------
    private SharedPreferences accounts() { return getSharedPreferences("unihub_local_accounts", MODE_PRIVATE); }

    private void localRegister(String email, String pass, String name, String roll, String group) {
        if (accounts().contains(email)) { alreadyRegistered(roll); return; }
        String role = roleChoice();
        accounts().edit().putString(email, hash(pass) + "|" + role + "|" + group + "|" + name).apply();
        Session.save(this, name, role, roll, group);
        goMain();
    }

    private void localLogin(String email, String pass, String roll) {
        String saved = accounts().getString(email, null);
        if (saved == null) { setBusy(false); showError("No account found. Please create an account first."); return; }
        String[] p = saved.split("\\|", 4);
        if (p.length < 4 || !p[0].equals(hash(pass))) { setBusy(false); showError("Wrong password."); return; }
        Session.save(this, p[3], Session.CR.equals(p[1]) ? Session.CR : Session.STUDENT, roll, p[2]);
        goMain();
    }

    private String hash(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte x : d) sb.append(String.format("%02x", x));
            return sb.toString();
        } catch (Exception e) { return s; }
    }

    // ---------------- helpers ----------------
    /** Roll already has an account: toast, mark the roll field and tell the user to sign in instead. */
    private void alreadyRegistered(String roll) {
        setBusy(false);
        String msg = "Roll " + roll + " already has an account. Please sign in.";
        android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_LONG).show();
        b.layoutId.setError("This roll already has an account");
        showError(msg);
    }

    private void fail(Exception e) { setBusy(false); showError(com.unihub.app.util.Ui.firebaseError(e)); }

    private void showError(String m) { b.tvStatus.setText(m); b.tvStatus.setVisibility(View.VISIBLE); }

    private void setBusy(boolean busy) {
        this.busy = busy;
        refreshSubmit();
    }

    private void goMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
