package com.steevsapps.updogfarmer.steam;

import uk.co.thomasc.steamkit.base.generated.steamlanguage.EResult;

/**
 * Callback to handle Steam responses
 */
public interface SteamCallback {
    void onResponse(EResult result);
}
