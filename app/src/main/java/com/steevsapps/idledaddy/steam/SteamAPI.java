package com.steevsapps.idledaddy.steam;

import com.steevsapps.idledaddy.steam.model.GamesOwnedResponse;

import java.util.Map;

import in.dragonbra.javasteam.types.KeyValue;
import retrofit2.Call;
import retrofit2.http.FieldMap;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.QueryMap;

public interface SteamAPI {
    @GET("IPlayerService/GetOwnedGames/v0001/?include_appinfo=1&format=json")
    Call<GamesOwnedResponse> getGamesOwned(@QueryMap Map<String,String> args);

    @FormUrlEncoded
    @POST("ISteamUserAuth/AuthenticateUser/v0001/")
    Call<KeyValue> authenticateUser(@FieldMap(encoded = true) Map<String,String> args);
}
