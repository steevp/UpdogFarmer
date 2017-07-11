package com.steevsapps.updogfarmer.steam;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.steevsapps.updogfarmer.LoginActivity;
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

    private SteamCallback listener;

    private static SteamService ourInstance;

    public static SteamService getInstance() {
        return ourInstance;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        Log.i(TAG, "Service created");
        super.onCreate();
        ourInstance = this;
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
        return Service.START_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "Service destroyed");
        super.onDestroy();
        stopForeground(true);
        running = false;
    }

    private Notification buildNotification(String text) {
        final Intent notificationIntent = new Intent(this, LoginActivity.class);
        final PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                notificationIntent, 0);
        return new NotificationCompat.Builder(this)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(text)
                .setContentIntent(pendingIntent).build();
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

    public void setListener(SteamCallback callback) {
        listener = callback;
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
                if (running) {
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    steamClient.connect();
                }
            }
        });
        msg.handle(LoggedOnCallback.class, new ActionT<LoggedOnCallback>() {
            @Override
            public void call(final LoggedOnCallback callback) {
                final EResult result = callback.getResult();
                Log.i(TAG, result.toString());

                if (result == EResult.OK) {
                    final boolean gotAuth = authenticate(steamClient, callback);
                    Log.i(TAG, "Got auth? " + gotAuth);

                    steamFriends.setPersonaState(EPersonaState.Online);
                    Log.i(TAG, "playing sonic");
                    updateNotification("Playing Sonic Adventure DX");
                    playGame();
                } else {
                    if (result == EResult.InvalidPassword && !Prefs.getLoginKey().isEmpty()) {
                        // Probably no longer valid
                        Prefs.writeLoginKey("");
                        updateNotification("Login failed! Click here to try again.");
                    }
                    steamClient.disconnect();
                }

                if (listener != null) {
                    // Needs to run on ui thread
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            listener.onResponse(result);
                        }
                    });
                }
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

                    List<WebScraper.Badge> badges = WebScraper.getRemainingGames(generateWebCookies());
                    for (WebScraper.Badge b: badges) {
                        Log.i(TAG, "bagde " + b.name);
                    }
                }
            }
        });
    }

    private void playGame() {
        steamUser.setPlayingGame(71250);
    }

    private void stopPlaying() {
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
        cookies.put("steamMachineAuth" + steamClient.getSteamId().convertToLong(), sentryHash);
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
    public boolean authenticate(SteamClient steamClient, LoggedOnCallback callback) {
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
