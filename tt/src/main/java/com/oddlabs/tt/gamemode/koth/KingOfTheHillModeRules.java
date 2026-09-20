package com.oddlabs.tt.gamemode.koth;

import com.oddlabs.matchmaking.GameModeOption;
import com.oddlabs.tt.animation.Animated;
import com.oddlabs.tt.gamemode.GameModeRules;
import com.oddlabs.tt.gui.Label;
import com.oddlabs.tt.gui.Origin; //added by ikill240c
import com.oddlabs.tt.gui.Skin; //added by ikill240c
import com.oddlabs.tt.model.SceneryModel; //added by ikill240c
import com.oddlabs.tt.model.Unit;
import com.oddlabs.tt.pathfinder.FindOccupantFilter;
import com.oddlabs.tt.pathfinder.UnitGrid;
import com.oddlabs.tt.player.Player;
import com.oddlabs.tt.player.PlayerInfo;
import com.oddlabs.tt.viewer.WorldViewer;
import org.jspecify.annotations.NonNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

// King of the Hill: a statue sits at the map center; a team wins by keeping units within
// CAPTURE_RADIUS of it, uncontested by any other team, for HOLD_SECONDS_TO_WIN continuous seconds.
// Losing control (no one present, or more than one team present at once) resets the hold timer to
// zero rather than just pausing it - this is a deliberate design choice (a "hard reset" king-of-the-hill
// rather than a "pause and resume" one) that can be changed later if a softer version is preferred.
// //added by ikill240c
//
// NOTE: the statue's visual is one of the existing generic treasure props (RacesResources.
// getTreasures(), the same array campaign scenarios already use for decorative "statue" scenery - see
// e.g. VikingIsland7.java's SceneryModel placements) rather than a new custom model, since no new art
// assets exist for this mode specifically. Swap the TREASURE_INDEX constant below if a different one
// of the six treasure sprites looks more statue-like once seen in-game. //added by ikill240c
//
// NOTE: getOptions() now declares a real schema (previously empty, pending confirmation of
// GameModeOption's numeric-type constructor - now confirmed via GameModeOption.java). The lobby UI
// should auto-render controls for these per GameModeOption's own doc comment. However, there is
// currently NO live path from a configured GameModeOptions value into GameModeRules.onGameStart()/
// isPlayerAlive() anywhere in this codebase - confirmed by checking StandardModeRules, which declares
// OPTION_RATED the same way but never actually reads its configured value back into gameplay logic
// either. The two call sites of onGameStart() (WorldStarter.java, ReplayWorldStarter.java) only ever
// pass a WorldViewer, never a GameModeOptions. Wiring that through would mean extending the
// GameModeRules interface itself plus threading a GameModeOptions reference through World/
// WorldParameters construction - a cross-cutting change affecting every mode, not scoped to just this
// one, so it has NOT been done here. Until then, CAPTURE_RADIUS/HOLD_SECONDS_TO_WIN below stay as the
// actual hardcoded values used at runtime regardless of what the (currently inert) lobby slider shows.
// //added by ikill240c
public final class KingOfTheHillModeRules implements GameModeRules {
    private static final float CAPTURE_RADIUS = 15f; // grid units around the map-center point //added by ikill240c
    private static final float HOLD_SECONDS_TO_WIN = 120f; // continuous, uncontested hold time required //added by ikill240c
    private static final int TREASURE_INDEX = 0; // which of the 6 generic treasure sprites to use as the statue - see note above //added by ikill240c

    public static final @NonNull String OPTION_CAPTURE_RADIUS = "kingofthehill_capture_radius"; //added by ikill240c
    public static final @NonNull String OPTION_HOLD_SECONDS = "kingofthehill_hold_seconds"; //added by ikill240c

    // i18n keys below ("kingofthehill_capture_radius"/"kingofthehill_capture_radius_tip", "kingofthehill_hold_seconds"/
    // "kingofthehill_hold_seconds_tip") need adding to TerrainMenu.properties (all locale files) before this
    // schema renders correctly anywhere - this is the exact same gap as the earlier missing
    // "action.MAGIC_3" key, which crashed the game the first time the options menu tried to render it.
    // //added by ikill240c
    private static final @NonNull List<@NonNull GameModeOption> OPTIONS = List.of(
            new GameModeOption(OPTION_CAPTURE_RADIUS, GameModeOption.Type.FLOAT, CAPTURE_RADIUS,
                    "kingofthehill_capture_radius", 5f, 40f, null), //added by ikill240c - i18n_key is the label key ("kingofthehill_capture_radius"); a separate "kingofthehill_capture_radius_tip" key provides the hover tooltip, matching StandardModeRules's rated_game/rated_game_tip pattern
            new GameModeOption(OPTION_HOLD_SECONDS, GameModeOption.Type.FLOAT, HOLD_SECONDS_TO_WIN,
                    "kingofthehill_hold_seconds", 30f, 600f, null)); //added by ikill240c

    // Per-game mutable state. GameModeRules implementations are registered once as a shared instance
    // in GameModeRegistry (see StandardModeRules's registration pattern), so every game played in the
    // same process reuses this same object - all mutable state here is reset in onGameStart() to avoid
    // leaking a previous game's hold progress or winner into a new one. //added by ikill240c
    private int winning_team = PlayerInfo.TEAM_NEUTRAL; //added by ikill240c
    private int holding_team = PlayerInfo.TEAM_NEUTRAL; //added by ikill240c
    private float hold_time = 0f; //added by ikill240c
    // Promoted from onGameStart()'s local variables to fields so the AI can query the capture
    // point's location and status after game start - see the public accessors below.
    // //added by ikill240c
    private float center_x = 0f; //added by ikill240c
    private float center_y = 0f; //added by ikill240c

    // Public accessors so AdvancedAI (or any other gameplay code) can find out where the capture
    // point is and who currently holds it, in order to actually play the objective - added
    // alongside the AI logic that uses them, since nothing previously needed to read this state
    // from outside this class. //added by ikill240c
    public float getCenterX() { //added by ikill240c
        return center_x;
    } //added by ikill240c

    public float getCenterY() { //added by ikill240c
        return center_y;
    } //added by ikill240c

    public int getHoldingTeam() { //added by ikill240c
        return holding_team;
    } //added by ikill240c

    public int getWinningTeam() { //added by ikill240c
        return winning_team;
    } //added by ikill240c

    public static float getCaptureRadius() { //added by ikill240c
        return CAPTURE_RADIUS;
    } //added by ikill240c

    @Override
    public @NonNull List<@NonNull GameModeOption> getOptions() { //added by ikill240c
        return OPTIONS;
    }

    @Override
    public boolean isPlayerAlive(@NonNull Player player) { //added by ikill240c
        // Once a winning team is decided, every player NOT on that team counts as eliminated - this
        // reuses the exact generic win/loss mechanism every other mode already relies on (see
        // GameOverTrigger, which polls isPlayerAlive() for every player every tick and ends the game
        // once only one team remains "alive"). Before a winner is decided, fall back to the standard
        // survival check so players can still be eliminated the normal way (losing their whole army)
        // mid-match, same as in StandardModeRules. //added by ikill240c
        if (winning_team != PlayerInfo.TEAM_NEUTRAL) {
            return player.getPlayerInfo().getTeam() == winning_team;
        }
        int units = player.getUnitCountContainer().getNumSupplies();
        return units > 0 || player.hasActiveChieftain() || player.getQuarters() != null;
    }

    @Override
    public void onGameStart(@NonNull WorldViewer viewer) { //added by ikill240c
        // Reset all mutable state at the start of every game - see field comment above. //added by ikill240c
        winning_team = PlayerInfo.TEAM_NEUTRAL;
        holding_team = PlayerInfo.TEAM_NEUTRAL;
        hold_time = 0f;

        int center = viewer.getWorld().getHeightMap().getGridUnitsPerWorld() / 2; //added by ikill240c
        center_x = UnitGrid.coordinateFromGrid(center); //added by ikill240c - was a local variable, now a field so the AI can query it after game start (see accessors above)
        center_y = UnitGrid.coordinateFromGrid(center); //added by ikill240c

        // Place the statue itself - purely decorative (occupy=false so units can freely walk onto/
        // around it, which they need to be able to do to actually capture it), using the same
        // SceneryModel + treasure-sprite mechanism campaign scenarios already use for "statue" props.
        // //added by ikill240c
        new SceneryModel(viewer.getWorld(), center_x, center_y, 0f, 1f,
                viewer.getWorld().getRacesResources().getTreasures()[TREASURE_INDEX], 2.6f, false,
                "King of the Hill"); //added by ikill240c

        // Leader/countdown HUD label, updated every tick from within the same ticker below. Positioned
        // top-center of the screen; re-centered on resize via GUIRoot's own layout pass would need a
        // resize listener, which is out of scope here - this recenters once at creation using the
        // GUIRoot's dimensions at game start. //added by ikill240c
        Label koth_status_label = new Label("", Skin.getSkin().getHeadlineFont(), 400, Origin.AT_MIDDLE);
        koth_status_label.setPos((viewer.getGUIRoot().getWidth() - koth_status_label.getWidth()) / 2, 10);
        viewer.getGUIRoot().addChild(koth_status_label);

        // Register a per-frame ticker that checks who currently controls the capture zone and advances
        // (or resets) the hold timer. Uses the same registerAnimation() pattern already proven to work
        // for one-shot magic effects (see Convert.java/Stun.java), just with an anonymous Animated that
        // never unregisters itself, since the win check needs to keep running for the whole match.
        // //added by ikill240c
        viewer.getWorld().getAnimationManagerGameTime().registerAnimation(new Animated() { //added by ikill240c
            @Override
            public void animate(float t) {
                if (winning_team != PlayerInfo.TEAM_NEUTRAL) {
                    return; // already decided and the label already shows the final result //added by ikill240c
                }

                FindOccupantFilter<Unit> filter = new FindOccupantFilter<>(center_x, center_y, CAPTURE_RADIUS,
                        null, Unit.class); //added by ikill240c
                viewer.getWorld().getUnitGrid().scan(filter, center, center); //added by ikill240c

                // Count which team(s) currently have a living unit inside the capture radius. //added by ikill240c
                Map<Integer, Integer> team_presence = new HashMap<>();
                for (Unit u : filter.getResult()) {
                    if (u.isDead())
                        continue;
                    int team = u.getOwner().getPlayerInfo().getTeam();
                    if (team == PlayerInfo.TEAM_NEUTRAL)
                        continue; // neutral/observer units never count toward capture //added by ikill240c
                    team_presence.merge(team, 1, Integer::sum);
                }

                // Exactly one team present = they hold it. Zero teams present (empty) or more than one
                // (contested) both count as "not held" and reset progress. //added by ikill240c
                int contesting_team = team_presence.size() == 1
                        ? team_presence.keySet().iterator().next()
                        : PlayerInfo.TEAM_NEUTRAL;

                if (contesting_team != PlayerInfo.TEAM_NEUTRAL && contesting_team == holding_team) {
                    hold_time += t;
                    if (hold_time >= HOLD_SECONDS_TO_WIN) {
                        winning_team = holding_team; //added by ikill240c
                    }
                } else {
                    holding_team = contesting_team; //added by ikill240c
                    hold_time = 0f;
                }

                // Update the HUD label every tick with the current leader and remaining time, or a
                // neutral/contested message when nobody holds it outright. //added by ikill240c
                if (winning_team != PlayerInfo.TEAM_NEUTRAL) {
                    koth_status_label.setText("Team " + (winning_team + 1) + " has won the Hill!");
                } else if (holding_team != PlayerInfo.TEAM_NEUTRAL) {
                    int seconds_left = (int) Math.ceil(HOLD_SECONDS_TO_WIN - hold_time);
                    koth_status_label.setText(
                            "Team " + (holding_team + 1) + " leads the Hill - " + seconds_left + "s to win");
                } else if (team_presence.isEmpty()) {
                    koth_status_label.setText("The Hill is uncontrolled");
                } else {
                    koth_status_label.setText("The Hill is contested!");
                }
            }
        });
    }
}
