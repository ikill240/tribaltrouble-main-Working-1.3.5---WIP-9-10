package com.oddlabs.tt.net;

import com.oddlabs.matchmaking.Game;
import com.oddlabs.matchmaking.MatchmakingServerInterface;
import com.oddlabs.net.ARMIEvent;
import com.oddlabs.net.ARMIEventBroker;
import com.oddlabs.net.ARMIInterfaceMethods;
import com.oddlabs.net.AbstractConnection;
import com.oddlabs.net.Connection;
import com.oddlabs.net.ConnectionInterface;
import com.oddlabs.net.IllegalARMIEventException;
import com.oddlabs.net.NetworkSelector;
import com.oddlabs.tt.form.ProgressForm;
import com.oddlabs.tt.global.Globals;
import com.oddlabs.tt.gui.GUI;
import com.oddlabs.tt.landscape.WorldParameters;
import com.oddlabs.tt.player.Player;
import com.oddlabs.tt.player.UnitInfo;
import com.oddlabs.tt.resource.CustomMapGenerator; //added by ikill240c
import com.oddlabs.tt.resource.WorldGenerator;
import com.oddlabs.tt.viewer.InGameInfo;
import com.oddlabs.util.Utils;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayOutputStream; //added by ikill240c
import java.io.IOException;
import java.net.InetSocketAddress;

public final class Client implements ARMIEventBroker, GameClientInterface, ConnectionInterface {
    private static final int CONNECTING = 1;
    private static final int NEGOTIATING = 2;
    private static final int CLOSED = 5;

    private final @NonNull AbstractConnection connection;

    private final ARMIInterfaceMethods interface_methods = new ARMIInterfaceMethods(GameClientInterface.class);
    private final WorldParameters world_params;
    private final @NonNull GameServerInterface gameserver_interface;
    private final UnitInfo @NonNull [] unit_infos;
    private final WorldInitAction initial_action;
    private final InGameInfo ingame_info;
    private final GUI gui;
    private final @NonNull NetworkSelector network;
    private final Runnable cleanup_action;
    private int state = CONNECTING;
    private int session_id;

    private @Nullable WorldGenerator generator = null;
    // Custom map download in progress (null when none) and the last progress step shown in the chat. //added by ikill240c
    private @Nullable ByteArrayOutputStream custom_map_download; //added by ikill240c
    private int custom_map_progress_shown; //added by ikill240c

    private PlayerSlot[] player_slots;
    private short player_slot = -1;
    private boolean error_while_fading;
    private ConfigurationListener configuration_listener;

    //	public Client(int host_id, int gametype, boolean rated, int start_speed, String map_code, int initial_unit_count, Runnable initial_action, float random_start_pos, int max_unit_count) {
    public Client(Runnable cleanup_action, @NonNull NetworkSelector network, GUI gui, int host_id,
            WorldParameters world_params, InGameInfo ingame_info, WorldInitAction initial_action) {
        this.cleanup_action = cleanup_action;
        this.network = network;
        this.gui = gui;
        this.ingame_info = ingame_info;
        this.world_params = world_params;
        this.initial_action = initial_action;
        if (host_id != -1)
            this.connection = new TunnelledConnection(host_id, this);
        else
            this.connection = new Connection(network, new InetSocketAddress(Utils.getLoopbackAddress(),
                    Globals.NET_PORT), this);
        gameserver_interface = (GameServerInterface) ARMIEvent.createProxy(connection, GameServerInterface.class);

        this.unit_infos = new UnitInfo[MatchmakingServerInterface.MAX_PLAYERS];
        for (int i = 0; i < unit_infos.length; i++) {
            // Starting peons AND warriors-by-type come from the world settings, so the "starting
            // units"/"starting warriors" sliders actually change what a player spawns with. Was
            // hardcoded to 0 for all three warrior counts - the original game never spawned any
            // starting warriors at all, so 0 remains the correct default; this just makes it an
            // actual, changeable setting instead of an unconditional constant. //added by ikill240c
            int peons = world_params.getInitialUnitCount(); //added by ikill240c 2026-09-14
            int rock_warriors = world_params.getStartingRockWarriors(); //added by ikill240c 2026-09-14
            int iron_warriors = world_params.getStartingIronWarriors(); //added by ikill240c 2026-09-14
            int rubber_warriors = world_params.getStartingRubberWarriors(); //added by ikill240c 2026-09-14
            // These four counts come from independent TerrainMenu sliders with nothing enforcing
            // that they fit within Max Unit Count (also its own independent slider) - a player could
            // pick a low Max Unit Count together with high starting-warrior counts and their own
            // starting spawn wouldn't fit in their own unit container, hitting
            // `assert increaseSupply(1) == 1` ("No room for new unit in player unit container") in
            // Unit's constructor before any real gameplay even started. Clamp the total down to fit,
            // trimming the newer/more-optional warrior counts first (rubber, then iron, then rock)
            // and leaving peons - the one setting a base can't function without - alone unless even
            // peons alone would overflow an even smaller Max Unit Count, in which case they get
            // clamped too as a last resort rather than crash regardless. //added by ikill240c 2026-09-14
            int max_units = world_params.getMaxUnitCount(); //added by ikill240c 2026-09-14
            int over = peons + rock_warriors + iron_warriors + rubber_warriors - max_units; //added by ikill240c 2026-09-14
            if (over > 0) { //added by ikill240c 2026-09-14
                int rubber_trim = Math.min(rubber_warriors, over); //added by ikill240c 2026-09-14
                rubber_warriors -= rubber_trim; //added by ikill240c 2026-09-14
                over -= rubber_trim; //added by ikill240c 2026-09-14
                int iron_trim = Math.min(iron_warriors, over); //added by ikill240c 2026-09-14
                iron_warriors -= iron_trim; //added by ikill240c 2026-09-14
                over -= iron_trim; //added by ikill240c 2026-09-14
                int rock_trim = Math.min(rock_warriors, over); //added by ikill240c 2026-09-14
                rock_warriors -= rock_trim; //added by ikill240c 2026-09-14
                over -= rock_trim; //added by ikill240c 2026-09-14
                peons = Math.max(0, peons - over); //added by ikill240c 2026-09-14
            } //added by ikill240c 2026-09-14
            unit_infos[i] = new UnitInfo(false, false, 0, 0, false, peons, //added by ikill240c 2026-09-14
                    rock_warriors, iron_warriors, rubber_warriors); //added by ikill240c 2026-09-14
        }
    }

    private ConfigurationListener getConfigurationListener() {
        return configuration_listener;
    }

    public void setConfigurationListener(ConfigurationListener listener) {
        configuration_listener = listener;
    }

    public void setUnitInfo(int slot, UnitInfo unit_info) {
        this.unit_infos[slot] = unit_info;
    }

    public @NonNull GameServerInterface getServerInterface() {
        return gameserver_interface;
    }

    @Override
    public void chat(int player_slot, @Nullable String chat) {
        if (chat != null && player_slot >= 0 && player_slot < player_slots.length)
            Network.getChatHub().chat(new ChatMessage(player_slots[player_slot].getInfo().getName(), chat,
                    ChatMessage.Type.GAME_MENU));
    }

    @Override
    public void setWorldGeneratorAndPlayerSlot(Game game, WorldGenerator generator, short player_slot,
            int player_count) {
        if (state != CONNECTING)
            return;
        state = NEGOTIATING;
        this.generator = generator;
        this.player_slot = player_slot;
        getConfigurationListener().connected(this, game, generator, player_slot, player_count);
        gameserver_interface.reportBuild(BuildFingerprint.get()); //added by ikill240c - lets the host warn about mismatched builds
        // Custom map game: report that we already have the host's map, or ask the host to send it. //added by ikill240c
        if (generator instanceof CustomMapGenerator map) { //added by ikill240c
            if (map.hasLocalCopy()) { //added by ikill240c
                gameserver_interface.customMapReady(); //added by ikill240c
            } else { //added by ikill240c
                custom_map_download = new ByteArrayOutputStream(); //added by ikill240c
                custom_map_progress_shown = 0; //added by ikill240c
                customMapNotice("Downloading the custom map \"" + map.getMapFileName() + "\" from the host..."); //added by ikill240c
                gameserver_interface.requestCustomMap(); //added by ikill240c
            } //added by ikill240c
        } //added by ikill240c
    }

    @Override
    public void writeBufferDrained(AbstractConnection conn) {
    }

    public void close() {
        connection.close();
        state = CLOSED;
        if (cleanup_action != null)
            cleanup_action.run();
    }

    public PlayerSlot[] getPlayers() {
        return player_slots;
    }

    @Override
    public void startGame(int session_id) {
        if (state != NEGOTIATING)
            return;
        close();
        this.session_id = session_id;
        getConfigurationListener().gameStarted();
        ProgressForm.setProgressForm(network, gui, new WorldStarter(network, session_id, generator, world_params,
                player_slots, unit_infos, player_slot, ingame_info, initial_action));
    }

    @Override
    public void setPlayers(PlayerSlot @NonNull [] player_slots) {
        this.player_slots = player_slots;
        for (PlayerSlot playerSlot : player_slots) {
            if (playerSlot == null) {
                error();
                return;
            }
        }
        getConfigurationListener().setPlayers(player_slots);
    }

    @Override
    public void handle(Object sender, @NonNull ARMIEvent armi_event) {
        try {
            armi_event.execute(interface_methods, this);
        } catch (IllegalARMIEventException _) {
            error();
        }
    }

    @Override
    public void connected(AbstractConnection conn) {
    }

    @Override
    public void error(AbstractConnection conn, IOException e) {
        error();
    }

    private void error() {
        getConfigurationListener().connectionLost();
        close();
    }

    @Override //added by ikill240c
    public void receiveCustomMapChunk(int total_bytes, byte @Nullable [] data) { //added by ikill240c
        if (state != NEGOTIATING || custom_map_download == null || !(generator instanceof CustomMapGenerator map)) //added by ikill240c
            return; //added by ikill240c
        if (total_bytes < 0 || data == null) { //added by ikill240c
            custom_map_download = null; //added by ikill240c
            customMapNotice("The host couldn't send the custom map. Ask them for " + map.getMapFileName() //added by ikill240c
                    + " and put it in " + CustomMapGenerator.sharedMapsDir() + ", then join again."); //added by ikill240c
            return; //added by ikill240c
        } //added by ikill240c
        custom_map_download.write(data, 0, data.length); //added by ikill240c
        int received = custom_map_download.size(); //added by ikill240c
        int percent = total_bytes == 0 ? 100 : (int) (100L * received / total_bytes); //added by ikill240c
        if (received < total_bytes) { //added by ikill240c
            gameserver_interface.customMapChunkReceived(); //added by ikill240c - lets the host send the next piece
            if (percent >= custom_map_progress_shown + 25) { //added by ikill240c
                custom_map_progress_shown = percent - percent % 25; //added by ikill240c
                customMapNotice("Downloading the custom map... " + custom_map_progress_shown + "%"); //added by ikill240c
            } //added by ikill240c
            return; //added by ikill240c
        } //added by ikill240c
        byte[] compressed = custom_map_download.toByteArray(); //added by ikill240c
        custom_map_download = null; //added by ikill240c
        try { //added by ikill240c
            java.nio.file.Path saved = map.installDownloadedMap(compressed); //added by ikill240c
            customMapNotice("Custom map received and saved to " + saved); //added by ikill240c
            gameserver_interface.customMapReady(); //added by ikill240c
        } catch (IOException | RuntimeException e) { //added by ikill240c
            customMapNotice("Couldn't save the custom map: " + e.getMessage()); //added by ikill240c
        } //added by ikill240c
    } //added by ikill240c

    @Override //added by ikill240c
    public void customMapNotice(@Nullable String text) { //added by ikill240c
        if (text != null) //added by ikill240c
            Network.getChatHub().chat(new ChatMessage("Custom map", text, ChatMessage.Type.GAME_MENU)); //added by ikill240c
    } //added by ikill240c

    @Override //added by ikill240c
    public void lobbyNotice(@Nullable String label, @Nullable String text) { //added by ikill240c
        if (label != null && text != null) //added by ikill240c
            Network.getChatHub().chat(new ChatMessage(label, text, ChatMessage.Type.GAME_MENU)); //added by ikill240c
    } //added by ikill240c
}
