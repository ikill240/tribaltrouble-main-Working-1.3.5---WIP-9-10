package com.oddlabs.tt.model.weapon;

import com.oddlabs.tt.model.Unit;
import org.jspecify.annotations.NonNull;

public final class ConvertFactory implements MagicFactory {//added by ikill240c
    private final float offset_x;
    private final float offset_y;
    private final float hit_radius;
    private final float seconds_per_anim;
    private final float init_ratio;
    private final float release_ratio;
    private final boolean affects_chieftains;

    // constructor now takes affects_chieftains as an actual parameter instead of reading itself //added by ikill240c 2026-09-08 03:00
    public ConvertFactory(float offset_x, float offset_y, float hit_radius, float seconds_per_anim,
            float init_ratio, float release_ratio, boolean affects_chieftains) {
        this.offset_x = offset_x;
        this.offset_y = offset_y;
        this.hit_radius = hit_radius;
        this.seconds_per_anim = seconds_per_anim;
        this.init_ratio = init_ratio;
        this.release_ratio = release_ratio;
        this.affects_chieftains = affects_chieftains; // store the customizable toggle //added by ikill240c 2026-09-08 03:00
    }

    // single execute() method (removed the duplicate that caused a compile error); passes the toggle through //added by ikill240c 2026-09-08 03:00
    @Override
    public @NonNull Magic execute(@NonNull Unit src) {
        return new Convert(offset_x, offset_y, hit_radius, src, affects_chieftains);
    }

    @Override
    public float getHitRadius() {
        return hit_radius;
    }

    @Override
    public float getSecondsPerAnim() {
        return seconds_per_anim;
    }

    @Override
    public float getSecondsPerInit() {
        return init_ratio * seconds_per_anim;
    }

    @Override
    public float getSecondsPerRelease() {
        return release_ratio * seconds_per_anim;
    }
}
