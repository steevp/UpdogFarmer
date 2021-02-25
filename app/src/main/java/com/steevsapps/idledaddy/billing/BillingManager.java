package com.steevsapps.idledaddy.billing;

import android.app.Activity;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.AcknowledgePurchaseResponseListener;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.SkuDetails;
import com.android.billingclient.api.SkuDetailsParams;
import com.android.billingclient.api.SkuDetailsResponseListener;

import java.util.ArrayList;
import java.util.List;

public class BillingManager implements PurchasesUpdatedListener {
    private final static String TAG = BillingManager.class.getSimpleName();

    private Activity activity;
    private BillingUpdatesListener listener;
    private BillingClient billingClient;

    private boolean serviceConnected = false;
    private boolean displayAds = true;

    public BillingManager(Activity activity) {
        this.activity = activity;
        try {
            listener = (BillingUpdatesListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString() + " must implement BillingUpdatesListener.");
        }

        // Setup the billing client
        billingClient = BillingClient.newBuilder(activity)
                .enablePendingPurchases()
                .setListener(this)
                .build();

        // Start the setup asynchronously.
        // The specified listener is called once setup completes.
        // New purchases are reported through the onPurchasesUpdated() callback
        // of the class specified using the setListener() method above.
        startServiceConnection(new Runnable() {
            @Override
            public void run() {
                // Query existing purchases
                queryPurchases();
                // Notify the listener that the billing client is ready.
                listener.onBillingClientSetupFinished();
            }
        });
    }

    private void startServiceConnection(final Runnable executeOnSuccess) {
        billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(@NonNull BillingResult billingResult) {
                Log.i(TAG, "Billing setup finished. Response code: " + billingResult.getResponseCode());

                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    serviceConnected = true;
                    if (executeOnSuccess != null) {
                        executeOnSuccess.run();
                    }
                }
            }

            @Override
            public void onBillingServiceDisconnected() {
                Log.i(TAG, "Billing service disconnected");
                serviceConnected = false;
            }
        });
    }

    private void executeServiceRequest(Runnable runnable) {
        if (serviceConnected) {
            runnable.run();
        } else {
            // If billing service was disconnected, we try to reconnect 1 time.
            startServiceConnection(runnable);
        }
    }

    public void queryPurchases() {
        if (billingClient != null && billingClient.isReady()) {
            final Purchase.PurchasesResult result = billingClient.queryPurchases(BillingClient.SkuType.INAPP);
            final List<Purchase> purchases = result.getPurchasesList();

            if (purchases != null) {
                for (Purchase purchase : purchases) {
                    handlePurchase(purchase);
                }
            }
        }
    }

    @Override
    public void onPurchasesUpdated(@NonNull BillingResult billingResult, @Nullable List<Purchase> purchases) {
        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (Purchase purchase : purchases) {
                handlePurchase(purchase);
            }
            Log.i(TAG, "Purchases updated.");
            listener.onPurchasesUpdated(purchases);
        } else if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.USER_CANCELED) {
            // Handle an error caused by a user canceling the purchase flow.
            Log.i(TAG, "Purchase canceled.");
            listener.onPurchaseCanceled();
        } else {
            // Handle any other error codes.
            Log.e(TAG, billingResult.getDebugMessage());
        }
    }

    private void handlePurchase(Purchase purchase) {
        if ("remove_ads".equals(purchase.getSku()) && purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
            displayAds = false;

            if (!purchase.isAcknowledged()) {
                // Acknowledge the purchase
                final AcknowledgePurchaseParams acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.getPurchaseToken())
                        .build();
                billingClient.acknowledgePurchase(acknowledgePurchaseParams, new AcknowledgePurchaseResponseListener() {
                    @Override
                    public void onAcknowledgePurchaseResponse(@NonNull BillingResult billingResult) {
                        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                            Log.i(TAG, "Purchase acknowledged");
                        } else {
                            Log.e(TAG, billingResult.getDebugMessage());
                        }
                    }
                });
            }
        }
    }

    public boolean shouldDisplayAds() {
        return displayAds;
    }

    public void destroy() {
        if (billingClient != null && billingClient.isReady()) {
            billingClient.endConnection();
            billingClient = null;
        }
    }

    public void launchPurchaseFlow() {
        final Runnable runnable = new Runnable() {
            @Override
            public void run() {
                final List<String> skuList = new ArrayList<>();
                skuList.add("remove_ads");
                final SkuDetailsParams skuParams = SkuDetailsParams.newBuilder()
                        .setSkusList(skuList)
                        .setType(BillingClient.SkuType.INAPP)
                        .build();
                billingClient.querySkuDetailsAsync(skuParams, new SkuDetailsResponseListener() {
                    @Override
                    public void onSkuDetailsResponse(@NonNull BillingResult billingResult, @Nullable List<SkuDetails> skuDetails) {
                        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && skuDetails != null) {
                            final BillingFlowParams billingFlowParams = BillingFlowParams.newBuilder()
                                    .setSkuDetails(skuDetails.get(0))
                                    .build();
                            billingClient.launchBillingFlow(activity, billingFlowParams);
                        } else {
                            Log.e(TAG, billingResult.getDebugMessage());
                        }
                    }
                });
            }
        };
        executeServiceRequest(runnable);
    }
}
