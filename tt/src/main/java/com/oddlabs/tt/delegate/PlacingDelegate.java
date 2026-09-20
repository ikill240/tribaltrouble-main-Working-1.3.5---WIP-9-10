package com.oddlabs.tt.delegate;

import com.oddlabs.tt.camera.CameraState;
import com.oddlabs.tt.camera.GameCamera;
import com.oddlabs.tt.gui.MouseButton;
import com.oddlabs.tt.input.GameAction;
import com.oddlabs.tt.input.InputEvent;
import com.oddlabs.tt.input.InputPhase;
import com.oddlabs.tt.landscape.HeightMap;
import com.oddlabs.tt.landscape.LandscapeTarget;
import com.oddlabs.tt.model.Abilities;
import com.oddlabs.tt.model.BuildingTemplate;
import com.oddlabs.tt.pathfinder.UnitGrid;
import com.oddlabs.tt.player.BuildingSiteScanFilter;
import com.oddlabs.tt.render.BuildingSiteRenderer;
import com.oddlabs.tt.render.LandscapeLocation;
import com.oddlabs.tt.render.LandscapeRenderer;
import com.oddlabs.tt.render.MatrixStack;
import com.oddlabs.tt.render.RenderQueues;
import com.oddlabs.tt.render.Renderer;
import com.oddlabs.tt.render.Sprite;
import com.oddlabs.tt.render.SpriteRenderer;
import com.oddlabs.tt.render.shader.SpriteShader;
import com.oddlabs.tt.render.state.BlendMode;
import com.oddlabs.tt.render.state.CullMode;
import com.oddlabs.tt.render.state.DepthMode;
import com.oddlabs.tt.render.state.RenderContext;
import com.oddlabs.tt.viewer.WorldViewer;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.logging.Logger;

public final class PlacingDelegate extends ControllableCameraDelegate {
    private static final Logger logger = Logger.getLogger(PlacingDelegate.class.getName());
    private static final int GRID_RADIUS = 20;
    private static final LandscapeLocation landscape_hit = new LandscapeLocation();

    private final BuildingSiteRenderer site_renderer = new BuildingSiteRenderer();
    private final int building_index;
    private final SpriteShader spriteShader = new SpriteShader();
    // Tracks whether a building has already been placed by THIS delegate instance - the delegate stays
    // alive across repeat placements while shift is held (see placeObject() below), so this is true from
    // the second placement onward in the same shift-held streak. Used to decide whether to take over
    // the builder(s) immediately (first placement) or queue behind whatever they're already doing
    // (every placement after that), so buildings get worked on in click order instead of each new
    // placement abandoning the previous one. //added by ikill240c
    private boolean has_placed_once = false;

    public PlacingDelegate(@NonNull WorldViewer viewer, @NonNull CameraState old_camera, int building_index) {
        super(viewer, new GameCamera(viewer, old_camera));
        this.building_index = building_index;
    }

    private @NonNull BuildingTemplate getTemplate() {
        return getViewer().getLocalPlayer().getRace().getBuildingTemplate(building_index);
    }

    public void placeObject() {
        if (!getViewer().getPicker().pickLocation(getCamera().getState(), landscape_hit)) {
            logger.info("placeObject: Pick failed (off map?)");
            return;
        }
        UnitGrid unit_grid = getViewer().getWorld().getUnitGrid();
        int placing_grid_x = UnitGrid.toGridCoordinate(landscape_hit.x);
        int placing_grid_y = UnitGrid.toGridCoordinate(landscape_hit.y);
        if (getTemplate().isPlacingLegal(getViewer().getWorld().getUnitGrid(), placing_grid_x, placing_grid_y)) {
            var peons = getViewer().getSelection().getCurrentSelection().filter(Abilities.BUILD);
            if (peons.length > 0) {
                logger.info("placeObject: Placing building at " + placing_grid_x + "," + placing_grid_y);
                // queue=has_placed_once: the first placement in a shift-held streak takes over the
                // builder(s) immediately (matching normal, non-repeat placement); every placement after
                // that in the same streak queues instead, so the buildings actually get built in the
                // order they were clicked rather than each new click abandoning the previous site.
                // //added by ikill240c
                getViewer().getPeerHub().getPlayerInterface().placeBuilding(peons, building_index, placing_grid_x,
                        placing_grid_y, has_placed_once);
                has_placed_once = true; //added by ikill240c
            } else {
                logger.info("placeObject: No peons selected");
            }
            // Age of Empires 2-style repeat placement: holding Shift at the moment of a successful
            // placement keeps this delegate active (skipping pop()) instead of exiting placement mode,
            // so the same building type can be placed again immediately without reopening the menu.
            // Checked here (rather than only in handleInput's UI_ACTIVATE branch) so it also covers the
            // direct mousePressed(LEFT) path below. //added by ikill240c
            if (Renderer.getLocalInput().isShiftDownCurrently()) {
                logger.info("placeObject: Shift held, staying in placement mode");
            } else {
                logger.info("placeObject: Popping delegate");
                pop();
            }
        } else {
            logger.info("placeObject: Placement illegal");
        }
    }

    @Override
    public void handleInput(@NonNull InputEvent event) {
        if (event.consumeAction(GameAction.UI_ACTIVATE)) {
            if (event.getPhase() == InputPhase.RELEASED) {
                placeObject();
            }
            event.consume();
            return;
        }

        if (event.getPhase() == InputPhase.PRESSED || event.getPhase() == InputPhase.REPEAT) {
            if (event.consumeAction(GameAction.UI_CANCEL)) {
                pop();
                event.consume();
                return;
            }
        }

        super.handleInput(event);
    }

    @Override
    public void mousePressed(@NonNull MouseButton button, int x, int y) {
        switch (button) {
            case LEFT -> placeObject();
            case RIGHT -> pop();
            default -> super.mousePressed(button, x, y);
        }
    }

    @Override
    public void render3D(@NonNull LandscapeRenderer renderer, @NonNull RenderQueues queues, @NonNull CameraState state,
            @NonNull MatrixStack modelViewStack, @NonNull MatrixStack projectionStack) {
        if (!getViewer().getPicker().pickLocation(getCamera().getState(), landscape_hit)) return;
        UnitGrid unit_grid = getViewer().getWorld().getUnitGrid();
        int placing_grid_x = UnitGrid.toGridCoordinate(landscape_hit.x) - (getTemplate().getPlacingSize() - 1);
        int placing_grid_y = UnitGrid.toGridCoordinate(landscape_hit.y) - (getTemplate().getPlacingSize() - 1);
        int placing_center_grid_x = UnitGrid.toGridCoordinate(landscape_hit.x);
        int placing_center_grid_y = UnitGrid.toGridCoordinate(landscape_hit.y);

        float center_x = HeightMap.METERS_PER_UNIT_GRID * (placing_grid_x + (getTemplate().getPlacingSize() - .5f));
        float center_y = HeightMap.METERS_PER_UNIT_GRID * (placing_grid_y + (getTemplate().getPlacingSize() - .5f));

        BuildingSiteScanFilter filter = new BuildingSiteScanFilter(unit_grid, getTemplate(), GRID_RADIUS, false);
        unit_grid.scan(filter, placing_center_grid_x, placing_center_grid_y);
        List<LandscapeTarget> target_list = filter.getResult();

        RenderContext context = Renderer.getRenderer().getRenderContext();
        site_renderer.renderSites(context, renderer, modelViewStack, projectionStack, target_list, center_x, center_y,
                2 * GRID_RADIUS);
        com.oddlabs.tt.util.GLUtils.checkGLError("Placing: After renderSites");

        SpriteRenderer built_renderer = queues.getRenderer(getTemplate().getBuiltRenderer());
        Sprite sprite = built_renderer.getSpriteList().getSprite(0);

        try (var _ = spriteShader.use()) {

            spriteShader.setUniform(SpriteShader.Uniforms.DESATURATE, 0.5f);
            sprite.setupShaderUniforms(context, spriteShader, 0, false);
            spriteShader.setUniform(SpriteShader.Uniforms.MODULATE_COLOR, true);
            spriteShader.setUniform(SpriteShader.Uniforms.ALPHA_TEST_VALUE, 0.5f);

            if (getTemplate().isPlacingLegal(unit_grid, placing_center_grid_x, placing_center_grid_y))
                spriteShader.setUniform(SpriteShader.Uniforms.COLOR, 1f, 1f, 1f, .8f);
            else
                spriteShader.setUniform(SpriteShader.Uniforms.COLOR, 1f, 0f, 0f, .8f);

            float z = getViewer().getWorld().getHeightMap().getNearestHeight(center_x, center_y);

            modelViewStack.push();
            modelViewStack.translate(center_x, center_y, z);
            spriteShader.setUniformMatrix4(SpriteShader.Uniforms.MODEL_VIEW_MATRIX, false, modelViewStack.current());

            try (var _ = context.withCullMode(CullMode.BACK)) {
                // Pass 1: Depth Prime (Write Depth, No Color)
                try (var _ = context.withDepthMode(DepthMode.READ_WRITE); var _ = context.withColorMask(false, false,
                        false, false); var _ = context.withBlendMode(BlendMode.NONE)) {

                    sprite.renderShader(spriteShader, 0, 0f, built_renderer.getSpriteList());
                }

                // Pass 2: Color Render (No Depth Write, Equal Depth)
                try (var _ = context.withDepthMode(DepthMode.READ_ONLY); var _ = context.withColorMask(true, true, true,
                        true); var _ = context.withBlendMode(BlendMode.ALPHA)) {

                    sprite.renderShader(spriteShader, 0, 0f, built_renderer.getSpriteList());
                }
            } finally {
                spriteShader.setUniform(SpriteShader.Uniforms.DESATURATE, 0.0f);
                spriteShader.setUniform(SpriteShader.Uniforms.MODULATE_COLOR, false);
                spriteShader.setUniform(SpriteShader.Uniforms.ALPHA_TEST_VALUE, 0.3f);
            }

            modelViewStack.pop();
        }
    }
}
