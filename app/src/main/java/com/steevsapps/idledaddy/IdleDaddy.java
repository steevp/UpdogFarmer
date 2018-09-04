package com.steevsapps.idledaddy;

import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;

import com.steevsapps.idledaddy.db.AppDatabase;
import com.steevsapps.idledaddy.preferences.Prefs;
import com.steevsapps.idledaddy.utils.LocaleManager;

public class IdleDaddy extends Application {
    private AppExecutors executors;

    @Override
    public void onCreate() {
        super.onCreate();
        executors = new AppExecutors();
    }

    @Override
    protected void attachBaseContext(Context base) {
        // Init SharedPreferences manager. Needs to be done before using LocaleManager
        Prefs.init(base);
        super.attachBaseContext(LocaleManager.setLocale(base));
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        LocaleManager.setLocale(this);
    }

    public AppDatabase getDatabase() {
        return AppDatabase.getInstance(this, getExecutors());
    }

    public UserRepository getUserRepository() {
        return UserRepository.getInstance(getDatabase(), getExecutors());
    }

    public AppExecutors getExecutors() {
        return executors;
    }
}
