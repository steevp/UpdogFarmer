package com.steevsapps.idledaddy.listeners;

import android.util.Log;

import uk.co.thomasc.steamkit.util.logging.IDebugListener;

/**
 * DebugLog listener that writes to Logcat.
 *
 * Use with:
 * DebugLog.addListener(new LogcatDebugListener())
 */
public class LogcatDebugListener implements IDebugListener {
    @Override
    public void writeLine(String category, String msg) {
        final String[] lines = msg.split(System.getProperty("line.separator"));
        for (String line: lines) {
            Log.i("SteamKit", String.format("%s: %s", category, msg));
        }
    }
}
