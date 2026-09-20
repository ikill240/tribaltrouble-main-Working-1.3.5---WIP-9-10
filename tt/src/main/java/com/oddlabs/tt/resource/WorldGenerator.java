package com.oddlabs.tt.resource;

import com.oddlabs.tt.procedural.Landscape;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.Serializable;

public interface WorldGenerator extends Serializable {
    @NonNull
    // team_together and player_teams added by ikill240c: when team_together is true and
    // player_teams is non-null, generated start positions are grouped so that same-team players
    // end up adjacent to one another on the map instead of the normal fully-random assignment -
    // see Landscape.generateUnitLocations() for where this is actually applied. player_teams is
    // indexed the same way as num_players (player_teams[i] is that player's team, matching
    // PlayerInfo.getTeam()); pass null when there's no real roster to group (e.g. the decorative
    // main-menu background world), which preserves the original fully-random behavior regardless
    // of team_together's value.
    WorldInfo generate(int num_players, int initial_unit_count, float random_start_pos, boolean team_together,
            int @Nullable [] player_teams); //added by ikill240c

    Landscape.@NonNull TerrainType getTerrainType();

    int getMetersPerWorld();

    @NonNull
    FogInfo getFogInfo();
}
