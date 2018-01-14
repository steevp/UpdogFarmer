package com.steevsapps.idledaddy.adapters;

import android.support.v7.util.DiffUtil;

import com.steevsapps.idledaddy.steam.wrapper.Game;

import java.util.List;

class GamesDiffCallback extends DiffUtil.Callback {
    private List<Game> oldGames;
    private List<Game> newGames;

    GamesDiffCallback(List<Game> newGames, List<Game> oldGames) {
        this.newGames = newGames;
        this.oldGames = oldGames;
    }

    @Override
    public int getOldListSize() {
        return oldGames.size();
    }

    @Override
    public int getNewListSize() {
        return newGames.size();
    }

    @Override
    public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
        return oldGames.get(oldItemPosition).appId == newGames.get(newItemPosition).appId;
    }

    @Override
    public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
        final Game oldGame = oldGames.get(oldItemPosition);
        final Game newGame = newGames.get(newItemPosition);
        return oldGame.appId == newGame.appId && oldGame.hoursPlayed == newGame.hoursPlayed;
    }
}
