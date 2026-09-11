package com.oddlabs.tt.landscape;

import com.oddlabs.matchmaking.GameMode;
import com.oddlabs.tt.animation.AnimationManager;
import com.oddlabs.tt.event.LocalEventQueue;
import com.oddlabs.tt.form.ProgressForm;
import com.oddlabs.tt.global.Settings;
import com.oddlabs.tt.model.AbstractElementNode;
import com.oddlabs.tt.model.RacesResources;
import com.oddlabs.tt.model.Supply;
import com.oddlabs.tt.model.SupplyManager;
import com.oddlabs.tt.model.SupplyManagers;
import com.oddlabs.tt.pathfinder.RegionBuilder;
import com.oddlabs.tt.pathfinder.UnitGrid;
import com.oddlabs.tt.player.Player;
import com.oddlabs.tt.player.PlayerInfo;
import com.oddlabs.tt.procedural.Landscape;
import com.oddlabs.tt.render.RenderQueues;
import com.oddlabs.tt.resource.FogInfo;
import com.oddlabs.tt.resource.WorldInfo;
import org.joml.Vector4fc;

import java.util.List;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Random;

public final class World {
    public static final int GAMESPEED_DONTCARE = -2;

    private static final float[] GAMESPEEDS = new float[]{0f, AnimationManager.ANIMATION_SECONDS_PER_TICK / 2, AnimationManager.ANIMATION_SECONDS_PER_TICK * 2f, AnimationManager.ANIMATION_SECONDS_PER_TICK * 5f, AnimationManager.ANIMATION_SECONDS_PER_TICK * 10 //0f /2 * 1.75 * 4
    };

    private final @NonNull HeightMap world;
    private final @NonNull Random random;
    private final @NonNull AnimationManager animation_manager_game_time;
    private final @NonNull AnimationManager animation_manager_real_time;
    private final @NonNull AudioImplementation audio_impl;

    private final int max_unit_count;
    // Mapcode is identical on every peer, so it is a safe seed for match-wide rolls. //added by ikill240c 2026-09-09 23:55
    private final @NonNull String map_code; //added by ikill240c 2026-09-09 23:55
    // Maximum buildings a player can have. //added by ikill240c 2026-09-10 00:00
    private final int max_building_count; //added by ikill240c 2026-09-10 00:00
    // Number of units each player starts with. //added by ikill240c 2026-09-10 00:00
    private final int initial_unit_count; //added by ikill240c 2026-09-10 00:00
    // Maximum total chieftains a player can have. //added by ikill240 2026-09-09 20:49
    private final int max_chieftains; //added by ikill240 2026-09-09 20:49
    // Maximum chieftains a single quarters can produce. //added by ikill240 2026-09-09 20:49
    private final int max_chieftains_per_quarters; //added by ikill240 2026-09-09 20:49
    // AI tuning parameters, sourced from WorldParameters so they stay in sync across clients. //added by ikill240c 2026-09-09 23:40
    private final float chieftain_heal_idle_seconds; //added by ikill240c 2026-09-09 23:40
    private final int chieftain_heal_amount; //added by ikill240c 2026-09-09 23:40
    private final int target_num_quarters; //added by ikill240c 2026-09-09 23:40
    private final int target_num_armories; //added by ikill240c 2026-09-09 23:40
    private final int max_concurrent_quarters; //added by ikill240c 2026-09-09 23:40
    private final int max_concurrent_armories; //added by ikill240c 2026-09-09 23:40
    private final int num_resource_towers; //added by ikill240c 2026-09-09 23:40
    private final int max_concurrent_towers; //added by ikill240c 2026-09-09 23:40
    private final @NonNull NotificationListener notification_listener;

    private final @NonNull Player @NonNull [] players;
    private final @NonNull SupplyManagers supply_managers;
    private final @NonNull UnitGrid unit_grid;
    private final @NonNull PatchGroup patch_root;
    private final @NonNull AbstractTreeGroup tree_root;
    private final @NonNull List<int[]> treePositions;
    private final @NonNull AbstractElementNode<?> element_root;
    private final @Nullable RacesResources races_resources;
    private final @NonNull LandscapeResources landscape_resources;
    private final @NonNull FogInfo fog;

    private int global_checksum;
    private int gamespeed;
    private int map_size;
    private final @NonNull GameMode mode;

    public static @NonNull LandscapeResources loadCommon(@NonNull RenderQueues queues) {
        LandscapeResources landscape_resources = new LandscapeResources(queues);
        ProgressForm.progress();
        return landscape_resources;
    }

    public static @NonNull RacesResources loadInGame(@NonNull RenderQueues queues) {
        return new RacesResources(queues);
    }

    public static @NonNull World newWorld(@NonNull AudioImplementation audio_implementation,
            @NonNull LandscapeResources landscape_resources, @Nullable RacesResources races_resources,
            @NonNull NotificationListener notification_listener, @NonNull WorldParameters world_params,
            @NonNull WorldInfo world_info, Landscape.@NonNull TerrainType terrain,
            @NonNull PlayerInfo @NonNull [] player_infos, @NonNull FogInfo fog) {
        ProgressForm.progress();
        World world = new World(audio_implementation, landscape_resources, races_resources, notification_listener,
                world_params, world_info, terrain, player_infos, fog);
        ProgressForm.progress();
        ProgressForm.progress(1 / 5f);
        ProgressForm.progress();
        Player[] players = world.getPlayers();
        for (short i = 0; i < players.length; i++) {
            Player player = players[i];
            assert player != null;
            player.init(world_info.starting_locations()[i]);
        }
        return world;
    }

    public com.oddlabs.tt.resource.@NonNull FogInfo getFog() {
        return fog;
    }

    public @NonNull LandscapeResources getLandscapeResources() {
        return landscape_resources;
    }

    public @Nullable RacesResources getRacesResources() {
        return races_resources;
    }

    public @NonNull AudioImplementation getAudio() {
        return audio_impl;
    }

    public int getChecksum() {
        return global_checksum;
    }

    public void updateGlobalChecksum(int value) {
        global_checksum += value;
    }

    public int getGamespeed() {
        return gamespeed;
    }

    public int getMapSize() {
        return map_size;
    }

    public @NonNull GameMode getGameMode() {
        return mode;
    }

    public float getSecondsPerTick() {
        return GAMESPEEDS[gamespeed];
    }

    public static boolean isValidPreferredGamespeed(int speed) {
        return speed == GAMESPEED_DONTCARE || isValidGamespeed(speed);
    }

    public static boolean isValidGamespeed(int speed) {
        return speed >= 0 && speed < GAMESPEEDS.length;
    }

    public void gamespeedChanged() {
        int new_gamespeed = GAMESPEED_DONTCARE;
        for (Player player : players) {
            int gamespeed = player.getPreferredGamespeed();
            if (gamespeed != GAMESPEED_DONTCARE) {
                if (new_gamespeed != GAMESPEED_DONTCARE && gamespeed != new_gamespeed)
                    return;
                new_gamespeed = gamespeed;
            }
        }
        if (new_gamespeed != GAMESPEED_DONTCARE && new_gamespeed != gamespeed) {
            gamespeed = new_gamespeed;
            getNotificationListener().gamespeedChanged(gamespeed);
        }
    }

    public void tick(float t) {
        getAnimationManagerGameTime().runAnimations(
                getSecondsPerTick() * t / AnimationManager.ANIMATION_SECONDS_PER_TICK);
        getAnimationManagerRealTime().runAnimations(t/*AnimationManager.ANIMATION_SECONDS_PER_TICK*/);
    }

    public int getTick() {
        return getAnimationManagerRealTime().getTick();
    }

    private World(@NonNull AudioImplementation audio_implementation, @NonNull LandscapeResources landscape_resources,
            @Nullable RacesResources races_resources, @NonNull NotificationListener notification_listener,
            @NonNull WorldParameters world_params, @NonNull WorldInfo world_info,
            Landscape.@NonNull TerrainType terrain, @NonNull PlayerInfo @NonNull [] player_infos,
            @NonNull FogInfo fog) {
        IO.println(
                "****************** Generating landscape at tick " + LocalEventQueue.getQueue().getHighPrecisionManager().getTick() + " ********************");
        this.fog = fog;
        this.landscape_resources = landscape_resources;
        this.races_resources = races_resources;
        this.audio_impl = audio_implementation;
        // Never let the unit cap sit below the starting unit count, otherwise the initial
        // spawn would overflow the player's supply container. //added by ikill240c 2026-09-10 00:00
        this.max_unit_count = Math.max(world_params.getMaxUnitCount(),
                world_params.getInitialUnitCount()); //added by ikill240c 2026-09-10 00:00
        this.map_code = world_params.getMapcode(); //added by ikill240c 2026-09-09 23:55
        this.max_building_count = world_params.getMaxBuildingCount(); //added by ikill240c 2026-09-10 00:00
        this.initial_unit_count = world_params.getInitialUnitCount(); //added by ikill240c 2026-09-10 00:00
        this.max_chieftains = world_params.getMaxChieftains(); //added by ikill240 2026-09-09 20:49
        this.max_chieftains_per_quarters = world_params.getMaxChieftainsPerQuarters(); //added by ikill240 2026-09-09 20:49
        this.chieftain_heal_idle_seconds = world_params.getChieftainHealIdleSeconds(); //added by ikill240c 2026-09-09 23:40
        this.chieftain_heal_amount = world_params.getChieftainHealAmount(); //added by ikill240c 2026-09-09 23:40
        this.target_num_quarters = world_params.getTargetNumQuarters(); //added by ikill240c 2026-09-09 23:40
        this.target_num_armories = world_params.getTargetNumArmories(); //added by ikill240c 2026-09-09 23:40
        this.max_concurrent_quarters = world_params.getMaxConcurrentQuarters(); //added by ikill240c 2026-09-09 23:40
        this.max_concurrent_armories = world_params.getMaxConcurrentArmories(); //added by ikill240c 2026-09-09 23:40
        // Resolve the "random" sentinel with a match-seeded roll so every client gets the same
        // number; a plain Random here would desync the lockstep simulation. //added by ikill240c 2026-09-09 23:55
        int requested_towers = world_params.getNumResourceTowers(); //added by ikill240c 2026-09-09 23:55
        this.num_resource_towers = requested_towers >= 0 //added by ikill240c 2026-09-09 23:55
                ? requested_towers //added by ikill240c 2026-09-09 23:55
                : rollMatchValue(SALT_NUM_RESOURCE_TOWERS, WorldParameters.MIN_RANDOM_RESOURCE_TOWERS, //added by ikill240c 2026-09-09 23:55
                        WorldParameters.MAX_RANDOM_RESOURCE_TOWERS); //added by ikill240c 2026-09-09 23:55
        this.max_concurrent_towers = world_params.getMaxConcurrentTowers(); //added by ikill240c 2026-09-09 23:40
        this.notification_listener = notification_listener;
        this.gamespeed = world_params.getInitialGameSpeed();
        this.map_size = world_params.getMapSize();
        this.mode = world_params.getGameMode();
        long time_start = System.currentTimeMillis();

        world = new HeightMap(this, world_info.meters_per_world(), world_info.sea_level_meters(),
                world_info.texels_per_colormap(), world_info.chunks_per_colormap(), world_info.heightmap(),
                world_info.trees(), world_info.access_grid(), world_info.dock_grid(),
                world_info.water_grid(), world_info.build_grid(), world_info.island_ids(),
                world_info.island_infos());

        animation_manager_game_time = new AnimationManager();
        animation_manager_real_time = new AnimationManager();
        random = new Random(42);

        Iterator<Vector4fc> eachColor = Arrays.asList((Vector4fc[]) Settings.getSettings().team_colours).iterator();
        players = Arrays.stream(player_infos).map(info -> new Player(this, info, eachColor.next())).toArray(
                Player[]::new);

        long time_stop = System.currentTimeMillis();
        IO.println(
                "****************** Finished landscape in " + ((time_stop - time_start) / 1000f) + " sec ********************");
        this.supply_managers = new SupplyManagers(this);
        this.unit_grid = new UnitGrid(world);
        boolean archipelago = world_info.island_infos().size() > 1;
        if (archipelago) {
            RegionBuilder.buildRegions(unit_grid);
        } else {
            RegionBuilder.buildRegions(unit_grid, world_info.starting_locations()[0][0],
                    world_info.starting_locations()[0][1]);
        }
        this.patch_root = new PatchGroup(this);
        this.treePositions = world_info.trees();
        this.tree_root = AbstractTreeGroup.newRoot(this, world_info.trees(), world_info.palm_trees(), terrain);
        this.element_root = AbstractElementNode.newRoot(world);
        AbstractElementNode.buildSupplies(this, world_info.iron(), world_info.rocks(), world_info.plants(), terrain);
    }

    public @NonNull AbstractElementNode getElementRoot() {
        return element_root;
    }

    public @NonNull AbstractTreeGroup getTreeRoot() {
        return tree_root;
    }

    public @NonNull List<int[]> getTreePositions() {
        return treePositions;
    }

    public @NonNull AbstractPatchGroup getPatchRoot() {
        return patch_root;
    }

    public @NonNull UnitGrid getUnitGrid() {
        return unit_grid;
    }

    public @Nullable SupplyManager getSupplyManager(@NonNull Class<? extends Supply> cl) {
        return supply_managers.getSupplyManager(cl);
    }

    public @NonNull Player @NonNull [] getPlayers() {
        return players;
    }

    public int getMaxUnitCount() {
        return max_unit_count;
    }

    // Returns the maximum buildings a single player can own. //added by ikill240c 2026-09-10 00:00
    public int getMaxBuildingCount() { //added by ikill240c 2026-09-10 00:00
        return max_building_count; //added by ikill240c 2026-09-10 00:00
    }

    // Returns the number of units each player starts the match with. //added by ikill240c 2026-09-10 00:00
    public int getInitialUnitCount() { //added by ikill240c 2026-09-10 00:00
        return initial_unit_count; //added by ikill240c 2026-09-10 00:00
    }

    // Returns the maximum total chieftains a player can have alive at once. //added by ikill240 2026-09-09 20:49
    public int getMaxChieftains() { //added by ikill240 2026-09-09 20:49
        return max_chieftains; //added by ikill240 2026-09-09 20:49
    }

    // Returns the maximum chieftains a single quarters building can produce. //added by ikill240 2026-09-09 20:49
    public int getMaxChieftainsPerQuarters() { //added by ikill240 2026-09-09 20:49
        return max_chieftains_per_quarters; //added by ikill240 2026-09-09 20:49
    }

    // Salts keep independent rolls from correlating with each other. //added by ikill240c 2026-09-09 23:55
    public static final int SALT_NUM_RESOURCE_TOWERS = 0x52545731; //added by ikill240c 2026-09-09 23:55
    public static final int SALT_ESCORTS_PER_GATHER_GROUP = 0x45534332; //added by ikill240c 2026-09-09 23:55
    public static final int SALT_PATROL_FORCE_SIZE = 0x50415433; //added by ikill240c 2026-09-09 23:55

    // Deterministic per-match roll, seeded only from the mapcode (identical on every peer) plus a
    // caller-supplied salt. Returns the same value on every client and in replays, and never touches
    // the simulation RNG. Use this instead of Math.random/ThreadLocalRandom for anything the
    // simulation can observe. //added by ikill240c 2026-09-09 23:55
    public int rollMatchValue(int salt, int min, int max_inclusive) { //added by ikill240c 2026-09-09 23:55
        if (max_inclusive <= min) //added by ikill240c 2026-09-09 23:55
            return min; //added by ikill240c 2026-09-09 23:55
        long seed = (map_code.hashCode() * 0x9E3779B97F4A7C15L) ^ salt; //added by ikill240c 2026-09-09 23:55
        return min + new Random(seed).nextInt(max_inclusive - min + 1); //added by ikill240c 2026-09-09 23:55
    }

    // Seconds a chieftain must be idle and undamaged before the AI auto-heals it. //added by ikill240c 2026-09-09 23:40
    public float getChieftainHealIdleSeconds() { //added by ikill240c 2026-09-09 23:40
        return chieftain_heal_idle_seconds; //added by ikill240c 2026-09-09 23:40
    }

    // HP restored per heal tick when an idle chieftain auto-heals. //added by ikill240c 2026-09-09 23:40
    public int getChieftainHealAmount() { //added by ikill240c 2026-09-09 23:40
        return chieftain_heal_amount; //added by ikill240c 2026-09-09 23:40
    }

    // How many Quarters the AI aims to build. //added by ikill240c 2026-09-09 23:40
    public int getTargetNumQuarters() { //added by ikill240c 2026-09-09 23:40
        return target_num_quarters; //added by ikill240c 2026-09-09 23:40
    }

    // How many Armories the AI aims to build. //added by ikill240c 2026-09-09 23:40
    public int getTargetNumArmories() { //added by ikill240c 2026-09-09 23:40
        return target_num_armories; //added by ikill240c 2026-09-09 23:40
    }

    // Max Quarters the AI builds concurrently. //added by ikill240c 2026-09-09 23:40
    public int getMaxConcurrentQuarters() { //added by ikill240c 2026-09-09 23:40
        return max_concurrent_quarters; //added by ikill240c 2026-09-09 23:40
    }

    // Max Armories the AI builds concurrently. //added by ikill240c 2026-09-09 23:40
    public int getMaxConcurrentArmories() { //added by ikill240c 2026-09-09 23:40
        return max_concurrent_armories; //added by ikill240c 2026-09-09 23:40
    }

    // How many resource-outpost towers the AI builds. //added by ikill240c 2026-09-09 23:40
    public int getNumResourceTowers() { //added by ikill240c 2026-09-09 23:40
        return num_resource_towers; //added by ikill240c 2026-09-09 23:40
    }

    // Max towers the AI builds concurrently. //added by ikill240c 2026-09-09 23:40
    public int getMaxConcurrentTowers() { //added by ikill240c 2026-09-09 23:40
        return max_concurrent_towers; //added by ikill240c 2026-09-09 23:40
    }

    public @NonNull NotificationListener getNotificationListener() {
        return notification_listener;
    }

    public @NonNull HeightMap getHeightMap() {
        return world;
    }

    public @NonNull AnimationManager getAnimationManagerGameTime() {
        return animation_manager_game_time;
    }

    public @NonNull AnimationManager getAnimationManagerRealTime() {
        return animation_manager_real_time;
    }

    public @NonNull Random getRandom() {
        return random;
    }
}
