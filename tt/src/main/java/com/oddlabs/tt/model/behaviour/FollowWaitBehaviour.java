package com.oddlabs.tt.model.behaviour;

import com.oddlabs.tt.model.Unit;
import org.jspecify.annotations.NonNull;

/**
 * Brief idle wait used by FollowController between distance re-checks while close enough to the
 * followed target. Mirrors IdleBehaviour's wait/DONE pattern (returns INTERRUPTIBLE while waiting,
 * DONE once the wait elapses so decide() runs again), just tied to FollowController instead of
 * IdleController since IdleBehaviour's constructor is hard-tied to that specific controller type.
 * //added by ikill240c
 */
final class FollowWaitBehaviour implements Behaviour {
    private final @NonNull FollowController controller;
    private final @NonNull Unit unit;

    FollowWaitBehaviour(@NonNull FollowController controller, @NonNull Unit unit) {
        this.controller = controller;
        this.unit = unit;
    }

    @Override
    public @NonNull State animate(float t) {
        unit.switchToIdleAnimation();
        return controller.shouldWait(t) ? State.INTERRUPTIBLE : State.DONE;
    }

    @Override
    public boolean isBlocking() {
        return true;
    }

    @Override
    public void forceInterrupted() {
    }
}
