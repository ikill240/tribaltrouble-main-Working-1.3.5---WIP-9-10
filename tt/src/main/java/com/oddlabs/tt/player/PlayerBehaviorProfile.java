package com.oddlabs.tt.player; //added by ikill240c 2026-09-13

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.oddlabs.tt.global.Globals;
import com.oddlabs.tt.render.Renderer;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

// Segmented, multi-signal attack-pattern profile for Adaptive AI - the richer sibling of
// AdaptiveAIProfile (which only tracks one overall skill_rating scalar). This is what
// AdvancedAI actually means when it talks about "learning from how you play": a small set of
// named, human-readable statistics, tracked SEPARATELY per (opponent race, map-size bucket,
// difficulty) context, each updated online (as running counts or exponential moving averages)
// every time a new observation comes in, and read back to bias a handful of concrete AI
// decisions (see AdvancedAI.vulnerabilityWeight()/rushDefenseMultiplier()/
// attackForceMultiplier()/standingGarrisonMultiplier()).
//
// Calling this a "learning model" is fair in the narrow sense that it is: (a) a persistent set
// of parameters, (b) updated online from observed play, (c) conditioned on context (the segment
// key), and (d) used to drive a policy (the AI's defensive/offensive tuning). It is NOT a neural
// network, has no training loop, gradient descent, or generalization across unseen contexts -
// each segment is learned entirely independently, so "Vikings, Large map, Hard" and "Vikings,
// Large map, Normal" share nothing even though a real model would generalize between them. Be
// upfront about that distinction if asked. //added by ikill240c 2026-09-13
public final class PlayerBehaviorProfile { //added by ikill240c 2026-09-13
    private static final Logger logger = Logger.getLogger(PlayerBehaviorProfile.class.getName()); //added by ikill240c 2026-09-13
    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT); //added by ikill240c 2026-09-13
    private static final TypeReference<Map<String, Segment>> SEGMENT_MAP_TYPE = new TypeReference<>() { //added by ikill240c 2026-09-13
    };

    // Sentinel meaning "no first-attack time observed yet in this segment" - see AdaptiveAIProfile
    // for why this pattern (rather than e.g. -1) is used: it needs to compare as "false" against
    // the early-rush threshold with no special-casing. //added by ikill240c 2026-09-13
    private static final float NO_FIRST_ATTACK_DATA = 99999f; //added by ikill240c 2026-09-13
    private static final float EARLY_RUSH_THRESHOLD_SECONDS = 240f; //added by ikill240c 2026-09-13
    private static final float FIRST_ATTACK_LEARNING_RATE = 0.3f; //added by ikill240c 2026-09-13
    private static final float FORCE_SIZE_LEARNING_RATE = 0.25f; //added by ikill240c 2026-09-13

    // One independent bundle of statistics per (race, map size, difficulty) context. Public,
    // mutable fields - this is a private-to-the-install save file, not a network-facing contract,
    // so there's no need for the immutable-builder ceremony used by classes like WorldConfig that
    // do cross the network. Jackson (de)serializes public fields with zero extra annotations.
    // //added by ikill240c 2026-09-13
    public static final class Segment { //added by ikill240c 2026-09-13
        public float first_attack_seconds = NO_FIRST_ATTACK_DATA; //added by ikill240c 2026-09-13
        public float quarters_hits = 0f; //added by ikill240c 2026-09-13
        public float armory_hits = 0f; //added by ikill240c 2026-09-13
        public float outpost_hits = 0f; //added by ikill240c 2026-09-13
        // EMA of how many enemy units were present in a single detected attack wave - "how big is
        // the army they send", regardless of its exact unit-tier makeup (the game's warrior tiers
        // are a strict upgrade progression, not a rock-paper-scissors triangle, and Unit doesn't
        // expose a clean per-instance tier accessor to arbitrary observed enemies, so tracking
        // "which tier they favor" isn't reliably implementable here - force size and force
        // strength are the practically useful stand-in). //added by ikill240c 2026-09-13
        public float avg_force_size = 0f; //added by ikill240c 2026-09-13
        // EMA of the same wave's total combat score (getUnitScore() sum) - captures army quality
        // (upgraded/veteran units) on top of avg_force_size's raw headcount.
        // //added by ikill240c 2026-09-13
        public float avg_force_score = 0f; //added by ikill240c 2026-09-13
        // Lifetime count of distinct attack waves observed in this segment (cooldown-grouped at the
        // AdvancedAI call site), and total minutes this segment was actually being played, so
        // wave_count / total_minutes gives attacks-per-minute - "how often do they attack".
        // //added by ikill240c 2026-09-13
        public int wave_count = 0; //added by ikill240c 2026-09-13
        public float total_minutes_observed = 0f; //added by ikill240c 2026-09-13
        public int matches_played = 0; //added by ikill240c 2026-09-13
    }

    private static @Nullable PlayerBehaviorProfile instance; //added by ikill240c 2026-09-13

    private final @NonNull Map<String, Segment> segments; //added by ikill240c 2026-09-13

    private PlayerBehaviorProfile(@NonNull Map<String, Segment> segments) { //added by ikill240c 2026-09-13
        this.segments = segments; //added by ikill240c 2026-09-13
    }

    public static synchronized @NonNull PlayerBehaviorProfile get() { //added by ikill240c 2026-09-13
        if (instance == null) { //added by ikill240c 2026-09-13
            instance = new PlayerBehaviorProfile(load()); //added by ikill240c 2026-09-13
        } //added by ikill240c 2026-09-13
        return instance; //added by ikill240c 2026-09-13
    } //added by ikill240c 2026-09-13

    // Clears every learned per-segment stat (attack timing, target preference, force size, etc.)
    // and immediately saves, so a "reset adaptive AI" action clears both halves of what the AI has
    // learned about this player - this segmented behavioral data alongside AdaptiveAIProfile's
    // single overall skill rating - rather than only resetting the seed while this richer,
    // in-match-behavior-biasing data silently survives untouched. //added by ikill240c
    public synchronized void reset() { //added by ikill240c
        segments.clear(); //added by ikill240c
        save(); //added by ikill240c
    }

    // Builds the segment key AdvancedAI looks its context up under. Exposed as a static helper
    // (rather than requiring callers to format the string themselves) so the key format only
    // needs to be correct in one place. race/map_size/difficulty are all plain lowercase words
    // (e.g. "vikings"/"medium"/"hard") - see AdvancedAI.opponentRaceLabel()/mapSizeLabel()/
    // difficultyLabel() for where they come from. //added by ikill240c 2026-09-13
    public static @NonNull String segmentKey(@NonNull String race, @NonNull String map_size, @NonNull String difficulty) { //added by ikill240c 2026-09-13
        return race + "|" + map_size + "|" + difficulty; //added by ikill240c 2026-09-13
    } //added by ikill240c 2026-09-13

    private @NonNull Segment segment(@NonNull String key) { //added by ikill240c 2026-09-13
        return segments.computeIfAbsent(key, k -> new Segment()); //added by ikill240c 2026-09-13
    } //added by ikill240c 2026-09-13

    // --- recording (all save immediately - see the class comment on AdaptiveAIProfile's sibling
    // file for why: this is meant to survive an unclean exit, not just a clean one).
    // //added by ikill240c 2026-09-13

    public void recordFirstAttackTime(@NonNull String key, float seconds) { //added by ikill240c 2026-09-13
        Segment s = segment(key); //added by ikill240c 2026-09-13
        if (s.first_attack_seconds >= NO_FIRST_ATTACK_DATA) { //added by ikill240c 2026-09-13
            s.first_attack_seconds = seconds; //added by ikill240c 2026-09-13
        } else { //added by ikill240c 2026-09-13
            s.first_attack_seconds += FIRST_ATTACK_LEARNING_RATE * (seconds - s.first_attack_seconds); //added by ikill240c 2026-09-13
        } //added by ikill240c 2026-09-13
        save(); //added by ikill240c 2026-09-13
    } //added by ikill240c 2026-09-13

    public void recordAttackWave(@NonNull String key, @NonNull String kind, int force_size, int force_score) { //added by ikill240c 2026-09-13
        Segment s = segment(key); //added by ikill240c 2026-09-13
        switch (kind) { //added by ikill240c 2026-09-13
            case "quarters" -> s.quarters_hits++; //added by ikill240c 2026-09-13
            case "armory" -> s.armory_hits++; //added by ikill240c 2026-09-13
            case "outpost" -> s.outpost_hits++; //added by ikill240c 2026-09-13
            default -> logger.warning("Unknown attack-target kind: " + kind); //added by ikill240c 2026-09-13
        } //added by ikill240c 2026-09-13
        if (s.wave_count == 0) { //added by ikill240c 2026-09-13
            s.avg_force_size = force_size; //added by ikill240c 2026-09-13
            s.avg_force_score = force_score; //added by ikill240c 2026-09-13
        } else { //added by ikill240c 2026-09-13
            s.avg_force_size += FORCE_SIZE_LEARNING_RATE * (force_size - s.avg_force_size); //added by ikill240c 2026-09-13
            s.avg_force_score += FORCE_SIZE_LEARNING_RATE * (force_score - s.avg_force_score); //added by ikill240c 2026-09-13
        } //added by ikill240c 2026-09-13
        s.wave_count++; //added by ikill240c 2026-09-13
        save(); //added by ikill240c 2026-09-13
    } //added by ikill240c 2026-09-13

    // Called once per match (from AdvancedAI, at game-over) with how long that match lasted, so
    // getAttacksPerMinute() has an honest denominator - without this, wave_count alone can't tell
    // "attacks constantly in short games" apart from "attacks rarely in long games".
    // //added by ikill240c 2026-09-13
    public void recordMatchExposure(@NonNull String key, float minutes_played) { //added by ikill240c 2026-09-13
        Segment s = segment(key); //added by ikill240c 2026-09-13
        s.total_minutes_observed += minutes_played; //added by ikill240c 2026-09-13
        s.matches_played++; //added by ikill240c 2026-09-13
        save(); //added by ikill240c 2026-09-13
    } //added by ikill240c 2026-09-13

    // --- queries. Every one of these is safe to call on a segment with zero data and returns a
    // neutral default (no bias) rather than a divide-by-zero or a false pattern.
    // //added by ikill240c 2026-09-13

    public boolean isEarlyRusher(@NonNull String key) { //added by ikill240c 2026-09-13
        return segment(key).first_attack_seconds < EARLY_RUSH_THRESHOLD_SECONDS; //added by ikill240c 2026-09-13
    } //added by ikill240c 2026-09-13

    private float totalHits(@NonNull Segment s) { //added by ikill240c 2026-09-13
        return s.quarters_hits + s.armory_hits + s.outpost_hits; //added by ikill240c 2026-09-13
    } //added by ikill240c 2026-09-13

    public float getQuartersHitShare(@NonNull String key) { //added by ikill240c 2026-09-13
        Segment s = segment(key); //added by ikill240c 2026-09-13
        return totalHits(s) <= 0f ? (1f / 3f) : s.quarters_hits / totalHits(s); //added by ikill240c 2026-09-13
    } //added by ikill240c 2026-09-13

    public float getArmoryHitShare(@NonNull String key) { //added by ikill240c 2026-09-13
        Segment s = segment(key); //added by ikill240c 2026-09-13
        return totalHits(s) <= 0f ? (1f / 3f) : s.armory_hits / totalHits(s); //added by ikill240c 2026-09-13
    } //added by ikill240c 2026-09-13

    public float getOutpostHitShare(@NonNull String key) { //added by ikill240c 2026-09-13
        Segment s = segment(key); //added by ikill240c 2026-09-13
        return totalHits(s) <= 0f ? (1f / 3f) : s.outpost_hits / totalHits(s); //added by ikill240c 2026-09-13
    } //added by ikill240c 2026-09-13

    // 1 with no data. Grows above 1 the bigger this player's attack waves have historically been in
    // this segment, so AdvancedAI can scale up its own attack-force size to avoid getting
    // outnumbered by a player who reliably masses large armies. Capped to keep one freak-large wave
    // from making the AI commit its entire army to every attack. //added by ikill240c 2026-09-13
    public float getForceSizeMultiplier(@NonNull String key, float reference_force_size) { //added by ikill240c 2026-09-13
        Segment s = segment(key); //added by ikill240c 2026-09-13
        if (s.wave_count == 0 || reference_force_size <= 0f) //added by ikill240c 2026-09-13
            return 1f; //added by ikill240c 2026-09-13
        return Math.clamp(s.avg_force_size / reference_force_size, 1f, 2f); //added by ikill240c 2026-09-13
    } //added by ikill240c 2026-09-13

    public float getAttacksPerMinute(@NonNull String key) { //added by ikill240c 2026-09-13
        Segment s = segment(key); //added by ikill240c 2026-09-13
        if (s.total_minutes_observed <= 0f) //added by ikill240c 2026-09-13
            return 0f; //added by ikill240c 2026-09-13
        return s.wave_count / s.total_minutes_observed; //added by ikill240c 2026-09-13
    } //added by ikill240c 2026-09-13

    private @NonNull Path file() { //added by ikill240c 2026-09-13
        return Renderer.getLocalInput().getGameDir().resolve(Globals.getPlayerBehaviorProfileFileName()); //added by ikill240c 2026-09-13
    } //added by ikill240c 2026-09-13

    private static @NonNull Map<String, Segment> load() { //added by ikill240c 2026-09-13
        Path file = Renderer.getLocalInput().getGameDir().resolve(Globals.getPlayerBehaviorProfileFileName()); //added by ikill240c 2026-09-13
        if (!Files.exists(file)) //added by ikill240c 2026-09-13
            return new HashMap<>(); // no history yet - start with an empty set of segments //added by ikill240c 2026-09-13
        try { //added by ikill240c 2026-09-13
            Map<String, Segment> loaded = MAPPER.readValue(file.toFile(), SEGMENT_MAP_TYPE); //added by ikill240c 2026-09-13
            return loaded != null ? loaded : new HashMap<>(); //added by ikill240c 2026-09-13
        } catch (IOException e) { //added by ikill240c 2026-09-13
            logger.log(Level.WARNING, "Failed to read player behavior profile from " + file + "; starting fresh.", e); //added by ikill240c 2026-09-13
            return new HashMap<>(); //added by ikill240c 2026-09-13
        } //added by ikill240c 2026-09-13
    } //added by ikill240c 2026-09-13

    private void save() { //added by ikill240c 2026-09-13
        Path file = file(); //added by ikill240c 2026-09-13
        try { //added by ikill240c 2026-09-13
            MAPPER.writeValue(file.toFile(), segments); //added by ikill240c 2026-09-13
        } catch (IOException e) { //added by ikill240c 2026-09-13
            logger.log(Level.WARNING, "Failed to write player behavior profile to " + file, e); //added by ikill240c 2026-09-13
        } //added by ikill240c 2026-09-13
    } //added by ikill240c 2026-09-13
} //added by ikill240c 2026-09-13
