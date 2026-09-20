package com.oddlabs.tt.model;

import com.oddlabs.tt.model.weapon.IronAxeWeapon;
import com.oddlabs.tt.model.weapon.IronSpearWeapon;
import com.oddlabs.tt.model.weapon.RockAxeWeapon;
import com.oddlabs.tt.model.weapon.RockSpearWeapon;
import com.oddlabs.tt.model.weapon.RubberAxeWeapon;
import com.oddlabs.tt.model.weapon.RubberSpearWeapon;


import org.joml.Vector2f;
import org.joml.Vector3f;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ShipHR {

    private final boolean vikings;

    float unitSize(Unit unit) { //added by ikill240c 2026-09-10 - class is final, so 'protected' gave no extra access beyond package-private; simplified per errorprone warning
        if (unit.isWarrior()) {
            return 1.20f;
        } else {
            return 0.70f;
        }
    }

    interface Row {
        public abstract boolean canFit(Unit unit);

        public abstract void seat(Unit unit);

        public abstract void exit(Unit unit);

        public abstract ShipAllocation getAllocation(Unit unit);

        public abstract void killAll();

        public abstract List<Unit> allUnits();

        public abstract Unit findUnit(UnitTemplate template);

        public abstract boolean needRowers();

        public abstract int countRowers();

        // Merged from boats_on_steam: whether a peon can be pulled out of this row (e.g. for
        // reassignment or disembarking) without leaving it unable to function - a rowing bench
        // needs to keep at least its minimum required rowers, for instance. Missing this check
        // previously meant peons could be pulled from any row regardless of whether doing so left
        // it non-functional (e.g. a bench with no rowers left). //added by ikill240c
        public abstract boolean canSparePeons(); //added by ikill240c
    }

    static class Rudder implements Row { //added by ikill240c 2026-09-10 - verified this class never references the enclosing ShipHR instance; safe to make static per errorprone warning
        private Unit unit = null;
        private ShipAllocation alloc;

        public Rudder(Ship ship, float x, float y, float z) {
            alloc = new ShipAllocation(new Vector3f(x, y, z), new Vector2f(0.0f, 1.0f), ShipAllocation.STEERING);
            var owner = ship.getOwner();
            unit = new Unit(owner, x, y, null, owner.getRace().getUnitTemplate(Race.UNIT_PEON), null, false, false,
                    true);
            unit.mount(ship, alloc);
        }

        public boolean canFit(Unit unit) {
            return false;
        }

        public void seat(Unit unit) {
            assert (false);
        }

        public void exit(Unit unit) {
            assert (false);
        }

        public ShipAllocation getAllocation(Unit unit) {
            if (this.unit == unit) {
                return alloc;
            } else {
                return null;
            }
        }

        public void killAll() {
            // Defensive: if this unit already died some other way before the ship itself did
            // (e.g. picked off independently), unit.drown() here would call pushController() on
            // an already-dead unit - pushController() asserts a unit is never dead when pushed
            // to, crashing the game outright rather than harmlessly no-opping on a crew member
            // that's already gone. //added by ikill240c
            if (unit != null && !unit.isDead()) { //added by ikill240c
                unit.drown();
            } //added by ikill240c
            unit = null;
        }

        public List<Unit> allUnits() {
            return new ArrayList<Unit>();
        }

        public Unit findUnit(UnitTemplate template) {
            return null;
        }

        public boolean needRowers() {
            return false;
        }

        public int countRowers() {
            return 0;
        }

        public boolean canSparePeons() { //added by ikill240c
            return false; // the rudder's single peon is never spareable //added by ikill240c
        }
    }

    class LowerDeckRow implements Row {
        private final float x;
        private final float y0;
        private final float y1;
        private final float z;
        private final float total;
        private final boolean left_rower;
        private final boolean right_rower;
        private float used;
        private final HashMap<Unit, ShipAllocation> allocs = new HashMap<>();
        private final List<Unit> all_units = new ArrayList<>();
        private final List<Unit> peons = new ArrayList<>();
        private final List<Unit> warriors = new ArrayList<>();

        public LowerDeckRow(float x, float y0, float y1, float z, boolean left_rower, boolean right_rower) {
            this.x = x;
            this.y0 = y0;
            this.y1 = y1;
            this.z = z;
            this.total = Math.abs(y1 - y0);
            this.used = 0.0f;
            this.left_rower = left_rower;
            this.right_rower = right_rower;
        }

        public boolean canFit(Unit unit) {
            float size = unitSize(unit);
            return total - used > size;
        }

        public void seat(Unit unit) {
            if (!allocs.containsKey(unit)) {
                float size = unitSize(unit);
                allocs.put(unit, new ShipAllocation());
                all_units.add(unit);
                if (unit.isWarrior()) {
                    warriors.add(unit);
                } else {
                    peons.add(unit);
                }
                reassign();
            }
        }

        public void exit(Unit unit) {
            if (allocs.containsKey(unit)) {
                allocs.remove(unit);
                all_units.remove(unit);
                peons.remove(unit);
                warriors.remove(unit);
                reassign();
                assert (used >= 0.0f);
            }
        }

        public void killAll() {
            for (int i = 0; i < all_units.size(); i++) {
                Unit unit = all_units.get(i);
                // Same defensive check as Rudder.killAll() above - see its comment. //added by ikill240c
                if (!unit.isDead()) { //added by ikill240c
                    unit.drown();
                } //added by ikill240c
            }
            all_units.clear();
            peons.clear();
            warriors.clear();
            allocs.clear();
        }

        public ShipAllocation getAllocation(Unit unit) {
            if (allocs.containsKey(unit)) {
                return allocs.get(unit);
            } else {
                return null;
            }
        }

        public List<Unit> allUnits() {
            return all_units;
        }

        public Unit findUnit(UnitTemplate template) {
            for (int i = 0; i < all_units.size(); i++) {
                Unit unit = all_units.get(i);
                if (unit.getTemplate() == template) {
                    return unit;
                }
            }
            return null;
        }

        public boolean needRowers() {
            int required = (left_rower ? 1 : 0) + (right_rower ? 1 : 0);
            return peons.size() < required;
        }

        public int countRowers() {
            int required = (left_rower ? 1 : 0) + (right_rower ? 1 : 0);
            return Math.min(peons.size(), required);
        }

        public boolean canSparePeons() { //added by ikill240c
            // Only excess peons beyond the minimum rowers this bench actually needs can be pulled
            // - e.g. a bench needing 1 rower with exactly 1 peon seated can't spare it (pulling it
            // would leave the bench unable to row), but with 2 peons seated the extra one can be
            // spared. //added by ikill240c
            int required = (left_rower ? 1 : 0) + (right_rower ? 1 : 0); //added by ikill240c
            return peons.size() > required; //added by ikill240c
        }

        private void reassign() {
            used = 0.0f;
            for (int i = 0; i < all_units.size(); i++) {
                used += unitSize(all_units.get(i));
            }
            if (all_units.size() == 1) {
                Unit unit = all_units.get(0);
                float size = unitSize(unit);
                ShipAllocation alloc = allocs.get(unit);
                alloc.setRotation(1.0f, 0.0f);
                if (unit.isWarrior()) {
                    alloc.setOffset(x, (y0 + y1) * 0.5f, z);
                    alloc.setRole(ShipAllocation.SITTING);
                } else {
                    if (right_rower) {
                        alloc.setOffset(x, y0 + size * 0.5f, z);
                        alloc.setRole(ShipAllocation.ROWING_RIGHT);
                    } else if (left_rower) {
                        alloc.setOffset(x, y1 - size * 0.5f, z);
                        alloc.setRole(ShipAllocation.ROWING_LEFT);
                    } else {
                        alloc.setOffset(x, (y0 + y1) * 0.5f, z);
                        alloc.setRole(ShipAllocation.SITTING);
                    }
                }
            } else if (all_units.size() > 1) {
                float gap = (total - used) / (all_units.size() - 1);
                float yOffset = Math.min(y0, y1);
                int tmpCounter = 0;
                Unit leftRower = null;
                Unit rightRower = null;
                if (left_rower && peons.size() > tmpCounter) {
                    leftRower = peons.get(tmpCounter);
                    tmpCounter++;
                }
                if (right_rower && peons.size() > tmpCounter) {
                    rightRower = peons.get(tmpCounter);
                    tmpCounter++;
                }
                if (rightRower != null) {
                    ShipAllocation alloc = allocs.get(rightRower);
                    float s = unitSize(rightRower);
                    alloc.setOffset(x, yOffset + s * 0.5f, z);
                    alloc.setRole(ShipAllocation.ROWING_RIGHT);
                    yOffset += gap + s;
                }
                for (int i = 0; i < all_units.size(); i++) {
                    Unit unit = all_units.get(i);
                    if (unit == leftRower || unit == rightRower) {
                        continue;
                    }
                    ShipAllocation alloc = allocs.get(unit);
                    float s = unitSize(unit);
                    alloc.setOffset(x, yOffset + s * 0.5f, z);
                    yOffset += gap + s;
                }
                if (leftRower != null) {
                    ShipAllocation alloc = allocs.get(leftRower);
                    float s = unitSize(leftRower);
                    alloc.setOffset(x, yOffset + s * 0.5f, z);
                    alloc.setRole(ShipAllocation.ROWING_LEFT);
                    yOffset += gap + s;
                }
            }
        }
    }

    static class UpperDeckRow implements Row { //added by ikill240c 2026-09-10 - verified this class never references the enclosing ShipHR instance; safe to make static per errorprone warning
        private Unit left = null;
        private Unit right = null;
        private ShipAllocation leftAlloc;
        private ShipAllocation rightAlloc;

        public UpperDeckRow(float x, float y, float z) {
            leftAlloc = new ShipAllocation(new Vector3f(x, y, z), new Vector2f(0.0f, 1.0f), ShipAllocation.FIGHTING);
            rightAlloc = new ShipAllocation(new Vector3f(x, -y, z), new Vector2f(0.0f, -1.0f), ShipAllocation.FIGHTING);
        }

        public boolean canFit(Unit unit) {
            return unit.isWarrior() && (left == null || right == null);
        }

        public void seat(Unit unit) {
            if (unit.isWarrior()) {
                if (left == null) {
                    left = unit;
                } else if (right == null) {
                    right = unit;
                }
            }
        }

        public void exit(Unit unit) {
            if (left == unit) {
                left = null;
            } else if (right == unit) {
                right = null;
            }
        }

        public void killAll() {
            // Same defensive check as Rudder.killAll() above - see its comment. The reference is
            // always cleared regardless of whether drown() was actually called, since other code
            // relies on left/right == null meaning "seat empty" - only the drown() call itself
            // needs skipping for an already-dead unit, not the cleanup. //added by ikill240c
            if (left != null) {
                if (!left.isDead()) { //added by ikill240c
                    left.drown();
                } //added by ikill240c
                left = null;
            }
            if (right != null) {
                if (!right.isDead()) { //added by ikill240c
                    right.drown();
                } //added by ikill240c
                right = null;
            }
        }

        public ShipAllocation getAllocation(Unit unit) {
            if (left == unit) {
                return leftAlloc;
            } else if (right == unit) {
                return rightAlloc;
            }
            return null;
        }

        public List<Unit> allUnits() {
            List<Unit> ret = new ArrayList<>();
            if (left != null) {
                ret.add(left);
            }
            if (right != null) {
                ret.add(right);
            }
            return ret;
        }

        public Unit findUnit(UnitTemplate template) {
            if (left != null && left.getTemplate() == template) {
                return left;
            }
            if (right != null && right.getTemplate() == template) {
                return right;
            }
            return null;
        }

        public boolean needRowers() {
            return false;
        }

        public int countRowers() {
            return 0;
        }

        public boolean canSparePeons() { //added by ikill240c
            return false; // fighters, not rowers - never spareable from this row //added by ikill240c
        }
    }

    private LinkedHashMap<Unit, Row> unit2row = new LinkedHashMap<>();

    private ArrayList<Row> rows = new ArrayList<>();

    public ShipHR(Ship ship, boolean vikings) {
        this.vikings = vikings;
        if (vikings) {
            rows.add(new Rudder(ship, -11.14f, -0.43f, +0.54f));
            rows.add(new LowerDeckRow(-9.62f, -1.98f, +1.98f, 0.42f, true, true));
            rows.add(new LowerDeckRow(-8.18f, -2.31f, +2.31f, 0.42f, true, true));
            rows.add(new LowerDeckRow(-6.71f, -2.54f, +2.54f, 0.43f, true, true));
            rows.add(new LowerDeckRow(-5.32f, +0.25f, +2.75f, 0.44f, true, false));
            rows.add(new LowerDeckRow(-5.32f, -2.75f, -0.25f, 0.44f, false, true));
            rows.add(new LowerDeckRow(-3.42f, +0.39f, +2.90f, 0.45f, true, false));
            rows.add(new LowerDeckRow(-3.42f, -2.90f, -0.39f, 0.45f, false, true));
            rows.add(new LowerDeckRow(-1.75f, -2.99f, +2.99f, 0.51f, true, true));
            rows.add(new LowerDeckRow(-0.24f, -3.07f, +3.07f, 0.55f, true, true));
            rows.add(new LowerDeckRow(+1.30f, -3.00f, +3.00f, 0.58f, true, true));
            rows.add(new LowerDeckRow(+2.80f, -2.90f, +2.90f, 0.59f, true, true));
            rows.add(new LowerDeckRow(+4.32f, -2.80f, +2.80f, 0.62f, true, true));
            rows.add(new LowerDeckRow(+5.81f, -2.64f, +2.64f, 0.65f, true, true));
            rows.add(new LowerDeckRow(+7.31f, +0.18f, +2.45f, 0.66f, true, false));
            rows.add(new LowerDeckRow(+7.31f, -2.45f, -0.18f, 0.66f, false, true));
            rows.add(new LowerDeckRow(+8.80f, -2.19f, +2.19f, 0.68f, true, true));
            rows.add(new UpperDeckRow(+9.64f, +1.17f, +3.29f));
            rows.add(new UpperDeckRow(+7.89f, +1.36f, +3.29f));
            rows.add(new UpperDeckRow(+6.53f, +1.60f, +3.29f));
            rows.add(new UpperDeckRow(+5.12f, +1.74f, +3.29f));
            rows.add(new UpperDeckRow(+3.78f, +1.80f, +3.29f));
            rows.add(new UpperDeckRow(+2.10f, +2.07f, +3.11f));
            rows.add(new UpperDeckRow(+0.56f, +2.07f, +3.11f));
            rows.add(new UpperDeckRow(-0.82f, +2.07f, +3.11f));
            rows.add(new UpperDeckRow(-2.60f, +2.07f, +3.11f));
            rows.add(new UpperDeckRow(-5.24f, +1.82f, +3.13f));
            rows.add(new UpperDeckRow(-7.17f, +1.86f, +2.99f));
            rows.add(new UpperDeckRow(-8.99f, +1.31f, +2.99f));
            rows.add(new UpperDeckRow(-10.49f, +1.00f, +2.99f));
        } else {
            rows.add(new Rudder(ship, -11.4f, +0.87f, +2.455f));
            rows.add(new LowerDeckRow(-9.39f, -2.88f, +2.88f, +0.37f, true, true));
            rows.add(new LowerDeckRow(-7.92f, -2.88f, +2.88f, +0.37f, true, true));
            rows.add(new LowerDeckRow(-6.47f, -2.88f, +2.88f, +0.37f, true, true));
            rows.add(new LowerDeckRow(-4.84f, -2.88f, +2.88f, +0.37f, true, true));
            rows.add(new LowerDeckRow(-3.44f, -2.88f, +2.88f, +0.37f, true, true));
            rows.add(new LowerDeckRow(-1.96f, -2.88f, +2.88f, +0.37f, true, true));
            rows.add(new LowerDeckRow(-0.42f, -2.88f, +2.88f, +0.37f, true, true));
            rows.add(new LowerDeckRow(+1.10f, -2.88f, +2.88f, +0.37f, true, true));
            rows.add(new LowerDeckRow(+2.46f, -2.88f, +2.88f, +0.37f, true, true));
            rows.add(new LowerDeckRow(+4.17f, -2.84f, +2.84f, +0.37f, true, true));
            rows.add(new LowerDeckRow(+5.57f, -2.77f, +2.77f, +0.37f, true, true));
            rows.add(new LowerDeckRow(+7.01f, -2.42f, +2.42f, +0.37f, true, true));
            rows.add(new LowerDeckRow(+8.62f, -2.42f, +2.42f, +0.37f, true, true));
            rows.add(new UpperDeckRow(-9.39f, +1.01f, +3.41f));
            rows.add(new UpperDeckRow(-7.75f, +1.22f, +3.41f));
            rows.add(new UpperDeckRow(-6.02f, +1.35f, +3.41f));
            rows.add(new UpperDeckRow(-3.93f, +1.22f, +3.24f));
            rows.add(new UpperDeckRow(-2.50f, +1.22f, +3.24f));
            rows.add(new UpperDeckRow(-1.00f, +1.22f, +3.24f));
            rows.add(new UpperDeckRow(+0.50f, +1.22f, +3.24f));
            rows.add(new UpperDeckRow(+2.00f, +1.22f, +3.24f));
            rows.add(new UpperDeckRow(+3.50f, +1.22f, +3.24f));
            rows.add(new UpperDeckRow(+5.00f, +1.22f, +3.24f));
            rows.add(new UpperDeckRow(+6.50f, +1.22f, +3.24f));
            rows.add(new UpperDeckRow(+8.00f, +1.09f, +3.24f));
            rows.add(new UpperDeckRow(+9.50f, +0.83f, +3.24f));
        }
    }

    public boolean canAllocate(Unit unit) {
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).canFit(unit)) {
                return true;
            }
        }
        return false;
    }

    public ShipAllocation tryAllocate(Unit unit) {
        if (!unit.isWarrior()) {
            for (int i = 0; i < rows.size(); i++) {
                Row row = rows.get(i);
                if (row.needRowers()) {
                    row.seat(unit);
                    unit2row.put(unit, row);
                    return row.getAllocation(unit);
                }
            }
            for (int i = 0; i < rows.size(); i++) {
                Row row = rows.get(i);
                if (row.canFit(unit)) {
                    row.seat(unit);
                    unit2row.put(unit, row);
                    return row.getAllocation(unit);
                }
            }
        } else {
            for (int i = rows.size() - 1; i >= 0; i--) {
                Row row = rows.get(i);
                if (row.canFit(unit)) {
                    row.seat(unit);
                    unit2row.put(unit, row);
                    ShipAllocation alloc = row.getAllocation(unit);
                    if (alloc.getRole() == ShipAllocation.FIGHTING) {
                        unit.increaseRange(10f);
                    }
                    return alloc;
                }
            }
        }
        return null;
    }

    public void killCrew() {
        for (int i = 0; i < rows.size(); i++) {
            rows.get(i).killAll();
            unit2row.clear();
        }
    }

    public void removeUnit(Unit unit) {
        if (unit2row.containsKey(unit)) {
            Row row = unit2row.get(unit);
            row.exit(unit);
            unit.setReference(null);
            unit2row.remove(unit);
        }
    }

    public Unit exitUnit(UnitTemplate template) {
        // Bug fix (merged from boats_on_steam): getWeaponFactory() is @NonNull (see
        // UnitTemplate.getWeaponFactory()) - it never returns null even for peons, which have a
        // "no weapon" factory whose getType() returns null instead. The old check here
        // (getWeaponFactory() != null) was therefore always true, classifying every unit
        // including peons as a warrior and corrupting ship row/seating assignment.
        // //added by ikill240c
        boolean warrior = (template.getWeaponFactory().getType() != null); //added by ikill240c
        if (warrior) {
            for (int i = 0; i < rows.size(); i++) {
                Row row = rows.get(i);
                Unit unit = row.findUnit(template);
                if (unit != null) {
                    ShipAllocation alloc = row.getAllocation(unit);
                    row.exit(unit);
                    unit2row.remove(unit);
                    unit.setReference(null);
                    unit.unmount();
                    if (alloc.getRole() == ShipAllocation.FIGHTING) {
                        unit.increaseRange(-8f);
                    }
                    return unit;
                }
            }
        } else {
            // Merged from boats_on_steam: first pass only considers rows that can actually spare
            // a peon (see Row.canSparePeons()) - e.g. a rowing bench with excess peons beyond its
            // minimum required rowers. Only falls back to pulling from ANY row (including one that
            // would be left unable to row) if no row can spare one. Previously this method had no
            // such distinction at all, so a peon could be pulled from a bench's last rower just as
            // readily as an excess one, leaving that bench unable to function.
            // //added by ikill240c
            for (int i = rows.size() - 1; i >= 0; i--) { //added by ikill240c
                Row row = rows.get(i); //added by ikill240c
                Unit unit = row.findUnit(template); //added by ikill240c
                if (unit != null && row.canSparePeons()) { //added by ikill240c
                    row.exit(unit); //added by ikill240c
                    unit2row.remove(unit); //added by ikill240c
                    unit.setReference(null); //added by ikill240c
                    unit.unmount(); //added by ikill240c
                    return unit; //added by ikill240c
                } //added by ikill240c
            } //added by ikill240c
            for (int i = rows.size() - 1; i >= 0; i--) {
                Row row = rows.get(i);
                Unit unit = row.findUnit(template);
                if (unit != null) {
                    ShipAllocation alloc = row.getAllocation(unit);
                    row.exit(unit);
                    unit2row.remove(unit);
                    unit.setReference(null);
                    unit.unmount();
                    return unit;
                }
            }
        }
        return null;
    }

    private boolean isRock(Class type) {
        return type == RockAxeWeapon.class || type == RockSpearWeapon.class;
    }

    private boolean isIron(Class type) {
        return type == IronAxeWeapon.class || type == IronSpearWeapon.class;
    }

    private boolean isRubber(Class type) {
        return type == RubberAxeWeapon.class || type == RubberSpearWeapon.class;
    }

    private boolean isSameType(Class type, Unit unit) {
        if (!unit.isWarrior()) {
            return type == Unit.class;
        } else {
            Class unitType = unit.getWeaponFactory().getType();
            if (isRock(unitType) && isRock(type)) return true;
            if (isIron(unitType) && isIron(type)) return true;
            if (isRubber(unitType) && isRubber(type)) return true;
            return false;
        }
    }

    public int countUnitsOfType(Class type) {
        int result = 0;
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            List<Unit> units = row.allUnits();
            for (int j = 0; j < units.size(); j++) {
                Unit unit = units.get(j);
                if (isSameType(type, unit)) {
                    result++;
                }
            }
        }
        return result;
    }

    public int countUnits() {
        return unit2row.size();
    }

    public int countPeons() {
        int result = 0;
        Unit[] units = unit2row.keySet().toArray(new Unit[0]);
        for (int j = 0; j < units.length; j++) {
            Unit unit = units[j];
            if (!unit.isWarrior()) {
                result++;
            }
        }
        return result;
    }

    public Unit pickVictim(float random) {
        int index = StrictMath.round(random * 120);
        if (index >= unit2row.size()) {
            return null;
        }
        Iterator<Map.Entry<Unit, Row>> it = unit2row.entrySet().iterator();
        Map.Entry<Unit, Row> victim = it.next();
        for (int i = 0; i < index; i++) {
            victim = it.next();
        }
        Unit unit = victim.getKey();
        Row row = victim.getValue();
        return unit;
    }

    public int countRowers() {
        int result = 0;
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            result += row.countRowers();
        }
        return result;
    }
}
