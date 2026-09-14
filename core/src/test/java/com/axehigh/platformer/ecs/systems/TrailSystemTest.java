package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.PlayerConfig;
import com.axehigh.platformer.ecs.components.TextureComponent;
import com.axehigh.platformer.ecs.components.TransformComponent;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static com.axehigh.platformer.ecs.components.Mappers.TRAIL;
import static com.axehigh.platformer.ecs.components.Mappers.TRANSFORM;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

/**
 * Headless tests for {@code TrailSystem}: spawned trail ghosts carry only
 * Transform/Texture/Trail components, fade their alpha over {@code BULLET_TRAIL_LIFETIME},
 * remove themselves on expiry, and apply the left-facing anchor shift that keeps them aligned
 * with the RenderSystem fallback branch. {@code Gdx.gl}/{@code Gdx.graphics} are stubbed (same
 * pattern as {@code VignetteRenderSystemTest}) so {@code Pixmap}-backed {@code Texture} regions
 * can be created without a real GL context.
 */
public class TrailSystemTest extends SystemTestBase {

    private GL20 previousGl;
    private Graphics previousGraphics;
    private Engine engine;
    private TrailSystem system;
    private Texture texture;

    @Before
    public void setUp() {
        previousGl = Gdx.gl;
        previousGraphics = Gdx.graphics;
        Gdx.gl = mock(GL20.class);
        Gdx.graphics = mock(Graphics.class);
        texture = new Texture(new Pixmap(32, 16, Pixmap.Format.RGBA8888));
        system = new TrailSystem();
        engine = newEngine();
        engine.addSystem(system);
    }

    @After
    public void tearDown() {
        texture.dispose();
        Gdx.gl = previousGl;
        Gdx.graphics = previousGraphics;
    }

    private TextureComponent textureComponent(int w, int h) {
        TextureComponent texture = new TextureComponent();
        texture.region = new TextureRegion(this.texture, 0, 0, w, h);
        return texture;
    }

    @Test
    public void ghostFadesOutAndRemovesItself() {
        Entity ghost = TrailSystem.spawnTrail(engine, transform(100f, 50f), textureComponent(32, 16));
        assertNotNull(ghost);
        assertEquals(PlayerConfig.BULLET_TRAIL_LIFETIME, TRAIL.get(ghost).lifeTime, EPSILON);
        assertEquals(1f, TRANSFORM.get(ghost).alpha, EPSILON);

        engine.update(DT);
        assertTrue("ghost should start fading after one frame", TRANSFORM.get(ghost).alpha < 1f);

        for (int i = 0; i < 12; i++) {
            engine.update(DT);
        }
        assertEquals(0, engine.getEntities().size());
    }

    @Test
    public void rightFacingGhostCopiesPositionAndScale() {
        TransformComponent bulletTransform = transform(100f, 50f);
        bulletTransform.scale.set(2f, 2f);
        bulletTransform.z = 8f;

        Entity ghost = TrailSystem.spawnTrail(engine, bulletTransform, textureComponent(32, 16));

        TransformComponent ghostTransform = TRANSFORM.get(ghost);
        assertEquals(100f, ghostTransform.position.x, EPSILON);
        assertEquals(50f, ghostTransform.position.y, EPSILON);
        assertEquals(2f, ghostTransform.scale.x, EPSILON);
        assertEquals(2f, ghostTransform.scale.y, EPSILON);
        assertEquals(8f, ghostTransform.z, EPSILON);
    }

    @Test
    public void leftFacingGhostShiftsAnchorToMatchRenderer() {
        TransformComponent bulletTransform = transform(100f, 50f);
        bulletTransform.scale.x = -1f; // left-facing bullet

        Entity ghost = TrailSystem.spawnTrail(engine, bulletTransform, textureComponent(32, 16));

        // RenderSystem's fallback branch shifts drawX by regionWidth * |scale.x| for left-facing
        // sprites; the ghost position must compensate so it overlays the bullet exactly.
        assertEquals(100f - 32f, TRANSFORM.get(ghost).position.x, EPSILON);
        assertEquals(50f, TRANSFORM.get(ghost).position.y, EPSILON);
    }

    @Test
    public void nullRegionReturnsNullGhost() {
        assertNull(TrailSystem.spawnTrail(engine, transform(0f, 0f), new TextureComponent()));
    }

    @Test
    public void ghostCarriesOnlyCosmeticComponents() {
        Entity ghost = TrailSystem.spawnTrail(engine, transform(100f, 50f), textureComponent(32, 16));
        assertNotNull(TRAIL.get(ghost));
        assertEquals(3, ghost.getComponents().size());
    }
}
