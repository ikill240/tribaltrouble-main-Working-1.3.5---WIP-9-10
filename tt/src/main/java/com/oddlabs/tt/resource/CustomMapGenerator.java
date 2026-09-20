package com.oddlabs.tt.resource;

import com.oddlabs.procedural.AuthoredTerrain;
import com.oddlabs.tt.form.ProgressForm;
import com.oddlabs.tt.global.Globals;
import com.oddlabs.tt.global.Settings;
import com.oddlabs.tt.landscape.HeightMap;
import com.oddlabs.tt.landscape.LandscapeBaker;
import com.oddlabs.tt.procedural.Landscape;
import com.oddlabs.tt.render.Texture;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable; //added by ikill240c
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.io.Serial;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;

/**
 * WorldGenerator implementation for hand-authored (painted) maps, used instead of
 * IslandGenerator when the lobby has a custom map loaded. Mirrors IslandGenerator's generate()
 * line-for-line for everything after Landscape construction - that whole tail (colormap sizing,
 * detail texture creation, LandscapeBaker, WorldInfo assembly) only ever calls public getters on
 * the Landscape result; none of it cares whether those getters were populated by noise or by an
 * editor's painted data, so it's identical between the two generators.
 *
 * <p>hills/vegetation_amount/supplies_amount/seed are deliberately not accepted here (unlike
 * IslandGenerator) since Landscape's authored path (loadAuthoredHeight()/loadAuthoredSupplies())
 * bypasses every noise generator those parameters would otherwise feed.
 *
 * <p>IMPORTANT: carries a map_file_path (a plain String), NOT the AuthoredTerrain object itself.
 * WorldGenerator instances are transmitted whole over Server.setWorldGeneratorAndPlayerSlot's RPC
 * channel (ARMIEvent) to every connecting client - including the host's own client, since even
 * singleplayer games route through loopback networking - and that channel encodes each event's
 * size as a short (ARMIEvent.getEventSize()), a hard ~32KB ceiling per event that has nothing to
 * do with any buffer being too small to grow. A painted heightmap is hundreds of KB of raw floats
 * even for a small map, so embedding the whole AuthoredTerrain directly (as an earlier version of
 * this class did) reliably overflows that limit. Sending only the file path keeps this class as
 * small as IslandGenerator's own handful of primitive fields, and each receiving side loads the
 * actual terrain data from disk independently in generate() below.
 *
 * <p>KNOWN LIMITATION: this only works when every participant has the file at the same path -
 * true today for a local/loopback singleplayer game (server and client are the same machine), but
 * NOT for real multiplayer with remote clients, which would need a genuine file-transfer step
 * this class does not implement. //added by ikill240c
 */
public final class CustomMapGenerator implements WorldGenerator { //added by ikill240c
    @Serial
    private static final long serialVersionUID = 2; //added by ikill240c - bumped: field shape changed from an AuthoredTerrain to a String path

    private static final int TEXELS_PER_CHUNK = 512;
    private static final int IDEAL_TEXELS_PER_DETAIL = 256;
    private static final float IDEAL_DETAIL_ALPHA = .15f;

    private final @NonNull String map_file_path; //added by ikill240c
    private final int meters_per_world;
    private final Landscape.@NonNull TerrainType terrain;
    private final int grid_units;

    public CustomMapGenerator(@NonNull String map_file_path, int meters_per_world, //added by ikill240c
            Landscape.@NonNull TerrainType terrain) {
        this.map_file_path = map_file_path; //added by ikill240c
        this.terrain = terrain;
        // meters_per_world/grid_units are DERIVED FROM THE ACTUAL AUTHORED FILE's saved height
        // channel dimensions, NOT the meters_per_world parameter above (which comes from the
        // lobby's separate, independent "map size" pulldown - IslandGenerator's own procedural
        // size setting, entirely unrelated to what size a hand-painted map was actually saved at).
        // A hand-painted map has a fixed, intrinsic size decided at authoring time. Using the
        // lobby's value instead of the file's real size caused every downstream Channel sized
        // from grid_units (trees/rock/iron/palmtrees, in loadAuthoredSupplies()) to silently
        // disagree with the actual height Channel's real size (sized from the file itself),
        // producing IndexOutOfBoundsException the moment an authored resource coordinate fell
        // within the lobby's size but outside the file's real, smaller-or-larger size.
        // //added by ikill240c
        int authored_grid_size; //added by ikill240c
        try { //added by ikill240c
            authored_grid_size = AuthoredTerrain.load(new java.io.File(map_file_path)) //added by ikill240c
                    .getHeightChannel().getWidth(); //added by ikill240c
        } catch (IOException e) { //added by ikill240c
            // Falls back to the lobby's value rather than letting the constructor itself throw -
            // generate() will hit and report the same load failure properly (as an
            // UncheckedIOException) when it actually needs the file's contents, so this fallback
            // is only ever "wrong" in the narrow window where the file was readable a moment ago
            // and isn't anymore, not a silent, permanent mis-sizing. //added by ikill240c
            authored_grid_size = meters_per_world / HeightMap.METERS_PER_UNIT_GRID; //added by ikill240c
        } //added by ikill240c
        this.meters_per_world = authored_grid_size * HeightMap.METERS_PER_UNIT_GRID; //added by ikill240c
        this.grid_units = authored_grid_size; //added by ikill240c
        // Landscape's constructor only knows how to configure itself for meters_per_world values in
        // Landscape.VALID_METERS_PER_WORLD - anything else falls through to a bare
        // `assert false : "illegal meters_per_world"` deep inside it, with no mention of which map
        // caused it, and which is silently a no-op entirely if assertions happen to be disabled
        // (leaving size_multiplier at 0 and corrupting the landscape instead of failing at all).
        // Validate right here instead, immediately after computing the value above, so a map
        // authored/saved at an unsupported resolution fails loudly and clearly - naming the actual
        // file and its real vs. supported sizes - at the moment the map is chosen, rather than
        // wherever generate() happens to be called from. //added by ikill240c 2026-09-14
        if (java.util.Arrays.stream(Landscape.VALID_METERS_PER_WORLD).noneMatch(v -> v == this.meters_per_world)) { //added by ikill240c 2026-09-14
            throw new IllegalArgumentException("Custom map '" + map_file_path + "' has a height field " //added by ikill240c 2026-09-14
                    + authored_grid_size + "x" + authored_grid_size + " (" + this.meters_per_world //added by ikill240c 2026-09-14
                    + " meters), which isn't one of the supported world sizes " //added by ikill240c 2026-09-14
                    + java.util.Arrays.toString(Landscape.VALID_METERS_PER_WORLD) //added by ikill240c 2026-09-14
                    + ". Resave the map at one of those sizes."); //added by ikill240c 2026-09-14
        } //added by ikill240c 2026-09-14
    }

    private static @NonNull Texture createDetail(@NonNull GLImage detail_image, int base_level) {
        GLImage[] detail_mipmaps = detail_image.buildMipMaps(base_level, Globals.LANDSCAPE_DETAIL_FADEOUT_FACTOR,
                true, false);
        return new Texture(detail_mipmaps, GL11.GL_RGBA8, GL11.GL_LINEAR_MIPMAP_LINEAR,
                GL11.GL_LINEAR, GL11.GL_REPEAT, GL11.GL_REPEAT);
    }

    private static int getTexelsPerGridUnit() {
        return Globals.TEXELS_PER_GRID_UNIT / (int) Math.pow(2,
                Globals.TEXTURE_MIP_SHIFT[Settings.getSettings().graphic_detail]);
    }

    @Override
    public Landscape.@NonNull TerrainType getTerrainType() {
        return terrain;
    }

    @Override
    public int getMetersPerWorld() {
        return meters_per_world;
    }

    @Override
    public @NonNull FogInfo getFogInfo() {
        return Landscape.getFogInfo(terrain, meters_per_world);
    }

    @Override
    public @NonNull WorldInfo generate(int num_players, int initial_unit_count, float random_start_pos,
            boolean team_together, int @Nullable [] player_teams) { //added by ikill240c
        // Loaded fresh on whichever side (server or client) actually calls generate() - see the
        // class-level note on why this isn't received over the network as part of this object.
        // WorldGenerator.generate() has no checked-exception signature to propagate an IOException
        // through, so this wraps it the same way ARMIEvent's own argument-writing code wraps its
        // IOExceptions - as an unchecked exception, matching this codebase's existing convention
        // for that situation rather than inventing a new one. //added by ikill240c
        AuthoredTerrain authored_terrain; //added by ikill240c
        try { //added by ikill240c
            authored_terrain = AuthoredTerrain.load(new java.io.File(map_file_path)); //added by ikill240c
        } catch (IOException e) { //added by ikill240c
            throw new UncheckedIOException("Failed to load custom map: " + map_file_path, e); //added by ikill240c
        } //added by ikill240c

        int colormap_size = grid_units * getTexelsPerGridUnit();
        int chunks_per_colormap = colormap_size / TEXELS_PER_CHUNK;

        Instant time_before = Instant.now();
        int base_level = Globals.LANDSCAPE_DETAIL_FADEOUT_BASE_LEVEL;
        int detail_mip_level = IDEAL_TEXELS_PER_DETAIL / Globals.DETAIL_SIZE - 1;
        int detail_prefade_level = Math.max(detail_mip_level - base_level, 0);
        float detail_prefade = IDEAL_DETAIL_ALPHA * (float) Math.pow(Globals.LANDSCAPE_DETAIL_FADEOUT_FACTOR,
                detail_prefade_level);
        base_level -= detail_mip_level;
        base_level = Math.min(base_level, 1);

        // hills/vegetation_amount/supplies_amount/seed are unused (0f/0): authored_terrain drives
        // height and resource placement directly via Landscape's authored branch, bypassing the
        // noise generators those parameters would otherwise feed. archipelago is always false -
        // authored maps don't support the archipelago sub-island noise path.
        Landscape landscape = new Landscape(num_players, meters_per_world, terrain, detail_prefade, 0f, 0f, 0f, 0,
                initial_unit_count, random_start_pos, false, authored_terrain, team_together, player_teams); //added by ikill240c
        Instant time_after = Instant.now();
        IO.println("Custom map loaded in " + Duration.between(time_before, time_after));

        BlendInfo[] blend_infos = landscape.getBlendInfos();
        Texture detail = createDetail(landscape.getDetail(), base_level);

        LandscapeBaker baker = new LandscapeBaker();
        float textureScale = (float) colormap_size / Globals.STRUCTURE_SIZE;
        WorldInfo.Maps maps = baker.bake(colormap_size, textureScale, blend_infos);

        ProgressForm.progress();
        return new WorldInfo(meters_per_world, landscape.getSeaLevelMeters(),
                colormap_size, chunks_per_colormap, null, maps, detail,
                landscape.getHeight(),
                landscape.getTrees(), landscape.getPalmtrees(), landscape.getRock(), landscape.getIron(),
                landscape.getPlants(),
                landscape.getAccessGrid(), landscape.getDockGrid(), landscape.getWaterGrid(),
                landscape.getBuildGrid(), landscape.getIslandIds(), landscape.getIslandInfos(),
                landscape.getStartingLocations(),
                blend_infos);
    }
}
