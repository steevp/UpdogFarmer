package com.steevsapps.idledaddy.listeners;

import com.steevsapps.idledaddy.steam.wrapper.Game;

import java.util.List;

public interface GamePickedListener {
    void onGamePicked(Game game);
    void onGamesPicked(List<Game> games);
    void onGameRemoved(Game game);
    void onGameLongPressed(Game game);
}
