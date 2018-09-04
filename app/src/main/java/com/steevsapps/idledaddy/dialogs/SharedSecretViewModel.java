package com.steevsapps.idledaddy.dialogs;

import android.annotation.SuppressLint;
import android.app.Application;
import android.arch.lifecycle.AndroidViewModel;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.util.Log;

import com.steevsapps.idledaddy.R;
import com.steevsapps.idledaddy.preferences.Prefs;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.Locale;

import eu.chainfire.libsuperuser.Shell;

public class SharedSecretViewModel extends AndroidViewModel {
    private final static String TAG = SharedSecretViewModel.class.getSimpleName();
    // Shell command to read SteamGuard file
    private final static String STEAMGUARD_CMD = "cat /data/data/com.valvesoftware.android.steam.community/files/Steamguard-%d";

    private final MutableLiveData<String> statusText = new MutableLiveData<>();

    private long steamId;

    private boolean suAvailable;
    private List<String> suResult;

    public SharedSecretViewModel(@NonNull Application application) {
        super(application);
    }

    void init(long steamId) {
        this.steamId = steamId;
    }

    LiveData<String> getStatus() {
        return statusText;
    }

    void setValue(String value) {
        statusText.setValue(value);
    }

    @SuppressLint("staticfieldleak")
    public void getSharedSecret() {
        new AsyncTask<Void,Void,Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                // Check if root is available
                suAvailable = Shell.SU.available();
                if (!suAvailable) {
                    return null;
                }

                // Read the SteamGuard file
                suResult = Shell.SU.run(String.format(Locale.US, STEAMGUARD_CMD, steamId));
                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                if (!suAvailable) {
                    Log.e(TAG, "Device is not rooted");
                    statusText.setValue(getApplication().getString(R.string.device_not_rooted));
                    return;
                }

                final StringBuilder sb = new StringBuilder();
                for (String line : suResult) {
                    sb.append(line);
                    sb.append("\n");
                }

                try {
                    final String sharedSecret = new JSONObject(sb.toString())
                            .optString("shared_secret");
                    if (!sharedSecret.isEmpty()) {
                        Log.i(TAG, "shared_secret import successful");
                        Prefs.setSharedSecret(sharedSecret);
                        statusText.setValue(getApplication().getString(R.string.your_shared_secret, sharedSecret));
                        return;
                    }
                } catch (JSONException e) {
                    Log.e(TAG, "Failed to import shared_secret", e);
                    statusText.setValue(getApplication().getString(R.string.import_shared_secret_failed));
                    return;
                }

                Log.e(TAG, "Failed to import shared_secret");
                statusText.setValue(getApplication().getString(R.string.import_shared_secret_failed));
            }
        }.execute();
    }
}
