package com.steevsapps.idledaddy.steam;

import com.steevsapps.idledaddy.AppExecutors;
import com.steevsapps.idledaddy.Secrets;
import com.steevsapps.idledaddy.preferences.Prefs;

import in.dragonbra.javasteam.steam.handlers.steamapps.SteamApps;
import in.dragonbra.javasteam.steam.handlers.steamfriends.SteamFriends;
import in.dragonbra.javasteam.steam.handlers.steamuser.LogOnDetails;
import in.dragonbra.javasteam.steam.handlers.steamuser.SteamUser;
import in.dragonbra.javasteam.steam.steamclient.SteamClient;
import in.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackManager;
import in.dragonbra.javasteam.steam.steamclient.callbacks.ConnectedCallback;
import in.dragonbra.javasteam.util.NetHelpers;

public class SteamBot {
    private final static String TAG = SteamBot.class.getSimpleName();

    private final SteamClient steamClient;
    private final SteamUser steamUser;
    private final SteamApps steamApps;
    private final SteamFriends steamFriends;
    private final AppExecutors executors;
    private final CallbackManager manager;

    private LogOnDetails logOnDetails = null;

    public SteamBot(SteamClient steamClient, AppExecutors executors) {
        this.steamClient = steamClient;
        this.executors = executors;

        steamUser = steamClient.getHandler(SteamUser.class);
        steamApps = steamClient.getHandler(SteamApps.class);
        steamFriends = steamClient.getHandler(SteamFriends.class);

        manager = new CallbackManager(steamClient);
        manager.subscribe(ConnectedCallback.class, this::onConnected);
    }

    public void start(LogOnDetails logOnDetails) {
        if (Prefs.useCustomLoginId()) {
            // User custom Login ID
            int localIp = NetHelpers.getIPAddress(steamClient.getLocalIP());
            logOnDetails.setLoginID(localIp ^ Secrets.CUSTOM_OBFUSCATION_MASK);
        }
        this.logOnDetails = logOnDetails;
        connect();
    }

    private void connect() {
        executors.networkIO().execute(() -> steamClient.connect());
    }

    public void stop() {

    }

    private void onConnected(ConnectedCallback callback) {
        executors.networkIO().execute(() -> {
            steamUser.logOn(logOnDetails);
        });
    }
}
