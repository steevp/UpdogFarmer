package com.steevsapps.idledaddy.steam;

import android.support.annotation.IntDef;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.steevsapps.idledaddy.Secrets;
import com.steevsapps.idledaddy.preferences.PrefsManager;
import com.steevsapps.idledaddy.steam.converter.GamesOwnedResponseDeserializer;
import com.steevsapps.idledaddy.steam.converter.VdfConverterFactory;
import com.steevsapps.idledaddy.steam.model.Game;
import com.steevsapps.idledaddy.steam.model.GamesOwnedResponse;
import com.steevsapps.idledaddy.utils.Utils;
import com.steevsapps.idledaddy.utils.WebHelpers;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import in.dragonbra.javasteam.steam.steamclient.SteamClient;
import in.dragonbra.javasteam.types.KeyValue;
import in.dragonbra.javasteam.types.SteamID;
import in.dragonbra.javasteam.util.KeyDictionary;
import in.dragonbra.javasteam.util.crypto.CryptoException;
import in.dragonbra.javasteam.util.crypto.CryptoHelper;
import in.dragonbra.javasteam.util.crypto.RSACrypto;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Scrapes card drop info from Steam website
 */
public class SteamWebHandler {
    private final static String STEAM_STORE = "https://store.steampowered.com/";
    private final static String STEAM_COMMUNITY = "https://steamcommunity.com/";
    private final static String STEAM_API = "https://api.steampowered.com/";

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
    private String apiKey = Secrets.API_KEY;

    private final SteamAPI api;

    private SteamWebHandler() {
        final Gson gson = new GsonBuilder()
                .registerTypeAdapter(GamesOwnedResponse.class, new GamesOwnedResponseDeserializer())
                .create();

        final Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(STEAM_API)
                .addConverterFactory(VdfConverterFactory.create())
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build();

        api = retrofit.create(SteamAPI.class);
    }

    public static SteamWebHandler getInstance() {
        return ourInstance;
    }

    /**
     * Authenticate on the Steam website
     *
     * @param client the Steam client
     * @param webApiUserNonce the WebAPI User Nonce returned by LoggedOnCallback
     * @return true if authenticated
     */
    boolean authenticate(SteamClient client, String webApiUserNonce) {
        authenticated = false;
        final SteamID clientSteamId = client.getSteamID();
        if (clientSteamId == null) {
            return false;
        }
        steamId = clientSteamId.convertToUInt64();
        sessionId = Utils.bytesToHex(CryptoHelper.generateRandomBlock(4));

        // generate an AES session key
        final byte[] sessionKey = CryptoHelper.generateRandomBlock(32);

        // rsa encrypt it with the public key for the universe we're on
        final byte[] publicKey = KeyDictionary.getPublicKey(client.getUniverse());
        if (publicKey == null) {
            return false;
        }

        final RSACrypto rsa = new RSACrypto(publicKey);
        final byte[] cryptedSessionKey = rsa.encrypt(sessionKey);

        final byte[] loginKey = new byte[20];
        System.arraycopy(webApiUserNonce.getBytes(), 0, loginKey, 0, webApiUserNonce.length());

        // aes encrypt the loginkey with our session key
        final byte[] cryptedLoginKey;
        try {
            cryptedLoginKey = CryptoHelper.symmetricEncrypt(loginKey, sessionKey);
        } catch (CryptoException e) {
            e.printStackTrace();
            return false;
        }

        final KeyValue authResult;

        final Map<String,String> args = new HashMap<>();
        args.put("steamid", String.valueOf(steamId));
        args.put("sessionkey", WebHelpers.urlEncode(cryptedSessionKey));
        args.put("encrypted_loginkey", WebHelpers.urlEncode(cryptedLoginKey));
        args.put("format", "vdf");

        try {
            authResult = api.authenticateUser(args).execute().body();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        if (authResult == null) {
            return false;
        }

        token = authResult.get("token").asString();
        tokenSecure = authResult.get("tokenSecure").asString();

        authenticated = true;

        final String pin = PrefsManager.getParentalPin().trim();
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
        final String sentryHash = PrefsManager.getSentryHash().trim();
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
        final String url = STEAM_COMMUNITY + "my/badges?l=english";
        final List<Game> badgeList = new ArrayList<>();
        Document doc;
        try {
            doc = Jsoup.connect(url)
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
                    final  Document doc2 = Jsoup.connect(url + "&p=" + i)
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

        final List<String> blacklist = PrefsManager.getBlacklist();
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

            if (blacklist.contains(m.group(1))) {
                // Skip appids in the blacklist
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
     * Unlock Steam parental controls with a pin
     */
    private String unlockParental(String pin) {
        final String url = STEAM_STORE + "parental/ajaxunlock";
        try {
            final Map<String,String> responseCookies = Jsoup.connect(url)
                    .referrer(STEAM_STORE)
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

    public Call<GamesOwnedResponse> getGamesOwned(long steamId) {
        final Map<String,String> args = new HashMap<>();
        args.put("key", apiKey);
        args.put("steamid", String.valueOf(steamId));
        if (PrefsManager.includeFreeGames()) {
            args.put("include_played_free_games", "1");
        }
        return api.getGamesOwned(args);
    }

    /**
     * Check if user is currently NOT in-game, so we can resume farming.
     */
    Boolean checkIfNotInGame() {
        final String url = STEAM_COMMUNITY + "my/profile?l=english";
        Document doc;
        try {
            doc = Jsoup.connect(url)
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
        final String url = STEAM_STORE + "checkout/addfreelicense";
        try {
            final Document doc = Jsoup.connect(url)
                    .referrer(STEAM_STORE)
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

    public JSONArray generateNewDiscoveryQueue() throws Exception {
        final String url = STEAM_STORE + "explore/generatenewdiscoveryqueue";
        final String json = Jsoup.connect(url)
                .ignoreContentType(true)
                .referrer(STEAM_STORE)
                .followRedirects(true)
                .cookies(generateWebCookies())
                .method(Connection.Method.POST)
                .data("sessionid", sessionId)
                .data("queuetype", "0")
                .execute()
                .body();
        return new JSONObject(json).getJSONArray("queue");
    }

    public void clearFromQueue(String appId) throws Exception {
        final String url = STEAM_STORE + "app/10";
        final Document doc = Jsoup.connect(url)
                .ignoreContentType(true)
                .referrer(STEAM_STORE)
                .followRedirects(true)
                .cookies(generateWebCookies())
                .data("sessionid", sessionId)
                .data("appid_to_clear_from_queue", appId)
                .post();
    }

    public boolean autoVote() {
        final String url = STEAM_STORE + "SteamAwards/?l=english";
        try {
            final Document doc = Jsoup.connect(url)
                    .referrer(url)
                    .followRedirects(true)
                    .cookies(generateWebCookies())
                    .get();
            final Element container = doc.select("div.vote_nominations").first();
            if (container == null) {
                return false;
            }
            final String voteId = container.attr("data-voteid");
            final Elements voteNominations = container.select("div.vote_nomination");
            if (voteNominations.isEmpty()) {
                return false;
            }
            final Element choice = voteNominations.get(new Random().nextInt(voteNominations.size()));
            final String appId = choice.attr("data-vote-appid");
            final Document doc2 = Jsoup.connect(STEAM_STORE + "salevote")
                    .referrer(STEAM_STORE)
                    .cookies(generateWebCookies())
                    .data("sessionid", sessionId)
                    .data("voteid", voteId)
                    .data("appid", appId)
                    .post();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Get daily tasks for the Spring Cleaning Event 2018
     */
    public List<String> getDailyTaskAppIds() {
        final String url = STEAM_STORE + "springcleaning?l=english";
        try {
            // Get list of task urls
            final List<String> taskAppIds = new ArrayList<>();
            final Document doc = Jsoup.connect(url)
                    .referrer(STEAM_STORE)
                    .followRedirects(true)
                    .cookies(generateWebCookies())
                    .get();

            final Element stickyNotes = doc.select("div.spring_sticky_notes").first();
            if (stickyNotes == null) {
                return new ArrayList<>();
            }
            for (Element a : stickyNotes.select("a")) {
                final String taskUrl = a.attr("href") + "?l=english";
                if (taskUrl.startsWith("https://store.steampowered.com/springcleaning/task")) {
                    final String taskAppId = getDailyTaskAppId(taskUrl);
                    if (!taskAppId.isEmpty()) {
                        taskAppIds.add(taskAppId);
                    }
                }
            }
            return taskAppIds;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new ArrayList<>();
    }

    public List<String> getProjectAppIds() {
        final String url = STEAM_STORE + "springcleaning?l=english";
        try {
            // Get list of task urls
            final List<String> taskAppIds = new ArrayList<>();
            final Document doc = Jsoup.connect(url)
                    .referrer(STEAM_STORE)
                    .followRedirects(true)
                    .cookies(generateWebCookies())
                    .get();

            final Elements trophies = doc.select("div.spring_trophy_desc");
            for (Element trophy : trophies) {
                final Element a = trophy.select("a").first();
                if (a == null) {
                    continue;
                }
                final String taskUrl = a.attr("href") + "?l=english";
                if (taskUrl.startsWith("https://store.steampowered.com/springcleaning/task")) {
                    final String taskAppId = getDailyTaskAppId(taskUrl);
                    if (!taskAppId.isEmpty()) {
                        taskAppIds.add(taskAppId);
                    }
                }
            }
            return taskAppIds;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new ArrayList<>();
    }

    private String getDailyTaskAppId(String taskUrl) {
        final Pattern ptrn = Pattern.compile("^https://store.steampowered.com/app/(\\d+)/.+$");
        try {
            final Document doc = Jsoup.connect(taskUrl)
                    .referrer(STEAM_STORE)
                    .cookies(generateWebCookies())
                    .get();
            final Element springGame = doc.select("span.spring_game").first();
            if (springGame == null) {
                return "";
            }
            final Element springGameLink = springGame.select("a").first();
            if (springGameLink == null) {
                return "";
            }
            final Matcher m = ptrn.matcher(springGameLink.attr("href"));
            if (!m.find()) {
                return "";
            }
            return m.group(1);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "";
    }

    @ApiKeyState int updateApiKey() {
        if (Utils.isValidKey(PrefsManager.getApiKey())) {
            // Use saved API key
            apiKey = PrefsManager.getApiKey();
            return ApiKeyState.REGISTERED;
        }
        // Try to fetch key from web
        final String url = STEAM_COMMUNITY + "dev/apikey?l=english";
        try {
            final Document doc = Jsoup.connect(url)
                    .referrer(STEAM_COMMUNITY)
                    .followRedirects(true)
                    .cookies(generateWebCookies())
                    .get();
            final Element titleNode = doc.select("div#mainContents h2").first();
            if (titleNode == null) {
                return ApiKeyState.ERROR;
            }
            final String title = titleNode.text().trim();
            if (title.toLowerCase().contains("access denied")) {
                // Limited account, use the built-in API key
                apiKey = Secrets.API_KEY;
                PrefsManager.writeApiKey(apiKey);
                return ApiKeyState.ACCESS_DENIED;
            }
            final Element bodyContentsEx = doc.select("div#bodyContents_ex p").first();
            if (bodyContentsEx == null) {
                return ApiKeyState.ERROR;
            }
            final String text = bodyContentsEx.text().trim();
            if (text.toLowerCase().contains("registering for a steam web api key")
                    && registerApiKey()) {
                // Should actually be registered here, but we have to call this method again to get the key
                return ApiKeyState.UNREGISTERED;
            } else if (text.toLowerCase().startsWith("key: ")) {
                final String key = text.substring(5);
                if (Utils.isValidKey(key)) {
                    apiKey = key;
                    PrefsManager.writeApiKey(apiKey);
                    return ApiKeyState.REGISTERED;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return ApiKeyState.ERROR;
    }

    private boolean registerApiKey() {
        final String url = STEAM_COMMUNITY + "dev/registerkey";
        try {
            final Document doc = Jsoup.connect(url)
                    .ignoreContentType(true)
                    .followRedirects(true)
                    .referrer(STEAM_COMMUNITY)
                    .cookies(generateWebCookies())
                    .data("domain", "localhost")
                    .data("agreeToTerms", "agreed")
                    .data("sessionid", sessionId)
                    .data("Submit", "Register")
                    .post();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    @IntDef({
            ApiKeyState.REGISTERED,
            ApiKeyState.UNREGISTERED,
            ApiKeyState.ACCESS_DENIED,
            ApiKeyState.ERROR
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface ApiKeyState {
        // Account has registered an API key
        int REGISTERED = 1;
        // Account has not registered an API key yet
        int UNREGISTERED = 2;
        // Account is limited and can't register an API key
        int ACCESS_DENIED = -1;
        // Some other error occurred
        int ERROR = -2;
    }
}
