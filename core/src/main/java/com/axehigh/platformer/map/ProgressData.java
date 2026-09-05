package com.axehigh.platformer.map;

import com.badlogic.gdx.utils.Array;

/** Durable, account-level record of level/world stars, kept across New Game / death / Clear Player. */
public class ProgressData {
    public Array<String> completedLevelIds = new Array<>();
    public Array<String> completedWorldIds = new Array<>();

    /** No-arg constructor required by libGDX {@code Json}. */
    public ProgressData() {
    }
}
