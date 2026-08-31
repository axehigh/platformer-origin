package com.axehigh.platformer.text;

/**
 * Single source of truth for all story/narrative text displayed in the game.
 * Edit constants here rather than scattering strings across screens.
 */
public final class StoryText {

    private StoryText() {}

    public static final String PROLOGUE_TITLE = "PROLOGUE";

    public static final String INTRO_BODY =
        "You are hired to clean out the cave where monsters are pestering the villagers.\n"
      + "Arm yourself, descend into the dungeon, and put an end to the menace.";

    public static final String ENTER_BUTTON = "Enter";
}
