package com.oddlabs.tt.trigger;

import com.oddlabs.matchmaking.Game;
import com.oddlabs.matchmaking.MatchmakingServerInterface;
import com.oddlabs.tt.animation.Animated;
import com.oddlabs.tt.gui.GUIRoot;
import com.oddlabs.tt.net.PeerHub;
import com.oddlabs.tt.player.AdaptiveAIProfile; //added by ikill240c 2026-09-12
import com.oddlabs.tt.player.AdvancedAI;
import com.oddlabs.tt.player.Player;
import com.oddlabs.tt.player.PlayerInfo;
import com.oddlabs.tt.steam.SteamAchievementNames;
import com.oddlabs.tt.steam.SteamManager;
import com.oddlabs.tt.util.Utils;
import com.oddlabs.tt.viewer.WorldViewer;
import org.jspecify.annotations.NonNull;

import java.util.Arrays;
import java.util.ResourceBundle;

public final class GameOverTrigger implements Animated {

    private final int @NonNull [] teams;
    private final boolean @NonNull [] dead_tribes;
    private static final ResourceBundle bundle = ResourceBundle.getBundle(GameOverTrigger.class.getName());

    private @NonNull String i18n(@NonNull String key, @NonNull Object @NonNull... args) {
        return Utils.getBundleString(bundle, key, args);
    }

    private final @NonNull WorldViewer viewer;

    public GameOverTrigger(@NonNull WorldViewer viewer) {
        this.viewer = viewer;
        viewer.getWorld().getAnimationManagerRealTime().registerAnimation(this);
        teams = new int[MatchmakingServerInterface.MAX_PLAYERS];
        dead_tribes = new boolean[viewer.getWorld().getPlayers().length];
        Arrays.fill(dead_tribes, false);
    }

    @Override
    public void animate(float t) {
        Player[] players = viewer.getWorld().getPlayers();
        Player local_player = viewer.getLocalPlayer();
        boolean enemy_alive = false;
        // Whether ANYONE on the local player's own team (including themselves) is still alive
        // in-game - see the loop below for how this and dead_tribes/defeat-message tracking now
        // both use current.isAlive() directly (in-game state only) rather than
        // viewer.getPeerHub().isAlive(current) (which also factored in connection status - see
        // that comment for why that was wrong). //added by ikill240c
        boolean team_alive = false; //added by ikill240c

        for (int i = 0; i < players.length; i++) {
            Player current = players[i];
            if (!dead_tribes[i]) {
                // Was viewer.getPeerHub().isAlive(current), which is
                // "(nonhuman_players.contains(player) || locatePeerFromPlayer(player) != null) &&
                // player.isAlive()" - the connection-status half meant a player who simply
                // disconnected (locatePeerFromPlayer returning null the instant their peer
                // connection drops) was immediately treated as DEFEATED here regardless of
                // whether their base was still standing, firing the "defeat_message" for someone
                // who had merely left rather than lost, and - if that player was a teammate -
                // corrupting the team-alive count with a tribe that was never actually beaten.
                // current.isAlive() alone reflects only the actual in-game state (does this
                // player's Player object still have units/buildings, per the game mode's own
                // isPlayerAlive() check), which is what a victory condition should be judging in
                // the first place - a disconnected player's still-standing base should keep
                // counting for exactly that reason: their team hasn't lost it.
                // //added by ikill240c
                if (!current.isAlive()) { //added by ikill240c
                    dead_tribes[i] = true; //added by ikill240c
                    if (current != local_player) { //added by ikill240c
                        String defeat_message = i18n("defeat_message", current.getPlayerInfo().getName());
                        viewer.getPeerHub().receiveChat(PeerHub.SYSTEM_NAME, defeat_message, false);
                    } //added by ikill240c
                } else if (local_player.isEnemy(current)) {
                    enemy_alive = true;
                } else { //added by ikill240c
                    // Not an enemy and still alive - either the local player themselves, or a
                    // living teammate. Was previously `if (current == local_player) { doGameOver
                    // (...); return; }` right where this branch now sits - meaning the game ended
                    // for the local player the INSTANT THEIR OWN base fell, without ever checking
                    // whether any teammate was still fighting. Per an explicit request that a team
                    // game shouldn't end until the whole team is dead, this now just marks the
                    // team as having a survivor and keeps going; the actual game-over decision
                    // below only fires once nobody on the team (local player included) is left.
                    // //added by ikill240c
                    team_alive = true; //added by ikill240c
                } //added by ikill240c
            }
        }
        if (!team_alive) { //added by ikill240c
            doGameOver(countTeams(players)); //added by ikill240c
            return; //added by ikill240c
        } //added by ikill240c
        if (!enemy_alive) {
            tryUnlockAchievements(local_player, players);
            doGameWon();
            return;
        }
        if (countTeams(players) < 2) {
            stop();
        }
    }

    private void tryUnlockAchievements(@NonNull Player local_player, Player @NonNull [] players) {
        if (SteamManager.getInstance() == null) return;
        // Ludicrous speed only
        if (viewer.getWorld().getGamespeed() != 4) return;

        int ai_team = -1;
        boolean is_player_alone = true;
        boolean all_hards_same_team = true;
        int hard_ais_on_same_team = 0;
        int current_player_team = local_player.getPlayerInfo().getTeam();

        for (Player current : players) {
            if (current != local_player && current.getPlayerInfo().getTeam() == current_player_team) {
                is_player_alone = false;
                break;
            }

            if (current != local_player
                    && current.getAI() instanceof AdvancedAI ai
                    && !ai.isAdaptive() //added by ikill240c 2026-09-12
                    && ai.getDifficulty() == AdvancedAI.DIFFICULTY_HARD) {
                if (ai_team == -1) {
                    ai_team = current.getPlayerInfo().getTeam();
                    hard_ais_on_same_team++;
                } else if (current.getPlayerInfo().getTeam() == ai_team) {
                    hard_ais_on_same_team++;
                } else {
                    all_hards_same_team = false;
                }
            }
        }

        if (!is_player_alone || !all_hards_same_team) return;

        int map_size = viewer.getWorld().getMapSize();
        if (hard_ais_on_same_team >= 3 && map_size == Game.SIZE_SMALL) {
            SteamManager.unlockAchievement(SteamAchievementNames.BEAT_3_HARDS_ON_SMALL);
        } else if (hard_ais_on_same_team >= 5 && map_size == Game.SIZE_MEDIUM) {
            SteamManager.unlockAchievement(SteamAchievementNames.BEAT_5_HARDS_ON_MEDIUM);
        }
    }

    private int countTeams(Player @NonNull [] players) {
        for (int i = 0; i < players.length; i++) {
            teams[i] = 0;
        }

        for (Player current : players) {
            // Was viewer.getPeerHub().isAlive(current) - same connection-status conflation as
            // animate()'s own fix above; a disconnected-but-not-defeated player's team should
            // still count as in the game. //added by ikill240c
            if (current.isAlive() && current.getPlayerInfo().getTeam() != PlayerInfo.TEAM_NEUTRAL) //added by ikill240c
                teams[current.getPlayerInfo().getTeam()]++;
        }

        int team_count = 0;
        for (int team : teams) {
            if (team > 0)
                team_count++;
        }
        return team_count;
    }

    public void disable() {
        viewer.getWorld().getAnimationManagerRealTime().removeAnimation(this);
    }

    private void createDelayTrigger(@NonNull String text) {
        GUIRoot gui_root = viewer.getGUIRoot();
        new GameOverDelayTrigger(viewer, gui_root.getDelegate().getCamera(), text);
    }

    private void doGameOver(int team_count) {
        recordAdaptiveAiResult(false); //added by ikill240c 2026-09-12
        viewer.getPeerHub().leaveGame();
        if (team_count < 2) {
            createDelayTrigger(i18n("you_defeated_game_over"));
        } else {
            createDelayTrigger(i18n("you_defeated"));
        }
        disable();
    }

    private void doGameWon() {
        recordAdaptiveAiResult(true); //added by ikill240c 2026-09-12
        viewer.getPeerHub().gameWon();
        createDelayTrigger(i18n("you_victorious"));
        disable();
    }

    // Feeds the match result back into AdaptiveAIProfile (the single global skill-rating scalar,
    // recorded once per match), the behavior bandit (Stage 2 - recorded once PER adaptive AI, since
    // each one independently selected its own context/arm), and PlayerBehaviorProfile's segmented
    // learning (via AdvancedAI.finalizeAdaptiveLearning() - also once per adaptive AI, since each may
    // have observed a different segment). A no-op pass over the player list if no adaptive AI took
    // part - a match against fixed-difficulty AIs (or all-human) shouldn't move any of these.
    // player_won is from the local human's perspective. //added by ikill240c 2026-09-12
    private void recordAdaptiveAiResult(boolean player_won) { //added by ikill240c 2026-09-12
        boolean skill_rating_recorded = false; //added by ikill240c 2026-09-13
        for (Player current : viewer.getWorld().getPlayers()) { //added by ikill240c 2026-09-12
            if (current.getAI() instanceof AdvancedAI ai && ai.isAdaptive()) { //added by ikill240c 2026-09-12
                if (!skill_rating_recorded) { //added by ikill240c 2026-09-13
                    AdaptiveAIProfile.get().recordMatchResult(player_won); //added by ikill240c 2026-09-12
                    skill_rating_recorded = true; // one global skill-rating update per match is enough //added by ikill240c 2026-09-13
                } //added by ikill240c 2026-09-13
                // Stage 2: feed the same match's result to this AI's own behavior bandit, from its
                // own perspective (the inverse of player_won, which is from the human's).
                // //added by ikill240c 2026-09-12
                ai.recordBanditOutcome(!player_won); //added by ikill240c 2026-09-12
                ai.finalizeAdaptiveLearning(); //added by ikill240c 2026-09-13
            } //added by ikill240c 2026-09-12
        } //added by ikill240c 2026-09-12
    } //added by ikill240c 2026-09-12

    private void stop() {
        createDelayTrigger(i18n("game_over"));
        disable();
    }
}
