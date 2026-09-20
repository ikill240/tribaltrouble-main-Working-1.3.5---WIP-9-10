package com.oddlabs.tt.model.behaviour;

import com.oddlabs.tt.model.Ship;
import com.oddlabs.tt.model.ShipAllocation;
import com.oddlabs.tt.model.Unit;
import org.jspecify.annotations.NonNull;

/**
 * Merged from boats_on_steam: a unit boarding a ship now walks from the ship's entrance to its
 * final seat over TOTAL_BOARDING_TIME (playing a moving animation throughout) instead of
 * instantly snapping into its seated position/direction the moment this behaviour starts.
 * //added by ikill240c
 */
public final class ShipAttackBehaviour implements Behaviour {
    private final ShipAttackController controller;
    private final Unit unit;
    private final Ship ship;
    private final ShipAllocation allocation;
    private boolean boarded = false; //added by ikill240c
    private float boarding_time = 0.0f; //added by ikill240c
    private static final float TOTAL_BOARDING_TIME = 1.0f; //added by ikill240c

    public ShipAttackBehaviour(
            ShipAttackController controller, Unit unit, Ship ship, ShipAllocation allocation, boolean boarded) { //added by ikill240c
        this.controller = controller;
        this.unit = unit;
        this.ship = ship;
        this.allocation = allocation;
        this.boarded = boarded; //added by ikill240c
    }

    @Override
    public @NonNull State animate(float t) {
        if (unit.isDead()) {
            return State.DONE;
        }
        // A ship that's already moving (e.g. it set sail mid-boarding) skips straight to boarded -
        // there's no sensible "still walking to your seat while the ship is already underway"
        // animation, so this treats it the same as boarding having already finished.
        // //added by ikill240c
        if (ship.isMoving() || boarding_time >= TOTAL_BOARDING_TIME) { //added by ikill240c
            boarded = true; //added by ikill240c
        } //added by ikill240c
        if (!boarded) { //added by ikill240c
            var proxy = ship.getEntrance(); //added by ikill240c
            boarding_time += t; //added by ikill240c
            unit.switchAnimation(3.0f, Unit.Animation.MOVING); //added by ikill240c
            allocation.updateIntermediate(unit, ship, boarding_time / TOTAL_BOARDING_TIME); //added by ikill240c
            return State.INTERRUPTIBLE; //added by ikill240c
        } else { //added by ikill240c
            unit.switchToIdleAnimation();
            allocation.updateFinal(unit, ship); //added by ikill240c
            if (!controller.shouldSleep(t)) return State.DONE;
            else return State.INTERRUPTIBLE;
        } //added by ikill240c
    }

    public final boolean isBlocking() {
        return true;
    }

    public final void forceInterrupted() {
    }
}
