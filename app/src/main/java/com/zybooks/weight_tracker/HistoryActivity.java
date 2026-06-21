package com.zybooks.weight_tracker;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.List;

/**
 * Displays the user's weight history and provides entry management (add, edit, delete).
 * <p>
 * This screen acts as the primary navigation hub after login.
 */
public class HistoryActivity extends AppCompatActivity {

    // =====================================================
    // Fields
    // =====================================================

    private DatabaseHelper databaseHelper;
    private int userId;

    private RecyclerView recyclerView;
    private SwitchMaterial smsToggle;

    private TextView goalWeightText;
    private TextView weeklyAvgText;
    private TextView monthlyAvgText;
    private TextView trendText;
    private TextView rateText;
    private TextView etaText;

    // =====================================================
    // Activity lifecycle
    // =====================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        databaseHelper = new DatabaseHelper(this);
        userId = getIntent().getIntExtra("USER_ID", -1);

        recyclerView = findViewById(R.id.historyRecycler);
        MaterialButton logoutButton = findViewById(R.id.logoutButton);
        smsToggle = findViewById(R.id.smsToggle);
        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);

        goalWeightText = findViewById(R.id.goalWeightText);
        weeklyAvgText = findViewById(R.id.weeklyAvgText);
        monthlyAvgText = findViewById(R.id.monthlyAvgText);
        trendText = findViewById(R.id.trendText);
        rateText = findViewById(R.id.rateText);
        etaText = findViewById(R.id.etaText);

        // Shared setup keeps navigation consistent across the app.
        NavigationHelper.setupBottomNav(this, bottomNav, R.id.nav_history, userId);

        // Shared helper restores and manages the SMS opt-in state for this user.
        SMSToggleHelper.setupSmsToggle(this, smsToggle, userId);

        logoutButton.setOnClickListener(v -> logout());

        loadWeightHistory();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Re-sync the toggle after returning from the permission screen.
        SMSToggleHelper.syncSmsToggle(this, smsToggle, userId);
    }

    // =====================================================
    // Data loading
    // =====================================================

    /**
     * Loads all stored weight entries for the current user and binds them to the RecyclerView.
     * <p>
     * The adapter is recreated to ensure the UI stays in sync after edits or deletions.
     */
    private void loadWeightHistory() {
        List<WeightEntry> weightList = databaseHelper.getAllWeightEntries(userId);

        if (weightList.isEmpty()) {
            Toast.makeText(this, "No weight entries found", Toast.LENGTH_SHORT).show();
        }

        // --- Category Two analytics ---
        double goalWeight = databaseHelper.getGoalWeight(userId);
        WeightAnalytics.Result result = WeightAnalytics.analyze(weightList, goalWeight);

        Log.d("ANALYTICS", "entries=" + weightList.size()
                + " weeklyAvg=" + result.weeklyAverage
                + " monthlyAvg=" + result.monthlyAverage
                + " rate=" + result.rateLbsPerDay
                + " trend=" + result.trendType
                + " etaDays=" + result.etaDays);

        weeklyAvgText.setText(getString(R.string.stat_labeled_value,
                getString(R.string.label_weekly_avg),
                weightOrDash(result.weeklyAverage)));

        monthlyAvgText.setText(getString(R.string.stat_labeled_value,
                getString(R.string.label_monthly_avg),
                weightOrDash(result.monthlyAverage)));

        trendText.setText(getString(R.string.stat_labeled_value,
                getString(R.string.label_trend),
                formatTrendValue(result.trendType)));

        Double goal = goalWeight > 0 ? goalWeight : null;

        goalWeightText.setText(getString(
                R.string.stat_labeled_value,
                getString(R.string.label_goal),
                weightOrDash(goal)
        ));

        String rateValue = (result.rateLbsPerDay != null)
                ? getString(R.string.rate_value_format, result.rateLbsPerDay * 7.0)
                : getString(R.string.stat_dash);

        rateText.setText(getString(
                R.string.stat_labeled_value,
                getString(R.string.label_rate),
                rateValue
        ));

        String etaValue = (result.etaDays != null)
                ? getString(R.string.eta_days, result.etaDays)
                : getString(R.string.stat_dash);

        etaText.setText(getString(
                R.string.stat_labeled_value,
                getString(R.string.label_eta),
                etaValue
        ));
        // --- end analytics ---

        bindWeightHistory(weightList);
    }

    /**
     * Formats a weight value using {@link R.string#weight_display}, or returns a dash when absent.
     * <p>
     * Passing null indicates the stat is not available (e.g., not enough data).
     */
    private String weightOrDash(Double value) {
        return value == null ? dash() : getString(R.string.weight_display, value);
    }

    /** Returns the standard placeholder used for unavailable stats. */
    private String dash() {
        return getString(R.string.stat_dash);
    }

    /**
     * Converts a trend classification into a user-friendly label.
     * Returns a dash when the trend is unavailable or insufficient data is present.
     */
    private String formatTrendValue(WeightAnalytics.TrendType type) {
        if (type == null || type == WeightAnalytics.TrendType.INSUFFICIENT) return dash();
        switch (type) {
            case LOSS: return getString(R.string.trend_loss);
            case GAIN: return getString(R.string.trend_gain);
            case FLAT: return getString(R.string.trend_flat);
            default: return dash();
        }
    }

    /**
     * Creates and binds the RecyclerView adapter for the given weight list.
     */
    private void bindWeightHistory(List<WeightEntry> weightList) {
        WeightAdapter adapter = new WeightAdapter(
                weightList,
                this::editEntry,
                this::deleteEntry
        );

        recyclerView.setAdapter(adapter);
    }

    // =====================================================
    // Entry actions
    // =====================================================

    /**
     * Launches the daily entry screen with an existing entry preloaded for editing.
     */
    private void editEntry(WeightEntry entry) {
        Intent intent = new Intent(this, DailyWeightActivity.class);
        intent.putExtra("USER_ID", userId);
        intent.putExtra("ENTRY_ID", entry.getId());
        startActivity(intent);
    }

    /**
     * Removes an entry from the database and refreshes the list to reflect the change immediately.
     */
    private void deleteEntry(WeightEntry entry) {
        boolean deleted = databaseHelper.deleteWeight(entry.getId());

        if (!deleted) {
            Toast.makeText(this, "Failed to delete entry", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "Entry deleted", Toast.LENGTH_SHORT).show();
        loadWeightHistory();
    }

    // =====================================================
    // Session management
    // =====================================================

    /**
     * Clears the login state and returns the user to the login screen,
     * removing this activity from the back stack.
     */
    private void logout() {
        getSharedPreferences("UserPrefs", MODE_PRIVATE)
                .edit()
                .clear()
                .apply();

        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }
}