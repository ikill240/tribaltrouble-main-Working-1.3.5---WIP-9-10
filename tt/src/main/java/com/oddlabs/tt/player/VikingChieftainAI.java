package com.oddlabs.tt.player;

import com.oddlabs.tt.model.RacesResources;
import com.oddlabs.tt.model.Selectable;
import com.oddlabs.tt.model.Unit;
import com.oddlabs.tt.pathfinder.FindOccupantFilter;
import org.jspecify.annotations.NonNull;

import java.util.stream.StreamSupport;

public final class VikingChieftainAI extends ChieftainAI {
    private static final int NUM_UNITS_FOR_STUN = 10; //og 5
    private static final int NUM_UNITS_FOR_BLAST = 15; //og 7
    private static final int NUM_UNITS_FOR_CONVERT = 6;//added by ikill240

    @Override
    public void decide(@NonNull Unit chieftain) {
        nodeBlast(chieftain);
        nodeStun(chieftain);
        nodeConvert(chieftain);//added by ikill240
    }

    private void nodeStun(@NonNull Unit chieftain) {
        if (chieftain.getMagicProgress(RacesResources.INDEX_MAGIC_STUN) < 1)
            return;

        float hit_radius = 33f;//OG 30;
        int num_enemy_units = numEnemyUnits(chieftain.getOwner());
        int num_enemy_units_close = getNumEnemyUnitsClose(chieftain, hit_radius, Unit.class);
        if (num_enemy_units_close >= NUM_UNITS_FOR_STUN
                || (num_enemy_units < NUM_UNITS_FOR_STUN && num_enemy_units_close > 1)
                || (chieftain.getHitPoints() <= 2 && num_enemy_units_close > 1)) {
            chieftain.doMagic(RacesResources.INDEX_MAGIC_STUN, false);
        }
    }

    private void nodeBlast(@NonNull Unit chieftain) {
        if (chieftain.getMagicProgress(RacesResources.INDEX_MAGIC_BLAST) < 1)
            return;

        float hit_radius = chieftain.getOwner().getRace().getMagicFactory(1).getHitRadius();
        int num_enemy_units = numEnemyUnits(chieftain.getOwner());

        int num_enemy_units_close = getNumEnemyUnitsClose(chieftain, hit_radius, Selectable.genericClass());
        int num_friendly_units_close = getNumFriendlyUnitsClose(chieftain, hit_radius);
        if (2 * num_friendly_units_close < num_enemy_units_close
                && (num_enemy_units_close >= NUM_UNITS_FOR_BLAST
                        || (num_enemy_units < NUM_UNITS_FOR_BLAST && num_enemy_units_close > 2) //og 1
                        || (chieftain.getHitPoints() <= 4 && num_enemy_units_close > 5))) { //og 2 & 1
            chieftain.doMagic(RacesResources.INDEX_MAGIC_BLAST, false);
        }
    }

    private void nodeConvert(@NonNull Unit chieftain) {//added by ikill240
        if (chieftain.getMagicProgress(RacesResources.INDEX_MAGIC_CONVERT) < 1)
            return;

        float hit_radius = chieftain.getOwner().getRace().getMagicFactory(
                RacesResources.INDEX_MAGIC_CONVERT).getHitRadius();
        int num_enemy_units_close = getNumEnemyUnitsClose(chieftain, hit_radius, Unit.class);
        if (num_enemy_units_close >= NUM_UNITS_FOR_CONVERT) {
            chieftain.doMagic(RacesResources.INDEX_MAGIC_CONVERT, false);
        }
    }

    private <S extends Selectable<?>> int getNumEnemyUnitsClose(@NonNull Unit chieftain, float hit_radius,
            Class<S> type) {
        FindOccupantFilter<S> filter = new FindOccupantFilter<>(chieftain.getPositionX(), chieftain.getPositionY(),
                hit_radius, chieftain, type);
        chieftain.getUnitGrid().scan(filter, chieftain.getGridX(), chieftain.getGridY());
        long num_enemy_units_close = StreamSupport.stream(filter.getResult().spliterator(), false).filter(
                Selectable::isAlive).filter(s -> {
                    float dx = s.getPositionX() - chieftain.getPositionX();
                    float dy = s.getPositionY() - chieftain.getPositionY();
                    float squared_dist = dx * dx + dy * dy;
                    return chieftain.getOwner().isEnemy(s.getOwner()) && squared_dist < hit_radius * hit_radius;
                }).count();
        return (int) num_enemy_units_close;
    }

    private int getNumFriendlyUnitsClose(@NonNull Unit chieftain, float hit_radius) {
        var filter = new FindOccupantFilter<>(chieftain.getPositionX(), chieftain.getPositionY(), hit_radius, chieftain,
                Selectable.genericClass());
        chieftain.getUnitGrid().scan(filter, chieftain.getGridX(), chieftain.getGridY());
        long num_friendly_units_close = StreamSupport.stream(filter.getResult().spliterator(), false).filter(
                Selectable::isAlive).filter(s -> {
                    float dx = s.getPositionX() - chieftain.getPositionX();
                    float dy = s.getPositionY() - chieftain.getPositionY();
                    float squared_dist = dx * dx + dy * dy;
                    return !chieftain.getOwner().isEnemy(s.getOwner()) && squared_dist < hit_radius * hit_radius;
                }).count();
        return (int) num_friendly_units_close;
    }
}
