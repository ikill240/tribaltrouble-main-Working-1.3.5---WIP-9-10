package com.oddlabs.tt.viewer;

// Was final; opened up so SinglePlayerSpectatorInGameInfo (a local, no-network singleplayer
// variant - see its own comment) can extend it and inherit WorldViewer's `instanceof
// SpectatorInGameInfo` spectator-detection check, which is the actual mechanism that makes
// spectating take effect. //added by ikill240c
public class SpectatorInGameInfo extends DefaultInGameInfo { //added by ikill240c
    private final float random_start_position;

    public SpectatorInGameInfo(float random_start_position) {
        this.random_start_position = random_start_position;
    }

    @Override
    public boolean isMultiplayer() {
        return true;
    }

    @Override
    public float getRandomStartPosition() {
        return random_start_position;
    }
}
