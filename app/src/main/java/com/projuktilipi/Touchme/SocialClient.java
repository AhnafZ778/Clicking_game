package com.projuktilipi.Touchme;

import android.app.Activity;
import androidx.annotation.Nullable;

public interface SocialClient {
    void signIn(Activity activity, @Nullable Runnable onResult); // silent → interactive fallback
    void signOut();

    boolean isSignedIn();
    @Nullable String getPlayerId();

    void submitScore(Activity activity, String leaderboardId, long score);
    void unlock(Activity activity, String achievementId);

    void showLeaderboards(Activity activity);
    void showAchievements(Activity activity);
}
