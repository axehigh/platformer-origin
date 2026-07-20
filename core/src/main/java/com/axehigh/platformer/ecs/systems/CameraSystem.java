package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.GameConstants;
import com.axehigh.platformer.ecs.components.PlayerComponent;
import com.axehigh.platformer.ecs.components.TransformComponent;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.graphics.OrthographicCamera;

import static com.axehigh.platformer.ecs.components.Mappers.TRANSFORM;

/**
 * Flip-screen (room-based) camera per AGENTS.md section E: computes the room the player is
 * currently in and snaps the camera to that room's center, with no per-frame smooth tracking.
 */
public class CameraSystem extends EntitySystem {
    private final OrthographicCamera camera;
    private ImmutableArray<Entity> players;
    private int roomX = Integer.MIN_VALUE;
    private int roomY = Integer.MIN_VALUE;

    public CameraSystem(OrthographicCamera camera) {
        this(camera, 0);
    }

    public CameraSystem(OrthographicCamera camera, int priority) {
        super(priority);
        this.camera = camera;
    }

    @Override
    public void addedToEngine(Engine engine) {
        players = engine.getEntitiesFor(Family.all(PlayerComponent.class, TransformComponent.class).get());
    }

    @Override
    public void update(float deltaTime) {
        if (players.size() == 0) {
            return;
        }

        TransformComponent transform = TRANSFORM.get(players.first());
        roomX = (int) (transform.position.x / GameConstants.VIRTUAL_WIDTH);
        roomY = (int) (transform.position.y / GameConstants.VIRTUAL_HEIGHT);

        camera.position.set(
            (roomX * GameConstants.VIRTUAL_WIDTH) + (GameConstants.VIRTUAL_WIDTH / 2f),
            (roomY * GameConstants.VIRTUAL_HEIGHT) + (GameConstants.VIRTUAL_HEIGHT / 2f),
            0f);
        camera.update();
    }
}
