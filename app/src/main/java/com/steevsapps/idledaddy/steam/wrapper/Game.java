package com.steevsapps.idledaddy.steam.wrapper;

import android.support.annotation.NonNull;

import org.json.JSONObject;

import java.util.Locale;

public class Game implements Comparable<Game> {
    private final static String IMG_URL = "http://media.steampowered.com/steamcommunity/public/images/apps/%d/%s.jpg";
    public int appId;
    public String name;
    public String iconUrl;
    public float hoursPlayed;
    public int dropsRemaining;

    public Game(JSONObject obj) {
        appId = obj.optInt("appid", 0);
        name = obj.optString("name", "Unknown app");
        iconUrl = String.format(Locale.US, IMG_URL, appId, obj.optString("img_logo_url"));
        hoursPlayed = 0;
        dropsRemaining = 0;
    }

    public Game(int appId, String name, float hoursPlayed, int dropsRemaining) {
        this.appId = appId;
        this.name = name;
        this.iconUrl = "http://cdn.akamai.steamstatic.com/steam/apps/" + appId + "/header_292x136.jpg";
        this.hoursPlayed = hoursPlayed;
        this.dropsRemaining = dropsRemaining;
    }

    @Override
    public int compareTo(@NonNull Game game) {
        if (hoursPlayed == game.hoursPlayed) {
            return 0;
        }
        return hoursPlayed < game.hoursPlayed ? -1 : 1;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (obj.getClass() != getClass()) {
            return false;
        }
        final Game otherGame = (Game) obj;
        return otherGame.appId == appId && otherGame.name.equals(name) &&
                otherGame.iconUrl.equals(iconUrl) && otherGame.hoursPlayed == hoursPlayed &&
                otherGame.dropsRemaining == dropsRemaining;
    }

    @Override
    public int hashCode() {
        // Start with a non-zero constant. Prime is preferred
        int result = 17;
        // Include a hash for each field
        result = 31 * result + appId;
        result = 31 * result + name.hashCode();
        result = 31 * result + iconUrl.hashCode();
        result = 31 * result + Float.floatToIntBits(hoursPlayed);
        result = 31 * result + dropsRemaining;
        return result;
    }
}
