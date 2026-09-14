package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.GameConstants;
import com.axehigh.platformer.ecs.components.LightComponent;
import com.axehigh.platformer.ecs.components.PlayerComponent;
import com.axehigh.platformer.ecs.components.TransformComponent;
import com.axehigh.platformer.util.FeatureFlags;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.PooledEngine;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static com.axehigh.platformer.ecs.components.Mappers.*;
import static org.junit.Assert.*;

/**
 * Integration tests supplementing the developer's AmbientEmberSystemTest: verifying that
 * non-player light sources spawn mote particles over time, the player entity (buff halo) is
 * excluded, a plain (non-Pooled) engine suppresses spawning, the embers feature flag gates
 * spawning, and spawned motes appear near the light source.
 */
public class AmbientEmberSystemIntegrationTest extends SystemTestBase {

    private AmbientEmberSystem system;
    private PooledEngine pooledEngine;

    @Before
    public void setUp() {
        FeatureFlags.setEmbersEnabled(true);
        system = new AmbientEmberSystem();
        pooledEngine = new PooledEngine();
        pooledEngine.addSystem(system);
    }

    @After
    public void tearDown() {
        FeatureFlags.setEmbersEnabled(true);
    }

    private Entity torch(float x, float y, float emberTimer) {
        LightComponent light = new LightComponent();
        light.emberTimer = emberTimer;
        TransformComponent t = transform(x, y);
        return entity(t, light);
    }

    private Entity playerLight(float x, float y) {
        LightComponent light = new LightComponent();
        light.emberTimer = 0f;
        TransformComponent t = transform(x, y);
        PlayerComponent player = new PlayerComponent();
        return entity(t, light, player);
    }

    private int countParticles() {
        int count = 0;
        for (Entity e : pooledEngine.getEntities()) {
            if (PARTICLE.get(e) != null) count++;
        }
        return count;
    }

    private int countParticles(Engine eng) {
        int count = 0;
        for (Entity e : eng.getEntities()) {
            if (PARTICLE.get(e) != null) count++;
        }
        return count;
    }

    @Test
    public void lights_SpawnMotesOverTime() {
        // emberTimer = 0 fires on the first frame; the mote is created immediately.
        Entity t = torch(100f, 200f, 0f);
        pooledEngine.addEntity(t);

        pooledEngine.update(DT);

        // At least one particle entity (the mote) plus the torch.
        assertTrue("mote spawned", countParticles() >= 1);

        // After > MAX_INTERVAL more time, a second mote may spawn (timer re-arms randomly).
        float maxSim = GameConstants.AMBIENT_MOTE_MAX_INTERVAL + 2 * DT;
        for (float sim = 0; sim < maxSim; sim += DT) {
            pooledEngine.update(DT);
        }
        assertTrue("additional motes spawned over time", countParticles() >= 2);
    }

    @Test
    public void playerBuffHalo_DoesNotSpawnMotes() {
        Entity playerEntity = playerLight(100f, 200f);
        pooledEngine.addEntity(playerEntity);

        // Run well past MAX_INTERVAL.
        float simTime = GameConstants.AMBIENT_MOTE_MAX_INTERVAL + 1f;
        for (float sim = 0; sim < simTime; sim += DT) {
            pooledEngine.update(DT);
        }

        assertEquals("player light spawns no motes", 0, countParticles());
    }

    @Test
    public void plainEngine_NoMotes() {
        Engine plainEngine = newEngine();
        plainEngine.addSystem(system);

        Entity t = torch(100f, 200f, 0f);
        plainEngine.addEntity(t);

        // Run past MAX_INTERVAL.
        float simTime = GameConstants.AMBIENT_MOTE_MAX_INTERVAL + 1f;
        for (float sim = 0; sim < simTime; sim += DT) {
            plainEngine.update(DT);
        }

        assertEquals("plain engine produces no motes (instanceof guard)",
            0, countParticles(plainEngine));
    }

    @Test
    public void embersFlagOff_NoMotes() {
        FeatureFlags.setEmbersEnabled(false);

        Entity t = torch(100f, 200f, 0f);
        pooledEngine.addEntity(t);

        // Run past MAX_INTERVAL.
        float simTime = GameConstants.AMBIENT_MOTE_MAX_INTERVAL + 1f;
        for (float sim = 0; sim < simTime; sim += DT) {
            pooledEngine.update(DT);
        }

        assertEquals("no motes when flag off", 0, countParticles());
    }

    @Test
    public void motePosition_NearLightCenter() {
        float lightX = 200f;
        float lightY = 150f;
        Entity t = torch(lightX, lightY, 0f);
        pooledEngine.addEntity(t);

        pooledEngine.update(DT);

        // Find the spawned mote entity.
        Entity mote = null;
        for (Entity e : pooledEngine.getEntities()) {
            if (PARTICLE.get(e) != null && TRANSFORM.get(e) != null) {
                mote = e;
                break;
            }
        }
        assertNotNull("mote entity found", mote);

        TransformComponent moteT = TRANSFORM.get(mote);
        LightComponent lightC = LIGHT.get(t);
        TransformComponent lightT = TRANSFORM.get(t);

        // Mote position = light.position + light.offset + random(-1,1)*radius*0.5 per axis.
        float expectedCx = lightT.position.x + lightC.offset.x;
        float expectedCy = lightT.position.y + lightC.offset.y;
        float spread = lightC.radius * 0.5f;

        assertTrue("mote x near light center (|"
            + moteT.position.x + " - " + expectedCx + "| <= " + spread + ")",
            Math.abs(moteT.position.x - expectedCx) <= spread + EPSILON);
        assertTrue("mote y near light center (|"
            + moteT.position.y + " - " + expectedCy + "| <= " + spread + ")",
            Math.abs(moteT.position.y - expectedCy) <= spread + EPSILON);
    }
}
