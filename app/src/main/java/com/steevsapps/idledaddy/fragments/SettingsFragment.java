package com.steevsapps.idledaddy.fragments;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceFragmentCompat;

import com.steevsapps.idledaddy.R;
import com.steevsapps.idledaddy.dialogs.AboutDialog;

public class SettingsFragment extends PreferenceFragmentCompat {
    private final static String TAG = SettingsFragment.class.getSimpleName();
    private SharedPreferences prefs;

    public static SettingsFragment newInstance() {
        return new SettingsFragment();
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        // Load preferences from XML resource
        addPreferencesFromResource(R.xml.preferences);

        // Show 'about' dialog on click
        final Preference button = findPreference("about");
        button.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                AboutDialog.newInstance().show(getFragmentManager(), "about");
                return true;
            }
        });
    }
}
