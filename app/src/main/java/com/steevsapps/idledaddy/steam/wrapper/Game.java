package com.steevsapps.idledaddy.steam.wrapper;

import android.os.Parcel;
import android.os.Parcelable;

import org.json.JSONObject;

import java.util.Locale;

public class Game implements Parcelable {
    private final static String IMG_URL = "http://media.steampowered.com/steamcommunity/public/images/apps/%d/%s.jpg";
    public int appId;
    public String name;
    public String logoUrl;

    public final static Creator<Game> CREATOR = new Creator<Game>() {
        @Override
        public Game createFromParcel(Parcel parcel) {
            return new Game(parcel);
        }

        @Override
        public Game[] newArray(int i) {
            return new Game[i];
        }
    };

    public Game(JSONObject obj) {
        appId = obj.optInt("appid", 0);
        name = obj.optString("name", "Unknown app");
        logoUrl = String.format(Locale.US, IMG_URL, appId, obj.optString("img_logo_url"));
    }

    private Game(Parcel parcel) {
        appId = parcel.readInt();
        name = parcel.readString();
        logoUrl = parcel.readString();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeInt(appId);
        parcel.writeString(name);
        parcel.writeString(logoUrl);
    }
}
