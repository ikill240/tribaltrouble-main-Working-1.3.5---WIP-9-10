package com.oddlabs.tt.landscape;

import com.oddlabs.matchmaking.Game;
import com.oddlabs.matchmaking.GameMode;
import com.oddlabs.tt.model.RacesResources; //added by ikill240c
import org.jspecify.annotations.NonNull;

import java.io.Serial;
import java.io.Serializable;

public final class WorldParameters implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    // Default resource-tower count, matching the TerrainMenu pulldown default. //added by ikill240c 2026-09-09 23:55
    public static final int DEFAULT_NUM_RESOURCE_TOWERS = 7; //added by ikill240c 2026-09-09 23:55
    // Sentinel: let World pick the tower count with a match-seeded roll that is identical on every
    // client. Never use java.util.Random/ThreadLocalRandom directly for simulation-visible values -
    // each peer would roll a different number and the lockstep simulation would desync.
    //added by ikill240c 2026-09-09 23:55
    public static final int NUM_RESOURCE_TOWERS_RANDOM = -1; //added by ikill240c 2026-09-09 23:55
    // Roll bounds used when NUM_RESOURCE_TOWERS_RANDOM is requested. //added by ikill240c 2026-09-09 23:55
    public static final int MIN_RANDOM_RESOURCE_TOWERS = 1; //added by ikill240c 2026-09-09 23:55
    public static final int MAX_RANDOM_RESOURCE_TOWERS = 50; //added by ikill240c 2026-09-09 23:55
    public static final int MIN_KOTH_STATUE_COUNT = 3; //added by ikill240c
    public static final int MAX_KOTH_STATUE_COUNT = 10; //added by ikill240c
    public static final int DEFAULT_KOTH_STATUE_COUNT = 5; //added by ikill240c
    private final @NonNull String map_code;
    private final int initial_unit_count;
    private final int max_unit_count;
    private final int initial_game_speed;
    private final int map_size;
    private final @NonNull GameMode mode;
    // Maximum total chieftains a player can have alive at once. //added by ikill240 2026-09-09 20:49
    private final int max_chieftains; //added by ikill240 2026-09-09 20:49
    // Maximum chieftains a single quarters building can produce. //added by ikill240 2026-09-09 20:49
    private final int max_chieftains_per_quarters; //added by ikill240 2026-09-09 20:49
    // Maximum buildings a player can have alive at once. //added by ikill240c 2026-09-09 22:24
    private final int max_building_count; //added by ikill240c 2026-09-09 22:24
    // How many statues King of the Island's capture objective uses (3-10, see
    // KingOfTheIslandModeRules for how this is actually applied). //added by ikill240c
    private final int koth_statue_count; //added by ikill240c
    // Seconds a chieftain must be idle+undamaged before the AI auto-heals it. //added by ikill240c 2026-09-09 23:10
    private final float chieftain_heal_idle_seconds; //added by ikill240c 2026-09-09 23:10
    // HP restored per heal tick when an idle chieftain auto-heals. //added by ikill240c 2026-09-09 23:10
    private final int chieftain_heal_amount; //added by ikill240c 2026-09-09 23:10
    // How many Quarters the AI aims to build. //added by ikill240c 2026-09-09 23:10
    private final int target_num_quarters; //added by ikill240c 2026-09-09 23:10
    // How many Armories the AI aims to build. //added by ikill240c 2026-09-09 23:10
    private final int target_num_armories; //added by ikill240c 2026-09-09 23:10
    // Max Quarters the AI builds concurrently. //added by ikill240c 2026-09-09 23:10
    private final int max_concurrent_quarters; //added by ikill240c 2026-09-09 23:10
    // Max Armories the AI builds concurrently. //added by ikill240c 2026-09-09 23:10
    private final int max_concurrent_armories; //added by ikill240c 2026-09-09 23:10
    // How many resource-outpost towers the AI builds. //added by ikill240c 2026-09-09 23:10
    private final int num_resource_towers; //added by ikill240c 2026-09-09 23:10
    // Max towers the AI builds concurrently. //added by ikill240c 2026-09-09 23:10
    private final int max_concurrent_towers; //added by ikill240c 2026-09-09 23:10
    // Starting warriors by weapon tier, spawned alongside the initial peons at game start. The
    // original game never spawned any starting warriors at all (every UnitInfo construction site for
    // a regular game hardcoded these to 0), so 0 is the genuine original default, not a guess.
    // //added by ikill240c
    private final int starting_rock_warriors; //added by ikill240c
    private final int starting_iron_warriors; //added by ikill240c
    private final int starting_rubber_warriors; //added by ikill240c
    // Per-magic enable toggles, applied to every player in the match at World construction time (see
    // World.java's player-creation loop). All default to true, matching the game's normal
    // all-enabled state. Index 2 is Convert; Chiefs Courage isn't a magic slot (it's the automatic
    // buff triggered by casting any magic - see Unit.triggerChiefsCourage()), so it gets its own flag
    // rather than a 4th "magic index". //added by ikill240c
    private final boolean magic1_enabled; //added by ikill240c
    private final boolean magic2_enabled; //added by ikill240c
    private final boolean magic3_enabled; //added by ikill240c
    private final boolean chiefs_courage_enabled; //added by ikill240c
    // Global switch: when true, every AI player in the match ignores its manually-chosen
    // Easy/Normal/Hard tier and instead seeds its starting difficulty from the player's persisted
    // historical skill rating (AdaptiveAIProfile) and rebalances itself live during the match (see
    // AdvancedAI.nodeAdaptiveDifficulty()). Kept as a single global toggle rather than a new
    // per-slot pulldown entry, since the per-slot difficulty index also feeds the legacy mapcode
    // encoding and the multiplayer RosterTemplate.Fill enum - a new tier there risks breaking
    // mapcode/network compatibility. //added by ikill240c 2026-09-12
    private final boolean adaptive_ai_enabled; //added by ikill240c 2026-09-12
    // When true, player start positions are grouped so that teammates spawn adjacent to one
    // another instead of the normal fully-random/procedural placement - see Landscape.java's
    // generateUnitLocations() for where this is actually consulted. //added by ikill240c
    private final boolean team_together; //added by ikill240c
    // Magic energy cost (how much must accumulate before casting) per slot. Defaults match Unit.java's
    // original hardcoded MAX_MAGIC_ENERGY values exactly. //added by ikill240c
    private final float magic1_cost; //added by ikill240c
    private final float magic2_cost; //added by ikill240c
    private final float magic3_cost; //added by ikill240c
    // Building max HP multiplier, applied on top of each building's base template HP. //added by ikill240c
    private final float building_health_multiplier; //added by ikill240c
    // Chieftain max HP multiplier, applied per the chieftain's own effective race (the converted
    // unit's original race if it has a magic race override - see Unit.setMagicRaceOverride() - or
    // its owner's race otherwise), not a single blanket value. //added by ikill240c
    private final float viking_chief_health_multiplier; //added by ikill240c
    private final float native_chief_health_multiplier; //added by ikill240c
    // Applies uniformly to every unit's attack/interaction range via Unit.getRange() - covers melee,
    // ranged, and tower-mounted warriors alike, since they all go through that one centralized method.
    // //added by ikill240c
    private final float unit_range_multiplier; //added by ikill240c
    // Per-resource-type armory storage caps, mirroring WorldConfig's fields of the same name so a
    // started game actually carries the values chosen on TerrainMenu's resource-cap sliders.
    // //added by ikill240c 2026-09-12
    private final int armory_resource_cap; //added by ikill240c 2026-09-12
    private final int rock_resource_cap; //added by ikill240c 2026-09-12
    private final int iron_resource_cap; //added by ikill240c 2026-09-12
    private final int rubber_resource_cap; //added by ikill240c 2026-09-12

    public WorldParameters(int initial_game_speed, @NonNull String map_code, int initial_unit_count,
            int max_unit_count) {
        this(initial_game_speed, map_code, initial_unit_count, max_unit_count, Game.SIZE_NONE, GameMode.STANDARD);
    }

    public WorldParameters(int initial_game_speed, @NonNull String map_code, int initial_unit_count, int max_unit_count,
            int map_size) {
        this(initial_game_speed, map_code, initial_unit_count, max_unit_count, map_size, GameMode.STANDARD);
    }

    public WorldParameters(int initial_game_speed, @NonNull String map_code, int initial_unit_count, int max_unit_count,
            int map_size, @NonNull GameMode mode) {
        this(builder().initialGameSpeed(initial_game_speed).mapcode(map_code).initialUnitCount(
                initial_unit_count).maxUnitCount(max_unit_count).mapSize(map_size).mode(mode));
    }

    private WorldParameters(@NonNull Builder b) {
        this.map_code = b.mapcode;
        this.initial_unit_count = b.initial_unit_count;
        this.max_unit_count = b.max_unit_count;
        this.initial_game_speed = b.initial_game_speed;
        this.map_size = b.map_size;
        this.mode = b.mode;
        this.max_chieftains = b.max_chieftains; //added by ikill240 2026-09-09 20:49
        this.max_chieftains_per_quarters = b.max_chieftains_per_quarters; //added by ikill240 2026-09-09 20:49
        this.max_building_count = b.max_building_count; //added by ikill240c 2026-09-09 22:24
        this.koth_statue_count = b.koth_statue_count; //added by ikill240c
        this.chieftain_heal_idle_seconds = b.chieftain_heal_idle_seconds; //added by ikill240c 2026-09-09 23:10
        this.chieftain_heal_amount = b.chieftain_heal_amount; //added by ikill240c 2026-09-09 23:10
        this.target_num_quarters = b.target_num_quarters; //added by ikill240c 2026-09-09 23:10
        this.target_num_armories = b.target_num_armories; //added by ikill240c 2026-09-09 23:10
        this.max_concurrent_quarters = b.max_concurrent_quarters; //added by ikill240c 2026-09-09 23:10
        this.max_concurrent_armories = b.max_concurrent_armories; //added by ikill240c 2026-09-09 23:10
        this.num_resource_towers = b.num_resource_towers; //added by ikill240c 2026-09-09 23:10
        this.max_concurrent_towers = b.max_concurrent_towers; //added by ikill240c 2026-09-09 23:10
        this.starting_rock_warriors = b.starting_rock_warriors; //added by ikill240c
        this.starting_iron_warriors = b.starting_iron_warriors; //added by ikill240c
        this.starting_rubber_warriors = b.starting_rubber_warriors; //added by ikill240c
        this.magic1_enabled = b.magic1_enabled; //added by ikill240c
        this.magic2_enabled = b.magic2_enabled; //added by ikill240c
        this.magic3_enabled = b.magic3_enabled; //added by ikill240c
        this.chiefs_courage_enabled = b.chiefs_courage_enabled; //added by ikill240c
        this.adaptive_ai_enabled = b.adaptive_ai_enabled; //added by ikill240c 2026-09-12
        this.team_together = b.team_together; //added by ikill240c
        this.magic1_cost = b.magic1_cost; //added by ikill240c
        this.magic2_cost = b.magic2_cost; //added by ikill240c
        this.magic3_cost = b.magic3_cost; //added by ikill240c
        this.building_health_multiplier = b.building_health_multiplier; //added by ikill240c
        this.viking_chief_health_multiplier = b.viking_chief_health_multiplier; //added by ikill240c
        this.native_chief_health_multiplier = b.native_chief_health_multiplier; //added by ikill240c
        this.unit_range_multiplier = b.unit_range_multiplier; //added by ikill240c
        this.armory_resource_cap = b.armory_resource_cap; //added by ikill240c 2026-09-12
        this.rock_resource_cap = b.rock_resource_cap; //added by ikill240c 2026-09-12
        this.iron_resource_cap = b.iron_resource_cap; //added by ikill240c 2026-09-12
        this.rubber_resource_cap = b.rubber_resource_cap; //added by ikill240c 2026-09-12
    }

    public static @NonNull Builder builder() {
        return new Builder();
    }

    public @NonNull String getMapcode() {
        return map_code;
    }

    public int getInitialUnitCount() {
        return initial_unit_count;
    }

    public int getMaxUnitCount() {
        return max_unit_count;
    }

    public int getInitialGameSpeed() {
        return initial_game_speed;
    }

    public int getMapSize() {
        return map_size;
    }

    public @NonNull GameMode getGameMode() {
        return mode;
    }

    // Returns the maximum total chieftains a player can have alive at once. //added by ikill240 2026-09-09 20:49
    public int getMaxChieftains() { //added by ikill240 2026-09-09 20:49
        return max_chieftains; //added by ikill240 2026-09-09 20:49
    }

    // Returns the maximum chieftains a single quarters building can produce. //added by ikill240 2026-09-09 20:49
    public int getMaxChieftainsPerQuarters() { //added by ikill240 2026-09-09 20:49
        return max_chieftains_per_quarters; //added by ikill240 2026-09-09 20:49
    }

    // Returns the maximum buildings a player can have alive at once. //added by ikill240c 2026-09-09 22:24
    public int getMaxBuildingCount() { //added by ikill240c 2026-09-09 22:24
        return max_building_count; //added by ikill240c 2026-09-09 22:24
    }

    // Returns how many statues King of the Island's capture objective uses. //added by ikill240c
    public int getKothStatueCount() { //added by ikill240c
        return koth_statue_count; //added by ikill240c
    }

    // Seconds a chieftain must be idle+undamaged before the AI auto-heals it. //added by ikill240c 2026-09-09 23:10
    public float getChieftainHealIdleSeconds() { //added by ikill240c 2026-09-09 23:10
        return chieftain_heal_idle_seconds; //added by ikill240c 2026-09-09 23:10
    }

    // HP restored per heal tick when an idle chieftain auto-heals. //added by ikill240c 2026-09-09 23:10
    public int getChieftainHealAmount() { //added by ikill240c 2026-09-09 23:10
        return chieftain_heal_amount; //added by ikill240c 2026-09-09 23:10
    }

    // How many Quarters the AI aims to build. //added by ikill240c 2026-09-09 23:10
    public int getTargetNumQuarters() { //added by ikill240c 2026-09-09 23:10
        return target_num_quarters; //added by ikill240c 2026-09-09 23:10
    }

    // How many Armories the AI aims to build. //added by ikill240c 2026-09-09 23:10
    public int getTargetNumArmories() { //added by ikill240c 2026-09-09 23:10
        return target_num_armories; //added by ikill240c 2026-09-09 23:10
    }

    // Max Quarters the AI builds concurrently. //added by ikill240c 2026-09-09 23:10
    public int getMaxConcurrentQuarters() { //added by ikill240c 2026-09-09 23:10
        return max_concurrent_quarters; //added by ikill240c 2026-09-09 23:10
    }

    // Max Armories the AI builds concurrently. //added by ikill240c 2026-09-09 23:10
    public int getMaxConcurrentArmories() { //added by ikill240c 2026-09-09 23:10
        return max_concurrent_armories; //added by ikill240c 2026-09-09 23:10
    }

    // How many resource-outpost towers the AI builds. //added by ikill240c 2026-09-09 23:10
    public int getNumResourceTowers() { //added by ikill240c 2026-09-09 23:10
        return num_resource_towers; //added by ikill240c 2026-09-09 23:10
    }

    // Max towers the AI builds concurrently. //added by ikill240c 2026-09-09 23:10
    public int getMaxConcurrentTowers() { //added by ikill240c 2026-09-09 23:10
        return max_concurrent_towers; //added by ikill240c 2026-09-09 23:10
    }

    public int getStartingRockWarriors() { //added by ikill240c
        return starting_rock_warriors;
    }

    public int getStartingIronWarriors() { //added by ikill240c
        return starting_iron_warriors;
    }

    public int getStartingRubberWarriors() { //added by ikill240c
        return starting_rubber_warriors;
    }

    public boolean isMagic1Enabled() { //added by ikill240c
        return magic1_enabled;
    }

    public boolean isMagic2Enabled() { //added by ikill240c
        return magic2_enabled;
    }

    public boolean isMagic3Enabled() { //added by ikill240c
        return magic3_enabled;
    }

    public boolean isChiefsCourageEnabled() { //added by ikill240c
        return chiefs_courage_enabled;
    }

    public boolean isAdaptiveAiEnabled() { //added by ikill240c 2026-09-12
        return adaptive_ai_enabled;
    }

    public boolean isTeamTogether() { //added by ikill240c
        return team_together;
    }

    public float getMagic1Cost() { //added by ikill240c
        return magic1_cost;
    }

    public float getMagic2Cost() { //added by ikill240c
        return magic2_cost;
    }

    public float getMagic3Cost() { //added by ikill240c
        return magic3_cost;
    }

    public float getBuildingHealthMultiplier() { //added by ikill240c
        return building_health_multiplier;
    }

    // Looks up the chief health multiplier for a given race index, rather than making every caller
    // branch on RACE_VIKINGS/RACE_NATIVES themselves. Returns 1.0 (no change) for any race index this
    // game doesn't recognize, rather than throwing - defensive since race indices are plain ints, not
    // an enum the compiler can exhaustively check. //added by ikill240c
    public float getChiefHealthMultiplier(int race_index) { //added by ikill240c
        if (race_index == RacesResources.RACE_VIKINGS)
            return viking_chief_health_multiplier;
        if (race_index == RacesResources.RACE_NATIVES)
            return native_chief_health_multiplier;
        return 1f;
    }

    public float getUnitRangeMultiplier() { //added by ikill240c
        return unit_range_multiplier;
    }

    public int getArmoryResourceCap() { //added by ikill240c 2026-09-12
        return armory_resource_cap;
    }

    public int getRockResourceCap() { //added by ikill240c 2026-09-12
        return rock_resource_cap;
    }

    public int getIronResourceCap() { //added by ikill240c 2026-09-12
        return iron_resource_cap;
    }

    public int getRubberResourceCap() { //added by ikill240c 2026-09-12
        return rubber_resource_cap;
    }

    public static final class Builder {
        private @NonNull String mapcode = "";
        private int initial_unit_count;
        private int max_unit_count;
        private int initial_game_speed;
        private int map_size = Game.SIZE_NONE;
        private @NonNull GameMode mode = GameMode.STANDARD;
        // Default chieftain limits, matching the original hardcoded values. //added by ikill240 2026-09-09 20:49
        private int max_chieftains = 3; //added by ikill240 2026-09-09 20:49
        private int max_chieftains_per_quarters = 1; //added by ikill240 2026-09-09 20:49
        // Default building limit, matching Player.MAX_BUILDING_COUNT (1200). //added by ikill240c 2026-09-09 22:24
        private int max_building_count = 20; //added by ikill240c 2026-09-09 22:24
        private int koth_statue_count = DEFAULT_KOTH_STATUE_COUNT; //added by ikill240c
        // Defaults below match the original hardcoded AdvancedAI constants. //added by ikill240c 2026-09-09 23:10
        private float chieftain_heal_idle_seconds = 10f; //added by ikill240c 2026-09-09 23:10
        private int chieftain_heal_amount = 50; //added by ikill240c 2026-09-09 23:10
        private int target_num_quarters = 3; //added by ikill240c 2026-09-09 23:10
        private int target_num_armories = 1; //added by ikill240c 2026-09-09 23:10
        private int max_concurrent_quarters = 2; //added by ikill240c 2026-09-09 23:10
        private int max_concurrent_armories = 1; //added by ikill240c 2026-09-09 23:10
        // Was a ThreadLocalRandom roll, which gave every client a different value and desynced
        // multiplayer. Fixed default now; pass NUM_RESOURCE_TOWERS_RANDOM for a deterministic roll.
        //added by ikill240c 2026-09-09 23:55
        private int num_resource_towers = DEFAULT_NUM_RESOURCE_TOWERS; //added by ikill240c 2026-09-09 23:55
        private int max_concurrent_towers = 6; //added by ikill240c 2026-09-09 23:10
        // See the field comment on WorldParameters.starting_rock_warriors for why 0 is the genuine
        // original default. //added by ikill240c
        private int starting_rock_warriors = 0; //added by ikill240c
        private int starting_iron_warriors = 0; //added by ikill240c
        private int starting_rubber_warriors = 0; //added by ikill240c
        private boolean magic1_enabled = true; //added by ikill240c
        private boolean magic2_enabled = true; //added by ikill240c
        private boolean magic3_enabled = true; //added by ikill240c
        private boolean chiefs_courage_enabled = true; //added by ikill240c
        // Off by default - opt-in experimental feature. //added by ikill240c 2026-09-12
        private boolean adaptive_ai_enabled = false; //added by ikill240c 2026-09-12
        private boolean team_together = false; //added by ikill240c
        // Defaults match Unit.java's original hardcoded MAX_MAGIC_ENERGY values exactly. //added by ikill240c
        private float magic1_cost = 40f; //added by ikill240c
        private float magic2_cost = 70f; //added by ikill240c
        private float magic3_cost = 120f; //added by ikill240c
        private float building_health_multiplier = 1f; //added by ikill240c
        private float viking_chief_health_multiplier = 1f; //added by ikill240c
        private float native_chief_health_multiplier = 1f; //added by ikill240c
        private float unit_range_multiplier = 1f; //added by ikill240c
        private int armory_resource_cap = 50000; //added by ikill240c 2026-09-12
        private int rock_resource_cap = 50000; //added by ikill240c 2026-09-12
        private int iron_resource_cap = 50000; //added by ikill240c 2026-09-12
        private int rubber_resource_cap = 50000; //added by ikill240c 2026-09-12

        private Builder() {
        }

        public @NonNull Builder mapcode(@NonNull String mapcode) {
            this.mapcode = mapcode;
            return this;
        }

        public @NonNull Builder initialUnitCount(int v) {
            this.initial_unit_count = v;
            return this;
        }

        // Alias used by the custom-options UI (TerrainMenu) for the "Initial units" setting.
        //added by ikill240c 2026-09-09 22:31
        public @NonNull Builder initialUnitCountSetting(int v) { //added by ikill240c 2026-09-09 22:31
            this.initial_unit_count = v; //added by ikill240c 2026-09-09 22:31
            return this; //added by ikill240c 2026-09-09 22:31
        }

        public @NonNull Builder maxUnitCount(int v) {
            this.max_unit_count = v;
            return this;
        }

        public @NonNull Builder initialGameSpeed(int v) {
            this.initial_game_speed = v;
            return this;
        }

        public @NonNull Builder mapSize(int v) {
            this.map_size = v;
            return this;
        }

        public @NonNull Builder mode(@NonNull GameMode mode) {
            this.mode = mode;
            return this;
        }

        // Set the maximum total chieftains a player can have alive at once. //added by ikill240 2026-09-09 20:49
        public @NonNull Builder maxChieftains(int v) { //added by ikill240 2026-09-09 20:49
            this.max_chieftains = v; //added by ikill240 2026-09-09 20:49
            return this; //added by ikill240 2026-09-09 20:49
        }

        // Set the maximum chieftains a single quarters building can produce. //added by ikill240 2026-09-09 20:49
        public @NonNull Builder maxChieftainsPerQuarters(int v) { //added by ikill240 2026-09-09 20:49
            this.max_chieftains_per_quarters = v; //added by ikill240 2026-09-09 20:49
            return this; //added by ikill240 2026-09-09 20:49
        }

        // Set the maximum buildings a player can have alive at once. //added by ikill240c 2026-09-09 22:24
        public @NonNull Builder maxBuildingCount(int v) { //added by ikill240c 2026-09-09 22:24
            this.max_building_count = v; //added by ikill240c 2026-09-09 22:24
            return this; //added by ikill240c 2026-09-09 22:24
        }

        // Set how many statues King of the Island's capture objective uses. Clamped to
        // MIN_KOTH_STATUE_COUNT..MAX_KOTH_STATUE_COUNT here rather than trusting the caller, since
        // this value ends up driving a fixed-size scan/placement loop in
        // KingOfTheIslandModeRules - an out-of-range value there wouldn't fail loudly, it would
        // just place too many or too few statues. //added by ikill240c
        public @NonNull Builder kothStatueCount(int v) { //added by ikill240c
            this.koth_statue_count = Math.clamp(v, MIN_KOTH_STATUE_COUNT, MAX_KOTH_STATUE_COUNT); //added by ikill240c
            return this; //added by ikill240c
        }

        // Seconds a chieftain must be idle+undamaged before the AI auto-heals it. //added by ikill240c 2026-09-09 23:10
        public @NonNull Builder chieftainHealIdleSeconds(float v) { //added by ikill240c 2026-09-09 23:10
            this.chieftain_heal_idle_seconds = v; //added by ikill240c 2026-09-09 23:10
            return this; //added by ikill240c 2026-09-09 23:10
        }

        // HP restored per heal tick when an idle chieftain auto-heals. //added by ikill240c 2026-09-09 23:10
        public @NonNull Builder chieftainHealAmount(int v) { //added by ikill240c 2026-09-09 23:10
            this.chieftain_heal_amount = v; //added by ikill240c 2026-09-09 23:10
            return this; //added by ikill240c 2026-09-09 23:10
        }

        // How many Quarters the AI aims to build. //added by ikill240c 2026-09-09 23:10
        public @NonNull Builder targetNumQuarters(int v) { //added by ikill240c 2026-09-09 23:10
            this.target_num_quarters = v; //added by ikill240c 2026-09-09 23:10
            return this; //added by ikill240c 2026-09-09 23:10
        }

        // How many Armories the AI aims to build. //added by ikill240c 2026-09-09 23:10
        public @NonNull Builder targetNumArmories(int v) { //added by ikill240c 2026-09-09 23:10
            this.target_num_armories = v; //added by ikill240c 2026-09-09 23:10
            return this; //added by ikill240c 2026-09-09 23:10
        }

        // Max Quarters the AI builds concurrently. //added by ikill240c 2026-09-09 23:10
        public @NonNull Builder maxConcurrentQuarters(int v) { //added by ikill240c 2026-09-09 23:10
            this.max_concurrent_quarters = v; //added by ikill240c 2026-09-09 23:10
            return this; //added by ikill240c 2026-09-09 23:10
        }

        // Max Armories the AI builds concurrently. //added by ikill240c 2026-09-09 23:10
        public @NonNull Builder maxConcurrentArmories(int v) { //added by ikill240c 2026-09-09 23:10
            this.max_concurrent_armories = v; //added by ikill240c 2026-09-09 23:10
            return this; //added by ikill240c 2026-09-09 23:10
        }

        // How many resource-outpost towers the AI builds. //added by ikill240c 2026-09-09 23:10
        public @NonNull Builder numResourceTowers(int v) { //added by ikill240c 2026-09-09 23:10
            this.num_resource_towers = v; //added by ikill240c 2026-09-09 23:10
            return this; //added by ikill240c 2026-09-09 23:10
        }

        // Max towers the AI builds concurrently. //added by ikill240c 2026-09-09 23:10
        public @NonNull Builder maxConcurrentTowers(int v) { //added by ikill240c 2026-09-09 23:10
            this.max_concurrent_towers = v; //added by ikill240c 2026-09-09 23:10
            return this; //added by ikill240c 2026-09-09 23:10
        }

        public @NonNull Builder startingRockWarriors(int v) { //added by ikill240c
            this.starting_rock_warriors = v;
            return this;
        }

        public @NonNull Builder startingIronWarriors(int v) { //added by ikill240c
            this.starting_iron_warriors = v;
            return this;
        }

        public @NonNull Builder startingRubberWarriors(int v) { //added by ikill240c
            this.starting_rubber_warriors = v;
            return this;
        }

        public @NonNull Builder magic1Enabled(boolean v) { //added by ikill240c
            this.magic1_enabled = v;
            return this;
        }

        public @NonNull Builder magic2Enabled(boolean v) { //added by ikill240c
            this.magic2_enabled = v;
            return this;
        }

        public @NonNull Builder magic3Enabled(boolean v) { //added by ikill240c
            this.magic3_enabled = v;
            return this;
        }

        public @NonNull Builder chiefsCourageEnabled(boolean v) { //added by ikill240c
            this.chiefs_courage_enabled = v;
            return this;
        }

        public @NonNull Builder adaptiveAiEnabled(boolean v) { //added by ikill240c 2026-09-12
            this.adaptive_ai_enabled = v;
            return this;
        }

        public @NonNull Builder teamTogether(boolean v) { //added by ikill240c
            this.team_together = v;
            return this;
        }

        public @NonNull Builder magic1Cost(float v) { //added by ikill240c
            this.magic1_cost = v;
            return this;
        }

        public @NonNull Builder magic2Cost(float v) { //added by ikill240c
            this.magic2_cost = v;
            return this;
        }

        public @NonNull Builder magic3Cost(float v) { //added by ikill240c
            this.magic3_cost = v;
            return this;
        }

        public @NonNull Builder buildingHealthMultiplier(float v) { //added by ikill240c
            this.building_health_multiplier = v;
            return this;
        }

        public @NonNull Builder vikingChiefHealthMultiplier(float v) { //added by ikill240c
            this.viking_chief_health_multiplier = v;
            return this;
        }

        public @NonNull Builder nativeChiefHealthMultiplier(float v) { //added by ikill240c
            this.native_chief_health_multiplier = v;
            return this;
        }

        public @NonNull Builder unitRangeMultiplier(float v) { //added by ikill240c
            this.unit_range_multiplier = v;
            return this;
        }

        public @NonNull Builder armoryResourceCap(int v) { //added by ikill240c 2026-09-12
            this.armory_resource_cap = v;
            return this;
        }

        public @NonNull Builder rockResourceCap(int v) { //added by ikill240c 2026-09-12
            this.rock_resource_cap = v;
            return this;
        }

        public @NonNull Builder ironResourceCap(int v) { //added by ikill240c 2026-09-12
            this.iron_resource_cap = v;
            return this;
        }

        public @NonNull Builder rubberResourceCap(int v) { //added by ikill240c 2026-09-12
            this.rubber_resource_cap = v;
            return this;
        }

        public @NonNull WorldParameters build() {
            return new WorldParameters(this);
        }
    }
}
