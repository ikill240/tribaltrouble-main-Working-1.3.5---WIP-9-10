package com.oddlabs.tt.model;

import com.oddlabs.tt.pathfinder.ShipTrajectoryPoint; //added by ikill240c
import com.oddlabs.tt.pathfinder.UnitGrid; //added by ikill240c
import org.joml.Vector2f;
import org.joml.Vector3f;

public final class ShipAllocation {

    public static final int SITTING = 0;
    public static final int ROWING_LEFT = 1;
    public static final int ROWING_RIGHT = 2;
    public static final int FIGHTING = 3;
    public static final int STEERING = 4;

    private int role = SITTING;
    private Vector3f offset = new Vector3f(0.0f, 0.0f, 0.0f);
    private Vector2f rotation = new Vector2f(0.0f, 1.0f);

    public ShipAllocation() {
    }

    public ShipAllocation(Vector3f offset, Vector2f rotation, int role) {
        this.role = role;
        this.offset = offset;
        this.rotation = rotation;
    }

    public void setOffset(float x, float y, float z) {
        offset = new Vector3f(x, y, z);
    }

    public void setRotation(float x, float y) {
        rotation = new Vector2f(x, y);
    }

    public void setRole(int role) {
        this.role = role;
    }

    public Vector3f getOffset() {
        return offset;
    }

    public Vector2f getRotation() {
        return rotation;
    }

    public int getRole() {
        return role;
    }

    // Merged from boats_on_steam - the two halves of the boarding-animation feature. A unit
    // boarding a ship walks from the ship's entrance to its final seat over
    // ShipAttackBehaviour.TOTAL_BOARDING_TIME rather than snapping instantly into place.
    // //added by ikill240c

    // Snaps the unit directly to its final seated position/direction/mount-offset once boarding
    // completes - this is what previously ran unconditionally and immediately for every boarding
    // unit; now it's only the LAST step of the animated sequence. //added by ikill240c
    public void updateFinal(Unit unit, Ship ship) { //added by ikill240c
        float x = ship.getPositionX(); //added by ikill240c
        float y = ship.getPositionY(); //added by ikill240c
        float dx = ship.getDirectionX(); //added by ikill240c
        float dy = ship.getDirectionY(); //added by ikill240c
        float ox = offset.x; //added by ikill240c
        float oy = offset.y; //added by ikill240c
        float gx = x + dx * ox - dy * oy; //added by ikill240c
        float gy = y + dy * ox + dx * oy; //added by ikill240c
        int gridSize = ship.getUnitGrid().getGridSize(); //added by ikill240c
        int gridX = Math.clamp(UnitGrid.toGridCoordinate(gx), 0, gridSize - 1); //added by ikill240c
        int gridY = Math.clamp(UnitGrid.toGridCoordinate(gy), 0, gridSize - 1); //added by ikill240c
        unit.setReference(ship); //added by ikill240c
        unit.setPosition(gx, gy); //added by ikill240c
        unit.setGridPosition(gridX, gridY); //added by ikill240c
        unit.setDirection(-dy, dx); //added by ikill240c
        unit.setMountOffset(offset.z); //added by ikill240c
    }

    // Interpolates the unit's position along a straight path from the ship's entrance (where
    // boarding units start from) to this allocation's final seat, at the given progress (0 at the
    // start of boarding, 1 at the end) - called every tick during boarding, before updateFinal()
    // takes over once progress reaches 1. //added by ikill240c
    public void updateIntermediate(Unit unit, Ship ship, float progress) { //added by ikill240c
        float x = ship.getPositionX(); //added by ikill240c
        float y = ship.getPositionY(); //added by ikill240c
        float dx = ship.getDirectionX(); //added by ikill240c
        float dy = ship.getDirectionY(); //added by ikill240c
        float ox = offset.x; //added by ikill240c
        float oy = offset.y; //added by ikill240c
        float gx = x + dx * ox - dy * oy; //added by ikill240c
        float gy = y + dy * ox + dx * oy; //added by ikill240c
        var proxy = ship.getEntrance(); //added by ikill240c
        ShipTrajectoryPoint p0 = new ShipTrajectoryPoint(proxy.getPositionX(), proxy.getPositionY()); //added by ikill240c
        ShipTrajectoryPoint p1 = new ShipTrajectoryPoint(gx, gy); //added by ikill240c
        p0.setDirectionTo(p1); //added by ikill240c
        float d = p0.distanceTo(p1) * progress; //added by ikill240c
        p0.move(d); //added by ikill240c
        unit.setPosition(p0.positionX, p0.positionY); //added by ikill240c
        unit.setGridPosition(p0.gridX, p0.gridY); //added by ikill240c
        unit.setDirection(p0.directionX, p0.directionY); //added by ikill240c
        float z = progress * offset.z; //added by ikill240c
        unit.setMountOffset(z); //added by ikill240c
    }
}
