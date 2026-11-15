package com.yongsa.map;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.utils.GdxNativesLoader;
import java.nio.file.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReviewedMaskDecodeTest {
    @Test void realGameDecoderPreservesBlackAndWhiteSilhouettePixels() throws Exception {
        GdxNativesLoader.load();
        var data=ReviewedOcclusion.parse(Files.readString(Path.of("assets/data/reviewed-occlusion.json")));
        int transparent=0,opaque=0;
        for(var scene:data.scenes.values())for(var piece:scene.pieces) {
            var file=Path.of("assets",piece.mask).toFile();
            var expected=ImageIO.read(file);
            var actual=new Pixmap(new FileHandle(file));
            try {
                for(int y=0;y<actual.getHeight();y+=Math.max(1,actual.getHeight()/15))
                    for(int x=0;x<actual.getWidth();x+=Math.max(1,actual.getWidth()/15)) {
                        // These PNGs store linear mask coverage in their gray sample.
                        // getRGB converts Java's gray color space to sRGB (29 -> 95),
                        // which is inappropriate for an alpha/coverage value.
                        int value=expected.getRaster().getSample(x,y,0);
                        assertEquals(value,ReviewedForegrounds.maskCoverage(actual.getPixel(x,y)),piece.mask+" @ "+x+","+y);
                        if(value==0)transparent++;else if(value==255)opaque++;
                    }
            } finally { actual.dispose(); }
        }
        assertTrue(transparent>100,"Outside silhouettes must not be repainted as solid rectangles");
        assertTrue(opaque>100,"Solid object pixels must still occlude");
    }

    @Test void coverageWorksForAlphaRgbAndRgbaMasks() {
        assertEquals(0,ReviewedForegrounds.maskCoverage(0xffffff00)); // black in Alpha format
        assertEquals(0,ReviewedForegrounds.maskCoverage(0x000000ff)); // black in RGB format
        assertEquals(255,ReviewedForegrounds.maskCoverage(0xffffffff));
        assertEquals(128,ReviewedForegrounds.maskCoverage(0xffffff80));
        assertEquals(128,ReviewedForegrounds.maskCoverage(0x808080ff));
        assertEquals(64,ReviewedForegrounds.maskCoverage(0x80808080));
    }
}
