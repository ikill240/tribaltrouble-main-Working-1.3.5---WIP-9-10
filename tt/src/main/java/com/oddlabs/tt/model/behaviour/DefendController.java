package com.oddlabs.tt.model.behaviour;

import com.oddlabs.tt.model.Unit;
import com.oddlabs.tt.util.Target;

public final class DefendController extends Controller {
    private final Unit unit;
    private final Target target;

    public DefendController(Unit unit, Target t) {
        super(1);
        this.unit = unit;
        this.target = t;
    }

    @Override
    public void decide() {
        if (shouldGiveUp(0))
            unit.popController();
        else
            // Was range=0f - walks all the way to the exact target location rather than stopping
            // near it. If the threatened target is itself a building (which it often is - this is
            // "defend the thing under attack", and that thing is frequently an ally's building),
            // range=0f sends the defender to walk right up onto the building's own grid cell.
            // Combined with Unit.canEnter()'s ally relaxation for towers/quarters/armory, arriving
            // there can trigger entering the building outright instead of standing guard near it -
            // a defender should protect the area and whatever's in it, not garrison inside
            // whatever's being attacked. DEFEND_STANDOFF_RANGE keeps it a short distance away,
            // close enough to fight anything nearby but not walking into the building itself.
            // //added by ikill240c
            unit.setBehaviour(new WalkBehaviour(unit, target, DEFEND_STANDOFF_RANGE, true)); //added by ikill240c
    }

    // Matches GuardController's own RETURN_THRESHOLD - close enough to actually engage anything
    // near the defended point/building, without walking onto (and potentially into) it.
    // //added by ikill240c
    private static final float DEFEND_STANDOFF_RANGE = 3f; //added by ikill240c
}
