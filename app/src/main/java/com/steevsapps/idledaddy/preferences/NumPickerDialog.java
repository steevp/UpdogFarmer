package com.steevsapps.idledaddy.preferences;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceDialogFragmentCompat;
import android.view.View;
import android.widget.NumberPicker;

import com.steevsapps.idledaddy.R;

public class NumPickerDialog extends PreferenceDialogFragmentCompat implements NumberPicker.OnValueChangeListener {
    private final static String VALUE = "VALUE";

    private NumberPicker numPicker;
    private NumPickerPreference preference;
    private int currentValue;

    public static NumPickerDialog newInstance(Preference preference) {
        final NumPickerDialog fragment = new NumPickerDialog();
        final Bundle args = new Bundle();
        args.putString("key", preference.getKey());
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preference = (NumPickerPreference) getPreference();
        if (savedInstanceState == null) {
            currentValue = preference.getValue();
        } else {
            currentValue = savedInstanceState.getInt(VALUE);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(VALUE, currentValue);
    }

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);
        numPicker = view.findViewById(R.id.numpicker);
        numPicker.setMinValue(0);
        numPicker.setMaxValue(5);
        numPicker.setValue(currentValue);
        numPicker.setOnValueChangedListener(this);
    }

    @Override
    public void onDialogClosed(boolean positiveResult) {
        preference.persistValue(currentValue);
    }

    @Override
    public void onValueChange(NumberPicker numberPicker, int oldValue, int newValue) {
        currentValue = newValue;
    }
}
