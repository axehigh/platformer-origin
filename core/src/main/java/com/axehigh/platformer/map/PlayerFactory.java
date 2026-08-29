package com.axehigh.platformer.map;

import com.axehigh.platformer.GameConstants;
import com.axehigh.platformer.assets.SpriteConstants;
import com.axehigh.platformer.ecs.components.*;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

import static com.axehigh.platformer.assets.GameAssetRegistry.HERO_ASSET;
import static com.axehigh.platformer.ecs.components.AnimationComponent.State.*;
import static com.badlogic.gdx.graphics.g2d.Animation.PlayMode.LOOP;
import static com.badlogic.gdx.graphics.g2d.Animation.PlayMode.NORMAL;

/**
 * Builds the player entity: transform, texture, movement, collision box (offsets derived from the
 * canonical idle frame), buffs, and the full hero animation set.
 */
class PlayerFactory {
    private final FactoryContext context;

    PlayerFactory(FactoryContext context) {
        this.context = context;
    }

    public Entity createPlayer(float x, float y) {
        TextureAtlas heroAtlas = context.assetManager.get(HERO_ASSET, TextureAtlas.class);
        TextureRegion region = heroAtlas.findRegion("idle");

        float scaleFactor = SpriteConstants.PlayerScale; // Scaling factor for the new larger graphics
        float finalScale = context.unitScale * scaleFactor;

        Entity player = new Entity();

        TransformComponent transform = new TransformComponent();
        transform.position.set(x, y);
        transform.scale.set(finalScale, finalScale);
        transform.z = FactoryContext.PLAYER_Z;
        player.add(transform);

        TextureComponent textureComponent = new TextureComponent();
        textureComponent.region = region;
        player.add(textureComponent);

        MovementComponent movementComponent = new MovementComponent();
        movementComponent.maxSpeedX *= context.unitScale;
        movementComponent.maxSpeedY *= context.unitScale;
        player.add(movementComponent);

        CollisionComponent collisionComponent = new CollisionComponent();
        float collisionWidth = SpriteConstants.PlayerCollisionWidth;
        float collisionHeight = SpriteConstants.PlayerCollisionHeight;
        collisionComponent.bounds.setSize(collisionWidth * finalScale, collisionHeight * finalScale);

        if (region instanceof AtlasRegion) {
            AtlasRegion atlasRegion = (AtlasRegion) region;
            collisionComponent.baseOffsetX = (atlasRegion.offsetX + (atlasRegion.getRegionWidth() - collisionWidth) / 2f) * finalScale;
            collisionComponent.baseOffsetY = (atlasRegion.offsetY + (atlasRegion.getRegionHeight() - collisionHeight) / 2f) * finalScale;
        } else {
            collisionComponent.baseOffsetX = (region.getRegionWidth() - collisionWidth) / 2f * finalScale;
            collisionComponent.baseOffsetY = (region.getRegionHeight() - collisionHeight) / 2f * finalScale;
        }
        collisionComponent.currentOffsetX = SpriteConstants.PlayerOffsetRight * finalScale;
        collisionComponent.currentOffsetY = SpriteConstants.PlayerOffsetY * finalScale;
        collisionComponent.bounds.setX(collisionComponent.baseOffsetX + collisionComponent.currentOffsetX);
        collisionComponent.bounds.setY(collisionComponent.baseOffsetY + collisionComponent.currentOffsetY);
        player.add(collisionComponent);

        PlayerComponent playerComponent = new PlayerComponent();
        playerComponent.ammo = GameConstants.PlayerStartBullet;
        player.add(playerComponent);
        player.add(new BuffComponent());
        attachPlayerAnimations(player, heroAtlas);

        return player;
    }

    /**
     * Recomputes a (persisted) player's collision offsets from the canonical idle frame, so a
     * level swap never inherits offsets computed from whatever animation frame the player happened
     * to be showing at that moment (e.g. the death frame, whose region is much larger), which would
     * float the collision box far above the sprite. Mirrors the math in {@link #createPlayer(float, float)}.
     */
    public void resetPlayerCollision(CollisionComponent collisionComponent, float finalScale) {
        TextureAtlas heroAtlas = context.assetManager.get(HERO_ASSET, TextureAtlas.class);
        TextureRegion region = heroAtlas.findRegion("idle");
        float collisionWidth = SpriteConstants.PlayerCollisionWidth;
        float collisionHeight = SpriteConstants.PlayerCollisionHeight;
        collisionComponent.bounds.setSize(collisionWidth * finalScale, collisionHeight * finalScale);
        if (region instanceof AtlasRegion) {
            AtlasRegion atlasRegion = (AtlasRegion) region;
            collisionComponent.baseOffsetX = (atlasRegion.offsetX + (atlasRegion.getRegionWidth() - collisionWidth) / 2f) * finalScale;
            collisionComponent.baseOffsetY = (atlasRegion.offsetY + (atlasRegion.getRegionHeight() - collisionHeight) / 2f) * finalScale;
        } else {
            collisionComponent.baseOffsetX = (region.getRegionWidth() - collisionWidth) / 2f * finalScale;
            collisionComponent.baseOffsetY = (region.getRegionHeight() - collisionHeight) / 2f * finalScale;
        }
        collisionComponent.currentOffsetX = SpriteConstants.PlayerOffsetRight * finalScale;
        collisionComponent.currentOffsetY = SpriteConstants.PlayerOffsetY * finalScale;
        collisionComponent.bounds.setX(collisionComponent.baseOffsetX + collisionComponent.currentOffsetX);
        collisionComponent.bounds.setY(collisionComponent.baseOffsetY + collisionComponent.currentOffsetY);
    }

    /**
     * Attaches the hero's full animation set. The {@code ATTACKING} clip is authored ~1.5x faster
     * than the other action clips (0.066s/frame vs 0.1s) so a swing reads snappy.
     */
    private static void attachPlayerAnimations(Entity player, TextureAtlas heroAtlas) {
        AnimationComponent animationComponent = new AnimationComponent();
        animationComponent.animations.put(IDLE, new Animation<>(0.15f, heroAtlas.findRegions("idle"), LOOP));
        animationComponent.animations.put(WALKING, new Animation<>(0.1f, heroAtlas.findRegions("walk"), LOOP));
        animationComponent.animations.put(RUNNING, new Animation<>(0.1f, heroAtlas.findRegions("run"), LOOP));
        animationComponent.animations.put(JUMPING, new Animation<>(0.1f, heroAtlas.findRegions("jump"), NORMAL));
        animationComponent.animations.put(DOUBLE_JUMPING, new Animation<>(0.1f, heroAtlas.findRegions("high_jump"), NORMAL));
        animationComponent.animations.put(WALL_CLIMBING, new Animation<>(0.1f, heroAtlas.findRegions("climb"), LOOP));
        animationComponent.animations.put(ATTACKING, new Animation<>(0.066f, heroAtlas.findRegions("attack"), NORMAL));
        animationComponent.animations.put(DEATH, new Animation<>(0.1f, heroAtlas.findRegions("death"), NORMAL));
        animationComponent.animations.put(HURT, new Animation<>(0.1f, heroAtlas.findRegions("hurt"), NORMAL));

        player.add(animationComponent);
    }
}
