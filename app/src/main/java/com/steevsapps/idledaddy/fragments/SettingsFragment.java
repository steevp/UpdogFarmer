package com.steevsapps.idledaddy.fragments;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceFragmentCompat;

import com.steevsapps.idledaddy.R;
import com.steevsapps.idledaddy.preferences.BlacklistDialog;
import com.steevsapps.idledaddy.preferences.BlacklistPreference;
import com.steevsapps.idledaddy.preferences.NumPickerDialog;
import com.steevsapps.idledaddy.preferences.NumPickerPreference;

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
    }

    @Override
    public void onDisplayPreferenceDialog(Preference preference) {
        if (preference instanceof BlacklistPreference) {
            // Show blacklist dialog
            final BlacklistDialog fragment = BlacklistDialog.newInstance(preference);
            fragment.setTargetFragment(this, 0);
            fragment.show(getFragmentManager(), "android.support.v7.preference.PreferenceFragment.DIALOG");
        } else if (preference instanceof NumPickerPreference) {
            final NumPickerDialog fragment = NumPickerDialog.newInstance(preference);
            fragment.setTargetFragment(this, 0);
            fragment.show(getFragmentManager(), "android.support.v7.preference.PreferenceFragment.DIALOG");
        } else {
            super.onDisplayPreferenceDialog(preference);
        }
    }
}
