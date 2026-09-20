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
import java.util.Random; //added by ikill240c 2026-09-12
import java.util.logging.Level; //added by ikill240c 2026-09-12
import java.util.logging.Logger; //added by ikill240c 2026-09-12

// Stage 2 of play-style-aware AI: a classic epsilon-greedy contextual bandit. The "context" is which
// PlayerStyleContext bucket the human opponent currently falls into (see PlayerStyleProfile +
// PlayerStyleContext); the "arms" are the fixed set of AiBehaviorProfile postures. Over many
// matches, this learns which posture tends to win against players in each style bucket, without
// needing a training pipeline, labeled data, or anything beyond the match's own win/loss result -
// the exploration/exploitation trade-off epsilon-greedy provides is what makes a bandit the right
// tool here rather than just "always pick whatever won last time" (which would get stuck on the
// first arm that happens to win once, including by luck).
//
// This is still not a trained model in the deep-learning sense - no gradients, no features beyond
// one discrete context - but it is genuinely learning (accumulating and acting on statistics across
// matches), which distinguishes it from AdaptiveAIProfile's fixed-formula EMA rebalancer. It is the
// natural extension point if a real model is added later: the context could become a feature vector
// instead of one enum, and arm selection could become an argmax over a learned value function
// instead of a lookup table - the calling convention (selectArm(context) / recordOutcome(context,
// arm, reward)) wouldn't need to change. //added by ikill240c 2026-09-12
public final class AiBehaviorBandit { //added by ikill240c 2026-09-12
    private static final Logger logger = Logger.getLogger(AiBehaviorBandit.class.getName()); //added by ikill240c 2026-09-12

    // Chance of picking a uniformly random arm instead of the current best-known one for this
    // context, so the bandit keeps sampling arms it hasn't settled on yet rather than permanently
    // committing to whichever happened to win first. //added by ikill240c 2026-09-12
    private static final float EPSILON = 0.2f; //added by ikill240c 2026-09-12
    // Optimistic initial value for an arm with zero recorded matches in a context: higher than any
    // real win-rate can average out to be, so every arm gets tried at least once per context before
    // exploitation kicks in, on top of epsilon's ongoing exploration. //added by ikill240c 2026-09-12
    private static final float OPTIMISTIC_INITIAL_VALUE = 1.0f; //added by ikill240c 2026-09-12

    private static @org.jspecify.annotations.Nullable AiBehaviorBandit instance; //added by ikill240c 2026-09-12

    private final @NonNull Random random = new Random(); //added by ikill240c 2026-09-12
    // Keyed by "<context>.<arm>" -> running match count and average reward (1.0 = AI won that
    // match, 0.0 = AI lost). Flat maps rather than a 2D structure since (context, arm) pairs are a
    // small, fixed, known set - a properties file with composite keys is simplest to read/write/
    // hand-inspect. //added by ikill240c 2026-09-12
    private final java.util.Map<@NonNull String, Integer> counts = new java.util.HashMap<>(); //added by ikill240c 2026-09-12
    private final java.util.Map<@NonNull String, Float> averages = new java.util.HashMap<>(); //added by ikill240c 2026-09-12

    private AiBehaviorBandit() { //added by ikill240c 2026-09-12
    } //added by ikill240c 2026-09-12

    public static synchronized @NonNull AiBehaviorBandit get() { //added by ikill240c 2026-09-12
        if (instance == null) { //added by ikill240c 2026-09-12
            instance = new AiBehaviorBandit(); //added by ikill240c 2026-09-12
            instance.load(); //added by ikill240c 2026-09-12
        } //added by ikill240c 2026-09-12
        return instance; //added by ikill240c 2026-09-12
    } //added by ikill240c 2026-09-12

    // Picks a behavior profile for the given context: with probability EPSILON, a uniformly random
    // arm (exploration); otherwise the arm with the highest running average reward recorded so far
    // for this context, with ties (including "never tried" arms, which all share
    // OPTIMISTIC_INITIAL_VALUE) broken by enum order (exploitation). //added by ikill240c 2026-09-12
    public @NonNull AiBehaviorProfile selectArm(@NonNull PlayerStyleContext context) { //added by ikill240c 2026-09-12
        AiBehaviorProfile[] arms = AiBehaviorProfile.values(); //added by ikill240c 2026-09-12
        if (random.nextFloat() < EPSILON) //added by ikill240c 2026-09-12
            return arms[random.nextInt(arms.length)]; //added by ikill240c 2026-09-12

        AiBehaviorProfile best = arms[0]; //added by ikill240c 2026-09-12
        float best_value = averageFor(context, best); //added by ikill240c 2026-09-12
        for (int i = 1; i < arms.length; i++) { //added by ikill240c 2026-09-12
            float value = averageFor(context, arms[i]); //added by ikill240c 2026-09-12
            if (value > best_value) { //added by ikill240c 2026-09-12
                best = arms[i]; //added by ikill240c 2026-09-12
                best_value = value; //added by ikill240c 2026-09-12
            } //added by ikill240c 2026-09-12
        } //added by ikill240c 2026-09-12
        return best; //added by ikill240c 2026-09-12
    } //added by ikill240c 2026-09-12

    // Feeds one match's outcome back in - call once per match, from the same context and arm that
    // selectArm() returned for it. reward is 1.0 for a win, 0.0 for a loss; incremental running mean
    // rather than a stored history, since only the average is ever needed. //added by ikill240c 2026-09-12
    public void recordOutcome(@NonNull PlayerStyleContext context, @NonNull AiBehaviorProfile arm, float reward) { //added by ikill240c 2026-09-12
        String key = key(context, arm); //added by ikill240c 2026-09-12
        int count = counts.getOrDefault(key, 0) + 1; //added by ikill240c 2026-09-12
        float previous_average = averages.getOrDefault(key, OPTIMISTIC_INITIAL_VALUE); //added by ikill240c 2026-09-12
        float new_average = previous_average + (reward - previous_average) / count; //added by ikill240c 2026-09-12
        counts.put(key, count); //added by ikill240c 2026-09-12
        averages.put(key, new_average); //added by ikill240c 2026-09-12
        save(); //added by ikill240c 2026-09-12
    } //added by ikill240c 2026-09-12

    private float averageFor(@NonNull PlayerStyleContext context, @NonNull AiBehaviorProfile arm) { //added by ikill240c 2026-09-12
        return averages.getOrDefault(key(context, arm), OPTIMISTIC_INITIAL_VALUE); //added by ikill240c 2026-09-12
    } //added by ikill240c 2026-09-12

    private static @NonNull String key(@NonNull PlayerStyleContext context, @NonNull AiBehaviorProfile arm) { //added by ikill240c 2026-09-12
        return context.name() + "." + arm.name(); //added by ikill240c 2026-09-12
    } //added by ikill240c 2026-09-12

    private @NonNull Path file() { //added by ikill240c 2026-09-12
        return Renderer.getLocalInput().getGameDir().resolve(Globals.getAiBehaviorBanditFileName()); //added by ikill240c 2026-09-12
    } //added by ikill240c 2026-09-12

    private void load() { //added by ikill240c 2026-09-12
        Path file = file(); //added by ikill240c 2026-09-12
        if (!Files.exists(file)) //added by ikill240c 2026-09-12
            return; // no history yet - every (context, arm) starts at the optimistic default
            //added by ikill240c 2026-09-12
        Properties props = new Properties(); //added by ikill240c 2026-09-12
        try (InputStream in = Files.newInputStream(file)) { //added by ikill240c 2026-09-12
            props.load(in); //added by ikill240c 2026-09-12
        } catch (IOException e) { //added by ikill240c 2026-09-12
            logger.log(Level.WARNING, "Failed to read AI behavior bandit stats from " + file + "; starting fresh.", //added by ikill240c 2026-09-12
                    e); //added by ikill240c 2026-09-12
            return; //added by ikill240c 2026-09-12
        } //added by ikill240c 2026-09-12
        for (PlayerStyleContext context : PlayerStyleContext.values()) { //added by ikill240c 2026-09-12
            for (AiBehaviorProfile arm : AiBehaviorProfile.values()) { //added by ikill240c 2026-09-12
                String key = key(context, arm); //added by ikill240c 2026-09-12
                String count_str = props.getProperty(key + ".count"); //added by ikill240c 2026-09-12
                String average_str = props.getProperty(key + ".average"); //added by ikill240c 2026-09-12
                if (count_str == null || average_str == null) //added by ikill240c 2026-09-12
                    continue; // never recorded - keep the optimistic default //added by ikill240c 2026-09-12
                try { //added by ikill240c 2026-09-12
                    counts.put(key, Integer.parseInt(count_str)); //added by ikill240c 2026-09-12
                    averages.put(key, Float.parseFloat(average_str)); //added by ikill240c 2026-09-12
                } catch (NumberFormatException e) { //added by ikill240c 2026-09-12
                    logger.log(Level.WARNING, "Invalid bandit stats for " + key + " in " + file + "; skipping.", e); //added by ikill240c 2026-09-12
                } //added by ikill240c 2026-09-12
            } //added by ikill240c 2026-09-12
        } //added by ikill240c 2026-09-12
    } //added by ikill240c 2026-09-12

    private void save() { //added by ikill240c 2026-09-12
        Properties props = new Properties(); //added by ikill240c 2026-09-12
        for (var entry : counts.entrySet()) { //added by ikill240c 2026-09-12
            props.setProperty(entry.getKey() + ".count", Integer.toString(entry.getValue())); //added by ikill240c 2026-09-12
        } //added by ikill240c 2026-09-12
        for (var entry : averages.entrySet()) { //added by ikill240c 2026-09-12
            props.setProperty(entry.getKey() + ".average", Float.toString(entry.getValue())); //added by ikill240c 2026-09-12
        } //added by ikill240c 2026-09-12
        Path file = file(); //added by ikill240c 2026-09-12
        try (OutputStream out = Files.newOutputStream(file)) { //added by ikill240c 2026-09-12
            props.store(out, Instant.now().toString()); //added by ikill240c 2026-09-12
        } catch (IOException e) { //added by ikill240c 2026-09-12
            logger.log(Level.WARNING, "Failed to write AI behavior bandit stats to " + file, e); //added by ikill240c 2026-09-12
        } //added by ikill240c 2026-09-12
    } //added by ikill240c 2026-09-12
} //added by ikill240c 2026-09-12
