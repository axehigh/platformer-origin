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

import static com.axehigh.platformer.ecs.components.Mappers.COLLISION;
import static com.axehigh.platformer.ecs.components.Mappers.TEXTURE;
import static com.axehigh.platformer.ecs.components.Mappers.TRANSFORM;

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

        float offsetX = 0f;
        float offsetY = 0f;
        if (region instanceof AtlasRegion) {
            AtlasRegion atlasRegion = (AtlasRegion) region;
            offsetX = atlasRegion.offsetX * transform.scale.x;
            offsetY = atlasRegion.offsetY * transform.scale.y;
        }

        CollisionComponent collision = COLLISION.get(entity);
        float directionalOffsetX = 0f;
        float directionalOffsetY = 0f;
        if (collision != null) {
            directionalOffsetX = (collision.bounds.x - collision.baseOffsetX);
            directionalOffsetY = (collision.bounds.y - collision.baseOffsetY);
        }

        float drawX = transform.position.x + offsetX + directionalOffsetX - Math.min(0f, width);
        float drawY = transform.position.y + offsetY + directionalOffsetY - Math.min(0f, height);
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
