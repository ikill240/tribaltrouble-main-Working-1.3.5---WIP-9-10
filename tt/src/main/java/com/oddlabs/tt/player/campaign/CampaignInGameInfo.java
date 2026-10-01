package com.oddlabs.tt.player.campaign;

import com.oddlabs.tt.delegate.GameStatsDelegate;
import com.oddlabs.tt.delegate.InGameMainMenu;
import com.oddlabs.tt.delegate.Menu;
import com.oddlabs.tt.gui.Group;
import com.oddlabs.tt.gui.HorizButton;
import com.oddlabs.tt.gui.LabelBox;
import com.oddlabs.tt.gui.OKButton;
import com.oddlabs.tt.gui.Skin;
import com.oddlabs.tt.net.PlayerSlot; //added by ikill240c
import com.oddlabs.tt.render.Renderer;
import com.oddlabs.tt.viewer.InGameInfo;
import com.oddlabs.tt.viewer.WorldViewer;
import org.jspecify.annotations.NonNull;

import static com.oddlabs.tt.gui.Placement.BOTTOM_LEFT;

final class CampaignInGameInfo implements InGameInfo {
    private final Campaign campaign;

    public CampaignInGameInfo(Campaign campaign) {
        this.campaign = campaign;
    }

    // Islands ask for Easy/Normal/Hard AIs (AdvancedAI) for their real opponents and allies, and special scripted
    // types (AI_NEUTRAL_CAMPAIGN, AI_PASSIVE_CAMPAIGN) for story characters. With an enemy AI chosen for this
    // campaign, the Easy/Normal/Hard slots get that AI instead; story characters and the campaign difficulty
    // setting (unit counts, reinforcements) are untouched. //added by ikill240c
    @Override //added by ikill240c
    public int resolveAIDifficulty(int ai_difficulty) { //added by ikill240c
        int enemy_ai = campaign.getState().getEnemyAI(); //added by ikill240c
        if (enemy_ai == 0) //added by ikill240c
            return ai_difficulty; //added by ikill240c
        return switch (ai_difficulty) { //added by ikill240c
            case PlayerSlot.AI_EASY, PlayerSlot.AI_NORMAL, PlayerSlot.AI_HARD -> enemy_ai; //added by ikill240c
            default -> ai_difficulty; //added by ikill240c
        }; //added by ikill240c
    } //added by ikill240c

    @Override
    public boolean isRated() {
        return false;
    }

    @Override
    public boolean isMultiplayer() {
        return false;
    }

    @Override
    public float getRandomStartPosition() {
        return 0f;
    }

    @Override
    public void addGUI(@NonNull WorldViewer viewer, @NonNull InGameMainMenu menu, @NonNull Group game_infos) {
        menu.addAbortButton(Menu.i18n("end_game"));
        int screen_width = viewer.getGUIRoot().getWidth();
        LabelBox label_objective = new LabelBox(Menu.i18n("objective"), Skin.getSkin().getEditFont(), screen_width / 2);
        LabelBox label_description = new LabelBox(campaign.getCurrentObjective(), Skin.getSkin().getEditFont(),
                screen_width / 2);
        game_infos.addChild(label_objective);
        game_infos.addChild(label_description);
        label_objective.place();
        label_description.place(label_objective, BOTTOM_LEFT);
        game_infos.compileCanvas();
    }

    @Override
    public void addGameOverGUI(WorldViewer viewer, final @NonNull GameStatsDelegate delegate, int header_y,
            @NonNull Group group) {
        HorizButton button_ok = new OKButton(150);
        button_ok.addMouseClickListener((_, _, _, _) -> delegate.startMenu());

        group.addChild(button_ok);
        button_ok.place();
    }

    @Override
    public void close(@NonNull WorldViewer viewer) {
        if (campaign.getState().getIslandState(0) != CampaignState.ISLAND_COMPLETED) {
            Renderer.startMenu(viewer.getNetwork(), viewer.getGUIRoot().getGUI());
        } else {
            campaign.pushDelegate(viewer.getNetwork(), viewer.getGUIRoot().getGUI());
        }

    }

    @Override
    public void abort(@NonNull WorldViewer viewer) {
        viewer.getGUIRoot().pushDelegate(new GameStatsDelegate(viewer, viewer.getGUIRoot().getDelegate().getCamera(),
                Menu.i18n("game_aborted")));
        campaign.doDefeated();
    }
}
