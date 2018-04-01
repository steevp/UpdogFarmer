package com.steevsapps.idledaddy.steam.converter;

import com.google.gson.Gson;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.steevsapps.idledaddy.steam.model.Game;
import com.steevsapps.idledaddy.steam.model.GamesOwnedResponse;

import java.lang.reflect.Type;
import java.util.Locale;

public class GamesOwnedResponseDeserializer implements JsonDeserializer<GamesOwnedResponse> {
    private final static String IMG_URL = "http://media.steampowered.com/steamcommunity/public/images/apps/%d/%s.jpg";

    @Override
    public GamesOwnedResponse deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        final JsonElement element = json.getAsJsonObject().get("response");
        final GamesOwnedResponse response = new Gson().fromJson(element, GamesOwnedResponse.class);

        for (Game game : response.getGames()) {
            game.iconUrl = String.format(Locale.US, IMG_URL, game.appId, game.iconUrl);
            game.hoursPlayed /= 60F;
            game.dropsRemaining = 0;
        }

        return response;
    }
}
