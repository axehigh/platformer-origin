package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.ecs.components.PlayerComponent;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;

import static com.axehigh.platformer.ecs.components.Mappers.PLAYER;

/** Detects the player's health reaching 0 and fires a one-time callback into GameScreen. */
public class PlayerDeathSystem extends IteratingSystem {
    private final Runnable onDeath;
    private boolean triggered = false;

    public PlayerDeathSystem(Runnable onDeath, int priority) {
        super(Family.all(PlayerComponent.class).get(), priority);
        this.onDeath = onDeath;
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        if (triggered) {
            return;
        }
        PlayerComponent player = PLAYER.get(entity);
        if (player.health <= 0) {
            triggered = true;
            onDeath.run();
        }
    }
}
