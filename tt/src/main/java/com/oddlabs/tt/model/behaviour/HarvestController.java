package com.oddlabs.tt.model.behaviour;

import com.oddlabs.tt.model.Supply;
import com.oddlabs.tt.model.SupplyFinder;
import com.oddlabs.tt.model.Unit;
import com.oddlabs.tt.pathfinder.FinderTrackerAlgorithm;
import org.jspecify.annotations.Nullable;

public final class HarvestController<S extends Supply> extends Controller {
    private final Unit unit;
    private final Class<S> supply_class;
    private FinderTrackerAlgorithm<S> tracker;

    private @Nullable Supply supply;

    public HarvestController(Unit unit, S supply, Class<S> supply_class) {
        super(1);
        this.unit = unit;
        this.supply = supply;
        this.supply_class = supply_class;
    }

    public @Nullable Supply getSupply() { //added by ikill240c - needed so SupplyFinder can count current harvesters of a candidate supply for the per-resource gatherer cap
        return supply;
    }

    private void gather() {
        // Re-checks crowding here as a final, guaranteed gate right before actually starting to
        // harvest - see SupplyFinder.isCrowded()'s own comment for why the search/pathfinding path
        // alone (acceptRegion() -> getOccupantFromRegion(), which has no crowding awareness) isn't
        // enough by itself to guarantee an over-full node is never the one a unit commits to. If
        // this specific supply became crowded (e.g. other units arrived after this one was already
        // tracking it, or the pathfinder's region-level hint pointed here despite it being full),
        // clear it and fall through to the search branch below to find a genuinely available node
        // instead - not a wasted tick, since a real search happens on the very next decide() either
        // way. //added by ikill240c
        if (supply != null && SupplyFinder.isCrowded(unit, supply)) { //added by ikill240c
            supply = null; //added by ikill240c
        } //added by ikill240c
        if (supply != null && !supply.isEmpty() && unit.isCloseEnough(0f, supply)) {
            resetGiveUpCounter(0);
            unit.setBehaviour(new HarvestBehaviour(unit, supply));
        } else if (!shouldGiveUp(0)) {
            tracker = new FinderTrackerAlgorithm<>(unit.getUnitGrid(), new SupplyFinder<>(unit, supply_class));
            unit.setBehaviour(new WalkBehaviour(unit, tracker, false));
        } else {
            unit.popController();
        }
    }

    @Override
    public void decide() {
        if (unit.getSupplyContainer().getSupplyType() == supply_class && unit.getSupplyContainer().isSupplyFull()) {
            unit.popController();
        } else {
            if (tracker != null) {
                supply = tracker.getOccupant();
            }
            gather();
        }
    }
}
