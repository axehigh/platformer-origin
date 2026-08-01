package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.ecs.components.ParticleComponent;
import com.axehigh.platformer.ecs.components.TransformComponent;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

import static com.axehigh.platformer.ecs.components.Mappers.PARTICLE;
import static com.axehigh.platformer.ecs.components.Mappers.TRANSFORM;

/** Advances and draws every live ParticleEffect, removing its entity once the effect completes. */
public class ParticleSystem extends IteratingSystem {
    private final SpriteBatch batch;
    private final OrthographicCamera camera;

    public ParticleSystem(SpriteBatch batch, OrthographicCamera camera, int priority) {
        super(Family.all(ParticleComponent.class, TransformComponent.class).get(), priority);
        this.batch = batch;
        this.camera = camera;
    }

    @Override
    public void update(float deltaTime) {
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        super.update(deltaTime);
        batch.end();
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        ParticleComponent particle = PARTICLE.get(entity);
        if (particle.isDead) {
            return;
        }

        if (!particle.started) {
            if (particle.delay > 0f) {
                particle.delay -= deltaTime;
                if (particle.delay > 0f) {
                    return;
                }
            }
            particle.started = true;
            particle.effect.start();
        }

        TransformComponent transform = TRANSFORM.get(entity);
        particle.effect.setPosition(transform.position.x, transform.position.y);
        particle.effect.update(deltaTime);
        particle.effect.draw(batch);

        if (particle.effect.isComplete()) {
            particle.isDead = true;
            getEngine().removeEntity(entity);
        }
    }
}
