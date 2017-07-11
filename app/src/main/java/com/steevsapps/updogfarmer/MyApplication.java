package com.steevsapps.updogfarmer;

import android.app.Application;

import com.steevsapps.updogfarmer.utils.Prefs;

import uk.co.thomasc.steamkit.steam3.CMClient;

public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        // Init SharePreferences manager
        Prefs.init(this);

        // Update CM Servers
        final String servers = Prefs.getCmServers();
        if (!servers.isEmpty()) {
            CMClient.updateCMServers(servers.split(","));
        }
    }
}
