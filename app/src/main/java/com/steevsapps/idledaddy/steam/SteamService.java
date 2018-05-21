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
import com.steevsapps.idledaddy.handlers.PurchaseResponse;
import com.steevsapps.idledaddy.handlers.callbacks.PurchaseResponseCallback;
import com.steevsapps.idledaddy.listeners.AndroidLogListener;
import com.steevsapps.idledaddy.preferences.PrefsManager;
import com.steevsapps.idledaddy.steam.model.Game;
import com.steevsapps.idledaddy.utils.LocaleManager;
import com.steevsapps.idledaddy.utils.Utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.zip.CRC32;

import in.dragonbra.javasteam.base.ClientMsgProtobuf;
import in.dragonbra.javasteam.enums.EMsg;
import in.dragonbra.javasteam.enums.EOSType;
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
import in.dragonbra.javasteam.util.log.LogManager;

public class SteamService extends Service {
    private final static String TAG = SteamService.class.getSimpleName();
    private final static int NOTIF_ID = 6896; // Ongoing notification ID
    private final static String CHANNEL_ID = "idle_channel"; // Notification channel
    // Some Huawei phones reportedly kill apps when they hold a WakeLock for a long time.
    // This can be prevented by using a WakeLock tag from the PowerGenie whitelist.
    private final static String WAKELOCK_TAG = "LocationManagerService";

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
    public final static String NOW_PLAYING_EVENT = "NOW_PLAYING_EVENT"; // Emitted when the game you're idling changes

    // Actions
    public final static String SKIP_INTENT = "SKIP_INTENT";
    public final static String STOP_INTENT = "STOP_INTENT";
    public final static String PAUSE_INTENT = "PAUSE_INTENT";
    public final static String RESUME_INTENT = "RESUME_INTENT";

    private SteamClient steamClient;
    private CallbackManager manager;
    private SteamUser steamUser;
    private SteamFriends steamFriends;
    private SteamApps steamApps;
    private SteamWebHandler webHandler = SteamWebHandler.getInstance();
    private PowerManager.WakeLock wakeLock;

    private int farmIndex = 0;
    private List<Game> gamesToFarm;
    private List<Game> currentGames = new ArrayList<>();
    private int gameCount = 0;
    private int cardCount = 0;
    private LogOnDetails logOnDetails = null;

    private volatile boolean running = false; // Service running
    private volatile boolean connected = false; // Connected to Steam
    private volatile boolean farming = false; // Currently farming
    private volatile boolean paused = false; // Game paused
    private volatile boolean waiting = false; // Waiting for user to stop playing
    private volatile boolean loginInProgress = true; // Currently logging in, so don't reconnect on disconnects

    private long steamId;
    private boolean loggedIn = false;
    private boolean isHuawei = false;

    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(8);
    private ScheduledFuture<?> farmHandle;
    private ScheduledFuture<?> waitHandle;

    private final LinkedList<Integer> pendingFreeLicenses = new LinkedList<>();

    private File sentryFolder;

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

    private final Runnable farmTask = new Runnable() {
        @Override
        public void run() {
            try {
                farm();
            } catch (Exception e) {
                Log.i(TAG, "FarmTask failed", e);
            }
        }
    };

    /**
     * Wait for user to NOT be in-game so we can resume idling
     */
    private final Runnable waitTask = new Runnable() {
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
    };

    public void startFarming() {
        if (!farming) {
            farming = true;
            paused = false;
            executor.execute(farmTask);
        }
    }

    public void stopFarming() {
        if (farming) {
            farming = false;
            gamesToFarm = null;
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
            new Handler(Looper.getMainLooper()).post(() -> idleSingle(currentGames.get(0)));
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
            //steamClient.disconnect();
            steamUser.requestWebAPIUserNonce();
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
        if (game.hoursPlayed >= PrefsManager.getHoursUntilDrops() || gamesToFarm.size() == 1 || farmIndex > 0) {
            // Idle a single game
            new Handler(Looper.getMainLooper()).post(() -> idleSingle(game));
            unscheduleFarmTask();
        } else {
            // Idle multiple games (max 32) until one has reached 2 hrs
            idleMultiple(gamesToFarm);
            scheduleFarmTask();
        }
    }

    public void skipGame() {
        if (gamesToFarm == null || gamesToFarm.size() < 2) {
            return;
        }

        farmIndex++;
        if (farmIndex >= gamesToFarm.size()) {
            farmIndex = 0;
        }

        idleSingle(gamesToFarm.get(farmIndex));
    }

    public void stopGame() {
        paused = false;
        stopPlaying();
        stopFarming();
        updateNotification(getString(R.string.stopped));
        LocalBroadcastManager.getInstance(SteamService.this).sendBroadcast(new Intent(STOP_EVENT));
    }

    public void pauseGame() {
        paused = true;
        stopPlaying();
        showPausedNotification();
        // Tell the activity to update
        LocalBroadcastManager.getInstance(SteamService.this).sendBroadcast(new Intent(NOW_PLAYING_EVENT));
    }

    public void resumeGame() {
        if (farming) {
            Log.i(TAG, "Resume farming");
            paused = false;
            executor.execute(farmTask);
        } else if (currentGames.size() == 1) {
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

        sentryFolder = new File(getFilesDir(), "sentry");
        sentryFolder.mkdirs();

        final SteamConfiguration config = SteamConfiguration.create(b -> {
            b.withServerListProvider(new FileServerListProvider(new File(getFilesDir(), "servers.bin")));
        });

        steamClient = new SteamClient(config);
        steamClient.addHandler(new PurchaseResponse());
        steamUser = steamClient.getHandler(SteamUser.class);
        steamFriends = steamClient.getHandler(SteamFriends.class);
        steamApps = steamClient.getHandler(SteamApps.class);

        // Subscribe to callbacks
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

        // Detect Huawei devices running Lollipop which have a bug with MediaStyle notifications
        isHuawei = (android.os.Build.VERSION.SDK_INT == Build.VERSION_CODES.LOLLIPOP_MR1 ||
                android.os.Build.VERSION.SDK_INT == Build.VERSION_CODES.LOLLIPOP) &&
                Build.MANUFACTURER.toLowerCase(Locale.getDefault()).contains("huawei");
        if (PrefsManager.stayAwake()) {
            acquireWakeLock();
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Create notification channel
            createChannel();
        }
        if (BuildConfig.DEBUG) {
            LogManager.addListener(new AndroidLogListener());
        }
        startForeground(NOTIF_ID, buildNotification(getString(R.string.service_started)));
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LocaleManager.setLocale(base));
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
        new Thread(() -> {
            steamUser.logOff();
            steamClient.disconnect();
        }).start();
        stopForeground(true);
        running = false;
        stopFarming();
        executor.shutdownNow();
        scheduler.shutdownNow();
        releaseWakeLock();
        unregisterReceiver(receiver);
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

    public boolean isLoggedIn() {
        return loggedIn;
    }

    public boolean isFarming() {
        return farming;
    }

    public boolean isPaused() {
        return paused;
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

    public long getSteamId() {
        return steamId;
    }

    public void changeStatus(EPersonaState status) {
        if (isLoggedIn()) {
            executor.execute(() -> steamFriends.setPersonaState(status));
        }
    }

    /**
     * Acquire WakeLock to keep the CPU from sleeping
     */
    public void acquireWakeLock() {
        if (wakeLock == null) {
            Log.i(TAG, "Acquiring WakeLock");
            final PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG);
            wakeLock.acquire();
        }
    }

    /**
     * Release the WakeLock
     */
    public void releaseWakeLock() {
        if (wakeLock != null) {
            Log.i(TAG, "Releasing WakeLock");
            wakeLock.release();
            wakeLock = null;
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
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.now_playing2,
                        (game.appId == 0) ? getString(R.string.playing_non_steam_game, game.name) : game.name))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setContentIntent(pendingIntent);

        // MediaStyle causes a crash on certain Huawei devices running Lollipop
        // https://stackoverflow.com/questions/34851943/couldnt-expand-remoteviews-mediasessioncompat-and-notificationcompat-mediastyl
        if (!isHuawei) {
            builder.setStyle(new MediaStyle());
        }

        if (game.dropsRemaining > 0) {
            // Show drops remaining
            builder.setSubText(getResources().getQuantityString(R.plurals.card_drops_remaining, game.dropsRemaining, game.dropsRemaining));
        }

        // Add the stop and pause actions
        final PendingIntent stopIntent = PendingIntent.getBroadcast(this, 0, new Intent(STOP_INTENT), PendingIntent.FLAG_CANCEL_CURRENT);
        final PendingIntent pauseIntent = PendingIntent.getBroadcast(this, 0, new Intent(PAUSE_INTENT), PendingIntent.FLAG_CANCEL_CURRENT);
        builder.addAction(R.drawable.ic_action_stop, getString(R.string.stop), stopIntent);
        builder.addAction(R.drawable.ic_action_pause, getString(R.string.pause), pauseIntent);

        if (farming) {
            // Add the skip action
            final PendingIntent skipIntent = PendingIntent.getBroadcast(this, 0, new Intent(SKIP_INTENT), PendingIntent.FLAG_CANCEL_CURRENT);
            builder.addAction(R.drawable.ic_action_skip, getString(R.string.skip), skipIntent);
        }

        final NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (!PrefsManager.minimizeData()) {
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
                .addAction(R.drawable.ic_action_stop, getString(R.string.stop), stopIntent)
                .addAction(R.drawable.ic_action_pause, getString(R.string.pause), pauseIntent)
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
                .addAction(R.drawable.ic_action_play, getString(R.string.resume), resumeIntent)
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
        paused = false;
        currentGames.clear();
        currentGames.add(game);
        playGames(game);
        showIdleNotification(game);
    }

    private void idleMultiple(List<Game> games) {
        Log.i(TAG, "Idling multiple");
        paused = false;
        final List<Game> gamesCopy = new ArrayList<>(games);
        currentGames.clear();

        int size = gamesCopy.size();
        if (size > 32) {
            size = 32;
        }

        final StringBuilder msg = new StringBuilder();
        for (int i=0;i<size;i++) {
            final Game game = gamesCopy.get(i);
            currentGames.add(game);
            if (game.appId == 0) {
                // Non-Steam game
                msg.append(getString(R.string.playing_non_steam_game, game.name));
            } else {
                msg.append(game.name);
            }
            if (i + 1 < size) {
                msg.append("\n");
            }
        }

        playGames(currentGames.toArray(new Game[0]));
        showMultipleNotification(msg.toString());
    }

    public void addGame(Game game) {
        stopFarming();
        if (currentGames.isEmpty()) {
            idleSingle(game);
        } else {
            currentGames.add(game);
            idleMultiple(currentGames);
        }
    }

    public void addGames(List<Game> games) {
        stopFarming();
        if (games.size() == 1) {
            idleSingle(games.get(0));
        } else if (games.size() > 1){
            idleMultiple(games);
        } else {
            stopGame();
        }
    }

    public void removeGame(Game game) {
        stopFarming();
        currentGames.remove(game);
        if (currentGames.size() == 1) {
            idleSingle(currentGames.get(0));
        } else if (currentGames.size() > 1) {
            idleMultiple(currentGames);
        } else {
            stopGame();
        }
    }

    public void start() {
        running = true;
        if (!PrefsManager.getLoginKey().isEmpty()) {
            // We can log in using saved credentials
            executor.execute(() -> steamClient.connect());
        }
        // Run the the callback handler
        executor.execute(() -> {
            while (running) {
                try {
                    manager.runWaitCallbacks(1000L);
                } catch (Exception e) {
                    Log.i(TAG, "update() failed", e);
                }
            }
        });
    }

    public void login(final LogOnDetails details) {
        Log.i(TAG, "logging in");
        loginInProgress = true;
        logOnDetails = details;
        executor.execute(() -> steamClient.connect());
    }

    public void logoff() {
        Log.i(TAG, "logging off");
        loginInProgress = true;
        loggedIn = false;
        steamId = 0;
        logOnDetails = null;
        currentGames.clear();
        stopFarming();
        executor.execute(() -> {
            steamUser.logOff();
            steamClient.disconnect();
        });
        PrefsManager.clearUser();
        updateNotification(getString(R.string.logged_out));
    }

    /**
     * Redeem Steam key or activate free license
     */
    public void redeemKey(final String key) {
        if (key.matches("\\d+")) {
            // Request a free license
            try {
                final int freeLicense = Integer.parseInt(key);
                pendingFreeLicenses.add(freeLicense);
                executor.execute(() -> steamApps.requestFreeLicense(freeLicense));
            } catch (NumberFormatException e) {
                showToast(getString(R.string.invalid_key));
            }
        } else {
            // Register product key
            final ClientMsgProtobuf<SteammessagesClientserver2.CMsgClientRegisterKey.Builder> registerKey;
            registerKey = new ClientMsgProtobuf<>(SteammessagesClientserver2.CMsgClientRegisterKey.class, EMsg.ClientRegisterKey);
            registerKey.getBody().setKey(key);
            executor.execute(() -> steamClient.send(registerKey));
        }
    }

    public void autoVote() {
        executor.execute(() -> {
            final int msgId = webHandler.autoVote() ? R.string.vote_successful : R.string.vote_failed;
            showToast(getString(msgId));
        });
    }

    /**
     * Perform log in. Needs to happen as soon as we connect or else we'll get an error
     */
    private void doLogin() {
        steamUser.logOn(logOnDetails);
        logOnDetails = null; // No longer need this
    }

    /**
     * Log in using saved credentials
     */
    private void attemptRestoreLogin() {
        final String username = PrefsManager.getUsername();
        final String loginKey = PrefsManager.getLoginKey();
        if (username.isEmpty() || loginKey.isEmpty()) {
            return;
        }
        Log.i(TAG, "Restoring login");
        final LogOnDetails details = new LogOnDetails();
        details.setUsername(username);
        details.setLoginKey(loginKey);
        details.setClientOSType(EOSType.LinuxUnknown);
        try {
            final File sentryFile = new File(sentryFolder, username + ".sentry");
            details.setSentryFileHash(Utils.calculateSHA1(sentryFile));
        } catch (IOException | NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        details.setShouldRememberPassword(true);
        steamUser.logOn(details);
    }

    private boolean attemptAuthentication(String nonce) {
        Log.i(TAG, "Attempting SteamWeb authentication");
        for (int i=0;i<3;i++) {
            if (webHandler.authenticate(steamClient, nonce)) {
                Log.i(TAG, "Authenticated!");
                return true;
            }

            if (i + 1 < 3) {
                Log.i(TAG, "Retrying...");
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    return false;
                }
            }
        }
        return false;
    }

    private void registerApiKey() {
        Log.i(TAG, "Registering API key");
        final int result = webHandler.updateApiKey();
        Log.i(TAG, "API key result: " + result);
        switch (result) {
            case SteamWebHandler.ApiKeyState.REGISTERED:
                break;
            case SteamWebHandler.ApiKeyState.ACCESS_DENIED:
                showToast(getString(R.string.apikey_access_denied));
                break;
            case SteamWebHandler.ApiKeyState.UNREGISTERED:
                // Call updateApiKey once more to actually update it
                webHandler.updateApiKey();
                break;
            case SteamWebHandler.ApiKeyState.ERROR:
                showToast(getString(R.string.apikey_register_failed));
                break;
        }
    }

    private void showToast(final String message) {
        new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show());
    }

    private void onConnected(ConnectedCallback callback) {
        Log.i(TAG, "Connected()");
        connected = true;
        if (logOnDetails != null) {
            doLogin();
        } else {
            attemptRestoreLogin();
        }
    }

    private void onDisconnected(DisconnectedCallback callback) {
        Log.i(TAG, "Disconnected()");
        connected = false;
        loggedIn = false;

        if (!loginInProgress) {
            // Try to reconnect after a 5 second delay
            scheduler.schedule(() -> {
                Log.i(TAG, "Reconnecting");
                steamClient.connect();
            }, 5, TimeUnit.SECONDS);
        } else {
            // SteamKit may disconnect us while logging on (if already connected),
            // but since it reconnects immediately after we do not have to reconnect here.
            Log.i(TAG, "NOT reconnecting (logon in progress)");
        }

        // Tell the activity that we've been disconnected from Steam
        LocalBroadcastManager.getInstance(SteamService.this).sendBroadcast(new Intent(DISCONNECT_EVENT));
    }

    private void onLoggedOff(LoggedOffCallback callback) {
        Log.i(TAG, "Logoff result " + callback.getResult().toString());
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

    private void onLoggedOn(LoggedOnCallback callback) {
        final EResult result = callback.getResult();

        if (result == EResult.OK) {
            // Successful login
            Log.i(TAG, "Logged on!");
            loginInProgress = false;
            loggedIn = true;
            steamId = steamClient.getSteamID().convertToUInt64();
            if (paused) {
                showPausedNotification();
            } else if (waiting) {
                updateNotification(getString(R.string.logged_in_elsewhere));
            } else {
                updateNotification(getString(R.string.logged_in));
            }
            executor.execute(() -> {
                final boolean gotAuth = attemptAuthentication(callback.getWebAPIUserNonce());

                if (gotAuth) {
                    resumeFarming();
                    registerApiKey();
                } else {
                    // Request a new WebAPI user authentication nonce
                    steamUser.requestWebAPIUserNonce();
                }
            });
        } else if (result == EResult.InvalidPassword && !PrefsManager.getLoginKey().isEmpty()) {
            // Probably no longer valid
            Log.i(TAG, "Login key expired");
            PrefsManager.writeLoginKey("");
            updateNotification(getString(R.string.login_key_expired));
            steamClient.disconnect();
        } else {
            Log.i(TAG, "LogOn result: " + result.toString());
            steamClient.disconnect();
        }

        // Tell LoginActivity the result
        final Intent intent = new Intent(LOGIN_EVENT);
        intent.putExtra(RESULT, result);
        LocalBroadcastManager.getInstance(SteamService.this).sendBroadcast(intent);
    }

    private void onLoginKey(LoginKeyCallback callback) {
        Log.i(TAG, "Saving loginkey");
        PrefsManager.writeLoginKey(callback.getLoginKey());
        steamUser.acceptNewLoginKey(callback);
    }

    private void onUpdateMachineAuth(UpdateMachineAuthCallback callback) {
        final File sentryFile = new File(sentryFolder, PrefsManager.getUsername() + ".sentry");
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

            steamUser.sendMachineAuthResponse(auth);

            PrefsManager.writeSentryHash(Utils.bytesToHex(sha1));
        } catch (IOException | NoSuchAlgorithmException e) {
            Log.i(TAG, "Error saving sentry file", e);
        }
    }

    private void onPurchaseResponse(PurchaseResponseCallback callback) {
        if (callback.getResult() == EResult.OK) {
            final KeyValue kv = callback.getPurchaseReceiptInfo();
            final EPaymentMethod paymentMethod = EPaymentMethod.from(kv.get("PaymentMethod").asInteger());
            if (paymentMethod == EPaymentMethod.ActivationCode) {
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
                showToast(getString(R.string.activated, products.toString()));
            }
        } else {
            final EPurchaseResultDetail purchaseResult = callback.getPurchaseResultDetails();
            final int errorId;
            if (purchaseResult == EPurchaseResultDetail.AlreadyPurchased) {
                errorId = R.string.product_already_owned;
            } else if (purchaseResult == EPurchaseResultDetail.BadActivationCode) {
                errorId = R.string.invalid_key;
            } else {
                errorId = R.string.activation_failed;
            }
            showToast(getString(errorId));
        }
    }

    private void onPersonaStates(PersonaStatesCallback callback) {
        for (PersonaState ps : callback.getPersonaStates()) {
            if (ps.getFriendID().equals(steamClient.getSteamID())) {
                final String personaName = ps.getName();
                final String avatarHash = Utils.bytesToHex(ps.getAvatarHash()).toLowerCase();
                Log.i(TAG, "Avatar hash " + avatarHash);
                final Intent event = new Intent(PERSONA_EVENT);
                event.putExtra(PERSONA_NAME, personaName);
                event.putExtra(AVATAR_HASH, avatarHash);
                LocalBroadcastManager.getInstance(SteamService.this).sendBroadcast(event);
                break;
            }
        }
    }

    private void onFreeLicense(FreeLicenseCallback callback) {
        final int freeLicense = pendingFreeLicenses.removeFirst();
        if (!callback.getGrantedApps().isEmpty()) {
            showToast(getString(R.string.activated, String.valueOf(callback.getGrantedApps().get(0))));
        } else if (!callback.getGrantedPackages().isEmpty()) {
            showToast(getString(R.string.activated, String.valueOf(callback.getGrantedPackages().get(0))));
        } else {
            // Try activating it with the web handler
            executor.execute(() -> {
                final String msg;
                if (webHandler.addFreeLicense(freeLicense)) {
                    msg = getString(R.string.activated, String.valueOf(freeLicense));
                } else {
                    msg = getString(R.string.activation_failed);
                }
                showToast(msg);
            });
        }
    }

    private void onAccountInfo(AccountInfoCallback callback) {
        if (!PrefsManager.getOffline()) {
            steamFriends.setPersonaState(EPersonaState.Online);
        }
    }

    private void onWebAPIUserNonce(WebAPIUserNonceCallback callback) {
        Log.i(TAG, "Got new WebAPI user authentication nonce");
        executor.execute(() -> {
            final boolean gotAuth = attemptAuthentication(callback.getNonce());

            if (gotAuth) {
                resumeFarming();
            } else {
                updateNotification(getString(R.string.web_login_failed));
            }
        });
    }

    private void onItemAnnouncements(ItemAnnouncementsCallback callback) {
        Log.i(TAG, "New item notification " + callback.getCount());
        if (callback.getCount() > 0 && farming) {
            // Possible card drop
            executor.execute(farmTask);
        }
    }

    /**
     * Idle one or more games
     * @param games the games to idle
     */
    private void playGames(Game...games) {
        final ClientMsgProtobuf<SteammessagesClientserver.CMsgClientGamesPlayed.Builder> gamesPlayed;
        gamesPlayed = new ClientMsgProtobuf<>(SteammessagesClientserver.CMsgClientGamesPlayed.class, EMsg.ClientGamesPlayed);
        for (Game game : games) {
            if (game.appId == 0) {
                // Non-Steam game
                final GameID gameId = new GameID(game.appId);
                gameId.setAppType(GameID.GameType.SHORTCUT);
                final CRC32 crc = new CRC32();
                crc.update(game.name.getBytes());
                // set the high-bit on the mod-id
                // reduces crc32 to 31bits, but lets us use the modID as a guaranteed unique
                // replacement for appID
                gameId.setModID(crc.getValue() | (0x80000000));
                gamesPlayed.getBody().addGamesPlayedBuilder()
                        .setGameId(gameId.convertToUInt64())
                        .setGameExtraInfo(game.name);
            } else {
                gamesPlayed.getBody().addGamesPlayedBuilder()
                        .setGameId(game.appId);
            }
        }
        executor.execute(() -> {
            steamClient.send(gamesPlayed);
        });
        // Tell the activity
        LocalBroadcastManager.getInstance(SteamService.this).sendBroadcast(new Intent(NOW_PLAYING_EVENT));
    }

    private void stopPlaying() {
        if (!paused) {
            currentGames.clear();
        }
        final ClientMsgProtobuf<SteammessagesClientserver.CMsgClientGamesPlayed.Builder> stopGame;
        stopGame = new ClientMsgProtobuf<>(SteammessagesClientserver.CMsgClientGamesPlayed.class, EMsg.ClientGamesPlayed);
        stopGame.getBody().addGamesPlayedBuilder().setGameId(0);
        executor.execute(() -> steamClient.send(stopGame));
        // Tell the activity
        LocalBroadcastManager.getInstance(SteamService.this).sendBroadcast(new Intent(NOW_PLAYING_EVENT));
    }
}
