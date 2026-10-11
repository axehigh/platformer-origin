package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.PlayerConfig;
import com.axehigh.platformer.ecs.components.CollisionComponent;
import com.axehigh.platformer.ecs.components.TextureComponent;
import com.axehigh.platformer.ecs.components.TrailComponent;
import com.axehigh.platformer.ecs.components.TransformComponent;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion;
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
        float progress = trail.age / trail.lifeTime;
        transform.alpha = Math.max(0f, 0.75f * (float) Math.pow(1f - progress, 1.5));

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

    /**
     * Spawns a fading ghost afterimage of the player at its current position and animation frame.
     *
     * @param engine    the engine to add the ghost entity to
     * @param transform the player's transform component
     * @param texture   the player's texture component
     * @param collision the player's collision component
     * @return the created ghost entity
     */
    public static Entity spawnPlayerTrail(Engine engine, TransformComponent transform, TextureComponent texture, CollisionComponent collision) {
        return spawnPlayerTrail(engine, transform, texture, collision, true, false);
    }

    /**
     * Spawns a fading ghost afterimage of the player at its current position and animation frame.
     *
     * @param engine      the engine to add the ghost entity to
     * @param transform   the player's transform component
     * @param texture     the player's texture component
     * @param collision   the player's collision component
     * @param speedActive whether speed buff is active
     * @param jumpActive  whether jump buff is active
     * @return the created ghost entity
     */
    public static Entity spawnPlayerTrail(Engine engine, TransformComponent transform, TextureComponent texture, CollisionComponent collision, boolean speedActive, boolean jumpActive) {
        TextureRegion region = texture != null ? texture.region : null;
        if (region == null) {
            return null;
        }

        Entity ghost = new Entity();

        TransformComponent tc = new TransformComponent();
        float drawX = transform.position.x;
        float drawY = transform.position.y;

        if (collision != null && region instanceof AtlasRegion) {
            AtlasRegion atlasRegion = (AtlasRegion) region;
            float absScaleX = Math.abs(transform.scale.x);
            float absScaleY = Math.abs(transform.scale.y);

            float colCenterX = transform.position.x + collision.bounds.x + collision.bounds.width / 2f;
            float colCenterY = transform.position.y + collision.bounds.y + collision.bounds.height / 2f;

            float frameCenterX = (transform.scale.x >= 0) ? colCenterX - collision.currentOffsetX : colCenterX + collision.currentOffsetX;
            float frameCenterY = (transform.scale.y >= 0) ? colCenterY - collision.currentOffsetY : colCenterY + collision.currentOffsetY;

            float frameAnchorX = frameCenterX - (atlasRegion.originalWidth / 2f) * absScaleX;
            float frameAnchorY = frameCenterY - (atlasRegion.originalHeight / 2f) * absScaleY;

            if (transform.scale.x >= 0) {
                drawX = frameAnchorX + atlasRegion.offsetX * absScaleX;
            } else {
                drawX = frameAnchorX + (atlasRegion.originalWidth - atlasRegion.offsetX) * absScaleX;
            }

            if (transform.scale.y >= 0) {
                drawY = frameAnchorY + atlasRegion.offsetY * absScaleY;
            } else {
                drawY = frameAnchorY + (atlasRegion.originalHeight - atlasRegion.offsetY) * absScaleY;
            }
        }

        tc.position.x = drawX;
        tc.position.y = drawY;
        tc.scale.set(transform.scale);
        tc.z = transform.z - 0.5f;
        tc.alpha = 0.75f;
        ghost.add(tc);

        TextureComponent tex = new TextureComponent();
        tex.region = region;
        ghost.add(tex);

        TrailComponent trail = new TrailComponent();
        trail.lifeTime = PlayerConfig.PLAYER_TRAIL_LIFETIME;
        trail.age = 0f;

        if (speedActive && jumpActive) {
            trail.tint.set(0.1f, 0.85f, 1f, 1f);
        } else if (speedActive) {
            trail.tint.set(0f, 0.9f, 1f, 1f);
        } else if (jumpActive) {
            trail.tint.set(0.2f, 0.8f, 1f, 1f);
        } else {
            trail.tint.set(1f, 1f, 1f, 1f);
        }
        ghost.add(trail);

        engine.addEntity(ghost);
        return ghost;
    }
}
