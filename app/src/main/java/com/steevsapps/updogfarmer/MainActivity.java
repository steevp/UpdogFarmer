package com.steevsapps.updogfarmer;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;

import com.steevsapps.updogfarmer.steam.SteamSession;
import com.steevsapps.updogfarmer.utils.Prefs;

import uk.co.thomasc.steamkit.steam3.handlers.steamuser.types.LogOnDetails;


public class MainActivity extends AppCompatActivity {
    private final static String TAG = "ywtag";

    private SteamSession steamSession;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        steamSession = SteamSession.getInstance();
        steamSession.start();

        if (Prefs.getLoginKey().isEmpty()) {
            // Start LoginActivity
            final Intent intent = LoginActivity.createIntent(this);
            startActivity(intent);
        } else {
            attemptLogin();
        }
    }

    private void attemptLogin() {
        final String username = Prefs.getUsername();
        final String password = Prefs.getPassword();
        final String loginKey = Prefs.getLoginKey();

        if (!username.isEmpty() && !password.isEmpty() && !loginKey.isEmpty()) {
            final LogOnDetails details = new LogOnDetails();
            details.username(username);
            details.loginkey = loginKey;
            details.shouldRememberPassword = true;
            steamSession.login(details);
        }
    }

}
