package com.steevsapps.idledaddy.fragments;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.preference.PreferenceFragmentCompat;
import android.view.WindowManager;

import com.steevsapps.idledaddy.R;
import com.steevsapps.idledaddy.utils.Prefs;

public class SettingsFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener {
    private final static String TAG = SettingsFragment.class.getSimpleName();
    private SharedPreferences prefs;

    public static SettingsFragment newInstance() {
        return new SettingsFragment();
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        // Load preferences from XML resource
        addPreferencesFromResource(R.xml.preferences);
    }

    @Override
    public void onPause() {
        super.onPause();
        prefs.unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        // Listen for changes
        prefs = Prefs.getPrefs();
        prefs.registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals("stay_awake")) {
            if (sharedPreferences.getBoolean("stay_awake", false)) {
                getActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            } else {
                getActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
        }
    }
}
