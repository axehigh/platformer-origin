package com.axehigh.platformer.ecs.components;

import com.axehigh.platformer.GameConstants;
import com.axehigh.platformer.util.Timer;
import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.ObjectSet;


public class PlayerComponent implements Component {
    public int health = GameConstants.MaxHealth;
    public int maxHealth = GameConstants.MaxHealth;
    public int coins = 0;
    public int items = 0;
    public int maxItems = GameConstants.MaxItems;
    /** Held Healing potions, capped at {@link GameConstants#POTION_CAP}. */
    public int healingPotions = 0;
    /** Held Strength potions, capped at {@link GameConstants#POTION_CAP}. */
    public int strengthPotions = 0;
    /** Held Speed potions, capped at {@link GameConstants#POTION_CAP}. */
    public int speedPotions = 0;
    /** Held Invulnerability potions, capped at {@link GameConstants#POTION_CAP}. */
    public int invulnerabilityPotions = 0;
    /** The potion type the "use potion" action currently targets; cycled by the potion input. */
    public PotionType selectedPotion = PotionType.HEALING;
    /** Short window after drinking a potion during which another drink is ignored. */
    public Timer potionCooldown = new Timer();
    /** Debounce window for batching coin-pickup floating messages. */
    public Timer coinMessageCooldown = new Timer();
    /** Accumulated coin count waiting to be shown in the next floating message. */
    public int pendingCoinMessage = 0;
    /** Current melee/sword damage per hit; base 5, raised to 8 by the "Sharp Edge" shop upgrade. */
    public int swordDamage = GameConstants.BaseDamage;
    /** One-time flag: true once the "Sharp Edge" upgrade (swordDamage -> 8) has been purchased. */
    public boolean sharpEdgePurchased = false;
    /** One-time flag: true once the "Dagger Bandolier" upgrade (maxItems -> 60) has been purchased. */
    public boolean daggerBandolierPurchased = false;
    /** Number of times the repeatable "Iron Heart" upgrade (+1 maxHealth/health) has been purchased. */
    public int ironHeartCount = 0;
    /** -1 for left, 1 for right. */
    public int facingDirection = 1;

    /** Tracks current jumps executed; resets to 0 when grounded. */
    public int jumpCount = 0;
    /** Allows for double jumping. */
    public int maxJumps = 2;
    /** Flag for wall attachment. */
    public boolean isWallClimbing = false;
    /** Prevents bullet spamming. */
    public Timer shootCooldown = new Timer();

    /** Prevents melee-strike spamming. */
    public Timer meleeCooldown = new Timer();
    /** Counts down while the melee strike hitbox is active; active means the strike is in progress. */
    public Timer meleeAttack = new Timer();
    /** True once the current swing has landed at least one hit (enemy damaged, chest opened, secret
     * wall broken, or wall clank); gates the one-shot secret-wall break / clank effects. */
    public boolean meleeHasHit = false;
    /** Enemies already damaged by the current swing (cleared at every swing start by
     * {@code PlayerInputSystem}), so a swing can hit every enemy that comes into reach as the blade
     * extends but never the same enemy twice — regardless of swing length vs.
     * {@code EnemyDamageResolver}'s hit-stun grace. */
    public ObjectSet<Entity> meleeHitEnemies = new ObjectSet<>();
    /** Grace period after being hit by an enemy, during which further enemy contact is ignored. */
    public Timer hitInvulnerability = new Timer();
    /**
     * Short hit-stun window after being hit (at least long enough to play the HURT animation once).
     * While active, the player's movement/jump/melee/shoot input is locked (a knockback pop can play
     * out) and the HURT animation state is shown; the longer {@code hitInvulnerability} window that
     * runs past it is communicated by blinking the sprite instead.
     */
    public Timer hurtTimer = new Timer();

    /** Flag to track death status. */
    public boolean isDead = false;

    /** One-shot: true only during the frame the interact key/touch button was pressed. */
    public boolean interactPressed = false;
    /** True while the player is inside any exit gate's proximity sensor; drives the interact UI prompt. */
    public boolean nearExit = false;

    /** One-shot: true only during the frame a drop-through request (down/S/touch) was made. */
    public boolean dropRequested = false;
    /** True while the player is standing on (or falling through) a drop-through platform; drives the contextual drop button. */
    public boolean onDropTile = false;
    /**
     * Short window after a drop request during which the player falls through drop-through platforms
     * instead of landing on them; while active the one-way rects are skipped in MovementSystem.
     */
    public Timer dropWindow = new Timer();

    /** True from the frame the player leaves the ground until the next grounded frame. */
    public boolean inAir = false;
    /** Highest feet Y (world units) reached while airborne; used to gate landing dust. */
    public float maxAirHeight = 0f;

    /** Squash-and-stretch visual pulse: true while the player sprite is scaling through a pulse. */
    public boolean squashActive = false;
    /** True = jump stretch (taller/thinner), false = landing squash (flatter/wider). Jump stretch is currently unused. */
    public boolean squashIsStretch = false;
    /** Current deviation magnitude (0..1) of the pulse; decays toward 0 each frame. */
    public float squashAmount = 0f;
    /** Base (resting) scale captured when the pulse started, before the deviation. */
    public float squashBaseX = 1f;
    public float squashBaseY = 1f;

    /** Advances {@link #selectedPotion} to the next potion type in the cycle order. */
    public void cyclePotion() {
        PotionType[] types = PotionType.values();
        selectedPotion = types[(selectedPotion.ordinal() + 1) % types.length];
    }

    /**
     * Consumes one potion of the {@link #selectedPotion} type (count decremented, clamped to 0).
     * Returns {@code false} without consuming when the player holds none of that type.
     */
    public boolean consumeSelectedPotion() {
        return consumePotion(selectedPotion);
    }

    /**
     * Consumes one potion of the given type (count decremented, clamped to 0). Returns
     * {@code false} without consuming when the player holds none of that type.
     */
    public boolean consumePotion(PotionType type) {
        if (countPotion(type) <= 0) {
            return false;
        }
        setPotionCount(type, countPotion(type) - 1);
        return true;
    }

    /** Returns how many potions of the given type the player holds. */
    public int countPotion(PotionType type) {
        switch (type) {
            case HEALING:
                return healingPotions;
            case STRENGTH:
                return strengthPotions;
            case SPEED:
                return speedPotions;
            case INVULNERABILITY:
                return invulnerabilityPotions;
        }
        return 0;
    }

    /** Sets how many potions of the given type the player holds, clamped to {@code 0..POTION_CAP}. */
    public void setPotionCount(PotionType type, int count) {
        int clamped = Math.max(0, Math.min(GameConstants.POTION_CAP, count));
        switch (type) {
            case HEALING:
                healingPotions = clamped;
                break;
            case STRENGTH:
                strengthPotions = clamped;
                break;
            case SPEED:
                speedPotions = clamped;
                break;
            case INVULNERABILITY:
                invulnerabilityPotions = clamped;
                break;
        }
    }
}
