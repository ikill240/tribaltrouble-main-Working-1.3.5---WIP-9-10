package com.oddlabs.tt.player; //added by ikill240c 2026-09-12

// Stage 2 of play-style-aware AI: a small, fixed set of named behavior profiles ("arms" in the
// bandit sense - see AiBehaviorBandit) that AdvancedAI can be nudged toward. Each is expressed
// purely as multipliers on knobs AdvancedAI already has (attack-stall timing, the warrior count
// required to break a stalemate), rather than new mechanics, so choosing a profile can't put the
// AI into an untested code path - only change how aggressively it uses paths that already exist
// and are already tuned per-difficulty. //added by ikill240c 2026-09-12
public enum AiBehaviorProfile { //added by ikill240c 2026-09-12
    // Control arm: no change from whatever Stage 1's style-based adjustment already produces.
    // Always in the running so the bandit has a safe baseline to compare the others against.
    // //added by ikill240c 2026-09-12
    BALANCED(1.0f, 1.0f), //added by ikill240c 2026-09-12
    // Attacks sooner off a smaller idle army - a rush/pressure posture. //added by ikill240c 2026-09-12
    AGGRESSIVE(0.6f, 0.6f), //added by ikill240c 2026-09-12
    // Waits for a substantially bigger army before committing - a boom/turtle posture.
    // //added by ikill240c 2026-09-12
    ECONOMIC(1.5f, 1.5f), //added by ikill240c 2026-09-12
    // Frequent small commitments rather than one big army - harasses instead of trading evenly.
    // //added by ikill240c 2026-09-12
    HARASSER(0.75f, 0.4f); //added by ikill240c 2026-09-12

    // Multiplies AdvancedAI.getStyleAdjustedStallSeconds()'s result - lower means attacking sooner.
    // //added by ikill240c 2026-09-12
    private final float stall_multiplier; //added by ikill240c 2026-09-12
    // Multiplies AdvancedAI.MIN_WARRIORS_FOR_STALL_ATTACK - lower means willing to commit smaller
    // forces once stalled. //added by ikill240c 2026-09-12
    private final float min_warriors_multiplier; //added by ikill240c 2026-09-12

    AiBehaviorProfile(float stall_multiplier, float min_warriors_multiplier) { //added by ikill240c 2026-09-12
        this.stall_multiplier = stall_multiplier; //added by ikill240c 2026-09-12
        this.min_warriors_multiplier = min_warriors_multiplier; //added by ikill240c 2026-09-12
    } //added by ikill240c 2026-09-12

    public float getStallMultiplier() { //added by ikill240c 2026-09-12
        return stall_multiplier; //added by ikill240c 2026-09-12
    } //added by ikill240c 2026-09-12

    public float getMinWarriorsMultiplier() { //added by ikill240c 2026-09-12
        return min_warriors_multiplier; //added by ikill240c 2026-09-12
    } //added by ikill240c 2026-09-12
} //added by ikill240c 2026-09-12
