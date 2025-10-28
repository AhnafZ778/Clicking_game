package com.projuktilipi.Touchme;

import android.app.Activity;
import androidx.annotation.Nullable;

public class SocialNoop implements SocialClient {
    @Override public void signIn(Activity activity, @Nullable Runnable onResult) { if (onResult != null) onResult.run(); }
    @Override public void signOut() { }
    @Override public boolean isSignedIn() { return false; }
    @Override public @Nullable String getPlayerId() { return null; }
    @Override public void submitScore(Activity activity, String leaderboardId, long score) { }
    @Override public void unlock(Activity activity, String achievementId) { }
    @Override public void showLeaderboards(Activity activity) { }
    @Override public void showAchievements(Activity activity) { }
}
