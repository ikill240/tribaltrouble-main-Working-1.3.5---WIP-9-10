package com.oddlabs.tt.render;

import com.oddlabs.tt.global.Settings;
import com.oddlabs.tt.model.Building;
import com.oddlabs.tt.model.Model;
import com.oddlabs.tt.model.Selectable;
import com.oddlabs.tt.model.Unit;
import com.oddlabs.tt.player.Player;
import com.oddlabs.util.Color;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.joml.Vector4fc;
import org.jspecify.annotations.NonNull;

class SelectableVisitor<S extends Selectable<?>> extends ModelVisitor<S> {
    private static final Vector4fc COLOR_RED = Color.argb4v(0xC0_FF_00_00);
    private static final Vector4fc COLOR_RED_HOVER = Color.argb4v(0xC0_7f_00_00);
    private static final Vector4fc COLOR_GREEN = Color.argb4v(0xC0_00_FF_00);
    private static final Vector4fc COLOR_GREEN_HOVER = Color.argb4v(0xC0_00_7F_00);
    private static final Vector4fc COLOR_BLUE = Color.argb4v(0xC0_00_00_FF);
    private static final Vector4fc COLOR_BLUE_HOVER = Color.argb4v(0xC0_00_00_7F);
    // Full-damage target color for the health-halo tint below - deliberately the same red as
    // COLOR_RED (the "selected enemy" halo) so a critically-wounded unit's halo converges on a
    // color the player already associates with danger, rather than introducing a third distinct
    // color meaning into the same ring. //added by ikill240c
    private static final Vector4fc HEALTH_LOW_COLOR = Color.argb4v(0xC0_FF_00_00); //added by ikill240c

    @Override
    public void getTransform(@NonNull ElementRenderState<S> render_state, @NonNull Matrix4f dest) {
        Model model = render_state.model;
        float angle = (float) Math.atan2(model.getDirectionY(), model.getDirectionX());
        dest.translation(model.getPositionX(), model.getPositionY(), render_state.f).rotate(angle, 0f, 0f, 1f);
    }

    // Buildings use the player's shared TEAM color (getTeamBuildingColor()) so allied bases read
    // as one color at a glance; units keep each player's own distinct color (getColor()) so
    // individual players' armies stay visually distinguishable within a team. Was uniformly
    // getOwner().getColor() for both, despite the name already saying "team" - per explicit
    // request. //added by ikill240c
    static @NonNull Vector4fc getTeamColor(@NonNull Selectable<?> model) {
        if (model instanceof Building) //added by ikill240c
            return model.getOwner().getTeamBuildingColor(); //added by ikill240c
        return model.getOwner().getColor();
    }

    @Override
    public final @NonNull Vector4fc getTeamColor(@NonNull ElementRenderState<S> render_state) {
        return getTeamColor(render_state.getModel());
    }

    // Selectable<?> itself declares neither getHitPoints() nor getEffectiveMaxHitPoints() - Unit
    // and Building each declare their own, independently, with no shared ancestor method between
    // them (same situation AdvancedAI.hitPointsOf() already works around for the same reason).
    // Returns 1f (full health) for anything else, rather than guessing, since that leaves the
    // halo exactly as it was before this feature existed. //added by ikill240c
    private static float healthRatio(@NonNull Selectable<?> model) { //added by ikill240c
        if (model instanceof Unit u) //added by ikill240c
            return u.getEffectiveMaxHitPoints() <= 0 ? 1f : (float) u.getHitPoints() / u.getEffectiveMaxHitPoints(); //added by ikill240c
        if (model instanceof Building b) //added by ikill240c
            return b.getEffectiveMaxHitPoints() <= 0 ? 1f : (float) b.getHitPoints() / b.getEffectiveMaxHitPoints(); //added by ikill240c
        return 1f; //added by ikill240c
    }

    // Blends the given color toward HEALTH_LOW_COLOR as the model's own health ratio drops, so a
    // damaged unit's ring visibly shifts color while its owner's team is still readable at a
    // glance rather than being replaced outright. Alpha is left untouched (taken from `color`)
    // since HEALTH_LOW_COLOR's own alpha is irrelevant here - only its RGB is a blend target.
    // //added by ikill240c
    private static @NonNull Vector4fc applyHealthTint(@NonNull Vector4fc color, @NonNull Selectable<?> model) { //added by ikill240c
        if (!Settings.getSettings().health_halos_enabled || model.isDead()) //added by ikill240c
            return color; //added by ikill240c
        float ratio = Math.clamp(healthRatio(model), 0f, 1f); //added by ikill240c
        if (ratio >= .999f) //added by ikill240c - full health: skip the blend entirely rather than doing needless work every frame for the common case
            return color; //added by ikill240c
        float damage = 1f - ratio; // 0 at full health, 1 at death //added by ikill240c
        return new Vector4f( //added by ikill240c
                color.x() + (HEALTH_LOW_COLOR.x() - color.x()) * damage, //added by ikill240c
                color.y() + (HEALTH_LOW_COLOR.y() - color.y()) * damage, //added by ikill240c
                color.z() + (HEALTH_LOW_COLOR.z() - color.z()) * damage, //added by ikill240c
                color.w()); //added by ikill240c
    }

    @Override
    public final @NonNull Vector4fc getSelectionColor(@NonNull ElementRenderState<S> render_state) {
        Player local_player = render_state.render_state.getLocalPlayer();
        S model = render_state.getModel();
        Vector4fc base = render_state.render_state.isSelected(
                model) ? model.getOwner() == local_player ? COLOR_GREEN : local_player.isEnemy(
                        model.getOwner()) ? COLOR_RED : COLOR_BLUE : render_state.render_state.isHovered(
                                model) ? model.getOwner() == local_player ? COLOR_GREEN_HOVER : local_player.isEnemy(
                                        model.getOwner()) ? COLOR_RED_HOVER : COLOR_BLUE_HOVER : model.getOwner().getColor();
        return applyHealthTint(base, model); //added by ikill240c
    }

    @Override
    public final void markDetailPoint(@NonNull ElementRenderState<S> render_state) {
        S selectable = render_state.model;
        if (!selectable.isDead())
            super.markDetailPoint(render_state);
    }
}

