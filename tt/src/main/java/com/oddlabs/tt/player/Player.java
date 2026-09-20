package com.oddlabs.tt.player;

import com.oddlabs.matchmaking.Game;
import com.oddlabs.tt.gamemode.GameModeRegistry;
import com.oddlabs.tt.global.Settings; //added by ikill240c
import com.oddlabs.tt.landscape.LandscapeTarget;
import com.oddlabs.tt.landscape.TreeSupply;
import com.oddlabs.tt.landscape.World;
import com.oddlabs.tt.model.Abilities;
import com.oddlabs.tt.model.Action;
import com.oddlabs.tt.model.Army;
import com.oddlabs.tt.model.LandBuilding;
import com.oddlabs.tt.model.Building;
import com.oddlabs.tt.model.Ship;
import com.oddlabs.tt.model.DeployType;
import com.oddlabs.tt.model.IronSupply;
import com.oddlabs.tt.model.Race;
import com.oddlabs.tt.model.RacesResources;
import com.oddlabs.tt.model.RockSupply;
import com.oddlabs.tt.model.RubberSupply;
import com.oddlabs.tt.model.Selectable;
import com.oddlabs.tt.model.Supply;
import com.oddlabs.tt.model.SupplyContainer;
import com.oddlabs.tt.model.Unit;
import com.oddlabs.tt.model.behaviour.GatherController;
import com.oddlabs.tt.model.behaviour.NullController;
import com.oddlabs.tt.model.weapon.IronAxeWeapon;
import com.oddlabs.tt.model.weapon.RockAxeWeapon;
import com.oddlabs.tt.model.weapon.RubberAxeWeapon;
import com.oddlabs.tt.util.Target;
import org.joml.Vector4fc;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class Player implements PlayerInterface {
    public static final int INITIAL_UNIT_COUNT = 20;//og 20
    public static final int MAX_BUILDING_COUNT = 20;//og 20
    public static final int DEFAULT_MAX_UNIT_COUNT = 250;//og 250
    // Maximum total chieftains a player can have alive at any time (includes the primary chieftain,
    // extra chieftains from conversion, and chieftains currently being trained). Change this value
    // to allow more or fewer chieftains per player. //added by ikill240 2026-09-09 20:45
    public static final int DEFAULT_MAX_CHIEFTAINS = 1; //added by ikill240 2026-09-09 20:45

    private final @NonNull World world;
    private final @NonNull PlayerInfo player_info;
    private final Army units = new Army();
    private final @NonNull SupplyContainer unit_count;
    // Building limit now comes from the world settings instead of the constant. //added by ikill240c 2026-09-10 00:00
    private final @NonNull SupplyContainer building_count; //added by ikill240c 2026-09-10 00:00

    private final @NonNull Vector4fc color;

//	private final String team_tip;

    private @Nullable AI ai = null;

    private @Nullable Unit chieftain = null;
    // Additional chieftain-ability units this player controls beyond the single "primary" chieftain
    // tracked above (e.g. gained via the Convert magic). Deliberately kept separate from `chieftain`
    // so every existing call site that assumes one chieftain (training gate, achievements, UI selection
    // binding, removeDying() cleanup) keeps working unmodified - these are purely additive and fully
    // playable/selectable/castable on their own, they just aren't "the" primary chieftain.
    // //added by ikill240c 2026-09-08 17:00
    private final java.util.List<@NonNull Unit> extra_chieftains = new java.util.ArrayList<>();
    private int training_chieftain_count = 0;
    private float start_x;
    private float start_y;

    // stats
    private int units_lost;
    private int buildings_lost;
    private int units_killed;
    private int buildings_destroyed;
    private int units_moved;
    private int weapons_thrown;
    private int magics;

    private int tree_harvested;
    private int rock_harvested;
    private int iron_harvested;
    private int rubber_harvested;

    // Was a Player-level field here (a single client-side dispatch preference applying to every
    // future order regardless of selection); removed - formation now lives per-unit on Selectable
    // itself (see Selectable.formation's own comment for the full reasoning).
    // //added by ikill240c
    private boolean can_build_chieftains = true;
    // Gates the automatic Chiefs Courage buff (triggered whenever any magic is successfully cast - see
    // Unit.triggerChiefsCourage()). Defaults to enabled; campaign scenarios can disable it for a race
    // until a later island is unlocked, the same way enableChieftains()/enableMagic() gate other
    // abilities. //added by ikill240c
    private boolean can_use_chiefs_courage = true;
    private boolean can_repair = true;
    private boolean can_attack = true;
    private final boolean[] can_build = new boolean[Race.NUM_BUILDINGS];
    private boolean can_move = true;
    private boolean can_exit_towers = true;
    private boolean can_use_rubber = true;
    private boolean can_set_rally = true;
    private boolean can_harvest = true;
    private boolean can_build_armies = true;
    private boolean can_build_weapons = true;
    private boolean can_transport = true;
    private final boolean[] can_do_magic = new boolean[RacesResources.NUM_MAGIC];

    private float hit_bonus;

    private int preferred_speed = World.GAMESPEED_DONTCARE;

    public Player(@NonNull World world, @NonNull PlayerInfo player_info, @NonNull Vector4fc color) {
        this.world = world;
        this.color = color;
        Arrays.fill(can_do_magic, true);
        Arrays.fill(can_build, true);
        this.player_info = player_info;
        this.unit_count = new SupplyContainer(world.getMaxUnitCount());
        this.building_count = new SupplyContainer(world.getMaxBuildingCount()); //added by ikill240c 2026-09-10 00:00
//		this.team_tip = i18n("team", new Object[]{Integer.toString(player_info.getTeam() + 1)});
    }

    @Override
    public void changePreferredGamespeed(int delta) {
        int old_speed = getGamespeed();
        int new_speed = Math.clamp(old_speed + delta, Game.GAMESPEED_PAUSE, Game.GAMESPEED_LUDICROUS);
        setPreferredGamespeed(new_speed);
    }

    @Override
    public void setPreferredGamespeed(int speed) {
        if (!World.isValidPreferredGamespeed(speed))
            return;
        if (preferred_speed != speed) {
            int old_speed = preferred_speed;
            this.preferred_speed = speed;
            if (World.isValidGamespeed(preferred_speed) && World.isValidGamespeed(old_speed))
                world.getNotificationListener().playerGamespeedChanged();
            world.gamespeedChanged();
        }
    }

    public int getGamespeed() {
        return World.isValidGamespeed(preferred_speed) ? preferred_speed : world.getGamespeed();
    }

    public int getPreferredGamespeed() {
        return preferred_speed;
    }

    public float getHitBonus() {
        return hit_bonus;
    }

    public void setHitBonus(float bonus) {
        this.hit_bonus = bonus;
    }

    public @NonNull World getWorld() {
        return world;
    }

    public void enableArmies(boolean enabled) {
        can_build_armies = enabled;
    }

    public void enableWeapons(boolean enabled) {
        can_build_weapons = enabled;
    }

    public void enableTransporting(boolean enabled) {
        can_transport = enabled;
    }

    public void enableHarvesting(boolean enabled) {
        can_harvest = enabled;
    }

    public void enableRubber(boolean enabled) {
        can_use_rubber = enabled;
    }

    public void enableChieftains(boolean enabled) {
        can_build_chieftains = enabled;
    }

    // Resolves the formation to actually use for THIS order from the units being ordered, now
    // that formation lives per-unit rather than as one Player-wide setting (see Selectable.
    // formation's own comment). Different units in the same selection could in principle carry
    // different formations (e.g. two separately-formed groups get box-selected together and given
    // one new order together) - picks whichever formation is most common among the selected
    // units' own settings, falling back to Formation.LOOSE (the same default the old player-wide
    // field started at) when none of them have one set yet. //added by ikill240c
    private @NonNull Formation resolveFormation(Selectable<?> @NonNull [] selection) { //added by ikill240c
        java.util.Map<Formation, Integer> counts = new java.util.HashMap<>(); //added by ikill240c
        for (Selectable<?> s : selection) { //added by ikill240c
            Formation f = s.getFormation(); //added by ikill240c
            if (f != null) //added by ikill240c
                counts.merge(f, 1, Integer::sum); //added by ikill240c
        } //added by ikill240c
        Formation best = null; //added by ikill240c
        int best_count = 0; //added by ikill240c
        for (var entry : counts.entrySet()) { //added by ikill240c
            if (entry.getValue() > best_count) { //added by ikill240c
                best_count = entry.getValue(); //added by ikill240c
                best = entry.getKey(); //added by ikill240c
            } //added by ikill240c
        } //added by ikill240c
        return best != null ? best : Formation.LOOSE; //added by ikill240c
    } //added by ikill240c

    @Override
    public void setFormation(Selectable<?> @NonNull [] selection, @NonNull Formation formation) { //added by ikill240c
        // Applies to exactly the units passed in, not globally - see Selectable.formation's own
        // comment for why this moved off Player entirely. isValid() gates this the same as every
        // other order-issuing method (ally units included, per the unit-control support), so a
        // human teammate can set formation for an ally AI's units too. //added by ikill240c
        for (Selectable<?> s : selection) { //added by ikill240c
            if (isValid(s)) //added by ikill240c
                s.setFormation(formation); //added by ikill240c
        } //added by ikill240c
    }

    // See can_use_chiefs_courage field comment. //added by ikill240c
    public void enableChiefsCourage(boolean enabled) {
        can_use_chiefs_courage = enabled;
    }

    public boolean canUseChiefsCourage() { //added by ikill240c
        return can_use_chiefs_courage;
    }

    // Tracks how many times THIS player has triggered Chiefs Courage - each use makes the next
    // one's buff magnitude/duration 1.5% stronger (compounding, not additive: use #10 is stronger
    // relative to use #9 by the same 1.5%, not a flat +15% over the base value by then), per the
    // explicit request. Never resets - this is a persistent, permanent escalation for the whole
    // match, not something that decays or resets between casts. //added by ikill240c
    private int chiefs_courage_uses = 0; //added by ikill240c

    public int incrementAndGetChiefsCourageUses() { //added by ikill240c
        return ++chiefs_courage_uses;
    }

    // Minimum simulated game-time (in seconds) between two Chiefs Courage triggers for a given
    // player - previously Chiefs Courage fired on every single successful magic cast with no limit
    // at all. Expressed in the same "simulated seconds" unit as Unit's courage_stacks duration (see
    // Unit.CourageStack), so this cooldown and the buff's own duration speed up/slow down together
    // under game-speed changes instead of drifting relative to each other. //added by ikill240p 2026-09-14
    private static final float CHIEFS_COURAGE_COOLDOWN_SECONDS = 40f; //added by ikill240p 2026-09-14 og 20
    // Remaining cooldown, in the same simulated game-seconds unit as above; <= 0 means ready to
    // trigger again. Decremented once per world tick by World.tick() -> tickChiefsCourageCooldown()
    // (NOT from per-unit doAnimate(), which would decrement it once per UNIT per tick instead of
    // once per tick). //added by ikill240p 2026-09-14
    private float chiefs_courage_cooldown_remaining = 0f; //added by ikill240p 2026-09-14 - 0 means ready from the very start of the game

    // Whether this player's Chiefs Courage cooldown has fully elapsed, i.e. a new trigger is
    // allowed right now. //added by ikill240p 2026-09-14
    public boolean isChiefsCourageReady() { //added by ikill240p 2026-09-14
        return chiefs_courage_cooldown_remaining <= 0f; //added by ikill240p 2026-09-14
    }

    // Starts this player's Chiefs Courage cooldown. Deliberately separate from
    // incrementAndGetChiefsCourageUses() (the escalation counter) even though both are driven by
    // the same trigger event, because the cooldown length itself is NOT escalated - only the
    // buff's own magnitude/duration are, per Unit.triggerChiefsCourage(). //added by ikill240p 2026-09-14
    public void chiefsCourageTriggered() { //added by ikill240p 2026-09-14
        chiefs_courage_cooldown_remaining = CHIEFS_COURAGE_COOLDOWN_SECONDS; //added by ikill240p 2026-09-14
    }

    // Advances this player's Chiefs Courage cooldown by one world tick's worth of simulated
    // game-time. Called exactly once per world tick from World.tick() - deliberately NOT called
    // from Unit.doAnimate(), since that runs once per UNIT per tick and would decrement a per-PLAYER
    // cooldown far too fast (once per unit instead of once per tick) if hooked in there instead.
    // //added by ikill240p 2026-09-14
    public void tickChiefsCourageCooldown(float t) { //added by ikill240p 2026-09-14
        if (chiefs_courage_cooldown_remaining > 0f) //added by ikill240p 2026-09-14
            chiefs_courage_cooldown_remaining = Math.max(0f, chiefs_courage_cooldown_remaining - t); //added by ikill240p 2026-09-14 - clamp at 0 rather than letting it run negative
    }

    public void enableTowerExits(boolean enabled) {
        can_exit_towers = enabled;
    }

    public void enableRepairing(boolean enabled) {
        can_repair = enabled;
    }

    public void enableBuilding(int building, boolean enabled) {
        can_build[building] = enabled;
    }

    public void enableAttacking(boolean enabled) {
        can_attack = enabled;
    }

    public void enableRallyPoints(boolean enabled) {
        can_set_rally = enabled;
    }

    public void enableMoving(boolean enabled) {
        can_move = enabled;
    }

    public boolean canTransport() {
        return can_transport;
    }

    public boolean canBuildWeapons() {
        return can_build_weapons;
    }

    public boolean canHarvest() {
        return can_harvest;
    }

    public boolean canBuildArmies() {
        return can_build_armies;
    }

    public boolean canSetRallyPoints() {
        return can_set_rally;
    }

    public boolean canUseRubber() {
        return can_use_rubber;
    }

    public void enableMagic(int magic_index, boolean enabled) {
        can_do_magic[magic_index] = enabled;
    }

    public boolean canDoMagic(int magic_index) {
        return can_do_magic[magic_index];
    }

    public boolean canExitTowers() {
        return can_exit_towers;
    }

    public boolean canAttack() {
        return can_attack;
    }

    public boolean canMove() {
        return can_move;
    }

    public boolean canBuild(int building) {
        // Use the configured limit rather than the hard-coded constant. //added by ikill240c 2026-09-10 00:00
        return can_build[building] && getBuildingCountContainer().getNumSupplies() < world.getMaxBuildingCount();
    }

    public boolean canRepair() {
        return can_repair;
    }

    public boolean canBuildChieftains() {
        return can_build_chieftains;
    }

    @Override
    public @NonNull String toString() {
        return player_info.toString();
    }

    public @NonNull PlayerInfo getPlayerInfo() {
        return player_info;
    }

    public void setAI(@Nullable AI ai) {
        this.ai = ai;
    }

    public @Nullable AI getAI() {
        return ai;
    }

    public @Nullable Building buildBuilding(int building_type, int grid_x, int grid_y) {
        BuildingSiteScanFilter filter = new BuildingSiteScanFilter(world.getUnitGrid(), getRace().getBuildingTemplate(
                building_type), 40, true);
        world.getUnitGrid().scan(filter, grid_x, grid_y);
        List<LandscapeTarget> target_list = filter.getResult();
        Building b = null;
        if (!target_list.isEmpty()) {
            Target t = target_list.getFirst();
            b = getRace().getBuildingTemplate(building_type).create(this, t.getGridX(), t.getGridY());
            b.place();
            // was hardcoded repair(1000), which only fully-completed a building when its max HP was <=1000;
            // building HP values in this project were later increased well beyond that (Quarters/Armory
            // now 5000/4500 - see RacesResources), so repair(1000) silently left scenario-placed starting
            // buildings permanently incomplete (no REPRODUCE/BUILD_ARMIES ability), which is why
            // Player.getQuarters()/getArmory() returned null and crashed campaign scripts like VikingIsland0
            // that expect a working starting Armory immediately. repair() already clamps to max HP internally,
            // so passing the building's own max HP here always fully completes it regardless of future rebalancing.
            // //added by ikill240c 2026-09-08 16:00
            b.repair(b.getTemplate().getMaxHitPoints());
        }
        return b;
    }

    public void init(float @NonNull [] starting_location) {
        this.start_x = starting_location[0];
        this.start_y = starting_location[1];
    }

    public @Nullable Selectable<?> findNearestEnemy(int start_x, int start_y) {
        return findNearestEnemy(start_x, start_y, null);
    }

    public @Nullable Selectable<?> findNearestEnemy(int start_x, int start_y, Selectable<?> target) {
        return findNearestEnemy(start_x, start_y, target, Selectable.genericClass());
    }

    public int getStatus() {
        return getUnits().getSet().stream().mapToInt(Selectable::getStatusValue).sum();
    }

    public @Nullable Selectable<?> findNearestEnemy(int start_x, int start_y, Selectable<?> target,
            @NonNull Class<? extends Selectable<?>> type) {
        int best_dist_squared = Integer.MAX_VALUE;
        Selectable<?> best_target = null;
        for (Player player : world.getPlayers()) {
            if (isEnemy(player)) {
                for (var s : player.getUnits().getSet()) {
                    if (!(type.isInstance(s)) || s == target) {
                        continue;
                    }
                    int dx = s.getGridX() - start_x;
                    int dy = s.getGridY() - start_y;
                    int dist_squared = dx * dx + dy * dy;
                    if (best_dist_squared > dist_squared) {
                        best_dist_squared = dist_squared;
                        best_target = s;
                    }
                }
            }
        }
        return best_target;
    }

    public @Nullable Selectable<?> findNearestEnemyBuilding(int start_x, int start_y) {
        return findNearestEnemy(start_x, start_y, null, LandBuilding.class);
    }

    public @Nullable Selectable<?> findNearestEnemyShip(int start_x, int start_y) {
        return findNearestEnemy(start_x, start_y, null, Ship.class);
    }

    public @Nullable Selectable<?> findNearestEnemyOnBeach(int start_x, int start_y) {
        int best_dist_squared = Integer.MAX_VALUE;
        var dock = getWorld().getHeightMap().getDockGrid();
        var map_size = getWorld().getHeightMap().getGridUnitsPerWorld();
        Selectable<?> best_target = null;
        for (Player player : world.getPlayers()) {
            if (isEnemy(player)) {
                for (var s : player.getUnits().getSet()) {
                    int x = s.getGridX();
                    int y = s.getGridY();
                    int size = StrictMath.round(s.getSize());
                    boolean on_beach = false;
                    for (int i = 0; i < size && !on_beach; i++) {
                        for (int j = 0; j < size && !on_beach; j++) {
                            int cx = x + i - size / 2;
                            int cy = y + i - size / 2;
                            if (cx < 0 || cx >= map_size || cy < 0 || cy >= map_size) {
                                continue;
                            }
                            if (dock[cy][cx] != 0) {
                                on_beach = true;
                            }
                        }
                    }
                    if (!on_beach) {
                        continue;
                    }
                    int dx = x - start_x;
                    int dy = y - start_y;
                    int dist_squared = dx * dx + dy * dy;
                    if (best_dist_squared > dist_squared) {
                        best_dist_squared = dist_squared;
                        best_target = s;
                    }
                }
            }
        }
        return best_target;
    }

    public @NonNull Race getRace() {
        return getWorld().getRacesResources().getRace(player_info.getRace());
    }

    public @NonNull SupplyContainer getUnitCountContainer() {
        return unit_count;
    }

    public @NonNull SupplyContainer getBuildingCountContainer() {
        return building_count;
    }

    public void setActiveChieftain(Unit chieftain) {
        this.chieftain = chieftain;
    }

    // Register a chieftain-ability unit gained through conversion (or any other future source)
    // without disturbing the single "primary" chieftain slot above. Dead entries are pruned lazily
    // on read via getExtraChieftains() rather than tracked with listeners, since Player has no
    // per-unit death hook to remove from wired here already. //added by ikill240c 2026-09-08 17:00
    public void addExtraChieftain(@NonNull Unit extra) {
        extra_chieftains.add(extra);
    }

    // Returns all additional chieftains still alive, pruning dead ones from the backing list first.
    // //added by ikill240c 2026-09-08 17:00
    public @NonNull List<@NonNull Unit> getExtraChieftains() {
        extra_chieftains.removeIf(Unit::isDead);
        return extra_chieftains;
    }

    public @Nullable Building getArmory() {
        Selectable<?>[][] lists = classifyUnits();
        for (Selectable<?>[] list : lists) {
            Selectable<?> s = list[0];
            if (s.getPrimaryController() instanceof NullController && s.getAbilities().hasAbilities(
                    Abilities.BUILD_ARMIES)) {
                return (Building) s;
            }
        }
        return null;
    }

    public @Nullable Building getQuarters() {
        Selectable<?>[][] lists = classifyUnits();
        for (Selectable<?>[] list : lists) {
            Selectable<?> s = list[0];
            if (s.getPrimaryController() instanceof NullController && s.getAbilities().hasAbilities(
                    Abilities.REPRODUCE)) {
                return (Building) s;
            }
        }
        return null;
    }

    public boolean isAlive() {
        return GameModeRegistry.get(world.getGameMode()).isPlayerAlive(this);
    }


    public boolean hasActiveChieftain() {
        return (chieftain != null && !chieftain.isDead()) || !getExtraChieftains().isEmpty();
    }

    public @Nullable Unit getChieftain() {
        return chieftain;
    }

    public void setTrainingChieftain(boolean training_chieftain) {
        if (training_chieftain) {
            training_chieftain_count++;
        } else if (training_chieftain_count > 0) {
            training_chieftain_count--;
        }
    }

    public boolean isTrainingChieftain() {
        return training_chieftain_count > 0;
    }

    // Returns the total number of chieftains this player currently has: the primary chieftain
    // (if alive), all extra chieftains (alive ones only, dead entries are pruned), plus any
    // chieftains currently being trained across all quarters buildings. //added by ikill240 2026-09-09 20:45
    public int getTotalChieftainCount() { //added by ikill240 2026-09-09 20:45
        // Start with the primary chieftain slot: count 1 if it exists and is alive //added by ikill240 2026-09-09 20:45
        int count = (chieftain != null && !chieftain.isDead()) ? 1 : 0; //added by ikill240 2026-09-09 20:45
        // Add all alive extra chieftains (getExtraChieftains prunes dead entries) //added by ikill240 2026-09-09 20:45
        count += getExtraChieftains().size(); //added by ikill240 2026-09-09 20:45
        // Add chieftains currently being trained in any quarters building //added by ikill240 2026-09-09 20:45
        count += training_chieftain_count; //added by ikill240 2026-09-09 20:45
        return count; //added by ikill240 2026-09-09 20:45
    }

    // Returns the configured maximum number of chieftains this player can have. //added by ikill240 2026-09-09 20:49
    public int getMaxChieftains() { //added by ikill240 2026-09-09 20:49
        return getWorld().getMaxChieftains(); //added by ikill240 2026-09-09 20:49
    }

    // Returns true if the player can train more chieftains without exceeding the total cap. //added by ikill240 2026-09-09 20:45
    public boolean canTrainMoreChieftains() { //added by ikill240 2026-09-09 20:45
        return getTotalChieftainCount() < getMaxChieftains(); //added by ikill240 2026-09-09 20:45
    }

    public @NonNull Vector4fc getColor() {
        return color;
    }

    // Team-shared color, distinct from getColor()'s per-player color: every player on the same
    // team returns the SAME color here, used for buildings so allied bases read as one shared
    // color at a glance while individual units still show each player's own distinct color via
    // getColor() - see SelectableVisitor.getTeamColor(Selectable) for where the two get split by
    // Unit vs Building. Falls back to this player's own individual color for a player with no real
    // team (free-for-all, or team_colours somehow shorter than the team index). //added by ikill240c
    public @NonNull Vector4fc getTeamBuildingColor() { //added by ikill240c
        int team = player_info.getTeam(); //added by ikill240c
        Vector4fc[] team_colours = Settings.getSettings().team_colours; //added by ikill240c
        if (team == PlayerInfo.TEAM_NEUTRAL || team < 0 || team >= team_colours.length) //added by ikill240c
            return color; //added by ikill240c
        return team_colours[team]; //added by ikill240c
    } //added by ikill240c

    @Override
    public void deployUnits(@NonNull Building building, @NonNull DeployType type, int num_units) {
        if (isValid(building))
            building.deployUnits(type, num_units);
    }

    public int getGathererCount(@NonNull Class<? extends Supply> supply_type, Building building) {
        int count = 0;
        for (Selectable<?> s : units.getSet()) {
            if (s instanceof Unit && s.getPrimaryController() instanceof GatherController<?> gather) {
                if (gather.getSupplyType() == supply_type && gather.getAssignedBuilding() == building) {
                    count++;
                }
            }
        }
        return count;
    }

    // Combined form of getGathererCount() above, computing tree/rock/iron/rubber counts for one
    // building in a SINGLE pass over this player's units, rather than four separate full scans.
    // AdvancedAI.nodeGather() previously called getGathererCount() four times per armory (once
    // per resource type), each independently re-iterating every unit this player owns - with a
    // growing army over the course of a match and potentially several under-weaponed armories
    // checked periodically, that compounded into a real, worsening cost as a match went on and
    // unit counts climbed, matching reports of stuttering that gets worse over time specifically
    // under high player/unit counts. Returns {tree, rock, iron, rubber} in that fixed order.
    // //added by ikill240c
    public int @NonNull [] getGathererCounts(@NonNull Building building) { //added by ikill240c
        int tree = 0, rock = 0, iron = 0, rubber = 0; //added by ikill240c
        for (Selectable<?> s : units.getSet()) { //added by ikill240c
            if (s instanceof Unit && s.getPrimaryController() instanceof GatherController<?> gather //added by ikill240c
                    && gather.getAssignedBuilding() == building) { //added by ikill240c
                Class<?> type = gather.getSupplyType(); //added by ikill240c
                if (type == TreeSupply.class) tree++; //added by ikill240c
                else if (type == RockSupply.class) rock++; //added by ikill240c
                else if (type == IronSupply.class) iron++; //added by ikill240c
                else if (type == RubberSupply.class) rubber++; //added by ikill240c
            } //added by ikill240c
        } //added by ikill240c
        return new int[]{tree, rock, iron, rubber}; //added by ikill240c
    } //added by ikill240c

    @Override
    public void recallGatherers(@NonNull Building building, @NonNull Class<? extends Supply> supply_type, int amount) {
        if (!isValid(building)) return;

        float bx = building.getPositionX();
        float by = building.getPositionY();

        for (int i = 0; i < amount; i++) {
            Unit nearest = null;
            float nearest_dist_sq = Float.MAX_VALUE;

            for (Selectable<?> s : units.getSet()) {
                if (s instanceof Unit unit && s.getPrimaryController() instanceof GatherController<?> gather) {
                    if (gather.getSupplyType() == supply_type && gather.getAssignedBuilding() == building) {
                        float dx = s.getPositionX() - bx;
                        float dy = s.getPositionY() - by;
                        float dist_sq = dx * dx + dy * dy;
                        if (dist_sq < nearest_dist_sq) {
                            nearest_dist_sq = dist_sq;
                            nearest = unit;
                        }
                    }
                }
            }

            if (nearest != null) {
                nearest.initTarget(building, Action.DEFAULT, false);
            } else {
                break;
            }
        }
    }

    @Override
    public void createHarvesters(@NonNull Building building, int num_tree, int num_rock, int num_iron, int num_rubber) {
        if (isValid(building))
            building.createHarvesters(num_tree, num_rock, num_iron, num_rubber);
    }

    @Override
    public void buildRockWeapons(@NonNull Building building, int num_weapons, boolean infinite) {
        if (isValid(building))
            building.buildWeapons(RockAxeWeapon.class, num_weapons, infinite);
    }

    @Override
    public void buildIronWeapons(@NonNull Building building, int num_weapons, boolean infinite) {
        if (isValid(building))
            building.buildWeapons(IronAxeWeapon.class, num_weapons, infinite);
    }

    @Override
    public void buildRubberWeapons(@NonNull Building building, int num_weapons, boolean infinite) {
        if (isValid(building))
            building.buildWeapons(RubberAxeWeapon.class, num_weapons, infinite);
    }

    @Override
    public void doMagic(@NonNull Unit chieftain, int magic) {
        if (isValid(chieftain))
            // Was clear_stack=true, which wipes the chieftain's ENTIRE controller stack before
            // casting - destroying any active Follow/Guard/Patrol/Attack order outright, not just
            // suspending it. Once MagicController finished and popped itself, there was nothing
            // left underneath to resume, so the chieftain fell back to idle - a standing order
            // silently ending the moment a player cast any spell on that chieftain. false (matching
            // every AI call site already using it - VikingChieftainAI/NativeChieftainAI/
            // AdvancedAI) instead pushes MagicController on top of whatever's currently active, so
            // the underlying order resumes normally once the spell finishes. //added by ikill240c
            chieftain.doMagic(magic, false); //added by ikill240c
    }

    @Override
    public void exitTower(@NonNull Building building) {
        if (isValid(building))
            building.exitTower();
    }

    @Override
    public void standGround(@NonNull Selectable<?> @NonNull [] units) { //added by ikill240c
        for (Selectable<?> s : units) { //added by ikill240c
            if (isValid(s) && s instanceof Unit unit) { //added by ikill240c
                unit.standGround(); //added by ikill240c
            } //added by ikill240c
        } //added by ikill240c
    }

    @Override
    public void trainChieftain(@NonNull Building building, boolean start) {
        if (isValid(building))
            building.trainChieftain(start);
    }

    @Override
    public void placeBuilding(Selectable<?> @NonNull [] selection, int template_id, int placing_grid_x,
            int placing_grid_y, boolean queue) { //added by ikill240c
        Building building = getRace().getBuildingTemplate(template_id).create(this, placing_grid_x, placing_grid_y);

        // Was always initTarget() unconditionally - each shift-click repeat placement immediately took
        // over the builder(s), clearing their controller stack and abandoning whatever they were
        // already building. That meant only the LAST clicked building ever actually got worked on;
        // earlier ones were started (the Building object itself is always created here regardless) but
        // left as an abandoned foundation. queue=true appends this as a queued order instead (same
        // Age-of-Empires-style order queue already used for shift + right click move/attack orders), so
        // repeat-placed buildings get built in the exact order they were clicked. //added by ikill240c
        for (var selection1 : selection) {
            if (isValid(selection1)) {
                if (queue) {
                    selection1.enqueueTarget(building, Action.DEFAULT, false);
                } else {
                    selection1.initTarget(building, Action.DEFAULT, false);
                }
            }
        }
    }

    @Override
    public void setRallyPoint(@NonNull Building building, @Nullable Target target) {
        if (isValid(building) && target != null)
            building.setRallyPoint(target);
    }

    @Override
    public void setRallyPoint(@NonNull Building building, int grid_x, int grid_y) {
        setRallyPoint(building, new LandscapeTarget(grid_x, grid_y));
    }

    @Override
    public void setTarget(Selectable<?> @NonNull [] selection, @NonNull Target target, @NonNull Action action,
            boolean aggressive) {
        // Unit.setTarget asserts the target is alive. Buildings can be destroyed between the moment
        // the AI picks them and the moment the order is issued (a construction site blown up while
        // builders are walking to it), which crashed the game. Drop the order instead.
        // //added by ikill240c 2026-09-10 16:00
        if (target.isDead()) //added by ikill240c 2026-09-10 16:00
            return; //added by ikill240c 2026-09-10 16:00
        for (Selectable<?> selection1 : selection) {
            if (isValid(selection1)) {
                selection1.initTarget(target, action, aggressive);
            }
        }
    }

    public void killSelection(Selectable<?> @NonNull [] selection) {
        for (Selectable<?> selection1 : selection) {
            if (selection1 != null) {
                selection1.hit(10000, 0f, 1f, this);
            }
        }
    }

    @Override
    public final void setSailingTarget(Selectable<?> @NonNull [] selection, @NonNull Target target) {
        if (selection.length == 0) return;
        for (int i = 0; i < selection.length; i++) {
            if (isValid(selection[i]))
                selection[i].initTarget(target, Action.MOVE, false);
        }
    }

    @Override
    public final void setSailingTarget(Selectable<?> @NonNull [] selection, int grid_x, int grid_y) {
        if (selection.length == 0) return;
        int grid_size = world.getUnitGrid().getGridSize();
        if (grid_x < 0 || grid_x >= grid_size || grid_y < 0 || grid_y >= grid_size) return;
        Target target = new LandscapeTarget(grid_x, grid_y);
        for (int i = 0; i < selection.length; i++) {
            if (isValid(selection[i]))
                selection[i].initTarget(target, Action.MOVE, false);
        }
    }

    @Override
    public void setLandscapeTarget(Selectable<?> @NonNull [] selection, int grid_x, int grid_y, @NonNull Action action,
            boolean aggressive) {
        if (selection.length == 0)
            return;
        int grid_size = world.getUnitGrid().getGridSize();
        if (grid_x < 0 || grid_x >= grid_size || grid_y < 0 || grid_y >= grid_size)
            return;
        Target[] targets = computeFormationTargets(selection, grid_x, grid_y);
        for (int i = 0; i < selection.length; i++) {
            if (isValid(selection[i]) && targets[i] != null)
                selection[i].initTarget(targets[i], action, aggressive);
        }
    }

    // Was `world.getUnitGrid().findGridTargets(grid_x, grid_y, selection.length, ...)` - found N
    // valid nearby cells via spiral scan and assigned them in scan-discovery order, which is not a
    // deliberate formation shape, just whatever the scan happened to find first. Now computes real
    // formation offsets (square/diamond/circle/tight/loose, oriented toward the direction of travel)
    // and finds the nearest valid cell to EACH individual offset position - same per-target null
    // safety as the rest of this session's findGridTargets fixes, since any single offset can still
    // legitimately have no valid nearby cell (blocked area, map edge). //added by ikill240c
    private Target @NonNull [] computeFormationTargets(Selectable<?> @NonNull [] selection, int grid_x, int grid_y) {
        Target[] targets = new Target[selection.length];
        if (selection.length == 1) {
            targets[0] = world.getUnitGrid().findGridTargets(grid_x, grid_y, 1, false)[0];
            return targets;
        }
        // Was a bare reference to the (now-removed) Player-wide `formation` field throughout this
        // method - resolved per-order instead, from whichever units are actually being given this
        // order. See resolveFormation()'s own comment for how it picks one when the selection's
        // units don't all agree. //added by ikill240c
        Formation formation = resolveFormation(selection); //added by ikill240c
        // DEFAULT restores the original pre-formation-system behavior exactly: the nearest N valid
        // cells found by a spiral scan around the destination, assigned in whatever order the scan
        // happens to find them - no deliberate shape, no offset math at all. //added by ikill240c
        if (formation == Formation.DEFAULT) { //added by ikill240c
            return world.getUnitGrid().findGridTargets(grid_x, grid_y, selection.length, false); //added by ikill240c
        } //added by ikill240c
        float avg_x = 0f, avg_y = 0f;
        int valid_count = 0;
        for (Selectable<?> s : selection) {
            if (isValid(s)) {
                avg_x += s.getPositionX();
                avg_y += s.getPositionY();
                valid_count++;
            }
        }
        float dest_x = com.oddlabs.tt.pathfinder.UnitGrid.coordinateFromGrid(grid_x);
        float dest_y = com.oddlabs.tt.pathfinder.UnitGrid.coordinateFromGrid(grid_y);
        float facing_dx = 0f, facing_dy = 0f;
        if (valid_count > 0) {
            avg_x /= valid_count;
            avg_y /= valid_count;
            facing_dx = dest_x - avg_x;
            facing_dy = dest_y - avg_y;
        }
        var offsets = FormationLayout.computeOffsets(
                formation == Formation.BY_TYPE ? Formation.SQUARE : formation, //added by ikill240c
                selection.length, facing_dx, facing_dy);

        Integer[] selection_order = new Integer[selection.length]; //added by ikill240c
        for (int i = 0; i < selection.length; i++) selection_order[i] = i; //added by ikill240c

        if (formation == Formation.BY_TYPE) { //added by ikill240c
            // Front-most offsets (highest projection onto the facing direction) go to the weakest
            // units first (by max hit points - a static, type-based toughness measure, unaffected by
            // current battle damage), with the strongest units filling in behind. Was the reverse
            // (attack-capable units front) - changed per explicit request. A stable sort preserves
            // each group's relative order otherwise, so e.g. multiple peons keep whatever order they
            // were selected in. //added by ikill240c
            float len = (float) Math.sqrt(facing_dx * facing_dx + facing_dy * facing_dy); //added by ikill240c
            float dir_x = len > 0.0001f ? facing_dx / len : 0f; //added by ikill240c
            float dir_y = len > 0.0001f ? facing_dy / len : 0f; //added by ikill240c
            offsets.sort((a, b) -> Float.compare( //added by ikill240c
                    b[0] * dir_x + b[1] * dir_y, //added by ikill240c
                    a[0] * dir_x + a[1] * dir_y)); //added by ikill240c
            Arrays.sort(selection_order, (a, b) -> { //added by ikill240c
                int a_toughness = isValid(selection[a]) ? unitToughness(selection[a]) : 0; //added by ikill240c
                int b_toughness = isValid(selection[b]) ? unitToughness(selection[b]) : 0; //added by ikill240c
                return Integer.compare(a_toughness, b_toughness); // weakest (lowest) sorts first //added by ikill240c
            }); //added by ikill240c
        } //added by ikill240c

        for (int i = 0; i < selection.length && i < offsets.size(); i++) {
            float[] offset = offsets.get(i);
            int unit_index = selection_order[i]; //added by ikill240c
            int offset_grid_x = com.oddlabs.tt.pathfinder.UnitGrid.toGridCoordinate(dest_x + offset[0]);
            int offset_grid_y = com.oddlabs.tt.pathfinder.UnitGrid.toGridCoordinate(dest_y + offset[1]);
            if (offset_grid_x < 0 || offset_grid_x >= world.getUnitGrid().getGridSize() || offset_grid_y < 0
                    || offset_grid_y >= world.getUnitGrid().getGridSize()) {
                // Formation offset landed off the map edge - fall back to the plain destination point
                // instead of leaving this unit with no target at all. //added by ikill240c
                targets[unit_index] = world.getUnitGrid().findGridTargets(grid_x, grid_y, 1, false)[0]; //added by ikill240c
            } else {
                targets[unit_index] = world.getUnitGrid().findGridTargets(offset_grid_x, offset_grid_y, 1, false)[0]; //added by ikill240c
            }
        }
        return targets;
    }

    // Static, type-based "how tough is this kind of unit" measure for BY_TYPE formation ordering -
    // a unit's own max hit points (unaffected by current battle damage, unlike getHitPoints()), or 0
    // for a non-Unit (e.g. a Building somehow in the selection), which sorts to the very front along
    // with any other unrecognized/invalid entry. //added by ikill240c
    private static int unitToughness(@NonNull Selectable<?> s) { //added by ikill240c
        if (s instanceof Unit u) //added by ikill240c
            return u.getTemplate().getMaxHitPoints(); //added by ikill240c
        return 0; //added by ikill240c
    } //added by ikill240c

    @Override //added by ikill240c 2026-09-10 16:45
    public void queueTarget(Selectable<?> @NonNull [] selection, @NonNull Target target, @NonNull Action action, //added by ikill240c 2026-09-10 16:45
            boolean aggressive) { //added by ikill240c 2026-09-10 16:45
        if (target.isDead()) //added by ikill240c 2026-09-10 16:45
            return; //added by ikill240c 2026-09-10 16:45
        for (Selectable<?> selectable : selection) { //added by ikill240c 2026-09-10 16:45
            if (isValid(selectable)) //added by ikill240c 2026-09-10 16:45
                selectable.enqueueTarget(target, action, aggressive); //added by ikill240c 2026-09-10 16:45
        } //added by ikill240c 2026-09-10 16:45
    } //added by ikill240c 2026-09-10 16:45

    @Override //added by ikill240c 2026-09-10 16:45
    public void queueLandscapeTarget(Selectable<?> @NonNull [] selection, int grid_x, int grid_y, //added by ikill240c 2026-09-10 16:45
            @NonNull Action action, boolean aggressive) { //added by ikill240c 2026-09-10 16:45
        if (selection.length == 0) //added by ikill240c 2026-09-10 16:45
            return; //added by ikill240c 2026-09-10 16:45
        int grid_size = world.getUnitGrid().getGridSize(); //added by ikill240c 2026-09-10 16:45
        if (grid_x < 0 || grid_x >= grid_size || grid_y < 0 || grid_y >= grid_size) //added by ikill240c 2026-09-10 16:45
            return; //added by ikill240c 2026-09-10 16:45
        // Same formation-aware offset computation as setLandscapeTarget() above, reused here so a
        // shift-queued move order is spread the same deliberate way as a direct one. //added by ikill240c
        Target[] targets = computeFormationTargets(selection, grid_x, grid_y);
        for (int i = 0; i < selection.length; i++) { //added by ikill240c 2026-09-10 16:45
            if (isValid(selection[i]) && targets[i] != null) //added by ikill240c 2026-09-10 16:45
                selection[i].enqueueTarget(targets[i], action, aggressive); //added by ikill240c 2026-09-10 16:45
        } //added by ikill240c 2026-09-10 16:45
    } //added by ikill240c 2026-09-10 16:45

    private boolean isValid(@Nullable Selectable<?> s) {
        // A mounted unit (inside a building or aboard a ship) cannot accept orders - Unit.setTarget
        // asserts on it, which crashed the game when the AI drafted garrisoned units into a defense
        // group. //added by ikill240c 2026-09-10 00:35
        if (s instanceof Unit unit && unit.isMounted()) //added by ikill240c 2026-09-10 00:35
            return false; //added by ikill240c 2026-09-10 00:35
        if (s == null || s.isDead()) //added by ikill240c
            return false; //added by ikill240c
        if (s.getOwner() == this) //added by ikill240c
            return true; //added by ikill240c
        // Was strictly getOwner() == this - every order-issuing method here (setTarget,
        // setLandscapeTarget, deployUnits, buildRockWeapons, trainChieftain, ...) funnels through
        // this one check, so that strict form silently rejected EVERY command aimed at an ally's
        // building or unit even once SelectionDelegate/SelectionArmy started letting them be
        // selected and shown in the UI - the click would appear to do something (the button existed,
        // the selection updated) but the actual order never applied, which looks exactly like "the
        // AI is fighting my commands" from the outside: nothing this player does to that
        // selectable ever sticks, because it silently never took effect in the first place, and
        // the owning AI's own logic (never told otherwise) just keeps right on making its own
        // decisions for it uninterrupted. Now accepts anything owned by an ally (not an enemy)
        // too - both units and buildings, since the request was for genuine control of teammates'
        // stuff, not just buildings. //added by ikill240c
        if (isEnemy(s.getOwner())) //added by ikill240c
            return false; //added by ikill240c
        // This IS a cross-player order taking effect - record it on the selectable itself so its
        // owning AdvancedAI can recognize "a human teammate just directly controlled this" and
        // hold off reassigning it for a while (see Selectable.human_override_until_tick's own
        // comment for the full reasoning and why this needs to exist at all). Every order-issuing
        // method funnels through this one check, so this is the single place that needs to record
        // it - nothing else does. //added by ikill240c
        s.setHumanOverrideUntilTick(world.getTick() + HUMAN_OVERRIDE_TICKS); //added by ikill240c
        return true; //added by ikill240c
    }

    // How long (in world ticks) a unit or building stays "recently human-overridden" after a
    // teammate directly commands it - see isValid()'s own comment above for why this exists.
    // //added by ikill240c
    private static final int HUMAN_OVERRIDE_TICKS = 300; //added by ikill240c

    public float getStartX() {
        return start_x;
    }

    public void setStartX(float x) {
        start_x = x;
    }

    public float getStartY() {
        return start_y;
    }

    public void setStartY(float y) {
        start_y = y;
    }

    public boolean isEnemy(@NonNull Player other_player) {
        if (other_player.player_info.getTeam() == PlayerInfo.TEAM_NEUTRAL
                || this.player_info.getTeam() == PlayerInfo.TEAM_NEUTRAL) {
            return false;
        }
        return other_player.player_info.getTeam() != this.player_info.getTeam();
    }

    // Whether other_player is a teammate of this player (same non-neutral team), including this
    // player itself. Mirrors isEnemy() above and teamHasBuilding()'s team-comparison pattern -
    // added so friendly, team-wide effects (Chiefs Courage - see Unit.triggerChiefsCourage()) have
    // a single reusable place to check "is this player one of mine or my teammate's".
    // //added by ikill240p 2026-09-14
    public boolean isAlly(@NonNull Player other_player) { //added by ikill240p 2026-09-14
        if (other_player == this) //added by ikill240p 2026-09-14 - a player always counts as their own ally
            return true; //added by ikill240p 2026-09-14
        if (this.player_info.getTeam() == PlayerInfo.TEAM_NEUTRAL //added by ikill240p 2026-09-14 - neutral players have no teammates, same exclusion isEnemy() uses
                || other_player.player_info.getTeam() == PlayerInfo.TEAM_NEUTRAL) { //added by ikill240p 2026-09-14
            return false; //added by ikill240p 2026-09-14
        }
        return other_player.player_info.getTeam() == this.player_info.getTeam(); //added by ikill240p 2026-09-14
    }

    public boolean teamHasBuilding() {
        for (Player player : world.getPlayers()) {
            if (player.getPlayerInfo().getTeam() == player_info.getTeam()
                    && player.getBuildingCountContainer().getNumSupplies() > 0) {
                return true;
            }
        }
        return false;
    }

    public @NonNull Army getUnits() {
        return units;
    }

    public @NonNull Selectable<?> @NonNull [] @NonNull [] classifyUnits() {
        Map<String, List<Selectable<?>>> map = units.getSet().stream().collect(Collectors.groupingBy(
                u -> u.getPrimaryController().getKey()));
        return map.values().stream().map(list -> list.toArray(Selectable[]::new)).toArray(Selectable[][]::new);
    }

    public void magicCast() {
        magics++;
    }

    public int getMagics() {
        return magics;
    }

    public void weaponThrown() {
        weapons_thrown++;
    }

    public int getWeaponsThrown() {
        return weapons_thrown;
    }

    public void unitMoved() {
        units_moved++;
    }

    public int getUnitsMoved() {
        return units_moved;
    }

    public void unitLost() {
        units_lost++;
    }


    public int getUnitsLost() {
        return units_lost;
    }

    public void buildingLost() {
        buildings_lost++;
    }

    public int getBuildingsLost() {
        return buildings_lost;
    }

    public void unitKilled() {
        units_killed++;
    }

    public int getUnitsKilled() {
        return units_killed;
    }

    public void buildingDestroyed() {
        buildings_destroyed++;
    }

    public int getBuildingsDestroyed() {
        return buildings_destroyed;
    }

    public void harvested(@NonNull Class<? extends Supply> type) {
        if (type == TreeSupply.class) {
            tree_harvested++;
        } else if (type == RockSupply.class) {
            rock_harvested++;
        } else if (type == IronSupply.class) {
            iron_harvested++;
        } else if (type == RubberSupply.class) {
            rubber_harvested++;
        } else
            throw new RuntimeException();
    }

    public int getTreeHarvested() {
        return tree_harvested;
    }

    public int getRockHarvested() {
        return rock_harvested;
    }

    public int getIronHarvested() {
        return iron_harvested;
    }

    public int getRubberHarvested() {
        return rubber_harvested;
    }
}
