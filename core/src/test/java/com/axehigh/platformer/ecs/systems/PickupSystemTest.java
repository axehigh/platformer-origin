package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.GameConstants;
import com.axehigh.platformer.ecs.components.*;
import com.axehigh.platformer.map.EntityFactory;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import org.junit.Before;
import org.junit.Test;

import static com.axehigh.platformer.ecs.components.Mappers.PLAYER;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

/**
 * Headless unit tests for {@code PickupSystem}: coin/dagger/potion overlap resolution, the ammo cap
 * at {@code maxAmmo}, the potion cap (over-cap converts to coins), that a non-overlapping pickup is
 * left untouched, and the SFX contract.
 *
 * <p><b>SFX contract (placeholder wiring):</b> the coin chirp fires exactly when the batched "+N"
 * floating message spawns — the first coin in a burst (fresh cooldown) spawns immediately and plays
 * immediately; further coins picked up inside the {@code COIN_MESSAGE_COOLDOWN} window coalesce into
 * the next flush, which plays a single chirp. Daggers play {@code playAmmoPickup()}, under-cap
 * potions {@code playPotionPickup()}, and over-cap potions {@code playCoin()}. Without an
 * {@code EntityFactory} no floating message — and no coin chirp — ever spawns.
 */
public class PickupSystemTest extends SystemTestBase {

    private Engine engine;
    private PickupSystem system;
    private SfxSystem sfxSystem;
    private EntityFactory entityFactory;

    @Before
    public void setUp() {
        sfxSystem = mock(SfxSystem.class);
        entityFactory = mock(EntityFactory.class);
        system = new PickupSystem(sfxSystem, entityFactory, 0);
        engine = newEngine();
        engine.addSystem(system);
    }

    private Entity player(float x, float y) {
        TransformComponent transform = transform(x, y);
        CollisionComponent collision = collision(-15f, -30f, 30f, 60f);
        place(transform, collision, x, y);
        Entity entity = entity(transform, player(), collision);
        engine.addEntity(entity);
        return entity;
    }

    private Entity coin(float x, float y, int amount) {
        TransformComponent transform = transform(x, y);
        CollisionComponent collision = collision(-5f, -5f, 10f, 10f);
        place(transform, collision, x, y);
        CoinPickupComponent coin = new CoinPickupComponent();
        coin.amount = amount;
        Entity entity = entity(transform, collision, coin);
        engine.addEntity(entity);
        return entity;
    }

    private Entity dagger(float x, float y, int amount) {
        TransformComponent transform = transform(x, y);
        CollisionComponent collision = collision(-5f, -5f, 10f, 10f);
        place(transform, collision, x, y);
        DaggerPickupComponent dagger = new DaggerPickupComponent();
        dagger.amount = amount;
        Entity entity = entity(transform, collision, dagger);
        engine.addEntity(entity);
        return entity;
    }

    private Entity potion(float x, float y, PotionType type) {
        TransformComponent transform = transform(x, y);
        CollisionComponent collision = collision(-5f, -5f, 10f, 10f);
        place(transform, collision, x, y);
        PotionPickupComponent potion = new PotionPickupComponent();
        potion.type = type;
        potion.amount = 1;
        Entity entity = entity(transform, collision, potion);
        engine.addEntity(entity);
        return entity;
    }

    @Test
    public void coinPickupPlaysCoinSfxExactlyOnce_whenCooldownFresh() {
        player(0f, 130f);
        coin(0f, 130f, 1);

        engine.update(DT);

        // Fresh cooldown: the first coin spawns its message (and chirp) immediately — exactly once.
        verify(sfxSystem, times(1)).playCoin();
    }

    @Test
    public void coinsWithinCooldownWindowCoalesceIntoSingleFlushChirp() {
        Entity player = player(0f, 130f);
        PlayerComponent playerComponent = PLAYER.get(player);

        // Frame 0: first coin, cooldown fresh -> immediate message + chirp #1, cooldown armed.
        coin(0f, 130f, 1);
        engine.update(DT);
        assertEquals(1, playerComponent.coins);
        verify(sfxSystem, times(1)).playCoin();

        // Frame 1: two more coins land inside the 0.3s window -> both coalesce quietly.
        coin(0f, 130f, 1);
        coin(0f, 130f, 1);
        engine.update(DT);
        assertEquals(3, playerComponent.coins);
        assertEquals(2, playerComponent.pendingCoinMessage);
        verify(sfxSystem, times(1)).playCoin();

        // Past the cooldown: the "+2" batch flushes -> exactly ONE more chirp for the whole burst.
        for (int i = 0; i < 25; i++) {
            engine.update(DT);
        }
        assertEquals(0, playerComponent.pendingCoinMessage);
        verify(sfxSystem, times(2)).playCoin();
    }

    @Test
    public void coinPickupWithoutEntityFactoryPlaysNoCoinSfx() {
        // No-factory path: spawnCoinMessage early-returns, so no message and no chirp.
        engine.removeSystem(system);
        engine.addSystem(new PickupSystem(sfxSystem, null, 0));
        Entity player = player(0f, 130f);
        coin(0f, 130f, 1);

        engine.update(DT);

        assertEquals(1, PLAYER.get(player).coins);
        verify(sfxSystem, never()).playCoin();
    }

    @Test
    public void daggerPickupPlaysAmmoPickupSfxNotCoin() {
        player(0f, 130f);
        dagger(0f, 130f, 1);

        engine.update(DT);

        verify(sfxSystem).playAmmoPickup();
        verify(sfxSystem, never()).playCoin();
    }

    @Test
    public void underCapPotionPickupPlaysPotionPickupSfx() {
        Entity player = player(0f, 130f);
        potion(0f, 130f, PotionType.HEALING);

        engine.update(DT);

        assertEquals(1, PLAYER.get(player).countPotion(PotionType.HEALING));
        verify(sfxSystem).playPotionPickup();
        verify(sfxSystem, never()).playCoin();
    }

    @Test
    public void overCapPotionPickupConvertsToCoinsAndPlaysCoinSfx() {
        Entity player = player(0f, 130f);
        PLAYER.get(player).setPotionCount(PotionType.HEALING, GameConstants.POTION_CAP);
        potion(0f, 130f, PotionType.HEALING);

        engine.update(DT);

        assertEquals(GameConstants.POTION_OVERFLOW_COINS, PLAYER.get(player).coins);
        assertEquals(GameConstants.POTION_CAP, PLAYER.get(player).countPotion(PotionType.HEALING));
        verify(sfxSystem).playCoin();
        verify(sfxSystem, never()).playPotionPickup();
    }

    @Test
    public void coinPickupIncrementsCoinsAndIsRemoved() {
        Entity player = player(0f, 130f);
        coin(0f, 130f, 3);
        PlayerComponent playerComponent = PLAYER.get(player);

        engine.update(DT);

        assertEquals(3, playerComponent.coins);
        assertEquals(1, engine.getEntities().size());
    }

    @Test
    public void daggerPickupIncrementsItemsCappedAtMax() {
        Entity player = player(0f, 130f);
        PlayerComponent playerComponent = PLAYER.get(player);
        playerComponent.ammo = 9;
        dagger(0f, 130f, 5);

        engine.update(DT);

        assertEquals(playerComponent.maxAmmo, playerComponent.ammo);
        assertEquals(1, engine.getEntities().size());
    }

    @Test
    public void daggerPickupAddsFullAmountWhenBelowCap() {
        Entity player = player(0f, 130f);
        dagger(0f, 130f, 5);

        engine.update(DT);

        assertEquals(5, PLAYER.get(player).ammo);
    }

    @Test
    public void nonOverlappingPickupIsLeftUntouched() {
        Entity player = player(0f, 130f);
        coin(500f, 500f, 1);
        PlayerComponent playerComponent = PLAYER.get(player);

        engine.update(DT);

        assertEquals(0, playerComponent.coins);
        assertEquals(2, engine.getEntities().size());
        verifyNoInteractions(sfxSystem);
    }
}
