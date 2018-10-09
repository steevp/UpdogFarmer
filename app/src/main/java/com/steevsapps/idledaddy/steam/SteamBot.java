package com.steevsapps.idledaddy.steam;

import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.StringRes;
import android.text.TextUtils;
import android.util.Log;

import com.steevsapps.idledaddy.AppExecutors;
import com.steevsapps.idledaddy.IdleDaddy;
import com.steevsapps.idledaddy.R;
import com.steevsapps.idledaddy.Secrets;
import com.steevsapps.idledaddy.UserRepository;
import com.steevsapps.idledaddy.db.entity.User;
import com.steevsapps.idledaddy.handlers.PurchaseResponse;
import com.steevsapps.idledaddy.handlers.callbacks.PurchaseResponseCallback;
import com.steevsapps.idledaddy.preferences.Prefs;
import com.steevsapps.idledaddy.steam.model.Game;
import com.steevsapps.idledaddy.utils.CryptHelper;
import com.steevsapps.idledaddy.utils.Utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.zip.CRC32;

import in.dragonbra.javasteam.base.ClientMsgProtobuf;
import in.dragonbra.javasteam.enums.EMsg;
import in.dragonbra.javasteam.enums.EPaymentMethod;
import in.dragonbra.javasteam.enums.EPersonaState;
import in.dragonbra.javasteam.enums.EPurchaseResultDetail;
import in.dragonbra.javasteam.enums.EResult;
import in.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver;
import in.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver2;
import in.dragonbra.javasteam.steam.discovery.FileServerListProvider;
import in.dragonbra.javasteam.steam.handlers.steamapps.SteamApps;
import in.dragonbra.javasteam.steam.handlers.steamapps.callback.FreeLicenseCallback;
import in.dragonbra.javasteam.steam.handlers.steamfriends.PersonaState;
import in.dragonbra.javasteam.steam.handlers.steamfriends.SteamFriends;
import in.dragonbra.javasteam.steam.handlers.steamfriends.callback.PersonaStatesCallback;
import in.dragonbra.javasteam.steam.handlers.steamnotifications.callback.ItemAnnouncementsCallback;
import in.dragonbra.javasteam.steam.handlers.steamuser.LogOnDetails;
import in.dragonbra.javasteam.steam.handlers.steamuser.MachineAuthDetails;
import in.dragonbra.javasteam.steam.handlers.steamuser.OTPDetails;
import in.dragonbra.javasteam.steam.handlers.steamuser.SteamUser;
import in.dragonbra.javasteam.steam.handlers.steamuser.callback.AccountInfoCallback;
import in.dragonbra.javasteam.steam.handlers.steamuser.callback.LoggedOffCallback;
import in.dragonbra.javasteam.steam.handlers.steamuser.callback.LoggedOnCallback;
import in.dragonbra.javasteam.steam.handlers.steamuser.callback.LoginKeyCallback;
import in.dragonbra.javasteam.steam.handlers.steamuser.callback.UpdateMachineAuthCallback;
import in.dragonbra.javasteam.steam.handlers.steamuser.callback.WebAPIUserNonceCallback;
import in.dragonbra.javasteam.steam.steamclient.SteamClient;
import in.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackManager;
import in.dragonbra.javasteam.steam.steamclient.callbacks.ConnectedCallback;
import in.dragonbra.javasteam.steam.steamclient.callbacks.DisconnectedCallback;
import in.dragonbra.javasteam.steam.steamclient.configuration.SteamConfiguration;
import in.dragonbra.javasteam.types.GameID;
import in.dragonbra.javasteam.types.KeyValue;
import in.dragonbra.javasteam.util.NetHelpers;

public class SteamBot {
    private String TAG;

    public final static String LOGIN_EVENT = "LOGIN_EVENT"; // Emitted on login
    public final static String LOGIN_RESULT = "LOGIN_RESULT"; // Login result
    public final static String DISCONNECT_EVENT = "DISCONNECT_EVENT"; // Emitted on disconnect
    public final static String STOP_EVENT = "STOP_EVENT"; // Emitted when stop clicked
    public final static String FARM_EVENT = "FARM_EVENT"; // Emitted when farm() is called
    public final static String GAME_COUNT = "GAME_COUNT"; // Number of games left to farm
    public final static String CARD_COUNT = "CARD_COUNT"; // Number of card drops remaining
    public final static String NOW_PLAYING_EVENT = "NOW_PLAYING_EVENT"; // Emitted when the game you're idling changes

    private final SteamClient steamClient;
    private final SteamUser steamUser;
    private final SteamApps steamApps;
    private final SteamFriends steamFriends;
    private final SteamWeb steamWeb;
    private final CallbackManager manager;

    private final UserRepository userRepo;
    private final AppExecutors executors;
    private final File filesDir;
    private final Context context;

    // ScheduledFuture to check for cards
    private ScheduledFuture<?> farmHandle;
    // ScheduledFuture to check if account is still busy
    private ScheduledFuture<?> busyHandle;

    private NotificationListener listener;

    private int farmIndex = 0;
    private int gameCount = 0;
    private int cardCount = 0;
    private List<Game> gamesIdling;
    private List<Game> gamesWithCards;

    private long steamId = 0;
    private String username;
    private User userEntity;

    private String authCode = "";
    private String twoFactorCode = "";

    // Is the bot running?
    private volatile boolean running = false;

    // Is the userEntity logged on?
    private volatile boolean loggedOn = false;

    // Automatically reconnect on disconnections. Disabled at login screen.
    private volatile boolean attemptReconnect = false;

    // Account is logged in elsewhere
    private volatile boolean busy = false;

    // Idling paused
    private volatile boolean paused = false;

    // Account is farming cards
    private volatile boolean farming = false;

    private final FarmTask farmTask = new FarmTask();
    private final BusyTask busyTask = new BusyTask();

    @StringRes
    private int lastMsg = R.string.logged_in;

    private class FarmTask implements Runnable {
        @Override
        public void run() {
            try {
                checkForCards();
            } catch (Exception e) {
                Log.e(TAG, "FarmTask failed", e);
            }
        }
    }

    private class BusyTask implements Runnable {
        @Override
        public void run() {
            try {
                Log.i(TAG, "Checking if account is still busy...");
                final Boolean accountBusy = steamWeb.checkIfBusy();
                if (accountBusy == null) {
                    Log.i(TAG, "Invalid cookie data or no internet, reconnecting...");
                    steamClient.disconnect();
                } else if (accountBusy) {
                    Log.i(TAG, "Account is still busy!");
                } else {
                    Log.i(TAG, "Account is free!");
                    busy = false;
                    lastMsg = R.string.logged_in;
                    steamClient.disconnect();
                    unscheduleBusyTask();
                }
            } catch (Exception e) {
                Log.e(TAG, "BusyTask failed", e);
            }
        }
    }

    public SteamBot(String username, Application application) {
        TAG = String.format(Locale.US, "SteamBot[%s]", username);
        this.username = username;

        userRepo = ((IdleDaddy) application).getUserRepository();
        executors = ((IdleDaddy) application).getExecutors();
        steamWeb = SteamWeb.getInstance(username);
        filesDir = application.getFilesDir();
        context = application.getApplicationContext();

        final SteamConfiguration config = SteamConfiguration.create(b ->
                b.withServerListProvider(new FileServerListProvider(
                        new File(filesDir, "servers.bin")
                ))
        );
        steamClient = new SteamClient(config);
        steamClient.addHandler(new PurchaseResponse());
        steamUser = steamClient.getHandler(SteamUser.class);
        steamApps = steamClient.getHandler(SteamApps.class);
        steamFriends = steamClient.getHandler(SteamFriends.class);

        manager = new CallbackManager(steamClient);
        manager.subscribe(ConnectedCallback.class, this::onConnected);
        manager.subscribe(DisconnectedCallback.class, this::onDisconnected);
        manager.subscribe(LoggedOffCallback.class, this::onLoggedOff);
        manager.subscribe(LoggedOnCallback.class, this::onLoggedOn);
        manager.subscribe(LoginKeyCallback.class, this::onLoginKey);
        manager.subscribe(UpdateMachineAuthCallback.class, this::onUpdateMachineAuth);
        manager.subscribe(PersonaStatesCallback.class, this::onPersonaStates);
        manager.subscribe(FreeLicenseCallback.class, this::onFreeLicense);
        manager.subscribe(AccountInfoCallback.class, this::onAccountInfo);
        manager.subscribe(WebAPIUserNonceCallback.class, this::onWebAPIUserNonce);
        manager.subscribe(ItemAnnouncementsCallback.class, this::onItemAnnouncements);
        manager.subscribe(PurchaseResponseCallback.class, this::onPurchaseResponse);
    }

    public void setListener(NotificationListener listener) {
        this.listener = listener;
    }

    public void start() {
        if (!running) {
            Log.i(TAG, "Starting bot...");
            running = true;
            executors.networkIO().execute(() -> {
                while (running) {
                    try {
                        manager.runWaitCallbacks(1000L);
                    } catch (Exception e) {
                        Log.e(TAG, "runWaitCallbacks() failed!", e);
                    }
                }
            });
        }
    }

    public void stop() {
        if (running) {
            Log.i(TAG, "Stopping bot....");
            running = false;
            attemptReconnect = false;
            stopFarming();
            unscheduleBusyTask();
            steamUser.logOff();
            steamClient.disconnect();
        }
    }

    public void login(LogOnDetails logOnDetails) {
        userEntity = new User(logOnDetails.getUsername());
        userEntity.setPassword(CryptHelper.encryptString(context, logOnDetails.getPassword()));
        authCode = logOnDetails.getAuthCode();
        twoFactorCode = logOnDetails.getTwoFactorCode();
        executors.networkIO().execute(() -> steamClient.connect());
    }

    public boolean isLoggedOn() {
        return loggedOn;
    }

    public boolean isFarming() {
        return farming;
    }

    public boolean isPaused() {
        return paused;
    }

    public String getUsername() {
        return username;
    }

    public long getSteamId() {
        return steamId;
    }

    public int getGameCount() {
        return gameCount;
    }

    public int getCardCount() {
        return cardCount;
    }

    public ArrayList<Game> getGamesIdling() {
        if (gamesIdling == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(gamesIdling);
    }

    public void changeStatus(EPersonaState status) {
        executors.networkIO().execute(() -> steamFriends.setPersonaState(status));
    }

    public void skipGame() {
        if (farming && gamesWithCards != null) {
            Log.i(TAG, "Skipping game...");
            if (++farmIndex >= gamesWithCards.size()) {
                farmIndex = 0;
            }
            playGames(gamesWithCards.get(farmIndex));
        }
    }

    public void stopGame() {
        Log.i(TAG, "Stopping game...");
        paused = false;
        stopPlaying();
        stopFarming();
        lastMsg = R.string.stopped;
        listener.showTextNotification(username, R.string.stopped);
        listener.sendEvent(username, STOP_EVENT);
    }

    public void pauseGame() {
        Log.i(TAG, "Pausing game...");
        paused = true;
        stopPlaying();
        listener.showPausedNotification(username);
        listener.sendEvent(username, NOW_PLAYING_EVENT);
    }

    public void resumeGame() {
        Log.i(TAG, "Resuming game...");
        paused = false;
        restorePlaySession();
    }

    public void loadUser() {
        if (userEntity == null) {
            Log.i(TAG, "Attempting to load user from database...");
            executors.diskIO().execute(() -> {
                userEntity = userRepo.findByNameSync(username);
                if (userEntity != null && userEntity.canLogOn()) {
                    Log.i(TAG, "Connecting...");
                    executors.networkIO().execute(() -> steamClient.connect());
                }
            });
        }
    }

    private void scheduleFarmTask() {
        if (farmHandle == null || farmHandle.isCancelled()) {
            Log.i(TAG, "Starting FarmTask...");
            farmHandle = executors.scheduler().scheduleAtFixedRate(farmTask, 10, 10, TimeUnit.MINUTES);
        }
    }

    private void unscheduleFarmTask() {
        if (farmHandle != null) {
            Log.i(TAG, "Stopping farmtask");
            farmHandle.cancel(true);
        }
    }

    private void scheduleBusyTask() {
        if (busyHandle == null || busyHandle.isCancelled()) {
            Log.i(TAG, "Starting BusyTask...");
            busyHandle = executors.scheduler().scheduleAtFixedRate(busyTask, 0, 30, TimeUnit.SECONDS);
        }
    }

    private void unscheduleBusyTask() {
        if (busyHandle != null) {
            Log.i(TAG, "Stopping BusyTask...");
            busyHandle.cancel(true);
        }
    }

    public void startFarming() {
        if (!farming) {
            farming = true;
            paused = false;
            executors.networkIO().execute(farmTask);
        }
    }

    public void stopFarming() {
        if (farming) {
            farming = false;
            gamesWithCards = null;
            farmIndex = 0;
            gamesIdling = null;
            unscheduleFarmTask();
        }
    }

    private void checkForCards() {
        if (busy || paused) {
            return;
        }

        Log.i(TAG, "Checking remaining card drops...");
        int count = 0;
        int maxTries = 3;
        while (true) {
            gamesWithCards = steamWeb.getRemainingGames();
            if (gamesWithCards != null) {
                Log.i(TAG, "Found " + gamesWithCards.size() + " games with cards");
                break;
            }

            if (++count >= maxTries) {
                break;
            }

            Log.i(TAG, "Retrying...");
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
                return;
            }
        }

        if (gamesWithCards == null) {
            Log.i(TAG, "Invalid cookie data or no internet, reconnecting");
            executors.networkIO().execute(() -> steamUser.requestWebAPIUserNonce());
            return;
        }

        // Count the games and cards
        gameCount = gamesWithCards.size();
        cardCount = 0;
        for (Game g : gamesWithCards) {
            cardCount += g.dropsRemaining;
        }

        // Send farm event
        final Bundle extras = new Bundle();
        extras.putInt(GAME_COUNT, gameCount);
        extras.putInt(CARD_COUNT, cardCount);
        listener.sendEvent(username, FARM_EVENT, extras);

        if (gameCount == 0) {
            Log.i(TAG, "Finished idling");
            stopPlaying();
            stopFarming();
            lastMsg = R.string.idling_finished;
            listener.showTextNotification(username, R.string.idling_finished);
            return;
        }

        // Sort by hours played descending
        Collections.sort(gamesWithCards, Collections.reverseOrder());

        if (farmIndex >= gamesWithCards.size()) {
            farmIndex = 0;
        }
        final Game game = gamesWithCards.get(farmIndex);

        // TODO: Steam only updates play time every half hour, so maybe we should keep track of it ourselves
        if (game.hoursPlayed >= Prefs.getHoursUntilDrops() || gamesWithCards.size() == 1 || farmIndex > 0) {
            // Idle a single game
            playGames(game);
            unscheduleFarmTask();
        } else {
            // Idle multiple games (max 32) until one has reached 2 hrs
            playGames(gamesWithCards.toArray(new Game[0]));
            scheduleFarmTask();
        }
    }

    /**
     * Resume playing the games we were idling
     */
    private void restorePlaySession() {
        if (paused || busy) {
            return;
        }

        if (farming) {
            Log.i(TAG, "Resume card farming");
            executors.networkIO().execute(farmTask);
        } else if (gamesIdling != null) {
            Log.i(TAG, "Resume playing");
            playGames(gamesIdling.toArray(new Game[0]));
        }
    }

    /**
     * Save user entity to database
     */
    private void updateUser() {
        userRepo.updateUser(userEntity);
    }

    private void onConnected(ConnectedCallback callback) {
        Log.i(TAG, "Connected.");
        if (userEntity != null) {
            Log.i(TAG, "Logging on");
            final LogOnDetails logOnDetails = new LogOnDetails();
            logOnDetails.setUsername(userEntity.getUsername());
            logOnDetails.setPassword(CryptHelper.decryptString(context, userEntity.getPassword()) );
            logOnDetails.setAuthCode(authCode);
            logOnDetails.setTwoFactorCode(twoFactorCode);
            logOnDetails.setLoginKey(userEntity.getLoginKey());
            if (!TextUtils.isEmpty(userEntity.getSentryHash())) {
                logOnDetails.setSentryFileHash(Utils.hexToBytes(userEntity.getSentryHash()));
            }
            if (Prefs.useCustomLoginId()) {
                // Use custom Login ID
                int localIp = NetHelpers.getIPAddress(steamClient.getLocalIP());
                logOnDetails.setLoginID(localIp ^ Secrets.CUSTOM_OBFUSCATION_MASK);
            }
            logOnDetails.setShouldRememberPassword(true);
            executors.networkIO().execute(() -> steamUser.logOn(logOnDetails));
        }
    }

    private void onDisconnected(DisconnectedCallback callback) {
        Log.i(TAG, "Disconnected.");
        loggedOn = false;
        authCode = "";
        twoFactorCode = "";
        if (attemptReconnect) {
            // Schedule a reconnect in 5 seconds
            executors.scheduler().schedule(() -> {
                Log.i(TAG, "Reconnecting");
                steamClient.connect();
            }, 5, TimeUnit.SECONDS);
        }
        listener.sendEvent(username, DISCONNECT_EVENT);
    }

    private void onLoggedOff(LoggedOffCallback callback) {
        Log.i(TAG, "Logged off. Result: " + callback.getResult());
        if (callback.getResult() == EResult.LoggedInElsewhere) {
            // Account is busy. Wait for it to be free.
            lastMsg = R.string.logged_in_elsewhere;
            listener.showTextNotification(username, R.string.logged_in_elsewhere);
            unscheduleFarmTask();
            if (!busy) {
                busy = true;
                scheduleBusyTask();
            }
        } else {
            // Reconnect
            steamClient.disconnect();
        }
    }

    private void onLoggedOn(LoggedOnCallback callback) {
        if (callback.getResult() == EResult.OK) {
            Log.i(TAG, "Logged on!");
            loggedOn = true;
            attemptReconnect = true;
            steamId = steamClient.getSteamID().convertToUInt64();

            // Don't replace the paused notification
            if (!paused) {
                listener.showTextNotification(username, lastMsg);
            }

            executors.diskIO().execute(() -> {
                if (!userRepo.hasUser(username)) {
                    Log.i(TAG, "Saving " + username + " to the database");
                    userRepo.insertUser(userEntity);
                    userRepo.setCurrentUser(username);
                }
            });

            executors.networkIO().execute(() -> {
                boolean authenticated = attemptWebAuthentication(callback.getWebAPIUserNonce());
                if (authenticated) {
                    restorePlaySession();
                    registerApiKey();
                } else {
                    // Request a new web api nonce
                    steamUser.requestWebAPIUserNonce();
                }
            });
        } else if (callback.getResult() == EResult.InvalidPassword && !TextUtils.isEmpty(userEntity.getLoginKey())) {
            Log.i(TAG, "Login key expired!");
            attemptReconnect = false;
            userEntity.setLoginKey("");
            updateUser();
            steamClient.disconnect();
        } else {
            Log.i(TAG, "LogOn Result: " + callback.getResult());
            steamClient.disconnect();
        }

        // Tell LoginActivity the result
        final Bundle args = new Bundle();
        args.putSerializable(LOGIN_RESULT, callback.getResult());
        listener.sendEvent(username, LOGIN_EVENT, args);
    }

    private void onLoginKey(LoginKeyCallback callback) {
        Log.i(TAG, "Saving loginkey");
        userEntity.setLoginKey(callback.getLoginKey());
        updateUser();
        executors.networkIO().execute(() -> steamUser.acceptNewLoginKey(callback));
    }

    private void onUpdateMachineAuth(UpdateMachineAuthCallback callback) {
        final File sentryDir = new File(filesDir, "sentry");
        sentryDir.mkdirs();
        final File sentryFile = new File(sentryDir, userEntity.getUsername() + ".sentry");
        Log.i(TAG, "Saving sentry file to " + sentryFile.getAbsolutePath());
        try (final FileOutputStream fos = new FileOutputStream(sentryFile)) {
            final FileChannel channel = fos.getChannel();
            channel.position(callback.getOffset());
            channel.write(ByteBuffer.wrap(callback.getData(), 0, callback.getBytesToWrite()));

            final byte[] sha1 = Utils.calculateSHA1(sentryFile);

            final OTPDetails otp = new OTPDetails();
            otp.setIdentifier(callback.getOneTimePassword().getIdentifier());
            otp.setType(callback.getOneTimePassword().getType());

            final MachineAuthDetails auth = new MachineAuthDetails();
            auth.setJobID(callback.getJobID());
            auth.setFileName(callback.getFileName());
            auth.setBytesWritten(callback.getBytesToWrite());
            auth.setFileSize((int) sentryFile.length());
            auth.setOffset(callback.getOffset());
            auth.seteResult(EResult.OK);
            auth.setLastError(0);
            auth.setSentryFileHash(sha1);
            auth.setOneTimePassword(otp);

            executors.networkIO().execute(() -> steamUser.sendMachineAuthResponse(auth));

            userEntity.setSentryHash(Utils.bytesToHex(sha1));
            updateUser();
        } catch (IOException | NoSuchAlgorithmException e) {
            Log.e(TAG, "Error saving sentry file", e);
        }
    }

    private void onPersonaStates(PersonaStatesCallback callback) {
        for (PersonaState ps : callback.getPersonaStates()) {
            if (ps.getFriendID().equals(steamClient.getSteamID())) {
                final String personaName = ps.getName();
                final String avatarHash = Utils.bytesToHex(ps.getAvatarHash()).toLowerCase();
                if (!userEntity.getPersonaName().equals(personaName) || !userEntity.getAvatarHash().equals(avatarHash)) {
                    userEntity.setPersonaName(personaName);
                    userEntity.setAvatarHash(avatarHash);
                    updateUser();
                }
                break;
            }
        }
    }

    private void onFreeLicense(FreeLicenseCallback callback) {
        /*int freeLicense = pendingFreeLicenses.removeFirst();
        if (!callback.getGrantedApps().isEmpty()) {
            listener.showToast(R.string.activated, String.valueOf(callback.getGrantedApps().get(0)));
        } else if (!callback.getGrantedPackages().isEmpty()) {
            listener.showToast(R.string.activated, String.valueOf(callback.getGrantedPackages().get(0)));
        } else {
            // Try activating it with the web handler
            executors.networkIO().execute(() -> {
                if (steamWeb.addFreeLicense(freeLicense)) {
                    listener.showToast(R.string.activated, String.valueOf(freeLicense));
                } else {
                    listener.showToast(R.string.activation_failed);
                }
            });
        }*/
    }

    private void onAccountInfo(AccountInfoCallback callback) {
        if (!Prefs.getOffline()) {
            executors.networkIO().execute(() -> steamFriends.setPersonaState(EPersonaState.Online));
        }
    }

    private void onWebAPIUserNonce(WebAPIUserNonceCallback callback) {
        Log.i(TAG, "Got new WebAPI user authentication nonce");
        executors.networkIO().execute(() -> {
            boolean authenticated = attemptWebAuthentication(callback.getNonce());

            if (authenticated) {
                restorePlaySession();
            } else {
                listener.showTextNotification(username, R.string.web_login_failed);
            }
        });
    }

    private void onItemAnnouncements(ItemAnnouncementsCallback callback) {
        Log.i(TAG, "New item notification " + callback.getCount());
        if (callback.getCount() > 0 && farming) {
            // Possible card drop.
            executors.networkIO().execute(farmTask);
        }
    }

    private void onPurchaseResponse(PurchaseResponseCallback callback) {
        if (callback.getResult() == EResult.OK) {
            final KeyValue kv = callback.getPurchaseReceiptInfo();
            final EPaymentMethod paymentMethod = EPaymentMethod.from(kv.get("PaymentMethod").asInteger());
            if (paymentMethod == EPaymentMethod.ActivationCode) {
                final StringBuilder products = new StringBuilder();
                int size = kv.get("LineItemCount").asInteger();
                Log.i(TAG, "LineItemCount " + size);
                for (int i=0;i<size;i++) {
                    final String lineItem = kv.get("lineitems").get(i + "").get("ItemDescription").asString();
                    Log.i(TAG, "lineItem " + i + " " + lineItem);
                    products.append(lineItem);
                    if (i + 1 < size) {
                        products.append(", ");
                    }
                }
                listener.showToast(R.string.activated, products.toString());
            }
        } else {
            final EPurchaseResultDetail purchaseResult = callback.getPurchaseResultDetails();
            int errorId;
            if (purchaseResult == EPurchaseResultDetail.AlreadyPurchased) {
                errorId = R.string.product_already_owned;
            } else if (purchaseResult == EPurchaseResultDetail.BadActivationCode) {
                errorId = R.string.invalid_key;
            } else {
                errorId = R.string.activation_failed;
            }
            listener.showToast(errorId);
        }
    }

    /**
     * Authenticate on the Steam website
     */
    private boolean attemptWebAuthentication(String nonce) {
        Log.i(TAG, "Attempting SteamWeb authentication...");
        int count = 0;
        int maxTries = 3;
        while (true) {
            boolean authenticated = steamWeb.authenticate(steamClient, userEntity, nonce);
            if (authenticated) {
                Log.i(TAG, "Authenticated!");
                return true;
            }
            if (++count >= maxTries) {
                return false;
            }
            Log.i(TAG, "Retrying...");
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                return false;
            }
        }
    }

    /**
     * Automatically register a Steam web api key if there isn't one.
     */
    private void registerApiKey() {
        if (Utils.isValidKey(userEntity.getApiKey())) {
            Log.i(TAG, "API key already registered!");
            return;
        }
        Log.i(TAG, "Registering API key");
        ApiKeyState state = steamWeb.updateApiKey();
        Log.i(TAG, "API key result: " + state);
        switch (state) {
            case REGISTERED:
                break;
            case ACCESS_DENIED:
                listener.showToast(R.string.apikey_access_denied);
                break;
            case UNREGISTERED:
                // Call updateApiKey once more to actually update it
                state = steamWeb.updateApiKey();
                break;
            case ERROR:
                listener.showToast(R.string.apikey_register_failed);
                break;
        }
        if (Utils.isValidKey(state.getApiKey())) {
            Log.i(TAG, "Updating API key...");
            userEntity.setApiKey(state.getApiKey());
            updateUser();
        }
    }

    /**
     * Send a request to the Steam Network to begin idling one or more games
     */
    public void playGames(Game... games) {
        gamesIdling = new ArrayList<>(Arrays.asList(games));
        // Limit to 32 games (this is Steam's limit)
        int s = gamesIdling.size();
        if (s > 32) {
            gamesIdling.subList(32, s).clear();
        }
        final ClientMsgProtobuf<SteammessagesClientserver.CMsgClientGamesPlayed.Builder> gamesPlayed;
        gamesPlayed = new ClientMsgProtobuf<>(SteammessagesClientserver.CMsgClientGamesPlayed.class, EMsg.ClientGamesPlayed);
        for (Game game : gamesIdling) {
            final GameID gameId = new GameID(game.appId);
            if (game.appId == 0) {
                // Non-Steam game
                gameId.setAppType(GameID.GameType.SHORTCUT);
                final CRC32 crc = new CRC32();
                crc.update(game.name.getBytes());
                // set the high-bit on the mod-id
                // reduces crc32 to 31bits, but lets us use the modID as a guaranteed unique
                // replacement for appID
                gameId.setModID(crc.getValue() | (0x80000000));
            }
            gamesPlayed.getBody().addGamesPlayedBuilder()
                    .setGameId(gameId.convertToUInt64())
                    .setGameExtraInfo(game.name);
        }
        executors.networkIO().execute(() -> steamClient.send(gamesPlayed));
        listener.sendEvent(username, NOW_PLAYING_EVENT);
        listener.showIdleNotification(username, gamesIdling, farming);
    }

    /**
     * Send request to the Steam Network to stop idling games
     */
    private void stopPlaying() {
        if (!paused) {
            gamesIdling = null;
        }
        final ClientMsgProtobuf<SteammessagesClientserver.CMsgClientGamesPlayed.Builder> stopGame;
        stopGame = new ClientMsgProtobuf<>(SteammessagesClientserver.CMsgClientGamesPlayed.class, EMsg.ClientGamesPlayed);
        stopGame.getBody().addGamesPlayedBuilder().setGameId(0);
        executors.networkIO().execute(() -> steamClient.send(stopGame));
        listener.sendEvent(username, STOP_EVENT);
        lastMsg = R.string.stopped;
        listener.showTextNotification(username, R.string.stopped);
    }

    /**
     * Register a product key
     */
    private void registerProductKey(String productKey) {
        final ClientMsgProtobuf<SteammessagesClientserver2.CMsgClientRegisterKey.Builder> registerKey;
        registerKey = new ClientMsgProtobuf<>(SteammessagesClientserver2.CMsgClientRegisterKey.class, EMsg.ClientRegisterKey);
        registerKey.getBody().setKey(productKey);
        executors.networkIO().execute(() -> steamClient.send(registerKey));
    }
}
