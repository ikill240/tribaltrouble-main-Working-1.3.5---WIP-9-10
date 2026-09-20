package com.oddlabs.tt.model.behaviour;

import com.oddlabs.tt.landscape.LandscapeTarget;
import com.oddlabs.tt.model.Abilities;
import com.oddlabs.tt.model.AttackScanFilter;
import com.oddlabs.tt.model.Selectable;
import com.oddlabs.tt.model.Unit;
import com.oddlabs.tt.util.Target;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * "Guard": stays near a specific point or target, always attacking any enemy that comes within
 * MAX_LEASH_DISTANCE of the guard point, and wandering within a small radius of it while nothing
 * needs attention rather than standing perfectly still.
 *
 * <p>Always aggressive - unlike Patrol/Follow, which respect Settings.aggressive_units (a normal
 * move order may or may not want to stop and fight along the way), guarding a spot is inherently
 * a defensive/watch order: there is no meaningful "non-aggressive guard", so this doesn't gate
 * engagement behind that setting at all.
 *
 * <p>Manages AttackBehaviour/WalkBehaviour directly rather than delegating to HuntController (the
 * way PatrolController does) because the leash needs to be re-checked against the ORIGINAL
 * guard_target every tick, including for a target that's already being chased - HuntController
 * has no concept of "give up because a leash was exceeded", only "give up because the target died
 * or fled out of scan range entirely", which would let a fleeing enemy pull a guard arbitrarily
 * far from what it's supposed to be watching.
 *
 * <p>Every WalkBehaviour this controller issues (returning to guard_target, wandering, closing on
 * current_enemy) passes scan_attack=true, so WalkBehaviour's own built-in scan can react to a
 * DIFFERENT enemy encountered mid-walk rather than the unit only ever noticing what's in front of
 * it at the exact moments decide() happens to run (order issued, arrival, blocked) - previously
 * false everywhere, which meant a unit could walk right past an enemy without engaging it at all
 * unless that enemy happened to still be there when the walk finished.
 *
 * <p>Also passes a custom HuntControllerFactory (see WalkBehaviour) that creates a
 * LeashedHuntController instead of WalkBehaviour's own default, unleashed HuntController - a
 * mid-walk detour onto a different enemy previously ran through the ordinary HuntController
 * regardless of what pushed it, which has no leash concept at all and could pull the guard
 * arbitrarily far past MAX_LEASH_DISTANCE chasing a fleeing target before ever handing control
 * back to this controller's own leash check. The custom factory means even THAT chase gives up
 * the moment it exceeds the leash, rather than only being caught after the fact.
 * //added by ikill240c
 *
 * <p>If guard_target is itself a moving Selectable (e.g. an allied unit, not a fixed ground
 * point), Target's own position accessors naturally track its current position each tick - a
 * guarded ally that moves is followed (and the wander/leash circle moves with them), not left
 * behind at a stale coordinate. If the guarded target is itself destroyed (checked only when it's
 * a Selectable; a plain ground-point Target has nothing to die), there's nothing left to anchor to
 * and the order ends.
 */
public final class GuardController extends Controller {
    // How far the unit can drift from the guard point (e.g. while chasing an enemy that fled just
    // out of attack range) before "return to guard point" kicks back in, for the walking-back
    // case specifically (distinct from MAX_LEASH_DISTANCE, which governs whether an engagement is
    // allowed to continue at all - RETURN_THRESHOLD only matters once nothing is being chased).
    // //added by ikill240c
    private static final float RETURN_THRESHOLD = 3f; //added by ikill240c
    // Hard cap on how far from the guard point this unit will travel to fight - once an engaged
    // enemy (or the unit's own position while chasing) would exceed this, the engagement is
    // abandoned and the unit returns, rather than being pulled indefinitely far from the area it's
    // meant to be watching. //added by ikill240c
    private static final float MAX_LEASH_DISTANCE = 10f; //added by ikill240c
    // Radius the unit wanders within (walks to a random nearby point, arrives, picks another) when
    // not engaging anything - deliberately smaller than the leash so wandering itself can never
    // risk exceeding it. Replaces simply standing motionless (NullBehaviour), which looked like
    // the unit was stuck "running in place" with no visible reason it had stopped moving.
    // //added by ikill240c
    private static final float WANDER_RADIUS = 6f; //added by ikill240c
    // Never let the wander radius round down to 0 - see the wander-point computation below for
    // why a degenerate (0-radius) point crashes the game outright rather than just looking static.
    // //added by ikill240c
    private static final float MIN_WANDER_RADIUS = 2f; //added by ikill240c
    private static final float WANDER_ARRIVE_THRESHOLD = 1f; //added by ikill240c

    private final @NonNull Unit unit;
    private final @NonNull Target guard_target;
    private final @NonNull AttackScanFilter scan_filter;
    // The enemy currently being engaged, if any - tracked across ticks (rather than re-deciding
    // fresh from scratch every time) specifically so the leash can be re-checked against the SAME
    // enemy as it potentially moves, not just at the moment it was first spotted. //added by ikill240c
    private @Nullable Selectable<?> current_enemy; //added by ikill240c
    private @Nullable Target wander_point; //added by ikill240c
    // Supplied to every WalkBehaviour this controller issues - see the class comment for why a
    // mid-walk detour needs to respect the leash too, not just this controller's own decide()-time
    // checks. Assigned in the constructor body (not as a field initializer) because field
    // initializers run before the constructor body, and this lambda captures guard_target - which
    // is only assigned via the constructor parameter a few lines below, not its own field
    // initializer, so javac's definite-assignment check correctly flagged it as possibly
    // unassigned at the point this would otherwise run. //added by ikill240c
    private final WalkBehaviour.@NonNull HuntControllerFactory leashed_hunt_factory; //added by ikill240c

    public GuardController(@NonNull Unit unit, @NonNull Target guard_target) {
        super(1);
        this.unit = unit;
        this.guard_target = guard_target;
        this.scan_filter = new AttackScanFilter(unit.getOwner(), AttackScanFilter.UNIT_RANGE);
        this.leashed_hunt_factory = (u, target) -> new LeashedHuntController(u, target, this.guard_target, MAX_LEASH_DISTANCE); //added by ikill240c
    }

    private boolean withinLeash(@NonNull Target t) { //added by ikill240c
        float dx = t.getPositionX() - guard_target.getPositionX(); //added by ikill240c
        float dy = t.getPositionY() - guard_target.getPositionY(); //added by ikill240c
        return dx * dx + dy * dy <= MAX_LEASH_DISTANCE * MAX_LEASH_DISTANCE; //added by ikill240c
    }

    @Override
    public void decide() {
        if (guard_target instanceof Selectable<?> guarded && guarded.isDead()) {
            unit.popController();
            return;
        }

        if (current_enemy != null && (current_enemy.isDead() || !withinLeash(current_enemy))) { //added by ikill240c
            current_enemy = null; // dead, or has fled beyond the leash - stop chasing either way //added by ikill240c
        }
        if (current_enemy == null && unit.getAbilities().hasAbilities(Abilities.ATTACK)) { //added by ikill240c
            unit.scanVicinity(scan_filter);
            Selectable<?> found = scan_filter.removeTarget();
            if (found != null && withinLeash(found)) { //added by ikill240c
                current_enemy = found; //added by ikill240c
            } //added by ikill240c
        }

        if (current_enemy != null) { //added by ikill240c
            if (unit.isCloseEnough(unit.getRange(current_enemy), current_enemy)) { //added by ikill240c
                unit.setBehaviour(new AttackBehaviour(unit, current_enemy)); //added by ikill240c
            } else { //added by ikill240c
                unit.setBehaviour(new WalkBehaviour(unit, current_enemy, unit.getRange(current_enemy), true, leashed_hunt_factory)); //added by ikill240c - scan_attack=true so the unit reacts to other enemies encountered en route, not just the one it's already engaging; leashed_hunt_factory so that reaction still respects the leash
            } //added by ikill240c
            return;
        }

        if (!unit.isCloseEnough(RETURN_THRESHOLD, guard_target)) {
            wander_point = null; // stale - re-picked once actually back near the guard point //added by ikill240c
            unit.setBehaviour(new WalkBehaviour(unit, guard_target, 0f, true, leashed_hunt_factory)); //added by ikill240c - was false, then true without a leashed factory; see class comment
            return;
        }

        // Wandering: only picks a new point once the current one is reached (or there isn't one
        // yet), rather than on a fixed timer - decide() itself is only re-invoked when the current
        // WalkBehaviour needs re-evaluating (arrived, blocked, etc.), so this naturally paces the
        // wander without needing its own clock. //added by ikill240c
        if (wander_point == null || unit.isCloseEnough(WANDER_ARRIVE_THRESHOLD, wander_point)) { //added by ikill240c
            var random = unit.getOwner().getWorld().getRandom(); // deterministic RNG - required for multiplayer lockstep, same source IdleController etc. already use //added by ikill240c
            float angle = random.nextFloat() * (float) (Math.PI * 2); //added by ikill240c
            // Enforces a minimum radius (never 0) - a near-zero radius rounds wx/wy to
            // guard_target's own grid cell, which the unit is already standing on/near at this
            // point in decide() (this branch only runs once isCloseEnough(RETURN_THRESHOLD,
            // guard_target) is true). Issuing a WalkBehaviour to the unit's own current grid cell
            // makes it its own "next occupant", which PathTracker.getNextOccupant() asserts can
            // never happen - this was crashing the game outright (AssertionError, e.g.
            // "49 193 49 193" - the same coordinates twice) rather than just looking wrong.
            // //added by ikill240c
            float radius = MIN_WANDER_RADIUS + random.nextFloat() * (WANDER_RADIUS - MIN_WANDER_RADIUS); //added by ikill240c
            int wx = guard_target.getGridX() + Math.round((float) Math.cos(angle) * radius); //added by ikill240c
            int wy = guard_target.getGridY() + Math.round((float) Math.sin(angle) * radius); //added by ikill240c
            // Belt-and-suspenders on top of the minimum radius above: if rounding still somehow
            // landed exactly on the unit's current cell (e.g. the unit sitting well off-center
            // within RETURN_THRESHOLD of guard_target, at a point the rounded offset happens to
            // reach), nudge one grid step over rather than risk the same crash on a coordinate
            // combination the minimum radius alone didn't anticipate. //added by ikill240c
            if (wx == unit.getGridX() && wy == unit.getGridY()) { //added by ikill240c
                wx += 1; //added by ikill240c
            } //added by ikill240c
            wander_point = new LandscapeTarget(wx, wy); //added by ikill240c
        }
        unit.setBehaviour(new WalkBehaviour(unit, wander_point, 0f, true, leashed_hunt_factory)); //added by ikill240c - was false, then true without a leashed factory; see class comment
    }
}
