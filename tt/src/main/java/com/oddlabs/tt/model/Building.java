package com.oddlabs.tt.model;

import com.oddlabs.tt.landscape.TreeSupply;
import com.oddlabs.tt.model.weapon.IronAxeWeapon;
import com.oddlabs.tt.model.weapon.RockAxeWeapon;
import com.oddlabs.tt.model.weapon.RubberAxeWeapon;
import com.oddlabs.tt.model.weapon.ThrowingWeapon;
import com.oddlabs.tt.pathfinder.Occupant;
import com.oddlabs.tt.player.Player;
import com.oddlabs.tt.util.Target;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;


public abstract class Building extends Selectable<BuildingTemplate> implements Occupant {
    public enum BuildState {
        START,
        HALFBUILT,
        BUILT
    }

    public Building(@NonNull Player owner, @NonNull BuildingTemplate template) {
        super(owner, template);
    }

    public abstract boolean hasRallyPoint();

    public abstract Target getRallyPoint();

    public abstract @Nullable UnitContainer getUnitContainer();

    public abstract @Nullable SupplyContainer getSupplyContainer(@NonNull Class<?> key);

    public abstract @Nullable BuildSupplyContainer getBuildSupplyContainer(@NonNull Class<?> key);

    public abstract DeployContainer getDeployContainer(DeployType type);

    public abstract @Nullable ChieftainContainer getChieftainContainer();

    public abstract int getUnitCount();

    public abstract boolean canExitTower();

    public abstract void exitTower();

    public abstract void deployUnits(@NonNull DeployType type, int num_units);

    public abstract void createHarvesters(int num_tree, int num_rock, int num_iron, int num_rubber);

    public abstract void buildWeapons(@NonNull Class<? extends ThrowingWeapon> type, int num_weapons, boolean infinite);

    public abstract boolean canBuildChieftain();

    public abstract boolean canStopChieftain();

    public abstract void trainChieftain(boolean start);

    public abstract void deployChieftain();

    public abstract void createArmy(int num_peon, int num_rock, int num_iron, int num_rubber);

    public abstract void createTransporters(int num_tree, int num_rock, int num_iron, int num_rubber);

    public abstract boolean isDamaged();

    public abstract int getHitPoints();

    /**
     * This building's actual max HP, accounting for any multiplier applied to it (e.g.
     * LandBuilding's building health multiplier). Defaults to the raw template value, which is
     * already correct for subclasses (Ship, ShipProxy) that don't apply any such multiplier -
     * LandBuilding overrides this with the scaled value. Needed so callers that only hold a
     * Building reference (not knowing which concrete subclass) can get the correct max HP without
     * an instanceof check - ToolTipAdapter.visitBuilding() previously divided by
     * getTemplate().getMaxHitPoints() directly, which overflowed the health ratio above 1.0 once a
     * health multiplier was in play, crashing GUIIcons.getWatch() with an out-of-bounds index.
     * //added by ikill240c 2026-09-12
     */
    public int getEffectiveMaxHitPoints() { //added by ikill240c 2026-09-12
        return getTemplate().getMaxHitPoints();
    }

    public abstract void repair(int amount);

    public abstract boolean isPlacingLegal();

    public abstract boolean isPlaced();

    public abstract boolean isComplete();

    public abstract void place();

    public abstract boolean isValidRallyPoint(Target t);

    public abstract void setRallyPoint(@NonNull Target target);

    public abstract void fillSupplies(@NonNull Class<?> key, int max);

    public abstract void removeSupplies(@NonNull Class<?> key);

    public abstract @NonNull BuildState getRenderLevel();

    public Building getEntrance() {
        return this;
    }

    public Building getBase() {
        return this;
    }

    public void printDebugInfo() {
        IO.println("-----------------------------------");
        if (getAbilities().hasAbilities(Abilities.REPRODUCE)) {
            IO.println("Units = " + getUnitContainer().getNumSupplies());
        } else if (getAbilities().hasAbilities(Abilities.BUILD_ARMIES)) {
            IO.println("Units = " + getUnitContainer().getNumSupplies());
            IO.println("Tree = " + getSupplyContainer(TreeSupply.class).getNumSupplies());
            IO.println("Rock = " + getSupplyContainer(RockSupply.class).getNumSupplies());
            IO.println("Iron = " + getSupplyContainer(IronSupply.class).getNumSupplies());
            IO.println("Rubber = " + getSupplyContainer(RubberSupply.class).getNumSupplies());
            IO.println("Rock Weapons = " + getSupplyContainer(RockAxeWeapon.class).getNumSupplies());
            IO.println("Iron Weapons = " + getSupplyContainer(IronAxeWeapon.class).getNumSupplies());
            IO.println("Rubber Weapons = " + getSupplyContainer(RubberAxeWeapon.class).getNumSupplies());
        }
    }
}
