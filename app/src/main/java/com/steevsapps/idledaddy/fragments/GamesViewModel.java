package com.steevsapps.idledaddy.fragments;

import android.annotation.SuppressLint;
import android.app.Application;
import android.arch.lifecycle.AndroidViewModel;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.os.AsyncTask;
import android.support.annotation.NonNull;

import com.steevsapps.idledaddy.IdleDaddy;
import com.steevsapps.idledaddy.SpinnerTabIds;
import com.steevsapps.idledaddy.UserRepository;
import com.steevsapps.idledaddy.db.entity.User;
import com.steevsapps.idledaddy.steam.SteamWebHandler;
import com.steevsapps.idledaddy.steam.wrapper.Game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Reminder: Must be public or else you will get a runtime exception
 */
public class GamesViewModel extends AndroidViewModel {
    private final static int SORT_ALPHABETICALLY = 0;
    private final static int SORT_HOURS_PLAYED = 1;

    private final SteamWebHandler webHandler;
    private User currentUser;
    private int currentTab;
    private boolean initialized = false;

    private final MutableLiveData<List<Game>> games = new MutableLiveData<>();
    private int sortId = SORT_ALPHABETICALLY;

    public GamesViewModel(@NonNull Application application) {
        super(application);
        final UserRepository userRepo = ((IdleDaddy) application).getRepository();
        webHandler = SteamWebHandler.getInstance(userRepo);
    }

    void init(User user, @SpinnerTabIds int tabId) {
        if (currentUser != null) {
            return;
        }
        currentUser = user;
        currentTab = tabId;
        initialized = true;
        fetchGames();
    }

    LiveData<List<Game>> getGames() {
        return games;
    }

    void setCurrentTab(@SpinnerTabIds int tabId) {
        currentTab = tabId;
        fetchGames();
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

        if (currentTab == SpinnerTabIds.BLACKLIST) {
            // Only show blacklisted games
            final List<String> blacklist = currentUser.getBlacklist();
            final List<Game> blacklistGames = new ArrayList<>();
            for (Game game : games) {
                if (blacklist.contains(String.valueOf(game.appId))) {
                    blacklistGames.add(game);
                }
            }
            this.games.setValue(blacklistGames);
        } else {
            this.games.setValue(games);
        }
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

    @SuppressLint("StaticFieldLeak")
    void fetchGames() {
        if (!initialized) {
            // Not ready yet
            return;
        }
        if (currentTab == SpinnerTabIds.LAST_SESSION) {
            // Load last idling session
            setGames(currentUser.getLastSession());
        } else {
            // Fetch games from web
            new AsyncTask<Void,Void,List<Game>>() {
                @Override
                protected List<Game> doInBackground(Void... voids) {
                    return webHandler.getGamesOwned(currentUser.getSteamId());
                }

                @Override
                protected void onPostExecute(List<Game> games) {
                    setGames(games);
                }
            }.execute();
        }
    }
}
