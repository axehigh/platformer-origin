package com.axehigh.platformer.map;

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

    /** No-arg constructor required by libGDX {@code Json}. */
    public SaveData() {
    }
}
