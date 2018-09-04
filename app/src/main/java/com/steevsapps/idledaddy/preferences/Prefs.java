package com.steevsapps.idledaddy.preferences;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.steevsapps.idledaddy.utils.Utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * SharedPreferences manager
 */
public class Prefs {
    private final static int CURRENT_VERSION = 2;

    //private final static String USERNAME = "username";
    //private final static String PASSWORD = "password";
    //private final static String LOGIN_KEY = "login_key";
    //private final static String SENTRY_HASH = "sentry_hash";
    private final static String SHARED_SECRET = "shared_secret";
    private final static String OFFLINE = "offline";
    private final static String STAY_AWAKE = "stay_awake";
    private final static String MINIMIZE_DATA = "minimize_data";
    //private final static String PARENTAL_PIN = "parental_pin";
    private final static String BLACKLIST = "blacklist";
    //private final static String LAST_SESSION = "last_session";
    private final static String HOURS_UNTIL_DROPS = "hours_until_drops";
    private final static String INCLUDE_FREE_GAMES = "include_free_games";
    private final static String USE_CUSTOM_LOGINID = "use_custom_loginid";
    //private final static String PERSONA_NAME = "persona_name";
    //private final static String AVATAR_HASH = "avatar_hash";
    //private final static String API_KEY = "api_key";
    private final static String LANGUAGE = "language";
    private final static String VERSION = "version";

    private static SharedPreferences prefs;

    private Prefs() {
    }

    public static void init(Context c) {
        if (prefs == null) {
            prefs = PreferenceManager.getDefaultSharedPreferences(c);
        }

        if (getVersion() != CURRENT_VERSION) {
            onUpgrade(getVersion());
        }
    }

    private static void onUpgrade(int oldVersion) {
        if (oldVersion < 2) {
            // Serialized names have changed
            //setLastSession(new ArrayList<>());
        }
        setVersion(CURRENT_VERSION);
    }

    /**
     * Clear all preferences related to user
     */
    /*public static void clearUser() {
        prefs.edit()
                //.putString(USERNAME, "")
                //.putString(PASSWORD, "")
                //.putString(LOGIN_KEY, "")
                //.putString(SENTRY_HASH, "")
                .putString(BLACKLIST, "")
                //.putString(LAST_SESSION, "")
                //.putString(PARENTAL_PIN, "")
                //.putString(PERSONA_NAME, "")
                //.putString(AVATAR_HASH, "")
                //.putString(API_KEY, "")
                .apply();
    }*/

    public static SharedPreferences getPrefs() {
        return prefs;
    }

    public static void setSharedSecret(String sharedSecret) {
        setPref(SHARED_SECRET, sharedSecret);
    }

    public static void setBlacklist(List<String> blacklist) {
        setPref(BLACKLIST, Utils.arrayToString(blacklist));
    }

    public static void setLanguage(String language) {
        setPref(LANGUAGE, language);
    }

    public static void setVersion(int version) {
        setPref(VERSION, version);
    }

    public static String getSharedSecret() {
        return prefs.getString(SHARED_SECRET, "");
    }

    public static boolean getOffline() {
        return prefs.getBoolean(OFFLINE, false);
    }

    public static boolean stayAwake() {
        return prefs.getBoolean(STAY_AWAKE, false);
    }

    public static boolean minimizeData() { return prefs.getBoolean(MINIMIZE_DATA, false); }

    public static List<String> getBlacklist() {
        final String[] blacklist = prefs.getString(BLACKLIST, "").split(",");
        return new ArrayList<>(Arrays.asList(blacklist));
    }

    public static int getHoursUntilDrops() {
        return prefs.getInt(HOURS_UNTIL_DROPS, 3);
    }

    public static boolean includeFreeGames() {
        return prefs.getBoolean(INCLUDE_FREE_GAMES, false);
    }

    public static boolean useCustomLoginId() {
        return prefs.getBoolean(USE_CUSTOM_LOGINID, false);
    }

    public static String getLanguage() {
        return prefs.getString(LANGUAGE, "");
    }

    public static int getVersion() {
        return prefs.getInt(VERSION, 1);
    }

    private static void setPref(String key, String value) {
        prefs.edit().putString(key, value).apply();
    }

    private static void setPref(String key, int value) {
        prefs.edit().putInt(key, value).apply();
    }
}
