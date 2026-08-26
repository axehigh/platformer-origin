package com.axehigh.platformer.ecs.components;

import static com.axehigh.platformer.GameConstants.*;

/**
 * The four consumable potion types held in the player's inventory. Each carries its display name,
 * an in-inventory description used by the inventory dialog, and the floating pickup message
 * (text + color) shown when the potion is drunk; the per-type effects are defined in
 * {@code BuffComponent} / {@code GameConstants} (see the potion &amp; buff constants).
 */
public enum PotionType {
    HEALING("Healing", "Restores 1 heart", "+1 HP", MESSAGE_COLOR_HEAL),
    STRENGTH("Strength", "Extra melee damage for 20s", "Double strength!!", MESSAGE_COLOR_STRENGTH),
    SPEED("Speed", "Move faster + triple jump for 15s", "Triple jump!!", MESSAGE_COLOR_SPEED),
    INVULNERABILITY("Invulnerability", "Take no damage for 10s", "Invulnerable!", MESSAGE_COLOR_INVULN);

    private final String displayName;
    private final String description;
    private final String pickupMessage;
    private final float[] messageColor;

    PotionType(String displayName, String description, String pickupMessage, float[] messageColor) {
        this.displayName = displayName;
        this.description = description;
        this.pickupMessage = pickupMessage;
        this.messageColor = messageColor;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    /** Floating text shown above the player when the potion is drunk. */
    public String pickupMessage() {
        return pickupMessage;
    }

    /** RGB (0-1) of the floating pickup message. */
    public float[] messageColor() {
        return messageColor;
    }

    /** Region key shared by {@code origin-game.atlas} (in-game) and {@code uiskin.atlas} (UI). */
    public String regionName() {
        return "potion_" + name().toLowerCase();
    }
}
