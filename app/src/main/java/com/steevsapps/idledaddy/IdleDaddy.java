package com.steevsapps.idledaddy;

import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;

import com.steevsapps.idledaddy.db.AppDatabase;
import com.steevsapps.idledaddy.preferences.PrefsManager;

import uk.co.thomasc.steamkit.steam3.CMClient;

public class IdleDaddy extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        // Update CM Servers
        final String servers = PrefsManager.getCmServers();
        if (!servers.isEmpty()) {
            CMClient.updateCMServers(servers.split(","));
        }
    }

    @Override
    protected void attachBaseContext(Context base) {
        // Init SharedPreferences manager
        PrefsManager.init(base);
        super.attachBaseContext(LocaleManager.setLocale(base));
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        LocaleManager.setLocale(this);
    }

    public AppDatabase getDatabase() {
        return AppDatabase.getInstance(this);
    }

    public UserRepository getRepository() {
        return UserRepository.getInstance(getDatabase());
    }
}
