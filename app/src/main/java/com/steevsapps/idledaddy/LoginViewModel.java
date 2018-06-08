package com.steevsapps.idledaddy;

import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.arch.lifecycle.ViewModel;
import android.util.Log;

import com.steevsapps.idledaddy.steam.SteamWebHandler;
import com.steevsapps.idledaddy.steam.model.TimeQuery;
import com.steevsapps.idledaddy.utils.Utils;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginViewModel extends ViewModel {
    private final static String TAG = LoginViewModel.class.getSimpleName();

    private SteamWebHandler webHandler;
    private final MutableLiveData<Integer> timeDifference = new MutableLiveData<>();

    private boolean timeAligned = false;

    void init(SteamWebHandler webHandler) {
        this.webHandler = webHandler;
    }

    LiveData<Integer> getTimeDifference() {
        if (!timeAligned) {
            alignTime();
        }
        return timeDifference;
    }

    private void alignTime() {
        final long currentTime = Utils.getCurrentUnixTime();
        webHandler.queryServerTime().enqueue(new Callback<TimeQuery>() {
            @Override
            public void onResponse(Call<TimeQuery> call, Response<TimeQuery> response) {
                if (response.isSuccessful()) {
                    timeDifference.setValue((int) (response.body().getResponse().getServerTime() - currentTime));
                    timeAligned = true;
                }
            }

            @Override
            public void onFailure(Call<TimeQuery> call, Throwable t) {
                Log.e(TAG, "Failed to get server time", t);
            }
        });
    }
}
