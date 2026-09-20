package com.oddlabs.matchmaking; //added by ikill240c

import com.fasterxml.jackson.databind.annotation.JsonDeserialize; //added by ikill240c
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder; //added by ikill240c
import org.jspecify.annotations.NonNull; //added by ikill240c

import java.io.Serial; //added by ikill240c

// Mirrors KingOfTheIslandOptions's exact builder/Jackson pattern - see that class's own comment.
// Without this class, GameMode.SURVIVAL had a rules implementation (SurvivalModeRules) and a
// lobby pulldown entry, but no way to actually be represented as a GameModeOptions value at all -
// GameModeOptions is a SEALED interface (see its own doc comment) permitting only StandardOptions
// and KingOfTheIslandOptions, so selecting Survival had nowhere to actually go once a game
// started. //added by ikill240c
@JsonDeserialize(builder = SurvivalOptions.Builder.class) //added by ikill240c
public final class SurvivalOptions implements GameModeOptions { //added by ikill240c
    @Serial
    private static final long serialVersionUID = 1L; //added by ikill240c

    private final float wave_interval_seconds; //added by ikill240c
    private final float growth_per_wave; //added by ikill240c

    private SurvivalOptions(@NonNull Builder b) { //added by ikill240c
        this.wave_interval_seconds = b.wave_interval_seconds; //added by ikill240c
        this.growth_per_wave = b.growth_per_wave; //added by ikill240c
    } //added by ikill240c

    public static @NonNull Builder builder() { //added by ikill240c
        return new Builder(); //added by ikill240c
    } //added by ikill240c

    public static @NonNull SurvivalOptions defaults() { //added by ikill240c
        return new Builder().build(); //added by ikill240c
    } //added by ikill240c

    @Override
    public @NonNull GameMode getMode() { //added by ikill240c
        return GameMode.SURVIVAL; //added by ikill240c
    }

    public float getWaveIntervalSeconds() { //added by ikill240c
        return wave_interval_seconds; //added by ikill240c
    } //added by ikill240c

    public float getGrowthPerWave() { //added by ikill240c
        return growth_per_wave; //added by ikill240c
    } //added by ikill240c

    @JsonPOJOBuilder(buildMethodName = "build", withPrefix = "") //added by ikill240c
    public static final class Builder { //added by ikill240c
        // Defaults match SurvivalModeRules's current hardcoded constants. //added by ikill240c
        private float wave_interval_seconds = 45f; //added by ikill240c
        private float growth_per_wave = 2f; //added by ikill240c

        private Builder() { //added by ikill240c
        } //added by ikill240c

        public @NonNull Builder waveIntervalSeconds(float v) { //added by ikill240c
            this.wave_interval_seconds = v; //added by ikill240c
            return this; //added by ikill240c
        } //added by ikill240c

        public @NonNull Builder growthPerWave(float v) { //added by ikill240c
            this.growth_per_wave = v; //added by ikill240c
            return this; //added by ikill240c
        } //added by ikill240c

        public @NonNull SurvivalOptions build() { //added by ikill240c
            return new SurvivalOptions(this); //added by ikill240c
        } //added by ikill240c
    } //added by ikill240c
} //added by ikill240c
