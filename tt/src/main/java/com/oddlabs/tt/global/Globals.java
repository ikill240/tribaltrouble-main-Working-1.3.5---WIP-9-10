package com.oddlabs.tt.global;

import com.oddlabs.tt.steam.SteamManager;
import org.jspecify.annotations.NonNull;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import java.nio.file.Path;

public final class Globals {
    public static final int DETAIL_LOW = 0;
    public static final int DETAIL_NORMAL = 1;
    public static final int DETAIL_HIGH = 2;

    public static final int[] TEXTURE_MIP_SHIFT = new int[]{0, 0, 0};
    public static final int[] UNIT_HIGH_POLY_COUNT = new int[]{20000, 80000, 200000};
    public static final int[] LANDSCAPE_POLY_COUNT = new int[]{10000, 40000, 100000};
    public static final boolean[] INSERT_PLANTS = new boolean[]{true, true, true};

    public static final boolean SHIPS_ENABLED = true; //added by ikill240c - was false, disabling ship construction entirely regardless of every ship-behavior fix (boarding animation, collision shapes, row assignment) merged from boats_on_steam; none of those mattered while this single flag kept the build option from ever appearing at all

    public static final String GAME_NAME = "TribalTrouble";
    private static final String SETTINGS_FILE_NAME = "settings";

    private static final String SAVEGAMES_FILE_NAME = "savegames";

    private static final String PRESETS_FILE_NAME = "presets.json";

    // Small persisted per-player skill estimate used to seed Adaptive AI's starting difficulty.
    // Its own tiny properties file rather than a new Settings field, since it's numeric
    // match-result data rather than a user preference. //added by ikill240c 2026-09-12
    private static final String ADAPTIVE_AI_PROFILE_FILE_NAME = "adaptive_ai_profile.properties"; //added by ikill240c 2026-09-12
    // Persisted play-style profile (aggression vs. economy focus) - see PlayerStyleProfile.
    // //added by ikill240c 2026-09-12
    private static final String PLAYER_STYLE_PROFILE_FILE_NAME = "player_style_profile.properties"; //added by ikill240c 2026-09-12
    // Persisted per-(style context, behavior profile) win statistics - see AiBehaviorBandit.
    // //added by ikill240c 2026-09-12
    private static final String AI_BEHAVIOR_BANDIT_FILE_NAME = "ai_behavior_bandit.properties"; //added by ikill240c 2026-09-12
    // Richer, segmented (race / map-size / difficulty) attack-pattern learning data - see
    // PlayerBehaviorProfile. Kept as its own JSON file rather than folded into
    // adaptive_ai_profile.properties, since it is structured (nested per-segment stats) rather than a
    // handful of flat scalars. //added by ikill240c 2026-09-13
    private static final String PLAYER_BEHAVIOR_PROFILE_FILE_NAME = "player_behavior_profile.json"; //added by ikill240c 2026-09-13

    public static @NonNull Path getSettingsFileName() {
        return steamPrefixed(SETTINGS_FILE_NAME);
    }

    public static @NonNull Path getSavegamesFileName() {
        return steamPrefixed(SAVEGAMES_FILE_NAME);
    }

    public static @NonNull Path getPresetsFileName() {
        return steamPrefixed(PRESETS_FILE_NAME);
    }

    public static @NonNull Path getAdaptiveAiProfileFileName() { //added by ikill240c 2026-09-12
        return steamPrefixed(ADAPTIVE_AI_PROFILE_FILE_NAME); //added by ikill240c 2026-09-12
    } //added by ikill240c 2026-09-12

    public static @NonNull Path getPlayerStyleProfileFileName() { //added by ikill240c 2026-09-12
        return steamPrefixed(PLAYER_STYLE_PROFILE_FILE_NAME); //added by ikill240c 2026-09-12
    } //added by ikill240c 2026-09-12

    public static @NonNull Path getAiBehaviorBanditFileName() { //added by ikill240c 2026-09-12
        return steamPrefixed(AI_BEHAVIOR_BANDIT_FILE_NAME); //added by ikill240c 2026-09-12
    } //added by ikill240c 2026-09-12

    public static @NonNull Path getPlayerBehaviorProfileFileName() { //added by ikill240c 2026-09-13
        return steamPrefixed(PLAYER_BEHAVIOR_PROFILE_FILE_NAME); //added by ikill240c 2026-09-13
    } //added by ikill240c 2026-09-13

    private static @NonNull Path steamPrefixed(@NonNull String name) {
        SteamManager steam = SteamManager.getInstance();
        if (steam != null) {
            return Path.of(steam.getAccountID() + "." + name);
        }
        return Path.of(name);
    }

    // Debug flag to enable Steam auth on localhost server
    public static boolean debug_steam_auth_localhost = false;

    public static boolean run_ai = true;

    public static int gamespeed = 2;

    public static final boolean process_landscape = true;
    public static final boolean process_trees = true;
    public static boolean process_misc = true;

    // Was a hardcoded `public static final boolean process_shadows = true` - shadows were
    // unconditionally rendered for every visible unit/building regardless of hardware or player
    // preference, with no way to turn them off even from the Graphics settings panel. Shadow
    // rendering is a real, scene-proportional cost (see SelectableShadowRenderer.renderShadows() -
    // one draw call per selected/shadowed unit on screen), and on a large, busy scene that cost is
    // sustained for as long as the game runs - which can also crowd out the same thread's own
    // network/heartbeat processing, contributing to disconnects during exactly the same sessions
    // where framerate is suffering. Tied to the existing, already-user-facing graphic_detail
    // setting instead: Low detail now also turns shadows off, giving players an actual lever to
    // pull for this cost rather than always paying it no matter what they've set their other
    // graphics options to. //added by ikill240c
    public static boolean processShadows() { //added by ikill240c
        return Settings.getSettings().graphic_detail != DETAIL_LOW; //added by ikill240c
    } //added by ikill240c

    public static boolean draw_status = false;
    public static final boolean draw_landscape = true;
    public static final boolean draw_trees = true;
    public static boolean draw_misc = true;
    public static boolean draw_particles = true;
    public static boolean draw_water = true;
    public static final boolean draw_sky = true;
    public static boolean draw_axes = false;
    public static boolean draw_detail = true;
    public static boolean draw_shadows = true;
    public static boolean draw_light = true;
    public static boolean draw_plants = true;
    public static boolean draw_debug_maps = false;
    public static boolean classic_lighting = true;

    public static final boolean line_mode = false;
    public static boolean clear_frame_buffer = false;
    public static boolean frustum_freeze = false;

    public static boolean slowmotion = false;

    public static boolean checksum_error_in_last_game = false;

    /**
     * Drawing of debug bounding boxes.
     */
    private static @NonNull BoundingMode bounding = BoundingMode.NONE;

    public static void switchBoundingMode() {
        bounding = bounding.next();
        IO.println("Bounding mode: " + bounding);
    }

    public static boolean isBoundsEnabled(@NonNull BoundingMode mode) {
        return bounding == mode || bounding == BoundingMode.ALL;
    }

    public static boolean debugRenderingEnabled() {
        return draw_axes || bounding != BoundingMode.NONE;
    }

    public static final int COMPRESSED_RGB_FORMAT = GL13.GL_COMPRESSED_RGB;
    public static final int COMPRESSED_RGBA_FORMAT = GL13.GL_COMPRESSED_RGBA;
    public static final int COMPRESSED_A_FORMAT = 0x8229; // GL_R8
    public static final int COMPRESSED_LUMINANCE_FORMAT = GL11.GL_RED;
    public static int LOW_DETAIL_TEXTURE_SHIFT = 1;

    public static final float LANDSCAPE_HILLS = 1f;
    public static final float LANDSCAPE_VEGETATION = 2f;
    public static final float LANDSCAPE_RESOURCES = 0f;
    public static final int LANDSCAPE_SEED = 1;

    public static final float LANDSCAPE_TEXTURE_SCALE = 1.0f / 16.0f;//og 16f

    public static final int VIEW_BIT_DEPTH = 16;
    public static final float FOV = 70.0f;// og 45
    public static final float VIEW_MIN = 1.2f;
    public static final float VIEW_MAX = 9000.0f;//og 9000

    public static final int NET_PORT = 21000;

    public static final int NO_MIPMAP_CUTOFF = 1000;

    public static final int STRUCTURE_SIZE = 256;
    public static final int DETAIL_SIZE = 256;
    public static final int TEXELS_PER_GRID_UNIT = 8;

    public static final float LANDSCAPE_DETAIL_REPEAT_RATE = 0.25f;
    public static final float WATER_REPEAT_RATE = 0.001f;
    public static final float WATER_DETAIL_REPEAT_RATE = 0.01f;
    public static final int LANDSCAPE_DETAIL_FADEOUT_BASE_LEVEL = 2;
    public static final float LANDSCAPE_DETAIL_FADEOUT_FACTOR = 0.75f;

    public static final int MAX_RENDERNODE_DEPTH = 5;

    public static final String SCREENSHOT_DEFAULT = "screenshot";

    public static final float TREE_ERROR_DISTANCE = 100f;

    public static final float WHEEL_SCALE = 0.01f;

    public static final int CURSOR_BLINK_TIME = 1000;

    public static final int FPS_WIDTH = 800;

    public static final int SHELL_HISTORY_SIZE = 50;
    public static final int SHELL_HISTORY_PAGE_SIZE = 10;

    // max texture size (for generated textures)
    public static final int MIN_TEXTURE_POWER = 2;
    public static final int MIN_TEXTURE_SIZE = 1 << MIN_TEXTURE_POWER;
    public static int MAX_TEXTURE_POWER;
    public static int MAX_TEXTURE_SIZE;
    // How to divide images in 2^n textures - 1 means split most memory preserving 0 means split least
    public static final float TEXTURE_WEIGHT = 0.5f;
    public static int[] TEXTURE_SIZES;
    public static byte[] TEXTURE_SPLITS;
    public static int[] BEST_SIZES;

    public static final float SEA_LEVEL = .1f;
    public static final int TEXELS_PER_CHUNK_BORDER = 4;

    public static final int BLOCK_SCROLL_AMOUNT = 20;//og 20

    public static final float ERROR_TOLERANCE = 10f;

    private Globals() {
    }
}
