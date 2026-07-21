package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.ecs.components.EnemyComponent;
import com.axehigh.platformer.ecs.components.MovementComponent;
import com.axehigh.platformer.ecs.components.TransformComponent;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;

import static com.axehigh.platformer.ecs.components.Mappers.ENEMY;
import static com.axehigh.platformer.ecs.components.Mappers.MOVEMENT;
import static com.axehigh.platformer.ecs.components.Mappers.TRANSFORM;

/**
 * Drives simple back-and-forth enemy patrol movement: sets horizontal velocity from
 * {@code EnemyComponent.direction}/{@code speed}, and flips direction once the enemy strays
 * {@code patrolRange} away from its spawn X ({@code originX}) or gets blocked by a wall (detected
 * via the zeroed horizontal velocity {@code MovementSystem} leaves behind after a collision).
 * Runs before {@code MovementSystem} so the velocity it sets is integrated the same frame.
 * Gravity and wall collision for enemies are handled for free by {@code MovementSystem}, since
 * any entity with Transform+Movement+Collision (and no BulletComponent) already matches its family.
 */
public class EnemySystem extends IteratingSystem {
    public EnemySystem() {
        this(0);
    }

    public EnemySystem(int priority) {
        super(Family.all(EnemyComponent.class, MovementComponent.class, TransformComponent.class).get(), priority);
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        EnemyComponent enemy = ENEMY.get(entity);
        MovementComponent movement = MOVEMENT.get(entity);
        TransformComponent transform = TRANSFORM.get(entity);

        boolean blockedByWall = movement.grounded && movement.velocity.x == 0f;
        if (blockedByWall) {
            enemy.direction = -enemy.direction;
        } else if (transform.position.x <= enemy.originX - enemy.patrolRange) {
            enemy.direction = 1;
        } else if (transform.position.x >= enemy.originX + enemy.patrolRange) {
            enemy.direction = -1;
        }

        movement.velocity.x = enemy.speed * enemy.direction;
    }
}
