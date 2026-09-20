package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.ecs.components.*;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Headless unit tests for {@code EnemyBulletCollisionSystem}: the travel-range despawn — an
 * enemy bullet carrying a finite {@code maxTravelDistance} is removed once its accumulated
 * {@code |velocity.x| * deltaTime} distance reaches it, independent of lifetime counts (0 walls,
 * no player). Player bullets keep the default {@code maxTravelDistance = 0} (unlimited), which is
 * exercised elsewhere.
 */
public class EnemyBulletCollisionSystemTest extends SystemTestBase {

    private final Array<Rectangle> collisionRects = new Array<>();
    private Engine engine;
    private EnemyBulletCollisionSystem system;

    @Before
    public void setUp() {
        system = new EnemyBulletCollisionSystem(collisionRects);
        system.setUnitScale(1f);
        engine = newEngine();
        engine.addSystem(system);
    }

    private Entity bullet(float x, float y, float velocityX, float lifetime, float maxTravelDistance) {
        TransformComponent transform = transform(x, y);
        CollisionComponent collision = collision(0f, 0f, 4f, 4f);
        place(transform, collision, x, y);
        MovementComponent movement = movement();
        movement.velocity.x = velocityX;
        BulletComponent bulletComponent = new BulletComponent();
        bulletComponent.lifetime = lifetime;
        bulletComponent.maxTravelDistance = maxTravelDistance;
        bulletComponent.traveledDistance = 0f;
        TextureComponent textureComponent = new TextureComponent();
        textureComponent.region = new TextureRegion(mock(Texture.class));
        Entity entity = entity(transform, movement, collision, bulletComponent, new EnemyBulletComponent(), textureComponent);
        engine.addEntity(entity);
        return entity;
    }

    @Test
    public void bulletDespawnsAtShootRangeDistance() {
        // velocity.x = 100 u/s, maxTravelDistance = 128 world units -> despawn at 1.28s = 77 frames.
        // Lifetime 999f guarantees only the distance despawn can kill it.
        Entity bullet = bullet(0f, 0f, 100f, 999f, 128f);

        // One frame short: traveled ~126.7 < 128 -> still alive.
        for (int i = 0; i < 76; i++) {
            engine.update(DT);
        }
        assertTrue("bullet below its travel range must still be alive",
                engine.getEntities().contains(bullet, true));

        // One more frame: traveled ~128.3 >= 128 -> removed by distance.
        engine.update(DT);

        assertFalse("bullet must despawn once it traveled exactly its shootRange",
                engine.getEntities().contains(bullet, true));
    }
}
