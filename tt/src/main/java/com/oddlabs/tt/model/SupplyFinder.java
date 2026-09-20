package com.oddlabs.tt.model;

import com.oddlabs.tt.pathfinder.FinderFilter;
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
    public static boolean isCrowded(@NonNull Unit unit, @NonNull Supply supply) { //added by ikill240c
        // Small, spatially-bounded scan (not a full-world/full-player iteration) to count units
        // already actively harvesting THIS specific supply instance, so this check stays cheap
        // regardless of total unit count on the map. Uses grid coordinates (guaranteed by Occupant)
        // converted to world position, rather than assuming Supply implements Target.
        // //added by ikill240c
        float supply_x = com.oddlabs.tt.pathfinder.UnitGrid.coordinateFromGrid(supply.getGridX()); //added by ikill240c
        float supply_y = com.oddlabs.tt.pathfinder.UnitGrid.coordinateFromGrid(supply.getGridY()); //added by ikill240c
        var nearby = new com.oddlabs.tt.pathfinder.FindOccupantFilter<>(supply_x, supply_y, 4f, null, Unit.class); //added by ikill240c
        unit.getUnitGrid().scan(nearby, supply.getGridX(), supply.getGridY()); //added by ikill240c
        int gatherer_count = 0; //added by ikill240c
        for (Unit u : nearby.getResult()) { //added by ikill240c
            // Counts every player's units toward this same cap, not just the same owner - a
            // shared/contested resource node can visually pile up units from several different
            // players at once, so the crowding limit needs to reflect everyone actually standing
            // there, not just each player's own separate sub-count of it. //added by ikill240c
            if (!u.isDead() && u != unit //added by ikill240c
                    && u.getCurrentController() instanceof //added by ikill240c - excludes the unit itself: it may already be standing at/near this exact supply while deciding whether to keep gathering from it, and shouldn't count against its own slot
                    com.oddlabs.tt.model.behaviour.HarvestController<?> hc && hc.getSupply() == supply) { //added by ikill240c
                gatherer_count++; //added by ikill240c
                if (gatherer_count >= MAX_GATHERERS_PER_SUPPLY) //added by ikill240c
                    return true; //added by ikill240c
            } //added by ikill240c
        } //added by ikill240c
        return false; //added by ikill240c
    }

    @Override
    public boolean acceptOccupant(@NonNull Occupant occ) {
        if (supply_class.isInstance(occ)) {
            Supply supply = (Supply) occ;
            assert !supply.isEmpty();
            return !isCrowded(unit, supply); //added by ikill240c - now delegates to the shared check instead of duplicating it
        } else
            return false;
    }
}
