package com.oddlabs.tt.model.behaviour;

import com.oddlabs.tt.model.Abilities;
import com.oddlabs.tt.model.AttackScanFilter;
import com.oddlabs.tt.model.Selectable;
import com.oddlabs.tt.model.Unit;
import com.oddlabs.tt.util.Target;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Follows a friendly unit indefinitely, matching its position as it moves - the "right click on an
 * ally/own unit to follow them" order used by most RTS games. Mirrors HuntController's never-arrives
 * pattern (that one re-engages a moving ENEMY forever by re-walking toward it whenever it's out of
 * range, then attacking once in range) instead of WalkController, which pops itself off permanently
 * the first time it arrives - which is exactly why a plain move order to a friendly unit's position
 * doesn't keep tracking them as they walk away afterward. //added by ikill240c
 *
 * <p>When aggressive is true (driven by Settings.getSettings().aggressive_units, the same global
 * "aggressive unit command" toggle every other move order already respects - see
 * Picker.pickTarget()), this also scans for and engages nearby enemies while following, resuming
 * the follow afterward - previously this controller never engaged anything regardless of that
 * setting, silently ignoring it. When false, behavior is unchanged from before: follow only, never
 * initiate combat.
 */
public final class FollowController extends Controller {
    private static final float FOLLOW_DISTANCE = 3f; // how close to stay, in grid units //added by ikill240c
    private static final float MIN_RECHECK_DELAY = 0.5f; // seconds between distance re-checks while close enough //added by ikill240c
    private static final float MAX_RECHECK_DELAY = 1f; //added by ikill240c

    private final @NonNull Target target;
    private final @NonNull Unit unit;
    private final boolean aggressive; //added by ikill240c
    private final @Nullable AttackScanFilter scan_filter; //added by ikill240c
    private final @NonNull FollowWaitBehaviour wait_behaviour;
    private float redecide_time;

    public FollowController(@NonNull Unit unit, @NonNull Target target, boolean aggressive) { //added by ikill240c
        super(1);
        this.unit = unit;
        this.target = target;
        this.aggressive = aggressive; //added by ikill240c
        // Only allocated when actually needed - non-aggressive follows (the majority, historically
        // the only case this controller supported) shouldn't pay for a filter they never use.
        // //added by ikill240c
        this.scan_filter = aggressive ? new AttackScanFilter(unit.getOwner(), AttackScanFilter.UNIT_RANGE) : null; //added by ikill240c
        this.wait_behaviour = new FollowWaitBehaviour(this, unit);
    }

    /** Called by FollowWaitBehaviour each frame while close enough to the target. */ //added by ikill240c
    boolean shouldWait(float t) {
        redecide_time -= t;
        return redecide_time > 0;
    }

    @Override
    public void decide() {
        if (target.isDead() || target == unit) {
            unit.popController();
            return;
        }
        if (aggressive && unit.getAbilities().hasAbilities(Abilities.ATTACK)) { //added by ikill240c
            unit.scanVicinity(scan_filter); //added by ikill240c
            Selectable<?> enemy = scan_filter.removeTarget(); //added by ikill240c
            if (enemy != null) { //added by ikill240c
                // Re-push a fresh FollowController underneath the hunt so following resumes
                // against the SAME target afterward, same pattern as GuardController/
                // PatrolController re-pushing themselves under a HuntController.
                // //added by ikill240c
                unit.pushControllers(new FollowController(unit, target, true), //added by ikill240c
                        new HuntController(unit, enemy)); //added by ikill240c
                return; //added by ikill240c
            } //added by ikill240c
        }
        if (unit.isCloseEnough(FOLLOW_DISTANCE, target)) {
            // Close enough - reset the give-up counter (this is success, not failure) and wait a short,
            // randomized delay before re-checking distance again, same cadence pattern IdleController
            // uses for its own periodic re-checks. //added by ikill240c
            resetGiveUpCounter(0);
            unit.setBehaviour(wait_behaviour);
            redecide_time = MIN_RECHECK_DELAY
                    + unit.getOwner().getWorld().getRandom().nextFloat() * (MAX_RECHECK_DELAY - MIN_RECHECK_DELAY);
        } else if (!shouldGiveUp(0)) {
            // Not close enough - walk toward the target's CURRENT position (a live Target reference,
            // not a snapshotted coordinate), stopping at FOLLOW_DISTANCE rather than distance 0.
            // //added by ikill240c
            unit.setBehaviour(new WalkBehaviour(unit, target, FOLLOW_DISTANCE, aggressive)); //added by ikill240c - was hardcoded false regardless of the aggressive flag, so even an aggressive follow never reacted to anything while actively walking toward the target, only at decide()-time (order issued, arrival, blocked)
        } else {
            // Gave up after repeated consecutive failures to get close (e.g. genuinely unreachable).
            // //added by ikill240c
            unit.popController();
        }
    }
}
