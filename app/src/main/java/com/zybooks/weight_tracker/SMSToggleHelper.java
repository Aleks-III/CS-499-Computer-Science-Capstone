package com.zybooks.weight_tracker;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;

import com.google.android.material.switchmaterial.SwitchMaterial;

/**
 * Shared SMS toggle behavior for all screens.
 * <p>
 * This keeps the per-user notification preference logic in one place,
 * which reduces duplicated code and helps prevent inconsistent behavior.
 */
public final class SMSToggleHelper {

    private SMSToggleHelper() {
        // Utility class
    }

    /**
     * Initializes the toggle from saved preferences and wires up the enable/disable behavior.
     *
     * @param activity current activity
     * @param smsToggle toggle shown on screen
     * @param userId    active user ID used for per-user preferences
     */
    public static void setupSmsToggle(Activity activity, SwitchMaterial smsToggle, int userId) {
        SharedPreferences prefs = getUserSmsPrefs(activity, userId);

        // Restore the saved toggle state for this specific user.
        smsToggle.setChecked(
                prefs.getBoolean(SMSPermissionActivity.SMS_ENABLED, false)
        );

        smsToggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                // Only request SMS permission when the user explicitly opts in.
                Intent intent = new Intent(activity, SMSPermissionActivity.class);
                intent.putExtra("USER_ID", userId);
                activity.startActivity(intent);
            } else {
                // Save the opt-out immediately for this user.
                prefs.edit()
                        .putBoolean(SMSPermissionActivity.SMS_ENABLED, false)
                        .apply();
            }
        });
    }

    /**
     * Refreshes the toggle after returning from the permission screen.
     * This keeps the UI aligned with the stored preference.
     */
    public static void syncSmsToggle(Activity activity, SwitchMaterial smsToggle, int userId) {
        smsToggle.setChecked(
                getUserSmsPrefs(activity, userId)
                        .getBoolean(SMSPermissionActivity.SMS_ENABLED, false)
        );
    }

    private static SharedPreferences getUserSmsPrefs(Activity activity, int userId) {
        return activity.getSharedPreferences(
                SMSPermissionActivity.PREFS_NAME + "_user_" + userId,
                Activity.MODE_PRIVATE
        );
    }
}