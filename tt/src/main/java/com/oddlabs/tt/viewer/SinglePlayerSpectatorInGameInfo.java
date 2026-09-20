package com.oddlabs.tt.viewer; //added by ikill240c

// Lets the local player close their own slot in a singleplayer-with-AI game and spectate it
// instead of playing, per an explicit request. WorldViewer identifies a spectator session purely
// by `ingame_info instanceof SpectatorInGameInfo` (see its own constructor), so extending that
// class is what actually makes spectating take effect - the alternative of implementing
// InGameInfo directly wouldn't satisfy that check. SpectatorInGameInfo itself hardcodes
// isMultiplayer() to true, which is correct for its own original purpose (joining a real online
// match as a spectator), but wrong here - this is still a local, no-network singleplayer session,
// and reporting it as multiplayer would misclassify it for anything that branches on that (the
// stats/game-over UI, PeerHub construction, etc.). Overriding isMultiplayer() back to false is
// the one behavior this subclass needs to change. //added by ikill240c
public final class SinglePlayerSpectatorInGameInfo extends SpectatorInGameInfo { //added by ikill240c

    public SinglePlayerSpectatorInGameInfo(float random_start_position) { //added by ikill240c
        super(random_start_position); //added by ikill240c
    } //added by ikill240c

    @Override
    public boolean isMultiplayer() { //added by ikill240c
        return false; //added by ikill240c
    } //added by ikill240c
} //added by ikill240c
