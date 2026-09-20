package com.oddlabs.tt.model.behaviour;

import com.oddlabs.tt.model.Abilities;
import com.oddlabs.tt.model.AttackScanFilter;
import com.oddlabs.tt.model.Selectable;
import com.oddlabs.tt.model.Unit;
import com.oddlabs.tt.util.Target;
import org.jspecify.annotations.NonNull;

/**
 * "Patrol": walks back and forth between two points, always attacking any enemy encountered along
 * the way, then resuming the patrol route (toward whichever waypoint it was already heading to)
 * afterward rather than restarting from point_a every time.
 *
 * <p>Always aggressive - previously gated behind Settings.aggressive_units (the same global
 * toggle a plain move order respects), which defaults to false, meaning patrol units silently
 * never engaged anything by default. Unlike a plain move order, a patrol route is inherently a
 * standing watch order: there's no meaningful "non-aggressive patrol" the way there's a meaningful
 * "just walk there without stopping to fight" move order, so this no longer depends on that
 * setting at all. //added by ikill240c
 *
 * <p>Shares the same scan-and-hunt-then-resume shape as GuardController, just with a moving
 * "current destination" (alternating between point_a/point_b) instead of a single fixed guard
 * point. Deliberately does not check whether point_a/point_b are themselves Selectable objects
 * that could die - a patrol route is defined by two ground locations, not by escorting a specific
 * unit or building (that's what GuardController's Target-can-be-a-moving-Selectable case is for).
 */
public final class PatrolController extends Controller {
    private static final float WAYPOINT_THRESHOLD = 3f; //added by ikill240c

    private final @NonNull Unit unit;
    private final @NonNull Target point_a;
    private final @NonNull Target point_b;
    private final @NonNull AttackScanFilter scan_filter; //added by ikill240c
    // Which waypoint the unit is currently walking toward - preserved across the
    // pushControllers(...) re-push below so resuming a patrol after a fight continues in the same
    // direction it was already heading, rather than snapping back to point_a every time.
    // //added by ikill240c
    private boolean heading_to_b; //added by ikill240c

    public PatrolController(@NonNull Unit unit, @NonNull Target point_a, @NonNull Target point_b) {
        this(unit, point_a, point_b, true);
    }

    private PatrolController(@NonNull Unit unit, @NonNull Target point_a, @NonNull Target point_b, //added by ikill240c
            boolean heading_to_b) {
        super(1);
        this.unit = unit;
        this.point_a = point_a;
        this.point_b = point_b;
        this.scan_filter = new AttackScanFilter(unit.getOwner(), AttackScanFilter.UNIT_RANGE);
        this.heading_to_b = heading_to_b;
    }

    @Override
    public void decide() {
        if (unit.getAbilities().hasAbilities(Abilities.ATTACK)) {
            unit.scanVicinity(scan_filter);
            Selectable<?> enemy = scan_filter.removeTarget();
            if (enemy != null) {
                unit.pushControllers(new PatrolController(unit, point_a, point_b, heading_to_b), //added by ikill240c
                        new HuntController(unit, enemy));
                return;
            }
        }
        Target current_waypoint = heading_to_b ? point_b : point_a;
        if (unit.isCloseEnough(WAYPOINT_THRESHOLD, current_waypoint)) {
            heading_to_b = !heading_to_b;
            current_waypoint = heading_to_b ? point_b : point_a;
        }
        unit.setBehaviour(new WalkBehaviour(unit, current_waypoint, 0f, true)); //added by ikill240c - was false, meaning the class's own "always attacking any enemy encountered along the way" doc comment above wasn't actually true: WalkBehaviour's own scan_attack flag (separate from this controller's own decide()-time scan) gates whether it reacts to anything mid-walk at all, and every call here passed false
    }
}
