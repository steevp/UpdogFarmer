package com.steevsapps.idledaddy.steam.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class GamesOwnedResponse {
    @SerializedName("game_count")
    private int count;

    @SerializedName("games")
    private List<Game> games;

    public int getCount() {
        return count;
    }

    public List<Game> getGames() {
        return games;
    }
}
