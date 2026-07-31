package com.axehigh.platformer.map;

import com.badlogic.gdx.math.Rectangle;

/**
 * A room zone parsed from the map's "Rooms" object layer: the room's world-pixel bounds (a {@code
 * Rectangle}) plus an optional camera-mode override. {@code CameraSystem} decides how to frame the
 * room per axis from {@link #mode}: {@link Mode#AUTO} (the default) infers it from size — a room no
 * bigger than the viewport on an axis gets static, room-centered (flip-screen) framing, while a
 * bigger room gets dead-zone scrolling — whereas {@link Mode#FLIP} forces static framing and {@link
 * Mode#SCROLL} forces scrolling regardless of size. Set via an optional {@code camera} custom
 * property ("flip"/"scroll") on the room rectangle in Tiled.
 */
public class Room extends Rectangle {

    public enum Mode {
        /** Infer from the room's size relative to the viewport. */
        AUTO,
        /** Always lock the camera to the room's center (flip-screen framing). */
        FLIP,
        /** Always dead-zone scroll within the room's bounds. */
        SCROLL
    }

    /** Camera framing mode; {@link Mode#AUTO} unless the room declares a {@code camera} property. */
    public Mode mode = Mode.AUTO;

    public Room() {
    }

    public Room(float x, float y, float width, float height) {
        super(x, y, width, height);
    }

    public Room(float x, float y, float width, float height, Mode mode) {
        this(x, y, width, height);
        this.mode = mode;
    }
}
