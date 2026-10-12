package com.axehigh.platformer.ecs.components;

import static com.axehigh.platformer.GameConstants.*;

/**
 * Consumable potion types held in the player's inventory. Each carries its display name,
 * duration (for timed buffs, 0 for instant effects like Healing), an in-inventory description
 * connected to the duration, and the floating pickup message (text + color) shown when the potion
 * is drunk; the per-type effects are defined in {@code BuffComponent} / {@code BuffSystem}.
 */
public enum PotionType {
    FIRE_BREATH("Fire Breath", 15f, "Shoot fireballs for %ds", "Fire Breath", MESSAGE_COLOR_FIRE),
    HEALING("Healing", 0f, "Restores 1 heart", "Healing", MESSAGE_COLOR_HEAL),
    INVISIBILITY("Invisibility", 10f, "Walk through monsters for %ds", "Invisibility", MESSAGE_COLOR_INVISIBILITY),
    INVULNERABILITY("Invulnerability", 10f, "Take no damage for %ds", "Invulnerability", MESSAGE_COLOR_INVULN),
    JUMP("Jump", 15f, "Grants triple jump for %ds", "Jump", MESSAGE_COLOR_JUMP),
    STRENGTH("Strength", 20f, "Extra melee damage for %ds", "Strength", MESSAGE_COLOR_STRENGTH),
    SPEED("Speed", 15f, "Move faster for %ds", "Speed potion", MESSAGE_COLOR_SPEED),
    SPIDER("Spider", 15f, "Climb walls for %ds", "Spider climb", MESSAGE_COLOR_SPIDER)
    ;

    private final String displayName;
    private final float duration;
    private final String description;
    private final String pickupMessage;
    private final float[] messageColor;

    PotionType(String displayName, String description, String pickupMessage, float[] messageColor) {
        this(displayName, 0f, description, pickupMessage, messageColor);
    }

    PotionType(String displayName, float duration, String descriptionPattern, String pickupMessage, float[] messageColor) {
        this.displayName = displayName;
        this.duration = duration;
        if (duration > 0f && descriptionPattern.contains("%d")) {
            this.description = descriptionPattern.replace("%d", Integer.toString((int) duration));
        } else {
            this.description = descriptionPattern;
        }
        this.pickupMessage = pickupMessage;
        this.messageColor = messageColor;
    }

    public String displayName() {
        return displayName;
    }

    public float duration() {
        return duration;
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

    /** Parses a potion type name robustly, falling back to {@link #HEALING}. */
    public static PotionType parse(String name) {
        if (name == null) {
            return HEALING;
        }
        String clean = name.trim().toUpperCase().replace("-", "_").replace(" ", "_");
        try {
            return valueOf(clean);
        } catch (IllegalArgumentException e) {
            if ("FIREBREATH".equals(clean)) {
                return FIRE_BREATH;
            }
            return HEALING;
        }
    }
}
