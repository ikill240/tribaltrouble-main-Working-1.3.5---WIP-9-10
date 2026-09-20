package com.oddlabs.tt.model;

import com.oddlabs.tt.model.weapon.IronAxeWeapon;
import com.oddlabs.tt.model.weapon.IronSpearWeapon;
import com.oddlabs.tt.model.weapon.RubberAxeWeapon;
import com.oddlabs.tt.model.weapon.RubberSpearWeapon;
import com.oddlabs.tt.model.weapon.ThrowingWeapon;
import com.oddlabs.tt.pathfinder.Occupant;
import com.oddlabs.tt.player.Player;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Tower-specific target selection, used only for units mounted in a tower (see
 * Unit.mount()/enterBuilding() - not for regular unit-vs-unit combat, which keeps using the plain
 * AttackScanFilter). Priority, highest to lowest:
 *
 * <ol>
 *   <li>Ships and other non-Unit Selectables keep their existing getAttackPriority() ordering
 *       (SHIP is the single highest tier in that system) - a tower shouldn't ignore a ship in
 *       range to instead fixate on a warrior it may not even be able to reach.
 *   <li>If more than PEON_RUSH_THRESHOLD enemy peons are in range, target a peon - a mass of
 *       peons (a common early rush/harass tactic) is treated as the bigger immediate threat than
 *       a single warrior or chief would be.
 *   <li>Otherwise, the strongest warrior present: rubber ("chicken") and iron warriors first, then
 *       rock warriors.
 *   <li>A chieftain, but only once no warriors qualify above - chiefs are high-value but
 *       comparatively fragile targets that would otherwise draw tower fire away from the actual
 *       warrior threat they're often escorted by.
 *   <li>A peon, if that's the only thing in range (below the rush threshold).
 *   <li>Any other enemy building (quarters, armory, etc.) via the original priority ordering.
 * </ol>
 */
public final class TowerAttackScanFilter implements TargetPickingScanFilter { //added by ikill240c
    // How many enemy peons need to be in range before this tower prioritizes hitting one over
    // whatever warrior/chief is also present. //added by ikill240c
    private static final int PEON_RUSH_THRESHOLD = 4; //added by ikill240c

    private final int max_range;
    private final @NonNull Player owner;

    private @Nullable Selectable<?> best_other; // ships, buildings - via original getAttackPriority() //added by ikill240c
    private AttackScanFilter.@NonNull Priority best_other_priority = AttackScanFilter.Priority.NONE; //added by ikill240c
    private @Nullable Unit best_rubber_or_iron; //added by ikill240c
    private @Nullable Unit best_rock; //added by ikill240c
    private @Nullable Unit best_chief; //added by ikill240c
    private @Nullable Unit best_peon; //added by ikill240c
    private int peon_count = 0; //added by ikill240c

    public TowerAttackScanFilter(@NonNull Player owner, int max_range) { //added by ikill240c
        this.owner = owner;
        this.max_range = max_range;
    }

    public @Nullable Selectable<?> removeTarget() { //added by ikill240c
        Selectable<?> result; //added by ikill240c
        if (best_other != null && best_other_priority == AttackScanFilter.Priority.SHIP) { //added by ikill240c
            result = best_other; // a ship in range always wins - see class comment //added by ikill240c
        } else if (peon_count > PEON_RUSH_THRESHOLD && best_peon != null) { //added by ikill240c
            result = best_peon; //added by ikill240c
        } else if (best_rubber_or_iron != null) { //added by ikill240c
            result = best_rubber_or_iron; //added by ikill240c
        } else if (best_rock != null) { //added by ikill240c
            result = best_rock; //added by ikill240c
        } else if (best_chief != null) { //added by ikill240c
            result = best_chief; //added by ikill240c
        } else if (best_peon != null) { //added by ikill240c
            result = best_peon; //added by ikill240c
        } else { //added by ikill240c
            result = best_other; //added by ikill240c
        } //added by ikill240c

        best_other = null; //added by ikill240c
        best_other_priority = AttackScanFilter.Priority.NONE; //added by ikill240c
        best_rubber_or_iron = null; //added by ikill240c
        best_rock = null; //added by ikill240c
        best_chief = null; //added by ikill240c
        best_peon = null; //added by ikill240c
        peon_count = 0; //added by ikill240c
        return result; //added by ikill240c
    }

    @Override
    public int getMinRadius() {
        return 1;
    }

    @Override
    public int getMaxRadius() {
        return max_range;
    }

    @Override
    public boolean filter(int grid_x, int grid_y, @NonNull Occupant occ) { //added by ikill240c
        if (!(occ instanceof Selectable<?> s) || s.isDead() || !owner.isEnemy(s.getOwner())) { //added by ikill240c
            return false; //added by ikill240c
        } //added by ikill240c

        if (!(s instanceof Unit unit)) { //added by ikill240c
            // Buildings, ships, etc. - classified via the shared, existing priority system rather
            // than duplicating it here. //added by ikill240c
            AttackScanFilter.Priority priority = s.getAttackPriority(); //added by ikill240c
            if (best_other_priority.value < priority.value) { //added by ikill240c
                best_other_priority = priority; //added by ikill240c
                best_other = s; //added by ikill240c
            } //added by ikill240c
            return false; //added by ikill240c
        } //added by ikill240c

        if (unit.getAbilities().hasAbilities(Abilities.MAGIC)) { //added by ikill240c
            if (best_chief == null) best_chief = unit; //added by ikill240c
            return false; //added by ikill240c
        } //added by ikill240c
        if (unit.getAbilities().hasAbilities(Abilities.HARVEST)) { //added by ikill240c
            peon_count++; //added by ikill240c
            if (best_peon == null) best_peon = unit; //added by ikill240c
            return false; //added by ikill240c
        } //added by ikill240c

        Class<? extends ThrowingWeapon> weapon_type = unit.getWeaponFactory().getType(); //added by ikill240c
        if (weapon_type == RubberAxeWeapon.class || weapon_type == RubberSpearWeapon.class //added by ikill240c
                || weapon_type == IronAxeWeapon.class || weapon_type == IronSpearWeapon.class) { //added by ikill240c
            if (best_rubber_or_iron == null) best_rubber_or_iron = unit; //added by ikill240c
        } else { //added by ikill240c
            // Rock warriors, and any unrecognized weapon type (fail safe by treating it like the
            // lowest warrior tier rather than silently dropping it as a possible target) both fall
            // here - deliberately merged into one branch rather than two identical ones, which
            // errorprone flagged as suspicious duplicate code when they were separate.
            // //added by ikill240c
            if (best_rock == null) best_rock = unit; //added by ikill240c
        } //added by ikill240c
        return false;
    }
}
