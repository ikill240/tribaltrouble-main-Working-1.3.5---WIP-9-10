package com.oddlabs.tt.player; //added by ikill240c 2026-09-12 00:30

import com.oddlabs.tt.global.Globals; //added by ikill240c 2026-09-12 00:30
import com.oddlabs.tt.render.Renderer; //added by ikill240c 2026-09-12 00:30
import org.jspecify.annotations.NonNull; //added by ikill240c 2026-09-12 00:30

import java.io.IOException; //added by ikill240c 2026-09-12 00:30
import java.io.InputStream; //added by ikill240c 2026-09-12 00:30
import java.io.OutputStream; //added by ikill240c 2026-09-12 00:30
import java.nio.file.Files; //added by ikill240c 2026-09-12 00:30
import java.nio.file.Path; //added by ikill240c 2026-09-12 00:30
import java.time.Instant; //added by ikill240c 2026-09-12 00:30
import java.util.Properties; //added by ikill240c 2026-09-12 00:30
import java.util.logging.Level; //added by ikill240c 2026-09-12 00:30
import java.util.logging.Logger; //added by ikill240c 2026-09-12 00:30

// Persisted, single-number estimate of how the human player has historically fared against the AI,
// used only by the "Adaptive AI difficulty" toggle (WorldParameters.isAdaptiveAiEnabled()) to seed a
// new adaptive AI's STARTING difficulty tier. This class deliberately does only that one job now -
// the richer, segmented (per race/map-size/difficulty) attack-pattern learning (first-attack timing,
// which building gets hit, attack force size, attack frequency) lives in the sibling class
// PlayerBehaviorProfile, which is what actually biases AdvancedAI's in-match behaviour. Splitting
// them keeps "one overall number that seeds a starting tier" and "many segmented behavioural stats"
// as two clearly separate concerns instead of one file doing both. //added by ikill240c 2026-09-13
//
// This is a small heuristic rebalancer, not a trained model - there is no training pipeline, feature
// store, or labeled dataset anywhere in this codebase to learn from, so "adaptive" here means "seeded
// by the player's own win/loss history, then self-tuned live within a match" (see
// AdvancedAI.nodeAdaptiveDifficulty()), not literal machine learning. Calling it "AI" in the UI refers
// to the game's AI opponent, not to any ML technique used to adjust it.
//
// skill_rating lives on [0, 1]: 0 means the player has struggled even against Easy AI, 1 means they've
// been dominating Hard AI, 0.5 (the default, matching a player with no history yet) maps to Normal.
// Updated once per match, in the direction of the result, using a simple exponential-moving-average
// step (LEARNING_RATE) rather than a hard win/loss counter, so a single unusual match (e.g. an early
// disconnect, or a stomp against a much weaker/stronger opponent) can't swing the seed too far on its
// own, but a consistent trend still shifts it within a handful of games. //added by ikill240c 2026-09-12 00:30
public final class AdaptiveAIProfile { //added by ikill240c 2026-09-12 00:30
    private static final Logger logger = Logger.getLogger(AdaptiveAIProfile.class.getName()); //added by ikill240c 2026-09-12 00:30

    private static final float DEFAULT_SKILL_RATING = 0.5f; //added by ikill240c 2026-09-12 00:30
    private static final float LEARNING_RATE = 0.15f; //added by ikill240c 2026-09-12 00:30
    // Below this rating, adaptive AI seeds at Easy; above the upper bound, it seeds at Hard; in
    // between, Normal. Kept as named constants rather than a straight 1/3-2/3 split so the bands can be
    // retuned independently of the EMA step size above. //added by ikill240c 2026-09-12 00:30
    private static final float EASY_SEED_CEILING = 0.35f; //added by ikill240c 2026-09-12 00:30
    private static final float HARD_SEED_FLOOR = 0.65f; //added by ikill240c 2026-09-12 00:30

    private static @org.jspecify.annotations.Nullable AdaptiveAIProfile instance; //added by ikill240c 2026-09-12 00:30

    private float skill_rating = DEFAULT_SKILL_RATING; //added by ikill240c 2026-09-12 00:30

    private AdaptiveAIProfile() { //added by ikill240c 2026-09-12 00:30
    } //added by ikill240c 2026-09-12 00:30

    // Lazily loaded singleton - there's exactly one human player per client, so one profile per
    // installation (per Steam account, via Globals' steamPrefixed()) is the right scope for this
    // single overall number. Segmented data lives in PlayerBehaviorProfile instead.
    // //added by ikill240c 2026-09-12 00:30
    public static synchronized @NonNull AdaptiveAIProfile get() { //added by ikill240c 2026-09-12 00:30
        if (instance == null) { //added by ikill240c 2026-09-12 00:30
            instance = new AdaptiveAIProfile(); //added by ikill240c 2026-09-12 00:30
            instance.load(); //added by ikill240c 2026-09-12 00:30
        } //added by ikill240c 2026-09-12 00:30
        return instance; //added by ikill240c 2026-09-12 00:30
    } //added by ikill240c 2026-09-12 00:30

    // Restores skill_rating to its fresh-install default and immediately saves, so the reset
    // persists rather than reverting the next time the profile happens to reload - a human
    // pressing "reset" expects the seed to have genuinely forgotten their history, not just look
    // reset for the current session. //added by ikill240c
    public synchronized void reset() { //added by ikill240c
        skill_rating = DEFAULT_SKILL_RATING; //added by ikill240c
        save(); //added by ikill240c
    }

    public float getSkillRating() { //added by ikill240c 2026-09-12 00:30
        return skill_rating; //added by ikill240c 2026-09-12 00:30
    } //added by ikill240c 2026-09-12 00:30

    // Starting difficulty index (AdvancedAI.DIFFICULTY_EASY/NORMAL/HARD) an adaptive AI should seed
    // from before its first live rebalance check. Returns a raw int (rather than importing
    // AdvancedAI's constants, which would create a circular reference back to this class once
    // AdvancedAI calls get()) - the values below match AdvancedAI.DIFFICULTY_EASY=0/NORMAL=1/HARD=2
    // exactly; a comment at that call site cross-references this one. //added by ikill240c 2026-09-12 00:30
    public int getSeedDifficulty() { //added by ikill240c 2026-09-12 00:30
        if (skill_rating < EASY_SEED_CEILING) //added by ikill240c 2026-09-12 00:30
            return 0; // AdvancedAI.DIFFICULTY_EASY //added by ikill240c 2026-09-12 00:30
        if (skill_rating > HARD_SEED_FLOOR) //added by ikill240c 2026-09-12 00:30
            return 2; // AdvancedAI.DIFFICULTY_HARD //added by ikill240c 2026-09-12 00:30
        return 1; // AdvancedAI.DIFFICULTY_NORMAL //added by ikill240c 2026-09-12 00:30
    } //added by ikill240c 2026-09-12 00:30

    // Called once per match (from GameOverTrigger) when at least one adaptive AI opponent took part.
    // player_won is from the human's perspective: true if the local player was the one left standing.
    //added by ikill240c 2026-09-12 00:30
    public void recordMatchResult(boolean player_won) { //added by ikill240c 2026-09-12 00:30
        if (player_won) { //added by ikill240c 2026-09-12 00:30
            skill_rating += LEARNING_RATE * (1f - skill_rating); //added by ikill240c 2026-09-12 00:30
        } else { //added by ikill240c 2026-09-12 00:30
            skill_rating -= LEARNING_RATE * skill_rating; //added by ikill240c 2026-09-12 00:30
        } //added by ikill240c 2026-09-12 00:30
        skill_rating = Math.clamp(skill_rating, 0f, 1f); //added by ikill240c 2026-09-12 00:30
        save(); //added by ikill240c 2026-09-12 00:30
    } //added by ikill240c 2026-09-12 00:30

    private @NonNull Path file() { //added by ikill240c 2026-09-12 00:30
        return Renderer.getLocalInput().getGameDir().resolve(Globals.getAdaptiveAiProfileFileName()); //added by ikill240c 2026-09-12 00:30
    } //added by ikill240c 2026-09-12 00:30

    private void load() { //added by ikill240c 2026-09-12 00:30
        Path file = file(); //added by ikill240c 2026-09-12 00:30
        if (!Files.exists(file)) //added by ikill240c 2026-09-12 00:30
            return; // no history yet - keep the default rating //added by ikill240c 2026-09-12 00:30
        Properties props = new Properties(); //added by ikill240c 2026-09-12 00:30
        try (InputStream in = Files.newInputStream(file)) { //added by ikill240c 2026-09-12 00:30
            props.load(in); //added by ikill240c 2026-09-12 00:30
        } catch (IOException e) { //added by ikill240c 2026-09-12 00:30
            logger.log(Level.WARNING, "Failed to read adaptive AI profile from " + file + "; using default.", e); //added by ikill240c 2026-09-12 00:30
            return; //added by ikill240c 2026-09-12 00:30
        } //added by ikill240c 2026-09-12 00:30
        try { //added by ikill240c 2026-09-12 00:30
            skill_rating = Math.clamp( //added by ikill240c 2026-09-12 00:30
                    Float.parseFloat(props.getProperty("skill_rating", Float.toString(DEFAULT_SKILL_RATING))), 0f, //added by ikill240c 2026-09-12 00:30
                    1f); //added by ikill240c 2026-09-12 00:30
        } catch (NumberFormatException e) { //added by ikill240c 2026-09-12 00:30
            logger.log(Level.WARNING, "Invalid skill_rating in " + file + "; using default.", e); //added by ikill240c 2026-09-12 00:30
            skill_rating = DEFAULT_SKILL_RATING; //added by ikill240c 2026-09-12 00:30
        } //added by ikill240c 2026-09-12 00:30
    } //added by ikill240c 2026-09-12 00:30

    private void save() { //added by ikill240c 2026-09-12 00:30
        Properties props = new Properties(); //added by ikill240c 2026-09-12 00:30
        props.setProperty("skill_rating", Float.toString(skill_rating)); //added by ikill240c 2026-09-12 00:30
        Path file = file(); //added by ikill240c 2026-09-12 00:30
        try (OutputStream out = Files.newOutputStream(file)) { //added by ikill240c 2026-09-12 00:30
            props.store(out, Instant.now().toString()); //added by ikill240c 2026-09-12 00:30
        } catch (IOException e) { //added by ikill240c 2026-09-12 00:30
            logger.log(Level.WARNING, "Failed to write adaptive AI profile to " + file, e); //added by ikill240c 2026-09-12 00:30
        } //added by ikill240c 2026-09-12 00:30
    } //added by ikill240c 2026-09-12 00:30
} //added by ikill240c 2026-09-12 00:30
