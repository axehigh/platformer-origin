package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.ecs.components.LightComponent;
import com.axehigh.platformer.ecs.components.PlayerComponent;
import com.axehigh.platformer.ecs.components.TransformComponent;
import com.axehigh.platformer.util.FeatureFlags;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.PooledEngine;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Headless tests for {@code AmbientEmberSystem}: non-player light sources spawn ambient mote
 * particles (dummy entities in headless mode) on their ember timer, the player entity (buff halo
 * LightComponent) is excluded, and the {@code FeatureFlags.isEmbersEnabled()} gate suppresses
 * spawning entirely.
 */
public class AmbientEmberSystemTest extends SystemTestBase {

    private AmbientEmberSystem system;
    private PooledEngine engine;

    @Before
    public void setUp() {
        FeatureFlags.setEmbersEnabled(true);
        system = new AmbientEmberSystem();
        engine = new PooledEngine();
        engine.addSystem(system);
    }

    @After
    public void tearDown() {
        FeatureFlags.setEmbersEnabled(true);
    }

    private Entity light(float x, float y, float emberTimer) {
        LightComponent light = new LightComponent();
        light.emberTimer = emberTimer;
        TransformComponent transform = transform(x, y);
        return entity(transform, light);
    }

    @Test
    public void spawnsMoteOnTimerFireAndReArmsInterval() {
        Entity torch = light(100f, 200f, 0f);
        engine.addEntity(torch);

        engine.update(DT);

        // Headless ParticleHelper adds a dummy mote entity alongside the light.
        assertEquals(2, engine.getEntities().size());
        LightComponent light = com.axehigh.platformer.ecs.components.Mappers.LIGHT.get(torch);
        assertTrue("timer should re-arm to a positive random interval", light.emberTimer > 0f);
    }

    @Test
    public void playerBuffHaloIsExcluded() {
        Entity player = entity(transform(100f, 200f), new LightComponent(), new PlayerComponent());
        engine.addEntity(player);

        engine.update(DT);

        assertEquals(1, engine.getEntities().size());
    }

    @Test
    public void disabledFlagSpawnsNothing() {
        FeatureFlags.setEmbersEnabled(false);
        Entity torch = light(100f, 200f, 0f);
        engine.addEntity(torch);

        engine.update(DT);

        assertEquals(1, engine.getEntities().size());
    }
}