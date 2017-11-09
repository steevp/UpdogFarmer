package com.steevsapps.idledaddy.preferences;

import android.content.Context;
import android.content.res.TypedArray;
import android.support.v7.preference.DialogPreference;
import android.util.AttributeSet;

import com.steevsapps.idledaddy.R;

public class NumPickerPreference extends DialogPreference {
    private final static int DEFAULT_VALUE = 3;
    private int currentValue;

    public NumPickerPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setDialogLayoutResource(R.layout.numpicker_dialog);
        setPositiveButtonText(null);
        setNegativeButtonText(android.R.string.cancel);
        setDialogIcon(null);
    }

    void persistValue(int value) {
        currentValue = value;
        persistInt(value);
    }

    int getValue() {
        return currentValue;
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return a.getInteger(index, DEFAULT_VALUE);
    }

    @Override
    protected void onSetInitialValue(boolean restorePersistedValue, Object defaultValue) {
        if (restorePersistedValue) {
            // Restore persisted value.
            // NOTE: local variable defaultValue is always null here
            currentValue = getPersistedInt(DEFAULT_VALUE);
        } else {
            // Set default value
            currentValue = (int) defaultValue;
            persistInt(currentValue);
        }
    }
}
