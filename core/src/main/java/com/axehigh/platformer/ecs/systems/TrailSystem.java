package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.PlayerConfig;
import com.axehigh.platformer.ecs.components.TextureComponent;
import com.axehigh.platformer.ecs.components.TrailComponent;
import com.axehigh.platformer.ecs.components.TransformComponent;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

import static com.axehigh.platformer.ecs.components.Mappers.TRAIL;
import static com.axehigh.platformer.ecs.components.Mappers.TRANSFORM;

/**
 * Ages and fades short-lived afterimage "ghost" entities spawned along a bullet's path.
 * Each ghost carries only {@code TransformComponent + TextureComponent + TrailComponent}
 * (no BulletComponent/MovementComponent/CollisionComponent), so no other system moves or
 * collides it. Purely cosmetic — gated on {@code FeatureFlags.isSlashArcEnabled()} at the
 * spawn site ({@code PlayerBulletSystem} / {@code EnemyBulletCollisionSystem}).
 */
public class TrailSystem extends IteratingSystem {

    public TrailSystem() {
        this(0);
    }

    public TrailSystem(int priority) {
        super(Family.all(TransformComponent.class, TextureComponent.class, TrailComponent.class).get(), priority);
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        TrailComponent trail = TRAIL.get(entity);
        TransformComponent transform = TRANSFORM.get(entity);

        trail.age += deltaTime;
        transform.alpha = Math.max(0f, 1f - trail.age / trail.lifeTime);

        if (trail.age >= trail.lifeTime) {
            getEngine().removeEntity(entity);
        }
    }

    /**
     * Spawns a fading ghost afterimage of a bullet at its current position.
     *
     * @param engine          the engine to add the ghost entity to
     * @param bulletTransform the live bullet's transform (position, scale, z copied)
     * @param bulletTexture   the live bullet's texture region (reference copied)
     * @return the created ghost entity
     */
    public static Entity spawnTrail(Engine engine, TransformComponent bulletTransform, TextureComponent bulletTexture) {
        // Defensive: production bullets always carry a texture; headless tests may omit one.
        TextureRegion region = bulletTexture != null ? bulletTexture.region : null;
        if (region == null) {
            return null;
        }

        Entity ghost = new Entity();

        TransformComponent tc = new TransformComponent();
        // Anchor shift: for LEFT-facing bullets (negative scale.x) the RenderSystem fallback
        // branch shifts drawX by regionWidth*|scale.x|, so we must offset to match. The public
        // API getRegionWidth() reads regionWidth (no texture.getWidth() call in 1.14.2).
        tc.position.x = bulletTransform.position.x - (bulletTransform.scale.x < 0 ? region.getRegionWidth() * Math.abs(bulletTransform.scale.x) : 0f);
        tc.position.y = bulletTransform.position.y;
        tc.scale.set(bulletTransform.scale);
        tc.z = bulletTransform.z;
        tc.alpha = 1f;
        ghost.add(tc);

        TextureComponent tex = new TextureComponent();
        tex.region = region;
        ghost.add(tex);

        TrailComponent trail = new TrailComponent();
        trail.lifeTime = PlayerConfig.BULLET_TRAIL_LIFETIME;
        trail.age = 0f;
        ghost.add(trail);

        engine.addEntity(ghost);
        return ghost;
    }
}
