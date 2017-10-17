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
import android.os.PowerManager;
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
import com.steevsapps.idledaddy.BuildConfig;
import com.steevsapps.idledaddy.MainActivity;
import com.steevsapps.idledaddy.R;
import com.steevsapps.idledaddy.handlers.FreeLicense;
import com.steevsapps.idledaddy.handlers.FreeLicenseCallback;
import com.steevsapps.idledaddy.listeners.LogcatDebugListener;
import com.steevsapps.idledaddy.steam.wrapper.Game;
import com.steevsapps.idledaddy.utils.Prefs;
import com.steevsapps.idledaddy.utils.Utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
import uk.co.thomasc.steamkit.steam3.handlers.steamfriends.callbacks.PersonaStateCallback;
import uk.co.thomasc.steamkit.steam3.handlers.steamnotifications.callbacks.NotificationUpdateCallback;
import uk.co.thomasc.steamkit.steam3.handlers.steamnotifications.types.NotificationType;
import uk.co.thomasc.steamkit.steam3.handlers.steamuser.SteamUser;
import uk.co.thomasc.steamkit.steam3.handlers.steamuser.callbacks.AccountInfoCallback;
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
import uk.co.thomasc.steamkit.types.keyvalue.KeyValue;
import uk.co.thomasc.steamkit.util.cSharp.events.ActionT;
import uk.co.thomasc.steamkit.util.crypto.CryptoHelper;
import uk.co.thomasc.steamkit.util.logging.DebugLog;

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
    public final static String PERSONA_EVENT = "PERSONA_EVENT"; // Emitted when we get PersonaStateCallback
    public final static String PERSONA_NAME = "PERSONA_NAME"; // Username
    public final static String AVATAR_HASH = "AVATAR_HASH"; // User avatar hash

    // Actions
    public final static String SKIP_INTENT = "SKIP_INTENT";
    public final static String STOP_INTENT = "STOP_INTENT";
    public final static String PAUSE_INTENT = "PAUSE_INTENT";
    public final static String RESUME_INTENT = "RESUME_INTENT";

    private SteamClient steamClient;
    private SteamUser steamUser;
    private SteamFriends steamFriends;
    private FreeLicense freeLicense;
    private SteamWebHandler webHandler = SteamWebHandler.getInstance();
    private PowerManager.WakeLock wakeLock;

    private int farmIndex = 0;
    private List<Game> gamesToFarm;
    private List<Game> currentGames = new ArrayList<>();
    private int gameCount = 0;
    private int cardCount = 0;
    private String personaName = "";
    private String avatarHash = "";

    private volatile boolean running = false; // Service running
    private volatile boolean connected = false; // Connected to Steam
    private volatile boolean farming = false; // Currently farming
    private volatile boolean paused = false; // Game paused
    private volatile boolean waiting = false; // Waiting for user to stop playing

    private long steamId;
    private boolean loggedIn = false;

    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(8);
    private Future<?> loginHandle;
    private ScheduledFuture<?> farmHandle;
    private ScheduledFuture<?> waitHandle;
    private ScheduledFuture<?> timeoutHandle;

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
            switch (intent.getAction()) {
                case SKIP_INTENT:
                    skipGame();
                    break;
                case STOP_INTENT:
                    stopGame();
                    break;
                case PAUSE_INTENT:
                    pauseGame();
                    break;
                case RESUME_INTENT:
                    resumeGame();
                    break;
            }
        }
    };

    private final class FarmTask implements Runnable {
        @Override
        public void run() {
            try {
                farm();
            } catch (Exception e) {
                Log.i(TAG, "FarmTask failed", e);
            }
        }
    }

    private final FarmTask farmTask = new FarmTask();

    /**
     * Wait for user to NOT be in-game so we can resume idling
     */
    private final class WaitTask implements Runnable {
        @Override
        public void run() {
            try {
                Log.i(TAG, "Checking if we can resume idling...");
                final Boolean notInGame = webHandler.checkIfNotInGame();
                if (notInGame == null) {
                    Log.i(TAG, "Invalid cookie data or no internet, reconnecting...");
                    steamClient.disconnect();
                } else if (notInGame) {
                    Log.i(TAG, "Resuming...");
                    waiting = false;
                    steamClient.disconnect();
                    waitHandle.cancel(false);
                }
            } catch (Exception e) {
                Log.i(TAG, "WaitTask failed", e);
            }
        }
    }

    private final WaitTask waitTask = new WaitTask();

    /**
     * Task to restart connection if it hangs
     * TODO: determine if this is really needed and if so fix in SteamKit
     */
    private final class TimeoutTask implements Runnable {
        @Override
        public void run() {
            Log.i(TAG, "Reconnecting (connection timed out)");
            steamClient.disconnect();
        }
    }

    private final TimeoutTask timeoutTask = new TimeoutTask();

    public void startFarming() {
        if (!farming) {
            farming = true;
            executor.execute(farmTask);
        }
    }

    public void stopFarming() {
        if (farming) {
            farming = false;
            farmIndex = 0;
            currentGames.clear();
            unscheduleFarmTask();
        }
    }

    /**
     * Resume farming/idling
     */
    private void resumeFarming() {
        if (paused || waiting) {
            return;
        }

        if (farming) {
            Log.i(TAG, "Resume farming");
            executor.execute(farmTask);
        } else if (currentGames.size() == 1) {
            Log.i(TAG, "Resume playing");
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    idleSingle(currentGames.get(0));
                }
            });
        } else if (currentGames.size() > 1) {
            Log.i(TAG, "Resume playing (multiple)");
            idleMultiple(currentGames);
        }
    }

    private void farm() {
        if (paused || waiting) {
            return;
        }
        Log.i(TAG, "Checking remaining card drops");
        for (int i=0;i<3;i++) {
            gamesToFarm = webHandler.getRemainingGames();
            if (gamesToFarm != null) {
                Log.i(TAG, "gotem");
                break;
            }
            if (i + 1 < 3) {
                Log.i(TAG, "retrying...");
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    return;
                }
            }
        }

        if (gamesToFarm == null) {
            Log.i(TAG, "Invalid cookie data or no internet, reconnecting");
            steamClient.disconnect();
            return;
        }

        // Count the games and cards
        gameCount = gamesToFarm.size();
        cardCount = 0;
        for (Game g : gamesToFarm) {
            cardCount += g.dropsRemaining;
        }

        // Send farm event
        final Intent event = new Intent(FARM_EVENT);
        event.putExtra(GAME_COUNT, gameCount);
        event.putExtra(CARD_COUNT, cardCount);
        LocalBroadcastManager.getInstance(SteamService.this)
                .sendBroadcast(event);

        if (gamesToFarm.isEmpty()) {
            Log.i(TAG, "Finished idling");
            stopPlaying();
            updateNotification(getString(R.string.idling_finished));
            stopFarming();
            return;
        }

        // Sort by hours played descending
        Collections.sort(gamesToFarm, Collections.reverseOrder());

        if (farmIndex >= gamesToFarm.size()) {
            farmIndex = 0;
        }
        final Game game = gamesToFarm.get(farmIndex);

        // TODO: Steam only updates play time every half hour, so maybe we should keep track of it ourselves
        if (game.hoursPlayed >= 2 || gamesToFarm.size() == 1 || Prefs.simpleFarming() || farmIndex > 0) {
            // If a game has over 2 hrs we can just idle it
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    idleSingle(game);
                }
            });
            unscheduleFarmTask();
        } else {
            // Idle multiple games (max 32) until one has reached 2 hrs
            idleMultiple(gamesToFarm);
            scheduleFarmTask();
        }

        // Reset inventory notifications
        webHandler.viewInventory();
    }

    private void skipGame() {
        if (gamesToFarm == null || gamesToFarm.size() < 2) {
            return;
        }

        farmIndex++;
        if (farmIndex >= gamesToFarm.size()) {
            farmIndex = 0;
        }

        idleSingle(gamesToFarm.get(farmIndex));
    }

    private void stopGame() {
        stopPlaying();
        stopFarming();
        updateNotification(getString(R.string.stopped));
        LocalBroadcastManager.getInstance(SteamService.this).sendBroadcast(new Intent(STOP_EVENT));
    }

    private void pauseGame() {
        paused = true;
        stopPlaying();
        showPausedNotification();
    }

    private void resumeGame() {
        paused = false;
        if (currentGames.size() == 1) {
            Log.i(TAG, "Resume playing");
            idleSingle(currentGames.get(0));
        } else if (currentGames.size() > 1) {
            Log.i(TAG, "Resume playing (multiple)");
            idleMultiple(currentGames);
        }
    }

    private void scheduleFarmTask() {
        if (farmHandle == null || farmHandle.isCancelled()) {
            Log.i(TAG, "Starting farmtask");
            farmHandle = scheduler.scheduleAtFixedRate(farmTask, 10, 10, TimeUnit.MINUTES);
        }
    }

    private void unscheduleFarmTask() {
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
        steamClient.addHandler(new FreeLicense());
        freeLicense = steamClient.getHandler(FreeLicense.class);
        // Acquire WakeLock to keep the CPU from sleeping
        final PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "IdleDaddyWakeLock");
        wakeLock.acquire();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Create notification channel
            createChannel();
        }
        if (BuildConfig.DEBUG) {
            DebugLog.addListener(new LogcatDebugListener());
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
            filter.addAction(PAUSE_INTENT);
            filter.addAction(RESUME_INTENT);
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
        executor.shutdownNow();
        scheduler.shutdownNow();
        wakeLock.release();
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

    /**
     * Get the games we're currently idling
     */
    public ArrayList<Game> getCurrentGames() {
        return  new ArrayList<>(currentGames);
    }

    public int getGameCount() {
        return gameCount;
    }

    public int getCardCount() {
        return cardCount;
    }

    public String getPersonaName() {
        return personaName;
    }

    public String getAvatarHash() {
        return avatarHash;
    }

    public long getSteamId() {
        return steamId;
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
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(text)
                .setContentIntent(pendingIntent)
                .build();
    }

    /**
     * Show idling notification
     * @param game
     */
    private void showIdleNotification(Game game) {
        Log.i(TAG, "Idle notification");
        final Intent notificationIntent = new Intent(this, MainActivity.class);
        final PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                notificationIntent, 0);

        final NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setStyle(new MediaStyle())
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.now_playing2, game.name))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setContentIntent(pendingIntent);

        if (game.dropsRemaining > 0) {
            // Show drops remaining
            builder.setSubText(getResources().getQuantityString(R.plurals.card_drops_remaining, game.dropsRemaining, game.dropsRemaining));
        }

        // Add the stop and pause actions
        final PendingIntent stopIntent = PendingIntent.getBroadcast(this, 0, new Intent(STOP_INTENT), PendingIntent.FLAG_CANCEL_CURRENT);
        final PendingIntent pauseIntent = PendingIntent.getBroadcast(this, 0, new Intent(PAUSE_INTENT), PendingIntent.FLAG_CANCEL_CURRENT);
        builder.addAction(R.drawable.ic_stop_white_32dp, getString(R.string.stop), stopIntent);
        builder.addAction(R.drawable.ic_pause_white_32dp, getString(R.string.pause), pauseIntent);

        if (farming) {
            // Add the skip action
            final PendingIntent skipIntent = PendingIntent.getBroadcast(this, 0, new Intent(SKIP_INTENT), PendingIntent.FLAG_CANCEL_CURRENT);
            builder.addAction(R.drawable.ic_skip_next_white_32dp, getString(R.string.skip), skipIntent);
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
     * Show "Big Text" style notification with the games we're idling
     * @param msg the games
     */
    private void showMultipleNotification(String msg) {
        final PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), 0);

        // Add stop and pause actions
        final PendingIntent stopIntent = PendingIntent.getBroadcast(this, 0, new Intent(STOP_INTENT), PendingIntent.FLAG_CANCEL_CURRENT);
        final PendingIntent pauseIntent = PendingIntent.getBroadcast(this, 0, new Intent(PAUSE_INTENT), PendingIntent.FLAG_CANCEL_CURRENT);

        final Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(msg))
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.idling_multiple))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setContentIntent(pendingIntent)
                .addAction(R.drawable.ic_stop_white_32dp, getString(R.string.stop), stopIntent)
                .addAction(R.drawable.ic_pause_white_32dp, getString(R.string.pause), pauseIntent)
                .build();

        final NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.notify(NOTIF_ID, notification);
    }

    private void showPausedNotification() {
        final PendingIntent pi = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), 0);
        final PendingIntent resumeIntent = PendingIntent.getBroadcast(this, 0, new Intent(RESUME_INTENT), PendingIntent.FLAG_CANCEL_CURRENT);
        final Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.paused))
                .setContentIntent(pi)
                .addAction(R.drawable.ic_play_arrow_white_32dp, getString(R.string.resume), resumeIntent)
                .build();
        final NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.notify(NOTIF_ID, notification);
    }

    /**
     * Used to update the notification
     * @param text the text to display
     */
    private void updateNotification(String text) {
        final NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(NOTIF_ID, buildNotification(text));
    }

    private void idleSingle(Game game) {
        Log.i(TAG, "Now playing " + game.name);
        currentGames.clear();
        currentGames.add(game);
        playGame(game.appId);
        showIdleNotification(game);
    }

    private void idleMultiple(List<Game> games) {
        Log.i(TAG, "Idling multiple");
        final List<Game> gamesCopy = new ArrayList<>(games);
        currentGames.clear();

        int size = gamesCopy.size();
        if (size > 32) {
            size = 32;
        }

        final int[] appIds = new int[size];
        final StringBuilder msg = new StringBuilder();
        for (int i=0;i<size;i++) {
            final Game game = gamesCopy.get(i);
            currentGames.add(game);
            appIds[i] = game.appId;
            msg.append(game.name);
            if (i + 1 < size) {
                msg.append("\n");
            }
        }

        playGames(appIds);
        showMultipleNotification(msg.toString());
    }

    public void addGame(Game game) {
        if (currentGames.isEmpty()) {
            idleSingle(game);
        } else {
            currentGames.add(game);
            idleMultiple(currentGames);
        }
    }

    public void removeGame(Game game) {
        currentGames.remove(game);
        if (currentGames.size() == 1) {
            idleSingle(currentGames.get(0));
        } else if (currentGames.size() > 1) {
            idleMultiple(currentGames);
        } else {
            stopPlaying();
            updateNotification(getString(R.string.stopped));
        }
    }

    public void start() {
        running = true;
        executor.execute(new Runnable() {
            @Override
            public void run() {
                steamClient.connect();
            }
        });
        scheduler.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                try {
                    update();
                } catch (Exception e) {
                    Log.i(TAG, "update() failed", e);
                }
            }
        }, 500, 500, TimeUnit.MILLISECONDS);
    }

    public void login(final LogOnDetails details) {
        Log.i(TAG, "logging in");
        details.loginId = NOTIF_ID;
        loginHandle = executor.submit(new Runnable() {
            @Override
            public void run() {
                waitForConnection();
                steamUser.logOn(details, Prefs.getMachineId());
            }
        });
    }

    public void logoff() {
        Log.i(TAG, "logging off");
        steamId = 0;
        stopFarming();
        steamUser.logOff();
        Prefs.writeUsername("");
        Prefs.writeLoginKey("");
        disconnect();
        updateNotification(getString(R.string.logged_out));
    }

    public void disconnect() {
        if (loginHandle != null) {
            loginHandle.cancel(true);
        }
        executor.execute(new Runnable() {
            @Override
            public void run() {
                steamClient.disconnect();
            }
        });
    }

    /**
     * Redeem Steam key or activate free license
     */
    public void redeemKey(final String key) {
        if (key.matches("\\d+")) {
            freeLicense.requestFreeLicense(Integer.parseInt(key));
        } else {
            steamUser.registerProductKey(key);
        }
    }


    private void startTimeout() {
        stopTimeout();
        Log.i(TAG, "Starting timeout");
        timeoutHandle = scheduler.schedule(timeoutTask, 15, TimeUnit.SECONDS);
    }

    private void stopTimeout() {
        if (timeoutHandle != null) {
            Log.i(TAG, "Stopping timeout");
            timeoutHandle.cancel(true);
        }
    }

    /**
     * Try to login using saved details in prefs
     */
    private void attemptRestoreLogin() {
        // Just in case
        Prefs.init(this);
        final String username = Prefs.getUsername();
        final String loginKey = Prefs.getLoginKey();
        final byte[] sentryData = readSentryFile();
        if (username.isEmpty() || loginKey.isEmpty()) {
            return;
        }
        Log.i(TAG, "Restoring login");
        final LogOnDetails details = new LogOnDetails();
        details.username(username);
        details.loginkey = loginKey;
        if (sentryData != null) {
            details.sentryFileHash = CryptoHelper.SHAHash(sentryData);
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
                break;
            }
        }
    }

    private void update() {
        while (true) {
            final CallbackMsg msg = steamClient.getCallback(true);

            if (msg == null) {
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
                stopTimeout();
                connected = true;
                attemptRestoreLogin();
            }
        });
        msg.handle(DisconnectedCallback.class, new ActionT<DisconnectedCallback>() {
            @Override
            public void call(DisconnectedCallback callback) {
                Log.i(TAG, "Disconnected()");
                stopTimeout();
                connected = false;
                loggedIn = false;
                // Try to reconnect after a 3 second delay
                scheduler.schedule(new Runnable() {
                    @Override
                    public void run() {
                        // Disconnect in 15 seconds if connection hangs....
                        startTimeout();
                        Log.i(TAG, "Reconnecting");
                        steamClient.connect();
                    }
                }, 3, TimeUnit.SECONDS);
                // Tell the activity that we've been disconnected from Steam
                LocalBroadcastManager.getInstance(SteamService.this).sendBroadcast(new Intent(DISCONNECT_EVENT));
            }
        });
        msg.handle(LoggedOffCallback.class, new ActionT<LoggedOffCallback>() {
            @Override
            public void call(LoggedOffCallback callback) {
                Log.i(TAG, "Logoff result " + callback.getResult().toString());
                stopTimeout();
                if (callback.getResult() == EResult.LoggedInElsewhere) {
                    updateNotification(getString(R.string.logged_in_elsewhere));
                    unscheduleFarmTask();
                    if (!waiting) {
                        waiting = true;
                        waitHandle = scheduler.scheduleAtFixedRate(waitTask, 0, 30, TimeUnit.SECONDS);
                    }
                } else {
                    // Reconnect
                    steamClient.disconnect();
                }
            }
        });
        msg.handle(LoggedOnCallback.class, new ActionT<LoggedOnCallback>() {
            @Override
            public void call(final LoggedOnCallback callback) {
                final EResult result = callback.getResult();
                Log.i(TAG, result.toString());

                final String webApiUserNonce = callback.getWebAPIUserNonce();

                if (result == EResult.OK) {
                    loggedIn = true;
                    steamId = steamClient.getSteamId().convertToLong();
                    // Don't hide the paused notification
                    if (!paused) {
                        updateNotification(getString(R.string.logged_in));
                    } else {
                        showPausedNotification();
                    }
                    executor.execute(new Runnable() {
                        @Override
                        public void run() {
                            boolean gotAuth = false;
                            for (int i=0;i<3;i++) {
                                gotAuth = webHandler.authenticate(steamClient, webApiUserNonce);
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
                                resumeFarming();
                            } else {
                                updateNotification(getString(R.string.web_login_failed));
                            }
                        }
                    });
                } else {
                    if (result == EResult.InvalidPassword && !Prefs.getLoginKey().isEmpty()) {
                        // Probably no longer valid
                        Log.i(TAG, "Login key expired");
                        Prefs.writeLoginKey("");
                        updateNotification(getString(R.string.login_key_expired));
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

                    final String sentryHash = Utils.bytesToHex(sha1);
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
                    Prefs.writeCmServers(Utils.arrayToString(servers));
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
                        executor.execute(farmTask);
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
                        final StringBuilder products = new StringBuilder();
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
                                Toast.makeText(getApplicationContext(), getString(R.string.activated, products.toString()), Toast.LENGTH_LONG).show();
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
        msg.handle(PersonaStateCallback.class, new ActionT<PersonaStateCallback>() {
            @Override
            public void call(PersonaStateCallback callback) {
                if (steamClient.getSteamId().equals(callback.getFriendID())) {
                    personaName = callback.getName();
                    avatarHash = Utils.bytesToHex(callback.getAvatarHash()).toLowerCase();
                    Log.i(TAG, "Avatar hash" + avatarHash);
                    final Intent event = new Intent(PERSONA_EVENT);
                    event.putExtra(PERSONA_NAME, personaName);
                    event.putExtra(AVATAR_HASH, avatarHash);
                    LocalBroadcastManager.getInstance(SteamService.this).sendBroadcast(event);
                }
            }
        });
        msg.handle(FreeLicenseCallback.class, new ActionT<FreeLicenseCallback>() {
            @Override
            public void call(final FreeLicenseCallback callback) {
                final int gameId = callback.getGameId();
                final int[] grantedApps = callback.getGrantedApps();
                final int[] grantedPackages = callback.getGrantedPackages();

                if (grantedApps.length > 0 || grantedPackages.length > 0) {
                    // Granted
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(SteamService.this, getString(R.string.activated, String.valueOf(gameId)), Toast.LENGTH_LONG).show();
                        }
                    });
                } else {
                    // Try activating it with the web handler
                    executor.execute(new Runnable() {
                        @Override
                        public void run() {
                            final String msg;
                            if (webHandler.addFreeLicense(gameId)) {
                                msg = getString(R.string.activated, String.valueOf(gameId));
                            } else {
                                msg = getString(R.string.activation_failed);
                            }
                            new Handler(Looper.getMainLooper()).post(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(SteamService.this, msg, Toast.LENGTH_LONG).show();
                                }
                            });
                        }
                    });
                }
            }
        });
        msg.handle(AccountInfoCallback.class, new ActionT<AccountInfoCallback>() {
            @Override
            public void call(AccountInfoCallback callback) {
                if (Prefs.getOffline()) {
                    return;
                }

                // Try to delay calling setPersonaState until persona name is ready. Thanks ASF
                int tries = 0;
                String nickname;
                while (tries < 5) {
                    nickname = steamFriends.getPersonaName();
                    if (nickname != null && !nickname.equals("[unassigned]")) {
                        break;
                    }
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    tries++;
                }

                steamFriends.setPersonaState(EPersonaState.Online);
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
        if (!paused) {
            currentGames.clear();
        }
        steamUser.setPlayingGame(0);
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
                final byte[] data = new byte[(int) sentryFile.length()];
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
}
