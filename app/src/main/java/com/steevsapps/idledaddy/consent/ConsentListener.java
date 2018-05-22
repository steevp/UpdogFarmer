package com.steevsapps.idledaddy.consent;

import com.google.ads.consent.ConsentStatus;

public interface ConsentListener {
    void onConsentInfoUpdated(ConsentStatus consentStatus, boolean userPrefersAdFree);
}
