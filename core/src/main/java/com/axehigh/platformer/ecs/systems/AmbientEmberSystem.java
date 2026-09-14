package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.GameConstants;
import com.axehigh.platformer.ecs.components.LightComponent;
import com.axehigh.platformer.ecs.components.PlayerComponent;
import com.axehigh.platformer.ecs.components.TransformComponent;
import com.axehigh.platformer.particles.ParticleHelper;
import com.axehigh.platformer.util.FeatureFlags;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.core.PooledEngine;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.math.MathUtils;

import static com.axehigh.platformer.ecs.components.Mappers.LIGHT;
import static com.axehigh.platformer.ecs.components.Mappers.TRANSFORM;

/**
 * Spawns sparse warm ember/dust motes drifting near non-player light sources (torches,
 * map light effects). Pure decoration — motes are particle entities that self-remove
 * via {@code ParticleSystem}. Gated on {@code FeatureFlags.isEmbersEnabled()}.
 * Excludes the player entity (which may also carry a LightComponent for buff halos).
 */
public class AmbientEmberSystem extends IteratingSystem {

    public AmbientEmberSystem() {
        this(0);
    }

    public AmbientEmberSystem(int priority) {
        super(Family.all(TransformComponent.class, LightComponent.class)
            .exclude(PlayerComponent.class).get(), priority);
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        LightComponent light = LIGHT.get(entity);
        TransformComponent transform = TRANSFORM.get(entity);

        light.emberTimer -= deltaTime;
        if (light.emberTimer <= 0f) {
            light.emberTimer = MathUtils.random(GameConstants.AMBIENT_MOTE_MIN_INTERVAL, GameConstants.AMBIENT_MOTE_MAX_INTERVAL);

            float cx = transform.position.x + light.offset.x + MathUtils.random(-1f, 1f) * light.radius * 0.5f;
            float cy = transform.position.y + light.offset.y + MathUtils.random(-1f, 1f) * light.radius * 0.5f;

            if (FeatureFlags.isEmbersEnabled() && getEngine() instanceof PooledEngine) {
                ParticleHelper.spawnAmbientMote((PooledEngine) getEngine(), cx, cy);
            }
        }
    }
}
