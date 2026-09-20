package com.oddlabs.matchmaking;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import org.jspecify.annotations.NonNull;

import java.io.Serial;
import java.io.Serializable;

/**
 * World-level options a preset captures: the terrain and pacing that exist in every mode. Mode-specific options live in
 * {@link GameModeOptions} instead. Stored as raw UI indices/values so {@code TerrainMenu} can stamp them straight back
 * onto its pulldowns and sliders.
 */
@JsonDeserialize(builder = WorldConfig.Builder.class)
public final class WorldConfig implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final int gamespeed;
    private final int island_size;
    private final int terrain_type;
    private final int hills;
    private final int vegetation;
    private final int supplies;
    // The following mirror WorldParameters' own settings so a preset can actually round-trip them -
    // previously this class only ever carried the original 6 terrain/pacing fields above, so applying
    // a preset (or even just re-saving one) silently reset every custom AI/population setting back to
    // its default, since there was nowhere to persist the value the player had actually chosen.
    // //added by ikill240c
    private final int initial_unit_count; //added by ikill240c
    private final int max_unit_count; //added by ikill240c
    private final int max_chieftains; //added by ikill240c
    private final int max_chieftains_per_quarters; //added by ikill240c
    private final int max_building_count; //added by ikill240c
    private final float chieftain_heal_idle_seconds; //added by ikill240c
    private final int chieftain_heal_amount; //added by ikill240c
    private final int target_num_quarters; //added by ikill240c
    private final int target_num_armories; //added by ikill240c
    private final int max_concurrent_quarters; //added by ikill240c
    private final int max_concurrent_armories; //added by ikill240c
    private final int num_resource_towers; //added by ikill240c
    private final int max_concurrent_towers; //added by ikill240c
    // Mirrors WorldParameters.koth_statue_count so presets can round-trip it too - added
    // alongside King of the Island's statue-count setting, following this class's own established
    // "every WorldParameters field needs a WorldConfig mirror" rule (see class comment above).
    // //added by ikill240c
    private final int koth_statue_count; //added by ikill240c
    private final int starting_rock_warriors; //added by ikill240c
    private final int starting_iron_warriors; //added by ikill240c
    private final int starting_rubber_warriors; //added by ikill240c
    private final boolean magic1_enabled; //added by ikill240c
    private final boolean magic2_enabled; //added by ikill240c
    private final boolean magic3_enabled; //added by ikill240c
    private final boolean chiefs_courage_enabled; //added by ikill240c
    // Mirrors WorldParameters.adaptive_ai_enabled so presets can round-trip it too. //added by ikill240c 2026-09-12
    private final boolean adaptive_ai_enabled; //added by ikill240c 2026-09-12
    // Mirrors WorldParameters.team_together so presets can round-trip it too. //added by ikill240c
    private final boolean team_together; //added by ikill240c
    private final float magic1_cost; //added by ikill240c
    private final float magic2_cost; //added by ikill240c
    private final float magic3_cost; //added by ikill240c
    private final float building_health_multiplier; //added by ikill240c
    private final float viking_chief_health_multiplier; //added by ikill240c
    private final float native_chief_health_multiplier; //added by ikill240c
    private final float unit_range_multiplier; //added by ikill240c
    // Per-resource-type armory storage caps used by the resource-cap sliders on TerrainMenu -
    // missing from this WorldConfig, which caused compile failures against the main module (Builder
    // methods/getters referenced but not defined here). //added by ikill240c 2026-09-12
    private final int armory_resource_cap; //added by ikill240c 2026-09-12
    private final int rock_resource_cap; //added by ikill240c 2026-09-12
    private final int iron_resource_cap; //added by ikill240c 2026-09-12
    private final int rubber_resource_cap; //added by ikill240c 2026-09-12

    private WorldConfig(@NonNull Builder b) {
        this.gamespeed = b.gamespeed;
        this.island_size = b.island_size;
        this.terrain_type = b.terrain_type;
        this.hills = b.hills;
        this.vegetation = b.vegetation;
        this.supplies = b.supplies;
        this.initial_unit_count = b.initial_unit_count; //added by ikill240c
        this.max_unit_count = b.max_unit_count; //added by ikill240c
        this.max_chieftains = b.max_chieftains; //added by ikill240c
        this.max_chieftains_per_quarters = b.max_chieftains_per_quarters; //added by ikill240c
        this.max_building_count = b.max_building_count; //added by ikill240c
        this.chieftain_heal_idle_seconds = b.chieftain_heal_idle_seconds; //added by ikill240c
        this.chieftain_heal_amount = b.chieftain_heal_amount; //added by ikill240c
        this.target_num_quarters = b.target_num_quarters; //added by ikill240c
        this.target_num_armories = b.target_num_armories; //added by ikill240c
        this.max_concurrent_quarters = b.max_concurrent_quarters; //added by ikill240c
        this.max_concurrent_armories = b.max_concurrent_armories; //added by ikill240c
        this.num_resource_towers = b.num_resource_towers; //added by ikill240c
        this.max_concurrent_towers = b.max_concurrent_towers; //added by ikill240c
        this.koth_statue_count = b.koth_statue_count; //added by ikill240c
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

    public static @NonNull WorldConfig defaults() {
        return new Builder().build();
    }

    public int getGamespeed() {
        return gamespeed;
    }

    public int getIslandSize() {
        return island_size;
    }

    public int getTerrainType() {
        return terrain_type;
    }

    public int getHills() {
        return hills;
    }

    public int getVegetation() {
        return vegetation;
    }

    public int getSupplies() {
        return supplies;
    }

    public int getInitialUnitCount() { //added by ikill240c
        return initial_unit_count;
    }

    public int getMaxUnitCount() { //added by ikill240c
        return max_unit_count;
    }

    public int getMaxChieftains() { //added by ikill240c
        return max_chieftains;
    }

    public int getMaxChieftainsPerQuarters() { //added by ikill240c
        return max_chieftains_per_quarters;
    }

    public int getMaxBuildingCount() { //added by ikill240c
        return max_building_count;
    }

    public float getChieftainHealIdleSeconds() { //added by ikill240c
        return chieftain_heal_idle_seconds;
    }

    public int getChieftainHealAmount() { //added by ikill240c
        return chieftain_heal_amount;
    }

    public int getTargetNumQuarters() { //added by ikill240c
        return target_num_quarters;
    }

    public int getTargetNumArmories() { //added by ikill240c
        return target_num_armories;
    }

    public int getMaxConcurrentQuarters() { //added by ikill240c
        return max_concurrent_quarters;
    }

    public int getMaxConcurrentArmories() { //added by ikill240c
        return max_concurrent_armories;
    }

    public int getNumResourceTowers() { //added by ikill240c
        return num_resource_towers;
    }

    public int getMaxConcurrentTowers() { //added by ikill240c
        return max_concurrent_towers;
    }

    public int getKothStatueCount() { //added by ikill240c
        return koth_statue_count;
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

    public float getVikingChiefHealthMultiplier() { //added by ikill240c
        return viking_chief_health_multiplier;
    }

    public float getNativeChiefHealthMultiplier() { //added by ikill240c
        return native_chief_health_multiplier;
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

    @JsonPOJOBuilder(buildMethodName = "build", withPrefix = "")
    public static final class Builder {
        private int gamespeed;
        private int island_size;
        private int terrain_type;
        private int hills;
        private int vegetation;
        private int supplies;
        // Defaults mirror WorldParameters.Builder's own defaults exactly, so a WorldConfig.defaults()
        // preset behaves identically to never having applied a preset at all. //added by ikill240c
        private int initial_unit_count;
        private int max_unit_count;
        private int max_chieftains = 3; //added by ikill240c
        private int max_chieftains_per_quarters = 1; //added by ikill240c
        private int max_building_count = 20; //added by ikill240c
        private float chieftain_heal_idle_seconds = 10f; //added by ikill240c
        private int chieftain_heal_amount = 50; //added by ikill240c
        private int target_num_quarters = 3; //added by ikill240c
        private int target_num_armories = 1; //added by ikill240c
        private int max_concurrent_quarters = 2; //added by ikill240c
        private int max_concurrent_armories = 1; //added by ikill240c
        private int num_resource_towers = 6; //added by ikill240c
        private int max_concurrent_towers = 6; //added by ikill240c
        private int koth_statue_count = 5; //added by ikill240c - matches WorldParameters.DEFAULT_KOTH_STATUE_COUNT; not referenced directly since WorldConfig (in the common/matchmaking module) doesn't depend on WorldParameters (in the tt module) - see this class's own package for why it mirrors fields as plain values instead
        private int starting_rock_warriors = 0; //added by ikill240c
        private int starting_iron_warriors = 0; //added by ikill240c
        private int starting_rubber_warriors = 0; //added by ikill240c
        private boolean magic1_enabled = true; //added by ikill240c
        private boolean magic2_enabled = true; //added by ikill240c
        private boolean magic3_enabled = true; //added by ikill240c
        private boolean chiefs_courage_enabled = true; //added by ikill240c
        private boolean adaptive_ai_enabled = false; //added by ikill240c 2026-09-12
        private boolean team_together = false; //added by ikill240c
        private float magic1_cost = 40f; //added by ikill240c
        private float magic2_cost = 70f; //added by ikill240c
        private float magic3_cost = 120f; //added by ikill240c
        private float building_health_multiplier = 1f; //added by ikill240c
        private float viking_chief_health_multiplier = 1f; //added by ikill240c
        private float native_chief_health_multiplier = 1f; //added by ikill240c
        private float unit_range_multiplier = 1f; //added by ikill240c
        // Defaults match TerrainMenu's DEFAULT_RESOURCE_CAP so an unmodified preset behaves like
        // never having applied one. //added by ikill240c 2026-09-12
        private int armory_resource_cap = 50000; //added by ikill240c 2026-09-12
        private int rock_resource_cap = 50000; //added by ikill240c 2026-09-12
        private int iron_resource_cap = 50000; //added by ikill240c 2026-09-12
        private int rubber_resource_cap = 50000; //added by ikill240c 2026-09-12

        private Builder() {
        }

        public @NonNull Builder gamespeed(int gamespeed) {
            this.gamespeed = gamespeed;
            return this;
        }

        public @NonNull Builder islandSize(int island_size) {
            this.island_size = island_size;
            return this;
        }

        public @NonNull Builder terrainType(int terrain_type) {
            this.terrain_type = terrain_type;
            return this;
        }

        public @NonNull Builder hills(int hills) {
            this.hills = hills;
            return this;
        }

        public @NonNull Builder vegetation(int vegetation) {
            this.vegetation = vegetation;
            return this;
        }

        public @NonNull Builder supplies(int supplies) {
            this.supplies = supplies;
            return this;
        }

        public @NonNull Builder initialUnitCount(int v) { //added by ikill240c
            this.initial_unit_count = v;
            return this;
        }

        public @NonNull Builder maxUnitCount(int v) { //added by ikill240c
            this.max_unit_count = v;
            return this;
        }

        public @NonNull Builder maxChieftains(int v) { //added by ikill240c
            this.max_chieftains = v;
            return this;
        }

        public @NonNull Builder maxChieftainsPerQuarters(int v) { //added by ikill240c
            this.max_chieftains_per_quarters = v;
            return this;
        }

        public @NonNull Builder maxBuildingCount(int v) { //added by ikill240c
            this.max_building_count = v;
            return this;
        }

        public @NonNull Builder chieftainHealIdleSeconds(float v) { //added by ikill240c
            this.chieftain_heal_idle_seconds = v;
            return this;
        }

        public @NonNull Builder chieftainHealAmount(int v) { //added by ikill240c
            this.chieftain_heal_amount = v;
            return this;
        }

        public @NonNull Builder targetNumQuarters(int v) { //added by ikill240c
            this.target_num_quarters = v;
            return this;
        }

        public @NonNull Builder targetNumArmories(int v) { //added by ikill240c
            this.target_num_armories = v;
            return this;
        }

        public @NonNull Builder maxConcurrentQuarters(int v) { //added by ikill240c
            this.max_concurrent_quarters = v;
            return this;
        }

        public @NonNull Builder maxConcurrentArmories(int v) { //added by ikill240c
            this.max_concurrent_armories = v;
            return this;
        }

        public @NonNull Builder numResourceTowers(int v) { //added by ikill240c
            this.num_resource_towers = v;
            return this;
        }

        public @NonNull Builder maxConcurrentTowers(int v) { //added by ikill240c
            this.max_concurrent_towers = v;
            return this;
        }

        public @NonNull Builder kothStatueCount(int v) { //added by ikill240c
            this.koth_statue_count = v;
            return this;
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

        public @NonNull WorldConfig build() {
            return new WorldConfig(this);
        }
    }
}
