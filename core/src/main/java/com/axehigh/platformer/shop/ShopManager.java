package com.axehigh.platformer.shop;

import com.axehigh.platformer.ecs.components.PlayerComponent;

import java.util.Collections;
import java.util.List;

/**
 * Holds the fixed catalog of purchasable upgrades and applies purchases directly against a {@link
 * PlayerComponent}. Plain Java class (no Ashley wiring) — there's no vendor entity/UI yet; a
 * future shop map/vendor interaction can call {@link #purchase(PlayerComponent, ShopItem)}
 * directly once it exists.
 */
public class ShopManager {
    /** "Sharp Edge": raises sword damage from 5 to 8 (one-time). */
    public static final String SHARP_EDGE = "Sharp Edge";
    /** "Dagger Bandolier": doubles max dagger ammo from 30 to 60 (one-time). */
    public static final String DAGGER_BANDOLIER = "Dagger Bandolier";
    /** "Iron Heart": raises max health (and current health) by 1 (repeatable). */
    public static final String IRON_HEART = "Iron Heart";

    private final List<ShopItem> catalog;

    public ShopManager() {
        catalog = Collections.unmodifiableList(java.util.Arrays.asList(
            new ShopItem(SHARP_EDGE, 100,
                player -> player.swordDamage = 8,
                false,
                player -> player.sharpEdgePurchased),
            new ShopItem(DAGGER_BANDOLIER, 75,
                player -> player.maxItems = 60,
                false,
                player -> player.daggerBandolierPurchased),
            new ShopItem(IRON_HEART, 150,
                player -> {
                    player.maxHealth += 1;
                    player.health += 1;
                },
                true,
                null)
        ));
    }

    public List<ShopItem> getCatalog() {
        return catalog;
    }

    /**
     * Attempts to buy {@code item} for {@code player}. Rejects the transaction (no state change)
     * if the player doesn't have enough gold, or if the item is a one-time upgrade that's already
     * been purchased. Otherwise deducts {@code item.cost} from {@code player.coins}, applies the
     * item's effect, marks the purchased flag (for one-time items), and returns {@code true}.
     */
    public boolean purchase(PlayerComponent player, ShopItem item) {
        if (player.coins < item.cost) {
            return false;
        }
        if (!item.repeatable && item.alreadyPurchased != null && item.alreadyPurchased.test(player)) {
            return false;
        }

        player.coins -= item.cost;
        item.effect.accept(player);

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
