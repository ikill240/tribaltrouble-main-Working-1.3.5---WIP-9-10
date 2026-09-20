package com.oddlabs.matchmaking;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import org.jspecify.annotations.NonNull;

import java.io.Serial;

// Mirrors StandardOptions's exact builder/Jackson pattern. //added by ikill240c
@JsonDeserialize(builder = KingOfTheIslandOptions.Builder.class)
public final class KingOfTheIslandOptions implements GameModeOptions {
    @Serial
    private static final long serialVersionUID = 1L;

    private final float capture_radius; //added by ikill240c
    private final float hold_seconds_to_win; //added by ikill240c

    private KingOfTheIslandOptions(@NonNull Builder b) {
        this.capture_radius = b.capture_radius;
        this.hold_seconds_to_win = b.hold_seconds_to_win;
    }

    public static @NonNull Builder builder() {
        return new Builder();
    }

    public static @NonNull KingOfTheIslandOptions defaults() {
        return new Builder().build();
    }

    @Override
    public @NonNull GameMode getMode() {
        return GameMode.KING_OF_THE_ISLAND;
    }

    public float getCaptureRadius() { //added by ikill240c
        return capture_radius;
    }

    public float getHoldSecondsToWin() { //added by ikill240c
        return hold_seconds_to_win;
    }

    @JsonPOJOBuilder(buildMethodName = "build", withPrefix = "")
    public static final class Builder {
        // Defaults match KingOfTheIslandModeRules's current hardcoded constants. //added by ikill240c
        private float capture_radius = 15f;
        private float hold_seconds_to_win = 120f;

        private Builder() {
        }

        public @NonNull Builder captureRadius(float v) { //added by ikill240c
            this.capture_radius = v;
            return this;
        }

        public @NonNull Builder holdSecondsToWin(float v) { //added by ikill240c
            this.hold_seconds_to_win = v;
            return this;
        }

        public @NonNull KingOfTheIslandOptions build() {
            return new KingOfTheIslandOptions(this);
        }
    }
}
