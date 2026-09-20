package com.oddlabs.matchmaking;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.jspecify.annotations.NonNull;

import java.io.Serializable;

/**
 * Mode-specific options. Each game mode has its own implementation with its own fields.
 * <p>
 * Example: {@link StandardOptions} has {@code rated}. A future {@code WaveDefenseOptions} would have
 * {@code wavesToWin}, {@code difficulty}, etc. Fields are per-mode, not shared across modes.
 * <p>
 * Only {@link #getMode()} is required across implementations.
 * <p>
 * Sealed: new implementations must be listed in {@code permits} below. This lets the compiler verify that any
 * {@code switch (options) {...}} statement handles every mode.
 * <p>
 * JSON discriminator is the existing {@code mode} field; no synthetic {@code @type} property is added. Each subtype
 * must be listed in {@link JsonSubTypes} with the matching enum name.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "mode", include = JsonTypeInfo.As.EXISTING_PROPERTY)
@JsonSubTypes({
        @JsonSubTypes.Type(value = StandardOptions.class, name = "STANDARD"),
        @JsonSubTypes.Type(value = KingOfTheIslandOptions.class, name = "KING_OF_THE_ISLAND"), //added by ikill240c
        @JsonSubTypes.Type(value = SurvivalOptions.class, name = "SURVIVAL") //added by ikill240c
})
public sealed interface GameModeOptions extends Serializable permits StandardOptions, KingOfTheIslandOptions, //added by ikill240c
        SurvivalOptions { //added by ikill240c
    @NonNull
    GameMode getMode();
}
