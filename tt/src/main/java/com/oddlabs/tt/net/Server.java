package com.oddlabs.tt.net;

import com.oddlabs.matchmaking.Game;
import com.oddlabs.matchmaking.GameSession;
import com.oddlabs.matchmaking.MatchmakingServerInterface;
import com.oddlabs.matchmaking.Profile;
import com.oddlabs.matchmaking.RosterTemplate;
import com.oddlabs.matchmaking.TunnelAddress;
import com.oddlabs.net.AbstractConnection;
import com.oddlabs.net.AbstractConnectionListener;
import com.oddlabs.net.ConnectionListener;
import com.oddlabs.net.ConnectionListenerInterface;
import com.oddlabs.net.NetworkSelector;
import com.oddlabs.tt.event.LocalEventQueue;
import com.oddlabs.tt.global.Globals;
import com.oddlabs.tt.model.RacesResources;
import com.oddlabs.tt.player.PlayerInfo;
import com.oddlabs.tt.resource.CustomMapGenerator; //added by ikill240c
import com.oddlabs.tt.resource.WorldGenerator;
import com.oddlabs.tt.util.Utils;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.ResourceBundle;

public final class Server implements ConnectionListenerInterface {
    private static final int NEGOTIATING = 1;
    private static final int SYNCHRONIZING = 2;
    private static final int CLOSED = 3;

    private static final int JOIN_DEFAULT_NONE = -1;

    private final PlayerSlot @NonNull [] players;
    // Per-slot race/team a joining human inherits, seeded in-process from the host's create-dialog roster. Slots left
    // at JOIN_DEFAULT_NONE fall back to the legacy random race / slot-derived team.
    private final int @NonNull [] join_default_race;
    private final int @NonNull [] join_default_team;
    private final String[] ai_names;
    private final WorldGenerator generator;
    private final Game game;
    private final @NonNull AbstractConnectionListener local_listener;
    private final Map<AbstractConnection, ClientConnection> connection_to_client = new LinkedHashMap<>();
    private final @NonNull Random random;
    private AbstractConnectionListener tunnelled_listener;

    private int state = NEGOTIATING;
    private final boolean register_server;

    public Server(@NonNull NetworkSelector network, Game game, InetAddress ip, WorldGenerator generator,
            boolean register_server, String[] ai_names) {
        this(network, game, ip, generator, register_server, ai_names, MatchmakingServerInterface.MAX_PLAYERS);
    }

    public Server(@NonNull NetworkSelector network, Game game, InetAddress ip, WorldGenerator generator,
            boolean register_server, String[] ai_names, int player_count) {
        this.local_listener = new ConnectionListener(network, ip, Globals.NET_PORT, this);
        this.game = game;
        this.generator = generator;
        this.register_server = register_server;
        this.ai_names = ai_names;
        this.random = new Random(LocalEventQueue.getQueue().getHighPrecisionManager().getTick());
        players = new PlayerSlot[player_count];
        for (short i = 0; i < players.length; i++) {
            players[i] = new PlayerSlot(i);
            players[i].setReady(i != 0);
        }
        join_default_race = new int[player_count];
        join_default_team = new int[player_count];
        Arrays.fill(join_default_race, JOIN_DEFAULT_NONE);
        Arrays.fill(join_default_team, JOIN_DEFAULT_NONE);
    }

    /**
     * Seeds the race/team a joining human inherits per slot from the host's create-dialog roster. The server runs in
     * the host's own process, so this is a direct in-process call: nothing crosses the wire. Joiners simply receive the
     * host's intended race/team in the normal player broadcast instead of a random race and slot-derived team. Slots
     * the roster leaves unspecified keep the legacy defaults, so un-updated clients and the join handshake are
     * unaffected.
     */
    public void applyRosterJoinDefaults(@NonNull RosterTemplate roster) {
        RosterTemplate.Slot[] slots = roster.getSlots();
        for (int i = 0; i < players.length && i < slots.length; i++) {
            Integer race = slots[i].getRace();
            Integer team = slots[i].getTeam();
            if (race != null)
                join_default_race[i] = race;
            if (team != null)
                join_default_team[i] = team;
        }
    }

    private @NonNull Iterator<ClientConnection> getClientIterator() {
        return connection_to_client.values().iterator();
    }

    private int getNumClients() {
        return connection_to_client.size();
    }

    private ClientConnection getClientFromConnection(AbstractConnection conn) {
        return connection_to_client.get(conn);
    }

    private void unregisterGame() {
        // local_listener can still be null if construction failed inside `new ConnectionListener(...)` (e.g. the port
        // is already bound): the listener's constructor calls back into error() -> close() before the field is
        // assigned. Guard so the real IOException surfaces instead of an NPE that masks it.
        if (local_listener != null)
            local_listener.close();
        if (tunnelled_listener != null)
            tunnelled_listener.close();
        if (register_server && Network.getMatchmakingClient().isConnected()) {
            Network.getMatchmakingClient().getInterface().unregisterGame();
        }
    }

    private void unregister() {
        state = CLOSED;
    }

    private void closeConnections() {
        for (AbstractConnection conn : connection_to_client.keySet()) {
            conn.close();
        }
        connection_to_client.clear();
        unregister();
    }

    public void close() {
        unregisterGame();
        closeConnections();
    }

    private int getNumReady() {
        int count = 0;
        Iterator<ClientConnection> it = getClientIterator();
        while (it.hasNext()) {
            ClientConnection client = it.next();
            if (client.getClient().getPlayerSlot().isReady())
                count++;
        }
        return count;
    }

    @Override
    public void error(AbstractConnectionListener listener, IOException e) {
        IO.println("Listener failed: " + e);
        // NetworkSelector's dispatch loop wraps any non-IOException as
        // `new IOException("Unexpected error", e)`, preserving the real cause - but printing just
        // e.toString() (as this line always did) never shows that cause, so whatever actually
        // went wrong (e.g. an exception thrown while generating a custom map) was completely
        // invisible in the log. printStackTrace() walks the full "Caused by:" chain.
        // //added by ikill240c
        e.printStackTrace(); //added by ikill240c
        close();
    }

    public void handleError(AbstractConnection conn, Exception e) {
        IO.println("Disconnecting client because of exception: " + e);
        ClientConnection client = getClientFromConnection(conn);
        if (client != null) {
            disconnectClient(client);
            if (state == NEGOTIATING) {
                resetSlotState(client.getClient().getPlayerSlot(), true);
            }
        }
    }

    private void disconnectClient(@NonNull ClientConnection client) {
        assert client != null;
        client.getConnection().close();
        connection_to_client.remove(client.getConnection());
    }

    private @Nullable ClientConnection locateClientForSlot(PlayerSlot player_slot) {
        Iterator<ClientConnection> it = getClientIterator();
        while (it.hasNext()) {
            ClientConnection client = it.next();
            if (client.getClient().getPlayerSlot() == player_slot)
                return client;
        }
        return null;
    }

    public void resetSlotState(@NonNull PlayerSlot client_slot, int slot, boolean open) {
        if (!canControlSlot(client_slot, slot))
            return;
        resetSlotState(players[slot], open);
    }

    private void resetSlotState(@NonNull PlayerSlot client_slot, boolean open) {
        client_slot.setType(open ? PlayerSlot.OPEN : PlayerSlot.CLOSED);
        client_slot.setInfo(null);
        client_slot.setAddress(null);
        client_slot.setReady(true);
        client_slot.setAIDifficulty(PlayerSlot.AI_NONE);
        ClientConnection player_client = locateClientForSlot(client_slot);
        if (player_client != null)
            disconnectClient(player_client);
        broadcastPlayers(true);
    }

    private boolean canControlSlot(@NonNull PlayerSlot client_slot, int slot) {
        return slot >= 0 && slot < players.length && state == NEGOTIATING &&
                ((client_slot.getSlot() == 0 || client_slot.getSlot() == slot));
    }

    public void startServer(@NonNull PlayerSlot slot) {
        if (!canControlSlot(slot, 0) || getNumReady() != getNumClients())// || PlayerSlot.getNumTeams(players) < 2)
            return;
        if (!allPlayersHaveCustomMap(slot)) //added by ikill240c
            return; //added by ikill240c
        state = SYNCHRONIZING;
        unregisterGame();
        broadcastInits();
    }

    public void setPlayerSlot(@NonNull PlayerSlot client_slot, int slot, int type, int race, int team, boolean ready,
            int ai_difficulty) {
        if (!PlayerSlot.isValidType(type) || !RacesResources.isValidRace(race))
            return;
        if (!canControlSlot(client_slot, slot) || (client_slot.getSlot() == slot && type != PlayerSlot.HUMAN))
            return;
        PlayerSlot player_slot = players[slot];
//		PlayerInfo player_info = players[slot];
        ClientConnection player_client = locateClientForSlot(player_slot);
        if (player_client != null && type != PlayerSlot.HUMAN)
            disconnectClient(player_client);
        String name;
        if (type == PlayerSlot.AI) {
            name = ai_names[slot];
        } else {
            name = player_slot.getInfo().getName();
        }
        PlayerInfo player_info = new PlayerInfo(team, race, name);
        boolean reset_ready = player_slot.getInfo() == null || type != player_slot.getType()
                || ai_difficulty != player_slot.getAIDifficulty() || !player_info.equals(player_slot.getInfo());
        player_slot.setType(type);
        player_slot.setAIDifficulty(ai_difficulty);
        player_slot.setInfo(player_info);
        player_slot.setReady(type != PlayerSlot.HUMAN || ready);
        broadcastPlayers(reset_ready);
    }

    private void resetReady() {
        int num_humans = 0;
        for (PlayerSlot player_slot : players) {
            if (player_slot.getType() == PlayerSlot.HUMAN)
                num_humans++;
        }
        if (num_humans > 1) {
            for (PlayerSlot player_slot : players) {
                if (player_slot.getType() == PlayerSlot.HUMAN)
                    player_slot.setReady(false);
            }
        }
    }

    private void broadcastPlayers(boolean reset_ready) {
        if (reset_ready)
            resetReady();
        Iterator<ClientConnection> it = getClientIterator();
        while (it.hasNext()) {
            ClientConnection client = it.next();
            client.getClientInterface().setPlayers(players);
        }
    }

    public void chat(@NonNull PlayerSlot player_slot, String chat) {
        Iterator<ClientConnection> it = getClientIterator();
        while (it.hasNext()) {
            ClientConnection client = it.next();
            client.getClientInterface().chat(player_slot.getSlot(), chat);
        }
    }

    private void broadcastInits() {
        Iterator<ClientConnection> it = getClientIterator();
        while (it.hasNext()) {
            ClientConnection client = it.next();
            int session_id = new Random(LocalEventQueue.getQueue().getHighPrecisionManager().getTick()).nextInt();
            client.getClientInterface().startGame(session_id);
        }
    }

    private short locateAvailableSlot() {
        for (short i = 0; i < players.length; i++) {
            if (players[i].getType() == PlayerSlot.OPEN)
                return i;
        }
        return (short) -1;
    }

    @Override
    public void incomingConnection(@NonNull AbstractConnectionListener connection_listener, Object remote_address) {
        IO.println("Incoming host connection from " + remote_address);
        short available_slot = locateAvailableSlot();
        if (state != NEGOTIATING || available_slot == -1 ||
                (remote_address instanceof InetAddress address && !address.isLoopbackAddress()) ||
                (remote_address instanceof TunnelIdentifier identifier && game != null && game.isRated() &&
                        identifier.profile().getWins() < GameSession.MIN_WINS_FOR_RANKING)) {
            IO.println(
                    "rejecting incoming connection since state = " + state + " | locateAvailableSlot() = " + available_slot + " remote_address = " + remote_address);
            connection_listener.rejectConnection();
            return;
        }
        PlayerSlot player_slot = players[available_slot];
        int rating = 0;
        String name;
        TunnelAddress address;
        if (remote_address instanceof InetAddress) {
            address = Network.getMatchmakingClient().getLocalAddress();
            if (register_server) {
                tunnelled_listener = new TunnelledConnectionListener(this);
                Network.getMatchmakingClient().getInterface().registerGame(game);
            }
            Profile profile = Network.getMatchmakingClient().getProfile();
            if (profile != null) {
                name = profile.getNick();
                rating = profile.getRating();
            } else
                name = Utils.getBundleString(ResourceBundle.getBundle(MatchmakingClient.class.getName()), "player");
        } else {
            TunnelIdentifier tunnel_id = (TunnelIdentifier) remote_address;
            name = tunnel_id.profile().getNick();
            rating = tunnel_id.profile().getRating();
            address = tunnel_id.address();
        }
        player_slot.setReady(false);
        int max_teams = players.length;
        if (game != null && game.isRated())
            max_teams = 2;
        int race = join_default_race[available_slot] != JOIN_DEFAULT_NONE ? join_default_race[available_slot] : random.nextInt(
                RacesResources.getNumRaces());
        int team = join_default_team[available_slot] != JOIN_DEFAULT_NONE ? join_default_team[available_slot] % max_teams : available_slot % max_teams;
        PlayerInfo player_info = new PlayerInfo(team, race, name);
        player_slot.setRating(rating);
        player_slot.setType(PlayerSlot.HUMAN);
        player_slot.setAddress(address);
        player_slot.setInfo(player_info);
        ClientInfo client = new ClientInfo(this, player_slot);
        AbstractConnection conn = connection_listener.acceptConnection(client);
        ClientConnection client_conn = new ClientConnection(conn, client);
        connection_to_client.put(conn, client_conn);
        client_conn.getClientInterface().setWorldGeneratorAndPlayerSlot(game, generator, available_slot,
                players.length);
        broadcastPlayers(true);
    }

    // ---------------- custom map transfer ---------------- //added by ikill240c
    // Every player needs the host's custom map file to build the same world. Players who don't have it get it from
    // the host through the lobby connection: the host compresses the file once, then sends it in pieces of
    // CUSTOM_MAP_CHUNK_BYTES, keeping at most CUSTOM_MAP_WINDOW pieces unacknowledged - the player confirms each
    // piece (customMapChunkReceived) before another is sent. Each piece stays well under the 32KB per-message limit
    // and the in-flight total under the 64KB connection buffer, so normal lobby messages keep flowing. Acknowledgement
    // pacing (not writeBufferDrained) because internet lobbies run through the matchmaking tunnel, whose connection
    // reports "drained" immediately from inside the send call - pacing on that would send the whole map at once.
    // The host can't start the game until every player has reported an identical copy. //added by ikill240c
    private static final int CUSTOM_MAP_CHUNK_BYTES = 28 * 1024; //added by ikill240c
    private static final int CUSTOM_MAP_WINDOW = 2; //added by ikill240c
    private byte @Nullable [] custom_map_data; //added by ikill240c - compressed map, built on the first request

    void requestCustomMap(@NonNull PlayerSlot player_slot) { //added by ikill240c
        ClientConnection client = locateClientForSlot(player_slot); //added by ikill240c
        if (state != NEGOTIATING || client == null || client.has_custom_map || client.custom_map_offset >= 0 //added by ikill240c
                || !(generator instanceof CustomMapGenerator map)) //added by ikill240c
            return; //added by ikill240c
        if (custom_map_data == null) { //added by ikill240c
            try { //added by ikill240c
                custom_map_data = map.readCompressed(); //added by ikill240c
            } catch (IOException | RuntimeException e) { //added by ikill240c
                IO.println("Couldn't read the custom map to send it: " + e); //added by ikill240c
                client.getClientInterface().receiveCustomMapChunk(-1, new byte[0]); //added by ikill240c
                return; //added by ikill240c
            } //added by ikill240c
        } //added by ikill240c
        client.custom_map_offset = 0; //added by ikill240c
        client.custom_map_in_flight = 0; //added by ikill240c
        fillCustomMapWindow(client); //added by ikill240c
    } //added by ikill240c

    void customMapChunkReceived(@NonNull PlayerSlot player_slot) { //added by ikill240c
        ClientConnection client = locateClientForSlot(player_slot); //added by ikill240c
        if (state != NEGOTIATING || client == null || client.custom_map_offset < 0) //added by ikill240c
            return; //added by ikill240c
        client.custom_map_in_flight = Math.max(0, client.custom_map_in_flight - 1); //added by ikill240c
        fillCustomMapWindow(client); //added by ikill240c
    } //added by ikill240c

    private void fillCustomMapWindow(@NonNull ClientConnection client) { //added by ikill240c
        byte[] data = custom_map_data; //added by ikill240c
        while (data != null && client.custom_map_in_flight < CUSTOM_MAP_WINDOW && client.custom_map_offset < data.length) //added by ikill240c
            sendNextCustomMapChunk(client); //added by ikill240c
    } //added by ikill240c

    private void sendNextCustomMapChunk(@NonNull ClientConnection client) { //added by ikill240c
        byte[] data = custom_map_data; //added by ikill240c
        if (data == null || client.custom_map_offset >= data.length) //added by ikill240c
            return; //added by ikill240c - finished (or nothing to send)
        int end = Math.min(client.custom_map_offset + CUSTOM_MAP_CHUNK_BYTES, data.length); //added by ikill240c
        byte[] chunk = Arrays.copyOfRange(data, client.custom_map_offset, end); //added by ikill240c
        client.custom_map_offset = end; //added by ikill240c
        client.custom_map_in_flight++; //added by ikill240c
        client.getClientInterface().receiveCustomMapChunk(data.length, chunk); //added by ikill240c
    } //added by ikill240c

    void customMapReady(@NonNull PlayerSlot player_slot) { //added by ikill240c
        ClientConnection client = locateClientForSlot(player_slot); //added by ikill240c
        if (client != null) { //added by ikill240c
            client.has_custom_map = true; //added by ikill240c
            client.custom_map_offset = -1; //added by ikill240c
        } //added by ikill240c
    } //added by ikill240c

    // True unless this is a custom map game and a player (other than the host, who has the file) is missing it.
    // If so, the host is told who is still downloading. //added by ikill240c
    private boolean allPlayersHaveCustomMap(@NonNull PlayerSlot host_slot) { //added by ikill240c
        if (!(generator instanceof CustomMapGenerator)) //added by ikill240c
            return true; //added by ikill240c
        StringBuilder waiting = new StringBuilder(); //added by ikill240c
        Iterator<ClientConnection> it = getClientIterator(); //added by ikill240c
        while (it.hasNext()) { //added by ikill240c
            ClientConnection client = it.next(); //added by ikill240c
            PlayerSlot slot = client.getClient().getPlayerSlot(); //added by ikill240c
            if (slot == host_slot || client.has_custom_map) //added by ikill240c
                continue; //added by ikill240c
            if (waiting.length() > 0) //added by ikill240c
                waiting.append(", "); //added by ikill240c
            waiting.append(slot.getInfo() != null ? slot.getInfo().getName() : "a player"); //added by ikill240c
        } //added by ikill240c
        if (waiting.isEmpty()) //added by ikill240c
            return true; //added by ikill240c
        ClientConnection host = locateClientForSlot(host_slot); //added by ikill240c
        if (host != null) //added by ikill240c
            host.getClientInterface().customMapNotice("Can't start yet - still sending the map to: " + waiting); //added by ikill240c
        return false; //added by ikill240c
    } //added by ikill240c
}
