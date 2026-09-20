package com.oddlabs.matchmaking;

import com.oddlabs.net.ARMIEvent;
import com.oddlabs.net.HostSequenceID;

public interface MatchmakingServerInterface {
    int TYPE_NONE = 0;
    int TYPE_GAME = 1;
    int TYPE_CHAT_ROOM_LIST = 2;
    int TYPE_RANKING_LIST = 3;
    int TYPE_OPENSKILL_RANKING_LIST = 4;
    int TYPE_OPENSKILL_PERSONAL_RANKING = 5;

    int MATCHMAKING_SERVER_PORT = 33214;

    // Every consumer of this constant (lobby UI slot arrays in TerrainMenu, the default color
    // palette in Settings.generateDefaultColours(), etc.) was already written to size itself off
    // this value dynamically rather than hardcoding 12, so raising it needed no changes anywhere
    // else in those systems. player_slot is transmitted as a short across the network (see
    // GameClientInterface.setWorldGeneratorAndPlayerSlot), which comfortably holds up to 32767,
    // so this is nowhere close to that ceiling. //added by ikill240c
    int MAX_PLAYERS = 80; //added by ikill240c - was 32, raised per explicit request; team_colours auto-scales to this (see Settings.generateDefaultColours())
    int MIN_PLAYERS = 1;
    int MIN_ROOM_NAME_LENGTH = 1;
    int MAX_ROOM_NAME_LENGTH = 20;
    int MAX_ROOM_USERS = 50;
    String ALLOWED_ROOM_CHARS = "abcdefghijklmnopqrstuvwxyzæøåABCDEFGHIJKLMNOPQRSTUVWXYZÆØÅ0123456789èéêëìíîïðñòóôõöùúûüýÿ-_,.:;?+={}[]()/&%#!<\\>'*";

    void setProfile(String nick);

    void createProfile(String nick);

    void deleteProfile(String nick);

    void requestProfiles();

    void logPriority(String nick, int priority);

    void registerGame(Game game);

    void unregisterGame();

    void sendMessageToRoom(String msg);

    void sendPrivateMessage(String nick, String msg);

    void joinRoom(String name);

    void leaveRoom();

    void requestInfo(String nick);

    void requestList(int type, int update_key);

    void acceptTunnel(HostSequenceID host_seq);

    void openTunnel(int address_to, int seq);

    void closeTunnel(HostSequenceID address_to);

    void routeEvent(HostSequenceID from, ARMIEvent event);

    void multicastEvent(ARMIEvent event);

    void setMulticast(HostSequenceID[] addresses);

    void gameStartedNotify(GameSession game_session);

    void gameQuitNotify(String nick);

    void freeQuitStopNotify();

    void gameLostNotify();

    void gameWonNotify();

    void updateGameStatus(int tick, int[] status);

    void updateSpectatorInfo(int tick, String info);

    void requestSpectate(String nick);

    void updateCommandEvent(int tick, int client_id, short event_size, byte[] event_data);

    void updateWorldParams(byte[] world_params_data);

    void requestSpectatorEventLog();
}
