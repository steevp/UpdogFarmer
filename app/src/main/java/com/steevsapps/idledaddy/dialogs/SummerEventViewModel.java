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
import com.steevsapps.idledaddy.steam.SteamWebHandler;

import org.json.JSONObject;

public class SummerEventViewModel extends AndroidViewModel {
    private final static String TAG = SummerEventViewModel.class.getSimpleName();

    private final MutableLiveData<String> statusText = new MutableLiveData<>();
    private SteamWebHandler webHandler;
    private AsyncTask<Void,String,Boolean> task;

    private volatile boolean finished = true;

    public SummerEventViewModel(@NonNull Application application) {
        super(application);
    }

    void init(SteamWebHandler webHandler) {
        this.webHandler = webHandler;
    }

    public boolean isFinished() {
        return finished;
    }

    LiveData<String> getStatus() {
        return statusText;
    }

    @SuppressLint("StaticFieldLeak")
    void playSaliens() {
        finished = false;
        task = new AsyncTask<Void,String,Boolean>() {
            @Override
            protected Boolean doInBackground(Void... voids) {
                final String accessToken = webHandler.getSaliensToken();
                if (accessToken == null) {
                    return false;
                }
                for (int i=0;i<3;i++) {
                    publishProgress(getApplication().getString(R.string.playing_saliens, i + 1, 3));
                    JSONObject playerInfo = webHandler.getPlayerInfo(accessToken);
                    if (playerInfo == null) {
                        // Try again
                        playerInfo = webHandler.getPlayerInfo(accessToken);
                        if (playerInfo == null) {
                            return false;
                        }
                    }
                    if (!webHandler.playSaliens(playerInfo, accessToken)) {
                        return false;
                    }
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    if (isCancelled()) {
                        return false;
                    }
                }
                return true;
            }

            @Override
            protected void onProgressUpdate(String... values) {
                statusText.setValue(values[0]);
            }

            @Override
            protected void onPostExecute(Boolean result) {
                finished = true;
                statusText.setValue(getApplication().getString(result ? R.string.success_check_inventory : R.string.play_saliens_failed));
            }
        }.execute();

    }

    @SuppressLint("StaticFieldLeak")
    void playSaliensFull() {
        finished = false;
        task = new AsyncTask<Void,String,Boolean>() {
            @Override
            protected Boolean doInBackground(Void... voids) {
                publishProgress(getApplication().getString(R.string.playing_saliens_indefinitely, "0"));
                final String accessToken = webHandler.getSaliensToken();
                if (accessToken == null) {
                    return false;
                }
                while (!finished) {
                    Log.i(TAG, "Playing saliens round");
                    JSONObject playerInfo = webHandler.getPlayerInfo(accessToken);
                    if (playerInfo == null) {
                        // Try again
                        playerInfo = webHandler.getPlayerInfo(accessToken);
                        if (playerInfo == null) {
                            return false;
                        }
                    }
                    Log.i(TAG, "Score: " + playerInfo.optString("score", "unknown"));
                    publishProgress(getApplication().getString(R.string.playing_saliens_indefinitely, playerInfo.optString("score", "0")));
                    webHandler.playSaliensRound(playerInfo, accessToken);

                    if (isCancelled()) {
                        return false;
                    }
                }
                return true;
            }

            @Override
            protected void onProgressUpdate(String... values) {
                statusText.setValue(values[0]);
            }

            @Override
            protected void onPostExecute(Boolean result) {
                finished = true;
                statusText.setValue(getApplication().getString(result ? R.string.success_check_inventory : R.string.play_saliens_failed));
            }
        }.execute();
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        finished = true;
        if (task != null) {
            task.cancel(true);
            task = null;
        }
    }
}
