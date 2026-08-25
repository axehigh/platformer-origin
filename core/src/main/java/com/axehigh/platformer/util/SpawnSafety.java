package com.axehigh.platformer.util;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;

/**
 * Ensures a player spawn position is safe — not inside solid collision geometry and
 * positioned above a solid floor. Called from {@code GameScreen} and {@code LevelManager}
 * after reading the raw spawn point from the Tiled map.
 *
 * <p>Algorithm:
 * <ol>
 *   <li>Build a simplified player AABB at the spawn position.</li>
 *   <li>If it overlaps any collision rect, push outward (upward preferred) + 1 px epsilon.</li>
 *   <li>Otherwise, scan downward to find the nearest floor and snap feet onto it.</li>
 *   <li>If no floor is found, leave the position as-is (intentional mid-air spawn).</li>
 * </ol>
 */
public final class SpawnSafety {
    /** Tiny buffer (world units) added after pushing out of overlap to prevent first-frame re-collision. */
    private static final float EPSILON = 1f;

    private SpawnSafety() {} // utility

    /**
     * Returns a safe spawn position derived from {@code raw}.
     *
     * @param raw                   the raw (x, y) from the Tiled playerStart object
     * @param collisionRects        fully-solid collision rects from the map
     * @param playerCollisionWidth  {@code SpriteConstants.PlayerCollisionWidth * finalScale}
     * @param playerCollisionHeight {@code SpriteConstants.PlayerCollisionHeight * finalScale}
     * @return adjusted Vector2 (safe to use as transform.position); may be identical to raw
     */
    public static Vector2 findSafeSpawn(Vector2 raw, Array<Rectangle> collisionRects,
                                         float playerCollisionWidth, float playerCollisionHeight) {
        if (collisionRects == null || collisionRects.size == 0) {
            return raw;
        }

        // Build a simplified player AABB centred on the spawn point.
        // The spawn position from Tiled is the bottom-left of the playerStart rectangle,
        // which roughly corresponds to the player's transform position.
        float aabbX = raw.x;
        float aabbY = raw.y;
        Rectangle playerAabb = new Rectangle(aabbX, aabbY, playerCollisionWidth, playerCollisionHeight);

        // --- Step 1: overlap escape ---
        for (Rectangle rect : collisionRects) {
            if (!playerAabb.overlaps(rect)) continue;

            // Compute overlap depths on each side
            float overlapRight  = (aabbX + playerCollisionWidth) - rect.x;
            float overlapLeft   = (rect.x + rect.width) - aabbX;
            float overlapTop    = (aabbY + playerCollisionHeight) - rect.y;
            float overlapBottom = (rect.y + rect.height) - aabbY;

            float minOverlap = Math.min(Math.min(overlapRight, overlapLeft),
                                        Math.min(overlapTop, overlapBottom));

            if (minOverlap == overlapTop) {
                // Push player above the rect
                aabbY = rect.y + rect.height + EPSILON;
            } else if (minOverlap == overlapBottom) {
                // Push player below the rect (rare at spawn)
                aabbY = rect.y - playerCollisionHeight - EPSILON;
            } else if (minOverlap == overlapRight) {
                // Player barely enters from the left → push left
                aabbX = rect.x - playerCollisionWidth - EPSILON;
            } else {
                // overlapLeft is min → player barely enters from the right → push right
                aabbX = rect.x + rect.width + EPSILON;
            }
            playerAabb.set(aabbX, aabbY, playerCollisionWidth, playerCollisionHeight);
        }

        // --- Step 2: floor scan ---
        // Thin vertical probe at horizontal centre of the collision box
        float probeX = aabbX;
        float probeW = Math.max(playerCollisionWidth, 1f);
        float feetY = aabbY; // bottom of the collision AABB

        Rectangle probe = new Rectangle(probeX, 0, probeW, 1);
        float bestFloorY = -1f;

        for (Rectangle rect : collisionRects) {
            // Must be below or at the current feet
            if (rect.y + rect.height > feetY + 0.5f) continue;
            // Horizontal overlap
            if (probe.x + probe.width <= rect.x || probe.x >= rect.x + rect.width) continue;
            // Highest floor wins
            if (rect.y + rect.height > bestFloorY) {
                bestFloorY = rect.y + rect.height;
            }
        }

        if (bestFloorY >= 0f) {
            aabbY = bestFloorY;
        }
        // else: no floor found — leave as-is (intentional mid-air spawn)

        return new Vector2(aabbX, aabbY);
    }
}
