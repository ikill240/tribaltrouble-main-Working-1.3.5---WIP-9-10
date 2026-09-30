package com.oddlabs.tt.model;

import com.oddlabs.geometry.AnimationInfo;
import com.oddlabs.tt.audio.AudioParameters;
import com.oddlabs.tt.audio.AudioPlayer;
import com.oddlabs.tt.landscape.LandscapeTarget;
import com.oddlabs.tt.model.behaviour.DefendController;
import com.oddlabs.tt.model.behaviour.GuardController; //added by ikill240c
import com.oddlabs.tt.model.behaviour.PatrolController; //added by ikill240c
import com.oddlabs.tt.model.behaviour.DieBehaviour;
import com.oddlabs.tt.model.behaviour.DieController;
import com.oddlabs.tt.model.behaviour.EnterController;
import com.oddlabs.tt.model.behaviour.FollowController; //added by ikill240c
import com.oddlabs.tt.model.behaviour.GatherController;
import com.oddlabs.tt.model.behaviour.HuntController;
import com.oddlabs.tt.model.behaviour.IdleController;
import com.oddlabs.tt.model.behaviour.MagicController;
import com.oddlabs.tt.model.behaviour.PlaceBuildingController;
import com.oddlabs.tt.model.behaviour.RepairController;
import com.oddlabs.tt.model.behaviour.ShipAttackController;
import com.oddlabs.tt.model.behaviour.SittingController;
import com.oddlabs.tt.model.behaviour.StunController;
import com.oddlabs.tt.model.behaviour.WalkBehaviour;
import com.oddlabs.tt.model.behaviour.WalkController;
import com.oddlabs.tt.model.weapon.IronAxeWeapon;
import com.oddlabs.tt.model.weapon.IronSpearWeapon;
import com.oddlabs.tt.model.weapon.RockAxeWeapon;
import com.oddlabs.tt.model.weapon.RockSpearWeapon;
import com.oddlabs.tt.model.weapon.RubberAxeWeapon;
import com.oddlabs.tt.model.weapon.RubberSpearWeapon;
import com.oddlabs.tt.model.weapon.WeaponFactory;
import com.oddlabs.tt.particle.BalancedParametricEmitter;
import com.oddlabs.tt.particle.StunFunction;
import com.oddlabs.tt.pathfinder.Movable;
import com.oddlabs.tt.pathfinder.Occupant;
import com.oddlabs.tt.pathfinder.PathFinder;
import com.oddlabs.tt.pathfinder.PathTracker;
import com.oddlabs.tt.pathfinder.Region;
import com.oddlabs.tt.pathfinder.TargetRegionFinder;
import com.oddlabs.tt.pathfinder.UnitGrid;
import com.oddlabs.tt.player.Player;
import com.oddlabs.tt.render.SpriteKey;
import com.oddlabs.tt.util.Target;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Unit extends Selectable<UnitTemplate> implements Occupant, Movable {

    private static final float IDLE_SPEED = 1f / 2.5f;
    private static final float TRANSPORT_SPEED_SCALE = 4f / 5f;

    private static final int PENALTY_INCREMENT = 3;
    private static final int INITIAL_PATH_PENALTY = 5;
    private static final float[] MAX_MAGIC_ENERGY = new float[]{40f, 70f, 90f};//{40f, 70f, 90f} //added by ikill240c
    // Only used for the array's LENGTH now (how many magic slots exist) - the actual per-slot cost
    // VALUES are customizable, read from World instead (see getMagicCost() below), so this array's
    // own values are effectively just the fallback defaults if something reads it directly.
    // //added by ikill240c
    private float getMagicCost(int index) { //added by ikill240c
        return switch (index) {
            case 0 -> getOwner().getWorld().getMagic1Cost();
            case 1 -> getOwner().getWorld().getMagic2Cost();
            case 2 -> getOwner().getWorld().getMagic3Cost();
            default -> MAX_MAGIC_ENERGY[index]; // out-of-range guard, shouldn't normally happen //added by ikill240c
        };
    }

    // One active Chiefs Courage buff instance on this unit. Multiple can be active at once
    // (stacking - see applyCourageBuff()/recomputeCourageTotals()) instead of a second trigger just
    // overwriting whatever buff was already running. speed_mult/attack_speed_mult/combat_mult are
    // stored as absolute multipliers (matching applyCourageBuff()'s existing parameter shape), but
    // recomputeCourageTotals() sums the BONUS portion (value - 1) of each active stack rather than
    // multiplying the multipliers together - multiplying several ~2.5x multipliers together would
    // explode exponentially once stacking is allowed. //added by ikill240p 2026-09-14
    private static final class CourageStack { //added by ikill240p 2026-09-14
        float time_remaining; //added by ikill240p 2026-09-14 - ticked down independently per stack in doAnimate(), see below
        final float speed_mult; //added by ikill240p 2026-09-14
        final float range_bonus; //added by ikill240p 2026-09-14
        final float attack_speed_mult; //added by ikill240p 2026-09-14
        final float combat_mult; //added by ikill240p 2026-09-14
        final float cooldown_reduction; //added by ikill240p 2026-09-14

        CourageStack(float duration, float speed_mult, float range_bonus, float attack_speed_mult, //added by ikill240p 2026-09-14
                float combat_mult, float cooldown_reduction) { //added by ikill240p 2026-09-14
            this.time_remaining = duration; //added by ikill240p 2026-09-14
            this.speed_mult = speed_mult; //added by ikill240p 2026-09-14
            this.range_bonus = range_bonus; //added by ikill240p 2026-09-14
            this.attack_speed_mult = attack_speed_mult; //added by ikill240p 2026-09-14
            this.combat_mult = combat_mult; //added by ikill240p 2026-09-14
            this.cooldown_reduction = cooldown_reduction; //added by ikill240p 2026-09-14
        }
    }

    // Every currently-active Chiefs Courage buff instance on this unit. A new trigger pushes an
    // ADDITIONAL stack here (see applyCourageBuff()) instead of overwriting whatever was already
    // active, so casting magic again before the previous buff expires stacks on top of it.
    // //added by ikill240p 2026-09-14
    private final List<CourageStack> courage_stacks = new ArrayList<>(); //added by ikill240p 2026-09-14

    // Combined totals across every currently-active courage stack, recomputed by
    // recomputeCourageTotals() whenever a stack is added or one expires. These are exactly the
    // fields the rest of the class (getCourageAttackSpeedMultiplier(), getMetersPerSecond(),
    // doAnimate()'s magic-regen loop, etc.) already reads, so nothing downstream needs to change.
    // //added by ikill240p 2026-09-14
    private float courage_time_remaining = 0f;//added by ikill240c
    private float courage_speed_mult = 1f;//added by ikill240c
    private float courage_range_bonus = 0f;//added by ikill240c
    private float courage_attack_speed_mult = 1f;//added by ikill240c
    private float courage_combat_mult = 1f;//added by ikill240c
    // Fraction by which this unit's magic energy regen rate is boosted while Chiefs Courage is
    // active - see doAnimate()'s magic-energy loop for where this is actually applied, and
    // triggerChiefsCourage() for how it reaches allies too (not just the caster's own army, unlike
    // the combat buff fields above). //added by ikill240c
    private float courage_cooldown_reduction = 0f; //added by ikill240c

    private static final float[] COURAGE_DURATION = new float[]{6f, 9f, 16f}; // indexed by magic_index//added by ikill240c
    private static final float COURAGE_SPEED_MULT = 2.5f;//added by ikill240c og 1.3
    private static final float COURAGE_RANGE_BONUS = 5f;//added by ikill240c og 3
    private static final float COURAGE_ATTACK_SPEED_MULT = 2.5f;//added by ikill240c og 1.23
    private static final float COURAGE_COMBAT_MULT = 1.75f;//added by ikill240c og 1.15
    // Flat magic-cooldown-reduction fraction Chiefs Courage grants to the caster's own army AND
    // their allies (via the ally loop in triggerChiefsCourage()) - see that method's own comments
    // for why this is deliberately NOT part of the escalating buff set. //added by ikill240c
    private static final float COOLDOWN_REDUCTION = 0.03f; //added by ikill240c
    // Chiefs Courage now only affects allied units within this radius (world units, same scale as
    // ConvertFactory's ~30f hit_radius) of the casting chieftain, instead of unconditionally
    // buffing every allied unit on the entire map regardless of distance. //added by ikill240p 2026-09-14
    private static final float COURAGE_RADIUS = 80f; //added by ikill240p 2026-09-14

    private float stuck_check_x = Float.NaN;//added by ikill240c
    private float stuck_check_y = Float.NaN;//added by ikill240c
    private float stuck_time = 0f;//added by ikill240c

    private static final float GATHER_STUCK_DIST_SQ = 4f;//added by ikill240c
    private static final float GATHER_STUCK_SECONDS = 3f;//added by ikill240c
    // Separate, longer threshold for the general "any unit, any controller" case below - popping
    // the controller entirely (the generic fallback when a controller doesn't handle onStuck()
    // itself) is a more drastic action than gathering's own "just pick a different resource node",
    // so this stays more conservative to avoid a large group's normal, brief traffic-jam-style
    // congestion while walking together being mistaken for genuinely stuck. //added by ikill240c
    private static final float GENERAL_STUCK_SECONDS = 5f; //added by ikill240c og 8

    public static class Animation { //added by ikill240c 2026-09-10 - pure constants holder, never instantiated as an inner class instance; made static per errorprone ClassCanBeStatic warning
        public static final int IDLING = 0;
        public static final int MOVING = 1;
        public static final int THROWING = 2;
        public static final int DYING = 3;
        public static final int MAGIC = 4;
        public static final int THOR = 5;
        public static final int SITTING = 4;
        public static final int STEERING = 5;
        public static final int ROWING_RIGHT = 6;
        public static final int ROWING_LEFT = 7;
    }

    public static final int SPEAR_RELEASE_FRAME = 29;

    private final @Nullable UnitSupplyContainer supply_container;
    private final @Nullable String name;
    private final @NonNull PathTracker path_tracker;
    private final float[] magic_energy = new float[MAX_MAGIC_ENERGY.length];//added by ikill240c
    // Set only on a converted chieftain whose ORIGINAL race differs from the recipient player's own
    // race (see Convert.java) - lets that specific unit cast spells from its own original race instead
    // of always using getOwner().getRace(), which is what actually makes "convert an opposite-race
    // chief -> can use magic from both races" work: your own chieftain still casts your race's spells,
    // and the converted one casts its original race's spells, both selectable and castable normally.
    // Stored as a plain race INDEX (RacesResources.RACE_VIKINGS/RACE_NATIVES) rather than a Race
    // object, since the chief health multiplier (getEffectiveMaxHitPoints() below) also needs an
    // index to look up World.getChiefHealthMultiplier(int), and re-deriving an index from a Race
    // object isn't possible (Race itself doesn't store its own index). -1 means no override.
    // //added by ikill240c
    private int magic_race_override_index = -1;
    private int last_magic_index = -1;

    private @Nullable BalancedParametricEmitter stun_marker;
    private int hit_points;
    private float time_since_damage = 0f;//added by ikill240c
    // -1f sentinel means "not yet eligible/started" - mirrors the old AdvancedAI-only
    // chieftain_heal_timers Map's absence-check (a chieftain with no entry yet), but as a per-unit
    // field instead of a per-player Map entry, since this now needs to run for every chieftain
    // regardless of which player (human or AI) controls it - see doAnimate() below.
    // //added by ikill240c
    private float chieftain_heal_timer = -1f; //added by ikill240c
    private @NonNull int animation = Animation.IDLING;
    private float anim_speed;
    private float anim_time;
    private int path_penalty;
    private boolean imaginary;
    /**
     * unit is in a tower
     */
    private boolean mounted;
    private boolean on_ship = false;
    private float mount_offset = 0;
    private Building mounted_building;
    private float range_bonus;

    public Unit(@NonNull Player owner, float x, float y, @Nullable Target rally_point,
            @NonNull UnitTemplate unit_template) {
        this(owner, x, y, rally_point, unit_template, null);
    }

    public Unit(@NonNull Player owner, float x, float y, @Nullable Target rally_point,
            @NonNull UnitTemplate unit_template, @Nullable String name) {
        this(owner, x, y, rally_point, unit_template, name, true);
    }

    public Unit(@NonNull Player owner, float x, float y, @Nullable Target rally_point,
            @NonNull UnitTemplate unit_template, @Nullable String name, boolean notify_by_chieftain) {
        this(owner, x, y, rally_point, unit_template, name, notify_by_chieftain, false);
    }

    public Unit(@NonNull Player owner, float x, float y, @Nullable Target rally_point,
            @NonNull UnitTemplate unit_template, @Nullable String name, boolean notify_by_chieftain,
            boolean grid_targets_only) {
        this(owner, x, y, rally_point, unit_template, name, notify_by_chieftain, grid_targets_only, false);
    }

    /**
     * This unit's max HP after applying the chief health-by-race multiplier, for chieftains only -
     * regular units are unaffected (returns the template's base value unchanged). Uses this unit's own
     * EFFECTIVE race (the magic race override if one is set, matching doMagic()'s casting-race logic,
     * or the owner's race otherwise) so a converted chieftain is multiplied by its own original race's
     * setting, not its new owner's. Deliberately a live method, not a cached field, since a converted
     * chieftain's override is only set AFTER construction (see Convert.java) - this always reflects
     * the current override state rather than whatever was true at spawn time. //added by ikill240c
     */
    public final int getEffectiveMaxHitPoints() { //added by ikill240c
        int base = getTemplate().getMaxHitPoints();
        if (!getAbilities().hasAbilities(Abilities.MAGIC))
            return base;
        int race_index = magic_race_override_index >= 0 ? magic_race_override_index : getOwner().getPlayerInfo().getRace();
        return Math.round(base * getOwner().getWorld().getChiefHealthMultiplier(race_index));
    }

    public Unit(@NonNull Player owner, float x, float y, @Nullable Target rally_point,
            @NonNull UnitTemplate unit_template, @Nullable String name, boolean notify_by_chieftain,
            boolean grid_targets_only, boolean imaginary) {
        super(owner, unit_template);
        this.name = name;
        this.imaginary = imaginary;
        getAbilities().addAbilities(unit_template.getAbilities());
        register();
        hit_points = getEffectiveMaxHitPoints(); //added by ikill240c - was unit_template.getMaxHitPoints() directly; now applies the chief health-by-race multiplier for chieftains
        this.path_tracker = new PathTracker(getUnitGrid(), this);
        UnitSupplyContainerFactory factory = unit_template.getUnitSupplyContainerFactory();
        supply_container = factory != null ? (UnitSupplyContainer) factory.createContainer(this) : null;

        if (!imaginary) {
            findInitialPosition(x, y, grid_targets_only);
        }

        pushController(new IdleController(this, new AttackScanFilter(getOwner(), AttackScanFilter.UNIT_RANGE), true));
        if (!getAbilities().hasAbilities(Abilities.MAGIC) && !imaginary) {
            int result = getOwner().getUnitCountContainer().increaseSupply(1);
            assert (result == 1) : "No room for new unit in player unit container.";
        } else if (notify_by_chieftain) {
            owner.getWorld().getNotificationListener().newSelectableNotification(this);
        }
        if (rally_point != null) {
            Target unit_target;
            if (rally_point instanceof LandscapeTarget) {
                UnitGrid grid = getUnitGrid();
                List<Target> temp_occupants = new ArrayList<>();
                for (var s : getOwner().getUnits().getSet()) {
                    if (s.getCurrentController() instanceof WalkController) {
                        Target target = ((WalkController) s.getCurrentController()).getTarget();
                        if (!grid.isGridOccupied(target.getGridX(), target.getGridY())) {
                            grid.occupyGrid(target.getGridX(), target.getGridY(), this);
                            temp_occupants.add(target);
                        }
                    }
                }
                Target found_target = grid.findGridTargets(rally_point.getGridX(), rally_point.getGridY(), 1, true)[0];
                // findGridTargets(...)[0] can legitimately return null when no valid, unoccupied cell is
                // found near the rally point. Falling back to the raw rally_point itself - the same
                // fallback the non-LandscapeTarget branch below already uses - instead of crashing unit
                // spawn/deployment entirely. //added by ikill240c
                unit_target = found_target != null ? found_target : rally_point;
                for (Target target : temp_occupants) {
                    grid.freeGrid(target.getGridX(), target.getGridY(), this);
                }
            } else
                unit_target = rally_point;

            boolean aggressive = unit_template.getAbilities().hasAbilities(Abilities.THROW);
            setTarget(unit_target, Action.DEFAULT, aggressive);
        }
    }

    @Override
    protected @NonNull Unit self() {
        return this;
    }

    @Override
    protected final float getZError() {
        if (on_ship) {
            return 0.0f;
        } else {
            return getLandscapeError();
        }
    }

    @Override
    public final void visit(@NonNull ElementVisitor visitor) {
        visitor.visitUnit(this);
    }

    public final @Nullable UnitSupplyContainer getSupplyContainer() {
        return supply_container;
    }

    @Override
    public final String toString() {
        if (!isDead())
            return "Unit: " + hashCode() + " | getOwner() = " + getOwner() + " | mounted = " + mounted + " | getGridX() = " + getGridX() + " | getGridY() = " + getGridY();
        else
            return super.toString();
    }

    // Was updateGatherStuckCheck(), and its very first line required getPrimaryController()
    // instanceof GatherController - meaning stuck detection only ever existed for peons actively
    // gathering, and every other activity (warriors walking to attack, units following, entering
    // a building, any plain move) had no stuck detection or recovery at all, matching reports of
    // units getting stuck and never getting themselves unstuck. Now applies to any moving unit
    // regardless of controller type: onStuck() (see Controller's own comment) lets a specific
    // controller like GatherController keep handling it its own way (picking a different resource
    // node), and reports back whether it did; if not (the base no-op default, which is every
    // controller except GatherController), popController() is applied as a generic fallback so
    // the unit abandons whatever specific sub-task has it stuck rather than standing there
    // indefinitely - it falls back to whatever's beneath on its controller stack, or goes idle if
    // nothing is. Uses the longer GENERAL_STUCK_SECONDS threshold for that generic fallback
    // specifically (gathering's own recovery, being cheaper/safer, still fires at the original,
    // shorter GATHER_STUCK_SECONDS via the same accumulated stuck_time). //added by ikill240c
    private void updateStuckCheck(float t) {//added by ikill240c
        // The Expert, Ultra and Fable AIs were written against the reference project, which has no stuck
        // handling at all - they manage their own units. Cancelling their orders here left those units idle:
        // a big army queuing through a gap moves less than GATHER_STUCK_DIST_SQ in GENERAL_STUCK_SECONDS and
        // looks exactly like "stuck", and the AI still believed the units were on their way, so they stood
        // around until something attacked them. (The gather branch also reassigned their peons to other
        // resources behind the AI's back.) Humans and AdvancedAI keep this check. //added by ikill240c
        if (getOwner().usesReferenceCommandRules()) { //added by ikill240c
            stuck_time = 0f; //added by ikill240c
            stuck_check_x = Float.NaN; //added by ikill240c
            return; //added by ikill240c
        } //added by ikill240c
        if (!(isMoving() && getPrimaryController() != null)) {
            stuck_time = 0f;
            stuck_check_x = Float.NaN;
            return;
        }
        float x = getPositionX();
        float y = getPositionY();
        if (Float.isNaN(stuck_check_x)) {
            stuck_check_x = x;
            stuck_check_y = y;
            return;
        }
        float dx = x - stuck_check_x;
        float dy = y - stuck_check_y;
        if (dx * dx + dy * dy < GATHER_STUCK_DIST_SQ) {
            stuck_time += t;
            boolean is_gathering = getPrimaryController() instanceof GatherController<?>; //added by ikill240c
            float threshold = is_gathering ? GATHER_STUCK_SECONDS : GENERAL_STUCK_SECONDS; //added by ikill240c
            if (stuck_time > threshold) { //added by ikill240c
                if (!getPrimaryController().onStuck()) { //added by ikill240c
                    popController(); //added by ikill240c - generic fallback; see this method's own comment
                } //added by ikill240c
                stuck_time = 0f;
                stuck_check_x = x;
                stuck_check_y = y;
            }
        } else {
            stuck_check_x = x;
            stuck_check_y = y;
            stuck_time = 0f;
        }
    }
    public void reposition() {
        findInitialPosition(getPositionX(), getPositionY(), true);
    }

    private void findInitialPosition(float x, float y, boolean grid_targets_only) {
        UnitGrid unit_grid = getUnitGrid();
        Target reserved_target = unit_grid.findGridTargets(UnitGrid.toGridCoordinate(x), UnitGrid.toGridCoordinate(y),
                1, grid_targets_only)[0];
        setGridPosition(reserved_target.getGridX(), reserved_target.getGridY());
        setPosition(reserved_target.getPositionX(), reserved_target.getPositionY());

        // Orient initially towards world center
        float center = getOwner().getWorld().getHeightMap().getMetersPerWorld() / 2f;
        float dx = center - reserved_target.getPositionX();
        float dy = center - reserved_target.getPositionY();
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len > 0) {
            setDirection(dx / len, dy / len);
        }

        occupy();
        reinsert();
    }

    @Override
    public final int getStatusValue() {
        int tower_factor = 1;
        if (mounted)
            tower_factor = 3;
        return getTemplate().getStatusValue() * tower_factor;
    }

    public final void increaseRange(float amount) {
        assert !isDead();
        range_bonus += amount;
    }

    @Override
    public final AttackScanFilter.@NonNull Priority getAttackPriority() {
        assert !isDead();
        return getAbilities().hasAbilities(
                Abilities.BUILD) ? AttackScanFilter.Priority.PEON : AttackScanFilter.Priority.WARRIOR;
    }

    @Override
    public final void visit(@NonNull ToolTipVisitor visitor) {
        visitor.visitUnit(this);
    }

    public final @Nullable String getName() {
        return name;
    }

    public final int getHitPoints() {
        return hit_points;
    }

    public final float getCourageAttackSpeedMultiplier() {//added by ikill240c
        return courage_attack_speed_mult;
    }

    public final float getCourageCombatMultiplier() {//added by ikill240c
        return courage_combat_mult;
    }

    public final void applyCourageBuff(float duration, float speed_mult, float extra_range, float attack_speed_mult,
            float combat_mult, float cooldown_reduction) {//added by ikill240c
        if (isDead())
            return;
        // Pushes a new independent stack instead of overwriting the fields directly - a second
        // trigger while the first buff is still active now ADDS to it rather than resetting it.
        // //added by ikill240p 2026-09-14
        courage_stacks.add(new CourageStack(duration, speed_mult, extra_range, attack_speed_mult, combat_mult, //added by ikill240p 2026-09-14
                cooldown_reduction)); //added by ikill240p 2026-09-14
        recomputeCourageTotals(); //added by ikill240p 2026-09-14
    }

    // Recomputes courage_time_remaining/courage_speed_mult/courage_range_bonus/
    // courage_attack_speed_mult/courage_combat_mult/courage_cooldown_reduction from the current
    // courage_stacks list. courage_time_remaining becomes the LONGEST remaining duration across all
    // stacks (so the unit keeps showing/using a buff for as long as any one stack is still active);
    // the multiplier fields are "1 + sum of every active stack's (multiplier - 1) bonus" (additive
    // stacking of the bonus portion, not multiplicative); range_bonus and cooldown_reduction are
    // plain sums since they were already flat bonus values, not multipliers. //added by ikill240p 2026-09-14
    private void recomputeCourageTotals() {//added by ikill240p 2026-09-14
        float longest_remaining = 0f; //added by ikill240p 2026-09-14
        float speed_bonus_sum = 0f; //added by ikill240p 2026-09-14
        float range_bonus_sum = 0f; //added by ikill240p 2026-09-14
        float attack_speed_bonus_sum = 0f; //added by ikill240p 2026-09-14
        float combat_bonus_sum = 0f; //added by ikill240p 2026-09-14
        float cooldown_reduction_sum = 0f; //added by ikill240p 2026-09-14
        for (CourageStack stack : courage_stacks) { //added by ikill240p 2026-09-14
            if (stack.time_remaining > longest_remaining) //added by ikill240p 2026-09-14
                longest_remaining = stack.time_remaining; //added by ikill240p 2026-09-14
            speed_bonus_sum += stack.speed_mult - 1f; //added by ikill240p 2026-09-14
            range_bonus_sum += stack.range_bonus; //added by ikill240p 2026-09-14
            attack_speed_bonus_sum += stack.attack_speed_mult - 1f; //added by ikill240p 2026-09-14
            combat_bonus_sum += stack.combat_mult - 1f; //added by ikill240p 2026-09-14
            cooldown_reduction_sum += stack.cooldown_reduction; //added by ikill240p 2026-09-14
        }
        courage_time_remaining = longest_remaining; //added by ikill240p 2026-09-14
        range_bonus -= courage_range_bonus; // remove the OLD courage range contribution from the shared range_bonus field //added by ikill240p 2026-09-14
        courage_range_bonus = range_bonus_sum; //added by ikill240p 2026-09-14
        range_bonus += courage_range_bonus; // then add back in the freshly-recomputed total //added by ikill240p 2026-09-14
        courage_speed_mult = 1f + speed_bonus_sum; //added by ikill240p 2026-09-14
        courage_attack_speed_mult = 1f + attack_speed_bonus_sum; //added by ikill240p 2026-09-14
        courage_combat_mult = 1f + combat_bonus_sum; //added by ikill240p 2026-09-14
        courage_cooldown_reduction = cooldown_reduction_sum; //added by ikill240p 2026-09-14
    }

    public final float getTimeSinceDamage() {//added by ikill240c
        return time_since_damage;
    }

    public final void heal(int amount) {//added by ikill240c
        if (isDead())
            return;
        hit_points = Math.min(hit_points + amount, getEffectiveMaxHitPoints()); //added by ikill240c
    }

    public final void drown() {
        clearOrderQueue(); //added by ikill240c 2026-09-10 16:45
        clearControllerStack();
        setReference(null);
        mounted = false;
        on_ship = false;
        mount_offset = 0;
        if (!imaginary) {
            enable();
        }
        if (supply_container != null) {
            supply_container.resetSupply(LeftPaddle.class);
            supply_container.resetSupply(RightPaddle.class);
        }
        mounted_building = null;
        startDying();
    }

    public final void unmount() {
        assert !isDead();
        clearControllerStack();
        swapController(new IdleController(this, new AttackScanFilter(getOwner(), AttackScanFilter.UNIT_RANGE), true));
        mounted = false;
        on_ship = false;
        mount_offset = 0;
        enable();
        Building entrance = mounted_building.getEntrance();
        findInitialPosition(entrance.getPositionX(), entrance.getPositionY(), true);
        if (supply_container != null) {
            supply_container.resetSupply(LeftPaddle.class);
            supply_container.resetSupply(RightPaddle.class);
        }
        mounted_building = null;
    }

    public final void mount(@NonNull Building building) {
        assert !isDead();
        mounted_building = building;
        mount_offset = building.getTemplate().getMountOffset();
        if (!imaginary) {
            disable();
            free();
        }
        setPosition(building.getPositionX(), building.getPositionY());
        mounted = true;
        clearOrderQueue(); //added by ikill240c 2026-09-10 16:45
        clearControllerStack();
        swapController(new IdleController(this, new TowerAttackScanFilter(getOwner(), AttackScanFilter.TOWER_RANGE), false)); //added by ikill240c - was plain AttackScanFilter; see TowerAttackScanFilter's own comment for the tower-specific priority this now applies
    }

    /**
     * "Stand Ground": clears any current order and makes this unit hold its exact current
     * position, attacking anything that comes within its own attack range but never chasing or
     * moving otherwise. This is deliberately not a new Controller - it's the exact same
     * IdleController(..., can_move=false) mechanism mount(Building) above already uses for
     * tower-mounted units (which also never move, and already auto-attack from a fixed spot for
     * exactly that reason), just applied to a normal ground unit instead of a mounted one.
     * No-ops for units that already can't meaningfully "stand ground": already-mounted units
     * have their own version of this via mount() itself, and non-attacking units (e.g. peons)
     * have no attack behavior for this state to enable. //added by ikill240c
     */
    public final void standGround() { //added by ikill240c
        if (mounted || !getAbilities().hasAbilities(Abilities.ATTACK)) //added by ikill240c
            return; //added by ikill240c
        clearOrderQueue(); //added by ikill240c
        clearControllerStack(); //added by ikill240c
        swapController(new IdleController(this, new AttackScanFilter(getOwner(), AttackScanFilter.UNIT_RANGE), //added by ikill240c
                false)); //added by ikill240c
    }

    public final void mount(Ship ship, ShipAllocation ship_allocation) {
        assert !isDead();
        mounted_building = ship;
        mount_offset = ship_allocation.getOffset().z;
        if (!imaginary) {
            disable();
            free();
        }
        mounted = true;
        on_ship = true;
        setReference(ship);
        clearOrderQueue(); //added by ikill240c 2026-09-10 16:45
        clearControllerStack();
        switch (ship_allocation.getRole()) {
            case ShipAllocation.FIGHTING:
                swapController(
                        new ShipAttackController(
                                this,
                                ship,
                                new AttackScanFilter(
                                        getOwner(), AttackScanFilter.TOWER_RANGE + 10),
                                ship_allocation));
                break;
            default:
                swapController(new SittingController(this, ship, ship_allocation));
                break;
        }
    }

    public final boolean isMounted() {
        return mounted;
    }

    // Sends an existing (already deployed) unit back to work gathering a resource. Deployed
    // harvesters get a GatherController at creation time in LandBuilding.createHarvesters, but a
    // peon that has been interrupted - drafted into a defense group, finished a construction site,
    // or whose supply node ran out - drops back to IdleController and previously had no way back
    // into the economy, so the AI's workforce slowly drained into idle units.
    // //added by ikill240c 2026-09-10 15:10
    public final <S extends Supply> void initGather(@NonNull Class<S> supply_type, @Nullable Building drop_off) {
        assert !isDead();
        if (mounted)
            return;
        clearOrderQueue(); //added by ikill240c 2026-09-10 16:45
        clearControllerStack();
        if (drop_off != null && !drop_off.isDead())
            pushController(new GatherController<>(this, null, supply_type, drop_off));
        else
            pushController(new GatherController<>(this, null, supply_type));
    }

    @Override
    public final boolean isEnabled() {
        return !isDead() && !mounted;
    }

    public final float getMetersPerSecond() {//added by ikill240c
        assert !isDead();
        float base;
        if (getAbilities().hasAbilities(Abilities.HARVEST) && supply_container.getNumSupplies() > 0)
            base = TRANSPORT_SPEED_SCALE * getTemplate().getMetersPerSecond();
        else
            base = getTemplate().getMetersPerSecond();
        return base * courage_speed_mult;
    }

    public final void aimAtTarget(@NonNull Target target) {
        assert !isDead();
        float dx = target.getPositionX() - getPositionX();
        float dy = target.getPositionY() - getPositionY();
        float dir_len_inv = 1f / (float) Math.sqrt(dx * dx + dy * dy);
        dx *= dir_len_inv;
        dy *= dir_len_inv;
        setDirection(dx, dy);
    }

    public final void switchToIdleAnimation() {
        assert !isDead();
        switchAnimation(IDLE_SPEED, Animation.IDLING);
    }

    public final void switchToSittingAnimation() {
        assert !isDead();
        switchAnimation(IDLE_SPEED, Animation.SITTING);
    }

    public final void switchToSteeringAnimation() {
        assert !isDead();
        switchAnimation(IDLE_SPEED, Animation.STEERING);
    }

    public final void switchToRowingRightAnimation() {
        assert !isDead();
        assert supply_container != null;
        switchAnimation(IDLE_SPEED, Animation.ROWING_RIGHT);
        supply_container.increaseSupply(1, RightPaddle.class);
    }

    public final void switchToRowingLeftAnimation() {
        assert !isDead();
        assert supply_container != null;
        switchAnimation(IDLE_SPEED, Animation.ROWING_LEFT);
        supply_container.increaseSupply(1, LeftPaddle.class);
    }

    public final @NonNull WeaponFactory getWeaponFactory() {
        assert !isDead();
        return getTemplate().getWeaponFactory();
    }

    public final float getRange(@NonNull Target target) {
        assert !isDead();
        // Multiplier applied here rather than to the raw weapon factory value, since that's a shared,
        // immutable template field used by every unit of this type - applying it at the point of
        // consumption (same technique as the health multipliers) avoids needing to touch
        // RacesResources' large, hardcoded per-unit construction data at all. //added by ikill240c
        return (getWeaponFactory().getRange() + range_bonus) * getOwner().getWorld().getUnitRangeMultiplier()
                + target.getSize(); //added by ikill240c
    }

    @Override
    public final float getSize() {
        return 1.9f;
    }

    @Override
    public final @NonNull SpriteKey getSpriteRenderer() {
        return getTemplate().getSpriteRenderer();
    }

    @Override
    public final void doAnimate(float t) {//added by ikill240c
        time_since_damage += t;
        updateStuckCheck(t); // generalized from gatherer-only to any moving unit - see that method's own comment //added by ikill240c
        anim_time += anim_speed * t;
        if (isDead() || mounted)
            reinsert();
        getOwner().getWorld().updateGlobalChecksum(animation);

        if (getAbilities().hasAbilities(Abilities.MAGIC)) {
            // courage_cooldown_reduction speeds up regen (shorter effective cooldown) rather than
            // reducing the cost itself - e.g. 0.03 means magic charges 3% faster while active, matching
            // "decrease magic cooldown by 3%" more directly than discounting the energy cost would.
            // //added by ikill240c
            float regen_amount = t * (1f + courage_cooldown_reduction); //added by ikill240c
            for (int i = 0; i < magic_energy.length; i++) {
                increaseMagicEnergy(i, regen_amount); //added by ikill240c
            }

            // Idle-chieftain heal-over-time. Previously lived ONLY inside AdvancedAI's own tick
            // loop (nodeHealChieftain()), which meant a human player's own chieftain never healed
            // this way at all - only AI-controlled ones did, since AdvancedAI is AI-only logic.
            // Moved here so it runs for every chieftain regardless of who controls it; the world
            // settings driving it (getChieftainHealIdleSeconds()/getChieftainHealAmount()) were
            // already player-agnostic, they just weren't being read anywhere a human player's
            // units would ever hit. Also now checks against getEffectiveMaxHitPoints() (the
            // race-multiplied cap heal() itself already respects) instead of the old check's
            // getTemplate().getMaxHitPoints() (the un-multiplied base) - the old check could
            // incorrectly consider a chieftain "full" and stop healing before it actually reached
            // its true, race-adjusted max HP. //added by ikill240c
            if (getTimeSinceDamage() < getOwner().getWorld().getChieftainHealIdleSeconds() //added by ikill240c
                    || getHitPoints() >= getEffectiveMaxHitPoints()) { //added by ikill240c
                chieftain_heal_timer = -1f; // reset the cadence - see the field's own comment //added by ikill240c
            } else if (chieftain_heal_timer < 0f) { //added by ikill240c
                // Just became eligible - heal right away rather than waiting a full extra cadence
                // period on top of the idle threshold already waited for eligibility, matching the
                // original AI-only behavior exactly. //added by ikill240c
                heal(getOwner().getWorld().getChieftainHealAmount()); //added by ikill240c
                chieftain_heal_timer = 0f; //added by ikill240c
            } else { //added by ikill240c
                chieftain_heal_timer += t; //added by ikill240c
                if (chieftain_heal_timer >= getOwner().getWorld().getChieftainHealIdleSeconds()) { //added by ikill240c
                    heal(getOwner().getWorld().getChieftainHealAmount()); //added by ikill240c
                    chieftain_heal_timer = 0f; //added by ikill240c
                } //added by ikill240c
            } //added by ikill240c
        }

        if (!courage_stacks.isEmpty()) { //added by ikill240p 2026-09-14 - tick every active stack down independently instead of a single shared timer
            boolean any_expired = false; //added by ikill240p 2026-09-14
            var iterator = courage_stacks.iterator(); //added by ikill240p 2026-09-14
            while (iterator.hasNext()) { //added by ikill240p 2026-09-14
                CourageStack stack = iterator.next(); //added by ikill240p 2026-09-14
                stack.time_remaining -= t; //added by ikill240p 2026-09-14
                if (stack.time_remaining <= 0f) { //added by ikill240p 2026-09-14
                    iterator.remove(); // this stack's duration ran out - drop it and remove its contribution below //added by ikill240p 2026-09-14
                    any_expired = true; //added by ikill240p 2026-09-14
                }
            }
            if (any_expired) //added by ikill240p 2026-09-14 - only worth recomputing totals when the active set actually changed
                recomputeCourageTotals(); //added by ikill240p 2026-09-14
        }
    }

    public final void increaseMagicEnergy(int index, float amount) {
        magic_energy[index] += amount;
        if (magic_energy[index] > getMagicCost(index)) { //added by ikill240c
            magic_energy[index] = getMagicCost(index); //added by ikill240c
        }
    }

    @Override
    public final @NonNull PathTracker getTracker() {
        assert !isDead();
        return path_tracker;
    }

    @Override
    public final void markBlocking() {
        assert !isDead();
        path_penalty = Math.min(path_penalty + PENALTY_INCREMENT, STATIC - 1); // never gets STATIC
    }

    @Override
    public final int getPenalty() {
        assert !isDead();
        return isBlocking() ? Occupant.STATIC : path_penalty;
    }

    @Override
    protected final void removeDying() {
        // Was unconditional: `if (getAbilities().hasAbilities(Abilities.MAGIC)) setActiveChieftain(null);`
        // - that cleared the PRIMARY chieftain slot whenever ANY chieftain-ability unit died, including an
        // extra (converted) chieftain that was never the primary. Killing a converted chieftain would wipe
        // out the reference to a still-alive primary chieftain, which (among other things) made campaign
        // DefeatTrigger fire a false defeat the instant any converted chieftain died. Now only clears the
        // primary slot when this dying unit actually IS the tracked primary. Extra chieftains need no
        // explicit cleanup here - getExtraChieftains() already prunes dead entries lazily on every read.
        // //added by ikill240c 2026-09-11
        if (getAbilities().hasAbilities(Abilities.MAGIC) && getOwner().getChieftain() == this) {
            getOwner().setActiveChieftain(null);
        }
        if (!imaginary) {
            free();
            if (!getAbilities().hasAbilities(Abilities.MAGIC)) {
                int result = getOwner().getUnitCountContainer().increaseSupply(-1);
                assert result == -1;
            }
        }
        if (stun_marker != null) {
            stun_marker.done();
            stun_marker = null;
        }
        super.removeDying();
    }

    public final void removeNow() {
        assert !isDead();
        removeDying();
        remove();
    }

    @Override
    public final void free() {
        assert !isDead();
        UnitGrid unit_grid = getUnitGrid();
        if (unit_grid.getOccupant(getGridX(), getGridY()) == this) {
            unit_grid.freeGrid(getGridX(), getGridY(), this);
        }
        path_penalty = INITIAL_PATH_PENALTY;
    }

    @Override
    public final void occupy() {
        assert !isDead();
        UnitGrid unit_grid = getUnitGrid();
        unit_grid.occupyGrid(getGridX(), getGridY(), this);

        // stats
        getOwner().unitMoved();
    }

    @Override
    public final boolean isMoving() {
        return getCurrentBehaviour() instanceof WalkBehaviour;
    }

    /*	public final void moveNextAnimate() {
    	WalkBehaviour behaviour = (WalkBehaviour)getCurrentBehaviour();
    	behaviour.moveNextAnimate();
    }
     */
    @Override
    public final void hit(int damage, float direction_x, float direction_y, @NonNull Player owner) {
        if (damage > 0) time_since_damage = 0f;//added by ikill240c
        super.hit(damage, direction_x, direction_y, owner);
        if (mounted && !on_ship) {
            mounted_building.hit(damage, direction_x, direction_y, owner);
        } else if (!isDead()) {
            hit_points = Math.clamp(hit_points - damage, 0, getEffectiveMaxHitPoints()); //added by ikill240c
            if (hit_points == 0) {
                owner.unitKilled();
                if (mounted_building instanceof Ship ship) {
                    ship.getShipHR().removeUnit(this);
                    drown();
                } else {
                    startDying();
                    setDirection(-direction_x, -direction_y);
                }
            }
        }
    }

    public final void startDying() {
        getOwner().unitLost();

        mounted = false;
        on_ship = false;
        mount_offset = 0;

        pushController(new DieController(this));
        forceDecide();
        getOwner().getWorld().getAudio().newAudio(
                new AudioParameters(
                        getTemplate().getDeathSound(),
                        getPositionX(),
                        getPositionY(),
                        getPositionZ(),
                        AudioPlayer.AUDIO_RANK_DEATH,
                        AudioPlayer.AUDIO_DISTANCE_DEATH,
                        AudioPlayer.AUDIO_GAIN_DEATH,
                        AudioPlayer.AUDIO_RADIUS_DEATH,
                        1f + (getOwner().getWorld().getRandom().nextFloat() - .5f) * getTemplate().getDeathPitch()));
        removeDying();
    }

    public final void stun(float time) {
        float x = getPositionX() + getTemplate().getStunX() * getDirectionX() + getTemplate().getStunY() * (-getDirectionY());
        float y = getPositionY() + getTemplate().getStunX() * getDirectionY() + getTemplate().getStunY() * getDirectionX();
        float z = getOwner().getWorld().getHeightMap().getNearestHeight(x, y) + getTemplate().getStunZ() + mount_offset;

        if (stun_marker != null) {
            stun_marker.done();
        }
        stun_marker = createStunStar(x, y, z, time, (float) Math.PI / 2);
        pushController(new StunController(this, time));
        forceDecide();
    }

    public final boolean isWarrior() {
        Class type = getWeaponFactory().getType();
        if (type == RockAxeWeapon.class
                || type == IronAxeWeapon.class
                || type == RubberAxeWeapon.class
                || type == RockSpearWeapon.class
                || type == IronSpearWeapon.class
                || type == RubberSpearWeapon.class) {
            return true;
        }
        return false;
    }

    private @NonNull BalancedParametricEmitter createStunStar(float x, float y, float z, float time, float velocity) {
        int num_particles = 5;
        return new BalancedParametricEmitter(getOwner().getWorld(),
                new StunFunction(.4f, .15f), new Vector3f(x, y, z),
                velocity, 5f, (float) Math.PI * 2, (float) Math.PI * 2,
                num_particles, 0f, 2f,
                new Vector4f(1f, 1f, 1f, 1f), new Vector4f(0f, 0f, 0f, 0f),
                new Vector3f(.1f, .1f, .1f), new Vector3f(0f, 0f, 0f), time,
                GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                getOwner().getWorld().getRacesResources().getStarTextures(),
                getOwner().getWorld().getAnimationManagerGameTime());
    }

    public final boolean canAttack(@NonNull Target target, boolean kill_friendly) {
        assert !isDead();
        if (!(target instanceof Selectable<?> selectable) || !getAbilities().hasAbilities(Abilities.ATTACK))
            return false;
        Player target_player = selectable.getOwner();
        return kill_friendly || getOwner().isEnemy(target_player);
    }

    private boolean canBuild(@NonNull Target target) {
        return target instanceof Building building &&
                getAbilities().hasAbilities(Abilities.BUILD) &&
                !building.isPlaced();
    }

    private boolean canGather(@NonNull Target target) {
        return target instanceof Supply && getAbilities().hasAbilities(Abilities.BUILD);
    }

        private @Nullable Building nearestSupplyBuilding(@NonNull Supply supply) {
        UnitGrid grid = getUnitGrid();
        BuildingFinder finder = new BuildingFinder(getOwner(), Abilities.SUPPLY_CONTAINER);
        Region region = PathFinder.findPathRegion(grid, new TargetRegionFinder(grid, finder),
                grid.getRegion(supply.getGridX(), supply.getGridY()));
        Building building = region != null ? finder.getOccupantFromRegion(region, true) : null;
        return building != null ? building.getBase() : null;
    }

    private boolean canRepair(@NonNull Target target, boolean action_repair) {
        return target instanceof Building building &&
                getAbilities().hasAbilities(Abilities.BUILD) &&
                (action_repair || !building.getAbilities().hasAbilities(Abilities.SUPPLY_CONTAINER)
                        || !building.isComplete()) &&
                // getOwner() == building.getOwner() && building.isPlaced() && building.isDamaged();
                !getOwner().isEnemy(building.getOwner()) && building.isPlaced() && building.isDamaged();
    }

    public boolean canEnter(@NonNull Target target) {
        return target instanceof Building building &&
                !getAbilities().hasAbilities(Abilities.MAGIC) &&
                building.getUnitContainer() != null &&
                // Was `getOwner() == building.getOwner()` - a strict same-owner check that blocked
                // allies from garrisoning each other's towers even though nothing else about a tower
                // is exclusive to its owner (an ally's units already fight alongside you, defend your
                // buildings via nodeDefendAllies(), etc). Scoped specifically to towers
                // (BUILDING_TOWER) rather than every UnitContainer building - Quarters/Armory entry
                // has real per-owner meaning (population accounting, weapon stockpiles) that wasn't
                // part of what was asked for here, so those stay strictly same-owner.
                // //added by ikill240c 2026-09-14
                //
                // Extended to also allow Quarters/Armory, but for a different reason than towers:
                // entering either one already consumes the unit outright and credits the BUILDING
                // OWNER's own supply count (see WorkerUnitContainer.enter()/
                // ReproduceUnitContainer.enter() - both just call unit.removeNow() then
                // increaseSupply(1) on the container, which belongs to the building, not the
                // entering unit). That's exactly what "donate a unit to an ally" means: the unit
                // disappears from the donor's roster and becomes raw material the ally can later
                // deploy as their own new peon/warrior. No new container/controller logic is needed
                // for this - it falls entirely out of relaxing this same ownership check the way
                // towers already were. //added by ikill240c
                (getOwner() == building.getOwner() //added by ikill240c 2026-09-14
                        || (!getOwner().isEnemy(building.getOwner()) //added by ikill240c
                                && (building.getTemplate().getTemplateID() == Race.BUILDING_TOWER //added by ikill240c 2026-09-14
                                        || building.getTemplate().getTemplateID() == Race.BUILDING_QUARTERS //added by ikill240c
                                        || building.getTemplate().getTemplateID() == Race.BUILDING_ARMORY))) && //added by ikill240c
                building.getUnitContainer().canEnter(this);
    }

    @Override
    public final float getDefenseChance() {
        return getCurrentController() instanceof StunController ? 0 : super.getDefenseChance();
    }

    private void walkToTarget(@NonNull Target target, boolean scan_attack) {
        // findGridTargets(...)[0] can legitimately return null when no valid, unoccupied cell is found
        // near the given target. This is called on every move/attack/interact order in the game, so a
        // crash here would be very high-frequency. Falling back to the original (unrefined) target
        // instead of crashing - if it's genuinely unreachable, normal pathfinding/stuck-detection
        // handles that gracefully rather than a hard NPE. //added by ikill240c
        Target walkable_target = getUnitGrid().findGridTargets(target.getGridX(), target.getGridY(), 1, false)[0];
        pushController(new WalkController(this, walkable_target != null ? walkable_target : target, scan_attack));
    }

    @Override
    public void setTarget(@NonNull Target target, @NonNull Action action, boolean aggressive) {
        if (target == this)
            return;
        assert !target.isDead() : "Setting dead target";
        assert !mounted;
        if (target instanceof Building) {
            target = ((Building) target).getEntrance();
        }
        switch (action) {
            case DEFAULT:
                if (canBuild(target)) {
                    pushController(new PlaceBuildingController(this, (Building) target));
                } else if (canGather(target)) {
                    pushController(new GatherController(this, (Supply) target, ((Supply) target).getClass(),
                            nearestSupplyBuilding((Supply) target)));
                } else if (canRepair(target, false)) {
                    pushController(new RepairController(this, (Building) target));
                } else if (canEnter(target)) {
                    pushController(new EnterController(this, (Building) target));
                } else if (target instanceof Unit friendly_unit && !getOwner().isEnemy(friendly_unit.getOwner())) {
                    // Right click on your own unit or an ally: follow them indefinitely instead of
                    // walking to their position once and stopping there. Placed after all the more
                    // specific friendly-target interactions above (build/gather/repair/enter all
                    // require non-Unit targets, so this never shadows them) and before canAttack, which
                    // should only ever apply to actual enemies. //added by ikill240c
                    pushController(new FollowController(this, target, aggressive)); //added by ikill240c - now respects Settings.aggressive_units instead of silently ignoring it
                } else if (canAttack(target, false)) {
                    pushController(new HuntController(this, (Selectable<?>) target));
                } else {
                    walkToTarget(target, aggressive);
                }
                break;
            case MOVE:
                if (canEnter(target)) {
                    pushController(new EnterController(this, (Building) target));
                } else {
                    walkToTarget(target, false);
                }
                break;
            case ATTACK:
                if (canAttack(target, true)) {
                    pushController(new HuntController(this, (Selectable<?>) target));
                } else {
                    walkToTarget(target, true);
                }
                break;
            case GATHER_REPAIR:
                if (canGather(target)) {
                    pushController(new GatherController(this, (Supply) target, ((Supply) target).getClass(),
                            nearestSupplyBuilding((Supply) target)));
                } else if (canRepair(target, true)) {
                    pushController(new RepairController(this, (Building) target));
                }
                break;
            case DEFEND:
                pushController(new DefendController(this, target));
                break;
            case GUARD: //added by ikill240c
                pushController(new GuardController(this, target)); //added by ikill240c
                break; //added by ikill240c
            case PATROL: //added by ikill240c
                // point_a is wherever the unit is standing the moment the order is issued, not
                // re-evaluated later - the patrol route is fixed at order time, matching
                // conventional RTS patrol UX ("patrol from here to there"), not a route that
                // silently shifts if something else moves the unit before the order starts.
                // //added by ikill240c
                pushController(new PatrolController(this, //added by ikill240c
                        new com.oddlabs.tt.landscape.LandscapeTarget(getGridX(), getGridY()), target)); //added by ikill240c - always aggressive now, see PatrolController's own comment for why the "aggressive" parameter was removed
                break; //added by ikill240c
            default:
                IO.println("Invalid action: " + action);
                break;
        }
    }

    public final void printDebugInfo() {
        IO.println("-----------------------------------");
        IO.println("Primary Controller = " + getPrimaryController());
        if (getAbilities().hasAbilities(Abilities.MAGIC)) {
            IO.println("Hit Points = " + hit_points);
            IO.println("Magic Energy 0 = " + magic_energy[0]);
            IO.println("Magic Energy 1 = " + magic_energy[1]);
            IO.println("Controller = " + getPrimaryController());
        }
    }

    // Uses >= instead of == so that floating-point energy that has been clamped to exactly
    // MAX_MAGIC_ENERGY still passes the check (== on floats is fragile). //added by ikill240c 2026-09-09 15:05
    public final boolean canDoMagic(int magic_index) {//added by ikill240c
        return !isDead() && magic_index >= 0 && magic_index < RacesResources.NUM_MAGIC && getOwner().canDoMagic(
                magic_index) && magic_energy[magic_index] >= getMagicCost(magic_index); //added by ikill240c
    }

    public final void doMagic(int magic_index, boolean clear_stack) {//added by ikill240c
        if (canDoMagic(magic_index)) {
            if (clear_stack)
                clearControllerStack();
            // Was unconditionally getOwner().getRace() - a converted opposite-race chieftain would
            // cast using the RECIPIENT PLAYER's race's spells instead of its own original race's, which
            // isn't what "convert an opposite-race chief to gain magic from both races" means. Falls
            // back to the owner's race normally; only overridden for a cross-race converted chieftain
            // (see setMagicRaceOverride() / Convert.java). //added by ikill240c
            Race casting_race = magic_race_override_index >= 0
                    ? getOwner().getWorld().getRacesResources().getRace(magic_race_override_index)
                    : getOwner().getRace(); //added by ikill240c
            pushController(new MagicController(this, casting_race.getMagicFactory(magic_index)));
            magic_energy[magic_index] = 0f;
            last_magic_index = magic_index;

            // stats
            getOwner().magicCast();

            triggerChiefsCourage(magic_index);
        }
    }

    /**
     * Marks this unit as casting spells with a different race's magic than its owner's - set on a
     * converted chieftain whose original race differs from the recipient's own race. Magic
     * ENABLEMENT (Player.canDoMagic()'s campaign-progression gating) is intentionally still checked
     * against the recipient player, not the original race - that gating tracks the player's own
     * campaign progress, not a property of any specific unit they happen to control. //added by ikill240c
     */
    public final void setMagicRaceOverride(int race_index) { //added by ikill240c
        this.magic_race_override_index = race_index;
    }

    // Public getter for the same field doMagic() above already reads - needed by the magic button
    // UI (ActionButtonPanel) so it can show the icons/tooltips for whichever race this specific
    // unit is ACTUALLY casting with, rather than always assuming the selection's owner's own race.
    // Without this, a converted chieftain's magic worked correctly server-side but the buttons
    // shown to the player still displayed the recipient's own race's spells - wrong icons, wrong
    // tooltips, and (worse) potentially letting the player click a button that then casts a
    // different spell than the one shown. -1 (the field's default) means "no override - use this
    // unit's owner's own race", matching doMagic()'s own check. //added by ikill240c
    public final int getMagicRaceOverride() { //added by ikill240c
        return magic_race_override_index; //added by ikill240c
    }

    private void triggerChiefsCourage(int magic_index) {//added by ikill240c
        if (!getOwner().canUseChiefsCourage()) //added by ikill240c
            return; // campaign scenarios can disable this until a later island unlocks it //added by ikill240c
        if (!getOwner().isChiefsCourageReady()) //added by ikill240p 2026-09-14 - new cooldown gate: Chiefs Courage previously fired on every single successful cast with no limit
            return; // still on cooldown from a previous trigger - see Player.CHIEFS_COURAGE_COOLDOWN_SECONDS //added by ikill240p 2026-09-14
        float duration = magic_index >= 0 && magic_index < COURAGE_DURATION.length ? COURAGE_DURATION[magic_index] : 6f;

        // Escalation: each use makes the next one 1.5% stronger, compounding - the FIRST use is
        // the baseline (no bonus yet), matching "each TIME it's used, it increases" reading as
        // "each use after this one gets stronger", not "this very first cast is already boosted".
        // incrementAndGetChiefsCourageUses() returns the count INCLUDING this cast, so subtracting
        // 1 gives "how many previous casts happened before this one". //added by ikill240c
        int previous_uses = getOwner().incrementAndGetChiefsCourageUses() - 1; //added by ikill240c
        float escalation = (float) Math.pow(1.015, previous_uses); //added by ikill240c
        // Multipliers above 1.0 (speed/attack-speed/combat) escalate the BONUS portion (the amount
        // above 1.0), not the whole multiplier - escalating the whole value would also inflate the
        // "no change" baseline itself, which isn't what "the buff gets stronger" should mean.
        // Range bonus and duration have no such baseline to preserve (a flat addition and a
        // duration respectively), so those scale directly. //added by ikill240c
        float speed_mult = 1f + (COURAGE_SPEED_MULT - 1f) * escalation; //added by ikill240c
        float attack_speed_mult = 1f + (COURAGE_ATTACK_SPEED_MULT - 1f) * escalation; //added by ikill240c
        float combat_mult = 1f + (COURAGE_COMBAT_MULT - 1f) * escalation; //added by ikill240c
        float range_bonus_value = COURAGE_RANGE_BONUS * escalation; //added by ikill240c
        float escalated_duration = duration * escalation; //added by ikill240c

        // Reaches every teammate's units too (not just the caster's own army), per explicit
        // request - looping over all world players and filtering by isAlly() (rather than e.g. a
        // dedicated "team roster" list) matches the existing team-comparison pattern already used
        // by Player.teamHasBuilding(). Unified into ONE loop (previously the full combat buff only
        // reached the caster's own army while a separate loop gave allies just the cooldown
        // reduction) - now the whole buff package, including the cooldown reduction, reaches
        // allies alike; applyCourageBuff() stacks rather than overwrites, so this being a single
        // pass per ally doesn't lose anything the old two-loop version had. //added by ikill240p 2026-09-14
        float caster_x = getPositionX(); //added by ikill240p 2026-09-14 - the buff now radiates out from wherever the casting chieftain actually is, not the whole map
        float caster_y = getPositionY(); //added by ikill240p 2026-09-14
        for (Player player : getOwner().getWorld().getPlayers()) { //added by ikill240p 2026-09-14
            if (!getOwner().isAlly(player)) //added by ikill240p 2026-09-14 - skip anyone who isn't the caster or one of the caster's teammates
                continue; //added by ikill240p 2026-09-14
            for (var s : player.getUnits().getSet()) {
                if (s instanceof Unit unit && !unit.isDead()) {
                    // Radius check: Chiefs Courage previously buffed every allied unit on the entire
                    // map unconditionally, regardless of how far away it was from the caster. Now
                    // only allies within COURAGE_RADIUS of the casting chieftain are affected.
                    // //added by ikill240p 2026-09-14
                    float dx = unit.getPositionX() - caster_x; //added by ikill240p 2026-09-14
                    float dy = unit.getPositionY() - caster_y; //added by ikill240p 2026-09-14
                    if (dx * dx + dy * dy > COURAGE_RADIUS * COURAGE_RADIUS) //added by ikill240p 2026-09-14
                        continue; //added by ikill240p 2026-09-14 - too far away from the caster to feel the effect
                    unit.applyCourageBuff(escalated_duration, speed_mult, range_bonus_value, //added by ikill240c
                            attack_speed_mult, combat_mult, COOLDOWN_REDUCTION); //added by ikill240c
                }
            }
        }

        getOwner().chiefsCourageTriggered(); // starts the cooldown for NEXT time //added by ikill240p 2026-09-14

        float z = getPositionZ() + getHitOffsetZ();
        getOwner().getWorld().getAudio().newAudio(new AudioParameters<>(
                getOwner().getWorld().getRacesResources().getStunSound(getOwner().getWorld().getRandom()),
                getPositionX(), getPositionY(), z,
                AudioPlayer.AUDIO_RANK_MAGIC,
                AudioPlayer.AUDIO_DISTANCE_MAGIC,
                AudioPlayer.AUDIO_GAIN_STUN_LUR,
                AudioPlayer.AUDIO_RADIUS_STUN_LUR,
                1f));
    }

    public final int getLastMagicIndex() {
        return last_magic_index; // for tutorial
    }

    public final float getMagicProgress(int magic_index) {
        return magic_energy[magic_index] / getMagicCost(magic_index); //added by ikill240c
    }

    public final void switchAnimation(float anim_speed, @NonNull int animation) {
        assert !isDead();
        if (supply_container != null) {
            supply_container.resetSupply(LeftPaddle.class);
            supply_container.resetSupply(RightPaddle.class);
        }
        this.anim_speed = anim_speed;
        if (this.animation != animation) {
            this.animation = animation;
            this.anim_time = 0f;
        } else if (getTemplate().getSpriteRenderer().getAnimationType(
                animation) == AnimationInfo.AnimationType.PLAIN.ordinal()) {
                    this.anim_time = 0f;
                }
    }

    @Override
    public final int getAnimation() {
        return animation;
    }

    @Override
    public final float getAnimationTicks() {
        return anim_time;
    }

    public final float getMountOffset() {
        assert !isDead();
        return mount_offset;
    }

    // Merged from boats_on_steam - a public setter for mount_offset, needed by
    // ShipAllocation.updateFinal()/updateIntermediate() (the boarding-animation feature) to adjust
    // a boarding unit's vertical seat offset from outside this class as it progresses toward its
    // final seated position. Previously mount_offset was only ever assigned directly from within
    // Unit itself. //added by ikill240c
    public final void setMountOffset(float offset) { //added by ikill240c
        mount_offset = offset; //added by ikill240c
    }

    @Override
    public final float getOffsetZ() {
        if (mounted)
            return mounted_building.getOffsetZ() + mount_offset;
        else {
            if (isDead()) {
                DieBehaviour die_behaviour = (DieBehaviour) getCurrentBehaviour();
                return die_behaviour.getOffsetZ();
            } else
                return calculateSlopeOffset();
        }
    }

    private float calculateSlopeOffset() {
        // Check surrounding heights to lift unit on slopes
        float r = getSize() * 0.2f; // Check closer to center (feet) to avoid excessive floating
        float x = getPositionX();
        float y = getPositionY();
        var hm = getOwner().getWorld().getHeightMap();

        float h_center = hm.getNearestHeight(x, y);
        float h_max = h_center;

        // Axis-aligned
        h_max = Math.max(h_max, hm.getNearestHeight(x + r, y));
        h_max = Math.max(h_max, hm.getNearestHeight(x - r, y));
        h_max = Math.max(h_max, hm.getNearestHeight(x, y + r));
        h_max = Math.max(h_max, hm.getNearestHeight(x, y - r));

        // Diagonals (approx 0.707 * r)
        float d = r * 0.707f;
        h_max = Math.max(h_max, hm.getNearestHeight(x + d, y + d));
        h_max = Math.max(h_max, hm.getNearestHeight(x - d, y + d));
        h_max = Math.max(h_max, hm.getNearestHeight(x + d, y - d));
        h_max = Math.max(h_max, hm.getNearestHeight(x - d, y - d));

        return Math.max(0f, h_max - h_center);
    }

    public final float getHitError() {
        return on_ship ? 2.2f : 0.0f;
    }

    public final void debugRender() {
        path_tracker.debugRender();
    }
}
