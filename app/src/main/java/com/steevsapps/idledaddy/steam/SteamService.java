package com.steevsapps.idledaddy.steam;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.media.app.NotificationCompat.MediaStyle;
import android.util.Log;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.animation.GlideAnimation;
import com.bumptech.glide.request.target.SimpleTarget;
import com.steevsapps.idledaddy.MainActivity;
import com.steevsapps.idledaddy.R;
import com.steevsapps.idledaddy.steam.wrapper.Game;
import com.steevsapps.idledaddy.utils.Prefs;
import com.steevsapps.idledaddy.utils.Utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import uk.co.thomasc.steamkit.base.ClientMsgProtobuf;
import uk.co.thomasc.steamkit.base.generated.SteammessagesClientserver;
import uk.co.thomasc.steamkit.base.generated.steamlanguage.EMsg;
import uk.co.thomasc.steamkit.base.generated.steamlanguage.EPaymentMethod;
import uk.co.thomasc.steamkit.base.generated.steamlanguage.EPersonaState;
import uk.co.thomasc.steamkit.base.generated.steamlanguage.EResult;
import uk.co.thomasc.steamkit.steam3.handlers.steamfriends.SteamFriends;
import uk.co.thomasc.steamkit.steam3.handlers.steamnotifications.callbacks.NotificationUpdateCallback;
import uk.co.thomasc.steamkit.steam3.handlers.steamnotifications.types.NotificationType;
import uk.co.thomasc.steamkit.steam3.handlers.steamuser.SteamUser;
import uk.co.thomasc.steamkit.steam3.handlers.steamuser.callbacks.LoggedOffCallback;
import uk.co.thomasc.steamkit.steam3.handlers.steamuser.callbacks.LoggedOnCallback;
import uk.co.thomasc.steamkit.steam3.handlers.steamuser.callbacks.LoginKeyCallback;
import uk.co.thomasc.steamkit.steam3.handlers.steamuser.callbacks.PurchaseResponseCallback;
import uk.co.thomasc.steamkit.steam3.handlers.steamuser.callbacks.UpdateMachineAuthCallback;
import uk.co.thomasc.steamkit.steam3.handlers.steamuser.types.LogOnDetails;
import uk.co.thomasc.steamkit.steam3.handlers.steamuser.types.MachineAuthDetails;
import uk.co.thomasc.steamkit.steam3.steamclient.SteamClient;
import uk.co.thomasc.steamkit.steam3.steamclient.callbackmgr.CallbackMsg;
import uk.co.thomasc.steamkit.steam3.steamclient.callbackmgr.JobCallback;
import uk.co.thomasc.steamkit.steam3.steamclient.callbacks.CMListCallback;
import uk.co.thomasc.steamkit.steam3.steamclient.callbacks.ConnectedCallback;
import uk.co.thomasc.steamkit.steam3.steamclient.callbacks.DisconnectedCallback;
import uk.co.thomasc.steamkit.steam3.webapi.WebAPI;
import uk.co.thomasc.steamkit.types.keyvalue.KeyValue;
import uk.co.thomasc.steamkit.types.steamid.SteamID;
import uk.co.thomasc.steamkit.util.KeyDictionary;
import uk.co.thomasc.steamkit.util.WebHelpers;
import uk.co.thomasc.steamkit.util.cSharp.events.ActionT;
import uk.co.thomasc.steamkit.util.crypto.CryptoHelper;
import uk.co.thomasc.steamkit.util.crypto.RSACrypto;

public class SteamService extends Service {
    private final static String TAG = SteamService.class.getSimpleName();
    private final static int NOTIF_ID = 6896; // Ongoing notification ID
    private final static String CHANNEL_ID = "idle_channel"; // Notification channel

    // Events
    public final static String LOGIN_EVENT = "LOGIN_EVENT"; // Emitted on login
    public final static String RESULT = "RESULT"; // Login result
    public final static String DISCONNECT_EVENT = "DISCONNECT_EVENT"; // Emitted on disconnect
    public final static String STOP_EVENT = "STOP_EVENT"; // Emitted when stop clicked
    public final static String FARM_EVENT = "FARM_EVENT"; // Emitted when farm() is called
    public final static String GAME_COUNT = "GAME_COUNT"; // Number of games left to farm
    public final static String CARD_COUNT = "CARD_COUNT"; // Number of card drops remaining

    // Actions
    public final static String SKIP_INTENT = "SKIP_INTENT";
    public final static String STOP_INTENT = "STOP_INTENT";

    private SteamClient steamClient;
    private SteamUser steamUser;
    private SteamFriends steamFriends;
    private int farmIndex = 0;
    private Game currentGame;
    private int gameCount = 0;
    private int cardCount = 0;

    private volatile boolean running = false;
    private volatile boolean connected = false;
    private volatile boolean farming = false;

    private String webApiUserNonce;
    private String sessionId;
    private String token;
    private String tokenSecure;
    private String sentryHash;
    private String steamParental;
    private boolean authenticated = false;
    private boolean loggedIn = false;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private ScheduledFuture<?> farmHandle;

    /**
     * Class for clients to access.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with
     * IPC.
     */
    public class LocalBinder extends Binder {
        public SteamService getService() {
            return SteamService.this;
        }
    }

    // This is the object that receives interactions from clients.
    private final IBinder binder = new LocalBinder();


    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(SKIP_INTENT)) {
                // Skip clicked
                farmIndex++;
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        farm();
                    }
                }).start();
            } else if (intent.getAction().equals(STOP_INTENT)) {
                Log.i(TAG, "received stop intent");
                stopPlaying();
                stopFarming();
                updateNotification("Stopped");
                LocalBroadcastManager.getInstance(SteamService.this)
                        .sendBroadcast(new Intent(STOP_EVENT));
            }
        }
    };

    public void startFarming() {
        farming = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                    farm();
            }
        }).start();
    }

    public void stopFarming() {
        farming = false;
        stopFarmTask();
    }

    private void farm() {
        Log.i(TAG, "Checking remaining card drops");
        List<Game> games = null;
        for (int i=0;i<3;i++) {
            games = WebScraper.getRemainingGames(generateWebCookies());
            if (games != null) {
                Log.i(TAG, "gotem");
                break;
            }
            if (i + 1 < 3) {
                Log.i(TAG, "retrying...");
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        if (games == null) {
            Log.i(TAG, "Invalid cookie data or no internet, reconnecting");
            steamClient.disconnect();
            return;
        }

        // Count the games and cards
        gameCount = games.size();
        cardCount = 0;
        for (Game g : games) {
            cardCount += g.dropsRemaining;
        }

        // Send farm event
        final Intent event = new Intent(FARM_EVENT);
        event.putExtra(GAME_COUNT, gameCount);
        event.putExtra(CARD_COUNT, cardCount);
        LocalBroadcastManager.getInstance(SteamService.this)
                .sendBroadcast(event);

        if (games.isEmpty()) {
            Log.i(TAG, "Finished idling");
            stopPlaying();
            updateNotification(getString(R.string.idling_finished));
            stopFarming();
            return;
        }

        // Sort by hours played descending
        Collections.sort(games, Collections.reverseOrder());

        if (farmIndex >= games.size()) {
            farmIndex = 0;
        }
        final Game game = games.get(farmIndex);

        // TODO: Steam only updates play time every half hour, so maybe we should keep track of it ourselves
        if (game.hoursPlayed >= 2 || games.size() == 1 || Prefs.simpleFarming() || farmIndex > 0) {
            // If a game has over 2 hrs we can just idle it
            Log.i(TAG, "Now idling " + game.name);
            currentGame = game;
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    buildIdleNotification(game);
                }
            });
            playGame(game.appId);
            stopFarmTask();
        } else {
            // Idle multiple games (max 32) until one has reached 2 hrs
            Log.i(TAG, "Idling multiple");
            currentGame = null;
            int size = games.size();
            if (size > 32) {
                size = 32;
            }
            final int[] appIds = new int[size];
            for (int i=0;i<size;i++) {
                appIds[i] = games.get(i).appId;
            }
            playGames(appIds);
            updateNotification("Idling multiple");
            startFarmTask();
        }
    }

    private void startFarmTask() {
        final Runnable runnable = new Runnable() {
            @Override
            public void run() {
                farm();
            }
        };
        if (farmHandle == null || farmHandle.isCancelled()) {
            Log.i(TAG, "Starting farmtask");
            farmHandle = scheduler.scheduleAtFixedRate(runnable, 10, 10, TimeUnit.MINUTES);
        }
    }

    private void stopFarmTask() {
        if (farmHandle != null) {
            Log.i(TAG, "Stopping farmtask");
            farmHandle.cancel(true);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }


    public static Intent createIntent(Context c) {
        return new Intent(c, SteamService.class);
    }

    @Override
    public void onCreate() {
        Log.i(TAG, "Service created");
        super.onCreate();
        steamClient = new SteamClient();
        steamUser = steamClient.getHandler(SteamUser.class);
        steamFriends = steamClient.getHandler(SteamFriends.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Create notification channel
            createChannel();
        }
        startForeground(NOTIF_ID, buildNotification("Steam service started"));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!running) {
            Log.i(TAG, "Command starting");
            final IntentFilter filter = new IntentFilter();
            filter.addAction(SKIP_INTENT);
            filter.addAction(STOP_INTENT);
            registerReceiver(receiver, filter);
            start();
        }
        return Service.START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "Service destroyed");
        stopForeground(true);
        running = false;
        stopFarming();
        // Somebody was getting IllegalArgumentException from this, possibly because I was calling
        // super.onDestroy() at the beginning, but I'll still catch it just to be safe.
        try {
            unregisterReceiver(receiver);
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        }
        super.onDestroy();
    }

    /**
     * Create notification channel for Android O
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private void createChannel() {
        final CharSequence name = getString(R.string.channel_name);
        final int importance = NotificationManager.IMPORTANCE_LOW;
        final NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
        channel.setShowBadge(false);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        channel.enableVibration(false);
        channel.enableLights(false);
        channel.setBypassDnd(false);
        final NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(channel);
    }

    /**
     * Check if user is logged in
     */
    public boolean isLoggedIn() {
        return loggedIn;
    }

    public boolean isFarming() {
        return farming;
    }

    public int getCurrentAppId() {
        if (currentGame != null) {
            return currentGame.appId;
        }
        return 0;
    }

    public int getGameCount() {
        return gameCount;
    }

    public int getCardCount() {
        return cardCount;
    }

    public long getSteamId() {
        final SteamID steamID = steamClient.getSteamId();
        if (steamID != null) {
            return steamID.convertToLong();
        }
        return 0;
    }

    public void changeStatus(EPersonaState status) {
        if (isLoggedIn()) {
            steamFriends.setPersonaState(status);
        }
    }

    private Notification buildNotification(String text) {
        final Intent notificationIntent = new Intent(this, MainActivity.class);
        final PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                notificationIntent, 0);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(text)
                .setContentIntent(pendingIntent)
                .build();
    }

    /**
     * Build idling notification
     * @param game
     */
    private void buildIdleNotification(Game game) {
        Log.i(TAG, "Idle notification");
        final Intent notificationIntent = new Intent(this, MainActivity.class);
        final PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                notificationIntent, 0);

        final NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setStyle(new MediaStyle())
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.now_playing2, game.name))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setContentIntent(pendingIntent);

        if (game.dropsRemaining > 0) {
            // Show drops remaining
            builder.setSubText(getResources().getQuantityString(R.plurals.card_drops_remaining, game.dropsRemaining, game.dropsRemaining));
        }

        // Add the stop action
        final PendingIntent stopIntent = PendingIntent.getBroadcast(this, 0, new Intent(STOP_INTENT), PendingIntent.FLAG_CANCEL_CURRENT);
        builder.addAction(R.drawable.ic_stop_white_48dp, "Stop", stopIntent);

        if (farming) {
            // Add the skip action
            final PendingIntent skipIntent = PendingIntent.getBroadcast(this, 0, new Intent(SKIP_INTENT), PendingIntent.FLAG_CANCEL_CURRENT);
            builder.addAction(R.drawable.ic_skip_next_white_48dp, "Skip", skipIntent);
        }

        final NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (!Prefs.minimizeData()) {
            // Load game icon into notification
            Glide.with(getApplicationContext())
                    .load(game.iconUrl)
                    .asBitmap()
                    .into(new SimpleTarget<Bitmap>() {
                        @Override
                        public void onResourceReady(Bitmap resource, GlideAnimation<? super Bitmap> glideAnimation) {
                            builder.setLargeIcon(resource);
                            nm.notify(NOTIF_ID, builder.build());
                        }

                        @Override
                        public void onLoadFailed(Exception e, Drawable errorDrawable) {
                            super.onLoadFailed(e, errorDrawable);
                            nm.notify(NOTIF_ID, builder.build());
                        }
                    });
        } else {
            nm.notify(NOTIF_ID, builder.build());
        }
    }

    /**
     * Used to update the notification
     * @param text the text to display
     */
    private void updateNotification(String text) {
        final NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(NOTIF_ID, buildNotification(text));
    }

    public void idleSingle(Game game) {
        Log.i(TAG, "Now playing " + game.name);
        currentGame = game;
        playGame(game.appId);
        buildIdleNotification(game);
    }

    public void start() {
        running = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                steamClient.connect();

                while (running) {
                    update();
                }

                Log.i(TAG, "thread stopping");
                steamClient.disconnect();
            }
        }).start();
    }

    public void login(final LogOnDetails details) {
        Log.i(TAG, "logging in");
        new Thread(new Runnable() {
            @Override
            public void run() {
                waitForConnection();
                steamUser.logOn(details, Prefs.getMachineId());
            }
        }).start();
    }

    public void logoff() {
        Log.i(TAG, "logging off");
        stopFarming();
        steamUser.logOff();
        Prefs.writeUsername("");
        Prefs.writePassword("");
        Prefs.writeLoginKey("");
        steamClient.disconnect();
        updateNotification("Logged out");
    }

    public void disconnect() {
        steamClient.disconnect();
    }

    public void redeemKey(final String key) {
        steamUser.registerProductKey(key);
    }

    /**
     * Try to login using saved details in prefs
     */
    private void attemptRestoreLogin() {
        // Just in case
        Prefs.init(this);
        final String username = Prefs.getUsername();
        final String password = Prefs.getPassword();
        final String loginKey = Prefs.getLoginKey();
        final byte[] sentryData = readSentryFile();
        if (username.isEmpty() || password.isEmpty() || loginKey.isEmpty()) {
            return;
        }
        Log.i(TAG, "Restoring login");
        final LogOnDetails details = new LogOnDetails();
        details.username(username);
        details.loginkey = loginKey;
        if (sentryData != null) {
            details.sentryFileHash = CryptoHelper.SHAHash(sentryData);
            sentryHash = Utils.bytesToHex(details.sentryFileHash);
        }
        details.shouldRememberPassword = true;
        login(details);
    }

    private void waitForConnection() {
        while(running && !connected) {
            try {
                Log.i(TAG, "Waiting for connection...");
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void update() {
        while (true) {
            final CallbackMsg msg = steamClient.getCallback(true);

            if (msg == null) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                break;
            }

            handleSteamMessage(msg);
        }
    }

    private void handleSteamMessage(CallbackMsg msg) {
        Log.i(TAG, msg.toString());
        msg.handle(ConnectedCallback.class, new ActionT<ConnectedCallback>() {
            @Override
            public void call(ConnectedCallback callback) {
                Log.i(TAG, "Connected()");
                connected = true;
                attemptRestoreLogin();
            }
        });
        msg.handle(DisconnectedCallback.class, new ActionT<DisconnectedCallback>() {
            @Override
            public void call(DisconnectedCallback callback) {
                Log.i(TAG, "Disconnected()");
                connected = false;
                loggedIn = false;
                if (running) {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                Thread.sleep(5000);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            steamClient.connect();
                        }
                    }).start();
                }
                // Notify the activity that user is logged out
                LocalBroadcastManager.getInstance(SteamService.this).sendBroadcast(new Intent(DISCONNECT_EVENT));
            }
        });
        msg.handle(LoggedOffCallback.class, new ActionT<LoggedOffCallback>() {
            @Override
            public void call(LoggedOffCallback callback) {
                Log.i(TAG, "Logoff result " + callback.getResult().toString());
                if (callback.getResult() == EResult.LoggedInElsewhere) {
                    updateNotification("Logged in elsewhere");
                    stopFarming();
                }
                // Reconnect
                steamClient.disconnect();
            }
        });
        msg.handle(LoggedOnCallback.class, new ActionT<LoggedOnCallback>() {
            @Override
            public void call(final LoggedOnCallback callback) {
                final EResult result = callback.getResult();
                Log.i(TAG, result.toString());

                webApiUserNonce = callback.getWebAPIUserNonce();

                if (result == EResult.OK) {
                    loggedIn = true;
                    updateNotification("Logged in");
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            boolean gotAuth = false;
                            for (int i=0;i<3;i++) {
                                gotAuth = authenticate();
                                Log.i(TAG, "Got auth? "  + gotAuth);
                                if (gotAuth) {
                                    break;
                                }
                                Log.i(TAG, "retrying...");
                                try {
                                    Thread.sleep(500);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                            }

                            if (gotAuth) {
                                if (farming) {
                                    Log.i(TAG, "Resume farming");
                                    new Thread(new Runnable() {
                                        @Override
                                        public void run() {
                                            farm();
                                            // Reset inventory notifications
                                            WebScraper.viewInventory(generateWebCookies());
                                        }
                                    }).start();
                                } else if (currentGame != null) {
                                    Log.i(TAG, "Resume playing");
                                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                                        @Override
                                        public void run() {
                                            idleSingle(currentGame);
                                        }
                                    });
                                }
                            } else {
                                updateNotification("Unable to get Steam web authentication!");
                            }
                        }
                    }).start();

                    if (!Prefs.getOffline()) {
                        steamFriends.setPersonaState(EPersonaState.Online);
                    }
                } else {
                    if (result == EResult.InvalidPassword && !Prefs.getLoginKey().isEmpty()) {
                        // Probably no longer valid
                        Prefs.writeLoginKey("");
                        updateNotification("Login key expired!");
                    }

                    // Reconnect
                    steamClient.disconnect();
                }

                // Tell LoginActivity the result
                final Intent intent = new Intent(LOGIN_EVENT);
                intent.putExtra(RESULT, result);
                LocalBroadcastManager.getInstance(SteamService.this).sendBroadcast(intent);
            }
        });
        msg.handle(LoginKeyCallback.class, new ActionT<LoginKeyCallback>() {
            @Override
            public void call(LoginKeyCallback callback) {
                Log.i(TAG, "Saving loginkey");
                Prefs.writeLoginKey(callback.getLoginKey());
            }
        });
        msg.handle(JobCallback.class, new ActionT<JobCallback>() {
            @Override
            public void call(JobCallback callback) {
                if (callback.getCallbackType() == UpdateMachineAuthCallback.class) {
                    Log.i(TAG, "Got new sentry file");
                    final UpdateMachineAuthCallback authCallback = (UpdateMachineAuthCallback) callback.getCallback();
                    final byte[] data = authCallback.getData();
                    final byte[] sha1 = CryptoHelper.SHAHash(data);

                    writeSentryFile(data);

                    final MachineAuthDetails auth = new MachineAuthDetails();
                    auth.jobId = callback.getJobId().getValue();
                    auth.fileName = authCallback.getFileName();
                    auth.bytesWritten = authCallback.getBytesToWrite();
                    auth.fileSize = data.length;
                    auth.offset = authCallback.getOffset();
                    auth.result = EResult.OK;
                    auth.lastError = 0;
                    auth.oneTimePassword = authCallback.getOneTimePassword();
                    auth.sentryFileHash = sha1;

                    steamUser.sendMachineAuthResponse(auth);

                    sentryHash = Utils.bytesToHex(sha1);
                    Prefs.writeSentryHash(sentryHash);
                }
            }
        });
        msg.handle(CMListCallback.class, new ActionT<CMListCallback>() {
            @Override
            public void call(CMListCallback callback) {
                final String[] servers = callback.getServerList();
                if (servers.length > 0) {
                    Log.i(TAG, "Saving CM servers");
                    final StringBuilder serverString = new StringBuilder();
                    for (int i=0,size=servers.length;i<size;i++) {
                        serverString.append(servers[i]);
                        if (i + 1 < size) {
                            serverString.append(",");
                        }
                    }
                    Log.i(TAG, serverString.toString());
                    Prefs.writeCmServers(serverString.toString());
                }
            }
        });
        msg.handle(NotificationUpdateCallback.class, new ActionT<NotificationUpdateCallback>() {
            @Override
            public void call(NotificationUpdateCallback callback) {
                Log.i(TAG, "New notifications " + callback.getNotificationCounts().toString());
                for (Map.Entry<NotificationType,Integer> entry: callback.getNotificationCounts().entrySet()) {
                    if (entry.getKey() == NotificationType.ITEMS && entry.getValue() > 0  && farming) {
                        // Possible card drop
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                farm();
                                // Reset inventory notifications
                                WebScraper.viewInventory(generateWebCookies());
                            }
                        }).start();
                        break;
                    }
                }
            }
        });
        msg.handle(PurchaseResponseCallback.class, new ActionT<PurchaseResponseCallback>() {
            @Override
            public void call(PurchaseResponseCallback callback) {

                if (callback.getResult() == EResult.OK) {
                    final KeyValue kv = callback.getPurchaseReceiptInfo().getKeyValues();
                    if (kv.get("PaymentMethod").asInteger() == EPaymentMethod.ActivationCode.v()) {
                        final StringBuilder products = new StringBuilder("Activated: ");
                        final int size = kv.get("LineItemCount").asInteger();
                        Log.i(TAG, "LineItemCount " + size);
                        for (int i=0;i<size;i++) {
                            final String lineItem = kv.get("lineitems").get(i + "").get("ItemDescription").asString();
                            Log.i(TAG, "lineItem " + i + " " + lineItem);
                            products.append(lineItem);
                            if (i + 1 < size) {
                                products.append(", ");
                            }
                        }
                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(getApplicationContext(), products.toString(), Toast.LENGTH_LONG).show();
                            }
                        });
                    }
                } else {
                    final int purchaseResult = callback.getPurchaseResultDetails();

                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            String error;
                            if (purchaseResult == 9) {
                                error = getString(R.string.product_already_owned);
                            } else if (purchaseResult == 14) {
                                error = getString(R.string.invalid_key);
                            } else {
                                error = getString(R.string.activation_failed);
                            }
                            Toast.makeText(getApplicationContext(), error, Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        });
    }

    /**
     * Idle a game
     * @param appId game to idle
     */
    private void playGame(int appId) {
        steamUser.setPlayingGame(appId);
    }


    /**
     * Idle multiple games at once
     * @param appIds the games to idle
     */
    private void playGames(int...appIds) {
        // Array of games played
        final SteammessagesClientserver.CMsgClientGamesPlayed.GamePlayed[] gamesPlayed =
                new SteammessagesClientserver.CMsgClientGamesPlayed.GamePlayed[appIds.length];

        for (int i=0;i<gamesPlayed.length;i++) {
            // A single game played
            final SteammessagesClientserver.CMsgClientGamesPlayed.GamePlayed gp =
                    new SteammessagesClientserver.CMsgClientGamesPlayed.GamePlayed();
            gp.gameId = appIds[i];
            gamesPlayed[i] = gp;
        }

        final ClientMsgProtobuf<SteammessagesClientserver.CMsgClientGamesPlayed> playGame;
        playGame = new ClientMsgProtobuf<SteammessagesClientserver.CMsgClientGamesPlayed>(SteammessagesClientserver.CMsgClientGamesPlayed.class, EMsg.ClientGamesPlayed);
        playGame.getBody().gamesPlayed = gamesPlayed;
        steamClient.send(playGame);
    }

    private void stopPlaying() {
        currentGame = null;
        steamUser.setPlayingGame(0);
    }

    /**
     * Generate Steam web cookies
     * @return Map of the cookies or null
     */
    public Map<String,String> generateWebCookies() {
        if (!authenticated) {
            return null;
        }

        final Map<String, String> cookies = new HashMap<>();
        cookies.put("sessionid", sessionId);
        cookies.put("steamLogin", token);
        cookies.put("steamLoginSecure", tokenSecure);
        if (sentryHash != null) {
            cookies.put("steamMachineAuth" + steamClient.getSteamId().convertToLong(), sentryHash);
        }
        if (steamParental != null) {
            cookies.put("steamparental", steamParental);
        }
        return cookies;
    }

    private void writeSentryFile(byte[] data) {
        final File sentryFolder = new File(getFilesDir(), "sentry");
        if (sentryFolder.exists() || sentryFolder.mkdir()) {
            final File sentryFile = new File(sentryFolder, Prefs.getUsername() + ".sentry");
            FileOutputStream fos = null;
            try {
                Log.i(TAG, "Writing sentry file to " + sentryFile.getAbsolutePath());
                fos = new FileOutputStream(sentryFile);
                fos.write(data);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (fos != null) {
                    try {
                        fos.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private byte[] readSentryFile() {
        final File sentryFolder = new File(getFilesDir(), "sentry");
        final File sentryFile = new File(sentryFolder, Prefs.getUsername() + ".sentry");
        if (sentryFile.exists()) {
            Log.i(TAG, "Reading sentry file " + sentryFile.getAbsolutePath());
            FileInputStream fis = null;
            try {
                fis = new FileInputStream(sentryFile);
                final byte[] data = new byte[fis.available()];
                fis.read(data);
                return data;
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (fis != null) {
                    try {
                        fis.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        return null;
    }

    /**
     * Authenticate. This does the same as SteamWeb.DoLogin(),
     * but without contacting the Steam Website.
     * Should this one stop working, use SteamWeb.DoLogin().
     */
    public boolean authenticate() {
        authenticated = false;

        //sessionId = Base64.encodeToString(String.valueOf(callback.getUniqueId()).getBytes(), Base64.DEFAULT);
        sessionId = Utils.bytesToHex(CryptoHelper.GenerateRandomBlock(4));

        final WebAPI userAuth = new WebAPI("ISteamUserAuth", null);
        // generate an AES session key
        final byte[] sessionKey = CryptoHelper.GenerateRandomBlock(32);

        // rsa encrypt it with the public key for the universe we're on
        byte[] cryptedSessionKey = null;
        final byte[] publicKey = KeyDictionary.getPublicKey(steamClient.getConnectedUniverse());
        if (publicKey == null) {
            return false;
        }
        final RSACrypto rsa = new RSACrypto(publicKey);
        cryptedSessionKey = rsa.encrypt(sessionKey);

        final byte[] loginKey = new byte[20];
        System.arraycopy(webApiUserNonce.getBytes(), 0, loginKey, 0, webApiUserNonce.length());

        // aes encrypt the loginkey with our session key
        final byte[] cryptedLoginKey = CryptoHelper.SymmetricEncrypt(loginKey, sessionKey);

        KeyValue authResult;

        try {
            authResult = userAuth.authenticateUser(String.valueOf(steamClient.getSteamId().convertToLong()), WebHelpers.UrlEncode(cryptedSessionKey), WebHelpers.UrlEncode(cryptedLoginKey), "POST", "true");
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
            steamParental = WebScraper.unlockParental(pin, generateWebCookies());
        }

        return true;
    }
}
