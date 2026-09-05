package com.axehigh.platformer.shop;

import com.axehigh.platformer.ecs.components.PlayerComponent;
import com.badlogic.gdx.utils.Array;

/**
 * Holds the fixed catalog of purchasable upgrades and applies purchases directly against a {@link
 * PlayerComponent}. Plain Java class (no Ashley wiring) — there's no vendor entity/UI yet; a
 * future shop map/vendor interaction can call {@link #purchase(PlayerComponent, ShopItem)}
 * directly once it exists.
 */
public class ShopManager {
    /** Hard cap on repeatable "Iron Heart" purchases: 3 buys max (+3 max health). */
    public static final int MAX_IRON_HEART_BUYS = 3;
    /** "Sharp Edge": raises sword damage from 5 to 8 (one-time). */
    public static final String SHARP_EDGE = "Sharp Edge";
    /** "Dagger Bandolier": doubles max dagger ammo from 30 to 60 (one-time). */
    public static final String DAGGER_BANDOLIER = "Dagger Bandolier";
    /** "Iron Heart": raises max health (and current health) by 1 (repeatable). */
    public static final String IRON_HEART = "Iron Heart";

    private final Array<ShopItem> catalog;

    public ShopManager() {
        catalog = new Array<>();
        catalog.add(new ShopItem(SHARP_EDGE, 100,
            player -> player.swordDamage = 8,
            false,
            player -> player.sharpEdgePurchased));
        catalog.add(new ShopItem(DAGGER_BANDOLIER, 75,
            player -> player.maxAmmo = 60,
            false,
            player -> player.daggerBandolierPurchased));
        catalog.add(new ShopItem(IRON_HEART, 150,
            player -> {
                player.maxHealth += 1;
                player.health += 1;
            },
            true,
            null));
    }

    public Array<ShopItem> getCatalog() {
        return catalog;
    }

    /**
     * Attempts to buy {@code item} for {@code player}. Rejects the transaction (no state change)
     * if the player doesn't have enough gold, if the item is a one-time upgrade that's already
     * been purchased, or if the item is "Iron Heart" and the repeatable cap
     * ({@link #MAX_IRON_HEART_BUYS}) has been reached. Otherwise deducts {@code item.cost} from
     * {@code player.coins}, applies the item's effect, marks the purchased flag (for one-time
     * items), and returns {@code true}.
     */
    public boolean purchase(PlayerComponent player, ShopItem item) {
        if (player.coins < item.cost) {
            return false;
        }
        if (!item.repeatable && item.alreadyPurchased != null && item.alreadyPurchased.test(player)) {
            return false;
        }
        if (IRON_HEART.equals(item.name) && player.ironHeartCount >= MAX_IRON_HEART_BUYS) {
            return false;
        }

        player.coins -= item.cost;
        item.effect.apply(player);

        if (SHARP_EDGE.equals(item.name)) {
            player.sharpEdgePurchased = true;
        } else if (DAGGER_BANDOLIER.equals(item.name)) {
            player.daggerBandolierPurchased = true;
        } else if (IRON_HEART.equals(item.name)) {
            player.ironHeartCount++;
        }

        return true;
    }
}
