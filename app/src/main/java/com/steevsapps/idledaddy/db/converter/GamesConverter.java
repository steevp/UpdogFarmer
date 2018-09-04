package com.steevsapps.idledaddy.db.converter;

import android.arch.persistence.room.TypeConverter;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.steevsapps.idledaddy.steam.model.Game;

import java.lang.reflect.Type;
import java.util.List;

public class GamesConverter {
    @TypeConverter
    public static String toJson(List<Game> games) {
        return new Gson().toJson(games);
    }

    @TypeConverter
    public static List<Game> fromJson(String json) {
        final Type type = new TypeToken<List<Game>>(){}.getType();
        return new Gson().fromJson(json, type);
    }
}
