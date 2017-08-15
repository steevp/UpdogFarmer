package com.steevsapps.idledaddy.listeners;

import com.steevsapps.idledaddy.steam.wrapper.Game;

import java.util.List;

public interface FetchGamesListener {
    void onGamesListReceived(List<Game> games);
}
