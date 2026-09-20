package com.oddlabs.tt.model.behaviour;

import com.oddlabs.tt.model.Selectable;
import com.oddlabs.tt.model.Unit;
import com.oddlabs.tt.util.Target;
import org.jspecify.annotations.NonNull;

/**
 * Like HuntController, but also gives up - popping back to whatever pushed it, rather than
 * continuing to chase - once the target strays more than max_leash_distance from a fixed anchor
 * point. Used by WalkBehaviour's own mid-walk enemy scan (see its hunt_controller_factory field)
 * for controllers like GuardController that need chases triggered by that scan to still respect a
 * leash, rather than the plain HuntController WalkBehaviour otherwise always pushes, which has no
 * leash concept at all and would happily chase a fleeing target indefinitely far from wherever the
 * unit was actually supposed to be watching. //added by ikill240c
 */
public final class LeashedHuntController extends Controller { //added by ikill240c
    private final @NonNull Unit unit; //added by ikill240c
    private final @NonNull Selectable<?> target; //added by ikill240c
    private final @NonNull Target anchor; //added by ikill240c
    private final float max_leash_distance; //added by ikill240c

    public LeashedHuntController(@NonNull Unit unit, @NonNull Selectable<?> target, @NonNull Target anchor, //added by ikill240c
            float max_leash_distance) {
        super(1);
        this.unit = unit;
        this.target = target;
        this.anchor = anchor;
        this.max_leash_distance = max_leash_distance;
    }

    private boolean canAttack() { //added by ikill240c
        return unit.isCloseEnough(unit.getRange(target), target); //added by ikill240c
    }

    private boolean withinLeash() { //added by ikill240c
        float dx = target.getPositionX() - anchor.getPositionX(); //added by ikill240c
        float dy = target.getPositionY() - anchor.getPositionY(); //added by ikill240c
        return dx * dx + dy * dy <= max_leash_distance * max_leash_distance; //added by ikill240c
    }

    @Override
    public void decide() {
        if (target.isDead() || !withinLeash()) { //added by ikill240c
            unit.popController();
        } else if (canAttack()) {
            unit.setBehaviour(new AttackBehaviour(unit, target));
            resetGiveUpCounter(0);
        } else if (!shouldGiveUp(0)) {
            unit.setBehaviour(new WalkBehaviour(unit, target, unit.getRange(target), false));
        } else
            unit.popController();
    }
}
