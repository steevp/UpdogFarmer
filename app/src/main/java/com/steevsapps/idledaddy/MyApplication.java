package com.steevsapps.idledaddy;

import android.app.Application;

import com.steevsapps.idledaddy.preferences.PrefsManager;

import uk.co.thomasc.steamkit.steam3.CMClient;

public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        // Init SharePreferences manager
        PrefsManager.init(this);

        // Update CM Servers
        final String servers = PrefsManager.getCmServers();
        if (!servers.isEmpty()) {
            CMClient.updateCMServers(servers.split(","));
        }
    }
}
