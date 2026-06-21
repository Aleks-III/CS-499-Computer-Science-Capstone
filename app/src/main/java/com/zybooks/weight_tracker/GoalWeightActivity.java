package com.zybooks.weight_tracker;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;

/**
 * Allows the user to define or update a single active goal weight.
 * <p>
 * This screen also controls whether SMS notifications are enabled,
 * but permission is only requested if the user explicitly opts in.
 */
public class GoalWeightActivity extends AppCompatActivity {

    // =====================================================
    // Fields
    // =====================================================

    private DatabaseHelper databaseHelper;
    private int userId;

    private TextInputEditText goalWeightField;
    private SwitchMaterial smsToggle;

    // =====================================================
    // Activity lifecycle
    // =====================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_goal_weight);

        databaseHelper = new DatabaseHelper(this);
        userId = getIntent().getIntExtra("USER_ID", -1);

        goalWeightField = findViewById(R.id.goalWeightField);

        MaterialButton saveButton = findViewById(R.id.saveGoalButton);
        MaterialButton logoutButton = findViewById(R.id.logoutButton);
        smsToggle = findViewById(R.id.smsToggle);
        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);

        // Shared setup keeps the navigation behavior identical across screens.
        NavigationHelper.setupBottomNav(this, bottomNav, R.id.nav_goal, userId);

        // Shared helper restores and manages the SMS opt-in state for this user.
        SMSToggleHelper.setupSmsToggle(this, smsToggle, userId);

        saveButton.setOnClickListener(v -> saveGoalWeight());
        logoutButton.setOnClickListener(v -> logout());
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Re-sync the toggle after returning from the permission screen.
        SMSToggleHelper.syncSmsToggle(this, smsToggle, userId);
    }

    // =====================================================
    // Goal handling
    // =====================================================

    /**
     * Saves the goal weight and captures the user's
     * current weight as the baseline for goal evaluation.
     * <p>
     * Using the latest recorded weight avoids incorrect
     * notifications if the goal is later edited.
     */
    private void saveGoalWeight() {

        Editable editable = goalWeightField.getText();
        String input = editable != null ? editable.toString().trim() : "";

        if (input.isEmpty()) {
            Toast.makeText(this, "Please enter a goal weight", Toast.LENGTH_SHORT).show();
            return;
        }

        double goalWeight;
        try {
            goalWeight = Double.parseDouble(input);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid weight value", Toast.LENGTH_SHORT).show();
            return;
        }

        Double latestWeight = databaseHelper.getLatestWeight(userId);

        // If no history exists yet, treat the goal itself
        // as the starting point to avoid false crossings
        double startWeight = (latestWeight != null)
                ? latestWeight
                : goalWeight;

        boolean saved =
                databaseHelper.updateGoalWeight(userId, goalWeight, startWeight);

        if (!saved) {
            Toast.makeText(this, "Failed to save goal weight", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(this, DailyWeightActivity.class);
        intent.putExtra("USER_ID", userId);
        startActivity(intent);
        finish();
    }

    // =====================================================
    // Session management
    // =====================================================

    private void logout() {

        // Clearing login state ensures the next app launch requires authentication
        getSharedPreferences("UserPrefs", MODE_PRIVATE)
                .edit()
                .clear()
                .apply();

        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }
}