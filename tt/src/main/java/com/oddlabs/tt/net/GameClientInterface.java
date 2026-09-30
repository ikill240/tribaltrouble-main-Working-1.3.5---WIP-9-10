package com.oddlabs.tt.net;

import com.oddlabs.matchmaking.Game;
import com.oddlabs.tt.resource.WorldGenerator;

public interface GameClientInterface {
    void setWorldGeneratorAndPlayerSlot(Game game, WorldGenerator generator, short player_slot, int player_count);

    void setPlayers(PlayerSlot[] players);

    void startGame(int session_id);

    void chat(int player_slot, String chat);

    // Custom map transfer (server -> player): one piece of the gzip-compressed map, in order. total_bytes is the
    // full compressed size, or -1 if the host couldn't send the map at all. //added by ikill240c
    void receiveCustomMapChunk(int total_bytes, byte[] data); //added by ikill240c

    // A line for the lobby chat about the transfer (e.g. who is still downloading when the host tries to start). //added by ikill240c
    void customMapNotice(String text); //added by ikill240c
}
