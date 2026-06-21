package com.zybooks.weight_tracker;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/**
 * Handles enabling or disabling SMS notifications for a specific user.
 * <p>
 * This screen exists so the user explicitly opts into SMS messaging
 * and Android permission flow, rather than enabling notifications implicitly.
 */
public class SMSPermissionActivity extends AppCompatActivity {

    /* ---------- SharedPreferences keys ---------- */
    public static final String PREFS_NAME = "WeightTrackerPrefs";
    public static final String SMS_ENABLED = "sms_enabled";

    /* ---------- Permission handling ---------- */
    private static final int SMS_PERMISSION_CODE = 100;

    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sms_permission);

        /*
         * SMS preferences are stored per-user so that enabling notifications
         * for one account does not affect other users on the same device.
         */
        int userId = getIntent().getIntExtra("USER_ID", -1);
        if (userId == -1) {
            Toast.makeText(this, "Missing user ID for SMS settings", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        prefs = getSharedPreferences(
                PREFS_NAME + "_user_" + userId,
                MODE_PRIVATE
        );

        findViewById(R.id.allowSmsButton)
                .setOnClickListener(v -> requestSmsPermission());

        findViewById(R.id.denySmsButton)
                .setOnClickListener(v -> disableSms());
    }

    /**
     * Requests Android SMS permission if needed.
     * <p>
     * Permission is requested only after the user explicitly opts in,
     * which aligns with Android permission best practices.
     */
    private void requestSmsPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.SEND_SMS},
                    SMS_PERMISSION_CODE
            );
        } else {
            enableSms();
        }
    }

    /**
     * Enables SMS notifications for this user.
     * <p>
     * This method is called only after permission is granted
     * to avoid enabling a feature the OS would block.
     */
    private void enableSms() {
        prefs.edit()
                .putBoolean(SMS_ENABLED, true)
                .apply();

        Toast.makeText(this, "SMS notifications enabled", Toast.LENGTH_SHORT).show();
        finish();
    }

    /**
     * Disables SMS notifications for this user.
     * <p>
     * This allows users to opt out without revoking Android permissions.
     */
    private void disableSms() {
        prefs.edit()
                .putBoolean(SMS_ENABLED, false)
                .apply();

        finish();
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults) {

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == SMS_PERMISSION_CODE &&
                grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {

            enableSms();
        } else {
            disableSms();
        }
    }
}