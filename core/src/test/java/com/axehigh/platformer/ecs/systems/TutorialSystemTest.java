package com.axehigh.platformer.ecs.systems;

import com.axehigh.platformer.ecs.components.CollisionComponent;
import com.axehigh.platformer.ecs.components.TransformComponent;
import com.axehigh.platformer.ecs.components.TutorialComponent;
import com.axehigh.platformer.ui.TouchControlsStage;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Array;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import static com.axehigh.platformer.ecs.components.Mappers.COLLISION;
import static com.axehigh.platformer.ecs.components.Mappers.TRANSFORM;
import static org.junit.Assert.assertTrue;
import static org.mockito.AdditionalMatchers.not;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Headless tests for {@link TutorialSystem#update(float)} — specifically the recent fix that runs
 * {@code super.update()} (which activates sensors) BEFORE collecting the touch-button highlight, so
 * the highlight dispatches on the SAME frame the player enters a tutorial sensor (previously it
 * lagged one frame behind the tooltip).
 */
public class TutorialSystemTest extends SystemTestBase {

    private TutorialSystem system;
    private Engine engine;
    private TouchControlsStage stage;

    @Before
    public void setUp() {
        SpriteBatch batch = mock(SpriteBatch.class);
        OrthographicCamera camera = new OrthographicCamera();

        Skin skin = new Skin();
        BitmapFont.BitmapFontData fontData = new BitmapFont.BitmapFontData();
        Array<TextureRegion> pageRegions = new Array<>();
        pageRegions.add(new TextureRegion());
        BitmapFont font = new BitmapFont(fontData, pageRegions, true);
        skin.add("edgeofgalaxy", font);
        // Registered under Drawable.class so skin.getDrawable("table") resolves it; Skin keys
        // resources by the registered type class and getDrawable() looks up under Drawable.class.
        skin.add("table", new TextureRegionDrawable(new TextureRegion()), Drawable.class);

        system = new TutorialSystem(batch, camera, skin, 0);
        stage = mock(TouchControlsStage.class);
        system.setTouchControlsStage(stage);

        engine = newEngine();
        engine.addSystem(system);
    }

    private Entity sign(float x, float y, String text, String highlight) {
        TutorialComponent tutorial = new TutorialComponent();
        tutorial.text = text;
        tutorial.highlight = highlight;
        TransformComponent transform = transform(x, y);
        CollisionComponent collision = collision(0f, 0f, 40f, 40f);
        place(transform, collision, x, y);
        Entity entity = entity(transform, collision, tutorial);
        engine.addEntity(entity);
        return entity;
    }

    private Entity player(float x, float y) {
        TransformComponent transform = transform(x, y);
        CollisionComponent collision = collision(0f, 0f, 20f, 20f);
        place(transform, collision, x, y);
        Entity entity = entity(transform, player(), collision);
        engine.addEntity(entity);
        return entity;
    }

    @Test
    public void update_playerInsideSensor_dispatchesHighlightSameFrame() {
        sign(100f, 100f, "", "jump");
        player(100f, 100f);

        engine.update(DT);

        // Regression: the highlight used to fire one frame AFTER the sensor was entered, because it
        // was collected before super.update() ran. One frame must be enough now.
        verify(stage).setHighlightedControl(eq("jump"), anyFloat());
    }

    @Test
    public void update_playerFarAway_dispatchesEmptyHighlight() {
        sign(100f, 100f, "", "jump");
        player(1000f, 100f);

        engine.update(DT);

        verify(stage).setHighlightedControl(eq(""), anyFloat());
    }

    @Test
    public void update_playerLeavesSensor_resetsHighlightToEmpty() {
        Entity signEntity = sign(100f, 100f, "", "jump");
        Entity playerEntity = player(100f, 100f);

        engine.update(DT);
        verify(stage).setHighlightedControl(eq("jump"), anyFloat());

        place(TRANSFORM.get(playerEntity), COLLISION.get(playerEntity), 1000f, 100f);
        engine.update(DT);

        verify(stage, atLeastOnce()).setHighlightedControl(eq(""), anyFloat());
    }

    @Test
    public void update_sensorWithEmptyHighlightNeverDispatchesNonEmptyTarget() {
        sign(100f, 100f, "", "");
        player(100f, 100f);

        engine.update(DT);

        verify(stage, atLeastOnce()).setHighlightedControl(eq(""), anyFloat());
        verify(stage, never()).setHighlightedControl(not(eq("")), anyFloat());
    }

    @Test
    public void update_nullTouchControlsStage_doesNotCrash() {
        system.setTouchControlsStage(null);
        sign(100f, 100f, "", "jump");
        player(100f, 100f);

        engine.update(DT); // must not throw
    }

    @Test
    public void update_playerInsideSensor_highlightTimerAccumulatesAcrossFrames() {
        sign(100f, 100f, "", "jump");
        player(100f, 100f);

        engine.update(DT);
        engine.update(DT);

        // Both frames overlapping the sensor: the highlight keeps firing and the pulse timer keeps
        // accumulating (not resetting) while the highlight stays active.
        ArgumentCaptor<Float> timerCaptor = ArgumentCaptor.forClass(Float.class);
        verify(stage, times(2)).setHighlightedControl(eq("jump"), timerCaptor.capture());
        assertTrue("highlight timer should accumulate while active",
            timerCaptor.getAllValues().get(1) > timerCaptor.getAllValues().get(0));
    }
}
