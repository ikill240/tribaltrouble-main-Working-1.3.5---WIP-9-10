package com.oddlabs.tt.net;

public interface GameServerInterface {
    void resetSlotState(int slot, boolean open);

    void setPlayerSlot(int slot, int type, int race, int team, boolean ready, int ai_difficulty);

    void startServer();

    void chat(String chat);

    // Custom map transfer: a joining player that lacks the lobby's custom map asks for it; every player reports
    // when it has an identical copy (the host won't start until all have). See Server "custom map transfer". //added by ikill240c
    void requestCustomMap(); //added by ikill240c

    void customMapReady(); //added by ikill240c

    // Sent by the player after storing each piece, so the host knows it may send another (flow control). //added by ikill240c
    void customMapChunkReceived(); //added by ikill240c

    // Build check: a joining player reports its BuildFingerprint so the host can warn about mismatched builds. //added by ikill240c
    void reportBuild(String fingerprint); //added by ikill240c
}
