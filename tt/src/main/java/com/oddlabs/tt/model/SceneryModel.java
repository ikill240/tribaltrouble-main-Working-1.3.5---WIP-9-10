package com.oddlabs.tt.model;

import com.oddlabs.tt.animation.Animated;
import com.oddlabs.tt.landscape.World;
import com.oddlabs.tt.pathfinder.Occupant;
import com.oddlabs.tt.pathfinder.UnitGrid;
import com.oddlabs.tt.render.SpriteKey;
import com.oddlabs.tt.util.StateChecksum;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public class SceneryModel extends Model implements Occupant, ModelToolTip, Animated {
    private final @NonNull SpriteKey sprite_renderer;
    private final float shadow_diameter;
    private final boolean occupy;
    // Tracks whether occupyGrid() actually ran in the constructor below, separately from `occupy`
    // (the caller's REQUEST to occupy) - the two can now differ, since the constructor skips the
    // actual occupyGrid() call when the target cell turns out to already be taken by something
    // else. remove() must only free the grid if this scenery model actually occupied it in the
    // first place - freeing a cell it never held crashes freeGrid()'s own "this occupant isn't
    // what's actually there" assertion just as surely as the original occupyGrid() crash did.
    // //added by ikill240c
    private boolean did_occupy = false; //added by ikill240c
    private final @Nullable String name;
    private final int animation;
    private final float seconds_per_animation_cycle;
    private float anim_time = 0;

    public SceneryModel(@NonNull World world, float x, float y, float dir_x, float dir_y,
            @NonNull SpriteKey sprite_renderer) {
        this(world, x, y, dir_x, dir_y, sprite_renderer, 0f, false, null);
    }

    public SceneryModel(@NonNull World world, float x, float y, float dir_x, float dir_y,
            @NonNull SpriteKey sprite_renderer, float shadow_diameter, boolean occupy, @Nullable String name) {
        this(world, x, y, dir_x, dir_y, sprite_renderer, shadow_diameter, occupy, name, -1, -1, 0);
    }

    public SceneryModel(@NonNull World world, float x, float y, float dir_x, float dir_y,
            @NonNull SpriteKey sprite_renderer, float shadow_diameter, boolean occupy, @Nullable String name,
            int animation, float seconds_per_animation_cycle, float anim_offset) {
        super(world);
        this.sprite_renderer = sprite_renderer;
        this.shadow_diameter = shadow_diameter;
        this.occupy = occupy;
        this.name = name;
        this.animation = animation;
        this.seconds_per_animation_cycle = seconds_per_animation_cycle;
        anim_time = anim_offset;
        setPosition(x, y);
        setDirection(dir_x, dir_y);
        doRegister();
        if (occupy) {
            // Guard against a grid cell already being occupied by something else (e.g. a randomly
            // scattered plant/resource that happens to land on this scenery's fixed campaign
            // coordinates, or two scenery placements overlapping) - occupyGrid() asserts the cell
            // is free and crashes the entire game outright if it isn't. Scenery is purely
            // decorative, so skipping the occupancy (it still renders and plays its animation,
            // just doesn't block pathfinding at this one cell) is a far better outcome than a
            // fatal crash over what's ultimately a cosmetic placement conflict.
            // //added by ikill240c
            if (!world.getUnitGrid().isGridOccupied(getGridX(), getGridY(), UnitGrid.LAND)) { //added by ikill240c
                world.getUnitGrid().occupyGrid(getGridX(), getGridY(), this);
                did_occupy = true; //added by ikill240c
            } //added by ikill240c
        }
    }

    public final @Nullable String getName() {
        return name;
    }

    @Override
    public final float getShadowDiameter() {
        return shadow_diameter;
    }

    protected void doRegister() {
        register();
        reinsert();
        getWorld().getNotificationListener().registerTarget(this);
        if (animation > -1)
            getWorld().getAnimationManagerGameTime().registerAnimation(this);
    }

    @Override
    public final void remove() {
        if (did_occupy) { //added by ikill240c - was `if (occupy)`; see did_occupy's own field comment for why occupy alone is no longer sufficient here
            getWorld().getUnitGrid().freeGrid(getGridX(), getGridY(), this);
        }
        super.remove();
        getWorld().getNotificationListener().unregisterTarget(this);
        if (animation > -1)
            getWorld().getAnimationManagerGameTime().removeAnimation(this);
    }

    @Override
    public final void visit(@NonNull ToolTipVisitor visitor) {
        visitor.visitSceneryModel(this);
    }

    @Override
    public final void animate(float t) {
        anim_time += t / 2.5f;
        if (seconds_per_animation_cycle > -1 && anim_time > seconds_per_animation_cycle)
            anim_time = 0;
        reinsert();
    }

    @Override
    public final int getAnimation() {
        return animation > -1 ? animation : 0;
    }

    @Override
    public final float getAnimationTicks() {
        return animation > -1 ? anim_time : 0;
    }

    @Override
    public final void updateChecksum(@NonNull StateChecksum checksum) {
    }

    @Override
    public int getPenalty() {
        return Occupant.STATIC;
    }

    @Override
    public final int getGridX() {
        return UnitGrid.toGridCoordinate(getPositionX());
    }

    @Override
    public final int getGridY() {
        return UnitGrid.toGridCoordinate(getPositionY());
    }

    @Override
    public final float getSize() {
        throw new RuntimeException();
    }

    @Override
    public final boolean isDead() {
        return false;
    }

    public final boolean isOccupying() {
        return occupy;
    }

    @Override
    public final @NonNull SpriteKey getSpriteRenderer() {
        return sprite_renderer;
    }

    @Override
    public void visit(@NonNull ElementVisitor visitor) {
        visitor.visitSceneryModel(this);
    }
}
