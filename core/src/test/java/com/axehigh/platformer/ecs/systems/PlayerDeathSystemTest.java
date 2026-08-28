package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.ecs.components.AnimationComponent;
import com.axehigh.platformer.ecs.components.MovementComponent;
import com.axehigh.platformer.ecs.components.PlayerComponent;
import com.axehigh.platformer.ecs.components.TransformComponent;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import org.junit.Before;
import org.junit.Test;

import static com.axehigh.platformer.ecs.components.Mappers.TRANSFORM;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Headless tests for {@code PlayerDeathSystem}'s deferred death sequence: on the first frame
 * {@code health <= 0} the player is marked dead and frozen (velocity zeroed) but the Game Screen
 * callback is NOT fired yet; it fires exactly once after the death-wait — {@code max(DEATH
 * animation duration, 0.8s) + 0.5s} — elapses, and is re-armed if the player is revived.
 */
public class PlayerDeathSystemTest extends SystemTestBase {

    private Engine engine;
    private PlayerDeathSystem system;
    private final int[] callbackCount = new int[1];
    private PlayerComponent player;
    private MovementComponent movement;

    @Before
    public void setUp() {
        callbackCount[0] = 0;
        system = new PlayerDeathSystem(() -> callbackCount[0]++, -1000f, 0);
        engine = newEngine();
        engine.addSystem(system);
    }

    private void playerWithHealth(int health) {
        player = new PlayerComponent();
        player.health = health;
        movement = movement();
        TransformComponent transform = transform(0f, 0f);
        Entity entity = entity(player, movement, transform);
        engine.addEntity(entity);
    }

    private void playerWithHealthAndDeathAnimation(int health, float frameDuration, int frames) {
        player = new PlayerComponent();
        player.health = health;
        movement = movement();
        TransformComponent transform = transform(0f, 0f);
        AnimationComponent animation = new AnimationComponent();
        TextureRegion[] regions = new TextureRegion[frames];
        for (int i = 0; i < frames; i++) {
            regions[i] = new TextureRegion();
        }
        animation.animations.put(AnimationComponent.State.DEATH, new Animation<>(frameDuration, regions));
        Entity entity = entity(player, movement, transform, animation);
        engine.addEntity(entity);
    }

    @Test
    public void marksDeadAndFreezesButDefersCallback() {
        playerWithHealth(0);
        movement.velocity.set(50f, 20f);
        engine.update(DT);

        assertTrue(player.isDead);
        assertEquals(0f, movement.velocity.x, EPSILON);
        assertEquals(0f, movement.velocity.y, EPSILON);
        assertEquals(0, callbackCount[0]);
    }

    @Test
    public void callbackFiresAfterDeathDelayElapses() {
        playerWithHealth(0);
        engine.update(DT);

        for (int i = 0; i < 12; i++) {
            engine.update(0.1f);
        }
        assertEquals(0, callbackCount[0]);

        engine.update(0.1f);
        assertEquals(1, callbackCount[0]);

        engine.update(0.1f);
        assertEquals(1, callbackCount[0]);
    }

    @Test
    public void callbackWaitsForLongerAnimationDuration() {
        // 0.5s/frame * 4 frames = 2.0s animation -> delay = max(0.8, 2.0) + 0.5 = 2.5s.
        playerWithHealthAndDeathAnimation(0, 0.5f, 4);
        engine.update(DT);

        for (int i = 0; i < 16; i++) {
            engine.update(0.1f);
        }
        assertEquals(0, callbackCount[0]);

        for (int i = 0; i < 10; i++) {
            engine.update(0.1f);
        }
        assertEquals(1, callbackCount[0]);
    }

    @Test
    public void fallingBelowKillPlaneTriggersDeath() {
        playerWithHealth(3);
        TransformComponent transform = TRANSFORM.get(engine.getEntities().first());
        transform.position.y = -1500f;
        engine.update(DT);

        assertTrue(player.isDead);
        assertEquals(0, player.health);
    }

    @Test
    public void revivalRearmsDeathTrigger() {
        playerWithHealth(0);
        engine.update(DT);
        for (int i = 0; i < 13; i++) {
            engine.update(0.1f);
        }
        assertEquals(1, callbackCount[0]);

        player.health = 3;
        engine.update(DT);
        assertEquals(false, player.isDead);

        player.health = 0;
        engine.update(DT);
        assertEquals(1, callbackCount[0]);
        for (int i = 0; i < 13; i++) {
            engine.update(0.1f);
        }
        assertEquals(2, callbackCount[0]);
    }
}
