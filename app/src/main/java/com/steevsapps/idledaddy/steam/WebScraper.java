package com.steevsapps.idledaddy.steam;

import android.support.annotation.NonNull;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scrapes card drop info from Steam website
 */
class WebScraper {
    private final static String BADGES = "http://steamcommunity.com/my/badges?l=english";
    private final static String GAMECARDS = "http://steamcommunity.com/my/gamecards/";
    private final static String INVENTORY = "http://steamcommunity.com/my/inventory";

    // Pattern to match app ID
    private final static Pattern playPattern = Pattern.compile("^steam://run/(\\d+)$");
    // Pattern to match card drops remaining
    private final static Pattern dropPattern = Pattern.compile("^(\\d+) card drops? remaining$");
    // Pattern to match play time
    private final static Pattern timePattern = Pattern.compile("([0-9\\.]+) hrs on record");

    static class Badge implements Comparable<Badge> {
        int appId;
        String name;
        String iconUrl;
        float hoursPlayed;
        int dropsRemaining;
        private Badge(int appId, String name, float hoursPlayed, int dropsRemaining) {
            this.appId = appId;
            this.name = name;
            this.iconUrl = "http://cdn.akamai.steamstatic.com/steam/apps/" + appId + "/header_292x136.jpg";
            this.hoursPlayed = hoursPlayed;
            this.dropsRemaining = dropsRemaining;
        }

        @Override
        public int compareTo(@NonNull Badge o) {
            if (hoursPlayed == o.hoursPlayed) {
                return 0;
            }
            return hoursPlayed < o.hoursPlayed ? -1 : 1;
        }
    }

    /**
     * Get a list of games with card drops remaining
     * @param cookies Steam cookies
     * @return list of games with remaining drops
     */
    static List<Badge> getRemainingGames(Map<String,String> cookies) {
        final List<Badge> badgeList = new ArrayList<>();
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

            badgeList.add(new Badge(appId, name, time, drops));
        }

        return badgeList;
    }

    static boolean hasRemainingDrops(int appId, Map<String,String> cookies) {
        Document doc;
        try {
            doc = Jsoup.connect(GAMECARDS + appId + "?l=english")
                    .followRedirects(true)
                    .cookies(cookies)
                    .get();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        final Element progressInfo = doc.select("span.progress_info_bold").first();
        if (progressInfo == null) {
            return false;
        }

        final Matcher m = dropPattern.matcher(progressInfo.text());
        return m.find();
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
}
