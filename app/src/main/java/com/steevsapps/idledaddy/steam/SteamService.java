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
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.annotation.StringRes;
import android.support.v4.app.NotificationCompat;
import android.support.v4.media.app.NotificationCompat.MediaStyle;
import android.util.Log;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.animation.GlideAnimation;
import com.bumptech.glide.request.target.SimpleTarget;
import com.steevsapps.idledaddy.AppExecutors;
import com.steevsapps.idledaddy.BuildConfig;
import com.steevsapps.idledaddy.EventBroadcaster;
import com.steevsapps.idledaddy.IdleDaddy;
import com.steevsapps.idledaddy.MainActivity;
import com.steevsapps.idledaddy.R;
import com.steevsapps.idledaddy.UserRepository;
import com.steevsapps.idledaddy.listeners.AndroidLogListener;
import com.steevsapps.idledaddy.preferences.Prefs;
import com.steevsapps.idledaddy.steam.model.Game;
import com.steevsapps.idledaddy.utils.LocaleManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import in.dragonbra.javasteam.enums.EPersonaState;
import in.dragonbra.javasteam.steam.handlers.steamuser.LogOnDetails;
import in.dragonbra.javasteam.util.log.LogManager;

public class SteamService extends Service implements NotificationListener {
    private final static String TAG = SteamService.class.getSimpleName();
    private final static int NOTIF_ID = 6896; // Ongoing notification ID
    private final static String CHANNEL_ID = "idle_channel"; // Notification channel
    // Some Huawei phones reportedly kill apps when they hold a WakeLock for a long time.
    // This can be prevented by using a WakeLock tag from the PowerGenie whitelist.
    private final static String WAKELOCK_TAG = "LocationManagerService";

    private final ConcurrentMap<String, SteamBot> botMap = new ConcurrentHashMap<>();
    private SteamBot activeBot;

    public final static String ACTION_SKIP = "com.steevsapps.idledaddy.ACTION_SKIP";
    public final static String ACTION_STOP = "com.steevsapps.idledaddy.ACTION_STOP";
    public final static String ACTION_PAUSE = "com.steevsapps.idledaddy.ACTION_PAUSE";
    public final static String ACTION_RESUME = "com.steeevsapps.idledaddy.ACTION_RESUME";
    private final static String EXTRA_SENDER = "com.steevsapps.idledaddy.EXTRA_SENDER";

    private PowerManager.WakeLock wakeLock;

    // Huawei devices with Lollipop have a problem with the 'MediaStyle' notification
    // https://stackoverflow.com/questions/34851943/couldnt-expand-remoteviews-mediasessioncompat-and-notificationcompat-mediastyl
    private boolean isHuawei5 = (android.os.Build.VERSION.SDK_INT == Build.VERSION_CODES.LOLLIPOP_MR1 ||
            android.os.Build.VERSION.SDK_INT == Build.VERSION_CODES.LOLLIPOP) &&
            Build.MANUFACTURER.toLowerCase(Locale.US).contains("huawei");

    private UserRepository userRepo;
    private AppExecutors executors;

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
            if (intent.getAction() != null) {
                final String sender = intent.getStringExtra(EXTRA_SENDER);
                switch (intent.getAction()) {
                    case ACTION_SKIP:
                        skipGame(sender);
                        break;
                    case ACTION_STOP:
                        stopGame(sender);
                        break;
                    case ACTION_PAUSE:
                        pauseGame(sender);
                        break;
                    case ACTION_RESUME:
                        resumeGame(sender);
                        break;
                }
            }
        }
    };

    public static Intent createIntent(Context c) {
        return new Intent(c, SteamService.class);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }


    @Override
    public void onCreate() {
        Log.i(TAG, "Creating service...");
        super.onCreate();

        final IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_SKIP);
        filter.addAction(ACTION_STOP);
        filter.addAction(ACTION_PAUSE);
        filter.addAction(ACTION_RESUME);
        registerReceiver(receiver, filter);

        userRepo = ((IdleDaddy) getApplication()).getUserRepository();
        executors = ((IdleDaddy) getApplication()).getExecutors();

        if (Prefs.stayAwake()) {
            acquireWakeLock();
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Create notification channel
            createChannel();
        }

        if (BuildConfig.DEBUG) {
            LogManager.addListener(new AndroidLogListener());
        }

        final PendingIntent pi = PendingIntent.getActivity(this, getRequestCode(),
                new Intent(this, MainActivity.class), 0);
        final Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.service_started))
                .setContentIntent(pi)
                .setOngoing(true)
                .build();

        startForeground(NOTIF_ID, notification);
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LocaleManager.setLocale(base));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "Starting command...");
        return Service.START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "Destroying service...");
        stopForeground(true);
        clearNotificaitons();

        new Thread(() -> {
            for (SteamBot bot : botMap.values()) {
                Log.i(TAG, "Stopping " + bot.getUsername() + "...");
                bot.stop();
            }
        }).start();

        executors.shutdownNow();
        releaseWakeLock();
        unregisterReceiver(receiver);

        super.onDestroy();
    }

    public boolean isLoggedOn() {
        return activeBot != null && activeBot.isLoggedOn();
    }

    public boolean isFarming() {
        return activeBot != null && activeBot.isFarming();
    }

    public boolean isPaused() {
        return activeBot != null && activeBot.isPaused();
    }

    @Nullable
    public String getUsername() {
        return activeBot != null ? activeBot.getUsername() : null;
    }

    public long getSteamId() {
        return activeBot != null ? activeBot.getSteamId() : 0;
    }

    public ArrayList<Game> getGamesIdling() {
        return activeBot != null ? activeBot.getGamesIdling() : new ArrayList<>();
    }

    public ArrayList<Game> getLastSession() {
        return activeBot != null ? activeBot.getLastSession() : new ArrayList<>();
    }

    public int getGameCount() {
        return activeBot != null ? activeBot.getGameCount() : 0;
    }

    public int getCardCount() {
        return activeBot != null ? activeBot.getCardCount() : 0;
    }

    public void startFarming() {
        if (activeBot == null) {
            Log.w(TAG, "Called startFarming() without an active bot!");
            return;
        }
        activeBot.startFarming();
    }

    public void playGames(List<Game> games) {
        if (activeBot == null) {
            Log.w(TAG, "Called playGames() without an active bot!");
            return;
        }
        activeBot.playGames(games.toArray(new Game[0]));
    }

    public void skipGame() {
        if (activeBot == null) {
            Log.w(TAG, "Called skipGame() without an active bot!");
            return;
        }
        activeBot.skipGame();
    }

    public void stopGame() {
        if (activeBot == null) {
            Log.w(TAG, "Called stopGame() without an active bot!");
            return;
        }
        activeBot.stopGame();
    }

    public void pauseGame() {
        if (activeBot == null) {
            Log.w(TAG, "Called pauseGame() without an active bot!");
            return;
        }
        activeBot.pauseGame();
    }

    public void resumeGame() {
        activeBot.resumeGame();
    }

    public void changeStatus(EPersonaState status) {
        if (activeBot == null) {
            Log.w(TAG, "Called changeStatus() without an active bot!");
            return;
        }
        activeBot.changeStatus(status);
    }

    public void redeemKey(String key) {
        if (activeBot == null) {
            Log.w(TAG, "Called redeemKey without an active bot!");
            return;
        }
        Log.w(TAG, "Not implemented yet");
    }

    public void setActiveBot(String username) {
        activeBot = getBot(username);
        activeBot.loadUser();
    }

    public void login(LogOnDetails logOnDetails) {
        activeBot = getBot(logOnDetails.getUsername());
        activeBot.login(logOnDetails);
    }

    public void logoff() {
        if (activeBot == null) {
            Log.w(TAG, "Called logoff() without an active bot!");
            return;
        }
        executors.networkIO().execute(() -> {
            activeBot.stop();
            botMap.remove(activeBot.getUsername());
            activeBot.delete();
            activeBot = null;
        });
    }

    private SteamBot getBot(String username) {
        SteamBot bot = botMap.get(username);
        if (bot == null) {
            final SteamBot b = new SteamBot(username, getApplication());
            bot = botMap.putIfAbsent(username, b);

            if (bot == null) {
                bot = b;
                bot.setListener(this);
                bot.start();
            }
        }
        return bot;
    }

    private void skipGame(String sender) {
        if (!botMap.containsKey(sender)) {
            Log.w(TAG, "Called skipGame() with invalid sender!");
            return;
        }
        botMap.get(sender).skipGame();
    }

    private void stopGame(String sender) {
        if (!botMap.containsKey(sender)) {
            Log.w(TAG, "Called stopGame() with invalid sender!");
            return;
        }
        botMap.get(sender).stopGame();
    }

    private void pauseGame(String sender) {
        if (!botMap.containsKey(sender)) {
            Log.w(TAG, "Called pauseGame() with invalid sender!");
            return;
        }
        botMap.get(sender).pauseGame();
    }

    private void resumeGame(String sender) {
        if (!botMap.containsKey(sender)) {
            Log.w(TAG, "Called resumeGame() with invalid sender!");
            return;
        }
        botMap.get(sender).resumeGame();
    }

    /**
     * Create notification channel for Android O
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private void createChannel() {
        final String name = getString(R.string.channel_name);
        final NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_LOW);
        channel.setShowBadge(false);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        channel.enableVibration(false);
        channel.enableLights(false);
        channel.setBypassDnd(false);
        final NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(channel);
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

    private int getRequestCode() {
        return (int) System.currentTimeMillis();
    }

    private Intent getAction(String action, String sender) {
        final Intent intent = new Intent(action);
        intent.putExtra(EXTRA_SENDER, sender);
        return intent;
    }

    private Notification buildTextNotification(String tag, String text) {
        final PendingIntent pi = PendingIntent.getActivity(this, getRequestCode(),
                new Intent(this, MainActivity.class), 0);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(getString(R.string.notification_title, tag))
                .setContentText(text)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    private void clearNotificaitons() {
        final NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.cancelAll();
    }

    private void buildIdleNotification(String tag, Game game, boolean farming) {
        final PendingIntent pi = PendingIntent.getActivity(this, getRequestCode(),
                new Intent(this, MainActivity.class), 0);

        final NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(getString(R.string.notification_title, tag))
                .setContentText(getString(R.string.now_playing2,
                        (game.appId == 0) ? getString(R.string.playing_non_steam_game, game.name) : game.name))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setContentIntent(pi)
                .setOngoing(true);

        if (!isHuawei5) {
            builder.setStyle(new MediaStyle());
        }

        if (game.dropsRemaining > 0) {
            // Show drops remaining
            builder.setSubText(getResources().getQuantityString(R.plurals.card_drops_remaining, game.dropsRemaining, game.dropsRemaining));
        }

        // Add the stop and pause actions
        final PendingIntent piStop = PendingIntent.getBroadcast(this, getRequestCode(), getAction(ACTION_STOP, tag), PendingIntent.FLAG_CANCEL_CURRENT);
        final PendingIntent piPause = PendingIntent.getBroadcast(this, getRequestCode(), getAction(ACTION_PAUSE, tag), PendingIntent.FLAG_CANCEL_CURRENT);
        builder.addAction(R.drawable.ic_action_stop, getString(R.string.stop), piStop);
        builder.addAction(R.drawable.ic_action_pause, getString(R.string.pause), piPause);

        if (farming) {
            // Add the skip action
            final PendingIntent piSkip = PendingIntent.getBroadcast(this, getRequestCode(), getAction(ACTION_SKIP, tag), PendingIntent.FLAG_CANCEL_CURRENT);
            builder.addAction(R.drawable.ic_action_skip, getString(R.string.skip), piSkip);
        }

        final NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (!Prefs.minimizeData()) {
            // Load game icon into notification
            executors.mainThread().execute(() -> {
                Glide.with(getApplicationContext())
                        .load(game.iconUrl)
                        .asBitmap()
                        .into(new SimpleTarget<Bitmap>() {
                            @Override
                            public void onResourceReady(Bitmap resource, GlideAnimation<? super Bitmap> glideAnimation) {
                                builder.setLargeIcon(resource);
                                nm.notify(tag, NOTIF_ID, builder.build());
                            }

                            @Override
                            public void onLoadFailed(Exception e, Drawable errorDrawable) {
                                super.onLoadFailed(e, errorDrawable);
                                nm.notify(tag, NOTIF_ID, builder.build());
                            }
                        });
            });
        } else {
            nm.notify(tag, NOTIF_ID, builder.build());
        }
    }

    /**
     * Show a "Big Text" style notification with the games we're idling
     */
    private void buildMultipleNotification(String tag, List<Game> games) {
        final StringBuilder sb = new StringBuilder();
        for (Game game : games) {
            if (game.appId == 0) {
                // Non-Steam game
                sb.append(getString(R.string.playing_non_steam_game, game.name));
            } else {
                sb.append(game.name);
            }
            sb.append("\n");
        }

        final PendingIntent pi = PendingIntent.getActivity(this, getRequestCode(),
                new Intent(this, MainActivity.class), 0);

        // Add stop and pause actions
        final PendingIntent piStop = PendingIntent.getBroadcast(this, getRequestCode(), getAction(ACTION_STOP, tag), PendingIntent.FLAG_CANCEL_CURRENT);
        final PendingIntent piPause = PendingIntent.getBroadcast(this, getRequestCode(), getAction(ACTION_PAUSE, tag), PendingIntent.FLAG_CANCEL_CURRENT);

        final Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(sb.toString()))
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(getString(R.string.notification_title, tag))
                .setContentText(getString(R.string.idling_multiple))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setContentIntent(pi)
                .addAction(R.drawable.ic_action_stop, getString(R.string.stop), piStop)
                .addAction(R.drawable.ic_action_pause, getString(R.string.pause), piPause)
                .setOngoing(true)
                .build();

        final NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.notify(tag, NOTIF_ID, notification);
    }

    @Override
    public void showTextNotification(String tag, @StringRes int resId) {
        final NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.notify(tag, NOTIF_ID, buildTextNotification(tag, getString(resId)));
    }

    @Override
    public void showIdleNotification(String tag, List<Game> games, boolean farming) {
        if (games.size() == 1) {
            // Show single style notification
            buildIdleNotification(tag, games.get(0), farming);
        } else {
            // Show idling multiple notification
            buildMultipleNotification(tag, games);
        }
    }

    @Override
    public void showPausedNotification(String tag) {
        final PendingIntent pi = PendingIntent.getActivity(this, getRequestCode(), new Intent(this, MainActivity.class), 0);
        final PendingIntent piResume = PendingIntent.getBroadcast(this, getRequestCode(), getAction(ACTION_RESUME, tag), PendingIntent.FLAG_CANCEL_CURRENT);
        final Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(getString(R.string.notification_title, tag))
                .setContentText(getString(R.string.paused))
                .setContentIntent(pi)
                .addAction(R.drawable.ic_action_play, getString(R.string.resume), piResume)
                .setOngoing(true)
                .build();
        final NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.notify(tag, NOTIF_ID, notification);
    }

    @Override
    public void showToast(@StringRes int resId) {
        executors.mainThread().execute(() -> Toast.makeText(getApplicationContext(), resId, Toast.LENGTH_LONG).show());
    }

    @Override
    public void showToast(@StringRes int resId, String formatArgs) {
        executors.mainThread().execute(() -> Toast.makeText(getApplicationContext(), getString(resId, formatArgs), Toast.LENGTH_LONG).show());
    }

    @Override
    public void sendEvent(String tag, String event) {
        if (activeBot == null) {
            Log.w(TAG, "Called sendEvent without an active bot!");
            return;
        }
        if (tag.equals(activeBot.getUsername())) {
            EventBroadcaster.send(this, event);
        }
    }

    @Override
    public void sendEvent(String tag, String event, Bundle args) {
        if (activeBot == null) {
            Log.w(TAG, "Called sendEvent without an active bot!");
            return;
        }
        if (tag.equals(activeBot.getUsername())) {
            EventBroadcaster.send(this, event, args);
        }
    }

    /**
     * Redeem Steam key or activate free license
     */
    /*public void redeemKey(String key) {
        if (!loggedIn && currentUser.canLogOn()) {
            Log.i(TAG, "Will redeem key at login");
            keyToRedeem = key;
            return;
        }
        Log.i(TAG, "Redeeming key...");
        if (key.matches("\\d+")) {
            // Request a free license
            try {
                int freeLicense = Integer.parseInt(key);
                addFreeLicense(freeLicense);
            } catch (NumberFormatException e) {
                showToast(getString(R.string.invalid_key));
            }
        } else {
            // Register product key
            registerProductKey(key);
        }
    }*/
}
