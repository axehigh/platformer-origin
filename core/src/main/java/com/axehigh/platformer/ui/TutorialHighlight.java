package com.axehigh.platformer.ui;

/**
 * Single source of truth for the tutorial {@code highlight} keyword table: each constant maps a
 * set of authorable alias keywords (what a level designer writes in a Tiled sign object's
 * {@code highlight} property) to the skin drawable name used for the pulsing touch-control button
 * and the inline tooltip icon (see {@link TouchControlsStage#setHighlightedControl}).
 *
 * <p>The alias → icon table below is the canonical reference. An exact copy is maintained in
 * {@code resources/docs-ai/map-design-for-tiled.md} §5.8, and the dedicated test
 * {@code TutorialHighlightDocsTest} fails if the two ever disagree.
 *
 * <p>Unknown, empty, or {@code null} keywords resolve to no highlight — the tutorial system treats
 * them as "no button to pulse" (all touch buttons stay white/context-driven).
 */
public enum TutorialHighlight {
    JUMP    ("jump",    "jump", "a", "j"),
    ATTACK  ("sword",   "attack", "sword", "b", "melee"),
    SPECIAL ("daggers", "special", "ranged", "y", "dagger", "throw"),
    INVENTORY("potion", "inventory", "bag", "potion"),
    LEFT    ("left",    "left"),
    RIGHT   ("right",   "right"),
    INTERACT("door",    "enter", "exit", "up", "door", "interact"),
    DROP    ("down",    "down", "drop");

    private final String icon;
    private final String[] keywords;

    TutorialHighlight(String icon, String... keywords) {
        this.icon = icon;
        this.keywords = keywords;
    }

    /** The skin drawable name of the touch button this highlight pulses (e.g. {@code "jump"}). */
    public String icon() {
        return icon;
    }

    /** All authorable alias keywords for this highlight (case-insensitive, whitespace-tolerated). */
    public String[] keywords() {
        return keywords;
    }

    /**
     * Finds the {@link TutorialHighlight} whose {@link #keywords()} contains {@code target}.
     * Null-safe; the lookup is case-insensitive and trims surrounding whitespace. Returns
     * {@code null} for unknown, empty, or {@code null} keywords (meaning "no highlight").
     */
    public static TutorialHighlight fromKeyword(String target) {
        if (target == null) return null;
        String t = target.toLowerCase().trim();
        if (t.isEmpty()) return null;
        for (TutorialHighlight h : values()) {
            for (String k : h.keywords) {
                if (t.equals(k)) return h;
            }
        }
        return null;
    }

    /**
     * Delegates to {@link #fromKeyword(String)} and returns the matching highlight's
     * {@link #icon()}, or {@code null} when no highlight matches.
     */
    public static String iconNameFor(String target) {
        TutorialHighlight h = fromKeyword(target);
        return h != null ? h.icon() : null;
    }
}