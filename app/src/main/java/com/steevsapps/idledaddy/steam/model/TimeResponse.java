package com.steevsapps.idledaddy.steam.model;

import com.google.gson.annotations.SerializedName;

public class TimeResponse {
    @SerializedName("server_time")
    private long serverTime;

    public long getServerTime() {
        return serverTime;
    }
}
