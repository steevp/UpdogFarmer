package com.steevsapps.updogfarmer;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.TextInputLayout;
import android.support.v7.app.AppCompatActivity;
import android.view.View;

import com.steevsapps.updogfarmer.steam.SteamCallback;
import com.steevsapps.updogfarmer.steam.SteamSession;
import com.steevsapps.updogfarmer.utils.Prefs;

import uk.co.thomasc.steamkit.base.generated.steamlanguage.EResult;
import uk.co.thomasc.steamkit.steam3.handlers.steamuser.types.LogOnDetails;

public class LoginActivity extends AppCompatActivity implements SteamCallback {
    private final static String TAG = "ywtag";

    private SteamSession steamSession;
    private boolean twoFactorRequired;

    // Views
    private TextInputLayout usernameInput;
    private TextInputLayout passwordInput;
    private TextInputLayout twoFactorInput;

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

        steamSession = SteamSession.getInstance();
        steamSession.setListener(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        steamSession.setListener(null);
    }

    @Override
    public void onResponse(EResult result) {
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
            // Login successful
            setResult(RESULT_OK);

            // Save username and password
            Prefs.writeUsername(usernameInput.getEditText().getText().toString());
            Prefs.writePassword(passwordInput.getEditText().getText().toString());

            finish();
        }
    }

    public void doLogin(View v) {
        final String username = usernameInput.getEditText().getText().toString();
        final String password = passwordInput.getEditText().getText().toString();
        if (!username.isEmpty() && !password.isEmpty()) {
            final LogOnDetails details = new LogOnDetails();
            details.username(username);
            details.password(password);
            if (twoFactorRequired) {
                details.twoFactorCode(twoFactorInput.getEditText().getText().toString());
            }
            details.shouldRememberPassword = true;
            steamSession.login(details);
        }
    }
}
