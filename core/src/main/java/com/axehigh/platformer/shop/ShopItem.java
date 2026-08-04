package com.axehigh.platformer.shop;

import com.axehigh.platformer.ecs.components.PlayerComponent;

/**
 * Describes a single purchasable shop upgrade: its display name, gold cost, the stat-mutating
 * effect applied on purchase, and whether it can be bought more than once. See {@link
 * ShopManager} for the fixed catalog and the {@code purchase(...)} transaction logic.
 */
public class ShopItem {
    /** Applied to the player once the gold is deducted. */
    public interface Effect {
        void apply(PlayerComponent player);
    }

    /** Decides whether a one-time upgrade was already purchased. */
    public interface AlreadyPurchased {
        boolean test(PlayerComponent player);
    }

    public final String name;
    public final int cost;
    public final Effect effect;
    /** False for one-time upgrades (already-purchased items can't be bought again). */
    public final boolean repeatable;
    /** For one-time items, checks whether it's already been purchased; {@code null} for repeatable items. */
    public final AlreadyPurchased alreadyPurchased;

    public ShopItem(String name, int cost, Effect effect, boolean repeatable,
                    AlreadyPurchased alreadyPurchased) {
        this.name = name;
        this.cost = cost;
        this.effect = effect;
        this.repeatable = repeatable;
        this.alreadyPurchased = alreadyPurchased;
    }
}
