---
sessionId: session-260726-185708-1arb
---

# Technical Design

### Overview & Goals
The player's collision box is misaligned with the sprite and shifts incorrectly when facing left. The current system hardcodes the collision box's position to the `TransformComponent`'s position, ignoring any offset required to center it within the sprite.

### Key Decisions
- **Offset-based Collision**: Update `MovementSystem` to treat `CollisionComponent.bounds.x` and `y` as offsets relative to `TransformComponent.position`.
- **EntityFactory Configuration**: Initialize `CollisionComponent.bounds` with the appropriate `x` and `y` offsets to correctly center the collision box on the player sprite.

### Technical Design
- **MovementSystem**: Update the `entityBounds` calculation in `moveX` and `moveY` to include the `collision.bounds.x` and `collision.bounds.y`.
- **EntityFactory**: Calculate the `x` and `y` offsets based on the difference between the sprite's full size and the desired collision box size, applying the `finalScale` factor.

# Testing

### Validation Approach
- **Visual Verification**: Use the debug rendering (`SHIFT+D`) to ensure the green collision box is correctly centered on the player sprite when facing both left and right.
- **Gameplay Verification**: Ensure that the player can still move and collide with walls as expected without the box being offset.

# Delivery Steps

### ✓ Step 1: Update MovementSystem collision logic
Update `MovementSystem.java` to incorporate `collision.bounds.x` and `collision.bounds.y` as offsets when calculating the collision `entityBounds` for X and Y movement resolution.
- Modify `moveX` and `moveY` methods in `MovementSystem` to use `transform.position.x + collision.bounds.x` and `transform.position.y + collision.bounds.y` instead of just `transform.position.x` and `y`.

### ✓ Step 2: Update EntityFactory collision offset
Update `EntityFactory.createPlayer` to set the `collisionComponent.bounds.x` and `collisionComponent.bounds.y` to the correct centered offset.
- Calculate the `x` offset to center the 40-pixel collision box within the 128-pixel sprite (i.e., `(128 - 40) / 2`).
- Apply this offset in `EntityFactory.createPlayer` when creating the `CollisionComponent`.