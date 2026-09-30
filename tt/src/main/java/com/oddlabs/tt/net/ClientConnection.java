package com.oddlabs.tt.net;

import com.oddlabs.net.ARMIEvent;
import com.oddlabs.net.AbstractConnection;
import org.jspecify.annotations.NonNull;

final class ClientConnection {
    private final AbstractConnection connection;
    private final @NonNull GameClientInterface gameclient_interface;
    private final ClientInfo client;
    // Custom map transfer state (only used by Server): next byte offset to send, or -1 when no transfer is running;
    // and whether this player has reported an identical copy of the map. //added by ikill240c
    int custom_map_offset = -1; //added by ikill240c
    boolean has_custom_map; //added by ikill240c
    int custom_map_in_flight; //added by ikill240c - pieces sent but not yet acknowledged

    public ClientConnection(AbstractConnection conn, ClientInfo client) {
        this.connection = conn;
        this.gameclient_interface = (GameClientInterface) ARMIEvent.createProxy(connection, GameClientInterface.class);
        this.client = client;
    }

    public @NonNull GameClientInterface getClientInterface() {
        return gameclient_interface;
    }

    public AbstractConnection getConnection() {
        return connection;
    }

    public ClientInfo getClient() {
        return client;
    }
}
