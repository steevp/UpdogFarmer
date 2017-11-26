package com.steevsapps.idledaddy.fragments;

import android.annotation.SuppressLint;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.arch.lifecycle.ViewModel;
import android.os.AsyncTask;

import com.steevsapps.idledaddy.steam.SteamWebHandler;
import com.steevsapps.idledaddy.steam.wrapper.Game;

import java.util.List;

class GamesViewModel extends ViewModel {
    private long steamId;
    private MutableLiveData<List<Game>> games;

    void init(long steamId) {
        this.steamId = steamId;
    }

    LiveData<List<Game>> getGames() {
        if (games == null) {
            games = new MutableLiveData<>();
            fetchGames();
        }
        return games;
    }

    @SuppressLint("StaticFieldLeak")
    void fetchGames() {
        new AsyncTask<Void,Void,List<Game>>() {
            @Override
            protected List<Game> doInBackground(Void... voids) {
                return SteamWebHandler.getGamesOwned(steamId);
            }

            @Override
            protected void onPostExecute(List<Game> games) {
                GamesViewModel.this.games.setValue(games);
            }
        }.execute();
    }
}
