package com.projuktilipi.Touchme;

import android.app.Activity;
import android.content.Context;

import androidx.annotation.MainThread;
import androidx.annotation.Nullable;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;

import java.util.ArrayList;
import java.util.List;

public class BillingManager implements PurchasesUpdatedListener {

    public interface Listener {
        void onBillingReady();
        void onAdsRemoved();
        void onPurchaseFailed(String reason);
    }

    public static final String SKU_REMOVE_ADS = "remove_ads";
    public static final String SKU_SKIN_PACK  = "skin_pack"; // optional cosmetic

    private final Context app;
    private final Listener listener;

    private BillingClient client;
    private ProductDetails pdRemoveAds;
    private ProductDetails pdSkin;

    public BillingManager(Context ctx, Listener listener) {
        this.app = ctx.getApplicationContext();
        this.listener = listener;
    }

    public void start() {
        client = BillingClient.newBuilder(app)
                .setListener(this)
                .enablePendingPurchases()
                .build();

        client.startConnection(new BillingClientStateListener() {
            @Override public void onBillingSetupFinished(BillingResult billingResult) {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    queryProductDetails();
                    restore();
                    if (listener != null) listener.onBillingReady();
                }
            }
            @Override public void onBillingServiceDisconnected() {
                // You can retry startConnection() later.
            }
        });
    }

    private void queryProductDetails() {
        List<QueryProductDetailsParams.Product> list = new ArrayList<>();
        list.add(QueryProductDetailsParams.Product.newBuilder()
                .setProductId(SKU_REMOVE_ADS)
                .setProductType(BillingClient.ProductType.INAPP)
                .build());
        list.add(QueryProductDetailsParams.Product.newBuilder()
                .setProductId(SKU_SKIN_PACK)
                .setProductType(BillingClient.ProductType.INAPP)
                .build());

        QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder()
                .setProductList(list)
                .build();

        client.queryProductDetailsAsync(params, (br, detailsList) -> {
            if (br.getResponseCode() == BillingClient.BillingResponseCode.OK && detailsList != null) {
                for (ProductDetails d : detailsList) {
                    if (SKU_REMOVE_ADS.equals(d.getProductId())) pdRemoveAds = d;
                    if (SKU_SKIN_PACK.equals(d.getProductId()))  pdSkin = d;
                }
            }
        });
    }

    @MainThread
    public void buyRemoveAds(Activity act) {
        if (pdRemoveAds == null) {
            if (listener != null) listener.onPurchaseFailed("Item not available yet");
            return;
        }
        BillingFlowParams.ProductDetailsParams p =
                BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(pdRemoveAds)
                        .build();

        BillingFlowParams flow = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(java.util.Collections.singletonList(p))
                .build();

        client.launchBillingFlow(act, flow);
    }

    @MainThread
    public void buySkinPack(Activity act) {
        if (pdSkin == null) {
            if (listener != null) listener.onPurchaseFailed("Item not available yet");
            return;
        }
        BillingFlowParams.ProductDetailsParams p =
                BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(pdSkin)
                        .build();

        BillingFlowParams flow = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(java.util.Collections.singletonList(p))
                .build();

        client.launchBillingFlow(act, flow);
    }

    public void restore() {
        // Billing v6 form
        QueryPurchasesParams params = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build();
        client.queryPurchasesAsync(params, (br, list) -> {
            if (list != null) handlePurchases(list);
        });
    }

    @Override
    public void onPurchasesUpdated(BillingResult br, @Nullable List<Purchase> purchases) {
        if (br.getResponseCode() == BillingClient.BillingResponseCode.OK && purchases != null) {
            handlePurchases(purchases);
        } else if (listener != null
                && br.getResponseCode() != BillingClient.BillingResponseCode.USER_CANCELED) {
            listener.onPurchaseFailed(br.getDebugMessage());
        }
    }

    private void handlePurchases(List<Purchase> list) {
        for (Purchase p : list) {
            if (p.getPurchaseState() != Purchase.PurchaseState.PURCHASED) continue;
            if (p.getProducts().isEmpty()) continue;

            String id = p.getProducts().get(0);

            if (SKU_REMOVE_ADS.equals(id)) {
                if (!p.isAcknowledged()) {
                    AcknowledgePurchaseParams ack = AcknowledgePurchaseParams.newBuilder()
                            .setPurchaseToken(p.getPurchaseToken())
                            .build();
                    client.acknowledgePurchase(ack, result -> { /* optional: check result */ });
                }
                if (listener != null) listener.onAdsRemoved();
            }

            // Add handling for other products here if needed
        }
    }
}
