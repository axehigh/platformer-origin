# Traps Feature — Testing Guide

## Overview

This document describes how to test the two new trap types: **Acid/Lava Drops** and **Flame Traps**. Traps are placed via Tiled map objects and interact with the existing ECS systems.

---

## Prerequisites

1. Run the game on desktop (`DesktopLauncher`)
2. Have a level with trap objects placed in Tiled (see "Placing Traps in Tiled" below)
3. Ensure collision debug is enabled (SHIFT+D) to visualize trap AABBs

---

## Placing Traps in Tiled

### Acid/Lava Drop Spawner

1. In the **`objects`** layer, add a **Point** or **Rectangle** object
2. Set the `type` property to `trap`
3. Add these custom properties:

| Property | Value | Description |
|----------|-------|-------------|
| `trapType` | `acidDrop` (default) | Spawner type |
| `direction` | `down` / `up` / `left` / `right` | Drop direction |
| `interval` | `2.0` | Seconds between drips |
| `speed` | `200` | Projectile speed (u/s) |
| `damage` | `1` | Damage on contact |

**Examples:**
- Acid dripping from ceiling: `direction=down`, place object near ceiling
- Lava geyser from floor: `direction=up`, place object near floor
- Side sprayer: `direction=left` or `direction=right`, place near wall

### Flame Trap

1. In the **`objects`** layer, add a **Point** or **Rectangle** object
2. Set the `type` property to `trap`
3. Add these custom properties:

| Property | Value | Description |
|----------|-------|-------------|
| `trapType` | `flame` | Flame trap type |
| `direction` | `down` / `up` / `left` / `right` | Flame direction |
| `duration` | `2.0` | Seconds flame is active |
| `cooldown` | `1.5` | Seconds between flame bursts |
| `pulseSpeed` | `2.0` | How fast flame grows/shrinks |

**Examples:**
- Ceiling flame: `direction=down`, place object at ceiling
- Floor lava flame: `direction=up`, place object at floor
- Wall flame: `direction=left` or `direction=right`, place near wall

---

## Test Cases

### 1. Acid Drop — Basic Functionality

**Setup:** Place an acid drop spawner with `direction=down`, `interval=2.0`

**Steps:**
1. Start the level, observe the spawner location
2. Wait 2 seconds — a green teardrop sprite should appear and fall downward
3. Observe the drop falling at constant speed (no gravity acceleration)
4. When the drop hits the ground/wall, it should disappear
5. After 2 more seconds, another drop should spawn

**Expected:** Drops spawn every 2 seconds, fall straight down, disappear on contact with surfaces.

### 2. Acid Drop — Player Damage

**Setup:** Place an acid drop spawner directly above a walkable platform

**Steps:**
1. Walk into the path of a falling drop
2. Observe the player takes 1 damage (health decreases by 1)
3. Observe the "-1" floating message appears
4. Observe the player blinks (invulnerability frames)
5. Try to touch another drop immediately — should not take damage (2-second grace period)
6. Wait for blinking to stop, then touch another drop — should take damage again

**Expected:** 1 damage per hit, 2-second invulnerability grace period, visual feedback (blink + floating message).

### 3. Acid Drop — Direction Variants

**Setup:** Place spawners with `direction=up`, `direction=left`, `direction=right`

**Steps:**
1. `direction=up`: Drop should rise from the floor upward
2. `direction=left`: Drop should move leftward from the spawner
3. `direction=right`: Drop should move rightward from the spawner

**Expected:** Drops travel in the configured direction at constant speed.

### 4. Acid Drop — Wall Collision

**Setup:** Place a spawner near a wall so the drop path intersects the wall

**Steps:**
1. Wait for a drop to spawn
2. Observe the drop hits the wall and disappears
3. Drop should not pass through the wall

**Expected:** Drops are removed on wall/ceiling/floor contact.

### 5. Flame Trap — Pulse Cycle

**Setup:** Place a flame trap with `direction=down`, `duration=2.0`, `cooldown=1.5`

**Steps:**
1. Start the level, observe the flame trap location
2. Initially, the flame should be small (or in cooldown)
3. Observe the flame grow from small to large over 2 seconds
4. After reaching full size, observe the flame disappears
5. Wait 1.5 seconds (cooldown), then observe the flame reappears and grows again

**Expected:** Flame cycles between on (2s) and off (1.5s), growing/shrinking visually.

### 6. Flame Trap — Collision Scaling

**Setup:** Place a flame trap, enable collision debug (SHIFT+D)

**Steps:**
1. Observe the green collision box around the flame
2. As the flame grows, the collision box should grow proportionally
3. As the flame shrinks, the collision box should shrink
4. When the flame is off, no collision box should be active

**Expected:** Collision box scales with the visual flame size.

### 7. Flame Trap — Player Damage

**Setup:** Place a flame trap in the player's path

**Steps:**
1. Walk into the flame while it's in the "on" phase
2. Observe the player takes 1 damage
3. Walk into the flame while it's in the "off" phase — no damage
4. Observe the invulnerability grace period (blinking)

**Expected:** Damage only during flame-on phase, 1 damage per hit, invulnerability frames.

### 8. Flame Trap — Direction Variants

**Setup:** Place flame traps with `direction=up`, `direction=left`, `direction=right`

**Steps:**
1. `direction=up`: Flame should grow upward from the floor
2. `direction=left`: Flame should grow leftward from the right wall (sprite rotated 270°)
3. `direction=right`: Flame should grow rightward from the left wall (sprite rotated 90°)

**Expected:** Flames extend in the configured direction, collision boxes anchor at the source point.

### 9. Room Awareness

**Setup:** Place traps in a room that the player will leave

**Steps:**
1. Enter the room with traps — observe traps are active
2. Leave the room (cross room boundary)
3. Observe spawners stop spawning, flames freeze
4. Re-enter the room — observe traps resume

**Expected:** Traps freeze in inactive rooms, resume when player returns.

### 10. Level Transition Cleanup

**Setup:** Place traps in a level, then exit to the next level

**Steps:**
1. Start level with traps, observe them active
2. Reach the exit gate and transition to the next level
3. Return to the original level (if possible)
4. Observe traps are respawned fresh (no stale state)

**Expected:** Traps are cleaned up on level transition, respawn correctly on re-entry.

---

## Debugging Tips

1. **Collision Debug (SHIFT+D):** Shows all trap AABBs in lime green. Flame traps show dynamically scaling boxes.
2. **Logcat:** Check for any `TrapSystem` or `TrapContactSystem` errors in the console.
3. **Touch Debug:** Use the pause menu's "Touch Debug" button if touch input seems misaligned with traps.
4. **Room Boundaries:** Ensure traps are placed inside valid Room rectangles for room-awareness to work.

---

## Expected Visual Results

- **Acid drops:** Small green teardrops (8×12 pixels) falling/rising at constant speed
- **Lava drops:** Same shape, can be swapped to orange/red sprite by changing the texture
- **Flames:** Animated fire (fire1..10 sprites) growing/shrinking with visible collision scaling
- **Damage feedback:** "-1" floating message, player blink animation, health decrease

---

## Common Issues

| Issue | Likely Cause | Fix |
|-------|-------------|-----|
| Traps not spawning | `type` property not set to `trap` | Check Tiled object properties |
| Drops don't appear | Texture not loaded | Verify `gfx/acid_drop.png` exists in `assets/gfx/` |
| No damage dealt | `TrapContactSystem` not registered | Check `GameScreen.show()` system registration |
| Flame not pulsing | `TrapSystem` not running at priority 4 | Verify system priority in `GameScreen.show()` |
| Traps active in wrong room | `roomIndex` not set correctly | Ensure Room rectangles cover trap positions |
