package com.steevsapps.updogfarmer.steam;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.RemoteViews;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.NotificationTarget;
import com.steevsapps.updogfarmer.LoginActivity;
import com.steevsapps.updogfarmer.MainActivity;
import com.steevsapps.updogfarmer.R;
import com.steevsapps.updogfarmer.utils.Prefs;
import com.steevsapps.updogfarmer.utils.Utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
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
import uk.co.thomasc.steamkit.base.generated.steamlanguage.EPersonaState;
import uk.co.thomasc.steamkit.base.generated.steamlanguage.EResult;
import uk.co.thomasc.steamkit.steam3.handlers.steamfriends.SteamFriends;
import uk.co.thomasc.steamkit.steam3.handlers.steamuser.SteamUser;
import uk.co.thomasc.steamkit.steam3.handlers.steamuser.callbacks.LoggedOnCallback;
import uk.co.thomasc.steamkit.steam3.handlers.steamuser.callbacks.LoginKeyCallback;
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
import uk.co.thomasc.steamkit.util.KeyDictionary;
import uk.co.thomasc.steamkit.util.WebHelpers;
import uk.co.thomasc.steamkit.util.cSharp.events.ActionT;
import uk.co.thomasc.steamkit.util.crypto.CryptoHelper;
import uk.co.thomasc.steamkit.util.crypto.RSACrypto;

public class SteamService extends Service {
    private final static String TAG = "ywtag";
    private final static int NOTIF_ID = 6896; // Ongoing notification ID

    // Used to tell activities when login state has changed
    public final static String LOGIN_INTENT = "LOGIN_INTENT";
    public final static String RESULT = "RESULT";

    private SteamClient steamClient;
    private SteamUser steamUser;
    private SteamFriends steamFriends;

    private volatile boolean running;
    private volatile boolean connected;

    private String sessionId;
    private String token;
    private String tokenSecure;
    private String sentryHash;
    private boolean authenticated;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private ScheduledFuture<?> farmHandle;
    private int currentAppId  = 0;

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

    private void startFarming() {
        final Runnable runnable = new Runnable() {
            @Override
            public void run() {
                farm();
            }
        };
        farmHandle = scheduler.scheduleAtFixedRate(runnable, 0, 10 * 60, TimeUnit.SECONDS);
    }

    private void farm() {
        final List<WebScraper.Badge> badges = WebScraper.getRemainingGames(generateWebCookies());
        if (badges.isEmpty()) {
            Log.i(TAG, "Finished idling");
            stopPlaying();
            updateNotification(getString(R.string.idling_finished));
            farmHandle.cancel(true);
            return;
        }

        final WebScraper.Badge b = badges.get(0);
        if (b.appId != currentAppId) {
            Log.i(TAG, "Now idling " + b.name);
            playGame(b.appId);

            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    buildIdleNotification(b);
                }
            });
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
        startForeground(NOTIF_ID, buildNotification("Steam service started"));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!running) {
            Log.i(TAG, "Command starting");
            start();
        }
        return Service.START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "Service destroyed");
        super.onDestroy();
        if (farmHandle != null) {
            farmHandle.cancel(true);
        }
        stopForeground(true);
        running = false;
    }

    /**
     * Check if user is logged in
     */
    public boolean isLoggedIn() {
        return steamClient.getSteamId() != null;
    }

    private Notification buildNotification(String text) {
        final Intent notificationIntent = new Intent(this, LoginActivity.class);
        final PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                notificationIntent, 0);
        return new NotificationCompat.Builder(this)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(text)
                .setContentIntent(pendingIntent)
                .build();
    }

    /**
     * Build custom idling notification
     */
    private void buildIdleNotification(WebScraper.Badge badge) {
        Log.i(TAG, "Idle notification");
        final RemoteViews rv = new RemoteViews(getPackageName(), R.layout.idle_notification);
        rv.setImageViewResource(R.id.remoteview_notification_icon, R.mipmap.ic_launcher);
        rv.setTextViewText(R.id.remoteview_notification_headline, getString(R.string.app_name));
        rv.setTextViewText(R.id.remoteview_notification_short_message, "Now playing " + badge.name);

        final Intent notificationIntent = new Intent(this, MainActivity.class);
        final PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                notificationIntent, 0);

        // build notification
        final Notification notification =
                new NotificationCompat.Builder(this)
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setContent(rv)
                        .setCustomBigContentView(rv)
                        .setContentIntent(pendingIntent)
                        .build();

        final NotificationTarget target = new NotificationTarget(
                this,
                rv,
                R.id.remoteview_notification_icon,
                notification,
                NOTIF_ID);

        // Load game icon into notication
        Glide.with(getApplicationContext())
                .load(badge.iconUrl)
                .asBitmap()
                .into(target);

        final NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(NOTIF_ID, notification);
    }

    /**
     * Used to update the notification
     * @param text the text to display
     */
    private void updateNotification(String text) {
        final NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(NOTIF_ID, buildNotification(text));
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
        new Thread(new Runnable() {
            @Override
            public void run() {
                steamUser.logOff();
                Prefs.writeLoginKey("");
                steamClient.disconnect();
            }
        }).start();
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
            updateNotification("Click here to login to Steam");
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
                currentAppId = 0;
                if (farmHandle != null) {
                    farmHandle.cancel(true);
                }
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
            }
        });
        msg.handle(LoggedOnCallback.class, new ActionT<LoggedOnCallback>() {
            @Override
            public void call(final LoggedOnCallback callback) {
                final EResult result = callback.getResult();
                Log.i(TAG, result.toString());

                if (result == EResult.OK) {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            final boolean gotAuth = authenticate(callback);
                            Log.i(TAG, "Got auth? "  + gotAuth);

                            if (gotAuth) {
                                startFarming();
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
                        updateNotification("Login key expired! Click here to login again");
                    }

                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            steamClient.disconnect();
                        }
                    }).start();
                }

                // Tell LoginActivity the result
                final Intent intent = new Intent(LOGIN_INTENT);
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
                    Prefs.writeCmServers(serverString.toString());
                }
            }
        });
    }

    /**
     * Idle a game
     * @param appId game to idle
     */
    private void playGame(int appId) {
        currentAppId = appId;
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
        currentAppId = 0;
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
    public boolean authenticate(LoggedOnCallback callback) {
        authenticated = false;

        //sessionId = Base64.encodeToString(String.valueOf(callback.getUniqueId()).getBytes(), Base64.DEFAULT);
        sessionId = Utils.bytesToHex(CryptoHelper.GenerateRandomBlock(4));

        final String webApiUserNonce = callback.getWebAPIUserNonce();
        final WebAPI userAuth = new WebAPI("ISteamUserAuth", null);
        // generate an AES session key
        final byte[] sessionKey = CryptoHelper.GenerateRandomBlock(32);

        // rsa encrypt it with the public key for the universe we're on
        byte[] cryptedSessionKey = null;
        final RSACrypto rsa = new RSACrypto(KeyDictionary.getPublicKey(steamClient.getConnectedUniverse()));
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
        return true;
    }
}
