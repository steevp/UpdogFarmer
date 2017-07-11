package com.steevsapps.updogfarmer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.design.widget.TextInputLayout;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;

import com.steevsapps.updogfarmer.steam.SteamService;
import com.steevsapps.updogfarmer.utils.Prefs;

import uk.co.thomasc.steamkit.base.generated.steamlanguage.EResult;
import uk.co.thomasc.steamkit.steam3.handlers.steamuser.types.LogOnDetails;

public class LoginActivity extends AppCompatActivity {
    private final static String TAG = "ywtag";

    public final static String LOGIN_INTENT = ".LoginActivity.LOGIN_INTENT";
    public final static String RESULT = ".LoginActivity.RESULT";

    private SteamService steamService;
    private boolean twoFactorRequired;

    // Views
    private TextInputLayout usernameInput;
    private TextInputLayout passwordInput;
    private TextInputLayout twoFactorInput;
    private Button loginButton;
    private ProgressBar progress;

    // Used to receive messages from SteamService
    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(LOGIN_INTENT)) {
                loginButton.setEnabled(true);
                progress.setVisibility(View.GONE);
                final EResult result = (EResult) intent.getSerializableExtra(RESULT);
                if (result != EResult.OK) {
                    usernameInput.setErrorEnabled(false);
                    passwordInput.setErrorEnabled(false);
                    twoFactorInput.setErrorEnabled(false);

                    if (result == EResult.InvalidPassword) {
                        passwordInput.setError("Invalid password");
                    } else if (result == EResult.AccountLoginDeniedNeedTwoFactor) {
                        twoFactorRequired = true;
                        twoFactorInput.setVisibility(View.VISIBLE);
                        twoFactorInput.setError("Two factor code required");
                        twoFactorInput.getEditText().requestFocus();
                    }
                } else {
                    // Save username and password
                    Prefs.writeUsername(usernameInput.getEditText().getText().toString());
                    Prefs.writePassword(passwordInput.getEditText().getText().toString());
                    finish();
                }
            }
        }
    };

    public static Intent createIntent(Context c) {
        return new Intent(c, LoginActivity.class);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        usernameInput = (TextInputLayout) findViewById(R.id.username);
        passwordInput = (TextInputLayout) findViewById(R.id.password);
        twoFactorInput = (TextInputLayout) findViewById(R.id.two_factor);
        loginButton = (Button) findViewById(R.id.login);
        progress = (ProgressBar) findViewById(R.id.progress);

        steamService = SteamService.getInstance();

        // Restore saved password if any
        usernameInput.getEditText().setText(Prefs.getUsername());
        passwordInput.getEditText().setText(Prefs.getPassword());
    }

    @Override
    protected void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver);
    }

    @Override
    protected void onResume() {
        super.onResume();
        final IntentFilter filter = new IntentFilter(LOGIN_INTENT);
        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, filter);
    }

    public void doLogin(View v) {
        final String username = usernameInput.getEditText().getText().toString();
        final String password = passwordInput.getEditText().getText().toString();
        if (!username.isEmpty() && !password.isEmpty()) {
            loginButton.setEnabled(false);
            progress.setVisibility(View.VISIBLE);
            final LogOnDetails details = new LogOnDetails();
            details.username(username);
            details.password(password);
            if (twoFactorRequired) {
                details.twoFactorCode(twoFactorInput.getEditText().getText().toString());
            }
            details.shouldRememberPassword = true;
            steamService.login(details);
        }
    }
}
