package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.PlayerConfig;
import com.axehigh.platformer.ecs.components.CollisionComponent;
import com.axehigh.platformer.ecs.components.MovementComponent;
import com.axehigh.platformer.ecs.components.PlayerComponent;
import com.axehigh.platformer.ecs.components.TransformComponent;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import org.junit.Before;
import org.junit.Test;

import static com.axehigh.platformer.ecs.components.Mappers.MOVEMENT;
import static com.axehigh.platformer.ecs.components.Mappers.PLAYER;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Headless unit tests for {@code HazardSystem}: tile hazards (spikes/lava) deal exactly one point
 * of damage on overlap, apply no horizontal knockback, respect the shared invulnerability grace
 * period (one hit per window for a sustained overlap), ignore dead players, and do nothing when
 * there's no overlap. The grace/hurt timers are ticked manually (as in {@code PlayerHurtTest})
 * since {@code EnemyContactSystem} owns the per-frame ticking in the real engine.
 */
public class HazardSystemTest extends SystemTestBase {

    private final Array<Rectangle> hazardRects = new Array<>();
    private Engine engine;
    private com.axehigh.platformer.ecs.systems.HazardSystem system;

    @Before
    public void setUp() {
        system = new com.axehigh.platformer.ecs.systems.HazardSystem(hazardRects);
        engine = newEngine();
        engine.addSystem(system);
    }

    private Entity playerOverlapping(float x, float y) {
        TransformComponent transform = transform(x, y);
        CollisionComponent collision = collision(-15f, -30f, 30f, 60f);
        place(transform, collision, x, y);
        Entity entity = entity(transform, player(), movement(), collision);
        engine.addEntity(entity);
        return entity;
    }

    @Test
    public void hazardOverlapDealsOneDamageNoKnockbackAndStartsGrace() {
        hazardRects.add(new Rectangle(0f, 0f, 100f, 50f));
        Entity entity = playerOverlapping(0f, 0f);
        PlayerComponent player = PLAYER.get(entity);
        MovementComponent movement = MOVEMENT.get(entity);
        movement.velocity.x = 50f;

        engine.update(DT);

        assertEquals(PlayerConfig.MAX_HEALTH - 1, player.health);
        assertEquals(50f, movement.velocity.x, EPSILON);
        assertTrue(player.hurtTimer.isActive());
        assertTrue(player.hitInvulnerability.isActive());
    }

    @Test
    public void sustainedOverlapDealsOneHitPerGraceWindow() {
        hazardRects.add(new Rectangle(0f, 0f, 100f, 50f));
        Entity entity = playerOverlapping(0f, 0f);
        PlayerComponent player = PLAYER.get(entity);

        engine.update(DT);
        engine.update(DT);
        assertEquals(PlayerConfig.MAX_HEALTH - 1, player.health);

        player.hitInvulnerability.update(com.axehigh.platformer.ecs.systems.PlayerDamageResolver.HIT_INVULNERABILITY_DURATION);
        engine.update(DT);
        assertEquals(PlayerConfig.MAX_HEALTH - 2, player.health);
    }

    @Test
    public void noOverlapDealsNoDamage() {
        hazardRects.add(new Rectangle(500f, 500f, 100f, 50f));
        Entity entity = playerOverlapping(0f, 0f);
        PlayerComponent player = PLAYER.get(entity);

        engine.update(DT);

        assertEquals(PlayerConfig.MAX_HEALTH, player.health);
    }

    @Test
    public void deadPlayerIgnoresHazard() {
        hazardRects.add(new Rectangle(0f, 0f, 100f, 50f));
        Entity entity = playerOverlapping(0f, 0f);
        PlayerComponent player = PLAYER.get(entity);
        player.isDead = true;

        engine.update(DT);

        assertEquals(PlayerConfig.MAX_HEALTH, player.health);
    }
}
