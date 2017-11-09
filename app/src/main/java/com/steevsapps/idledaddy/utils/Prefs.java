package com.steevsapps.idledaddy.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.steevsapps.idledaddy.steam.wrapper.Game;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * SharedPreferences manager
 */
public class Prefs {
    private final static String USERNAME = "username";
    private final static String LOGIN_KEY = "login_key";
    private final static String MACHINE_ID = "machine_id";
    private final static String SENTRY_HASH = "sentry_hash";
    private final static String CM_SERVERS = "cm_servers";
    private final static String OFFLINE = "offline";
    private final static String STAY_AWAKE = "stay_awake";
    private final static String MINIMIZE_DATA = "minimize_data";
    private final static String PARENTAL_PIN = "parental_pin";
    private final static String BLACKLIST = "blacklist";
    private final static String LAST_SESSION = "last_session";
    private final static String HOURS_UNTIL_DROPS = "hours_until_drops";

    private static SharedPreferences prefs;

    private Prefs() {
    }

    public static void init(Context c) {
        if (prefs == null) {
            prefs = PreferenceManager.getDefaultSharedPreferences(c.getApplicationContext());

            if (getMachineId().isEmpty()) {
                // Generate machine id
                writePref(MACHINE_ID, UUID.randomUUID().toString());
            }
        }
    }

    /**
     * Clear all preferences related to user
     */
    public static void clearUser() {
        final SharedPreferences.Editor editor = prefs.edit();
        editor.putString(USERNAME, "")
                .putString(LOGIN_KEY, "")
                .putString(SENTRY_HASH, "")
                .putString(BLACKLIST, "")
                .putString(LAST_SESSION, "")
                .putString(PARENTAL_PIN, "")
                .apply();
    }

    public static SharedPreferences getPrefs() {
        return prefs;
    }

    public static void writeUsername(String username) {
        writePref(USERNAME, username);
    }

    public static void writeLoginKey(String loginKey) {
        writePref(LOGIN_KEY, loginKey);
    }

    public static void writeSentryHash(String sentryHash) {
        writePref(SENTRY_HASH, sentryHash);
    }

    public static void writeCmServers(String servers) {
        writePref(CM_SERVERS, servers);
    }

    public static void writeBlacklist(List<String> blacklist) {
        writePref(BLACKLIST, Utils.arrayToString(blacklist));
    }

    public static void writeLastSession(List<Game> games) {
        final String json = new Gson().toJson(games);
        writePref(LAST_SESSION, json);
    }

    public static String getUsername() {
        return prefs.getString(USERNAME, "");
    }

    public static String getLoginKey() {
        return prefs.getString(LOGIN_KEY, "");
    }

    public static String getMachineId() {
        return prefs.getString(MACHINE_ID, "");
    }

    public static String getSentryHash() {
        return prefs.getString(SENTRY_HASH, "");
    }

    public static String getCmServers() {
        return prefs.getString(CM_SERVERS, "");
    }

    public static boolean getOffline() {
        return prefs.getBoolean(OFFLINE, false);
    }

    public static boolean stayAwake() {
        return prefs.getBoolean(STAY_AWAKE, false);
    }

    public static boolean minimizeData() { return prefs.getBoolean(MINIMIZE_DATA, false); }

    public static String getParentalPin() {
        return prefs.getString(PARENTAL_PIN, "");
    }

    public static List<String> getBlacklist() {
        final String[] blacklist = prefs.getString(BLACKLIST, "").split(",");
        return new ArrayList<>(Arrays.asList(blacklist));
    }

    public static List<Game> getLastSession() {
        final String json = prefs.getString(LAST_SESSION, "");
        final Type type = new TypeToken<List<Game>>(){}.getType();
        final List<Game> games = new Gson().fromJson(json, type);
        if (games == null) {
            return new ArrayList<>();
        }
        return games;
    }

    public static int getHoursUntilDrops() {
        return Integer.parseInt(prefs.getString(HOURS_UNTIL_DROPS, "3"));
    }

    private static void writePref(String key, String value) {
        final SharedPreferences.Editor editor = prefs.edit();
        editor.putString(key, value);
        editor.apply();
    }
}
