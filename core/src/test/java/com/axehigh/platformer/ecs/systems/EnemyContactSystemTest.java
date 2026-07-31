package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.ecs.components.CollisionComponent;
import com.axehigh.platformer.ecs.components.EnemyComponent;
import com.axehigh.platformer.ecs.components.PlayerComponent;
import com.axehigh.platformer.ecs.components.TransformComponent;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import org.junit.Before;
import org.junit.Test;

import static com.axehigh.platformer.ecs.components.Mappers.ENEMY;
import static com.axehigh.platformer.ecs.components.Mappers.PLAYER;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Headless unit tests for {@code EnemyContactSystem}: overlap-based contact damage, the one-second
 * hit-invulnerability grace period (no repeated damage while it's active, damage resumes once it
 * expires), and that dead enemies deal no contact damage.
 */
public class EnemyContactSystemTest extends SystemTestBase {

    private Engine engine;
    private EnemyContactSystem system;

    @Before
    public void setUp() {
        system = new EnemyContactSystem();
        engine = newEngine();
        engine.addSystem(system);
    }

    private Entity player(float x, float y) {
        TransformComponent transform = transform(x, y);
        CollisionComponent collision = collision(-15f, -30f, 30f, 60f);
        place(transform, collision, x, y);
        Entity entity = entity(transform, player(), collision);
        engine.addEntity(entity);
        return entity;
    }

    private Entity enemy(float x, float y) {
        TransformComponent transform = transform(x, y);
        CollisionComponent collision = collision(-10f, -20f, 20f, 40f);
        place(transform, collision, x, y);
        Entity entity = entity(transform, collision, new EnemyComponent());
        engine.addEntity(entity);
        return entity;
    }

    @Test
    public void contactDamagesOnceThenGraceProtects() {
        Entity player = player(0f, 130f);
        enemy(0f, 130f);
        PlayerComponent playerComponent = PLAYER.get(player);

        engine.update(0.1f);
        assertEquals(2, playerComponent.health);
        assertTrue(playerComponent.hitInvulnerability.isActive());

        engine.update(0.1f);
        assertEquals(2, playerComponent.health);
    }

    @Test
    public void contactDamagesAgainAfterGraceExpires() {
        Entity player = player(0f, 130f);
        enemy(0f, 130f);
        PlayerComponent playerComponent = PLAYER.get(player);

        engine.update(0.1f);
        engine.update(0.5f);
        assertEquals(2, playerComponent.health);

        engine.update(0.6f);
        assertEquals(1, playerComponent.health);
        assertTrue(playerComponent.hitInvulnerability.isActive());
    }

    @Test
    public void deadEnemyCausesNoContactDamage() {
        Entity player = player(0f, 130f);
        Entity enemy = enemy(0f, 130f);
        ENEMY.get(enemy).isDead = true;
        PlayerComponent playerComponent = PLAYER.get(player);

        engine.update(0.1f);

        assertEquals(3, playerComponent.health);
    }
}
