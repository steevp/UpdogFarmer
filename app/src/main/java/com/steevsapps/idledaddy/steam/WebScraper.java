package com.steevsapps.idledaddy.steam;

import com.steevsapps.idledaddy.Secrets;
import com.steevsapps.idledaddy.steam.wrapper.Game;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scrapes card drop info from Steam website
 */
public class WebScraper {
    private final static String BADGES = "http://steamcommunity.com/my/badges?l=english";
    private final static String INVENTORY = "http://steamcommunity.com/my/inventory";
    private final static String GAMES_OWNED = "http://api.steampowered.com/IPlayerService/GetOwnedGames/v0001/?key=%s&steamid=%d&include_appinfo=1&include_played_free_games=1&format=json";
    private final static String PARENTAL_UNLOCK = "http://store.steampowered.com/parental/ajaxunlock";
    private final static String PROFILE = "http://steamcommunity.com/my/profile?l=english";

    // Pattern to match app ID
    private final static Pattern playPattern = Pattern.compile("^steam://run/(\\d+)$");
    // Pattern to match card drops remaining
    private final static Pattern dropPattern = Pattern.compile("^(\\d+) card drops? remaining$");
    // Pattern to match play time
    private final static Pattern timePattern = Pattern.compile("([0-9\\.]+) hrs on record");

    /**
     * Get a list of games with card drops remaining
     * @param cookies Steam cookies
     * @return list of games with remaining drops
     */
    static List<Game> getRemainingGames(Map<String,String> cookies) {
        final List<Game> badgeList = new ArrayList<>();
        Document doc;
        try {
            doc = Jsoup.connect(BADGES)
                    .followRedirects(true)
                    .cookies(cookies)
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
                            .cookies(cookies)
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
    static void viewInventory(Map<String,String> cookies) {
        try {
            Jsoup.connect(INVENTORY)
                    .followRedirects(true)
                    .cookies(cookies)
                    .get();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Unlock Steam parental controls with a pin
     */
    static String unlockParental(String pin, Map<String,String> cookies) {
        try {
            final Map<String,String> responseCookies = Jsoup.connect(PARENTAL_UNLOCK)
                    .referrer("http://store.steampowered.com/")
                    .followRedirects(true)
                    .ignoreContentType(true)
                    .cookies(cookies)
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
    static Boolean checkIfNotInGame(Map<String,String> cookies) {
        Document doc;
        try {
            doc = Jsoup.connect(PROFILE)
                    .followRedirects(true)
                    .cookies(cookies)
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

        final Element inGame = doc.select("div.profile_in_game_name").first();
        return inGame == null;
    }
}
