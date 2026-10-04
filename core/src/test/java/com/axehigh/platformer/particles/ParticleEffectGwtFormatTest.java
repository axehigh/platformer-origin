package com.axehigh.platformer.particles;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.ParticleEffect;
import com.badlogic.gdx.graphics.g2d.ParticleEmitter;
import org.junit.Test;

import java.io.*;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Parses every shipped {@code .p} effect with a reader whose {@link BufferedReader#markSupported()}
 * is {@code false}, exactly as libGDX behaves on the GWT/web target.
 *
 * <p>libGDX's {@code IndependentScaledNumericValue.load()} can only skip a missing {@code independent:}
 * key by marking and resetting the reader, which GWT's emulated {@code java.io} does not support —
 * so an effect file saved by an older Particle Editor loads fine on desktop and throws
 * "old invalid format" in the browser. Parsing the real asset files with a non-markable reader
 * catches that at build time instead of at runtime.
 */
public class ParticleEffectGwtFormatTest {

    private static final Charset UTF_8 = Charset.forName("UTF-8");

    /** Walks up from the working dir to find the repo root holding assets/particles. */
    private static final File BASE = findBase();

    /** GWT emulation: {@code java.io.BufferedReader.markSupported()} always answers false. */
    private static class GwtLikeReader extends BufferedReader {
        GwtLikeReader(Reader reader) {
            super(reader);
        }

        @Override
        public boolean markSupported() {
            return false;
        }
    }

    private static File findBase() {
        File dir = new File(System.getProperty("user.dir"));
        for (int i = 0; i < 8 && dir != null; i++) {
            File candidate = new File(dir, "assets/particles");
            if (candidate.isDirectory()) return candidate;
            dir = dir.getParentFile();
        }
        throw new IllegalStateException("assets/particles not found from " + System.getProperty("user.dir"));
    }

    private static List<File> effectFiles(File dir) {
        List<File> files = new ArrayList<>();
        collect(dir, files);
        if (files.isEmpty()) throw new IllegalStateException("no .p effects found in " + dir);
        return files;
    }

    private static void collect(File dir, List<File> out) {
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isDirectory()) {
                collect(child, out);
            } else if (child.getName().endsWith(".p")) {
                out.add(child);
            }
        }
    }

    @Test
    public void everyEffectParsesWithoutReaderMarkSupport() throws IOException {
        for (File file : effectFiles(BASE)) {
            GwtLikeReader reader = new GwtLikeReader(new InputStreamReader(new FileInputStream(file), UTF_8));
            ParticleEmitter emitter = new ParticleEmitter();
            try {
                emitter.load(reader);
            } catch (IOException e) {
                throw new AssertionError(file.getName() + " does not parse on the GWT/web target "
                    + "(re-save it with the current Particle Editor): " + e.getMessage(), e);
            } finally {
                reader.close();
            }
            assertTrue(file.getName() + " yielded an emitter with no name", emitter.getName() != null);
        }
    }

    /** Image paths must stay relative: the effect's own folder is the only valid base on every target. */
    @Test
    public void everyEffectUsesRelativeImagePaths() throws IOException {
        for (File file : effectFiles(BASE)) {
            ParticleEffect effect = new ParticleEffect();
            effect.loadEmitters(new FileHandle(file));
            for (ParticleEmitter emitter : effect.getEmitters()) {
                for (String imagePath : emitter.getImagePaths()) {
                    assertFalse(file.getName() + " / " + emitter.getName() + " bakes in an absolute"
                        + " image path: " + imagePath, imagePath.startsWith("/") || imagePath.contains(":"));
                    String name = new File(imagePath.replace('\\', '/')).getName();
                    assertTrue(file.getName() + " references a missing image " + name,
                        new File(file.getParentFile(), name).isFile());
                }
            }
        }
    }

    /** The {@code independent: false} keys added for GWT must not change how the effect parses. */
    @Test
    public void ghostEffectKeepsItsEmittersAndLifeValues() throws IOException {
        File ghost = new File(BASE, "ghost/Early Grey Ghost.p");
        assertTrue("expected " + ghost + " to exist", ghost.isFile());
        ParticleEffect effect = new ParticleEffect();
        effect.loadEmitters(new FileHandle(ghost));

        assertEquals("ghost effect should expose 4 emitters", 4, effect.getEmitters().size);
        String[] expectedNames = {"background", "sparks", "outer_glow", "inner_glow"};
        for (int i = 0; i < expectedNames.length; i++) {
            ParticleEmitter emitter = effect.getEmitters().get(i);
            assertEquals(expectedNames[i], emitter.getName());
            ParticleEmitter.IndependentScaledNumericValue life =
                (ParticleEmitter.IndependentScaledNumericValue) emitter.getLife();
            assertFalse(emitter.getName() + " life should be shared, not per-particle",
                life.isIndependent());
            assertTrue(emitter.getName() + " life should still span the file's range",
                life.getHighMax() >= life.getHighMin());
        }
    }
}
