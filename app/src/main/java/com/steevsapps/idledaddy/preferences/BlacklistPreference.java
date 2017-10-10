package com.steevsapps.idledaddy.preferences;

import android.content.Context;
import android.content.res.TypedArray;
import android.support.v7.preference.DialogPreference;
import android.util.AttributeSet;

import com.steevsapps.idledaddy.R;

public class BlacklistPreference extends DialogPreference {
    private String currentValue;

    public BlacklistPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setDialogLayoutResource(R.layout.blacklist_dialog);
        setPositiveButtonText(android.R.string.ok);
        setNegativeButtonText(android.R.string.cancel);
        setDialogIcon(null);
    }

    void persistStringValue(String value) {
        currentValue = value;
        persistString(currentValue);
    }

    String getValue() {
        return currentValue;
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return a.getString(index);
    }

    @Override
    protected void onSetInitialValue(boolean restorePersistedValue, Object defaultValue) {
        if (restorePersistedValue) {
            // Restore persisted value
            currentValue = getPersistedString("");
        } else {
            // Set default value
            currentValue = defaultValue.toString();
            persistString(currentValue);
        }
    }

}
