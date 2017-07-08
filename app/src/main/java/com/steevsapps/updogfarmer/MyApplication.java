package com.steevsapps.updogfarmer;

import android.app.Application;

import com.steevsapps.updogfarmer.utils.Prefs;

public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        // Init SharePreferences manager
        Prefs.init(this);
    }
}
