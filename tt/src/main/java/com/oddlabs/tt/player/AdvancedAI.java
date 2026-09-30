package com.oddlabs.tt.player;

import com.oddlabs.matchmaking.Game;
import com.oddlabs.matchmaking.GameMode; //added by ikill240c
import com.oddlabs.tt.gamemode.GameModeRegistry; //added by ikill240c
import com.oddlabs.tt.gamemode.GameModeRules; //added by ikill240c
import com.oddlabs.tt.gamemode.koth.KingOfTheIslandModeRules; //added by ikill240c
import com.oddlabs.tt.gamemode.koth.KingOfTheHillModeRules; //added by ikill240c
import com.oddlabs.tt.landscape.LandscapeTarget;
import com.oddlabs.tt.landscape.World; //added by ikill240c 2026-09-09 23:55
import com.oddlabs.tt.model.Abilities;
import com.oddlabs.tt.model.Action;
import com.oddlabs.tt.model.Building;
import com.oddlabs.tt.model.MountUnitContainer; //added by ikill240c
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
import com.oddlabs.tt.model.behaviour.NullController; //added by ikill240c
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
import com.oddlabs.tt.pathfinder.UnitGrid; //added by ikill240c
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
    // Extrapolates each difficulty-scaled array's existing normal->hard trend one step further
    // (see each array below) rather than reusing hard's own values - an "Insane" tier that plays
    // identically to Hard wouldn't actually be a new difficulty. //added by ikill240c
    public static final int DIFFICULTY_INSANE = 3; //added by ikill240c

    private static final int SCORE_PEON = 2;
    private static final int SCORE_WARRIOR_ROCK = 5;
    private static final int SCORE_WARRIOR_IRON = 10;
    private static final int SCORE_WARRIOR_RUBBER = 15;
    private static final int SCORE_CHIEFTAIN = 25;
    private static final float[] DEFENSE_FACTOR = new float[]{0.5f, 1.0f, 1.5f, 2f}; //added by ikill240c

    private static final int[] MIN_UNITS_BUILDING_WEAPONS = new int[]{5, 10, 15, 20}; //added by ikill240c
    private static final int[] MIN_WEAPONS_IN_STOCK = new int[]{25, 15, 10, 5}; // rushing penalty //added by ikill240c
    // Reserve kept in each Quarters' own stored supply before any surplus gets transferred out to
    // an armory (see nodeTransferUnits()). This is meant to make a harder AI hoard a slightly
    // bigger buffer before committing peons outward, not stall its economy outright - but Hard/
    // Insane's original values of 20/30 badly underestimated how slow ReproduceUnitContainer's
    // reproduction rate actually is at low stored supply (it scales with cube-root of the CURRENT
    // stored amount, off a 0.5 floor, so a Quarters starting from zero climbs it very slowly - and
    // every additional Quarters an AI builds starts its own reproduction back at that same slow
    // floor rather than sharing progress, so an AI with several Quarters was spreading its growth
    // across multiple pools that individually crawl even slower). In practice this meant whichever
    // AI's Quarters happened to get a small early lead (pure timing luck) compounded away past the
    // threshold and snowballed, while the others sat effectively permanently below the 20/30
    // reserve and never sent a single peon to their own armory for the rest of the match - matching
    // reports of "quarters and armory get built fine, but no further gatherers/peons/warriors ever
    // go out", varying session to session by which AI got lucky. Lowered to comfortably reachable
    // values within a normal game's opening minutes regardless of that early-luck factor, while
    // still keeping Hard/Insane's intent of holding back a somewhat bigger buffer than Normal's 5.
    // //added by ikill240c
    private static final int[] MIN_UNITS_REPRODUCING = new int[]{5, 8, 14, 18}; //added by ikill240c
    private static final int[] MAX_UNITS_GATHERING_TREE = new int[]{2, 5, 30, 50}; //added by ikill240c
    private static final int[] MAX_UNITS_GATHERING_ROCK = new int[]{1, 3, 8, 12}; //added by ikill240c
    private static final int[] MAX_UNITS_GATHERING_IRON = new int[]{1, 3, 25, 50}; //added by ikill240c
    private static final int[] MAX_UNITS_GATHERING_RUBBER = new int[]{1, 3, 10, 15}; //added by ikill240c

    private static final float WARRIOR_STUCK_THRESHOLD = 4f;//added by ikill240c og 8
    private final java.util.Map<Unit, float[]> warrior_positions = new java.util.HashMap<>();//added by ikill240c
    // Chieftain idle-heal logic itself moved to Unit.doAnimate() so it applies to every chieftain
    // regardless of who controls it - see that method's own comment for why, and its
    // chieftain_heal_timer field for the per-unit equivalent of what used to be tracked here as a
    // per-player Map. //added by ikill240c

    private static final float CHIEFTAIN_IDLE_RETURN_SECONDS = 15f;//added by ikill240c
    private float chieftain_idle_time = 0f;//added by ikill240c
    // chieftainHealIdleSeconds()/chieftainHealAmount() removed - both just forwarded to
    // World getters directly, and their only caller (nodeHealChieftain()) moved to
    // Unit.doAnimate(), which reads the same World getters itself. //added by ikill240c
    private static final float CHIEF_HITRUN_HEALTH_PCT = 0.3f;//added by ikill240c - was 0.6f, lowered per explicit request to make hit-and-run raids happen more often: a chieftain now qualifies for another opportunistic raid sooner, without waiting to heal as much first
    private static final int CHIEF_HITRUN_MAX_ESCORT = 25;//added by ikill240c
    private static final float CHIEF_HITRUN_SEARCH_RADIUS = 450f;//added by ikill240c
    // More than this many idle, ready chieftains at once group into a single coordinated raid on
    // one shared target instead of each launching its own separate raid - see
    // nodeChiefHitAndRun()'s own comment for why. //added by ikill240c
    private static final int GROUP_HITRUN_THRESHOLD = 2; //added by ikill240c
    // Every chieftain currently out on a hit-and-run raid, individual or group - was a single
    // boolean+Unit pair (chief_hitrun_active/chief_hitrun_unit), which meant only ONE chieftain
    // could ever be raiding at a time regardless of how many idle ones a player actually had; any
    // additional chieftains just sat idle at home unused. A Set lets every ready chieftain raid
    // independently (or together, once above GROUP_HITRUN_THRESHOLD) rather than serializing them
    // through one shared flag. //added by ikill240c
    private final java.util.Set<Unit> chief_hitrun_units = new java.util.HashSet<>(); //added by ikill240c

    private final java.util.Set<Unit> patrol_units = new java.util.HashSet<>();//added by ikill240c
    private final java.util.Set<Unit> escort_units = new java.util.HashSet<>();//added by ikill240c
    // These were ThreadLocalRandom statics, so every client rolled its own value and multiplayer
    // desynced. World.rollMatchValue is seeded from the mapcode, so all peers agree. //added by ikill240c 2026-09-09 23:55
    private int escortsPerGatherGroup() { //added by ikill240c 2026-09-09 23:55
        return getOwner().getWorld().rollMatchValue(World.SALT_ESCORTS_PER_GATHER_GROUP, 4, 9); //added by ikill240c 2026-09-09 23:55
    } //og 15
    private int patrolForceSize() { //added by ikill240c 2026-09-09 23:55
        return getOwner().getWorld().rollMatchValue(World.SALT_PATROL_FORCE_SIZE, 6, 22); //added by ikill240c 2026-09-09 23:55
    } //og 4
    private static final float PATROL_RADIUS = 300f;//og 20
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

    private static final float DEFENSE_SCAN_RADIUS = 30f;//added by ikill240c og 45 base 30
    private final List<int[]> occupied_zones = new ArrayList<>();
    // occupied_zones only ever grew (every resource-outpost tower this AI ever built adds an
    // entry - see the various occupied_zones.add() call sites), with nothing ever removing a
    // zone once its tower was destroyed. Both nodeDefendBase() (every single tick) and
    // isNearSafeZone() (called per idle warrior, periodically) iterate this entire list, so a
    // long match on a large map - more territory to establish outposts across, more chances for
    // them to be destroyed and rebuilt over a long game - meant this list only ever grew, making
    // every tick's defense scan progressively more expensive for the rest of the match even
    // though most of those zones no longer had anything there to defend. This is very plausibly
    // the actual cause behind "gets worse as it progresses" - unlike a one-time cost, this
    // compounds for the rest of the game once it starts. Periodically (not every tick, to avoid
    // this cleanup itself adding a per-tick cost) prunes any zone with no living, this-player-
    // owned building still standing nearby. //added by ikill240c
    private float occupied_zones_cleanup_cooldown = 0f; //added by ikill240c

    private void nodeCleanupOccupiedZones(float t) { //added by ikill240c
        occupied_zones_cleanup_cooldown -= t; //added by ikill240c
        if (occupied_zones_cleanup_cooldown > 0f) return; //added by ikill240c
        occupied_zones_cleanup_cooldown = 15f; //added by ikill240c - infrequent; this list only changes on the timescale of towers being built or destroyed, not every tick

        occupied_zones.removeIf(zone -> { //added by ikill240c
            float world_x = com.oddlabs.tt.pathfinder.UnitGrid.coordinateFromGrid(zone[0]); //added by ikill240c
            float world_y = com.oddlabs.tt.pathfinder.UnitGrid.coordinateFromGrid(zone[1]); //added by ikill240c
            FindOccupantFilter<Building> filter = new FindOccupantFilter<>(world_x, world_y, //added by ikill240c
                    15f, null, Building.class); //added by ikill240c - a tight radius: this is "is there still a building AT this exact outpost spot", not a general area search
            getUnitGrid().scan(filter, zone[0], zone[1]); //added by ikill240c
            for (Building building : filter.getResult()) { //added by ikill240c
                if (!building.isDead() && building.getOwner() == getOwner()) //added by ikill240c
                    return false; // still has a live building here - keep this zone //added by ikill240c
            } //added by ikill240c
            return true; // nothing left standing here - prune it //added by ikill240c
        }); //added by ikill240c
    } //added by ikill240c

    private final java.util.Set<Unit> attacking_units = new java.util.HashSet<>();//added by ikill240c
    private static final float RETREAT_SCAN_RADIUS = 30f;//added by ikill240c og 40

    // Configurable: how many resource-outpost towers the AI builds. //added by ikill240c 2026-09-09 23:10
    private int numResourceTowers() { //added by ikill240c 2026-09-09 23:10
        return getOwner().getWorld().getNumResourceTowers(); //added by ikill240c 2026-09-09 23:10
    } //added by ikill240c 2026-09-09 23:10
    private int resource_towers_built = 0;
    private static final int BUILDERS_PER_TOWER_TARGET = 10; // matches getPeons(10) used to start a tower //added by ikill240c
    private static final int TOWER_SPACING_SLOTS = 12;   // towers per ring, ~45° apart //added by ikill240c
    private static final float TOWER_BASE_RADIUS = 16f;
    private static final float TOWER_RADIUS_STEP = 10f;  // radius growth per full ring, og 10
    private static final int TOWER_SCAN_RANGE = 18;      // tight, to avoid crossing into unsafe terrain, og 12
    // Configurable: max towers the AI builds concurrently. //added by ikill240c 2026-09-09 23:10
    private int maxConcurrentTowers() { //added by ikill240c 2026-09-09 23:10
        return getOwner().getWorld().getMaxConcurrentTowers(); //added by ikill240c 2026-09-09 23:10
    } //added by ikill240c 2026-09-09 23:10
// Replaces UNITS_PER_TOWER1..UNITS_PER_TOWER40.
// Row index = tower tier - 1 (row 0 = tier 1, row 39 = tier 40).
// Column index = difficulty (0=Easy, 1=Normal, 2=Hard, 3=Insane).
//
// Was missing the Insane column entirely (only 3 entries per row) - nodeUpdateTowerTarget()
// indexes this with the AI's own difficulty value (0-3), so any Insane-difficulty AI hit
// ArrayIndexOutOfBoundsException on this exact line every single tick, forever, for the entire
// match. safeRun() caught it each time so it never crashed the game outright, but that also
// meant this node silently never succeeded even once - no tower tier was ever picked, so
// nodeGuardTowers() was never even called, and towers never got built or manned for the rest of
// the game on that difficulty. Insane values below follow the same "more aggressive than Hard"
// relationship Hard already has to Normal - roughly 70% of the Hard threshold at each tier,
// continuing the existing progression rather than introducing an unrelated curve.
// //added by ikill240c
    private static final int[][] UNITS_PER_TOWER = { //added by ikill240c
            {80   , 60  , 40  , 20  },  // tier 1
            {200  , 120 , 80  , 40  },  // tier 2
            {320  , 180 , 120 , 60  },  // tier 3
            {440  , 240 , 160 , 80  },  // tier 4
            {560  , 300 , 200 , 100 },  // tier 5
            {680  , 360 , 240 , 120 },  // tier 6
            {800  , 420 , 280 , 140 },  // tier 7
            {920  , 480 , 320 , 160 },  // tier 8
            {1040 , 540 , 360 , 180 },  // tier 9
            {1160 , 600 , 400 , 200 },  // tier 10
            {1280 , 660 , 440 , 220 },  // tier 11
            {1400 , 720 , 480 , 240 },  // tier 12
            {1520 , 780 , 520 , 260 },  // tier 13
            {1640 , 840 , 560 , 280 },  // tier 14
            {1760 , 900 , 600 , 300 },  // tier 15
            {1880 , 960 , 640 , 320 },  // tier 16
            {2000 , 1020, 680 , 340 },  // tier 17
            {2120 , 1080, 720 , 360 },  // tier 18
            {2240 , 1140, 760 , 380 },  // tier 19
            {2360 , 1200, 800 , 400 },  // tier 20
            {2480 , 1260, 840 , 420 },  // tier 21
            {2600 , 1320, 880 , 440 },  // tier 22
            {2720 , 1380, 920 , 460 },  // tier 23
            {2840 , 1440, 960 , 480 },  // tier 24
            {2960 , 1500, 1000, 500 },  // tier 25
            {3080 , 1560, 1040, 520 },  // tier 26
            {3200 , 1620, 1080, 540 },  // tier 27
            {3320 , 1680, 1120, 560 },  // tier 28
            {3440 , 1740, 1160, 580 },  // tier 29
            {3560 , 1800, 1200, 600 },  // tier 30
            {3680 , 1860, 1240, 620 },  // tier 31
            {3800 , 1920, 1280, 640 },  // tier 32
            {3920 , 1980, 1320, 660 },  // tier 33
            {4040 , 2040, 1360, 680 },  // tier 34
            {4160 , 2100, 1400, 700 },  // tier 35
            {4280 , 2160, 1440, 720 },  // tier 36
            {4400 , 2220, 1480, 740 },  // tier 37
            {4520 , 2280, 1520, 760 },  // tier 38
            {4640 , 2340, 1560, 780 },  // tier 39
            {4760 , 2400, 1600, 800 },  // tier 40 
			{4880 , 2460, 1640, 820 },  // tier 41 
            {5000 , 2520, 1680, 840 },  // tier 42 
            {5120 , 2580, 1720, 860 },  // tier 43 
            {5240 , 2640, 1760, 880 },  // tier 44 
            {5360 , 2700, 1800, 900 },  // tier 45 
            {5480 , 2760, 1840, 920 },  // tier 46 
            {5600 , 2820, 1880, 940 },  // tier 47 
            {5720 , 2880, 1920, 960 },  // tier 48 
            {5840 , 2940, 1960, 980 },  // tier 49 
            {5960 , 3000, 2000, 1000},  // tier 50 
            {6080 , 3060, 2040, 1020},  // tier 51 
            {6200 , 3120, 2080, 1040},  // tier 52 
            {6320 , 3180, 2120, 1060},  // tier 53 
            {6440 , 3240, 2160, 1080},  // tier 54 
            {6560 , 3300, 2200, 1100},  // tier 55 
            {6680 , 3360, 2240, 1120},  // tier 56 
            {6800 , 3420, 2280, 1140},  // tier 57 
            {6920 , 3480, 2320, 1160},  // tier 58 
            {7040 , 3540, 2360, 1180},  // tier 59 
            {7160 , 3600, 2400, 1200},  // tier 60 
            {7280 , 3660, 2440, 1220},  // tier 61 
            {7400 , 3720, 2480, 1240},  // tier 62 
            {7520 , 3780, 2520, 1260},  // tier 63 
            {7640 , 3840, 2560, 1280},  // tier 64 
            {7760 , 3900, 2600, 1300},  // tier 65 
            {7880 , 3960, 2640, 1320},  // tier 66 
            {8000 , 4020, 2680, 1340},  // tier 67 
            {8120 , 4080, 2720, 1360},  // tier 68 
            {8240 , 4140, 2760, 1380},  // tier 69 
            {8360 , 4200, 2800, 1400},  // tier 70 
            {8480 , 4260, 2840, 1420},  // tier 71 
            {8600 , 4320, 2880, 1440},  // tier 72 
            {8720 , 4380, 2920, 1460},  // tier 73 
            {8840 , 4440, 2960, 1480},  // tier 74 
            {8960 , 4500, 3000, 1500},  // tier 75 
            {9080 , 4560, 3040, 1520},  // tier 76 
            {9200 , 4620, 3080, 1540},  // tier 77 
            {9320 , 4680, 3120, 1560},  // tier 78 
            {9440 , 4740, 3160, 1580},  // tier 79 
            {9560 , 4800, 3200, 1600},  // tier 80 
			{9680 , 4860, 3240, 1620},  // tier 81 
			{9800 , 4920, 3280, 1640},  // tier 82 
            {9920 , 4980, 3320, 1660},  // tier 83 
            {10040, 5040, 3360, 1680},  // tier 84 
            {10160, 5100, 3400, 1700},  // tier 85 
            {10280, 5160, 3440, 1720},  // tier 86 
            {10400, 5220, 3480, 1740},  // tier 87 
            {10520, 5280, 3520, 1760},  // tier 88 
            {10640, 5340, 3560, 1780},  // tier 89 
            {10760, 5400, 3600, 1800},  // tier 90 
            {10880, 5460, 3640, 1820},  // tier 91 
            {11000, 5520, 3680, 1840},  // tier 92 
            {11120, 5580, 3720, 1860},  // tier 93 
            {11240, 5640, 3760, 1880},  // tier 94 
            {11360, 5700, 3800, 1900},  // tier 95 
            {11480, 5760, 3840, 1920},  // tier 96 
            {11600, 5820, 3880, 1940},  // tier 97 
            {11720, 5880, 3920, 1960},  // tier 98 
            {11840, 5940, 3960, 1980},  // tier 99 
            {11960, 6000, 4000, 2000},  // tier 100
            {12080, 6060, 4040, 2020},  // tier 101
            {12200, 6120, 4080, 2040},  // tier 102
            {12320, 6180, 4120, 2060},  // tier 103
            {12440, 6240, 4160, 2080},  // tier 104
            {12560, 6300, 4200, 2100},  // tier 105
            {12680, 6360, 4240, 2120},  // tier 106
            {12800, 6420, 4280, 2140},  // tier 107
            {12920, 6480, 4320, 2160},  // tier 108
            {13040, 6540, 4360, 2180},  // tier 109
            {13160, 6600, 4400, 2200},  // tier 110
            {13280, 6660, 4440, 2220},  // tier 111
            {13400, 6720, 4480, 2240},  // tier 112
            {13520, 6780, 4520, 2260},  // tier 113
            {13640, 6840, 4560, 2280},  // tier 114
            {13760, 6900, 4600, 2300},  // tier 115
            {13880, 6960, 4640, 2320},  // tier 116
            {14000, 7020, 4680, 2340},  // tier 117
            {14120, 7080, 4720, 2360},  // tier 118
            {14240, 7140, 4760, 2380},  // tier 119
            {14360, 7200, 4800, 2400},  // tier 120
            {14480, 7260, 4840, 2420},  // tier 121
    };

    private static final int SHIP_PEONS = 26;
    private static final int SHIP_WARRIORS = 26;
    private static final int SHIP_BUILDERS = 20;
    private static final float SHIP_MIN_HEALTH = 0.35f;
    private static final int SHIP_HOME_RANGE = 50;
    private static final int FLEET_SIZE = 5;

    // Support for sending idle, fully-crewed ships out to OTHER islands instead of just sitting at
    // home once there's no already-known nearby enemy to fight (see useShip()/chooseExplorationIsland()
    // below). Previously a battle-ready ship with no enemy on a beach or the open sea simply did
    // nothing at all, forever, even on archipelago maps with untouched islands elsewhere.
    // //added by ikill240p 2026-09-14
    private record ExplorationTarget(int island_id, boolean hostile) { //added by ikill240p 2026-09-14 - hostile = island has a known enemy (attack); otherwise it was picked for its untapped resources (gather)
    }

    // Tracks, per ship, which island (if any) it's currently sailing to or has just arrived at as
    // part of an exploration voyage. Absent from this map = not currently exploring (normal
    // home/combat/return logic applies). //added by ikill240p 2026-09-14
    private final java.util.Map<Ship, ExplorationTarget> ship_exploration = new java.util.HashMap<>(); //added by ikill240p 2026-09-14

    private static final int ISLAND_ARRIVAL_RANGE = 60; //added by ikill240p 2026-09-14 - how close (grid cells) a ship must get to its target island's start point before it's considered "arrived" and ready to unload
    private static final int MIN_ISLAND_RESOURCES = 20; //added by ikill240p 2026-09-14 - an island's combined tree+rock+iron count must beat this to be worth a special gathering voyage
    private static final int EXPLORE_PEON_BATCH = 15; //added by ikill240p 2026-09-14 - how many peons to land per voyage to a resource island (one-shot per voyage - see deployCrewAtIsland())
    private static final int EXPLORE_WARRIOR_BATCH = 15; //added by ikill240p 2026-09-14 - how many warriors to land per voyage to a hostile island (one-shot per voyage - see deployCrewAtIsland())

    // Mutable (was final) so Adaptive AI can rebalance it live during a match - see
    // nodeAdaptiveDifficulty() below. Manual Easy/Normal/Hard AIs never change this after
    // construction, so behaviour for them is unaffected by dropping final.
    // //added by ikill240c 2026-09-12
    private int difficulty;
    // True only for AI players constructed with the 4-arg constructor's adaptive=true - i.e. when
    // WorldParameters.isAdaptiveAiEnabled() was on for the match. //added by ikill240c 2026-09-12
    private final boolean adaptive; //added by ikill240c 2026-09-12
    // Stage 2's bandit-chosen posture and the context it was chosen for, so the match's outcome can
    // be recorded against the same pair later (see recordBanditOutcome()). Both null for non-adaptive
    // AIs - manual Easy/Normal/Hard difficulty always behaves exactly as before.
    // //added by ikill240c 2026-09-12
    private final @Nullable PlayerStyleContext selected_context; //added by ikill240c 2026-09-12
    private final @Nullable AiBehaviorProfile selected_behavior; //added by ikill240c 2026-09-12

    private final int[] NUM_WARRIORS = new int[]{3, 7, 14, 30}; //added by ikill240c
    private final int[] NUM_WARRIORS_INCREASE = new int[]{2, 5, 13, 21}; //added by ikill240c
    private final int[] NUM_WARRIORS_MAX = new int[]{50, 100, 250, 500}; //added by ikill240c
    private final int[] NUM_WARRIORS_FOR_CHIEFTAIN = new int[]{80, 40, 25, 15}; //added by ikill240c

    private static final class SupplyLocationFilter implements ScanFilter {//added by ikill240c
        private final Class<?> supply_class;
        private final int range;
        // Candidates within this distance of any excluded_zones entry are rejected (the scan
        // continues past them) rather than accepted - without this, the scan always stops at
        // whichever matching supply node is nearest to the origin, which is the SAME node every
        // time it's called from the same origin, clustering every resource tower on top of one
        // another instead of spreading them across different resource spots.
        // //added by ikill240c
        private static final int MIN_DISTANCE_FROM_EXCLUDED = 20; //added by ikill240c
        private final @NonNull List<int[]> excluded_zones; //added by ikill240c
        private Target result;

        SupplyLocationFilter(Class<?> supply_class, int range) { //added by ikill240c
            this(supply_class, range, java.util.List.of()); //added by ikill240c - no exclusions, matches the original behavior exactly for any caller that doesn't need spreading
        }

        SupplyLocationFilter(Class<?> supply_class, int range, @NonNull List<int[]> excluded_zones) { //added by ikill240c
            this.supply_class = supply_class;
            this.range = range;
            this.excluded_zones = excluded_zones; //added by ikill240c
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
                for (int[] zone : excluded_zones) { //added by ikill240c
                    int dx = grid_x - zone[0]; //added by ikill240c
                    int dy = grid_y - zone[1]; //added by ikill240c
                    if (dx * dx + dy * dy < MIN_DISTANCE_FROM_EXCLUDED * MIN_DISTANCE_FROM_EXCLUDED) //added by ikill240c
                        return false; // too close to an already-used spot - keep scanning //added by ikill240c
                } //added by ikill240c
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
        // Which kind of location this threat is defending - "quarters"/"armory"/"outpost", or null
        // for threats that aren't from the AI's own base (e.g. ally defense). Used only to look up
        // PlayerBehaviorProfile's per-building hit-share weighting; doesn't affect anything when
        // Adaptive AI is off. //added by ikill240c 2026-09-12
        final @Nullable String kind; //added by ikill240c 2026-09-12

        Threat(LandscapeTarget target, int score, @Nullable String kind) { //added by ikill240c 2026-09-12
            this.target = target;
            this.score = score;
            this.kind = kind; //added by ikill240c 2026-09-12
        }
    }

    private @Nullable LandscapeTarget defense_target = null;

    // Seconds since the last attack was launched. Used to break out of a permanent "never favorable"
    // stalemate where the AI sits on a big army and never attacks again. //added by ikill240c 2026-09-10 15:10
    private float time_since_attack = 0f; //added by ikill240c 2026-09-10 15:10
    // Tracks the live target the main attack most recently sent its force at, so
    // nodeCheckMainAttackTargetStillAlive() below can notice if it died before the force actually
    // arrives - the attack order itself is issued via setLandscapeTarget() with the target's
    // SNAPSHOT coordinates (needed for formation-aware offset spreading across the group - see
    // FormationLayout - which setTarget() with a live Target doesn't do, since it gives every
    // unit the exact same target with no spread at all), so once dispatched, the walking force has
    // no live awareness that its destination enemy might already be dead, and would otherwise just
    // keep walking to wherever it used to be until arrival, wasting the whole trip. This field is
    // the AI-level substitute for that missing live tracking. //added by ikill240c
    private @Nullable Target tracked_main_attack_target = null; //added by ikill240c
    private float main_attack_target_check_cooldown = 0f; //added by ikill240c
    private static final float ATTACK_STALL_SECONDS = 90f; //added by ikill240c 2026-09-10 15:10
    private static final int MIN_WARRIORS_FOR_STALL_ATTACK = 8; //added by ikill240c 2026-09-10 15:10

    public AdvancedAI(@NonNull Player owner, UnitInfo unit_info, int difficulty) {
        this(owner, unit_info, difficulty, false); //added by ikill240c 2026-09-12
    }

    // Adaptive-AI entry point. When adaptive is true, the manually-passed difficulty is ignored in
    // favor of AdaptiveAIProfile's seed (based on the player's historical results), and animate()
    // will keep rebalancing it live via nodeAdaptiveDifficulty(). //added by ikill240c 2026-09-12
    public AdvancedAI(@NonNull Player owner, UnitInfo unit_info, int difficulty, boolean adaptive) { //added by ikill240c
        this(owner, unit_info, difficulty, adaptive, -1); //added by ikill240c
    } //added by ikill240c

    // shared_seed_difficulty: the host's Adaptive AI seed (WorldParameters.getAdaptiveAiSeedDifficulty()), so every
    // player's machine starts the AI identically; -1 = use this computer's own AdaptiveAIProfile. //added by ikill240c
    public AdvancedAI(@NonNull Player owner, UnitInfo unit_info, int difficulty, boolean adaptive, //added by ikill240c
            int shared_seed_difficulty) { //added by ikill240c
        super(owner, unit_info);
        this.adaptive = adaptive; //added by ikill240c 2026-09-12
        this.difficulty = !adaptive ? difficulty //added by ikill240c
                : shared_seed_difficulty >= 0 ? shared_seed_difficulty //added by ikill240c
                : AdaptiveAIProfile.get().getSeedDifficulty(); //added by ikill240c 2026-09-12
        // Stage 2: pick a behavior posture for this match based on how the human opponent has
        // historically played (PlayerStyleProfile), via the bandit's learned per-context statistics.
        // Chosen once at construction rather than re-picked mid-match - re-rolling the posture
        // during a match would make the reward signal (this match's win/loss) ambiguous about which
        // posture actually caused the outcome. //added by ikill240c 2026-09-12
        if (adaptive) { //added by ikill240c 2026-09-12
            this.selected_context = PlayerStyleContext.fromAggression(PlayerStyleProfile.get().getAggression()); //added by ikill240c 2026-09-12
            this.selected_behavior = AiBehaviorBandit.get().selectArm(selected_context); //added by ikill240c 2026-09-12
        } else { //added by ikill240c 2026-09-12
            this.selected_context = null; //added by ikill240c 2026-09-12
            this.selected_behavior = null; //added by ikill240c 2026-09-12
        } //added by ikill240c 2026-09-12
    }

    public int getDifficulty() {
        return difficulty;
    }

    // True only for a genuinely, deliberately Hard-configured AI - matches isAdaptive()'s own
    // reasoning just below: an adaptive AI currently sitting at DIFFICULTY_HARD wasn't actually
    // configured as Hard by the player, so it must not report itself as hard-difficulty either.
    // //added by ikill240c
    @Override //added by ikill240c
    public boolean isHardDifficulty() { //added by ikill240c
        return !adaptive && difficulty == DIFFICULTY_HARD; //added by ikill240c
    } //added by ikill240c

    // Whether this AI is running in Adaptive mode (see the 4-arg constructor above). Exposed so
    // achievement/UI code that keys off a fixed "Hard AI" concept can exclude adaptive AIs, since
    // an adaptive AI that happens to be sitting at DIFFICULTY_HARD at the moment of victory wasn't
    // actually configured as Hard by the player. //added by ikill240c 2026-09-12
    public boolean isAdaptive() { //added by ikill240c 2026-09-12
        return adaptive; //added by ikill240c 2026-09-12
    } //added by ikill240c 2026-09-12

    // Runs a single AI decision node in isolation: if it throws, the exception is logged (with the
    // node's name, so the real culprit is identifiable) but NOT allowed to propagate out of
    // animate() and abort every node scheduled after it that tick. Previously an uncaught
    // exception from any single node (e.g. one hitting a unit type/state it didn't expect - see
    // getUnitScore()'s own history of exactly this) silently skipped every remaining node for the
    // rest of that tick's animate() call: nodes scheduled early (tower target updates, chieftain
    // checks) would have already run and "stuck", while everything scheduled later (offense,
    // patrol, escort, gather assignment) simply never ran that tick - which reads externally as
    // "units are only doing tower/chief-related things", not as a crash, since nothing ever
    // surfaced the actual exception anywhere visible. This doesn't fix whatever the underlying
    // per-node bug might be, but it stops one bad node from cascading into every other system
    // going quiet, and the printed stack trace is what's needed to find and fix that underlying
    // bug precisely rather than guessing at it. //added by ikill240c
    private void safeRun(@NonNull String node_name, @NonNull Runnable node) { //added by ikill240c
        try { //added by ikill240c
            node.run(); //added by ikill240c
        } catch (RuntimeException e) { //added by ikill240c
            System.err.println("AdvancedAI node '" + node_name + "' threw and was skipped for this tick:"); //added by ikill240c
            e.printStackTrace(); //added by ikill240c
        } //added by ikill240c
    }

    @Override
    public void animate(float t) {   //added by ikill240c
        committed_this_tick.clear(); //added by ikill240c
        time_since_attack += t; //added by ikill240c 2026-09-10 15:10
        match_elapsed_seconds += t; //added by ikill240c 2026-09-12
        time_since_last_recorded_wave += t; //added by ikill240c 2026-09-12
        safeRun("nodeAdaptiveDifficulty", () -> nodeAdaptiveDifficulty(t)); //added by ikill240c
        safeRun("nodeUpdatePlayerStyle", () -> nodeUpdatePlayerStyle(t)); //added by ikill240c
        safeRun("nodeCheckIdleChieftain", () -> nodeCheckIdleChieftain(t)); //added by ikill240c
        safeRun("nodeCheckStuckWarriors", () -> nodeCheckStuckWarriors(t)); //added by ikill240c 2026-09-13: was dropped from animate() entirely in an earlier merge - restoring it, since without it a warrior that gets physically stuck (pathfinding edge case) never recovers for the rest of the match.
        safeRun("nodeCheckAttackingArmy", () -> nodeCheckAttackingArmy(t)); // check if an ongoing attack has turned unfavorable and should retreat //added by ikill240c
        if (!shouldDoAction(t))
            return;

        // ---- ECONOMY: buildings, in strict order Quarters(1) -> Armory -> remaining Quarters/outposts ----
        // nodeBuildArmory() used to only be reachable through the attack path (via
        // nodeDeployUnitsInArmory's else-branch), which meant it silently stopped running once attacks
        // became gated on favorable odds below. It is now called directly and unconditionally here so
        // the economy always progresses regardless of whether the AI is currently attacking.
        // //added by ikill240c 2026-09-08 16:30
        reclassify();
        safeRun("nodeBuildQuarters", this::nodeBuildQuarters); //added by ikill240c
        safeRun("nodeBuildArmory", this::nodeBuildArmory); //added by ikill240c
        safeRun("nodeBuildWeapons", this::nodeBuildWeapons); //added by ikill240c
        safeRun("nodeCleanupOccupiedZones", () -> nodeCleanupOccupiedZones(t)); //added by ikill240c
        safeRun("nodeCheckMainAttackTargetStillAlive", () -> nodeCheckMainAttackTargetStillAlive(t)); //added by ikill240c
        // Always deploy any finished armory stock immediately so the AI can start gathering,
        // defending, and attacking instead of getting stuck after the build phase.
        // //added by ikill240c 2026-09-10 00:00
        safeRun("nodeDeployArmy", this::nodeDeployArmy); //added by ikill240c
        // Chieftain training used to only be reachable through nodeAttackWithWarriorsAndChieftain's
        // else-branch, which itself only runs inside the "if (favorable || stalled)" OFFENSE gate
        // below - the same bug class already fixed for nodeBuildArmory() above. That meant on any
        // tick the AI decided not to attack, no chieftain training was even attempted, matching the
        // reported "sometimes they don't train any [chiefs] or attack" symptom. Call it directly
        // and unconditionally here instead, same as Armory. //added by ikill240c 2026-09-11
        safeRun("nodeTrainChieftain", this::nodeTrainChieftain); //added by ikill240c
        safeRun("nodeCheckOutpost", this::nodeCheckOutpost); //added by ikill240c
        reclassify();
        safeRun("nodeBuildResourceTowers", this::nodeBuildResourceTowers); //added by ikill240c
        reclassify();

        // ---- DEFENSE: base defense, ally assistance, chieftain healing - always runs, never gated ----
        safeRun("nodeDefendBase", this::nodeDefendBase); //added by ikill240c
        reclassify();
        // nodeHealChieftain() removed - chieftain idle-heal now lives in Unit.doAnimate() and runs
        // universally, not just for AI-controlled players. See that method's comment.
        // //added by ikill240c
        reclassify();
        safeRun("nodeDefendAllies", this::nodeDefendAllies); //added by ikill240c
        safeRun("nodeDonateToAllies", this::nodeDonateToAllies); //added by ikill240c
        safeRun("nodeGarrisonAllyTowers", this::nodeGarrisonAllyTowers); //added by ikill240c
        reclassify();

        // ---- TOWERS: guard-tower count target scales with population (see UNITS_PER_TOWER table) ----
        safeRun("nodeUpdateTowerTarget", this::nodeUpdateTowerTarget); //added by ikill240c 2026-09-08 16:30 - extracted from an inline loop that used to live directly in animate()
        reclassify();

        if (isArchipelago()) {
            reclassify();
            if (baseBuildingsDone())
                safeRun("nodeBuildShipAndLoad", this::nodeBuildShipAndLoad); //added by ikill240c
            safeRun("nodeUseShip", this::nodeUseShip); //added by ikill240c
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
        // Bare (not safeRun-wrapped) because favorable/stalled feed the if-statement immediately
        // below - safeRun's Runnable-based wrapper has nowhere to hand back a computed value. That
        // meant an exception here (e.g. from computeArmyStrength()/computeStrongestEnemyStrength())
        // would propagate up UNCAUGHT and abort the rest of THIS TICK's animate() for this AI
        // entirely - everything below this point (nodeChiefHitAndRun, nodeAllInAttack,
        // nodeFavorableAttack, nodeBestMoveAttack, nodeMultiFrontAttack, nodeCheckAllyOutpost,
        // nodePlayKingOfTheIslandObjective, nodeRespondToBeacon) never running that tick, with
        // nothing printed anywhere to say why - silent and easy to mistake for those features
        // simply "not working" rather than an exception further up the same method. Everything
        // ABOVE this point (nodeDefendBase, nodeDefendAllies, nodeCheckAttackingArmy, etc.) is
        // unaffected either way, since each of those already runs inside its own separate safeRun
        // call earlier in this method. Wrapped in a try/catch defaulting to false/false on failure
        // (skip this tick's attack decision, not attack-and-hope) so a thrown exception here can no
        // longer take out everything that follows. //added by ikill240c
        boolean favorable; //added by ikill240c
        boolean stalled; //added by ikill240c
        try { //added by ikill240c
            favorable = computeArmyStrength(getOwner()) >= computeStrongestEnemyStrength(); //added by ikill240c 2026-09-10 15:10
            stalled = time_since_attack >= getStyleAdjustedStallSeconds() //added by ikill240c 2026-09-12
                    && countWarriors() >= getEffectiveMinWarriorsForStallAttack(); //added by ikill240c 2026-09-12
        } catch (RuntimeException e) { //added by ikill240c
            System.out.println("AdvancedAI offense-gate computation threw and was skipped for this tick: " + e); //added by ikill240c
            favorable = false; //added by ikill240c
            stalled = false; //added by ikill240c
        } //added by ikill240c

        if (favorable || stalled) { //added by ikill240c 2026-09-10 15:10
            time_since_attack = 0f; //added by ikill240c 2026-09-10 15:10
            main_attack_wave_count++; //added by ikill240c
            // attackForceMultiplier() scales this up (never down) if this player has historically
            // hit back with bigger forces than a stock attack wave, in this segment (see its own
            // comment - PlayerBehaviorProfile). Independent of, and stacks with, the bandit-selected
            // posture above: that adjusts WHEN the AI decides to attack (stall timing/threshold),
            // this adjusts HOW BIG the attack is once triggered. Returns 1 (no-op) unless Adaptive AI
            // is on. //added by ikill240c 2026-09-13
            int attack_force = (int) (NUM_WARRIORS[difficulty] * attackForceMultiplier()); //added by ikill240c 2026-09-13
            safeRun("nodeAttackWithWarriorsAndChieftain", () -> nodeAttackWithWarriorsAndChieftain(attack_force, //added by ikill240c 2026-09-13
                    attack_force >= NUM_WARRIORS_FOR_CHIEFTAIN[difficulty])); //added by ikill240c 2026-09-13
        }

        // Warrior production (nodeDeployUnitsInArmory - converts armory-stored peons+weapons into
        // actual warrior units) used to happen ONLY as a side effect of nodeAttackWithWarriorsAndChieftain()'s
        // "not enough idle warriors yet" branch above, which itself only ever runs when favorable ||
        // stalled is true. That's a bootstrap deadlock for any AI that isn't currently the strongest:
        // favorable requires enough army strength to beat the strongest enemy (which requires
        // warriors, which requires this very call), and stalled requires countWarriors() >= 6 (same
        // problem). Every AI ties at match start (my_strength == strongest_enemy_strength, so
        // favorable is briefly true for everyone) - the ONLY window where warrior production ever
        // got triggered. The instant any one AI's strength edges ahead by even a point, every other
        // AI flips to favorable=false permanently, can never reach 6 warriors to trigger stalled
        // either, and is locked out of producing a single warrior for the rest of the match -
        // matching reports of one AI snowballing while the rest sit at warriors=0 forever. Calling
        // this unconditionally, independent of whether an attack is favorable right now, decouples
        // "keep building up a warrior pool" from "attack with it this instant" - the favorable/
        // stalled gate above still solely controls WHEN to launch, this just makes sure there's
        // always something to launch with. Uses the same shortfall-vs-idle computation the old
        // in-attack call used, so behavior when an attack IS launching is unchanged.
        // //added by ikill240c
        safeRun("nodeDeployUnitsInArmory", () -> { //added by ikill240c
            int target = NUM_WARRIORS[difficulty]; //added by ikill240c
            Selectable<?>[] idle = getIdleWarriors(); //added by ikill240c
            nodeDeployUnitsInArmory(idle != null ? target - idle.length : target); //added by ikill240c
        }); //added by ikill240c

        // ---- PATROL / ESCORT / CHIEF HIT-AND-RUN: independent of the offense gate above ----
        // Each of these is now isolated via safeRun() specifically because it's this whole section
        // that was reported as going silent - if any single node above this point (most plausibly
        // nodeUpdateTowerTarget, given the reported symptom of units fixating on towers/chiefs
        // instead) threw, NONE of patrol/escort/gather-assignment below ever ran that tick, with no
        // visible error anywhere. //added by ikill240c
        //
        // Full attack/defense roster and how each one is kept genuinely separate, per explicit
        // request - every strategy below claims its OWN slice of idle warriors via
        // claimIdleWarriors() (backed by the committed_this_tick ledger cleared at the top of this
        // method), so none of them can silently cannibalize another's force and make several
        // strategies look like one merged attack:
        //   - Main attack (nodeAttackWithWarriorsAndChieftain, above, inside the favorable||stalled
        //     gate) - the base chief-led attack. Left untouched here beyond what earlier fixes
        //     already needed (the bootstrap-deadlock fix that made nodeDeployUnitsInArmory run
        //     unconditionally, and main_attack_wave_count's increment for nodeAllInAttack below) -
        //     still triggers on its own normal schedule only, not made ongoing.
        //   - Favorable attack (nodeFavorableAttack) - a SEPARATE, ongoing strategy from the main
        //     attack's own favorable/stalled gate: targets specifically the weakest enemy on the
        //     map with a modest force, whenever comfortably ahead of that one player alone.
        //   - Best move attack (nodeBestMoveAttack) - ongoing; picks off the single cheapest,
        //     least-defended target anywhere on the map.
        //   - Multi-front attack (nodeMultiFrontAttack) - ongoing; two independent detachments
        //     against two different objectives at once.
        //   - Hit-and-run (nodeChiefHitAndRun/nodeChiefHitAndRunReturn) - ongoing; opportunistic
        //     chieftain raids, separate from the warrior-based strategies entirely.
        //   - Adaptive (nodeAdaptiveDifficulty/nodeUpdatePlayerStyle, top of this method) - ongoing;
        //     adjusts posture (stall timing, force sizing) from this player's own win/loss and
        //     combat history, feeding the OTHER strategies' parameters rather than attacking itself.
        //   - All-in attack (nodeAllInAttack) - ongoing, but self-throttled to fire only once every
        //     WAVES_PER_ALL_IN main waves - throws the entire idle pool at once.
        //   - Defense (nodeDefendBase/nodeCheckAttackingArmy, above) and ally defense
        //     (nodeDefendAllies, above) - both ongoing and unconditional, never gated behind any
        //     attack decision.
        //   - Ally outpost (nodeCheckAllyOutpost, below) - ongoing; builds near a teammate's base
        //     once this AI's own economy can spare it.
        //   - King of the Island objective (nodePlayKingOfTheIslandObjective, below) - ongoing;
        //     sends a dedicated garrison to hold/contest the capture point whenever this AI's team
        //     doesn't currently hold it, in that game mode specifically (a no-op otherwise).
        // //added by ikill240c
        safeRun("nodeChiefHitAndRun", this::nodeChiefHitAndRun); //added by ikill240c
        safeRun("nodeRespondToBeacon", this::nodeRespondToBeacon); //added by ikill240c
        safeRun("nodeChiefHitAndRunReturn", this::nodeChiefHitAndRunReturn); //added by ikill240c
        safeRun("nodePatrol", this::nodePatrol); //added by ikill240c
        safeRun("nodeAllInAttack", this::nodeAllInAttack); //added by ikill240c - checked before the smaller opportunistic strategies below so a due all-in wave isn't starved of idle warriors by them claiming first
        safeRun("nodeFavorableAttack", () -> nodeFavorableAttack(t)); //added by ikill240c
        safeRun("nodeClosestPlayerAttack", () -> nodeClosestPlayerAttack(t)); //added by ikill240c
        safeRun("nodeRecallStrandedWarriors", () -> nodeRecallStrandedWarriors(t)); //added by ikill240c
        safeRun("nodeBestMoveAttack", () -> nodeBestMoveAttack(t)); //added by ikill240c
        safeRun("nodeMultiFrontAttack", () -> nodeMultiFrontAttack(t)); //added by ikill240c
        safeRun("nodeCheckAllyOutpost", this::nodeCheckAllyOutpost); //added by ikill240c
        safeRun("nodePlayKingOfTheIslandObjective", () -> nodePlayKingOfTheIslandObjective(t)); //added by ikill240c
        safeRun("nodeEscortGatherers", this::nodeEscortGatherers); //added by ikill240c
        safeRun("nodeAssignIdlePeons", this::nodeAssignIdlePeons); //added by ikill240c
        reclassify(); //added by ikill240c 2026-09-10 15:10
        safeRun("nodeAssignIdleGatherers", this::nodeAssignIdleGatherers); //added by ikill240c 2026-09-10 15:10
        // Was `if (getOwner().hasActiveChieftain())` then unconditionally calling getChieftain() -
        // hasActiveChieftain() now also returns true when only extra (converted) chieftains exist, while
        // getChieftain() only ever returns the single primary slot (null if no primary was trained yet).
        // That combination NPE'd here. Now explicitly checks the primary chieftain is actually non-null
        // before deciding for it; extra chieftains are already handled separately below regardless.
        // //added by ikill240c 2026-09-11
        Unit primary_chieftain = getOwner().getChieftain();
        if (primary_chieftain != null && !primary_chieftain.isDead()) {
            safeRun("ChieftainAI.decide(primary)", () -> getOwner().getRace().getChieftainAI().decide(primary_chieftain)); //added by ikill240c
        }
        // Also let any additional (converted) chieftains make their own magic decisions, not just the
        // primary one - see Player.getExtraChieftains(). //added by ikill240c 2026-09-08 17:00
        for (Unit extra : getOwner().getExtraChieftains()) {
            if (!extra.isDead()) {
                // A converted, cross-race chieftain (see Convert.setMagicRaceOverride()) is the most
                // likely single node in this whole method to hit an unexpected combination of
                // ability/race/template state some downstream code doesn't handle - isolating THIS
                // call specifically, not just the loop as a whole, means one bad extra chieftain
                // can't even stop OTHER extra chieftains in the same loop from getting their turn.
                // //added by ikill240c
                safeRun("ChieftainAI.decide(extra)", () -> getOwner().getRace().getChieftainAI().decide(extra)); //added by ikill240c
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
            // Was a fixed PATROL_RADIUS with no awareness of enemy towers at all - a patrol point
            // could land right inside a nearby enemy tower's range if the enemy happened to build
            // one close to this AI's own base, sending patrol warriors to walk into unnecessary
            // danger on a route that was never meant to be an attack. Pulls the point inward
            // (toward home, along the same angle) in fixed steps until it's clear of any enemy
            // tower's ENEMY_TOWER_DEFENSE_RADIUS, reusing the same countNearbyEnemyTowers() check
            // findTarget() already uses for tower-avoidance elsewhere - "unless they plan on
            // attacking them": this is Action.DEFEND (a patrol/guard order, not an attack), so
            // avoidance is exactly the right default here. Never pulls in past a small minimum
            // radius, so a tower sitting extremely close to home can't collapse every patrol point
            // onto the doorstep. //added by ikill240c
            double radius = PATROL_RADIUS; //added by ikill240c
            int px = (int) (hx + radius * Math.cos(angle)); //added by ikill240c
            int py = (int) (hy + radius * Math.sin(angle)); //added by ikill240c
            double min_radius = PATROL_RADIUS * 0.3; //added by ikill240c
            while (countNearbyEnemyTowers(px, py) > 0 && radius > min_radius) { //added by ikill240c
                radius -= PATROL_RADIUS * 0.15; //added by ikill240c
                px = (int) (hx + radius * Math.cos(angle)); //added by ikill240c
                py = (int) (hy + radius * Math.sin(angle)); //added by ikill240c
            } //added by ikill240c
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

    // Throttle for nodeCheckStuckWarriors below - separate from the main shouldDoAction() AI-decision
    // throttle (which is far too coarse, ~5-7s, for good stuck-detection responsiveness) but still much
    // less wasteful than running every single frame. //added by ikill240c 2026-09-11
    private float stuck_check_interval = 0f;
    private static final float STUCK_CHECK_INTERVAL_SECONDS = 0.5f;

    private void nodeCheckStuckWarriors(float t) {//added by ikill240c
        // Was called every single frame regardless of unit count - iterating the player's entire unit
        // set (including every peon/chieftain that gets filtered out immediately by !isWarrior()) 60+
        // times a second per AI player. Stuck-detection doesn't need frame-perfect timing (the
        // threshold itself is several seconds - see WARRIOR_STUCK_THRESHOLD), so this now only actually
        // scans a few times a second, cutting the iteration rate by roughly 30-60x at typical frame
        // rates with no loss of responsiveness for its purpose. //added by ikill240c 2026-09-11
        stuck_check_interval += t;
        if (stuck_check_interval < STUCK_CHECK_INTERVAL_SECONDS)
            return;
        float elapsed = stuck_check_interval;
        stuck_check_interval = 0f;

        for (Selectable<?> s : getOwner().getUnits().getSet()) {
            if (!(s instanceof Unit unit) || unit.isDead() || unit.isMounted() || !unit.isWarrior())
                continue;
            if (!unit.isMoving())
                continue;
            float[] rec = warrior_positions.get(unit);
            if (rec == null) {
                warrior_positions.put(unit, new float[]{unit.getGridX(), unit.getGridY(), 0f});
                continue;
            }
            float dx = unit.getGridX() - rec[0];
            float dy = unit.getGridY() - rec[1];
            if (dx * dx + dy * dy < 4f) {
                rec[2] += elapsed;
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
                int score = scanForEnemies((Building) q, "quarters"); //added by ikill240c 2026-09-12
                if (score > 0) threats.add(new Threat(defense_target, score, "quarters")); //added by ikill240c 2026-09-12
            }
        }
        // Scan every armory in the player’s roster so multi-armory setups contribute fully to base
        // defense instead of only the first armory being considered for threat response.
        // ikill240c 2026-09-09 17:30
        if (getArmory() != null) {
            for (Selectable<?> a : getArmory()) {
                Building armory = (Building) a;
                if (armory.isDead()) continue;
                int score = scanForEnemies(armory, "armory"); //added by ikill240c 2026-09-12
                if (score > 0) threats.add(new Threat(defense_target, score, "armory")); //added by ikill240c 2026-09-12
            }
        }
        for (int[] zone : occupied_zones) {
            int score = scanForEnemiesAt(zone[0], zone[1], "outpost"); //added by ikill240c 2026-09-12
            if (score > 0) threats.add(new Threat(defense_target, score, "outpost")); //added by ikill240c 2026-09-12
        }
        // Was missing entirely - occupied_zones only ever tracks resource-tower spots (see its own
        // declaration comment), so a plain defensive tower built via nodeBuildTower()/nodeGuardTowers()
        // was never scanned for threats at all, and neither was any location where this AI merely has
        // units stationed without a fixed building there. getTowers() covers every tower this AI owns
        // regardless of why it was built; patrol_units' current positions cover "an occupied zone" in
        // the broader sense the request actually meant - anywhere this AI has units standing watch,
        // not just the specific resource-tower spots occupied_zones tracks. //added by ikill240c
        if (getTowers() != null) { //added by ikill240c
            for (Selectable<?> tower : getTowers()) { //added by ikill240c
                if (tower.isDead()) continue; //added by ikill240c
                int score = scanForEnemies(tower, "tower"); //added by ikill240c
                if (score > 0) threats.add(new Threat(defense_target, score, "tower")); //added by ikill240c
            } //added by ikill240c
        } //added by ikill240c
        patrol_units.removeIf(Unit::isDead); //added by ikill240c
        for (Unit patrol_unit : patrol_units) { //added by ikill240c
            int score = scanForEnemies(patrol_unit, "occupied_zone"); //added by ikill240c
            if (score > 0) threats.add(new Threat(defense_target, score, "occupied_zone")); //added by ikill240c
        } //added by ikill240c
        if (threats.isEmpty())
            return;

        int total_defense_score = 0;
        if (getDefendingUnits() != null) {
            for (Selectable<?> d : getDefendingUnits())
                total_defense_score += getUnitScore((Unit) d);
        }

        List<Threat> scaled = new ArrayList<>();
        for (Threat t : threats)
            // vulnerabilityWeight()/rushDefenseMultiplier() both return exactly 1 when Adaptive AI
            // is off, or when PlayerBehaviorProfile has no learned data yet, so this is a no-op
            // scaling factor for anyone not opted into adaptive AI. //added by ikill240c 2026-09-12
            scaled.add(new Threat(t.target,
                    (int) (DEFENSE_FACTOR[difficulty] * t.score * vulnerabilityWeight(t.kind)
                            * rushDefenseMultiplier()), //added by ikill240c 2026-09-12
                    t.kind)); //added by ikill240c 2026-09-12

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
        int peon_cap = (int) (total_peons * standingGarrisonFraction()); //added by ikill240c 2026-09-13: was the flat MAX_PEON_DEFENSE_FRACTION; standingGarrisonFraction() scales it up for historically-frequent attackers and returns the flat constant unchanged otherwise (see its own comment).
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
                // Was REPRODUCE/BUILD_ARMIES only (Quarters/Armory), so an ally's plain defensive
                // tower being attacked was never noticed at all - matches the same gap just fixed
                // in nodeDefendBase() above, for the same reason (occupied_zones/getTowers() were
                // never part of this check either). Towers are identified by the same
                // NullController + ATTACK-ability convention used elsewhere in this class (e.g.
                // nodeGarrisonAllyTowers()) - the exclusion of REPRODUCE/BUILD_ARMIES below mirrors
                // the game's own tower-classification order so this can't mistake an ally's armory
                // or quarters for a tower. //added by ikill240c
                boolean is_reproduce_or_armory = building.getAbilities().hasAbilities(Abilities.REPRODUCE) //added by ikill240c
                        || building.getAbilities().hasAbilities(Abilities.BUILD_ARMIES); //added by ikill240c
                boolean is_tower = !is_reproduce_or_armory //added by ikill240c
                        && s.getPrimaryController() instanceof com.oddlabs.tt.model.behaviour.NullController //added by ikill240c
                        && building.getAbilities().hasAbilities(Abilities.ATTACK); //added by ikill240c
                if (!is_reproduce_or_armory && !is_tower) //added by ikill240c
                    continue;

                int score = scanForEnemies(building, null); //added by ikill240c 2026-09-12: ally defense isn't the AI's own base, so don't feed it into PlayerBehaviorProfile
                if (score > 0 && defense_target != null) {
                    List<Threat> threats = new ArrayList<>();
                    threats.add(new Threat(defense_target, (int) (DEFENSE_FACTOR[difficulty] * score), null)); //added by ikill240c 2026-09-12
                    // Pass the current defense score so nodeDefendMultiple accounts for units already
                    // defending, preventing repeated over-drafting of peons for ally defense.
                    // //added by ikill240c 2026-09-09 15:05
                    nodeDefendMultiple(threats, currentDefenseScore());
                    return;
                }
            }
        }
    }

    // Below this fraction of the strongest enemy's army strength, an ally is considered
    // struggling enough to warrant help. A ratio rather than a flat unit count, since "struggling"
    // is inherently relative to how dangerous the actual enemy on this map currently is.
    // //added by ikill240c
    private static final float ALLY_STRUGGLING_RATIO = 0.3f; //added by ikill240c
    // Never donate more than this many idle peons per pass, and never let a donation bring this
    // AI's OWN idle peon count below this floor - donating help to an ally shouldn't cripple this
    // player's own economy in the process. //added by ikill240c
    private static final int ALLY_DONATION_MAX_UNITS = 30; //added by ikill240c
    private static final int ALLY_DONATION_MIN_OWN_RESERVE = 15; //added by ikill240c

    // Proactively sends surplus idle peons to a struggling teammate's quarters/armory, which
    // (per Unit.canEnter()'s ally relaxation for those two building types) consumes the donated
    // peon and credits the RECIPIENT's own supply count - the same mechanism a human player would
    // use to manually donate a unit, just triggered by the AI on its teammate's behalf instead of
    // requiring a human ally to notice and do it themselves. //added by ikill240c
    private void nodeDonateToAllies() { //added by ikill240c
        int my_team = getOwner().getPlayerInfo().getTeam(); //added by ikill240c
        if (my_team == PlayerInfo.TEAM_NEUTRAL) //added by ikill240c
            return;

        float strongest_enemy = computeStrongestEnemyStrength(); //added by ikill240c
        if (strongest_enemy <= 0f) //added by ikill240c
            return; // no enemy army on the map yet to measure "struggling" against //added by ikill240c

        Selectable<?>[] idle_peons = getIdlePeons(); //added by ikill240c
        if (idle_peons == null || idle_peons.length <= ALLY_DONATION_MIN_OWN_RESERVE) //added by ikill240c
            return; // nothing to spare right now //added by ikill240c

        for (Player ally : getOwner().getWorld().getPlayers()) { //added by ikill240c
            if (ally == getOwner() || ally.getPlayerInfo().getTeam() != my_team) //added by ikill240c
                continue;
            if (computeArmyStrength(ally) >= strongest_enemy * ALLY_STRUGGLING_RATIO) //added by ikill240c
                continue; // this teammate is holding their own //added by ikill240c

            Building recipient = null; //added by ikill240c
            for (Selectable<?> s : ally.getUnits().getSet()) { //added by ikill240c
                if (s instanceof Building b && !b.isDead() //added by ikill240c
                        && (b.getAbilities().hasAbilities(Abilities.REPRODUCE) //added by ikill240c
                                || b.getAbilities().hasAbilities(Abilities.BUILD_ARMIES))) { //added by ikill240c
                    recipient = b; //added by ikill240c
                    break; //added by ikill240c
                } //added by ikill240c
            } //added by ikill240c
            if (recipient == null) //added by ikill240c
                continue; // this teammate has nowhere to receive a donation right now //added by ikill240c

            int spare = idle_peons.length - ALLY_DONATION_MIN_OWN_RESERVE; //added by ikill240c
            int donate_count = Math.min(ALLY_DONATION_MAX_UNITS, spare); //added by ikill240c
            if (donate_count <= 0) //added by ikill240c
                continue; //added by ikill240c
            getOwner().setTarget(firstN(idle_peons, donate_count), recipient, Action.DEFAULT, false); //added by ikill240c
            return; // one donation per tick is plenty - re-evaluate the rest of the team next pass //added by ikill240c
        } //added by ikill240c
    }

    // Never let sending a warrior to guard an ally's tower drop this AI's own idle warrior count
    // below this floor - same reasoning as ALLY_DONATION_MIN_OWN_RESERVE, just for warriors rather
    // than peons. A tower only ever holds one unit (MountUnitContainer's capacity is fixed at 1 -
    // see its constructor), so there's never a reason to send more than one at a time regardless of
    // how many are spare. //added by ikill240c
    private static final int ALLY_TOWER_GARRISON_MIN_OWN_RESERVE = 15; //added by ikill240c

    // Sends one spare idle warrior to garrison an allied teammate's empty/undermanned tower, the
    // same way a human player could manually send a unit to stand guard in an ally's tower.
    // MountUnitContainer.canEnter() has no ownership check at all (only isSupplyFull() and the
    // unit having Abilities.THROW), so the engine already fully supports this - it just never had
    // any AI logic that actually issued the order. Unlike nodeDonateToAllies() above, this does
    // NOT transfer ownership: MountUnitContainer.enter() calls unit.mount(building) rather than
    // unit.removeNow(), and unlike ReproduceUnitContainer/WorkerUnitContainer it doesn't override
    // increaseSupply() to credit the building owner's population - so the warrior stays this AI's
    // own unit the whole time, just physically standing guard in a teammate's tower instead of
    // this player's own. //added by ikill240c
    private void nodeGarrisonAllyTowers() { //added by ikill240c
        int my_team = getOwner().getPlayerInfo().getTeam(); //added by ikill240c
        if (my_team == PlayerInfo.TEAM_NEUTRAL) //added by ikill240c
            return;

        Selectable<?>[] idle_warriors = getIdleWarriors(); //added by ikill240c
        if (idle_warriors == null || idle_warriors.length <= ALLY_TOWER_GARRISON_MIN_OWN_RESERVE) //added by ikill240c
            return; // nothing to spare right now //added by ikill240c

        for (Player ally : getOwner().getWorld().getPlayers()) { //added by ikill240c
            if (ally == getOwner() || ally.getPlayerInfo().getTeam() != my_team) //added by ikill240c
                continue;

            Building empty_tower = null; //added by ikill240c
            for (Selectable<?> s : ally.getUnits().getSet()) { //added by ikill240c
                // Mirrors Player/AI's own tower-classification order (BUILD_ARMIES and REPRODUCE
                // are checked, and excluded, before ATTACK gets classified as a tower) so this
                // can't accidentally target an ally's armory or quarters instead. //added by ikill240c
                if (s instanceof Building b && !b.isDead() //added by ikill240c
                        && s.getPrimaryController() instanceof NullController //added by ikill240c
                        && b.getAbilities().hasAbilities(Abilities.ATTACK) //added by ikill240c
                        && !b.getAbilities().hasAbilities(Abilities.BUILD_ARMIES) //added by ikill240c
                        && !b.getAbilities().hasAbilities(Abilities.REPRODUCE) //added by ikill240c
                        && !b.getUnitContainer().isSupplyFull()) { //added by ikill240c
                    empty_tower = b; //added by ikill240c
                    break; //added by ikill240c
                } //added by ikill240c
            } //added by ikill240c
            if (empty_tower == null) //added by ikill240c
                continue; // this teammate has no undermanned tower right now //added by ikill240c

            getOwner().setTarget(firstN(idle_warriors, 1), empty_tower, Action.DEFAULT, false); //added by ikill240c
            return; // one garrison assignment per tick is plenty - re-evaluate the rest of the team next pass //added by ikill240c
        } //added by ikill240c
    }

    // How many idle warriors get sent per beacon response - a small, fixed reinforcement rather
    // than the AI's whole army, so a player can call in help repeatedly without stripping the AI's
    // own defense bare with a single beacon. //added by ikill240c
    private static final int BEACON_RESPONSE_WARRIORS = 15; //added by ikill240c
    // Set by PeerHub.receiveBeacon() (via onAllyBeacon()) whenever a teammate places a beacon -
    // non-null means a response is pending; cleared once nodeRespondToBeacon() actually acts on it
    // (or decides there's nothing to send). A plain field rather than a queue: only the most
    // recent beacon matters if several are placed in quick succession, since they're all "come
    // here now" requests to the same standing army. //added by ikill240c
    private float @Nullable [] pending_beacon = null; //added by ikill240c - annotation placement matters here: @Nullable applies to the whole array reference (which CAN be null), not the primitive float elements (which cannot)

    // Called by PeerHub.receiveBeacon() for every AdvancedAI on the same team as whoever placed the
    // beacon - lets a human player call in a fixed reinforcement from their AI teammates by placing
    // a beacon, rather than only being able to watch the AI decide everything on its own.
    // //added by ikill240c
    public void onAllyBeacon(float x, float y) { //added by ikill240c
        pending_beacon = new float[]{x, y}; //added by ikill240c
    }

    private void nodeRespondToBeacon() { //added by ikill240c
        if (pending_beacon == null) //added by ikill240c
            return;
        float x = pending_beacon[0]; //added by ikill240c
        float y = pending_beacon[1]; //added by ikill240c
        pending_beacon = null; // consume it either way - a beacon over unreachable terrain or with no available warriors shouldn't keep re-triggering every tick //added by ikill240c

        Selectable<?>[] idle_warriors = getIdleWarriors(); //added by ikill240c
        if (idle_warriors == null || idle_warriors.length == 0) //added by ikill240c
            return;
        int send_count = Math.min(BEACON_RESPONSE_WARRIORS, idle_warriors.length); //added by ikill240c
        // Action.ATTACK (not MOVE) so the responding warriors fight anything they run into on the
        // way to the beacon, rather than walking past a threat to reach the exact marked spot.
        // //added by ikill240c
        getOwner().setLandscapeTarget(firstN(idle_warriors, send_count), //added by ikill240c
                com.oddlabs.tt.pathfinder.UnitGrid.toGridCoordinate(x), //added by ikill240c
                com.oddlabs.tt.pathfinder.UnitGrid.toGridCoordinate(y), Action.ATTACK, true); //added by ikill240c
    }

    // Shared, cross-AI-instance cache for computeArmyStrength() below - avoids the O(P^2 * U) cost
    // of every AI's every check re-scanning every OTHER player's entire unit set from scratch,
    // every single tick. The main offense gate's own "am I stronger than the strongest enemy"
    // check (favorable, near the top of animate()) runs UNCONDITIONALLY every tick for every AI,
    // and computeStrongestEnemyStrength()/findWeakestEnemyPlayer() both loop over every player
    // computing this fresh each time - with up to 80 players, that was as many as 80*80 = 6400
    // full unit-set scans PER TICK, the primary cause of severe lag reported at high player
    // counts. Refreshed at most once every STRENGTH_CACHE_REFRESH_TICKS - keyed off World.getTick()
    // (a deterministic, shared simulation-tick counter, not wall-clock time), so every peer in a
    // lockstep multiplayer game refreshes at identical simulated moments and computes identical
    // cached values, keeping this safe for network determinism. static/shared across ALL
    // AdvancedAI instances by design: a per-instance cache would still mean every AI recomputing
    // every OTHER player's strength independently, exactly the redundant work this exists to
    // eliminate. A plain HashMap, not a concurrent one - the game loop that calls animate() (and
    // thus this) is single-threaded, so there's no actual concurrent access to guard against.
    // //added by ikill240c
    private static final int STRENGTH_CACHE_REFRESH_TICKS = 30; //added by ikill240c
    private static final java.util.Map<Player, Float> strength_cache = new java.util.HashMap<>(); //added by ikill240c
    private static int last_cache_refresh_tick = Integer.MIN_VALUE; //added by ikill240c
    // Tracks which World the cache above belongs to - this is a STATIC cache, so it would
    // otherwise survive across separate games played in the same JVM session (e.g. one match
    // ending and another starting without restarting the game process). A new World's tick count
    // starts back near zero, so "tick - last_cache_refresh_tick >= STRENGTH_CACHE_REFRESH_TICKS"
    // alone could stay false for a very long time (or the whole match) if the PREVIOUS game had
    // run for many ticks, silently serving stale strength values - or values for Player objects
    // belonging to an already-ended game - instead of ever refreshing. Comparing the World
    // reference itself forces an immediate, unconditional cache reset the moment a new game's
    // AdvancedAI starts calling this, regardless of tick numbering. //added by ikill240c
    private static @Nullable World last_cache_world = null; //added by ikill240c
    // Parallel cache for Player.getStatus() below - a SEPARATE computation from
    // computeArmyStrength() above (sums getStatusValue() over EVERY unit unconditionally, rather
    // than getUnitScore() over only attack-capable ones), so it needed its own cache map, but
    // shares this cache generation's SAME tick/world invalidation via ensureCacheFresh() below
    // rather than duplicating that check a second time. Was called directly (uncached) once per
    // enemy player from chooseFocusEnemy() - which itself runs UNCONDITIONALLY on every single
    // findTarget() call, from several AI strategies (main attack, best-move, multi-front) - so
    // this was a second, independent O(players * units) cost on top of the one
    // computeArmyStrength's cache above already fixed, compounding directly with player count and
    // (via army/economy size) map size - matching reports of lag specifically at high player
    // counts on large maps. getStatus() itself is a base Player method potentially used elsewhere
    // (e.g. UI status display) for an always-fresh value, so it's left untouched; this cache lives
    // entirely on the AdvancedAI/chooseFocusEnemy call path instead. //added by ikill240c
    private static final java.util.Map<Player, Integer> status_cache = new java.util.HashMap<>(); //added by ikill240c

    // Shared invalidation check for BOTH strength_cache and status_cache above - both generations
    // are refreshed together, on the same cadence, since they're both "how strong/active is this
    // player right now" snapshots with no reason to go stale at different times from each other.
    // //added by ikill240c
    private void ensureCacheFresh() { //added by ikill240c
        World world = getOwner().getWorld(); //added by ikill240c
        int tick = world.getTick(); //added by ikill240c
        if (world != last_cache_world || tick - last_cache_refresh_tick >= STRENGTH_CACHE_REFRESH_TICKS) { //added by ikill240c
            strength_cache.clear(); //added by ikill240c
            status_cache.clear(); //added by ikill240c
            last_cache_refresh_tick = tick; //added by ikill240c
            last_cache_world = world; //added by ikill240c
        } //added by ikill240c
    } //added by ikill240c

    private int cachedPlayerStatus(@NonNull Player player) { //added by ikill240c
        ensureCacheFresh(); //added by ikill240c
        Integer cached = status_cache.get(player); //added by ikill240c
        if (cached != null) //added by ikill240c
            return cached; //added by ikill240c
        int status = player.getStatus(); //added by ikill240c
        status_cache.put(player, status); //added by ikill240c
        return status; //added by ikill240c
    } //added by ikill240c

    private float computeArmyStrength(@NonNull Player player) {//added by ikill240c
        ensureCacheFresh(); //added by ikill240c - was the tick/world check duplicated inline here; now shared with status_cache via ensureCacheFresh()
        Float cached = strength_cache.get(player); //added by ikill240c
        if (cached != null) //added by ikill240c
            return cached; //added by ikill240c
        float total = 0f;
        for (Selectable<?> s : player.getUnits().getSet()) {
            if (s instanceof Unit unit && !unit.isDead() && unit.getAbilities().hasAbilities(Abilities.ATTACK)) {
                total += getUnitScore(unit);
            }
        }
        strength_cache.put(player, total); //added by ikill240c
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

    // ---- Shared claim ledger: prevents the strategies below from double-booking the same idle
    // warriors the main attack (or each other) would also claim this tick. Cleared once per tick,
    // at the top of animate(). First-come-first-served, all-or-nothing per claim so no strategy
    // ends up with a partial, under-strength detachment because another one grabbed some of the
    // units it wanted mid-claim. //added by ikill240c
    private final java.util.Set<Unit> committed_this_tick = new java.util.HashSet<>(); //added by ikill240c

    private @NonNull Selectable<?> @NonNull [] claimIdleWarriors(int count) { //added by ikill240c
        Selectable<?>[] idle = getIdleWarriors(); //added by ikill240c
        if (idle == null || count <= 0) return new Selectable<?>[0]; //added by ikill240c
        int tick = getOwner().getWorld().getTick(); //added by ikill240c
        java.util.List<Selectable<?>> available = new java.util.ArrayList<>(); //added by ikill240c
        for (Selectable<?> s : idle) { //added by ikill240c
            // Skip anything a human teammate directly ordered recently (see
            // Selectable.human_override_until_tick's own comment) - otherwise this AI would
            // reclaim a unit for its own purposes the instant a manually-issued order finishes and
            // the unit goes idle again, which is exactly what "the AI fights my commands" looks
            // like from the outside. //added by ikill240c
            if (s.getHumanOverrideUntilTick() > tick) //added by ikill240c
                continue; //added by ikill240c
            if (s instanceof Unit u && !u.isDead() && !committed_this_tick.contains(u)) //added by ikill240c
                available.add(s); //added by ikill240c
            if (available.size() == count) break; //added by ikill240c
        } //added by ikill240c
        if (available.size() < count) return new Selectable<?>[0]; //added by ikill240c
        for (Selectable<?> s : available) committed_this_tick.add((Unit) s); //added by ikill240c
        return available.toArray(new Selectable<?>[0]); //added by ikill240c
    } //added by ikill240c

    // ---- Best-move attack: hits whichever enemy target currently looks weakest/most vulnerable,
    // rather than the nearest one (findTarget()'s job) or the main force's usual objective. Own,
    // smaller detachment via the claim ledger above, own cooldown, so this never competes with the
    // main attack gate or steals its warriors. //added by ikill240c
    private static final int BEST_MOVE_SCAN_RADIUS = 140; //added by ikill240c
    private float best_move_cooldown = 0f; //added by ikill240c

    private int nearbyDefenderCount(@NonNull Selectable<?> candidate) { //added by ikill240c
        // Was `FindOccupantFilter<Selectable<?>> filter = new FindOccupantFilter<>(..., Selectable.class)`
        // - binding FindOccupantFilter's own type parameter (S extends Selectable<?>) directly to
        // the wildcard type Selectable<?> doesn't type-check; Selectable.genericClass() (already
        // used the same way elsewhere, e.g. Convert.java) exists specifically to hand back a
        // properly-parameterized Class token for this exact case, with `var` letting the filter's
        // own type get inferred from it instead of being stated (wrongly) up front.
        // //added by ikill240c
        var filter = new FindOccupantFilter<>(candidate.getGridX(), //added by ikill240c
                candidate.getGridY(), 10, null, Selectable.genericClass()); //added by ikill240c
        getUnitGrid().scan(filter, candidate.getGridX(), candidate.getGridY()); //added by ikill240c
        int count = 0; //added by ikill240c
        for (Selectable<?> s : filter.getResult()) { //added by ikill240c
            if (!s.isDead() && s.getOwner() == candidate.getOwner() //added by ikill240c
                    && s.getAbilities().hasAbilities(Abilities.ATTACK)) //added by ikill240c
                count++; //added by ikill240c
        } //added by ikill240c
        return count; //added by ikill240c
    } //added by ikill240c

    // Selectable<?> itself declares no getHitPoints() - Unit and Building each declare their own,
    // independently, with no shared ancestor method between them - so a target's current HP has to
    // be read via an explicit instanceof check rather than a plain s.getHitPoints() call.
    // //added by ikill240c
    private static int hitPointsOf(@NonNull Selectable<?> s) { //added by ikill240c
        if (s instanceof Unit u) //added by ikill240c
            return u.getHitPoints(); //added by ikill240c
        if (s instanceof Building b) //added by ikill240c
            return b.getHitPoints(); //added by ikill240c
        return 0; // neither - treat as already-dead/worthless rather than guessing //added by ikill240c
    }

    private @Nullable Target findWeakestTarget(int start_x, int start_y) { //added by ikill240c
        var filter = new FindOccupantFilter<>(start_x, start_y, //added by ikill240c
                BEST_MOVE_SCAN_RADIUS, null, Selectable.genericClass()); //added by ikill240c
        getUnitGrid().scan(filter, start_x, start_y); //added by ikill240c
        Target weakest = null; //added by ikill240c
        float best_score = Float.MAX_VALUE; //added by ikill240c
        for (Selectable<?> s : filter.getResult()) { //added by ikill240c
            if (s.isDead() || !getOwner().isEnemy(s.getOwner())) continue; //added by ikill240c
            // Lower score = more attractive: cheap to kill, undefended. //added by ikill240c
            float score = hitPointsOf(s) + (nearbyDefenderCount(s) * 50f); //added by ikill240c
            if (score < best_score) { //added by ikill240c
                best_score = score; //added by ikill240c
                weakest = s; //added by ikill240c
            } //added by ikill240c
        } //added by ikill240c
        return weakest; //added by ikill240c
    } //added by ikill240c

    // Picks a formation based on force size - a form of tactical variation ("counter actions"): a
    // small force moves TIGHT (fast and compact, matching a quick opportunistic raid rather than a
    // deliberate push), a large force (at or above this difficulty's own full target size) forms up
    // DIAMOND (bulkier, tapering at front and back - see FormationLayout's own comment on why this
    // shape suits a bigger mixed force), anything in between uses SQUARE (the existing cohesive
    // default). Was previously only ever set for the main attack and the closest-player attack -
    // every other strategy here left the AI's formation field at whatever it last happened to be,
    // rather than deliberately choosing one for the attack actually being launched.
    // //added by ikill240c
    private @NonNull Formation chooseTacticalFormation(int force_size) { //added by ikill240c
        if (force_size < 8) //added by ikill240c
            return Formation.TIGHT; //added by ikill240c
        if (force_size >= NUM_WARRIORS[difficulty]) //added by ikill240c
            return Formation.DIAMOND; //added by ikill240c
        return Formation.SQUARE; //added by ikill240c
    } //added by ikill240c

    private void nodeBestMoveAttack(float t) { //added by ikill240c
        best_move_cooldown -= t; //added by ikill240c
        if (best_move_cooldown > 0f) return; //added by ikill240c
        best_move_cooldown = 8f; //added by ikill240c

        Selectable<?>[] force = claimIdleWarriors(Math.max(3, NUM_WARRIORS[difficulty] / 3)); //added by ikill240c
        if (force.length == 0) return; //added by ikill240c
        Target weak = findWeakestTarget(force[0].getGridX(), force[0].getGridY()); //added by ikill240c
        if (weak != null) { //added by ikill240c
            getOwner().setFormation(force, chooseTacticalFormation(force.length)); //added by ikill240c - now takes the selection directly, since formation is per-unit rather than global
            getOwner().setLandscapeTarget(force, weak.getGridX(), weak.getGridY(), Action.ATTACK, true); //added by ikill240c
        } //added by ikill240c
    } //added by ikill240c

    // ---- Multi-front attack: splits into two independently-sized, independently-targeted
    // detachments so pressure lands on two different objectives at once, rather than the AI ever
    // only ever mounting one attack at a time. //added by ikill240c
    private float multi_front_cooldown = 0f; //added by ikill240c

    private void nodeMultiFrontAttack(float t) { //added by ikill240c
        multi_front_cooldown -= t; //added by ikill240c
        if (multi_front_cooldown > 0f) return; //added by ikill240c
        multi_front_cooldown = 15f; //added by ikill240c og 20

        int per_front = Math.max(3, NUM_WARRIORS[difficulty] / 4); //added by ikill240c
        Selectable<?>[] front_a = claimIdleWarriors(per_front); //added by ikill240c
        if (front_a.length == 0) return; //added by ikill240c
        Selectable<?>[] front_b = claimIdleWarriors(per_front); //added by ikill240c
        if (front_b.length == 0) return; //added by ikill240c

        Player focus = chooseFocusEnemy(); //added by ikill240c
        if (focus == null) return; //added by ikill240c
        Target target_a = findTarget(front_a[0].getGridX(), front_a[0].getGridY()); //added by ikill240c
        // Deliberately a different target-selection function than front_a, so the two fronts don't
        // just converge back onto the same objective. //added by ikill240c
        Target target_b = findWeakestTarget(front_b[0].getGridX(), front_b[0].getGridY()); //added by ikill240c
        // Each detachment is independently sized but shares the same per_front size. Formation is
        // per-unit now (not a global player setting), so each front's own units need the call
        // made on them directly rather than one shared call covering both. //added by ikill240c
        Formation front_formation = chooseTacticalFormation(per_front); //added by ikill240c
        getOwner().setFormation(front_a, front_formation); //added by ikill240c
        getOwner().setFormation(front_b, front_formation); //added by ikill240c
        if (target_a != null) //added by ikill240c
            getOwner().setLandscapeTarget(front_a, target_a.getGridX(), target_a.getGridY(), Action.ATTACK, true); //added by ikill240c
        if (target_b != null) //added by ikill240c
            getOwner().setLandscapeTarget(front_b, target_b.getGridX(), target_b.getGridY(), Action.ATTACK, true); //added by ikill240c
    } //added by ikill240c

    // ---- Favorable attack: deliberately separate from the main chief-led attack above. That one
    // triggers on this AI's OVERALL army strength vs. the single STRONGEST enemy, on its own
    // stall-driven schedule, and stays untouched. This one runs on its own ongoing cooldown and
    // looks for a narrower, more opportunistic matchup instead: specifically the WEAKEST enemy
    // currently on the map, attacked with a modest dedicated force once this AI can confidently
    // beat that one player alone (a real margin, not just barely ahead) - independent of how the
    // main attack's gate against the strongest enemy happens to be doing right now. //added by ikill240c
    private static final float FAVORABLE_ATTACK_MARGIN = 1.3f; //added by ikill240c
    private float favorable_attack_cooldown = 0f; //added by ikill240c

    private @Nullable Player findWeakestEnemyPlayer() { //added by ikill240c
        Player weakest = null; //added by ikill240c
        float weakest_strength = Float.MAX_VALUE; //added by ikill240c
        for (Player p : getOwner().getWorld().getPlayers()) { //added by ikill240c
            if (!getOwner().isEnemy(p) || p.getUnits().getSet().isEmpty()) //added by ikill240c
                continue;
            float strength = computeArmyStrength(p); //added by ikill240c
            if (strength < weakest_strength) { //added by ikill240c
                weakest_strength = strength; //added by ikill240c
                weakest = p; //added by ikill240c
            } //added by ikill240c
        } //added by ikill240c
        return weakest; //added by ikill240c
    } //added by ikill240c

    private void nodeFavorableAttack(float t) { //added by ikill240c
        favorable_attack_cooldown -= t; //added by ikill240c
        if (favorable_attack_cooldown > 0f) return; //added by ikill240c
        favorable_attack_cooldown = 20f; //added by ikill240c og 15

        Player weakest = findWeakestEnemyPlayer(); //added by ikill240c
        if (weakest == null) return; //added by ikill240c
        if (computeArmyStrength(getOwner()) < computeArmyStrength(weakest) * FAVORABLE_ATTACK_MARGIN) //added by ikill240c
            return; //added by ikill240c

        Selectable<?>[] force = claimIdleWarriors(Math.max(3, NUM_WARRIORS[difficulty] / 3)); //added by ikill240c
        if (force.length == 0) return; //added by ikill240c
        Target target = findNearestOfPlayer(weakest, force[0].getGridX(), force[0].getGridY(), false); //added by ikill240c
        if (target == null) //added by ikill240c
            target = findNearestOfPlayer(weakest, force[0].getGridX(), force[0].getGridY(), true); //added by ikill240c
        if (target != null) { //added by ikill240c
            getOwner().setFormation(force, chooseTacticalFormation(force.length)); //added by ikill240c
            getOwner().setLandscapeTarget(force, target.getGridX(), target.getGridY(), Action.ATTACK, true); //added by ikill240c
        } //added by ikill240c
    } //added by ikill240c

    // ---- Closest-player attack: mimics the main chief-led attack's force-sizing and chieftain
    // inclusion, but targets the geographically NEAREST enemy player instead of comparing overall
    // army strength - a distinct selection criterion from every other strategy here (favorable =
    // weakest enemy, best-move = weakest single target, main attack = beat the strongest enemy).
    // Runs on its own ongoing cooldown, separate from the main attack's favorable/stalled gate.
    // //added by ikill240c
    private float closest_player_attack_cooldown = 0f; //added by ikill240c
    private static final float CLOSEST_PLAYER_ATTACK_COOLDOWN_SECONDS = 10f; //added by ikill240c og 18

    private @Nullable Player findClosestEnemyPlayer() { //added by ikill240c
        Building home = homeBuilding(); //added by ikill240c
        if (home == null) //added by ikill240c
            return null; //added by ikill240c
        int hx = home.getGridX(); //added by ikill240c
        int hy = home.getGridY(); //added by ikill240c
        Player closest = null; //added by ikill240c
        double closest_dist_sqr = Double.MAX_VALUE; //added by ikill240c
        for (Player p : getOwner().getWorld().getPlayers()) { //added by ikill240c
            if (!getOwner().isEnemy(p) || p.getUnits().getSet().isEmpty()) //added by ikill240c
                continue;
            int ex = com.oddlabs.tt.pathfinder.UnitGrid.toGridCoordinate(p.getStartX()); //added by ikill240c
            int ey = com.oddlabs.tt.pathfinder.UnitGrid.toGridCoordinate(p.getStartY()); //added by ikill240c
            double dx = ex - hx; //added by ikill240c
            double dy = ey - hy; //added by ikill240c
            double dist_sqr = dx * dx + dy * dy; //added by ikill240c
            if (dist_sqr < closest_dist_sqr) { //added by ikill240c
                closest_dist_sqr = dist_sqr; //added by ikill240c
                closest = p; //added by ikill240c
            } //added by ikill240c
        } //added by ikill240c
        return closest; //added by ikill240c
    } //added by ikill240c

    private void nodeClosestPlayerAttack(float t) { //added by ikill240c
        closest_player_attack_cooldown -= t; //added by ikill240c
        if (closest_player_attack_cooldown > 0f) return; //added by ikill240c
        closest_player_attack_cooldown = CLOSEST_PLAYER_ATTACK_COOLDOWN_SECONDS; //added by ikill240c

        Player closest = findClosestEnemyPlayer(); //added by ikill240c
        if (closest == null) //added by ikill240c
            return; //added by ikill240c

        // Was reading getIdleWarriors() directly and manually copying the first N entries into a
        // new array - unlike every other attack strategy here, this bypassed claimIdleWarriors()
        // entirely, meaning it neither respected committed_this_tick (so it could double-dispatch
        // warriors ANOTHER strategy already claimed this same tick, silently overwriting that
        // order) nor human_override_until_tick (so it could grab a unit a human teammate just
        // directly commanded, fighting that command exactly like the general "AI fights my
        // commands" issue this was supposed to already be fixed against - it just wasn't fixed
        // HERE specifically). claimIdleWarriors() is all-or-nothing (returns exactly `count`
        // warriors or an empty array), so the old min_force/target_force split collapses into a
        // single claim call. //added by ikill240c
        int target_force = NUM_WARRIORS[difficulty]; //added by ikill240c
        int min_force = Math.max(3, target_force / 2); //added by ikill240c
        Selectable<?>[] warriors = claimIdleWarriors(min_force); //added by ikill240c
        if (warriors.length == 0) //added by ikill240c
            return; //added by ikill240c
        boolean use_chieftain = warriors.length >= NUM_WARRIORS_FOR_CHIEFTAIN[difficulty] //added by ikill240c
                && getOwner().hasActiveChieftain(); //added by ikill240c
        boolean idle_chieftain = getIdleChieftains() != null && getIdleChieftains().length >= 1; //added by ikill240c
        if (idle_chieftain && use_chieftain) { //added by ikill240c
            Selectable<?>[] with_chieftain = Selectable.newArray(warriors.length + 1); //added by ikill240c
            System.arraycopy(warriors, 0, with_chieftain, 0, warriors.length); //added by ikill240c
            with_chieftain[warriors.length] = getIdleChieftains()[0]; //added by ikill240c
            warriors = with_chieftain; //added by ikill240c
        } //added by ikill240c

        Target target = findNearestOfPlayer(closest, warriors[0].getGridX(), warriors[0].getGridY(), false); //added by ikill240c
        if (target == null) //added by ikill240c
            target = findNearestOfPlayer(closest, warriors[0].getGridX(), warriors[0].getGridY(), true); //added by ikill240c
        if (target != null) { //added by ikill240c
            getOwner().setFormation(warriors, Formation.SQUARE); //added by ikill240c
            getOwner().setLandscapeTarget(warriors, target.getGridX(), target.getGridY(), Action.ATTACK, true); //added by ikill240c
        } //added by ikill240c
    } //added by ikill240c

    // ---- Never leave idle warriors stranded outside safe territory: periodically checks every
    // currently-idle warrior's position, and recalls any found further than STRANDED_SAFE_RADIUS
    // grid units from home, an occupied zone, or one of this AI's own towers - e.g. survivors left
    // behind after an attack retreats, or a unit that wandered off. Action.ATTACK (not MOVE) so a
    // stranded warrior fights its way home if something's actually blocking the path, rather than
    // refusing to move past a threat. //added by ikill240c
    private static final int STRANDED_SAFE_RADIUS_SQ = 40 * 40; //added by ikill240c
    private float recall_stranded_cooldown = 0f; //added by ikill240c

    private boolean isNearSafeZone(int gx, int gy) { //added by ikill240c
        Building home = homeBuilding(); //added by ikill240c
        if (home != null) { //added by ikill240c
            int dx = home.getGridX() - gx; //added by ikill240c
            int dy = home.getGridY() - gy; //added by ikill240c
            if (dx * dx + dy * dy <= STRANDED_SAFE_RADIUS_SQ) //added by ikill240c
                return true; //added by ikill240c
        } //added by ikill240c
        for (int[] zone : occupied_zones) { //added by ikill240c
            int dx = zone[0] - gx; //added by ikill240c
            int dy = zone[1] - gy; //added by ikill240c
            if (dx * dx + dy * dy <= STRANDED_SAFE_RADIUS_SQ) //added by ikill240c
                return true; //added by ikill240c
        } //added by ikill240c
        if (getTowers() != null) { //added by ikill240c
            for (Selectable<?> tower : getTowers()) { //added by ikill240c
                if (tower.isDead()) continue; //added by ikill240c
                int dx = tower.getGridX() - gx; //added by ikill240c
                int dy = tower.getGridY() - gy; //added by ikill240c
                if (dx * dx + dy * dy <= STRANDED_SAFE_RADIUS_SQ) //added by ikill240c
                    return true; //added by ikill240c
            } //added by ikill240c
        } //added by ikill240c
        return false; //added by ikill240c
    } //added by ikill240c

    private void nodeRecallStrandedWarriors(float t) { //added by ikill240c
        recall_stranded_cooldown -= t; //added by ikill240c
        if (recall_stranded_cooldown > 0f) return; //added by ikill240c
        recall_stranded_cooldown = 2f; //added by ikill240c - a cheap positional scan; doesn't need per-tick precision og 5

        Building home = homeBuilding(); //added by ikill240c
        if (home == null) return; //added by ikill240c
        Selectable<?>[] idle_warriors = getIdleWarriors(); //added by ikill240c
        if (idle_warriors == null || idle_warriors.length == 0) return; //added by ikill240c

        List<Selectable<?>> stranded = new ArrayList<>(); //added by ikill240c
        for (Selectable<?> w : idle_warriors) { //added by ikill240c
            if (!isNearSafeZone(w.getGridX(), w.getGridY())) //added by ikill240c
                stranded.add(w); //added by ikill240c
        } //added by ikill240c
        if (stranded.isEmpty()) return; //added by ikill240c

        getOwner().setLandscapeTarget(stranded.toArray(new Selectable<?>[0]), home.getGridX(), home.getGridY(), //added by ikill240c
                Action.ATTACK, true); //added by ikill240c
    } //added by ikill240c

    // Sends dedicated garrisons to the capture objective(s) in King of the Island (multiple
    // statues, see KingOfTheIslandModeRules) or King of the Hill (one center point, see
    // KingOfTheHillModeRules) - otherwise this game mode's actual win condition went entirely
    // unplayed by the AI, which just ran its normal economy/attack/defense loop with no awareness
    // the objective existed.
    //
    // Was a one-shot: send a garrison only when not currently holding, then stop (an early return
    // for "already holding"). setLandscapeTarget()/Action.ATTACK is a one-time move-and-fight
    // order with no persistent "stay here" memory - once a garrison arrived with nothing left to
    // fight, those units became idle again on a later tick and were free for
    // claimIdleWarriors()/getIdleWarriors() to hand to a completely different node (an attack wave,
    // base defense, whatever asked first), silently draining the garrison over time with nothing
    // re-sent to replace it. This is very likely why the objective still wasn't being played in
    // practice despite this node already existing and running every tick.
    //
    // Fixed by dropping the "already holding, do nothing" early return entirely and instead
    // topping up periodically regardless of current status - every check interval, claim whatever
    // idle warriors are actually available (however few) and send them to whichever objective
    // point(s) this AI's team doesn't currently control, continuously replacing whatever the
    // garrison loses to drift/reclaiming/combat rather than sending exactly once and hoping it
    // holds. //added by ikill240c
    private static final int KOTH_GARRISON_SIZE = 15; //added by ikill240c og 6
    private static final int KOTI_GARRISON_PER_STATUE = 8; //added by ikill240c - smaller per-statue share since King of the Island needs force spread across several points at once, not concentrated on one
    private float koth_check_cooldown = 0f; //added by ikill240c

    private void nodePlayKingOfTheIslandObjective(float t) { //added by ikill240c
        koth_check_cooldown -= t; //added by ikill240c
        if (koth_check_cooldown > 0f) return; //added by ikill240c
        koth_check_cooldown = 8f; // frequent enough to react to being contested, not spammy //added by ikill240c og 10

        GameMode mode = getOwner().getWorld().getGameMode(); //added by ikill240c
        if (mode != GameMode.KING_OF_THE_ISLAND && mode != GameMode.KING_OF_THE_HILL) //added by ikill240c
            return; // not one of these game modes - nothing to play //added by ikill240c

        int my_team = getOwner().getPlayerInfo().getTeam(); //added by ikill240c
        if (my_team == PlayerInfo.TEAM_NEUTRAL) //added by ikill240c
            return; // no team to hold the point(s) on behalf of //added by ikill240c

        if (mode == GameMode.KING_OF_THE_ISLAND) { //added by ikill240c
            GameModeRules rules = GameModeRegistry.get(GameMode.KING_OF_THE_ISLAND); //added by ikill240c
            if (!(rules instanceof KingOfTheIslandModeRules koti)) //added by ikill240c
                return; // defensive - shouldn't happen given the mode check above //added by ikill240c
            if (koti.getWinningTeam() != PlayerInfo.TEAM_NEUTRAL) //added by ikill240c
                return; // match already decided, nothing left to contest //added by ikill240c

            // Reinforce every statue this team doesn't currently control, one small garrison send
            // per check cycle per uncontrolled statue - spread across all of them rather than
            // piling everyone onto just one, since the win condition needs most statues held at
            // once, not a single one. //added by ikill240c
            for (int i = 0; i < koti.getStatueCount(); i++) { //added by ikill240c
                if (koti.getStatueControllingTeam(i) == my_team) //added by ikill240c
                    continue; // already ours - no need to send more right now //added by ikill240c
                Selectable<?>[] force = claimIdleWarriors(KOTI_GARRISON_PER_STATUE); //added by ikill240c
                if (force.length == 0) //added by ikill240c
                    break; // no idle warriors left to send anywhere this cycle //added by ikill240c
                int grid_x = UnitGrid.toGridCoordinate(koti.getStatueX(i)); //added by ikill240c
                int grid_y = UnitGrid.toGridCoordinate(koti.getStatueY(i)); //added by ikill240c
                getOwner().setLandscapeTarget(force, grid_x, grid_y, Action.ATTACK, true); //added by ikill240c
            } //added by ikill240c
        } else { //added by ikill240c - GameMode.KING_OF_THE_HILL
            GameModeRules rules = GameModeRegistry.get(GameMode.KING_OF_THE_HILL); //added by ikill240c
            if (!(rules instanceof KingOfTheHillModeRules koth)) //added by ikill240c
                return; // defensive - shouldn't happen given the mode check above //added by ikill240c
            if (koth.getWinningTeam() != PlayerInfo.TEAM_NEUTRAL) //added by ikill240c
                return; // match already decided, nothing left to contest //added by ikill240c

            Selectable<?>[] force = claimIdleWarriors(KOTH_GARRISON_SIZE); //added by ikill240c
            if (force.length == 0) //added by ikill240c
                return; //added by ikill240c

            int grid_x = UnitGrid.toGridCoordinate(koth.getCenterX()); //added by ikill240c
            int grid_y = UnitGrid.toGridCoordinate(koth.getCenterY()); //added by ikill240c
            getOwner().setLandscapeTarget(force, grid_x, grid_y, Action.ATTACK, true); //added by ikill240c
        } //added by ikill240c
    } //added by ikill240c

    // ---- All-in attack: every couple main waves, throw the ENTIRE currently-idle warrior pool at
    // once, rather than always trickling out standard-sized forces. main_attack_wave_count is
    // incremented at the main attack's own trigger point (the favorable||stalled gate above) -
    // that gate itself is untouched other than this one counter increment. //added by ikill240c
    private static final int WAVES_PER_ALL_IN = 2; //added by ikill240c og 3
    private int main_attack_wave_count = 0; //added by ikill240c

    private void nodeAllInAttack() { //added by ikill240c
        if (main_attack_wave_count < WAVES_PER_ALL_IN) return; //added by ikill240c
        Selectable<?>[] idle = getIdleWarriors(); //added by ikill240c
        if (idle == null || idle.length == 0) return; //added by ikill240c
        Selectable<?>[] everyone = claimIdleWarriors(idle.length); //added by ikill240c
        if (everyone.length == 0) return; //added by ikill240c
        main_attack_wave_count = 0; //added by ikill240c
        Target target = findTarget(everyone[0].getGridX(), everyone[0].getGridY()); //added by ikill240c
        if (target != null) { //added by ikill240c
            getOwner().setFormation(everyone, chooseTacticalFormation(everyone.length)); //added by ikill240c
            getOwner().setLandscapeTarget(everyone, target.getGridX(), target.getGridY(), Action.ATTACK, true); //added by ikill240c
        } //added by ikill240c
    } //added by ikill240c

    // ---- Ally outpost: builds a defensive outpost near an allied player's base once this AI's own
    // economy is well-established, distinct from nodeCheckOutpost()'s resource-driven outpost
    // (that one triggers on gatherers walking too far from home; this one is purely about
    // supporting a teammate). //added by ikill240c
    private boolean ally_outpost_built = false; //added by ikill240c

    private @Nullable Player findAlly() { //added by ikill240c
        Player owner = getOwner(); //added by ikill240c
        for (Player p : owner.getWorld().getPlayers()) { //added by ikill240c
            if (p != owner && !owner.isEnemy(p) //added by ikill240c
                    && p.getPlayerInfo().getTeam() != PlayerInfo.TEAM_NEUTRAL //added by ikill240c
                    && p.getPlayerInfo().getTeam() == owner.getPlayerInfo().getTeam()) //added by ikill240c
                return p; //added by ikill240c
        } //added by ikill240c
        return null; //added by ikill240c
    } //added by ikill240c

    private void nodeCheckAllyOutpost() { //added by ikill240c
        if (ally_outpost_built) return; //added by ikill240c
        Player ally = findAlly(); //added by ikill240c
        if (ally == null) return; //added by ikill240c
        // "When convenient": only once our own economy is established, so this never competes with
        // our own base's build queue for the same peons. //added by ikill240c
        if (getArmory() == null || getQuarters() == null) return; //added by ikill240c
        Selectable<?>[] builders = getPeons(BUILDERS_PER_TOWER_TARGET); //added by ikill240c
        if (builders.length < BUILDERS_PER_TOWER_TARGET) return; //added by ikill240c

        int ax = com.oddlabs.tt.pathfinder.UnitGrid.toGridCoordinate(ally.getStartX()); //added by ikill240c
        int ay = com.oddlabs.tt.pathfinder.UnitGrid.toGridCoordinate(ally.getStartY()); //added by ikill240c
        if (buildBuilding(Race.BUILDING_TOWER, builders, ax, ay, 30)) { //added by ikill240c
            ally_outpost_built = true; //added by ikill240c
        } //added by ikill240c
    } //added by ikill240c

    // Adaptive AI's live rebalancer. Reuses the same computeArmyStrength()/
    // computeStrongestEnemyStrength() helpers the attack-favorability gate already relies on, so
    // "how the match is going" means the same thing in both places. Checked on a slow cadence
    // (ADAPTIVE_CHECK_INTERVAL_SECONDS) and moves at most one difficulty tier per check - a hard
    // snap to whatever tier the instantaneous ratio suggests would visibly whiplash the AI's
    // behaviour (e.g. its warrior/tower targets) from one moment to the next.
    //
    // This is a rule-based rubber-bander, not a trained model - see the class comment on
    // AdaptiveAIProfile for why "adaptive" doesn't mean literal machine learning here.
    // //added by ikill240c 2026-09-12
    private static final float ADAPTIVE_CHECK_INTERVAL_SECONDS = 10f; //added by ikill240c 2026-09-12 og 30
    // If one side's army score is more than this many times the other's, nudge difficulty toward
    // the underdog. Symmetric: used both for "AI is dominating, ease off" and "AI is getting
    // crushed, toughen up". //added by ikill240c 2026-09-12
    private static final float ADAPTIVE_REBALANCE_RATIO = 1.6f; //added by ikill240c 2026-09-12
    private float time_since_adaptive_check = 0f; //added by ikill240c 2026-09-12
    // Advances by 1 on every nodeDeployUnitsInArmory() call, mod the current armory count, so which
    // armory gets first crack at a (usually small) deployment request rotates instead of always
    // being whichever armory getArmory() happens to list first - see that method's own comment.
    // Deliberately NOT reset when armories are built/destroyed; it's just a rotating offset, so
    // drifting relative to the current armory count when one dies/gets added is harmless - the next
    // call's modulo just picks it back up cleanly. //added by ikill240c 2026-09-14
    private int armory_deploy_rotation = 0; //added by ikill240c 2026-09-14
    // Elapsed match time in seconds - used both by nodeAdaptiveDifficulty's cadence check above and
    // by rushDefenseMultiplier()/recordPlayerAttackObservation() below for PlayerBehaviorProfile
    // learning. Kept as one shared clock rather than duplicating a timer per feature.
    // //added by ikill240c 2026-09-12
    private float match_elapsed_seconds = 0f; //added by ikill240c 2026-09-12

    private void nodeAdaptiveDifficulty(float t) { //added by ikill240c 2026-09-12
        if (!adaptive) //added by ikill240c 2026-09-12
            return; //added by ikill240c 2026-09-12
        time_since_adaptive_check += t; //added by ikill240c 2026-09-12
        if (time_since_adaptive_check < ADAPTIVE_CHECK_INTERVAL_SECONDS) //added by ikill240c 2026-09-12
            return; //added by ikill240c 2026-09-12
        time_since_adaptive_check = 0f; //added by ikill240c 2026-09-12

        float own = computeArmyStrength(getOwner()); //added by ikill240c 2026-09-12
        float enemy = computeStrongestEnemyStrength(); //added by ikill240c 2026-09-12
        if (own <= 0f && enemy <= 0f) //added by ikill240c 2026-09-12
            return; // too early in the match to judge anything yet //added by ikill240c 2026-09-12

        if (own > enemy * ADAPTIVE_REBALANCE_RATIO && difficulty > DIFFICULTY_EASY) { //added by ikill240c 2026-09-12
            difficulty--; // AI is dominating - ease off so the match stays competitive //added by ikill240c 2026-09-12
        } else if (enemy > own * ADAPTIVE_REBALANCE_RATIO && difficulty < DIFFICULTY_INSANE) { //added by ikill240c 2026-09-14: ceiling was DIFFICULTY_HARD, written before the Insane tier existed - an adaptive AI getting genuinely crushed should be able to escalate all the way, not cap out one tier short of the hardest the game now offers
            difficulty++; // AI is getting crushed - toughen up to keep up //added by ikill240c 2026-09-12
        } //added by ikill240c 2026-09-12
    } //added by ikill240c 2026-09-12

    // Stage 1 of play-style-aware AI: keep PlayerStyleProfile fed with live data about the human
    // opponent so it has something to read from by the time later stages (a contextual bandit
    // choosing between behavior profiles, eventually a trained model) need it. Deliberately
    // independent of nodeAdaptiveDifficulty() above - that rebalances overall strength/difficulty,
    // this tracks a different axis (how the opponent plays, not how well). A profile only makes
    // sense for a human, so this skips AI opponents; in a game with multiple humans it profiles
    // whichever is found first, which is an acceptable v1 limitation (see PlayerStyleProfile's own
    // note on single-profile-per-installation scope). //added by ikill240c 2026-09-12
    private void nodeUpdatePlayerStyle(float t) { //added by ikill240c 2026-09-12
        for (Player p : getOwner().getWorld().getPlayers()) { //added by ikill240c 2026-09-12
            if (getOwner().isEnemy(p) && p.getAI() == null) { //added by ikill240c 2026-09-12
                PlayerStyleProfile.get().update(p, t); //added by ikill240c 2026-09-12
                return; //added by ikill240c 2026-09-12
            } //added by ikill240c 2026-09-12
        } //added by ikill240c 2026-09-12
    } //added by ikill240c 2026-09-12

    // How long to let a big idle army sit before attacking anyway (see the "stalled" check below),
    // adjusted by how the human opponent has been playing. Against a highly aggressive/rushy
    // opponent, waiting the full stall window can let them dictate the pace of the whole match, so
    // shrink the wait (down to half); against a heavily economic/turtling opponent there's less
    // urgency to attack into their defenses before an army is truly ready, so stretch it out (up to
    // 1.5x). Neutral/no-history reads (aggression == 0.5) leave the constant unchanged.
    // //added by ikill240c 2026-09-12
    private float getStyleAdjustedStallSeconds() { //added by ikill240c 2026-09-12
        float aggression = PlayerStyleProfile.get().getAggression(); //added by ikill240c 2026-09-12
        // Maps aggression in [0, 1] to a multiplier in [0.5, 1.5] around the neutral 0.5 reading.
        // //added by ikill240c 2026-09-12
        float multiplier = 1.5f - aggression; //added by ikill240c 2026-09-12
        // Layer Stage 2's bandit-chosen posture on top of Stage 1's style-based adjustment above,
        // rather than replacing it - the posture is "how should I generally play given the bucket
        // this opponent falls in", while the style adjustment is "fine-tune within that given how
        // aggressive they've specifically been this match". Non-adaptive AIs have no selected
        // behavior, so their multiplier is an explicit no-op (1.0). //added by ikill240c 2026-09-12
        if (selected_behavior != null) { //added by ikill240c 2026-09-12
            multiplier *= selected_behavior.getStallMultiplier(); //added by ikill240c 2026-09-12
        } //added by ikill240c 2026-09-12
        return ATTACK_STALL_SECONDS * multiplier; //added by ikill240c 2026-09-12
    } //added by ikill240c 2026-09-12

    // Companion to getStyleAdjustedStallSeconds() for the other half of the "stalled" check - how
    // large an idle army must be before attacking anyway. Only the bandit-chosen posture affects
    // this one (there's no clean per-match-aggression signal to scale it by the way stall seconds
    // are, so Stage 1 alone leaves it unchanged). //added by ikill240c 2026-09-12
    private int getEffectiveMinWarriorsForStallAttack() { //added by ikill240c 2026-09-12
        if (selected_behavior == null) //added by ikill240c 2026-09-12
            return MIN_WARRIORS_FOR_STALL_ATTACK; //added by ikill240c 2026-09-12
        return Math.max(1, Math.round(MIN_WARRIORS_FOR_STALL_ATTACK * selected_behavior.getMinWarriorsMultiplier())); //added by ikill240c 2026-09-12
    } //added by ikill240c 2026-09-12

    // Called once at game-over, only for an adaptive AI (see recordAdaptiveAiResult() in
    // GameOverTrigger, which finds the same instance recordMatchResult() already uses).
    // ai_won is from this AI's own perspective (the inverse of GameOverTrigger's human-perspective
    // player_won). No-op for non-adaptive AIs, which never selected a context/arm to attribute a
    // reward to. //added by ikill240c 2026-09-12
    public void recordBanditOutcome(boolean ai_won) { //added by ikill240c 2026-09-12
        if (selected_context != null && selected_behavior != null) { //added by ikill240c 2026-09-12
            AiBehaviorBandit.get().recordOutcome(selected_context, selected_behavior, ai_won ? 1f : 0f); //added by ikill240c 2026-09-12
        } //added by ikill240c 2026-09-12
    } //added by ikill240c 2026-09-12

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

    // Small (unit, score) pair so findBestClusterTarget can report both without a second scan.
    // //added by ikill240c 2026-09-11
    private static final class ClusterResult {
        final Unit unit;
        final float score;
        ClusterResult(Unit unit, float score) { this.unit = unit; this.score = score; }
    }

    private static long cellKey(int cx, int cy) {//added by ikill240c 2026-09-11
        return ((long) cx << 32) ^ (cy & 0xFFFFFFFFL);
    }

    // Was O(n^2): every enemy unit on this player checked against every other unit on the SAME player,
    // every single call - with hundreds of units that is tens of thousands of distance checks per enemy
    // player, every AI tick (this is called from nodeChiefHitAndRun, which runs on the throttled AI
    // cycle for every AI player in the match). Replaced with spatial bucketing: units are grouped into
    // a coarse grid whose cell size matches the clustering radius, so each candidate only checks its own
    // cell plus its 8 neighbors instead of the whole army - roughly O(n) for any reasonably spread-out
    // army instead of O(n^2), with identical results (same 20-unit clustering radius, same scoring).
    // //added by ikill240c 2026-09-11
    private @Nullable ClusterResult findBestClusterTarget(@NonNull Player p) {
        final int CELL_SIZE = 20; // matches the clustering radius (400 = 20*20) below
        java.util.Map<Long, List<Unit>> buckets = new java.util.HashMap<>();
        List<Unit> alive_units = new ArrayList<>();
        for (Selectable<?> s : p.getUnits().getSet()) {
            if (s instanceof Unit u && !u.isDead()) {
                alive_units.add(u);
                long key = cellKey(u.getGridX() / CELL_SIZE, u.getGridY() / CELL_SIZE);
                buckets.computeIfAbsent(key, k -> new ArrayList<>()).add(u);
            }
        }

        Unit best = null;
        float best_score = 0f;
        for (Unit candidate : alive_units) {
            int cx = candidate.getGridX() / CELL_SIZE;
            int cy = candidate.getGridY() / CELL_SIZE;
            float score = 0f;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    List<Unit> bucket = buckets.get(cellKey(cx + dx, cy + dy));
                    if (bucket == null) continue;
                    for (Unit other : bucket) {
                        int ddx = other.getGridX() - candidate.getGridX();
                        int ddy = other.getGridY() - candidate.getGridY();
                        if (ddx * ddx + ddy * ddy < 400) // ~20 grid units clustering radius
                            score += getUnitScore(other);
                    }
                }
            }
            if (score > best_score) {
                best_score = score;
                best = candidate;
            }
        }
        return best != null ? new ClusterResult(best, best_score) : null;
    }

    // Picks any usable chieftain-ability unit belonging to this AI - the primary chieftain if it's
    // alive, otherwise the first alive extra (converted) chieftain, otherwise null. Needed because
    // getOwner().getChieftain() only ever returns the primary slot, which can be null while extra
    // chieftains still exist (see hasActiveChieftain()'s OR condition) - both nodeChiefHitAndRun()
    // and nodeChiefHitAndRunReturn() need a chieftain to act on regardless of which slot it's in.
    // //added by ikill240c 2026-09-11
    private @Nullable Unit getUsableChieftain() { //added by ikill240c 2026-09-11
        Unit primary = getOwner().getChieftain(); //added by ikill240c 2026-09-11
        if (primary != null && !primary.isDead()) //added by ikill240c 2026-09-11
            return primary; //added by ikill240c 2026-09-11
        for (Unit extra : getOwner().getExtraChieftains()) { //added by ikill240c 2026-09-11
            if (!extra.isDead()) //added by ikill240c 2026-09-11
                return extra; //added by ikill240c 2026-09-11
        } //added by ikill240c 2026-09-11
        return null; //added by ikill240c 2026-09-11
    } //added by ikill240c 2026-09-11

    private void nodeChiefHitAndRun() {//added by ikill240c
        // Was `if (!getOwner().hasActiveChieftain() || chief_hitrun_active) return;` followed by an
        // unconditional `getOwner().getChieftain()` - hasActiveChieftain() can be true from extra
        // chieftains alone while the primary slot is still null, which NPE'd on chief.isDead() below.
        // Now considers EVERY idle chieftain (primary or extra) that qualifies, rather than picking
        // just one via getUsableChieftain() - previously only a single chieftain could ever be
        // raiding at a time, so a player with several idle chieftains had all but one sit unused at
        // home indefinitely. //added by ikill240c
        Selectable<?>[] idle_chiefs = getIdleChieftains(); //added by ikill240c
        if (idle_chiefs == null) //added by ikill240c
            return;

        List<Unit> ready = new ArrayList<>(); //added by ikill240c
        for (Selectable<?> s : idle_chiefs) { //added by ikill240c
            if (!(s instanceof Unit chief) || chief.isMounted() || chief_hitrun_units.contains(chief)) //added by ikill240c
                continue; //added by ikill240c
            // Guarantee a raid whenever the chief is fully healed and idle at base, instead of just
            // standing around - the old check only allowed an *opportunistic* raid above 70% health,
            // so a fully-healed chief with no other trigger could sit idle indefinitely. Full health
            // (>=100%) now always qualifies; the >70% threshold remains as the opportunistic lower
            // bound. //added by ikill240c 2026-09-08 17:15
            boolean fully_healed = chief.getHitPoints() >= chief.getTemplate().getMaxHitPoints();
            boolean opportunistic = chief.getHitPoints() > CHIEF_HITRUN_HEALTH_PCT * chief.getTemplate().getMaxHitPoints();
            if (fully_healed || opportunistic) //added by ikill240c
                ready.add(chief); //added by ikill240c
        } //added by ikill240c
        if (ready.isEmpty()) //added by ikill240c
            return;

        // More than GROUP_HITRUN_THRESHOLD ready chieftains group into one coordinated raid on a
        // single shared target, rather than each independently picking its own (possibly
        // different) target and escort - a handful of chieftains raiding all over the map
        // separately is a very different, much less deliberate use of a large chieftain roster
        // than throwing them at one target together. At or below the threshold, each raids
        // independently exactly as a single chieftain always has. //added by ikill240c
        if (ready.size() > GROUP_HITRUN_THRESHOLD) { //added by ikill240c
            launchHitAndRun(ready); //added by ikill240c
        } else { //added by ikill240c
            for (Unit chief : ready) { //added by ikill240c
                launchHitAndRun(List.of(chief)); //added by ikill240c
            } //added by ikill240c
        } //added by ikill240c
    }

    // Shared launch logic for both a single-chieftain raid and a grouped one - raiders.size() == 1
    // for an individual raid, > GROUP_HITRUN_THRESHOLD for a group raid. The escort/target search
    // below uses the FIRST raider as the anchor point (idle chieftains are typically clustered
    // together at home anyway, so this is representative for the group case too) and excludes
    // every raider itself from the "nearby own units" escort-limit count. //added by ikill240c
    private void launchHitAndRun(@NonNull List<Unit> raiders) { //added by ikill240c
        Unit anchor = raiders.get(0); //added by ikill240c
        int nearby_own = 0;
        for (Selectable<?> s : getOwner().getUnits().getSet()) {
            if (s instanceof Unit u && !u.isDead() && !raiders.contains(u)) { //added by ikill240c
                int dx = u.getGridX() - anchor.getGridX(); //added by ikill240c
                int dy = u.getGridY() - anchor.getGridY(); //added by ikill240c
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
            ClusterResult result = findBestClusterTarget(p);
            if (result != null && result.score > best_score) {
                best_score = result.score;
                best_target = result.unit;
            }
        }
        if (best_target == null)//added by ikill240c
            return;

        // Bring a random-sized escort of currently idle warriors along instead of sending the
        // chief(s) completely alone - 0 escorts is still possible (e.g. no warriors built yet), but
        // whenever warriors are available a random subset joins the raid. //added by ikill240c 2026-09-08 17:15
        Selectable<?>[] idle_warriors = getIdleWarriors();
        List<Selectable<?>> raid_party = new ArrayList<>();
        raid_party.addAll(raiders); //added by ikill240c
        if (idle_warriors != null && idle_warriors.length > 0) {
            int escort_count = getOwner().getWorld().getRandom().nextInt(idle_warriors.length + 1);
            for (int i = 0; i < escort_count; i++) {
                raid_party.add(idle_warriors[i]);
            }
        }
        Selectable<?>[] raid_array = raid_party.toArray(Selectable.newArray(raid_party.size()));

        chief_hitrun_units.addAll(raiders); //added by ikill240c
        getOwner().setLandscapeTarget(raid_array, best_target.getGridX(), best_target.getGridY(),
                Action.ATTACK, true);
        // Cast the first available COMBAT magic (stun/blast for Vikings, lightning/poison for
        // Natives) - i.e. indices 0 and 1 only. Convert (index 2) is intentionally skipped here
        // because it has a long 90-second cooldown and must be aimed at nearby enemy units to be
        // useful; casting it at the chieftain's current position (which is often far from the
        // target) wastes the cooldown and prevents ChieftainAI.nodeConvert() from ever firing it
        // when enemies are actually nearby. nodeConvert handles convert casting separately.
        // //added by ikill240c 2026-09-09 15:05
        for (Unit chief : raiders) { //added by ikill240c
            for (int i = 0; i < RacesResources.INDEX_MAGIC_CONVERT; i++) {
                if (chief.canDoMagic(i)) {
                    chief.doMagic(i, false);
                    break;
                }
            }
        } //added by ikill240c
    }

    // Returns the nearest living enemy tower within ENEMY_TOWER_DEFENSE_RADIUS of a point, or null
    // if none - unlike countNearbyEnemyTowers() (which only counts them), this is used where the
    // actual tower's position is needed to compute a direction to move away from it.
    // //added by ikill240c
    private @Nullable Building findNearestEnemyTower(int grid_x, int grid_y) { //added by ikill240c
        float world_x = com.oddlabs.tt.pathfinder.UnitGrid.coordinateFromGrid(grid_x); //added by ikill240c
        float world_y = com.oddlabs.tt.pathfinder.UnitGrid.coordinateFromGrid(grid_y); //added by ikill240c
        FindOccupantFilter<Building> filter = new FindOccupantFilter<>(world_x, world_y, //added by ikill240c
                ENEMY_TOWER_DEFENSE_RADIUS, null, Building.class); //added by ikill240c
        getUnitGrid().scan(filter, grid_x, grid_y); //added by ikill240c
        Building nearest = null; //added by ikill240c
        int nearest_dist_sqr = Integer.MAX_VALUE; //added by ikill240c
        for (Building building : filter.getResult()) { //added by ikill240c
            if (building.isDead() || !isTowerTemplate(building) || !getOwner().isEnemy(building.getOwner())) //added by ikill240c
                continue; //added by ikill240c
            int dx = building.getGridX() - grid_x; //added by ikill240c
            int dy = building.getGridY() - grid_y; //added by ikill240c
            int dist_sqr = dx * dx + dy * dy; //added by ikill240c
            if (dist_sqr < nearest_dist_sqr) { //added by ikill240c
                nearest_dist_sqr = dist_sqr; //added by ikill240c
                nearest = building; //added by ikill240c
            } //added by ikill240c
        } //added by ikill240c
        return nearest; //added by ikill240c
    } //added by ikill240c

    private void nodeChiefHitAndRunReturn() {//added by ikill240c
        if (chief_hitrun_units.isEmpty()) //added by ikill240c
            return;
        // Iterates a copy since chiefs get removed from chief_hitrun_units mid-loop below -
        // mutating a Set while iterating it directly would throw ConcurrentModificationException.
        // //added by ikill240c
        for (Unit chief : new java.util.ArrayList<>(chief_hitrun_units)) { //added by ikill240c
            if (chief.isDead()) { //added by ikill240c
                chief_hitrun_units.remove(chief); //added by ikill240c
                continue; //added by ikill240c
            }
            if (chief.getHitPoints() <= 0.4f * chief.getTemplate().getMaxHitPoints()
                    || (getIdleChieftains() != null && java.util.Arrays.asList(getIdleChieftains()).contains(chief))) {
                Building home = homeBuilding();
                if (home != null) {
                    // Do not send the chieftain back to the same tile again; that causes a zero-length
                    // movement request during hit-and-run return. //added by ikill240c 2026-09-09 20:00
                    if (chief.getGridX() != home.getGridX() || chief.getGridY() != home.getGridY()) {
                        // A wounded, retreating chieftain is often deep in enemy territory - if an
                        // enemy tower sits nearby right now, step directly away from it first
                        // (queued as a waypoint via queueLandscapeTarget, Action.MOVE - not an
                        // attack, since retreating shouldn't pick a fight) before continuing on to
                        // home, rather than potentially walking straight back through the tower's
                        // range on the direct path there. "Unless they plan on attacking them" -
                        // this chieftain is retreating, so avoidance is the right default.
                        // //added by ikill240c
                        Building nearby_tower = findNearestEnemyTower(chief.getGridX(), chief.getGridY()); //added by ikill240c
                        if (nearby_tower != null) { //added by ikill240c
                            int away_dx = chief.getGridX() - nearby_tower.getGridX(); //added by ikill240c
                            int away_dy = chief.getGridY() - nearby_tower.getGridY(); //added by ikill240c
                            double len = Math.sqrt(away_dx * away_dx + away_dy * away_dy); //added by ikill240c
                            if (len < 0.001) { //added by ikill240c - directly on top of the tower (shouldn't happen, but avoid a divide-by-zero direction)
                                away_dx = 1; //added by ikill240c
                                len = 1; //added by ikill240c
                            } //added by ikill240c
                            int step_x = chief.getGridX() + (int) (away_dx / len * 20); //added by ikill240c
                            int step_y = chief.getGridY() + (int) (away_dy / len * 20); //added by ikill240c
                            getOwner().setLandscapeTarget(Selectable.newArray(chief), step_x, step_y, Action.MOVE, false); //added by ikill240c
                            getOwner().queueLandscapeTarget(Selectable.newArray(chief), home.getGridX(), //added by ikill240c
                                    home.getGridY(), Action.MOVE, false); //added by ikill240c
                        } else { //added by ikill240c
                            //added by ikill240c 2026-09-09 17:30 - Return the chieftain to home coordinates so hit-and-run units do not get trapped in the armory/quarters.
                            getOwner().setLandscapeTarget(Selectable.newArray(chief), home.getGridX(),
                                    home.getGridY(), Action.MOVE, false);
                        } //added by ikill240c
                    }
                }
                chief_hitrun_units.remove(chief); //added by ikill240c
            }
        } //added by ikill240c
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

    private int scanForEnemies(@NonNull Selectable<?> src, @Nullable String kind) { //added by ikill240c 2026-09-12: kind param
        FindOccupantFilter<Unit> filter = new FindOccupantFilter<>(src.getPositionX(), src.getPositionY(),
                DEFENSE_SCAN_RADIUS, src, Unit.class);
        getUnitGrid().scan(filter, src.getGridX(), src.getGridY());
        int score = 0;
        int human_unit_count = 0; //added by ikill240c 2026-09-13
        int human_score = 0; //added by ikill240c 2026-09-13
        Player human_attacker = null; //added by ikill240c 2026-09-13
        defense_target = null;
        for (Unit unit : filter.getResult()) {
            if (!unit.isDead() && getOwner().isEnemy(unit.getOwner())) {
                int unit_score = getUnitScore(unit); //added by ikill240c 2026-09-13
                score += unit_score;
                if (unit.getOwner().getAI() == null) { //added by ikill240c 2026-09-12: a null AI means a human-controlled player
                    human_attacker = unit.getOwner(); //added by ikill240c 2026-09-13
                    human_unit_count++; //added by ikill240c 2026-09-13
                    human_score += unit_score; //added by ikill240c 2026-09-13
                } //added by ikill240c 2026-09-13
                if (defense_target == null)
                    defense_target = new LandscapeTarget(unit.getGridX(), unit.getGridY());
            }
        }
        recordPlayerAttackObservation(kind, human_attacker, human_unit_count, human_score); //added by ikill240c 2026-09-13
        return score;
    }

    private int scanForEnemiesAt(int grid_x, int grid_y, @Nullable String kind) { //added by ikill240c 2026-09-12: kind param
        float px = com.oddlabs.tt.pathfinder.UnitGrid.coordinateFromGrid(grid_x);
        float py = com.oddlabs.tt.pathfinder.UnitGrid.coordinateFromGrid(grid_y);
        FindOccupantFilter<Unit> filter = new FindOccupantFilter<>(px, py, DEFENSE_SCAN_RADIUS, null, Unit.class);
        getUnitGrid().scan(filter, grid_x, grid_y);
        int score = 0;
        int human_unit_count = 0; //added by ikill240c 2026-09-13
        int human_score = 0; //added by ikill240c 2026-09-13
        Player human_attacker = null; //added by ikill240c 2026-09-13
        defense_target = null;
        for (Unit unit : filter.getResult()) {
            if (!unit.isDead() && getOwner().isEnemy(unit.getOwner())) {
                int unit_score = getUnitScore(unit); //added by ikill240c 2026-09-13
                score += unit_score;
                if (unit.getOwner().getAI() == null) { //added by ikill240c 2026-09-12
                    human_attacker = unit.getOwner(); //added by ikill240c 2026-09-13
                    human_unit_count++; //added by ikill240c 2026-09-13
                    human_score += unit_score; //added by ikill240c 2026-09-13
                } //added by ikill240c 2026-09-13
                if (defense_target == null)
                    defense_target = new LandscapeTarget(unit.getGridX(), unit.getGridY());
            }
        }
        recordPlayerAttackObservation(kind, human_attacker, human_unit_count, human_score); //added by ikill240c 2026-09-13
        return score;
    }

    // Feeds PlayerBehaviorProfile from real combat: called every time nodeDefendBase() scans one of
    // this AI's own buildings (kind != null) and finds an actively-hostile presence involving the
    // human player. Everything is tagged with a "segment key" - (opponent race, map-size bucket,
    // current difficulty) - so a player's habits get tracked separately per context instead of
    // averaged into one global number; see opponentRaceLabel()/mapSizeLabel()/difficultyLabel() and
    // PlayerBehaviorProfile's class comment for what that does and doesn't buy over a single global
    // profile. Note this is a separate concern from Stage 1/2's play-style bandit
    // (PlayerStyleProfile/AiBehaviorBandit above) - that tracks one continuous aggression axis from
    // Player's existing kill/loss/harvest counters and picks an overall behavior posture; this tracks
    // discrete per-building/per-context combat events from direct scan observations and biases
    // specific defensive/offensive knobs. They read genuinely different signals and don't overlap.
    // Four things get learned per segment, each cheap and honest about what it is - counts and
    // running EMAs, not a trained model:
    //  1) The elapsed time of the FIRST human attack observed this match in this segment (once per
    //     match, via first_attack_recorded) - read back by rushDefenseMultiplier().
    //  2) A running tally of which building kind gets attacked, subject to a cooldown so one
    //     continuous siege counts as one "wave" rather than hundreds - read back by
    //     vulnerabilityWeight().
    //  3) How large (unit count) and how strong (combat score) that attack wave was - read back by
    //     attackForceMultiplier().
    //  4) How often waves land, once match length is known (see finalizeAdaptiveLearning(), called
    //     from GameOverTrigger at game-over) - read back by standingGarrisonMultiplier().
    // Deliberately gated on `adaptive` - this is part of the same opt-in toggle as the
    // difficulty-seeding behaviour, not a change to how AI plays by default. //added by ikill240c 2026-09-12
    private static final float ATTACK_WAVE_COOLDOWN_SECONDS = 15f; //added by ikill240c 2026-09-12
    private boolean first_attack_recorded = false; //added by ikill240c 2026-09-12
    private float time_since_last_recorded_wave = ATTACK_WAVE_COOLDOWN_SECONDS; //added by ikill240c 2026-09-12
    // Cached once the first human attacker is observed this match - race doesn't change mid-match,
    // so there's no reason to re-resolve it on every single wave. //added by ikill240c 2026-09-13
    private @Nullable Player observed_human_opponent = null; //added by ikill240c 2026-09-13
    private @Nullable String cached_map_size_label = null; //added by ikill240c 2026-09-13

    private void recordPlayerAttackObservation(@Nullable String kind, @Nullable Player human_attacker, //added by ikill240c 2026-09-13
            int force_size, int force_score) { //added by ikill240c 2026-09-13
        if (!adaptive || kind == null || human_attacker == null) //added by ikill240c 2026-09-13
            return; //added by ikill240c 2026-09-13
        if (observed_human_opponent == null) //added by ikill240c 2026-09-13
            observed_human_opponent = human_attacker; //added by ikill240c 2026-09-13
        String key = currentSegmentKey(); //added by ikill240c 2026-09-13
        if (!first_attack_recorded) { //added by ikill240c 2026-09-12
            first_attack_recorded = true; //added by ikill240c 2026-09-12
            PlayerBehaviorProfile.get().recordFirstAttackTime(key, match_elapsed_seconds); //added by ikill240c 2026-09-13
        } //added by ikill240c 2026-09-12
        if (time_since_last_recorded_wave < ATTACK_WAVE_COOLDOWN_SECONDS) //added by ikill240c 2026-09-12
            return; //added by ikill240c 2026-09-12
        time_since_last_recorded_wave = 0f; //added by ikill240c 2026-09-12
        PlayerBehaviorProfile.get().recordAttackWave(key, kind, force_size, force_score); //added by ikill240c 2026-09-13
    } //added by ikill240c 2026-09-12

    // Race label for whichever human player has been observed attacking this AI so far this match
    // (cached in observed_human_opponent); "unknown" before any attack has been observed yet, which
    // only matters for the very first scan of a match (everything before the first recorded wave
    // uses the neutral "unknown" segment, which simply never accumulates data since nothing is ever
    // recorded against it before observed_human_opponent is set). //added by ikill240c 2026-09-13
    private @NonNull String opponentRaceLabel() { //added by ikill240c 2026-09-13
        if (observed_human_opponent == null) //added by ikill240c 2026-09-13
            return "unknown"; //added by ikill240c 2026-09-13
        return switch (observed_human_opponent.getPlayerInfo().getRace()) { //added by ikill240c 2026-09-13
            case RacesResources.RACE_NATIVES -> "natives"; //added by ikill240c 2026-09-13
            case RacesResources.RACE_VIKINGS -> "vikings"; //added by ikill240c 2026-09-13
            default -> "unknown"; //added by ikill240c 2026-09-13
        }; //added by ikill240c 2026-09-13
    } //added by ikill240c 2026-09-13

    // Bucketed off the map's grid side length (getGridSize() - the codebase's maps are square).
    // Thresholds are a reasonable-guess split, not calibrated against real map-size data; retune if
    // they don't match how "small/medium/large" actually feel in practice. Cached since it can't
    // change mid-match. //added by ikill240c 2026-09-13
    private @NonNull String mapSizeLabel() { //added by ikill240c 2026-09-13
        if (cached_map_size_label == null) { //added by ikill240c 2026-09-13
            int size = getUnitGrid().getGridSize(); //added by ikill240c 2026-09-13
            cached_map_size_label = size < 192 ? "small" : size < 384 ? "medium" : "large"; //added by ikill240c 2026-09-13
        } //added by ikill240c 2026-09-13
        return cached_map_size_label; //added by ikill240c 2026-09-13
    } //added by ikill240c 2026-09-13

    // Uses the AI's CURRENT difficulty, not whatever it was seeded at - under Adaptive AI, difficulty
    // can drift mid-match (see nodeAdaptiveDifficulty()), so this can genuinely change what segment
    // an observation is recorded into partway through a single game. That's an accepted
    // simplification: the alternative (freezing the label at match start) would misattribute data
    // from a match that drifted difficulty significantly, which seems like the worse trade-off.
    // //added by ikill240c 2026-09-13
    private @NonNull String difficultyLabel() { //added by ikill240c 2026-09-13
        return switch (difficulty) { //added by ikill240c 2026-09-13
            case DIFFICULTY_EASY -> "easy"; //added by ikill240c 2026-09-13
            case DIFFICULTY_HARD -> "hard"; //added by ikill240c 2026-09-13
            case DIFFICULTY_INSANE -> "insane"; //added by ikill240c 2026-09-14: new tier added independently since this was last written - segmenting it separately from hard rather than folding it in, since an Insane opponent plays meaningfully differently (see DIFFICULTY_INSANE's own arrays)
            default -> "normal"; //added by ikill240c 2026-09-13
        }; //added by ikill240c 2026-09-13
    } //added by ikill240c 2026-09-13

    private @NonNull String currentSegmentKey() { //added by ikill240c 2026-09-13
        return PlayerBehaviorProfile.segmentKey(opponentRaceLabel(), mapSizeLabel(), difficultyLabel()); //added by ikill240c 2026-09-13
    } //added by ikill240c 2026-09-13

    // Called once from GameOverTrigger at game-over (regardless of who won) so
    // PlayerBehaviorProfile.getAttacksPerMinute() has an honest per-segment time denominator - see
    // standingGarrisonMultiplier(). A no-op if this AI never actually observed a human attacker this
    // match (nothing meaningful to attribute the match length to). //added by ikill240c 2026-09-13
    public void finalizeAdaptiveLearning() { //added by ikill240c 2026-09-13
        if (!adaptive || observed_human_opponent == null) //added by ikill240c 2026-09-13
            return; //added by ikill240c 2026-09-13
        PlayerBehaviorProfile.get().recordMatchExposure(currentSegmentKey(), match_elapsed_seconds / 60f); //added by ikill240c 2026-09-13
    } //added by ikill240c 2026-09-13

    // How much extra weight nodeDefendBase() should give a threat to `kind`, based on how often this
    // player has historically gone after that building type in this exact (race/map-size/difficulty)
    // segment. Returns exactly 1 (no-op) unless Adaptive AI is on, keeping default AI behaviour
    // completely unaffected by this feature. //added by ikill240c 2026-09-12
    private float vulnerabilityWeight(@Nullable String kind) { //added by ikill240c 2026-09-12
        if (!adaptive || kind == null) //added by ikill240c 2026-09-12
            return 1f; //added by ikill240c 2026-09-12
        String key = currentSegmentKey(); //added by ikill240c 2026-09-13
        return 1f + switch (kind) { //added by ikill240c 2026-09-12
            case "quarters" -> PlayerBehaviorProfile.get().getQuartersHitShare(key); //added by ikill240c 2026-09-13
            case "armory" -> PlayerBehaviorProfile.get().getArmoryHitShare(key); //added by ikill240c 2026-09-13
            case "outpost" -> PlayerBehaviorProfile.get().getOutpostHitShare(key); //added by ikill240c 2026-09-13
            default -> 0f; //added by ikill240c 2026-09-12
        }; //added by ikill240c 2026-09-12
    } //added by ikill240c 2026-09-12

    // Extra defensive responsiveness during the opening minutes of a match, only applied when this
    // player has a historical pattern of attacking early IN THIS SEGMENT. Returns exactly 1 (no-op)
    // once past EARLY_GAME_WINDOW_SECONDS into the match, once adaptive is off, or once there isn't
    // enough history to call it a pattern yet. //added by ikill240c 2026-09-12
    private static final float EARLY_GAME_WINDOW_SECONDS = 240f; //added by ikill240c 2026-09-12
    private static final float EARLY_RUSH_DEFENSE_BOOST = 1.5f; //added by ikill240c 2026-09-12

    private float rushDefenseMultiplier() { //added by ikill240c 2026-09-12
        if (!adaptive || match_elapsed_seconds >= EARLY_GAME_WINDOW_SECONDS) //added by ikill240c 2026-09-12
            return 1f; //added by ikill240c 2026-09-12
        return PlayerBehaviorProfile.get().isEarlyRusher(currentSegmentKey()) ? EARLY_RUSH_DEFENSE_BOOST : 1f; //added by ikill240c 2026-09-13
    } //added by ikill240c 2026-09-12

    // Scales the AI's own attack-wave warrior count up (never down) when this player has
    // historically sent noticeably bigger attack forces than the AI's own current wave size, in this
    // segment - so the AI doesn't keep attacking with a wave sized for an opponent who, historically,
    // hits back several times harder. Applied at nodeAttackWithWarriorsAndChieftain()'s call site.
    // //added by ikill240c 2026-09-13
    private float attackForceMultiplier() { //added by ikill240c 2026-09-13
        if (!adaptive) //added by ikill240c 2026-09-13
            return 1f; //added by ikill240c 2026-09-13
        return PlayerBehaviorProfile.get().getForceSizeMultiplier(currentSegmentKey(), NUM_WARRIORS[difficulty]); //added by ikill240c 2026-09-13
    } //added by ikill240c 2026-09-13

    // Scales up how large a fraction of the peon population is allowed into standing defense duty
    // (MAX_PEON_DEFENSE_FRACTION) when this player has historically attacked frequently in this
    // segment - a player who attacks every couple of minutes justifies keeping more of the workforce
    // on defensive standby than the flat default. Capped well short of 1 so the AI never actually
    // stops gathering resources entirely, no matter how relentless the player has been historically.
    // //added by ikill240c 2026-09-13
    private static final float MAX_STANDING_GARRISON_FRACTION = 0.6f; //added by ikill240c 2026-09-13
    // Attacks-per-minute at/above which the multiplier is fully ramped up to
    // MAX_STANDING_GARRISON_FRACTION / MAX_PEON_DEFENSE_FRACTION; scales linearly below that.
    // //added by ikill240c 2026-09-13
    private static final float HIGH_FREQUENCY_ATTACKS_PER_MINUTE = 1f; //added by ikill240c 2026-09-13

    private float standingGarrisonFraction() { //added by ikill240c 2026-09-13
        if (!adaptive) //added by ikill240c 2026-09-13
            return MAX_PEON_DEFENSE_FRACTION; //added by ikill240c 2026-09-13
        float attacks_per_minute = PlayerBehaviorProfile.get().getAttacksPerMinute(currentSegmentKey()); //added by ikill240c 2026-09-13
        float t = Math.clamp(attacks_per_minute / HIGH_FREQUENCY_ATTACKS_PER_MINUTE, 0f, 1f); //added by ikill240c 2026-09-13
        return MAX_PEON_DEFENSE_FRACTION + t * (MAX_STANDING_GARRISON_FRACTION - MAX_PEON_DEFENSE_FRACTION); //added by ikill240c 2026-09-13
    } //added by ikill240c 2026-09-13

    private @Nullable Unit findBestIdleWarrior(@NonNull Selectable<?>[] idle_warriors, boolean[] used) {//added by ikill240c
        int tick = getOwner().getWorld().getTick(); //added by ikill240c
        int best_index = -1;
        int best_score = -1;
        for (int i = 0; i < idle_warriors.length; i++) {
            if (used[i])
                continue;
            // Same reasoning as claimIdleWarriors()'s own check - skip anything a human teammate
            // recently directly ordered, so this AI doesn't immediately reclaim it. //added by ikill240c
            if (idle_warriors[i].getHumanOverrideUntilTick() > tick) //added by ikill240c
                continue; //added by ikill240c
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

    private int getUnitScore(@NonNull Unit unit) {//added by ikill240c
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
        // Was `throw new RuntimeException();` - safe in the original codebase since every call site only
        // ever passed small, pre-filtered lists. Newer call sites added this session scan much wider,
        // unfiltered live populations every AI tick (scanForEnemies()/scanForEnemiesAt() around every
        // Quarters/Armory/occupied zone, whole-army scans in computeArmyStrength(),
        // nodeCheckAttackingArmy(), nodeChiefHitAndRun()'s clustering search). Any of those hitting a Unit
        // type this method doesn't recognize (mounted tower defender, ship crew, etc.) threw uncaught from
        // inside animate(), silently aborting every section scheduled after the throw point for that tick
        // - which is why attack/defend/patrol/escort/hit-and-run/Convert could all appear broken at once:
        // one recurring uncaught exception, not six separate bugs. Returning a small safe default instead
        // of throwing fixes this without changing the score for any already-recognized type above.
        // //added by ikill240c 2026-09-10
        return SCORE_PEON;
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
                } else if (tower.getUnitContainer() instanceof MountUnitContainer mount_container //added by ikill240c
                        && idle_warriors != null) { //added by ikill240c
                    // Tower is already occupied - check whether a better idle warrior is standing
                    // around doing nothing, and if so, send it to replace the current occupant
                    // (EnterController's own "tower full, but arriving unit is eligible" case now
                    // ejects and replaces rather than giving up or overflowing elsewhere - see its
                    // comment). Uses the SAME getUnitScore() metric findBestIdleWarrior() already
                    // ranks by, so "better" here means whatever this AI already considers a
                    // stronger unit type/weapon tier everywhere else. Skipped entirely if nothing
                    // idle beats the current occupant, so this never needlessly swaps a warrior for
                    // an equally-good one. //added by ikill240c
                    Unit mounted = mount_container.getUnit(); //added by ikill240c
                    if (mounted != null) { //added by ikill240c
                        int mounted_score = getUnitScore(mounted); //added by ikill240c
                        Unit best = findBestIdleWarrior(idle_warriors, used); //added by ikill240c
                        if (best != null && getUnitScore(best) > mounted_score) { //added by ikill240c
                            getOwner().setTarget(Selectable.newArray(best), tower, Action.DEFAULT, false); //added by ikill240c
                        } else if (best != null) { //added by ikill240c
                            // findBestIdleWarrior() already marked this candidate used before we
                            // could check whether it was actually good enough to deploy - give it
                            // back so it's still available for a genuinely empty tower later in
                            // this same loop, rather than being silently wasted here. //added by ikill240c
                            for (int j = 0; j < idle_warriors.length; j++) { //added by ikill240c
                                if (idle_warriors[j] == best) { //added by ikill240c
                                    used[j] = false; //added by ikill240c
                                    break; //added by ikill240c
                                } //added by ikill240c
                            } //added by ikill240c
                        } //added by ikill240c
                    } //added by ikill240c
                } //added by ikill240c
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
        // Same reasoning as nodeBuildResourceTowers()'s identical check - see that method's own
        // comment. This is the defensive/guard tower path (nodeBuildResourceTowers builds the
        // resource-boosting kind), but the underlying problem was the same: no warrior-count check
        // at all here either, so a defensive tower could start construction before the AI had
        // anything to defend with or attack alongside it. //added by ikill240c
        if (countWarriors() < NUM_WARRIORS[difficulty] / 2) //added by ikill240c
            return; //added by ikill240c

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
        // Previously had no warrior-count check at all - as soon as a Quarters, Armory, and 10
        // idle peons existed, the AI would start a resource tower even with zero warriors trained,
        // meaning it could be defenseless (and unable to attack) while sinking peon labor and
        // resources into a tower instead of an army. Requiring at least half this difficulty's
        // target warrior count first means gathering/training/attacking/defending are already
        // underway (or towers and warriors can proceed together once that bar is cleared) rather
        // than towers coming first while the AI has nothing to fight with. Scales with
        // NUM_WARRIORS[difficulty] (which itself grows over the match via NUM_WARRIORS_INCREASE),
        // rather than a single fixed threshold that would be trivial on Hard and unreachable on
        // Easy. //added by ikill240c
        if (countWarriors() < NUM_WARRIORS[difficulty] / 2) //added by ikill240c
            return; //added by ikill240c
        Building home = homeBuilding();
        if (home == null)
            return;

        // Excludes zones already used by an earlier resource tower (occupied_zones - also used by
        // nodeBuildTower()'s own expansion-zone rotation), so towers spread across DIFFERENT
        // resource spots instead of every one clustering around whichever single node happened to
        // be nearest home. Tries iron first, then rock, since a base often has both nearby -
        // without the fallback, once every iron node near home was already claimed, this would
        // simply stop building resource towers at all even with untouched rock nodes available.
        // //added by ikill240c
        SupplyLocationFilter filter = new SupplyLocationFilter(IronSupply.class, 250, occupied_zones); //added by ikill240c
        getUnitGrid().scan(filter, home.getGridX(), home.getGridY());
        if (!filter.found()) { //added by ikill240c
            filter = new SupplyLocationFilter(RockSupply.class, 250, occupied_zones); //added by ikill240c
            getUnitGrid().scan(filter, home.getGridX(), home.getGridY()); //added by ikill240c
        } //added by ikill240c
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

    // Same-tick-only cache (not the 30-tick window strength_cache/status_cache use - construction
    // state can change within that window, e.g. a tower finishing mid-window, and this feeds
    // build-gate decisions where that staleness could cause over-committing to too many
    // simultaneous builds) for the three near-identical getXUnderConstruction() scans below.
    // nodeBuildTower(), nodeBuildResourceTowers(), nodeBuildQuarters(), and nodeBuildArmory() are
    // four separate nodes that all run every tick, each independently re-scanning this player's
    // entire unit set just to count buildings of one type under construction - refreshed every
    // tick (so never stale), but computed once per tick and shared across however many of those
    // four nodes ask for it that same tick, rather than once per node. //added by ikill240c
    private int under_construction_cache_tick = Integer.MIN_VALUE; //added by ikill240c
    private java.util.Set<Building> towers_under_construction_cache = java.util.Set.of(); //added by ikill240c
    private java.util.Set<Building> quarters_under_construction_cache = java.util.Set.of(); //added by ikill240c
    private java.util.Set<Building> armories_under_construction_cache = java.util.Set.of(); //added by ikill240c

    private void ensureUnderConstructionCacheFresh() { //added by ikill240c
        int tick = getOwner().getWorld().getTick(); //added by ikill240c
        if (tick == under_construction_cache_tick) //added by ikill240c
            return; // already computed this exact tick //added by ikill240c
        under_construction_cache_tick = tick; //added by ikill240c
        java.util.Set<Building> towers = new java.util.HashSet<>(); //added by ikill240c
        java.util.Set<Building> quarters = new java.util.HashSet<>(); //added by ikill240c
        java.util.Set<Building> armories = new java.util.HashSet<>(); //added by ikill240c
        for (Selectable<?> s : getOwner().getUnits().getSet()) { //added by ikill240c
            if (s.isDead()) //added by ikill240c
                continue; //added by ikill240c
            Controller c = s.getPrimaryController(); //added by ikill240c
            Building building = null; //added by ikill240c
            if (c instanceof PlaceBuildingController pbc) { //added by ikill240c
                building = pbc.getBuilding(); //added by ikill240c
            } else if (c instanceof RepairController rc) { //added by ikill240c
                building = rc.getBuilding(); //added by ikill240c
            } //added by ikill240c
            if (building == null || building.isDead() || building.isComplete()) //added by ikill240c
                continue; //added by ikill240c
            if (isTowerTemplate(building)) towers.add(building); //added by ikill240c
            else if (isQuartersTemplate(building)) quarters.add(building); //added by ikill240c
            else if (isArmoryTemplate(building)) armories.add(building); //added by ikill240c
        } //added by ikill240c
        towers_under_construction_cache = towers; //added by ikill240c
        quarters_under_construction_cache = quarters; //added by ikill240c
        armories_under_construction_cache = armories; //added by ikill240c
    } //added by ikill240c

    private boolean isTowerTemplate(@NonNull Building building) {//added by ikill240c
        BuildingTemplate tower_template = getOwner().getRace().getBuildingTemplate(Race.BUILDING_TOWER);
        return building.getTemplate().getTemplateID() == tower_template.getTemplateID();
    }

    private java.util.Set<Building> getTowersUnderConstruction() {
        ensureUnderConstructionCacheFresh(); //added by ikill240c
        return towers_under_construction_cache; //added by ikill240c
    }

    private int countTowersUnderConstruction() { //added by ikill240c
        return getTowersUnderConstruction().size();
    }

    private boolean isQuartersTemplate(@NonNull Building building) {
        BuildingTemplate quarters_template = getOwner().getRace().getBuildingTemplate(Race.BUILDING_QUARTERS);
        return building.getTemplate().getTemplateID() == quarters_template.getTemplateID();
    }

    private java.util.Set<Building> getQuartersUnderConstruction() {
        ensureUnderConstructionCacheFresh(); //added by ikill240c
        return quarters_under_construction_cache; //added by ikill240c
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
        ensureUnderConstructionCacheFresh(); //added by ikill240c
        return armories_under_construction_cache; //added by ikill240c
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
    private void nodeAssignIdleGatherers() {
        Selectable<?>[] idle = getIdlePeons();
        if (idle == null || idle.length == 0)
            return;
    
        Building drop_off = gatherDropOffBuilding();
        if (drop_off == null)
            return;
    
        int tree = countOrZero(getGatherTreePeons());
        int rock = countOrZero(getGatherRockPeons());
        int iron = countOrZero(getGatherIronPeons());
        int rubber = countOrZero(getGatherRubberPeons());
    
        for (Selectable<?> s : idle) {
            if (!(s instanceof Unit unit) || unit.isDead() || unit.isMounted())
                continue;
            if (!unit.getAbilities().hasAbilities(Abilities.BUILD))
                continue;
        
            int tree_room = MAX_UNITS_GATHERING_TREE[difficulty] - tree;
            int rock_room = MAX_UNITS_GATHERING_ROCK[difficulty] - rock;
            int iron_room = MAX_UNITS_GATHERING_IRON[difficulty] - iron;
            int rubber_room = MAX_UNITS_GATHERING_RUBBER[difficulty] - rubber;
            int best_room = Math.max(Math.max(tree_room, rock_room), Math.max(iron_room, rubber_room));
    
            Class<? extends Supply> supply_type;
            if (best_room > 0) {
                // Normal path: balance toward whichever resource has spare room under the cap.
                if (tree_room == best_room) { supply_type = TreeSupply.class; tree++; }
                else if (rock_room == best_room) { supply_type = RockSupply.class; rock++; }
                else if (iron_room == best_room) { supply_type = IronSupply.class; iron++; }
                else { supply_type = RubberSupply.class; rubber++; }
            } else {
                // Every resource is at its difficulty cap - rather than leave this peon fully idle,
                // pile it onto whichever pool currently has the fewest gatherers so the surplus still
                // does something productive instead of sitting as inert "reserve."
                int min = Math.min(Math.min(tree, rock), Math.min(iron, rubber));
                if (tree == min) { supply_type = TreeSupply.class; tree++; }
                else if (rock == min) { supply_type = RockSupply.class; rock++; }
                else if (iron == min) { supply_type = IronSupply.class; iron++; }
                else { supply_type = RubberSupply.class; rubber++; }
            }
            unit.initGather(supply_type, drop_off);
        }
    }//added by ikill240c 2026-09-10 15:10

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
     Selectable<?>[] armory = getArmory();
     if (armory != null) {
         for (Selectable<?> a : armory) {
             if (a instanceof Building b && !b.isDead() && b.getAbilities().hasAbilities(Abilities.SUPPLY_CONTAINER))
                 return b;
            }
        }
    return null; // no building can accept raw resources yet - keep peons idle rather than risk a bad target
    }

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

    private void nodeCheckMainAttackTargetStillAlive(float t) { //added by ikill240c
        if (tracked_main_attack_target == null) return; //added by ikill240c
        main_attack_target_check_cooldown -= t; //added by ikill240c
        if (main_attack_target_check_cooldown > 0f) return; //added by ikill240c
        main_attack_target_check_cooldown = 5f; //added by ikill240c - frequent enough to catch a dead target well before a long walk completes, cheap enough (a single isDead() check) not to matter running this often

        if (tracked_main_attack_target.isDead()) { //added by ikill240c
            // The force sent at this target is now walking toward a location with nothing left to
            // fight - force the main attack's own gate to re-evaluate immediately (matching the
            // "stalled" trigger's own condition: enough time since the last attack, and enough
            // idle/available warriors) rather than waiting out its normal stall-timer pacing,
            // since that timer was reset when THIS attack was launched, not when it became
            // pointless. Doesn't affect currently-walking units directly (that would need a
            // deeper controller-level change - see this field's own comment for why formation
            // spreading rules that out here) - what this achieves is the AI redirecting its NEXT
            // wave sooner, rather than sitting on a fixed cooldown while its current wave
            // pointlessly finishes walking to an empty spot. //added by ikill240c
            time_since_attack = getStyleAdjustedStallSeconds(); //added by ikill240c
            tracked_main_attack_target = null; //added by ikill240c
        } //added by ikill240c
    } //added by ikill240c

    private void nodeAttackWithWarriorsAndChieftain(int num_warriors, boolean use_chieftain) {
        /*
        System.out.print("nodeAttackWithWarriorsAndChieftain");
        if (getIdleWarriors() == null)
        	System.out.println(" | no idling warriors");
        else
        	System.out.println(" | " + getIdleWarriors().length + " idling warriors");
        */
        // Was an all-or-nothing check requiring the FULL requested num_warriors to be idle before
        // this would do anything at all. Once the ongoing opportunistic strategies (best-move,
        // multi-front, favorable, all-in - see the roster comment above where these are wired into
        // animate()) started claiming their own slices of the idle pool between main-attack
        // triggers, the standing idle count often fell short of the full target even though a
        // perfectly meaningful force was still available - silently launching NOTHING, not even a
        // smaller wave, and still burning the trigger (time_since_attack/main_attack_wave_count
        // reset unconditionally by the caller regardless of what happens in here). That matched
        // reports of the AI barely attacking and never bringing the chieftain along: NUM_WARRIORS
        // growth and chieftain inclusion both only ever happened inside the block this check
        // gated, so a starved idle pool meant neither ever occurred. Now requires only a real,
        // meaningful minimum instead of the full amount, and attacks with whatever's actually
        // available up to the original request - so a wave that was sized (and had the chieftain
        // decision made) for the full target still goes out, just occasionally smaller, rather
        // than not going out at all. //added by ikill240c
        Selectable<?>[] idle_warriors = getIdleWarriors(); //added by ikill240c
        int min_force = Math.max(3, num_warriors / 2); //added by ikill240c
        if (idle_warriors != null && idle_warriors.length >= min_force
                && (!use_chieftain || getOwner().hasActiveChieftain())) {
            int actual_num_warriors = Math.min(num_warriors, idle_warriors.length); //added by ikill240c
            boolean idle_chieftain = getIdleChieftains() != null && getIdleChieftains().length >= 1;
            Selectable<?>[] warriors;
            if (idle_chieftain && use_chieftain) {
                warriors = Selectable.newArray(actual_num_warriors + 1); //added by ikill240c
                warriors[actual_num_warriors] = getIdleChieftains()[0]; //added by ikill240c
            } else {
                warriors = Selectable.newArray(actual_num_warriors); //added by ikill240c
            }

            System.arraycopy(idle_warriors, 0, warriors, 0, actual_num_warriors); //added by ikill240c
            Target target = findTarget(warriors[0].getGridX(), warriors[0].getGridY(), warriors.length); //added by ikill240c - passes the actual force size (including the chieftain, if along) so tower-avoidance scales to what this group can realistically handle
            if (target != null) {
                tracked_main_attack_target = target; //added by ikill240c - see this field's own comment for why this AI-level tracking exists
                // Previously never called at all, meaning every AI attack order moved through
                // Player.setLandscapeTarget() (which already IS formation-aware - see
                // FormationLayout.computeOffsets()) using whatever the player field's default
                // (Formation.LOOSE) happened to be, rather than the AI ever deliberately choosing
                // one. SQUARE keeps the attacking group cohesive without being as tightly packed
                // as TIGHT, a reasonable default for a mixed warrior+chieftain strike force.
                // //added by ikill240c
                getOwner().setFormation(warriors, Formation.SQUARE); //added by ikill240c
                // Flanking: a minority of the force is routed via a waypoint to one side of the
                // target before attacking, rather than every warrior approaching from the exact
                // same direction as the main group - splits the enemy's attention and defense
                // between two approach angles instead of concentrating it all on one front.
                // Skipped for very small forces (below FLANK_MIN_FORCE_SIZE) where splitting off a
                // flank group would leave both halves too weak to accomplish anything.
                // //added by ikill240c
                if (warriors.length >= FLANK_MIN_FORCE_SIZE) { //added by ikill240c
                    launchFlankingAttack(warriors, target); //added by ikill240c
                } else { //added by ikill240c
                    getOwner().setLandscapeTarget(warriors, target.getGridX(), target.getGridY(), Action.ATTACK, true);
                } //added by ikill240c
                if (NUM_WARRIORS[difficulty] < NUM_WARRIORS_MAX[difficulty])
                    NUM_WARRIORS[difficulty] += NUM_WARRIORS_INCREASE[difficulty];
                for (Selectable<?> w : warriors) {
                    if (w instanceof Unit u) attacking_units.add(u);
                }
            }
        } else {
            if (idle_warriors != null) { //added by ikill240c
                nodeDeployUnitsInArmory(num_warriors - idle_warriors.length); //added by ikill240c
            } else {
                nodeDeployUnitsInArmory(num_warriors);
            }
            // nodeTrainChieftain() is now called unconditionally from animate() (ECONOMY section)
            // so it keeps running regardless of whether the attack gate above is satisfied this
            // tick - see the call site there for why. //added by ikill240c 2026-09-11
        }
    }

    // Below this force size, splitting off a flanking group would leave both halves too weak to
    // accomplish anything - a 3-warrior raid split into 2+1 isn't a flanking maneuver, it's just a
    // weaker raid with extra steps. //added by ikill240c
    private static final int FLANK_MIN_FORCE_SIZE = 15; //added by ikill240c
    // Fraction of the force sent around the flank - a genuine minority, so the main group can
    // still credibly engage on its own even before the flank arrives. //added by ikill240c
    private static final float FLANK_GROUP_FRACTION = 0.4f; //added by ikill240c og 0.35
    // How far to the side of the target the flanking group's intermediate waypoint sits, in world
    // units - far enough to approach from a visibly different angle than the main group, not so
    // far that the flank group wanders off chasing a separate detour. //added by ikill240c
    private static final float FLANK_OFFSET_DISTANCE = 40f; //added by ikill240c

    // Splits warriors into a main group (sent directly at target, same as a non-flanking attack)
    // and a smaller flanking group, routed via an intermediate waypoint to one side of the target
    // first - enqueueTarget() chains "walk to the waypoint" then "attack the target" on each
    // flanking unit individually, so it doesn't cut straight at the target alongside the main
    // group but instead approaches from a different direction once it reaches the waypoint.
    // //added by ikill240c
    private void launchFlankingAttack(@NonNull Selectable<?>[] warriors, @NonNull Target target) { //added by ikill240c
        int flank_count = Math.round(warriors.length * FLANK_GROUP_FRACTION); //added by ikill240c
        // Clamp so both groups end up non-empty even at the small end of what
        // FLANK_MIN_FORCE_SIZE allows through, and so the flank group can never be the WHOLE
        // force (that would just be a slower, indirect attack, not a flank). //added by ikill240c
        flank_count = Math.max(1, Math.min(flank_count, warriors.length - 1)); //added by ikill240c

        float tx = target.getPositionX(); //added by ikill240c
        float ty = target.getPositionY(); //added by ikill240c
        // Approach direction: from the force's own current position toward the target, not from
        // home base - this is the direction the main group will actually be attacking from, which
        // is what the flank needs to be perpendicular to. //added by ikill240c
        float dx = tx - warriors[0].getPositionX(); //added by ikill240c
        float dy = ty - warriors[0].getPositionY(); //added by ikill240c
        float len = (float) Math.sqrt(dx * dx + dy * dy); //added by ikill240c
        if (len < 1f) { //added by ikill240c - degenerate (already on top of the target) - no meaningful flank direction, just attack directly
            getOwner().setLandscapeTarget(warriors, target.getGridX(), target.getGridY(), Action.ATTACK, true); //added by ikill240c
            return; //added by ikill240c
        } //added by ikill240c
        dx /= len; //added by ikill240c
        dy /= len; //added by ikill240c
        // Perpendicular to the approach direction - randomly picks left or right side each time
        // (deterministic RNG, required for multiplayer lockstep) so flanks don't always come from
        // the same side. //added by ikill240c
        float side = getOwner().getWorld().getRandom().nextBoolean() ? 1f : -1f; //added by ikill240c
        float perp_x = -dy * side; //added by ikill240c
        float perp_y = dx * side; //added by ikill240c
        float waypoint_x = tx + perp_x * FLANK_OFFSET_DISTANCE; //added by ikill240c
        float waypoint_y = ty + perp_y * FLANK_OFFSET_DISTANCE; //added by ikill240c
        Target flank_waypoint = new com.oddlabs.tt.landscape.LandscapeTarget( //added by ikill240c
                com.oddlabs.tt.pathfinder.UnitGrid.toGridCoordinate(waypoint_x), //added by ikill240c
                com.oddlabs.tt.pathfinder.UnitGrid.toGridCoordinate(waypoint_y)); //added by ikill240c

        Selectable<?>[] main_group = new Selectable<?>[warriors.length - flank_count]; //added by ikill240c
        Selectable<?>[] flank_group = new Selectable<?>[flank_count]; //added by ikill240c
        System.arraycopy(warriors, 0, main_group, 0, main_group.length); //added by ikill240c
        System.arraycopy(warriors, main_group.length, flank_group, 0, flank_count); //added by ikill240c

        getOwner().setLandscapeTarget(main_group, target.getGridX(), target.getGridY(), Action.ATTACK, true); //added by ikill240c
        for (Selectable<?> unit : flank_group) { //added by ikill240c
            // Guard against the same class of bug fixed earlier for GuardController's wander
            // logic: if this particular unit's current position already rounds to the same grid
            // cell as flank_waypoint, issuing a MOVE order to it would be a zero-length walk to
            // the unit's own current position - PathTracker asserts a unit can never be its own
            // "next occupant", crashing the game outright rather than harmlessly no-opping. Skip
            // straight to the attack order in that case, since the unit is effectively already at
            // the flank position. //added by ikill240c
            if (unit.getGridX() == flank_waypoint.getGridX() && unit.getGridY() == flank_waypoint.getGridY()) { //added by ikill240c
                unit.initTarget(target, Action.ATTACK, true); //added by ikill240c
            } else { //added by ikill240c
                unit.initTarget(flank_waypoint, Action.MOVE, false); //added by ikill240c
                unit.enqueueTarget(target, Action.ATTACK, true); //added by ikill240c
            } //added by ikill240c
        } //added by ikill240c
    }

    // Dedicated, higher reserve specifically for starting chieftain training - MIN_UNITS_REPRODUCING
    // is tuned for a DIFFERENT purpose (whether it's worth building a 3rd+ Quarters at all) and is
    // deliberately low ({0,5,8,12}) so that check itself stays reachable. But chieftain training
    // freezes ALL of this Quarters' reproduction progress for its full ~9 minutes (see this
    // method's comment below), so clearing only MIN_UNITS_REPRODUCING's low bar left as few as 8
    // peons as the ENTIRE peon supply nodeTransferUnits() has to work with for that whole window -
    // not enough left over to actually staff an armory and produce warriors, matching the reported
    // "starts training a chief with only 8 peons, not enough left to make warriors." Set
    // substantially higher so a real buffer exists before committing to that freeze.
    // //added by ikill240c
    private static final int[] CHIEFTAIN_TRAINING_RESERVE = {5, 15, 25, 30}; //added by ikill240c

    private void nodeTrainChieftain() { //added by ikill240c 2026-09-11
        if (getQuarters() == null)
            return;
        // Previously this always targeted getQuarters()[0] and additionally refused to start
        // training at all while getOwner().isTrainingChieftain() was true anywhere - a single
        // global flag that serialized training to one chieftain at a time for the whole base,
        // even though each Quarters has its own independent ChieftainContainer and the engine
        // supports several training concurrently (Player.canTrainMoreChieftains() already checks
        // the live total against the cap). That meant only Quarters #1 ever trained a chieftain,
        // and if it happened to be busy, dead, or capped out per-building, no other Quarters was
        // ever tried - matching the reported "sometimes trains none" symptom. Building.
        // canBuildChieftain() already re-checks (per-building busy flag, per-building production
        // cap, and the player's overall cap) on every call, including the live count after any
        // earlier iteration in this same loop just started training - so it's safe to just offer
        // every Quarters the chance and let each one decide for itself. //added by ikill240c 2026-09-11
        // Building a chieftain and growing this Quarters' own peon reserve compete for the exact
        // same progress meter - ReproduceUnitContainer.animate() diverts EVERY tick of
        // reproduction progress into chieftain training instead of increaseSupply(1) for as long
        // as isTraining() is true, so a Quarters that starts training before it has any peon
        // reserve stops accumulating peons entirely (not just slows down) for the ~9 minutes a
        // single chieftain takes at the slow, near-zero-supply reproduction rate (40 ticks at the
        // ~13.85s/tick floor rate) - long enough that in practice it may never reach
        // MIN_UNITS_REPRODUCING within a normal game. That starves nodeTransferUnits() of any
        // surplus to ever send to the armory, which was the actual root cause behind "quarters and
        // armory get built fine, but no further gatherers/peons/warriors ever go out" - only
        // whichever AI happened to NOT start training a chieftain early kept growing normally.
        // Requiring the reserve to already be met first means training only ever starts once this
        // Quarters can afford to pause its own peon growth for a while, rather than starving an
        // economy that hasn't gotten off the ground yet. //added by ikill240c
        int reserve = CHIEFTAIN_TRAINING_RESERVE[difficulty]; //added by ikill240c - was MIN_UNITS_REPRODUCING[difficulty]; see CHIEFTAIN_TRAINING_RESERVE's own comment for why this needed to be a separate, higher bar
        for (Selectable<?> q : getQuarters()) {
            if (q.isDead())
                continue;
            Building quarters = (Building) q;
            if (quarters.getUnitContainer().getNumSupplies() < reserve) //added by ikill240c
                continue; //added by ikill240c
            if (quarters.canBuildChieftain()) {
                getOwner().trainChieftain(quarters, true);
            }
        }
    }

    private void nodeDeployUnitsInArmory(int num_warriors) {
        if (getArmory() == null || getArmory().length == 0) {
            nodeBuildArmory();
            return;
        }

        // Caps how much any single armory can contribute per call to its fair share of the total
        // request, rather than letting the first armory encountered in iteration order greedily
        // consume the whole thing before the loop ever reaches the others. Without this, a small
        // request (e.g. NUM_WARRIORS[difficulty] = 3-18) is easily satisfied entirely by one
        // armory's own stockpile every single time this runs, so every OTHER armory sits idle
        // indefinitely - not because of a bug in the loop itself, but because demand this small
        // never needed to spread out. Any request this cap leaves unsatisfied this call simply
        // carries over to future calls (num_warriors is recomputed fresh each time from current
        // army/idle-warrior counts), so this doesn't create backlog, it just spreads the load so
        // every armory keeps actively consuming its own weapon stockpile instead of some sitting
        // unused while the caster relies entirely on whichever one happened to be classified
        // first. //added by ikill240c
        int alive_armory_count = 0; //added by ikill240c
        for (Selectable<?> a : getArmory()) { //added by ikill240c
            if (!a.isDead()) //added by ikill240c
                alive_armory_count++; //added by ikill240c
        } //added by ikill240c
        int per_armory_cap = alive_armory_count > 0 //added by ikill240c
                ? Math.max(1, (num_warriors + alive_armory_count - 1) / alive_armory_count) //added by ikill240c
                : num_warriors; //added by ikill240c

        int remaining = num_warriors;
        java.util.List<Building> understaffed_armories = new java.util.ArrayList<>(); //added by ikill240c
        java.util.List<Building> under_weaponed_armories = new java.util.ArrayList<>(); //added by ikill240c
        // Iterated via an explicit index list rotated by armory_deploy_rotation (advanced below),
        // instead of the plain "for (Selectable<?> a : getArmory())" this used to be. getArmory()'s
        // ordering comes from Player.classifyUnits() and is stable call-to-call (same relative order
        // every tick, effectively build order) - so with a PLAIN iteration, per_armory_cap above only
        // actually spreads deployment out within a single call that requests enough warriors to
        // exceed one armory's cap. The much more common case is a small request (num_warriors is
        // typically just the shortfall vs. current idle warriors, often 1-3) that armory #1 alone
        // satisfies completely every single time, before the loop ever reaches armory #2/#3 - which
        // is exactly the "still not sending out warriors from every armory" symptom this is fixing.
        // Rotating which armory goes first each call means that over a run of many small calls,
        // every armory gets its turn to be "first" and actually contribute, instead of #1 perpetually
        // winning the race. //added by ikill240c 2026-09-14
        java.util.List<Selectable<?>> ordered_armory = new java.util.ArrayList<>(); //added by ikill240c 2026-09-14
        for (Selectable<?> a : getArmory()) //added by ikill240c 2026-09-14
            ordered_armory.add(a); //added by ikill240c 2026-09-14
        int rotation = ordered_armory.isEmpty() ? 0 : armory_deploy_rotation % ordered_armory.size(); //added by ikill240c 2026-09-14
        armory_deploy_rotation++; //added by ikill240c 2026-09-14
        for (int offset = 0; offset < ordered_armory.size(); offset++) { //added by ikill240c 2026-09-14
            Building armory = (Building) ordered_armory.get((offset + rotation) % ordered_armory.size()); //added by ikill240c 2026-09-14
            if (armory.isDead())
                continue;

            int num_units = armory.getUnitContainer().getNumSupplies() - MIN_UNITS_BUILDING_WEAPONS[difficulty];
            int num_weapons = numWeapons(armory) - MIN_WEAPONS_IN_STOCK[difficulty];
            // Tracked BEFORE the remaining <= 0/num_units <= 0/num_weapons <= 0 skip below, so an
            // armory that's already out of units or weapons this call still gets queued for
            // peon-transfer/gathering help instead of being silently passed over.
            // //added by ikill240c
            if (num_units < per_armory_cap) //added by ikill240c
                understaffed_armories.add(armory); //added by ikill240c
            if (num_weapons < per_armory_cap) //added by ikill240c
                under_weaponed_armories.add(armory); //added by ikill240c
            if (remaining <= 0 || num_units <= 0 || num_weapons <= 0)
                continue;

            int deployable = Math.min(remaining, Math.min(num_units, num_weapons)); //added by ikill240c
            deployable = Math.min(deployable, per_armory_cap); //added by ikill240c - the fair-share cap described above
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

        // Was `if (remaining <= 0) return;` here, which skipped nodeTransferUnits()/nodeGather()
        // entirely whenever this call's warrior-deployment shortfall was already zero or negative
        // (e.g. the AI already has enough idle warriors sitting around this tick) - even though
        // understaffed_armories/under_weaponed_armories were already correctly populated by the
        // loop above regardless of remaining's value. Keeping armories stocked with peons and
        // materials is an ongoing economy-maintenance task independent of whether the AI wants
        // MORE warriors deployed this exact instant; gating it behind that unrelated shortfall is
        // exactly why armories could sit understaffed indefinitely once idle warrior count caught
        // up to target, with no obvious way to recover until it fell behind again.
        //
        // remaining itself is no good as the transfer/gather budget once it's <= 0 (it wouldn't
        // move anything either), so a fresh, independent top-up amount is used instead: each
        // armory in the relevant list gets its own per_armory_cap worth of budget, since that's
        // already this method's own established "how much should one armory get per call" figure.
        // //added by ikill240c
        int transfer_budget = Math.max(remaining, per_armory_cap * understaffed_armories.size()); //added by ikill240c
        int gather_budget = Math.max(remaining, per_armory_cap); //added by ikill240c

        // Distributes across EVERY armory that's short, not just whichever one happened to be
        // encountered first in the loop above - see nodeTransferUnits()'s own comment for the
        // full reasoning. //added by ikill240c
        if (!understaffed_armories.isEmpty()) { //added by ikill240c
            nodeTransferUnits(transfer_budget, understaffed_armories); //added by ikill240c
        } //added by ikill240c
        for (Building armory : under_weaponed_armories) { //added by ikill240c
            nodeGather(armory, gather_budget); //added by ikill240c
        } //added by ikill240c
        // Matches the original condition precisely (build another armory only when there are NONE
        // alive at all) using alive_armory_count computed above for the fair-share cap, rather
        // than inferring "no armories" from both shortage lists being empty - which would also be
        // true, incorrectly, whenever every existing armory happens to be fully stocked.
        // //added by ikill240c
        if (alive_armory_count == 0) { //added by ikill240c
            nodeBuildArmory(); //added by ikill240c
        } //added by ikill240c
    }

    private void nodeGather(@NonNull Building armory, int num_units) {
        // Was getGatherTreePeons().length etc. - GLOBAL counts of gatherers across every armory,
        // compared against MAX_UNITS_GATHERING_*[difficulty], also a global cap. This method is
        // called once PER under-weaponed armory (see its caller's loop), clearly intending "give
        // THIS armory more gatherers" - but once the first armory's own gatherers alone reached
        // the global cap for a resource type, that same global count made the cap check fail for
        // EVERY subsequent armory too, regardless of how few gatherers THAT specific armory
        // actually had. Every additional armory would then never receive a single new gatherer,
        // never get raw materials, and so its weapon production stayed at zero even with orders
        // technically queued - matching the reported "additional armories have weapon production
        // set to 0". getGathererCount(type, building) (already used elsewhere for exactly this -
        // see Player.java) counts only gatherers actually assigned to THIS armory, making the cap
        // check per-armory instead. //added by ikill240c
        // Was four separate calls to getGathererCount(), each independently re-scanning every
        // unit this player owns - getGathererCounts() (plural) computes all four in a single
        // pass instead, since a growing army over the course of a match meant this compounded
        // into a real, worsening cost specifically under high player/unit counts (matching
        // reports of stuttering that gets progressively worse as a match goes on).
        // //added by ikill240c
        int[] gatherer_counts = getOwner().getGathererCounts(armory); //added by ikill240c
        int tree = gatherer_counts[0]; //added by ikill240c
        int rock = gatherer_counts[1]; //added by ikill240c
        int iron = gatherer_counts[2]; //added by ikill240c
        int rubber = gatherer_counts[3]; //added by ikill240c

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

    // Distributes surplus peons from ALL quarters across ALL of the given understaffed armories,
    // round-robin by quarters index, rather than every quarters rallying to the same single
    // armory. Previously took one Building and set every quarters' rally point to that SAME
    // armory - meaning peon production could only ever feed one armory at a time, leaving every
    // other armory to rely solely on its own already-trained stock (see
    // nodeDeployUnitsInArmory's per-armory fair-share cap for the matching fix on the warrior-
    // deployment side of this same "only one building actually gets used" symptom).
    // //added by ikill240c
    // Persistent across calls (not a local variable) so the round-robin actually rotates through
    // every armory over successive calls, rather than restarting at 0 every single time this
    // method runs. That distinction matters a lot when there are fewer quarters than armories (a
    // common case - quarters are pricier and built less freely than armories): the inner loop
    // below only iterates once per QUARTERS, so with, say, 1 quarters and 3 armories, a
    // call-local index would only ever reach armory_index=0 before the single iteration ends,
    // meaning armories 1 and 2 never received a rally point or a peon transfer at all - matching
    // reports of additional armories producing far less than the first one. A persistent field
    // means each new call continues the rotation where the last one left off, so every armory
    // eventually gets its turn across enough calls even when there's only ever one quarters
    // active per call. //added by ikill240c
    private int transfer_armory_index = 0; //added by ikill240c

    private void nodeTransferUnits(int num_units, java.util.@NonNull List<Building> understaffed_armories) { //added by ikill240c
        if (getQuarters() == null || getQuarters().length == 0) {
            nodeBuildQuarters();
            return;
        }
        if (understaffed_armories.isEmpty()) //added by ikill240c
            return; //added by ikill240c

        int remaining = num_units;
        for (Selectable<?> q : getQuarters()) {
            if (remaining <= 0)
                break;
            Building quarters = (Building) q;
            if (quarters.isDead())
                continue;
            // Round-robins which armory THIS quarters rallies to, so surplus peons from different
            // quarters actually spread across different armories instead of all converging on one.
            // Was unconditional every single call regardless of whether a transfer actually
            // happened this time, forcing this quarters' rally point back to the armory on every
            // tick this node runs - which fought a human teammate's own rally-point choice on an
            // ally's quarters (now controllable - see the ally-building/unit selection support)
            // the instant they set one, since this ran again moments later and stomped it back.
            // Two changes: only set the rally point when a transfer is ACTUALLY about to happen
            // this call (moved inside the surplus check below, rather than firing every call even
            // when nothing gets sent), and skip it entirely if a human recently directly commanded
            // this quarters (setRallyPoint itself goes through Player.isValid(), which marks
            // human_override_until_tick on cross-player orders - see its own comment) - that
            // means they just set their own rally point deliberately, so their choice should
            // stick rather than being immediately overridden by this AI's own logic.
            // //added by ikill240c
            int tick = getOwner().getWorld().getTick(); //added by ikill240c
            boolean quarters_human_overridden = quarters.getHumanOverrideUntilTick() > tick; //added by ikill240c
            Building armory = understaffed_armories.get(transfer_armory_index % understaffed_armories.size()); //added by ikill240c - was a call-local armory_index; see this field's own comment for why that under-rotated
            transfer_armory_index++; //added by ikill240c
            int surplus = quarters.getUnitContainer().getNumSupplies() - MIN_UNITS_REPRODUCING[difficulty];
            if (surplus > 0) {
                if (!quarters_human_overridden) //added by ikill240c
                    quarters.setRallyPoint(armory); //added by ikill240c - moved inside this check, and only if not recently human-overridden; was unconditional above
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
            // Concentrate early economic growth rather than spreading it thin: each Quarters has
            // its own independent reproduction pool that starts back at the slow, near-zero-supply
            // floor rate (ReproduceUnitContainer.animate() scales reproduction off THIS building's
            // own current stored supply), so committing builder peons to a 3rd+ Quarters before any
            // existing one has actually crossed its own peon reserve just gambles builder
            // investment across more slow-starting pools instead of letting an already-working one
            // build momentum. Only applies once there are already 2+ Quarters (the 2nd is still
            // allowed unconditionally, alongside the Armory-first ordering already enforced above)
            // - from the 3rd onward, require proof that this economy has actually gotten off the
            // ground before further diluting it. Left untouched: the underlying reproduction rate
            // formula itself, since that's shared with human-controlled Quarters too and not
            // something to change just for the AI. //added by ikill240c
            if (existing >= 2 && !anyQuartersMetReserve()) //added by ikill240c
                return; //added by ikill240c

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

    // True once at least one of this AI's own (alive) Quarters has built its stored peon supply up
    // to or past its own reserve threshold - i.e. this economy has actually gotten off the ground,
    // rather than still being stuck at the slow, near-zero-supply reproduction floor. Used to gate
    // further Quarters expansion above - see nodeBuildQuarters(). //added by ikill240c
    private boolean anyQuartersMetReserve() { //added by ikill240c
        if (getQuarters() == null) //added by ikill240c
            return false; //added by ikill240c
        int reserve = MIN_UNITS_REPRODUCING[difficulty]; //added by ikill240c
        for (Selectable<?> q : getQuarters()) { //added by ikill240c
            if (q.isDead()) //added by ikill240c
                continue; //added by ikill240c
            Building quarters = (Building) q; //added by ikill240c
            if (quarters.getUnitContainer().getNumSupplies() >= reserve) //added by ikill240c
                return true; //added by ikill240c
        } //added by ikill240c
        return false; //added by ikill240c
    } //added by ikill240c

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
        if (ship.isDead() || !ship.isComplete())
            return;

        // Resume/resolve any exploration voyage already in progress for this ship BEFORE the
        // isMoving() early-return below, so an arriving ship still gets a chance to unload even
        // though it's the isMoving() check itself that used to gate the whole rest of this method.
        // //added by ikill240p 2026-09-14
        ExplorationTarget exploring = ship_exploration.get(ship); //added by ikill240p 2026-09-14
        if (exploring != null) { //added by ikill240p 2026-09-14
            if (ship.isMoving()) //added by ikill240p 2026-09-14 - still sailing there; nothing to (re)decide until it stops
                return; //added by ikill240p 2026-09-14
            // One-shot regardless of outcome: whether it actually reaches the island and unloads
            // below, or stopped short (unreachable/blocked path), this voyage attempt is over either
            // way - falling through afterward lets the normal at_home logic sail the ship back home.
            // //added by ikill240p 2026-09-14
            ship_exploration.remove(ship); //added by ikill240p 2026-09-14
            if (closeToIsland(ship, exploring.island_id())) //added by ikill240p 2026-09-14
                deployCrewAtIsland(ship, exploring); //added by ikill240p 2026-09-14
        }

        if (ship.isMoving())
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

            // No already-known nearby enemy (neither on the open sea nor standing on a beach) - rather
            // than let a fully-crewed, healthy ship sit at home doing nothing forever, send it out to
            // find something useful to do on another island: attack one with a known enemy presence
            // anywhere on it, or failing that, ferry peons to the richest not-yet-claimed resource
            // island. Only launched from home, same as how the ship only re-engages once fully
            // crewed and at_home in the first place. //added by ikill240p 2026-09-14
            if (at_home) { //added by ikill240p 2026-09-14
                ExplorationTarget target = chooseExplorationIsland(); //added by ikill240p 2026-09-14
                if (target != null) { //added by ikill240p 2026-09-14
                    sailToIsland(ship, target); //added by ikill240p 2026-09-14
                    ship_exploration.put(ship, target); //added by ikill240p 2026-09-14
                    return; //added by ikill240p 2026-09-14
                }
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

    // Picks a worthwhile island (other than home) for an idle, battle-ready ship to sail to when
    // there is no already-known nearby enemy to fight. Islands with an enemy presence anywhere on
    // them are preferred - so the fleet actively goes hunting instead of only ever reacting to
    // whatever happens to wander onto a beach - and failing that, the richest not-yet-claimed
    // (no own building on it) resource island is picked, so idle peons have somewhere new to
    // gather from instead of the ship just sitting at home forever. Returns null if nothing on the
    // map is currently worth a special voyage. //added by ikill240p 2026-09-14
    private @Nullable ExplorationTarget chooseExplorationIsland() { //added by ikill240p 2026-09-14
        int home_island = homeIsland(); //added by ikill240p 2026-09-14
        int best_resource_island = -1; //added by ikill240p 2026-09-14
        int best_resource_amount = MIN_ISLAND_RESOURCES; //added by ikill240p 2026-09-14 - a candidate must beat this to be worth the trip
        for (int island_id : getUnitGrid().getIslandIds()) { //added by ikill240p 2026-09-14
            if (island_id == home_island) //added by ikill240p 2026-09-14 - never "explore" our own home island
                continue; //added by ikill240p 2026-09-14
            if (islandHasOwnBuilding(island_id)) //added by ikill240p 2026-09-14 - already claimed/settled by us - nothing new to do there
                continue; //added by ikill240p 2026-09-14
            if (islandHasEnemy(island_id)) //added by ikill240p 2026-09-14 - enemy presence always takes priority over any resource island
                return new ExplorationTarget(island_id, true); //added by ikill240p 2026-09-14
            var info = getUnitGrid().getIslandInfo(island_id); //added by ikill240p 2026-09-14
            if (info == null) //added by ikill240p 2026-09-14 - defensive: an id from getIslandIds() should always resolve, but don't trust it blindly
                continue; //added by ikill240p 2026-09-14
            int resources = info.trees() + info.rocks() + info.iron(); //added by ikill240p 2026-09-14
            if (resources > best_resource_amount) { //added by ikill240p 2026-09-14
                best_resource_amount = resources; //added by ikill240p 2026-09-14
                best_resource_island = island_id; //added by ikill240p 2026-09-14
            }
        }
        if (best_resource_island != -1) //added by ikill240p 2026-09-14
            return new ExplorationTarget(best_resource_island, false); //added by ikill240p 2026-09-14
        return null; //added by ikill240p 2026-09-14 - nothing worth a special voyage right now
    }

    // Whether this player already has a (living) building on the given island - used to skip
    // islands we've already settled when picking a fresh resource island to explore.
    // //added by ikill240p 2026-09-14
    private boolean islandHasOwnBuilding(int island_id) { //added by ikill240p 2026-09-14
        for (Selectable<?> s : getOwner().getUnits().getSet()) { //added by ikill240p 2026-09-14
            if (s instanceof Building building && !building.isDead() && building.getIslandId() == island_id) //added by ikill240p 2026-09-14
                return true; //added by ikill240p 2026-09-14
        }
        return false; //added by ikill240p 2026-09-14
    }

    // Whether any enemy of ours has a living unit or building on the given island. Deliberately a
    // full, un-fogged scan across every enemy player's roster - the same omniscient style already
    // used by Player.findNearestEnemy()/findNearestEnemyShip() elsewhere in this AI, not a new
    // fog-of-war-breaking assumption. //added by ikill240p 2026-09-14
    private boolean islandHasEnemy(int island_id) { //added by ikill240p 2026-09-14
        for (Player player : getOwner().getWorld().getPlayers()) { //added by ikill240p 2026-09-14
            if (!getOwner().isEnemy(player)) //added by ikill240p 2026-09-14
                continue; //added by ikill240p 2026-09-14
            for (Selectable<?> s : player.getUnits().getSet()) { //added by ikill240p 2026-09-14
                if (!s.isDead() && s.getIslandId() == island_id) //added by ikill240p 2026-09-14
                    return true; //added by ikill240p 2026-09-14
            }
        }
        return false; //added by ikill240p 2026-09-14
    }

    // Nearest (to home) living enemy Selectable located on the given island - used to aim a ship's
    // initial voyage at an actual target so it sails to engage exactly like the existing
    // known-beach/sea-enemy combat branch above, rather than at an arbitrary point on the island.
    // //added by ikill240p 2026-09-14
    private @Nullable Selectable<?> findEnemyOnIsland(int island_id) { //added by ikill240p 2026-09-14
        Building home = homeBuilding(); //added by ikill240p 2026-09-14
        int home_x = home != null ? home.getGridX() : 0; //added by ikill240p 2026-09-14
        int home_y = home != null ? home.getGridY() : 0; //added by ikill240p 2026-09-14
        Selectable<?> best = null; //added by ikill240p 2026-09-14
        int best_dist_squared = Integer.MAX_VALUE; //added by ikill240p 2026-09-14
        for (Player player : getOwner().getWorld().getPlayers()) { //added by ikill240p 2026-09-14
            if (!getOwner().isEnemy(player)) //added by ikill240p 2026-09-14
                continue; //added by ikill240p 2026-09-14
            for (Selectable<?> s : player.getUnits().getSet()) { //added by ikill240p 2026-09-14
                if (s.isDead() || s.getIslandId() != island_id) //added by ikill240p 2026-09-14
                    continue; //added by ikill240p 2026-09-14
                int dx = s.getGridX() - home_x; //added by ikill240p 2026-09-14
                int dy = s.getGridY() - home_y; //added by ikill240p 2026-09-14
                int dist_squared = dx * dx + dy * dy; //added by ikill240p 2026-09-14
                if (dist_squared < best_dist_squared) { //added by ikill240p 2026-09-14
                    best_dist_squared = dist_squared; //added by ikill240p 2026-09-14
                    best = s; //added by ikill240p 2026-09-14
                }
            }
        }
        return best; //added by ikill240p 2026-09-14
    }

    // Sends a ship on its way to the chosen exploration target: for a hostile island, sails
    // straight at the nearest enemy there using the exact same "sail to engage" call already used
    // above for a known beach/sea enemy; for a resource island (no known enemy), sails toward the
    // island's designated start point instead - water pathfinding is expected to stop the ship at
    // the nearest reachable shore tile short of actual land, the same way it already must for any
    // other water-bound move order aimed at a land coordinate. //added by ikill240p 2026-09-14
    private void sailToIsland(@NonNull Ship ship, @NonNull ExplorationTarget target) { //added by ikill240p 2026-09-14
        if (target.hostile()) { //added by ikill240p 2026-09-14
            Selectable<?> enemy = findEnemyOnIsland(target.island_id()); //added by ikill240p 2026-09-14
            if (enemy != null) { //added by ikill240p 2026-09-14
                getOwner().setTarget(Selectable.newArray(ship), enemy, Action.MOVE, true); //added by ikill240p 2026-09-14
                return; //added by ikill240p 2026-09-14
            }
            // Enemy died between being spotted and now - fall through to the plain landscape target
            // below rather than leaving the ship with no order at all. //added by ikill240p 2026-09-14
        }
        var info = getUnitGrid().getIslandInfo(target.island_id()); //added by ikill240p 2026-09-14
        if (info == null) //added by ikill240p 2026-09-14
            return; //added by ikill240p 2026-09-14
        getOwner().setLandscapeTarget(Selectable.newArray(ship), info.startX(), info.startY(), Action.MOVE, false); //added by ikill240p 2026-09-14
    }

    // Whether a ship has gotten close enough to its target island's start point to be considered
    // "arrived" and ready to unload crew. //added by ikill240p 2026-09-14
    private boolean closeToIsland(@NonNull Ship ship, int island_id) { //added by ikill240p 2026-09-14
        var info = getUnitGrid().getIslandInfo(island_id); //added by ikill240p 2026-09-14
        if (info == null) //added by ikill240p 2026-09-14
            return false; //added by ikill240p 2026-09-14
        int dx = ship.getGridX() - info.startX(); //added by ikill240p 2026-09-14
        int dy = ship.getGridY() - info.startY(); //added by ikill240p 2026-09-14
        return dx * dx + dy * dy <= ISLAND_ARRIVAL_RANGE * ISLAND_ARRIVAL_RANGE; //added by ikill240p 2026-09-14
    }

    // Lands some of the ship's crew on the island it just arrived at: warriors for a hostile island
    // (the ship itself, with whatever crew remains aboard, also keeps fighting from just offshore
    // via the existing combat branch above - this just adds reinforcements ashore), or peons for a
    // peaceful resource island. Deliberately one-shot per voyage (see the removal in useShip()
    // right before this is called) rather than re-issued every AI tick: Ship.deployUnits() queues
    // production through the same throttled DeployContainer used by buildings (one unit roughly
    // every "seconds_per_deploy"), and calling it repeatedly before a previous order has drained
    // would keep adding to that queue - a one-shot call per voyage sidesteps that entirely. Once
    // ashore, the disembarked units need no further wiring: nodeAssignIdleGatherers() and the
    // attack-orchestration nodes already scan the player's ENTIRE roster for idle units regardless
    // of location, so they pick these up exactly as if they'd walked here from home.
    // //added by ikill240p 2026-09-14
    private void deployCrewAtIsland(@NonNull Ship ship, @NonNull ExplorationTarget target) { //added by ikill240p 2026-09-14
        var info = getUnitGrid().getIslandInfo(target.island_id()); //added by ikill240p 2026-09-14
        if (info == null) //added by ikill240p 2026-09-14
            return; //added by ikill240p 2026-09-14
        ship.setRallyPoint(new LandscapeTarget(info.startX(), info.startY())); //added by ikill240p 2026-09-14 - newly-landed crew walk here, onto the island itself, instead of milling around next to the ship

        if (target.hostile()) { //added by ikill240p 2026-09-14
            int warriors_aboard = ship.getShipHR().countUnits() - ship.getShipHR().countPeons(); //added by ikill240p 2026-09-14
            // Was DeployType.IRON_WARRIOR only - ShipHR.exitUnit() (what this ultimately calls)
            // matches by exact weapon template, so if the warriors actually aboard were Rock or
            // Rubber armed (very likely early on, before Iron is available at all), this found
            // nothing to deploy and silently landed no one, despite warriors_aboard > 0 and the
            // call appearing to succeed - matching reports of the AI still not deploying warriors
            // to other islands. Now tries all three weapon types, exactly like
            // AdvancedAI.nodeGuardTowers()/nodeBuildWeapons() already do elsewhere in this class
            // when they don't know in advance which weapon type an armory/tower actually has.
            // //added by ikill240c
            if (warriors_aboard > 0) { //added by ikill240p 2026-09-14
                getOwner().deployUnits(ship, DeployType.ROCK_WARRIOR, Math.min(EXPLORE_WARRIOR_BATCH, warriors_aboard)); //added by ikill240c
                getOwner().deployUnits(ship, DeployType.IRON_WARRIOR, Math.min(EXPLORE_WARRIOR_BATCH, warriors_aboard)); //added by ikill240p 2026-09-14
                getOwner().deployUnits(ship, DeployType.RUBBER_WARRIOR, Math.min(EXPLORE_WARRIOR_BATCH, warriors_aboard)); //added by ikill240c
            } //added by ikill240p 2026-09-14
        } else { //added by ikill240p 2026-09-14
            int peons_aboard = ship.getShipHR().countPeons(); //added by ikill240p 2026-09-14
            if (peons_aboard > 0) //added by ikill240p 2026-09-14
                getOwner().deployUnits(ship, DeployType.PEON, Math.min(EXPLORE_PEON_BATCH, peons_aboard)); //added by ikill240p 2026-09-14
        }
    }

    private void nodeBuildShip(int fleet_size) {
        Ship ship = getIncompleteShip();
        if (ship == null && fleet_size >= FLEET_SIZE)
            return;

        Selectable<?>[] idle = getIdlePeons();
        int idle_count = idle != null ? idle.length : 0;

        int missing = SHIP_BUILDERS - (ship != null ? countBuilders(ship.getEntrance()) : 0);

        if (missing > idle_count) {
            // Pull surplus peons from EVERY Quarters/Armory in turn, not just the first one built -
            // with multiple Quarters/Armories, index 0 alone often has no surplus while a later one
            // does, and this call used to unconditionally index getArmory()[0] even when
            // getArmory() was null (no armory built yet), which would have crashed with an NPE.
            // //added by ikill240c 2026-09-11
            int remaining = missing - idle_count;
            if (getQuarters() != null) {
                for (Selectable<?> q : getQuarters()) {
                    if (remaining <= 0)
                        break;
                    Building quarters = (Building) q;
                    remaining -= deployPeonsFromQuarters(quarters, remaining, quarters);
                }
            }
            if (getArmory() != null) {
                for (Selectable<?> a : getArmory()) {
                    if (remaining <= 0)
                        break;
                    Building armory = (Building) a;
                    remaining -= deployPeonsFromQuarters(armory, remaining, armory);
                }
            }
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
        // Loop every Quarters instead of only ever pulling from getQuarters()[0], so a ship can
        // still get crewed when the first-built Quarters has no surplus peons but a later one
        // does. //added by ikill240c 2026-09-11
        int remaining = num_peons;
        for (Selectable<?> q : getQuarters()) {
            if (remaining <= 0)
                break;
            remaining -= deployPeonsFromQuarters((Building) q, remaining, ship);
        }
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

    // Keep each armory's weapon stock at roughly this multiple of the deploy-time minimum
    // (MIN_WEAPONS_IN_STOCK) rather than right at it, so there's always a buffer being produced
    // ahead of actual warrior deployment instead of racing to keep up with it.
    // //added by ikill240c
    private static final int WEAPON_STOCK_TARGET_MULTIPLIER = 3; //added by ikill240c
    // Modest per-order batch size, not the whole target at once - orders are additive
    // (buildWeapons() with infinite=false just adds to whatever's already queued, it doesn't set
    // an absolute target), so a small batch re-issued once stock/orders run low keeps production
    // going without ever risking a runaway pile of stacked orders. //added by ikill240c
    private static final int WEAPON_BUILD_BATCH = 10; //added by ikill240c

    // Previously nothing in this AI ever called Building.buildWeapons() at all - peons were sent
    // to gather materials AT an armory (see nodeGather()), but the armory itself was never
    // actually told to convert those materials into weapons. Without weapons ever being produced,
    // numWeapons(armory) never rises above MIN_WEAPONS_IN_STOCK, so
    // nodeDeployUnitsInArmory()'s own weapon-stock check never passes either - armories would
    // accumulate gatherers and raw materials indefinitely while never producing a single weapon or
    // deploying a single warrior. //added by ikill240c
    private void nodeBuildWeapons() { //added by ikill240c
        if (getArmory() == null) //added by ikill240c
            return;
        int target = MIN_WEAPONS_IN_STOCK[difficulty] * WEAPON_STOCK_TARGET_MULTIPLIER; //added by ikill240c
        for (Selectable<?> a : getArmory()) { //added by ikill240c
            if (!(a instanceof Building armory) || armory.isDead()) //added by ikill240c
                continue;
            if (numWeapons(armory) >= target) //added by ikill240c
                continue; // already well-stocked, nothing to top up //added by ikill240c
            // Don't re-issue if something is already queued for any of the three types - orders
            // are additive, so re-checking every tick without this guard would stack a fresh batch
            // on top of an already-in-progress one indefinitely. //added by ikill240c
            boolean already_building = armory.getBuildSupplyContainer(RockAxeWeapon.class).getNumOrders() > 0 //added by ikill240c
                    || armory.getBuildSupplyContainer(IronAxeWeapon.class).getNumOrders() > 0 //added by ikill240c
                    || armory.getBuildSupplyContainer(RubberAxeWeapon.class).getNumOrders() > 0; //added by ikill240c
            if (already_building) //added by ikill240c
                continue;
            // Orders all three types rather than trying to guess which raw material is most
            // abundant at this specific armory - an order for a material this armory doesn't
            // currently have is harmless (that queue simply doesn't progress until the material
            // arrives), so this naturally covers whatever mix of resources each armory actually
            // has instead of needing to inspect its stockpiles first. //added by ikill240c
            armory.buildWeapons(RockAxeWeapon.class, WEAPON_BUILD_BATCH, false); //added by ikill240c
            armory.buildWeapons(IronAxeWeapon.class, WEAPON_BUILD_BATCH, false); //added by ikill240c
            armory.buildWeapons(RubberAxeWeapon.class, WEAPON_BUILD_BATCH, false); //added by ikill240c
        } //added by ikill240c
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
            int strength = cachedPlayerStatus(p); //added by ikill240c - was p.getStatus() directly (uncached); see ensureCacheFresh()'s comment for why
            // Was strength / 100.0 - getStatus() sums getStatusValue() over EVERY unit AND
            // building a player owns (see Player.getStatus()'s own implementation), so a
            // developed enemy's strength routinely reaches into the thousands. Divided by 100,
            // that meant a strength of 1000 alone multiplied effective distance by 11x, and 5000
            // by 51x - strong enough that a much weaker enemy on the far side of the map would
            // always outscore a strong one right next door (e.g. dist=100/strength=500 scored 600,
            // while dist=500/strength=10 scored only 550 and would still "win"). That's what made
            // every strategy sharing this method (main attack, best-move, multi-front - see the
            // status_cache field comment above) converge on the same weakest-anywhere target
            // instead of ever meaningfully weighing proximity, matching reports that the AI only
            // ever attacks the weakest enemy. Raised 10x so strength still breaks ties and nudges
            // toward softer targets, but no longer overwhelms distance outright. //added by ikill240c
            double score = dist * (1.0 + strength / 1000.0); //added by ikill240c
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

    // Radius within which enemy towers are counted as "defending" a candidate target - wider than
    // TOWER_SCAN_RANGE (which is for placing the AI's OWN towers on safe terrain, a different
    // purpose) since this needs to cover a tower's actual attack range plus enough buffer that an
    // approaching attack force would already be under fire from multiple towers at once, not just
    // the one closest to the target itself. //added by ikill240c
    private static final float ENEMY_TOWER_DEFENSE_RADIUS = 40f; //added by ikill240c og 30
    // How many warriors it takes to justify pushing through one nearby enemy tower - e.g. a value
    // of 4 means a target defended by 3 towers is only attempted once the force is at least 12
    // warriors strong. Deliberately a ratio rather than a flat tower-count cap, so a bigger raiding
    // party can still take on a more heavily towered target instead of every group being held to
    // the same absolute limit. //added by ikill240c
    private static final int WARRIORS_PER_TOWER_TO_ENGAGE = 7; //added by ikill240c og 4

    // Counts enemy towers within ENEMY_TOWER_DEFENSE_RADIUS of a point - used by findTarget() to
    // avoid picking a target sitting in the middle of a defended tower cluster unless the
    // attacking force is actually large enough to push through it. //added by ikill240c
    private int countNearbyEnemyTowers(int grid_x, int grid_y) { //added by ikill240c
        float world_x = com.oddlabs.tt.pathfinder.UnitGrid.coordinateFromGrid(grid_x); //added by ikill240c
        float world_y = com.oddlabs.tt.pathfinder.UnitGrid.coordinateFromGrid(grid_y); //added by ikill240c
        FindOccupantFilter<Building> filter = new FindOccupantFilter<>(world_x, world_y, //added by ikill240c
                ENEMY_TOWER_DEFENSE_RADIUS, null, Building.class); //added by ikill240c
        getUnitGrid().scan(filter, grid_x, grid_y); //added by ikill240c
        int count = 0; //added by ikill240c
        for (Building building : filter.getResult()) { //added by ikill240c
            if (!building.isDead() && isTowerTemplate(building) //added by ikill240c
                    && getOwner().isEnemy(building.getOwner())) { //added by ikill240c
                count++; //added by ikill240c
            } //added by ikill240c
        } //added by ikill240c
        return count; //added by ikill240c
    }

    private @Nullable Target findTarget(int start_x, int start_y) {//added by ikill240c
        return findTarget(start_x, start_y, Integer.MAX_VALUE); //added by ikill240c - MAX_VALUE: callers that don't pass a force size get the old "no tower-avoidance" behavior, rather than every existing call site needing an update
    }

    // num_warriors is the size of the force this target is being picked FOR, used to decide
    // whether a heavily tower-defended building candidate is worth attempting - see
    // WARRIORS_PER_TOWER_TO_ENGAGE. //added by ikill240c
    private @Nullable Target findTarget(int start_x, int start_y, int num_warriors) {//added by ikill240c
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
        // If the nearest building is defended by more towers than this force can reasonably push
        // through, prefer the nearest unit target instead - not blocked outright (an AI that can
        // never attack anything looks more broken than one that occasionally attacks something
        // heavily defended), just deprioritized in favor of a softer target when one exists.
        // //added by ikill240c
        if (best_building != null //added by ikill240c
                && countNearbyEnemyTowers(best_building.getGridX(), best_building.getGridY()) //added by ikill240c
                        * WARRIORS_PER_TOWER_TO_ENGAGE > num_warriors) { //added by ikill240c
            if (best_target != null) //added by ikill240c
                return best_target; //added by ikill240c
            // No softer target exists either - fall through to the normal distance-based choice
            // below rather than returning null and leaving this force with nothing to do at all.
            // //added by ikill240c
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
            getOwner().placeBuilding(selection, building_type, target.getGridX(), target.getGridY(), false); //added by ikill240c - AI always places immediately, never queued (that's a shift-click UI-only concept)
            return true;
        } else {
            return false;
        }
    }
}
