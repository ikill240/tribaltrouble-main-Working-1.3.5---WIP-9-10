package com.oddlabs.tt.model.behaviour;

import org.jspecify.annotations.NonNull;

import java.util.Arrays;

public abstract class Controller {
    private static final int MAX_TRIES = 1;
    private final int @NonNull [] give_up_counters;

    protected Controller(int num_states) {
        give_up_counters = new int[num_states];
    }

    public final void resetGiveUpCounters() {
        Arrays.fill(give_up_counters, 0);
    }

    public final void resetGiveUpCounter(int state_index) {
        give_up_counters[state_index] = 0;
    }

    protected final boolean shouldGiveUp(int state_index) {
        if (give_up_counters[state_index] != MAX_TRIES) {
            give_up_counters[state_index]++;
            return false;
        } else {
            return true;
        }
    }

    // Was void, with a no-op default meaning "nothing happens" for any controller that doesn't
    // override this - which was every controller except GatherController, so a unit stuck under
    // any other controller (walking to attack, following, entering a building, anything) had no
    // recovery mechanism at all once its own stuck-detection actually started firing for those
    // too (see Unit.updateStuckCheck()'s own comment for why detection itself was previously
    // limited to gathering specifically). Returns whether this controller actually did something
    // about being stuck, so the caller (Unit.updateStuckCheck()) can apply a generic fallback -
    // popping the controller, abandoning the stuck order rather than the unit standing there
    // forever - for any controller that doesn't have a more specific recovery of its own.
    // //added by ikill240c
    public boolean onStuck() {//added by ikill240c
        return false; // default: not handled; caller applies its own generic fallback //added by ikill240c
    }

    public @NonNull String getKey() {
        return Integer.toString(getClass().hashCode());
    }

    public abstract void decide();
}
