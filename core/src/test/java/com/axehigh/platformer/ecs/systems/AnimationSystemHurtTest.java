package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.ecs.components.AnimationComponent;
import com.axehigh.platformer.ecs.components.MovementComponent;
import com.axehigh.platformer.ecs.components.PlayerComponent;
import com.axehigh.platformer.ecs.components.TextureComponent;
import com.axehigh.platformer.ecs.components.TransformComponent;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Headless tests for the player hurt/blink animation resolution in {@code AnimationSystem}: while
 * {@code PlayerComponent.hurtTimer} is active the state is {@code HURT}; once it ends (but while
 * {@code hitInvulnerability} is still active) the state returns to normal and the sprite blinks by
 * nulling {@code TextureComponent.region} on alternating blink phases.
 */
public class AnimationSystemHurtTest extends SystemTestBase {
    private Engine engine;
    private AnimationSystem system;
    private AnimationComponent animation;
    private TextureComponent texture;
    private PlayerComponent player;

    @Before
    public void setUp() {
        system = new AnimationSystem();
        engine = newEngine();
        engine.addSystem(system);

        animation = new AnimationComponent();
        texture = new TextureComponent();
        player = new PlayerComponent();
        MovementComponent movement = new MovementComponent();

        Entity entity = new Entity();
        entity.add(animation);
        entity.add(texture);
        entity.add(player);
        entity.add(movement);
        entity.add(new TransformComponent());
        engine.addEntity(entity);
    }

    @Test
    public void hurtWindowShowsHurtState() {
        player.hurtTimer.start(0.3f);

        engine.update(DT);

        assertEquals(AnimationComponent.State.HURT, animation.currentState);
    }

    @Test
    public void afterHurtWindowStateReturnsToNormalAndBlinkNullsRegion() {
        player.hitInvulnerability.start(1f);
        texture.region = new TextureRegion();

        for (int i = 0; i < 10; i++) {
            engine.update(DT);
        }

        assertNotEquals(AnimationComponent.State.HURT, animation.currentState);
        assertTrue(animation.blinkTimer > 0f);
        assertNull(texture.region);
    }

    @Test
    public void noBlinkWhenNotInvulnerable() {
        texture.region = new TextureRegion();

        for (int i = 0; i < 10; i++) {
            engine.update(DT);
        }

        assertEquals(0f, animation.blinkTimer, EPSILON);
        assertNotNull(texture.region);
    }
}
