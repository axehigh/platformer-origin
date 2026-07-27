---
sessionId: session-260727-135303-swkm
---

# Requirements

### Overview & Goals
The goal is to enhance the mobile user experience by making the on-screen touch controls larger and more spaced out, while reducing their visual footprint via transparency.

### Scope
- **In Scope**:
    - Modification of the `TouchControlsStage` class.
    - Adjustment of button sizes (D-pad, Action buttons).
    - Adjustment of padding and margins.
    - Application of transparency to the touch overlay.
- **Out of Scope**:
    - Modification of menu buttons (Main Menu, Level Select).
    - Changes to HUD elements (Health, Coins).
    - Functional changes to input handling.


# Technical Design

### Current Implementation
The touch controls are implemented in `TouchControlsStage.java` using a `root` Table that contains a `dpad` Table (left) and an `actions` Table (right). Buttons are small (20-24 units) and have tight padding (2-6 units).

### Proposed Changes
- **Button Sizing**:
    - D-pad (Left/Right): Increase to `40x40`.
    - Action (A/B/Y) & Interact: Increase to `36x36`.
- **Spacing**:
    - D-pad internal padding: Increase to `12`.
    - Action button padding: Increase to `8`.
    - Screen edge padding: Increase to `20`.
- **Transparency**:
    - Apply `0.6f` alpha to the `root` table of the `TouchControlsStage`.

### File Structure
- `core/src/main/java/com/axehigh/platformer/ui/TouchControlsStage.java` (Modified)


# Testing

### Validation Approach
- Verify that the buttons are visually larger and easier to hit in the touch overlay.
- Verify that the spacing between buttons is sufficient to prevent accidental overlapping taps.
- Verify that the controls are semi-transparent and do not completely obscure the game world.
- Ensure that the contextual "Interact" button (up arrow) also follows the new size and transparency rules.


# Delivery Steps

### ✓ Step 1: Adjust touch control button sizes and internal spacing
Update the button dimensions and spacing in `TouchControlsStage.java`.

- Change `leftButton` and `rightButton` size from `24f x 24f` to `40f x 40f`.
- Change `yButton`, `bButton`, `aButton`, and `interactButton` size from `20f x 20f` to `36f x 36f`.
- Increase `dpad` button padding from `6f` to `12f`.
- Increase `actions` button padding from `2f` to `8f`.

### ✓ Step 2: Increase screen margins and apply transparency
Update the layout margins and transparency in `TouchControlsStage.java`.

- Increase the `root` table padding for `dpad` and `actions` groups from `10f` to `20f` to move them away from the screen corners.
- Set the alpha of the `root` table to `0.6f` by modifying its color: `root.getColor().a = 0.6f;`.