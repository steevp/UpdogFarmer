package com.steevsapps.idledaddy.db.entity;

import android.arch.persistence.room.ColumnInfo;
import android.arch.persistence.room.Entity;
import android.arch.persistence.room.PrimaryKey;
import android.support.annotation.NonNull;

import com.steevsapps.idledaddy.steam.model.Game;

import java.util.List;

@Entity(tableName = "user")
public class User {
    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "username")
    private String username = "";

    @ColumnInfo(name = "password")
    private String password = "";

    @ColumnInfo(name = "steamid")
    private long steamId;

    @ColumnInfo(name = "api_key")
    private String apiKey = "";

    @ColumnInfo(name = "login_key")
    private String loginKey = "";

    @ColumnInfo(name = "sentry_hash")
    private String sentryHash = "";

    @ColumnInfo(name = "parental_pin")
    private String parentalPin = "";

    @ColumnInfo(name = "persona_name")
    private String personaName = "";

    @ColumnInfo(name = "avatar_hash")
    private String avatarHash = "";

    @ColumnInfo(name = "last_session")
    private List<Game> lastSession;

    public User(@NonNull String username) {
        this.username = username;
    }

    @NonNull
    public String getUsername() {
        return username;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getPassword() {
        return password;
    }

    public void setSteamId(long steamId) {
        this.steamId = steamId;
    }

    public long getSteamId() {
        return steamId;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setLoginKey(String loginKey) {
        this.loginKey = loginKey;
    }

    public String getLoginKey() {
        return loginKey;
    }

    public void setSentryHash(String sentryHash) {
        this.sentryHash = sentryHash;
    }

    public String getSentryHash() {
        return sentryHash;
    }

    public void setParentalPin(String parentalPin) {
        this.parentalPin = parentalPin;
    }

    public String getParentalPin() {
        return parentalPin;
    }

    public void setPersonaName(String personaName) {
        this.personaName = personaName;
    }

    public String getPersonaName() {
        return personaName;
    }

    public void setAvatarHash(String avatarHash) {
        this.avatarHash = avatarHash;
    }

    public String getAvatarHash() {
        return avatarHash;
    }

    public void setLastSession(List<Game> lastSession) {
        this.lastSession = lastSession;
    }

    public List<Game> getLastSession() {
        return lastSession;
    }

    public boolean canLogOn() {
        return !getUsername().isEmpty() && !getLoginKey().isEmpty();
    }
}
