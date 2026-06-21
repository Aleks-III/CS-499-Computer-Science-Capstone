package com.zybooks.weight_tracker;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;

import java.util.Locale;

/**
 * Handles user authentication and account creation.
 * <p>
 * This activity is responsible only for validating credentials
 * and routing the user to the appropriate next screen.
 */
public class LoginActivity extends AppCompatActivity {

    private DatabaseHelper databaseHelper;

    private TextInputEditText usernameField;
    private TextInputEditText passwordField;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        databaseHelper = new DatabaseHelper(this);

        usernameField = findViewById(R.id.emailField);
        passwordField = findViewById(R.id.passwordField);

        Button loginButton = findViewById(R.id.loginButton);
        Button createAccountButton = findViewById(R.id.createAccountButton);

        loginButton.setOnClickListener(v -> loginUser());
        createAccountButton.setOnClickListener(v -> createAccount());
    }

    /**
     * Safely reads and trims text from a TextInputEditText.
     */
    private String getTrimmedText(TextInputEditText field) {
        return field.getText() != null ? field.getText().toString().trim() : "";
    }

    /**
     * Returns the trimmed username/email input normalized for lookup.
     * Emails are treated case-insensitively for UX consistency.
     */
    private String getNormalizedUsernameInput() {
        return getTrimmedText(usernameField).toLowerCase(Locale.US);
    }

    /**
     * Returns the trimmed password input.
     */
    private String getPasswordInput() {
        return getTrimmedText(passwordField);
    }

    /**
     * Returns true if the provided credentials are invalid and login/create should stop.
     */
    private boolean isCredentialInputInvalid(String username, String password) {
        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this,
                    "Please enter email and password",
                    Toast.LENGTH_SHORT).show();
            return true;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(username).matches()) {
            Toast.makeText(this,
                    "Please enter a valid email address",
                    Toast.LENGTH_SHORT).show();
            return true;
        }

        return false;
    }

    /**
     * Authenticates an existing user.
     * <p>
     * Email format validation is performed here to prevent
     * unnecessary database queries with invalid input.
     */
    private void loginUser() {
        String username = getNormalizedUsernameInput();
        String password = getPasswordInput();

        if (isCredentialInputInvalid(username, password)) {
            return;
        }

        boolean valid = databaseHelper.checkUserCredentials(username, password);

        if (valid) {
            int userId = databaseHelper.getUserId(username);

            // Returning users skip goal setup and go straight to daily entry
            Intent intent = new Intent(this, DailyWeightActivity.class);
            intent.putExtra("USER_ID", userId);
            startActivity(intent);
            finish();

        } else {
            Toast.makeText(this,
                    "Invalid login credentials",
                    Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Creates a new user account.
     * <p>
     * New users are routed to goal setup so future progress and notifications have a clear target.
     */
    private void createAccount() {
        String username = getNormalizedUsernameInput();
        String password = getPasswordInput();

        if (isCredentialInputInvalid(username, password)) {
            return;
        }

        boolean created = databaseHelper.insertUser(username, password);

        if (created) {
            int userId = databaseHelper.getUserId(username);

            Toast.makeText(this,
                    "Account created",
                    Toast.LENGTH_SHORT).show();

            // New users must define a goal before logging entries
            Intent intent = new Intent(this, GoalWeightActivity.class);
            intent.putExtra("USER_ID", userId);
            startActivity(intent);
            finish();

        } else {
            Toast.makeText(this,
                    "Username already exists",
                    Toast.LENGTH_SHORT).show();
        }
    }
}