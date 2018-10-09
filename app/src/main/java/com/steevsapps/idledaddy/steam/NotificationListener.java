package com.steevsapps.idledaddy.steam;

import android.os.Bundle;
import android.support.annotation.StringRes;

import com.steevsapps.idledaddy.steam.model.Game;

import java.util.List;

public interface NotificationListener {
    void showTextNotification(String tag, @StringRes int resId);
    void showIdleNotification(String tag, List<Game> games, boolean farming);
    void showPausedNotification(String tag);
    void showToast(@StringRes int resId);
    void showToast(@StringRes int resId, String formatArgs);
    void sendEvent(String tag, String event);
    void sendEvent(String tag, String event, Bundle args);
}
