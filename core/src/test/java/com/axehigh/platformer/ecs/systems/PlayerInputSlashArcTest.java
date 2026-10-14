package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.assets.GameAssetRegistry;
import com.axehigh.platformer.assets.SpriteConstants;
import com.axehigh.platformer.util.FeatureFlags;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.PooledEngine;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static com.axehigh.platformer.ecs.components.Mappers.TEXTURE;
import static com.axehigh.platformer.ecs.components.Mappers.TRANSFORM;
import static com.badlogic.gdx.Input.Keys.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Headless tests for the melee slash-arc spawn in {@code PlayerInputSystem}: exactly one cosmetic
 * {@code SlashArcComponent} entity per swing, flipped horizontally for a left-facing swing, and
 * gated off entirely by {@code FeatureFlags.isSlashArcEnabled()}. The arc needs the loaded
 * {@code slash_arc} region — stubbed via a mocked {@code AssetManager}/{@code TextureAtlas}/
 * {@code AtlasRegion} (plain Java objects, no GL) so the region-resolution path runs for real.
 */
public class PlayerInputSlashArcTest extends SystemTestBase {
    private static final int REGION_SIZE = 128;

    private PooledEngine engine;
    private com.axehigh.platformer.ecs.systems.PlayerInputSystem system;
    private com.axehigh.platformer.ecs.components.PlayerComponent player;
    private AssetManager assetManager;

    @Before
    public void setUp() {
        Gdx.input = mock(Input.class);
        FeatureFlags.setSlashArcEnabled(true);

        TextureAtlas.AtlasRegion region = mock(TextureAtlas.AtlasRegion.class);
        when(region.getRegionWidth()).thenReturn(REGION_SIZE);
        when(region.getRegionHeight()).thenReturn(REGION_SIZE);
        TextureAtlas atlas = mock(TextureAtlas.class);
        when(atlas.findRegion(SpriteConstants.SLASH_ARC_REGION)).thenReturn(region);
        assetManager = mock(AssetManager.class);
        when(assetManager.isLoaded(GameAssetRegistry.ORIGIN_GAME_GFX)).thenReturn(true);
        when(assetManager.get(GameAssetRegistry.ORIGIN_GAME_GFX, TextureAtlas.class)).thenReturn(atlas);

        system = new com.axehigh.platformer.ecs.systems.PlayerInputSystem(assetManager);
        engine = new PooledEngine();
        engine.addSystem(system);

        com.axehigh.platformer.ecs.components.TransformComponent transform = engine.createComponent(com.axehigh.platformer.ecs.components.TransformComponent.class);
        transform.position.set(0f, 0f);
        com.axehigh.platformer.ecs.components.MovementComponent movement = engine.createComponent(com.axehigh.platformer.ecs.components.MovementComponent.class);
        com.axehigh.platformer.ecs.components.CollisionComponent collision = engine.createComponent(com.axehigh.platformer.ecs.components.CollisionComponent.class);
        collision.bounds.set(-8f, -32f, 16f, 32f);
        player = engine.createComponent(com.axehigh.platformer.ecs.components.PlayerComponent.class);
        Entity entity = engine.createEntity();
        entity.add(transform);
        entity.add(movement);
        entity.add(collision);
        entity.add(player);
        engine.addEntity(entity);
    }

    @After
    public void tearDown() {
        Gdx.input = null;
        // Restore the default so any test class running afterwards isn't affected by this one.
        FeatureFlags.setSlashArcEnabled(true);
    }

    private int countSlashArcs() {
        int count = 0;
        for (Entity e : engine.getEntities()) {
            if (e.getComponent(com.axehigh.platformer.ecs.components.SlashArcComponent.class) != null) {
                count++;
            }
        }
        return count;
    }

    private Entity singleArc() {
        for (Entity e : engine.getEntities()) {
            if (e.getComponent(com.axehigh.platformer.ecs.components.SlashArcComponent.class) != null) {
                return e;
            }
        }
        return null;
    }

    @Test
    public void singleSwingSpawnsExactlyOneSlashArc() {
        when(Gdx.input.isKeyJustPressed(J)).thenReturn(true, false, false);

        engine.update(DT);
        Entity arc = singleArc();
        assertNotNull(arc);
        assertTrue("the arc must carry the resolved region", TEXTURE.get(arc).region != null);
        assertEquals("region resolved through the stubbed atlas", REGION_SIZE, TEXTURE.get(arc).region.getRegionWidth());
        assertEquals("one swing spawns one arc", 1, countSlashArcs());

        // The release frames add no arc: a single press must never double-spawn.
        engine.update(DT);
        assertEquals("frame 2", 1, countSlashArcs());
        engine.update(DT);
        assertEquals("frame 3", 1, countSlashArcs());
    }

    @Test
    public void secondSwingAfterCooldownSpawnsSecondArc() {
        // Press J on frame 0, release, press again on frame 8 (past the 0.12s melee cooldown).
        when(Gdx.input.isKeyJustPressed(J))
            .thenReturn(true, false, false, false, false, false, false, false, true);

        int arcsAfterFirstSwing = 0;
        for (int frame = 0; frame < 9; frame++) {
            engine.update(DT);
            int count = countSlashArcs();
            if (frame == 0) {
                arcsAfterFirstSwing = count;
            }
            assertTrue("frame " + frame + ": at most one new arc per frame", count <= arcsAfterFirstSwing + 1);
        }

        assertEquals("one arc per swing, never two", 2, countSlashArcs());
    }

    @Test
    public void leftFacingSwingFlipsArcScaleX() {
        when(Gdx.input.isKeyPressed(A)).thenReturn(true);
        when(Gdx.input.isKeyJustPressed(J)).thenReturn(true);

        engine.update(DT);

        assertTrue("sanity: player faces left", player.facingDirection < 0);
        Entity arc = singleArc();
        assertNotNull(arc);
        assertTrue("left-facing swings flip the arc horizontally", TRANSFORM.get(arc).scale.x < 0f);
    }

    @Test
    public void rightFacingSwingKeepsArcScaleXPositive() {
        when(Gdx.input.isKeyPressed(D)).thenReturn(true);
        when(Gdx.input.isKeyJustPressed(J)).thenReturn(true);

        engine.update(DT);

        assertTrue("sanity: player faces right", player.facingDirection > 0);
        Entity arc = singleArc();
        assertNotNull(arc);
        assertTrue("right-facing swings keep the arc upright", TRANSFORM.get(arc).scale.x > 0f);
    }

    @Test
    public void slashArcDisabledSpawnsNoArc() {
        FeatureFlags.setSlashArcEnabled(false);
        when(Gdx.input.isKeyJustPressed(J)).thenReturn(true);

        engine.update(DT);

        assertEquals("with the flag off, a swing spawns no arc", 0, countSlashArcs());
        assertTrue("the melee swing itself still fires", player.meleeAttack.isActive());
    }
}
