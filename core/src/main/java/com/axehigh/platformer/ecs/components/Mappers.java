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
    public static final ComponentMapper<EnemyBulletComponent> ENEMY_BULLET = ComponentMapper.getFor(EnemyBulletComponent.class);
    public static final ComponentMapper<EnemyComponent> ENEMY = ComponentMapper.getFor(EnemyComponent.class);
    public static final ComponentMapper<FlyingEnemyComponent> FLYING = ComponentMapper.getFor(FlyingEnemyComponent.class);
    public static final ComponentMapper<EnemyShooterComponent> ENEMY_SHOOTER = ComponentMapper.getFor(EnemyShooterComponent.class);
    public static final ComponentMapper<DaggerPickupComponent> DAGGER_PICKUP = ComponentMapper.getFor(DaggerPickupComponent.class);
    public static final ComponentMapper<CoinPickupComponent> COIN_PICKUP = ComponentMapper.getFor(CoinPickupComponent.class);
    public static final ComponentMapper<ChestComponent> CHEST = ComponentMapper.getFor(ChestComponent.class);
    public static final ComponentMapper<PoppedItemComponent> POPPED_ITEM = ComponentMapper.getFor(PoppedItemComponent.class);
    public static final ComponentMapper<LevelExitComponent> LEVEL_EXIT = ComponentMapper.getFor(LevelExitComponent.class);
    public static final ComponentMapper<ParticleComponent> PARTICLE = ComponentMapper.getFor(ParticleComponent.class);

    private Mappers() {
    }
}
