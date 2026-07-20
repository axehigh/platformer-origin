package com.axehigh.platformer.ecs.components;

import com.badlogic.ashley.core.ComponentMapper;

/** Shared, reusable ComponentMapper instances for all ECS systems. */
public final class Mappers {
    public static final ComponentMapper<TransformComponent> TRANSFORM = ComponentMapper.getFor(TransformComponent.class);
    public static final ComponentMapper<TextureComponent> TEXTURE = ComponentMapper.getFor(TextureComponent.class);
    public static final ComponentMapper<AnimationComponent> ANIMATION = ComponentMapper.getFor(AnimationComponent.class);
    public static final ComponentMapper<MovementComponent> MOVEMENT = ComponentMapper.getFor(MovementComponent.class);
    public static final ComponentMapper<CollisionComponent> COLLISION = ComponentMapper.getFor(CollisionComponent.class);
    public static final ComponentMapper<PlayerComponent> PLAYER = ComponentMapper.getFor(PlayerComponent.class);
    public static final ComponentMapper<BulletComponent> BULLET = ComponentMapper.getFor(BulletComponent.class);
    public static final ComponentMapper<EnemyComponent> ENEMY = ComponentMapper.getFor(EnemyComponent.class);

    private Mappers() {
    }
}
