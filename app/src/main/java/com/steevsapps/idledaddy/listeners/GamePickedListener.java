package com.steevsapps.idledaddy.listeners;

import com.steevsapps.idledaddy.steam.wrapper.Game;

public interface GamePickedListener {
    void onGamePicked(Game game);
    void onGameRemoved(Game game);
}
