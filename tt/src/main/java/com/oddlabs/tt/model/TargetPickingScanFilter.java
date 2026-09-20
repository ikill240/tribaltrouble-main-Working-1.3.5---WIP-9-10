package com.oddlabs.tt.model;

import com.oddlabs.tt.pathfinder.ScanFilter;
import org.jspecify.annotations.Nullable;

/**
 * A ScanFilter that, in addition to the plain scan callback, accumulates candidates during the
 * scan and exposes the chosen one afterward via removeTarget() - implemented by both
 * AttackScanFilter (regular unit-vs-unit combat) and TowerAttackScanFilter (tower-specific target
 * priority). Lets IdleController accept either without depending on which concrete targeting
 * strategy a given mounted/unmounted unit is using. //added by ikill240c
 */
public interface TargetPickingScanFilter extends ScanFilter { //added by ikill240c
    @Nullable Selectable<?> removeTarget(); //added by ikill240c
}
