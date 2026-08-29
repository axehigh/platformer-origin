package com.axehigh.platformer.map;

import com.axehigh.platformer.ecs.components.PlayerComponent;
import com.axehigh.platformer.ecs.components.PotionType;
import com.badlogic.gdx.utils.Array;

/** Plain, JSON-serializable snapshot of the level path plus a player's core persistent stats. */
public class SaveData {
    public String levelPath;
    public int health;
    public int maxHealth;
    public int coins;
    public int items;
    public int swordDamage;
    public boolean sharpEdgePurchased;
    public boolean daggerBandolierPurchased;
    public int ironHeartCount;
    public int healingPotions;
    public int strengthPotions;
    public int speedPotions;
    public int invulnerabilityPotions;
    public Array<String> completedLevelIds = new Array<>();
    public int triesRemaining = 3;
    public int enemiesKilled = 0;

    /** No-arg constructor required by libGDX {@code Json}. */
    public SaveData() {
    }

    /**
     * Builds a snapshot from the player's current stats (save path). The caller owns the
     * non-stat fields: {@code levelPath} and {@code completedLevelIds}.
     */
    public static SaveData of(PlayerComponent player) {
        SaveData saveData = new SaveData();
        saveData.health = player.health;
        saveData.maxHealth = player.maxHealth;
        saveData.coins = player.coins;
        saveData.items = player.items;
        saveData.swordDamage = player.swordDamage;
        saveData.sharpEdgePurchased = player.sharpEdgePurchased;
        saveData.daggerBandolierPurchased = player.daggerBandolierPurchased;
        saveData.ironHeartCount = player.ironHeartCount;
        saveData.healingPotions = player.countPotion(PotionType.HEALING);
        saveData.strengthPotions = player.countPotion(PotionType.STRENGTH);
        saveData.speedPotions = player.countPotion(PotionType.SPEED);
        saveData.invulnerabilityPotions = player.countPotion(PotionType.INVULNERABILITY);
        return saveData;
    }

    /** Copies this snapshot's stats onto the player (load path). */
    public void applyTo(PlayerComponent player) {
        player.health = health;
        player.maxHealth = maxHealth;
        player.coins = coins;
        player.items = items;
        player.swordDamage = swordDamage;
        player.sharpEdgePurchased = sharpEdgePurchased;
        player.daggerBandolierPurchased = daggerBandolierPurchased;
        player.ironHeartCount = ironHeartCount;
        player.setPotionCount(PotionType.HEALING, healingPotions);
        player.setPotionCount(PotionType.STRENGTH, strengthPotions);
        player.setPotionCount(PotionType.SPEED, speedPotions);
        player.setPotionCount(PotionType.INVULNERABILITY, invulnerabilityPotions);
    }
}
