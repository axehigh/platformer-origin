package com.axehigh.platformer.ecs.components;

import com.badlogic.ashley.core.Component;

/** Flag component for damageable enemy entities (no AI/behavior logic; bullets can damage them). */
public class EnemyComponent implements Component {
    public float health = 1f;
}
