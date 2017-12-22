package com.steevsapps.idledaddy.dialogs;

import android.annotation.SuppressLint;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.arch.lifecycle.ViewModel;
import android.os.AsyncTask;

import com.steevsapps.idledaddy.steam.SteamWebHandler;

import org.json.JSONArray;

public class AutoDiscoverViewModel extends ViewModel {

    class QueueItem {
        String appId;
        int number;
        int count;

        private QueueItem(String appId, int number, int count) {
            this.appId = appId;
            this.number = number;
            this.count = count;
        }
    }

    private MutableLiveData<QueueItem> queueItem;
    private SteamWebHandler webHandler;
    private Boolean result = false;

    void init(SteamWebHandler webHandler) {
        this.webHandler = webHandler;
    }

    LiveData<QueueItem> getQueueItem() {
        if (queueItem == null) {
            queueItem = new MutableLiveData<>();
            loadData();
        }

        return queueItem;
    }

    boolean getResult() {
        return result;
    }

    @SuppressLint("StaticFieldLeak")
    private void loadData() {
        new AsyncTask<Void,QueueItem,Boolean>() {
            @Override
            protected Boolean doInBackground(Void... voids) {
                try {
                    final JSONArray discoveryQueue = webHandler.generateNewDiscoveryQueue();
                    for (int i=0, count=discoveryQueue.length();i<count;i++) {
                        final String appId = discoveryQueue.getString(i);
                        publishProgress(new QueueItem(appId, i + 1, count));
                        webHandler.clearFromQueue(appId);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    return false;
                }
                return true;
            }

            @Override
            protected void onProgressUpdate(QueueItem... values) {
                queueItem.setValue(values[0]);
            }

            @Override
            protected void onPostExecute(Boolean result) {
                AutoDiscoverViewModel.this.result = result;
                queueItem.setValue(null);
            }
        }.execute();
    }
}
