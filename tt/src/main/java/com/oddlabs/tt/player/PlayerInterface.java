package com.oddlabs.tt.player;

import com.oddlabs.tt.model.Action;
import com.oddlabs.tt.model.Building;
import com.oddlabs.tt.model.DeployType;
import com.oddlabs.tt.model.Selectable;
import com.oddlabs.tt.model.Supply;
import com.oddlabs.tt.model.Unit;
import com.oddlabs.tt.util.Target;
import org.jspecify.annotations.NonNull;

public interface PlayerInterface {
    void deployUnits(@NonNull Building building, @NonNull DeployType type, int num_units);

    /*	void deployPeons(Building building, int num_units);
        void deployRockWarriors(Building building, int num_units);
        void deployIronWarriors(Building building, int num_units);
        void deployRubberWarriors(Building building, int num_units);*/
    void createHarvesters(@NonNull Building building, int num_tree, int num_rock, int num_iron, int num_rubber);

    void buildRockWeapons(@NonNull Building building, int num_weapons, boolean infinite);

    void buildIronWeapons(@NonNull Building building, int num_weapons, boolean infinite);

    void buildRubberWeapons(@NonNull Building building, int num_weapons, boolean infinite);

    void doMagic(@NonNull Unit chieftain, int magic);

    void exitTower(@NonNull Building building);

    /**
     * "Stand Ground" for every attack-capable unit in the given selection - see
     * Unit.standGround() for exactly what this does and why it's not a new Controller.
     * //added by ikill240c
     */
    void standGround(@NonNull Selectable<?> @NonNull [] units); //added by ikill240c

    void trainChieftain(@NonNull Building building, boolean start);

    void placeBuilding(Selectable<?> @NonNull [] selection, int template_id, int placing_grid_x, int placing_grid_y,
            boolean queue); //added by ikill240c - queue=true appends to the builder's order queue (shift-click repeat placement) instead of immediately taking over, so buildings get worked on in the order they were clicked instead of each click abandoning the previous site

    void setFormation(Selectable<?> @NonNull [] selection, @NonNull Formation formation); //added by ikill240c - was setFormation(Formation) with no selection; formation is now per-unit, not global, so the selection it applies to must be passed explicitly

    void setRallyPoint(@NonNull Building building, @NonNull Target target);

    void setTarget(Selectable<?> @NonNull [] selection, @NonNull Target target, @NonNull Action action,
            boolean aggressive);

    void setRallyPoint(@NonNull Building building, int grid_x, int grid_y);

    void setSailingTarget(Selectable<?> @NonNull [] selection, @NonNull Target target);

    void setSailingTarget(Selectable<?> @NonNull [] selection, int grid_x, int grid_y);

    void setLandscapeTarget(Selectable<?> @NonNull [] selection, int grid_x, int grid_y, @NonNull Action action,
            boolean aggressive);

    /** Appends an order to each selected unit's queue instead of replacing the current one. */ //added by ikill240c 2026-09-10 16:45
    void queueTarget(Selectable<?> @NonNull [] selection, @NonNull Target target, @NonNull Action action, //added by ikill240c 2026-09-10 16:45
            boolean aggressive); //added by ikill240c 2026-09-10 16:45

    void queueLandscapeTarget(Selectable<?> @NonNull [] selection, int grid_x, int grid_y, @NonNull Action action, //added by ikill240c 2026-09-10 16:45
            boolean aggressive); //added by ikill240c 2026-09-10 16:45

    void recallGatherers(@NonNull Building building, @NonNull Class<? extends Supply> supply_type, int amount);

    void setPreferredGamespeed(int speed);

    void changePreferredGamespeed(int delta);
}
