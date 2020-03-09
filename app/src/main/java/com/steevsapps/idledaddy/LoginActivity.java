package com.steevsapps.idledaddy;

import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;

import com.steevsapps.idledaddy.preferences.PrefsManager;
import com.steevsapps.idledaddy.steam.SteamGuard;
import com.steevsapps.idledaddy.steam.SteamService;
import com.steevsapps.idledaddy.steam.SteamWebHandler;
import com.steevsapps.idledaddy.utils.Utils;

import in.dragonbra.javasteam.enums.EOSType;
import in.dragonbra.javasteam.enums.EResult;
import in.dragonbra.javasteam.steam.handlers.steamuser.LogOnDetails;

import static com.steevsapps.idledaddy.steam.SteamService.LOGIN_EVENT;

public class LoginActivity extends BaseActivity {
    private final static String TAG = LoginActivity.class.getSimpleName();

    private final static String LOGIN_IN_PROGRESS = "LOGIN_IN_PROGRESS";
    private final static String TWO_FACTOR_REQUIRED = "TWO_FACTOR_REQUIRED";

    private boolean loginInProgress;
    private boolean twoFactorRequired;
    private Integer timeDifference = null;

    private LoginViewModel viewModel;

    // Views
    private CoordinatorLayout coordinatorLayout;
    private TextInputLayout usernameInput;
    private TextInputEditText usernameEditText;
    private TextInputLayout passwordInput;
    private TextInputEditText passwordEditText;
    private TextInputLayout twoFactorInput;
    private TextInputEditText twoFactorEditText;
    private Button loginButton;
    private ProgressBar progress;

    // Used to receive messages from SteamService
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (SteamService.LOGIN_EVENT.equals(intent.getAction())) {
                stopTimeout();
                progress.setVisibility(View.GONE);
                final EResult result = (EResult) intent.getSerializableExtra(SteamService.RESULT);
                if (result != EResult.OK) {
                    loginButton.setEnabled(true);
                    usernameInput.setErrorEnabled(false);
                    passwordInput.setErrorEnabled(false);
                    twoFactorInput.setErrorEnabled(false);

                    if (result == EResult.InvalidPassword) {
                        passwordInput.setError(getString(R.string.invalid_password));
                    } else if (result == EResult.AccountLoginDeniedNeedTwoFactor || result == EResult.AccountLogonDenied || result == EResult.AccountLogonDeniedNoMail || result == EResult.AccountLogonDeniedVerifiedEmailRequired) {
                        twoFactorRequired = result == EResult.AccountLoginDeniedNeedTwoFactor;
                        if (twoFactorRequired && timeDifference != null) {
                            // Fill in the SteamGuard code
                            twoFactorEditText.setText(SteamGuard.generateSteamGuardCodeForTime(Utils.getCurrentUnixTime() + timeDifference));
                        }
                        twoFactorInput.setVisibility(View.VISIBLE);
                        twoFactorInput.setError(getString(R.string.steamguard_required));
                        twoFactorEditText.requestFocus();
                    } else if (result == EResult.TwoFactorCodeMismatch || result == EResult.InvalidLoginAuthCode) {
                        twoFactorInput.setError(getString(R.string.invalid_code));
                    }
                } else {
                    // Save username
                    final String username = Utils.removeSpecialChars(usernameEditText.getText().toString()).trim();
                    final String password = Utils.removeSpecialChars(passwordEditText.getText().toString().trim());
                    PrefsManager.writeUsername(username);
                    PrefsManager.writePassword(LoginActivity.this, password);
                    finish();
                }
            }
        }
    };

    /**
     * Start timeout handler in case the server doesn't respond
     */
    private void startTimeout() {
        loginInProgress = true;
        viewModel.startTimeout();
    }

    /**
     * Stop the timeout handler
     */
    private void stopTimeout() {
        loginInProgress = false;
        viewModel.stopTimeout();
    }

    public static Intent createIntent(Context c) {
        return new Intent(c, LoginActivity.class);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        coordinatorLayout = findViewById(R.id.coordinator);
        usernameInput = findViewById(R.id.username_input);
        usernameEditText = findViewById(R.id.username_edittext);
        passwordInput = findViewById(R.id.password_input);
        passwordEditText = findViewById(R.id.password_edittext);
        twoFactorInput = findViewById(R.id.two_factor_input);
        twoFactorEditText = findViewById(R.id.two_factor_edittext);
        loginButton = findViewById(R.id.login);
        progress = findViewById(R.id.progress);

        if (savedInstanceState != null) {
            loginInProgress = savedInstanceState.getBoolean(LOGIN_IN_PROGRESS);
            twoFactorRequired = savedInstanceState.getBoolean(TWO_FACTOR_REQUIRED);
            loginButton.setEnabled(!loginInProgress);
            twoFactorInput.setVisibility(twoFactorRequired ? View.VISIBLE : View.GONE);
            progress.setVisibility(loginInProgress ? View.VISIBLE : View.GONE);
        } else {
            // Restore saved username if any
            usernameEditText.setText(PrefsManager.getUsername());
            passwordEditText.setText(PrefsManager.getPassword(this));
        }

        setupViewModel();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(LOGIN_IN_PROGRESS, loginInProgress);
        outState.putBoolean(TWO_FACTOR_REQUIRED, twoFactorRequired);
    }

    @Override
    protected void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver);
    }

    @Override
    protected void onResume() {
        super.onResume();
        final IntentFilter filter = new IntentFilter(LOGIN_EVENT);
        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, filter);
    }

    public void doLogin(View v) {
        // Steam strips all non-ASCII characters from usernames and passwords
        final String username = Utils.removeSpecialChars(usernameEditText.getText().toString()).trim();
        final String password = Utils.removeSpecialChars(passwordEditText.getText().toString()).trim();
        if (!username.isEmpty() && !password.isEmpty()) {
            loginButton.setEnabled(false);
            progress.setVisibility(View.VISIBLE);
            final LogOnDetails details = new LogOnDetails();
            details.setUsername(username);
            details.setPassword(password);
            details.setClientOSType(EOSType.LinuxUnknown);
            if (twoFactorRequired) {
                details.setTwoFactorCode(twoFactorEditText.getText().toString().trim());
            } else {
                details.setAuthCode(twoFactorEditText.getText().toString().trim());
            }
            details.setShouldRememberPassword(true);
            getService().login(details);
            startTimeout();
        }
    }

    private void setupViewModel() {
        viewModel = ViewModelProviders.of(this).get(LoginViewModel.class);
        viewModel.init(SteamWebHandler.getInstance());
        viewModel.getTimeDifference().observe(this, new Observer<Integer>() {
            @Override
            public void onChanged(@Nullable Integer value) {
                timeDifference = value;
            }
        });
        viewModel.getTimeout().observe(this, new Observer<Void>() {
            @Override
            public void onChanged(@Nullable Void aVoid) {
                loginInProgress = false;
                loginButton.setEnabled(true);
                progress.setVisibility(View.GONE);
                Snackbar.make(coordinatorLayout, R.string.timeout_error, Snackbar.LENGTH_LONG).show();
            }
        });
    }
}
