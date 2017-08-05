package com.steevsapps.idledaddy.steam.wrapper;

import android.support.annotation.NonNull;

public class Badge implements Comparable<Badge> {
    public int appId;
    public String name;
    public String iconUrl;
    public float hoursPlayed;
    public int dropsRemaining;

    public Badge(int appId, String name, float hoursPlayed, int dropsRemaining) {
        this.appId = appId;
        this.name = name;
        this.iconUrl = "http://cdn.akamai.steamstatic.com/steam/apps/" + appId + "/header_292x136.jpg";
        this.hoursPlayed = hoursPlayed;
        this.dropsRemaining = dropsRemaining;
    }

    @Override
    public int compareTo(@NonNull Badge o) {
        if (hoursPlayed == o.hoursPlayed) {
            return 0;
        }
        return hoursPlayed < o.hoursPlayed ? -1 : 1;
    }
}