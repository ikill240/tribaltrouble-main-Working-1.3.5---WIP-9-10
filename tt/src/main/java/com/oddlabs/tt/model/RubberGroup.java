package com.oddlabs.tt.model;

import com.oddlabs.tt.landscape.TreeSupply;
import com.oddlabs.tt.landscape.World;
import com.oddlabs.tt.pathfinder.Occupant;
import com.oddlabs.tt.pathfinder.UnitGrid;
import com.oddlabs.tt.util.Target;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

public final class RubberGroup {
    private static final int MIN_CHICKENS_PER_GROUP = 1;// og 3;
    private static final int MAX_CHICKENS_PER_GROUP = 5;//og 7;

    private static final float SPAWN_TIME = 2.5f;

    private final @NonNull World world;
    private final List<Supply> supplies = new ArrayList<>();

    public RubberGroup(@NonNull World world) {
        this.world = world;
        int[] group_position = getGroupPosition();
        if (group_position != null) {
            int num_supplies = MIN_CHICKENS_PER_GROUP + world.getRandom().nextInt(
                    MAX_CHICKENS_PER_GROUP - MIN_CHICKENS_PER_GROUP + 1);
            Target[] supply_positions = world.getUnitGrid().findGridTargets(group_position[0], group_position[1],
                    num_supplies, true);
            float spawn_x = UnitGrid.coordinateFromGrid(group_position[0]);
            float spawn_y = UnitGrid.coordinateFromGrid(group_position[1]);
            for (int i = 0; i < num_supplies; i++) {
                // findGridTargets() only fills slots for cells it actually finds free (unoccupied) near
                // group_position - a densely wooded/rocky area can easily have fewer free cells nearby
                // than the randomly-chosen num_supplies, leaving some array slots null. This previously
                // crashed map generation itself (NullPointerException while spawning wildlife) on any
                // seed/area where that happened. Skipping unfilled slots simply spawns a slightly
                // smaller flock in that rare case instead of crashing - invisible to players.
                // //added by ikill240c
                if (supply_positions[i] == null)
                    continue;
                int grid_x = supply_positions[i].getGridX();
                int grid_y = supply_positions[i].getGridY();
                float x = UnitGrid.coordinateFromGrid(grid_x);
                float y = UnitGrid.coordinateFromGrid(grid_y);
                RubberSupply supply = new RubberSupply(world, world.getLandscapeResources().getChicken(), 2f, grid_x,
                        grid_y, x, y, 0f, this, spawn_x, spawn_y);
                supplies.add(supply);
                new SupplySpawnAnimation(supply, SPAWN_TIME);
            }
            ((RubberSupplyManager) world.getSupplyManager(RubberSupply.class)).newGroup();
        }
    }

    private int[] getGroupPosition() {
        List<int[]> tree_positions = world.getHeightMap().getTrees();
        // A sparse/barren map (e.g. some custom-generated maps) can have zero trees at all, in
        // which case there's nowhere to spawn a rubber/chicken group - nextInt(0) below would throw
        // IllegalArgumentException("bound must be positive") and crash landscape generation outright.
        // //added by ikill240c 2026-09-12
        if (tree_positions.isEmpty()) //added by ikill240c 2026-09-12
            return null; //added by ikill240c 2026-09-12
        int start_index = world.getRandom().nextInt(tree_positions.size());
        int index = (start_index + 1) % tree_positions.size();
        while (index != start_index) {
            int[] coords = tree_positions.get(index);
            Occupant occ = world.getUnitGrid().getOccupant(coords[0], coords[1]);
            if (occ instanceof TreeSupply)
                return coords;
            index = (index + 1) % tree_positions.size();
        }
        return null;
    }

    public void remove(RubberSupply supply) {
        boolean in_list = supplies.remove(supply);
        assert in_list;
        if (supplies.isEmpty())
            ((RubberSupplyManager) world.getSupplyManager(RubberSupply.class)).emptyGroup();
    }
}
