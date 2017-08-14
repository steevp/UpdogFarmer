package com.steevsapps.idledaddy.steam.wrapper;

import org.json.JSONObject;

import java.util.Locale;

public class Game {
    private final static String IMG_URL = "http://media.steampowered.com/steamcommunity/public/images/apps/%d/%s.jpg";
    public int appId;
    public String name;
    public String logoUrl;

    public Game(JSONObject obj) {
        appId = obj.optInt("appid", 0);
        name = obj.optString("name", "Unknown app");
        logoUrl = String.format(Locale.US, IMG_URL, appId, obj.optString("img_logo_url"));
    }
}
