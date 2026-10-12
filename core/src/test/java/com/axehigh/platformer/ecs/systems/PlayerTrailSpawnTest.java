package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.PlayerConfig;
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

import static com.axehigh.platformer.ecs.components.Mappers.PLAYER;
import static com.axehigh.platformer.ecs.components.Mappers.TRAIL;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class PlayerTrailSpawnTest extends SystemTestBase {

    private GL20 previousGl;
    private Graphics previousGraphics;
    private Engine engine;
    private com.axehigh.platformer.ecs.systems.BuffSystem buffSystem;
    private com.axehigh.platformer.ecs.systems.TrailSystem trailSystem;
    private Texture texture;

    @Before
    public void setUp() {
        previousGl = Gdx.gl;
        previousGraphics = Gdx.graphics;
        Gdx.gl = mock(GL20.class);
        Gdx.graphics = mock(Graphics.class);
        texture = new Texture(new Pixmap(32, 32, Pixmap.Format.RGBA8888));
        buffSystem = new com.axehigh.platformer.ecs.systems.BuffSystem(0);
        trailSystem = new com.axehigh.platformer.ecs.systems.TrailSystem(1);
        engine = newEngine();
        engine.addSystem(buffSystem);
        engine.addSystem(trailSystem);
    }

    @After
    public void tearDown() {
        texture.dispose();
        Gdx.gl = previousGl;
        Gdx.graphics = previousGraphics;
    }

    private com.axehigh.platformer.ecs.components.TextureComponent textureComponent() {
        com.axehigh.platformer.ecs.components.TextureComponent tc = new com.axehigh.platformer.ecs.components.TextureComponent();
        tc.region = new TextureRegion(texture, 0, 0, 32, 32);
        return tc;
    }

    private Entity playerWithBuffs(boolean speedActive, boolean jumpActive, float velocityX, boolean grounded) {
        com.axehigh.platformer.ecs.components.TransformComponent transform = transform(100f, 100f);
        com.axehigh.platformer.ecs.components.MovementComponent movement = movement();
        movement.velocity.x = velocityX;
        movement.grounded = grounded;
        com.axehigh.platformer.ecs.components.PlayerComponent player = player();
        com.axehigh.platformer.ecs.components.BuffComponent buff = new com.axehigh.platformer.ecs.components.BuffComponent();
        if (speedActive) {
            buff.startSpeed();
        }
        if (jumpActive) {
            buff.startJump();
        }
        Entity entity = entity(transform, movement, player, buff, textureComponent(), collision(0f, 0f, 32f, 32f));
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

    @Test
    public void speedBuffMoving_SpawnsPlayerTrailsWithConfiguredLifetime() {
        playerWithBuffs(true, false, 100f, true);

        assertEquals(0, countTrails());
        engine.update(PlayerConfig.PLAYER_TRAIL_INTERVAL + 0.01f);
        assertTrue("should spawn player trail ghost when speed buff active and moving", countTrails() > 0);

        for (Entity e : engine.getEntities()) {
            if (TRAIL.get(e) != null) {
                assertEquals(PlayerConfig.PLAYER_TRAIL_LIFETIME, TRAIL.get(e).lifeTime, EPSILON);
            }
        }
    }

    @Test
    public void playerTrailGhost_LingersLongerThanOldLifetime() {
        playerWithBuffs(true, false, 100f, true);

        // Spawn first trail ghost
        engine.update(PlayerConfig.PLAYER_TRAIL_INTERVAL + 0.001f);
        int initialTrails = countTrails();
        assertTrue(initialTrails > 0);

        // Advance 0.20s (which is longer than the old 0.15s lifetime, but less than 0.30s)
        engine.update(0.20f);
        assertTrue("trail ghosts should still linger after 0.20s", countTrails() > 0);
    }

    @Test
    public void jumpBuffFirstJumpInAir_SpawnsNoTrails() {
        Entity entity = playerWithBuffs(false, true, 0f, false);
        PLAYER.get(entity).jumpCount = 1;

        engine.update(PlayerConfig.PLAYER_TRAIL_INTERVAL + 0.01f);
        assertEquals(0, countTrails());
    }

    @Test
    public void jumpBuffSecondJumpInAir_SpawnsNoTrails() {
        Entity entity = playerWithBuffs(false, true, 0f, false);
        PLAYER.get(entity).jumpCount = 2;

        engine.update(PlayerConfig.PLAYER_TRAIL_INTERVAL + 0.01f);
        assertEquals(0, countTrails());
    }

    @Test
    public void jumpBuffThirdJumpInAir_SpawnsPlayerTrails() {
        Entity entity = playerWithBuffs(false, true, 0f, false);
        PLAYER.get(entity).jumpCount = 3;

        assertEquals(0, countTrails());
        engine.update(PlayerConfig.PLAYER_TRAIL_INTERVAL + 0.01f);
        assertTrue("should spawn player trail ghost on third jump when jump buff active", countTrails() > 0);
    }

    @Test
    public void noBuffStationary_SpawnsNoTrails() {
        playerWithBuffs(false, false, 0f, true);

        engine.update(0.2f);
        assertEquals(0, countTrails());
    }

    @Test
    public void buffStationaryGrounded_SpawnsNoTrails() {
        playerWithBuffs(true, true, 0f, true);

        engine.update(0.2f);
        assertEquals(0, countTrails());
    }
}
