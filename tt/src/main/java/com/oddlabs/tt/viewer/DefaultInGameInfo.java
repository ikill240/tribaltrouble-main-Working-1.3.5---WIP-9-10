package com.oddlabs.tt.viewer;

import com.oddlabs.matchmaking.Preset; //added by ikill240c
import com.oddlabs.matchmaking.RosterTemplate; //added by ikill240c
import com.oddlabs.matchmaking.StandardOptions; //added by ikill240c
import com.oddlabs.matchmaking.WorldConfig; //added by ikill240c
import com.oddlabs.tt.delegate.GameStatsDelegate;
import com.oddlabs.tt.delegate.InGameMainMenu;
import com.oddlabs.tt.delegate.Menu;
import com.oddlabs.tt.form.TerrainMenu;
import com.oddlabs.tt.gui.GUIObject;
import com.oddlabs.tt.gui.Group;
import com.oddlabs.tt.gui.HorizButton;
import com.oddlabs.tt.gui.Label;
import com.oddlabs.tt.gui.ScrollableGroup;
import com.oddlabs.tt.gui.Skin;
import com.oddlabs.tt.model.RacesResources;
import com.oddlabs.tt.player.AdvancedAI; //added by ikill240c
import com.oddlabs.tt.player.Player;
import com.oddlabs.tt.player.PlayerInfo;
import com.oddlabs.tt.render.Renderer;
import com.oddlabs.tt.util.Utils;
import org.joml.Vector4f;
import org.joml.Vector4fc;
import org.jspecify.annotations.NonNull;

import java.util.ResourceBundle;

import static com.oddlabs.tt.gui.Placement.BOTTOM_LEFT;
import static com.oddlabs.tt.gui.Placement.RIGHT_MID;
import static com.oddlabs.tt.gui.Placement.LEFT_MID;

public class DefaultInGameInfo implements InGameInfo {
    private static final ResourceBundle terrain_menu_bundle = ResourceBundle.getBundle(TerrainMenu.class.getName());
    private boolean replay_island_flag;

    private void addAbortButton(@NonNull InGameMainMenu menu) {
        String abort_text = Menu.i18n("end_game");
        menu.addAbortButton(abort_text);
    }

    @Override
    public float getRandomStartPosition() {
        return 0f;
    }

    @Override
    public boolean isRated() {
        return false;
    }

    @Override
    public void addGameOverGUI(@NonNull WorldViewer viewer, @NonNull GameStatsDelegate delegate, int header_y,
            @NonNull Group group) {
        addGameOverGUI(viewer, delegate, header_y, group, true);
    }

    protected final void addGameOverGUI(final @NonNull WorldViewer viewer, final @NonNull GameStatsDelegate delegate,
            int header_y, @NonNull Group group, boolean replay) {
        String map_code_str = GameStatsDelegate.i18n("map_code", viewer.getParameters().getMapcode());
        Label map_code = new Label(map_code_str, Skin.getSkin().getEditFont());
        delegate.addChild(map_code);
        map_code.setPos((delegate.getWidth() - map_code.getWidth()) / 2, header_y - map_code.getHeight());

        HorizButton button_replay = new HorizButton(GameStatsDelegate.i18n("replay_island"), 150);
        button_replay.addMouseClickListener((_, _, _, _) -> {
            replay_island_flag = true;
            delegate.startMenu();
        });
        HorizButton button_observer = new HorizButton(GameStatsDelegate.i18n("observer_mode"), 150);
        button_observer.addMouseClickListener((_, _, _, _) -> {
            delegate.getViewer().getDelegate().setObserverMode();
            delegate.pop();
        });

        HorizButton button_end = new HorizButton(GameStatsDelegate.i18n("main_menu"), 150);
        button_end.addMouseClickListener((_, _, _, _) -> delegate.startMenu());

        if (replay)
            group.addChild(button_replay);
        group.addChild(button_observer);
        group.addChild(button_end);

        button_end.place();
        button_observer.place(button_end, LEFT_MID);
        if (replay)
            button_replay.place(button_observer, LEFT_MID);
    }

    private void addGameInfos(@NonNull WorldViewer viewer, @NonNull Menu menu, @NonNull Group game_infos) {
        Player[] players = viewer.getWorld().getPlayers();
        ScrollableGroup scrollable = new ScrollableGroup(300, 50);

        // Pre-calculate largest name width for column alignment
        var nameLabels = new java.util.ArrayList<Label>();
        int largestLabel = 0;
        for (Player player : players) {
            Label name = new Label(player.getPlayerInfo().getName(), Skin.getSkin().getHeadlineFont());
            nameLabels.add(name);
            if (name.getWidth() > largestLabel)
                largestLabel = name.getWidth();
        }

        GUIObject last_name = null;
        for (int i = 0; i < players.length; i++) {
            PlayerInfo player_info = players[i].getPlayerInfo();
            var color_floats = players[i].getColor();
            Vector4fc color = viewer.getPeerHub().isAlive(players[i]) ? color_floats : new Vector4f(color_floats.x(),
                    color_floats.y(), color_floats.z(), .25f);

            Label name = nameLabels.get(i);
            name.setDim(largestLabel, name.getHeight());
            name.setColor(color);

            String race_str = RacesResources.getRaceName(player_info.getRace());
            Label race = new Label(race_str, Skin.getSkin().getHeadlineFont()).setColor(color);
            String team_str = Utils.getBundleString(terrain_menu_bundle, "team", Integer.toString(
                    player_info.getTeam() + 1));
            Label team = new Label(team_str, Skin.getSkin().getHeadlineFont()).setColor(color);

            scrollable.addChild(name);
            if (last_name != null) name.place(last_name, BOTTOM_LEFT);
            else name.place();
            last_name = name;

            scrollable.addChild(race);
            race.place(last_name, RIGHT_MID);

            scrollable.addChild(team);
            team.place(race, RIGHT_MID);
        }
        scrollable.compileCanvas();
        game_infos.addChild(scrollable);

        scrollable.place();
        game_infos.compileCanvas();
    }

    @Override
    public void addGUI(@NonNull WorldViewer viewer, @NonNull InGameMainMenu menu, @NonNull Group game_infos) {
        addAbortButton(menu);
        addGameInfos(viewer, menu, game_infos);
    }

    @Override
    public final void close(@NonNull WorldViewer viewer) {
        if (replay_island_flag) {
            TerrainMenu menu = new TerrainMenu(viewer.getNetwork(), viewer.getGUIRoot(), null, false, null);
            // Was just parseMapcode() + startGame() - that restores the MAP (size, terrain type,
            // hills/vegetation/supplies sliders, seed), but nothing about the player roster: player
            // count, races, teams, and AI difficulties were always left at the fresh menu's
            // defaults (2 players, both easy AI, no teams configured) regardless of what the just-
            // ended game actually had. "Replay Island" only ever restored the island itself, never
            // who was actually playing on it. Building a RosterTemplate from the just-ended game's
            // live players and running it through the exact same applyPreset() the presets system
            // already uses fixes this without duplicating that application logic.
            // //added by ikill240c
            menu.applyPreset(buildReplayPreset(viewer)); //added by ikill240c
            // parseMapcode() runs AFTER applyPreset() (not before) because applyPreset() also
            // applies preset.getWorld() (map size/terrain/sliders) - since buildReplayPreset()
            // below only supplies WorldConfig.defaults() for that part (the actual map is restored
            // by mapcode, not by trying to reverse-engineer a WorldConfig from the live
            // HeightMap), parseMapcode() needs the final say so its restored values aren't
            // clobbered back to those defaults. //added by ikill240c
            menu.parseMapcode(viewer.getParameters().getMapcode());
            menu.startGame();
        } else
            Renderer.startMenu(viewer.getNetwork(), viewer.getGUIRoot().getGUI());
    }

    // Captures the just-ended game's player roster (race, team, and AI difficulty per player) as a
    // Preset, so it can be re-applied to the fresh replay menu via the exact same applyPreset()
    // mechanism the saved-presets system already uses. World/mode options are left at defaults
    // deliberately - the map itself is restored separately via parseMapcode() in close() above,
    // and mode-specific options beyond the roster aren't part of what "Replay Island" has ever
    // claimed to restore. //added by ikill240c
    private @NonNull Preset buildReplayPreset(@NonNull WorldViewer viewer) { //added by ikill240c
        Player[] players = viewer.getWorld().getPlayers();
        RosterTemplate.Slot[] slots = new RosterTemplate.Slot[players.length]; //added by ikill240c
        for (int i = 0; i < players.length; i++) { //added by ikill240c
            Player player = players[i]; //added by ikill240c
            RosterTemplate.Fill fill; //added by ikill240c
            if (i == 0) { //added by ikill240c
                // Slot 0 is always the local human player's own seat, regardless of fill type -
                // matches fillToDifficultyIndex()'s own special-case for slot 0 in TerrainMenu.
                // //added by ikill240c
                fill = RosterTemplate.Fill.HOST; //added by ikill240c
            } else if (player.getAI() instanceof AdvancedAI ai) { //added by ikill240c
                fill = switch (ai.getDifficulty()) { //added by ikill240c
                    case 0 -> RosterTemplate.Fill.EASY_AI; //added by ikill240c
                    case 1 -> RosterTemplate.Fill.NORMAL_AI; //added by ikill240c
                    case 2 -> RosterTemplate.Fill.HARD_AI; //added by ikill240c
                    default -> RosterTemplate.Fill.INSANE_AI; //added by ikill240c
                };
            } else { //added by ikill240c
                // Not slot 0 and not AI - either another human (not present in a fresh SP replay
                // lobby, so treated as an open seat) or some other non-AdvancedAI controller.
                // //added by ikill240c
                fill = RosterTemplate.Fill.OPEN; //added by ikill240c
            } //added by ikill240c
            slots[i] = new RosterTemplate.Slot(fill, player.getPlayerInfo().getRace(), //added by ikill240c
                    player.getPlayerInfo().getTeam()); //added by ikill240c
        } //added by ikill240c
        return new Preset("replay_island", "Replay Island", WorldConfig.defaults(), //added by ikill240c
                StandardOptions.defaults(), new RosterTemplate(slots), false); //added by ikill240c
    }

    @Override
    public final void abort(@NonNull WorldViewer viewer) {
        viewer.getGUIRoot().pushDelegate(new GameStatsDelegate(viewer, viewer.getGUIRoot().getDelegate().getCamera(),
                Menu.i18n("game_aborted")));
    }

    @Override
    public boolean isMultiplayer() {
        return false;
    }
}
