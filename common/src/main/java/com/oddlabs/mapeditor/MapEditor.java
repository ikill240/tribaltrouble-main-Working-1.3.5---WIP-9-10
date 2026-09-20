package com.oddlabs.mapeditor;

import com.oddlabs.matchmaking.MatchmakingServerInterface; //added by ikill240c
import com.oddlabs.procedural.AuthoredTerrain;
import com.oddlabs.procedural.Channel;
import org.jspecify.annotations.NonNull; //added by ikill240c
import org.jspecify.annotations.Nullable;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane; //added by ikill240c
import javax.swing.JSlider;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point; //added by ikill240c
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.MouseWheelEvent; //added by ikill240c
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Random; //added by ikill240c

/**
 * Standalone terrain-painting editor for Tribal Trouble custom maps. Deliberately built as a
 * plain Swing application rather than reverse-engineering the game's own LWJGL-rendered GUI
 * framework - it depends only on Channel and AuthoredTerrain (both in the shared `common`
 * module), with zero dependency on the game's rendering, GUI, or gameplay code. Saves/loads the
 * exact .ttmap binary format AuthoredTerrain defines, so a map painted here drops directly into
 * Landscape's authored-terrain path (loadAuthoredHeight()/loadAuthoredSupplies()) with no format
 * translation needed.
 *
 * <p>NOTE on "make the map editor 3D matching the game's engine": deliberately NOT attempted here.
 * This editor's whole architecture is a plain 2D Swing/AWT heightmap painter with zero dependency
 * on the game's LWJGL rendering pipeline, by design (see the class comment above, predating this
 * change) - specifically so it never has to keep pace with the game's own renderer, shaders, or
 * GUI framework. A genuine 3D editor sharing the game's actual engine means a new LWJGL-based
 * application (real camera, real terrain mesh generation, real GL context) - a project on the
 * scale of the game's own renderer, not an incremental patch to this file. Flagging this honestly
 * rather than delivering a half-measure (e.g. a fake "3D-looking" 2D shading trick) that wouldn't
 * actually be the game's engine. Everything else on the requested list below IS implemented within
 * the existing 2D architecture.
 *
 * <p>Known limitations, stated plainly rather than left implicit:
 * <ul>
 *   <li>Undo/redo covers HEIGHT painting only, not resource/start placement or deletion. Those
 *       are individually reversible (right-click delete, or just re-place), but not part of the
 *       undo stack.
 *   <li>The playability preview's access threshold (0.05) is copied from Landscape.java's own
 *       constant for the smallest map size tier (256m). Landscape actually varies this value per
 *       map size (0.05 / 0.0375 / 0.025 / 0.0325 for 256/512/1024/2048m respectively), so the
 *       preview is a good directional check but not pixel-perfect for every map size.
 *   <li>The sea level constant (0.1f) is copied from the game's Globals.SEA_LEVEL rather than
 *       imported, since importing it would require depending on the game's own module, which
 *       this editor deliberately does not.
 * </ul>
 */
public final class MapEditor extends JFrame {
    // Copied from com.oddlabs.tt.global.Globals - see the class-level note above for why this
    // isn't imported directly.
    private static final float SEA_LEVEL = 0.1f;
    // Copied from com.oddlabs.tt.procedural.Landscape's 256m-tier constant - see class note.
    private static final float DEFAULT_ACCESS_THRESHOLD = 0.05f;

    private static final int DEFAULT_MAP_SIZE = 256;
    private static final int MAX_UNDO_STATES = 30;
    // References the game's own cap directly (both live in the common module) rather than a
    // separate hardcoded constant, so this can never silently drift out of sync with the actual
    // game-side player limit the way it previously did (this was hardcoded to 8 while the game
    // itself supported up to 12, then 32, then 80). //added by ikill240c
    private static final int MAX_PLAYERS = MatchmakingServerInterface.MAX_PLAYERS; //added by ikill240c
    private static final double MARKER_PICK_RADIUS = 15.0;

    // Zoom range and step for the canvas - see setupZoom()/MouseWheelListener below. //added by ikill240c
    private static final double MIN_ZOOM = 0.25; //added by ikill240c
    private static final double MAX_ZOOM = 4.0; //added by ikill240c
    private static final double ZOOM_STEP = 1.15; //added by ikill240c
    // Visual margin (screen pixels, unaffected by zoom) drawn around the map image itself, so the
    // map's actual edge is always clearly visible against the surrounding panel background rather
    // than running flush to the scrollpane's edge. //added by ikill240c
    private static final int MAP_BORDER_MARGIN = 24; //added by ikill240c

    private enum Tool {
        RAISE, LOWER, SMOOTH, FLATTEN,
        PLATEAU, // added by ikill240c - hard-edged flat area at a sampled height, no falloff blend
        CLIFF, // added by ikill240c - steep-edged raise/lower, minimal falloff band
        TREE, PALM_TREE, ROCK, IRON, START, DELETE
    }

    // Shape of the brush's area of effect for the height-painting tools (RAISE/LOWER/SMOOTH/
    // FLATTEN/PLATEAU/CLIFF). Purely a containment-test + falloff-normalization change in
    // applyBrush() below - the tools themselves are unaffected by which shape is active.
    // //added by ikill240c
    private enum BrushShape { //added by ikill240c
        CIRCLE, SQUARE, DIAMOND //added by ikill240c
    } //added by ikill240c

    private Channel height;
    private AuthoredTerrain terrain;
    private @Nullable File current_file;

    private Tool current_tool = Tool.RAISE;
    private BrushShape brush_shape = BrushShape.CIRCLE; //added by ikill240c
    private int brush_radius = 8;
    private float brush_strength = 0.05f;
    private float flatten_target_height = 0f;
    private boolean flatten_sampled = false;
    private boolean preview_mode = false;
    private int next_player_index = 0;
    // How many resource markers a single placement (click, or one step of a drag-paint stroke)
    // scatters within cluster_radius of the target point - 1 means "exactly at the clicked point,
    // no scatter", matching the original one-marker-per-click behavior exactly when left at its
    // default. Not applied to START (player spawns are placed one at a time, at exact positions -
    // scattering a player's starting point randomly would be actively harmful, not a convenience).
    // //added by ikill240c
    private int cluster_count = 1; //added by ikill240c
    private int cluster_radius = 6; //added by ikill240c

    // Current zoom factor (1.0 = fit-to-panel-width, matching the original behavior exactly).
    // //added by ikill240c
    private double zoom = 1.0; //added by ikill240c

    private final Deque<float[][]> undo_stack = new ArrayDeque<>();
    private final Deque<float[][]> redo_stack = new ArrayDeque<>();
    private boolean stroke_in_progress = false;

    private Canvas canvas;
    private JLabel status_label;
    private JComboBox<Integer> start_player_combo;
    private JComboBox<String> start_team_combo; //added by ikill240c
    private JComboBox<BrushShape> brush_shape_combo; //added by ikill240c

    public MapEditor() {
        super("Tribal Trouble Map Editor");
        newMapInternal(DEFAULT_MAP_SIZE);
        buildUI();
        // DISPOSE_ON_CLOSE, not EXIT_ON_CLOSE: this class is also launched directly from inside
        // the running game (see TerrainMenu's "Create Custom Map" button) by opening it as a
        // second window in the SAME JVM, not a separate process. EXIT_ON_CLOSE calls
        // System.exit(0), which would silently terminate the entire game the first time a player
        // closed the editor mid-session. When run standalone (gradlew common:runMapEditor),
        // disposing the only open window still lets the JVM exit naturally shortly after, since
        // nothing else keeps it alive. //added by ikill240c
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE); //added by ikill240c
        setSize(920, 760);
        setLocationRelativeTo(null);
    }

    private void newMapInternal(int size) {
        height = new Channel(size, size).fill(SEA_LEVEL * 1.5f);
        terrain = new AuthoredTerrain(height);
        current_file = null;
        next_player_index = 0;
        undo_stack.clear();
        redo_stack.clear();
        stroke_in_progress = false;
        preview_mode = false;
    }

    private void buildUI() {
        setLayout(new BorderLayout());

        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);

        addToolButton(toolbar, "Raise", Tool.RAISE);
        addToolButton(toolbar, "Lower", Tool.LOWER);
        addToolButton(toolbar, "Smooth", Tool.SMOOTH);
        addToolButton(toolbar, "Flatten", Tool.FLATTEN);
        addToolButton(toolbar, "Plateau", Tool.PLATEAU); //added by ikill240c
        addToolButton(toolbar, "Cliff", Tool.CLIFF); //added by ikill240c
        toolbar.addSeparator();
        addToolButton(toolbar, "Tree", Tool.TREE);
        addToolButton(toolbar, "Palm Tree", Tool.PALM_TREE);
        addToolButton(toolbar, "Rock", Tool.ROCK);
        addToolButton(toolbar, "Iron", Tool.IRON);
        toolbar.addSeparator();
        addToolButton(toolbar, "Player Start", Tool.START);
        start_player_combo = new JComboBox<>();
        for (int i = 0; i < MAX_PLAYERS; i++) {
            start_player_combo.addItem(i);
        }
        start_player_combo.setMaximumSize(new Dimension(60, 28));
        toolbar.add(new JLabel(" Player: "));
        toolbar.add(start_player_combo);
        // "No Team" (-1, this codebase's existing convention - see GameOverTrigger's ai_team == -1
        // check) plus one entry per possible player, matching the game's own "every player can be
        // on their own distinct team" FFA capability - was a fixed 8 regardless of player cap,
        // which silently stopped matching once MAX_PLAYERS grew past a handful of teams' worth.
        // //added by ikill240c
        start_team_combo = new JComboBox<>(); //added by ikill240c
        start_team_combo.addItem("No Team"); //added by ikill240c
        for (int t = 1; t <= MAX_PLAYERS; t++) { //added by ikill240c - was a hardcoded 8, now tracks MAX_PLAYERS
            start_team_combo.addItem("Team " + t); //added by ikill240c
        } //added by ikill240c
        start_team_combo.setMaximumSize(new Dimension(90, 28)); //added by ikill240c
        toolbar.add(new JLabel(" Team: ")); //added by ikill240c
        toolbar.add(start_team_combo); //added by ikill240c
        toolbar.addSeparator();
        addToolButton(toolbar, "Delete (or right-click)", Tool.DELETE);
        toolbar.addSeparator();

        // Cluster count/radius apply to resource placement tools (TREE/PALM_TREE/ROCK/IRON) - see
        // handlePlacementClick()'s resource branches and isPlacementTool()'s comment for why START
        // is excluded. Left at count=1 by default, which is exactly the original
        // one-marker-per-click behavior. //added by ikill240c
        toolbar.add(new JLabel(" Cluster size: ")); //added by ikill240c
        JSlider cluster_count_slider = new JSlider(1, 20, cluster_count); //added by ikill240c
        cluster_count_slider.setMaximumSize(new Dimension(100, 28)); //added by ikill240c
        cluster_count_slider.addChangeListener(e -> { //added by ikill240c
            cluster_count = cluster_count_slider.getValue(); //added by ikill240c
            updateStatus(); //added by ikill240c
        });
        toolbar.add(cluster_count_slider); //added by ikill240c
        toolbar.add(new JLabel(" Cluster radius: ")); //added by ikill240c
        JSlider cluster_radius_slider = new JSlider(1, 30, cluster_radius); //added by ikill240c
        cluster_radius_slider.setMaximumSize(new Dimension(100, 28)); //added by ikill240c
        cluster_radius_slider.addChangeListener(e -> { //added by ikill240c
            cluster_radius = cluster_radius_slider.getValue(); //added by ikill240c
            updateStatus(); //added by ikill240c
        });
        toolbar.add(cluster_radius_slider); //added by ikill240c
        toolbar.addSeparator(); //added by ikill240c

        toolbar.add(new JLabel(" Brush shape: ")); //added by ikill240c
        brush_shape_combo = new JComboBox<>(BrushShape.values()); //added by ikill240c
        brush_shape_combo.setMaximumSize(new Dimension(90, 28)); //added by ikill240c
        brush_shape_combo.addActionListener(e -> { //added by ikill240c
            brush_shape = (BrushShape) brush_shape_combo.getSelectedItem(); //added by ikill240c
            updateStatus(); //added by ikill240c
        });
        toolbar.add(brush_shape_combo); //added by ikill240c
        toolbar.addSeparator(); //added by ikill240c

        toolbar.add(new JLabel(" Brush size: "));
        JSlider radius_slider = new JSlider(1, 40, brush_radius);
        radius_slider.setMaximumSize(new Dimension(120, 28));
        radius_slider.addChangeListener(e -> {
            brush_radius = radius_slider.getValue();
            updateStatus();
        });
        toolbar.add(radius_slider);

        toolbar.add(new JLabel(" Strength: "));
        JSlider strength_slider = new JSlider(1, 20, Math.round(brush_strength * 100));
        strength_slider.setMaximumSize(new Dimension(100, 28));
        strength_slider.addChangeListener(e -> {
            brush_strength = strength_slider.getValue() / 100f;
            updateStatus();
        });
        toolbar.add(strength_slider);
        toolbar.addSeparator();

        // Buttons for undo/redo, which previously only had keyboard shortcuts (Ctrl+Z/Ctrl+Y -
        // see the registerKeyboardAction calls below) with no visible toolbar affordance.
        // //added by ikill240c
        JButton undo_button = new JButton("Undo"); //added by ikill240c
        undo_button.addActionListener(e -> undo()); //added by ikill240c
        toolbar.add(undo_button); //added by ikill240c
        JButton redo_button = new JButton("Redo"); //added by ikill240c
        redo_button.addActionListener(e -> redo()); //added by ikill240c
        toolbar.add(redo_button); //added by ikill240c
        toolbar.addSeparator(); //added by ikill240c

        // Zoom controls - mirrors the mouse-wheel zoom below (see Canvas's MouseWheelListener),
        // for anyone who prefers buttons/doesn't have a wheel. //added by ikill240c
        JButton zoom_out_button = new JButton("Zoom -"); //added by ikill240c
        zoom_out_button.addActionListener(e -> setZoom(zoom / ZOOM_STEP)); //added by ikill240c
        toolbar.add(zoom_out_button); //added by ikill240c
        JButton zoom_in_button = new JButton("Zoom +"); //added by ikill240c
        zoom_in_button.addActionListener(e -> setZoom(zoom * ZOOM_STEP)); //added by ikill240c
        toolbar.add(zoom_in_button); //added by ikill240c
        JButton zoom_reset_button = new JButton("Zoom Reset"); //added by ikill240c
        zoom_reset_button.addActionListener(e -> setZoom(1.0)); //added by ikill240c
        toolbar.add(zoom_reset_button); //added by ikill240c
        toolbar.addSeparator(); //added by ikill240c

        JButton preview_button = new JButton("Toggle Playability Preview");
        preview_button.addActionListener(e -> {
            preview_mode = !preview_mode;
            // rebuildImage(), not repaint() - repaint() only re-blits the EXISTING cached image,
            // which was rendered using the OLD preview_mode value. rebuildImage() is what actually
            // branches on preview_mode to decide whether to draw the plain grayscale heightmap or
            // the water/unreachable-highlighted preview - without calling it, this button silently
            // flipped a flag that nothing ever re-read. //added by ikill240c
            canvas.rebuildImage(); //added by ikill240c
        });
        toolbar.add(preview_button);
        toolbar.addSeparator();

        JButton new_button = new JButton("New Map");
        new_button.addActionListener(e -> promptNewMap());
        toolbar.add(new_button);
        JButton random_button = new JButton("Random Map"); //added by ikill240c
        random_button.addActionListener(e -> promptRandomMap()); //added by ikill240c
        toolbar.add(random_button); //added by ikill240c
        JButton save_button = new JButton("Save");
        save_button.addActionListener(e -> saveMap());
        toolbar.add(save_button);
        JButton load_button = new JButton("Load");
        load_button.addActionListener(e -> loadMap());
        toolbar.add(load_button);
        toolbar.addSeparator(); //added by ikill240c
        JButton auto_spawn_button = new JButton("Auto-Place Spawns"); //added by ikill240c
        auto_spawn_button.addActionListener(e -> promptAutoPlaceSpawns()); //added by ikill240c
        toolbar.add(auto_spawn_button); //added by ikill240c
        JButton preview_in_game_button = new JButton("Preview in Game"); //added by ikill240c
        preview_in_game_button.addActionListener(e -> previewInGame()); //added by ikill240c
        toolbar.add(preview_in_game_button); //added by ikill240c

        add(toolbar, BorderLayout.NORTH);

        canvas = new Canvas();
        canvas.setPreferredSize(new Dimension(700, 700));
        // Wrapped in a JScrollPane so zooming in (canvas grows larger than the visible area) can
        // be navigated by scrolling, rather than needing hand-rolled pan/drag viewport math -
        // Swing's own scrollpane already does this correctly. //added by ikill240c
        JScrollPane scroll_pane = new JScrollPane(canvas); //added by ikill240c
        scroll_pane.getVerticalScrollBar().setUnitIncrement(24); //added by ikill240c
        scroll_pane.getHorizontalScrollBar().setUnitIncrement(24); //added by ikill240c
        add(scroll_pane, BorderLayout.CENTER); //added by ikill240c

        status_label = new JLabel(" ");
        add(status_label, BorderLayout.SOUTH);
        updateStatus();

        setupKeyBindings();
    }

    private void addToolButton(JToolBar toolbar, String label, Tool tool) {
        JButton button = new JButton(label);
        button.addActionListener(e -> {
            current_tool = tool;
            flatten_sampled = false;
            updateStatus();
        });
        toolbar.add(button);
    }

    private void setupKeyBindings() {
        getRootPane().registerKeyboardAction(e -> undo(),
                KeyStroke.getKeyStroke("control Z"),
                javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW);
        getRootPane().registerKeyboardAction(e -> redo(),
                KeyStroke.getKeyStroke("control Y"),
                javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    // Sets the zoom level (clamped to [MIN_ZOOM, MAX_ZOOM]), resizes the canvas accordingly, and
    // asks the scrollpane to re-lay-out around the new size. Called from both the mouse wheel
    // (Canvas's listener below) and the toolbar zoom buttons, so both stay consistent.
    // //added by ikill240c
    private void setZoom(double new_zoom) { //added by ikill240c
        zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, new_zoom)); //added by ikill240c
        canvas.updatePreferredSizeForZoom(); //added by ikill240c
        updateStatus(); //added by ikill240c
    } //added by ikill240c

    private void updateStatus() {
        status_label.setText(String.format(
                " Tool: %s | Shape: %s | Brush radius: %d | Strength: %.2f | Zoom: %.0f%% | Map size: %dx%d | Resources: %d | Starts: %d | %s",
                current_tool, brush_shape, brush_radius, brush_strength, zoom * 100, //added by ikill240c
                height.getWidth(), height.getHeight(),
                terrain.getResources().size(), terrain.getStarts().size(),
                preview_mode ? "PREVIEW MODE" : "Edit mode"));
    }

    // ****************
    // * UNDO / REDO  *
    // ****************

    private float[][] deepCopyPixels() {
        float[][] source = height.getPixels();
        float[][] copy = new float[source.length][];
        for (int y = 0; y < source.length; y++) {
            copy[y] = source[y].clone();
        }
        return copy;
    }

    private void pushUndoSnapshot() {
        undo_stack.push(deepCopyPixels());
        while (undo_stack.size() > MAX_UNDO_STATES) {
            undo_stack.removeLast();
        }
        redo_stack.clear();
    }

    private void restorePixels(float[][] pixels) {
        float[][] target = height.getPixels();
        for (int y = 0; y < pixels.length; y++) {
            System.arraycopy(pixels[y], 0, target[y], 0, pixels[y].length);
        }
    }

    private void undo() {
        if (undo_stack.isEmpty())
            return;
        redo_stack.push(deepCopyPixels());
        restorePixels(undo_stack.pop());
        // rebuildImage(), not repaint() - restorePixels() changes the underlying height data, but
        // repaint() alone only re-blits the cached image rendered from the PREVIOUS height data.
        // Same class of bug as the preview-toggle button and marker placement above: the
        // component looked unchanged after undo/redo until something else (e.g. reloading the
        // file) happened to call rebuildImage() fresh. //added by ikill240c
        canvas.rebuildImage(); //added by ikill240c
    }

    private void redo() {
        if (redo_stack.isEmpty())
            return;
        undo_stack.push(deepCopyPixels());
        restorePixels(redo_stack.pop());
        canvas.rebuildImage(); // see undo()'s comment above //added by ikill240c
    }

    // ****************
    // * NEW / SAVE / LOAD *
    // ****************

    // Same preset tiers TerrainMenu.java's own "Island size" dropdown uses in the main game
    // (256/512/1024/2048/4096), so a map made here matches a size the game's own lobby actually
    // offers. //added by ikill240c
    private static final Integer[] MAP_SIZE_PRESETS = {256, 512, 1024, 2048, 4096}; //added by ikill240c

    // Was a free-text JOptionPane.showInputDialog() ("Map size (grid units, e.g. 256):") for both
    // callers below - a typo or an out-of-range number just bounced back an error dialog with no
    // guidance on what a valid size actually looked like. This overload of showInputDialog renders
    // as a dropdown of the exact preset values above instead of a text field, so there's no way to
    // type an invalid size in the first place. Returns null if the user cancelled. //added by ikill240c
    private @Nullable Integer promptForMapSize(@NonNull String dialog_title) { //added by ikill240c
        Integer current = height.getWidth(); //added by ikill240c
        Integer initial = java.util.Arrays.asList(MAP_SIZE_PRESETS).contains(current) ? current //added by ikill240c
                : MAP_SIZE_PRESETS[0]; //added by ikill240c - current map size isn't one of the presets (e.g. a loaded/imported map with a custom size) - default the dropdown's initial selection to the smallest preset rather than silently inserting a non-preset value into the list
        Object choice = JOptionPane.showInputDialog(this, "Map size:", dialog_title, //added by ikill240c
                JOptionPane.QUESTION_MESSAGE, null, MAP_SIZE_PRESETS, initial); //added by ikill240c
        return (Integer) choice; // null if the user cancelled //added by ikill240c
    }

    private void promptNewMap() {
        Integer size = promptForMapSize("New Map"); //added by ikill240c
        if (size == null) //added by ikill240c
            return; //added by ikill240c
        int confirm = JOptionPane.showConfirmDialog(this,
                "This discards the current map (any unsaved changes will be lost). Continue?", "New Map",
                JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION)
            return;
        newMapInternal(size);
        canvas.rebuildImage();
        canvas.updatePreferredSizeForZoom(); //added by ikill240c - map size changed, canvas dimensions must follow
        updateStatus();
    }

    // Starts a map the same size/confirmation flow as "New Map", but fills it with randomly
    // generated hills instead of a flat sea. Not true Perlin/simplex noise (Channel exposes no
    // such generator to this module - see the class-level note on why this editor avoids reaching
    // into the game's own procedural generation code) - instead layers many random raise/lower
    // "bumps" of random position, radius and strength on top of each other, then smooths the
    // result once, which produces a reasonably natural-looking bumpy terrain to start editing
    // from rather than a blank sea. //added by ikill240c
    private void promptRandomMap() { //added by ikill240c
        Integer size = promptForMapSize("Random Map"); //added by ikill240c
        if (size == null) //added by ikill240c
            return; //added by ikill240c
        int confirm = JOptionPane.showConfirmDialog(this, //added by ikill240c
                "This discards the current map (any unsaved changes will be lost) and generates a random one. Continue?", //added by ikill240c
                "Random Map", JOptionPane.YES_NO_OPTION); //added by ikill240c
        if (confirm != JOptionPane.YES_OPTION) //added by ikill240c
            return; //added by ikill240c

        newMapInternal(size); //added by ikill240c
        Random random = new Random(); //added by ikill240c - standalone offline tool, no lockstep determinism to preserve (see placeResourceCluster's own comment on this)
        int bump_count = Math.max(20, size / 4); //added by ikill240c - scales roughly with map area so bigger maps don't look sparse
        for (int i = 0; i < bump_count; i++) { //added by ikill240c
            int bx = random.nextInt(size); //added by ikill240c
            int by = random.nextInt(size); //added by ikill240c
            int radius = 10 + random.nextInt(Math.max(1, size / 8)); //added by ikill240c
            float strength = (random.nextFloat() * 2f - 1f) * 0.15f; // -0.15..0.15, raise or lower //added by ikill240c
            stampBump(bx, by, radius, strength); //added by ikill240c
        } //added by ikill240c
        // One smoothing pass over the whole map softens the raw bump-stacking into more natural
        // rolling terrain rather than a field of distinct circular mounds. //added by ikill240c
        height = height.copy().smooth(2); //added by ikill240c
        terrain = new AuthoredTerrain(height); //added by ikill240c - fresh terrain wrapping the new height channel (no resources/starts yet, matching newMapInternal's own fresh-map behavior)

        canvas.rebuildImage(); //added by ikill240c
        canvas.updatePreferredSizeForZoom(); //added by ikill240c
        updateStatus(); //added by ikill240c
    }

    // Shared circular height stamp used by promptRandomMap() above - deliberately simpler than
    // applyBrush() (plain smoothstep falloff, no tool/shape branching) since random generation
    // doesn't need brush-shape awareness. //added by ikill240c
    private void stampBump(int cx, int cy, int radius, float strength) { //added by ikill240c
        int r2 = radius * radius; //added by ikill240c
        for (int dy = -radius; dy <= radius; dy++) { //added by ikill240c
            int py = cy + dy; //added by ikill240c
            if (py < 0 || py >= height.getHeight()) //added by ikill240c
                continue; //added by ikill240c
            for (int dx = -radius; dx <= radius; dx++) { //added by ikill240c
                int px = cx + dx; //added by ikill240c
                if (px < 0 || px >= height.getWidth()) //added by ikill240c
                    continue; //added by ikill240c
                int dist2 = dx * dx + dy * dy; //added by ikill240c
                if (dist2 > r2) //added by ikill240c
                    continue; //added by ikill240c
                float t = 1f - (float) Math.sqrt(dist2) / radius; //added by ikill240c
                float falloff = t * t * (3f - 2f * t); //added by ikill240c
                float current = height.getPixel(px, py); //added by ikill240c
                height.putPixel(px, py, Math.max(0f, current + strength * falloff)); //added by ikill240c
            } //added by ikill240c
        } //added by ikill240c
    } //added by ikill240c

    // Clears any existing start positions and places `count` new ones evenly spaced around a
    // circle centered on the map - the same angular layout Landscape.generateUnitLocations() uses
    // for its own procedural (non-authored) start placement, so a map auto-spawned here starts
    // from a genuinely game-realistic default rather than an arbitrary invented pattern. All
    // placed with team -1 ("No Team"/free-for-all) by default, matching this tool's own combo
    // convention - the map author can still reassign teams afterward with the normal Player
    // Start tool. //added by ikill240c
    private void promptAutoPlaceSpawns() { //added by ikill240c
        String input = JOptionPane.showInputDialog(this, "Number of players (1-" + MAX_PLAYERS + "):", //added by ikill240c
                "4"); //added by ikill240c
        if (input == null) //added by ikill240c
            return; //added by ikill240c
        int count; //added by ikill240c
        try { //added by ikill240c
            count = Integer.parseInt(input.trim()); //added by ikill240c
        } catch (NumberFormatException e) { //added by ikill240c
            JOptionPane.showMessageDialog(this, "Not a valid number.", "Auto-Place Spawns", //added by ikill240c
                    JOptionPane.ERROR_MESSAGE); //added by ikill240c
            return; //added by ikill240c
        } //added by ikill240c
        if (count < 1 || count > MAX_PLAYERS) { //added by ikill240c
            JOptionPane.showMessageDialog(this, "Player count must be between 1 and " + MAX_PLAYERS + ".", //added by ikill240c
                    "Auto-Place Spawns", JOptionPane.ERROR_MESSAGE); //added by ikill240c
            return; //added by ikill240c
        } //added by ikill240c
        if (!terrain.getStarts().isEmpty()) { //added by ikill240c
            int confirm = JOptionPane.showConfirmDialog(this, //added by ikill240c
                    "This replaces the " + terrain.getStarts().size() + " existing start position(s). Continue?", //added by ikill240c
                    "Auto-Place Spawns", JOptionPane.YES_NO_OPTION); //added by ikill240c
            if (confirm != JOptionPane.YES_OPTION) //added by ikill240c
                return; //added by ikill240c
        } //added by ikill240c

        terrain.getStarts().clear(); //added by ikill240c
        int size = height.getWidth(); //added by ikill240c
        int center = size / 2; //added by ikill240c
        float radius = 0.35f * size; // matches Landscape.generateUnitLocations()'s own 0.35 * unit_grids_per_world //added by ikill240c
        float angle = 0.5f * (float) Math.PI; // matches Landscape's own starting angle //added by ikill240c
        float angle_step = 2f * (float) Math.PI / count; //added by ikill240c
        for (int i = 0; i < count; i++) { //added by ikill240c
            int x = (int) (radius * (float) Math.cos(angle) + center + 0.5f); //added by ikill240c
            int y = (int) (radius * (float) Math.sin(angle) + center + 0.5f); //added by ikill240c
            x = Math.max(0, Math.min(size - 1, x)); //added by ikill240c
            y = Math.max(0, Math.min(size - 1, y)); //added by ikill240c
            terrain.addStart(i, x, y, -1); //added by ikill240c
            angle += angle_step; //added by ikill240c
        } //added by ikill240c
        next_player_index = count; //added by ikill240c

        canvas.rebuildImage(); //added by ikill240c
        updateStatus(); //added by ikill240c
    }

    private void saveMap() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Tribal Trouble Maps (*.ttmap)", "ttmap"));
        if (current_file != null)
            chooser.setSelectedFile(current_file);
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION)
            return;
        File file = chooser.getSelectedFile();
        if (!file.getName().toLowerCase().endsWith(".ttmap")) {
            file = new File(file.getParentFile(), file.getName() + ".ttmap");
        }
        try {
            terrain.save(file);
            current_file = file;
            JOptionPane.showMessageDialog(this, "Saved: " + file.getName());
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Failed to save: " + e.getMessage(), "Save Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Saves the current map (prompting for a location if it's never been saved) and hands the
     * file path to PreviewRequest, which the game's own main thread polls once per frame (see
     * MainMenu's registered Animated) to actually start a real skirmish session on this map using
     * the exact same CustomMapGenerator -> Landscape -> World -> Renderer pipeline as any other
     * game - not a separate rendering path, so this is genuinely the game's real renderer, not an
     * approximation of it.
     *
     * <p>Only does anything useful if this editor was launched from inside a running game (the
     * "Create Custom Map" button in MainMenu) - if launched standalone (gradlew
     * common:runMapEditor), there's no game process polling PreviewRequest at all, so the request
     * would simply sit unconsumed. Told to the user directly rather than silently doing nothing.
     * //added by ikill240c
     */
    private void previewInGame() { //added by ikill240c
        if (current_file == null) { //added by ikill240c
            saveMap(); //added by ikill240c
            if (current_file == null) //added by ikill240c - user cancelled the save dialog
                return; //added by ikill240c
        } else { //added by ikill240c
            // Re-save silently to the existing file first, so the preview reflects the current
            // in-progress edits, not just whatever was true at the last explicit Save.
            // //added by ikill240c
            try { //added by ikill240c
                terrain.save(current_file); //added by ikill240c
            } catch (IOException e) { //added by ikill240c
                JOptionPane.showMessageDialog(this, "Failed to save before preview: " + e.getMessage(), //added by ikill240c
                        "Preview Error", JOptionPane.ERROR_MESSAGE); //added by ikill240c
                return; //added by ikill240c
            } //added by ikill240c
        } //added by ikill240c
        PreviewRequest.request(current_file.getPath()); //added by ikill240c
        JOptionPane.showMessageDialog(this, //added by ikill240c
                "Preview requested. If this editor was opened from inside the game (Main Menu ->" //added by ikill240c
                        + " Create Custom Map), a skirmish will start on this map shortly. If you" //added by ikill240c
                        + " launched this editor standalone, nothing will happen - there's no game" //added by ikill240c
                        + " running to preview in.", //added by ikill240c
                "Preview in Game", JOptionPane.INFORMATION_MESSAGE); //added by ikill240c
    }

    private void loadMap() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Tribal Trouble Maps (*.ttmap)", "ttmap"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION)
            return;
        File file = chooser.getSelectedFile();
        try {
            AuthoredTerrain loaded = AuthoredTerrain.load(file);
            // Push an undo snapshot of the CURRENT map first, so loading doesn't irreversibly
            // destroy unsaved work - the user can still Ctrl+Z back to what they had before,
            // though note the resource/start lists themselves are not covered by undo (see the
            // class-level limitations note).
            pushUndoSnapshot();
            terrain = loaded;
            height = terrain.getHeightChannel();
            current_file = file;
            next_player_index = terrain.getStarts().size();
            canvas.rebuildImage();
            canvas.updatePreferredSizeForZoom(); //added by ikill240c - loaded map may be a different size than the previous one
            updateStatus();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Failed to load: " + e.getMessage(), "Load Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    // ****************
    // * BRUSH LOGIC  *
    // ****************

    // True if (dx, dy) falls within the current brush_shape's area of effect at the given radius.
    // Circle: Euclidean distance. Square: Chebyshev (max of the two axis distances) - a plain
    // bounding-box test. Diamond: Manhattan (taxicab) distance - the same rhombus shape
    // FormationLayout's DIAMOND formation uses in the game itself, for visual consistency between
    // the two "diamond" concepts even though they're otherwise unrelated. //added by ikill240c
    private boolean inBrushShape(int dx, int dy, int r) { //added by ikill240c
        return switch (brush_shape) { //added by ikill240c
            case CIRCLE -> dx * dx + dy * dy <= r * r; //added by ikill240c
            case SQUARE -> Math.abs(dx) <= r && Math.abs(dy) <= r; //added by ikill240c
            case DIAMOND -> Math.abs(dx) + Math.abs(dy) <= r; //added by ikill240c
        }; //added by ikill240c
    } //added by ikill240c

    // "Shape distance" normalized to [0, 1] at the given radius, used for the falloff curve below
    // - the same three metrics as inBrushShape() above, just returning a continuous 0..1 value
    // instead of a boolean, so SQUARE/DIAMOND get a falloff that fades toward their own edge
    // shape rather than a circular one that wouldn't match their (non-circular) footprint.
    // //added by ikill240c
    private float shapeDistanceRatio(int dx, int dy, int r) { //added by ikill240c
        return switch (brush_shape) { //added by ikill240c
            case CIRCLE -> (float) Math.sqrt(dx * dx + dy * dy) / r; //added by ikill240c
            case SQUARE -> Math.max(Math.abs(dx), Math.abs(dy)) / (float) r; //added by ikill240c
            case DIAMOND -> (Math.abs(dx) + Math.abs(dy)) / (float) r; //added by ikill240c
        }; //added by ikill240c
    } //added by ikill240c

    private void applyBrush(int cx, int cy) {
        int r = brush_radius;
        for (int dy = -r; dy <= r; dy++) {
            int py = cy + dy;
            if (py < 0 || py >= height.getHeight())
                continue;
            for (int dx = -r; dx <= r; dx++) {
                int px = cx + dx;
                if (px < 0 || px >= height.getWidth())
                    continue;
                if (!inBrushShape(dx, dy, r)) //added by ikill240c - was a circle-only dist2 > r2 check
                    continue;
                // Simple smoothstep-style falloff: full strength at the center, fading to zero
                // at the brush edge, rather than a flat circular stamp. //added by ikill240c: now
                // shape-aware via shapeDistanceRatio() instead of always a circular ratio.
                float t = 1f - shapeDistanceRatio(dx, dy, r); //added by ikill240c
                float falloff = t * t * (3f - 2f * t);
                // CLIFF uses a much narrower falloff band (only the outer 15% of the radius
                // blends at all) instead of the smooth curve above, so most of the brush's
                // interior sits at full, undiminished strength right up to a steep edge - a
                // "cliff face" rather than a gentle slope. PLATEAU goes further still: no falloff
                // at all, a hard-edged stamp at a sampled height (see the switch below).
                // //added by ikill240c
                if (current_tool == Tool.CLIFF) { //added by ikill240c
                    float shape_t = shapeDistanceRatio(dx, dy, r); //added by ikill240c
                    falloff = shape_t <= 0.85f ? 1f : Math.max(0f, (1f - shape_t) / 0.15f); //added by ikill240c
                } //added by ikill240c
                float current = height.getPixel(px, py);
                switch (current_tool) {
                    case RAISE -> height.putPixel(px, py, current + brush_strength * falloff);
                    case LOWER -> height.putPixel(px, py, Math.max(0f, current - brush_strength * falloff));
                    case CLIFF -> { //added by ikill240c - raise/lower (by sign of brush_strength) with the steep falloff band computed above
                        float delta = brush_strength * falloff; //added by ikill240c
                        height.putPixel(px, py, Math.max(0f, current + delta)); //added by ikill240c
                    } //added by ikill240c
                    case SMOOTH -> {
                        // Local box-average smoothing, NOT Channel.smooth(radius) - that method
                        // blurs the entire channel uniformly, which would smooth the whole map on
                        // every brush stroke rather than just the area under the cursor.
                        float sum = 0f;
                        int count = 0;
                        for (int ny = -1; ny <= 1; ny++) {
                            for (int nx = -1; nx <= 1; nx++) {
                                sum += height.getPixelSafe(px + nx, py + ny);
                                count++;
                            }
                        }
                        float avg = sum / count;
                        height.putPixel(px, py, current + (avg - current) * falloff);
                    }
                    case FLATTEN -> height.putPixel(px, py,
                            current + (flatten_target_height - current) * falloff);
                    case PLATEAU -> height.putPixel(px, py, flatten_target_height); //added by ikill240c - no falloff at all, hard edge at the shape boundary
                    default -> {
                        // placement tools don't reach here - handled separately in mousePressed
                    }
                }
            }
        }
    }

    private void handlePlacementClick(int gx, int gy, boolean right_click) {
        if (right_click) {
            boolean removed = terrain.removeNearestResource(gx, gy, MARKER_PICK_RADIUS);
            if (!removed) {
                terrain.removeNearestStart(gx, gy, MARKER_PICK_RADIUS);
            }
            updateStatus();
            // Removing a marker means ERASING it, which requires restoring whatever terrain color
            // was underneath - not something a single incremental paintMarker() call can do the
            // way adding one can. rebuildImage() is the correct fix here (see its own call sites
            // below for the placement case, which uses the lightweight incremental path instead
            // for performance). //added by ikill240c
            canvas.rebuildImage(); //added by ikill240c
            return;
        }
        switch (current_tool) {
            case TREE -> placeResourceCluster(AuthoredTerrain.ResourceType.TREE, gx, gy); //added by ikill240c
            case PALM_TREE -> placeResourceCluster(AuthoredTerrain.ResourceType.PALM_TREE, gx, gy); //added by ikill240c
            case ROCK -> placeResourceCluster(AuthoredTerrain.ResourceType.ROCK, gx, gy); //added by ikill240c
            case IRON -> placeResourceCluster(AuthoredTerrain.ResourceType.IRON, gx, gy); //added by ikill240c
            case START -> {
                int player_index = (Integer) start_player_combo.getSelectedItem();
                // Combo index 0 is "No Team" (-1), index 1 is "Team 1", etc. - see the combo's
                // setup for why this mapping is index-1 rather than parsing the display text.
                // //added by ikill240c
                int team = start_team_combo.getSelectedIndex() - 1; //added by ikill240c
                terrain.addStart(player_index, gx, gy, team); //added by ikill240c
                canvas.paintNewStartMarker(new AuthoredTerrain.StartPosition(player_index, gx, gy, team)); //added by ikill240c
            }
            case DELETE -> {
                boolean removed = terrain.removeNearestResource(gx, gy, MARKER_PICK_RADIUS);
                if (!removed) {
                    terrain.removeNearestStart(gx, gy, MARKER_PICK_RADIUS);
                }
                canvas.rebuildImage(); // erasing - see the right-click branch's comment above //added by ikill240c
            }
            default -> {
                // brush tools don't reach here
            }
        }
        updateStatus();
        canvas.repaint();
    }

    // Places cluster_count markers of the given type: one at the exact clicked point, and the
    // rest scattered uniformly within a disc of cluster_radius around it (rejection-sampled - pick
    // a random point in the bounding square, keep it only if it's actually within the circle,
    // rather than a polar-coordinate sample that would bias points toward the center). Off-map
    // scatter points are silently skipped rather than clamped to the edge, since clamping would
    // pile extra markers up along map borders. A plain java.util.Random is fine here (not the
    // game's own deterministic RNG) - this is a standalone offline editing tool with no
    // multiplayer lockstep simulation to keep in sync. //added by ikill240c
    private void placeResourceCluster(AuthoredTerrain.@NonNull ResourceType type, int gx, int gy) { //added by ikill240c
        terrain.addResource(type, gx, gy); //added by ikill240c
        canvas.paintNewResourceMarker(new AuthoredTerrain.ResourceNode(type, gx, gy)); //added by ikill240c
        java.util.Random random = java.util.concurrent.ThreadLocalRandom.current(); //added by ikill240c
        int w = height.getWidth(); //added by ikill240c
        int h = height.getHeight(); //added by ikill240c
        for (int i = 1; i < cluster_count; i++) { //added by ikill240c
            int ox = gx + random.nextInt(-cluster_radius, cluster_radius + 1); //added by ikill240c
            int oy = gy + random.nextInt(-cluster_radius, cluster_radius + 1); //added by ikill240c
            int dx = ox - gx; //added by ikill240c
            int dy = oy - gy; //added by ikill240c
            if (dx * dx + dy * dy > cluster_radius * cluster_radius) //added by ikill240c
                continue; // outside the circle - rejection sample, try the next i rather than retry //added by ikill240c
            if (ox < 0 || ox >= w || oy < 0 || oy >= h) //added by ikill240c
                continue; // off the map - skip rather than clamp (see method comment) //added by ikill240c
            terrain.addResource(type, ox, oy); //added by ikill240c
            canvas.paintNewResourceMarker(new AuthoredTerrain.ResourceNode(type, ox, oy)); //added by ikill240c
        } //added by ikill240c
    }

    private boolean isBrushTool(Tool tool) {
        return tool == Tool.RAISE || tool == Tool.LOWER || tool == Tool.SMOOTH || tool == Tool.FLATTEN
                || tool == Tool.PLATEAU || tool == Tool.CLIFF; //added by ikill240c
    }

    // FLATTEN and PLATEAU both sample the clicked point's height as their target on first press,
    // before any brush strokes apply - CLIFF deliberately does NOT (it raises/lowers by
    // brush_strength like RAISE/LOWER, just with a steeper falloff band, so it has no fixed
    // target height to sample). //added by ikill240c
    private boolean isSampledHeightTool(Tool tool) { //added by ikill240c
        return tool == Tool.FLATTEN || tool == Tool.PLATEAU; //added by ikill240c
    } //added by ikill240c

    // TREE/PALM_TREE/ROCK/IRON/START specifically - DELETE is deliberately excluded (see the
    // mousePressed comment on why drag-erasing isn't armed the same way as drag-placing).
    // //added by ikill240c
    private boolean isPlacementTool(Tool tool) { //added by ikill240c
        return tool == Tool.TREE || tool == Tool.PALM_TREE || tool == Tool.ROCK || tool == Tool.IRON //added by ikill240c
                || tool == Tool.START; //added by ikill240c
    }

    // ****************
    // * CANVAS       *
    // ****************

    private final class Canvas extends JPanel {
        private BufferedImage image;
        // Current mouse position in GRID coordinates (not screen pixels), tracked via
        // mouseMoved/mouseDragged below, purely so paintComponent() can draw a live brush-size
        // circle that follows the cursor - null when the mouse is outside the canvas (or hasn't
        // moved over it yet), in which case no circle is drawn. //added by ikill240c
        private @Nullable Point brush_cursor_grid_pos; //added by ikill240c

        // Minimum grid-unit spacing between markers placed while drag-painting resources/starts -
        // without this, a single drag stroke at typical mouse-movement granularity would place a
        // marker on nearly every pixel of travel, producing a solid line rather than a scattered
        // placement. Not used for brush tools (height painting), which already apply falloff
        // continuously via applyBrush() regardless of movement granularity. //added by ikill240c
        private static final int PLACEMENT_DRAG_MIN_SPACING = 4; //added by ikill240c
        private @Nullable Point last_placement_grid_pos; //added by ikill240c
        private boolean placement_stroke_in_progress; //added by ikill240c

        Canvas() {
            rebuildImage();
            MouseAdapter mouse_adapter = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    int[] grid = toGridCoords(e.getX(), e.getY());
                    if (grid == null)
                        return;
                    boolean right_click = SwingUtilities.isRightMouseButton(e);
                    if (isBrushTool(current_tool) && !right_click) {
                        pushUndoSnapshot();
                        stroke_in_progress = true;
                        if (isSampledHeightTool(current_tool)) { //added by ikill240c - was `current_tool == Tool.FLATTEN`, now also covers PLATEAU
                            flatten_target_height = height.getPixel(grid[0], grid[1]);
                            flatten_sampled = true;
                        }
                        applyBrush(grid[0], grid[1]);
                        rebuildImage();
                    } else {
                        handlePlacementClick(grid[0], grid[1], right_click);
                        // Only arms drag-painting for a LEFT-click placement tool press, not
                        // right-click (removal) or DELETE - dragging while erasing should not
                        // sweep-delete everything under the cursor, matching how right-click
                        // removal has always been a precise, one-marker-per-click action.
                        // //added by ikill240c
                        if (!right_click && isPlacementTool(current_tool)) { //added by ikill240c
                            placement_stroke_in_progress = true; //added by ikill240c
                            last_placement_grid_pos = new Point(grid[0], grid[1]); //added by ikill240c
                        } //added by ikill240c
                    }
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    stroke_in_progress = false;
                    flatten_sampled = false;
                    placement_stroke_in_progress = false; //added by ikill240c
                    last_placement_grid_pos = null; //added by ikill240c
                }

                @Override
                public void mouseExited(MouseEvent e) { //added by ikill240c
                    // mouseExited is part of MouseListener, not MouseMotionListener - belongs on
                    // this MouseAdapter (which implements both), not the MouseMotionAdapter below.
                    // //added by ikill240c
                    brush_cursor_grid_pos = null; //added by ikill240c
                    repaint(); //added by ikill240c
                } //added by ikill240c
            };
            addMouseListener(mouse_adapter);
            addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseDragged(MouseEvent e) {
                    int[] grid = toGridCoords(e.getX(), e.getY()); //added by ikill240c
                    if (grid != null) { //added by ikill240c
                        brush_cursor_grid_pos = new Point(grid[0], grid[1]); //added by ikill240c
                    } //added by ikill240c
                    if (stroke_in_progress && isBrushTool(current_tool)) {
                        if (grid == null)
                            return;
                        applyBrush(grid[0], grid[1]);
                        rebuildImage();
                    } else if (placement_stroke_in_progress && isPlacementTool(current_tool)) { //added by ikill240c
                        if (grid == null || last_placement_grid_pos == null) //added by ikill240c
                            return; //added by ikill240c
                        int dx = grid[0] - last_placement_grid_pos.x; //added by ikill240c
                        int dy = grid[1] - last_placement_grid_pos.y; //added by ikill240c
                        if (dx * dx + dy * dy >= PLACEMENT_DRAG_MIN_SPACING * PLACEMENT_DRAG_MIN_SPACING) { //added by ikill240c
                            handlePlacementClick(grid[0], grid[1], false); //added by ikill240c
                            last_placement_grid_pos = new Point(grid[0], grid[1]); //added by ikill240c
                        } //added by ikill240c
                    } else { //added by ikill240c
                        repaint(); // still update the brush-size circle position even when idle //added by ikill240c
                    } //added by ikill240c
                }

                @Override
                public void mouseMoved(MouseEvent e) { //added by ikill240c
                    int[] grid = toGridCoords(e.getX(), e.getY()); //added by ikill240c
                    brush_cursor_grid_pos = grid == null ? null : new Point(grid[0], grid[1]); //added by ikill240c
                    repaint(); //added by ikill240c
                } //added by ikill240c
            });
            // Mouse-wheel zoom, centered on the cursor position conceptually (though since this
            // panel is wrapped in a JScrollPane, the actual scroll-position preservation is left
            // to the scrollpane's own defaults rather than hand-computed - a reasonable tradeoff
            // for how rarely zoom-while-precisely-tracking-a-point matters for a terrain editor).
            // //added by ikill240c
            addMouseWheelListener(this::onMouseWheel); //added by ikill240c
        }

        private void onMouseWheel(MouseWheelEvent e) { //added by ikill240c
            double factor = e.getWheelRotation() < 0 ? ZOOM_STEP : 1.0 / ZOOM_STEP; //added by ikill240c
            setZoom(zoom * factor); //added by ikill240c
            // Without this, the enclosing JScrollPane's own default wheel-scroll behavior ALSO
            // fires for the same event (Swing delivers an unconsumed MouseWheelEvent to ancestor
            // components after this listener returns) - every zoom action was simultaneously
            // scrolling the viewport too, which made zoom look broken: the view jumps to a
            // different, similarly-scaled-looking part of the map at the same time it actually
            // zooms, masking the real size change. //added by ikill240c
            e.consume(); //added by ikill240c
        } //added by ikill240c

        // Recomputes this panel's preferred size from the current zoom level and map dimensions,
        // then tells the enclosing JScrollPane to re-lay-out around it. MAP_BORDER_MARGIN is
        // added on all sides so the map's edge is never flush against the scrollpane's own edge -
        // see paintComponent()'s border-drawing for the matching visual. //added by ikill240c
        void updatePreferredSizeForZoom() { //added by ikill240c
            int base = Math.max(height.getWidth(), height.getHeight()); //added by ikill240c
            int zoomed = (int) Math.round(base * zoom); //added by ikill240c
            setPreferredSize(new Dimension(zoomed + 2 * MAP_BORDER_MARGIN, zoomed + 2 * MAP_BORDER_MARGIN)); //added by ikill240c
            revalidate(); //added by ikill240c
            repaint(); //added by ikill240c
        } //added by ikill240c

        // Screen-space bounds of the map image itself (excluding MAP_BORDER_MARGIN), given the
        // panel's current actual size and the map's aspect ratio - used by both toGridCoords()
        // (screen -> grid) and paintComponent()/rebuildImage() call sites (grid -> screen) so the
        // two directions always agree exactly. //added by ikill240c
        private java.awt.Rectangle mapScreenBounds() { //added by ikill240c
            int w = getWidth() - 2 * MAP_BORDER_MARGIN; //added by ikill240c
            int h = getHeight() - 2 * MAP_BORDER_MARGIN; //added by ikill240c
            if (w <= 0 || h <= 0) //added by ikill240c
                return new java.awt.Rectangle(MAP_BORDER_MARGIN, MAP_BORDER_MARGIN, Math.max(1, w), Math.max(1, h)); //added by ikill240c
            return new java.awt.Rectangle(MAP_BORDER_MARGIN, MAP_BORDER_MARGIN, w, h); //added by ikill240c
        } //added by ikill240c

        private int[] toGridCoords(int px, int py) {
            java.awt.Rectangle bounds = mapScreenBounds(); //added by ikill240c
            int rel_x = px - bounds.x; //added by ikill240c
            int rel_y = py - bounds.y; //added by ikill240c
            if (bounds.width <= 0 || bounds.height <= 0 || rel_x < 0 || rel_y < 0 //added by ikill240c
                    || rel_x >= bounds.width || rel_y >= bounds.height) //added by ikill240c
                return null;
            int gx = rel_x * height.getWidth() / bounds.width; //added by ikill240c
            int gy = rel_y * height.getHeight() / bounds.height; //added by ikill240c
            if (gx < 0 || gx >= height.getWidth() || gy < 0 || gy >= height.getHeight())
                return null;
            return new int[]{gx, gy};
        }

        void rebuildImage() {
            int w = height.getWidth();
            int h = height.getHeight();
            image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);

            if (preview_mode) {
                // Mirrors Landscape.loadAuthoredHeight()'s own derivation chain exactly (slope ->
                // threshold -> largestConnected), so what's shown here is a genuine preview of
                // what the game would compute, not an approximation invented separately.
                Channel slope = height.copy().lineart();
                Channel access = slope.threshold(0f, DEFAULT_ACCESS_THRESHOLD).largestConnected(1f);
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        float h_val = height.getPixel(x, y);
                        int rgb;
                        if (h_val < SEA_LEVEL) {
                            rgb = new Color(40, 80, 200).getRGB(); // water
                        } else if (access.getPixel(x, y) <= 0f) {
                            rgb = new Color(200, 40, 40).getRGB(); // land, but unreachable
                        } else {
                            int gray = (int) Math.max(0, Math.min(255, h_val * 255));
                            rgb = new Color(gray, gray, gray).getRGB();
                        }
                        image.setRGB(x, y, rgb);
                    }
                }
                overlayMarkers(image, true);
            } else {
                // Was plain grayscale for everything, land and water alike, with no way to tell
                // them apart while actively editing (only preview mode distinguished water) -
                // light blue water / tan flat land / brown-tinted hills reads as an actual terrain
                // map, and reuses the same slope computation preview mode already does to find
                // "flat" ground, so the two modes agree on what counts as flat. //added by ikill240c
                Channel slope = height.copy().lineart(); //added by ikill240c
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        float h_val = height.getPixel(x, y); //added by ikill240c
                        int rgb; //added by ikill240c
                        if (h_val < SEA_LEVEL) { //added by ikill240c
                            rgb = new Color(173, 216, 230).getRGB(); // light blue water //added by ikill240c
                        } else if (slope.getPixel(x, y) <= DEFAULT_ACCESS_THRESHOLD) { //added by ikill240c
                            rgb = new Color(210, 180, 140).getRGB(); // tan flat land //added by ikill240c
                        } else { //added by ikill240c
                            // Steep/hilly ground: brown-tinted gradient by elevation, so hills are
                            // still visually distinguishable from each other by height, but the
                            // whole palette stays warm/earthy rather than clashing gray against
                            // tan. //added by ikill240c
                            float t = Math.max(0f, Math.min(1f, h_val)); //added by ikill240c
                            int r = (int) (101 + t * (160 - 101)); //added by ikill240c
                            int g = (int) (67 + t * (120 - 67)); //added by ikill240c
                            int b = (int) (33 + t * (85 - 33)); //added by ikill240c
                            rgb = new Color(r, g, b).getRGB(); //added by ikill240c
                        }
                        image.setRGB(x, y, rgb); //added by ikill240c
                    }
                }
                overlayMarkers(image, false);
            }
            repaint();
        }

        // Paints just the one newly-added marker directly onto the existing cached image, rather
        // than calling the full rebuildImage() (which re-walks every pixel of the map to redraw
        // the whole heightmap-to-grayscale conversion, not just the marker layer). Without this,
        // handlePlacementClick()'s plain repaint() only re-blits the STALE cached image - the new
        // marker was never drawn onto it in the first place, so it looked like the placement
        // hadn't worked until the map was reloaded (which happens to call rebuildImage() fresh).
        // Skips the preview-mode unreachable/underwater magenta flagging overlayMarkers() does -
        // that needs slope/access recomputed over the whole map, which is exactly the per-pixel
        // cost this method exists to avoid; a freshly-placed marker will get that check the next
        // time rebuildImage() runs anyway (e.g. toggling preview mode, or after a removal).
        // //added by ikill240c
        private void paintNewResourceMarker(AuthoredTerrain.@NonNull ResourceNode node) { //added by ikill240c
            Color color = switch (node.type()) { //added by ikill240c
                case TREE -> new Color(0, 180, 0); //added by ikill240c
                case PALM_TREE -> new Color(0, 220, 120); //added by ikill240c
                case ROCK -> new Color(160, 160, 160); //added by ikill240c
                case IRON -> new Color(220, 140, 0); //added by ikill240c
            };
            paintMarker(image, node.x(), node.y(), color, 3); //added by ikill240c
            repaint(); //added by ikill240c
        }

        private void paintNewStartMarker(AuthoredTerrain.@NonNull StartPosition start) { //added by ikill240c
            paintMarker(image, start.x(), start.y(), Color.RED, 5); //added by ikill240c
            repaint(); //added by ikill240c
        }

        private void overlayMarkers(BufferedImage img, boolean is_preview) {
            int w = img.getWidth();
            int h = img.getHeight();
            Channel slope_for_check = is_preview ? height.copy().lineart() : null;
            Channel access_for_check = is_preview
                    ? slope_for_check.threshold(0f, DEFAULT_ACCESS_THRESHOLD).largestConnected(1f)
                    : null;
            for (AuthoredTerrain.ResourceNode node : terrain.getResources()) {
                Color color = switch (node.type()) {
                    case TREE -> new Color(0, 180, 0);
                    case PALM_TREE -> new Color(0, 220, 120);
                    case ROCK -> new Color(160, 160, 160);
                    case IRON -> new Color(220, 140, 0);
                };
                if (is_preview && (height.getPixel(node.x(), node.y()) < SEA_LEVEL
                        || access_for_check.getPixel(node.x(), node.y()) <= 0f)) {
                    color = Color.MAGENTA; // flags a resource placed somewhere unreachable/underwater
                }
                paintMarker(img, node.x(), node.y(), color, 3);
            }
            for (AuthoredTerrain.StartPosition start : terrain.getStarts()) {
                Color color = Color.RED;
                if (is_preview && (height.getPixel(start.x(), start.y()) < SEA_LEVEL
                        || access_for_check.getPixel(start.x(), start.y()) <= 0f)) {
                    color = Color.MAGENTA;
                }
                paintMarker(img, start.x(), start.y(), color, 5);
            }
        }

        private void paintMarker(BufferedImage img, int cx, int cy, Color color, int radius) {
            int w = img.getWidth();
            int h = img.getHeight();
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    int x = cx + dx;
                    int y = cy + dy;
                    if (x < 0 || x >= w || y < 0 || y >= h)
                        continue;
                    if (dx * dx + dy * dy <= radius * radius) {
                        img.setRGB(x, y, color.getRGB());
                    }
                }
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            java.awt.Rectangle bounds = mapScreenBounds(); //added by ikill240c
            // Border around the map's actual edge, drawn just outside mapScreenBounds() in the
            // MAP_BORDER_MARGIN gap - makes the map's boundary clearly visible against the
            // surrounding panel background at any zoom level, rather than the image simply
            // running flush to (and indistinguishable from) the panel/scrollpane edge.
            // //added by ikill240c
            g2.setColor(getBackground()); //added by ikill240c
            g2.fillRect(0, 0, getWidth(), getHeight()); //added by ikill240c
            g2.drawImage(image, bounds.x, bounds.y, bounds.width, bounds.height, null); //added by ikill240c
            g2.setColor(Color.DARK_GRAY); //added by ikill240c
            g2.drawRect(bounds.x - 1, bounds.y - 1, bounds.width + 1, bounds.height + 1); //added by ikill240c
            // Live brush/cluster-size circle at the cursor - drawn fresh every repaint directly
            // onto the component's Graphics, NOT baked into the cached `image` the way markers
            // and terrain are. Baking it in would mean erasing and repainting the whole image on
            // every single mouse-move event just to move a circle outline, which is exactly the
            // per-pixel cost the incremental marker-painting above exists to avoid.
            // //added by ikill240c
            if (brush_cursor_grid_pos != null && (isBrushTool(current_tool) //added by ikill240c
                    || (isPlacementTool(current_tool) && current_tool != Tool.START && cluster_count > 1))) { //added by ikill240c
                int radius_grid = isBrushTool(current_tool) ? brush_radius : cluster_radius; //added by ikill240c
                float scale_x = bounds.width / (float) height.getWidth(); //added by ikill240c
                float scale_y = bounds.height / (float) height.getHeight(); //added by ikill240c
                int screen_x = bounds.x + Math.round(brush_cursor_grid_pos.x * scale_x); //added by ikill240c
                int screen_y = bounds.y + Math.round(brush_cursor_grid_pos.y * scale_y); //added by ikill240c
                int screen_radius_x = Math.round(radius_grid * scale_x); //added by ikill240c
                int screen_radius_y = Math.round(radius_grid * scale_y); //added by ikill240c
                g2.setColor(Color.WHITE); //added by ikill240c
                // Brush outline now follows brush_shape too, not just its size - a circle for
                // CIRCLE, a plain rectangle for SQUARE, and a rotated-square (rhombus) polygon for
                // DIAMOND, so the cursor preview actually matches what applyBrush() will paint.
                // //added by ikill240c
                switch (brush_shape) { //added by ikill240c
                    case CIRCLE -> g2.drawOval(screen_x - screen_radius_x, screen_y - screen_radius_y, //added by ikill240c
                            screen_radius_x * 2, screen_radius_y * 2); //added by ikill240c
                    case SQUARE -> g2.drawRect(screen_x - screen_radius_x, screen_y - screen_radius_y, //added by ikill240c
                            screen_radius_x * 2, screen_radius_y * 2); //added by ikill240c
                    case DIAMOND -> g2.drawPolygon( //added by ikill240c
                            new int[]{screen_x, screen_x + screen_radius_x, screen_x, screen_x - screen_radius_x}, //added by ikill240c
                            new int[]{screen_y - screen_radius_y, screen_y, screen_y + screen_radius_y, screen_y}, //added by ikill240c
                            4); //added by ikill240c
                } //added by ikill240c
            } //added by ikill240c
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new MapEditor().setVisible(true));
    }
}
