package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.ecs.components.CollisionComponent;
import com.axehigh.platformer.ecs.components.TextureComponent;
import com.axehigh.platformer.ecs.components.TransformComponent;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.SortedIteratingSystem;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

import java.util.Comparator;

import static com.axehigh.platformer.ecs.components.Mappers.*;

/** Draws every entity that has a TransformComponent + TextureComponent, sorted by z-index. */
public class RenderSystem extends SortedIteratingSystem {
    private final SpriteBatch batch;
    private final OrthographicCamera camera;

    public RenderSystem(SpriteBatch batch, OrthographicCamera camera) {
        this(batch, camera, 0);
    }

    public RenderSystem(SpriteBatch batch, OrthographicCamera camera, int priority) {
        super(Family.all(TransformComponent.class, TextureComponent.class).get(), new ZComparator(), priority);
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
        TransformComponent transform = TRANSFORM.get(entity);
        TextureRegion region = TEXTURE.get(entity).region;
        if (region == null) {
            return;
        }

        float width = region.getRegionWidth() * transform.scale.x;
        float height = region.getRegionHeight() * transform.scale.y;

        float drawX = transform.position.x;
        float drawY = transform.position.y;

        CollisionComponent collision = COLLISION.get(entity);
        if (collision != null && region instanceof AtlasRegion) {
            AtlasRegion atlasRegion = (AtlasRegion) region;
            float absScaleX = Math.abs(transform.scale.x);
            float absScaleY = Math.abs(transform.scale.y);

            // Anchor the sprite based on the collision box's actual center.
            // This ensures that the character remains centered on the collision box even if
            // it's off-center in the atlas frame and the box shifts directionally.
            float colCenterX = transform.position.x + collision.bounds.x + collision.bounds.width / 2f;
            float colCenterY = transform.position.y + collision.bounds.y + collision.bounds.height / 2f;

            // To keep the character centered on the collision box even when flipping off-center frames,
            // we must adjust the frame's anchor based on the current directional offset.
            float frameCenterX = (transform.scale.x >= 0) ? colCenterX - collision.currentOffsetX : colCenterX + collision.currentOffsetX;
            float frameCenterY = (transform.scale.y >= 0) ? colCenterY - collision.currentOffsetY : colCenterY + collision.currentOffsetY;

            float frameAnchorX = frameCenterX - (atlasRegion.originalWidth / 2f) * absScaleX;
            float frameAnchorY = frameCenterY - (atlasRegion.originalHeight / 2f) * absScaleY;

            if (transform.scale.x >= 0) {
                drawX = frameAnchorX + atlasRegion.offsetX * absScaleX;
            } else {
                // When flipped, drawX is the right edge of the flipped packed region.
                drawX = frameAnchorX + (atlasRegion.originalWidth - atlasRegion.offsetX) * absScaleX;
            }

            if (transform.scale.y >= 0) {
                drawY = frameAnchorY + atlasRegion.offsetY * absScaleY;
            } else {
                drawY = frameAnchorY + (atlasRegion.originalHeight - atlasRegion.offsetY) * absScaleY;
            }
        } else {
            // Fallback for non-atlas or non-collision entities
            drawX -= Math.min(0f, width);
            drawY -= Math.min(0f, height);
        }

        batch.draw(region,
            drawX, drawY,
            width / 2f, height / 2f,
            width, height,
            1f, 1f,
            transform.rotation);
    }

    private static class ZComparator implements Comparator<Entity> {
        @Override
        public int compare(Entity a, Entity b) {
            return Float.compare(TRANSFORM.get(a).z, TRANSFORM.get(b).z);
        }
    }
}
