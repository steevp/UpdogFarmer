package com.steevsapps.idledaddy.fragments;

import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.arch.lifecycle.ViewModel;

import com.steevsapps.idledaddy.steam.SteamWebHandler;
import com.steevsapps.idledaddy.steam.model.Game;
import com.steevsapps.idledaddy.steam.model.GamesOwnedResponse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Reminder: Must be public or else you will get a runtime exception
 */
public class GamesViewModel extends ViewModel {
    private final static int SORT_ALPHABETICALLY = 0;
    private final static int SORT_HOURS_PLAYED = 1;

    private SteamWebHandler webHandler;
    private long steamId;
    private MutableLiveData<List<Game>> games;
    private int sortId = SORT_ALPHABETICALLY;

    void init(SteamWebHandler webHandler, long steamId) {
        this.webHandler = webHandler;
        this.steamId = steamId;
    }

    LiveData<List<Game>> getGames() {
        if (games == null) {
            games = new MutableLiveData<>();
        }
        return games;
    }

    void setGames(List<Game> games) {
        if (sortId == SORT_ALPHABETICALLY) {
            Collections.sort(games, new Comparator<Game>() {
                @Override
                public int compare(Game game1, Game game2) {
                    return game1.name.toLowerCase().compareTo(game2.name.toLowerCase());
                }
            });
        } else if (sortId == SORT_HOURS_PLAYED) {
            Collections.sort(games, Collections.reverseOrder());
        }
        this.games.setValue(games);
    }

    void sortAlphabetically() {
        if (sortId == SORT_ALPHABETICALLY) {
            return;
        }

        final List<Game> games = this.games.getValue();
        if (games != null && !games.isEmpty()) {
            sortId = SORT_ALPHABETICALLY;
            setGames(games);
        }
    }

    void sortHoursPlayed() {
        if (sortId == SORT_HOURS_PLAYED) {
            return;
        }

        final List<Game> games = this.games.getValue();
        if (games != null && !games.isEmpty()) {
            sortId = SORT_HOURS_PLAYED;
            setGames(games);
        }
    }

    void fetchGames() {
        webHandler.getGamesOwned(steamId).enqueue(new Callback<GamesOwnedResponse>() {
            @Override
            public void onResponse(Call<GamesOwnedResponse> call, Response<GamesOwnedResponse> response) {
                setGames(response.body().getGames());
            }

            @Override
            public void onFailure(Call<GamesOwnedResponse> call, Throwable t) {
                setGames(new ArrayList<>());
            }
        });
    }
}
