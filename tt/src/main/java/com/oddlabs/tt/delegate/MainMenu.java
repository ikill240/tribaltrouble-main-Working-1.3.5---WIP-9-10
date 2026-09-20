package com.oddlabs.tt.delegate;

import com.oddlabs.mapeditor.MapEditor; //added by ikill240c
import com.oddlabs.mapeditor.PreviewRequest; //added by ikill240c
import com.oddlabs.net.NetworkSelector;
import com.oddlabs.tt.camera.Camera;
import com.oddlabs.tt.event.LocalEventQueue; //added by ikill240c
import com.oddlabs.tt.form.CampaignForm;
import com.oddlabs.tt.form.LoginForm;
import com.oddlabs.tt.form.MatchmakingConnectingForm;
import com.oddlabs.tt.form.SelectGameMenu;
import com.oddlabs.tt.form.TerrainMenuForm;
import com.oddlabs.tt.form.TutorialForm;
import com.oddlabs.tt.global.Settings;
import com.oddlabs.tt.gui.GUIRoot;
import com.oddlabs.tt.gui.MenuButton;
import com.oddlabs.tt.net.Network;
import com.oddlabs.tt.render.Renderer; //added by ikill240c
import com.oddlabs.tt.steam.SteamManager;
import org.jspecify.annotations.NonNull;

import java.awt.event.WindowAdapter; //added by ikill240c
import java.awt.event.WindowEvent; //added by ikill240c
import javax.swing.SwingUtilities; //added by ikill240c

/**
 * The game main menu
 */
public final class MainMenu extends Menu {
    public MainMenu(@NonNull NetworkSelector network, @NonNull GUIRoot gui_root, @NonNull Camera camera) {
        super(network, gui_root, camera);
        reload();
        SteamManager.clearRichPresence();
        SteamManager.setInActiveWorld(false);
        // Polls once per frame, on this thread (the game's own main loop, via
        // Animated/AnimationManager) for a pending "preview this map" request from the
        // standalone Swing map editor. Never touches the editor's own objects, and the editor
        // never touches this menu's objects directly - PreviewRequest is the only thing shared
        // between them, and it's a plain thread-safe value holder, not a GUI object. See
        // PreviewRequest's own class comment for the full reasoning. //added by ikill240c
        LocalEventQueue.getQueue().getManager().registerAnimation(t -> { //added by ikill240c
            String path = PreviewRequest.pollAndClear(); //added by ikill240c
            if (path != null) { //added by ikill240c
                TerrainMenuForm form = new TerrainMenuForm(getNetwork(), getGUIRoot(), MainMenu.this); //added by ikill240c
                form.getTerrainMenu().preselectCustomMap(path); //added by ikill240c
                setMenu(form); //added by ikill240c
            } //added by ikill240c
        }); //added by ikill240c
    }

    private void addGameTypeButtons() {
        MenuButton tutorial = new MenuButton(Menu.i18n("tutorial"), COLOR_NORMAL, COLOR_ACTIVE);
        tutorial.addMouseClickListener((_, _, _, _) -> setMenu(new TutorialForm(getNetwork(), getGUIRoot())));
        addChild(tutorial);

        MenuButton campaign_menu = new MenuButton(Menu.i18n("campaign"), COLOR_NORMAL, COLOR_ACTIVE);
        campaign_menu.addMouseClickListener((_, _, _, _) -> setMenu(new CampaignForm(getNetwork(), getGUIRoot(),
                MainMenu.this)));
        addChild(campaign_menu);

        MenuButton single_player = new MenuButton(Menu.i18n("skirmish"), COLOR_NORMAL, COLOR_ACTIVE);
        single_player.addMouseClickListener((_, _, _, _) -> setMenu(new TerrainMenuForm(getNetwork(), getGUIRoot(),
                MainMenu.this)));
        addChild(single_player);

        if (!Settings.getSettings().hide_multiplayer) {
            MenuButton multi_player = new MenuButton(Menu.i18n("multiplayer"), COLOR_NORMAL, COLOR_ACTIVE);
            multi_player.addMouseClickListener((_, _, _, _) -> {
                if (Network.getMatchmakingClient().isConnected()) {
                    new SelectGameMenu(getNetwork(), getGUIRoot(), MainMenu.this);
                } else {
                    Network.getMatchmakingClient().close();
                    if (Settings.getSettings().isOfficialServer() && SteamManager.getInstance() != null) {
                        new MatchmakingConnectingForm(getNetwork(), getGUIRoot(), null, MainMenu.this, true);
                    } else {
                        new LoginForm(getNetwork(), getGUIRoot(), MainMenu.this);
                    }
                }
            });
            addChild(multi_player);
        }

        // Local tool, unrelated to multiplayer connectivity - deliberately outside the
        // hide_multiplayer check above, so it stays available even on builds/settings that hide
        // the multiplayer button entirely. Opens as a second Swing window in this SAME JVM rather
        // than a separate process, since MainMenu already has com.oddlabs.mapeditor.MapEditor
        // (in the shared `common` module) on its classpath - no packaging/launch-path guessing
        // needed. //added by ikill240c
        MenuButton create_custom_map = new MenuButton(Menu.i18n("create_custom_map"), COLOR_NORMAL, //added by ikill240c
                COLOR_ACTIVE); //added by ikill240c
        create_custom_map.addMouseClickListener((_, _, _, _) -> { //added by ikill240c
            boolean wasFullscreen = Settings.getSettings().fullscreen; //added by ikill240c
            if (wasFullscreen) { //added by ikill240c
                Renderer.getRenderer().toggleFullscreen(); //added by ikill240c
            } //added by ikill240c
            SwingUtilities.invokeLater(() -> { //added by ikill240c
                MapEditor editor = new MapEditor(); //added by ikill240c
                if (wasFullscreen) { //added by ikill240c
                    // Restores fullscreen only once the editor window actually closes, not
                    // immediately after opening it (unlike the modal file-dialog pattern
                    // elsewhere) - the editor is a non-blocking window the player may work in for
                    // a while. //added by ikill240c
                    editor.addWindowListener(new WindowAdapter() { //added by ikill240c
                        @Override //added by ikill240c
                        public void windowClosed(WindowEvent e) { //added by ikill240c
                            Renderer.getRenderer().toggleFullscreen(); //added by ikill240c
                        } //added by ikill240c
                    }); //added by ikill240c
                } //added by ikill240c
                editor.setVisible(true); //added by ikill240c
            }); //added by ikill240c
        }); //added by ikill240c
        addChild(create_custom_map); //added by ikill240c
    }

    @Override
    protected void addButtons() {
        addGameTypeButtons();

        addDefaultOptionsButton();

        addExitButton();

        if (Network.getMatchmakingClient().isConnected()) {
            new SelectGameMenu(getNetwork(), getGUIRoot(), this);
        }
    }
}
