package com.oddlabs.tt.player;

import com.oddlabs.matchmaking.Game;
import com.oddlabs.tt.landscape.LandscapeTarget;
import com.oddlabs.tt.landscape.World; //added by ikill240c 2026-09-09 23:55
import com.oddlabs.tt.model.Abilities;
import com.oddlabs.tt.model.Action;
import com.oddlabs.tt.model.Building;
import com.oddlabs.tt.model.BuildingTemplate;
import com.oddlabs.tt.model.RacesResources; // needed for RacesResources.NUM_MAGIC used by chief hit-and-run //added by
                                            // ikill240c 2026-09-08 04:00
import com.oddlabs.tt.model.LandBuilding;
import com.oddlabs.tt.model.DeployType;
import com.oddlabs.tt.model.Race;
import com.oddlabs.tt.model.Selectable;
import com.oddlabs.tt.model.Ship;
import com.oddlabs.tt.model.Unit;
import com.oddlabs.tt.model.IronSupply;
import com.oddlabs.tt.model.RockSupply; //added by ikill240c 2026-09-10 15:10
import com.oddlabs.tt.model.RubberSupply; //added by ikill240c 2026-09-10 15:10
import com.oddlabs.tt.model.Supply; //added by ikill240c 2026-09-10 15:10
import com.oddlabs.tt.landscape.TreeSupply; //added by ikill240c 2026-09-10 15:10
import com.oddlabs.tt.model.behaviour.Controller;
import com.oddlabs.tt.model.behaviour.PlaceBuildingController;
import com.oddlabs.tt.model.behaviour.RepairController;
import com.oddlabs.tt.model.behaviour.WalkController;
import com.oddlabs.tt.model.weapon.IronAxeWeapon;
import com.oddlabs.tt.model.weapon.IronSpearWeapon;
import com.oddlabs.tt.model.weapon.RockAxeWeapon;
import com.oddlabs.tt.model.weapon.RockSpearWeapon;
import com.oddlabs.tt.model.weapon.RubberAxeWeapon;
import com.oddlabs.tt.model.weapon.RubberSpearWeapon;
import com.oddlabs.tt.pathfinder.FindOccupantFilter;
import com.oddlabs.tt.pathfinder.ScanFilter;
import com.oddlabs.tt.pathfinder.Occupant;
import com.oddlabs.tt.util.Target;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class AdvancedAI extends AI {
    public static final int DIFFICULTY_EASY = 0;
    public static final int DIFFICULTY_NORMAL = 1;
    public static final int DIFFICULTY_HARD = 2;

    private static final int SCORE_PEON = 2;
    private static final int SCORE_WARRIOR_ROCK = 5;
    private static final int SCORE_WARRIOR_IRON = 10;
    private static final int SCORE_WARRIOR_RUBBER = 15;
    private static final int SCORE_CHIEFTAIN = 35;
    private static final float[] DEFENSE_FACTOR = new float[]{1f, 2f, 3f};

    private static final int[] MIN_UNITS_BUILDING_WEAPONS = new int[]{10, 20, 20};
    private static final int[] MIN_WEAPONS_IN_STOCK = new int[]{35, 15, 0}; // rushing penalty
    private static final int[] MIN_UNITS_REPRODUCING = new int[]{0, 5, 20};
    private static final int[] MAX_UNITS_GATHERING_TREE = new int[]{2, 5, 80};
    private static final int[] MAX_UNITS_GATHERING_ROCK = new int[]{1, 3, 15};
    private static final int[] MAX_UNITS_GATHERING_IRON = new int[]{1, 3, 60};
    private static final int[] MAX_UNITS_GATHERING_RUBBER = new int[]{1, 3, 25};

    private static final float WARRIOR_STUCK_THRESHOLD = 4f;//added by ikill240c og 8
    private final java.util.Map<Unit, float[]> warrior_positions = new java.util.HashMap<>();//added by ikill240c

    private static final float CHIEFTAIN_IDLE_RETURN_SECONDS = 30f;//added by ikill240c
    private float chieftain_idle_time = 0f;//added by ikill240c
    // Configurable: seconds a chieftain must be idle+undamaged before the AI auto-heals it. //added by ikill240c 2026-09-09 23:10
    private float chieftainHealIdleSeconds() { //added by ikill240c 2026-09-09 23:10
        return getOwner().getWorld().getChieftainHealIdleSeconds(); //added by ikill240c 2026-09-09 23:10
    } //added by ikill240c 2026-09-09 23:10
    // Configurable: HP restored per heal tick when an idle chieftain auto-heals. //added by ikill240c 2026-09-09 23:10
    private int chieftainHealAmount() { //added by ikill240c 2026-09-09 23:10
        return getOwner().getWorld().getChieftainHealAmount(); //added by ikill240c 2026-09-09 23:10
    } //added by ikill240c 2026-09-09 23:10
    private static final float CHIEF_HITRUN_HEALTH_PCT = 0.7f;//added by ikill240c
    private static final int CHIEF_HITRUN_MAX_ESCORT = 20;//added by ikill240c
    private static final float CHIEF_HITRUN_SEARCH_RADIUS = 225f;//added by ikill240c
    private boolean chief_hitrun_active = false;//added by ikill240c

    private final java.util.Set<Unit> patrol_units = new java.util.HashSet<>();//added by ikill240c
    private final java.util.Set<Unit> escort_units = new java.util.HashSet<>();//added by ikill240c
    // These were ThreadLocalRandom statics, so every client rolled its own value and multiplayer
    // desynced. World.rollMatchValue is seeded from the mapcode, so all peers agree. //added by ikill240c 2026-09-09 23:55
    private int escortsPerGatherGroup() { //added by ikill240c 2026-09-09 23:55
        return getOwner().getWorld().rollMatchValue(World.SALT_ESCORTS_PER_GATHER_GROUP, 3, 15); //added by ikill240c 2026-09-09 23:55
    } //og 15
    private int patrolForceSize() { //added by ikill240c 2026-09-09 23:55
        return getOwner().getWorld().rollMatchValue(World.SALT_PATROL_FORCE_SIZE, 7, 22); //added by ikill240c 2026-09-09 23:55
    } //og 4
    private static final float PATROL_RADIUS = 150f;//og 20
    private int patrol_waypoint_index = 0;

    private static final int OUTPOST_TRIGGER_DIST = 600;//added by ikill240c og 60 (very close)
    private boolean outpost_built = false;

    // Configurable: how many Quarters the AI aims to build. //added by ikill240c 2026-09-09 23:10
    private int targetNumQuarters() { //added by ikill240c 2026-09-09 23:10
        return getOwner().getWorld().getTargetNumQuarters(); //added by ikill240c 2026-09-09 23:10
    } //added by ikill240c 2026-09-09 23:10
    // Customizable Armory target, mirrors TARGET_NUM_QUARTERS. Default 1 - most games only need one Armory,
    // but this can be raised for bigger maps / higher difficulties. //added by ikill240c 2026-09-08 16:30
    // Configurable: how many Armories the AI aims to build. //added by ikill240c 2026-09-09 23:10
    private int targetNumArmories() { //added by ikill240c 2026-09-09 23:10
        return getOwner().getWorld().getTargetNumArmories(); //added by ikill240c 2026-09-09 23:10
    } //added by ikill240c 2026-09-09 23:10
    // Customizable concurrent-construction caps for Quarters and Armories, mirroring MAX_CONCURRENT_TOWERS below.
    // Default 1 (build one at a time) keeps peon allocation predictable; raise to allow parallel construction.
    // //added by ikill240c 2026-09-08 16:30
    // Configurable: max Quarters the AI builds concurrently. //added by ikill240c 2026-09-09 23:10
    private int maxConcurrentQuarters() { //added by ikill240c 2026-09-09 23:10
        return getOwner().getWorld().getMaxConcurrentQuarters(); //added by ikill240c 2026-09-09 23:10
    } //added by ikill240c 2026-09-09 23:10
    // Configurable: max Armories the AI builds concurrently. //added by ikill240c 2026-09-09 23:10
    private int maxConcurrentArmories() { //added by ikill240c 2026-09-09 23:10
        return getOwner().getWorld().getMaxConcurrentArmories(); //added by ikill240c 2026-09-09 23:10
    } //added by ikill240c 2026-09-09 23:10

    private static final float DEFENSE_SCAN_RADIUS = 10f;//added by ikill240c og 45 base 30
    private final List<int[]> occupied_zones = new ArrayList<>();
    private final java.util.Set<Unit> attacking_units = new java.util.HashSet<>();//added by ikill240c
    private static final float RETREAT_SCAN_RADIUS = 10f;//added by ikill240c og 40

    // Configurable: how many resource-outpost towers the AI builds. //added by ikill240c 2026-09-09 23:10
    private int numResourceTowers() { //added by ikill240c 2026-09-09 23:10
        return getOwner().getWorld().getNumResourceTowers(); //added by ikill240c 2026-09-09 23:10
    } //added by ikill240c 2026-09-09 23:10
    private int resource_towers_built = 0;
    private static final int BUILDERS_PER_TOWER_TARGET = 10; // matches getPeons(10) used to start a tower //added by ikill240c
    private static final int TOWER_SPACING_SLOTS = 12;   // towers per ring, ~45° apart //added by ikill240c
    private static final float TOWER_BASE_RADIUS = 16f;
    private static final float TOWER_RADIUS_STEP = 10f;  // radius growth per full ring, og 10
    private static final int TOWER_SCAN_RANGE = 16;      // tight, to avoid crossing into unsafe terrain, og 12
    // Configurable: max towers the AI builds concurrently. //added by ikill240c 2026-09-09 23:10
    private int maxConcurrentTowers() { //added by ikill240c 2026-09-09 23:10
        return getOwner().getWorld().getMaxConcurrentTowers(); //added by ikill240c 2026-09-09 23:10
    } //added by ikill240c 2026-09-09 23:10
// Replaces UNITS_PER_TOWER1..UNITS_PER_TOWER40.
// Row index = tower tier - 1 (row 0 = tier 1, row 39 = tier 40).
// Column index = difficulty (0=Easy, 1=Normal, 2=Hard).
    private static final int[][] UNITS_PER_TOWER = { //added by ikill240c
            {120, 100, 60},   // tier 1
            {160, 110, 70},   // tier 2
            {200, 130, 80},   // tier 3
            {300, 150, 90},   // tier 4
            {600, 160, 100},  // tier 5
            {900, 170, 120},  // tier 6
            {1200, 180, 140},  // tier 7
            {1500, 190, 160},  // tier 8
            {1800, 200, 180},  // tier 9
            {4500, 300, 200},  // tier 10
            {4500, 400, 210},  // tier 11
            {4500, 500, 215},  // tier 12
            {4500, 600, 220},  // tier 13
            {4500, 700, 225},  // tier 14
            {4500, 800, 230},  // tier 15
            {4500, 900, 235},  // tier 16
            {4500, 1000, 240},  // tier 17
            {4500, 1100, 245},  // tier 18
            {4500, 1200, 250},  // tier 19
            {4500, 1300, 255},  // tier 20
            {4500, 1400, 260},  // tier 21
            {4500, 1500, 265},  // tier 22
            {4500, 1600, 270},  // tier 23
            {4500, 1700, 275},  // tier 24
            {4500, 1800, 280},  // tier 25
            {4500, 1900, 285},  // tier 26
            {4500, 2000, 290},  // tier 27
            {4500, 2100, 295},  // tier 28
            {4500, 2200, 300},  // tier 29
            {4500, 2300, 305},  // tier 30
            {4500, 2400, 310},  // tier 31
            {4500, 2500, 315},  // tier 32
            {4500, 2600, 320},  // tier 33
            {4500, 2700, 325},  // tier 34
            {4500, 2800, 330},  // tier 35
            {4500, 2900, 335},  // tier 36
            {4500, 3000, 340},  // tier 37
            {4500, 3100, 345},  // tier 38
            {4500, 3200, 350},  // tier 39
            {4500, 3300, 355},  // tier 40
    };

    private static final int SHIP_PEONS = 26;
    private static final int SHIP_WARRIORS = 26;
    private static final int SHIP_BUILDERS = 20;
    private static final float SHIP_MIN_HEALTH = 0.5f;
    private static final int SHIP_HOME_RANGE = 50;
    private static final int FLEET_SIZE = 3;

    private final int difficulty;

    private final int[] NUM_WARRIORS = new int[]{3, 7, 25};
    private final int[] NUM_WARRIORS_INCREASE = new int[]{1, 11, 21};
    private final int[] NUM_WARRIORS_MAX = new int[]{300, 1250, 4500};
    private final int[] NUM_WARRIORS_FOR_CHIEFTAIN = new int[]{80, 50, 15};

    private static final class SupplyLocationFilter implements ScanFilter {//added by ikill240c
        private final Class<?> supply_class;
        private final int range;
        private Target result;

        SupplyLocationFilter(Class<?> supply_class, int range) {
            this.supply_class = supply_class;
            this.range = range;
        }

        @Override
        public int getMinRadius() {
            return 0;
        }

        @Override
        public int getMaxRadius() {
            return range;
        }

        @Override
        public boolean filter(int grid_x, int grid_y, Occupant occ) {
            if (occ != null && supply_class.isInstance(occ)) {
                result = (Target) occ;
                return true;
            }
            return false;
        }

        boolean found() {
            return result != null;
        }

        Target getTarget() {
            return result;
        }

        int getX() {
            return result.getGridX();
        }

        int getY() {
            return result.getGridY();
        }
    }

    private static final class Threat { //added by ikill240c
        final LandscapeTarget target;
        final int score;

        Threat(LandscapeTarget target, int score) {
            this.target = target;
            this.score = score;
        }
    }

    private @Nullable LandscapeTarget defense_target = null;

    // Seconds since the last attack was launched. Used to break out of a permanent "never favorable"
    // stalemate where the AI sits on a big army and never attacks again. //added by ikill240c 2026-09-10 15:10
    private float time_since_attack = 0f; //added by ikill240c 2026-09-10 15:10
    private static final float ATTACK_STALL_SECONDS = 90f; //added by ikill240c 2026-09-10 15:10
    private static final int MIN_WARRIORS_FOR_STALL_ATTACK = 6; //added by ikill240c 2026-09-10 15:10

    public AdvancedAI(@NonNull Player owner, UnitInfo unit_info, int difficulty) {
        super(owner, unit_info);
        this.difficulty = difficulty;
    }

    public int getDifficulty() {
        return difficulty;
    }

    @Override
    public void animate(float t) {   //added by ikill240c
        time_since_attack += t; //added by ikill240c 2026-09-10 15:10
        nodeCheckStuckWarriors(t);
        nodeCheckIdleChieftain(t);
        nodeCheckAttackingArmy(t); // check if an ongoing attack has turned unfavorable and should retreat //added by ikill240c 2026-09-08 03:00
        if (!shouldDoAction(t))
            return;

        // ---- ECONOMY: buildings, in strict order Quarters(1) -> Armory -> remaining Quarters/outposts ----
        // nodeBuildArmory() used to only be reachable through the attack path (via
        // nodeDeployUnitsInArmory's else-branch), which meant it silently stopped running once attacks
        // became gated on favorable odds below. It is now called directly and unconditionally here so
        // the economy always progresses regardless of whether the AI is currently attacking.
        // //added by ikill240c 2026-09-08 16:30
        reclassify();
        nodeBuildQuarters();
        nodeBuildArmory();
        // Always deploy any finished armory stock immediately so the AI can start gathering,
        // defending, and attacking instead of getting stuck after the build phase.
        // //added by ikill240c 2026-09-10 00:00
        nodeDeployArmy();
        nodeCheckOutpost();
        reclassify();
        nodeBuildResourceTowers();
        reclassify();

        // ---- DEFENSE: base defense, ally assistance, chieftain healing - always runs, never gated ----
        nodeDefendBase();
        reclassify();
        nodeHealChieftain();
        reclassify();
        nodeDefendAllies();
        reclassify();

        // ---- TOWERS: guard-tower count target scales with population (see UNITS_PER_TOWER table) ----
        nodeUpdateTowerTarget(); //added by ikill240c 2026-09-08 16:30 - extracted from an inline loop that used to live directly in animate()
        reclassify();

        if (isArchipelago()) {
            reclassify();
            if (baseBuildingsDone())
                nodeBuildShipAndLoad();
            nodeUseShip();
        }

        // ---- OFFENSE: the main chief-led attack force, gated on favorable odds only ----
        // Separated from patrol/escort/hit-and-run below, which run unconditionally: those are
        // small-scale, low-risk actions (defending gatherers, manning patrol points, opportunistic
        // chief raids) that shouldn't wait for the whole army to be favored before doing anything.
        // //added by ikill240c 2026-09-08 16:30
        reclassify();
        // The old gate compared this player's army against the COMBINED strength of every enemy, so
        // in any game with more than one opponent (or after a single bad fight) it could never be
        // satisfied again and the AI simply stopped attacking for the rest of the match. Compare
        // against the strongest single enemy instead, and break a long stalemate by attacking anyway
        // once the AI has a real army sitting idle. //added by ikill240c 2026-09-10 15:10
        boolean favorable = computeArmyStrength(getOwner()) >= computeStrongestEnemyStrength(); //added by ikill240c 2026-09-10 15:10
        boolean stalled = time_since_attack >= ATTACK_STALL_SECONDS //added by ikill240c 2026-09-10 15:10
                && countWarriors() >= MIN_WARRIORS_FOR_STALL_ATTACK; //added by ikill240c 2026-09-10 15:10
        if (favorable || stalled) { //added by ikill240c 2026-09-10 15:10
            time_since_attack = 0f; //added by ikill240c 2026-09-10 15:10
            nodeAttackWithWarriorsAndChieftain(NUM_WARRIORS[difficulty],
                    NUM_WARRIORS[difficulty] >= NUM_WARRIORS_FOR_CHIEFTAIN[difficulty]);
        }

        // ---- PATROL / ESCORT / CHIEF HIT-AND-RUN: independent of the offense gate above ----
        nodeChiefHitAndRun();
        nodeChiefHitAndRunReturn();
        nodePatrol();//added by ikill240c
        nodeEscortGatherers();
        nodeAssignIdlePeons();
        reclassify(); //added by ikill240c 2026-09-10 15:10
        nodeAssignIdleGatherers(); //added by ikill240c 2026-09-10 15:10
        if (getOwner().hasActiveChieftain()) {
            getOwner().getRace().getChieftainAI().decide(getOwner().getChieftain());
        }
        // Also let any additional (converted) chieftains make their own magic decisions, not just the
        // primary one - see Player.getExtraChieftains(). //added by ikill240c 2026-09-08 17:00
        for (Unit extra : getOwner().getExtraChieftains()) {
            if (!extra.isDead()) {
                getOwner().getRace().getChieftainAI().decide(extra);
            }
        }
    }

    // Extracted from animate(): picks the tower-count tier appropriate for current population and
    // asks nodeGuardTowers to build/man towers up to that tier. //added by ikill240c 2026-09-08 16:30
    private void nodeUpdateTowerTarget() {
        int num_supplies = getOwner().getUnitCountContainer().getNumSupplies();
        for (int tier = UNITS_PER_TOWER.length; tier >= 1; tier--) {
            if (num_supplies > UNITS_PER_TOWER[tier - 1][difficulty]) {
                nodeGuardTowers(tier);
                break;
            }
        }
    }

    private void nodePatrol() {//added by ikill240c
        patrol_waypoint_index++;

        Building home = homeBuilding();
        if (home == null)
            return;
        int hx = home.getGridX();
        int hy = home.getGridY();

        List<int[]> points = new ArrayList<>();
        int num_points = 6;
        for (int i = 0; i < num_points; i++) {
            double angle = (patrol_waypoint_index + i) * (2 * Math.PI / num_points);
            int px = (int) (hx + PATROL_RADIUS * Math.cos(angle));
            int py = (int) (hy + PATROL_RADIUS * Math.sin(angle));
            points.add(new int[]{px, py});
        }
        addGathererLocation(points, getGatherTreePeons());
        addGathererLocation(points, getGatherRockPeons());
        addGathererLocation(points, getGatherIronPeons());
        addGathererLocation(points, getGatherRubberPeons());

        Selectable<?>[] idle = getIdleWarriors();
        if (idle == null || idle.length == 0 || points.isEmpty())
            return;

        int patrol_count = Math.min(patrolForceSize(), idle.length);
        for (int i = 0; i < patrol_count; i++) {
            int[] point = points.get(i % points.size());
            Unit warrior = (Unit) idle[i];
            getOwner().setLandscapeTarget(Selectable.newArray(warrior), point[0], point[1], Action.DEFEND, true);
            patrol_units.add(warrior);
        }
    }

    private void nodeEscortGatherers() {//added by ikill240c
        escortGroup(getGatherTreePeons());
        escortGroup(getGatherRockPeons());
        escortGroup(getGatherIronPeons());
        escortGroup(getGatherRubberPeons());
    }

    private void escortGroup(Selectable<?>[] gatherers) {//added by ikill240c
        if (gatherers == null || gatherers.length == 0)
            return;
        Selectable<?>[] idle_warriors = getIdleWarriors();
        if (idle_warriors == null || idle_warriors.length == 0)
            return;

        Selectable<?> rep = gatherers[0];
        int count = Math.min(escortsPerGatherGroup(), idle_warriors.length);
        for (int i = 0; i < count; i++) {
            Unit warrior = (Unit) idle_warriors[i];
            getOwner().setLandscapeTarget(Selectable.newArray(warrior), rep.getGridX(), rep.getGridY(), Action.DEFEND,
                    true);
            escort_units.add(warrior);
        }
    }

    private void addGathererLocation(List<int[]> points, Selectable<?>[] gatherers) {//added by ikill240c
        if (gatherers != null && gatherers.length > 0) {
            Selectable<?> rep = gatherers[0];
            points.add(new int[]{rep.getGridX(), rep.getGridY()});
        }
    }

    // (old per-AI stuck-gatherer check removed - fully superseded by Unit.updateGatherStuckCheck(),
    // which runs for every peon regardless of owner, so it also covers human-controlled peons.
    // //added by ikill240c 2026-09-10 12:00)
    private void nodeCheckAttackingArmy(float t) {//added by ikill240c
        attacking_units.removeIf(Unit::isDead);
        if (attacking_units.isEmpty())
            return;

        float cx = 0f, cy = 0f;
        for (Unit u : attacking_units) {
            cx += u.getGridX();
            cy += u.getGridY();
        }
        cx /= attacking_units.size();
        cy /= attacking_units.size();

        float own_score = 0f;
        for (Unit u : attacking_units)
            own_score += getUnitScore(u);

        FindOccupantFilter<Unit> filter = new FindOccupantFilter<>(cx, cy, RETREAT_SCAN_RADIUS, null, Unit.class);
        getUnitGrid().scan(filter, (int) cx, (int) cy);
        float enemy_score = 0f;
        for (Unit u : filter.getResult()) {
            if (!u.isDead() && getOwner().isEnemy(u.getOwner()))
                enemy_score += getUnitScore(u);
        }

        if (enemy_score > own_score * 1.5f) { //1.5f is a tunable margin (retreat only when clearly outmatched, not on a near-even fight)
            Building home = homeBuilding();
            if (home != null) {
                //added by ikill240c 2026-09-09 17:30 - Send the retreating army to home coordinates with MOVE so it walks back out instead of re-entering a supply building.
                getOwner().setLandscapeTarget(attacking_units.toArray(new Selectable<?>[0]), home.getGridX(),
                        home.getGridY(), Action.MOVE, false);
            }
            attacking_units.clear();
        }
    }

    private void nodeCheckStuckWarriors(float t) {//added by ikill240c
        for (Selectable<?> s : getOwner().getUnits().getSet()) {
            if (!(s instanceof Unit unit) || unit.isDead() || unit.isMounted() || !unit.isWarrior())
                continue;
            if (!(unit.getCurrentController() instanceof WalkController))
                continue;
            float[] rec = warrior_positions.get(unit);
            if (rec == null) {
                warrior_positions.put(unit, new float[]{unit.getGridX(), unit.getGridY(), 0f});
                continue;
            }
            float dx = unit.getGridX() - rec[0];
            float dy = unit.getGridY() - rec[1];
            if (dx * dx + dy * dy < 4f) {
                rec[2] += t;
                if (rec[2] > WARRIOR_STUCK_THRESHOLD) {
                    Building home = homeBuilding();
                    if (home != null) {
                        // If the warrior is already standing on the home tile, do not send it back to the
                        // same location again; doing so creates a zero-length path and can trip the BezierPath
                        // assertion crash on the next movement update. // ikill240c 2026-09-09 20:00
                        if (unit.getGridX() == home.getGridX() && unit.getGridY() == home.getGridY()) {
                            rec[2] = 0f;
                            continue;
                        }
                        // Route stuck warriors back toward home coordinates with MOVE so they walk out of the
                        // base zone instead of re-entering a quarters or armory tile. // ikill240c 2026-09-09 17:30
                        getOwner().setLandscapeTarget(Selectable.newArray(unit), home.getGridX(),
                                home.getGridY(), Action.MOVE, false);
                    }
                    rec[2] = 0f;
                }
            } else {
                rec[0] = unit.getGridX();
                rec[1] = unit.getGridY();
                rec[2] = 0f;
            }
        }
        warrior_positions.keySet().removeIf(Unit::isDead);
    }

    private void nodeCheckIdleChieftain(float t) {//added by ikill240c
        Selectable<?>[] idle_chieftains = getIdleChieftains();
        if (idle_chieftains != null && idle_chieftains.length > 0) {
            chieftain_idle_time += t;
            if (chieftain_idle_time > CHIEFTAIN_IDLE_RETURN_SECONDS) {
                Building home = homeBuilding();
                if (home != null) {
                    Unit chief = (Unit) idle_chieftains[0];
                    // Ignore redundant home-return commands when the idle chieftain is already on the
                    // home tile; that would otherwise create the same zero-length movement path that can
                    // trigger the BezierPath assertion crash. // ikill240c 2026-09-09 20:00
                    if (chief.getGridX() != home.getGridX() || chief.getGridY() != home.getGridY()) {
                        // Move idle chieftains back to the home zone so they do not sit inside a quarters or
                        // armory tile and block the rest of the AI’s unit flow. // ikill240c 2026-09-09 17:30
                        getOwner().setLandscapeTarget(Selectable.newArray(chief), home.getGridX(),
                                home.getGridY(), Action.MOVE, false);
                    }
                }
                chieftain_idle_time = 0f;
            }
        } else {
            chieftain_idle_time = 0f;
        }
    }

    private void nodeCheckOutpost() {//added by ikill240c
        if (outpost_built)
            return;
        Building home = homeBuilding();
        if (home == null)
            return;
        int hx = home.getGridX();
        int hy = home.getGridY();

        if (isGatheringTooFar(getGatherTreePeons(), hx, hy)
                || isGatheringTooFar(getGatherRockPeons(), hx, hy)
                || isGatheringTooFar(getGatherIronPeons(), hx, hy)
                || isGatheringTooFar(getGatherRubberPeons(), hx, hy)) {
            buildResourceOutpost(hx, hy);
        }
    }

    private boolean isGatheringTooFar(Selectable<?>[] gatherers, int hx, int hy) {//added by ikill240c
        if (gatherers == null)
            return false;
        for (Selectable<?> s : gatherers) {
            int dx = s.getGridX() - hx;
            int dy = s.getGridY() - hy;
            if (dx * dx + dy * dy > OUTPOST_TRIGGER_DIST * OUTPOST_TRIGGER_DIST)
                return true;
        }
        return false;
    }

    private void buildResourceOutpost(int hx, int hy) {//added by ikill240c
        SupplyLocationFilter filter = new SupplyLocationFilter(IronSupply.class, 200);
        getUnitGrid().scan(filter, hx, hy);
        int ox = filter.found() ? filter.getX() : hx;
        int oy = filter.found() ? filter.getY() : hy;

        // Outposts should use the normal builder pool as well so expansion still happens once enough
        // peons exist, instead of waiting on a full idle-peon count that never materializes.
        // //added by ikill240c 2026-09-10 13:15
        Selectable<?>[] builders = getPeons(20);
        if (builders.length < 20)
            return;

        boolean quarters_ok = buildBuilding(Race.BUILDING_QUARTERS, firstN(builders, 10), ox, oy, 15);
        boolean armory_ok = buildBuilding(Race.BUILDING_ARMORY, lastN(builders, 10), ox, oy, 15);
        if (quarters_ok && armory_ok) {
            outpost_built = true;
            occupied_zones.add(new int[]{ox, oy});
        }
    }

    private void nodeDefendBase() {//added by ikill240c
        List<Threat> threats = new ArrayList<>();

        if (getQuarters() != null) {
            for (Selectable<?> q : getQuarters()) {
                if (q.isDead()) continue;
                int score = scanForEnemies((Building) q);
                if (score > 0) threats.add(new Threat(defense_target, score));
            }
        }
        // Scan every armory in the player’s roster so multi-armory setups contribute fully to base
        // defense instead of only the first armory being considered for threat response.
        // ikill240c 2026-09-09 17:30
        if (getArmory() != null) {
            for (Selectable<?> a : getArmory()) {
                Building armory = (Building) a;
                if (armory.isDead()) continue;
                int score = scanForEnemies(armory);
                if (score > 0) threats.add(new Threat(defense_target, score));
            }
        }
        for (int[] zone : occupied_zones) {
            int score = scanForEnemiesAt(zone[0], zone[1]);
            if (score > 0) threats.add(new Threat(defense_target, score));
        }
        if (threats.isEmpty())
            return;

        int total_defense_score = 0;
        if (getDefendingUnits() != null) {
            for (Selectable<?> d : getDefendingUnits())
                total_defense_score += getUnitScore((Unit) d);
        }

        List<Threat> scaled = new ArrayList<>();
        for (Threat t : threats)
            scaled.add(new Threat(t.target, (int) (DEFENSE_FACTOR[difficulty] * t.score)));

        nodeDeployArmy();
        nodeDefendMultiple(scaled, total_defense_score);
    }

    // Never let more than this fraction of the CURRENT total peon population be drafted into defense
    // duty in a single call. Without a cap, sustained enemy pressure could re-draft peons from the
    // gather/idle pools every single AI tick indefinitely (new small threats keep total_score_needed
    // above zero), which never technically locks any one peon forever but keeps churning a large
    // fraction of the workforce in and out of brief defend duty, starving the economy of any real
    // progress - reported as "AI stops building/attacking until a building is destroyed", since losing
    // a scanned building (fewer locations threat-scanned) was the only thing that ever dropped the
    // required score low enough to stop the churn. Capping this guarantees the AI always keeps most of
    // its workforce gathering, even under constant harassment. Tune to taste. //added by ikill240c 2026-09-10 12:00
    private static final float MAX_PEON_DEFENSE_FRACTION = 0.4f;

    private void nodeDefendMultiple(@NonNull List<Threat> threats, int total_defense_score) {//ADDED BY IKILL240
        int total_score_needed = 0;
        for (Threat t : threats)
            total_score_needed += t.score;
        total_score_needed -= total_defense_score;
        if (total_score_needed <= 0)
            return;

        patrol_units.removeIf(Unit::isDead);
        escort_units.removeIf(Unit::isDead);

        List<Unit> pool = new ArrayList<>();
        int result = 0;

        // Tier 1: recall patrol units first.
        for (Unit u : new java.util.ArrayList<>(patrol_units)) {
            if (result >= total_score_needed) break;
            if (!canReceiveOrders(u)) //added by ikill240c 2026-09-10 00:35
                continue; //added by ikill240c 2026-09-10 00:35
            pool.add(u);
            result += getUnitScore(u);
            patrol_units.remove(u);
        }
        // Tier 2: recall escort units.
        if (result < total_score_needed) {
            for (Unit u : new java.util.ArrayList<>(escort_units)) {
                if (result >= total_score_needed) break;
                if (!canReceiveOrders(u)) //added by ikill240c 2026-09-10 00:35
                    continue; //added by ikill240c 2026-09-10 00:35
                pool.add(u);
                result += getUnitScore(u);
                escort_units.remove(u);
            }
        }
        // Tier 3: remaining idle warriors.
        if (result < total_score_needed && getIdleWarriors() != null)
            result = addFromList(getIdleWarriors(), pool, result, total_score_needed);
        // Tier 4: armory deployment already happens via nodeDeployArmy() before this is called.
        // Tier 5 (last resort): peons, capped at MAX_PEON_DEFENSE_FRACTION of TOTAL peon population so
        // sustained pressure can never fully paralyze the economy. The cap is now computed from
        // countTotalPeons() (which includes defending, walking, and building peons) and starts
        // peons_drafted from the number of peons already in DefendController, making it an absolute
        // limit on total peons in defense mode rather than a per-call fraction of remaining peons.
        // This fixes the bug where sustained enemy pressure could progressively drain the majority of
        // peons into defense over multiple AI ticks, leaving too few for building - reported as
        // "AI stops building/attacking until a building is destroyed". //added by ikill240c 2026-09-09 15:05
        int total_peons = countTotalPeons();
        int peon_cap = (int) (total_peons * MAX_PEON_DEFENSE_FRACTION);
        int peons_drafted = countDefendingPeons(); // start from already-defending count //added by ikill240c 2026-09-09 15:05
        int remaining_peon_capacity = Math.max(0, peon_cap - peons_drafted); //added by ikill240c 2026-09-09 15:05
        if (result < total_score_needed && remaining_peon_capacity > 0 && getIdlePeons() != null) {
            int before = pool.size();
            result = addFromListCapped(getIdlePeons(), pool, result, total_score_needed, remaining_peon_capacity);
            peons_drafted += pool.size() - before;
            remaining_peon_capacity = Math.max(0, peon_cap - peons_drafted); //added by ikill240c 2026-09-09 15:05
        }
        if (result < total_score_needed && remaining_peon_capacity > 0 && getGatherTreePeons() != null) {
            int before = pool.size();
            result = addFromListCapped(getGatherTreePeons(), pool, result, total_score_needed, remaining_peon_capacity);
            peons_drafted += pool.size() - before;
            remaining_peon_capacity = Math.max(0, peon_cap - peons_drafted); //added by ikill240c 2026-09-09 15:05
        }
        if (result < total_score_needed && remaining_peon_capacity > 0 && getGatherRockPeons() != null) {
            int before = pool.size();
            result = addFromListCapped(getGatherRockPeons(), pool, result, total_score_needed, remaining_peon_capacity);
            peons_drafted += pool.size() - before;
            remaining_peon_capacity = Math.max(0, peon_cap - peons_drafted); //added by ikill240c 2026-09-09 15:05
        }
        if (result < total_score_needed && remaining_peon_capacity > 0 && getGatherIronPeons() != null) {
            int before = pool.size();
            result = addFromListCapped(getGatherIronPeons(), pool, result, total_score_needed, remaining_peon_capacity);
            peons_drafted += pool.size() - before;
            remaining_peon_capacity = Math.max(0, peon_cap - peons_drafted); //added by ikill240c 2026-09-09 15:05
        }
        if (result < total_score_needed && remaining_peon_capacity > 0 && getGatherRubberPeons() != null) {
            addFromListCapped(getGatherRubberPeons(), pool, result, total_score_needed, remaining_peon_capacity);
        }

        if (pool.isEmpty())
            return;

        int pool_index = 0;
        for (Threat t : threats) {
            if (pool_index >= pool.size())
                break;
            double proportion = (double) t.score / total_score_needed;
            int alloc = Math.max(1, (int) Math.round(proportion * pool.size()));
            alloc = Math.min(alloc, pool.size() - pool_index);
            List<Unit> group = pool.subList(pool_index, pool_index + alloc);
            // Final guard: a unit may have boarded a ship or entered a building between being
            // pooled and being ordered. //added by ikill240c 2026-09-10 00:35
            Unit[] units = group.stream().filter(AdvancedAI::canReceiveOrders)
                    .toArray(Unit[]::new); //added by ikill240c 2026-09-10 00:35
            if (units.length > 0) //added by ikill240c 2026-09-10 00:35
                getOwner().setLandscapeTarget(units, t.target.getGridX(), t.target.getGridY(), Action.DEFEND, true);
            pool_index += alloc;
        }
    }

    // Total peon population across ALL controllers (idle, gathering, defending, walking, building),
    // used to compute the defense-draft cap. Unlike countAllPeons() which only counts idle+gatherer
    // peons, this counts every alive HARVEST-ability unit the player owns, so the cap is an absolute
    // limit on how many peons can be in defense mode at any time. //added by ikill240c 2026-09-09 15:05
    private int countTotalPeons() { //added by ikill240c 2026-09-09 15:05
        int count = 0; //added by ikill240c 2026-09-09 15:05
        for (Selectable<?> s : getOwner().getUnits().getSet()) { //added by ikill240c 2026-09-09 15:05
            // Count every alive unit that has the HARVEST ability (i.e. is a peon), regardless of
            // what it's currently doing - defending, gathering, walking, building, or idle.
            // //added by ikill240c 2026-09-09 15:05
            if (s instanceof Unit unit && !unit.isDead() //added by ikill240c 2026-09-09 15:05
                    && unit.getAbilities().hasAbilities(Abilities.HARVEST)) { //added by ikill240c 2026-09-09 15:05
                count++; //added by ikill240c 2026-09-09 15:05
            } //added by ikill240c 2026-09-09 15:05
        } //added by ikill240c 2026-09-09 15:05
        return count; //added by ikill240c 2026-09-09 15:05
    }

    // Count peons currently in DefendController, so we can track how many are already drafted into
    // defense and prevent over-drafting beyond the cap. //added by ikill240c 2026-09-09 15:05
    private int countDefendingPeons() { //added by ikill240c 2026-09-09 15:05
        int count = 0; //added by ikill240c 2026-09-09 15:05
        if (getDefendingUnits() != null) { //added by ikill240c 2026-09-09 15:05
            for (Selectable<?> s : getDefendingUnits()) { //added by ikill240c 2026-09-09 15:05
                // Only count peons (HARVEST ability), not warriors that happen to be defending.
                // //added by ikill240c 2026-09-09 15:05
                if (s instanceof Unit unit && !unit.isDead() //added by ikill240c 2026-09-09 15:05
                        && unit.getAbilities().hasAbilities(Abilities.HARVEST)) { //added by ikill240c 2026-09-09 15:05
                    count++; //added by ikill240c 2026-09-09 15:05
                } //added by ikill240c 2026-09-09 15:05
            } //added by ikill240c 2026-09-09 15:05
        } //added by ikill240c 2026-09-09 15:05
        return count; //added by ikill240c 2026-09-09 15:05
    }

    // Current total defense score from all units already in DefendController, so ally-defense
    // calls can account for existing defenders instead of always passing 0. //added by ikill240c 2026-09-09 15:05
    private int currentDefenseScore() { //added by ikill240c 2026-09-09 15:05
        int score = 0; //added by ikill240c 2026-09-09 15:05
        if (getDefendingUnits() != null) { //added by ikill240c 2026-09-09 15:05
            for (Selectable<?> s : getDefendingUnits()) { //added by ikill240c 2026-09-09 15:05
                if (s instanceof Unit unit && !unit.isDead()) { //added by ikill240c 2026-09-09 15:05
                    score += getUnitScore(unit); //added by ikill240c 2026-09-09 15:05
                } //added by ikill240c 2026-09-09 15:05
            } //added by ikill240c 2026-09-09 15:05
        } //added by ikill240c 2026-09-09 15:05
        return score; //added by ikill240c 2026-09-09 15:05
    }

    // Same as addFromList, but never adds more than max_additional units from the list, regardless of
    // whether total_score_needed is still unmet - this is what actually enforces the peon-defense cap.
    // //added by ikill240c 2026-09-10 12:00
    private int addFromListCapped(Selectable<?> @NonNull [] list, @NonNull List<Unit> new_list, int progress,
            int score, int max_additional) {
        int result = progress;
        int added = 0;
        for (Selectable<?> list1 : list) {
            if (added >= max_additional)
                break;
            Unit unit = (Unit) list1;
            if (!canReceiveOrders(unit)) //added by ikill240c 2026-09-10 00:35
                continue; //added by ikill240c 2026-09-10 00:35
            new_list.add(unit);
            result += getUnitScore(unit);
            added++;
            if (result > score)
                break;
        }
        return result;
    }

    private void nodeDefendAllies() {//added by ikill240c
        int my_team = getOwner().getPlayerInfo().getTeam();
        if (my_team == PlayerInfo.TEAM_NEUTRAL)
            return;

        for (Player ally : getOwner().getWorld().getPlayers()) {
            if (ally == getOwner() || ally.getPlayerInfo().getTeam() != my_team)
                continue;

            for (Selectable<?> s : ally.getUnits().getSet()) {
                if (!(s instanceof Building building) || building.isDead())
                    continue;
                if (!building.getAbilities().hasAbilities(Abilities.REPRODUCE)
                        && !building.getAbilities().hasAbilities(Abilities.BUILD_ARMIES))
                    continue;

                int score = scanForEnemies(building);
                if (score > 0 && defense_target != null) {
                    List<Threat> threats = new ArrayList<>();
                    threats.add(new Threat(defense_target, (int) (DEFENSE_FACTOR[difficulty] * score)));
                    // Pass the current defense score so nodeDefendMultiple accounts for units already
                    // defending, preventing repeated over-drafting of peons for ally defense.
                    // //added by ikill240c 2026-09-09 15:05
                    nodeDefendMultiple(threats, currentDefenseScore());
                    return;
                }
            }
        }
    }

    private void nodeHealChieftain() {//added by ikill240c
        Selectable<?>[] idle_chieftains = getIdleChieftains();
        if (idle_chieftains == null)
            return;
        for (Selectable<?> s : idle_chieftains) {
            Unit chieftain = (Unit) s;
            if (chieftain.getTimeSinceDamage() >= chieftainHealIdleSeconds()
                    && chieftain.getHitPoints() < chieftain.getTemplate().getMaxHitPoints()) {
                chieftain.heal(chieftainHealAmount());
            }
        }
    }

    private float computeArmyStrength(@NonNull Player player) {//added by ikill240c
        float total = 0f;
        for (Selectable<?> s : player.getUnits().getSet()) {
            if (s instanceof Unit unit && !unit.isDead() && unit.getAbilities().hasAbilities(Abilities.ATTACK)) {
                total += getUnitScore(unit);
            }
        }
        return total;
    }

    // computeTotalEnemyStrength() removed - it summed every enemy on the map, which made the attack
    // gate unsatisfiable in multi-enemy games. See computeStrongestEnemyStrength().
    // //added by ikill240c 2026-09-10 15:10

    // Strength of the single strongest enemy, which is what an attack actually has to beat - unlike
    // the sum of every enemy on the map. //added by ikill240c 2026-09-10 15:10
    private float computeStrongestEnemyStrength() { //added by ikill240c 2026-09-10 15:10
        float strongest = 0f; //added by ikill240c 2026-09-10 15:10
        for (Player p : getOwner().getWorld().getPlayers()) { //added by ikill240c 2026-09-10 15:10
            if (getOwner().isEnemy(p)) //added by ikill240c 2026-09-10 15:10
                strongest = Math.max(strongest, computeArmyStrength(p)); //added by ikill240c 2026-09-10 15:10
        } //added by ikill240c 2026-09-10 15:10
        return strongest; //added by ikill240c 2026-09-10 15:10
    } //added by ikill240c 2026-09-10 15:10

    private int countWarriors() { //added by ikill240c 2026-09-10 15:10
        int count = 0; //added by ikill240c 2026-09-10 15:10
        for (Selectable<?> s : getOwner().getUnits().getSet()) { //added by ikill240c 2026-09-10 15:10
            if (s instanceof Unit unit && !unit.isDead() && unit.isWarrior()) //added by ikill240c 2026-09-10 15:10
                count++; //added by ikill240c 2026-09-10 15:10
        } //added by ikill240c 2026-09-10 15:10
        return count; //added by ikill240c 2026-09-10 15:10
    } //added by ikill240c 2026-09-10 15:10

    private void nodeDeployArmy() {
        if (getArmory() == null)
            return;

        for (Selectable<?> a : getArmory()) {
            Building armory = (Building) a;
            if (armory.isDead())
                continue;

            int num_units = armory.getUnitContainer().getNumSupplies();
            int num_weapons = numWeapons(armory) - MIN_WEAPONS_IN_STOCK[difficulty];
            if (num_units <= 0)
                continue;

            int num_warriors = 0;
            if (num_weapons > 0) {
                num_warriors = Math.min(num_units, num_weapons);
            }
            int num_rubber_units = Math.min(num_warriors, armory.getSupplyContainer(
                    RubberAxeWeapon.class).getNumSupplies());
            int num_iron_units = Math.min(num_warriors - num_rubber_units, armory.getSupplyContainer(
                    IronAxeWeapon.class).getNumSupplies());
            int num_rock_units = Math.min(num_warriors - num_rubber_units - num_iron_units, armory.getSupplyContainer(
                    RockAxeWeapon.class).getNumSupplies());
            if (num_rubber_units > 0) {
                getOwner().deployUnits(armory, DeployType.RUBBER_WARRIOR, num_rubber_units);
            }
            if (num_iron_units > 0) {
                getOwner().deployUnits(armory, DeployType.IRON_WARRIOR, num_iron_units);
            }
            if (num_rock_units > 0) {
                getOwner().deployUnits(armory, DeployType.ROCK_WARRIOR, num_rock_units);
            }
            int remaining_units = num_units - num_warriors;
            if (remaining_units > 0) {
                // Let stored peons in each armory convert into gatherers immediately after any warrior
                // deployment, instead of waiting on weapons stock. This prevents the AI from stalling
                // with peons trapped in the armory at game start while the economy never gets moving.
                // ikill240c 2026-09-10 13:45
                nodeGather(armory, remaining_units);
            }
        }
    }

    private void nodeChiefHitAndRun() {//added by ikill240c
        if (!getOwner().hasActiveChieftain() || chief_hitrun_active)
            return;
        Unit chief = getOwner().getChieftain();
        if (chief.isDead() || chief.isMounted())
            return;
        Selectable<?>[] idle_chiefs = getIdleChieftains();
        boolean chief_idle = idle_chiefs != null
                && java.util.Arrays.asList(idle_chiefs).contains(chief);
        if (!chief_idle)
            return;
        // Guarantee a raid whenever the chief is fully healed and idle at base, instead of just standing
        // around - the old check only allowed an *opportunistic* raid above 70% health, so a fully-healed
        // chief with no other trigger could sit idle indefinitely. Full health (>=100%) now always
        // qualifies; the >70% threshold remains as the opportunistic lower bound. //added by ikill240c 2026-09-08 17:15
        boolean fully_healed = chief.getHitPoints() >= chief.getTemplate().getMaxHitPoints();
        boolean opportunistic = chief.getHitPoints() > CHIEF_HITRUN_HEALTH_PCT * chief.getTemplate().getMaxHitPoints();
        if (!fully_healed && !opportunistic)
            return;

        int nearby_own = 0;
        for (Selectable<?> s : getOwner().getUnits().getSet()) {
            if (s instanceof Unit u && !u.isDead() && u != chief) {
                int dx = u.getGridX() - chief.getGridX();
                int dy = u.getGridY() - chief.getGridY();
                if (dx * dx + dy * dy < CHIEF_HITRUN_SEARCH_RADIUS * CHIEF_HITRUN_SEARCH_RADIUS)
                    nearby_own++;
            }
        }
        if (nearby_own >= CHIEF_HITRUN_MAX_ESCORT)//added by ikill240c
            return;

        Unit best_target = null;
        float best_score = 0f;
        for (Player p : getOwner().getWorld().getPlayers()) {
            if (!getOwner().isEnemy(p)) continue;
            for (Selectable<?> s : p.getUnits().getSet()) {
                if (!(s instanceof Unit candidate) || candidate.isDead()) continue;
                float score = 0f;
                for (Selectable<?> s2 : p.getUnits().getSet()) {
                    if (s2 instanceof Unit other && !other.isDead()) {
                        int dx = other.getGridX() - candidate.getGridX();
                        int dy = other.getGridY() - candidate.getGridY();
                        if (dx * dx + dy * dy < 400) // ~20 grid units clustering radius
                            score += getUnitScore(other);
                    }
                }
                if (score > best_score) {
                    best_score = score;
                    best_target = candidate;
                }
            }
        }
        if (best_target == null)//added by ikill240c
            return;

        // Bring a random-sized escort of currently idle warriors along instead of sending the chief
        // completely alone - 0 escorts is still possible (e.g. no warriors built yet), but whenever
        // warriors are available a random subset joins the raid. //added by ikill240c 2026-09-08 17:15
        Selectable<?>[] idle_warriors = getIdleWarriors();
        List<Selectable<?>> raid_party = new ArrayList<>();
        raid_party.add(chief);
        if (idle_warriors != null && idle_warriors.length > 0) {
            int escort_count = getOwner().getWorld().getRandom().nextInt(idle_warriors.length + 1);
            for (int i = 0; i < escort_count; i++) {
                raid_party.add(idle_warriors[i]);
            }
        }
        Selectable<?>[] raid_array = raid_party.toArray(Selectable.newArray(raid_party.size()));

        chief_hitrun_active = true;
        getOwner().setLandscapeTarget(raid_array, best_target.getGridX(), best_target.getGridY(),
                Action.ATTACK, true);
        // Cast the first available COMBAT magic (stun/blast for Vikings, lightning/poison for
        // Natives) - i.e. indices 0 and 1 only. Convert (index 2) is intentionally skipped here
        // because it has a long 90-second cooldown and must be aimed at nearby enemy units to be
        // useful; casting it at the chieftain's current position (which is often far from the
        // target) wastes the cooldown and prevents ChieftainAI.nodeConvert() from ever firing it
        // when enemies are actually nearby. nodeConvert handles convert casting separately.
        // //added by ikill240c 2026-09-09 15:05
        for (int i = 0; i < RacesResources.INDEX_MAGIC_CONVERT; i++) {
            if (chief.canDoMagic(i)) {
                chief.doMagic(i, false);
                break;
            }
        }
    }

    private void nodeChiefHitAndRunReturn() {//added by ikill240c
        if (!chief_hitrun_active || !getOwner().hasActiveChieftain())
            return;
        Unit chief = getOwner().getChieftain();
        if (chief.isDead()) {
            chief_hitrun_active = false;
            return;
        }
        if (chief.getHitPoints() <= 0.4f * chief.getTemplate().getMaxHitPoints()
                || (getIdleChieftains() != null && java.util.Arrays.asList(getIdleChieftains()).contains(chief))) {
            Building home = homeBuilding();
            if (home != null) {
                // Do not send the chieftain back to the same tile again; that causes a zero-length
                // movement request during hit-and-run return. //added by ikill240c 2026-09-09 20:00
                if (chief.getGridX() != home.getGridX() || chief.getGridY() != home.getGridY()) {
                    //added by ikill240c 2026-09-09 17:30 - Return the chieftain to home coordinates so hit-and-run units do not get trapped in the armory/quarters.
                    getOwner().setLandscapeTarget(Selectable.newArray(chief), home.getGridX(),
                            home.getGridY(), Action.MOVE, false);
                }
            }
            chief_hitrun_active = false;
        }
    }
    // (old single-target nodeDefend(int score) removed - fully superseded by nodeDefendMultiple above,
    // which handles multiple simultaneous threats with tiered unit recruitment and a peon-draft cap.
    // //added by ikill240c 2026-09-10 12:00)

    // A unit that is dead, or garrisoned in a building / aboard a ship, cannot be given orders -
    // Unit.setTarget asserts on both cases. //added by ikill240c 2026-09-10 00:35
    private static boolean canReceiveOrders(@NonNull Unit unit) { //added by ikill240c 2026-09-10 00:35
        return !unit.isDead() && !unit.isMounted(); //added by ikill240c 2026-09-10 00:35
    } //added by ikill240c 2026-09-10 00:35

    private int addFromList(Selectable<?> @NonNull [] list, @NonNull List<Unit> new_list, int progress, int score) {
        int result = progress;
        for (Selectable<?> list1 : list) {
            Unit unit = (Unit) list1;
            if (!canReceiveOrders(unit)) //added by ikill240c 2026-09-10 00:35
                continue; //added by ikill240c 2026-09-10 00:35
            new_list.add(unit);
            result += getUnitScore(unit);
            if (result > score)
                break;
        }
        return result;
    }

    private int scanForEnemies(@NonNull Selectable<?> src) {//added by ikill240c
        FindOccupantFilter<Unit> filter = new FindOccupantFilter<>(src.getPositionX(), src.getPositionY(),
                DEFENSE_SCAN_RADIUS, src, Unit.class);
        getUnitGrid().scan(filter, src.getGridX(), src.getGridY());
        int score = 0;
        defense_target = null;
        for (Unit unit : filter.getResult()) {
            if (!unit.isDead() && getOwner().isEnemy(unit.getOwner())) {
                score += getUnitScore(unit);
                if (defense_target == null)
                    defense_target = new LandscapeTarget(unit.getGridX(), unit.getGridY());
            }
        }
        return score;
    }

    private int scanForEnemiesAt(int grid_x, int grid_y) {//added by ikill240c
        float px = com.oddlabs.tt.pathfinder.UnitGrid.coordinateFromGrid(grid_x);
        float py = com.oddlabs.tt.pathfinder.UnitGrid.coordinateFromGrid(grid_y);
        FindOccupantFilter<Unit> filter = new FindOccupantFilter<>(px, py, DEFENSE_SCAN_RADIUS, null, Unit.class);
        getUnitGrid().scan(filter, grid_x, grid_y);
        int score = 0;
        defense_target = null;
        for (Unit unit : filter.getResult()) {
            if (!unit.isDead() && getOwner().isEnemy(unit.getOwner())) {
                score += getUnitScore(unit);
                if (defense_target == null)
                    defense_target = new LandscapeTarget(unit.getGridX(), unit.getGridY());
            }
        }
        return score;
    }

    private @Nullable Unit findBestIdleWarrior(@NonNull Selectable<?>[] idle_warriors, boolean[] used) {//added by ikill240c
        int best_index = -1;
        int best_score = -1;
        for (int i = 0; i < idle_warriors.length; i++) {
            if (used[i])
                continue;
            Unit unit = (Unit) idle_warriors[i];
            int score = getUnitScore(unit);
            if (score > best_score) {
                best_score = score;
                best_index = i;
            }
        }

        if (best_index == -1)
            return null;
        used[best_index] = true;
        return (Unit) idle_warriors[best_index];
    }

    private int getUnitScore(@NonNull Unit unit) {
        if (unit.getAbilities().hasAbilities(Abilities.HARVEST)) {
            return SCORE_PEON;
        } else if (unit.getAbilities().hasAbilities(Abilities.MAGIC)) {
            return SCORE_CHIEFTAIN;
        } else if (unit.getWeaponFactory().getType() == RockAxeWeapon.class
                || unit.getWeaponFactory().getType() == RockSpearWeapon.class) {
                    return SCORE_WARRIOR_ROCK;
                } else if (unit.getWeaponFactory().getType() == IronAxeWeapon.class
                        || unit.getWeaponFactory().getType() == IronSpearWeapon.class) {
                            return SCORE_WARRIOR_IRON;
                        } else if (unit.getWeaponFactory().getType() == RubberAxeWeapon.class
                                || unit.getWeaponFactory().getType() == RubberSpearWeapon.class) {
                                    return SCORE_WARRIOR_RUBBER;
                                }
        throw new RuntimeException();
    }

    private void nodeGuardTowers(int num_towers) { //added by ikill240c
        if ((getTowers() == null && num_towers > 0) || (getTowers() != null && num_towers > getTowers().length)) {
            nodeBuildTower(num_towers);
        }
        if (num_towers > 0 && getTowers() != null) {
            Selectable<?>[] idle_warriors = getIdleWarriors();
            boolean[] used = idle_warriors != null ? new boolean[idle_warriors.length] : null;
            for (int i = 0; i < getTowers().length; i++) {
                Building tower = (Building) getTowers()[i];
                if (!tower.getUnitContainer().isSupplyFull() && idle_warriors != null) {
                    Unit best = findBestIdleWarrior(idle_warriors, used);
                    if (best != null) {
                        getOwner().setTarget(Selectable.newArray(best), tower, Action.DEFAULT, false);
                        nodeDeployUnitsInArmory(1);
                    }
                }
            }
        }
    }

    // merged two previously-conflicting versions of this method into one coherent flow:
    // (1) pick an origin - either an occupied expansion zone (every 3rd tower) or home base,
    // (2) aim the placement angle toward the current focus enemy (with randomness), instead of a fixed ring.
    // //added by ikill240c 2026-09-08 03:15
    private void nodeBuildTower(int number) {//added by ikill240c
        int in_progress = countTowersUnderConstruction();
        int existing = getTowers() != null ? getTowers().length : 0;

        if (in_progress >= maxConcurrentTowers()
                || existing + in_progress >= number
                || getQuarters() == null || getArmory() == null)
            return; // not allowed/needed to build another tower right now //added by ikill240c 2026-09-08 03:15

        // Towers need the normal construction builder pool so they can pull from gatherers when idle
        // peons run low, instead of waiting forever for a perfect idle-peon count.
        // //added by ikill240c 2026-09-10 13:15
        Selectable<?>[] builders = getPeons(10);
        if (builders.length < 10)
            return;

        // pick the placement origin: rotate through occupied expansion zones for every 3rd tower, home otherwise //added by ikill240c 2026-09-08 03:15
        int ox, oy;
        if (!occupied_zones.isEmpty() && (existing + in_progress) % 3 == 2) {
            int[] zone = occupied_zones.get((existing + in_progress) % occupied_zones.size());
            ox = zone[0];
            oy = zone[1];
        } else {
            Building origin = homeBuilding();
            if (origin == null)
                return;
            ox = origin.getGridX();
            oy = origin.getGridY();
        }

        // aim the tower toward the current focus enemy (falls back to map center if no focus enemy found) //added by ikill240c 2026-09-08 03:15
        double base_angle;
        Player focus = chooseFocusEnemy();
        if (focus != null) {
            int ex = com.oddlabs.tt.pathfinder.UnitGrid.toGridCoordinate(focus.getStartX());
            int ey = com.oddlabs.tt.pathfinder.UnitGrid.toGridCoordinate(focus.getStartY());
            base_angle = Math.atan2(ey - oy, ex - ox);
        } else {
            int center = getOwner().getWorld().getHeightMap().getGridUnitsPerWorld() / 2;
            base_angle = Math.atan2(center - oy, center - ox);
        }

        // randomize the angle within +/-90 degrees of the enemy-facing direction for a less predictable layout //added by ikill240c 2026-09-08 03:15
        double spread = Math.PI / 2;
        double random_offset = (getOwner().getWorld().getRandom().nextFloat() - 0.5f) * spread;
        double angle = base_angle + random_offset;

        int slot = existing + in_progress;
        float radius = TOWER_BASE_RADIUS + ((float) slot / TOWER_SPACING_SLOTS) * TOWER_RADIUS_STEP + getOwner().getWorld().getRandom().nextFloat() * 6f; // small random jitter on distance too //added by ikill240c 2026-09-08 03:15

        int tx = (int) (ox + radius * Math.cos(angle));
        int ty = (int) (oy + radius * Math.sin(angle));

        buildBuilding(Race.BUILDING_TOWER, builders, tx, ty, TOWER_SCAN_RANGE);
    }

    private void nodeBuildResourceTowers() {//added by ikill240c
        if (resource_towers_built >= numResourceTowers())
            return;
        if (countTowersUnderConstruction() >= maxConcurrentTowers())
            return;
        if (getQuarters() == null || getArmory() == null)
            return;
        Building home = homeBuilding();
        if (home == null)
            return;

        SupplyLocationFilter filter = new SupplyLocationFilter(IronSupply.class, 250);
        getUnitGrid().scan(filter, home.getGridX(), home.getGridY());
        if (!filter.found())
            return;

        // Resource towers should also use the full construction builder pool so they can be placed
        // when gatherers are available, not only when the AI happens to have 10 idle peons sitting around.
        // //added by ikill240c 2026-09-10 13:15
        Selectable<?>[] builders = getPeons(10);
        if (builders.length < 10)
            return;

        if (buildBuilding(Race.BUILDING_TOWER, firstN(builders, 10), filter.getTarget().getGridX(),
                filter.getTarget().getGridY(), 20)) {
            resource_towers_built++;
            occupied_zones.add(new int[]{filter.getTarget().getGridX(), filter.getTarget().getGridY()});
        }
    }

    private boolean isTowerTemplate(@NonNull Building building) {//added by ikill240c
        BuildingTemplate tower_template = getOwner().getRace().getBuildingTemplate(Race.BUILDING_TOWER);
        return building.getTemplate().getTemplateID() == tower_template.getTemplateID();
    }

    private java.util.Set<Building> getTowersUnderConstruction() {
        java.util.Set<Building> towers = new java.util.HashSet<>();
        for (Selectable<?> s : getOwner().getUnits().getSet()) {
            if (s.isDead())
                continue;
            Controller c = s.getPrimaryController();
            Building building = null;
            if (c instanceof PlaceBuildingController pbc) {
                building = pbc.getBuilding();
            } else if (c instanceof RepairController rc) {
                building = rc.getBuilding();
            }
            if (building != null && !building.isDead() && !building.isComplete() && isTowerTemplate(building)) {
                towers.add(building);
            }
        }
        return towers;
    }

    private int countTowersUnderConstruction() { //added by ikill240c
        return getTowersUnderConstruction().size();
    }

    private boolean isQuartersTemplate(@NonNull Building building) {
        BuildingTemplate quarters_template = getOwner().getRace().getBuildingTemplate(Race.BUILDING_QUARTERS);
        return building.getTemplate().getTemplateID() == quarters_template.getTemplateID();
    }

    private java.util.Set<Building> getQuartersUnderConstruction() {
        java.util.Set<Building> quarters = new java.util.HashSet<>();
        for (Selectable<?> s : getOwner().getUnits().getSet()) {
            if (s.isDead())
                continue;
            Controller c = s.getPrimaryController();
            Building building = null;
            if (c instanceof PlaceBuildingController pbc) {
                building = pbc.getBuilding();
            } else if (c instanceof RepairController rc) {
                building = rc.getBuilding();
            }
            if (building != null && !building.isDead() && !building.isComplete() && isQuartersTemplate(building)) {
                quarters.add(building);
            }
        }
        return quarters;
    }

    private int countQuartersUnderConstruction() { //added by ikill240c
        return getQuartersUnderConstruction().size();
    }

    // Armory equivalents of the three Quarters helpers above, so Armory construction can be counted
    // and capped the same way (TARGET_NUM_ARMORIES / MAX_CONCURRENT_ARMORIES). //added by ikill240c 2026-09-08 16:30
    private boolean isArmoryTemplate(@NonNull Building building) {
        BuildingTemplate armory_template = getOwner().getRace().getBuildingTemplate(Race.BUILDING_ARMORY);
        return building.getTemplate().getTemplateID() == armory_template.getTemplateID();
    }

    private java.util.Set<Building> getArmoriesUnderConstruction() {
        java.util.Set<Building> armories = new java.util.HashSet<>();
        for (Selectable<?> s : getOwner().getUnits().getSet()) {
            if (s.isDead())
                continue;
            Controller c = s.getPrimaryController();
            Building building = null;
            if (c instanceof PlaceBuildingController pbc) {
                building = pbc.getBuilding();
            } else if (c instanceof RepairController rc) {
                building = rc.getBuilding();
            }
            if (building != null && !building.isDead() && !building.isComplete() && isArmoryTemplate(building)) {
                armories.add(building);
            }
        }
        return armories;
    }

    private int countArmoriesUnderConstruction() {
        return getArmoriesUnderConstruction().size();
    }

    private void nodeAssignIdlePeons() {
        if (getIdlePeons() == null)
            return;

        // getConstructionSites()[0] can be a site that was destroyed this tick. //added by ikill240c 2026-09-10 16:00
        Selectable<?> site = firstAliveConstructionSite(); //added by ikill240c 2026-09-10 16:00
        if (quartersUnderConstruction() && site != null) {
            getOwner().setTarget(getIdlePeons(), site, Action.DEFAULT, false);
        } else if (armoryUnderConstruction() && site != null) {
            getOwner().setTarget(getIdlePeons(), site, Action.DEFAULT, false);
        } else if (assignIdlePeonsToTowers()) {
            // handled inside the helper
        } else if (shipUnderConstruction() && site != null) {
            getOwner().setTarget(getIdlePeons(), site, Action.DEFAULT, false);
        }

        // Do not redirect every idle peon into Quarters. That permanently removes them from the
        // build/gather/defend decision loop and causes the AI to stall with units sitting in the
        // base while only one player keeps doing meaningful work.
    }

    // Puts idle peons back to work gathering. Without this the AI's economy bleeds out over a long
    // match: a peon only ever receives a GatherController when it is deployed out of a building, so
    // every peon that gets drafted into a defense group, finishes a construction site, or exhausts
    // its resource node falls back to IdleController and stays there forever. Eventually nobody is
    // gathering, no weapons are produced, no warriors are deployed, and the AI looks like it "gave
    // up". Runs after nodeAssignIdlePeons so builders are claimed for construction first.
    // //added by ikill240c 2026-09-10 15:10
    private void nodeAssignIdleGatherers() { //added by ikill240c 2026-09-10 15:10
        Selectable<?>[] idle = getIdlePeons(); //added by ikill240c 2026-09-10 15:10
        if (idle == null || idle.length == 0) //added by ikill240c 2026-09-10 15:10
            return; //added by ikill240c 2026-09-10 15:10

        Building drop_off = gatherDropOffBuilding(); //added by ikill240c 2026-09-10 15:10
        if (drop_off == null) //added by ikill240c 2026-09-10 15:10
            return; // nowhere to deliver resources yet - keep them free for construction //added by ikill240c 2026-09-10 15:10

        int tree = countOrZero(getGatherTreePeons()); //added by ikill240c 2026-09-10 15:10
        int rock = countOrZero(getGatherRockPeons()); //added by ikill240c 2026-09-10 15:10
        int iron = countOrZero(getGatherIronPeons()); //added by ikill240c 2026-09-10 15:10
        int rubber = countOrZero(getGatherRubberPeons()); //added by ikill240c 2026-09-10 15:10

        for (Selectable<?> s : idle) { //added by ikill240c 2026-09-10 15:10
            if (!(s instanceof Unit unit) || unit.isDead() || unit.isMounted()) //added by ikill240c 2026-09-10 15:10
                continue; //added by ikill240c 2026-09-10 15:10
            if (!unit.getAbilities().hasAbilities(Abilities.BUILD)) //added by ikill240c 2026-09-10 15:10
                continue; // peons only - warriors and chieftains are handled elsewhere //added by ikill240c 2026-09-10 15:10

            // Send the peon to whichever resource is furthest below its cap, so the four
            // stockpiles stay balanced the same way nodeGather balances fresh deployments.
            // //added by ikill240c 2026-09-10 15:10
            int tree_room = MAX_UNITS_GATHERING_TREE[difficulty] - tree; //added by ikill240c 2026-09-10 15:10
            int rock_room = MAX_UNITS_GATHERING_ROCK[difficulty] - rock; //added by ikill240c 2026-09-10 15:10
            int iron_room = MAX_UNITS_GATHERING_IRON[difficulty] - iron; //added by ikill240c 2026-09-10 15:10
            int rubber_room = MAX_UNITS_GATHERING_RUBBER[difficulty] - rubber; //added by ikill240c 2026-09-10 15:10
            int best_room = Math.max(Math.max(tree_room, rock_room), Math.max(iron_room, rubber_room)); //added by ikill240c 2026-09-10 15:10
            if (best_room <= 0) //added by ikill240c 2026-09-10 15:10
                return; // every resource is fully staffed - leave the rest idle as a defense reserve //added by ikill240c 2026-09-10 15:10

            Class<? extends Supply> supply_type; //added by ikill240c 2026-09-10 15:10
            if (tree_room == best_room) { //added by ikill240c 2026-09-10 15:10
                supply_type = TreeSupply.class; //added by ikill240c 2026-09-10 15:10
                tree++; //added by ikill240c 2026-09-10 15:10
            } else if (rock_room == best_room) { //added by ikill240c 2026-09-10 15:10
                supply_type = RockSupply.class; //added by ikill240c 2026-09-10 15:10
                rock++; //added by ikill240c 2026-09-10 15:10
            } else if (iron_room == best_room) { //added by ikill240c 2026-09-10 15:10
                supply_type = IronSupply.class; //added by ikill240c 2026-09-10 15:10
                iron++; //added by ikill240c 2026-09-10 15:10
            } else { //added by ikill240c 2026-09-10 15:10
                supply_type = RubberSupply.class; //added by ikill240c 2026-09-10 15:10
                rubber++; //added by ikill240c 2026-09-10 15:10
            } //added by ikill240c 2026-09-10 15:10
            unit.initGather(supply_type, drop_off); //added by ikill240c 2026-09-10 15:10
        } //added by ikill240c 2026-09-10 15:10
    } //added by ikill240c 2026-09-10 15:10

    private @Nullable Selectable<?> firstAliveConstructionSite() { //added by ikill240c 2026-09-10 16:00
        Selectable<?>[] sites = getConstructionSites(); //added by ikill240c 2026-09-10 16:00
        if (sites == null) //added by ikill240c 2026-09-10 16:00
            return null; //added by ikill240c 2026-09-10 16:00
        for (Selectable<?> site : sites) { //added by ikill240c 2026-09-10 16:00
            if (!site.isDead()) //added by ikill240c 2026-09-10 16:00
                return site; //added by ikill240c 2026-09-10 16:00
        } //added by ikill240c 2026-09-10 16:00
        return null; //added by ikill240c 2026-09-10 16:00
    } //added by ikill240c 2026-09-10 16:00

    private static int countOrZero(Selectable<?> @Nullable [] list) { //added by ikill240c 2026-09-10 15:10
        return list == null ? 0 : list.length; //added by ikill240c 2026-09-10 15:10
    } //added by ikill240c 2026-09-10 15:10

    // Preferred drop-off point for gathered resources: an armory (which turns them into weapons),
    // otherwise the quarters. //added by ikill240c 2026-09-10 15:10
    private @Nullable Building gatherDropOffBuilding() { //added by ikill240c 2026-09-10 15:10
        if (getArmory() != null) { //added by ikill240c 2026-09-10 15:10
            for (Selectable<?> a : getArmory()) { //added by ikill240c 2026-09-10 15:10
                if (a instanceof Building b && !b.isDead()) //added by ikill240c 2026-09-10 15:10
                    return b; //added by ikill240c 2026-09-10 15:10
            } //added by ikill240c 2026-09-10 15:10
        } //added by ikill240c 2026-09-10 15:10
        if (getQuarters() != null) { //added by ikill240c 2026-09-10 15:10
            for (Selectable<?> q : getQuarters()) { //added by ikill240c 2026-09-10 15:10
                if (q instanceof Building b && !b.isDead()) //added by ikill240c 2026-09-10 15:10
                    return b; //added by ikill240c 2026-09-10 15:10
            } //added by ikill240c 2026-09-10 15:10
        } //added by ikill240c 2026-09-10 15:10
        return null; //added by ikill240c 2026-09-10 15:10
    } //added by ikill240c 2026-09-10 15:10

    private boolean assignIdlePeonsToTowers() {//added by ikill240c
        java.util.Set<Building> towers = getTowersUnderConstruction();
        if (towers.isEmpty())
            return false;

        Selectable<?>[] idle = getIdlePeons();
        if (idle == null || idle.length == 0)
            return false;

        int next = 0;
        for (Building tower : towers) {
            if (next >= idle.length)
                break;
            if (tower.isDead()) //added by ikill240c 2026-09-10 16:00
                continue; // destroyed while builders were on the way //added by ikill240c 2026-09-10 16:00
            int current_builders = countBuilders(tower);
            int needed = BUILDERS_PER_TOWER_TARGET - current_builders;
            if (needed <= 0)
                continue;
            int take = Math.min(needed, idle.length - next);
            Selectable<?>[] batch = new Selectable<?>[take];
            System.arraycopy(idle, next, batch, 0, take);
            getOwner().setTarget(batch, tower, Action.DEFAULT, false);
            next += take;
        }
        return true; //added by ikill240c
    }

    private void nodeAttackWithWarriorsAndChieftain(int num_warriors, boolean use_chieftain) {
        /*
        System.out.print("nodeAttackWithWarriorsAndChieftain");
        if (getIdleWarriors() == null)
        	System.out.println(" | no idling warriors");
        else
        	System.out.println(" | " + getIdleWarriors().length + " idling warriors");
        */
        if (getIdleWarriors() != null && getIdleWarriors().length >= num_warriors
                && (!use_chieftain || getOwner().hasActiveChieftain())) {
            boolean idle_chieftain = getIdleChieftains() != null && getIdleChieftains().length >= 1;
            Selectable<?>[] warriors;
            if (idle_chieftain && use_chieftain) {
                warriors = Selectable.newArray(num_warriors + 1);
                warriors[num_warriors] = getIdleChieftains()[0];
            } else {
                warriors = Selectable.newArray(num_warriors);
            }

            System.arraycopy(getIdleWarriors(), 0, warriors, 0, num_warriors);
            Target target = findTarget(warriors[0].getGridX(), warriors[0].getGridY());
            if (target != null) {
                getOwner().setLandscapeTarget(warriors, target.getGridX(), target.getGridY(), Action.ATTACK, true);
                if (NUM_WARRIORS[difficulty] < NUM_WARRIORS_MAX[difficulty])
                    NUM_WARRIORS[difficulty] += NUM_WARRIORS_INCREASE[difficulty];
                getOwner().setLandscapeTarget(warriors, target.getGridX(), target.getGridY(), Action.ATTACK, true);//added by ikill240c
                for (Selectable<?> w : warriors) {
                    if (w instanceof Unit u) attacking_units.add(u);
                }
            }
        } else {
            if (getIdleWarriors() != null) {
                nodeDeployUnitsInArmory(num_warriors - getIdleWarriors().length);
            } else {
                nodeDeployUnitsInArmory(num_warriors);
            }
            if (use_chieftain)
                nodeTrainChieftain();
        }
    }

    private void nodeTrainChieftain() {
        if (!getOwner().hasActiveChieftain() && !getOwner().isTrainingChieftain()) {
            if (getQuarters() != null) {
                getOwner().trainChieftain((Building) getQuarters()[0], true);
            }
        }
    }

    private void nodeDeployUnitsInArmory(int num_warriors) {
        if (getArmory() == null || getArmory().length == 0) {
            nodeBuildArmory();
            return;
        }

        int remaining = num_warriors;
        Building shortage_armory = null;
        for (Selectable<?> a : getArmory()) {
            Building armory = (Building) a;
            if (armory.isDead())
                continue;
            if (shortage_armory == null)
                shortage_armory = armory;

            int num_units = armory.getUnitContainer().getNumSupplies() - MIN_UNITS_BUILDING_WEAPONS[difficulty];
            int num_weapons = numWeapons(armory) - MIN_WEAPONS_IN_STOCK[difficulty];
            if (remaining <= 0 || num_units <= 0 || num_weapons <= 0)
                continue;

            int deployable = Math.min(remaining, Math.min(num_units, num_weapons));
            if (deployable <= 0)
                continue;

            int num_rubber_units = Math.min(deployable, armory.getSupplyContainer(
                    RubberAxeWeapon.class).getNumSupplies());
            int num_iron_units = Math.min(deployable - num_rubber_units, armory.getSupplyContainer(
                    IronAxeWeapon.class).getNumSupplies());
            int num_rock_units = Math.min(deployable - num_rubber_units - num_iron_units,
                    armory.getSupplyContainer(RockAxeWeapon.class).getNumSupplies());
            if (num_rubber_units > 0)
                getOwner().deployUnits(armory, DeployType.RUBBER_WARRIOR, num_rubber_units);
            if (num_iron_units > 0)
                getOwner().deployUnits(armory, DeployType.IRON_WARRIOR, num_iron_units);
            if (num_rock_units > 0)
                getOwner().deployUnits(armory, DeployType.ROCK_WARRIOR, num_rock_units);

            remaining -= deployable;
        }

        if (remaining <= 0)
            return;

        if (shortage_armory != null) {
            int num_units = shortage_armory.getUnitContainer().getNumSupplies() - MIN_UNITS_BUILDING_WEAPONS[difficulty];
            int num_weapons = numWeapons(shortage_armory) - MIN_WEAPONS_IN_STOCK[difficulty];
            if (num_units < remaining) {
                nodeTransferUnits(remaining - num_units, shortage_armory);
            }
            if (num_weapons < remaining) {
                nodeGather(shortage_armory, num_units);
            }
        } else {
            nodeBuildArmory();
        }
    }

    private void nodeGather(@NonNull Building armory, int num_units) {
        int tree = 0;
        int rock = 0;
        int iron = 0;
        int rubber = 0;

        if (getGatherTreePeons() != null)
            tree = getGatherTreePeons().length;
        if (getGatherRockPeons() != null)
            rock = getGatherRockPeons().length;
        if (getGatherIronPeons() != null)
            iron = getGatherIronPeons().length;
        if (getGatherRubberPeons() != null)
            rubber = getGatherRubberPeons().length;

        if (tree >= MAX_UNITS_GATHERING_TREE[difficulty])
            tree = Integer.MAX_VALUE;
        if (rock >= MAX_UNITS_GATHERING_ROCK[difficulty])
            rock = Integer.MAX_VALUE;
        if (iron >= MAX_UNITS_GATHERING_IRON[difficulty])
            iron = Integer.MAX_VALUE;
        if (rubber >= MAX_UNITS_GATHERING_RUBBER[difficulty])
            rubber = Integer.MAX_VALUE;

        boolean deployed;
        do {
            deployed = false;
            if (num_units > 0 && tree < MAX_UNITS_GATHERING_TREE[difficulty] && tree <= rock && tree <= iron
                    && tree <= rubber) {
                getOwner().deployUnits(armory, DeployType.PEON_HARVEST_TREE, 1);
                deployed = true;
                tree++;
            } else if (num_units > 0 && rock < MAX_UNITS_GATHERING_ROCK[difficulty] && rock <= tree && rock <= iron
                    && rock <= rubber) {
                        getOwner().deployUnits(armory, DeployType.PEON_HARVEST_ROCK, 1);
                        deployed = true;
                        rock++;
                    } else if (num_units > 0 && iron < MAX_UNITS_GATHERING_IRON[difficulty] && iron <= tree
                            && iron <= rock && iron <= rubber) {
                                getOwner().deployUnits(armory, DeployType.PEON_HARVEST_IRON, 1);
                                deployed = true;
                                iron++;
                            } else if (num_units > 0 && rubber < MAX_UNITS_GATHERING_RUBBER[difficulty]
                                    && rubber <= tree && rubber <= rock && rubber <= iron) {
                                        getOwner().deployUnits(armory, DeployType.PEON_HARVEST_RUBBER, 1);
                                        deployed = true;
                                        rubber++;
                                    }
            num_units--;
        } while (deployed);
    }

    private void nodeTransferUnits(int num_units, @NonNull Building armory) { //added by ikill240c
        if (getQuarters() == null || getQuarters().length == 0) {
            nodeBuildQuarters();
            return;
        }

        int remaining = num_units;
        for (Selectable<?> q : getQuarters()) {
            if (remaining <= 0)
                break;
            Building quarters = (Building) q;
            if (quarters.isDead())
                continue;
            quarters.setRallyPoint(armory);
            int surplus = quarters.getUnitContainer().getNumSupplies() - MIN_UNITS_REPRODUCING[difficulty];
            if (surplus > 0) {
                int units = Math.min(remaining, surplus);
                getOwner().deployUnits(quarters, DeployType.PEON, units);
                remaining -= units;
            }
        }
    }

    private void nodeBuildArmory() { //added by ikill240c
        if (!quartersUnderConstruction() && getQuarters() == null) {
            nodeBuildQuarters();
        }

        Building quarters = null;
        boolean any_reproduce = false;
        if (getQuarters() != null) {
            for (Selectable<?> q : getQuarters()) {
                if (!q.isDead()) {
                    if (quarters == null)
                        quarters = (Building) q;
                    if (((Building) q).getAbilities().hasAbilities(Abilities.REPRODUCE))
                        any_reproduce = true;
                }
            }
        }

        // Generalized from "build exactly one Armory ever" to a target/cap pair, matching how
        // Quarters and Towers already work - customizable via TARGET_NUM_ARMORIES /
        // MAX_CONCURRENT_ARMORIES above. //added by ikill240c 2026-09-08 16:30
        int existing_armories = getArmory() != null ? getArmory().length : 0;
        int armories_in_progress = countArmoriesUnderConstruction();
        boolean should_build_more = armories_in_progress < maxConcurrentArmories()
                && existing_armories + armories_in_progress < targetNumArmories();

        if (should_build_more && any_reproduce) { //added by ikill240c
            Selectable<?>[] builders = getPeons(20);
            if (builders.length < 20 && getQuarters() != null) {
                for (Selectable<?> q : getQuarters()) {
                    Building b = (Building) q;
                    if (!b.isDead() && b.getUnitContainer().getNumSupplies() >= 20) {
                        getOwner().deployUnits(b, DeployType.PEON, 20);
                        break;
                    }
                }
            }
            if (builders.length == 0)
                return;

            // spread additional Armories out around the home base the same way extra Quarters are spread,
            // instead of always placing at the last builder's position //added by ikill240c 2026-09-08 16:30
            Building origin = homeBuilding();
            int ox = origin != null ? origin.getGridX() : builders[0].getGridX();
            int oy = origin != null ? origin.getGridY() : builders[0].getGridY();
            int center = getOwner().getWorld().getHeightMap().getGridUnitsPerWorld() / 2;
            int dx = center - ox;
            int dy = center - oy;
            double angle_offset = existing_armories * (Math.PI / 4);
            double angle = Math.atan2(dy, dx) + angle_offset;
            int tx = (int) (ox + 10f * Math.cos(angle));
            int ty = (int) (oy + 10f * Math.sin(angle));

            setArmoryUnderConstruction(buildBuilding(Race.BUILDING_ARMORY, builders, tx, ty));
            reclassify();
        }
    }

    private void nodeBuildQuarters() { //added by ikill240c
        int existing = getQuarters() != null ? getQuarters().length : 0;
        int in_progress = countQuartersUnderConstruction();

        // Build order fix: the first Quarters may always be built, but once it exists, hold off on
        // Quarters #2/#3 until the Armory is fully complete. This makes the intended order explicit:
        // Quarters(1) -> Armory -> remaining Quarters/outposts. Previously nothing enforced this, and a
        // separate bug (nodeBuildArmory() never being called from animate()) meant the Armory step was
        // skipped entirely, so peons piled into Quarters with nowhere else to go. //added by ikill240c 2026-09-08 16:30
        if (existing >= 1 && getArmory() == null) {
            return;
        }

        if (in_progress < maxConcurrentQuarters() && existing + in_progress < targetNumQuarters()) {
            Selectable<?>[] builders = getPeons(MIN_UNITS_REPRODUCING[difficulty]);
            if (builders.length == 0)
                return;

            Building origin = homeBuilding();
            int ox = origin != null ? origin.getGridX() : builders[0].getGridX();
            int oy = origin != null ? origin.getGridY() : builders[0].getGridY();
            int center = getOwner().getWorld().getHeightMap().getGridUnitsPerWorld() / 2;
            int dx = center - ox;
            int dy = center - oy;
            double angle_offset = (existing + in_progress) * (Math.PI / 4);
            double angle = Math.atan2(dy, dx) + angle_offset;
            int tx = (int) (ox + 10f * Math.cos(angle));
            int ty = (int) (oy + 10f * Math.sin(angle));

            buildBuilding(Race.BUILDING_QUARTERS, builders, tx, ty);
            reclassify();
        }
    }

    private boolean isArchipelago() {
        return getOwner().getWorld().getMapSize() == Game.SIZE_ARCHIPELAGO;
    }

    private boolean baseBuildingsDone() {
        return getQuarters() != null && getArmory() != null
                && !quartersUnderConstruction() && !armoryUnderConstruction() && !towerUnderConstruction();
    }

    private void nodeBuildShipAndLoad() {
        List<Ship> ships = getOwnShips();

        nodeBuildShip(ships.size());

        for (Ship ship : ships) {
            if (ship.isComplete() && !ship.isMoving() && shipAtHome(ship) && !shipFullyCrewed(ship)) {
                nodeLoadShip(ship);
                return;
            }
        }
    }

    private @NonNull List<Ship> getOwnShips() {
        List<Ship> ships = new ArrayList<>();
        for (Selectable<?> s : getOwner().getUnits().getSet()) {
            if (s instanceof Ship ship && !ship.isDead())
                ships.add(ship);
        }
        return ships;
    }

    private void nodeUseShip() {
        List<Ship> ships = getOwnShips();
        for (Ship ship : ships) {
            useShip(ship);
        }
    }

    private void useShip(@NonNull Ship ship) {
        if (ship.isDead() || !ship.isComplete() || ship.isMoving())
            return;

        boolean at_home = shipAtHome(ship);
        boolean should_escape = shipShouldEscape(ship);
        boolean battle_ready = (shipBattleReady(ship) && at_home) || !should_escape;

        if (battle_ready) {
            Selectable<?> sea_enemy = getOwner().findNearestEnemyShip(ship.getGridX(), ship.getGridY());
            if (sea_enemy != null && sea_enemy.isDead()) {
                sea_enemy = null;
            }
            Selectable<?> beach_enemy = getOwner().findNearestEnemyOnBeach(ship.getGridX(), ship.getGridY());
            if (beach_enemy != null && beach_enemy.isDead()) {
                beach_enemy = null;
            }
            Selectable<?> enemy = null;
            if (beach_enemy != null && sea_enemy != null) {
                int sea_dx = sea_enemy.getGridX() - ship.getGridX();
                int sea_dy = sea_enemy.getGridY() - ship.getGridY();
                int sea_d2 = sea_dx * sea_dx + sea_dy * sea_dy;
                int beach_dx = beach_enemy.getGridX() - ship.getGridX();
                int beach_dy = beach_enemy.getGridY() - ship.getGridY();
                int beach_d2 = beach_dx * beach_dx + beach_dy * beach_dy;
                if (sea_d2 > beach_d2) {
                    enemy = beach_enemy;
                } else {
                    enemy = sea_enemy;
                }
            } else if (beach_enemy != null) {
                enemy = beach_enemy;
            } else {
                enemy = sea_enemy;
            }

            if (enemy != null) {
                getOwner().setTarget(Selectable.newArray(ship), enemy, Action.MOVE, true);
                return;
            }
        }

        if (!at_home) {
            Building origin = homeBuilding();
            if (origin != null) {
                getOwner().setTarget(Selectable.newArray(ship), origin, Action.MOVE, false);
            }
        }
    }

    private boolean shipBattleReady(@NonNull Ship ship) {
        return shipFullyCrewed(ship) && !shipNeedsRepair(ship);
    }

    private boolean shipShouldEscape(@NonNull Ship ship) {
        return shipTooDamaged(ship) || !shipCrewedEnough(ship);
    }

    private boolean shipTooDamaged(@NonNull Ship ship) {
        return ship.getHitPoints() < SHIP_MIN_HEALTH * ship.getBuildingTemplate().getMaxHitPoints();
    }

    private boolean shipNeedsRepair(@NonNull Ship ship) {
        return ship.getHitPoints() < ship.getBuildingTemplate().getMaxHitPoints();
    }

    private boolean shipFullyCrewed(@NonNull Ship ship) {
        int peons = ship.getShipHR().countPeons();
        int warriors = ship.getShipHR().countUnits() - peons;
        return peons >= SHIP_PEONS && warriors >= SHIP_WARRIORS;
    }

    private boolean shipCrewedEnough(@NonNull Ship ship) {
        int peons = ship.getShipHR().countPeons();
        int warriors = ship.getShipHR().countUnits() - peons;
        return peons >= SHIP_PEONS / 2 && warriors >= SHIP_WARRIORS / 3;
    }

    private @Nullable Building homeBuilding() {//added by ikill240c
        if (getQuarters() != null) {
            for (Selectable<?> q : getQuarters()) {
                if (!q.isDead())
                    return (Building) q;
            }
        }
        if (getArmory() != null) {
            for (Selectable<?> a : getArmory()) {
                if (!a.isDead())
                    return (Building) a;
            }
        }
        return null;
    }

    private int homeIsland() {
        Building home = homeBuilding();
        return home != null ? home.getIslandId() : -1;
    }

    private boolean shipAtHome(@NonNull Ship ship) {
        Building entrance = ship.getEntrance();
        if (entrance == null) {
            return false;
        }
        return closeToAny(entrance, getQuarters()) || closeToAny(entrance, getArmory());
    }

    private boolean closeToAny(@NonNull Target target, @NonNull Selectable<?> @Nullable [] buildings) {
        if (buildings == null)
            return false;

        for (Selectable<?> building : buildings) {
            if (building.isDead())
                continue;
            int dx = building.getGridX() - target.getGridX();
            int dy = building.getGridY() - target.getGridY();
            if (dx * dx + dy * dy <= SHIP_HOME_RANGE * SHIP_HOME_RANGE)
                return true;
        }
        return false;
    }

    private boolean shipDockedAt(@NonNull Ship ship, int island) {
        Building entrance = ship.getEntrance();
        return entrance != ship && !entrance.isDead() && entrance.getIslandId() == island;
    }

    private void nodeBuildShip(int fleet_size) {
        Ship ship = getIncompleteShip();
        if (ship == null && fleet_size >= FLEET_SIZE)
            return;

        Selectable<?>[] idle = getIdlePeons();
        int idle_count = idle != null ? idle.length : 0;

        int missing = SHIP_BUILDERS - (ship != null ? countBuilders(ship.getEntrance()) : 0);

        if (missing > idle_count && getQuarters() != null) {
            Building quarters = (Building) getQuarters()[0];
            deployPeonsFromQuarters(quarters, missing - idle_count, quarters);
            Building armory = (Building) getArmory()[0];
            deployPeonsFromQuarters(armory, missing - idle_count, armory);
        }

        if (idle_count == 0 || missing <= 0)
            return;

        if (ship != null) {
            Action action = ship.isComplete() ? Action.GATHER_REPAIR : Action.DEFAULT;
            getOwner().setTarget(lastN(idle, missing), ship, action, false);
        } else {
            Building origin = homeBuilding();
            buildBuilding(Race.BUILDING_SHIP, firstN(idle, SHIP_BUILDERS), origin.getGridX(), origin.getGridY());
        }
    }

    private boolean shipIncomplete(@NonNull Ship ship) {
        return !ship.isComplete() || ship.isDamaged();
    }

    private @Nullable Ship getIncompleteShip() {
        for (Selectable<?> s : getOwner().getUnits().getSet()) {
            if (s instanceof Ship ship && !ship.isDead() && shipIncomplete(ship))
                return ship;
        }
        Selectable<?>[] placing = getPlaceBuildingPeons();
        if (placing != null) {
            for (Selectable<?> s : placing) {
                if (!s.isDead() && s.getPrimaryController() instanceof PlaceBuildingController controller
                        && controller.getBuilding() instanceof Ship ship && !ship.isDead())
                    return ship;
            }
        }
        return null;
    }

    private int countBuilders(@NonNull Building b) {
        int builders = 0;
        for (Selectable<?> s : getOwner().getUnits().getSet()) {
            if (s.isDead()) {
                continue;
            }
            Controller controller = s.getPrimaryController();
            if (controller instanceof RepairController repair && repair.getBuilding() == b) {
                builders++;
            } else if (controller instanceof PlaceBuildingController placing && placing.getBuilding() == b) {
                builders++;
            }
        }
        return builders;
    }

    private void nodeLoadShip(@NonNull Ship ship) {
        int peons_aboard = ship.getShipHR().countPeons();
        int warriors_aboard = ship.getShipHR().countUnits() - peons_aboard;

        int peons_needed = SHIP_PEONS - peons_aboard;
        if (peons_needed > 0) {
            if (getIdlePeons() != null && getIdlePeons().length > 0) {
                getOwner().setTarget(firstN(getIdlePeons(), peons_needed), ship, Action.DEFAULT, false);
            } else {
                nodeDeployPeonsFromQuarters(ship, peons_needed);
            }
        }

        int warriors_needed = SHIP_WARRIORS - warriors_aboard;
        if (warriors_needed > 0) {
            if (getIdleWarriors() != null && getIdleWarriors().length > 0) {
                getOwner().setTarget(firstN(getIdleWarriors(), warriors_needed), ship, Action.DEFAULT, false);
            } else {
                nodeDeployUnitsInArmory(warriors_needed);
            }
        }
    }

    private void nodeDeployPeonsFromQuarters(@NonNull Ship ship, int num_peons) {
        if (getQuarters() == null)
            return;
        deployPeonsFromQuarters((Building) getQuarters()[0], num_peons, ship);
    }

    private int deployPeonsFromQuarters(@NonNull Building quarters, int num_peons, @NonNull Target rally) {
        if (quarters.isDead())
            return 0;

        int available = quarters.getUnitContainer().getNumSupplies() - MIN_UNITS_REPRODUCING[difficulty];
        if (available <= 0)
            return 0;

        int ordered = Math.min(num_peons, available);
        quarters.setRallyPoint(rally);
        getOwner().deployUnits(quarters, DeployType.PEON, ordered);
        return ordered;
    }

    private @NonNull Selectable<?> @NonNull [] firstN(@NonNull Selectable<?> @NonNull [] list, int n) {
        n = Math.min(n, list.length);
        Selectable<?>[] result = Selectable.newArray(n);
        System.arraycopy(list, 0, result, 0, n);
        return result;
    }

    private @NonNull Selectable<?> @NonNull [] lastN(@NonNull Selectable<?> @NonNull [] list, int n) {
        n = Math.min(n, list.length);
        Selectable<?>[] result = Selectable.newArray(n);
        System.arraycopy(list, list.length - n, result, 0, n);
        return result;
    }

    private @NonNull Selectable<?> @NonNull [] getPeons(int min_num_peons) {
        var idle = getIdlePeons();
        int idleCount = idle != null ? idle.length : 0;

        // Use idle peons first for construction, but allow build steps to pull from gatherers as a
        // fallback when the idle pool is temporarily empty. That keeps the AI building consistently
        // while still letting gathering, army production, patrol, escort, and defense continue.
        // //added by ikill240c 2026-09-10 14:05
        List<Selectable<?>> peons = new ArrayList<>();
        if (idle != null) {
            peons.addAll(Arrays.asList(idle));
        }

        int needed = Math.max(min_num_peons - idleCount, 0);
        if (needed > 0) {
            for (Selectable<?>[] group : new Selectable<?>[][]{getGatherIronPeons(), getGatherRockPeons(), getGatherTreePeons(), getGatherRubberPeons()
            }) {
                if (group == null)
                    continue;
                for (Selectable<?> peon : group) {
                    if (peons.size() >= min_num_peons)
                        break;
                    peons.add(peon);
                }
                if (peons.size() >= min_num_peons)
                    break;
            }
        }

        return peons.toArray(Selectable.newArray(peons.size()));
    }

    /*	private final int getNumUnitsDeploying() {//removed by ikill240c
            int result = 0;
            if (getArmory() != null) {
                Building armory = (Building)getArmory()[0];
                result += armory.getDeployContainer(DeployType.ROCK_WARRIOR).getNumSupplies();
                result += armory.getDeployContainer(DeployType.IRON_WARRIOR).getNumSupplies();
                result += armory.getDeployContainer(DeployType.RUBBER_WARRIOR).getNumSupplies();
                result += armory.getDeployContainer(DeployType.PEON).getNumSupplies();
            }
            return result;
        }
    */
    private int numWeapons(@NonNull Building armory) {
        return armory.getSupplyContainer(RockAxeWeapon.class).getNumSupplies() + armory.getSupplyContainer(
                IronAxeWeapon.class).getNumSupplies() + armory.getSupplyContainer(
                        RubberAxeWeapon.class).getNumSupplies();
    }

    private @Nullable Player chooseFocusEnemy() { //added by ikill240c
        Building home = homeBuilding();
        if (home == null)
            return null;
        int hx = home.getGridX();
        int hy = home.getGridY();

        Player best = null;
        double best_score = Double.MAX_VALUE;
        for (Player p : getOwner().getWorld().getPlayers()) {
            if (!getOwner().isEnemy(p) || p.getUnits().getSet().isEmpty())
                continue;
            int ex = com.oddlabs.tt.pathfinder.UnitGrid.toGridCoordinate(p.getStartX());
            int ey = com.oddlabs.tt.pathfinder.UnitGrid.toGridCoordinate(p.getStartY());
            int dx = ex - hx;
            int dy = ey - hy;
            double dist = Math.sqrt(dx * dx + dy * dy);
            int strength = p.getStatus();
            double score = dist * (1.0 + strength / 100.0);
            if (score < best_score) {
                best_score = score;
                best = p;
            }
        }
        return best;
    }

    private @Nullable Target findNearestOfPlayer(@NonNull Player target_player, int start_x, int start_y,
            boolean buildings_only) {
        Selectable<?> best = null;
        int best_dist_sqr = Integer.MAX_VALUE;
        for (Selectable<?> s : target_player.getUnits().getSet()) {
            if (s.isDead() || (buildings_only && !(s instanceof LandBuilding)))
                continue;
            int dx = s.getGridX() - start_x;
            int dy = s.getGridY() - start_y;
            int dist_sqr = dx * dx + dy * dy;
            if (dist_sqr < best_dist_sqr) {
                best_dist_sqr = dist_sqr;
                best = s;
            }
        }
        return best;
    }

    private @Nullable Target findTarget(int start_x, int start_y) {//added by ikill240c
        Player focus = chooseFocusEnemy();
        Target best_building;
        Target best_target;
        if (focus != null) {
            best_building = findNearestOfPlayer(focus, start_x, start_y, true);
            best_target = findNearestOfPlayer(focus, start_x, start_y, false);
        } else {
            best_building = getOwner().findNearestEnemyBuilding(start_x, start_y);
            best_target = getOwner().findNearestEnemy(start_x, start_y);
        }
        if (best_building == null) {
            return best_target;
        }
        if (best_target == null) {
            return null;
        }
        int squared_dist_building = (best_building.getGridX() - start_x) * (best_building.getGridX() - start_x) + (best_building.getGridY() - start_y) * (best_building.getGridY() - start_y);
        int squared_dist_target = (best_target.getGridX() - start_x) * (best_target.getGridX() - start_x) + (best_target.getGridY() - start_y) * (best_target.getGridY() - start_y);
        return squared_dist_target < squared_dist_building / 2 ? best_target : best_building;
    }

    private boolean buildBuilding(int building_type, Selectable<?> @NonNull [] selection, int grid_x, int grid_y) {//added by ikill240c
        return buildBuilding(building_type, selection, grid_x, grid_y, 40);
    }

    private boolean buildBuilding(int building_type, Selectable<?> @NonNull [] selection, int grid_x, int grid_y, //added by ikill240c
            int range) {
        BuildingSiteScanFilter filter = new BuildingSiteScanFilter(getUnitGrid(),
                getOwner().getRace().getBuildingTemplate(building_type), range, true);
        getUnitGrid().scan(filter, grid_x, grid_y);
        List<? extends Target> target_list = filter.getResult();
        if (!target_list.isEmpty()) {
            Target target = target_list.getFirst();
            getOwner().placeBuilding(selection, building_type, target.getGridX(), target.getGridY());
            return true;
        } else {
            return false;
        }
    }
}
