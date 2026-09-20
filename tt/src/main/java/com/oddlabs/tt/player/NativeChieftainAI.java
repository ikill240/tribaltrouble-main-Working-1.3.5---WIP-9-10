package com.oddlabs.tt.player;

import com.oddlabs.tt.model.RacesResources;
import com.oddlabs.tt.model.Selectable;
import com.oddlabs.tt.model.Unit;
import com.oddlabs.tt.pathfinder.FindOccupantFilter;
import org.jspecify.annotations.NonNull;

import java.util.stream.StreamSupport;

public final class NativeChieftainAI extends ChieftainAI {
    private static final int NUM_UNITS_FOR_LIGHTNING = 8;//og2
    private static final int NUM_UNITS_FOR_POISON = 10;//og 5
    private static final int NUM_UNITS_FOR_CONVERT = 15;//og 1//added by ikill240

    @Override
    public void decide(@NonNull Unit chieftain) {
        nodeLightningCloud(chieftain);
        nodePoisonFog(chieftain);
        nodeConvert(chieftain);//added by ikill240
    }

    private void nodeLightningCloud(@NonNull Unit chieftain) {
        if (chieftain.getMagicProgress(RacesResources.INDEX_MAGIC_LIGHTNING) < 1)
            return;

        float hit_radius = 38f;//og 30
        int num_enemy_units = numEnemyUnits(chieftain.getOwner());
        int num_enemy_units_close = getNumEnemyUnitsClose(chieftain, hit_radius);
        if (num_enemy_units_close >= NUM_UNITS_FOR_LIGHTNING
                || (num_enemy_units < NUM_UNITS_FOR_LIGHTNING && num_enemy_units_close > 2)
                || (chieftain.getHitPoints() <= 2 && num_enemy_units_close > 1)) {
            chieftain.doMagic(RacesResources.INDEX_MAGIC_LIGHTNING, false);
        }
    }

    private void nodePoisonFog(@NonNull Unit chieftain) {
        if (chieftain.getMagicProgress(RacesResources.INDEX_MAGIC_POISON) < 1)
            return;

        float hit_radius = chieftain.getOwner().getRace().getMagicFactory(
                RacesResources.INDEX_MAGIC_POISON).getHitRadius();
        int num_enemy_units = numEnemyUnits(chieftain.getOwner());
        int num_enemy_units_close = getNumEnemyUnitsClose(chieftain, hit_radius);
        int num_friendly_units_close = getNumFriendlyUnitsClose(chieftain, hit_radius);
        if (2 * num_friendly_units_close < num_enemy_units_close
                && (num_enemy_units_close >= NUM_UNITS_FOR_POISON
                        || (num_enemy_units < NUM_UNITS_FOR_POISON && num_enemy_units_close > 4)
                        || (chieftain.getHitPoints() <= 2 && num_enemy_units_close > 5))) {//OG 2 & 1
            chieftain.doMagic(RacesResources.INDEX_MAGIC_POISON, false);
        }
    }

    private void nodeConvert(@NonNull Unit chieftain) {//added by ikill240
        if (chieftain.getMagicProgress(RacesResources.INDEX_MAGIC_CONVERT) < 1)
            return;

        float hit_radius = chieftain.getOwner().getRace().getMagicFactory(
                RacesResources.INDEX_MAGIC_CONVERT).getHitRadius();
        int num_enemy_units = numEnemyUnits(chieftain.getOwner());
        int num_enemy_units_close = getNumEnemyUnitsClose(chieftain, hit_radius);
        if (num_enemy_units_close >= NUM_UNITS_FOR_CONVERT
                        || (num_enemy_units < NUM_UNITS_FOR_CONVERT && num_enemy_units_close > 3) //og 1
                        || (chieftain.getHitPoints() <= 3 && num_enemy_units_close > 2)) {
            chieftain.doMagic(RacesResources.INDEX_MAGIC_CONVERT, false);
        }
    }

    private int getNumEnemyUnitsClose(@NonNull Unit chieftain, float hit_radius) {
        var filter = new FindOccupantFilter<>(chieftain.getPositionX(), chieftain.getPositionY(), hit_radius, chieftain,
                Unit.class);
        chieftain.getUnitGrid().scan(filter, chieftain.getGridX(), chieftain.getGridY());
        long num_enemy_units_close = StreamSupport.stream(filter.getResult().spliterator(), false).filter(
                Selectable::isAlive).filter(unit -> {
                    float dx = unit.getPositionX() - chieftain.getPositionX();
                    float dy = unit.getPositionY() - chieftain.getPositionY();
                    float squared_dist = dx * dx + dy * dy;
                    return chieftain.getOwner().isEnemy(unit.getOwner()) && squared_dist < hit_radius * hit_radius;
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
