package com.oddlabs.tt.form;

import com.oddlabs.matchmaking.Game;
import com.oddlabs.matchmaking.GameMode;
import com.oddlabs.matchmaking.GameSession;
import com.oddlabs.matchmaking.MatchmakingServerInterface;
import com.oddlabs.matchmaking.Preset;
import com.oddlabs.matchmaking.RosterTemplate;
import com.oddlabs.matchmaking.StandardOptions;
import com.oddlabs.matchmaking.WorldConfig;
import com.oddlabs.procedural.AuthoredTerrain; //added by ikill240c
import com.oddlabs.net.NetworkSelector;
import com.oddlabs.registration.RegistrationKey;
import com.oddlabs.tt.delegate.Menu;
import com.oddlabs.tt.event.LocalEventQueue;
import com.oddlabs.tt.gamemode.PresetLibrary;
import com.oddlabs.tt.global.Globals;
import com.oddlabs.tt.global.Settings;
import com.oddlabs.tt.gui.CancelButton;
import com.oddlabs.tt.gui.CheckBox;
import com.oddlabs.tt.gui.EditLine;
import com.oddlabs.tt.gui.GUIObject;
import com.oddlabs.tt.gui.GUIRoot;
import com.oddlabs.tt.gui.Group;
import com.oddlabs.tt.gui.HorizButton;
import com.oddlabs.tt.gui.Label;
import com.oddlabs.tt.gui.MouseButton;
import com.oddlabs.tt.gui.OKButton;
import com.oddlabs.tt.gui.Origin;
import com.oddlabs.tt.gui.Panel;
import com.oddlabs.tt.gui.PanelGroup;
import com.oddlabs.tt.gui.PulldownButton;
import com.oddlabs.tt.gui.PulldownItem;
import com.oddlabs.tt.gui.PulldownMenu;
import com.oddlabs.tt.gui.ScrollableGroup;
import com.oddlabs.tt.gui.ScrollablePulldownMenu;
import com.oddlabs.tt.gui.Skin;
import com.oddlabs.tt.gui.Slider;
import com.oddlabs.tt.guievent.ItemChosenListener;
import com.oddlabs.tt.guievent.MouseClickListener;
import com.oddlabs.tt.guievent.ValueListener;
import com.oddlabs.tt.landscape.WorldParameters;
import com.oddlabs.tt.model.RacesResources;
import com.oddlabs.tt.net.GameNetwork;
import com.oddlabs.tt.net.Network;
import com.oddlabs.tt.net.PlayerSlot;
import com.oddlabs.tt.player.Player;
import com.oddlabs.tt.procedural.Landscape;
import com.oddlabs.tt.render.Renderer;
import com.oddlabs.tt.util.ServerMessageBundler;
import com.oddlabs.tt.util.Utils;
import com.oddlabs.tt.util.WordsEncoding;
import com.oddlabs.tt.viewer.DefaultInGameInfo;
import com.oddlabs.tt.viewer.InGameInfo;
import com.oddlabs.tt.viewer.MultiplayerInGameInfo;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.File; //added by ikill240c
import java.io.IOException; //added by ikill240c
import java.math.BigInteger;
import java.util.Random;
import java.util.ResourceBundle;
import java.util.UUID;
import org.lwjgl.util.tinyfd.TinyFileDialogs; //added by ikill240c

import static com.oddlabs.tt.gui.Placement.BOTTOM_LEFT;
import static com.oddlabs.tt.gui.Placement.BOTTOM_RIGHT;
import static com.oddlabs.tt.gui.Placement.LEFT_MID;
import static com.oddlabs.tt.gui.Placement.RIGHT_MID;
import static com.oddlabs.tt.gui.Placement.RIGHT_TOP;
import static com.oddlabs.tt.gui.Placement.TOP_LEFT;
import static com.oddlabs.tt.gui.Placement.TOP_RIGHT; //added by ikill240c
import static com.oddlabs.tt.gui.Placement.TOP_MID;
// The lines below reference the type itself as "Placement.BOTTOM_LEFT" rather than the plain
// statically-imported constant, which needs the Placement type imported too, not just its
// individual constants. //added by ikill240c 2026-09-12
import com.oddlabs.tt.gui.Placement;

public final class TerrainMenu extends Group {
    private static final int[] SIZES = new int[]{256, 512, 1024, 2048, 4096, 2048}; //added by ikill240c - UNREAL (4096, halved from an original 8192 per explicit request) placed before the CONDITIONALLY-added ARCHIPELAGO entry, matching the pulldown's actual item order below (Archipelago's item only gets added at all when Globals.SHIPS_ENABLED, so it must stay last)
    private static final boolean[] ARCHIPELAGO = new boolean[]{false, false, false, false, false, true}; //added by ikill240c

    private static final int SLIDER_LENGTH = 250;
    private static final int BUTTON_WIDTH = 100;
    private static final int SLIDER_MAX_VALUE = 10;
    // Map settings (hills/vegetation/supplies) can now go up to an extra 100% beyond the original
    // cap - value 20 means 200%, since the normalization divisor below stays at SLIDER_MAX_VALUE=10
    // (10 still means exactly 100%, unchanged), this only widens how far PAST that point the slider
    // can go. Kept as a separate constant from SLIDER_MAX_VALUE specifically so the "10 = 100%"
    // normalization meaning used elsewhere is never disturbed by this. //added by ikill240c
    private static final int MAP_SETTING_SLIDER_MAX = 20; //added by ikill240c
    // Bounds for the numeric setting sliders. //added by ikill240c 2026-09-10 00:00
    private static final int SETTING_SLIDER_LENGTH = 200; //added by ikill240c 2026-09-10 00:00
    private static final int VALUE_LABEL_WIDTH = 50; //added by ikill240c 2026-09-10 00:00
    // Vertical gap between successive rows within one of the Advanced tab's two dropdown columns
    // (max chieftains, chief healing, tower targeting, etc.) - was relying on place()'s 2-arg
    // overload, which defaults to Skin's objectSpacing(), a gap sized for a label sitting right
    // next to its own pulldown, not for separating one full row (label + 150px pulldown button)
    // from the next row's label below it. Too tight a gap there let a row's pulldown button
    // visually crowd against the next row's label text, especially in locales where that label
    // runs longer than the English original. //added by ikill240c
    private static final int ADVANCED_ROW_SPACING = 20; //added by ikill240c
    // Was 1-1000; widened per request. Sliders for unit counts move in increments of
    // UNIT_COUNT_STEP rather than 1 - Slider itself has no native step concept (it's purely
    // min/max/cardinality-based, one position per integer), so this is achieved by constructing the
    // widget with a scaled-down internal range (actual value / step) and multiplying/dividing by step
    // at every boundary (construction, value display, snapshot, and preset restore).
    // //added by ikill240c
    private static final int UNIT_COUNT_STEP = 5; //added by ikill240c
    private static final int MIN_INITIAL_UNITS = 5; //added by ikill240c
    private static final int MAX_INITIAL_UNITS = 5000; //added by ikill240c
    private static final int DEFAULT_INITIAL_UNITS = Player.INITIAL_UNIT_COUNT; //added by ikill240c 2026-09-10 00:00
    // Starting warriors by weapon tier - range chosen generously above what a "starting army" would
    // realistically need; the original game default is 0 (see WorldParameters field comment).
    // //added by ikill240c
    private static final int MIN_STARTING_WARRIORS = 0; //added by ikill240c
    private static final int MAX_STARTING_WARRIORS = 300; //added by ikill240c
    private static final int DEFAULT_STARTING_WARRIORS = 0; //added by ikill240c
    // Magic cost sliders - direct integer values (energy units), matching the original hardcoded
    // defaults (40/70/120) exactly. Range chosen to allow both much cheaper and much more expensive
    // casting than the original. //added by ikill240c
    private static final int MIN_MAGIC_COST = 5; //added by ikill240c
    private static final int MAX_MAGIC_COST = 500; //added by ikill240c
    // Health multiplier sliders - stored as integer PERCENT (25% - 300%), divided by 100 when read.
    // 100 is the default (1.00x, i.e. unchanged from the original game). //added by ikill240c
    private static final int MIN_HEALTH_MULT_PERCENT = 5; //added by ikill240c
    private static final int MAX_HEALTH_MULT_PERCENT = 500; //added by ikill240c
    private static final int DEFAULT_HEALTH_MULT_PERCENT = 100; //added by ikill240c
    // Resource storage cap sliders - default matches LandBuilding.java/Ship.java's existing
    // MAX_SUPPLY_COUNT value (50000) exactly, so behavior is unchanged until turned down/up.
    // //added by ikill240c
    private static final int MIN_RESOURCE_CAP = 50; //added by ikill240c
    private static final int MAX_RESOURCE_CAP = 100000; //added by ikill240c
    private static final int DEFAULT_RESOURCE_CAP = 200; //added by ikill240c
    // Was 1-9999; widened per request (0 to 15000). //added by ikill240c
    private static final int MIN_MAX_UNITS = 0; //added by ikill240c
    private static final int MAX_MAX_UNITS = 25000; //added by ikill240c
    private static final int DEFAULT_MAX_UNITS = Player.DEFAULT_MAX_UNIT_COUNT; //added by ikill240c 2026-09-10 00:00

    private static final String SEED_CARDINALITY = "40000";
    // Widened to match MAP_SETTING_SLIDER_MAX (21 possible values: 0 through 20 inclusive) instead of
    // the original 11 (0 through 10). IMPORTANT: this changes the map-code encoding scheme for hills/
    // vegetation/supplies - map codes generated before this change will not decode correctly
    // afterward. This is a necessary, unavoidable tradeoff of widening the range these values can
    // represent, not a bug. //added by ikill240c
    private static final int SLIDER_CARDINALITY = 21; //added by ikill240c
    private static final int TERRAIN_TYPE_CARDINALITY = 4;
    private static final int TERRAIN_TYPE_CARDINALITY_LEGACY = 2;
    private static final int SIZE_CARDINALITY = 7;
    private static final int SIZE_CARDINALITY_LEGACY = 4;
    // Was 4 - correct for Closed/Easy/Normal/Hard before Insane existed, but this is used as the
    // modulus for this field's "digit" in a mixed-radix mapcode encoding (see parseMapcode()/the
    // encoding loop below): a value of 4 (index 0-3) assumed only 4 possible difficulty pulldown
    // items, so selecting Insane (index 4) overflowed into the next field's digit space, corrupting
    // every subsequent field encoded after it - race, team, and every later player's settings too.
    // Raised to 5 to match the actual item count now that Insane exists. Changing this constant
    // does mean a mapcode generated by an older build with an Insane-difficulty slot decodes
    // differently under this one (unavoidable - the old encoding was already wrong for that case,
    // not a valid code to begin with), but map codes with no Insane slot round-trip identically
    // either way since their actual difficulty digit values (0-3) haven't changed, only the
    // modulus used to size the field around them. //added by ikill240c
    private static final int DIFFICULTY_CARDINALITY = 5; //added by ikill240c
    private static final int RACE_CARDINALITY = 2;
    private static final int TEAM_CARDINALITY = 6;
    private static final @NonNull BigInteger MAX_VALUE;
    private static final @NonNull BigInteger MAX_VALUE_LEGACY;

    private static final ResourceBundle bundle = ResourceBundle.getBundle(TerrainMenu.class.getName());

    private @NonNull String i18n(@NonNull String key, @NonNull Object @NonNull... args) {
        return Utils.getBundleString(bundle, key, args);
    }

    private final @Nullable Menu main_menu;
    private final @Nullable TerrainMenuListener owner;

    private final @NonNull PulldownMenu<Void> pulldown_size;
    private final @NonNull EditLine editline_name;
    private final @NonNull PulldownMenu<Void> pm_terrain_type;
    private final @NonNull Slider slider_hills;
    private final @NonNull Slider slider_vegetation;
    private final @NonNull Slider slider_supplies;
    private final @NonNull Label label_mapcode;
    private final @NonNull HorizButton button_ok;
    // Set by CustomMapListener when a .ttmap file is loaded; null means "ordinary procedural
    // game", which is the default and keeps every existing game mode's behavior unchanged.
    // Deliberately just the file PATH, not the loaded AuthoredTerrain object itself - see
    // CustomMapGenerator's class-level comment for why: a WorldGenerator gets sent whole over a
    // network RPC channel with a hard ~32KB per-event limit, and a painted heightmap is hundreds
    // of KB even for a small map. Each side (server and client) loads the actual terrain data
    // from disk independently inside CustomMapGenerator.generate(). //added by ikill240c
    private @Nullable String selected_custom_map_path; //added by ikill240c
    private final @NonNull PulldownMenu<Void> @NonNull [] difficulty_pulldown_menus;
    private final @NonNull PulldownMenu<Void> @NonNull [] race_pulldown_menus;
    private final @NonNull PulldownMenu<Void> @NonNull [] team_pulldown_menus;
    private final @NonNull PulldownButton<Void> @NonNull [] difficulty_pulldown_buttons;
    private final @NonNull PulldownButton<Void> @NonNull [] race_pulldown_buttons;
    private final @NonNull PulldownButton<Void> @NonNull [] team_pulldown_buttons;
    private final @NonNull Label @NonNull [] labels_players;
    private final @NonNull CheckBox cb_rated;
    // Toggling this on sets every player slot's team pulldown to a unique team index (matching
    // defaultTeam(i)'s own multiplayer default of "every player gets team i") in one action,
    // rather than requiring the host to manually change each slot's team pulldown one at a time
    // to set up a free-for-all game. //added by ikill240c
    private final @NonNull CheckBox cb_free_for_all; //added by ikill240c
    // Per-magic enable toggles. //added by ikill240c
    private final @NonNull CheckBox cb_magic1_enabled; //added by ikill240c
    private final @NonNull CheckBox cb_magic2_enabled; //added by ikill240c
    private final @NonNull CheckBox cb_magic3_enabled; //added by ikill240c
    private final @NonNull CheckBox cb_chiefs_courage_enabled; //added by ikill240c
    // Was on ModeAndPresetsPanel (mode_and_presets), before that inline here, before that on
    // ModeAndPresetsPanel again - moved back here for good, onto the standard tab above the
    // player roster, which is where a player actually looking for "adaptive difficulty" would
    // check first, and not off in a group_advanced_settings row it doesn't visually resemble.
    // //added by ikill240c
    private final @NonNull CheckBox cb_adaptive_ai_enabled; //added by ikill240c
    // When toggled, player start positions are grouped so teammates spawn adjacent to one another
    // on the map instead of the normal fully-random placement. Placed and constructed alongside
    // cb_adaptive_ai_enabled/cb_free_for_all for the same reason those two are grouped together -
    // it's a standard-tab, roster-adjacent toggle a host looks for in the same place. //added by ikill240c
    private final @NonNull CheckBox cb_team_together; //added by ikill240c
    // Was a plain local variable inside the constructor - promoted to a field alongside the other
    // bulk-apply controls above (cb_adaptive_ai_enabled/cb_free_for_all/cb_team_together) since
    // PulldownUpdatePlayersChangedListener (a genuine named inner class, not a local/anonymous one
    // defined inside this constructor) needs to reference it too, and a named inner class can only
    // see enclosing INSTANCE fields, never a constructor's local variables - this was a real
    // compile error (cannot find symbol) until promoted. //added by ikill240c
    private final @NonNull Group group_set_all_difficulty; //added by ikill240c
    // "Set all AI to..." selector - applying a chosen difficulty sets every AI-eligible slot's own
    // difficulty pulldown to match in one action, rather than requiring the host to set each slot
    // individually. Items are Easy/Normal/Hard/Insane only (no Human/Open/Closed - those aren't
    // difficulties to apply in bulk); see its listener for how a chosen item maps to each slot's
    // own pulldown index via the existing fillToDifficultyIndex() helper. //added by ikill240c
    private final @NonNull PulldownMenu<RosterTemplate.Fill> pm_set_all_difficulty; //added by ikill240c
    // Magic costs and health multipliers. Multipliers are stored as integer PERCENT sliders (25-300)
    // and divided by 100 when read, matching the same integer-scaling technique already used for the
    // stepped unit-count sliders - Slider itself only ever produces plain ints. //added by ikill240c
    private final @NonNull Slider slider_magic1_cost; //added by ikill240c
    private final @NonNull Label label_magic1_cost_value; //added by ikill240c
    private final @NonNull Slider slider_magic2_cost; //added by ikill240c
    private final @NonNull Label label_magic2_cost_value; //added by ikill240c
    private final @NonNull Slider slider_magic3_cost; //added by ikill240c
    private final @NonNull Label label_magic3_cost_value; //added by ikill240c
    private final @NonNull Slider slider_building_health_mult; //added by ikill240c
    private final @NonNull Label label_building_health_mult_value; //added by ikill240c
    private final @NonNull Slider slider_viking_chief_health_mult; //added by ikill240c
    private final @NonNull Label label_viking_chief_health_mult_value; //added by ikill240c
    private final @NonNull Slider slider_native_chief_health_mult; //added by ikill240c
    private final @NonNull Label label_native_chief_health_mult_value; //added by ikill240c
    private final @NonNull Slider slider_unit_range_mult; //added by ikill240c
    private final @NonNull Label label_unit_range_mult_value; //added by ikill240c
    // Resource storage caps. //added by ikill240c
    private final @NonNull Slider slider_armory_resource_cap; //added by ikill240c
    private final @NonNull Label label_armory_resource_cap_value; //added by ikill240c
    private final @NonNull Slider slider_rock_resource_cap; //added by ikill240c
    private final @NonNull Label label_rock_resource_cap_value; //added by ikill240c
    private final @NonNull Slider slider_iron_resource_cap; //added by ikill240c
    private final @NonNull Label label_iron_resource_cap_value; //added by ikill240c
    private final @NonNull Slider slider_rubber_resource_cap; //added by ikill240c
    private final @NonNull Label label_rubber_resource_cap_value; //added by ikill240c
    // Pulldown for max chieftains per quarters building. //added by ikill240 2026-09-09 20:49
    private final @NonNull PulldownMenu<Void> pm_max_chiefs_per_q; //added by ikill240 2026-09-09 20:49
    // Pulldown for max total chieftains per player. //added by ikill240 2026-09-09 20:49
    private final @NonNull PulldownMenu<Void> pm_max_chiefs_total; //added by ikill240 2026-09-09 20:49
    // Slider for initial unit count (how many peons each player starts with). //added by ikill240c 2026-09-10 00:00
    private final @NonNull Slider slider_initial_units; //added by ikill240c 2026-09-10 00:00
    private final @NonNull Label label_initial_units_value; //added by ikill240c 2026-09-10 00:00
    // Starting warriors by weapon tier - see the WorldParameters field comment for why 0 is the
    // genuine original default. //added by ikill240c
    private final @NonNull Slider slider_starting_rock_warriors; //added by ikill240c
    private final @NonNull Label label_starting_rock_warriors_value; //added by ikill240c
    private final @NonNull Slider slider_starting_iron_warriors; //added by ikill240c
    private final @NonNull Label label_starting_iron_warriors_value; //added by ikill240c
    private final @NonNull Slider slider_starting_rubber_warriors; //added by ikill240c
    private final @NonNull Label label_starting_rubber_warriors_value; //added by ikill240c
    // Slider for max units per player. //added by ikill240c 2026-09-10 00:00
    private final @NonNull Slider slider_max_units; //added by ikill240c 2026-09-10 00:00
    private final @NonNull Label label_max_units_value; //added by ikill240c 2026-09-10 00:00
    // Pulldown for max buildings per player. //added by ikill240 2026-09-09 21:18
    private final @NonNull PulldownMenu<Void> pm_max_buildings; //added by ikill240 2026-09-09 21:18
    // Pulldowns for the configurable AdvancedAI settings. //added by ikill240c 2026-09-09 23:10
    private final @NonNull PulldownMenu<Void> pm_chief_heal_idle; //added by ikill240c 2026-09-09 23:10
    private final @NonNull PulldownMenu<Void> pm_chief_heal_amount; //added by ikill240c 2026-09-09 23:10
    private final @NonNull PulldownMenu<Void> pm_target_quarters; //added by ikill240c 2026-09-09 23:10
    private final @NonNull PulldownMenu<Void> pm_target_armories; //added by ikill240c 2026-09-09 23:10
    private final @NonNull PulldownMenu<Void> pm_max_concur_quarters; //added by ikill240c 2026-09-09 23:10
    private final @NonNull PulldownMenu<Void> pm_max_concur_armories; //added by ikill240c 2026-09-09 23:10
    private final @NonNull PulldownMenu<Void> pm_num_res_towers; //added by ikill240c 2026-09-09 23:10
    private final @NonNull PulldownMenu<Void> pm_max_concur_towers; //added by ikill240c 2026-09-09 23:10
    private final @NonNull PulldownMenu<Void> pm_koth_statue_count; //added by ikill240c
    private final boolean multiplayer;
    private final @NonNull PulldownMenu<Void> pm_gamespeed;
    private final @NonNull GUIRoot gui_root;
    private final @NonNull NetworkSelector network;
    private final @NonNull PresetLibrary preset_library = new PresetLibrary();
    private final @Nullable RosterPanel roster_panel;
    private final @Nullable ModeAndPresetsPanel mode_and_presets;
    private final @NonNull ScrollablePulldownMenu<Void> pulldown_menu_slots;
    private static final int DEFAULT_PLAYER_COUNT = 6;
    private int player_count = DEFAULT_PLAYER_COUNT;
    private int seed;
    private @Nullable Preset current_preset;
    private @NonNull GameMode selected_mode = GameMode.STANDARD;
    private boolean modified;
    private boolean apply_in_progress;
    private boolean initialized;

    static {
        // Legacy mapcode encoding (per-player settings, 6 players)
        BigInteger max = BigInteger.ONE;
        max = max.multiply(new BigInteger(SEED_CARDINALITY));
        max = max.multiply(new BigInteger(new byte[]{SLIDER_CARDINALITY}));
        max = max.multiply(new BigInteger(new byte[]{SLIDER_CARDINALITY}));
        max = max.multiply(new BigInteger(new byte[]{SLIDER_CARDINALITY}));
        max = max.multiply(new BigInteger(new byte[]{TERRAIN_TYPE_CARDINALITY_LEGACY}));
        max = max.multiply(new BigInteger(new byte[]{SIZE_CARDINALITY_LEGACY}));
        max = max.multiply(new BigInteger(new byte[]{RACE_CARDINALITY}));
        max = max.multiply(new BigInteger(new byte[]{TEAM_CARDINALITY}));
        for (int i = 1; i < DEFAULT_PLAYER_COUNT; i++) {
            max = max.multiply(new BigInteger(new byte[]{DIFFICULTY_CARDINALITY}));
            max = max.multiply(new BigInteger(new byte[]{RACE_CARDINALITY}));
            max = max.multiply(new BigInteger(new byte[]{TEAM_CARDINALITY}));
        }
        MAX_VALUE_LEGACY = max;

        // New word-based mapcode encoding (terrain-only, supports more sizes/types)
        max = BigInteger.ONE;
        max = max.multiply(new BigInteger(SEED_CARDINALITY));
        max = max.multiply(new BigInteger(new byte[]{SLIDER_CARDINALITY}));
        max = max.multiply(new BigInteger(new byte[]{SLIDER_CARDINALITY}));
        max = max.multiply(new BigInteger(new byte[]{SLIDER_CARDINALITY}));
        max = max.multiply(new BigInteger(new byte[]{TERRAIN_TYPE_CARDINALITY}));
        max = max.multiply(new BigInteger(new byte[]{SIZE_CARDINALITY}));
        MAX_VALUE = max;
    }

    @SuppressWarnings("unchecked")
    public TerrainMenu(@NonNull NetworkSelector network, @NonNull GUIRoot gui_root, @Nullable Menu main_menu,
            boolean multiplayer, @Nullable TerrainMenuListener owner) {
        this.network = network;
        this.main_menu = main_menu;
        this.multiplayer = multiplayer;
        this.owner = owner;
        this.gui_root = gui_root;

        // headline
        Label label_headline = new Label(i18n(multiplayer ? "new_game" : "skirmish"), Skin.getSkin().getHeadlineFont());
        addChild(label_headline);
        // Was gated behind `if (multiplayer)` - single-player games never loaded the saved presets
        // file at all, so even after making mode_and_presets always construct below, the preset cards
        // would render empty. Loading unconditionally so single-player sees the same saved presets.
        // //added by ikill240c
        preset_library.load(Renderer.getLocalInput().getGameDir().resolve(Globals.getPresetsFileName()));
        // Was `multiplayer ? new ModeAndPresetsPanel(...) : null` - game mode and presets should be
        // available in single-player too, not just multiplayer lobbies. Always constructed now; see the
        // panel_group layout below for the corresponding single-player placement change.
        // //added by ikill240c
        mode_and_presets = new ModeAndPresetsPanel(gui_root, preset_library, new PresetsHandler());
        Panel standard = new Panel(i18n("standard_options"));
        Panel advanced = new Panel(i18n("advanced_options"));
        // Own tab for Magic Costs/Combat/Resources (group_costs_health), pulled out of the
        // Advanced tab specifically to reduce how tall that tab is - see its own placement chain
        // further down for the rest of this change. //added by ikill240c
        Panel custom_options = new Panel(i18n("custom_options")); //added by ikill240c
        // Own tab for the AI/economy tuning controls (group_advanced_settings - max chiefs, chief
        // heal, quarters/armory/tower targets and caps, resource tower count), for the exact same
        // reason custom_options exists (see its own comment just above): this content was added
        // to the Advanced tab in one batch (2026-09-09 23:10) without similarly being pulled into
        // its own tab, recreating the very height problem custom_options was already created to
        // solve once. This is what was actually dragging multiplayer's overall dialog height past
        // what its extra tab/box chrome left room for on-screen - not something wrong with how
        // that chrome itself was sized. //added by ikill240c
        Panel economy_options = new Panel(i18n("economy_options")); //added by ikill240c
        roster_panel = multiplayer ? new RosterPanel() : null;
        Group group_map_options = new Group();

        // game name
        Label label_name = new Label(i18n("game_name"), Skin.getSkin().getEditFont());
        Label label_default_name = null;
        editline_name = new EditLine(180, Game.MAX_LENGTH);
        if (multiplayer) {
            standard.addChild(label_name);
            String default_name = i18n("default_name", Network.getMatchmakingClient().getProfile().getNick());
            label_default_name = new Label(default_name, Skin.getSkin().getEditFont());
            editline_name.append(default_name);
            if (Renderer.isRegistered())
                standard.addChild(editline_name);
            else
                standard.addChild(label_default_name);
        }
        String rated_tip = i18n("rated_game_tip", GameSession.MIN_WINS_FOR_RANKING);
        cb_rated = new CheckBox(false, i18n("rated_game"), rated_tip);
        if (multiplayer) {
            standard.addChild(cb_rated);
            cb_rated.setDisabled(Network.getMatchmakingClient().getProfile() == null
                    || Network.getMatchmakingClient().getProfile().getWins() < GameSession.MIN_WINS_FOR_RANKING);
        }

        // Container group for the advanced gameplay/AI settings (chieftain limits + AI tuning),
        // placed on the Advanced options tab. //added by ikill240c 2026-09-09 23:10
        Group group_advanced_settings = new Group(); //added by ikill240c 2026-09-09 23:10

        // Max chieftains per quarters building - pulldown with options 1, 2, 3, 5, 10.
        // //added by ikill240 2026-09-09 20:49
        Group group_max_chiefs_per_q = new Group(); //added by ikill240 2026-09-09 20:49
        Label label_max_chiefs_per_q = new Label(i18n("max_chiefs_per_q"), Skin.getSkin().getEditFont()); //added by ikill240 2026-09-09 20:49
        group_max_chiefs_per_q.addChild(label_max_chiefs_per_q); //added by ikill240 2026-09-09 20:49
        pm_max_chiefs_per_q = new PulldownMenu<>(); //added by ikill240 2026-09-09 20:49
        pm_max_chiefs_per_q.addItem(new PulldownItem<>("1")); //added by ikill240 2026-09-09 20:49
        pm_max_chiefs_per_q.addItem(new PulldownItem<>("3")); //added by ikill240 2026-09-09 20:49
        pm_max_chiefs_per_q.addItem(new PulldownItem<>("5")); //added by ikill240 2026-09-09 20:49
        pm_max_chiefs_per_q.addItem(new PulldownItem<>("10")); //added by ikill240 2026-09-09 20:49
        pm_max_chiefs_per_q.addItem(new PulldownItem<>("15")); //added by ikill240 2026-09-09 20:49
        pm_max_chiefs_per_q.addItem(new PulldownItem<>("30")); //added by ikill240 2026-09-09 20:49
        pm_max_chiefs_per_q.addItem(new PulldownItem<>(i18n("unlimited")));
        var pb_max_chiefs_per_q = new PulldownButton<>(gui_root, pm_max_chiefs_per_q, 6, 150); //added by ikill240 2026-09-09 20:49
        pm_max_chiefs_per_q.addItemChosenListener((_, _) -> markModified()); //added by ikill240 2026-09-09 20:49
        group_max_chiefs_per_q.addChild(pb_max_chiefs_per_q); //added by ikill240 2026-09-09 20:49
        label_max_chiefs_per_q.place(); //added by ikill240 2026-09-09 20:49
        pb_max_chiefs_per_q.place(label_max_chiefs_per_q, RIGHT_MID); //added by ikill240 2026-09-09 20:49
        group_max_chiefs_per_q.compileCanvas(); //added by ikill240 2026-09-09 20:49
        group_advanced_settings.addChild(group_max_chiefs_per_q); //added by ikill240c 2026-09-09 23:10

        // Max total chieftains per player - pulldown with options 1, 3, 5, 10, 20, Unlimited(9999).
        // //added by ikill240 2026-09-09 20:49
        Group group_max_chiefs_total = new Group(); //added by ikill240 2026-09-09 20:49
        Label label_max_chiefs_total = new Label(i18n("max_chiefs_total"), Skin.getSkin().getEditFont()); //added by ikill240 2026-09-09 20:49
        group_max_chiefs_total.addChild(label_max_chiefs_total); //added by ikill240 2026-09-09 20:49
        pm_max_chiefs_total = new PulldownMenu<>(); //added by ikill240 2026-09-09 20:49
        pm_max_chiefs_total.addItem(new PulldownItem<>("1")); //added by ikill240 2026-09-09 20:49
        pm_max_chiefs_total.addItem(new PulldownItem<>("3")); //added by ikill240 2026-09-09 20:49
        pm_max_chiefs_total.addItem(new PulldownItem<>("5")); //added by ikill240 2026-09-09 20:49
        pm_max_chiefs_total.addItem(new PulldownItem<>("10")); //added by ikill240 2026-09-09 20:49
        pm_max_chiefs_total.addItem(new PulldownItem<>("15")); //added by ikill240 2026-09-09 20:49
        pm_max_chiefs_total.addItem(new PulldownItem<>("30")); //added by ikill240 2026-09-09 20:49
        pm_max_chiefs_total.addItem(new PulldownItem<>(i18n("unlimited"))); //added by ikill240 2026-09-09 20:49
        var pb_max_chiefs_total = new PulldownButton<>(gui_root, pm_max_chiefs_total, 0, 150); //added by ikill240 2026-09-09 20:49
        pm_max_chiefs_total.addItemChosenListener((_, _) -> markModified()); //added by ikill240 2026-09-09 20:49
        group_max_chiefs_total.addChild(pb_max_chiefs_total); //added by ikill240 2026-09-09 20:49
        label_max_chiefs_total.place(); //added by ikill240 2026-09-09 20:49
        pb_max_chiefs_total.place(label_max_chiefs_total, RIGHT_MID); //added by ikill240 2026-09-09 20:49
        group_max_chiefs_total.compileCanvas(); //added by ikill240 2026-09-09 20:49
        group_advanced_settings.addChild(group_max_chiefs_total); //added by ikill240c 2026-09-09 23:10

        // Starting warriors by weapon tier, plus initial/max unit counts - one cohesive section
        // now rather than initial/max units living as separate standalone groups elsewhere on
        // this tab. Renamed header ("starting_units_header") since this covers more than just
        // warriors now. //added by ikill240c
        Group group_starting_warriors = new Group(); //added by ikill240c
        Label label_starting_warriors_header = new Label(i18n("starting_units_header"), //added by ikill240c - was "starting_warriors_header"
                Skin.getSkin().getHeadlineFont()); //added by ikill240c
        group_starting_warriors.addChild(label_starting_warriors_header); //added by ikill240c
        label_starting_warriors_header.place(); //added by ikill240c

        // Initial unit count - slider from 1 to 1000 peons. Was its own standalone group directly
        // under group_map_options; moved here, above the warrior-tier rows, as the first row below
        // this section's header. //added by ikill240c
        Label label_initial_units = new Label(i18n("initial_units"), Skin.getSkin().getEditFont()); //added by ikill240 2026-09-09 21:18
        group_starting_warriors.addChild(label_initial_units); //added by ikill240c
        slider_initial_units = new Slider(SETTING_SLIDER_LENGTH, MIN_INITIAL_UNITS / UNIT_COUNT_STEP,
                MAX_INITIAL_UNITS / UNIT_COUNT_STEP, DEFAULT_INITIAL_UNITS / UNIT_COUNT_STEP); //added by ikill240c
        label_initial_units_value = new Label(Integer.toString(DEFAULT_INITIAL_UNITS),
                Skin.getSkin().getEditFont(), VALUE_LABEL_WIDTH); //added by ikill240c 2026-09-10 00:00
        slider_initial_units.addValueListener(value -> { //added by ikill240c 2026-09-10 00:00
            label_initial_units_value.setText(Long.toString(value * UNIT_COUNT_STEP)); //added by ikill240c
            markModified(); //added by ikill240c 2026-09-10 00:00
        }); //added by ikill240c 2026-09-10 00:00
        group_starting_warriors.addChild(slider_initial_units); //added by ikill240c
        group_starting_warriors.addChild(label_initial_units_value); //added by ikill240c
        label_initial_units.place(label_starting_warriors_header, Placement.BOTTOM_LEFT); //added by ikill240c
        slider_initial_units.place(label_initial_units, RIGHT_MID); //added by ikill240c 2026-09-10 00:00
        label_initial_units_value.place(slider_initial_units, RIGHT_MID); //added by ikill240c 2026-09-10 00:00

        // Max units per player - slider from 1 to 9999. Was its own standalone group; moved here,
        // directly below initial units. //added by ikill240c
        Label label_max_units = new Label(i18n("max_units"), Skin.getSkin().getEditFont()); //added by ikill240 2026-09-09 21:18
        group_starting_warriors.addChild(label_max_units); //added by ikill240c
        slider_max_units = new Slider(SETTING_SLIDER_LENGTH, MIN_MAX_UNITS / UNIT_COUNT_STEP,
                MAX_MAX_UNITS / UNIT_COUNT_STEP, DEFAULT_MAX_UNITS / UNIT_COUNT_STEP); //added by ikill240c
        label_max_units_value = new Label(Integer.toString(DEFAULT_MAX_UNITS), Skin.getSkin().getEditFont(),
                VALUE_LABEL_WIDTH); //added by ikill240c 2026-09-10 00:00
        slider_max_units.addValueListener(value -> { //added by ikill240c 2026-09-10 00:00
            label_max_units_value.setText(Long.toString(value * UNIT_COUNT_STEP)); //added by ikill240c
            markModified(); //added by ikill240c 2026-09-10 00:00
        }); //added by ikill240c 2026-09-10 00:00
        group_starting_warriors.addChild(slider_max_units); //added by ikill240c
        group_starting_warriors.addChild(label_max_units_value); //added by ikill240c
        label_max_units.place(label_initial_units, Placement.BOTTOM_LEFT); //added by ikill240c
        slider_max_units.place(label_max_units, RIGHT_MID); //added by ikill240c 2026-09-10 00:00
        label_max_units_value.place(slider_max_units, RIGHT_MID); //added by ikill240c 2026-09-10 00:00

        Label label_starting_rock_warriors = new Label(i18n("starting_rock_warriors"), Skin.getSkin().getEditFont()); //added by ikill240c
        group_starting_warriors.addChild(label_starting_rock_warriors); //added by ikill240c
        slider_starting_rock_warriors = new Slider(SETTING_SLIDER_LENGTH, MIN_STARTING_WARRIORS, //added by ikill240c
                MAX_STARTING_WARRIORS, DEFAULT_STARTING_WARRIORS); //added by ikill240c
        label_starting_rock_warriors_value = new Label(Integer.toString(DEFAULT_STARTING_WARRIORS), //added by ikill240c
                Skin.getSkin().getEditFont(), VALUE_LABEL_WIDTH); //added by ikill240c
        slider_starting_rock_warriors.addValueListener(value -> { //added by ikill240c
            label_starting_rock_warriors_value.setText(Long.toString(value)); //added by ikill240c
            markModified(); //added by ikill240c
        }); //added by ikill240c
        group_starting_warriors.addChild(slider_starting_rock_warriors); //added by ikill240c
        group_starting_warriors.addChild(label_starting_rock_warriors_value); //added by ikill240c
        label_starting_rock_warriors.place(label_max_units, Placement.BOTTOM_LEFT); //added by ikill240c - was anchored to label_starting_warriors_header directly; now below the new initial/max units rows
        slider_starting_rock_warriors.place(label_starting_rock_warriors, RIGHT_MID); //added by ikill240c
        label_starting_rock_warriors_value.place(slider_starting_rock_warriors, RIGHT_MID); //added by ikill240c

        Label label_starting_iron_warriors = new Label(i18n("starting_iron_warriors"), Skin.getSkin().getEditFont()); //added by ikill240c
        group_starting_warriors.addChild(label_starting_iron_warriors); //added by ikill240c
        slider_starting_iron_warriors = new Slider(SETTING_SLIDER_LENGTH, MIN_STARTING_WARRIORS, //added by ikill240c
                MAX_STARTING_WARRIORS, DEFAULT_STARTING_WARRIORS); //added by ikill240c
        label_starting_iron_warriors_value = new Label(Integer.toString(DEFAULT_STARTING_WARRIORS), //added by ikill240c
                Skin.getSkin().getEditFont(), VALUE_LABEL_WIDTH); //added by ikill240c
        slider_starting_iron_warriors.addValueListener(value -> { //added by ikill240c
            label_starting_iron_warriors_value.setText(Long.toString(value)); //added by ikill240c
            markModified(); //added by ikill240c
        }); //added by ikill240c
        group_starting_warriors.addChild(slider_starting_iron_warriors); //added by ikill240c
        group_starting_warriors.addChild(label_starting_iron_warriors_value); //added by ikill240c
        label_starting_iron_warriors.place(label_starting_rock_warriors, Placement.BOTTOM_LEFT); //added by ikill240c
        slider_starting_iron_warriors.place(label_starting_iron_warriors, RIGHT_MID); //added by ikill240c
        label_starting_iron_warriors_value.place(slider_starting_iron_warriors, RIGHT_MID); //added by ikill240c

        Label label_starting_rubber_warriors = new Label(i18n("starting_rubber_warriors"), Skin.getSkin().getEditFont()); //added by ikill240c
        group_starting_warriors.addChild(label_starting_rubber_warriors); //added by ikill240c
        slider_starting_rubber_warriors = new Slider(SETTING_SLIDER_LENGTH, MIN_STARTING_WARRIORS, //added by ikill240c
                MAX_STARTING_WARRIORS, DEFAULT_STARTING_WARRIORS); //added by ikill240c
        label_starting_rubber_warriors_value = new Label(Integer.toString(DEFAULT_STARTING_WARRIORS), //added by ikill240c
                Skin.getSkin().getEditFont(), VALUE_LABEL_WIDTH); //added by ikill240c
        slider_starting_rubber_warriors.addValueListener(value -> { //added by ikill240c
            label_starting_rubber_warriors_value.setText(Long.toString(value)); //added by ikill240c
            markModified(); //added by ikill240c
        }); //added by ikill240c
        group_starting_warriors.addChild(slider_starting_rubber_warriors); //added by ikill240c
        group_starting_warriors.addChild(label_starting_rubber_warriors_value); //added by ikill240c
        label_starting_rubber_warriors.place(label_starting_iron_warriors, Placement.BOTTOM_LEFT); //added by ikill240c
        slider_starting_rubber_warriors.place(label_starting_rubber_warriors, RIGHT_MID); //added by ikill240c
        label_starting_rubber_warriors_value.place(slider_starting_rubber_warriors, RIGHT_MID); //added by ikill240c

        group_starting_warriors.compileCanvas(); //added by ikill240c
        group_map_options.addChild(group_starting_warriors); //added by ikill240c

        // Max buildings per player - pulldown with options 20, 50, 100, 500, 1200, Unlimited(9999).
        // //added by ikill240 2026-09-09 21:18
        Group group_max_buildings = new Group(); //added by ikill240 2026-09-09 21:18
        Label label_max_buildings = new Label(i18n("max_buildings"), Skin.getSkin().getEditFont()); //added by ikill240 2026-09-09 21:18
        group_max_buildings.addChild(label_max_buildings); //added by ikill240 2026-09-09 21:18
        pm_max_buildings = new PulldownMenu<>(); //added by ikill240 2026-09-09 21:18
        pm_max_buildings.addItem(new PulldownItem<>("20")); //added by ikill240 2026-09-09 21:18
        pm_max_buildings.addItem(new PulldownItem<>("40")); //added by ikill240 2026-09-09 21:18
        pm_max_buildings.addItem(new PulldownItem<>("60")); //added by ikill240 2026-09-09 21:18
        pm_max_buildings.addItem(new PulldownItem<>("100")); //added by ikill240 2026-09-09 21:18
        pm_max_buildings.addItem(new PulldownItem<>("200")); //added by ikill240 2026-09-09 21:18
        pm_max_buildings.addItem(new PulldownItem<>(i18n("unlimited"))); //added by ikill240 2026-09-09 21:18
        var pb_max_buildings = new PulldownButton<>(gui_root, pm_max_buildings, 0, 150); //added by ikill240 2026-09-09 21:18
        pm_max_buildings.addItemChosenListener((_, _) -> markModified()); //added by ikill240 2026-09-09 21:18
        group_max_buildings.addChild(pb_max_buildings); //added by ikill240 2026-09-09 21:18
        label_max_buildings.place(); //added by ikill240 2026-09-09 21:18
        pb_max_buildings.place(label_max_buildings, RIGHT_MID); //added by ikill240 2026-09-09 21:18
        group_max_buildings.compileCanvas(); //added by ikill240 2026-09-09 21:18
        group_map_options.addChild(group_max_buildings); //added by ikill240 2026-09-09 21:18

        // Configurable AdvancedAI tuning pulldowns, grouped on the Advanced options tab. //added by ikill240c 2026-09-09 23:10
        Group group_chief_heal_idle = new Group(); //added by ikill240c 2026-09-09 23:10
        Label label_chief_heal_idle = new Label(i18n("chief_heal_idle_seconds"), Skin.getSkin().getEditFont()); //added by ikill240c 2026-09-09 23:10
        group_chief_heal_idle.addChild(label_chief_heal_idle); //added by ikill240c 2026-09-09 23:10
        pm_chief_heal_idle = new PulldownMenu<>(); //added by ikill240c 2026-09-09 23:10
        pm_chief_heal_idle.addItem(new PulldownItem<>("10")); //added by ikill240c 2026-09-09 23:10
        pm_chief_heal_idle.addItem(new PulldownItem<>("20")); //added by ikill240c 2026-09-09 23:10
        pm_chief_heal_idle.addItem(new PulldownItem<>("30")); //added by ikill240c 2026-09-09 23:10
        pm_chief_heal_idle.addItem(new PulldownItem<>("60")); //added by ikill240c 2026-09-09 23:10
        pm_chief_heal_idle.addItem(new PulldownItem<>("120")); //added by ikill240c 2026-09-09 23:10
        var pb_chief_heal_idle = new PulldownButton<>(gui_root, pm_chief_heal_idle, 2, 150); //added by ikill240c 2026-09-09 23:10
        pm_chief_heal_idle.addItemChosenListener((_, _) -> markModified()); //added by ikill240c 2026-09-09 23:10
        group_chief_heal_idle.addChild(pb_chief_heal_idle); //added by ikill240c 2026-09-09 23:10
        label_chief_heal_idle.place(); //added by ikill240c 2026-09-09 23:10
        pb_chief_heal_idle.place(label_chief_heal_idle, RIGHT_MID); //added by ikill240c 2026-09-09 23:10
        group_chief_heal_idle.compileCanvas(); //added by ikill240c 2026-09-09 23:10
        group_advanced_settings.addChild(group_chief_heal_idle); //added by ikill240c 2026-09-09 23:10

        Group group_chief_heal_amount = new Group(); //added by ikill240c 2026-09-09 23:10
        Label label_chief_heal_amount = new Label(i18n("chief_heal_amount"), Skin.getSkin().getEditFont()); //added by ikill240c 2026-09-09 23:10
        group_chief_heal_amount.addChild(label_chief_heal_amount); //added by ikill240c 2026-09-09 23:10
        pm_chief_heal_amount = new PulldownMenu<>(); //added by ikill240c 2026-09-09 23:10
        pm_chief_heal_amount.addItem(new PulldownItem<>("1")); //added by ikill240c 2026-09-09 23:10
        pm_chief_heal_amount.addItem(new PulldownItem<>("3")); //added by ikill240c 2026-09-09 23:10
        pm_chief_heal_amount.addItem(new PulldownItem<>("5")); //added by ikill240c 2026-09-09 23:10
        pm_chief_heal_amount.addItem(new PulldownItem<>("10")); //added by ikill240c 2026-09-09 23:10
        pm_chief_heal_amount.addItem(new PulldownItem<>("15")); 
        pm_chief_heal_amount.addItem(new PulldownItem<>("30")); 
        var pb_chief_heal_amount = new PulldownButton<>(gui_root, pm_chief_heal_amount, 2, 150); //added by ikill240c 2026-09-09 23:10
        pm_chief_heal_amount.addItemChosenListener((_, _) -> markModified()); //added by ikill240c 2026-09-09 23:10
        group_chief_heal_amount.addChild(pb_chief_heal_amount); //added by ikill240c 2026-09-09 23:10
        label_chief_heal_amount.place(); //added by ikill240c 2026-09-09 23:10
        pb_chief_heal_amount.place(label_chief_heal_amount, RIGHT_MID); //added by ikill240c 2026-09-09 23:10
        group_chief_heal_amount.compileCanvas(); //added by ikill240c 2026-09-09 23:10
        group_advanced_settings.addChild(group_chief_heal_amount); //added by ikill240c 2026-09-09 23:10

        Group group_target_quarters = new Group(); //added by ikill240c 2026-09-09 23:10
        Label label_target_quarters = new Label(i18n("target_quarters"), Skin.getSkin().getEditFont()); //added by ikill240c 2026-09-09 23:10
        group_target_quarters.addChild(label_target_quarters); //added by ikill240c 2026-09-09 23:10
        pm_target_quarters = new PulldownMenu<>(); //added by ikill240c 2026-09-09 23:10
        pm_target_quarters.addItem(new PulldownItem<>("1")); //added by ikill240c 2026-09-09 23:10
        pm_target_quarters.addItem(new PulldownItem<>("2")); //added by ikill240c 2026-09-09 23:10
        pm_target_quarters.addItem(new PulldownItem<>("3")); //added by ikill240c 2026-09-09 23:10
        pm_target_quarters.addItem(new PulldownItem<>("4")); //added by ikill240c 2026-09-09 23:10
        pm_target_quarters.addItem(new PulldownItem<>("5"));
        pm_target_quarters.addItem(new PulldownItem<>("7"));
        pm_target_quarters.addItem(new PulldownItem<>("12"));
        var pb_target_quarters = new PulldownButton<>(gui_root, pm_target_quarters, 1, 150); //added by ikill240c 2026-09-09 23:10
        pm_target_quarters.addItemChosenListener((_, _) -> markModified()); //added by ikill240c 2026-09-09 23:10
        group_target_quarters.addChild(pb_target_quarters); //added by ikill240c 2026-09-09 23:10
        label_target_quarters.place(); //added by ikill240c 2026-09-09 23:10
        pb_target_quarters.place(label_target_quarters, RIGHT_MID); //added by ikill240c 2026-09-09 23:10
        group_target_quarters.compileCanvas(); //added by ikill240c 2026-09-09 23:10
        group_advanced_settings.addChild(group_target_quarters); //added by ikill240c 2026-09-09 23:10

        Group group_target_armories = new Group(); //added by ikill240c 2026-09-09 23:10
        Label label_target_armories = new Label(i18n("target_armories"), Skin.getSkin().getEditFont()); //added by ikill240c 2026-09-09 23:10
        group_target_armories.addChild(label_target_armories); //added by ikill240c 2026-09-09 23:10
        pm_target_armories = new PulldownMenu<>(); //added by ikill240c 2026-09-09 23:10
        pm_target_armories.addItem(new PulldownItem<>("1")); //added by ikill240c 2026-09-09 23:10
        pm_target_armories.addItem(new PulldownItem<>("2")); //added by ikill240c 2026-09-09 23:10
        pm_target_armories.addItem(new PulldownItem<>("3")); //added by ikill240c 2026-09-09 23:10
        pm_target_armories.addItem(new PulldownItem<>("4"));
        pm_target_armories.addItem(new PulldownItem<>("5"));
        pm_target_armories.addItem(new PulldownItem<>("6"));
        var pb_target_armories = new PulldownButton<>(gui_root, pm_target_armories, 0, 150); //added by ikill240c 2026-09-09 23:10
        pm_target_armories.addItemChosenListener((_, _) -> markModified()); //added by ikill240c 2026-09-09 23:10
        group_target_armories.addChild(pb_target_armories); //added by ikill240c 2026-09-09 23:10
        label_target_armories.place(); //added by ikill240c 2026-09-09 23:10
        pb_target_armories.place(label_target_armories, RIGHT_MID); //added by ikill240c 2026-09-09 23:10
        group_target_armories.compileCanvas(); //added by ikill240c 2026-09-09 23:10
        group_advanced_settings.addChild(group_target_armories); //added by ikill240c 2026-09-09 23:10

        Group group_max_concur_quarters = new Group(); //added by ikill240c 2026-09-09 23:10
        Label label_max_concur_quarters = new Label(i18n("max_concurrent_quarters"), Skin.getSkin().getEditFont()); //added by ikill240c 2026-09-09 23:10
        group_max_concur_quarters.addChild(label_max_concur_quarters); //added by ikill240c 2026-09-09 23:10
        pm_max_concur_quarters = new PulldownMenu<>(); //added by ikill240c 2026-09-09 23:10
        pm_max_concur_quarters.addItem(new PulldownItem<>("1")); //added by ikill240c 2026-09-09 23:10
        pm_max_concur_quarters.addItem(new PulldownItem<>("2")); //added by ikill240c 2026-09-09 23:10
        pm_max_concur_quarters.addItem(new PulldownItem<>("3")); //added by ikill240c 2026-09-09 23:10
        pm_max_concur_quarters.addItem(new PulldownItem<>("4"));
        pm_max_concur_quarters.addItem(new PulldownItem<>("5")); //added by ikill240c 2026-09-09 23:10
        var pb_max_concur_quarters = new PulldownButton<>(gui_root, pm_max_concur_quarters, 1, 150); //added by ikill240c 2026-09-09 23:10
        pm_max_concur_quarters.addItemChosenListener((_, _) -> markModified()); //added by ikill240c 2026-09-09 23:10
        group_max_concur_quarters.addChild(pb_max_concur_quarters); //added by ikill240c 2026-09-09 23:10
        label_max_concur_quarters.place(); //added by ikill240c 2026-09-09 23:10
        pb_max_concur_quarters.place(label_max_concur_quarters, RIGHT_MID); //added by ikill240c 2026-09-09 23:10
        group_max_concur_quarters.compileCanvas(); //added by ikill240c 2026-09-09 23:10
        group_advanced_settings.addChild(group_max_concur_quarters); //added by ikill240c 2026-09-09 23:10

        Group group_max_concur_armories = new Group(); //added by ikill240c 2026-09-09 23:10
        Label label_max_concur_armories = new Label(i18n("max_concurrent_armories"), Skin.getSkin().getEditFont()); //added by ikill240c 2026-09-09 23:10
        group_max_concur_armories.addChild(label_max_concur_armories); //added by ikill240c 2026-09-09 23:10
        pm_max_concur_armories = new PulldownMenu<>(); //added by ikill240c 2026-09-09 23:10
        pm_max_concur_armories.addItem(new PulldownItem<>("1")); //added by ikill240c 2026-09-09 23:10
        pm_max_concur_armories.addItem(new PulldownItem<>("2")); //added by ikill240c 2026-09-09 23:10
        pm_max_concur_armories.addItem(new PulldownItem<>("3")); //added by ikill240c 2026-09-09 23:10
        var pb_max_concur_armories = new PulldownButton<>(gui_root, pm_max_concur_armories, 0, 150); //added by ikill240c 2026-09-09 23:10
        pm_max_concur_armories.addItemChosenListener((_, _) -> markModified()); //added by ikill240c 2026-09-09 23:10
        group_max_concur_armories.addChild(pb_max_concur_armories); //added by ikill240c 2026-09-09 23:10
        label_max_concur_armories.place(); //added by ikill240c 2026-09-09 23:10
        pb_max_concur_armories.place(label_max_concur_armories, RIGHT_MID); //added by ikill240c 2026-09-09 23:10
        group_max_concur_armories.compileCanvas(); //added by ikill240c 2026-09-09 23:10
        group_advanced_settings.addChild(group_max_concur_armories); //added by ikill240c 2026-09-09 23:10

        Group group_num_res_towers = new Group(); //added by ikill240c 2026-09-09 23:10
        Label label_num_res_towers = new Label(i18n("num_resource_towers"), Skin.getSkin().getEditFont()); //added by ikill240c 2026-09-09 23:10
        group_num_res_towers.addChild(label_num_res_towers); //added by ikill240c 2026-09-09 23:10
        pm_num_res_towers = new PulldownMenu<>(); //added by ikill240c 2026-09-09 23:10
        pm_num_res_towers.addItem(new PulldownItem<>("0")); //added by ikill240c 2026-09-09 23:10
        pm_num_res_towers.addItem(new PulldownItem<>("3")); //added by ikill240c 2026-09-09 23:10
        pm_num_res_towers.addItem(new PulldownItem<>("5")); //added by ikill240c 2026-09-09 23:10
        pm_num_res_towers.addItem(new PulldownItem<>("7"));
        pm_num_res_towers.addItem(new PulldownItem<>("10"));
        pm_num_res_towers.addItem(new PulldownItem<>("15")); //added by ikill240c 2026-09-09 23:10
        pm_num_res_towers.addItem(new PulldownItem<>("30"));
        pm_num_res_towers.addItem(new PulldownItem<>("60"));
        pm_num_res_towers.addItem(new PulldownItem<>("90")); //added by ikill240c 2026-09-09 23:10
        var pb_num_res_towers = new PulldownButton<>(gui_root, pm_num_res_towers, 3, 150); //added by ikill240c 2026-09-09 23:10
        pm_num_res_towers.addItemChosenListener((_, _) -> markModified()); //added by ikill240c 2026-09-09 23:10
        group_num_res_towers.addChild(pb_num_res_towers); //added by ikill240c 2026-09-09 23:10
        label_num_res_towers.place(); //added by ikill240c 2026-09-09 23:10
        pb_num_res_towers.place(label_num_res_towers, RIGHT_MID); //added by ikill240c 2026-09-09 23:10
        group_num_res_towers.compileCanvas(); //added by ikill240c 2026-09-09 23:10
        group_advanced_settings.addChild(group_num_res_towers); //added by ikill240c 2026-09-09 23:10

        Group group_max_concur_towers = new Group(); //added by ikill240c 2026-09-09 23:10
        Label label_max_concur_towers = new Label(i18n("max_concurrent_towers"), Skin.getSkin().getEditFont()); //added by ikill240c 2026-09-09 23:10
        group_max_concur_towers.addChild(label_max_concur_towers); //added by ikill240c 2026-09-09 23:10
        pm_max_concur_towers = new PulldownMenu<>(); //added by ikill240c 2026-09-09 23:10
        pm_max_concur_towers.addItem(new PulldownItem<>("1")); //added by ikill240c 2026-09-09 23:10
        pm_max_concur_towers.addItem(new PulldownItem<>("3")); //added by ikill240c 2026-09-09 23:10
        pm_max_concur_towers.addItem(new PulldownItem<>("5"));
        pm_max_concur_towers.addItem(new PulldownItem<>("7"));
        pm_max_concur_towers.addItem(new PulldownItem<>("10")); //added by ikill240c 2026-09-09 23:10
        pm_max_concur_towers.addItem(new PulldownItem<>("15")); //added by ikill240c 2026-09-09 23:10
        pm_max_concur_towers.addItem(new PulldownItem<>(i18n("unlimited")));
        var pb_max_concur_towers = new PulldownButton<>(gui_root, pm_max_concur_towers, 2, 150); //added by ikill240c 2026-09-09 23:10
        pm_max_concur_towers.addItemChosenListener((_, _) -> markModified()); //added by ikill240c 2026-09-09 23:10
        group_max_concur_towers.addChild(pb_max_concur_towers); //added by ikill240c 2026-09-09 23:10
        label_max_concur_towers.place(); //added by ikill240c 2026-09-09 23:10
        pb_max_concur_towers.place(label_max_concur_towers, RIGHT_MID); //added by ikill240c 2026-09-09 23:10
        group_max_concur_towers.compileCanvas(); //added by ikill240c 2026-09-09 23:10
        group_advanced_settings.addChild(group_max_concur_towers); //added by ikill240c 2026-09-09 23:10

        // King of the Island's statue count (3-10, see WorldParameters.kothStatueCount() and
        // KingOfTheIslandModeRules) - harmless when a different game mode is selected, since only
        // that mode's rules ever read it. //added by ikill240c
        Group group_koth_statue_count = new Group(); //added by ikill240c
        Label label_koth_statue_count = new Label(i18n("koth_statue_count"), Skin.getSkin().getEditFont()); //added by ikill240c
        group_koth_statue_count.addChild(label_koth_statue_count); //added by ikill240c
        pm_koth_statue_count = new PulldownMenu<>(); //added by ikill240c
        for (int i = WorldParameters.MIN_KOTH_STATUE_COUNT; i <= WorldParameters.MAX_KOTH_STATUE_COUNT; i++) { //added by ikill240c
            pm_koth_statue_count.addItem(new PulldownItem<>(Integer.toString(i))); //added by ikill240c
        } //added by ikill240c
        var pb_koth_statue_count = new PulldownButton<>(gui_root, //added by ikill240c
                pm_koth_statue_count, WorldParameters.DEFAULT_KOTH_STATUE_COUNT - WorldParameters.MIN_KOTH_STATUE_COUNT, //added by ikill240c
                150); //added by ikill240c
        pm_koth_statue_count.addItemChosenListener((_, _) -> markModified()); //added by ikill240c
        group_koth_statue_count.addChild(pb_koth_statue_count); //added by ikill240c
        label_koth_statue_count.place(); //added by ikill240c
        pb_koth_statue_count.place(label_koth_statue_count, RIGHT_MID); //added by ikill240c
        group_koth_statue_count.compileCanvas(); //added by ikill240c
        group_advanced_settings.addChild(group_koth_statue_count); //added by ikill240c

        // Per-magic enable toggles - a sub-group of its own, matching the "Starting Warriors"
        // organization pattern above. //added by ikill240c
        Group group_magic_toggles = new Group(); //added by ikill240c
        Label label_magic_toggles_header = new Label(i18n("magic_toggles_header"), //added by ikill240c
                Skin.getSkin().getHeadlineFont()); //added by ikill240c
        group_magic_toggles.addChild(label_magic_toggles_header); //added by ikill240c
        label_magic_toggles_header.place(); //added by ikill240c

        cb_magic1_enabled = new CheckBox(true, i18n("magic1_enabled"), i18n("magic1_enabled_tip")); //added by ikill240c
        group_magic_toggles.addChild(cb_magic1_enabled); //added by ikill240c
        cb_magic1_enabled.place(label_magic_toggles_header, BOTTOM_LEFT); //added by ikill240c
        cb_magic1_enabled.addCheckBoxListener(_ -> markModified()); //added by ikill240c

        cb_magic2_enabled = new CheckBox(true, i18n("magic2_enabled"), i18n("magic2_enabled_tip")); //added by ikill240c
        group_magic_toggles.addChild(cb_magic2_enabled); //added by ikill240c
        cb_magic2_enabled.place(cb_magic1_enabled, BOTTOM_LEFT); //added by ikill240c
        cb_magic2_enabled.addCheckBoxListener(_ -> markModified()); //added by ikill240c

        cb_magic3_enabled = new CheckBox(true, i18n("magic3_enabled"), i18n("magic3_enabled_tip")); //added by ikill240c
        group_magic_toggles.addChild(cb_magic3_enabled); //added by ikill240c
        cb_magic3_enabled.place(cb_magic2_enabled, BOTTOM_LEFT); //added by ikill240c
        cb_magic3_enabled.addCheckBoxListener(_ -> markModified()); //added by ikill240c

        cb_chiefs_courage_enabled = new CheckBox(true, i18n("chiefs_courage_enabled"), //added by ikill240c
                i18n("chiefs_courage_enabled_tip")); //added by ikill240c
        group_magic_toggles.addChild(cb_chiefs_courage_enabled); //added by ikill240c
        cb_chiefs_courage_enabled.place(cb_magic3_enabled, BOTTOM_LEFT); //added by ikill240c
        cb_chiefs_courage_enabled.addCheckBoxListener(_ -> markModified()); //added by ikill240c

        group_magic_toggles.compileCanvas(); //added by ikill240c
        // Was group_advanced_settings.addChild(...) (Advanced tab) - moved onto the Custom Options
        // tab alongside group_costs_health, matching where a player would actually look for
        // "customize the magic/combat rules" style settings, rather than mixed in among the
        // Advanced tab's economy/building caps. //added by ikill240c
        custom_options.addChild(group_magic_toggles); //added by ikill240c

        // Global adaptive-AI toggle. Deliberately a single checkbox rather than a new per-slot
        // difficulty pulldown entry - see WorldParameters.adaptive_ai_enabled's field comment for
        // why. Constructed here (near the rest of the AI/economy tuning controls that already
        // exist at this point in the constructor) but placed on the standard tab above the player
        // roster, not added to any group in this immediate area - see its own placement call
        // further down, near where group_race_team is placed. //added by ikill240c
        cb_adaptive_ai_enabled = new CheckBox(false, i18n("adaptive_ai_enabled"), //added by ikill240c
                i18n("adaptive_ai_enabled_tip")); //added by ikill240c
        cb_adaptive_ai_enabled.addCheckBoxListener(_ -> markModified()); //added by ikill240c

        // "Set all AI to..." selector - see the field's own comment for why. Constructed here,
        // placed later near the roster (see its own placement call further down). //added by ikill240c
        pm_set_all_difficulty = new PulldownMenu<>(); //added by ikill240c
        pm_set_all_difficulty.addItem(new PulldownItem<>(i18n("easy_ai"), RosterTemplate.Fill.EASY_AI)); //added by ikill240c
        pm_set_all_difficulty.addItem(new PulldownItem<>(i18n("normal_ai"), RosterTemplate.Fill.NORMAL_AI)); //added by ikill240c
        pm_set_all_difficulty.addItem(new PulldownItem<>(i18n("hard_ai"), RosterTemplate.Fill.HARD_AI)); //added by ikill240c
        pm_set_all_difficulty.addItem(new PulldownItem<>(i18n("insane_ai"), RosterTemplate.Fill.INSANE_AI)); //added by ikill240c
        // Listener itself is registered further down, AFTER difficulty_pulldown_menus is actually
        // assigned (buildPlayerSlots() does that) - it's a blank final field, and the compiler
        // rejects any reference to one, even from inside a lambda that won't actually run until
        // long after construction finishes, if that reference sits syntactically before the
        // field's assignment in the constructor. See the listener's own registration site for the
        // rest of this. //added by ikill240c
        group_set_all_difficulty = new Group(); //added by ikill240c - was `Group group_set_all_difficulty = new Group();` (a local variable); now assigns the field declared above
        Label label_set_all_difficulty = new Label(i18n("set_all_ai_difficulty"), Skin.getSkin().getEditFont()); //added by ikill240c
        group_set_all_difficulty.addChild(label_set_all_difficulty); //added by ikill240c
        var pb_set_all_difficulty = new PulldownButton<>(gui_root, pm_set_all_difficulty, 0, 115); //added by ikill240c
        group_set_all_difficulty.addChild(pb_set_all_difficulty); //added by ikill240c
        label_set_all_difficulty.place(); //added by ikill240c
        pb_set_all_difficulty.place(label_set_all_difficulty, RIGHT_MID); //added by ikill240c
        group_set_all_difficulty.compileCanvas(); //added by ikill240c

        // cb_free_for_all's own construction (was previously right before its use near the
        // roster, after buildPlayerSlots()) moved up here, alongside cb_adaptive_ai_enabled/
        // pm_set_all_difficulty, so all three bulk-apply roster controls can be handed to
        // roster_panel.setHeader() as one unit before buildPlayerSlots()'s FIRST call - the
        // listener itself still has to wait (see its own registration site further down) since it
        // references team_pulldown_menus, a blank final only buildPlayerSlots() assigns.
        // //added by ikill240c
        cb_free_for_all = new CheckBox(false, i18n("free_for_all"), i18n("free_for_all_tip")); //added by ikill240c

        // Same construction-timing reasoning as cb_free_for_all just above - grouped with the
        // other standard-tab/roster-adjacent toggles here, placed later near the roster.
        // //added by ikill240c
        cb_team_together = new CheckBox(true, i18n("team_together"), i18n("team_together_tip")); //added by ikill240c
        cb_team_together.addCheckBoxListener(_ -> markModified()); //added by ikill240c

        // Magic costs and health multipliers - another sub-group, same organization pattern.
        // //added by ikill240c
        Group group_costs_health = new Group(); //added by ikill240c
        Label label_costs_health_header = new Label(i18n("costs_health_header"), //added by ikill240c
                Skin.getSkin().getHeadlineFont()); //added by ikill240c
        group_costs_health.addChild(label_costs_health_header); //added by ikill240c
        label_costs_health_header.place(); //added by ikill240c

        Label label_magic1_cost = new Label(i18n("magic1_cost"), Skin.getSkin().getEditFont()); //added by ikill240c
        group_costs_health.addChild(label_magic1_cost); //added by ikill240c
        slider_magic1_cost = new Slider(SETTING_SLIDER_LENGTH, MIN_MAGIC_COST, MAX_MAGIC_COST, 40); //added by ikill240c
        label_magic1_cost_value = new Label("40", Skin.getSkin().getEditFont(), VALUE_LABEL_WIDTH); //added by ikill240c
        slider_magic1_cost.addValueListener(value -> { //added by ikill240c
            label_magic1_cost_value.setText(Long.toString(value)); //added by ikill240c
            markModified(); //added by ikill240c
        }); //added by ikill240c
        group_costs_health.addChild(slider_magic1_cost); //added by ikill240c
        group_costs_health.addChild(label_magic1_cost_value); //added by ikill240c
        label_magic1_cost.place(label_costs_health_header, BOTTOM_LEFT); //added by ikill240c
        slider_magic1_cost.place(label_magic1_cost, RIGHT_MID); //added by ikill240c
        label_magic1_cost_value.place(slider_magic1_cost, RIGHT_MID); //added by ikill240c

        Label label_magic2_cost = new Label(i18n("magic2_cost"), Skin.getSkin().getEditFont()); //added by ikill240c
        group_costs_health.addChild(label_magic2_cost); //added by ikill240c
        slider_magic2_cost = new Slider(SETTING_SLIDER_LENGTH, MIN_MAGIC_COST, MAX_MAGIC_COST, 70); //added by ikill240c
        label_magic2_cost_value = new Label("70", Skin.getSkin().getEditFont(), VALUE_LABEL_WIDTH); //added by ikill240c
        slider_magic2_cost.addValueListener(value -> { //added by ikill240c
            label_magic2_cost_value.setText(Long.toString(value)); //added by ikill240c
            markModified(); //added by ikill240c
        }); //added by ikill240c
        group_costs_health.addChild(slider_magic2_cost); //added by ikill240c
        group_costs_health.addChild(label_magic2_cost_value); //added by ikill240c
        label_magic2_cost.place(label_magic1_cost, BOTTOM_LEFT); //added by ikill240c
        slider_magic2_cost.place(label_magic2_cost, RIGHT_MID); //added by ikill240c
        label_magic2_cost_value.place(slider_magic2_cost, RIGHT_MID); //added by ikill240c

        Label label_magic3_cost = new Label(i18n("magic3_cost"), Skin.getSkin().getEditFont()); //added by ikill240c
        group_costs_health.addChild(label_magic3_cost); //added by ikill240c
        slider_magic3_cost = new Slider(SETTING_SLIDER_LENGTH, MIN_MAGIC_COST, MAX_MAGIC_COST, 120); //added by ikill240c
        label_magic3_cost_value = new Label("90", Skin.getSkin().getEditFont(), VALUE_LABEL_WIDTH); //added by ikill240c
        slider_magic3_cost.addValueListener(value -> { //added by ikill240c
            label_magic3_cost_value.setText(Long.toString(value)); //added by ikill240c
            markModified(); //added by ikill240c
        }); //added by ikill240c
        group_costs_health.addChild(slider_magic3_cost); //added by ikill240c
        group_costs_health.addChild(label_magic3_cost_value); //added by ikill240c
        label_magic3_cost.place(label_magic2_cost, BOTTOM_LEFT); //added by ikill240c
        slider_magic3_cost.place(label_magic3_cost, RIGHT_MID); //added by ikill240c
        label_magic3_cost_value.place(slider_magic3_cost, RIGHT_MID); //added by ikill240c

        Label label_building_health_mult = new Label(i18n("building_health_mult"), Skin.getSkin().getEditFont()); //added by ikill240c
        group_costs_health.addChild(label_building_health_mult); //added by ikill240c
        slider_building_health_mult = new Slider(SETTING_SLIDER_LENGTH, MIN_HEALTH_MULT_PERCENT, //added by ikill240c
                MAX_HEALTH_MULT_PERCENT, DEFAULT_HEALTH_MULT_PERCENT); //added by ikill240c
        label_building_health_mult_value = new Label(DEFAULT_HEALTH_MULT_PERCENT + "%", //added by ikill240c
                Skin.getSkin().getEditFont(), VALUE_LABEL_WIDTH); //added by ikill240c
        slider_building_health_mult.addValueListener(value -> { //added by ikill240c
            label_building_health_mult_value.setText(value + "%"); //added by ikill240c
            markModified(); //added by ikill240c
        }); //added by ikill240c
        group_costs_health.addChild(slider_building_health_mult); //added by ikill240c
        group_costs_health.addChild(label_building_health_mult_value); //added by ikill240c
        label_building_health_mult.place(label_magic3_cost, BOTTOM_LEFT); //added by ikill240c
        slider_building_health_mult.place(label_building_health_mult, RIGHT_MID); //added by ikill240c
        label_building_health_mult_value.place(slider_building_health_mult, RIGHT_MID); //added by ikill240c

        Label label_viking_chief_health_mult = new Label(i18n("viking_chief_health_mult"), //added by ikill240c
                Skin.getSkin().getEditFont()); //added by ikill240c
        group_costs_health.addChild(label_viking_chief_health_mult); //added by ikill240c
        slider_viking_chief_health_mult = new Slider(SETTING_SLIDER_LENGTH, MIN_HEALTH_MULT_PERCENT, //added by ikill240c
                MAX_HEALTH_MULT_PERCENT, DEFAULT_HEALTH_MULT_PERCENT); //added by ikill240c
        label_viking_chief_health_mult_value = new Label(DEFAULT_HEALTH_MULT_PERCENT + "%", //added by ikill240c
                Skin.getSkin().getEditFont(), VALUE_LABEL_WIDTH); //added by ikill240c
        slider_viking_chief_health_mult.addValueListener(value -> { //added by ikill240c
            label_viking_chief_health_mult_value.setText(value + "%"); //added by ikill240c
            markModified(); //added by ikill240c
        }); //added by ikill240c
        group_costs_health.addChild(slider_viking_chief_health_mult); //added by ikill240c
        group_costs_health.addChild(label_viking_chief_health_mult_value); //added by ikill240c
        label_viking_chief_health_mult.place(label_building_health_mult, BOTTOM_LEFT); //added by ikill240c
        slider_viking_chief_health_mult.place(label_viking_chief_health_mult, RIGHT_MID); //added by ikill240c
        label_viking_chief_health_mult_value.place(slider_viking_chief_health_mult, RIGHT_MID); //added by ikill240c

        Label label_native_chief_health_mult = new Label(i18n("native_chief_health_mult"), //added by ikill240c
                Skin.getSkin().getEditFont()); //added by ikill240c
        group_costs_health.addChild(label_native_chief_health_mult); //added by ikill240c
        slider_native_chief_health_mult = new Slider(SETTING_SLIDER_LENGTH, MIN_HEALTH_MULT_PERCENT, //added by ikill240c
                MAX_HEALTH_MULT_PERCENT, DEFAULT_HEALTH_MULT_PERCENT); //added by ikill240c
        label_native_chief_health_mult_value = new Label(DEFAULT_HEALTH_MULT_PERCENT + "%", //added by ikill240c
                Skin.getSkin().getEditFont(), VALUE_LABEL_WIDTH); //added by ikill240c
        slider_native_chief_health_mult.addValueListener(value -> { //added by ikill240c
            label_native_chief_health_mult_value.setText(value + "%"); //added by ikill240c
            markModified(); //added by ikill240c
        }); //added by ikill240c
        group_costs_health.addChild(slider_native_chief_health_mult); //added by ikill240c
        group_costs_health.addChild(label_native_chief_health_mult_value); //added by ikill240c
        label_native_chief_health_mult.place(label_viking_chief_health_mult, BOTTOM_LEFT); //added by ikill240c
        slider_native_chief_health_mult.place(label_native_chief_health_mult, RIGHT_MID); //added by ikill240c
        label_native_chief_health_mult_value.place(slider_native_chief_health_mult, RIGHT_MID); //added by ikill240c

        Label label_unit_range_mult = new Label(i18n("unit_range_mult"), Skin.getSkin().getEditFont()); //added by ikill240c
        group_costs_health.addChild(label_unit_range_mult); //added by ikill240c
        slider_unit_range_mult = new Slider(SETTING_SLIDER_LENGTH, MIN_HEALTH_MULT_PERCENT, //added by ikill240c
                MAX_HEALTH_MULT_PERCENT, DEFAULT_HEALTH_MULT_PERCENT); //added by ikill240c
        label_unit_range_mult_value = new Label(DEFAULT_HEALTH_MULT_PERCENT + "%", //added by ikill240c
                Skin.getSkin().getEditFont(), VALUE_LABEL_WIDTH); //added by ikill240c
        slider_unit_range_mult.addValueListener(value -> { //added by ikill240c
            label_unit_range_mult_value.setText(value + "%"); //added by ikill240c
            markModified(); //added by ikill240c
        }); //added by ikill240c
        group_costs_health.addChild(slider_unit_range_mult); //added by ikill240c
        group_costs_health.addChild(label_unit_range_mult_value); //added by ikill240c
        // Was a straight BOTTOM_LEFT continuation of the same single column (making this group 12
        // rows tall). Split into 2 columns here instead - anchoring off label_magic1_cost_value (the
        // rightmost widget of row 1) rather than the header guarantees no overlap with column 1's
        // sliders regardless of label text width or locale. //added by ikill240c 2026-09-12
        label_unit_range_mult.place(label_magic1_cost_value, RIGHT_TOP); //added by ikill240c 2026-09-12
        slider_unit_range_mult.place(label_unit_range_mult, RIGHT_MID); //added by ikill240c
        label_unit_range_mult_value.place(slider_unit_range_mult, RIGHT_MID); //added by ikill240c

        Label label_armory_resource_cap = new Label(i18n("armory_resource_cap"), Skin.getSkin().getEditFont()); //added by ikill240c
        group_costs_health.addChild(label_armory_resource_cap); //added by ikill240c
        slider_armory_resource_cap = new Slider(SETTING_SLIDER_LENGTH, MIN_RESOURCE_CAP, MAX_RESOURCE_CAP, //added by ikill240c
                DEFAULT_RESOURCE_CAP); //added by ikill240c
        label_armory_resource_cap_value = new Label(Integer.toString(DEFAULT_RESOURCE_CAP), //added by ikill240c
                Skin.getSkin().getEditFont(), VALUE_LABEL_WIDTH); //added by ikill240c
        slider_armory_resource_cap.addValueListener(value -> { //added by ikill240c
            label_armory_resource_cap_value.setText(Long.toString(value)); //added by ikill240c
            markModified(); //added by ikill240c
        }); //added by ikill240c
        group_costs_health.addChild(slider_armory_resource_cap); //added by ikill240c
        group_costs_health.addChild(label_armory_resource_cap_value); //added by ikill240c
        label_armory_resource_cap.place(label_unit_range_mult, BOTTOM_LEFT); //added by ikill240c
        slider_armory_resource_cap.place(label_armory_resource_cap, RIGHT_MID); //added by ikill240c
        label_armory_resource_cap_value.place(slider_armory_resource_cap, RIGHT_MID); //added by ikill240c

        Label label_rock_resource_cap = new Label(i18n("rock_resource_cap"), Skin.getSkin().getEditFont()); //added by ikill240c
        group_costs_health.addChild(label_rock_resource_cap); //added by ikill240c
        slider_rock_resource_cap = new Slider(SETTING_SLIDER_LENGTH, MIN_RESOURCE_CAP, MAX_RESOURCE_CAP, //added by ikill240c
                DEFAULT_RESOURCE_CAP); //added by ikill240c
        label_rock_resource_cap_value = new Label(Integer.toString(DEFAULT_RESOURCE_CAP), //added by ikill240c
                Skin.getSkin().getEditFont(), VALUE_LABEL_WIDTH); //added by ikill240c
        slider_rock_resource_cap.addValueListener(value -> { //added by ikill240c
            label_rock_resource_cap_value.setText(Long.toString(value)); //added by ikill240c
            markModified(); //added by ikill240c
        }); //added by ikill240c
        group_costs_health.addChild(slider_rock_resource_cap); //added by ikill240c
        group_costs_health.addChild(label_rock_resource_cap_value); //added by ikill240c
        label_rock_resource_cap.place(label_armory_resource_cap, BOTTOM_LEFT); //added by ikill240c
        slider_rock_resource_cap.place(label_rock_resource_cap, RIGHT_MID); //added by ikill240c
        label_rock_resource_cap_value.place(slider_rock_resource_cap, RIGHT_MID); //added by ikill240c

        Label label_iron_resource_cap = new Label(i18n("iron_resource_cap"), Skin.getSkin().getEditFont()); //added by ikill240c
        group_costs_health.addChild(label_iron_resource_cap); //added by ikill240c
        slider_iron_resource_cap = new Slider(SETTING_SLIDER_LENGTH, MIN_RESOURCE_CAP, MAX_RESOURCE_CAP, //added by ikill240c
                DEFAULT_RESOURCE_CAP); //added by ikill240c
        label_iron_resource_cap_value = new Label(Integer.toString(DEFAULT_RESOURCE_CAP), //added by ikill240c
                Skin.getSkin().getEditFont(), VALUE_LABEL_WIDTH); //added by ikill240c
        slider_iron_resource_cap.addValueListener(value -> { //added by ikill240c
            label_iron_resource_cap_value.setText(Long.toString(value)); //added by ikill240c
            markModified(); //added by ikill240c
        }); //added by ikill240c
        group_costs_health.addChild(slider_iron_resource_cap); //added by ikill240c
        group_costs_health.addChild(label_iron_resource_cap_value); //added by ikill240c
        label_iron_resource_cap.place(label_rock_resource_cap, BOTTOM_LEFT); //added by ikill240c
        slider_iron_resource_cap.place(label_iron_resource_cap, RIGHT_MID); //added by ikill240c
        label_iron_resource_cap_value.place(slider_iron_resource_cap, RIGHT_MID); //added by ikill240c

        Label label_rubber_resource_cap = new Label(i18n("rubber_resource_cap"), Skin.getSkin().getEditFont()); //added by ikill240c
        group_costs_health.addChild(label_rubber_resource_cap); //added by ikill240c
        slider_rubber_resource_cap = new Slider(SETTING_SLIDER_LENGTH, MIN_RESOURCE_CAP, MAX_RESOURCE_CAP, //added by ikill240c
                DEFAULT_RESOURCE_CAP); //added by ikill240c
        label_rubber_resource_cap_value = new Label(Integer.toString(DEFAULT_RESOURCE_CAP), //added by ikill240c
                Skin.getSkin().getEditFont(), VALUE_LABEL_WIDTH); //added by ikill240c
        slider_rubber_resource_cap.addValueListener(value -> { //added by ikill240c
            label_rubber_resource_cap_value.setText(Long.toString(value)); //added by ikill240c
            markModified(); //added by ikill240c
        }); //added by ikill240c
        group_costs_health.addChild(slider_rubber_resource_cap); //added by ikill240c
        group_costs_health.addChild(label_rubber_resource_cap_value); //added by ikill240c
        label_rubber_resource_cap.place(label_iron_resource_cap, BOTTOM_LEFT); //added by ikill240c
        slider_rubber_resource_cap.place(label_rubber_resource_cap, RIGHT_MID); //added by ikill240c
        label_rubber_resource_cap_value.place(slider_rubber_resource_cap, RIGHT_MID); //added by ikill240c

        group_costs_health.compileCanvas(); //added by ikill240c
        custom_options.addChild(group_costs_health); //added by ikill240c - moved off the Advanced tab onto its own Custom Options tab

        // gamespeed
        Group group_gamespeed = new Group();
        Label label_gamespeed = new Label(i18n("gamespeed"), Skin.getSkin().getEditFont());
        group_gamespeed.addChild(label_gamespeed);
        pm_gamespeed = new PulldownMenu<>();
        pm_gamespeed.addItem(new PulldownItem<>(ServerMessageBundler.getGamespeedString(Game.GAMESPEED_SLOW)));
        pm_gamespeed.addItem(new PulldownItem<>(ServerMessageBundler.getGamespeedString(Game.GAMESPEED_NORMAL)));
        pm_gamespeed.addItem(new PulldownItem<>(ServerMessageBundler.getGamespeedString(Game.GAMESPEED_FAST)));
        pm_gamespeed.addItem(new PulldownItem<>(ServerMessageBundler.getGamespeedString(Game.GAMESPEED_LUDICROUS)));
        // Default the pulldown to the player's game speed setting from the options menu.
        int gamespeed_index = Math.clamp(Globals.gamespeed - Game.GAMESPEED_SLOW, 0,
                Game.GAMESPEED_LUDICROUS - Game.GAMESPEED_SLOW);
        var pb_gamespeed = new PulldownButton<>(gui_root, pm_gamespeed, gamespeed_index, 150);
        pm_gamespeed.addItemChosenListener((_, _) -> markModified());
        group_gamespeed.addChild(pb_gamespeed);
        label_gamespeed.place();
        pb_gamespeed.place(label_gamespeed, RIGHT_MID);
        group_gamespeed.compileCanvas();

        if (multiplayer) {
            group_map_options.addChild(group_gamespeed);
        }
        // size
        Group group_size = new Group();

        Label label_size = new Label(i18n("island_size"), Skin.getSkin().getEditFont());
        group_size.addChild(label_size);

        pulldown_size = new PulldownMenu<>();
        pulldown_size.addItem(new PulldownItem<>(ServerMessageBundler.getSizeString(Game.SIZE_SMALL)));
        pulldown_size.addItem(new PulldownItem<>(ServerMessageBundler.getSizeString(Game.SIZE_MEDIUM)));
        pulldown_size.addItem(new PulldownItem<>(ServerMessageBundler.getSizeString(Game.SIZE_LARGE)));
        pulldown_size.addItem(new PulldownItem<>(ServerMessageBundler.getSizeString(Game.SIZE_ENORMOUS)));
        pulldown_size.addItem(new PulldownItem<>(ServerMessageBundler.getSizeString(Game.SIZE_UNREAL))); //added by ikill240c - unconditional, placed right after Enormous and before the conditional Archipelago item so its pulldown position always matches SIZES[4]/ARCHIPELAGO[4] above
        if (Globals.SHIPS_ENABLED) {
            pulldown_size.addItem(new PulldownItem<>(ServerMessageBundler.getSizeString(Game.SIZE_ARCHIPELAGO)));
        }

        var pb_size = new PulldownButton<>(gui_root, pulldown_size, 1, 150);
        group_size.addChild(pb_size);
        label_size.place();
        pb_size.place(label_size, RIGHT_MID);
        group_size.compileCanvas();
        group_map_options.addChild(group_size);
        pulldown_size.addItemChosenListener(new PulldownUpdateMapcodeListener());

        // seed
        Label label_seed = new Label(i18n("map_code"), Skin.getSkin().getEditFont());
        label_mapcode = new Label("", Skin.getSkin().getHeadlineFont(), 500);

        Group group_seed = new Group();
        group_seed.addChild(label_seed);
        group_seed.addChild(label_mapcode);
        label_seed.place();
        label_mapcode.place(label_seed, RIGHT_MID);
        group_seed.compileCanvas();
        advanced.addChild(group_seed);

        // terrain_type
        Group group_terrain_type = new Group();
        Label label_terrain_type = new Label(i18n("terrain_type"), Skin.getSkin().getEditFont());
        group_terrain_type.addChild(label_terrain_type);
        pm_terrain_type = new PulldownMenu<>();
        pm_terrain_type.addItem(new PulldownItem<>(ServerMessageBundler.getTerrainTypeString(
                Game.TERRAIN_TYPE_NATIVE)));
        pm_terrain_type.addItem(new PulldownItem<>(ServerMessageBundler.getTerrainTypeString(
                Game.TERRAIN_TYPE_VIKING)));
        var pb_terrain_type = new PulldownButton<>(gui_root, pm_terrain_type, 0, 150);
        group_terrain_type.addChild(pb_terrain_type);
        label_terrain_type.place();
        pb_terrain_type.place(label_terrain_type, RIGHT_MID);
        group_terrain_type.compileCanvas();
        pm_terrain_type.addItemChosenListener(new PulldownUpdateMapcodeListener());
        group_map_options.addChild(group_terrain_type);

        Group group_sliders = new Group();
        // hills
        Label label_hills_low = new Label(i18n("min"), Skin.getSkin().getEditFont());
        group_sliders.addChild(label_hills_low);
        Label label_hills_high = new Label(i18n("max"), Skin.getSkin().getEditFont());
        group_sliders.addChild(label_hills_high);
        Label label_hills = new Label(i18n("hills"), Skin.getSkin().getEditFont());
        group_sliders.addChild(label_hills);
        slider_hills = new Slider(SLIDER_LENGTH, 0, MAP_SETTING_SLIDER_MAX, SLIDER_MAX_VALUE / 2); //added by ikill240c - widened upper bound only; default (50%) is unchanged
        slider_hills.addValueListener(new SliderUpdateMapcodeListener());
        group_sliders.addChild(slider_hills);

        // vegetation
        Label label_vegetation_low = new Label(i18n("min"), Skin.getSkin().getEditFont());
        group_sliders.addChild(label_vegetation_low);
        Label label_vegetation_high = new Label(i18n("max"), Skin.getSkin().getEditFont());
        group_sliders.addChild(label_vegetation_high);
        Label label_vegetation = new Label(i18n("trees"), Skin.getSkin().getEditFont());
        group_sliders.addChild(label_vegetation);
        slider_vegetation = new Slider(SLIDER_LENGTH, 0, MAP_SETTING_SLIDER_MAX, SLIDER_MAX_VALUE / 2); //added by ikill240c - widened upper bound only; default (50%) is unchanged
        slider_vegetation.addValueListener(new SliderUpdateMapcodeListener());
        group_sliders.addChild(slider_vegetation);

        // supplies
        Label label_supplies_low = new Label(i18n("min"), Skin.getSkin().getEditFont());
        group_sliders.addChild(label_supplies_low);
        Label label_supplies_high = new Label(i18n("max"), Skin.getSkin().getEditFont());
        group_sliders.addChild(label_supplies_high);
        Label label_supplies = new Label(i18n("resources"), Skin.getSkin().getEditFont());
        group_sliders.addChild(label_supplies);
        slider_supplies = new Slider(SLIDER_LENGTH, 0, MAP_SETTING_SLIDER_MAX, SLIDER_MAX_VALUE / 2); //added by ikill240c - widened upper bound only; default (50%) is unchanged
        slider_supplies.addValueListener(new SliderUpdateMapcodeListener());
        group_sliders.addChild(slider_supplies);

        // supplies
        label_supplies.place();
        label_supplies_low.place(label_supplies, RIGHT_MID);
        slider_supplies.place(label_supplies_low, RIGHT_MID);
        label_supplies_high.place(slider_supplies, RIGHT_MID);
        // vegetation
        label_vegetation.place(label_supplies, TOP_LEFT, Skin.getSkin().getFormData().sectionSpacing());
        slider_vegetation.place(slider_supplies, TOP_MID, Skin.getSkin().getFormData().sectionSpacing());
        label_vegetation_low.place(slider_vegetation, LEFT_MID);
        label_vegetation_high.place(slider_vegetation, RIGHT_MID);
        // hills
        label_hills.place(label_vegetation, TOP_LEFT, Skin.getSkin().getFormData().sectionSpacing());
        slider_hills.place(slider_vegetation, TOP_MID, Skin.getSkin().getFormData().sectionSpacing());
        label_hills_low.place(slider_hills, LEFT_MID);
        label_hills_high.place(slider_hills, RIGHT_MID);
        // sliders
        group_sliders.compileCanvas();
        advanced.addChild(group_sliders);

        // player count dropdown (in advanced panel)
        Group group_num_players = new Group();
        Label label_player_slots = new Label(i18n("players"), Skin.getSkin().getEditFont());
        group_num_players.addChild(label_player_slots);
        pulldown_menu_slots = new ScrollablePulldownMenu<>(DEFAULT_PLAYER_COUNT);
        for (int i = DEFAULT_PLAYER_COUNT; i <= MatchmakingServerInterface.MAX_PLAYERS; i++) {
            pulldown_menu_slots.addItem(new PulldownItem<>(Integer.toString(i)));
        }
        var pulldown_player_slots = new PulldownButton<>(gui_root, pulldown_menu_slots, 0, 150);
        group_num_players.addChild(pulldown_player_slots);
        label_player_slots.place();
        pulldown_player_slots.place(label_player_slots, RIGHT_MID);
        group_num_players.compileCanvas();
        advanced.addChild(group_num_players);

        // races and teams
        labels_players = new Label[MatchmakingServerInterface.MAX_PLAYERS];
        difficulty_pulldown_menus = new PulldownMenu[MatchmakingServerInterface.MAX_PLAYERS];
        race_pulldown_menus = new PulldownMenu[MatchmakingServerInterface.MAX_PLAYERS];
        team_pulldown_menus = new PulldownMenu[MatchmakingServerInterface.MAX_PLAYERS];
        difficulty_pulldown_buttons = new PulldownButton[MatchmakingServerInterface.MAX_PLAYERS];
        race_pulldown_buttons = new PulldownButton[MatchmakingServerInterface.MAX_PLAYERS];
        team_pulldown_buttons = new PulldownButton[MatchmakingServerInterface.MAX_PLAYERS];
        if (multiplayer) { //added by ikill240c
            // Bulk-apply roster controls (Adaptive AI, Set All AI To, Free For All, Team Together)
            // go on THIS tab for multiplayer, not the standard tab - see RosterPanel.setHeader()'s
            // own comment for why (this tab already scrolls internally instead of growing the whole
            // dialog, which is exactly what was pushing the OK/confirm button outside the visible
            // window). Placed and added here, BEFORE buildPlayerSlots() runs for the first time,
            // so setHeader() is already in effect the first time setRoster() places the roster
            // relative to it. The three checkboxes sit side by side in one row (per explicit
            // request, was stacked vertically); the difficulty pulldown goes on its own row below
            // them, since it isn't a checkbox and is wide enough that fitting it into the same row
            // would crowd the whole thing. //added by ikill240c
            cb_adaptive_ai_enabled.place(); //added by ikill240c
            cb_free_for_all.place(cb_adaptive_ai_enabled, RIGHT_MID, //added by ikill240c
                    Skin.getSkin().getFormData().objectSpacing()); //added by ikill240c
            cb_team_together.place(cb_free_for_all, RIGHT_MID, //added by ikill240c
                    Skin.getSkin().getFormData().objectSpacing()); //added by ikill240c
            group_set_all_difficulty.place(cb_adaptive_ai_enabled, BOTTOM_LEFT, //added by ikill240c
                    Skin.getSkin().getFormData().sectionSpacing()); //added by ikill240c
            roster_panel.addChild(cb_adaptive_ai_enabled); //added by ikill240c
            roster_panel.addChild(cb_free_for_all); //added by ikill240c
            roster_panel.addChild(cb_team_together); //added by ikill240c
            roster_panel.addChild(group_set_all_difficulty); //added by ikill240c
            roster_panel.setHeader(group_set_all_difficulty); //added by ikill240c
        } //added by ikill240c
        ScrollableGroup group_race_team = buildPlayerSlots(player_count);
        if (multiplayer) {
            roster_panel.setRoster(group_race_team);
        } else {
            standard.addChild(group_race_team);
        }

        // Registered here (not alongside pm_set_all_difficulty's own construction further up)
        // because difficulty_pulldown_menus - a blank final field - has only just now been
        // assigned by buildPlayerSlots() above. See pm_set_all_difficulty's own construction site
        // for the fuller explanation. //added by ikill240c
        pm_set_all_difficulty.addItemChosenListener((menu, index) -> { //added by ikill240c
            RosterTemplate.Fill chosen = menu.getItem(index).getAttachment(); //added by ikill240c
            if (chosen == null) //added by ikill240c
                return; //added by ikill240c
            // Slot 0 is always the local human player and is never touched here - matches every
            // other bulk-apply control on this form (e.g. Free For All also leaves slot 0's own
            // meaning alone). //added by ikill240c
            for (int i = 1; i < player_count; i++) { //added by ikill240c
                difficulty_pulldown_menus[i].chooseItem(fillToDifficultyIndex(chosen, i)); //added by ikill240c
            } //added by ikill240c
            markModified(); //added by ikill240c
        }); //added by ikill240c

        cb_free_for_all.addCheckBoxListener(_ -> { //added by ikill240c - construction itself moved earlier, see cb_adaptive_ai_enabled's neighboring comment for why
            if (cb_free_for_all.isMarked()) { //added by ikill240c
                // Matches defaultTeam(i)'s own "every player gets team i" multiplayer default -
                // this just lets the host apply that same assignment on demand (e.g. after having
                // manually grouped some slots into shared teams first) instead of only ever
                // getting it as whatever the pulldowns happened to default to. //added by ikill240c
                for (int i = 0; i < player_count; i++) { //added by ikill240c
                    team_pulldown_menus[i].chooseItem(i); //added by ikill240c
                } //added by ikill240c
                markModified(); //added by ikill240c
            } //added by ikill240c
        }); //added by ikill240c

        // buttons
        Group group_buttons = new Group();

        button_ok = new OKButton(BUTTON_WIDTH);
        button_ok.addMouseClickListener(new OKListener());
        HorizButton button_cancel = new CancelButton(BUTTON_WIDTH);
        button_cancel.addMouseClickListener(new CancelButtonListener());

        // Was chained into the SAME horizontal row as button_ok/button_cancel (mapcode, then
        // custom_map, each placed further left of the previous one), extending that row's total
        // width every time a button was added to it - button_cancel anchors this whole group via
        // place() with no arguments, and the group itself is positioned via Origin.AT_END below,
        // so growing the row's width without adjusting that anchor pushed button_ok/button_cancel
        // themselves outside the visible window (confirmed by two separate screenshots showing
        // exactly that). Re-applying this fix now that the actual cause of this dialog's other
        // rendering problems (TerrainMenu's overall height exceeding what multiplayer's extra tab
        // chrome left room for, fixed via the new Economy & AI tab and the group_max_chiefs_per_q
        // anchor fix above) has been found and fixed separately - this button-row width issue was
        // never actually related to that, it was reverted alongside it only because both were
        // visible in the same screenshots and it seemed safest to get back to a clean baseline
        // before re-attempting either one individually. Stays inside ONE group (so Origin.AT_END
        // still only ever applies once) and simply stacks mapcode/custom_map in a row ABOVE
        // ok/cancel rather than extending the same row further sideways - taller instead of wider,
        // which this dialog has more headroom for than it has spare width. //added by ikill240c
        HorizButton button_mapcode = new HorizButton(i18n("enter_map_code"), 170); //added by ikill240c
        button_mapcode.addMouseClickListener(new MapcodeListener()); //added by ikill240c
        HorizButton button_custom_map = new HorizButton(i18n("load_custom_map"), 170); //added by ikill240c
        button_custom_map.addMouseClickListener(new CustomMapListener()); //added by ikill240c

        group_buttons.addChild(button_ok);
        group_buttons.addChild(button_cancel);
        group_buttons.addChild(button_mapcode); //added by ikill240c
        group_buttons.addChild(button_custom_map); //added by ikill240c

        button_cancel.place();
        button_ok.place(button_cancel, LEFT_MID);
        button_mapcode.place(button_cancel, TOP_RIGHT); //added by ikill240c - directly above button_cancel, right-aligned with it, rather than further left of button_ok
        button_custom_map.place(button_mapcode, LEFT_MID); //added by ikill240c

        group_buttons.compileCanvas();
        addChild(group_buttons);

        // map options
        if (multiplayer) {
            group_gamespeed.place();
            group_size.place(group_gamespeed, BOTTOM_RIGHT);
        } else {
            group_size.place();
        }
        group_terrain_type.place(group_size, BOTTOM_RIGHT);
        // Chieftain limit pulldowns moved to the Advanced tab. //added by ikill240c 2026-09-09 23:10
        // Max buildings placed directly under terrain type now - was chained after
        // group_initial_units/group_max_units, which no longer exist as standalone groups here
        // (both got folded into group_starting_warriors above, alongside the warrior-tier rows).
        // //added by ikill240c
        group_max_buildings.place(group_terrain_type, BOTTOM_RIGHT); //added by ikill240c
        // Was never placed at all - compileCanvas() (a few lines down) iterates every child of
        // group_map_options and requires each one to already be placed, which is exactly what threw
        // "Group compiled before being placed" here. //added by ikill240c
        group_starting_warriors.place(group_max_buildings, BOTTOM_RIGHT); //added by ikill240c
        group_map_options.compileCanvas();
        standard.addChild(group_map_options);

        // standard
        if (multiplayer) {
            label_name.place();
            if (Renderer.isRegistered())
                editline_name.place(label_name, RIGHT_MID);
            else
                label_default_name.place(label_name, RIGHT_MID);
            cb_rated.place(label_name, BOTTOM_LEFT, Skin.getSkin().getFormData().sectionSpacing());
            group_map_options.place(cb_rated, BOTTOM_LEFT);
            // cb_adaptive_ai_enabled/group_set_all_difficulty/cb_free_for_all are NOT placed here
            // for multiplayer anymore - they moved to roster_panel (its own tab) instead, placed
            // and added there before buildPlayerSlots() ever runs. See RosterPanel.setHeader()'s
            // own comment for why: this tab's own vertical stack of controls, once those three
            // were piled on top of everything already here, was tall enough to push this dialog's
            // total height past the visible window - which meant the AT_END-anchored OK/confirm
            // button ended up positioned outside the visible area entirely. //added by ikill240c
        } else {
            group_map_options.place();
            // Placed between group_map_options and the roster itself, i.e. directly above the
            // "Player 1" row - was on ModeAndPresetsPanel, then briefly inline in a different spot;
            // this is where it actually belongs per explicit request. The three checkboxes sit side
            // by side in one row (per explicit request, was stacked vertically); the difficulty
            // pulldown goes on its own row below them, since it isn't a checkbox and is wide enough
            // that fitting it into the same row would crowd the whole thing. //added by ikill240c
            cb_adaptive_ai_enabled.place(group_map_options, BOTTOM_LEFT, //added by ikill240c
                    Skin.getSkin().getFormData().sectionSpacing()); //added by ikill240c
            cb_free_for_all.place(cb_adaptive_ai_enabled, RIGHT_MID, //added by ikill240c
                    Skin.getSkin().getFormData().objectSpacing()); //added by ikill240c
            cb_team_together.place(cb_free_for_all, RIGHT_MID, //added by ikill240c
                    Skin.getSkin().getFormData().objectSpacing()); //added by ikill240c
            // Directly below the checkbox row - a host setting every AI slot's difficulty at once,
            // right where they're about to look at those same slots individually if they want to
            // override any of them afterward. //added by ikill240c
            group_set_all_difficulty.place(cb_adaptive_ai_enabled, BOTTOM_LEFT, //added by ikill240c
                    Skin.getSkin().getFormData().sectionSpacing()); //added by ikill240c
            group_race_team.place(group_set_all_difficulty, BOTTOM_LEFT, Skin.getSkin().getFormData().sectionSpacing()); //added by ikill240c
            // Only added to the standard tab for singleplayer now - see the multiplayer branch's
            // own comment above for why multiplayer's copies live on roster_panel instead.
            // //added by ikill240c
            standard.addChild(cb_adaptive_ai_enabled); //added by ikill240c
            standard.addChild(group_set_all_difficulty); //added by ikill240c
            standard.addChild(cb_free_for_all); //added by ikill240c
            standard.addChild(cb_team_together); //added by ikill240c
        }
        standard.compileCanvas();

        // advanced
        group_sliders.place();
        group_num_players.place(group_sliders, BOTTOM_LEFT, Skin.getSkin().getFormData().sectionSpacing());
        group_seed.place(group_num_players, BOTTOM_LEFT, Skin.getSkin().getFormData().sectionSpacing());
        // Place the chieftain limits + AI tuning pulldowns on the Advanced tab. //added by ikill240c 2026-09-09 23:10
        // Was a single vertical chain of all 15 groups below (plus group_costs_health's own 11
        // internal rows), making the Advanced tab extremely tall. Rearranged into columns instead -
        // each column starts level with the top of the previous one via RIGHT_TOP, then continues
        // downward as before via BOTTOM_RIGHT. Also fixes group_max_chiefs_per_q.place() previously
        // being called with no reference widget at all, which left it at the same (0,0) origin as
        // group_sliders and overlapping it.
        //
        // Every placement below now passes an explicit spacing value instead of relying on the
        // 2-arg place(neighbor, direction) overload's default (Skin's small objectSpacing()) -
        // that default was the same tight gap used for closely-related items like a label and its
        // own pulldown, not enough room between an entire row's rightmost content (a 150px-wide
        // pulldown button) and the next row/column's own label text, especially once label text
        // runs longer in some locales than the English original. Column gaps (RIGHT_TOP) get the
        // larger sectionSpacing(), since those need to clear an entire preceding row's width, not
        // just sit next to one label. //added by ikill240c
        // Was group_max_chiefs_per_q.place(group_sliders, RIGHT_TOP, ...) - anchored relative to
        // group_sliders, a sibling that stayed behind on the Advanced tab when
        // group_advanced_settings (and everything chained from this first element) moved to its
        // own Economy & AI tab. That left this whole subtree positioned relative to a group living
        // in a completely different panel's coordinate space, pushing it far to the side - this is
        // what was actually cutting off the right column's pulldown values (Max concurrent
        // quarters/armories/towers) in the screenshot, not a width/sizing problem with this tab
        // itself. Placed at this tab's own origin instead, now that it's the first (and only
        // top-level) element here. //added by ikill240c
        group_max_chiefs_per_q.place(); //added by ikill240c
        group_max_chiefs_total.place(group_max_chiefs_per_q, BOTTOM_RIGHT, ADVANCED_ROW_SPACING); //added by ikill240c
        group_chief_heal_idle.place(group_max_chiefs_total, BOTTOM_RIGHT, ADVANCED_ROW_SPACING); //added by ikill240c
        group_chief_heal_amount.place(group_chief_heal_idle, BOTTOM_RIGHT, ADVANCED_ROW_SPACING); //added by ikill240c
        group_target_quarters.place(group_chief_heal_amount, BOTTOM_RIGHT, ADVANCED_ROW_SPACING); //added by ikill240c

        group_target_armories.place(group_max_chiefs_per_q, RIGHT_TOP, Skin.getSkin().getFormData().sectionSpacing()); //added by ikill240c
        group_max_concur_quarters.place(group_target_armories, BOTTOM_RIGHT, ADVANCED_ROW_SPACING); //added by ikill240c
        group_max_concur_armories.place(group_max_concur_quarters, BOTTOM_RIGHT, ADVANCED_ROW_SPACING); //added by ikill240c
        group_num_res_towers.place(group_max_concur_armories, BOTTOM_RIGHT, ADVANCED_ROW_SPACING); //added by ikill240c
        group_max_concur_towers.place(group_num_res_towers, BOTTOM_RIGHT, ADVANCED_ROW_SPACING); //added by ikill240c
        group_koth_statue_count.place(group_max_concur_towers, BOTTOM_RIGHT, ADVANCED_ROW_SPACING); //added by ikill240c

        // group_ai_toggles.place(...) removed here - that group no longer exists, its one checkbox
        // moved to ModeAndPresetsPanel (see mode_and_presets). //added by ikill240c
        group_advanced_settings.compileCanvas(); //added by ikill240c 2026-09-09 23:10
        // Was advanced.addChild(...) with group_advanced_settings.place(group_seed, BOTTOM_LEFT,
        // ...) (placed relative to a sibling that stayed behind on the Advanced tab) - now its own
        // tab (economy_options, see its own Panel declaration above), so it's placed at this tab's
        // own origin instead, the same way group_costs_health is placed() with no arguments as the
        // first thing on the Custom Options tab just below. //added by ikill240c
        economy_options.addChild(group_advanced_settings); //added by ikill240c
        group_advanced_settings.place(); //added by ikill240c
        economy_options.compileCanvas(); //added by ikill240c
        advanced.compileCanvas(); //added by ikill240c - restored: "advanced" still holds group_seed/group_sliders/group_num_players and still needs its own compileCanvas() even though group_advanced_settings no longer lives here

        // custom options - group_costs_health at the tab's own origin, group_magic_toggles below
        // it. Both live on this tab now (see group_magic_toggles' own addChild comment for why it
        // moved here from Advanced). //added by ikill240c
        group_costs_health.place(); //added by ikill240c
        group_magic_toggles.place(group_costs_health, BOTTOM_LEFT, //added by ikill240c
                Skin.getSkin().getFormData().sectionSpacing()); //added by ikill240c
        custom_options.compileCanvas(); //added by ikill240c

        // Was `multiplayer ? new PanelGroup(1, mode_and_presets, standard, advanced, roster_panel)
        // : new PanelGroup(standard, advanced)` - single-player dropped mode_and_presets entirely from
        // the layout even though it's now always constructed above. roster_panel (shows other network
        // players) correctly stays multiplayer-only; mode_and_presets does not need to.
        // //added by ikill240c
        PanelGroup panel_group = multiplayer ? new PanelGroup(1, mode_and_presets, standard, advanced,
                custom_options, economy_options, roster_panel) : new PanelGroup(1, mode_and_presets, standard, //added by ikill240c
                advanced, custom_options, economy_options); //added by ikill240c
        addChild(panel_group);
        var playersChangedListener = new PulldownUpdatePlayersChangedListener(standard);
        playersChangedListener.setCurrentGroup(group_race_team);
        pulldown_menu_slots.addItemChosenListener(playersChangedListener);

        // Place objects
        label_headline.place();
        panel_group.place(label_headline, BOTTOM_LEFT);

        // buttons
        group_buttons.place(Origin.AT_END);

        compileCanvas();
        randomize();

        // set standard game
        pulldown_size.addItemChosenListener(new PulldownUpdateSizeListener());
        pm_terrain_type.addItemChosenListener(new PulldownUpdateTerrainListener());
        for (int i = 0; i < player_count; i++) {
            difficulty_pulldown_menus[i].addItemChosenListener(new PulldownUpdateHardListener());
            team_pulldown_menus[i].chooseItem(defaultTeam(i));
            if (!multiplayer) {
                if (i == 0) {
                    // Keep slot 0 on the original human choice so the single-player menu stays valid when the roster is initialized.
                    // ikill240v 2026-09-09 17:30
                    difficulty_pulldown_menus[i].chooseItem(0);
                } else {
                    // Give every non-host skirmish slot a valid AI difficulty so the full AI roster is created on first load.
                    // ikill240v 2026-09-09 17:30
                    difficulty_pulldown_menus[i].chooseItem(PlayerSlot.AI_EASY);
                    // Give each extra single-player AI slot a non-host race so it starts as a real AI player instead of an invalid empty slot.
                    // ikill240v 2026-09-09 17:30
                    race_pulldown_menus[i].chooseItem((race_pulldown_menus[0].getChosenItemIndex() + 1) % 2);
                }
            } else {
                // MP non-host slots default to Open (index 0); SP non-host slots default to Closed (index 0).
                difficulty_pulldown_menus[i].chooseItem(0);
            }
        }
        pulldown_size.chooseItem(1);
        if (!Renderer.isRegistered())
            pm_terrain_type.chooseItem(0);

        cb_rated.addCheckBoxListener(_ -> markModified());
        initialized = true;
    }

    private void markModified() {
        if (!initialized || apply_in_progress) {
            return;
        }
        if (!modified) {
            modified = true;
            updateBanner();
        }
    }

    private void updateBanner() {
        if (mode_and_presets != null) {
            mode_and_presets.setPresetState(current_preset, modified);
        }
    }

    private void setMapcode() {
        BigInteger max_val = BigInteger.ONE;
        BigInteger result = BigInteger.ZERO;
        result = result.add((new BigInteger("" + seed)).multiply(max_val));
        max_val = max_val.multiply(new BigInteger(SEED_CARDINALITY));
        int hills = slider_hills.getValue();
        result = result.add((new BigInteger(new byte[]{(byte) hills})).multiply(max_val));
        max_val = max_val.multiply(new BigInteger(new byte[]{SLIDER_CARDINALITY}));
        int vegetation_amount = slider_vegetation.getValue();
        result = result.add((new BigInteger(new byte[]{(byte) vegetation_amount})).multiply(max_val));
        max_val = max_val.multiply(new BigInteger(new byte[]{SLIDER_CARDINALITY}));
        int supplies_amount = slider_supplies.getValue();
        result = result.add((new BigInteger(new byte[]{(byte) supplies_amount})).multiply(max_val));
        max_val = max_val.multiply(new BigInteger(new byte[]{SLIDER_CARDINALITY}));
        int terrain_type = pm_terrain_type.getChosenItemIndex();
        result = result.add((new BigInteger(new byte[]{(byte) terrain_type})).multiply(max_val));
        max_val = max_val.multiply(new BigInteger(new byte[]{TERRAIN_TYPE_CARDINALITY}));
        int size = pulldown_size.getChosenItemIndex();
        result = result.add((new BigInteger(new byte[]{(byte) size})).multiply(max_val));

        String code = WordsEncoding.encode(result);
        label_mapcode.clear();
        label_mapcode.append(code);
        markModified();
    }

    private void setMapcodeLegacy() {
        BigInteger max_val = BigInteger.ONE;
        BigInteger result = BigInteger.ZERO;
        result = result.add((new BigInteger("" + seed)).multiply(max_val));
        max_val = max_val.multiply(new BigInteger(SEED_CARDINALITY));
        int hills = slider_hills.getValue();
        result = result.add((new BigInteger(new byte[]{(byte) hills})).multiply(max_val));
        max_val = max_val.multiply(new BigInteger(new byte[]{SLIDER_CARDINALITY}));
        int vegetation_amount = slider_vegetation.getValue();
        result = result.add((new BigInteger(new byte[]{(byte) vegetation_amount})).multiply(max_val));
        max_val = max_val.multiply(new BigInteger(new byte[]{SLIDER_CARDINALITY}));
        int supplies_amount = slider_supplies.getValue();
        result = result.add((new BigInteger(new byte[]{(byte) supplies_amount})).multiply(max_val));
        max_val = max_val.multiply(new BigInteger(new byte[]{SLIDER_CARDINALITY}));
        int terrain_type = pm_terrain_type.getChosenItemIndex();
        result = result.add((new BigInteger(new byte[]{(byte) terrain_type})).multiply(max_val));
        max_val = max_val.multiply(new BigInteger(new byte[]{TERRAIN_TYPE_CARDINALITY_LEGACY}));
        int size = pulldown_size.getChosenItemIndex();
        result = result.add((new BigInteger(new byte[]{(byte) size})).multiply(max_val));
        max_val = max_val.multiply(new BigInteger(new byte[]{SIZE_CARDINALITY_LEGACY}));
        int player_race = race_pulldown_menus[0].getChosenItemIndex();
        result = result.add((new BigInteger(new byte[]{(byte) player_race})).multiply(max_val));
        max_val = max_val.multiply(new BigInteger(new byte[]{RACE_CARDINALITY}));
        int player_team = team_pulldown_menus[0].getChosenItemIndex();
        result = result.add((new BigInteger(new byte[]{(byte) player_team})).multiply(max_val));
        max_val = max_val.multiply(new BigInteger(new byte[]{TEAM_CARDINALITY}));
        for (int i = 1; i < DEFAULT_PLAYER_COUNT; i++) {
            int difficulty = difficulty_pulldown_menus[i].getChosenItemIndex();
            result = result.add((new BigInteger(new byte[]{(byte) difficulty})).multiply(max_val));
            max_val = max_val.multiply(new BigInteger(new byte[]{DIFFICULTY_CARDINALITY}));
            int race = race_pulldown_menus[i].getChosenItemIndex();
            result = result.add((new BigInteger(new byte[]{(byte) race})).multiply(max_val));
            max_val = max_val.multiply(new BigInteger(new byte[]{RACE_CARDINALITY}));
            int team = team_pulldown_menus[i].getChosenItemIndex();
            result = result.add((new BigInteger(new byte[]{(byte) team})).multiply(max_val));
            max_val = max_val.multiply(new BigInteger(new byte[]{TEAM_CARDINALITY}));
        }

        String code = RegistrationKey.createString(result);
        label_mapcode.clear();
        label_mapcode.append(code);
        markModified();
    }

    public void parseMapcode(@NonNull String text) {
        if (text.indexOf(' ') == -1) {
            // Legacy letter-based notation
            String code = text.toUpperCase();
            BigInteger result = RegistrationKey.parseBits(code);
            parseBigIntegerLegacy(result);
            label_mapcode.clear();
            label_mapcode.append(code);
        } else {
            // New word-based notation
            try {
                BigInteger result = WordsEncoding.decode(text);
                parseBigInteger(result);
                label_mapcode.clear();
                label_mapcode.append(text);
            } catch (IllegalArgumentException _) {
                // Invalid word code — ignore
            }
        }
    }

    private void parseBigInteger(BigInteger result) {
        BigInteger max_val = MAX_VALUE;
        result = result.mod(max_val);
        max_val = max_val.divide(new BigInteger(new byte[]{SIZE_CARDINALITY}));
        int size = result.divide(max_val).intValue();
        if (pulldown_size.getSize() > size) {
            pulldown_size.chooseItem(size);
        }
        result = result.mod(max_val);
        max_val = max_val.divide(new BigInteger(new byte[]{TERRAIN_TYPE_CARDINALITY}));
        int terrain_type = result.divide(max_val).intValue();
        if (pm_terrain_type.getSize() > terrain_type) {
            pm_terrain_type.chooseItem(terrain_type);
        }
        result = result.mod(max_val);
        max_val = max_val.divide(new BigInteger(new byte[]{SLIDER_CARDINALITY}));
        int supplies_amount = result.divide(max_val).intValue();
        slider_supplies.setValue(supplies_amount);
        result = result.mod(max_val);
        max_val = max_val.divide(new BigInteger(new byte[]{SLIDER_CARDINALITY}));
        int vegetation_amount = result.divide(max_val).intValue();
        slider_vegetation.setValue(vegetation_amount);
        result = result.mod(max_val);
        max_val = max_val.divide(new BigInteger(new byte[]{SLIDER_CARDINALITY}));
        int hills = result.divide(max_val).intValue();
        slider_hills.setValue(hills);
        result = result.mod(max_val);
        max_val = max_val.divide(new BigInteger(SEED_CARDINALITY));
        seed = result.divide(max_val).intValue();
    }

    private void parseBigIntegerLegacy(BigInteger result) {
        BigInteger max_val = MAX_VALUE_LEGACY;
        for (int i = DEFAULT_PLAYER_COUNT - 1; i >= 1; i--) {
            result = result.mod(max_val);
            max_val = max_val.divide(new BigInteger(new byte[]{TEAM_CARDINALITY}));
            int team = result.divide(max_val).intValue();
            team_pulldown_menus[i].chooseItem(team);
            result = result.mod(max_val);
            max_val = max_val.divide(new BigInteger(new byte[]{RACE_CARDINALITY}));
            int race = result.divide(max_val).intValue();
            race_pulldown_menus[i].chooseItem(race);
            result = result.mod(max_val);
            max_val = max_val.divide(new BigInteger(new byte[]{DIFFICULTY_CARDINALITY}));
            int difficulty = result.divide(max_val).intValue();
            difficulty_pulldown_menus[i].chooseItem(difficulty);
            if (difficulty == 0) {
                labels_players[i].setDisabled(true);
                race_pulldown_buttons[i].setDisabled(true);
                team_pulldown_buttons[i].setDisabled(true);
            } else {
                labels_players[i].setDisabled(false);
                race_pulldown_buttons[i].setDisabled(false);
                team_pulldown_buttons[i].setDisabled(false);
            }
        }
        result = result.mod(max_val);
        max_val = max_val.divide(new BigInteger(new byte[]{TEAM_CARDINALITY}));
        int player_team = result.divide(max_val).intValue();
        team_pulldown_menus[0].chooseItem(player_team);
        result = result.mod(max_val);
        max_val = max_val.divide(new BigInteger(new byte[]{RACE_CARDINALITY}));
        int player_race = result.divide(max_val).intValue();
        race_pulldown_menus[0].chooseItem(player_race);
        result = result.mod(max_val);
        max_val = max_val.divide(new BigInteger(new byte[]{SIZE_CARDINALITY_LEGACY}));
        int size = result.divide(max_val).intValue();
        pulldown_size.chooseItem(size);
        result = result.mod(max_val);
        max_val = max_val.divide(new BigInteger(new byte[]{TERRAIN_TYPE_CARDINALITY_LEGACY}));
        int terrain_type = result.divide(max_val).intValue();
        pm_terrain_type.chooseItem(terrain_type);
        result = result.mod(max_val);
        max_val = max_val.divide(new BigInteger(new byte[]{SLIDER_CARDINALITY}));
        int supplies_amount = result.divide(max_val).intValue();
        slider_supplies.setValue(supplies_amount);
        result = result.mod(max_val);
        max_val = max_val.divide(new BigInteger(new byte[]{SLIDER_CARDINALITY}));
        int vegetation_amount = result.divide(max_val).intValue();
        slider_vegetation.setValue(vegetation_amount);

        result = result.mod(max_val);
        max_val = max_val.divide(new BigInteger(new byte[]{SLIDER_CARDINALITY}));
        int hills = result.divide(max_val).intValue();
        slider_hills.setValue(hills);

        result = result.mod(max_val);
        max_val = max_val.divide(new BigInteger(SEED_CARDINALITY));
        seed = result.divide(max_val).intValue();
    }

    public void setSeed(int seed) {
        this.seed = seed;
    }

    public @NonNull GUIObject getButtonOK() {
        return button_ok;
    }

    private final class CancelButtonListener implements MouseClickListener {
        @Override
        public void mouseClicked(@NonNull MouseButton button, int x, int y, int clicks) {
            owner.terrainMenuCancel();
        }
    }

    @SuppressWarnings("unchecked")
    private ScrollableGroup buildPlayerSlots(int count) {
        ScrollableGroup inner = new ScrollableGroup(200, 64);
        Random random = new Random(
                LocalEventQueue.getQueue().getHighPrecisionManager().getTick() * (long) LocalEventQueue.getQueue().getHighPrecisionManager().getTick());
        random.nextFloat();
        for (int i = 0; i < count; i++) {
            difficulty_pulldown_menus[i] = new PulldownMenu<>();
            race_pulldown_menus[i] = new PulldownMenu<>();
            team_pulldown_menus[i] = new ScrollablePulldownMenu<>(DEFAULT_PLAYER_COUNT);

            if (i == 0) {
                difficulty_pulldown_menus[i].addItem(new PulldownItem<>(i18n("human")));
            } else {
                // MP slots can wait for a human joiner; SP has no joiners so it omits Open. Adding Open shifts the MP
                // slot indices (Open 0, Closed 1, AI 2-4). See fillToDifficultyIndex / difficultyIndexToFill.
                if (multiplayer) {
                    difficulty_pulldown_menus[i].addItem(new PulldownItem<>(i18n("open")));
                }
                difficulty_pulldown_menus[i].addItem(new PulldownItem<>(i18n("closed")));
                difficulty_pulldown_menus[i].addItem(new PulldownItem<>(i18n("easy_ai")));
                difficulty_pulldown_menus[i].addItem(new PulldownItem<>(i18n("normal_ai")));
                difficulty_pulldown_menus[i].addItem(new PulldownItem<>(i18n("hard_ai")));
                difficulty_pulldown_menus[i].addItem(new PulldownItem<>(i18n("insane_ai"))); //added by ikill240c - was commented out; now that PlayerSlot.AI_INSANE/RosterTemplate.Fill.INSANE_AI/PlayerTypes.AIInsane/AdvancedAI.DIFFICULTY_INSANE all exist and difficultyIndexToFill()/fillToDifficultyIndex() both handle index 4(SP)/5(MP), this item has somewhere to actually resolve to
            }

            difficulty_pulldown_buttons[i] = new PulldownButton<>(gui_root, difficulty_pulldown_menus[i], 0, 115);
            inner.addChild(difficulty_pulldown_buttons[i]);

            for (int j = 0; j < RacesResources.getNumRaces(); j++) {
                race_pulldown_menus[i].addItem(new PulldownItem<>(RacesResources.getRaceName(j)));
            }

            race_pulldown_buttons[i] = new PulldownButton<>(gui_root, race_pulldown_menus[i], 0, 115);
            inner.addChild(race_pulldown_buttons[i]);
            for (int j = 0; j < count; j++) {
                PulldownItem<Void> team_item = new PulldownItem<>(i18n("team", Integer.toString(j + 1))); //added by ikill240c - was added directly with no color, so the pulldown never showed which color a team actually corresponds to, even though Settings.getSettings().team_colours[] (32 entries: 18 hand-picked + auto-generated, further customizable in the Accessibility panel) already exists and is already used to color labels_players[0] a few lines below
                team_item.setLabelColor(Settings.getSettings().team_colours[j]); //added by ikill240c
                team_pulldown_menus[i].addItem(team_item); //added by ikill240c
            }
            team_pulldown_buttons[i] = new PulldownButton<>(gui_root, team_pulldown_menus[i], Math.min(i, count - 1),
                    115);
            inner.addChild(team_pulldown_buttons[i]);
            if (i == 0) {
                labels_players[0] = new Label(i18n("player", Integer.toString(1)),
                        Skin.getSkin().getEditFont()).setColor(Settings.getSettings().team_colours[0]);
                inner.addChild(labels_players[0]);
                labels_players[0].place();
                difficulty_pulldown_buttons[0].place(labels_players[0], RIGHT_MID);
                race_pulldown_buttons[0].place(difficulty_pulldown_buttons[0], RIGHT_MID);
                team_pulldown_buttons[0].place(race_pulldown_buttons[0], RIGHT_MID);
            } else {
                labels_players[i] = new Label(i18n("player", Integer.toString(i + 1)),
                        Skin.getSkin().getEditFont()).setColor(Settings.getSettings().team_colours[i]);
                inner.addChild(labels_players[i]);
                labels_players[i].place(labels_players[i - 1], BOTTOM_RIGHT);
                difficulty_pulldown_buttons[i].place(labels_players[i], RIGHT_MID);
                race_pulldown_buttons[i].place(difficulty_pulldown_buttons[i], RIGHT_MID);
                team_pulldown_buttons[i].place(race_pulldown_buttons[i], RIGHT_MID);
                difficulty_pulldown_menus[i].addItemChosenListener(new DisableListener(i));
            }
            difficulty_pulldown_menus[i].addItemChosenListener(new PulldownUpdateMapcodeListener());
            race_pulldown_menus[i].addItemChosenListener(new PulldownUpdateMapcodeListener());
            team_pulldown_menus[i].addItemChosenListener(new PulldownUpdateMapcodeListener());
        }
        inner.compileCanvas();
        return inner;
    }

    private void randomize() {
        Random random = new Random(
                LocalEventQueue.getQueue().getHighPrecisionManager().getTick() * (long) LocalEventQueue.getQueue().getHighPrecisionManager().getTick());
        random.nextInt();
        BigInteger rand_int = new BigInteger(100, random);
        parseBigIntegerLegacy(rand_int);
        setMapcode();
    }

    void doCancel() {
        if (multiplayer)
            new SelectGameMenu(network, gui_root, main_menu);
    }

    /**
     * Default team for slot {@code i}. MP puts each slot on its own team (Team 1, 2, 3, ...), matching the server's
     * slot-based assignment. Single-player keeps the host on team 1 and the AIs on team 2 (you vs the AIs).
     */
    private int defaultTeam(int i) {
        if (multiplayer) {
            return i;
        }
        return i == 0 ? 0 : 1;
    }

    private boolean isChosen(@NonNull PulldownMenu<Void> menu) {
        return menu.getChosenItemIndex() != 0;
    }

    public boolean startGame() {
        int hills = slider_hills.getValue();
        int vegetation_amount = slider_vegetation.getValue();
        int supplies_amount = slider_supplies.getValue();
        Landscape.TerrainType terrain_type = Landscape.TerrainType.values()[pm_terrain_type.getChosenItemIndex()];
        Game game;
        boolean rated = cb_rated.isMarked();
        if (rated)
            team_pulldown_menus[0].chooseItem(team_pulldown_menus[0].getChosenItemIndex() % 2);
        if (multiplayer) {
            String game_name = editline_name.getContents();
            if (game_name.length() < Game.MIN_LENGTH) {
                String min_name = i18n("min_name_length", Game.MIN_LENGTH);
                gui_root.addModalForm(new MessageForm(min_name));
                return false;
            }
            float random_start_pos = LocalEventQueue.getQueue().getTime() % 1f;
            game = Game.builder().name(game_name).size((byte) pulldown_size.getChosenItemIndex()).terrain(
                    (byte) terrain_type.ordinal()).hills((byte) hills).trees((byte) vegetation_amount).supplies(
                            (byte) supplies_amount).rated(rated).gamespeed(
                                    (byte) (pm_gamespeed.getChosenItemIndex() + 1)).mapcode(
                                            label_mapcode.getContents()).randomStartPos(random_start_pos).maxUnitCount(
                                                    // Advertise the host's max unit setting so joining clients
                                                    // build the same world. //added by ikill240c 2026-09-10 00:00
                                                    snapshotMaxUnits()).build();
        } else {
            boolean has_enemy = false;
            for (int i = 1; i < player_count; i++) {
                if (isChosen(difficulty_pulldown_menus[i])
                        && team_pulldown_menus[i].getChosenItemIndex() != team_pulldown_menus[0].getChosenItemIndex()) {
                    has_enemy = true;
                    break;
                }
            }
            if (!has_enemy) {
                String min_name = i18n("min_num_teams", 2);
                gui_root.addModalForm(new MessageForm(min_name));
                return false;
            }
            game = null;
        }
        if (owner != null)
            owner.terrainMenuOK();
        SelectGameMenu menu = null;
        if (multiplayer)
            menu = (SelectGameMenu) owner;
        int gametype;
        IO.println(
                "hills = " + hills / (float) SLIDER_MAX_VALUE + " | vegetation_amount = " + vegetation_amount / (float) SLIDER_MAX_VALUE + " | supplies_amount = " + supplies_amount / (float) SLIDER_MAX_VALUE + " | seed = " + seed * seed);
        String ai_string = i18n("ai");
        String[] ai_names = new String[MatchmakingServerInterface.MAX_PLAYERS];
        for (int i = 0; i < ai_names.length; i++) {
            ai_names[i] = ai_string + i;
        }
        InGameInfo ingame_info = multiplayer ? new MultiplayerInGameInfo(game.getRandomStartPos(),
                game.isRated()) : new DefaultInGameInfo(); //added by ikill240c - was conditionally SinglePlayerSpectatorInGameInfo when closing player 1's slot, but that dedicated spectator wrapping didn't work correctly; closing the slot itself (below) doesn't need any special InGameInfo variant to work
        GameNetwork game_network = Menu.startNewGame(network, gui_root,
                menu,
                WorldParameters.builder()
                        .initialGameSpeed(multiplayer ? game.getGamespeed() : Globals.gamespeed)
                        .mapcode(label_mapcode.getContents())
                        // Use pulldown values for initial unit count and max unit count. //added by ikill240 2026-09-09 21:18
                        .initialUnitCount(snapshotInitialUnits()) //added by ikill240 2026-09-09 21:18
                        .maxUnitCount(snapshotMaxUnits()) //added by ikill240 2026-09-09 21:18
                        .mapSize(pulldown_size.getChosenItemIndex())
                        .mode(selected_mode)
                        .maxChieftains(snapshotMaxChiefsTotal()) //added by ikill240 2026-09-09 20:49
                        .maxChieftainsPerQuarters(snapshotMaxChiefsPerQ()) //added by ikill240 2026-09-09 20:49
                        .maxBuildingCount(snapshotMaxBuildings()) //added by ikill240 2026-09-09 21:18
                        .chieftainHealIdleSeconds(snapshotChiefHealIdleSeconds()) //added by ikill240c 2026-09-09 23:10
                        .chieftainHealAmount(snapshotChiefHealAmount()) //added by ikill240c 2026-09-09 23:10
                        .targetNumQuarters(snapshotTargetQuarters()) //added by ikill240c 2026-09-09 23:10
                        .targetNumArmories(snapshotTargetArmories()) //added by ikill240c 2026-09-09 23:10
                        .maxConcurrentQuarters(snapshotMaxConcurrentQuarters()) //added by ikill240c 2026-09-09 23:10
                        .maxConcurrentArmories(snapshotMaxConcurrentArmories()) //added by ikill240c 2026-09-09 23:10
                        .numResourceTowers(snapshotNumResourceTowers()) //added by ikill240c 2026-09-09 23:10
                        .maxConcurrentTowers(snapshotMaxConcurrentTowers()) //added by ikill240c 2026-09-09 23:10
                        .kothStatueCount(snapshotKothStatueCount()) //added by ikill240c
                        .startingRockWarriors(snapshotStartingRockWarriors()) //added by ikill240c
                        .startingIronWarriors(snapshotStartingIronWarriors()) //added by ikill240c
                        .startingRubberWarriors(snapshotStartingRubberWarriors()) //added by ikill240c
                        .magic1Enabled(cb_magic1_enabled.isMarked()) //added by ikill240c
                        .magic2Enabled(cb_magic2_enabled.isMarked()) //added by ikill240c
                        .magic3Enabled(cb_magic3_enabled.isMarked()) //added by ikill240c
                        .chiefsCourageEnabled(cb_chiefs_courage_enabled.isMarked()) //added by ikill240c
                        .adaptiveAiEnabled(cb_adaptive_ai_enabled.isMarked()) //added by ikill240c - was mode_and_presets.isAdaptiveAiEnabled(), stale after that checkbox moved back into TerrainMenu itself
                        .teamTogether(cb_team_together.isMarked()) //added by ikill240c
                        .magic1Cost(snapshotMagic1Cost()) //added by ikill240c
                        .magic2Cost(snapshotMagic2Cost()) //added by ikill240c
                        .magic3Cost(snapshotMagic3Cost()) //added by ikill240c
                        .buildingHealthMultiplier(snapshotBuildingHealthMultiplier()) //added by ikill240c
                        .vikingChiefHealthMultiplier(snapshotVikingChiefHealthMultiplier()) //added by ikill240c
                        .nativeChiefHealthMultiplier(snapshotNativeChiefHealthMultiplier()) //added by ikill240c
                        .unitRangeMultiplier(snapshotUnitRangeMultiplier()) //added by ikill240c
                        .armoryResourceCap(snapshotArmoryResourceCap()) //added by ikill240c
                        .rockResourceCap(snapshotRockResourceCap()) //added by ikill240c
                        .ironResourceCap(snapshotIronResourceCap()) //added by ikill240c
                        .rubberResourceCap(snapshotRubberResourceCap()) //added by ikill240c
                        .build(),
                ingame_info,
                new Menu.DefaultWorldInitAction(),
                game,
                SIZES[pulldown_size.getChosenItemIndex()],
                terrain_type,
                hills / (float) SLIDER_MAX_VALUE,
                vegetation_amount / (float) SLIDER_MAX_VALUE,
                supplies_amount / (float) SLIDER_MAX_VALUE,
                seed * seed,
                ARCHIPELAGO[pulldown_size.getChosenItemIndex()] && Globals.SHIPS_ENABLED,
                selected_custom_map_path, //added by ikill240c
                ai_names,
                player_count);
        game_network.getClient().getServerInterface().setPlayerSlot(0, PlayerSlot.HUMAN,
                race_pulldown_menus[0].getChosenItemIndex(), team_pulldown_menus[0].getChosenItemIndex(), !multiplayer,
                PlayerSlot.AI_NONE);
        if (multiplayer) {
            // Carry the host's roster into the lobby; GameMenu applies it once on open (host only).
            game_network.setInitialRoster(snapshotRoster());
        }
        if (!multiplayer) {
            for (int i = 1; i < player_count; i++) {
                if (isChosen(difficulty_pulldown_menus[i]))
                    // Was difficulty_pulldown_menus[i].getChosenItemIndex() passed straight through
                    // as ai_difficulty - the raw SP pulldown index, never translated into a
                    // PlayerSlot.AI_* constant. SP's "Insane" sits at pulldown index 4, which
                    // WorldViewer.java's AI-construction switch instead reads as the unrelated
                    // PlayerSlot.AI_TOWER_TUTORIAL (also 4) - an empty no-op case, so the slot got
                    // no AdvancedAI, no starting units, nothing. See difficultyIndexToFill()'s own
                    // comment for the full trace. Routed through the same Fill-based conversion
                    // GameMenu.java's applyAi() dispatch already uses correctly, instead of ever
                    // handing a raw pulldown index to setPlayerSlot() again. //added by ikill240c
                    game_network.getClient().getServerInterface().setPlayerSlot(i, PlayerSlot.AI,
                            race_pulldown_menus[i].getChosenItemIndex(), team_pulldown_menus[i].getChosenItemIndex(),
                            true, fillToPlayerSlotAI(difficultyIndexToFill(i, //added by ikill240c
                                    difficulty_pulldown_menus[i].getChosenItemIndex()))); //added by ikill240c
            }
            game_network.getClient().getServerInterface().startServer();
            IO.println("Start server");
        }
        IO.println("Map code: " + label_mapcode.getContents());
        return true;
    }

    private final class MapcodeListener implements MouseClickListener {
        @Override
        public void mouseClicked(@NonNull MouseButton button, int x, int y, int clicks) {
            gui_root.addModalForm(new MapcodeForm(TerrainMenu.this));
        }
    }

    /**
     * Loads a hand-authored .ttmap file via a native OS file dialog (TinyFileDialogs, same
     * mechanism KeyBindingPanel.java already uses for import/export - not a Swing/AWT dialog,
     * which would visually clash with this LWJGL-rendered window). Mirrors that file's exact
     * fullscreen-toggle guard: a native dialog can't render on top of an exclusive-fullscreen
     * LWJGL surface, so fullscreen is dropped before showing the dialog and restored
     * afterward. //added by ikill240c
     */
    /**
     * Pre-selects a custom map file, exactly as if the user had clicked "Load Custom Map" and
     * chosen this file themselves - used by MainMenu's "Preview in Game" handoff from the
     * standalone map editor (see PreviewRequest), so opening this menu that way starts the user
     * right at "ready to click OK" rather than making them re-pick the file they were just
     * editing. Does NOT validate the file (unlike CustomMapListener, which loads it once just to
     * confirm it's readable before committing) - if the path is stale or the file was deleted,
     * the real load attempt happens later at actual game-start time in CustomMapGenerator, the
     * same as it would for any other invalid custom map path. //added by ikill240c
     */
    public void preselectCustomMap(@NonNull String map_file_path) { //added by ikill240c
        selected_custom_map_path = map_file_path; //added by ikill240c
        label_mapcode.setText(new File(map_file_path).getName()); //added by ikill240c
        slider_hills.setDisabled(true); //added by ikill240c
        slider_vegetation.setDisabled(true); //added by ikill240c
        slider_supplies.setDisabled(true); //added by ikill240c
    }

    private final class CustomMapListener implements MouseClickListener { //added by ikill240c
        @Override
        public void mouseClicked(@NonNull MouseButton button, int x, int y, int clicks) { //added by ikill240c
            boolean wasFullscreen = Settings.getSettings().fullscreen; //added by ikill240c
            if (wasFullscreen) { //added by ikill240c
                Renderer.getRenderer().toggleFullscreen(); //added by ikill240c
            } //added by ikill240c

            String path = TinyFileDialogs.tinyfd_openFileDialog(i18n("dialog_load_custom_map"), "", null, //added by ikill240c
                    i18n("ttmap_files"), false); //added by ikill240c
            if (path != null) { //added by ikill240c
                try { //added by ikill240c
                    // Loaded here purely to VALIDATE the file is a real, readable .ttmap before
                    // committing to it - the loaded object itself is discarded immediately after;
                    // only the path is kept and later sent to CustomMapGenerator, which reloads
                    // the file independently on whichever side actually needs the terrain data.
                    // See selected_custom_map_path's field comment for why. //added by ikill240c
                    AuthoredTerrain.load(new File(path)); //added by ikill240c
                    selected_custom_map_path = path; //added by ikill240c
                    label_mapcode.setText(new File(path).getName()); //added by ikill240c - reuses the existing mapcode display label to show which file is loaded; setText() is the real setter (verified against TextField.java after setContents() turned out not to exist - getContents() is the getter, but the setter is named differently)
                    slider_hills.setDisabled(true); //added by ikill240c - these three sliders are irrelevant once terrain comes from a painted file rather than noise
                    slider_vegetation.setDisabled(true); //added by ikill240c
                    slider_supplies.setDisabled(true); //added by ikill240c
                } catch (IOException e) { //added by ikill240c
                    gui_root.addModalForm(new MessageForm(i18n("error_load_custom_map_failed", e.getMessage()))); //added by ikill240c
                } //added by ikill240c
            } //added by ikill240c

            if (wasFullscreen) { //added by ikill240c
                Renderer.getRenderer().toggleFullscreen(); //added by ikill240c
            } //added by ikill240c
        } //added by ikill240c
    }

    private final class OKListener implements MouseClickListener {
        @Override
        public void mouseClicked(@NonNull MouseButton button, int x, int y, int clicks) {
            boolean started = startGame();
            if (started)
                button_ok.setDisabled(true);
        }
    }

    private final class DisableListener implements ItemChosenListener<Void> {
        final int i;

        public DisableListener(int i) {
            this.i = i;
        }

        @Override
        public void itemChosen(@NonNull PulldownMenu<Void> menu, int item_index) {
            // Closed slots grey out race/team. Open (MP index 0) keeps them enabled; they define what a joiner
            // inherits. Closed is index 0 in SP, index 1 in MP (Open occupies 0).
            int closed_index = multiplayer ? 1 : 0;
            boolean closed = item_index == closed_index;
            labels_players[i].setDisabled(closed);
            race_pulldown_buttons[i].setDisabled(closed);
            team_pulldown_buttons[i].setDisabled(closed);
        }
    }

    private final class PulldownUpdateMapcodeListener implements ItemChosenListener<Void> {
        @Override
        public void itemChosen(@NonNull PulldownMenu<Void> menu, int item_index) {
            setMapcode();
        }
    }

    private static final class PulldownUpdateSizeListener implements ItemChosenListener<Void> {
        @Override
        public void itemChosen(@NonNull PulldownMenu<Void> menu, int item_index) {
        }
    }

    private static final class PulldownUpdateHardListener implements ItemChosenListener<Void> {
        @Override
        public void itemChosen(@NonNull PulldownMenu<Void> menu, int item_index) {
        }
    }

    private static final class PulldownUpdateTerrainListener implements ItemChosenListener<Void> {
        @Override
        public void itemChosen(@NonNull PulldownMenu<Void> menu, int item_index) {
        }
    }

    private final class SliderUpdateMapcodeListener implements ValueListener {
        @Override
        public void valueSet(long value) {
            setMapcode();
        }
    }

    private final class PresetsHandler implements ModeAndPresetsHandler {
        @Override
        public void modeChosen(@NonNull GameMode mode) {
            TerrainMenu.this.selected_mode = mode;
            if (current_preset != null && current_preset.getMode() != mode) {
                current_preset = null;
                modified = false;
                updateBanner();
            }
            if (mode_and_presets != null) {
                mode_and_presets.refreshPresets();
            }
        }

        @Override
        public void presetChosen(@NonNull Preset preset) {
            if (modified) {
                // The card already marked itself on click. If the host declines, rebuild the grid so the highlight
                // reverts to the currently-applied preset instead of the un-applied one.
                gui_root.addModalForm(new QuestionForm(i18n("confirm_discard_changes"),
                        (_, _, _, _) -> applySelectedPreset(preset), this::revertSelection));
            } else {
                applySelectedPreset(preset);
            }
        }

        private void applySelectedPreset(@NonNull Preset preset) {
            current_preset = preset;
            apply_in_progress = true;
            applyPreset(preset);
            apply_in_progress = false;
            modified = false;
            updateBanner();
        }

        private void revertSelection() {
            if (mode_and_presets != null) {
                mode_and_presets.refreshPresets();
            }
        }

        @Override
        public void presetDeleted(@NonNull Preset preset) {
            gui_root.addModalForm(new QuestionForm(i18n("confirm_delete_preset", preset.getName()),
                    (_, _, _, _) -> confirmDeletePreset(preset)));
        }

        private void confirmDeletePreset(@NonNull Preset preset) {
            preset_library.remove(preset);
            preset_library.save(Renderer.getLocalInput().getGameDir().resolve(Globals.getPresetsFileName()));
            if (current_preset != null && current_preset.getId().equals(preset.getId())) {
                current_preset = null;
                modified = false;
                updateBanner();
            }
            if (mode_and_presets != null) {
                mode_and_presets.refreshPresets();
            }
        }

        @Override
        public void saveClicked() {
            gui_root.addModalForm(new SavePresetDialog(preset_library::hasName, this::saveNewPreset));
        }

        @Override
        public void resetClicked() {
            if (current_preset == null) {
                return;
            }
            apply_in_progress = true;
            applyPreset(current_preset);
            apply_in_progress = false;
            modified = false;
            updateBanner();
        }

        @Override
        public void updateClicked() {
            if (current_preset == null) {
                return;
            }
            Preset updated = new Preset(current_preset.getId(), current_preset.getName(), snapshotWorldConfig(),
                    snapshotModeOptions(), snapshotRoster(), false);
            preset_library.remove(current_preset);
            preset_library.add(updated);
            preset_library.save(Renderer.getLocalInput().getGameDir().resolve(Globals.getPresetsFileName()));
            current_preset = updated;
            modified = false;
            updateBanner();
            if (mode_and_presets != null) {
                mode_and_presets.refreshPresets();
            }
        }

        private void saveNewPreset(@NonNull String name) {
            Preset preset = new Preset(UUID.randomUUID().toString(), name, snapshotWorldConfig(),
                    snapshotModeOptions(), snapshotRoster(), false);
            preset_library.add(preset);
            preset_library.save(Renderer.getLocalInput().getGameDir().resolve(Globals.getPresetsFileName()));
            // Auto-select the new preset: the saved state matches the form, so it is selected and not modified.
            current_preset = preset;
            modified = false;
            updateBanner();
            if (mode_and_presets != null) {
                mode_and_presets.refreshPresets();
            }
        }
    }

    private @NonNull StandardOptions snapshotModeOptions() {
        return StandardOptions.builder().rated(cb_rated.isMarked()).build();
    }

    // Reads the selected max chieftains per quarters from the pulldown and returns the int value.
    // //added by ikill240 2026-09-09 20:49
    private int snapshotMaxChiefsPerQ() { //added by ikill240 2026-09-09 20:49
        return switch (pm_max_chiefs_per_q.getChosenItemIndex()) { //added by ikill240 2026-09-09 20:49
            case 0 -> 1; //added by ikill240 2026-09-09 20:49
            case 1 -> 3; //added by ikill240c - was `-> 2`, but index 1 is the pulldown item labeled "3"
            case 2 -> 5; //added by ikill240c - was `-> 3`, but index 2 is labeled "5"
            case 3 -> 10; //added by ikill240c - was `-> 5`, but index 3 is labeled "10"
            case 4 -> 15; //added by ikill240c - was `-> 10`, but index 4 is labeled "15"
            case 5 -> 20; //added by ikill240c - was missing (labeled "20")
            case 6 -> 9999; // "unlimited" //added by ikill240c - was missing entirely
            default -> 1; //added by ikill240 2026-09-09 20:49
        }; //added by ikill240 2026-09-09 20:49
    } //added by ikill240 2026-09-09 20:49

    // Reads the selected max total chieftains from the pulldown and returns the int value.
    // Unlimited is represented as 9999. //added by ikill240 2026-09-09 20:49
    private int snapshotMaxChiefsTotal() { //added by ikill240 2026-09-09 20:49
        return switch (pm_max_chiefs_total.getChosenItemIndex()) { //added by ikill240 2026-09-09 20:49
            case 0 -> 1; //added by ikill240 2026-09-09 20:49
            case 1 -> 3; //added by ikill240 2026-09-09 20:49
            case 2 -> 5; //added by ikill240 2026-09-09 20:49
            case 3 -> 10; //added by ikill240 2026-09-09 20:49
            case 4 -> 15; //added by ikill240c - was `-> 20`, but index 4 is the pulldown item labeled "15"
            case 5 -> 20; //added by ikill240c - was `-> 9999`, but index 5 is labeled "20", not "unlimited"
            case 6 -> 9999; // "unlimited" //added by ikill240c - was missing entirely (the real unlimited index)
            default -> 5; //added by ikill240 2026-09-09 20:49
        }; //added by ikill240 2026-09-09 20:49
    } //added by ikill240 2026-09-09 20:49

    // Reads the initial unit count from the slider (5 - 5000, in steps of UNIT_COUNT_STEP). //added by ikill240c
    private int snapshotInitialUnits() { //added by ikill240 2026-09-09 21:18
        return (int) Math.clamp(slider_initial_units.getValue() * (long) UNIT_COUNT_STEP, MIN_INITIAL_UNITS, //added by ikill240c - explicit long cast per compiler's own suggestion, avoids int-overflow-before-widening; outer (int) cast is safe since Math.clamp guarantees the result already fits within the int-sized min/max bounds
                MAX_INITIAL_UNITS); //added by ikill240c
    } //added by ikill240 2026-09-09 21:18

    // Reads the max unit count from the slider (0 - 15000, in steps of UNIT_COUNT_STEP). //added by ikill240c
    private int snapshotMaxUnits() { //added by ikill240 2026-09-09 21:18
        return (int) Math.clamp(slider_max_units.getValue() * (long) UNIT_COUNT_STEP, MIN_MAX_UNITS, MAX_MAX_UNITS); //added by ikill240c - explicit long cast per compiler's own suggestion; outer (int) cast is safe, see snapshotInitialUnits()
    } //added by ikill240 2026-09-09 21:18

    private int snapshotStartingRockWarriors() { //added by ikill240c
        return Math.clamp(slider_starting_rock_warriors.getValue(), MIN_STARTING_WARRIORS, MAX_STARTING_WARRIORS);
    }

    private int snapshotStartingIronWarriors() { //added by ikill240c
        return Math.clamp(slider_starting_iron_warriors.getValue(), MIN_STARTING_WARRIORS, MAX_STARTING_WARRIORS);
    }

    private int snapshotStartingRubberWarriors() { //added by ikill240c
        return Math.clamp(slider_starting_rubber_warriors.getValue(), MIN_STARTING_WARRIORS, MAX_STARTING_WARRIORS);
    }

    private float snapshotMagic1Cost() { //added by ikill240c
        return Math.clamp(slider_magic1_cost.getValue(), MIN_MAGIC_COST, MAX_MAGIC_COST);
    }

    private float snapshotMagic2Cost() { //added by ikill240c
        return Math.clamp(slider_magic2_cost.getValue(), MIN_MAGIC_COST, MAX_MAGIC_COST);
    }

    private float snapshotMagic3Cost() { //added by ikill240c
        return Math.clamp(slider_magic3_cost.getValue(), MIN_MAGIC_COST, MAX_MAGIC_COST);
    }

    private float snapshotBuildingHealthMultiplier() { //added by ikill240c
        return Math.clamp(slider_building_health_mult.getValue(), MIN_HEALTH_MULT_PERCENT, MAX_HEALTH_MULT_PERCENT)
                / 100f;
    }

    private float snapshotVikingChiefHealthMultiplier() { //added by ikill240c
        return Math.clamp(slider_viking_chief_health_mult.getValue(), MIN_HEALTH_MULT_PERCENT,
                MAX_HEALTH_MULT_PERCENT) / 100f;
    }

    private float snapshotNativeChiefHealthMultiplier() { //added by ikill240c
        return Math.clamp(slider_native_chief_health_mult.getValue(), MIN_HEALTH_MULT_PERCENT,
                MAX_HEALTH_MULT_PERCENT) / 100f;
    }

    private float snapshotUnitRangeMultiplier() { //added by ikill240c
        return Math.clamp(slider_unit_range_mult.getValue(), MIN_HEALTH_MULT_PERCENT, MAX_HEALTH_MULT_PERCENT)
                / 100f;
    }

    private int snapshotArmoryResourceCap() { //added by ikill240c
        return Math.clamp(slider_armory_resource_cap.getValue(), MIN_RESOURCE_CAP, MAX_RESOURCE_CAP);
    }

    private int snapshotRockResourceCap() { //added by ikill240c
        return Math.clamp(slider_rock_resource_cap.getValue(), MIN_RESOURCE_CAP, MAX_RESOURCE_CAP);
    }

    private int snapshotIronResourceCap() { //added by ikill240c
        return Math.clamp(slider_iron_resource_cap.getValue(), MIN_RESOURCE_CAP, MAX_RESOURCE_CAP);
    }

    private int snapshotRubberResourceCap() { //added by ikill240c
        return Math.clamp(slider_rubber_resource_cap.getValue(), MIN_RESOURCE_CAP, MAX_RESOURCE_CAP);
    }

    // Reads the selected max building count from the pulldown. Unlimited is 9999. //added by ikill240 2026-09-09 21:18
    private int snapshotMaxBuildings() { //added by ikill240 2026-09-09 21:18
        return switch (pm_max_buildings.getChosenItemIndex()) { //added by ikill240 2026-09-09 21:18
            case 0 -> 20; //added by ikill240 2026-09-09 21:18
            case 1 -> 50; //added by ikill240 2026-09-09 21:18
            case 2 -> 100; //added by ikill240 2026-09-09 21:18
            case 3 -> 500; //added by ikill240 2026-09-09 21:18
            case 4 -> 1200; //added by ikill240 2026-09-09 21:18
            case 5 -> 9999; // Unlimited //added by ikill240 2026-09-09 21:18
            default -> 1200; //added by ikill240 2026-09-09 21:18
        }; //added by ikill240 2026-09-09 21:18
    } //added by ikill240 2026-09-09 21:18

    // Reads the selected chieftain heal idle seconds. //added by ikill240c 2026-09-09 23:10
    private float snapshotChiefHealIdleSeconds() { //added by ikill240c 2026-09-09 23:10
        return switch (pm_chief_heal_idle.getChosenItemIndex()) { //added by ikill240c 2026-09-09 23:10
            case 0 -> 1f; //added by ikill240c 2026-09-09 23:10
            case 1 -> 3f; //added by ikill240c 2026-09-09 23:10
            case 2 -> 5f; //added by ikill240c 2026-09-09 23:10
            case 3 -> 10f; //added by ikill240c 2026-09-09 23:10
            case 4 -> 20f; //added by ikill240c 2026-09-09 23:10
            default -> 3f; //added by ikill240c 2026-09-09 23:10
        }; //added by ikill240c 2026-09-09 23:10
    } //added by ikill240c 2026-09-09 23:10

    // Reads the selected chieftain heal amount. //added by ikill240c 2026-09-09 23:10
    private int snapshotChiefHealAmount() { //added by ikill240c 2026-09-09 23:10
        return switch (pm_chief_heal_amount.getChosenItemIndex()) { //added by ikill240c 2026-09-09 23:10
            case 0 -> 10; //added by ikill240c - was `-> 25`, but index 0 is the pulldown item labeled "10"
            case 1 -> 25; //added by ikill240c - was `-> 50`, but index 1 is labeled "25"
            case 2 -> 50; //added by ikill240c - was `-> 100`, but index 2 is labeled "50"
            case 3 -> 100; //added by ikill240c - was `-> 200`, but index 3 is labeled "100"
            case 4 -> 150; //added by ikill240c - was missing (labeled "150")
            case 5 -> 250; //added by ikill240c - was missing (labeled "250")
            default -> 50; //added by ikill240c 2026-09-09 23:10
        }; //added by ikill240c 2026-09-09 23:10
    } //added by ikill240c 2026-09-09 23:10

    // Reads the selected target number of Quarters. //added by ikill240c 2026-09-09 23:10
    private int snapshotTargetQuarters() { //added by ikill240c 2026-09-09 23:10
        return switch (pm_target_quarters.getChosenItemIndex()) { //added by ikill240c 2026-09-09 23:10
            case 0 -> 1; //added by ikill240c 2026-09-09 23:10
            case 1 -> 2; //added by ikill240c 2026-09-09 23:10
            case 2 -> 3; //added by ikill240c 2026-09-09 23:10
            case 3 -> 5; //added by ikill240c 2026-09-09 23:10
            case 4 -> 7; //added by ikill240c - was missing: pulldown has items for "7"/"10"/"12" (indices 4-6) that this switch never handled, silently falling to the default (3) instead
            case 5 -> 10; //added by ikill240c
            case 6 -> 12; //added by ikill240c
            default -> 3; //added by ikill240c 2026-09-09 23:10
        }; //added by ikill240c 2026-09-09 23:10
    } //added by ikill240c 2026-09-09 23:10

    // Reads the selected target number of Armories. //added by ikill240c 2026-09-09 23:10
    private int snapshotTargetArmories() { //added by ikill240c 2026-09-09 23:10
        return switch (pm_target_armories.getChosenItemIndex()) { //added by ikill240c 2026-09-09 23:10
            case 0 -> 1; //added by ikill240c 2026-09-09 23:10
            case 1 -> 2; //added by ikill240c 2026-09-09 23:10
            case 2 -> 3; //added by ikill240c 2026-09-09 23:10
            case 3 -> 4; //added by ikill240c - was missing: pulldown has items for "4"/"5"/"6" (indices 3-5) that this switch never handled, silently falling to the default (1) instead
            case 4 -> 5; //added by ikill240c
            case 5 -> 6; //added by ikill240c
            default -> 1; //added by ikill240c 2026-09-09 23:10
        }; //added by ikill240c 2026-09-09 23:10
    } //added by ikill240c 2026-09-09 23:10

    // Reads the selected max concurrent Quarters. //added by ikill240c 2026-09-09 23:10
    // Straightforward index-to-value mapping (pulldown item i is the string "MIN+i"), unlike
    // snapshotMaxConcurrentQuarters() below which maps a small curated set of pulldown items -
    // this pulldown's items were generated as a contiguous MIN..MAX range (see its own
    // construction), so the index IS the offset from MIN. //added by ikill240c
    private int snapshotKothStatueCount() { //added by ikill240c
        return WorldParameters.MIN_KOTH_STATUE_COUNT + pm_koth_statue_count.getChosenItemIndex(); //added by ikill240c
    }

    private int snapshotMaxConcurrentQuarters() { //added by ikill240c 2026-09-09 23:10
        return switch (pm_max_concur_quarters.getChosenItemIndex()) { //added by ikill240c 2026-09-09 23:10
            case 0 -> 1; //added by ikill240c 2026-09-09 23:10
            case 1 -> 2; //added by ikill240c 2026-09-09 23:10
            case 2 -> 3; //added by ikill240c 2026-09-09 23:10
            case 3 -> 4; //added by ikill240c - was `-> 5`, but index 3 is the pulldown item labeled "4", not "5"
            case 4 -> 5; //added by ikill240c - was missing entirely (pulldown item labeled "5"), silently falling to the default (2) instead
            default -> 2; //added by ikill240c 2026-09-09 23:10
        }; //added by ikill240c 2026-09-09 23:10
    } //added by ikill240c 2026-09-09 23:10

    // Reads the selected max concurrent Armories. //added by ikill240c 2026-09-09 23:10
    private int snapshotMaxConcurrentArmories() { //added by ikill240c 2026-09-09 23:10
        return switch (pm_max_concur_armories.getChosenItemIndex()) { //added by ikill240c 2026-09-09 23:10
            case 0 -> 1; //added by ikill240c 2026-09-09 23:10
            case 1 -> 2; //added by ikill240c 2026-09-09 23:10
            case 2 -> 3; //added by ikill240c 2026-09-09 23:10
            default -> 1; //added by ikill240c 2026-09-09 23:10
        }; //added by ikill240c 2026-09-09 23:10
    } //added by ikill240c 2026-09-09 23:10

    // Reads the selected number of resource towers. //added by ikill240c 2026-09-09 23:10
    private int snapshotNumResourceTowers() { //added by ikill240c 2026-09-09 23:10
        return switch (pm_num_res_towers.getChosenItemIndex()) { //added by ikill240c 2026-09-09 23:10
            case 0 -> 0; //added by ikill240c 2026-09-09 23:10
            case 1 -> 1; //added by ikill240c - was `-> 3`, but index 1 is the pulldown item labeled "1"
            case 2 -> 3; //added by ikill240c - was `-> 6`, but index 2 is labeled "3"
            case 3 -> 5; //added by ikill240c - was `-> 10`, but index 3 is labeled "5"
            case 4 -> 7; //added by ikill240c - was `-> 20`, but index 4 is labeled "7"
            case 5 -> 10; //added by ikill240c - was missing (labeled "10")
            case 6 -> 15; //added by ikill240c - was missing (labeled "15")
            case 7 -> 20; //added by ikill240c - was missing (labeled "20")
            case 8 -> 30; //added by ikill240c - was missing (labeled "30")
            default -> 6; //added by ikill240c 2026-09-09 23:10
        }; //added by ikill240c 2026-09-09 23:10
    } //added by ikill240c 2026-09-09 23:10

    // Reads the selected max concurrent towers. //added by ikill240c 2026-09-09 23:10
    private int snapshotMaxConcurrentTowers() { //added by ikill240c 2026-09-09 23:10
        return switch (pm_max_concur_towers.getChosenItemIndex()) { //added by ikill240c 2026-09-09 23:10
            case 0 -> 1; //added by ikill240c 2026-09-09 23:10
            case 1 -> 3; //added by ikill240c 2026-09-09 23:10
            case 2 -> 5; //added by ikill240c - was `-> 6`, but index 2 is the pulldown item labeled "5"
            case 3 -> 7; //added by ikill240c - was `-> 10`, but index 3 is the pulldown item labeled "7"
            case 4 -> 10; //added by ikill240c - was missing (pulldown item labeled "10")
            case 5 -> 15; //added by ikill240c - was missing (pulldown item labeled "15")
            case 6 -> 9999; // "unlimited" //added by ikill240c - was missing entirely
            default -> 6; //added by ikill240c 2026-09-09 23:10
        }; //added by ikill240c 2026-09-09 23:10
    } //added by ikill240c 2026-09-09 23:10

    private @NonNull WorldConfig snapshotWorldConfig() {
        return WorldConfig.builder().gamespeed(pm_gamespeed.getChosenItemIndex()).islandSize(
                pulldown_size.getChosenItemIndex()).terrainType(pm_terrain_type.getChosenItemIndex()).hills(
                        slider_hills.getValue()).vegetation(slider_vegetation.getValue()).supplies(
                                slider_supplies.getValue())
                // Was missing every custom AI/population setting below entirely - presets could only ever
                // capture the original 6 terrain fields above, so saving/applying a preset silently reset
                // every one of these back to default. Reuses the exact same snapshotXxx() helpers already
                // used when actually starting a game, so presets and game-start always agree.
                // //added by ikill240c
                .initialUnitCount(snapshotInitialUnits()) //added by ikill240c
                .maxUnitCount(snapshotMaxUnits()) //added by ikill240c
                .maxChieftains(snapshotMaxChiefsTotal()) //added by ikill240c
                .maxChieftainsPerQuarters(snapshotMaxChiefsPerQ()) //added by ikill240c
                .maxBuildingCount(snapshotMaxBuildings()) //added by ikill240c
                .chieftainHealIdleSeconds(snapshotChiefHealIdleSeconds()) //added by ikill240c
                .chieftainHealAmount(snapshotChiefHealAmount()) //added by ikill240c
                .targetNumQuarters(snapshotTargetQuarters()) //added by ikill240c
                .targetNumArmories(snapshotTargetArmories()) //added by ikill240c
                .maxConcurrentQuarters(snapshotMaxConcurrentQuarters()) //added by ikill240c
                .maxConcurrentArmories(snapshotMaxConcurrentArmories()) //added by ikill240c
                .numResourceTowers(snapshotNumResourceTowers()) //added by ikill240c
                .maxConcurrentTowers(snapshotMaxConcurrentTowers()) //added by ikill240c
                .kothStatueCount(snapshotKothStatueCount()) //added by ikill240c
                .startingRockWarriors(snapshotStartingRockWarriors()) //added by ikill240c
                .startingIronWarriors(snapshotStartingIronWarriors()) //added by ikill240c
                .startingRubberWarriors(snapshotStartingRubberWarriors()) //added by ikill240c
                .magic1Enabled(cb_magic1_enabled.isMarked()) //added by ikill240c
                .magic2Enabled(cb_magic2_enabled.isMarked()) //added by ikill240c
                .magic3Enabled(cb_magic3_enabled.isMarked()) //added by ikill240c
                .chiefsCourageEnabled(cb_chiefs_courage_enabled.isMarked()) //added by ikill240c
                .adaptiveAiEnabled(cb_adaptive_ai_enabled.isMarked()) //added by ikill240c - was mode_and_presets.isAdaptiveAiEnabled(), stale after that checkbox moved back into TerrainMenu itself
                .teamTogether(cb_team_together.isMarked()) //added by ikill240c
                .magic1Cost(snapshotMagic1Cost()) //added by ikill240c
                .magic2Cost(snapshotMagic2Cost()) //added by ikill240c
                .magic3Cost(snapshotMagic3Cost()) //added by ikill240c
                .buildingHealthMultiplier(snapshotBuildingHealthMultiplier()) //added by ikill240c
                .vikingChiefHealthMultiplier(snapshotVikingChiefHealthMultiplier()) //added by ikill240c
                .nativeChiefHealthMultiplier(snapshotNativeChiefHealthMultiplier()) //added by ikill240c
                .unitRangeMultiplier(snapshotUnitRangeMultiplier()) //added by ikill240c
                .armoryResourceCap(snapshotArmoryResourceCap()) //added by ikill240c
                .rockResourceCap(snapshotRockResourceCap()) //added by ikill240c
                .ironResourceCap(snapshotIronResourceCap()) //added by ikill240c
                .rubberResourceCap(snapshotRubberResourceCap()) //added by ikill240c
                .build();
    }

    private void applyWorldConfig(@NonNull WorldConfig world) {
        // Stored values are pulldown indices. Within a build the item counts are fixed, but a preset file from a build
        // with different terrain/size lists could hold a stale index; clamp so it applies the nearest valid option
        // instead of silently leaving the prior value.
        pm_gamespeed.chooseItem(clampIndex(world.getGamespeed(), pm_gamespeed.getSize()));
        pulldown_size.chooseItem(clampIndex(world.getIslandSize(), pulldown_size.getSize()));
        pm_terrain_type.chooseItem(clampIndex(world.getTerrainType(), pm_terrain_type.getSize()));
        slider_hills.setValue(world.getHills());
        slider_vegetation.setValue(world.getVegetation());
        slider_supplies.setValue(world.getSupplies());
        // Was missing every custom AI/population setting restore below entirely - see the matching
        // snapshotWorldConfig() note above. Sliders restore the raw value directly; pulldowns need a
        // reverse lookup (value -> index) since that's how WorldConfig stores them (matching the actual
        // WorldParameters value, not a UI-specific index that could shift between builds).
        // //added by ikill240c
        slider_initial_units.setValue(Math.round(
                Math.clamp(world.getInitialUnitCount(), MIN_INITIAL_UNITS, MAX_INITIAL_UNITS) / (float) UNIT_COUNT_STEP)); //added by ikill240c
        slider_max_units.setValue(Math.round(
                Math.clamp(world.getMaxUnitCount(), MIN_MAX_UNITS, MAX_MAX_UNITS) / (float) UNIT_COUNT_STEP)); //added by ikill240c
        pm_max_chiefs_total.chooseItem(indexForMaxChiefsTotal(world.getMaxChieftains())); //added by ikill240c
        pm_max_chiefs_per_q.chooseItem(indexForMaxChiefsPerQ(world.getMaxChieftainsPerQuarters())); //added by ikill240c
        pm_max_buildings.chooseItem(indexForMaxBuildings(world.getMaxBuildingCount())); //added by ikill240c
        pm_chief_heal_idle.chooseItem(indexForChiefHealIdle(world.getChieftainHealIdleSeconds())); //added by ikill240c
        pm_chief_heal_amount.chooseItem(indexForChiefHealAmount(world.getChieftainHealAmount())); //added by ikill240c
        pm_target_quarters.chooseItem(indexForTargetQuarters(world.getTargetNumQuarters())); //added by ikill240c
        pm_target_armories.chooseItem(indexForTargetArmories(world.getTargetNumArmories())); //added by ikill240c
        pm_max_concur_quarters.chooseItem(indexForMaxConcurQuarters(world.getMaxConcurrentQuarters())); //added by ikill240c
        pm_max_concur_armories.chooseItem(indexForMaxConcurArmories(world.getMaxConcurrentArmories())); //added by ikill240c
        pm_num_res_towers.chooseItem(indexForNumResTowers(world.getNumResourceTowers())); //added by ikill240c
        pm_max_concur_towers.chooseItem(indexForMaxConcurTowers(world.getMaxConcurrentTowers())); //added by ikill240c
        pm_koth_statue_count.chooseItem(Math.clamp(world.getKothStatueCount(), //added by ikill240c
                WorldParameters.MIN_KOTH_STATUE_COUNT, WorldParameters.MAX_KOTH_STATUE_COUNT) //added by ikill240c
                - WorldParameters.MIN_KOTH_STATUE_COUNT); // linear range, no lookup function needed unlike the curated pulldowns above //added by ikill240c
        slider_starting_rock_warriors.setValue(
                Math.clamp(world.getStartingRockWarriors(), MIN_STARTING_WARRIORS, MAX_STARTING_WARRIORS)); //added by ikill240c
        slider_starting_iron_warriors.setValue(
                Math.clamp(world.getStartingIronWarriors(), MIN_STARTING_WARRIORS, MAX_STARTING_WARRIORS)); //added by ikill240c
        slider_starting_rubber_warriors.setValue(
                Math.clamp(world.getStartingRubberWarriors(), MIN_STARTING_WARRIORS, MAX_STARTING_WARRIORS)); //added by ikill240c
        cb_magic1_enabled.setMarked(world.isMagic1Enabled()); //added by ikill240c
        cb_magic2_enabled.setMarked(world.isMagic2Enabled()); //added by ikill240c
        cb_magic3_enabled.setMarked(world.isMagic3Enabled()); //added by ikill240c
        cb_chiefs_courage_enabled.setMarked(world.isChiefsCourageEnabled()); //added by ikill240c
        cb_adaptive_ai_enabled.setMarked(world.isAdaptiveAiEnabled()); //added by ikill240c - was mode_and_presets.setAdaptiveAiEnabled(...), stale after that checkbox moved back into TerrainMenu itself
        cb_team_together.setMarked(world.isTeamTogether()); //added by ikill240c
        slider_magic1_cost.setValue(Math.round(Math.clamp(world.getMagic1Cost(), MIN_MAGIC_COST, MAX_MAGIC_COST))); //added by ikill240c
        slider_magic2_cost.setValue(Math.round(Math.clamp(world.getMagic2Cost(), MIN_MAGIC_COST, MAX_MAGIC_COST))); //added by ikill240c
        slider_magic3_cost.setValue(Math.round(Math.clamp(world.getMagic3Cost(), MIN_MAGIC_COST, MAX_MAGIC_COST))); //added by ikill240c
        slider_building_health_mult.setValue(Math.round(Math.clamp(world.getBuildingHealthMultiplier() * 100f,
                MIN_HEALTH_MULT_PERCENT, MAX_HEALTH_MULT_PERCENT))); //added by ikill240c
        slider_viking_chief_health_mult.setValue(Math.round(Math.clamp(world.getVikingChiefHealthMultiplier() * 100f,
                MIN_HEALTH_MULT_PERCENT, MAX_HEALTH_MULT_PERCENT))); //added by ikill240c
        slider_native_chief_health_mult.setValue(Math.round(Math.clamp(world.getNativeChiefHealthMultiplier() * 100f,
                MIN_HEALTH_MULT_PERCENT, MAX_HEALTH_MULT_PERCENT))); //added by ikill240c
        slider_unit_range_mult.setValue(Math.round(Math.clamp(world.getUnitRangeMultiplier() * 100f,
                MIN_HEALTH_MULT_PERCENT, MAX_HEALTH_MULT_PERCENT))); //added by ikill240c
        slider_armory_resource_cap.setValue(
                Math.clamp(world.getArmoryResourceCap(), MIN_RESOURCE_CAP, MAX_RESOURCE_CAP)); //added by ikill240c
        slider_rock_resource_cap.setValue(
                Math.clamp(world.getRockResourceCap(), MIN_RESOURCE_CAP, MAX_RESOURCE_CAP)); //added by ikill240c
        slider_iron_resource_cap.setValue(
                Math.clamp(world.getIronResourceCap(), MIN_RESOURCE_CAP, MAX_RESOURCE_CAP)); //added by ikill240c
        slider_rubber_resource_cap.setValue(
                Math.clamp(world.getRubberResourceCap(), MIN_RESOURCE_CAP, MAX_RESOURCE_CAP)); //added by ikill240c
        setMapcode();
    }

    // Reverse lookups for applyWorldConfig() above - each mirrors its matching snapshotXxx() switch
    // exactly, just inverted (value -> index instead of index -> value). Falls back to whichever index
    // that snapshot method's own `default ->` case would have produced when the stored value doesn't
    // exactly match any option (e.g. an older preset file, or one edited by hand). //added by ikill240c
    private int indexForMaxChiefsTotal(int v) { //added by ikill240c
        return switch (v) { //added by ikill240c - rewritten to match snapshotMaxChiefsTotal()'s corrected forward mapping
            case 1 -> 0;
            case 3 -> 1;
            case 5 -> 2;
            case 10 -> 3;
            case 15 -> 4; //added by ikill240c
            case 20 -> 5; //added by ikill240c - was `-> 4` (paired with the old, wrong forward value)
            case 9999 -> 6; //added by ikill240c - was `-> 5`
            default -> 2;
        };
    }

    private int indexForMaxChiefsPerQ(int v) { //added by ikill240c
        return switch (v) { //added by ikill240c - rewritten to match snapshotMaxChiefsPerQ()'s corrected forward mapping
            case 1 -> 0;
            case 3 -> 1; //added by ikill240c - was `case 2 -> 1`
            case 5 -> 2; //added by ikill240c - was `case 3 -> 2`
            case 10 -> 3; //added by ikill240c - was `case 5 -> 3`
            case 15 -> 4; //added by ikill240c - was `case 10 -> 4`
            case 20 -> 5; //added by ikill240c
            case 9999 -> 6; //added by ikill240c
            default -> 0;
        };
    }

    private int indexForMaxBuildings(int v) { //added by ikill240c
        return switch (v) {
            case 20 -> 0;
            case 50 -> 1;
            case 100 -> 2;
            case 500 -> 3;
            case 1200 -> 4;
            case 9999 -> 5;
            default -> 4;
        };
    }

    private int indexForChiefHealIdle(float v) { //added by ikill240c
        if (v == 1f) return 0;
        if (v == 5f) return 2;
        if (v == 10f) return 3;
        if (v == 20f) return 4;
        return 1; // 3f and any unrecognized value //added by ikill240c
    }

    private int indexForChiefHealAmount(int v) { //added by ikill240c
        return switch (v) { //added by ikill240c - rewritten to match snapshotChiefHealAmount()'s corrected forward mapping
            case 10 -> 0; //added by ikill240c
            case 25 -> 1; //added by ikill240c - was `-> 0`
            case 50 -> 2; //added by ikill240c
            case 100 -> 3; //added by ikill240c - was `-> 2`
            case 150 -> 4; //added by ikill240c
            case 250 -> 5; //added by ikill240c - was `-> 3`
            default -> 2; // 50 and any unrecognized value //added by ikill240c
        };
    }

    private int indexForTargetQuarters(int v) { //added by ikill240c
        return switch (v) { //added by ikill240c - rewritten to match snapshotTargetQuarters()'s corrected forward mapping
            case 1 -> 0;
            case 2 -> 1;
            case 3 -> 2; //added by ikill240c
            case 5 -> 3; //added by ikill240c
            case 7 -> 4; //added by ikill240c
            case 10 -> 5; //added by ikill240c
            case 12 -> 6; //added by ikill240c
            default -> 2; // 3 and any unrecognized value //added by ikill240c
        };
    }

    private int indexForTargetArmories(int v) { //added by ikill240c
        return switch (v) { //added by ikill240c - rewritten to match snapshotTargetArmories()'s corrected forward mapping
            case 1 -> 0; //added by ikill240c
            case 2 -> 1;
            case 3 -> 2;
            case 4 -> 3; //added by ikill240c
            case 5 -> 4; //added by ikill240c
            case 6 -> 5; //added by ikill240c
            default -> 0;
        };
    }

    private int indexForMaxConcurQuarters(int v) { //added by ikill240c
        return switch (v) { //added by ikill240c - rewritten to match snapshotMaxConcurrentQuarters()'s corrected forward mapping
            case 1 -> 0;
            case 2 -> 1; //added by ikill240c
            case 3 -> 2; //added by ikill240c - was `-> 2` already, kept
            case 4 -> 3; //added by ikill240c - was missing (this pulldown item is "4")
            case 5 -> 4; //added by ikill240c - was `-> 3`
            default -> 1; // 2 and any unrecognized value //added by ikill240c
        };
    }

    private int indexForMaxConcurArmories(int v) { //added by ikill240c
        return switch (v) {
            case 2 -> 1;
            case 3 -> 2;
            default -> 0; // 1 and any unrecognized value //added by ikill240c
        };
    }

    private int indexForNumResTowers(int v) { //added by ikill240c
        return switch (v) { //added by ikill240c - rewritten to match snapshotNumResourceTowers()'s corrected forward mapping
            case 0 -> 0;
            case 1 -> 1; //added by ikill240c
            case 3 -> 2; //added by ikill240c - was `-> 1`
            case 5 -> 3; //added by ikill240c
            case 7 -> 4; //added by ikill240c
            case 10 -> 5; //added by ikill240c - was `-> 3`
            case 15 -> 6; //added by ikill240c
            case 20 -> 7; //added by ikill240c - was `-> 4`
            case 30 -> 8; //added by ikill240c
            default -> 2; // 3 and any unrecognized value //added by ikill240c
        };
    }

    private int indexForMaxConcurTowers(int v) { //added by ikill240c
        return switch (v) { //added by ikill240c - rewritten to match snapshotMaxConcurrentTowers()'s corrected forward mapping
            case 1 -> 0;
            case 3 -> 1;
            case 5 -> 2; //added by ikill240c
            case 7 -> 3; //added by ikill240c
            case 10 -> 4; //added by ikill240c - was `-> 3`
            case 15 -> 5; //added by ikill240c
            case 9999 -> 6; // "unlimited" //added by ikill240c
            default -> 2; // 5 and any unrecognized value //added by ikill240c
        };
    }

    private static int clampIndex(int index, int size) {
        if (index < 0) {
            return 0;
        }
        return Math.min(index, size - 1);
    }

    private @NonNull RosterTemplate snapshotRoster() {
        RosterTemplate.Slot[] slots = new RosterTemplate.Slot[player_count];
        for (int i = 0; i < player_count; i++) {
            int race = race_pulldown_menus[i].getChosenItemIndex();
            int team = team_pulldown_menus[i].getChosenItemIndex();
            slots[i] = new RosterTemplate.Slot(difficultyIndexToFill(i,
                    difficulty_pulldown_menus[i].getChosenItemIndex()),
                    race, team);
        }
        return new RosterTemplate(slots);
    }

    // MP slot menu order: Open 0, Closed 1, Easy 2, Normal 3, Hard 4. Only used on the MP preset path.
    // MP slot menu order: Open 0, Closed 1, Easy 2, Normal 3, Hard 4, Insane 5.
    // SP slot menu order (no Open item - see construction loop above): Closed 0, Easy 1, Normal 2, Hard 3, Insane 4.
    // Was `private static` and unconditionally used the MP indices above regardless of which
    // pulldown variant was actually showing - harmless for slot_index==0 (always HOST) and for
    // Easy/Normal/Hard, whose SP indices happen to coincide with PlayerSlot.AI_EASY/AI_NORMAL/
    // AI_HARD's own numeric values by coincidence, but genuinely wrong for Insane: SP's pulldown
    // index 4 for "Insane" hit this method's `case 4 -> HARD_AI` (the MP mapping), silently saving
    // an SP preset's Insane selection as Hard instead. Worse, TerrainMenu.startGame()'s SP path
    // passes difficulty_pulldown_menus[i].getChosenItemIndex() - the raw SP index, unconverted -
    // straight into setPlayerSlot() as the ai_difficulty argument, which WorldViewer.java's switch
    // interprets directly as a PlayerSlot.AI_* constant. SP index 4 for "Insane" collided with the
    // completely unrelated PlayerSlot.AI_TOWER_TUTORIAL (also value 4), whose case in that switch
    // is an empty no-op block - no AdvancedAI ever constructed, no starting units, nothing -
    // matching reports of an Insane AI slot that either never spawns in or, since that slot's
    // Player object still exists with a team but no army or economy behind it, fails the standard
    // isPlayerAlive() check within the first few ticks and ends the match almost immediately. Now
    // instance-scoped so it can read this.multiplayer and return the correct index for whichever
    // pulldown variant is actually in use - same fix fillToDifficultyIndex() below already needed
    // and got. //added by ikill240c
    private RosterTemplate.@NonNull Fill difficultyIndexToFill(int slot_index, int difficulty_index) {
        if (slot_index == 0) {
            return RosterTemplate.Fill.HOST;
        }
        if (multiplayer) { //added by ikill240c
            return switch (difficulty_index) { //added by ikill240c
                case 1 -> RosterTemplate.Fill.CLOSED; //added by ikill240c
                case 2 -> RosterTemplate.Fill.EASY_AI; //added by ikill240c
                case 3 -> RosterTemplate.Fill.NORMAL_AI; //added by ikill240c
                case 4 -> RosterTemplate.Fill.HARD_AI; //added by ikill240c
                case 5 -> RosterTemplate.Fill.INSANE_AI; //added by ikill240c
                default -> RosterTemplate.Fill.OPEN; //added by ikill240c
            }; //added by ikill240c
        } //added by ikill240c
        return switch (difficulty_index) { //added by ikill240c - SP has no Open item, so its indices are shifted down by one relative to MP's
            case 1 -> RosterTemplate.Fill.EASY_AI; //added by ikill240c
            case 2 -> RosterTemplate.Fill.NORMAL_AI; //added by ikill240c
            case 3 -> RosterTemplate.Fill.HARD_AI; //added by ikill240c
            case 4 -> RosterTemplate.Fill.INSANE_AI; //added by ikill240c
            default -> RosterTemplate.Fill.CLOSED; // SP index 0 - see the construction loop, SP has no Open item at all //added by ikill240c
        }; //added by ikill240c
    }

    // Maps a roster Fill value to the PlayerSlot.AI_* constant WorldViewer.java's AI-construction
    // switch actually expects - mirrors GameMenu.java's own applyAi() dispatch (case HARD_AI ->
    // PlayerSlot.AI_HARD, etc.), the correct pattern that TerrainMenu's own SP game-start path
    // wasn't using (see difficultyIndexToFill()'s comment above for why that mattered). Only
    // meaningful for the AI_* cases - HOST/OPEN/CLOSED never reach this, since callers only invoke
    // it once isChosen() has already confirmed the slot resolves to an actual AI difficulty.
    // //added by ikill240c
    private static int fillToPlayerSlotAI(RosterTemplate.@NonNull Fill fill) { //added by ikill240c
        return switch (fill) { //added by ikill240c
            case EASY_AI -> PlayerSlot.AI_EASY; //added by ikill240c
            case NORMAL_AI -> PlayerSlot.AI_NORMAL; //added by ikill240c
            case HARD_AI -> PlayerSlot.AI_HARD; //added by ikill240c
            case INSANE_AI -> PlayerSlot.AI_INSANE; //added by ikill240c
            case HOST, OPEN, CLOSED -> PlayerSlot.AI_NONE; // shouldn't be reached - see method comment //added by ikill240c
        }; //added by ikill240c
    } //added by ikill240c

    // Was private - made accessible so DefaultInGameInfo.close() can restore the just-ended game's
    // roster onto a freshly-constructed replay menu (see its own comment for why this exists at
    // all - "Replay Island" previously started a completely fresh, default lobby with only the
    // map restored via parseMapcode(), never the previous game's player count, races, teams, or
    // AI difficulties). //added by ikill240c
    public void applyPreset(@NonNull Preset preset) { //added by ikill240c - was private
        applyWorldConfig(preset.getWorld());

        RosterTemplate.Slot[] slots = preset.getRoster().getSlots();
        int target_count = Math.min(slots.length, MatchmakingServerInterface.MAX_PLAYERS);
        if (target_count >= DEFAULT_PLAYER_COUNT && target_count != player_count) {
            pulldown_menu_slots.chooseItem(target_count - DEFAULT_PLAYER_COUNT);
        }

        if (preset.getModeOptions() instanceof StandardOptions opts) {
            cb_rated.setMarked(opts.isRated());
        }

        for (int i = 0; i < Math.min(slots.length, player_count); i++) {
            RosterTemplate.Slot slot = slots[i];
            difficulty_pulldown_menus[i].chooseItem(fillToDifficultyIndex(slot.getFill(), i));
            if (slot.getRace() != null && slot.getRace() < race_pulldown_menus[i].getSize()) {
                race_pulldown_menus[i].chooseItem(slot.getRace());
            }
            if (slot.getTeam() != null && slot.getTeam() < team_pulldown_menus[i].getSize()) {
                team_pulldown_menus[i].chooseItem(slot.getTeam());
            }
        }
    }

    // MP slot menu order: Open 0, Closed 1, Easy 2, Normal 3, Hard 4.
    // SP slot menu order (no Open item - see construction loop above): Closed 0, Easy 1, Normal 2, Hard 3.
    // Was `private static` and always returned the MP indices - harmless while presets were MP-only,
    // but crashed (IndexOutOfBoundsException, "length 4") the moment a preset resolving to HARD_AI was
    // applied against the SP pulldown, which only has 4 items and expects hard at index 3, not 4. Now
    // instance-scoped so it can read this.multiplayer and return the correct index for whichever
    // pulldown variant is actually in use. //added by ikill240c
    private int fillToDifficultyIndex(RosterTemplate.@NonNull Fill fill, int slot_index) {
        if (slot_index == 0) {
            return 0;
        }
        if (multiplayer) {
            return switch (fill) {
                case HOST, OPEN -> 0;
                case CLOSED -> 1;
                case EASY_AI -> 2;
                case NORMAL_AI -> 3;
                case HARD_AI -> 4;
                case INSANE_AI -> 5; //added by ikill240c
            };
        }
        return switch (fill) {
            case HOST, OPEN, CLOSED -> 0; // SP has no Open item; treat as Closed //added by ikill240c
            case EASY_AI -> 1;
            case NORMAL_AI -> 2;
            case HARD_AI -> 3;
            case INSANE_AI -> 4; //added by ikill240c
        };
    }

    private final class PulldownUpdatePlayersChangedListener implements ItemChosenListener<Void> {
        private final Panel standard;
        private ScrollableGroup current_race_team;

        PulldownUpdatePlayersChangedListener(Panel standard) {
            this.standard = standard;
        }

        void setCurrentGroup(ScrollableGroup group) {
            this.current_race_team = group;
        }

        @Override
        public void itemChosen(@NonNull PulldownMenu<Void> menu, int item_index) {
            int previous_count = player_count;
            player_count = item_index + DEFAULT_PLAYER_COUNT;

            // Rebuilding the grid throws away the old menus, so snapshot the host's current choices first and
            // carry them over to the matching slots. Only genuinely new slots fall back to defaults; changing
            // the count no longer wipes already-configured slots.
            int[] prev_difficulty = new int[previous_count];
            int[] prev_race = new int[previous_count];
            int[] prev_team = new int[previous_count];
            for (int i = 0; i < previous_count; i++) {
                prev_difficulty[i] = difficulty_pulldown_menus[i].getChosenItemIndex();
                prev_race[i] = race_pulldown_menus[i].getChosenItemIndex();
                prev_team[i] = team_pulldown_menus[i].getChosenItemIndex();
            }

            ScrollableGroup new_group = buildPlayerSlots(player_count);
            if (multiplayer) {
                roster_panel.setRoster(new_group);
            } else {
                if (current_race_team != null) {
                    standard.removeChild(current_race_team);
                }
                // group_set_all_difficulty (and the rest of the bulk-apply cluster above it) are
                // static - constructed once and never torn down - while the roster itself gets
                // rebuilt as a brand new ScrollableGroup every time the player count changes. So
                // it's the roster that needs re-anchoring here, relative to the stable
                // group_set_all_difficulty (now the last static control, sitting below the
                // side-by-side checkbox row), rather than the other way around. //added by ikill240c
                new_group.place(group_set_all_difficulty, BOTTOM_LEFT, //added by ikill240c
                        Skin.getSkin().getFormData().sectionSpacing()); //added by ikill240c
                standard.addChild(new_group);
            }
            current_race_team = new_group;

            for (int i = 0; i < player_count; i++) {
                if (i < previous_count) {
                    difficulty_pulldown_menus[i].chooseItem(prev_difficulty[i]);
                    race_pulldown_menus[i].chooseItem(prev_race[i]);
                    // Team options run 1..player_count, so clamp the carried-over choice when the count shrinks.
                    team_pulldown_menus[i].chooseItem(Math.min(prev_team[i], player_count - 1));
                } else {
                    team_pulldown_menus[i].chooseItem(defaultTeam(i));
                    if (!multiplayer) {
                        // Default every new skirmish slot to an AI so the extra player slots are created instead of staying closed.
                        // ikill240v 2026-09-09 17:30
                        difficulty_pulldown_menus[i].chooseItem(PlayerSlot.AI_EASY);
                        // Give each newly created skirmish AI a non-host race so it behaves like a valid AI player after the menu rebuild.
                        // ikill240v 2026-09-09 17:30
                        race_pulldown_menus[i].chooseItem((race_pulldown_menus[0].getChosenItemIndex() + 1) % 2);
                    } else if (i != 0) {
                        // MP non-host slots default to Open (index 0); SP non-host slots default to Closed (index 0).
                        difficulty_pulldown_menus[i].chooseItem(0);
                    }
                }
            }
            markModified();
        }
    }
}
