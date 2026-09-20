package com.oddlabs.tt.net;

import com.oddlabs.matchmaking.TunnelAddress;
import com.oddlabs.tt.player.PlayerInfo;

import java.io.Serial;
import java.io.Serializable;

public final class PlayerSlot implements Serializable {
    @Serial
    private static final long serialVersionUID = 1;

    public static final int AI_NONE = 0;
    public static final int AI_EASY = 1;
    public static final int AI_NORMAL = 2;
    public static final int AI_HARD = 3;
    public static final int AI_TOWER_TUTORIAL = 4;
    public static final int AI_CHIEFTAIN_TUTORIAL = 5;
    public static final int AI_BATTLE_TUTORIAL = 6;
    public static final int AI_PASSIVE_CAMPAIGN = 7;
    public static final int AI_NEUTRAL_CAMPAIGN = 8;
    // 9 is the next free value - AI_TOWER_TUTORIAL through AI_NEUTRAL_CAMPAIGN above already
    // occupy 4-8, and these ints are transmitted over the network for lobby sync, so this can't
    // reuse or renumber an existing value without breaking compatibility with anything that
    // already expects the old numbering. //added by ikill240c
    public static final int AI_INSANE = 9; //added by ikill240c

    public static final int OPEN = 1;
    public static final int CLOSED = 2;
    public static final int HUMAN = 3;
    public static final int AI = 4;

    private final int slot;

    private int type = OPEN;
    private int rating;
    private boolean ready;
    private PlayerInfo player_info;
    private TunnelAddress address;
    private int ai_difficulty = AI_NONE;

    PlayerSlot(int slot) {
        this.slot = slot;
    }

    static boolean isValidType(int type) {
        return type == HUMAN || type == AI/* || type == OPEN || type == CLOSED*/;
    }

    void setRating(int rating) {
        this.rating = rating;
    }

    void setType(int type) {
        this.type = type;
    }

    void setAIDifficulty(int ai_difficulty) {
        this.ai_difficulty = ai_difficulty;
    }

    void setAddress(TunnelAddress address) {
        this.address = address;
    }

    void setReady(boolean ready) {
        this.ready = ready;
    }

    // Made public - WorldViewer (a different package) needs each retained slot's ORIGINAL index to
    // assign the correct color after WorldStarter/ReplayWorldStarter compact out closed slots. Compacting
    // filters the array but keeps the same PlayerSlot objects, so this value survives correctly.
    // //added by ikill240c
    public int getSlot() {
        return slot;
    }

    void setInfo(PlayerInfo player_info) {
        this.player_info = player_info;
    }

    public PlayerInfo getInfo() {
        return player_info;
    }

    public boolean isReady() {
        return ready;
    }

    public TunnelAddress getAddress() {
        return address;
    }

    public int getAIDifficulty() {
        return ai_difficulty;
    }

    public int getType() {
        return type;
    }

    public int getRating() {
        return rating;
    }
}
