package com.axehigh.platformer.ecs.components;

import com.badlogic.ashley.core.Component;

/**
 * Tags an entity as a scripted moving platform (defined in Tiled as a {@code type="platform"}
 * object). The platform oscillates around its spawn/base position: {@code pos = base + amplitude *
 * sin(angle + phase)} per axis, where {@code angle} advances at {@code speed} (rad/s). There is no
 * {@code MovementComponent}, so {@code MovementSystem} never applies gravity to it — the scripted
 * motion is driven entirely by {@code MovingPlatformSystem}. {@code roomIndex} mirrors the enemy
 * room-activation pattern: the platform only moves while its owning room is the active one.
 */
public class MovingPlatformComponent implements Component {
    /** Spawn position (the center of the oscillation range) in world units. */
    public float baseX;
    public float baseY;
    /** Travel distance (world units) away from the base position, per axis. */
    public float amplitudeX;
    public float amplitudeY;
    /** Oscillation speed in radians per second. */
    public float speed = 1f;
    /** Phase offset in radians, to desync several platforms. */
    public float phase;
    /** Accumulated oscillation angle (radians). */
    public float angle;
    /** Index into {@code RoomState.rooms} of the owning room, or -1 for always active. */
    public int roomIndex = -1;
}
