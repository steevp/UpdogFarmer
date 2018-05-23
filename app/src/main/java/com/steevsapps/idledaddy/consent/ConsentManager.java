package com.steevsapps.idledaddy.consent;

import android.content.Context;
import android.util.Log;

import com.google.ads.consent.ConsentForm;
import com.google.ads.consent.ConsentFormListener;
import com.google.ads.consent.ConsentInfoUpdateListener;
import com.google.ads.consent.ConsentInformation;
import com.google.ads.consent.ConsentStatus;
import com.google.ads.consent.DebugGeography;

import java.net.MalformedURLException;
import java.net.URL;

public class ConsentManager {
    private final static String TAG = ConsentManager.class.getSimpleName();
    private final static String PRIVACY_URL = "https://gist.github.com/steevp/2f80e3a05adf1112453ca50ac961d3c7";

    private Context context;
    private ConsentListener listener;
    private ConsentInformation consentInfo;
    private ConsentForm consentForm;

    public ConsentManager(Context context) {
        this.context = context;
        try {
            listener = (ConsentListener) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context.toString() + " must implement ConsentListener!");
        }
        setupConsentInfo();
        setupConsentForm();
    }

    /**
     * Request user consent status
     */
    public void requestConsentInfo() {
        final String[] publisherIds = {"pub-6413501894389361"};
        consentInfo.requestConsentInfoUpdate(publisherIds, new ConsentInfoUpdateListener() {
            @Override
            public void onConsentInfoUpdated(ConsentStatus consentStatus) {
                // User's consent status successfully updated.
                handleConsent(consentStatus);
            }

            @Override
            public void onFailedToUpdateConsentInfo(String reason) {
                Log.i(TAG, "Consent status failed to update. Reason: " + reason);
            }
        });
    }

    public void revokeConsent() {
        consentInfo.setConsentStatus(ConsentStatus.UNKNOWN);
        requestConsentInfo();
    }

    private void setupConsentInfo() {
        consentInfo = ConsentInformation.getInstance(context);
        consentInfo.addTestDevice("4A88C5F6AED0FB3E97E38F1719B2EEDB");
        consentInfo.setDebugGeography(DebugGeography.DEBUG_GEOGRAPHY_EEA);
    }

    private void setupConsentForm() {
        URL privacyUrl = null;
        try {
            privacyUrl = new URL(PRIVACY_URL);
        } catch (MalformedURLException e) {
            Log.i(TAG, "Privacy URL is invalid. This should never happen.", e);
            return;
        }
        consentForm = new ConsentForm.Builder(context, privacyUrl)
                .withListener(new ConsentFormListener() {
                    @Override
                    public void onConsentFormLoaded() {
                        // Consent form loaded successfully.
                        consentForm.show();
                    }

                    @Override
                    public void onConsentFormError(String reason) {
                        // Consent form error.
                        Log.i(TAG, "Consent form failed to load. Reason: " + reason);
                    }

                    @Override
                    public void onConsentFormOpened() {
                        // Consent form was displayed.
                    }

                    @Override
                    public void onConsentFormClosed(ConsentStatus consentStatus, Boolean userPrefersAdFree) {
                        // Consent form was closed.
                        listener.onConsentInfoUpdated(consentStatus, userPrefersAdFree);
                    }
                })
                .withPersonalizedAdsOption()
                .withNonPersonalizedAdsOption()
                .withAdFreeOption()
                .build();
    }

    /**
     * Handle consent status
     */
    private void handleConsent(ConsentStatus consentStatus) {
        switch (consentStatus) {
            case PERSONALIZED:
            case NON_PERSONALIZED:
                // Got user consent
                listener.onConsentInfoUpdated(consentStatus, false);
                break;
            case UNKNOWN:
                if (consentInfo.isRequestLocationInEeaOrUnknown()) {
                    // EU user detected. Request user consent
                    consentForm.load();
                } else {
                    // Show personalized ads
                    listener.onConsentInfoUpdated(consentStatus, false);
                }
                break;
        }
    }
}
