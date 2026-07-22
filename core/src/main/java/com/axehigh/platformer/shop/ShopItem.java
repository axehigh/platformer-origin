package com.axehigh.platformer.shop;

import com.axehigh.platformer.ecs.components.PlayerComponent;

import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Describes a single purchasable shop upgrade: its display name, gold cost, the stat-mutating
 * effect applied on purchase, and whether it can be bought more than once. See {@link
 * ShopManager} for the fixed catalog and the {@code purchase(...)} transaction logic.
 */
public class ShopItem {
    public final String name;
    public final int cost;
    public final Consumer<PlayerComponent> effect;
    /** False for one-time upgrades (already-purchased items can't be bought again). */
    public final boolean repeatable;
    /** For one-time items, checks whether it's already been purchased; {@code null} for repeatable items. */
    public final Predicate<PlayerComponent> alreadyPurchased;

    public ShopItem(String name, int cost, Consumer<PlayerComponent> effect, boolean repeatable,
                     Predicate<PlayerComponent> alreadyPurchased) {
        this.name = name;
        this.cost = cost;
        this.effect = effect;
        this.repeatable = repeatable;
        this.alreadyPurchased = alreadyPurchased;
    }
}
