package com.steevsapps.idledaddy.dialogs;

import android.annotation.SuppressLint;
import android.app.Application;
import android.arch.lifecycle.AndroidViewModel;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.os.AsyncTask;
import android.support.annotation.NonNull;

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
    void completeDailyTasks() {
        finished = false;
        statusText.setValue("");
        new AsyncTask<Void,String,Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                final List<String> dailyTaskApps = webHandler.getDailyTaskAppIds();
                for (String app : dailyTaskApps) {
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

    @SuppressLint("StaticFieldLeak")
    void completeProjectTasks() {
        finished = false;
        statusText.setValue("");
        new AsyncTask<Void,String,Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                final List<String> projectTaskApps = webHandler.getProjectAppIds();
                for (String app : projectTaskApps) {
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
                statusText.setValue(getApplication().getString(R.string.project_tasks_completed));
            }
        }.execute();
    }
}
