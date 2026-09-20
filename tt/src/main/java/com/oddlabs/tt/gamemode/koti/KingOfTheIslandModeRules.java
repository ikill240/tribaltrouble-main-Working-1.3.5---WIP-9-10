package com.oddlabs.tt.gamemode.koth;

import com.oddlabs.matchmaking.GameModeOption;
import com.oddlabs.tt.animation.Animated;
import com.oddlabs.tt.gamemode.GameModeRules;
import com.oddlabs.tt.gui.Label;
import com.oddlabs.tt.gui.Origin;
import com.oddlabs.tt.gui.Skin;
import com.oddlabs.tt.landscape.WorldParameters;
import com.oddlabs.tt.model.SceneryModel;
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

// King of the Island: STATUE_COUNT statues (3-10, see World.getKothStatueCount() /
// WorldParameters.kothStatueCount()) are scattered in a ring around the map center. A team wins
// by simultaneously controlling at least WIN_FRACTION (80%) of all statues, uncontested at each
// one it counts toward that share, for HOLD_SECONDS_TO_WIN continuous seconds. Falling below 80%
// share at any point (a statue lost, contested, or abandoned) resets the hold timer to zero for
// every team, the same "hard reset" design KingOfTheHillModeRules (this mode's original
// single-point design, kept alive as its own separate mode) already uses - see that class's own
// comment for the reasoning. //added by ikill240c
//
// NOTE: the statue's visual is one of the existing generic treasure props (RacesResources.
// getTreasures(), the same array campaign scenarios already use for decorative "statue" scenery - see
// e.g. VikingIsland7.java's SceneryModel placements) rather than a new custom model, since no new art
// assets exist for this mode specifically. Swap the TREASURE_INDEX constant below if a different one
// of the six treasure sprites looks more statue-like once seen in-game. //added by ikill240c
//
// NOTE on the statue count actually reaching this class: GameModeOption/GameModeOptions (the
// generic per-mode lobby-option schema below) still has no live path into onGameStart() anywhere
// in this codebase (see the OPTIONS field's own comment - this is unchanged from before). Statue
// count specifically was wired through a DIFFERENT, already-existing path instead -
// WorldParameters.kothStatueCount() -> World.getKothStatueCount(), the same route
// max_building_count and every other AI/economy tuning value already use - since that path is
// already proven to reach World by the time onGameStart(WorldViewer) runs, where CAPTURE_RADIUS/
// HOLD_SECONDS_TO_WIN below still can't be reached from any lobby control and remain hardcoded.
// //added by ikill240c
public final class KingOfTheIslandModeRules implements GameModeRules {
    private static final float CAPTURE_RADIUS = 15f; // grid units around each statue //added by ikill240c
    private static final float HOLD_SECONDS_TO_WIN = 60f; // continuous, uncontested 80%+ share required - shorter than King of the Hill's single-point 120s, since holding 80% of several separate statues at once against contest is a much stronger, harder-to-fluke signal of dominance //added by ikill240c
    private static final float WIN_FRACTION = 0.8f; // share of all statues that must be simultaneously controlled //added by ikill240c
    private static final int TREASURE_INDEX = 0; // which of the 6 generic treasure sprites to use as the statue - see note above //added by ikill240c
    private static final float RING_RADIUS_FRACTION = 0.35f; // statue ring's radius as a fraction of the map's half-width //added by ikill240c

    public static final @NonNull String OPTION_CAPTURE_RADIUS = "koth_capture_radius"; //added by ikill240c
    public static final @NonNull String OPTION_HOLD_SECONDS = "koth_hold_seconds"; //added by ikill240c

    // i18n keys below ("koth_capture_radius"/"koth_capture_radius_tip", "koth_hold_seconds"/
    // "koth_hold_seconds_tip") need adding to TerrainMenu.properties (all locale files) before this
    // schema renders correctly anywhere - this is the exact same gap as the earlier missing
    // "action.MAGIC_3" key, which crashed the game the first time the options menu tried to render it.
    // //added by ikill240c
    private static final @NonNull List<@NonNull GameModeOption> OPTIONS = List.of(
            new GameModeOption(OPTION_CAPTURE_RADIUS, GameModeOption.Type.FLOAT, CAPTURE_RADIUS,
                    "koth_capture_radius", 5f, 40f, null), //added by ikill240c - i18n_key is the label key ("koth_capture_radius"); a separate "koth_capture_radius_tip" key provides the hover tooltip, matching StandardModeRules's rated_game/rated_game_tip pattern
            new GameModeOption(OPTION_HOLD_SECONDS, GameModeOption.Type.FLOAT, HOLD_SECONDS_TO_WIN,
                    "koth_hold_seconds", 15f, 300f, null)); //added by ikill240c

    // Per-game mutable state. GameModeRules implementations are registered once as a shared instance
    // in GameModeRegistry (see StandardModeRules's registration pattern), so every game played in the
    // same process reuses this same object - all mutable state here is reset in onGameStart() to avoid
    // leaking a previous game's hold progress or winner into a new one. //added by ikill240c
    private int winning_team = PlayerInfo.TEAM_NEUTRAL; //added by ikill240c
    private int leading_team = PlayerInfo.TEAM_NEUTRAL; //added by ikill240c - the team currently at/above WIN_FRACTION share, or TEAM_NEUTRAL if none is
    private float hold_time = 0f; //added by ikill240c
    // Statue positions and each one's current controlling team (TEAM_NEUTRAL if empty or
    // contested), recomputed every tick. Parallel arrays rather than a small record/class per
    // statue - there's no behavior per statue beyond these two values, so a class would add
    // indirection without adding clarity. //added by ikill240c
    private float @NonNull [] statue_x = new float[0]; //added by ikill240c
    private float @NonNull [] statue_y = new float[0]; //added by ikill240c
    private int @NonNull [] statue_controlling_team = new int[0]; //added by ikill240c

    // Public accessors so AdvancedAI (or any other gameplay code) can find out where the statues
    // are and who currently holds each one, in order to actually play the objective - added
    // alongside the AI logic that uses them, since nothing previously needed to read this state
    // from outside this class. //added by ikill240c
    public int getStatueCount() { //added by ikill240c
        return statue_x.length;
    } //added by ikill240c

    public float getStatueX(int index) { //added by ikill240c
        return statue_x[index];
    } //added by ikill240c

    public float getStatueY(int index) { //added by ikill240c
        return statue_y[index];
    } //added by ikill240c

    // TEAM_NEUTRAL if this statue is currently unoccupied or contested by more than one team.
    // //added by ikill240c
    public int getStatueControllingTeam(int index) { //added by ikill240c
        return statue_controlling_team[index];
    } //added by ikill240c

    public int getLeadingTeam() { //added by ikill240c
        return leading_team;
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
        leading_team = PlayerInfo.TEAM_NEUTRAL;
        hold_time = 0f;

        // Clamped defensively even though WorldParameters.Builder.kothStatueCount() already
        // clamps at the source - this is the actual gameplay-critical boundary (a bad count here
        // means a malformed ring or a division that changes win difficulty), so it doesn't rely
        // solely on every possible caller of the builder having clamped correctly upstream.
        // //added by ikill240c
        int statue_count = Math.clamp(viewer.getWorld().getKothStatueCount(),
                WorldParameters.MIN_KOTH_STATUE_COUNT, WorldParameters.MAX_KOTH_STATUE_COUNT); //added by ikill240c

        int center_grid = viewer.getWorld().getHeightMap().getGridUnitsPerWorld() / 2; //added by ikill240c
        float center_x = UnitGrid.coordinateFromGrid(center_grid); //added by ikill240c
        float center_y = UnitGrid.coordinateFromGrid(center_grid); //added by ikill240c
        float ring_radius = UnitGrid.coordinateFromGrid(center_grid) * RING_RADIUS_FRACTION; //added by ikill240c

        statue_x = new float[statue_count]; //added by ikill240c
        statue_y = new float[statue_count]; //added by ikill240c
        statue_controlling_team = new int[statue_count]; //added by ikill240c

        // Evenly spaced around a ring centered on the map, rather than clustered or randomly
        // placed - guarantees even spacing and no overlap between statues with no need for a
        // placement-legality search, and keeps every statue equidistant from a central starting
        // position regardless of statue_count. //added by ikill240c
        for (int i = 0; i < statue_count; i++) { //added by ikill240c
            double angle = 2 * Math.PI * i / statue_count; //added by ikill240c
            statue_x[i] = center_x + ring_radius * (float) Math.cos(angle); //added by ikill240c
            statue_y[i] = center_y + ring_radius * (float) Math.sin(angle); //added by ikill240c
            statue_controlling_team[i] = PlayerInfo.TEAM_NEUTRAL; //added by ikill240c

            // Purely decorative (occupy=false so units can freely walk onto/around it, which they
            // need to be able to do to actually capture it), using the same SceneryModel +
            // treasure-sprite mechanism campaign scenarios already use for "statue" props.
            // //added by ikill240c
            new SceneryModel(viewer.getWorld(), statue_x[i], statue_y[i], 0f, 1f, //added by ikill240c
                    viewer.getWorld().getRacesResources().getTreasures()[TREASURE_INDEX], 2.6f, false, //added by ikill240c
                    "King of the Island"); //added by ikill240c
        } //added by ikill240c

        // Leader/countdown HUD label, updated every tick from within the same ticker below. Positioned
        // top-center of the screen; re-centered on resize via GUIRoot's own layout pass would need a
        // resize listener, which is out of scope here - this recenters once at creation using the
        // GUIRoot's dimensions at game start. //added by ikill240c
        Label koth_status_label = new Label("", Skin.getSkin().getHeadlineFont(), 500, Origin.AT_MIDDLE);
        koth_status_label.setPos((viewer.getGUIRoot().getWidth() - koth_status_label.getWidth()) / 2, 10);
        viewer.getGUIRoot().addChild(koth_status_label);

        int win_threshold = (int) Math.ceil(statue_count * WIN_FRACTION); //added by ikill240c - e.g. ceil(0.8 * 5) = 4 of 5 statues

        // Register a per-frame ticker that checks who currently controls each statue and advances
        // (or resets) the shared hold timer based on the current overall leader's share. Uses the
        // same registerAnimation() pattern already proven to work for one-shot magic effects (see
        // Convert.java/Stun.java), just with an anonymous Animated that never unregisters itself,
        // since the win check needs to keep running for the whole match.
        //
        // The actual per-statue work (a full unit-grid scan within CAPTURE_RADIUS, times up to
        // MAX_KOTH_STATUE_COUNT statues) is throttled to run every CHECK_INTERVAL seconds rather
        // than every single tick unthrottled - this ticker has no cooldown gate at all otherwise,
        // unlike every comparable per-tick AI check elsewhere in this codebase (see AdvancedAI's
        // own node cooldowns), and an untouched full rescan of up to 10 statues' worth of nearby
        // units, every tick, for an entire match's duration, is exactly the kind of accumulating
        // per-tick cost that showed up as gameplay stalling/falling-behind warnings once unit
        // counts grew over the course of a match. Accumulates elapsed time every tick regardless
        // (t_since_last_check += t) so the hold timer itself still advances at the correct real
        // rate between checks, rather than losing time to the throttle. //added by ikill240c
        viewer.getWorld().getAnimationManagerGameTime().registerAnimation(new Animated() { //added by ikill240c
            private static final float CHECK_INTERVAL = 0.5f; //added by ikill240c
            private float t_since_last_check = 0f; //added by ikill240c

            @Override
            public void animate(float t) {
                if (winning_team != PlayerInfo.TEAM_NEUTRAL) {
                    return; // already decided and the label already shows the final result //added by ikill240c
                }

                t_since_last_check += t; //added by ikill240c
                if (t_since_last_check < CHECK_INTERVAL) //added by ikill240c
                    return; // not time for the next full rescan yet //added by ikill240c
                float elapsed = t_since_last_check; //added by ikill240c - the real time to credit toward hold_time below, not just CHECK_INTERVAL, since the last check may have run slightly late
                t_since_last_check = 0f; //added by ikill240c

                // Per-statue: exactly one team present inside CAPTURE_RADIUS = they control it.
                // Zero teams present (empty) or more than one (contested) both count as
                // uncontrolled. Also tallies each controlled statue toward its team's total share.
                // //added by ikill240c
                Map<Integer, Integer> statues_per_team = new HashMap<>(); //added by ikill240c
                for (int i = 0; i < statue_x.length; i++) { //added by ikill240c
                    FindOccupantFilter<Unit> filter = new FindOccupantFilter<>(statue_x[i], statue_y[i], //added by ikill240c
                            CAPTURE_RADIUS, null, Unit.class); //added by ikill240c
                    viewer.getWorld().getUnitGrid().scan(filter, UnitGrid.toGridCoordinate(statue_x[i]), //added by ikill240c
                            UnitGrid.toGridCoordinate(statue_y[i])); //added by ikill240c

                    Map<Integer, Integer> team_presence = new HashMap<>(); //added by ikill240c
                    for (Unit u : filter.getResult()) { //added by ikill240c
                        if (u.isDead()) //added by ikill240c
                            continue; //added by ikill240c
                        int team = u.getOwner().getPlayerInfo().getTeam(); //added by ikill240c
                        if (team == PlayerInfo.TEAM_NEUTRAL) //added by ikill240c
                            continue; // neutral/observer units never count toward capture //added by ikill240c
                        team_presence.merge(team, 1, Integer::sum); //added by ikill240c
                    } //added by ikill240c

                    int controller = team_presence.size() == 1 //added by ikill240c
                            ? team_presence.keySet().iterator().next() //added by ikill240c
                            : PlayerInfo.TEAM_NEUTRAL; //added by ikill240c
                    statue_controlling_team[i] = controller; //added by ikill240c
                    if (controller != PlayerInfo.TEAM_NEUTRAL) //added by ikill240c
                        statues_per_team.merge(controller, 1, Integer::sum); //added by ikill240c
                } //added by ikill240c

                // Find whichever team currently controls the most statues, and whether that meets
                // the win threshold. Ties (two teams controlling the same count) count as nobody
                // leading, same "ambiguous = not held" philosophy as the per-statue check above.
                // //added by ikill240c
                int best_team = PlayerInfo.TEAM_NEUTRAL; //added by ikill240c
                int best_count = 0; //added by ikill240c
                boolean tied = false; //added by ikill240c
                for (Map.Entry<Integer, Integer> entry : statues_per_team.entrySet()) { //added by ikill240c
                    if (entry.getValue() > best_count) { //added by ikill240c
                        best_team = entry.getKey(); //added by ikill240c
                        best_count = entry.getValue(); //added by ikill240c
                        tied = false; //added by ikill240c
                    } else if (entry.getValue().intValue() == best_count) { //added by ikill240c
                        tied = true; //added by ikill240c
                    } //added by ikill240c
                } //added by ikill240c

                int qualifying_team = (!tied && best_count >= win_threshold) //added by ikill240c
                        ? best_team : PlayerInfo.TEAM_NEUTRAL; //added by ikill240c

                if (qualifying_team != PlayerInfo.TEAM_NEUTRAL && qualifying_team == leading_team) { //added by ikill240c
                    hold_time += elapsed; //added by ikill240c - credit the real elapsed time since the last check, not just CHECK_INTERVAL
                    if (hold_time >= HOLD_SECONDS_TO_WIN) { //added by ikill240c
                        winning_team = leading_team; //added by ikill240c
                    } //added by ikill240c
                } else { //added by ikill240c
                    leading_team = qualifying_team; //added by ikill240c
                    hold_time = 0f; //added by ikill240c
                } //added by ikill240c

                // Update the HUD label every tick with the current leader's share and remaining
                // hold time, or a neutral message when no team meets the threshold. //added by ikill240c
                if (winning_team != PlayerInfo.TEAM_NEUTRAL) { //added by ikill240c
                    koth_status_label.setText("Team " + (winning_team + 1) + " has won the Island!"); //added by ikill240c
                } else if (leading_team != PlayerInfo.TEAM_NEUTRAL) { //added by ikill240c
                    int seconds_left = (int) Math.ceil(HOLD_SECONDS_TO_WIN - hold_time); //added by ikill240c
                    koth_status_label.setText("Team " + (leading_team + 1) + " controls " + best_count + "/" //added by ikill240c
                            + statue_x.length + " statues - " + seconds_left + "s to win"); //added by ikill240c
                } else if (best_count > 0) { //added by ikill240c
                    koth_status_label.setText("Best control: " + best_count + "/" + statue_x.length //added by ikill240c
                            + " statues (need " + win_threshold + " to lead)"); //added by ikill240c
                } else { //added by ikill240c
                    koth_status_label.setText("The Island's statues are uncontrolled"); //added by ikill240c
                } //added by ikill240c
            }
        });
    }
}
