package com.oddlabs.tt.player; //added by ikill240c 2026-09-12

import org.jspecify.annotations.NonNull; //added by ikill240c 2026-09-12

// A contextual bandit needs a small, discrete set of contexts to key its statistics on - a
// continuous aggression value in [0, 1] would mean every match is (almost) a brand new context with
// no accumulated statistics to learn from. Three buckets is deliberately coarse: enough to
// distinguish "plays aggressively" from "plays economically" from "no strong lean / not enough
// history yet", without fragmenting the limited number of matches any one player will contribute
// across too many contexts to ever build up meaningful statistics in each. //added by ikill240c 2026-09-12
public enum PlayerStyleContext { //added by ikill240c 2026-09-12
    LOW_AGGRESSION, //added by ikill240c 2026-09-12
    MEDIUM_AGGRESSION, //added by ikill240c 2026-09-12
    HIGH_AGGRESSION; //added by ikill240c 2026-09-12

    private static final float LOW_CEILING = 0.4f; //added by ikill240c 2026-09-12
    private static final float HIGH_FLOOR = 0.6f; //added by ikill240c 2026-09-12

    public static @NonNull PlayerStyleContext fromAggression(float aggression) { //added by ikill240c 2026-09-12
        if (aggression < LOW_CEILING) //added by ikill240c 2026-09-12
            return LOW_AGGRESSION; //added by ikill240c 2026-09-12
        if (aggression > HIGH_FLOOR) //added by ikill240c 2026-09-12
            return HIGH_AGGRESSION; //added by ikill240c 2026-09-12
        return MEDIUM_AGGRESSION; // covers both "genuinely balanced" and "no history yet (0.5)"
        //added by ikill240c 2026-09-12
    } //added by ikill240c 2026-09-12
} //added by ikill240c 2026-09-12
