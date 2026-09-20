package com.oddlabs.tt.viewer;

import com.oddlabs.tt.model.Abilities;
import com.oddlabs.tt.model.Army;
import com.oddlabs.tt.model.Building;
import com.oddlabs.tt.model.Selectable;
import com.oddlabs.tt.model.Unit;
import com.oddlabs.tt.player.Player;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public final class SelectionArmy extends Army {

    private final @NonNull Player local_player;
    private int num_units;
    private int num_builders;
    private @Nullable Unit chieftain;
    private @Nullable Building building;

    SelectionArmy(@NonNull Player local_player) {
        this.local_player = local_player;
    }

    public int getNumBuilders() {
        return num_builders;
    }

    public int getNumUnits() {
        return num_units;
    }

    public @Nullable Unit getChieftain() {
        return chieftain;
    }

    public @Nullable Building getBuilding() {
        return building;
    }

    private void update() {
        num_units = 0;
        num_builders = 0;
        chieftain = null;
        building = null;
        for (Selectable<?> s : getSet()) {
            // Was owner != local_player skipping everything not the local player's own - now also
            // accepts anything owned by an ally (SelectionDelegate is the only thing that can
            // actually get one into this Army in the first place, per its own comment on why - see
            // there for the full reasoning), so an ally's selected building OR unit still populates
            // `building`/`num_units`/`chieftain` here and drives ActionButtonPanel's controls,
            // exactly as the local player's own would. Originally scoped to buildings only; widened
            // to any ally-owned Selectable once unit control was added too, matching
            // Player.isValid()'s own non-enemy-owner acceptance on the engine side.
            // //added by ikill240c
            boolean is_own = s.getOwner() == local_player; //added by ikill240c
            boolean is_ally = !local_player.isEnemy(s.getOwner()); //added by ikill240c
            if (!is_own && !is_ally) //added by ikill240c
                continue;
            Abilities abilities = s.getAbilities();
            if (abilities.hasAbilities(Abilities.BUILD))
                num_builders++;
            else if (abilities.hasAbilities(Abilities.MAGIC))
                chieftain = (Unit) s;
            if (s instanceof Building building1) {
                building = building1;
            } else {
                num_units++;
            }
        }
    }

    @Override
    public void clear() {
        super.clear();
        update();
    }

    @Override
    public void remove(@NonNull Selectable<?> selectable) {
        super.remove(selectable);
        update();
    }

    @Override
    public void add(@NonNull Selectable<?> selectable) {
        super.add(selectable);
        update();
    }
}
