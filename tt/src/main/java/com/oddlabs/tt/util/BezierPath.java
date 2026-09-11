package com.oddlabs.tt.util;

import com.oddlabs.tt.landscape.HeightMap;
import org.jspecify.annotations.NonNull;

/**
 * Cubic Bezier curve path for smooth unit movement.
 * Uses four control points to generate smooth curves between waypoints.
 * Debug visualization shows the curve as a white line (enable with UNIT_GRID mode).
 */
public final class BezierPath {
    private static final int PREVIOUS = 0;
    private static final int START = 1;
    private static final int END = 2;
    private static final int NEXT = 3;

    private static final float[] debug_point = new float[2];
    private static final float[] debug_dir = new float[2];

    private final float[][] points = new float[4][2];
    private final float[] current_point = new float[2];
    private final float[] current_dir = new float[2];
    private float dt;
    private float t;

    public BezierPath() {
        initState();
    }

    private void initState() {
        t = 1f;
    }

    public boolean isDone() {
        return t >= 1f;
    }

    public void computeCurvePoint(float speed) {
        // Skip curve computation once the path is already finished, because a long frame stall can push
        // t past the end of the curve and previously triggered the assertion crash instead of letting the
        // unit remain at its last valid position. // ikill240v 2026-09-09 16:45
        if (isDone())
            return;
        computeCurvePointFromTime(t, current_point, current_dir);
        t += dt * speed;
    }

    public void dumpPoints() {
        for (float[] point : points) {
            System.out.println("points[i][0] = " + point[0] + " | points[i][1] = " + point[1]);
        }
    }

    private void computeCurvePointFromTime(float t, float @NonNull [] point, float @NonNull [] dir) {
        float t2 = t * t;
        float t3 = t2 * t;
        float b0 = 1 - 3 * t + 3 * t2 - t3;
        float b1 = 3 * t3 - 6 * t2 + 4;
        float b2 = -3 * t3 + 3 * t2 + 3 * t + 1;
        float b3 = t3;
        point[0] = (1f / 6f) * (points[PREVIOUS][0] * b0 + points[START][0] * b1 + points[END][0] * b2 + points[NEXT][0] * b3);
        point[1] = (1f / 6f) * (points[PREVIOUS][1] * b0 + points[START][1] * b1 + points[END][1] * b2 + points[NEXT][1] * b3);

        float db0 = -3 + 6 * t - 3 * t2;
        float db1 = 9 * t2 - 12 * t;
        float db2 = -9 * t2 + 6 * t + 3;
        float db3 = 3 * t2;
        float dx = (1f / 6f) * (points[PREVIOUS][0] * db0 + points[START][0] * db1 + points[END][0] * db2 + points[NEXT][0] * db3);
        float dy = (1f / 6f) * (points[PREVIOUS][1] * db0 + points[START][1] * db1 + points[END][1] * db2 + points[NEXT][1] * db3);
        float dir_len_inv = 1f / (float) Math.sqrt(dx * dx + dy * dy);
        dir[0] = dx * dir_len_inv;
        dir[1] = dy * dir_len_inv;
        if (Float.isNaN(dir[0]) || Float.isNaN(dir[1])) {
            dir[0] = 1f;
            dir[1] = 0f;
        }
    }

    public float getCurrentDirectionX() {
        return current_dir[0];
    }

    public float getCurrentDirectionY() {
        return current_dir[1];
    }

    public float getCurrentX() {
        return current_point[0];
    }

    public float getCurrentY() {
        return current_point[1];
    }

    public void init(float inv_length, float x1, float y1, float x2, float y2) {
        if (x1 == x2 && y1 == y2) {
            // Zero-length movement requests can happen when a unit is already at its home tile or a
            // redundant home-return command is sent. Treat that as a completed path and leave the unit
            // standing in place instead of creating a broken Bezier curve. // ikill240v 2026-09-09 20:00
            points[PREVIOUS][0] = x1;
            points[PREVIOUS][1] = y1;
            points[START][0] = x1;
            points[START][1] = y1;
            points[END][0] = x1;
            points[END][1] = y1;
            points[NEXT][0] = x1;
            points[NEXT][1] = y1;
            current_point[0] = x1;
            current_point[1] = y1;
            current_dir[0] = 1f;
            current_dir[1] = 0f;
            t = 1f;
            dt = inv_length;
            return;
        }
        float extrapolated_x = x1 + (x1 - x2);
        float extrapolated_y = y1 + (y1 - y2);
        points[PREVIOUS][0] = extrapolated_x;
        points[PREVIOUS][1] = extrapolated_y;
        points[START][0] = extrapolated_x;
        points[START][1] = extrapolated_y;
        points[END][0] = x1;
        points[END][1] = y1;
        points[NEXT][0] = x2;
        points[NEXT][1] = y2;
        initState();
        dt = inv_length;
    }

    public void nextPoint(float inv_length, float x, float y) {
        if (x == points[NEXT][0] && y == points[NEXT][1]) {
            // Ignore redundant end-of-path updates when the next waypoint is already the same point as the
            // current endpoint; that can happen during repeated home-return or end-path calls and used to
            // trigger the same BezierPath assertion crash. // ikill240v 2026-09-09 20:05
            dt = inv_length;
            t = t - (float) Math.floor(t);
            return;
        }
        cyclePoints();
        points[NEXT][0] = x;
        points[NEXT][1] = y;
        // Wrap t back into the [0, 1) range with modulo so a long stall cannot leave the Bezier path
        // stuck above the finished threshold; the old subtraction-only logic could fail when t >= 2.0.
        // This preserves the curve progress correctly and prevents the path from crashing later.
        // ikill240v 2026-09-09 16:45
        t = t - (float) Math.floor(t);
        dt = inv_length;
    }

    public float getNextX() {
        return points[NEXT][0];
    }

    public float getNextY() {
        return points[NEXT][1];
    }

    private void cyclePoints() {
        float[] previous = points[PREVIOUS];
        points[PREVIOUS] = points[START];
        points[START] = points[END];
        points[END] = points[NEXT];
        points[NEXT] = previous;
    }

    public void endPath() {
        float next_x = points[NEXT][0] + (points[NEXT][0] - points[END][0]);
        float next_y = points[NEXT][1] + (points[NEXT][1] - points[END][1]);
        nextPoint(dt, next_x, next_y);
    }

    public void debugRender(@NonNull HeightMap heightmap) {
        float prev_x = 0, prev_y = 0, prev_z = 0;
        boolean first = true;
        for (float t = 0f; t < 1f; t += .01f) {
            computeCurvePointFromTime(t, debug_point, debug_dir);
            float x = debug_point[0];
            float y = debug_point[1];
            float z = heightmap.getNearestHeight(x, y) + 0.5f;
            if (!first) {
                DebugRender.drawLine(prev_x, prev_y, prev_z, x, y, z, 1f, 1f, 1f);
            }
            prev_x = x;
            prev_y = y;
            prev_z = z;
            first = false;
        }
    }
}
