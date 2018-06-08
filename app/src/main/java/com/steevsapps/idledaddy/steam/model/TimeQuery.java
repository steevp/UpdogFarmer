package com.steevsapps.idledaddy.steam.model;

import com.google.gson.annotations.SerializedName;

public class TimeQuery {
    @SerializedName("response")
    private TimeResponse response;

    public TimeResponse getResponse() {
        return response;
    }
}
