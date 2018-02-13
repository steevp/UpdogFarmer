package com.steevsapps.idledaddy.db.converter;

import android.arch.persistence.room.TypeConverter;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class StringArrayTypeConverter {

    @TypeConverter
    public static String toJson(List<String> list) {
        return new Gson().toJson(list);
    }

    @TypeConverter
    public static List<String> fromJson(String json) {
        final Type type = new TypeToken<List<String>>(){}.getType();
        final List<String> list = new Gson().fromJson(json, type);
        return list != null ? list : new ArrayList<String>();
    }

}
