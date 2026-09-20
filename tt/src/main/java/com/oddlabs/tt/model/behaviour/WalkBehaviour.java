package com.oddlabs.tt.model.behaviour;

import com.oddlabs.tt.gui.ToolTipBox;
import com.oddlabs.tt.model.AttackScanFilter;
import com.oddlabs.tt.model.Selectable;
import com.oddlabs.tt.model.Unit;
import com.oddlabs.tt.pathfinder.Movable;
import com.oddlabs.tt.pathfinder.Occupant;
import com.oddlabs.tt.pathfinder.PathTracker;
import com.oddlabs.tt.pathfinder.TargetTrackerAlgorithm;
import com.oddlabs.tt.pathfinder.TrackerAlgorithm;
import com.oddlabs.tt.util.Target;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public final class WalkBehaviour implements Behaviour {
    private static final float WAIT_RETRY_DELAY = 1f / 2f;
    private static final float MAX_WAIT_RETRY_DELAY = 5f;

    // Lets a caller customize what happens when scan() finds an enemy mid-walk, instead of always
    // pushing the plain, unleashed HuntController - GuardController needs this so a chase
    // triggered by this scan still respects its leash (see LeashedHuntController), rather than
    // potentially being pulled arbitrarily far from wherever it's actually supposed to be
    // watching. Defaults to the original HuntController::new below for every other caller, so
    // this is purely additive - nothing about existing behavior changes unless a caller opts in.
    // //added by ikill240c
    public interface HuntControllerFactory { //added by ikill240c
        @NonNull Controller create(@NonNull Unit unit, @NonNull Selectable<?> target); //added by ikill240c
    }

    private final @NonNull Unit unit;
    private final @NonNull TrackerAlgorithm tracker_algorithm;
    private final @NonNull AttackScanFilter scan_filter;
    private final boolean scan_attack;
    private final @NonNull HuntControllerFactory hunt_controller_factory; //added by ikill240c

    private @Nullable Movable blocking_movable;
    private int blocker_x;
    private int blocker_y;
    private float retry_delay_counter;
    private float retry_delay;

    private PathTracker.State state;

    public WalkBehaviour(@NonNull Unit unit, @NonNull TrackerAlgorithm tracker_algorithm, boolean scan_attack) {
        this(unit, tracker_algorithm, scan_attack, HuntController::new); //added by ikill240c
    }

    // Overload taking an explicit HuntControllerFactory - see that interface's own comment for why
    // a caller would want one. //added by ikill240c
    public WalkBehaviour(@NonNull Unit unit, @NonNull TrackerAlgorithm tracker_algorithm, boolean scan_attack, //added by ikill240c
            @NonNull HuntControllerFactory hunt_controller_factory) {
        this.unit = unit;
        this.tracker_algorithm = tracker_algorithm;
        this.scan_attack = scan_attack;
        this.hunt_controller_factory = hunt_controller_factory; //added by ikill240c
        scan_filter = new AttackScanFilter(unit.getOwner(), AttackScanFilter.UNIT_RANGE);
        retry_delay = WAIT_RETRY_DELAY;
        unit.getTracker().setTarget(tracker_algorithm);
    }

    public WalkBehaviour(@NonNull Unit unit, @NonNull Target t, float range, boolean scan_attack) {
        this(unit, new TargetTrackerAlgorithm(unit.getUnitGrid(), range, t), scan_attack);
    }

    // Overload taking an explicit HuntControllerFactory - see that interface's own comment for why
    // a caller would want one. //added by ikill240c
    public WalkBehaviour(@NonNull Unit unit, @NonNull Target t, float range, boolean scan_attack, //added by ikill240c
            @NonNull HuntControllerFactory hunt_controller_factory) {
        this(unit, new TargetTrackerAlgorithm(unit.getUnitGrid(), range, t), scan_attack, hunt_controller_factory); //added by ikill240c
    }

    @Override
    public boolean isBlocking() {
        return state == PathTracker.State.BLOCKED;
    }

    public void appendToolTip(@NonNull ToolTipBox tool_tip_box) {
        tool_tip_box.append("WalkBehaviour: state=");
        tool_tip_box.append(state.toString());
        tool_tip_box.append(" | retry_delay=");
        tool_tip_box.append((long) retry_delay);
        tool_tip_box.append("(");
        tool_tip_box.append((long) retry_delay);
        tool_tip_box.append("s)");
        unit.getTracker().appendToolTip(tool_tip_box);
    }

    private void switchToMoving() {
        unit.switchAnimation(unit.getMetersPerSecond(), Unit.Animation.MOVING);
    }

    @Override
    public @NonNull State animate(float t) {
        retry_delay_counter -= t;
        boolean blocker_moved = blocking_movable != null && (blocking_movable.getGridX() != blocker_x
                || blocking_movable.getGridY() != blocker_y);
        if (retry_delay_counter > 0 && !blocker_moved) {
            return State.INTERRUPTIBLE;
        }
        retry_delay_counter = 0;
        blocking_movable = null;
        PathTracker tracker = unit.getTracker();
        state = tracker.animate(unit.getMetersPerSecond() * t);
        return switch (state) {
            case OK -> {
                switchToMoving();
                yield State.UNINTERRUPTIBLE;
            }
            case OK_INTERRUPTIBLE -> {
                retry_delay = WAIT_RETRY_DELAY;
                switchToMoving();
                scan();
                yield State.INTERRUPTIBLE;
            }
            case DONE -> State.DONE;
            case BLOCKED, SOFTBLOCKED -> {
                Occupant blocker = tracker.getBlocker();
                if (blocker instanceof Movable movable) {
                    blocking_movable = movable;
                    if (!blocking_movable.isDead()) {
                        blocking_movable.markBlocking();
                        blocker_x = blocking_movable.getGridX();
                        blocker_y = blocking_movable.getGridY();
                    } else {
                        blocking_movable = null;
                    }
                }
                scan();
                retry_delay = Math.min(2 * retry_delay, MAX_WAIT_RETRY_DELAY);
                yield doRetry();
            }
        };
    }

    private void scan() {
        if (scan_attack) {
            unit.scanVicinity(scan_filter);
            Selectable<?> s = scan_filter.removeTarget();
            if (s != null) {
                unit.getCurrentController().resetGiveUpCounters();
                unit.pushController(hunt_controller_factory.create(unit, s)); //added by ikill240c - was hardcoded new HuntController(unit, s); see HuntControllerFactory's own comment
            }
        }
    }

    /*	public final void moveNextAnimate() {
            retry_delay_counter = 0f;
        }
    */
    private @NonNull State doRetry() {
        retry_delay_counter = retry_delay;
        unit.switchToIdleAnimation();
        return State.INTERRUPTIBLE;
    }

    @Override
    public void forceInterrupted() {
    }
}
