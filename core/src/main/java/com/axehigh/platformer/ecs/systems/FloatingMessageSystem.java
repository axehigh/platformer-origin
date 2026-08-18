package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.ecs.components.FloatingMessageComponent;
import com.axehigh.platformer.ecs.components.TransformComponent;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;

import static com.axehigh.platformer.ecs.components.Mappers.FLOATING_MESSAGE;
import static com.axehigh.platformer.ecs.components.Mappers.TRANSFORM;

/**
 * Renders short-lived floating text labels that drift upward and fade out. Used for damage numbers,
 * coin pickups, potion effects, and buff activations. Runs just after {@code RenderSystem} so the
 * text draws on top of entities. The font is the skin's default {@code BitmapFont} scaled down to
 * fit the game-world coordinate space.
 */
public class FloatingMessageSystem extends IteratingSystem {
    private final SpriteBatch batch;
    private final OrthographicCamera camera;
    private final BitmapFont font;
    private final GlyphLayout layout = new GlyphLayout();

    public FloatingMessageSystem(SpriteBatch batch, OrthographicCamera camera, Skin skin, int priority) {
        super(Family.all(FloatingMessageComponent.class, TransformComponent.class).get(), priority);
        this.batch = batch;
        this.camera = camera;
        this.font = skin.getFont("edgeofgalaxy");
    }

    @Override
    public void update(float deltaTime) {
        int count = getEntities().size();
        if (count > 0) {
            Gdx.app.log("FloatingMsg", "updating " + count + " messages");
        }
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        super.update(deltaTime);
        batch.end();
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        FloatingMessageComponent msg = FLOATING_MESSAGE.get(entity);
        TransformComponent transform = TRANSFORM.get(entity);

        msg.age += deltaTime;
        if (msg.age >= msg.lifetime) {
            Gdx.app.log("FloatingMsg", "removing expired: '" + msg.text + "'");
            getEngine().removeEntity(entity);
            return;
        }

        transform.position.y += msg.driftSpeed * deltaTime;

        float alpha = 1f - (msg.age / msg.lifetime);
        Gdx.app.log("FloatingMsg", "draw '" + msg.text + "' at (" + transform.position.x + ", " + transform.position.y + ") alpha=" + alpha);
        font.setColor(msg.color.r, msg.color.g, msg.color.b, alpha);
        font.getData().setScale(msg.fontScale);
        layout.setText(font, msg.text);
        font.draw(batch, msg.text,
                transform.position.x - layout.width / 2f,
                transform.position.y + layout.height / 2f);
        font.getData().setScale(1f);
    }
}
