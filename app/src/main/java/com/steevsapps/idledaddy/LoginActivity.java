package com.steevsapps.idledaddy;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.Snackbar;
import android.support.design.widget.TextInputLayout;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;

import com.steevsapps.idledaddy.steam.SteamService;
import com.steevsapps.idledaddy.utils.Prefs;

import uk.co.thomasc.steamkit.base.generated.steamlanguage.EResult;
import uk.co.thomasc.steamkit.steam3.handlers.steamuser.types.LogOnDetails;

import static com.steevsapps.idledaddy.steam.SteamService.LOGIN_EVENT;

public class LoginActivity extends AppCompatActivity {
    private final static String TAG = LoginActivity.class.getSimpleName();

    boolean isBound;
    private SteamService steamService;
    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            steamService = ((SteamService.LocalBinder) service).getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            steamService = null;
        }
    };

    // Timeout handler
    private final static int TIMEOUT_MILLIS = 15000;
    private final Handler timeoutHandler = new Handler();
    private final Runnable timeoutRunnable = new Runnable() {
        @Override
        public void run() {
            loginButton.setEnabled(true);
            progress.setVisibility(View.GONE);
            Snackbar.make(coordinatorLayout, R.string.timeout_error, Snackbar.LENGTH_LONG).show();
            steamService.disconnect();
        }
    };

    private boolean twoFactorRequired;

    // Views
    private CoordinatorLayout coordinatorLayout;
    private TextInputLayout usernameInput;
    private TextInputLayout passwordInput;
    private TextInputLayout twoFactorInput;
    private Button loginButton;
    private ProgressBar progress;

    // Used to receive messages from SteamService
    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(SteamService.LOGIN_EVENT)) {
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
                        twoFactorInput.setVisibility(View.VISIBLE);
                        twoFactorInput.setError(getString(R.string.steamguard_required));
                        twoFactorInput.getEditText().requestFocus();
                    } else if (result == EResult.TwoFactorCodeMismatch || result == EResult.InvalidLoginAuthCode) {
                        twoFactorInput.setError(getString(R.string.invalid_code));
                    }
                } else {
                    // Save username and password
                    Prefs.writeUsername(usernameInput.getEditText().getText().toString().trim());
                    Prefs.writePassword(passwordInput.getEditText().getText().toString().trim());
                    finish();
                }
            }
        }
    };

    private void doBindService() {
        bindService(new Intent(LoginActivity.this, SteamService.class),
                connection, Context.BIND_AUTO_CREATE);
        isBound = true;
    }

    private void doUnbindService() {
        if (isBound) {
            // Detach our existing connection
            unbindService(connection);
            isBound = false;
        }
    }

    /**
     * Start timeout handler in case the server doesn't respond
     */
    private void startTimeout() {
        Log.i(TAG, "Starting timeout handler");
        timeoutHandler.postDelayed(timeoutRunnable, TIMEOUT_MILLIS);
    }

    /**
     * Stop the timeout handler
     */
    private void stopTimeout() {
        Log.i(TAG, "Stopping timeout handler");
        timeoutHandler.removeCallbacks(timeoutRunnable);
    }

    public static Intent createIntent(Context c) {
        return new Intent(c, LoginActivity.class);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        coordinatorLayout = findViewById(R.id.coordinator);
        usernameInput = findViewById(R.id.username);
        passwordInput = findViewById(R.id.password);
        twoFactorInput = findViewById(R.id.two_factor);
        loginButton = findViewById(R.id.login);
        progress = findViewById(R.id.progress);

        // Restore saved password if any
        usernameInput.getEditText().setText(Prefs.getUsername());
        passwordInput.getEditText().setText(Prefs.getPassword());
    }

    @Override
    protected void onPause() {
        super.onPause();
        doUnbindService();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver);
    }

    @Override
    protected void onResume() {
        super.onResume();
        doBindService();
        final IntentFilter filter = new IntentFilter(LOGIN_EVENT);
        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, filter);
    }

    @Override
    protected void onDestroy() {
        stopTimeout();
        super.onDestroy();
    }

    public void doLogin(View v) {
        final String username = usernameInput.getEditText().getText().toString().trim();
        final String password = passwordInput.getEditText().getText().toString().trim();
        if (!username.isEmpty() && !password.isEmpty()) {
            loginButton.setEnabled(false);
            progress.setVisibility(View.VISIBLE);
            final LogOnDetails details = new LogOnDetails();
            details.username(username);
            details.password(password);
            if (twoFactorRequired) {
                details.twoFactorCode(twoFactorInput.getEditText().getText().toString());
            } else {
                details.authCode(twoFactorInput.getEditText().getText().toString().trim());
            }
            details.shouldRememberPassword = true;
            steamService.login(details);
            startTimeout();
        }
    }
}
