package com.yongsa;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import static org.junit.jupiter.api.Assertions.*;

/** Asset handoff contract consumed by the follow-up weapon renderer. */
class HeroAttachmentAssetTest {
    private JsonValue manifest() throws Exception {
        return new JsonReader().parse(Files.readString(Path.of("assets/data/hero-motion.json")));
    }

    @Test void allSixtyPosesHaveSourceSpaceRightHandAnchors() throws Exception {
        var root = manifest();
        int count = 0;
        for (String action : new String[]{"walk", "roll"}) for (var row : root.get(action)) for (var frame : row) {
            float[] hand = frame.get("hand").asFloatArray();
            int[] rect = frame.get("rect").asIntArray();
            assertEquals(2, hand.length);
            assertTrue(Float.isFinite(hand[0]) && Float.isFinite(hand[1]));
            assertTrue(hand[0] >= rect[0] && hand[0] < rect[0]+rect[2]);
            assertTrue(hand[1] >= rect[1] && hand[1] < rect[1]+rect[3]);
            assertEquals("right", frame.getString("handSide"));
            if (frame.getBoolean("handVisible")) {
                var source = root.get("sources").get(frame.getString("source"));
                var im = ImageIO.read(Path.of("assets",source.getString("path")).toFile());
                assertTrue((im.getRGB(Math.round(hand[0]),Math.round(hand[1])) >>> 24) >= 128,
                        action + " hand is outside painted glove: " + hand[0] + "," + hand[1]);
            }
            count++;
        }
        assertEquals(60, count);
    }

    @Test void eightSwordsHaveTransparentPaddingAndMeasuredGrip() throws Exception {
        var swords = manifest().get("swords");
        assertEquals(8, swords.size);
        for (String family : new String[]{"blackiron","bloodmoon","skyfall","holy"})
            for (String suffix : new String[]{"","_awakened"}) {
                var s = swords.get("sword_" + family + suffix);
                assertNotNull(s);
                var path = Path.of("assets",s.getString("path"));
                var im = ImageIO.read(path.toFile());
                assertEquals(256, im.getWidth()); assertEquals(256, im.getHeight());
                assertTrue(im.getColorModel().hasAlpha());
                assertEquals(s.getString("sha256"), HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))));
                float[] grip = s.get("grip").asFloatArray();
                assertEquals(2, grip.length);
                assertTrue((im.getRGB(Math.round(grip[0]), Math.round(grip[1])) >>> 24) >= 128, "Grip misses handle");
                for (int i=0;i<256;i++) {
                    assertEquals(0, im.getRGB(i,0)>>>24); assertEquals(0, im.getRGB(i,255)>>>24);
                    assertEquals(0, im.getRGB(0,i)>>>24); assertEquals(0, im.getRGB(255,i)>>>24);
                }
                assertEquals(31.9f, s.getFloat("visibleLengthPixels")/s.getFloat("pixelsPerWorld"), .15f);
            }
    }
}
