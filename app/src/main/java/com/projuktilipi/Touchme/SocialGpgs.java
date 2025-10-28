package com.projuktilipi.Touchme;

import android.app.Activity;
import android.util.Log;
import androidx.annotation.Nullable;

import com.google.android.gms.games.PlayGames;
import com.google.android.gms.games.GamesSignInClient;
import com.google.android.gms.games.PlayersClient;
import com.google.android.gms.games.LeaderboardsClient;
import com.google.android.gms.games.AchievementsClient;

public class SocialGpgs implements SocialClient {
    private static final String TAG = "SocialGPGS";

    private volatile boolean authed = false;
    private volatile @Nullable String playerId = null;

    @Override
    public void signIn(Activity activity, @Nullable Runnable onResult) {
        GamesSignInClient signIn = PlayGames.getGamesSignInClient(activity);
        signIn.isAuthenticated().addOnCompleteListener(t -> {
            boolean ok = t.isSuccessful() && t.getResult().isAuthenticated();
            if (ok) {
                authed = true;
                fetchPlayerId(activity, onResult);
            } else {
                signIn.signIn()
                        .addOnSuccessListener(v -> { authed = true; fetchPlayerId(activity, onResult); })
                        .addOnFailureListener(e -> {
                            authed = false;
                            Log.w(TAG, "PGS signIn failed: " + e.getMessage());
                            if (onResult != null) onResult.run();
                        });
            }
        });
    }

    private void fetchPlayerId(Activity act, @Nullable Runnable onResult) {
        PlayersClient players = PlayGames.getPlayersClient(act);
        players.getCurrentPlayer().addOnCompleteListener(m -> {
            if (m.isSuccessful() && m.getResult() != null) {
                playerId = m.getResult().getPlayerId();
                Log.d(TAG, "PGS PlayerID = " + playerId);
            } else {
                Log.w(TAG, "Failed to get player");
            }
            if (onResult != null) onResult.run();
        });
    }

    @Override public void signOut() { authed = false; playerId = null; }
    @Override public boolean isSignedIn() { return authed; }
    @Override public @Nullable String getPlayerId() { return playerId; }

    @Override
    public void submitScore(Activity act, String leaderboardId, long score) {
        if (!authed || leaderboardId == null || leaderboardId.isEmpty()) return;
        LeaderboardsClient lb = PlayGames.getLeaderboardsClient(act);
        lb.submitScore(leaderboardId, score);
    }

    @Override
    public void unlock(Activity act, String achievementId) {
        if (!authed || achievementId == null || achievementId.isEmpty()) return;
        AchievementsClient ac = PlayGames.getAchievementsClient(act);
        ac.unlock(achievementId);
    }

    @Override
    public void showLeaderboards(Activity act) {
        if (!authed) { signIn(act, () -> showLeaderboards(act)); return; }
        PlayGames.getLeaderboardsClient(act)
                .getAllLeaderboardsIntent()
                .addOnSuccessListener(act::startActivity);
    }

    @Override
    public void showAchievements(Activity act) {
        if (!authed) { signIn(act, () -> showAchievements(act)); return; }
        PlayGames.getAchievementsClient(act)
                .getAchievementsIntent()
                .addOnSuccessListener(act::startActivity);
    }
}
