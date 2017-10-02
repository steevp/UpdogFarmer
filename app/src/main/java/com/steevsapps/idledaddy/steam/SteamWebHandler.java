package com.steevsapps.idledaddy.steam;

import com.steevsapps.idledaddy.Secrets;
import com.steevsapps.idledaddy.steam.wrapper.Game;
import com.steevsapps.idledaddy.utils.Prefs;
import com.steevsapps.idledaddy.utils.Utils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import uk.co.thomasc.steamkit.steam3.steamclient.SteamClient;
import uk.co.thomasc.steamkit.steam3.webapi.WebAPI;
import uk.co.thomasc.steamkit.types.keyvalue.KeyValue;
import uk.co.thomasc.steamkit.util.KeyDictionary;
import uk.co.thomasc.steamkit.util.WebHelpers;
import uk.co.thomasc.steamkit.util.crypto.CryptoHelper;
import uk.co.thomasc.steamkit.util.crypto.RSACrypto;

/**
 * Scrapes card drop info from Steam website
 */
public class SteamWebHandler {
    private final static String BADGES = "http://steamcommunity.com/my/badges?l=english";
    private final static String INVENTORY = "http://steamcommunity.com/my/inventory";
    private final static String GAMES_OWNED = "http://api.steampowered.com/IPlayerService/GetOwnedGames/v0001/?key=%s&steamid=%d&include_appinfo=1&include_played_free_games=1&format=json";
    private final static String PARENTAL_UNLOCK = "http://store.steampowered.com/parental/ajaxunlock";
    private final static String FREE_LICENSE = "http://store.steampowered.com/checkout/addfreelicense";
    private final static String PROFILE = "http://steamcommunity.com/my/profile?l=english";

    // Pattern to match app ID
    private final static Pattern playPattern = Pattern.compile("^steam://run/(\\d+)$");
    // Pattern to match card drops remaining
    private final static Pattern dropPattern = Pattern.compile("^(\\d+) card drops? remaining$");
    // Pattern to match play time
    private final static Pattern timePattern = Pattern.compile("([0-9\\.]+) hrs on record");

    private final static SteamWebHandler ourInstance = new SteamWebHandler();

    private boolean authenticated;
    private long steamId;
    private String sessionId;
    private String token;
    private String tokenSecure;
    private String steamParental;

    private SteamWebHandler() {

    }

    static SteamWebHandler getInstance() {
        return ourInstance;
    }

    /**
     * Authenticate with the Steam website
     *
     * @param client the Steam client
     * @param webApiUserNonce the WebAPI User Nonce returned by LoggedOnCallback
     * @return true if authenticated
     */
    boolean authenticate(SteamClient client, String webApiUserNonce) {
        authenticated = false;
        steamId = client.getSteamId().convertToLong();
        sessionId = Utils.bytesToHex(CryptoHelper.GenerateRandomBlock(4));

        final WebAPI userAuth = new WebAPI("ISteamUserAuth", null);
        // generate an AES session key
        final byte[] sessionKey = CryptoHelper.GenerateRandomBlock(32);

        // rsa encrypt it with the public key for the universe we're on
        final byte[] publicKey = KeyDictionary.getPublicKey(client.getConnectedUniverse());
        if (publicKey == null) {
            return false;
        }
        final RSACrypto rsa = new RSACrypto(publicKey);
        final byte[] cryptedSessionKey = rsa.encrypt(sessionKey);

        final byte[] loginKey = new byte[20];
        System.arraycopy(webApiUserNonce.getBytes(), 0, loginKey, 0, webApiUserNonce.length());

        // aes encrypt the loginkey with our session key
        final byte[] cryptedLoginKey = CryptoHelper.SymmetricEncrypt(loginKey, sessionKey);

        final KeyValue authResult;
        try {
            authResult = userAuth.authenticateUser(String.valueOf(steamId), WebHelpers.UrlEncode(cryptedSessionKey), WebHelpers.UrlEncode(cryptedLoginKey), "POST", "true");
        } catch (final Exception e) {
            return false;
        }

        if (authResult == null) {
            return false;
        }

        token = authResult.get("token").asString();
        tokenSecure = authResult.get("tokenSecure").asString();

        authenticated = true;

        final String pin = Prefs.getParentalPin().trim();
        if (!pin.isEmpty()) {
            // Unlock family view
            steamParental = unlockParental(pin);
        }

        return true;
    }

    /**
     * Generate Steam web cookies
     * @return Map of the cookies
     */
    private Map<String,String> generateWebCookies() {
        if (!authenticated) {
            return new HashMap<>();
        }

        final Map<String, String> cookies = new HashMap<>();
        cookies.put("sessionid", sessionId);
        cookies.put("steamLogin", token);
        cookies.put("steamLoginSecure", tokenSecure);
        final String sentryHash = Prefs.getSentryHash().trim();
        if (!sentryHash.isEmpty()) {
            cookies.put("steamMachineAuth" + steamId, sentryHash);
        }
        if (steamParental != null) {
            cookies.put("steamparental", steamParental);
        }

        return cookies;
    }

    /**
     * Get a list of games with card drops remaining
     * @return list of games with remaining drops
     */
    List<Game> getRemainingGames() {
        final List<Game> badgeList = new ArrayList<>();
        Document doc;
        try {
            doc = Jsoup.connect(BADGES)
                    .followRedirects(true)
                    .cookies(generateWebCookies())
                    .get();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }

        final Element userAvatar = doc.select("a.user_avatar").first();
        if (userAvatar == null) {
            // Invalid cookie data
            return null;
        }

        final Elements badges = doc.select("div.badge_title_row");

        final Element pages = doc.select("a.pagelink").last();
        if (pages != null) {
            // Multiple pages
            final int p = Integer.parseInt(pages.text());
            // Try to combine all the pages
            for (int i=2;i<=p;i++) {
                try {
                    final  Document doc2 = Jsoup.connect(BADGES + "&p=" + i)
                            .followRedirects(true)
                            .cookies(generateWebCookies())
                            .get();
                    final Elements badges2 = doc2.select("div.badge_title_row");
                    badges.addAll(badges2);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        Matcher m;
        for (Element b: badges) {
            // Get app id
            final Element playGame = b.select("div.badge_title_playgame").first();
            if (playGame == null) {
                continue;
            }
            m = playPattern.matcher(playGame.select("a[href]").first().attr("href"));
            if (!m.find()) {
                continue;
            }
            final int appId = Integer.parseInt(m.group(1));

            // Get remaining card drops
            final Element progressInfo = b.select("span.progress_info_bold").first();
            if (progressInfo == null) {
                continue;
            }
            m = dropPattern.matcher(progressInfo.text());
            if (!m.find()) {
                continue;
            }
            final int drops = Integer.parseInt(m.group(1));

            // Get app name
            final Element badgeTitle = b.select("div.badge_title").first();
            if (badgeTitle == null) {
                continue;
            }
            final String name = badgeTitle.ownText().trim();

            // Get play time
            final Element playTime = b.select("div.badge_title_stats_playtime").first();
            if (playTime == null) {
                continue;
            }
            final String playTimeText = playTime.text().trim();
            m = timePattern.matcher(playTimeText);
            float time = 0;
            if (m.find()) {
                time = Float.parseFloat(m.group(1));
            }

            badgeList.add(new Game(appId, name, time, drops));
        }

        return badgeList;
    }

    /**
     * View inventory to clear notifications
     */
    void viewInventory() {
        try {
            Jsoup.connect(INVENTORY)
                    .followRedirects(true)
                    .cookies(generateWebCookies())
                    .get();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Unlock Steam parental controls with a pin
     */
    private String unlockParental(String pin) {
        try {
            final Map<String,String> responseCookies = Jsoup.connect(PARENTAL_UNLOCK)
                    .referrer("http://store.steampowered.com/")
                    .followRedirects(true)
                    .ignoreContentType(true)
                    .cookies(generateWebCookies())
                    .data("pin", pin)
                    .method(Connection.Method.POST)
                    .execute()
                    .cookies();
            return responseCookies.get("steamparental");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static List<Game> getGamesOwned(long steamId) {
        HttpURLConnection conn = null;
        try {
            final URL url = new URL(String.format(Locale.US, GAMES_OWNED, Secrets.API_KEY, steamId));
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            final BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            final StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
            final JSONObject json = new JSONObject(builder.toString());
            final List<Game> games = new ArrayList<>();
            final JSONArray arr = json.getJSONObject("response").getJSONArray("games");
            for (int i=0,size=arr.length();i<size;i++) {
                games.add(new Game(arr.getJSONObject(i)));
            }
            return games;
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * Check if user is currently NOT in-game, so we can resume farming.
     */
    Boolean checkIfNotInGame() {
        Document doc;
        try {
            doc = Jsoup.connect(PROFILE)
                    .followRedirects(true)
                    .cookies(generateWebCookies())
                    .get();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }

        final Element userAvatar = doc.select("a.user_avatar").first();
        if (userAvatar == null) {
            // Invalid cookie data
            return null;
        }

        return doc.select("div.profile_in_game_name").first() == null;
    }

    /**
     * Add a free license to your account
     *
     * @param subId subscription id
     * @return true if successful
     */
    boolean addFreeLicense(int subId) {
        try {
            final Document doc = Jsoup.connect(FREE_LICENSE)
                    .referrer("http://store.steampowered.com/")
                    .followRedirects(true)
                    .cookies(generateWebCookies())
                    .data("sessionid", sessionId)
                    .data("subid", String.valueOf(subId))
                    .data("action", "add_to_cart")
                    .post();
            return doc.select("div.add_free_content_success_area").first() != null;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }
}
