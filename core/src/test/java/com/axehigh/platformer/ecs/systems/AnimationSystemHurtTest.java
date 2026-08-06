package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.ecs.components.AnimationComponent;
import com.axehigh.platformer.ecs.components.EnemyComponent;
import com.axehigh.platformer.ecs.components.MovementComponent;
import com.axehigh.platformer.ecs.components.PlayerComponent;
import com.axehigh.platformer.ecs.components.TextureComponent;
import com.axehigh.platformer.ecs.components.TransformComponent;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import org.junit.Before;
import org.junit.Test;

import static com.axehigh.platformer.ecs.components.Mappers.ANIMATION;
import static com.axehigh.platformer.ecs.components.Mappers.TEXTURE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Headless tests for the hurt/blink animation resolution in {@code AnimationSystem}: while
 * {@code PlayerComponent.hurtTimer} is active the player state is {@code HURT}; once it ends (but
 * while {@code hitInvulnerability} is still active) the state returns to normal and the sprite
 * blinks by nulling {@code TextureComponent.region} on alternating blink phases. A dead enemy's
 * corpse blinks the same way during the final {@code DEATH_FLASH_DURATION} window of its
 * {@code deathTimer}.
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

    private Entity deadEnemy(float deathTimerDuration) {
        EnemyComponent enemy = new EnemyComponent();
        enemy.isDead = true;
        enemy.deathTimer.start(deathTimerDuration);

        TextureComponent enemyTexture = new TextureComponent();
        enemyTexture.region = new TextureRegion();

        Entity entity = new Entity();
        entity.add(new AnimationComponent());
        entity.add(enemyTexture);
        entity.add(enemy);
        entity.add(new MovementComponent());
        entity.add(new TransformComponent());
        engine.addEntity(entity);
        return entity;
    }

    @Test
    public void deadEnemyBlinksDuringFinalFlashWindow() {
        Entity entity = deadEnemy(EnemyDamageResolver.DEATH_FLASH_DURATION);
        AnimationComponent enemyAnimation = ANIMATION.get(entity);
        TextureComponent enemyTexture = TEXTURE.get(entity);

        for (int i = 0; i < 10; i++) {
            engine.update(DT);
        }

        assertEquals(AnimationComponent.State.DEATH, enemyAnimation.currentState);
        assertTrue(enemyAnimation.blinkTimer > 0f);
        assertNull(enemyTexture.region);
    }

    @Test
    public void deadEnemyDoesNotBlinkDuringDeathAnimation() {
        Entity entity = deadEnemy(2f);
        AnimationComponent enemyAnimation = ANIMATION.get(entity);
        TextureComponent enemyTexture = TEXTURE.get(entity);

        for (int i = 0; i < 5; i++) {
            engine.update(DT);
        }

        assertEquals(0f, enemyAnimation.blinkTimer, EPSILON);
        assertNotNull(enemyTexture.region);
    }
}
