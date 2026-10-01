package com.oddlabs.tt.model;

import com.oddlabs.tt.pathfinder.FinderFilter;
import com.oddlabs.tt.pathfinder.Movable; //added by ikill240c
import com.oddlabs.tt.pathfinder.Occupant;
import com.oddlabs.tt.pathfinder.Region;
import com.oddlabs.tt.pathfinder.RegionBuilder;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class SupplyFinder<S extends Supply> implements FinderFilter<S> {
    private final @NonNull Unit unit;
    private final @NonNull Class<S> supply_class;
    private final List<@NonNull List<@NonNull S>> region_list = new ArrayList<>();
    private int max_region_dist_sqr;

    public SupplyFinder(@NonNull Unit unit, @NonNull Class<S> supply_class) {
        this.unit = unit;
        this.supply_class = supply_class;
    }

    @Override
    public @Nullable S getOccupantFromRegion(@NonNull Region region, boolean one_region) {
        List<S> supplies = region.getObjects(supply_class);
        if (one_region) {
            if (!supplies.isEmpty()) {
                S supply = findClosest(supplies);
                assert !supply.isEmpty();
                return supply;
            }
        } else {
            int dx = region.getGridX() - unit.getGridX();
            int dy = region.getGridY() - unit.getGridY();
            int region_dist_sqr = dx * dx + dy * dy;
            if (!supplies.isEmpty()) {
                if (region_list.isEmpty()) {
                    int region_dist = (int) Math.sqrt(region_dist_sqr);
                    int max_region_dist = region_dist + RegionBuilder.REGION_PATH_MAX_COST / 2;
                    max_region_dist_sqr = max_region_dist * max_region_dist;
                }
                region_list.add(supplies);
            }
            if (!region_list.isEmpty() && region_dist_sqr > max_region_dist_sqr) {
                S supply = findClosest();
                assert !supply.isEmpty();
                return supply;
            }
        }
        return null;
    }

    @Override
    public S getBest() {
        return findClosest();
    }

    private @Nullable S findClosest(@NonNull List<S> supplies) {
        return supplies.stream().min(Comparator.comparingInt(this::distanceSquared)).orElse(null);
    }

    private @Nullable S findClosest() {
        S closest = region_list.stream().flatMap(List::stream).min(Comparator.comparingInt(
                this::distanceSquared)).orElse(null);
        region_list.clear();
        return closest;
    }

    private int distanceSquared(@NonNull S supply) {
        int dx = supply.getGridX() - unit.getGridX();
        int dy = supply.getGridY() - unit.getGridY();
        return dx * dx + dy * dy;
    }

    // Maximum units allowed to actively gather from the same specific resource node at once. Beyond
    // this, acceptOccupant() rejects the candidate so the underlying nearest-search continues on to the
    // next-closest node of the same type automatically - no changes needed to the search/pathfinding
    // machinery itself, this just makes an over-full node invisible to it.
    //
    // Was 9 (visually indistinguishable from no cap at all), then lowered to 4 (too tight per
    // follow-up feedback) - 7 is the settled middle ground. //added by ikill240c
    private static final int MAX_GATHERERS_PER_SUPPLY = 7; //added by ikill240c

    // Extracted so HarvestController can apply the exact same crowding rule as a final check right
    // before a unit actually commits to harvesting - see that class for why this is needed in
    // addition to acceptOccupant() below, not instead of it: acceptRegion() (in
    // FinderTrackerAlgorithm) only ever calls getOccupantFromRegion(), which has no crowding check
    // of its own, to decide which REGIONS are worth searching at all. A region whose only supply is
    // already full still passes that check, since "is there a supply here" and "is there an
    // AVAILABLE supply here" are different questions - acceptOccupant() (used for the finer,
    // grid-level search once a region's been picked) then can't offer any alternative within that
    // region, so nothing stops the hinted, over-full node from still being the one a unit ends up
    // walking to and harvesting from. //added by ikill240c
    // Was: crowded once MAX_GATHERERS_PER_SUPPLY gatherers were working anywhere nearby, whatever the resource's actual
    // room. A resource with one free side was "uncrowded" until 7 peons were on it, so extra peons queued behind the
    // one that fit instead of going elsewhere, and one nobody can stand next to was never "crowded" at all, so peons
    // kept walking to it. Now: crowded once the gatherers standing next to it fill its free harvesting spots (capped at
    // MAX_GATHERERS_PER_SUPPLY as before), and always crowded if it has none. A peon already standing at the resource
    // is never crowded out - otherwise two waiting peons could each see the other and both leave. //added by ikill240c
    public static boolean isCrowded(@NonNull Unit unit, @NonNull Supply supply) { //added by ikill240c
        if (unit.isCloseEnough(0f, supply)) //added by ikill240c
            return false; //added by ikill240c - already holds a spot
        int capacity = Math.min(MAX_GATHERERS_PER_SUPPLY, harvestSpots(unit.getUnitGrid(), supply)); //added by ikill240c
        if (capacity == 0) //added by ikill240c
            return true; //added by ikill240c - nowhere to stand: unreachable
        float supply_x = com.oddlabs.tt.pathfinder.UnitGrid.coordinateFromGrid(supply.getGridX()); //added by ikill240c
        float supply_y = com.oddlabs.tt.pathfinder.UnitGrid.coordinateFromGrid(supply.getGridY()); //added by ikill240c
        var nearby = new com.oddlabs.tt.pathfinder.FindOccupantFilter<>(supply_x, supply_y, 4f, null, Unit.class); //added by ikill240c
        unit.getUnitGrid().scan(nearby, supply.getGridX(), supply.getGridY()); //added by ikill240c
        int at_supply = 0; //added by ikill240c
        for (Unit u : nearby.getResult()) { //added by ikill240c
            if (!u.isDead() && u != unit && u.isCloseEnough(0f, supply) //added by ikill240c
                    && u.getCurrentController() instanceof com.oddlabs.tt.model.behaviour.HarvestController<?> hc //added by ikill240c
                    && hc.getSupply() == supply && ++at_supply >= capacity) //added by ikill240c
                return true; //added by ikill240c
        } //added by ikill240c
        return false; //added by ikill240c
    } //added by ikill240c

    // Cells around a resource a gatherer could stand on to harvest it: its 8 neighbours that are empty or hold a unit
    // (units move). Trees, buildings, other resources and impassable terrain (RegionBuilder's static "unreachable"
    // occupant) don't count. A resource that isn't a single grid cell isn't limited. //added by ikill240c
    static int harvestSpots(com.oddlabs.tt.pathfinder.@NonNull UnitGrid grid, @NonNull Supply supply) { //added by ikill240c
        int gx = supply.getGridX(); //added by ikill240c
        int gy = supply.getGridY(); //added by ikill240c
        if (grid.getOccupant(gx, gy) != supply) //added by ikill240c
            return MAX_GATHERERS_PER_SUPPLY; //added by ikill240c
        int size = grid.getGridSize(); //added by ikill240c
        int spots = 0; //added by ikill240c
        for (int dy = -1; dy <= 1; dy++) //added by ikill240c
            for (int dx = -1; dx <= 1; dx++) { //added by ikill240c
                int x = gx + dx; //added by ikill240c
                int y = gy + dy; //added by ikill240c
                if ((dx == 0 && dy == 0) || x < 0 || y < 0 || x >= size || y >= size) //added by ikill240c
                    continue; //added by ikill240c
                Occupant occ = grid.getOccupant(x, y); //added by ikill240c
                if (occ == null || occ instanceof Movable) //added by ikill240c
                    spots++; //added by ikill240c
            } //added by ikill240c
        return spots; //added by ikill240c
    } //added by ikill240c

    @Override
    public boolean acceptOccupant(@NonNull Occupant occ) {
        if (supply_class.isInstance(occ)) {
            Supply supply = (Supply) occ;
            assert !supply.isEmpty();
            if (supply == unit.getAvoidedSupply()) //added by ikill240c
                return false; //added by ikill240c - this peon just got stuck on it
            return !isCrowded(unit, supply); //added by ikill240c - now delegates to the shared check instead of duplicating it
        } else
            return false;
    }
}
