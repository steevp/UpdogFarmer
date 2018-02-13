package com.steevsapps.idledaddy;


import android.support.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@IntDef({SpinnerTabIds.GAMES, SpinnerTabIds.LAST_SESSION, SpinnerTabIds.BLACKLIST})
@Retention(RetentionPolicy.SOURCE)
public @interface SpinnerTabIds {
    int GAMES = 0;
    int LAST_SESSION = 1;
    int BLACKLIST = 2;
}
