package com.steevsapps.idledaddy.steam;

import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import com.steevsapps.idledaddy.preferences.PrefsManager;
import com.steevsapps.idledaddy.utils.Utils;

import java.nio.charset.Charset;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

public class SteamGuard {
    private final static String TAG = SteamGuard.class.getSimpleName();

    private final static byte[] steamGuardCodeTranslations = new byte[] {50, 51, 52, 53, 54, 55, 56, 57, 66, 67, 68, 70, 71, 72, 74, 75, 77, 78, 80, 81, 82, 84, 86, 87, 88, 89};

    private SteamGuard(){
        // private empty constructor
    }

    public static String generateSteamGuardCodeForTime(long time) {
        final String sharedSecret = PrefsManager.getSharedSecret();
        if (TextUtils.isEmpty(sharedSecret)) {
            Log.w(TAG, "shared_secret is empty!");
            return "";
        }

        final byte[] sharedSecretArray = Base64.decode(sharedSecret, Base64.DEFAULT);
        final byte[] timeArray = new byte[8];

        time /= 30L;

        for (int i=8; i>0; i--) {
            timeArray[i - 1] = (byte) time;
            time >>= 8;
        }

        final byte[] hashedData;
        final byte[] codeArray = new byte[5];
        try {
            hashedData = Utils.calculateRFC2104HMAC(timeArray,sharedSecretArray);
        } catch (NoSuchAlgorithmException|InvalidKeyException e) {
            Log.e(TAG, "Failed to compute HMAC SHA1!", e);
            return "";
        }

        try {
            final byte b = (byte) (hashedData[19] & 0xF);
            int codePoint = (hashedData[b] & 0x7F) << 24 | (hashedData[b + 1] & 0xFF) << 16 | (hashedData[b + 2] & 0xFF) << 8 | (hashedData[b + 3] & 0xFF);
            for (int i=0; i<5; i++) {
                codeArray[i] = steamGuardCodeTranslations[codePoint % steamGuardCodeTranslations.length];
                codePoint /= steamGuardCodeTranslations.length;
            }
            return new String(codeArray, Charset.forName("UTF-8"));
        } catch (Exception e) {
            Log.e(TAG, "Failed to generate SteamGuard code!", e);
            return "";
        }
    }
}
