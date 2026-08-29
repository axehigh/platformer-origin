package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.assets.SpriteConstants;
import com.axehigh.platformer.ecs.components.*;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import org.junit.Before;
import org.junit.Test;

import static com.axehigh.platformer.assets.GameAssetRegistry.ORIGIN_GAME_GFX;
import static com.axehigh.platformer.ecs.components.Mappers.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Headless unit tests for {@code MeleeAttackSystem}: the frame-indexed per-frame reach (windup and
 * recovery frames produce no hitbox), strike-box placement in both facing directions, one hit per
 * enemy per swing (every enemy in reach is damaged, including ones only reached on later frames,
 * and later frames do not re-damage), and the unopened/already-opened chest branches (chests open
 * independently of enemy hits). The attack animation is a 5-frame swing at 0.1s/frame, and the
 * swing's elapsed time is controlled directly through {@code PlayerComponent.meleeAttack.start(...)}
 * (start remaining = duration - desired elapsed). The {@code AssetManager} is mocked so the
 * chest-open texture swap runs without a GL context.
 */
public class MeleeAttackSystemTest extends SystemTestBase {

    private Engine engine;
    private MeleeAttackSystem system;

    @Before
    public void setUp() {
        engine = newEngine();
        system = new MeleeAttackSystem(mockAssets());
        system.setUnitScale(1f);
        engine.addSystem(system);
    }

    private static AssetManager mockAssets() {
        AssetManager assets = mock(AssetManager.class);
        TextureAtlas atlas = mock(TextureAtlas.class);
        when(atlas.findRegion(SpriteConstants.CHEST_OPEN_REGION)).thenReturn(mock(AtlasRegion.class));
        when(assets.get(ORIGIN_GAME_GFX, TextureAtlas.class)).thenReturn(atlas);
        return assets;
    }

    private static Animation<TextureRegion> attackAnimation() {
        TextureRegion[] frames = new TextureRegion[] {
            new TextureRegion(), new TextureRegion(), new TextureRegion(), new TextureRegion(), new TextureRegion()
        };
        return new Animation<TextureRegion>(0.1f, frames);
    }

    private Entity player(float x, float y, int facing) {
        TransformComponent transform = transform(x, y);
        CollisionComponent collision = collision(-15f, -30f, 30f, 60f);
        place(transform, collision, x, y);
        PlayerComponent playerComponent = player();
        playerComponent.facingDirection = facing;
        AnimationComponent animation = new AnimationComponent();
        animation.animations.put(AnimationComponent.State.ATTACKING, attackAnimation());
        Entity entity = entity(transform, playerComponent, collision, animation);
        engine.addEntity(entity);
        return entity;
    }

    private Entity enemy(float x, float y) {
        TransformComponent transform = transform(x, y);
        CollisionComponent collision = collision(-10f, -20f, 20f, 40f);
        place(transform, collision, x, y);
        Entity entity = entity(transform, movement(), collision, new EnemyComponent());
        engine.addEntity(entity);
        return entity;
    }

    private Entity chest(float x, float y, boolean opened) {
        TransformComponent transform = transform(x, y);
        CollisionComponent collision = collision(-15f, -15f, 30f, 30f);
        place(transform, collision, x, y);
        ChestComponent chestComponent = new ChestComponent();
        chestComponent.opened = opened;
        TextureComponent texture = new TextureComponent();
        texture.region = new TextureRegion();
        Entity entity = entity(transform, collision, chestComponent, texture);
        engine.addEntity(entity);
        return entity;
    }

    @Test
    public void noStrikeBoundsWhileIdle() {
        Entity player = player(0f, 130f, 1);

        engine.update(DT);

        assertNull(system.getActiveStrikeBounds());
        assertFalse(PLAYER.get(player).meleeHasHit);
    }

    @Test
    public void windupAndRecoveryFramesProduceNoStrikeBounds() {
        Entity player = player(0f, 130f, 1);
        PlayerComponent playerComponent = PLAYER.get(player);

        playerComponent.meleeAttack.start(0.45f);
        engine.update(0f);
        assertNull(system.getActiveStrikeBounds());

        playerComponent.meleeAttack.start(0.05f);
        engine.update(0f);
        assertNull(system.getActiveStrikeBounds());
    }

    @Test
    public void reachScalesWithFrame() {
        Entity player = player(0f, 130f, 1);
        PLAYER.get(player).meleeAttack.start(0.15f);

        engine.update(0f);

        Rectangle box = system.getActiveStrikeBounds();
        assertNotNull(box);
        assertEquals(24f * SpriteConstants.PlayerScale, box.width, EPSILON);
        assertEquals(15f, box.x, EPSILON);
        assertEquals(100f, box.y, EPSILON);
        assertEquals(60f, box.height, EPSILON);
    }

    @Test
    public void strikeExtendsLeftWhenFacingLeft() {
        Entity player = player(0f, 130f, -1);
        PLAYER.get(player).meleeAttack.start(0.15f);

        engine.update(0f);

        Rectangle box = system.getActiveStrikeBounds();
        assertNotNull(box);
        assertEquals(-15f - (24f * SpriteConstants.PlayerScale), box.x, EPSILON);
        assertEquals(24f * SpriteConstants.PlayerScale, box.width, EPSILON);
    }

    @Test
    public void enemyIsHitOnceOnReachFrame() {
        Entity player = player(0f, 130f, 1);
        Entity enemy = enemy(16f, 105f);
        EnemyComponent enemyComponent = ENEMY.get(enemy);
        PlayerComponent playerComponent = PLAYER.get(player);

        playerComponent.meleeAttack.start(0.15f);
        engine.update(0f);

        assertEquals(5f, enemyComponent.health, EPSILON);
        assertTrue(playerComponent.meleeHasHit);
        assertNotNull(system.getActiveStrikeBounds());
    }

    @Test
    public void noHitOnWindupFrameEvenWhenAdjacent() {
        Entity player = player(0f, 130f, 1);
        Entity enemy = enemy(16f, 105f);
        EnemyComponent enemyComponent = ENEMY.get(enemy);
        PlayerComponent playerComponent = PLAYER.get(player);

        playerComponent.meleeAttack.start(0.45f);
        engine.update(0f);

        assertEquals(10f, enemyComponent.health, EPSILON);
        assertFalse(playerComponent.meleeHasHit);
    }

    @Test
    public void oneSwingHitsAllEnemiesInRange() {
        Entity player = player(0f, 130f, 1);
        Entity first = enemy(16f, 105f);
        Entity second = enemy(20f, 105f);
        PlayerComponent playerComponent = PLAYER.get(player);

        playerComponent.meleeAttack.start(0.15f);
        engine.update(0f);

        assertEquals(5f, ENEMY.get(first).health, EPSILON);
        assertEquals(5f, ENEMY.get(second).health, EPSILON);
        assertTrue(playerComponent.meleeHasHit);
    }

    @Test
    public void enemyReachedOnLaterFrameStillHit() {
        Entity player = player(0f, 130f, 1);
        Entity enemy = enemy(15f + (44f * SpriteConstants.PlayerScale), 105f);
        EnemyComponent enemyComponent = ENEMY.get(enemy);
        PlayerComponent playerComponent = PLAYER.get(player);

        playerComponent.meleeAttack.start(0.35f);
        engine.update(0f);
        engine.update(0.1f);
        assertEquals(10f, enemyComponent.health, EPSILON);
        engine.update(0.1f);
        assertEquals(10f, enemyComponent.health, EPSILON);

        engine.update(0.1f);
        assertEquals(5f, enemyComponent.health, EPSILON);
        assertTrue(playerComponent.meleeHasHit);
    }

    @Test
    public void chestAndEnemyBothHitInOneSwing() {
        Entity player = player(0f, 130f, 1);
        Entity chest = chest(16f, 105f, false);
        Entity enemy = enemy(22f, 105f);
        ChestComponent chestComponent = CHEST.get(chest);
        EnemyComponent enemyComponent = ENEMY.get(enemy);
        PlayerComponent playerComponent = PLAYER.get(player);

        playerComponent.meleeAttack.start(0.25f);
        engine.update(0f);

        assertTrue(chestComponent.opened);
        assertEquals(5f, enemyComponent.health, EPSILON);
        assertTrue(playerComponent.meleeHasHit);
    }

    @Test
    public void laterFramesDoNotReDamage() {
        Entity player = player(0f, 130f, 1);
        Entity enemy = enemy(16f, 105f);
        EnemyComponent enemyComponent = ENEMY.get(enemy);
        PlayerComponent playerComponent = PLAYER.get(player);

        playerComponent.meleeAttack.start(0.35f);
        engine.update(0f);
        assertEquals(5f, enemyComponent.health, EPSILON);

        engine.update(0.1f);
        engine.update(0.1f);
        assertEquals(5f, enemyComponent.health, EPSILON);
    }

    @Test
    public void chainedSwingNeedsSwingStartResetToRehit() {
        Entity player = player(0f, 130f, 1);
        Entity enemy = enemy(16f, 105f);
        EnemyComponent enemyComponent = ENEMY.get(enemy);
        PlayerComponent playerComponent = PLAYER.get(player);

        playerComponent.meleeAttack.start(0.15f);
        engine.update(0f);
        assertEquals(5f, enemyComponent.health, EPSILON);

        enemyComponent.hitStun.update(0.5f);

        // A re-started swing WITHOUT resetting the per-swing set (what input's swing-start reset
        // guards against) cannot re-damage, even across swing boundaries
        playerComponent.meleeAttack.start(0.15f);
        playerComponent.meleeHasHit = false;
        engine.update(0f);
        assertEquals(5f, enemyComponent.health, EPSILON);

        // ...so PlayerInputSystem clears the set at every swing start, letting the next swing land
        playerComponent.meleeAttack.start(0.15f);
        playerComponent.meleeHasHit = false;
        playerComponent.meleeHitEnemies.clear();
        engine.update(0f);
        assertEquals(0f, enemyComponent.health, EPSILON);
    }

    @Test
    public void unopenedChestOpensOnHit() {
        Entity player = player(0f, 130f, 1);
        Entity chest = chest(16f, 105f, false);
        ChestComponent chestComponent = CHEST.get(chest);
        PlayerComponent playerComponent = PLAYER.get(player);

        playerComponent.meleeAttack.start(0.15f);
        engine.update(0f);

        assertTrue(chestComponent.opened);
        assertTrue(chestComponent.disappearTimer.isActive());
        assertTrue(playerComponent.meleeHasHit);
    }

    @Test
    public void alreadyOpenedChestDoesNotConsumeSwing() {
        Entity player = player(0f, 130f, 1);
        Entity chest = chest(16f, 105f, true);
        Entity enemy = enemy(24f, 105f);
        ChestComponent chestComponent = CHEST.get(chest);
        PlayerComponent playerComponent = PLAYER.get(player);

        playerComponent.meleeAttack.start(0.15f);
        engine.update(0f);

        assertTrue(chestComponent.opened);
        assertFalse(chestComponent.disappearTimer.isActive());
        assertEquals(5f, ENEMY.get(enemy).health, EPSILON);
        assertTrue(playerComponent.meleeHasHit);
    }
}
