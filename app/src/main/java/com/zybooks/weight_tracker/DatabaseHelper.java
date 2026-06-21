package com.zybooks.weight_tracker;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Centralized database helper responsible for all persistent storage.
 * <p>
 * Enhancement notes:
 * - Uses parameterized queries throughout (prevents SQL injection and improves efficiency)
 * - Includes strategic indexes on frequently queried columns (user_id, date, username)
 * - Provides reusable helper methods to reduce code duplication and ensure consistent resource cleanup
 * <p>
 * This class intentionally keeps all SQL logic in one place to:
 * - avoid duplication across activities
 * - simplify schema changes
 * - make user and weight relationships explicit
 */
@SuppressWarnings({"SameParameterValue", "unused"}) // Checked warnings- helper methods are okay
public final class DatabaseHelper extends SQLiteOpenHelper {

    // =====================================================
    // Database configuration
    // =====================================================

    private static final String DATABASE_NAME = "weight_tracker.db";
    private static final int DATABASE_VERSION = 2; // Incremented for index additions

    // =====================================================
    // USERS TABLE
    // =====================================================

    public static final String TABLE_USERS = "users";
    public static final String COLUMN_USER_ID = "id";
    public static final String COLUMN_USERNAME = "username";
    public static final String COLUMN_PASSWORD = "password";

    // Goal-related columns are stored with the user so goals
    // persist independently of individual weight entries.
    public static final String COLUMN_GOAL_WEIGHT = "goal_weight";
    public static final String COLUMN_GOAL_START_WEIGHT = "goal_start_weight";

    // =====================================================
    // WEIGHTS TABLE
    // =====================================================

    public static final String TABLE_WEIGHTS = "weights";
    public static final String COLUMN_WEIGHT_ID = "id";
    public static final String COLUMN_DATE = "date";
    public static final String COLUMN_WEIGHT = "weight";
    public static final String COLUMN_USER_FK = "user_id";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    // =====================================================
    // Database lifecycle
    // =====================================================

    @Override
    public void onCreate(SQLiteDatabase db) {

        // Users table stores authentication and goal data.
        // Goal weights are stored with the user rather than a separate table
        // since each user has only one active goal at a time.

        String createUsersTable =
                "CREATE TABLE " + TABLE_USERS + " (" +
                        COLUMN_USER_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        COLUMN_USERNAME + " TEXT UNIQUE NOT NULL, " +
                        COLUMN_PASSWORD + " TEXT NOT NULL, " +
                        COLUMN_GOAL_WEIGHT + " REAL, " +
                        COLUMN_GOAL_START_WEIGHT + " REAL, " +
                        "CHECK (" + COLUMN_GOAL_WEIGHT + " > 0 OR " +
                        COLUMN_GOAL_WEIGHT + " IS NULL))";

        // Weights table stores historical entries per user.
        // Foreign key ensures entries cannot exist without a user.
        String createWeightsTable =
                "CREATE TABLE " + TABLE_WEIGHTS + " (" +
                        COLUMN_WEIGHT_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        COLUMN_DATE + " TEXT NOT NULL, " +
                        COLUMN_WEIGHT + " REAL NOT NULL, " +
                        COLUMN_USER_FK + " INTEGER NOT NULL, " +
                        "CHECK (" + COLUMN_WEIGHT + " > 0), " +
                        "FOREIGN KEY(" + COLUMN_USER_FK + ") REFERENCES " +
                        TABLE_USERS + "(" + COLUMN_USER_ID + "))";

        db.execSQL(createUsersTable);
        db.execSQL(createWeightsTable);

        // Add indexes on frequently queried columns for improved query performance
        db.execSQL("CREATE INDEX idx_users_username ON " +
                TABLE_USERS + "(" + COLUMN_USERNAME + ")");
        db.execSQL("CREATE INDEX idx_weights_user_id ON " +
                TABLE_WEIGHTS + "(" + COLUMN_USER_FK + ")");
        db.execSQL("CREATE INDEX idx_weights_date ON " +
                TABLE_WEIGHTS + "(" + COLUMN_DATE + ")");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // For version 1 -> 2: add indexes if they don't exist
        if (oldVersion < 2) {
            try {
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_users_username ON " +
                        TABLE_USERS + "(" + COLUMN_USERNAME + ")");
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_weights_user_id ON " +
                        TABLE_WEIGHTS + "(" + COLUMN_USER_FK + ")");
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_weights_date ON " +
                        TABLE_WEIGHTS + "(" + COLUMN_DATE + ")");
            } catch (Exception e) {
                // Indexes may already exist; continue gracefully
            }
        }
    }

    // =====================================================
    // Helper methods for consistent query execution
    // =====================================================

    /**
     * Executes a SELECT query and returns a single Long value.
     * Closes the cursor automatically.
     *
     * @param table      the table name
     * @param column     the column to select
     * @param whereClause the WHERE clause (without "WHERE")
     * @param whereArgs  the query arguments
     * @return the value, or -1 if not found
     */
    private long queryForLong(String table, String column, String whereClause, String[] whereArgs) {
        SQLiteDatabase db = this.getReadableDatabase();

        try (Cursor cursor = db.query(table, new String[]{column}, whereClause, whereArgs,
                null, null, null)) {
            if (cursor.moveToFirst()) {
                return cursor.getLong(0);
            }
        }

        return -1;
    }

    /**
     * Executes a SELECT query and returns a single Double value.
     * Closes the cursor automatically.
     *
     * @param table      the table name
     * @param column     the column to select
     * @param whereClause the WHERE clause (without "WHERE")
     * @param whereArgs  the query arguments
     * @return the value, or null if not found
     */
    private Double queryForDouble(String table, String column, String whereClause, String[] whereArgs) {
        SQLiteDatabase db = this.getReadableDatabase();

        try (Cursor cursor = db.query(table, new String[]{column}, whereClause, whereArgs,
                null, null, null)) {
            if (cursor.moveToFirst()) {
                return cursor.getDouble(0);
            }
        }

        return null;
    }

    /**
     * Executes a SELECT query and returns a single String value.
     * Closes the cursor automatically.
     *
     * @param table      the table name
     * @param column     the column to select
     * @param whereClause the WHERE clause (without "WHERE")
     * @param whereArgs  the query arguments
     * @return the value, or null if not found
     */
    private String queryForString(String table, String column, String whereClause, String[] whereArgs) {
        SQLiteDatabase db = this.getReadableDatabase();

        try (Cursor cursor = db.query(table, new String[]{column}, whereClause, whereArgs,
                null, null, null)) {
            if (cursor.moveToFirst()) {
                return cursor.getString(0);
            }
        }

        return null;
    }

    /**
     * Executes a SELECT query and returns whether a record exists.
     * Closes the cursor automatically.
     *
     * @param table      the table name
     * @param whereClause the WHERE clause (without "WHERE")
     * @param whereArgs  the query arguments
     * @return true if at least one record matches, false otherwise
     */
    private boolean recordExists(String table, String whereClause, String[] whereArgs) {
        SQLiteDatabase db = this.getReadableDatabase();

        try (Cursor cursor = db.query(table, new String[]{"1"}, whereClause, whereArgs,
                null, null, null)) {
            return cursor.getCount() > 0;
        }
    }

    // =====================================================
    // User / authentication methods
    // =====================================================

    public boolean insertUser(String username, String password) {
        SQLiteDatabase db = this.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(COLUMN_USERNAME, username);
        values.put(COLUMN_PASSWORD, password);

        return db.insert(TABLE_USERS, null, values) != -1;
    }

    /**
     * Checks whether a user with the given credentials exists.
     * Uses parameterized query to prevent SQL injection.
     */
    public boolean checkUserCredentials(String username, String password) {
        return recordExists(
                TABLE_USERS,
                COLUMN_USERNAME + "=? AND " + COLUMN_PASSWORD + "=?",
                new String[]{username, password}
        );
    }

    /**
     * Retrieves the user ID for a given username.
     * Uses parameterized query for safety.
     */
    public int getUserId(String username) {
        long id = queryForLong(
                TABLE_USERS,
                COLUMN_USER_ID,
                COLUMN_USERNAME + "=?",
                new String[]{username}
        );

        return (int) id;
    }

    // =====================================================
    // Goal-related methods
    // =====================================================

    /**
     * Updates the user's goal weight and captures the weight at the time the goal was set.
     * Storing the starting weight allows goal direction (gain vs. loss) to be determined
     * reliably even if the user later edits their goal.
     */
    public boolean updateGoalWeight(int userId, double goalWeight, double startWeight) {
        SQLiteDatabase db = this.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(COLUMN_GOAL_WEIGHT, goalWeight);
        values.put(COLUMN_GOAL_START_WEIGHT, startWeight);

        return db.update(
                TABLE_USERS,
                values,
                COLUMN_USER_ID + "=?",
                new String[]{String.valueOf(userId)}
        ) > 0;
    }

    /**
     * Retrieves the goal weight for a user.
     * Uses parameterized query for consistency and safety.
     */
    public double getGoalWeight(int userId) {
        Double goal = queryForDouble(
                TABLE_USERS,
                COLUMN_GOAL_WEIGHT,
                COLUMN_USER_ID + "=?",
                new String[]{String.valueOf(userId)}
        );

        return goal != null ? goal : -1;
    }

    // =====================================================
    // Weight entry CRUD methods
    // =====================================================

    public boolean insertWeight(String date, double weight, int userId) {
        SQLiteDatabase db = this.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(COLUMN_DATE, date);
        values.put(COLUMN_WEIGHT, weight);
        values.put(COLUMN_USER_FK, userId);

        return db.insert(TABLE_WEIGHTS, null, values) != -1;
    }

    /**
     * Retrieves all weight entries for a user, ordered by ID descending (newest first).
     * Note: This returns a Cursor that must be managed by the caller.
     */
    public Cursor getAllWeights(int userId) {
        SQLiteDatabase db = this.getReadableDatabase();

        return db.query(
                TABLE_WEIGHTS,
                null,
                COLUMN_USER_FK + "=?",
                new String[]{String.valueOf(userId)},
                null,
                null,
                COLUMN_WEIGHT_ID + " DESC"
        );
    }

    /**
     * Retrieves the weight entry immediately before the most recent one.
     * This is used to determine whether the user crossed their goal
     * threshold between two consecutive entries.
     * <p>
     * Uses parameterized query for safety and efficiency.
     */
    public Double getPreviousWeight(int userId) {
        SQLiteDatabase db = this.getReadableDatabase();

        try (Cursor cursor = db.query(
                TABLE_WEIGHTS,
                new String[]{COLUMN_WEIGHT},
                COLUMN_USER_FK + "=?",
                new String[]{String.valueOf(userId)},
                null,
                null,
                COLUMN_WEIGHT_ID + " DESC",
                "1 OFFSET 1"
        )) {
            if (cursor.moveToFirst()) {
                return cursor.getDouble(0);
            }
        }

        return null;
    }

    /**
     * Retrieves the most recent weight entry for a user.
     * Uses parameterized query for safety and efficiency.
     */
    public Double getLatestWeight(int userId) {
        SQLiteDatabase db = this.getReadableDatabase();

        try (Cursor cursor = db.query(
                TABLE_WEIGHTS,
                new String[]{COLUMN_WEIGHT},
                COLUMN_USER_FK + "=?",
                new String[]{String.valueOf(userId)},
                null,
                null,
                COLUMN_WEIGHT_ID + " DESC",
                "1"
        )) {
            if (cursor.moveToFirst()) {
                return cursor.getDouble(0);
            }
        }

        return null;
    }

    /**
     * Retrieves all weight entries for a user as a managed list.
     * Closes the cursor automatically and converts results to WeightEntry objects.
     */
    public java.util.List<WeightEntry> getAllWeightEntries(int userId) {
        java.util.List<WeightEntry> list = new java.util.ArrayList<>();

        try (Cursor cursor = getAllWeights(userId)) {
            while (cursor.moveToNext()) {
                int id = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_WEIGHT_ID));
                String date = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DATE));
                double weight = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_WEIGHT));
                list.add(new WeightEntry(id, date, weight));
            }
        }

        return list;
    }

    /**
     * Retrieves a specific weight entry by ID and user ID.
     * Returns null if the entry does not exist or does not belong to the user.
     * <p>
     * Uses parameterized query for safety.
     */
    public WeightEntry getWeightEntryById(int userId, int entryId) {
        SQLiteDatabase db = this.getReadableDatabase();

        try (Cursor cursor = db.query(
                TABLE_WEIGHTS,
                new String[]{COLUMN_WEIGHT_ID, COLUMN_DATE, COLUMN_WEIGHT},
                COLUMN_USER_FK + "=? AND " + COLUMN_WEIGHT_ID + "=?",
                new String[]{String.valueOf(userId), String.valueOf(entryId)},
                null,
                null,
                null
        )) {
            if (cursor.moveToFirst()) {
                int id = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_WEIGHT_ID));
                String date = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DATE));
                double weight = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_WEIGHT));
                return new WeightEntry(id, date, weight);
            }
        }

        return null;
    }

    /**
     * Updates an existing weight entry.
     * Uses parameterized query for safety.
     */
    public boolean updateWeight(int id, String date, double weight) {
        SQLiteDatabase db = this.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(COLUMN_DATE, date);
        values.put(COLUMN_WEIGHT, weight);

        return db.update(
                TABLE_WEIGHTS,
                values,
                COLUMN_WEIGHT_ID + "=?",
                new String[]{String.valueOf(id)}
        ) > 0;
    }

    /**
     * Deletes a weight entry by ID.
     * Uses parameterized query for safety.
     */
    public boolean deleteWeight(int id) {
        SQLiteDatabase db = this.getWritableDatabase();

        return db.delete(
                TABLE_WEIGHTS,
                COLUMN_WEIGHT_ID + "=?",
                new String[]{String.valueOf(id)}
        ) > 0;
    }
}






