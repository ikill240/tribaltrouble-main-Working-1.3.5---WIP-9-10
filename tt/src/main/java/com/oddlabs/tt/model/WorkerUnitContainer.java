package com.oddlabs.tt.model;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public final class WorkerUnitContainer extends UnitContainer {
    private final @NonNull Building building;

    public WorkerUnitContainer(@NonNull Building building) {
        super(building.getOwner().getWorld().getMaxUnitCount());
        this.building = building;
    }

    @Override
    public void enter(@NonNull Unit unit) {
        assert canEnter(unit);
        unit.removeNow();
        increaseSupply(1);
    }

    @Override
    public boolean canEnter(@NonNull Unit unit) {
        // The owner-population-cap check below only matters for a CROSS-owner entry (an ally
        // donation) - same-owner entry is a net-zero change to that owner's own population count
        // (unit.removeNow() decrements it, then increaseSupply() below increments it right back
        // up, both against the SAME owner's counter), so gating normal entry on isSupplyFull()
        // was blocking a unit from entering its own player's own building any time that player's
        // total population merely happened to be at/near cap - a routine, common state for an
        // established player, not an edge case. That caused mass pileups of peons unable to enter
        // any building at all. Only a genuine cross-owner donation actually changes the
        // RECIPIENT's own total (their counter goes up while the donor's own goes down against a
        // different counter entirely), so only that case needs the pre-check to avoid the
        // increaseSupply() assertion failing outright once the recipient is genuinely full.
        // //added by ikill240c
        boolean cross_owner_donation = unit.getOwner() != building.getOwner(); //added by ikill240c
        return getTotalSupplies() != getMaxSupplyCount() //added by ikill240c
                && (!cross_owner_donation || !building.getOwner().getUnitCountContainer().isSupplyFull()); //added by ikill240c
    }

    private int getTotalSupplies() {
//		return getNumSupplies() + building.getBuildSupplyContainer(Unit.class).getNumSupplies() == getMaxSupplyCount();
        return getNumSupplies() + getNumPreparing();
    }

    @Override
    public @Nullable Unit exit() {
        assert getNumSupplies() > 0;
        increaseSupply(-1);
        return null;
    }

    @Override
    public int increaseSupply(int amount) {
        int result = building.getOwner().getUnitCountContainer().increaseSupply(amount);
        assert result == amount : "result = " + result + " | amount = " + amount;
        return super.increaseSupply(amount);
    }
}
