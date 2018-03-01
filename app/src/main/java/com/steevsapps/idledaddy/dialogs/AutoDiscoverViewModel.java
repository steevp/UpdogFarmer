package com.steevsapps.idledaddy.dialogs;

import android.annotation.SuppressLint;
import android.app.Application;
import android.arch.lifecycle.AndroidViewModel;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.support.annotation.NonNull;

import com.steevsapps.idledaddy.IdleDaddy;
import com.steevsapps.idledaddy.R;
import com.steevsapps.idledaddy.steam.SteamWebHandler;

import org.json.JSONArray;

public class AutoDiscoverViewModel extends AndroidViewModel {
    private MutableLiveData<String> progress;
    private final SteamWebHandler webHandler;

    public AutoDiscoverViewModel(@NonNull Application application) {
        super(application);
        webHandler = SteamWebHandler.getInstance(((IdleDaddy) application).getRepository());
    }

    LiveData<String> getProgress() {
        if (progress == null) {
            progress = new MutableLiveData<>();
            loadData();
        }
        return progress;
    }

    @SuppressLint("StaticFieldLeak")
    private void loadData() {
        new AsyncTask<Void,String,Boolean>() {
            @Override
            protected Boolean doInBackground(Void... voids) {
                try {
                    final Resources res = getApplication().getResources();
                    final JSONArray discoveryQueue = webHandler.generateNewDiscoveryQueue();
                    for (int i=0, count=discoveryQueue.length();i<count;i++) {
                        final String appId = discoveryQueue.getString(i);
                        publishProgress(res.getString(R.string.discovering, appId, i + 1, count));
                        webHandler.clearFromQueue(appId);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    return false;
                }
                return true;
            }

            @Override
            protected void onProgressUpdate(String... values) {
                progress.setValue(values[0]);
            }

            @Override
            protected void onPostExecute(Boolean result) {
                final Resources res = getApplication().getResources();
                progress.setValue(res.getString(result ? R.string.discovery_finished : R.string.discovery_error));
            }
        }.execute();
    }
}
