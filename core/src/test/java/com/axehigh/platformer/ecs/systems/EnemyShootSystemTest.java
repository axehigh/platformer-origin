package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.ecs.components.CollisionComponent;
import com.axehigh.platformer.ecs.components.EnemyComponent;
import com.axehigh.platformer.ecs.components.EnemyShooterComponent;
import com.axehigh.platformer.ecs.components.TransformComponent;
import com.axehigh.platformer.map.RoomState;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.PooledEngine;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Texture;
import org.junit.Before;
import org.junit.Test;

import static com.axehigh.platformer.ecs.components.Mappers.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Headless unit tests for {@code EnemyShootSystem}: the cooldown-triggered firing, the room-activity
 * gate, and the hit-stun suppression — no shots while the shooter is inside its hit-stun, with
 * firing resuming immediately afterwards (the post-hit idle recovery window does NOT gate firing).
 * The {@code AssetManager} is mocked so the bullet's texture resolves headless.
 */
public class EnemyShootSystemTest extends SystemTestBase {

    private static final String BULLET_TEXTURE_PATH = "gfx/old/bullet.png";

    private PooledEngine engine;
    private EnemyShootSystem system;
    private RoomState roomState;

    @Before
    public void setUp() {
        roomState = new RoomState();
        system = new EnemyShootSystem(mockAssets(), roomState);
        engine = new PooledEngine();
        engine.addSystem(system);
    }

    private static AssetManager mockAssets() {
        AssetManager assets = mock(AssetManager.class);
        when(assets.get(BULLET_TEXTURE_PATH, Texture.class)).thenReturn(mock(Texture.class));
        return assets;
    }

    private Entity shooter(float x, float y) {
        TransformComponent transform = transform(x, y);
        CollisionComponent collision = collision(0f, 0f, 20f, 40f);
        place(transform, collision, x, y);
        EnemyComponent enemy = new EnemyComponent();
        enemy.direction = 1;
        EnemyShooterComponent shooter = new EnemyShooterComponent();
        shooter.shootCooldown.reset();
        Entity entity = entity(transform, collision, enemy, shooter);
        engine.addEntity(entity);
        return entity;
    }

    private Entity singleBullet() {
        for (Entity e : engine.getEntities()) {
            if (BULLET.get(e) != null) {
                return e;
            }
        }
        return null;
    }

    @Test
    public void readyShooterFiresOnceCooldownDone() {
        shooter(0f, 0f);

        engine.update(DT);

        Entity bullet = singleBullet();
        assertNotNull("cooldown-done shooter should spawn a bullet", bullet);
        assertEquals(150f, MOVEMENT.get(bullet).velocity.x, EPSILON);
        assertEquals(0f, MOVEMENT.get(bullet).velocity.y, EPSILON);
        assertNotNull("bullet should be tagged as an enemy bullet", ENEMY_BULLET.get(bullet));
        assertNotNull("bullet should be tagged as a bullet", BULLET.get(bullet));
    }

    @Test
    public void postHitIdleEnemyFiresAgain() {
        Entity shooter = shooter(0f, 0f);
        ENEMY.get(shooter).postHitIdle.start(0.5f);

        engine.update(DT);

        assertEquals(2, engine.getEntities().size());
        assertNotNull("post-hit-idle shooter should fire", singleBullet());
    }

    @Test
    public void postHitIdleShooterFiresOnceCooldownDone() {
        Entity shooter = shooter(0f, 0f);
        ENEMY.get(shooter).postHitIdle.start(0.5f);
        ENEMY_SHOOTER.get(shooter).shootCooldown.start(0.3f);

        engine.update(DT);
        assertEquals(1, engine.getEntities().size());

        // The cooldown finishes while the post-hit idle is still running; firing must resume
        // regardless of the recovery window.
        ENEMY_SHOOTER.get(shooter).shootCooldown.update(1f);
        assertTrue(ENEMY.get(shooter).postHitIdle.isActive());

        engine.update(DT);

        assertEquals(2, engine.getEntities().size());
        assertNotNull("active post-hit idle must not gate firing once the cooldown is done", singleBullet());
    }

    @Test
    public void hitStunnedEnemyDoesNotFire() {
        Entity shooter = shooter(0f, 0f);
        ENEMY.get(shooter).hitStun.start(0.3f);

        engine.update(DT);

        assertEquals(1, engine.getEntities().size());
        assertNull(singleBullet());
    }

    @Test
    public void shooterInInactiveRoomDoesNotFire() {
        Entity shooter = shooter(0f, 0f);
        ENEMY.get(shooter).roomIndex = 0;
        roomState.activeRoomIndex = 1;

        engine.update(DT);

        assertEquals(1, engine.getEntities().size());
        assertNull(singleBullet());
    }
}
