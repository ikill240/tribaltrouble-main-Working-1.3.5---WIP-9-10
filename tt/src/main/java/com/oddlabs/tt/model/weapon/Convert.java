package com.oddlabs.tt.model.weapon;

import com.oddlabs.tt.model.Abilities;
import com.oddlabs.tt.model.Selectable;
import com.oddlabs.tt.model.Unit;
import com.oddlabs.tt.model.UnitTemplate;
import com.oddlabs.tt.pathfinder.FindOccupantFilter;
import com.oddlabs.tt.pathfinder.UnitGrid;
import com.oddlabs.tt.player.Player;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

public final class Convert implements Magic {//added by ikill240c
    private final float hit_radius;
    private final @NonNull Player owner;
    private final float start_x;
    private final float start_y;
    // whether enemy chieftains can be converted by this cast; customizable per race/factory //added by ikill240c 2026-09-08 03:00
    private final boolean affects_chieftains;
    private final @NonNull Iterable<? extends Selectable<?>> target_list;

    // constructor now accepts affects_chieftains so the toggle is actually wired through //added by ikill240c 2026-09-08 03:00
    public Convert(float offset_x, float offset_y, float hit_radius, @NonNull Unit src, boolean affects_chieftains) {
        this.hit_radius = hit_radius;
        this.owner = src.getOwner();
        this.affects_chieftains = affects_chieftains; // store the toggle passed in from the factory //added by ikill240c 2026-09-08 03:00

        start_x = src.getPositionX() + offset_x * src.getDirectionX() - offset_y * (-src.getDirectionY());
        start_y = src.getPositionY() + offset_x * src.getDirectionY() + offset_y * src.getDirectionX();

        var filter = new FindOccupantFilter<>(src.getPositionX(), src.getPositionY(), hit_radius, src,
                Selectable.genericClass());
        UnitGrid unit_grid = owner.getWorld().getUnitGrid();
        unit_grid.scan(filter, UnitGrid.toGridCoordinate(src.getPositionX()),
                UnitGrid.toGridCoordinate(src.getPositionY()));
        target_list = filter.getResult();
    }

    @Override
    public void animate(float t) {
        List<Unit> to_convert = new ArrayList<>();
        for (Selectable<?> selectable : target_list) {
            if (!(selectable instanceof Unit unit))
                continue;
            if (unit.isDead() || unit.isMounted())
                continue;
            if (!affects_chieftains && unit.getAbilities().hasAbilities(Abilities.MAGIC))
                continue;

            float dx = unit.getPositionX() - start_x;
            float dy = unit.getPositionY() - start_y;
            float squared_dist = dx * dx + dy * dy;
            if (owner.isEnemy(unit.getOwner()) && squared_dist < hit_radius * hit_radius) {
                to_convert.add(unit);
            }
        }

        for (Unit unit : to_convert) {
            boolean is_chieftain = unit.getAbilities().hasAbilities(Abilities.MAGIC);
            UnitTemplate template = unit.getTemplate();
            float x = unit.getPositionX();
            float y = unit.getPositionY();

            // Always remove the enemy unit first so Convert still performs its core effect even when
            // the caster is already at unit-cap. Only the replacement spawn is gated by room.
            // //added by ikill240c 2026-09-09 15:05
            unit.hit(unit.getHitPoints() + 1000, 0f, 1f, owner);

            boolean has_room = is_chieftain
                    || owner.getUnitCountContainer().getNumSupplies() < owner.getUnitCountContainer().getMaxSupplyCount();

            if (!has_room)
                continue; // no room for a replacement, but the enemy has already been eliminated //added by ikill240c 2026-09-09 15:05

            Unit converted = new Unit(owner, x, y, null, template);

            // A converted chieftain is fully playable immediately (selectable, castable via the normal
            // magic UI which is driven by selection, not by Player.chieftain) - this just also tracks it
            // so the recipient's own AI (if any) can make magic decisions for it too, and so more than
            // one chieftain can exist at once without disturbing the single "primary" chieftain slot.
            // //added by ikill240c 2026-09-08 17:00
            if (is_chieftain) {
                owner.addExtraChieftain(converted);
            }
        }

        interrupt();
    }

    @Override
    public void interrupt() {
        owner.getWorld().getAnimationManagerGameTime().removeAnimation(this);
    }
}
