package com.oddlabs.tt.landscape;

import com.oddlabs.matchmaking.Game;
import com.oddlabs.matchmaking.GameMode;
import org.jspecify.annotations.NonNull;

import java.io.Serial;
import java.io.Serializable;

public final class WorldParameters implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    // Default resource-tower count, matching the TerrainMenu pulldown default. //added by ikill240c 2026-09-09 23:55
    public static final int DEFAULT_NUM_RESOURCE_TOWERS = 6; //added by ikill240c 2026-09-09 23:55
    // Sentinel: let World pick the tower count with a match-seeded roll that is identical on every
    // client. Never use java.util.Random/ThreadLocalRandom directly for simulation-visible values -
    // each peer would roll a different number and the lockstep simulation would desync.
    //added by ikill240c 2026-09-09 23:55
    public static final int NUM_RESOURCE_TOWERS_RANDOM = -1; //added by ikill240c 2026-09-09 23:55
    // Roll bounds used when NUM_RESOURCE_TOWERS_RANDOM is requested. //added by ikill240c 2026-09-09 23:55
    public static final int MIN_RANDOM_RESOURCE_TOWERS = 6; //added by ikill240c 2026-09-09 23:55
    public static final int MAX_RANDOM_RESOURCE_TOWERS = 20; //added by ikill240c 2026-09-09 23:55
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
        this.chieftain_heal_idle_seconds = b.chieftain_heal_idle_seconds; //added by ikill240c 2026-09-09 23:10
        this.chieftain_heal_amount = b.chieftain_heal_amount; //added by ikill240c 2026-09-09 23:10
        this.target_num_quarters = b.target_num_quarters; //added by ikill240c 2026-09-09 23:10
        this.target_num_armories = b.target_num_armories; //added by ikill240c 2026-09-09 23:10
        this.max_concurrent_quarters = b.max_concurrent_quarters; //added by ikill240c 2026-09-09 23:10
        this.max_concurrent_armories = b.max_concurrent_armories; //added by ikill240c 2026-09-09 23:10
        this.num_resource_towers = b.num_resource_towers; //added by ikill240c 2026-09-09 23:10
        this.max_concurrent_towers = b.max_concurrent_towers; //added by ikill240c 2026-09-09 23:10
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

    public static final class Builder {
        private @NonNull String mapcode = "";
        private int initial_unit_count;
        private int max_unit_count;
        private int initial_game_speed;
        private int map_size = Game.SIZE_NONE;
        private @NonNull GameMode mode = GameMode.STANDARD;
        // Default chieftain limits, matching the original hardcoded values. //added by ikill240 2026-09-09 20:49
        private int max_chieftains = 5; //added by ikill240 2026-09-09 20:49
        private int max_chieftains_per_quarters = 1; //added by ikill240 2026-09-09 20:49
        // Default building limit, matching Player.MAX_BUILDING_COUNT (1200). //added by ikill240c 2026-09-09 22:24
        private int max_building_count = 1200; //added by ikill240c 2026-09-09 22:24
        // Defaults below match the original hardcoded AdvancedAI constants. //added by ikill240c 2026-09-09 23:10
        private float chieftain_heal_idle_seconds = 3f; //added by ikill240c 2026-09-09 23:10
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

        public @NonNull WorldParameters build() {
            return new WorldParameters(this);
        }
    }
}
