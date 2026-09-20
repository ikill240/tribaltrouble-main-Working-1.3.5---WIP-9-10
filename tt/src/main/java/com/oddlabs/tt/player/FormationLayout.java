package com.oddlabs.tt.player;

import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Computes grid-unit offset positions for a group of units in a given Formation, centered on (0,0)
 * and oriented toward a facing direction (usually the direction of travel toward the move order's
 * destination). Callers add these offsets onto the actual destination point, then validate/snap each
 * one to a real walkable cell individually (a formation is a preference for spacing/shape, not a
 * guarantee every exact offset is walkable - see Player.setLandscapeTarget()). //added by ikill240c
 */
public final class FormationLayout {
    private FormationLayout() {
    }

    /**
     * @param count      number of units to place (must be > 0)
     * @param facing_dx  x component of a (not necessarily normalized) facing direction; (0,0) means
     *                   no rotation (formation stays axis-aligned)
     * @param facing_dy  y component of the facing direction
     */
    public static @NonNull List<float[]> computeOffsets(@NonNull Formation formation, int count, float facing_dx,
            float facing_dy) {
        List<float[]> offsets = new ArrayList<>(Math.max(count, 0));
        if (count <= 0)
            return offsets;

        float spacing = switch (formation) {
            case TIGHT -> 1.2f;
            case LOOSE -> 3f;
            default -> 1.8f; // SQUARE, DIAMOND, CIRCLE, STAR //added by ikill240c
        };

        if (formation == Formation.CIRCLE) {
            if (count == 1) {
                offsets.add(new float[]{0f, 0f});
            } else {
                float radius = spacing * count / (2f * (float) Math.PI);
                for (int i = 0; i < count; i++) {
                    float angle = (float) (2 * Math.PI * i / count);
                    offsets.add(new float[]{(float) Math.cos(angle) * radius, (float) Math.sin(angle) * radius});
                }
            }
        } else if (formation == Formation.STAR) { //added by ikill240c
            // A ring whose radius oscillates between an outer and inner value STAR_POINTS times per
            // revolution, tracing a scalloped star silhouette instead of a plain circle. //added by ikill240c
            if (count == 1) { //added by ikill240c
                offsets.add(new float[]{0f, 0f}); //added by ikill240c
            } else { //added by ikill240c
                final int STAR_POINTS = 6; //added by ikill240c
                float outer_radius = spacing * count / (2f * (float) Math.PI); //added by ikill240c
                float inner_radius = outer_radius * 0.5f; //added by ikill240c
                for (int i = 0; i < count; i++) { //added by ikill240c
                    float angle = (float) (2 * Math.PI * i / count); //added by ikill240c
                    // 0..1, oscillating STAR_POINTS times per full revolution. //added by ikill240c
                    float phase = (float) (Math.cos(angle * STAR_POINTS) * 0.5 + 0.5); //added by ikill240c
                    float radius = inner_radius + (outer_radius - inner_radius) * phase; //added by ikill240c
                    offsets.add(new float[]{(float) Math.cos(angle) * radius, (float) Math.sin(angle) * radius}); //added by ikill240c
                } //added by ikill240c
            } //added by ikill240c
        } else if (formation == Formation.DIAMOND) { //added by ikill240c
            // A genuine rhombus/diamond taper - a single point at the front and back, widest in the
            // middle - rather than a square grid merely rotated 45 degrees (which, for any non-square
            // row/column count, produced a lopsided parallelogram instead of an actual diamond
            // outline). Built from taxicab-distance rings around the center (all integer points with
            // |x|+|y| == d form a diamond ring of exactly 4d points, d >= 1; d == 0 is the single
            // center point), filled outward ring by ring until count units are placed - the natural
            // "fill a diamond from the center out" order, tapering correctly at both ends even when
            // count doesn't exactly fill a whole number of rings. //added by ikill240c
            int placed = 0; //added by ikill240c
            offsets.add(new float[]{0f, 0f}); //added by ikill240c - d = 0
            placed++; //added by ikill240c
            for (int d = 1; placed < count; d++) { //added by ikill240c
                // Walk the ring's 4 edges: (d,0) -> (0,d) -> (-d,0) -> (0,-d) -> back to (d,0). //added by ikill240c
                int x = d, y = 0; //added by ikill240c
                int[][] steps = {{-1, 1}, {-1, -1}, {1, -1}, {1, 1}}; //added by ikill240c
                for (int[] step : steps) { //added by ikill240c
                    for (int k = 0; k < d && placed < count; k++) { //added by ikill240c
                        offsets.add(new float[]{x * spacing, y * spacing}); //added by ikill240c
                        placed++; //added by ikill240c
                        x += step[0]; //added by ikill240c
                        y += step[1]; //added by ikill240c
                    } //added by ikill240c
                } //added by ikill240c
            } //added by ikill240c
        } else {
            // SQUARE, TIGHT, LOOSE: a simple row/column grid, centered on (0,0). //added by ikill240c
            int cols = (int) Math.ceil(Math.sqrt(count));
            int rows = (int) Math.ceil((float) count / cols);
            int placed = 0;
            for (int row = 0; row < rows && placed < count; row++) {
                for (int col = 0; col < cols && placed < count; col++) {
                    float x = (col - (cols - 1) / 2f) * spacing;
                    float y = (row - (rows - 1) / 2f) * spacing;
                    offsets.add(new float[]{x, y});
                    placed++;
                }
            }
        }

        // Rotate every offset toward the facing direction, so the formation's "front" points the way
        // the group is actually moving instead of always sitting axis-aligned to the world grid.
        // //added by ikill240c
        float len = (float) Math.sqrt(facing_dx * facing_dx + facing_dy * facing_dy);
        if (len > 0.0001f) {
            float dir_x = facing_dx / len;
            float dir_y = facing_dy / len;
            for (float[] o : offsets) {
                float x = o[0];
                float y = o[1];
                o[0] = x * dir_x - y * dir_y;
                o[1] = x * dir_y + y * dir_x;
            }
        }

        return offsets;
    }
}
