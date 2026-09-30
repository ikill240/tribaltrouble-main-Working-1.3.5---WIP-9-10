package com.oddlabs.tt.resource;

import com.oddlabs.procedural.AuthoredTerrain;
import com.oddlabs.tt.form.ProgressForm;
import com.oddlabs.tt.global.Globals;
import com.oddlabs.tt.global.Settings;
import com.oddlabs.tt.landscape.HeightMap;
import com.oddlabs.tt.landscape.LandscapeBaker;
import com.oddlabs.tt.procedural.Landscape;
import com.oddlabs.tt.render.Renderer; //added by ikill240c
import com.oddlabs.tt.render.Texture;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable; //added by ikill240c
import org.lwjgl.opengl.GL11;

import java.io.File; //added by ikill240c
import java.io.FileNotFoundException; //added by ikill240c
import java.io.IOException;
import java.io.ByteArrayInputStream; //added by ikill240c
import java.io.ByteArrayOutputStream; //added by ikill240c
import java.io.InputStream; //added by ikill240c
import java.io.OutputStream; //added by ikill240c
import java.nio.file.StandardCopyOption; //added by ikill240c
import java.util.zip.GZIPInputStream; //added by ikill240c
import java.util.zip.GZIPOutputStream; //added by ikill240c
import java.io.Serial;
import java.io.UncheckedIOException;
import java.nio.file.Files; //added by ikill240c
import java.nio.file.InvalidPathException; //added by ikill240c
import java.nio.file.Path; //added by ikill240c
import java.security.MessageDigest; //added by ikill240c
import java.security.NoSuchAlgorithmException; //added by ikill240c
import java.util.HexFormat; //added by ikill240c
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
    private static final long serialVersionUID = 3; //added by ikill240c - bumped again: added map_file_name and map_fingerprint

    private static final int TEXELS_PER_CHUNK = 512;
    private static final int IDEAL_TEXELS_PER_DETAIL = 256;
    private static final float IDEAL_DETAIL_ALPHA = .15f;

    private final @NonNull String map_file_path; //added by ikill240c
    // Multiplayer: this object is sent to every player, and map_file_path is the HOST's path, which doesn't
    // exist on anyone else's computer (e.g. a Linux host's /home/... path on a Windows client) - that crashed
    // every client at game start. Each machine now finds its own copy by file name (see resolveMapFile()),
    // and the fingerprint (SHA-256 of the host's file) makes sure every player loads exactly the same map;
    // a different file would desync the game. Null only if the host couldn't read its own file. //added by ikill240c
    private final @NonNull String map_file_name; //added by ikill240c
    private final @Nullable String map_fingerprint; //added by ikill240c
    /** Prefix of every map-loading failure message; Main.fail() recognizes it and shows the rest to the player. */ //added by ikill240c
    public static final String LOAD_FAILURE_PREFIX = "Failed to load custom map: "; //added by ikill240c
    private final int meters_per_world;
    private final Landscape.@NonNull TerrainType terrain;
    private final int grid_units;

    public CustomMapGenerator(@NonNull String map_file_path, int meters_per_world, //added by ikill240c
            Landscape.@NonNull TerrainType terrain) {
        this.map_file_path = map_file_path; //added by ikill240c
        // Runs on the host (the machine that picked the map), so its own path is valid here. //added by ikill240c
        this.map_file_name = new File(map_file_path).getName(); //added by ikill240c
        String fingerprint; //added by ikill240c
        try { //added by ikill240c
            fingerprint = fingerprint(Path.of(map_file_path)); //added by ikill240c
        } catch (IOException | InvalidPathException e) { //added by ikill240c
            fingerprint = null; //added by ikill240c - generate() reports the real problem when it tries to load
        } //added by ikill240c
        this.map_fingerprint = fingerprint; //added by ikill240c
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


    /** Folder every player can put shared custom maps in: <game folder>/maps. */ //added by ikill240c
    public static @NonNull Path sharedMapsDir() { //added by ikill240c
        Path game_dir = Renderer.getLocalInput().getGameDir(); //added by ikill240c
        return (game_dir != null ? game_dir : Path.of("")).resolve("maps"); //added by ikill240c
    } //added by ikill240c

    // Finds this machine's copy of the map: the host's own path first (the host itself, or players who keep maps
    // in an identical folder), then <game folder>/maps/<file name>. A copy only counts if its fingerprint matches
    // the host's. Failures carry LOAD_FAILURE_PREFIX so Main.fail() shows the player what to do instead of a raw
    // exception. //added by ikill240c
    private @NonNull File resolveMapFile() { //added by ikill240c
        Path shared_dir = sharedMapsDir(); //added by ikill240c
        Path host_path = null; //added by ikill240c
        try { //added by ikill240c
            host_path = Path.of(map_file_path); //added by ikill240c
        } catch (InvalidPathException e) { //added by ikill240c
            // The host's path isn't even valid on this operating system - skip straight to the maps folder. //added by ikill240c
        } //added by ikill240c
        Path mismatched = null; //added by ikill240c
        for (Path candidate : new Path[]{host_path, shared_dir.resolve(map_file_name), //added by ikill240c
                shared_dir.resolve(alternateFileName())}) { //added by ikill240c - the name a download is saved under if map_file_name is taken
            if (candidate == null || !Files.isRegularFile(candidate)) //added by ikill240c
                continue; //added by ikill240c
            if (map_fingerprint == null) //added by ikill240c
                return candidate.toFile(); //added by ikill240c
            try { //added by ikill240c
                if (map_fingerprint.equals(fingerprint(candidate))) //added by ikill240c
                    return candidate.toFile(); //added by ikill240c
            } catch (IOException e) { //added by ikill240c
                continue; //added by ikill240c
            } //added by ikill240c
            mismatched = candidate; //added by ikill240c
        } //added by ikill240c
        if (mismatched != null) { //added by ikill240c
            throw new UncheckedIOException(LOAD_FAILURE_PREFIX + "Your copy of \"" + map_file_name + "\" (" + mismatched //added by ikill240c
                    + ") is different from the host's. Every player needs the exact same file - copy the host's" //added by ikill240c
                    + " version into " + shared_dir + " and join again.", new IOException("map fingerprint mismatch")); //added by ikill240c
        } //added by ikill240c
        throw new UncheckedIOException(LOAD_FAILURE_PREFIX + "This game uses the custom map \"" + map_file_name //added by ikill240c
                + "\", which isn't on this computer. Every player needs their own copy - put " + map_file_name //added by ikill240c
                + " in " + shared_dir + " and join again.", new FileNotFoundException(map_file_name)); //added by ikill240c
    } //added by ikill240c

    private static @NonNull String fingerprint(@NonNull Path file) throws IOException { //added by ikill240c
        try (InputStream in = Files.newInputStream(file)) { //added by ikill240c
            MessageDigest digest = MessageDigest.getInstance("SHA-256"); //added by ikill240c
            byte[] buffer = new byte[1 << 16]; //added by ikill240c
            for (int n; (n = in.read(buffer)) > 0; ) //added by ikill240c
                digest.update(buffer, 0, n); //added by ikill240c
            return HexFormat.of().formatHex(digest.digest()); //added by ikill240c
        } catch (NoSuchAlgorithmException e) { //added by ikill240c
            throw new IOException(e); //added by ikill240c - SHA-256 is always available in the JDK
        } //added by ikill240c
    } //added by ikill240c

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
            authored_terrain = AuthoredTerrain.load(resolveMapFile()); //added by ikill240c
        } catch (IOException e) { //added by ikill240c
            throw new UncheckedIOException(LOAD_FAILURE_PREFIX + "Couldn't read the custom map \"" + map_file_name //added by ikill240c
                    + "\": " + e.getMessage(), e); //added by ikill240c
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

    // ---------------- automatic multiplayer transfer (see Server/Client "custom map transfer") ---------------- //added by ikill240c

    public @NonNull String getMapFileName() { //added by ikill240c
        return map_file_name; //added by ikill240c
    } //added by ikill240c

    /** True if this computer already has a copy identical to the host's (so nothing needs downloading). */ //added by ikill240c
    public boolean hasLocalCopy() { //added by ikill240c
        try { //added by ikill240c
            resolveMapFile(); //added by ikill240c
            return true; //added by ikill240c
        } catch (UncheckedIOException e) { //added by ikill240c
            return false; //added by ikill240c
        } //added by ikill240c
    } //added by ikill240c

    /** Host side: the map file, gzip-compressed, ready to be sent to players who don't have it. */ //added by ikill240c
    public byte @NonNull [] readCompressed() throws IOException { //added by ikill240c
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); //added by ikill240c
        try (InputStream in = Files.newInputStream(resolveMapFile().toPath()); //added by ikill240c
             OutputStream out = new GZIPOutputStream(bytes, 1 << 16)) { //added by ikill240c
            in.transferTo(out); //added by ikill240c
        } //added by ikill240c
        return bytes.toByteArray(); //added by ikill240c
    } //added by ikill240c

    /** //added by ikill240c
     * Player side: unpacks a map received from the host, checks it is byte-for-byte the host's file, and saves it in
     * the shared maps folder so resolveMapFile() finds it. Saved under the original name, or under
     * alternateFileName() if a different file already has that name (it's never overwritten). //added by ikill240c
     */ //added by ikill240c
    public @NonNull Path installDownloadedMap(byte @NonNull [] compressed) throws IOException { //added by ikill240c
        Path dir = sharedMapsDir(); //added by ikill240c
        Files.createDirectories(dir); //added by ikill240c
        Path temp = Files.createTempFile(dir, "download-", ".part"); //added by ikill240c
        try { //added by ikill240c
            try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(compressed), 1 << 16); //added by ikill240c
                 OutputStream out = Files.newOutputStream(temp)) { //added by ikill240c
                in.transferTo(out); //added by ikill240c
            } //added by ikill240c
            if (map_fingerprint != null && !map_fingerprint.equals(fingerprint(temp))) //added by ikill240c
                throw new IOException("the received map doesn't match the host's file"); //added by ikill240c
            AuthoredTerrain.load(temp.toFile()); //added by ikill240c - fail now, in the lobby, if it can't be read
            Path target = dir.resolve(map_file_name); //added by ikill240c
            if (Files.exists(target)) //added by ikill240c
                target = dir.resolve(alternateFileName()); //added by ikill240c
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING); //added by ikill240c
            return target; //added by ikill240c
        } finally { //added by ikill240c
            Files.deleteIfExists(temp); //added by ikill240c
        } //added by ikill240c
    } //added by ikill240c

    // "<name>-<first 8 fingerprint chars>.ttmap": used when a player already has a different map with the same name. //added by ikill240c
    private @NonNull String alternateFileName() { //added by ikill240c
        String tag = map_fingerprint != null ? map_fingerprint.substring(0, 8) : "copy"; //added by ikill240c
        int dot = map_file_name.lastIndexOf('.'); //added by ikill240c
        return dot > 0 ? map_file_name.substring(0, dot) + "-" + tag + map_file_name.substring(dot) //added by ikill240c
                : map_file_name + "-" + tag; //added by ikill240c
    } //added by ikill240c
}
