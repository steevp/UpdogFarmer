package com.steevsapps.idledaddy.preferences;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import java.util.UUID;

/**
 * SharedPreferences manager
 */
public class PrefsManager {
    private final static String CURRENT_USER = "current_user";
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
    private final static String INCLUDE_FREE_GAMES = "include_free_games";
    private final static String PERSONA_NAME = "persona_name";
    private final static String AVATAR_HASH = "avatar_hash";
    private final static String API_KEY = "api_key";
    private final static String LANGUAGE = "language";

    private static SharedPreferences prefs;

    private PrefsManager() {
    }

    public static void init(Context c) {
        if (prefs == null) {
            prefs = PreferenceManager.getDefaultSharedPreferences(c);

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
                .putString(PERSONA_NAME, "")
                .putString(AVATAR_HASH, "")
                .putString(API_KEY, "")
                .apply();
    }

    public static SharedPreferences getPrefs() {
        return prefs;
    }

    public static void writeCurrentUser(String username) {
        writePref(CURRENT_USER, username);
    }

    public static void writeCmServers(String servers) {
        writePref(CM_SERVERS, servers);
    }

    public static void writeLanguage(String language) {
        writePref(LANGUAGE, language);
    }

    public static String getCurrentUser() {
        return prefs.getString(CURRENT_USER, "");
    }

    public static String getMachineId() {
        return prefs.getString(MACHINE_ID, "");
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

    public static int getHoursUntilDrops() {
        return prefs.getInt(HOURS_UNTIL_DROPS, 3);
    }

    public static boolean includeFreeGames() {
        return prefs.getBoolean(INCLUDE_FREE_GAMES, false);
    }

    public static String getLanguage() {
        return prefs.getString(LANGUAGE, "");
    }

    private static void writePref(String key, String value) {
        final SharedPreferences.Editor editor = prefs.edit();
        editor.putString(key, value);
        editor.apply();
    }
}
