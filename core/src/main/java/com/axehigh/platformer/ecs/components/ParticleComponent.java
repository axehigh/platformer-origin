package com.axehigh.platformer.ecs.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.graphics.g2d.ParticleEffect;
import com.badlogic.gdx.utils.Pool;

public class ParticleComponent implements Component, Pool.Poolable {
    public ParticleEffect effect;
    public boolean isDead = false;
    public float delay = 0;
    public boolean started = false;
    public float scale = 1f;

    @Override
    public void reset() {
        if (effect != null) {
            effect.reset();
        }
        effect = null;
        isDead = false;
        delay = 0;
        started = false;
        scale = 1f;
    }
}
