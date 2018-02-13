package com.steevsapps.idledaddy.db.converter;

import android.arch.persistence.room.TypeConverter;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.steevsapps.idledaddy.steam.wrapper.Game;

import java.lang.reflect.Type;
import java.util.ArrayList;

public class GamesTypeConverter {

    @TypeConverter
    public static String toJson(ArrayList<Game> games) {
        return new Gson().toJson(games);
    }

    @TypeConverter
    public static ArrayList<Game> fromJson(String json) {
        final Type type = new TypeToken<ArrayList<Game>>(){}.getType();
        final ArrayList<Game> games = new Gson().fromJson(json, type);
        return games != null ? games : new ArrayList<Game>();
    }

}
