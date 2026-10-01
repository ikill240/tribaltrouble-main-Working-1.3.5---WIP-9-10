package com.oddlabs.tt.model.behaviour;

import com.oddlabs.tt.model.Abilities;
import com.oddlabs.tt.model.Building;
import com.oddlabs.tt.model.MountUnitContainer; //added by ikill240c
import com.oddlabs.tt.model.Race; //added by ikill240c
import com.oddlabs.tt.model.Selectable; //added by ikill240c
import com.oddlabs.tt.model.UnitSupplyContainer;
import com.oddlabs.tt.model.Unit;
import com.oddlabs.tt.model.weapon.ThrowingFactory;
import com.oddlabs.tt.model.weapon.ThrowingWeapon;
import com.oddlabs.tt.player.Player; //added by ikill240c 2026-09-14
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable; //added by ikill240c

public final class EnterController extends Controller {
    private final @NonNull Building building;
    private final @NonNull Unit unit;
    // Stuck while walking to the building (e.g. peons queuing at the side of the Quarters nearest them): re-route up
    // to this many times - a fresh path can lead round to a free side - before letting the stuck check cancel the order.
    // Before this, the stuck check cancelled the order on the first stall, leaving peons standing at the door. //added by ikill240c
    private static final int MAX_STUCK_REROUTES = 3; //added by ikill240c
    private int stuck_reroutes; //added by ikill240c

    public EnterController(@NonNull Unit unit, @NonNull Building building) {
        super(1);
        this.unit = unit;
        this.building = building.getEntrance();
    }

    // Finds the nearest OTHER building of the exact same template as `full_tower`, owned by the
    // same player OR an ally (matches Unit.canEnter()'s own ally-towers relaxation - see that
    // method's comment for why this is scoped to towers specifically), that still has room for
    // `unit` - used when a unit's ordered tower is already occupied, so an overflow unit from a
    // multi-unit "mount this tower" order doesn't just give up in place; it looks for the next-
    // nearest tower of the same type instead, across the unit owner's own AND allied towers.
    // Deliberately scoped to towers specifically (MountUnitContainer, capacity 1) rather than every
    // building type EnterController handles - a Quarters/Armory being "full" (of a different,
    // incompatible meaning for those container types) should keep the existing give-up behavior
    // unchanged. //added by ikill240c
    private static @Nullable Building findNearestAvailableTower(@NonNull Unit unit, @NonNull Building full_tower) { //added by ikill240c
        Building nearest = null; //added by ikill240c
        float nearest_dist2 = Float.MAX_VALUE; //added by ikill240c
        // Scans every player in the world, not just unit.getOwner(), so allied towers are
        // considered too - see the class comment above. //added by ikill240c 2026-09-14
        for (Player player : unit.getWorld().getPlayers()) { //added by ikill240c 2026-09-14
            if (player != unit.getOwner() && unit.getOwner().isEnemy(player)) //added by ikill240c 2026-09-14
                continue; // only the unit's own player or an ally - never scan an enemy's towers //added by ikill240c 2026-09-14
            for (Selectable<?> s : player.getUnits().getSet()) { //added by ikill240c 2026-09-14
                if (s.isDead() || !(s instanceof Building candidate) || candidate == full_tower) //added by ikill240c
                    continue; //added by ikill240c
                if (candidate.getTemplate().getTemplateID() != full_tower.getTemplate().getTemplateID()) //added by ikill240c
                    continue; //added by ikill240c
                if (!(candidate.getUnitContainer() instanceof MountUnitContainer) //added by ikill240c
                        || !candidate.getUnitContainer().canEnter(unit)) //added by ikill240c
                    continue; //added by ikill240c
                float dx = candidate.getPositionX() - unit.getPositionX(); //added by ikill240c
                float dy = candidate.getPositionY() - unit.getPositionY(); //added by ikill240c
                float dist2 = dx * dx + dy * dy; //added by ikill240c
                if (dist2 < nearest_dist2) { //added by ikill240c
                    nearest_dist2 = dist2; //added by ikill240c
                    nearest = candidate; //added by ikill240c
                } //added by ikill240c
            } //added by ikill240c 2026-09-14
        } //added by ikill240c 2026-09-14
        return nearest; //added by ikill240c
    }

    @Override
    public void decide() {
        if (building.isDead()) {
            unit.popController();
        } else if (unit.isCloseEnough(0f, building)) {
            if (building.getUnitContainer() != null && building.getUnitContainer().canEnter(unit)) {
                UnitSupplyContainer unitSupply = unit.getSupplyContainer();
                int numSupply = (unitSupply != null) ? unitSupply.getNumSupplies() : 0;
                if (building.getAbilities().hasAbilities(Abilities.SUPPLY_CONTAINER)) {
                    if (unit.getAbilities().hasAbilities(Abilities.HARVEST) && numSupply > 0) {
                        Class type = unitSupply.getSupplyType();
                        building.getSupplyContainer(type).increaseSupply(numSupply);
                        unitSupply.increaseSupply(-numSupply, type);
                    }
                    if (unit.getWeaponFactory() instanceof ThrowingFactory) {
                        Class<? extends ThrowingWeapon> type = unit.getWeaponFactory().getType();
                        building.getSupplyContainer(type).increaseSupply(1);
                    }
                }
                building.getUnitContainer().enter(unit);
            } else if (unit.getOwner().usesReferenceCommandRules()) { //added by ikill240c
                // Expert/Ultra/Fable units: the reference rule - can't get in, give up. Those AIs were written
                // against it and manage their own garrisons; the eject-and-redirect handling below moved their
                // units into towers they never chose (or out of ones they did), leaving units idle and the AI's
                // bookkeeping out of step. See Player.usesReferenceCommandRules(). //added by ikill240c
                unit.popController(); //added by ikill240c
            } else if (building.getUnitContainer() instanceof MountUnitContainer mount_container //added by ikill240c
                    && mount_container.isSupplyFull() //added by ikill240c
                    && unit.getAbilities().hasAbilities(Abilities.THROW)) { //added by ikill240c
                // This specific tower is full, but the arriving unit is otherwise eligible to mount
                // it (has THROW - canEnter()'s only other condition). This branch is reached both
                // by a direct right-click on this exact tower AND by a unit an AI dispatched to
                // what WAS an empty tower that became occupied by the time it actually walked here
                // - there is no way to distinguish those two cases at this level (both are just "a
                // unit was ordered to walk to and enter this building"), so the same rule has to
                // serve both. Earlier this only protected a MOUNTED unit that was recently given a
                // direct player order, and gave up entirely (rather than looking elsewhere) when
                // that protection applied - two real bugs: the protection window expiring meant
                // the AI would later boot a good, deliberately-placed unit for a worse one with no
                // check at all, and giving up outright (instead of falling through to look for a
                // different tower, exactly like the branch below does) meant a unit could stop
                // finding anywhere to go the instant one specific tower filled up. Now always
                // compares strength (by max HP, the same "toughness" proxy Player.unitToughness()
                // uses elsewhere) with no time-limited exception: replace only if the arriving unit
                // is actually stronger, and otherwise fall through to the exact same nearest-
                // available-tower search the branch below uses - so this tower filling up never
                // strands a unit that has nowhere else it could reasonably go, and an existing
                // deliberately-placed occupant is never downgraded once, whether that check happens
                // seconds or minutes after it was placed. //added by ikill240c
                Unit mounted = mount_container.getUnit(); //added by ikill240c
                // Never eject a unit that belongs to an Expert/Ultra/Fable player - that AI placed it there and
                // doesn't expect anyone else to remove it (the arriving unit is redirected instead). //added by ikill240c
                boolean arriving_is_stronger = mounted != null && !mounted.getOwner().usesReferenceCommandRules() //added by ikill240c
                        && unit.getTemplate().getMaxHitPoints() > mounted.getTemplate().getMaxHitPoints(); //added by ikill240c
                if (arriving_is_stronger) { //added by ikill240c
                    mount_container.exit(); //added by ikill240c
                    building.getUnitContainer().enter(unit); //added by ikill240c
                } else { //added by ikill240c
                    Building next_tower = findNearestAvailableTower(unit, building); //added by ikill240c
                    if (next_tower != null) { //added by ikill240c
                        unit.swapController(new EnterController(unit, next_tower)); //added by ikill240c
                    } else { //added by ikill240c
                        unit.popController(); //added by ikill240c
                    } //added by ikill240c
                } //added by ikill240c
            } else if (building.getUnitContainer() instanceof MountUnitContainer //added by ikill240c
                    && building.getTemplate().getTemplateID() == Race.BUILDING_TOWER) { //added by ikill240c
                // Reached when this tower isn't full but the arriving unit still can't enter it
                // (lacks THROW) - look for a different tower of the same type this unit CAN use,
                // same as the fallback above. //added by ikill240c
                Building next_tower = findNearestAvailableTower(unit, building); //added by ikill240c
                if (next_tower != null) { //added by ikill240c
                    unit.swapController(new EnterController(unit, next_tower)); //added by ikill240c
                } else { //added by ikill240c
                    unit.popController(); //added by ikill240c
                } //added by ikill240c
            } else {
                unit.popController();
            }
        } else {
            if (shouldGiveUp(0)) {
                unit.popController();
            } else
                unit.setBehaviour(new WalkBehaviour(unit, building, 0, false));
        }
    }

    @Override //added by ikill240c
    public boolean onStuck() { //added by ikill240c
        if (building.isDead() || ++stuck_reroutes > MAX_STUCK_REROUTES) //added by ikill240c
            return false; //added by ikill240c - give up: the stuck check cancels the order
        unit.setBehaviour(new WalkBehaviour(unit, building, 0, false)); //added by ikill240c
        return true; //added by ikill240c
    } //added by ikill240c
}
