package com.oddlabs.tt.net;

import com.oddlabs.net.ARMIEvent;
import com.oddlabs.net.ARMIHandlerException; //added by ikill240c
import com.oddlabs.net.ARMIInterfaceMethods;
import com.oddlabs.net.IllegalARMIEventException;
import com.oddlabs.tt.player.Player;
import com.oddlabs.tt.player.PlayerInfo;
import com.oddlabs.tt.player.PlayerInterface;
import org.jspecify.annotations.NonNull;

import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level; //added by ikill240c
import java.util.logging.Logger; //added by ikill240c

public final class Peer implements PeerHubInterface {
    private static final Logger logger = Logger.getLogger(Peer.class.getName()); //added by ikill240c

    private final GameArgumentReader argument_reader;
    private final int peer_index;
    private final Player player;
    private final PeerHub peer_hub;
    private final List<GameEvent> event_queue = new LinkedList<>();
    private final ARMIInterfaceMethods interface_methods = new ARMIInterfaceMethods(PlayerInterface.class);
    private final PeerHubInterface peerhub_interface;

    public Peer(PeerHub peer_hub, int peer_index, Player player, GameArgumentReader argument_reader,
            PeerHubInterface peerhub_interface) {
        this.peerhub_interface = peerhub_interface;
        this.peer_index = peer_index;
        this.peer_hub = peer_hub;
        this.player = player;
        this.argument_reader = argument_reader;
    }

    public int getPeerIndex() {
        return peer_index;
    }

    @Override
    public @NonNull String toString() {
        return "player: " + player.toString();
    }

    public void addEvent(int tick, ARMIEvent event) {
        event_queue.add(new GameEvent(tick, event));
    }

    public void executeEvents(int tick) throws IllegalARMIEventException {
        while (!event_queue.isEmpty()) {
            GameEvent game_event = event_queue.getFirst();
            if (game_event.tick != tick)
                return;
            event_queue.removeFirst();
            // A command that throws while executing is a bug in the game logic handling it, not this
            // player sending something illegal. Previously the exception escaped this loop, which did two
            // things wrong: PeerHub disconnected this player over it (the "kicked while still alive"
            // reports - e.g. while ordering units into a tower), and any remaining commands queued for this
            // same tick were stranded at the head of the queue with a tick number already in the past, so the
            // `game_event.tick != tick` check above returned early on every later tick and none of this
            // player's commands ever ran again. Now the failure is logged with its real stack trace and the
            // loop moves on to the next command. Every lockstep client executes the same commands against the
            // same state, so they all hit the same exception at the same point and skip it identically; if
            // they ever did diverge, the existing checksum check reports it as a desync instead of a wrong
            // kick. Malformed events (bad method id / arguments) still propagate and disconnect as before.
            // //added by ikill240c
            try { //added by ikill240c
                game_event.event.execute(interface_methods, argument_reader, player);
            } catch (ARMIHandlerException e) { //added by ikill240c
                logger.log(Level.SEVERE, "Command from " + player.getPlayerInfo().getName() + " failed at tick " //added by ikill240c
                        + tick + "; ignored, player stays connected", e.getCause()); //added by ikill240c
            } //added by ikill240c
        }
    }

    @Override
    public void chat(@NonNull String text, boolean team) {
        peer_hub.receiveChat(player.getPlayerInfo().getName(), text, team);
    }

    @Override
    public void beacon(float x, float y) {
        peer_hub.receiveBeacon(x, y, player.getPlayerInfo().getName());
    }

    public PeerHubInterface getPeerHubInterface() {
        return peerhub_interface;
    }

    public @NonNull PlayerInfo getPlayerInfo() {
        return player.getPlayerInfo();
    }

    public Player getPlayer() {
        return player;
    }

    private record GameEvent(int tick, ARMIEvent event) {
    }
}
