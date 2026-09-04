package com.axehigh.platformer.ecs.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Array;

public class LootComponent implements Component {
    public enum LootType { COIN, AMMO, POTION }

    public static class LootEntry {
        public LootType type;
        public int amount;
        public String potionType;
    }

    public final Array<LootEntry> drops = new Array<>();
}
