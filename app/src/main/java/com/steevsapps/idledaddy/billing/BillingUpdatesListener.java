package com.steevsapps.idledaddy.billing;

import com.android.billingclient.api.Purchase;

import java.util.List;

public interface BillingUpdatesListener {
    void onBillingClientSetupFinished();
    void onPurchasesUpdated(List<Purchase> purchases);
}
