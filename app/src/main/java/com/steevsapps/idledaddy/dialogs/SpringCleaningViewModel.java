package com.steevsapps.idledaddy.dialogs;

import android.annotation.SuppressLint;
import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import android.os.AsyncTask;
import androidx.annotation.NonNull;

import com.steevsapps.idledaddy.R;
import com.steevsapps.idledaddy.steam.SteamService;
import com.steevsapps.idledaddy.steam.SteamWebHandler;

import java.util.List;

public class SpringCleaningViewModel extends AndroidViewModel {

    private SteamWebHandler webHandler;
    private SteamService service;
    private final MutableLiveData<String> statusText = new MutableLiveData<>();
    private boolean finished = true;

    public SpringCleaningViewModel(@NonNull Application application) {
        super(application);
    }

    void init(SteamWebHandler webHandler, SteamService service) {
        this.webHandler = webHandler;
        if (this.service == null) {
            this.service = service;
        }
    }

    LiveData<String> getStatus() {
        return statusText;
    }

    boolean isFinished() {
        return finished;
    }

    @SuppressLint("StaticFieldLeak")
    void completeTasks() {
        finished = false;
        statusText.setValue("");
        new AsyncTask<Void,String,Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                final List<String> taskApps = webHandler.getTaskAppIds();
                for (String app : taskApps) {
                    publishProgress(app);
                    service.registerAndIdle(app);
                }
                return null;
            }

            @Override
            protected void onProgressUpdate(String... values) {
                statusText.setValue(getApplication().getString(R.string.now_playing2, values[0]));
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                finished = true;
                statusText.setValue(getApplication().getString(R.string.daily_tasks_completed));
            }
        }.execute();
    }
}