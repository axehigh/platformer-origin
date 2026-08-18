package com.axehigh.platformer.ecs.components;

/**
 * The four consumable potion types held in the player's inventory. Each carries its display name
 * and an in-inventory description used by the inventory dialog; the per-type effects are defined in
 * {@code BuffComponent} / {@code GameConstants} (see the potion &amp; buff constants).
 */
public enum PotionType {
    HEALING("Healing", "Restores 1 heart"),
    STRENGTH("Strength", "Extra melee damage for 20s"),
    SPEED("Speed", "Move faster for 15s"),
    INVULNERABILITY("Invulnerability", "Take no damage for 10s");

    private final String displayName;
    private final String description;

    PotionType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    /** Region key shared by {@code origin-game.atlas} (in-game) and {@code uiskin.atlas} (UI). */
    public String regionName() {
        return "potion_" + name().toLowerCase();
    }
}
