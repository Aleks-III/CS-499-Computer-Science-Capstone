package com.zybooks.weight_tracker;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.text.Editable;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Handles daily weight entry and goal evaluation.
 * <p>
 * This activity supports both:
 * - creating a new weight entry
 * - editing an existing entry (when ENTRY_ID is provided)
 */
public class DailyWeightActivity extends AppCompatActivity {

    // =====================================================
    // Fields
    // =====================================================

    private DatabaseHelper databaseHelper;
    private int userId;
    private int entryId;
    private String editingDate;

    private TextInputEditText weightField;
    private SwitchMaterial smsToggle;

    // =====================================================
    // Activity lifecycle
    // =====================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_daily_weight);

        databaseHelper = new DatabaseHelper(this);

        userId = getIntent().getIntExtra("USER_ID", -1);
        entryId = getIntent().getIntExtra("ENTRY_ID", -1);

        weightField = findViewById(R.id.weightField);

        MaterialButton saveButton = findViewById(R.id.submitWeightButton);
        MaterialButton logoutButton = findViewById(R.id.logoutButton);
        smsToggle = findViewById(R.id.smsToggle);
        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);

        // Shared setup keeps navigation consistent across the app.
        NavigationHelper.setupBottomNav(this, bottomNav, R.id.nav_new_entry, userId);

        // Shared helper restores and manages the SMS opt-in state for this user.
        SMSToggleHelper.setupSmsToggle(this, smsToggle, userId);

        // Pre-fill the field when editing an existing entry.
        if (entryId != -1) {

            WeightEntry entry = databaseHelper.getWeightEntryById(userId, entryId);
            if (entry == null) {
                // Entry might have been deleted; exit gracefully
                finish();
                return;
            }

            weightField.setText(String.valueOf(entry.getWeight()));
            editingDate = entry.getDate();

            // IMPORTANT: if you have a date field / date button, also set it here
            // dateField.setText(entry.getDate());
        }

        saveButton.setOnClickListener(v -> saveWeight());
        logoutButton.setOnClickListener(v -> logout());
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Re-sync the toggle after returning from the permission screen.
        SMSToggleHelper.syncSmsToggle(this, smsToggle, userId);
    }

    // =====================================================
    // Persistence helpers
    // =====================================================

    /**
     * SMS preferences are stored per-user to prevent notification
     * settings from carrying over when multiple users log in.
     */
    private SharedPreferences getUserSmsPrefs() {
        return getSharedPreferences(
                SMSPermissionActivity.PREFS_NAME + "_user_" + userId,
                MODE_PRIVATE
        );
    }

    /**
     * Returns the current weight text from the input field after trimming whitespace.
     */
    private String getWeightInput() {
        Editable editable = weightField.getText();
        return editable != null ? editable.toString().trim() : "";
    }

    /**
     * Validates and parses the user's entered weight value.
     *
     * @param weightInput the raw text input
     * @return the parsed weight, or null if the input is invalid
     */
    private Double parseWeight(String weightInput) {
        if (weightInput.isEmpty()) {
            Toast.makeText(this, "Please enter a weight", Toast.LENGTH_SHORT).show();
            return null;
        }

        try {
            double weight = Double.parseDouble(weightInput);
            if (weight <= 0) {
                Toast.makeText(this, "Weight must be greater than zero", Toast.LENGTH_SHORT).show();
                return null;
            }
            return weight;
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid weight value", Toast.LENGTH_SHORT).show();
            return null;
        }
    }

    /**
     * Returns today's date in the format used by the database.
     */
    private String getTodayDate() {
        return new SimpleDateFormat("MM/dd/yyyy", Locale.getDefault())
                .format(new Date());
    }

    // =====================================================
    // Weight saving logic
    // =====================================================

    private void saveWeight() {
        String weightInput = getWeightInput();
        Double weight = parseWeight(weightInput);

        if (weight == null) {
            return;
        }

        boolean success;
        if (entryId == -1) {
            String date = getTodayDate();
            success = saveNewWeight(date, weight);
        } else {
            // Preserve original date unless user explicitly changes it
            success = updateExistingWeight(editingDate, weight);
        }

        if (!success) {
            Toast.makeText(this, "Failed to save weight", Toast.LENGTH_SHORT).show();
            return;
        }

        // If goal is reached, the dialog will handle navigation
        if (checkAndHandleGoalReached(weight)) {
            return;
        }

        // Otherwise, go back to history immediately
        navigateToHistory();
    }

    /**
     * Saves a new weight record for the current user.
     */
    private boolean saveNewWeight(String date, double weight) {
        return databaseHelper.insertWeight(date, weight, userId);
    }

    /**
     * Updates an existing weight record.
     */
    private boolean updateExistingWeight(String date, double weight) {
        return databaseHelper.updateWeight(entryId, date, weight);
    }

    /**
     * Navigates back to the history screen after a successful save.
     */
    private void navigateToHistory() {
        Intent intent = new Intent(this, HistoryActivity.class);
        intent.putExtra("USER_ID", userId);
        startActivity(intent);
        finish();
    }

    // =====================================================
    // Goal evaluation logic
    // =====================================================

    /**
     * Determines whether the user's goal was crossed between
     * the previous entry and the current one.
     * <p>
     * This avoids false positives when users edit past entries
     * or repeatedly log weights beyond the goal threshold.
     */
    private boolean isGoalReached(double currentWeight) {
        double goalWeight = databaseHelper.getGoalWeight(userId);
        if (goalWeight <= 0) return false;

        Double previousWeight = databaseHelper.getPreviousWeight(userId);

        // Without a previous value, a crossing cannot be detected
        if (previousWeight == null) return false;

        // Losing weight: crossed from above → below
        if (previousWeight > goalWeight && currentWeight <= goalWeight) {
            return true;
        }

        // Gaining weight: crossed from below → above
        return previousWeight < goalWeight && currentWeight >= goalWeight;
    }

    private boolean checkAndHandleGoalReached(double weight) {
        SharedPreferences prefs = getUserSmsPrefs();

        boolean smsEnabled = prefs.getBoolean(SMSPermissionActivity.SMS_ENABLED, false);
        if (!smsEnabled) return false;

        if (isGoalReached(weight)) {

            try {
                SmsManager smsManager = SmsManager.getDefault();
                smsManager.sendTextMessage(
                        "+15551234567",
                        null,
                        "Great job! You've reached your goal weight. Set a new goal in the app anytime.",
                        null,
                        null
                );
            } catch (SecurityException e) {
                Toast.makeText(this,
                        "Unable to send SMS: permission denied",
                        Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this,
                        "Unable to send SMS",
                        Toast.LENGTH_SHORT).show();
            }

            showGoalReachedDialog();
            return true;
        }

        return false;
    }

    private void showGoalReachedDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Goal Reached!")
                .setMessage("Congratulations! Would you like to set a new goal weight?")
                .setPositiveButton("Yes, set new goal", (dialog, which) -> {
                    goToGoalWeightScreen();
                    finish();
                })
                .setNegativeButton("No, turn off alerts", (dialog, which) -> {
                    disableSmsNotifications();
                    navigateToHistory();
                })
                .setCancelable(false)
                .show();
    }

    private void goToGoalWeightScreen() {
        Intent intent = new Intent(this, GoalWeightActivity.class);
        intent.putExtra("USER_ID", userId);
        startActivity(intent);
    }

    private void disableSmsNotifications() {
        getUserSmsPrefs().edit()
                .putBoolean(SMSPermissionActivity.SMS_ENABLED, false)
                .apply();

        Toast.makeText(this,
                "SMS notifications turned off.",
                Toast.LENGTH_SHORT).show();
    }

    // =====================================================
    // Session management
    // =====================================================

    private void logout() {
        // Clear stored login state so the next launch
        // requires authentication
        getSharedPreferences("UserPrefs", MODE_PRIVATE)
                .edit()
                .clear()
                .apply();

        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }
}