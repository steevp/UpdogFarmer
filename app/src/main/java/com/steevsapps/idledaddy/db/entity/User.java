package com.steevsapps.idledaddy.db.entity;

import android.arch.persistence.room.ColumnInfo;
import android.arch.persistence.room.Entity;
import android.arch.persistence.room.PrimaryKey;
import android.support.annotation.NonNull;

import com.steevsapps.idledaddy.steam.wrapper.Game;

import java.util.ArrayList;
import java.util.List;

@Entity(tableName = "users")
public class User {
    @PrimaryKey
    @ColumnInfo(name = "username")
    @NonNull
    private String username = "";

    @ColumnInfo(name = "persona_name")
    private String personaName = "";

    @ColumnInfo(name = "avatar_hash")
    private String avatarHash = "";

    @ColumnInfo(name = "login_key")
    private String loginKey = "";

    @ColumnInfo(name = "sentry_hash")
    private String sentryHash = "";

    @ColumnInfo(name = "blacklist")
    private List<String> blacklist;

    @ColumnInfo(name = "last_session")
    private ArrayList<Game> lastSession;

    @ColumnInfo(name = "parental_pin")
    private String parentalPin = "";

    @ColumnInfo(name = "api_key")
    private String apiKey = "";

    @ColumnInfo(name = "steamid")
    private long steamId;

    @NonNull
    public String getUsername() {
        return username;
    }

    public void setUsername(@NonNull String username) {
        this.username = username;
    }

    public String getPersonaName() {
        return personaName;
    }

    public void setPersonaName(String personaName) {
        this.personaName = personaName;
    }

    public String getAvatarHash() {
        return avatarHash;
    }

    public void setAvatarHash(String avatarHash) {
        this.avatarHash = avatarHash;
    }

    public String getLoginKey() {
        return loginKey;
    }

    public void setLoginKey(String loginKey) {
        this.loginKey = loginKey;
    }

    public String getSentryHash() {
        return sentryHash;
    }

    public void setSentryHash(String sentryHash) {
        this.sentryHash = sentryHash;
    }

    public List<String> getBlacklist() {
        return blacklist;
    }

    public void setBlacklist(List<String> blacklist) {
        this.blacklist = blacklist;
    }

    public ArrayList<Game> getLastSession() {
        return lastSession;
    }

    public void setLastSession(ArrayList<Game> lastSession) {
        this.lastSession = lastSession;
    }

    public String getParentalPin() {
        return parentalPin;
    }

    public void setParentalPin(String parentalPin) {
        this.parentalPin = parentalPin;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public long getSteamId() {
        return steamId;
    }

    public void setSteamId(long steamId) {
        this.steamId = steamId;
    }
}
