package com.axehigh.platformer.map;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.maps.MapObject;
import com.badlogic.gdx.maps.tiled.TiledMapTile;

/**
 * Static readers for Tiled custom properties, uniformly tolerant of where a value was authored
 * (on the object marker or on its tile) and of how numbers were encoded (int/float/string).
 */
final class TileProps {
    private TileProps() {
    }

    /** Reads a string property from the object, falling back to its tile. */
    static String getProperty(MapObject object, TiledMapTile tile, String key, String defaultValue) {
        String value = object.getProperties().get(key, String.class);
        if (value == null && tile != null) {
            value = tile.getProperties().get(key, String.class);
        }
        return value != null ? value : defaultValue;
    }

    /** Reads a numeric custom property from the object (or its tile), tolerating int/float/string encodings. */
    static float getFloatProperty(MapObject object, TiledMapTile tile, String key, float defaultValue) {
        Object value = object.getProperties().get(key);
        if (value == null && tile != null) {
            value = tile.getProperties().get(key);
        }
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return ((Number) value).floatValue();
        }
        try {
            return Float.parseFloat(String.valueOf(value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /** Reads a string property directly from a tile (no map object involved). */
    static String getStringPropertyFromTile(TiledMapTile tile, String key, String defaultValue) {
        if (tile == null) return defaultValue;
        String value = tile.getProperties().get(key, String.class);
        return value != null ? value : defaultValue;
    }

    /** Reads a float property directly from a tile, tolerating int/float/string encodings. */
    static float getFloatPropertyFromTile(TiledMapTile tile, String key, float defaultValue) {
        if (tile == null) return defaultValue;
        Object value = tile.getProperties().get(key);
        if (value == null) return defaultValue;
        if (value instanceof Number) return ((Number) value).floatValue();
        try {
            return Float.parseFloat(String.valueOf(value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /** Parses a color string like {@code "FF8040"} (RGB hex) or {@code "FF8040FF"} (RGBA hex) into a {@link Color}. */
    static Color parseColor(String hex, Color fallback) {
        try {
            String h = hex.startsWith("#") ? hex.substring(1) : hex;
            if (h.length() == 6) {
                int rgb = Integer.parseInt(h, 16);
                return new Color(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f, 1f);
            }
            if (h.length() == 8) {
                int rgba = Integer.parseInt(h, 16);
                return new Color(((rgba >> 24) & 0xFF) / 255f, ((rgba >> 16) & 0xFF) / 255f, ((rgba >> 8) & 0xFF) / 255f, (rgba & 0xFF) / 255f);
            }
        } catch (NumberFormatException ignored) {
        }
        return fallback;
    }
}
