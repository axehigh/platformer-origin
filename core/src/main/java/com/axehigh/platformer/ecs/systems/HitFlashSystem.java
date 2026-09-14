package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.ecs.components.HitFlashComponent;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;

import static com.axehigh.platformer.ecs.components.Mappers.HIT_FLASH;

/**
 * Counts down the white hit-flash timer on every {@link HitFlashComponent}. No entity removal —
 * the component persists inactive at 0. The actual tint is applied by {@link RenderSystem}.
 * Gated at the call sites that invoke {@code HitFlashComponent.start(...)} (enemy/player damage
 * resolvers), not here.
 */
public class HitFlashSystem extends IteratingSystem {

    public HitFlashSystem() {
        this(0);
    }

    public HitFlashSystem(int priority) {
        super(Family.all(HitFlashComponent.class).get(), priority);
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        HitFlashComponent flash = HIT_FLASH.get(entity);
        flash.flashTimer = Math.max(0f, flash.flashTimer - deltaTime);
    }
}
