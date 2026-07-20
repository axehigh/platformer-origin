package com.axehigh.platformer.map;

import com.axehigh.platformer.ecs.components.ChestComponent;
import com.axehigh.platformer.ecs.components.CoinPickupComponent;
import com.axehigh.platformer.ecs.components.CollisionComponent;
import com.axehigh.platformer.ecs.components.DaggerPickupComponent;
import com.axehigh.platformer.ecs.components.MovementComponent;
import com.axehigh.platformer.ecs.components.PlayerComponent;
import com.axehigh.platformer.ecs.components.TextureComponent;
import com.axehigh.platformer.ecs.components.TransformComponent;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.maps.MapObject;
import com.badlogic.gdx.maps.MapObjects;
import com.badlogic.gdx.maps.objects.RectangleMapObject;
import com.badlogic.gdx.math.Rectangle;

/** Builds Ashley entities for the player and for object-layer markers (coin, chest, torch, exit gate). */
public class EntityFactory {
    private static final float DECOR_Z = 5f;
    private static final float PLAYER_Z = 10f;

    private final AssetManager assetManager;

    public EntityFactory(AssetManager assetManager) {
        this.assetManager = assetManager;
    }

    public Entity createPlayer(float x, float y) {
        Texture texture = getTexture("gfx/player.png");

        Entity player = new Entity();

        TransformComponent transform = new TransformComponent();
        transform.position.set(x, y);
        transform.z = PLAYER_Z;
        player.add(transform);

        TextureComponent textureComponent = new TextureComponent();
        textureComponent.region = new TextureRegion(texture);
        player.add(textureComponent);

        MovementComponent movementComponent = new MovementComponent();
        player.add(movementComponent);

        CollisionComponent collisionComponent = new CollisionComponent();
        collisionComponent.bounds.setSize(texture.getWidth(), texture.getHeight());
        player.add(collisionComponent);

        player.add(new PlayerComponent());

        return player;
    }

    /** Spawns decorative entities (coin, chest, torch, exit gate) found in the object layer. */
    public void spawnObjects(Engine engine, MapObjects objects) {
        for (MapObject object : objects) {
            if (!(object instanceof RectangleMapObject)) {
                continue;
            }
            String type = object.getProperties().get("type", String.class);
            if (type == null) {
                continue;
            }

            Rectangle rect = ((RectangleMapObject) object).getRectangle();
            float centerX = rect.x + rect.width / 2f;
            float centerY = rect.y + rect.height / 2f;

            switch (type) {
                case "coin":
                    engine.addEntity(createCoinPickup(centerX, centerY));
                    break;
                case "chest":
                    engine.addEntity(createChest(centerX, centerY));
                    break;
                case "torch":
                    engine.addEntity(createDecoration(centerX, centerY, "gfx/torch.png"));
                    break;
                case "exitGate":
                    engine.addEntity(createDecoration(centerX, centerY, "gfx/exit_gate.png"));
                    break;
                case "dagger":
                    engine.addEntity(createDaggerPickup(centerX, centerY));
                    break;
                default:
                    // "playerStart" and any unrecognized type: nothing to spawn here.
                    break;
            }
        }
    }

    private Entity createDecoration(float x, float y, String texturePath) {
        Texture texture = getTexture(texturePath);

        Entity entity = new Entity();

        TransformComponent transform = new TransformComponent();
        transform.position.set(x, y);
        transform.z = DECOR_Z;
        entity.add(transform);

        TextureComponent textureComponent = new TextureComponent();
        textureComponent.region = new TextureRegion(texture);
        entity.add(textureComponent);

        return entity;
    }

    private Entity createChest(float x, float y) {
        Texture texture = getTexture("gfx/chest.png");

        Entity entity = new Entity();

        TransformComponent transform = new TransformComponent();
        transform.position.set(x, y);
        transform.z = DECOR_Z;
        entity.add(transform);

        TextureComponent textureComponent = new TextureComponent();
        textureComponent.region = new TextureRegion(texture);
        entity.add(textureComponent);

        CollisionComponent collisionComponent = new CollisionComponent();
        collisionComponent.bounds.setSize(texture.getWidth(), texture.getHeight());
        entity.add(collisionComponent);

        entity.add(new ChestComponent());

        return entity;
    }

    /** Builds a standalone coin pickup entity (used both for map object markers and chest drops). */
    public Entity createCoinPickup(float x, float y) {
        Texture texture = getTexture("gfx/coin.png");

        Entity entity = new Entity();

        TransformComponent transform = new TransformComponent();
        transform.position.set(x, y);
        transform.z = DECOR_Z;
        entity.add(transform);

        TextureComponent textureComponent = new TextureComponent();
        textureComponent.region = new TextureRegion(texture);
        entity.add(textureComponent);

        CollisionComponent collisionComponent = new CollisionComponent();
        collisionComponent.bounds.setSize(texture.getWidth(), texture.getHeight());
        entity.add(collisionComponent);

        entity.add(new CoinPickupComponent());

        return entity;
    }

    private Entity createDaggerPickup(float x, float y) {
        Texture texture = getTexture("gfx/dagger.png");

        Entity entity = new Entity();

        TransformComponent transform = new TransformComponent();
        transform.position.set(x, y);
        transform.z = DECOR_Z;
        entity.add(transform);

        TextureComponent textureComponent = new TextureComponent();
        textureComponent.region = new TextureRegion(texture);
        entity.add(textureComponent);

        CollisionComponent collisionComponent = new CollisionComponent();
        collisionComponent.bounds.setSize(texture.getWidth(), texture.getHeight());
        entity.add(collisionComponent);

        entity.add(new DaggerPickupComponent());

        return entity;
    }

    private Texture getTexture(String path) {
        Texture texture = assetManager.get(path, Texture.class);
        texture.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
        return texture;
    }
}
