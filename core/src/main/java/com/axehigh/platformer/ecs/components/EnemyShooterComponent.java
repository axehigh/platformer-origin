package com.axehigh.platformer.ecs.components;

import com.axehigh.platformer.util.Timer;
import com.badlogic.ashley.core.Component;

/**
 * Marker component layered on top of {@code EnemyComponent} tagging an enemy as able to fire a
 * bullet periodically, resolved by {@code EnemyShootSystem}. The enemy still patrols exactly like
 * a base enemy (driven by {@code EnemySystem}, which already matches this entity's family) — the
 * one exception is the {@link #windUp} telegraph, during which {@code EnemyShootSystem} zeroes the
 * shooter's horizontal velocity every frame so it plants in place for the charged shot.
 * <p>
 * Shot tuning (both authored as **tile counts** in Tiled and converted by {@code EnemyFactory} to
 * **world-unit distances** via {@code × tileWidth} — note there is deliberately NO extra
 * {@code × unitScale}, unlike {@code EnemyComponent.patrolRange}/{@code EnemyAttackComponent.attackRange}):
 * {@code detectionRange} is the horizontal reach (each side of the shooter) that must contain the
 * player's collision-center before a shot commits, and {@code shootRange} is how far the fired
 * bullet can travel before despawning.
 */
public class EnemyShooterComponent implements Component {
    /** Counts down between shots; once done, {@code EnemyShootSystem} fires and restarts it. */
    public Timer shootCooldown = new Timer();
    /** Seconds between shots. */
    public float shootInterval = 5f;
    /**
     * Bullet travel range in world units (tile count × source tile width). At runtime this is a
     * world-unit distance: the default {@code 8} is a tile count, converted by {@code EnemyFactory}
     * to {@code 8 × tileWidth} (e.g. 128u on 16px tiles). {@code 0} would mean unlimited.
     */
    public float shootRange = 8f;
    /**
     * Horizontal detection reach in world units (tile count × source tile width), applied per side
     * around the shooter's collision center. Default {@code 8} is a tile count, converted by
     * {@code EnemyFactory} to {@code 8 × tileWidth} (e.g. 128u on 16px tiles).
     */
    public float detectionRange = 8f;
    /**
     * Authored wind-up telegraph duration in **real seconds** (Tiled per-marker {@code windUp}
     * property, default {@code 0.5}). Deliberately a TIME and not a tile count — read via
     * {@code TileProps.getFloatProperty}, never {@code getTileXProperty} (which multiplies by
     * tileWidth) — matching the melee {@code EnemyAttackComponent.windUpDuration} raw-seconds
     * convention. The effective telegraph used by {@code EnemyShootSystem} is
     * {@code Math.max(windUpSeconds, ATTACKING clip duration)}, so a longer attack clip extends
     * the charge but never shortens it.
     */
    public float windUpSeconds = 0.5f;
    /**
     * Stationary turn-idle delay before the wind-up, used only when a commit requires a facing
     * change (the player is on the side opposite the shooter's current {@code EnemyComponent.direction}):
     * the shooter snap-turns on commit and then stands still for {@code EnemySystem.TURN_PAUSE_DURATION
     * × unitScale} — the same duration as a walker patrol turn-around — before the wind-up starts.
     * {@code EnemyShootSystem} ticks it, zeroes the shooter's horizontal velocity while it's active,
     * and re-tracks the facing (a player crossing to the other side mid-idle flips it again); it is
     * cancelled (reset, no wind-up) exactly like the wind-up if the shooter dies, gets hit-stunned,
     * its room deactivates, or the player leaves detection.
     */
    public Timer turnIdle = new Timer();
    /**
     * Wind-up telegraph before a committed shot actually fires; its effective duration is
     * {@code Math.max(windUpSeconds, ATTACKING clip duration)} — the authored seconds win unless
     * the {@code ATTACKING} clip is longer (see {@link #windUpSeconds}). {@code EnemyShootSystem}
     * ticks it, zeroes the shooter's horizontal velocity every frame it runs (the shooter plants
     * in place for the charge, exactly like the turn-idle stand-still), and spawns the bullet the
     * frame it completes; the {@code AnimationSystem} loops the {@code ATTACKING} pose for the
     * whole telegraph (see the {@code AnimationSystem} row in {@code ashley-ecs-systems.md}). It
     * is cancelled (reset) if the shooter dies, gets hit-stunned, its room deactivates, or the
     * player leaves detection.
     */
    public Timer windUp = new Timer();
    /**
     * The fire direction captured at wind-up start (sign of the player-vs-shooter center-X
     * difference, equal to {@code EnemyComponent.direction} then). When the shot went through a
     * turn-idle first, this honors the re-tracked facing at idle completion — so a player crossing
     * sides during the idle re-aims the final shot. Locked from the wind-up on, so a patrol flip
     * mid-wind-up can't re-aim the committed shot.
     */
    public int fireDirection = 1;
}