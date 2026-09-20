package com.oddlabs.tt.player; //added by ikill240c 2026-09-12

import com.oddlabs.tt.global.Globals; //added by ikill240c 2026-09-12
import com.oddlabs.tt.render.Renderer; //added by ikill240c 2026-09-12
import org.jspecify.annotations.NonNull; //added by ikill240c 2026-09-12

import java.io.IOException; //added by ikill240c 2026-09-12
import java.io.InputStream; //added by ikill240c 2026-09-12
import java.io.OutputStream; //added by ikill240c 2026-09-12
import java.nio.file.Files; //added by ikill240c 2026-09-12
import java.nio.file.Path; //added by ikill240c 2026-09-12
import java.time.Instant; //added by ikill240c 2026-09-12
import java.util.Properties; //added by ikill240c 2026-09-12
import java.util.logging.Level; //added by ikill240c 2026-09-12
import java.util.logging.Logger; //added by ikill240c 2026-09-12

// Stage 1 of a play-style-aware AI: a persisted, low-dimensional summary of how a human opponent
// tends to play, derived purely from counters Player already maintains (units_killed/lost,
// buildings_destroyed/lost, and the four harvest counters) - no new instrumentation of combat or
// gather code was needed. This is deliberately a hand-tuned heuristic, not a trained model, for the
// same reason AdaptiveAIProfile is: there is no training pipeline or labeled dataset in this
// codebase yet. The intent is for this to be the feature source a real model (or a contextual
// bandit choosing among AdvancedAI behavior profiles) is layered on top of later, once enough
// signal like this exists to make training meaningful.
//
// Two complementary-but-independently-tracked axes:
//  - aggression:     how much of the player's activity is combat (dealing OR taking losses) vs.
//                     gathering, sampled as a rate and smoothed with an EMA so a single skirmish
//                     doesn't swing the reading.
//  - economy_focus:  the inverse lean - high when the player spends most of their activity
//                     harvesting rather than fighting.
// Both live on [0, 1] and are seeded at 0.5 (neutral) for a player with no history, matching
// AdaptiveAIProfile's convention for its skill_rating default.
//
// Scope is intentionally the same as AdaptiveAIProfile: one profile per installation. A per-race or
// per-opponent breakdown would need real design work (see AdaptiveAIProfile's own note on this) and
// isn't worth building until there's a concrete consumer that needs it. //added by ikill240c 2026-09-12
public final class PlayerStyleProfile { //added by ikill240c 2026-09-12
    private static final Logger logger = Logger.getLogger(PlayerStyleProfile.class.getName()); //added by ikill240c 2026-09-12

    private static final float DEFAULT_AXIS_VALUE = 0.5f; //added by ikill240c 2026-09-12
    // How much weight a single fresh sample gets when blended into the running EMA. Lower than
    // AdaptiveAIProfile's LEARNING_RATE (0.15) because this samples many times per match
    // (every SAMPLE_INTERVAL_SECONDS) rather than once per match - a higher rate here would let the
    // reading swing wildly within a single game based on short bursts of fighting or gathering.
    // //added by ikill240c 2026-09-12
    private static final float SAMPLE_WEIGHT = 0.08f; //added by ikill240c 2026-09-12
    // How often update() actually takes a sample; calls between intervals are a cheap no-op so
    // callers can invoke this every tick without needing their own throttle, the same convenience
    // AdvancedAI.nodeAdaptiveDifficulty() provides for its own interval. //added by ikill240c 2026-09-12
    private static final float SAMPLE_INTERVAL_SECONDS = 20f; //added by ikill240c 2026-09-12

    private static @org.jspecify.annotations.Nullable PlayerStyleProfile instance; //added by ikill240c 2026-09-12

    private float aggression = DEFAULT_AXIS_VALUE; //added by ikill240c 2026-09-12
    private float economy_focus = DEFAULT_AXIS_VALUE; //added by ikill240c 2026-09-12

    // Snapshot of the subject's cumulative counters as of the last sample, so update() can work from
    // deltas (a rate) instead of raw totals. -1 means "no snapshot yet" - the very first call after
    // a profile is attached to a match only primes these rather than computing a (misleadingly huge)
    // delta against zero. //added by ikill240c 2026-09-12
    private int prev_units_killed = -1; //added by ikill240c 2026-09-12
    private int prev_units_lost = -1; //added by ikill240c 2026-09-12
    private int prev_buildings_destroyed = -1; //added by ikill240c 2026-09-12
    private int prev_buildings_lost = -1; //added by ikill240c 2026-09-12
    private int prev_harvested = -1; //added by ikill240c 2026-09-12
    private float time_since_sample = 0f; //added by ikill240c 2026-09-12

    private PlayerStyleProfile() { //added by ikill240c 2026-09-12
    } //added by ikill240c 2026-09-12

    // Lazily loaded singleton, same lifecycle as AdaptiveAIProfile.get(). //added by ikill240c 2026-09-12
    public static synchronized @NonNull PlayerStyleProfile get() { //added by ikill240c 2026-09-12
        if (instance == null) { //added by ikill240c 2026-09-12
            instance = new PlayerStyleProfile(); //added by ikill240c 2026-09-12
            instance.load(); //added by ikill240c 2026-09-12
        } //added by ikill240c 2026-09-12
        return instance; //added by ikill240c 2026-09-12
    } //added by ikill240c 2026-09-12

    // 0 = purely economic in recent play, 1 = purely combat-focused, 0.5 = no history / balanced.
    // //added by ikill240c 2026-09-12
    public float getAggression() { //added by ikill240c 2026-09-12
        return aggression; //added by ikill240c 2026-09-12
    } //added by ikill240c 2026-09-12

    public float getEconomyFocus() { //added by ikill240c 2026-09-12
        return economy_focus; //added by ikill240c 2026-09-12
    } //added by ikill240c 2026-09-12

    // Feeds one tick's worth of elapsed time toward the next sample. Safe to call every animate()
    // tick - internally a no-op until SAMPLE_INTERVAL_SECONDS has accumulated. `subject` is whichever
    // Player this profile is tracking (normally the human opponent, from the AI's point of view).
    // //added by ikill240c 2026-09-12
    public void update(@NonNull Player subject, float t) { //added by ikill240c 2026-09-12
        time_since_sample += t; //added by ikill240c 2026-09-12
        if (time_since_sample < SAMPLE_INTERVAL_SECONDS) //added by ikill240c 2026-09-12
            return; //added by ikill240c 2026-09-12
        float elapsed = time_since_sample; //added by ikill240c 2026-09-12
        time_since_sample = 0f; //added by ikill240c 2026-09-12

        int units_killed = subject.getUnitsKilled(); //added by ikill240c 2026-09-12
        int units_lost = subject.getUnitsLost(); //added by ikill240c 2026-09-12
        int buildings_destroyed = subject.getBuildingsDestroyed(); //added by ikill240c 2026-09-12
        int buildings_lost = subject.getBuildingsLost(); //added by ikill240c 2026-09-12
        int harvested = subject.getTreeHarvested() + subject.getRockHarvested() + subject.getIronHarvested() //added by ikill240c 2026-09-12
                + subject.getRubberHarvested(); //added by ikill240c 2026-09-12

        if (prev_units_killed < 0) { //added by ikill240c 2026-09-12
            // First sample for this subject - just prime the snapshot, nothing to compute a rate
            // from yet. //added by ikill240c 2026-09-12
            prev_units_killed = units_killed; //added by ikill240c 2026-09-12
            prev_units_lost = units_lost; //added by ikill240c 2026-09-12
            prev_buildings_destroyed = buildings_destroyed; //added by ikill240c 2026-09-12
            prev_buildings_lost = buildings_lost; //added by ikill240c 2026-09-12
            prev_harvested = harvested; //added by ikill240c 2026-09-12
            return; //added by ikill240c 2026-09-12
        }

        // "Combat activity" counts the player's own losses alongside kills/destructions dealt,
        // deliberately not just kills - a player who's aggressively engaging and losing units is
        // still playing an aggressive style, not an economic one. //added by ikill240c 2026-09-12
        int combat_delta = Math.max(0, units_killed - prev_units_killed) //added by ikill240c 2026-09-12
                + Math.max(0, units_lost - prev_units_lost) //added by ikill240c 2026-09-12
                + Math.max(0, buildings_destroyed - prev_buildings_destroyed) //added by ikill240c 2026-09-12
                + Math.max(0, buildings_lost - prev_buildings_lost); //added by ikill240c 2026-09-12
        int harvest_delta = Math.max(0, harvested - prev_harvested); //added by ikill240c 2026-09-12

        prev_units_killed = units_killed; //added by ikill240c 2026-09-12
        prev_units_lost = units_lost; //added by ikill240c 2026-09-12
        prev_buildings_destroyed = buildings_destroyed; //added by ikill240c 2026-09-12
        prev_buildings_lost = buildings_lost; //added by ikill240c 2026-09-12
        prev_harvested = harvested; //added by ikill240c 2026-09-12

        if (combat_delta == 0 && harvest_delta == 0) //added by ikill240c 2026-09-12
            return; // nothing happened this window (e.g. game paused) - don't dilute the EMA with a
                     // meaningless neutral sample //added by ikill240c 2026-09-12

        float combat_rate = combat_delta / elapsed; //added by ikill240c 2026-09-12
        float harvest_rate = harvest_delta / elapsed; //added by ikill240c 2026-09-12
        // Ratio rather than absolute rates, so this reads the same whether sampled from a slow or
        // fast game speed, or from a player who's simply been playing for a long time.
        // //added by ikill240c 2026-09-12
        float aggression_sample = combat_rate / (combat_rate + harvest_rate); //added by ikill240c 2026-09-12

        aggression = aggression + SAMPLE_WEIGHT * (aggression_sample - aggression); //added by ikill240c 2026-09-12
        economy_focus = economy_focus + SAMPLE_WEIGHT * ((1f - aggression_sample) - economy_focus); //added by ikill240c 2026-09-12
        aggression = Math.clamp(aggression, 0f, 1f); //added by ikill240c 2026-09-12
        economy_focus = Math.clamp(economy_focus, 0f, 1f); //added by ikill240c 2026-09-12
        save(); //added by ikill240c 2026-09-12
    } //added by ikill240c 2026-09-12

    private @NonNull Path file() { //added by ikill240c 2026-09-12
        return Renderer.getLocalInput().getGameDir().resolve(Globals.getPlayerStyleProfileFileName()); //added by ikill240c 2026-09-12
    } //added by ikill240c 2026-09-12

    private void load() { //added by ikill240c 2026-09-12
        Path file = file(); //added by ikill240c 2026-09-12
        if (!Files.exists(file)) //added by ikill240c 2026-09-12
            return; // no history yet - keep the neutral defaults //added by ikill240c 2026-09-12
        Properties props = new Properties(); //added by ikill240c 2026-09-12
        try (InputStream in = Files.newInputStream(file)) { //added by ikill240c 2026-09-12
            props.load(in); //added by ikill240c 2026-09-12
        } catch (IOException e) { //added by ikill240c 2026-09-12
            logger.log(Level.WARNING, "Failed to read player style profile from " + file + "; using default.", e); //added by ikill240c 2026-09-12
            return; //added by ikill240c 2026-09-12
        } //added by ikill240c 2026-09-12
        try { //added by ikill240c 2026-09-12
            aggression = Math.clamp( //added by ikill240c 2026-09-12
                    Float.parseFloat(props.getProperty("aggression", Float.toString(DEFAULT_AXIS_VALUE))), 0f, 1f); //added by ikill240c 2026-09-12
            economy_focus = Math.clamp( //added by ikill240c 2026-09-12
                    Float.parseFloat(props.getProperty("economy_focus", Float.toString(DEFAULT_AXIS_VALUE))), 0f, //added by ikill240c 2026-09-12
                    1f); //added by ikill240c 2026-09-12
        } catch (NumberFormatException e) { //added by ikill240c 2026-09-12
            logger.log(Level.WARNING, "Invalid value in " + file + "; using defaults.", e); //added by ikill240c 2026-09-12
            aggression = DEFAULT_AXIS_VALUE; //added by ikill240c 2026-09-12
            economy_focus = DEFAULT_AXIS_VALUE; //added by ikill240c 2026-09-12
        } //added by ikill240c 2026-09-12
    } //added by ikill240c 2026-09-12

    private void save() { //added by ikill240c 2026-09-12
        Properties props = new Properties(); //added by ikill240c 2026-09-12
        props.setProperty("aggression", Float.toString(aggression)); //added by ikill240c 2026-09-12
        props.setProperty("economy_focus", Float.toString(economy_focus)); //added by ikill240c 2026-09-12
        Path file = file(); //added by ikill240c 2026-09-12
        try (OutputStream out = Files.newOutputStream(file)) { //added by ikill240c 2026-09-12
            props.store(out, Instant.now().toString()); //added by ikill240c 2026-09-12
        } catch (IOException e) { //added by ikill240c 2026-09-12
            logger.log(Level.WARNING, "Failed to write player style profile to " + file, e); //added by ikill240c 2026-09-12
        } //added by ikill240c 2026-09-12
    } //added by ikill240c 2026-09-12
} //added by ikill240c 2026-09-12
