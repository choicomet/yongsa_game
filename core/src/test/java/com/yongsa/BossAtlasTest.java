package com.yongsa;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BossAtlasTest {
    private static final Path ROOT = Path.of("assets/team/jiyong");

    @Test
    void detailFramesKeepTheOriginalGroundLineWithoutTouchingAdjacentCells() throws Exception {
        for (String form : List.of("golem", "lich", "lich2")) {
            BufferedImage original = ImageIO.read(ROOT.resolve("boss_" + form + ".png").toFile());
            for (int page = 0; page < 2; page++) {
                BufferedImage hd = ImageIO.read(ROOT.resolve("hd/" + form + "-" + (page + 1) + ".png").toFile());
                assertEquals(1632, hd.getWidth());
                assertEquals(2040, hd.getHeight());
                for (int i = 0; i < 20; i++) {
                    int frame = page * 20 + i;
                    var old = original.getSubimage(frame % 8 * 256, frame / 8 * 256, 256, 256);
                    var cell = hd.getSubimage(i % 4 * 408, i / 4 * 408, 408, 408);
                    String label = form + " frame " + frame;
                    int bottom = lastOpaqueRow(cell);
                    assertTrue(bottom > 0 && bottom < 407, label);
                    assertEquals(lastOpaqueRow(old), 26 + bottom / 2f, 2.0, label);
                    for (int j = 0; j < 408; j++) {
                        assertEquals(0, cell.getRGB(j, 0) >>> 24, label);
                        assertEquals(0, cell.getRGB(0, j) >>> 24, label);
                        assertEquals(0, cell.getRGB(407, j) >>> 24, label);
                    }
                }
            }
        }
    }

    private static int lastOpaqueRow(BufferedImage frame) {
        for (int y = frame.getHeight() - 1; y >= 0; y--) {
            for (int x = 0; x < frame.getWidth(); x++) {
                if ((frame.getRGB(x, y) >>> 24) > 16) return y;
            }
        }
        return -1;
    }
}
