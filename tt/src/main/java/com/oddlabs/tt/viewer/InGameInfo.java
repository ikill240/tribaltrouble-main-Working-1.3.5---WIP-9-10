package com.oddlabs.tt.viewer;

import com.oddlabs.tt.delegate.GameStatsDelegate;
import com.oddlabs.tt.delegate.InGameMainMenu;
import com.oddlabs.tt.gui.Group;

public interface InGameInfo {
    void addGUI(WorldViewer viewer, InGameMainMenu menu, Group game_infos);

    void addGameOverGUI(WorldViewer viewer, GameStatsDelegate delegate, int header_y, Group buttons);

    void abort(WorldViewer viewer);

    void close(WorldViewer viewer);

    boolean isMultiplayer();

    boolean isRated();

    float getRandomStartPosition();

    // Lets a game mode swap which AI plays a slot when the world is created. Default: unchanged (skirmish,
    // multiplayer, replays). The campaign overrides it to apply the player's chosen enemy AI. //added by ikill240c
    default int resolveAIDifficulty(int ai_difficulty) { //added by ikill240c
        return ai_difficulty; //added by ikill240c
    } //added by ikill240c
}
