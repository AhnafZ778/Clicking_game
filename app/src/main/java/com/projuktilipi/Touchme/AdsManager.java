package com.projuktilipi.Touchme;

import android.app.Activity;
import android.content.Context;

import androidx.annotation.MainThread;
import androidx.annotation.Nullable;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.OnUserEarnedRewardListener;  // <-- fixed package
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.gms.ads.rewarded.RewardItem;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;

public class AdsManager {

    public interface RewardListener { void onRewardEarned(); }

    // Google sample unit IDs (use your real IDs in release)
    private static final String TEST_INTERSTITIAL = "ca-app-pub-3940256099942544/1033173712";
    private static final String TEST_REWARDED     = "ca-app-pub-3940256099942544/5224354917";

    private InterstitialAd interstitial;
    private RewardedAd rewarded;

    public void init(Context ctx) {
        MobileAds.initialize(ctx, initializationStatus -> { });
        loadInterstitial(ctx);
        loadRewarded(ctx);
    }

    public void loadInterstitial(Context ctx) {
        AdRequest req = new AdRequest.Builder().build();
        InterstitialAd.load(ctx, TEST_INTERSTITIAL, req, new InterstitialAdLoadCallback() {
            @Override public void onAdLoaded(InterstitialAd ad) { interstitial = ad; }
            @Override public void onAdFailedToLoad(com.google.android.gms.ads.LoadAdError err) { interstitial = null; }
        });
    }

    public void loadRewarded(Context ctx) {
        AdRequest req = new AdRequest.Builder().build();
        RewardedAd.load(ctx, TEST_REWARDED, req, new RewardedAdLoadCallback() {
            @Override public void onAdLoaded(RewardedAd ad) { rewarded = ad; }
            @Override public void onAdFailedToLoad(com.google.android.gms.ads.LoadAdError err) { rewarded = null; }
        });
    }

    @MainThread
    public void showInterstitial(Activity act, @Nullable Runnable onClosed) {
        if (interstitial == null) { loadInterstitial(act); if (onClosed != null) onClosed.run(); return; }
        interstitial.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override public void onAdDismissedFullScreenContent() {
                interstitial = null; loadInterstitial(act);
                if (onClosed != null) onClosed.run();
            }
            @Override public void onAdFailedToShowFullScreenContent(AdError adError) {
                interstitial = null; loadInterstitial(act);
                if (onClosed != null) onClosed.run();
            }
        });
        interstitial.show(act);
    }

    @MainThread
    public void showRewarded(Activity act, @Nullable RewardListener listener) {
        if (rewarded == null) { loadRewarded(act); return; }
        rewarded.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override public void onAdDismissedFullScreenContent() {
                rewarded = null; loadRewarded(act);
            }
            @Override public void onAdFailedToShowFullScreenContent(AdError adError) {
                rewarded = null; loadRewarded(act);
            }
        });
        rewarded.show(act, new OnUserEarnedRewardListener() {
            @Override public void onUserEarnedReward(RewardItem rewardItem) {
                if (listener != null) listener.onRewardEarned();
            }
        });
    }
}
