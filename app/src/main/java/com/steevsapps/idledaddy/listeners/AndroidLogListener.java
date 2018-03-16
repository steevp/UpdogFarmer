package com.steevsapps.idledaddy.listeners;

import android.util.Log;

import in.dragonbra.javasteam.util.log.LogListener;

/**
 * LogListener that prints to Android Logcat
 */
public class AndroidLogListener implements LogListener {
    private final static String TAG = "JavaSteam";

    @Override
    public void onLog(Class clazz, String message, Throwable throwable) {
        if (clazz == null) {
            throw new IllegalArgumentException("class is null");
        }
        String threadName = Thread.currentThread().getName();
        threadName = threadName.substring(0, Math.min(10, threadName.length()));
        String className = clazz.getName();

        if (message == null) {
            Log.i(TAG, String.format("[%10s] %s", threadName, className), throwable);
        } else {
            Log.i(TAG, String.format("[%10s] %s - %s", threadName, className, message), throwable);
        }
    }
}
