package com.oddlabs.tt.model.weapon;

import com.oddlabs.tt.model.Abilities;
import com.oddlabs.tt.model.Building;
import com.oddlabs.tt.model.Selectable;
import com.oddlabs.tt.model.ShipProxy; //added by ikill240p 2026-09-14 - explicitly excluded when extending conversion to ships, see the comment on getSuccessRate()
import com.oddlabs.tt.model.Unit;
import com.oddlabs.tt.model.UnitTemplate;
import com.oddlabs.tt.pathfinder.FindOccupantFilter;
import com.oddlabs.tt.pathfinder.UnitGrid;
import com.oddlabs.tt.player.Player;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

public final class Convert implements Magic {//added by ikill240c
    // Per-target-type success rates - each roll uses World.getRandom(), the game's own
    // deterministic RNG (the same source IdleController etc. already use for vicinity-scan
    // timing), NOT Math.random() or any other non-deterministic source, since this is a lockstep
    // multiplayer simulation where every client must compute the identical outcome from the same
    // inputs. //added by ikill240c
    private static final float SUCCESS_RATE_PEON = 0.85f; //added by ikill240c
    private static final float SUCCESS_RATE_ROCK_WARRIOR = 0.75f; //added by ikill240c
    private static final float SUCCESS_RATE_IRON_WARRIOR = 0.55f; //added by ikill240c
    private static final float SUCCESS_RATE_RUBBER_WARRIOR = 0.35f; //added by ikill240c
    private static final float SUCCESS_RATE_CHIEF = 0.15f; //added by ikill240c
    private static final float SUCCESS_RATE_BUILDING = 0.075f; //added by ikill240c

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

        // Selectable.genericClass() already scans EVERY selectable type, buildings included - the
        // previous version's "if (!(selectable instanceof Unit)) continue;" filter inside
        // animate() below was the only thing excluding buildings/towers from ever being
        // considered, not this scan itself. //added by ikill240c
        var filter = new FindOccupantFilter<>(src.getPositionX(), src.getPositionY(), hit_radius, src,
                Selectable.genericClass());
        UnitGrid unit_grid = owner.getWorld().getUnitGrid();
        unit_grid.scan(filter, UnitGrid.toGridCoordinate(src.getPositionX()),
                UnitGrid.toGridCoordinate(src.getPositionY()));
        target_list = filter.getResult();
    }

    /**
     * Returns the success rate for converting this specific target, per the exact per-type rates
     * requested: 90% peons, 80% rock warriors, 60% iron warriors, 50% rubber warriors, 30%
     * chiefs, 5% buildings/towers/ships. Returns 0 (never succeeds) for any target that doesn't
     * match a recognized category, rather than guessing or defaulting to a nonzero rate for
     * something this classification doesn't understand. ShipProxy - the placeholder occupant used
     * for a ship while it's docked - is explicitly excluded even though it IS a Building: it
     * forwards hit()/repair() to the real Ship it represents but does NOT forward isDead() (that
     * method is final on Selectable), so if both the proxy and the real Ship ever showed up as
     * separate targets in the same cast, converting both would risk destroying the ship once but
     * then constructing TWO replacements. Only the real Ship object is convertible.
     * //added by ikill240c
     */
    private static float getSuccessRate(@NonNull Selectable<?> target) { //added by ikill240c
        if (target instanceof ShipProxy) { //added by ikill240p 2026-09-14 - see the method-level comment above for why the proxy specifically is excluded
            return 0f; //added by ikill240p 2026-09-14
        } //added by ikill240p 2026-09-14
        if (target instanceof Building) { //added by ikill240p 2026-09-14 - widened from "instanceof LandBuilding" so ships (which also extend Building) get the same building rate
            return SUCCESS_RATE_BUILDING; //added by ikill240c
        } //added by ikill240c
        if (!(target instanceof Unit unit)) { //added by ikill240c
            return 0f; //added by ikill240c
        } //added by ikill240c
        if (unit.getAbilities().hasAbilities(Abilities.MAGIC)) { //added by ikill240c
            return SUCCESS_RATE_CHIEF; //added by ikill240c
        } //added by ikill240c
        if (unit.getAbilities().hasAbilities(Abilities.HARVEST)) { //added by ikill240c
            return SUCCESS_RATE_PEON; //added by ikill240c
        } //added by ikill240c
        Class<? extends ThrowingWeapon> weapon_type = unit.getWeaponFactory().getType(); //added by ikill240c
        if (weapon_type == RockAxeWeapon.class || weapon_type == RockSpearWeapon.class) { //added by ikill240c
            return SUCCESS_RATE_ROCK_WARRIOR; //added by ikill240c
        } //added by ikill240c
        if (weapon_type == IronAxeWeapon.class || weapon_type == IronSpearWeapon.class) { //added by ikill240c
            return SUCCESS_RATE_IRON_WARRIOR; //added by ikill240c
        } //added by ikill240c
        if (weapon_type == RubberAxeWeapon.class || weapon_type == RubberSpearWeapon.class) { //added by ikill240c
            return SUCCESS_RATE_RUBBER_WARRIOR; //added by ikill240c
        } //added by ikill240c
        return 0f; // unrecognized unit type - fail safe rather than guess a rate //added by ikill240c
    }

    @Override
    public void animate(float t) {
        List<Selectable<?>> to_convert = new ArrayList<>(); //added by ikill240c
        for (Selectable<?> selectable : target_list) {
            if (selectable.isDead()) //added by ikill240c
                continue;
            if (selectable instanceof Unit unit) { //added by ikill240c
                if (unit.isMounted())
                    continue;
                if (!affects_chieftains && unit.getAbilities().hasAbilities(Abilities.MAGIC))
                    continue;
            } else if (selectable instanceof ShipProxy || !(selectable instanceof Building)) { //added by ikill240p 2026-09-14 - widened from "!(selectable instanceof LandBuilding)" to also allow real Ship targets through, while still excluding the ship-docking proxy
                continue; // only units and land buildings/towers/ships are convertible //added by ikill240c
            } //added by ikill240c

            float dx = selectable.getPositionX() - start_x; //added by ikill240c
            float dy = selectable.getPositionY() - start_y; //added by ikill240c
            float squared_dist = dx * dx + dy * dy;
            if (owner.isEnemy(selectable.getOwner()) && squared_dist < hit_radius * hit_radius) { //added by ikill240c
                to_convert.add(selectable); //added by ikill240c
            }
        }

        for (Selectable<?> selectable : to_convert) { //added by ikill240c
            float success_rate = getSuccessRate(selectable); //added by ikill240c
            if (owner.getWorld().getRandom().nextFloat() >= success_rate) //added by ikill240c
                continue; // roll failed - this specific target resists conversion //added by ikill240c

            if (selectable instanceof Building building) { //added by ikill240p 2026-09-14 - widened from "instanceof LandBuilding" so a successfully-rolled Ship target also routes here (ShipProxy never reaches this list, see getSuccessRate())
                convertBuilding(building); //added by ikill240c
            } else if (selectable instanceof Unit unit) { //added by ikill240c
                convertUnit(unit); //added by ikill240c
            } //added by ikill240c
        }

        interrupt();
    }

    /**
     * Buildings (and now ships - see below) can't change owner (Selectable.owner is final, by
     * design - see Selectable.java), so "converting" one means destroying the original and
     * constructing a fresh copy for the converter's own race at the same grid position, mirroring
     * exactly how Player.buildBuilding() constructs a new building (template.create() -> place()
     * -> repair(maxHP) to instantly complete it) rather than inventing a different construction
     * path. Uses the converter's OWN race's version of the same building type, not the original
     * owner's race's version - getTemplateID() doubles as the race-independent Race.BUILDING_*
     * slot index (confirmed by Race.java's own constructor assertion,
     * `buildings[i].getTemplateID() == i`), so this needs no separate lookup table. Works
     * unchanged for a Ship target too since Race.BUILDING_SHIP sits in that very same buildings[]
     * array (Race.java's own constructor registers it right alongside Quarters/Armory/Tower), and
     * Ship's own hit()/place()/repair()/getHitPoints()/getTemplate() all mirror LandBuilding's
     * contract exactly. //added by ikill240c
     */
    private void convertBuilding(@NonNull Building building) { //added by ikill240p 2026-09-14 - widened from LandBuilding to Building so this also accepts a Ship
        if (building.isDead()) //added by ikill240p 2026-09-14 - defensive: guards against ever double-processing the same underlying structure via two different target references in one cast
            return; //added by ikill240p 2026-09-14
        int building_type = building.getTemplate().getTemplateID(); //added by ikill240c
        int grid_x = building.getGridX(); //added by ikill240c
        int grid_y = building.getGridY(); //added by ikill240c

        // Was missing the "has room" check convertUnit() below already has for units - place()
        // asserts isPlacingLegal(), which calls owner.canBuild(building_type), which checks the
        // CONVERTER's own building count against the map's Max Buildings cap. A conversion adds a
        // building to the converter's own count (it doesn't free up one of the enemy's - that
        // enemy building is simply destroyed), so a converter already at their own cap hit this
        // assertion and crashed the entire game the moment a Convert cast landed on a building.
        // Mirrors convertUnit()'s own "always remove the enemy [target] first" comment: the enemy
        // building is always destroyed either way, since that's this spell's core destructive
        // effect: only the replacement construction is gated by room. //added by ikill240c
        boolean has_room = owner.canBuild(building_type); //added by ikill240c

        building.hit(building.getHitPoints() + 1000, 0f, 1f, owner); //added by ikill240c

        if (!has_room) //added by ikill240c
            return; // no room for a replacement, but the enemy building has already been destroyed //added by ikill240c

        Building new_building = owner.getRace().getBuildingTemplate(building_type).create(owner, grid_x, grid_y); //added by ikill240c
        new_building.place(); //added by ikill240c
        new_building.repair(new_building.getTemplate().getMaxHitPoints()); //added by ikill240c
    }

    private void convertUnit(@NonNull Unit unit) { //added by ikill240c
        boolean is_chieftain = unit.getAbilities().hasAbilities(Abilities.MAGIC);
        UnitTemplate template = unit.getTemplate();
        float x = unit.getPositionX();
        float y = unit.getPositionY();
        // Captured before hit() below eliminates the original unit and before the recipient's own
        // race is compared against it, so a cross-race converted chieftain can be tagged with its
        // ORIGINAL race for casting purposes (see Unit.setMagicRaceOverride()). Stored as a plain
        // race INDEX now (not a Race object) to match Unit.setMagicRaceOverride()'s signature -
        // see that method's comment for why. //added by ikill240c
        int original_race_index = unit.getOwner().getPlayerInfo().getRace();

        // Always remove the enemy unit first so Convert still performs its core effect even when
        // the caster is already at unit-cap. Only the replacement spawn is gated by room.
        // //added by ikill240c 2026-09-09 15:05
        unit.hit(unit.getHitPoints() + 1000, 0f, 1f, owner);

        boolean has_room = is_chieftain
                || owner.getUnitCountContainer().getNumSupplies() < owner.getUnitCountContainer().getMaxSupplyCount();

        if (!has_room)
            return; // no room for a replacement, but the enemy has already been eliminated //added by ikill240c 2026-09-09 15:05

        Unit converted = new Unit(owner, x, y, null, template);

        // A converted chieftain is fully playable immediately (selectable, castable via the normal
        // magic UI which is driven by selection, not by Player.chieftain) - this just also tracks it
        // so the recipient's own AI (if any) can make magic decisions for it too, and so more than
        // one chieftain can exist at once without disturbing the single "primary" chieftain slot.
        // //added by ikill240c 2026-09-08 17:00
        if (is_chieftain) {
            owner.addExtraChieftain(converted);
            // Convert an opposite-race chieftain -> gain access to magic from both races: this
            // converted chieftain casts using ITS OWN original race's spells (via the override),
            // while the recipient's own chieftain(s) keep casting the recipient's own race's spells
            // as normal - both are simultaneously selectable and castable, achieving "use all
            // magics from both races" without changing what the recipient's own race means anywhere
            // else in the game. //added by ikill240c
            if (original_race_index != owner.getPlayerInfo().getRace()) {
                converted.setMagicRaceOverride(original_race_index);
                // getEffectiveMaxHitPoints() only reflects the override from this point onward -
                // the unit was already constructed (and hit_points set) using the OWNER's race
                // multiplier a few lines up, before the override existed. Healing to full now
                // corrects that immediately instead of leaving the chief under-healed relative to
                // its true max until the next time it takes damage or heals naturally. Healing by
                // getEffectiveMaxHitPoints() itself (rather than Integer.MAX_VALUE) guarantees
                // reaching full health with no risk of the heal amount overflowing hit_points'
                // addition inside heal(). //added by ikill240c
                converted.heal(converted.getEffectiveMaxHitPoints());
            }
        }
    }

    @Override
    public void interrupt() {
        owner.getWorld().getAnimationManagerGameTime().removeAnimation(this);
    }
}
