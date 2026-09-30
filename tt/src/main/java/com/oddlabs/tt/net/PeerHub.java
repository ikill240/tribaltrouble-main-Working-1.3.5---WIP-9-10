package com.oddlabs.tt.net;

import com.oddlabs.net.ARMIEvent;
import com.oddlabs.net.ARMIEventWriter;
import com.oddlabs.net.ARMIHandlerException; //added by ikill240c
import com.oddlabs.net.ARMIInterfaceMethods;
import com.oddlabs.net.IllegalARMIEventException;
import com.oddlabs.net.NetworkSelector;
import com.oddlabs.router.Router;
import com.oddlabs.router.SessionID;
import com.oddlabs.router.SessionInfo;
import com.oddlabs.tt.animation.Animated;
import com.oddlabs.tt.animation.AnimationManager;
import com.oddlabs.tt.event.LocalEventQueue;
import com.oddlabs.tt.global.Globals;
import com.oddlabs.tt.global.Settings;
import com.oddlabs.tt.gui.GUIRoot;
import com.oddlabs.tt.landscape.HeightMap;
import com.oddlabs.tt.landscape.World;
import com.oddlabs.tt.model.Building;
import com.oddlabs.tt.model.Unit;
import com.oddlabs.tt.player.AdvancedAI; //added by ikill240c
import com.oddlabs.tt.player.Player;
import com.oddlabs.tt.player.PlayerInterface;
import com.oddlabs.tt.util.StateChecksum;
import com.oddlabs.tt.util.Utils;
import com.oddlabs.tt.viewer.NotificationManager;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class PeerHub implements Animated, RouterHandler {
    private static final Logger logger = Logger.getLogger(PeerHub.class.getName());

    public static final ResourceBundle bundle = ResourceBundle.getBundle(PeerHub.class.getName());

    private static @NonNull String i18n(@NonNull String key, @NonNull Object @NonNull... args) {
        return Utils.getBundleString(bundle, key, args);
    }

    public static final @NonNull String SYSTEM_NAME = i18n("system_name");

    private static final int MILLISECONDS_PER_HEARTBEAT = 60;
    private static final int CLIENT_MAX_DELAY_MILLIS = 60;
    private static final float FREE_QUIT_TIME = 120f;
    private static final int TICKS_PER_STATUS_UPDATE = (int) (20 / AnimationManager.ANIMATION_SECONDS_PER_TICK);
    private static final int TICKS_PER_SPECTATOR_UPDATE = 5;
    private static final int TICKS_PER_CHECKSUM = (int) (10 / AnimationManager.ANIMATION_SECONDS_PER_TICK);
    // Spectator controller is non-null only for spectator instances

    private static boolean waiting_for_ack = false;

    private final ARMIInterfaceMethods interface_methods = new ARMIInterfaceMethods(PeerHubInterface.class);
    private final StateChecksum checksum = new StateChecksum();
    private final @NonNull PeerHubInterface peerhubs_interface;
    private final @NonNull PlayerInterface player_interface;
    private final int num_participants;
    private final Peer @NonNull [] peer_index_to_peer;
    private final Map<Player, Peer> player_to_peer = new LinkedHashMap<>();
    private final Map<Peer, Player> peer_to_player = new LinkedHashMap<>();
    private final Set<Player> nonhuman_players = new HashSet<>();
    private final @NonNull NetworkSelector network;
    private final @NonNull RouterClient router_client;
    private final @Nullable Router router;
    private final NotificationManager notification_manager;
    private final @NonNull Player local_player;
    private final @NonNull AnimationManager manager;
    private final boolean is_multiplayer;
    private final boolean is_rated;
    private final boolean is_spectator;
    private final @Nullable PeerHubSpectatorController spectatorController;
    private final StallHandler stall_handler;
    private int local_peer_index;

    private int pause_ticks;
    private int server_millis;
    private int paused;
    private boolean is_synchronized;

    // Web spectator streaming state
    private boolean sentMap;
    private boolean sentInitInfo;
    private boolean sentTrees;
    private int mapRowsSent;

    public boolean isSynchronized() {
        return is_synchronized;
    }

//private int ignore_peer = -1;

    public PeerHub(@NonNull AnimationManager manager, boolean is_multiplayer, boolean is_rated,
            @NonNull Player local_player, PlayerSlot[] player_slots, @NonNull NetworkSelector network, GUIRoot gui_root,
            NotificationManager notification_manager, DistributableTable distributable_table, SessionID session_id,
            StallHandler stall_handler) {
        this(manager, is_multiplayer, is_rated, false, local_player, player_slots, network, gui_root, notification_manager, distributable_table, session_id, stall_handler);
    }

    public PeerHub(@NonNull AnimationManager manager, boolean is_multiplayer, boolean is_rated, boolean is_spectator,
            @NonNull Player local_player, PlayerSlot[] player_slots, @NonNull NetworkSelector network, GUIRoot gui_root,
            NotificationManager notification_manager, DistributableTable distributable_table, SessionID session_id,
            StallHandler stall_handler) {
        this.stall_handler = stall_handler;
        this.is_rated = is_rated;
        this.is_spectator = is_spectator;
        this.local_player = local_player;
        this.network = network;
        this.notification_manager = notification_manager;
        this.is_multiplayer = is_multiplayer;
        this.manager = manager;

        GameArgumentReader argument_reader = new GameArgumentReader(distributable_table);
        List<Peer> peer_index_to_peer_list = new ArrayList<>();
        Player[] players = local_player.getWorld().getPlayers();
        this.local_peer_index = -1;
        if (!is_multiplayer) {
            this.router = new Router(network, com.oddlabs.util.Utils.getLoopbackAddress(), 0,
                    Logger.getAnonymousLogger(), (IOException e) -> {
                        //					PeerHub.this.routerFailed(e);
                        throw new RuntimeException(e);
                    });
            this.router_client = new RouterClient(network, this, router.getPort());
        } else {
            this.router = null;
            this.router_client = new RouterClient(network, Settings.getSettings().getRouterAddress(), this);
        }
        for (short i = 0; i < players.length; i++) {
            Player player = players[i];
            if (player_slots[i].getType() != PlayerSlot.HUMAN) {
                nonhuman_players.add(player);
                continue;
            }
            IO.println("index " + i + " contains player " + player);
            final int peer_index = peer_index_to_peer_list.size();
            ARMIEventWriter router_handler = (ARMIEvent event) -> router_client.getInterface().relayEventTo(peer_index,
                    event);
            PeerHubInterface peer_interface = (PeerHubInterface) ARMIEvent.createProxy(router_handler,
                    PeerHubInterface.class);
            Peer peer = new Peer(this, peer_index, player, argument_reader, peer_interface);
            ARMIEventWriter peer_broker;
            if (player == local_player) {
                this.local_peer_index = peer_index;
            }
            peer_index_to_peer_list.add(peer);
            peer_to_player.put(peer, player);
            player_to_peer.put(player, peer);
        }
        this.peer_index_to_peer = new Peer[peer_index_to_peer_list.size()];
        peer_index_to_peer_list.toArray(peer_index_to_peer);
        this.num_participants = peer_index_to_peer.length;
        if (is_spectator) {
            this.player_interface = new NoOpPlayerInterface();
        } else {
            ARMIEventWriter game_router_handler = router_client.getInterface()::relayGameStateEvent;
            this.player_interface = (PlayerInterface) ARMIEvent.createProxy(game_router_handler, new GameArgumentWriter(
                    distributable_table), PlayerInterface.class);
        }
        ARMIEventWriter hub_router_handler = router_client.getInterface()::relayEvent;
        this.peerhubs_interface = (PeerHubInterface) ARMIEvent.createProxy(hub_router_handler, PeerHubInterface.class);
        this.spectatorController = is_spectator ? new PeerHubSpectatorController(this) : null;
        manager.registerAnimation(this);
        if (is_spectator) {
            router_client.connectSpectator(session_id);
        } else {
            router_client.connect(session_id, new SessionInfo(num_participants, MILLISECONDS_PER_HEARTBEAT),
                    local_peer_index);
        }
    }

    @Override
    public void routerFailed(Exception e) {
        logger.log(Level.WARNING, "Router failed", e);
        closeNetwork();
        stall_handler.peerhubFailed();
    }

    @Override
    public void heartbeat(int millis) {
        if (millis < server_millis) {
            routerFailed(new IOException(
                    "Invalid time received: " + millis + " (tick currently at " + getTick() + ")"));
            return;
        }
        server_millis = millis;
    }

    @Override
    public void receiveEvent(int client_id, @NonNull ARMIEvent event) {
        Peer peer = getPeerFromClientID(client_id);
        if (peer == null) {
            // A stale, in-flight event from a peer that was ALREADY removed (see
            // removePeerFromActiveList()/peerDisconnected()) before this event arrived - most
            // plausibly after a long stall (the lockstep simulation waiting on a slow/stalled
            // peer), where the underlying router/session layer's own timeout can disconnect what
            // is, in a singleplayer/loopback game, the ONLY player, well before that player's own
            // client actually stops sending anything. Previously treated identically to a genuine
            // protocol violation (an event from a client_id that was never valid at all), tearing
            // down the entire session via routerFailed() over what's actually a harmless race
            // between "peer got timed out" and "peer's own already-in-flight event arrives" -
            // this is very plausibly what's been reported as an occasional "desync". Logging and
            // ignoring the stale event, rather than propagating it as a fatal router error, lets
            // the session continue instead of being torn down by its own timeout mechanism.
            // //added by ikill240c
            IO.println("Ignoring stale event from already-disconnected client_id: " + client_id); //added by ikill240c
            return; //added by ikill240c
        }
        try {
            event.execute(interface_methods, peer);
        } catch (ARMIHandlerException e) { //added by ikill240c
            // The event was valid; our own handler threw while processing it. That's a local bug, not
            // grounds to kick the sender - log the real cause instead. See ARMIHandlerException.
            // //added by ikill240c
            logger.log(Level.SEVERE, "Handling an event from " + peer + " failed; ignored, peer stays connected", //added by ikill240c
                    e.getCause()); //added by ikill240c
        } catch (IllegalARMIEventException e) {
            peerDisconnected(peer, e.getMessage());
        }
    }

    @Override
    public void receiveGameStateEvent(int client_id, int millis, ARMIEvent event) {
        if (millis < server_millis) {
            routerFailed(new IOException(
                    "Invalid time received for event: " + millis + " (tick currently at " + getTick() + ")"));
            return;
        }
        Peer peer = getPeerFromClientID(client_id);
        if (peer == null) {
            // See receiveEvent()'s identical check above for the full reasoning - same stale-
            // event-after-disconnect race, same fix. //added by ikill240c
            IO.println("Ignoring stale game state event from already-disconnected client_id: " + client_id); //added by ikill240c
            return; //added by ikill240c
        }
        server_millis = millis;
        int event_tick = millisToTickCeil(millis);
        peer.addEvent(event_tick, event);
        if (!is_spectator && isFirstActivePeer() && Network.getMatchmakingClient().isConnected()) {
            PeerHubSpectatorController.sendCommandEvent(event_tick, client_id, event);
        }
    }

    private boolean isFirstActivePeer() {
        for (int i = 0; i < peer_index_to_peer.length; i++) {
            if (peer_index_to_peer[i] != null) return i == local_peer_index;
        }
        return false;
    }

    @Override
    public void playerDisconnected(int client_id, boolean checksum_error) {
        Peer peer = getPeerFromClientID(client_id);
        if (checksum_error)
            peerChecksumError(peer);
        else
            peerDisconnected(peer, "Peer disconnected");
    }

    private void peerChecksumError(@NonNull Peer peer) {
        IO.println("Disconnecting peer because of checksum mismatch: " + peer.getPlayerInfo().getName());
        peerDisconnected(peer, "Checksum error");
        Globals.checksum_error_in_last_game = true;
    }

    @Nullable
    Peer getPeerFromClientID(int client_id) {
        if (client_id >= 0 && client_id < peer_index_to_peer.length)
            return unsafeGetPeerFromClientID(client_id);
        else
            return null;
    }

    private Peer unsafeGetPeerFromClientID(int client_id) {
        return peer_index_to_peer[client_id];
    }

    @Override
    public void start() {
        if (spectatorController != null) {
            spectatorController.onStart();
        } else {
            is_synchronized = true;
        }
    }

    private Peer locatePeerFromPlayer(Player player) {
        return player_to_peer.get(player);
    }

    private boolean isDisconnected(Peer peer) {
        return getPlayerFromPeer(peer) == null;
    }

    private Player getPlayerFromPeer(Peer peer) {
        return peer_to_player.get(peer);
    }

    public boolean isAlive(@NonNull Player player) {
        return (nonhuman_players.contains(player) || locatePeerFromPlayer(player) != null) && player.isAlive();
    }

    public @NonNull PlayerInterface getPlayerInterface() {
        return player_interface;
    }

    private int millisToTickCeil(int millis) {
        return millisToTick(millis + (int) AnimationManager.ANIMATION_MILLISECONDS_PER_TICK - 1);
    }

    private int millisToTick(int millis) {
        return (int) (millis / AnimationManager.ANIMATION_MILLISECONDS_PER_TICK) - pause_ticks;
    }

    @Override
    public void animate(float t) {
        if (router != null)
            router.process();

        if (spectatorController != null && spectatorController.animateCatchUp(t))
            return;

        int server_tick = millisToTick(server_millis);
        if (!is_synchronized || getTick() == server_tick) {
            processStall();
        } else {
            if (!isPaused()) {
                doTick(t);
                int min_tick = millisToTick(server_millis - MILLISECONDS_PER_HEARTBEAT - CLIENT_MAX_DELAY_MILLIS);
                while (getTick() < min_tick)
                    doTick(t);
            } else
                pause_ticks++;
        }
    }

    int getTick() {
        return local_player.getWorld().getTick();
    }

    void setSynchronized(boolean value) {
        this.is_synchronized = value;
    }

    /**
     * Package-private entry point for SpectatorController catch-up ticks.
     */
    void doTickInternal(float t) {
        doTick(t);
    }

    private void doTick(float t) {
        stall_handler.stopStall();

        if (!is_spectator) {
            if (getFreeQuitTicksLeft(local_player.getWorld()) == 0 && Network.getMatchmakingClient().isConnected())
                Network.getMatchmakingClient().getInterface().freeQuitStopNotify();

            if (getTick() % TICKS_PER_STATUS_UPDATE == 0 && Network.getMatchmakingClient().isConnected())
                sendStatusUpdate();

            if (is_multiplayer && Network.getMatchmakingClient().isConnected()) {
                if (!sentMap) sendMap();
                if (!sentInitInfo) sendInitInfo();
                if (!sentTrees) sendTrees();
                if (getTick() % TICKS_PER_SPECTATOR_UPDATE == 0) sendSpectatorInfo();
            }
        }

        for (Peer peer : peer_index_to_peer) {
            if (peer != null) {
                try {
                    peer.executeEvents(getTick());
                } catch (IllegalARMIEventException e) {
                    peerDisconnected(peer, e.getMessage());
                }
            }
        }
        local_player.getWorld().tick(t);
        if (!is_spectator && getTick() % TICKS_PER_CHECKSUM == 0)
            sendChecksum();
    }

    private void sendChecksum() {
        checksum.update(getTick());
        checksum.update(local_player.getWorld().getChecksum());
        local_player.getWorld().getAnimationManagerGameTime().updateChecksum(checksum);
        local_player.getWorld().getAnimationManagerRealTime().updateChecksum(checksum);
        router_client.getInterface().checksum(checksum.getValue());
    }

    private void sendMap() {
        HeightMap map = local_player.getWorld().getHeightMap();
        int size = map.getGridUnitsPerWorld();
        if (mapRowsSent < size) {
            StringBuilder info = new StringBuilder("M ").append(mapRowsSent).append(' ');
            for (int x = 0; x < size; x++) {
                info.append(map.getHeight(x, mapRowsSent)).append(' ');
            }
            info.append('\n');
            mapRowsSent++;
            Network.getMatchmakingClient().getInterface().updateSpectatorInfo(-mapRowsSent, info.toString());
        } else {
            sentMap = true;
        }
    }

    private void sendInitInfo() {
        Player[] players = local_player.getWorld().getPlayers();
        StringBuilder info = new StringBuilder("I ");
        for (Player player : players) {
            var color = player.getColor();
            String name = player.getPlayerInfo().getName();
            int race = player.getPlayerInfo().getRace();
            int team = player.getPlayerInfo().getTeam();
            info.append("NAME ").append(name).append(' ');
            info.append("RACE ").append(race).append(' ');
            info.append("TEAM ").append(team).append(' ');
            info.append("COLOR ").append(color.x()).append(' ').append(color.y()).append(' ').append(color.z()).append(
                    ' ');
        }
        info.append('\n');
        Network.getMatchmakingClient().getInterface().updateSpectatorInfo(-10001, info.toString());
        sentInitInfo = true;
    }

    // Was one unbounded updateSpectatorInfo() message holding every tree on the map. A large map has tens of
    // thousands of trees, and a single event has to fit both the connection's 64KB buffer and ARMIEvent's
    // signed-short size field (32767 bytes). ARMIEvent.write() doesn't check this, so it threw
    // BufferOverflowException and crashed the game as soon as a large multiplayer game started (this runs at
    // game start whether or not anyone spectates). Sent in chunks instead: each chunk is a complete message in
    // the same "T ..." format with the same -10000 marker, just fewer trees - the receiving side is the
    // matchmaking server, which isn't part of this codebase, so the format itself isn't changed. //added by ikill240c
    private static final int TREE_CHUNK_SIZE = 2000; //added by ikill240c - well under both limits

    private void sendTrees() {
        List<int[]> trees = local_player.getWorld().getTreePositions();
        for (int start = 0; start < trees.size(); start += TREE_CHUNK_SIZE) { //added by ikill240c
            int end = Math.min(start + TREE_CHUNK_SIZE, trees.size()); //added by ikill240c
            StringBuilder info = new StringBuilder("T ");
            for (int[] pos : trees.subList(start, end)) { //added by ikill240c
                info.append(pos[0]).append(' ').append(pos[1]).append(' ');
            }
            info.append('\n');
            Network.getMatchmakingClient().getInterface().updateSpectatorInfo(-10000, info.toString());
        } //added by ikill240c
        sentTrees = true;
    }

    // Same size problem as sendTrees(), but this runs every tick with every unit and building of every player,
    // so a long game with enough units hits the same crash. Split into several messages at whole-player
    // boundaries (never inside one player's "P i ..." section); each message repeats the tick number and is
    // complete on its own. //added by ikill240c
    private static final int SPECTATOR_INFO_MAX_CHARS = 20000; //added by ikill240c - well under both limits

    private void sendSpectatorInfo() {
        int tick = getTick();
        String tick_prefix = tick + " "; //added by ikill240c
        StringBuilder info = new StringBuilder(tick_prefix); //added by ikill240c
        Player[] players = local_player.getWorld().getPlayers();
        for (int i = 0; i < players.length; i++) {
            StringBuilder section = new StringBuilder().append("P ").append(i).append(' '); //added by ikill240c
            for (var s : players[i].getUnits().getSet()) {
                if (s instanceof Unit u) {
                    section.append("U ").append(u.getGridX()).append(' ').append(u.getGridY()).append(' '); //added by ikill240c
                } else if (s instanceof Building b) {
                    section.append("B ").append(b.getGridX()).append(' ').append(b.getGridY()).append(' ').append( //added by ikill240c
                            b.getTemplate().getPlacingSize()).append(' ').append(b.getHitPoints()).append(' ');
                }
            }
            // Send what we have first if adding this player would go over the limit - but only if the message
            // already holds at least one player, so a single player's section is never split. //added by ikill240c
            if (info.length() > tick_prefix.length() && info.length() + section.length() > SPECTATOR_INFO_MAX_CHARS) { //added by ikill240c
                info.append('\n'); //added by ikill240c
                Network.getMatchmakingClient().getInterface().updateSpectatorInfo(tick, info.toString()); //added by ikill240c
                info = new StringBuilder(tick_prefix); //added by ikill240c
            } //added by ikill240c
            info.append(section); //added by ikill240c
        }
        info.append('\n');
        Network.getMatchmakingClient().getInterface().updateSpectatorInfo(tick, info.toString());
    }

    public void setPaused(boolean p) {
        if (!is_multiplayer && p)
            paused++;
        else if (!is_multiplayer && !p)
            paused--;
        // FIXME er ikke et problem naar hver gui_root har sin egen World
        //      assert world_singleton.paused >= 0;
    }

    public boolean isPaused() {
        return paused > 0;
    }

    private void sendStatusUpdate() {
        int[] status = new int[num_participants];
        for (int i = 0; i < status.length; i++) {
            Peer peer = getPeerFromClientID(i);
            if (peer != null)
                status[i] = peer.getPlayer().getStatus();
        }
        Network.getMatchmakingClient().getInterface().updateGameStatus(getTick(), status);
    }

    private @NonNull Iterator<Peer> getPeerIterator() {
        return peer_to_player.keySet().iterator();
    }

    public @NonNull PeerHubInterface getInterface() {
        return peerhubs_interface;
    }

    private void processStall() {
        stall_handler.processStall(getTick());
    }

    private void removePeerFromActiveList(@NonNull Peer peer) {
        IO.println("Removing from active list:" + peer);
        peer_index_to_peer[peer.getPeerIndex()] = null;
        peer.getPlayer().setPreferredGamespeed(World.GAMESPEED_DONTCARE);
    }

    @Override
    public void updateChecksum(@NonNull StateChecksum sum) {
        sum.update(getTick());
        sum.update(checksum.getValue());
    }

    public void peerDisconnected(@NonNull Peer peer, String reason) {
        // In a singleplayer/loopback session (no real remote human participants), the local
        // player's own connection has no actual network to disconnect FROM - there is no remote
        // machine, no real latency, and no second independent simulation to desync against. A
        // "disconnect" reported for that connection here can only be something misfiring rather
        // than an actual problem: either the router/session layer's own timeout mechanism
        // misfiring because this client's tick processing fell behind its expected pace (see the
        // matching comment on the stale-event handling above, which already identified this same
        // timeout as the source of that race), or peerChecksumError() comparing against nothing,
        // since there's no other peer's checksum to have diverged from in the first place.
        // Previously either was still processed as a real disconnect: tearing the peer out of the
        // active list, showing "[player] has left the game" to a player who never actually left,
        // and kicking off the exact stall/ignore-stale-event cascade seen in reports of the game
        // feeling laggy and then abruptly ending. Recognizing and ignoring this specific
        // combination - local player, singleplayer session - fixes the false report directly,
        // rather than only ever trying to prevent every possible source of the underlying
        // slowness that triggers it. A genuine disconnect of a REMOTE peer, or of the local
        // player in an actual multiplayer session (where a real desync or connection loss is
        // possible), is unaffected and still handled exactly as before.
        // //added by ikill240c
        if (!is_multiplayer && peer.getPeerIndex() == local_peer_index) { //added by ikill240c
            IO.println("Ignoring spurious self-disconnect of the local player in a singleplayer session (reason: " //added by ikill240c
                    + reason + ") - likely a router timeout from this client falling behind, not a real disconnect."); //added by ikill240c
            return; //added by ikill240c
        } //added by ikill240c
        Player player = getPlayerFromPeer(peer);
        if (player == null)
            return;
        peer_to_player.remove(peer);
        player_to_peer.remove(player);
        int peer_index = peer.getPeerIndex();
        removePeerFromActiveList(peer);
        String left_game_message = i18n("left_game", peer.getPlayerInfo().getName(), reason);
        receiveChat(SYSTEM_NAME, left_game_message, false);
        if (getFreeQuitTicksLeft(local_player.getWorld()) >= 0 && Network.getMatchmakingClient().isConnected())
            Network.getMatchmakingClient().getInterface().gameQuitNotify(peer.getPlayerInfo().getName());
    }

    public void sendChat(String text, boolean team_only) {
        Iterator<Peer> it = getPeerIterator();
        int local_team = local_player.getPlayerInfo().getTeam();
        while (it.hasNext()) {
            Peer peer = it.next();
            int peer_team = peer.getPlayerInfo().getTeam();
            if (!team_only || local_team == peer_team)
                peer.getPeerHubInterface().chat(text, team_only);
        }
    }

    public void sendBeacon(float x, float y) {
        Iterator<Peer> it = getPeerIterator();
        int local_team = local_player.getPlayerInfo().getTeam();
        while (it.hasNext()) {
            Peer peer = it.next();
            int peer_team = peer.getPlayerInfo().getTeam();
            if (local_team == peer_team)
                peer.getPeerHubInterface().beacon(x, y);
        }
        // getPeerIterator() only ever yields REMOTE peers, so without this, placing a beacon never
        // triggered anything on the LOCAL client itself - no notification, and critically, no
        // onAllyBeacon() call for this client's own AdvancedAI-controlled teammates, since every
        // peer in this lockstep game simulates ALL players' AI locally (see receiveBeacon()'s own
        // comment below), not just remote ones. Remote peers only ever heard about a beacon if
        // THEY placed it - a beacon placed here never came back around to affect anything on this
        // same client, matching reports of the beacon system "not working", especially playing
        // solo with AI teammates where there are no remote peers to relay through at all. Mirrors
        // exactly what Peer.beacon() does when a remote peer's placement arrives here - see its own
        // call to receiveBeacon() for the same owner-name convention. //added by ikill240c
        receiveBeacon(x, y, local_player.getPlayerInfo().getName()); //added by ikill240c
    }

    public void receiveChat(@NonNull String name, @NonNull String text, boolean team) {
        if (team)
            Network.getChatHub().chat(new ChatMessage(name, text, ChatMessage.Type.TEAM));
        else
            Network.getChatHub().chat(new ChatMessage(name, text, ChatMessage.Type.NORMAL));
    }

    public void receiveBeacon(float x, float y, @NonNull String owner) {
        if (!ChatCommand.isIgnoring(owner))
            notification_manager.newBeacon(manager, local_player, x, y);
        // sendBeacon() only ever relays to peers on the SAME team as whoever placed it, so simply
        // receiving this call at all already means local_player is on that team - every
        // AdvancedAI-controlled teammate (this client also simulates every player's AI, not just
        // local_player's, since AI decisions must be deterministic and identical across all peers
        // in this lockstep game) should respond to it. Lets a human player call in a fixed
        // reinforcement from their AI teammates by placing a beacon. //added by ikill240c
        int local_team = local_player.getPlayerInfo().getTeam(); //added by ikill240c
        for (Player player : local_player.getWorld().getPlayers()) { //added by ikill240c
            if (player.getPlayerInfo().getTeam() == local_team //added by ikill240c
                    && player.getAI() instanceof AdvancedAI advanced_ai) { //added by ikill240c
                advanced_ai.onAllyBeacon(x, y); //added by ikill240c
            } //added by ikill240c
        } //added by ikill240c
    }

    private void closeNetwork() {
        router_client.close();
        if (router != null)
            router.close();
        Object[] peers = peer_to_player.keySet().toArray();
        for (Object peer1 : peers) {
            Peer peer = (Peer) peer1;
            if (peer.getPlayer() != local_player) {
                peerDisconnected(peer, "Synchronization failed");
            }
        }
        stall_handler.stopStall();
        leaveGame();
    }

    public void close() {
        if (spectatorController != null) PeerHubSpectatorController.clearInstance(spectatorController);
        closeNetwork();
        LocalEventQueue.getQueue().getManager().removeAnimation(this);
        IO.println("PeerHub closed");
    }

    private static int getFreeQuitTicksLeft(@NonNull World world) {
        return (int) (FREE_QUIT_TIME / world.getSecondsPerTick()) - world.getTick();
    }

    public static float getFreeQuitTimeLeft(@NonNull World world) {
        int left = getFreeQuitTicksLeft(world);
        return left * AnimationManager.ANIMATION_SECONDS_PER_TICK;
    }

    public void leaveGame() {
        if (Network.getMatchmakingClient().isConnected()) {
            if (getFreeQuitTicksLeft(local_player.getWorld()) >= 0) {
                Network.getMatchmakingClient().getInterface().gameQuitNotify(local_player.getPlayerInfo().getName());
            } else {
                Network.getMatchmakingClient().getInterface().gameLostNotify();
            }
        }
    }

    public void gameWon() {
        if (Network.getMatchmakingClient().isConnected()) {
            Network.getMatchmakingClient().getInterface().gameWonNotify();
            waiting_for_ack = true;
        }
    }

    public static void receivedAck() {
        waiting_for_ack = false;
    }

    public static boolean isWaitingForAck() {
        return waiting_for_ack;
    }
}
