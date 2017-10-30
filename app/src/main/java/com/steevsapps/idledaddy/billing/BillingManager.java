package com.steevsapps.idledaddy.billing;

import android.app.Activity;
import android.support.annotation.Nullable;
import android.util.Log;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;

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
        billingClient = BillingClient.newBuilder(activity).setListener(this).build();

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
            public void onBillingSetupFinished(int responseCode) {
                Log.i(TAG, "Billing setup finished. Response code: " + responseCode);

                if (responseCode == BillingClient.BillingResponse.OK) {
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

    private void queryPurchases() {
        final Purchase.PurchasesResult result = billingClient.queryPurchases(BillingClient.SkuType.INAPP);
        final List<Purchase> purchases = result.getPurchasesList();
        if (purchases != null) {
            for (Purchase purchase : purchases) {
                handlePurchase(purchase);
            }
        }
    }

    @Override
    public void onPurchasesUpdated(int responseCode, @Nullable List<Purchase> purchases) {
        if (responseCode == BillingClient.BillingResponse.OK && purchases != null) {
            for (Purchase purchase : purchases) {
                handlePurchase(purchase);
            }
            listener.onPurchasesUpdated(purchases);
        } else if (responseCode == BillingClient.BillingResponse.USER_CANCELED) {
            // Handle an error caused by a user canceling the purchase flow.
            Log.i(TAG, "User canceled.");
        } else {
            // Handle any other error codes.
            Log.i(TAG, "Unknown error. Response code: " + responseCode);
        }
    }

    private void handlePurchase(Purchase purchase) {
        if ("remove_ads".equals(purchase.getSku())) {
            displayAds = false;
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
                final BillingFlowParams params = BillingFlowParams.newBuilder()
                        .setSku("remove_ads")
                        .setType(BillingClient.SkuType.INAPP)
                        .build();
                billingClient.launchBillingFlow(activity, params);
            }
        };
        executeServiceRequest(runnable);
    }
}
