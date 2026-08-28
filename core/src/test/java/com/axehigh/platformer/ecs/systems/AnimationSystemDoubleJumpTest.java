package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.ecs.components.*;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Headless unit tests for player double jump animation state in {@code AnimationSystem}.
 */
public class AnimationSystemDoubleJumpTest extends SystemTestBase {
    private Engine engine;
    private AnimationSystem system;
    private AnimationComponent animation;
    private PlayerComponent player;
    private MovementComponent movement;

    @Before
    public void setUp() {
        system = new AnimationSystem();
        engine = newEngine();
        engine.addSystem(system);

        animation = new AnimationComponent();
        animation.animations.put(AnimationComponent.State.JUMPING, new Animation<>(0.1f, new TextureRegion()));
        animation.animations.put(AnimationComponent.State.DOUBLE_JUMPING, new Animation<>(0.1f, new TextureRegion()));

        player = new PlayerComponent();
        movement = new MovementComponent();

        Entity entity = new Entity();
        entity.add(animation);
        entity.add(new TextureComponent());
        entity.add(player);
        entity.add(movement);
        entity.add(new TransformComponent());
        engine.addEntity(entity);
    }

    @Test
    public void firstJumpShowsJumpingState() {
        movement.grounded = false;
        player.jumpCount = 1;

        engine.update(DT);

        assertEquals(AnimationComponent.State.JUMPING, animation.currentState);
    }

    @Test
    public void secondJumpShowsDoubleJumpState() {
        movement.grounded = false;
        player.jumpCount = 2;

        engine.update(DT);

        assertEquals(AnimationComponent.State.DOUBLE_JUMPING, animation.currentState);
    }
}
