package com.zybooks.weight_tracker;

import android.app.Activity;
import android.content.Intent;

import com.google.android.material.bottomnavigation.BottomNavigationView;

/**
 * Shared bottom navigation setup for the three main screens.
 * <p>
 * Centralizing this logic reduces duplication and keeps navigation
 * behavior consistent across the app.
 */
public final class NavigationHelper {

    private NavigationHelper() {
        // Utility class
    }

    /**
     * Configures the bottom navigation bar for the current screen.
     *
     * @param activity        current activity
     * @param bottomNav       bottom navigation view
     * @param selectedItemId  menu item that should appear selected
     * @param userId          active user ID to pass between screens
     */
    public static void setupBottomNav(Activity activity,
                                      BottomNavigationView bottomNav,
                                      int selectedItemId,
                                      int userId) {
        bottomNav.setSelectedItemId(selectedItemId);

        bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();

            // Keep the current tab selected without reloading the same screen.
            if (itemId == selectedItemId) {
                return true;
            }

            Intent intent = null;

            if (itemId == R.id.nav_new_entry) {
                intent = new Intent(activity, DailyWeightActivity.class);
            } else if (itemId == R.id.nav_history) {
                intent = new Intent(activity, HistoryActivity.class);
            } else if (itemId == R.id.nav_goal) {
                intent = new Intent(activity, GoalWeightActivity.class);
            }

            if (intent != null) {
                intent.putExtra("USER_ID", userId);
                activity.startActivity(intent);
                activity.overridePendingTransition(0, 0);
                activity.finish();
                activity.overridePendingTransition(0, 0);
                return true;
            }

            return false;
        });
    }
}