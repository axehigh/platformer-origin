package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.ecs.components.*;
import com.axehigh.platformer.util.FeatureFlags;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.PooledEngine;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static com.axehigh.platformer.ecs.components.Mappers.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

/**
 * Integration tests for the PlayerBulletSystem - TrailSystem pipeline: bullets with a
 * TextureComponent spawn TrailComponent afterimage ghosts when FeatureFlags.isSlashArcEnabled()
 * is true, ghosts carry no gameplay components, position matches the bullet (with left-facing
 * anchor shift), they fade over BULLET_TRAIL_LIFETIME and self-remove, and no ghosts spawn
 * when the slash-arc flag is off.
 */
public class PlayerBulletTrailSpawnTest extends SystemTestBase {

    private GL20 previousGl;
    private Graphics previousGraphics;
    private final Array<Rectangle> collisionRects = new Array<>();
    private PooledEngine engine;
    private PlayerBulletSystem bulletSystem;
    private TrailSystem trailSystem;
    private Texture texture;

    @Before
    public void setUp() {
        previousGl = Gdx.gl;
        previousGraphics = Gdx.graphics;
        Gdx.gl = mock(GL20.class);
        Gdx.graphics = mock(Graphics.class);

        FeatureFlags.setSlashArcEnabled(true);

        texture = new Texture(new Pixmap(32, 16, Pixmap.Format.RGBA8888));
        bulletSystem = new PlayerBulletSystem(collisionRects);
        bulletSystem.setUnitScale(1f);
        trailSystem = new TrailSystem();
        engine = new PooledEngine();
        engine.addSystem(bulletSystem);
        engine.addSystem(trailSystem);
    }

    @After
    public void tearDown() {
        FeatureFlags.setSlashArcEnabled(true);
        texture.dispose();
        Gdx.gl = previousGl;
        Gdx.graphics = previousGraphics;
    }

    private TextureComponent bulletTexture() {
        TextureComponent tc = new TextureComponent();
        tc.region = new TextureRegion(texture, 0, 0, 32, 16);
        return tc;
    }

    /**
     * Builds a bullet entity whose trailTimer starts at 0 so the first frame triggers a trail
     * spawn. The bullet has a long lifetime so it survives all test frames. TextureComponent is
     * required for TrailSystem.spawnTrail to produce a ghost.
     */
    private Entity bullet(float x, float y, float velocityX, float lifetime) {
        TransformComponent transform = transform(x, y);
        CollisionComponent collision = collision(0f, 0f, 10f, 10f);
        place(transform, collision, x, y);
        MovementComponent movement = movement();
        movement.velocity.x = velocityX;
        BulletComponent bulletComp = new BulletComponent();
        bulletComp.lifetime = lifetime;
        bulletComp.damage = 5f;
        bulletComp.trailTimer = 0f; // triggers trail spawn on first frame
        Entity entity = entity(transform, movement, collision, bulletComp, bulletTexture());
        engine.addEntity(entity);
        return entity;
    }

    private int countTrails() {
        int count = 0;
        for (Entity e : engine.getEntities()) {
            if (TRAIL.get(e) != null) count++;
        }
        return count;
    }

    private Entity findFirstTrail() {
        for (Entity e : engine.getEntities()) {
            if (TRAIL.get(e) != null) return e;
        }
        return null;
    }

    @Test
    public void bulletFlying_SpawnsTrailGhosts() {
        bullet(0f, 0f, 50f, 5f);

        // BULLET_TRAIL_INTERVAL = 0.04s; 3 frames of DT (~0.05s) ensures at least one spawn.
        for (int i = 0; i < 3; i++) {
            engine.update(DT);
        }

        assertTrue("at least one trail ghost should exist", countTrails() > 0);

        Entity ghost = findFirstTrail();
        assertNotNull("trail ghost entity found", ghost);
        assertNotNull("ghost has TrailComponent", TRAIL.get(ghost));
        assertNotNull("ghost has TransformComponent", TRANSFORM.get(ghost));
        assertNotNull("ghost has TextureComponent", TEXTURE.get(ghost));

        // Ghost must NOT carry gameplay components.
        assertNull("ghost has no BulletComponent", BULLET.get(ghost));
        assertNull("ghost has no MovementComponent", MOVEMENT.get(ghost));
        assertNull("ghost has no CollisionComponent", COLLISION.get(ghost));
        assertNull("ghost has no PlayerComponent", PLAYER.get(ghost));
    }

    @Test
    public void trailGhost_PositionMatchesBulletRightFacing() {
        Entity bulletEntity = bullet(200f, 100f, 50f, 5f);

        engine.update(DT);

        // trailTimer starts at 0; first frame spawns a ghost.
        Entity ghost = findFirstTrail();
        assertNotNull("ghost spawned on first frame", ghost);

        TransformComponent bulletT = TRANSFORM.get(bulletEntity);
        TransformComponent ghostT = TRANSFORM.get(ghost);
        // Right-facing bullet (scale.x = 1): ghost x == bullet x exactly.
        assertEquals(bulletT.position.x, ghostT.position.x, EPSILON);
        assertEquals(bulletT.position.y, ghostT.position.y, EPSILON);
    }

    @Test
    public void trailGhost_LeftFacing_HandlesAnchorShift() {
        Entity bulletEntity = bullet(200f, 100f, -50f, 5f);
        // Mirror PlayerInputSystem: negative scale.x = left-facing bullet.
        TRANSFORM.get(bulletEntity).scale.x = -1f;

        engine.update(DT);

        Entity ghost = findFirstTrail();
        assertNotNull("ghost spawned for left-facing bullet", ghost);

        TransformComponent bulletT = TRANSFORM.get(bulletEntity);
        TransformComponent ghostT = TRANSFORM.get(ghost);
        // spawnTrail shifts x by regionWidth * |scale.x| for left-facing bullets.
        float regionWidth = TEXTURE.get(ghost).region.getRegionWidth();
        float expectedX = bulletT.position.x - regionWidth * Math.abs(bulletT.scale.x);
        assertEquals(expectedX, ghostT.position.x, EPSILON);
        assertEquals(bulletT.position.y, ghostT.position.y, EPSILON);
    }

    @Test
    public void trailGhost_FadesAndRemoves() {
        bullet(0f, 0f, 50f, 5f);

        engine.update(DT);
        Entity ghost = findFirstTrail();
        assertNotNull("ghost spawned", ghost);
        // TrailSystem runs after PlayerBulletSystem in the same frame, so the ghost is
        // already aged by one DT and its alpha has begun fading from 1.
        float alphaFirstFrame = TRANSFORM.get(ghost).alpha;
        assertTrue("ghost already fading after first frame", alphaFirstFrame < 1f);
        assertTrue("alpha still positive", alphaFirstFrame > 0f);

        // Advance another frame: alpha must keep decreasing.
        engine.update(DT);
        float alphaSecondFrame = TRANSFORM.get(ghost).alpha;
        assertTrue("alpha decreases each frame", alphaSecondFrame < alphaFirstFrame);
        assertTrue("alpha still positive on second frame", alphaSecondFrame > 0f);

        // Run enough frames for the first ghost to exceed BULLET_TRAIL_LIFETIME (0.12s).
        // 0.12s / DT(0.01667) = ~8 frames; add a few extra for safety.
        for (int i = 0; i < 12; i++) {
            engine.update(DT);
        }

        // The bullet is still alive (lifetime 5s) and keeps spawning new ghosts, so we
        // assert the ORIGINAL ghost was removed by TrailSystem once age >= lifeTime.
        for (Entity e : engine.getEntities()) {
            assertNotSame("original ghost removed after lifetime", ghost, e);
        }
    }

    @Test
    public void slashArcFlagOff_NoGhosts() {
        FeatureFlags.setSlashArcEnabled(false);

        bullet(0f, 0f, 50f, 5f);

        for (int i = 0; i < 3; i++) {
            engine.update(DT);
        }

        assertEquals("no ghosts when slash-arc flag off", 0, countTrails());
    }
}
