package com.axehigh.platformer.ecs.components;
import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Pool;

/**
 * Bounding box (in world units, relative to the entity's TransformComponent position) used for AABB checks.
 * Includes both local 'bounds' (offsets/size) and a derived 'worldBounds' (absolute map position)
 * updated centrally to ensure consistency across systems.
 */
public class CollisionComponent implements Component, Pool.Poolable {
    /** Local offsets (x, y) and dimensions (width, height) relative to TransformComponent position. */
    public final Rectangle bounds = new Rectangle();

    /**
     * World-space rectangle (transform.position + bounds.x/y).
     * Updated by a central system (e.g. MovementSystem or a dedicated BoundsSystem) every frame.
     */
    public final Rectangle worldBounds = new Rectangle();

    /** Static base offset used for sprite anchoring in RenderSystem, independent of dynamic flip offsets. */
    public float baseOffsetX = 0f;
    public float baseOffsetY = 0f;

    /** Current dynamic offset (lerped) relative to baseOffsetX/Y. */
    public float currentOffsetX = 0f;
    public float currentOffsetY = 0f;

    public void updateWorldBounds(com.badlogic.gdx.math.Vector2 position) {
        worldBounds.set(position.x + bounds.x, position.y + bounds.y, bounds.width, bounds.height);
    }

    @Override
    public void reset() {
        bounds.set(0f, 0f, 0f, 0f);
        worldBounds.set(0f, 0f, 0f, 0f);
        baseOffsetX = 0f;
        baseOffsetY = 0f;
        currentOffsetX = 0f;
        currentOffsetY = 0f;
    }
}
