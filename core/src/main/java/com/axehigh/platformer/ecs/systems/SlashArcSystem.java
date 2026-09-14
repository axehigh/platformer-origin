package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.ecs.components.SlashArcComponent;
import com.axehigh.platformer.ecs.components.TextureComponent;
import com.axehigh.platformer.ecs.components.TransformComponent;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.math.MathUtils;

import static com.axehigh.platformer.ecs.components.Mappers.SLASH_ARC;
import static com.axehigh.platformer.ecs.components.Mappers.TRANSFORM;

/**
 * Ages the one-shot melee slash-arc VFX: fades the crescent in over the first {@code fadeInRatio}
 * of its life, fades it back out over the remainder, and removes the entity once it outlives
 * {@code lifeTime}. Cosmetic only — no collision, no damage, no interaction.
 */
public class SlashArcSystem extends IteratingSystem {

    public SlashArcSystem() {
        this(0);
    }

    public SlashArcSystem(int priority) {
        super(Family.all(TransformComponent.class, TextureComponent.class, SlashArcComponent.class).get(), priority);
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        SlashArcComponent arc = SLASH_ARC.get(entity);
        TransformComponent transform = TRANSFORM.get(entity);

        arc.age += deltaTime;
        if (arc.age >= arc.lifeTime) {
            getEngine().removeEntity(entity);
            return;
        }

        float fadeInTime = arc.lifeTime * arc.fadeInRatio;
        float alpha;
        if (arc.age < fadeInTime) {
            alpha = arc.age / fadeInTime;
        } else {
            alpha = 1f - (arc.age - fadeInTime) / (arc.lifeTime - fadeInTime);
        }
        transform.alpha = MathUtils.clamp(alpha, 0f, 1f);
    }
}