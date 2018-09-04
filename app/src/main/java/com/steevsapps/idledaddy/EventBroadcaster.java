package com.steevsapps.idledaddy;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.content.LocalBroadcastManager;

public class EventBroadcaster {

    /**
     * Send event using LocalBroadcastManager
     */
    public static void send(@NonNull Context context, @NonNull String event) {
        LocalBroadcastManager.getInstance(context).sendBroadcast(new Intent(event));
    }

    /**
     * Send event with extras
     */
    public static void send(@NonNull Context context, @NonNull String event, @NonNull Bundle extras) {
        final Intent intent = new Intent(event);
        intent.putExtras(extras);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }
}
