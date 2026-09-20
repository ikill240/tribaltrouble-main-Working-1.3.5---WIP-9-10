package com.oddlabs.tt.gamemode.survival; //added by ikill240c

import com.oddlabs.matchmaking.GameModeOption; //added by ikill240c
import com.oddlabs.tt.animation.Animated; //added by ikill240c
import com.oddlabs.tt.gamemode.GameModeRules; //added by ikill240c
import com.oddlabs.tt.gui.Label; //added by ikill240c
import com.oddlabs.tt.gui.Origin; //added by ikill240c
import com.oddlabs.tt.gui.Skin; //added by ikill240c
import com.oddlabs.tt.model.Race; //added by ikill240c
import com.oddlabs.tt.model.Unit; //added by ikill240c
import com.oddlabs.tt.player.AdvancedAI; //added by ikill240c
import com.oddlabs.tt.player.Player; //added by ikill240c
import com.oddlabs.tt.viewer.WorldViewer; //added by ikill240c
import org.jspecify.annotations.NonNull; //added by ikill240c

import java.util.List; //added by ikill240c

// Survival: a endless mode. No win condition - the point is finding out
// how long the local player can hold out against increasingly large waves of enemy reinforcements.
// Rather than building an entirely separate "zombie faction" with its own spawning/targeting logic
// from scratch, this periodically drops a batch of free warriors directly into each existing enemy
// AI player's own army, near that player's own starting position - those units then get picked up
// by that player's OWN AdvancedAI on its own next attack-decision cycle exactly like any other
// idle warrior would, with zero new attack/targeting code needed here. The wave only grows in
// size; there's no economy behind it and nothing to gather or produce, so difficulty comes purely
// from mounting numbers rather than the enemy's own economic growth (which still happens too,
// same as any other mode, compounding on top of this). //added by ikill240c
public final class SurvivalModeRules implements GameModeRules { //added by ikill240c

    private static final float WAVE_INTERVAL_SECONDS = 60f; // time between waves //added by ikill240c
    private static final int BASE_WAVE_SIZE = 0; // wave 1's size //added by ikill240c
    private static final int GROWTH_PER_WAVE = 6; // additional warriors added to each subsequent wave //added by ikill240c
    private static final float SPAWN_SCATTER_RADIUS = 25f; // world units around the reinforced player's own start position //added by ikill240c

    public static final @NonNull String OPTION_WAVE_INTERVAL = "survival_wave_interval"; //added by ikill240c
    public static final @NonNull String OPTION_GROWTH_PER_WAVE = "survival_growth_per_wave"; //added by ikill240c

    // i18n keys ("survival_wave_interval"/"survival_wave_interval_tip",
    // "survival_growth_per_wave"/"survival_growth_per_wave_tip") need adding to
    // TerrainMenu.properties (all locale files) before this schema renders correctly anywhere -
    // same gap KingOfTheIslandModeRules's own options had before its own i18n keys were added.
    // As with that mode, there is currently no live path from a configured GameModeOptions value
    // back into GameModeRules.onGameStart() (see that class's own note on this same limitation),
    // so WAVE_INTERVAL_SECONDS/GROWTH_PER_WAVE above remain the actual hardcoded values used at
    // runtime regardless of what the lobby control shows, until that wiring exists.
    // //added by ikill240c
    private static final @NonNull List<@NonNull GameModeOption> OPTIONS = List.of( //added by ikill240c
            new GameModeOption(OPTION_WAVE_INTERVAL, GameModeOption.Type.FLOAT, WAVE_INTERVAL_SECONDS, //added by ikill240c
                    "survival_wave_interval", 15f, 180f, null), //added by ikill240c
            new GameModeOption(OPTION_GROWTH_PER_WAVE, GameModeOption.Type.FLOAT, (float) GROWTH_PER_WAVE, //added by ikill240c
                    "survival_growth_per_wave", 1f, 10f, null)); //added by ikill240c

    // Per-game mutable state - see KingOfTheIslandModeRules's identical field comment for why this
    // needs resetting in onGameStart() rather than just being initialized once: this same shared
    // instance is reused across every game played in the same process. //added by ikill240c
    private int wave_number = 0; //added by ikill240c
    private float time_since_last_wave = 0f; //added by ikill240c
    private float total_elapsed = 0f; //added by ikill240c

    public int getWaveNumber() { //added by ikill240c
        return wave_number; //added by ikill240c
    } //added by ikill240c

    @Override
    public @NonNull List<@NonNull GameModeOption> getOptions() { //added by ikill240c
        return OPTIONS; //added by ikill240c
    }

    @Override
    public boolean isPlayerAlive(@NonNull Player player) { //added by ikill240c
        // No special win/loss condition of its own - this mode is purely "how long can you last",
        // so elimination works exactly like standard mode (units, chieftain, or Quarters
        // remaining). The existing GameOverTrigger infrastructure ends the game normally once the
        // local player's whole team is gone - see that class for the team-aware version of this
        // check. //added by ikill240c
        int units = player.getUnitCountContainer().getNumSupplies(); //added by ikill240c
        return units > 0 || player.hasActiveChieftain() || player.getQuarters() != null; //added by ikill240c
    }

    @Override
    public void onGameStart(@NonNull WorldViewer viewer) { //added by ikill240c
        wave_number = 0; //added by ikill240c
        time_since_last_wave = 0f; //added by ikill240c
        total_elapsed = 0f; //added by ikill240c

        // Leader/status HUD label, same top-center placement and one-shot-at-creation positioning
        // as KingOfTheIslandModeRules's own status label. //added by ikill240c
        Label survival_status_label = new Label("", Skin.getSkin().getHeadlineFont(), 400, Origin.AT_MIDDLE); //added by ikill240c
        survival_status_label.setPos((viewer.getGUIRoot().getWidth() - survival_status_label.getWidth()) / 2, 10); //added by ikill240c
        viewer.getGUIRoot().addChild(survival_status_label); //added by ikill240c

        viewer.getWorld().getAnimationManagerGameTime().registerAnimation(new Animated() { //added by ikill240c
            @Override
            public void animate(float t) {
                total_elapsed += t; //added by ikill240c
                time_since_last_wave += t; //added by ikill240c
                if (time_since_last_wave >= WAVE_INTERVAL_SECONDS) { //added by ikill240c
                    time_since_last_wave = 0f; //added by ikill240c
                    wave_number++; //added by ikill240c
                    spawnWave(viewer, wave_number); //added by ikill240c
                } //added by ikill240c

                int minutes = (int) (total_elapsed / 60f); //added by ikill240c
                int seconds = (int) (total_elapsed % 60f); //added by ikill240c
                survival_status_label.setText( //added by ikill240c
                        "Wave " + wave_number + " - Survived " + minutes + ":" + (seconds < 10 ? "0" : "") + seconds); //added by ikill240c
            }
        });
    }

    // Drops a fresh batch of warriors into every AI-controlled enemy of the local player, scattered
    // around that player's own starting position. Deliberately every AI enemy, not just one
    // designated "zombie" player - in a multi-opponent lobby, every enemy grows in lockstep with
    // the wave counter, keeping the pressure escalating from every direction at once rather than
    // only one side of the map. //added by ikill240c
    private void spawnWave(@NonNull WorldViewer viewer, int wave) { //added by ikill240c
        int wave_size = BASE_WAVE_SIZE + (wave - 1) * GROWTH_PER_WAVE; //added by ikill240c
        Player local_player = viewer.getLocalPlayer(); //added by ikill240c
        int[] warrior_templates = {Race.UNIT_WARRIOR_ROCK, Race.UNIT_WARRIOR_IRON, Race.UNIT_WARRIOR_RUBBER}; //added by ikill240c

        for (Player p : viewer.getWorld().getPlayers()) { //added by ikill240c
            if (p == local_player || !local_player.isEnemy(p) || !(p.getAI() instanceof AdvancedAI)) //added by ikill240c
                continue; // only AI-controlled actual enemies of the local player get reinforced //added by ikill240c
            Race race = p.getRace(); //added by ikill240c
            float base_x = p.getStartX(); //added by ikill240c
            float base_y = p.getStartY(); //added by ikill240c
            for (int i = 0; i < wave_size; i++) { //added by ikill240c
                double angle = 2 * Math.PI * i / wave_size; //added by ikill240c - evenly spread around the base rather than piled on one spot
                float spawn_x = base_x + (float) (Math.cos(angle) * SPAWN_SCATTER_RADIUS); //added by ikill240c
                float spawn_y = base_y + (float) (Math.sin(angle) * SPAWN_SCATTER_RADIUS); //added by ikill240c
                int template = warrior_templates[i % warrior_templates.length]; //added by ikill240c - cycles evenly through all three weapon types rather than always the same one
                new Unit(p, spawn_x, spawn_y, null, race.getUnitTemplate(template)); //added by ikill240c
            } //added by ikill240c
        } //added by ikill240c
    } //added by ikill240c
} //added by ikill240c
