package com.yongsa;

import com.yongsa.combat.Facing;
import com.yongsa.gfx.HeroAtlasData;
import com.yongsa.gfx.HeroMotion;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import static org.junit.jupiter.api.Assertions.*;

class HeroAtlasTest {
    String manifest() throws Exception { return Files.readString(Path.of("assets/data/hero-motion.json")); }

    @Test void packagedArtworkMatchesMeasuredNativeSources() throws Exception {
        var data = new HeroAtlasData(manifest());
        for (var source : data.sources.values()) {
            var file = Path.of("assets",source.path());
            var image = ImageIO.read(file.toFile());
            assertEquals(source.width(),image.getWidth());
            assertEquals(source.height(),image.getHeight());
            assertTrue(image.getColorModel().hasAlpha());
            assertEquals(source.sha256(), HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file))));
        }
    }

    @Test void everyPosePreservesAspectAndGroundAcrossDirectionsAndActions() throws Exception {
        var data = new HeroAtlasData(manifest());
        for (var clip : data.clips.values()) for (var row : clip) for (var f : row) {
            float[] p = f.placement();
            float sx = p[2]*HeroMotion.WIDTH/f.width(), sy = p[3]*HeroMotion.HEIGHT/f.height();
            assertEquals(sx,sy,.000001f,"No nonuniform stretch");
            assertEquals(.5f,p[0]+(f.footX()-f.x())/f.width()*p[2],.000001f);
            assertEquals(1-HeroMotion.FOOT_OFFSET/HeroMotion.HEIGHT,
                    p[1]+(f.footY()-f.y())/f.height()*p[3],.000001f);
        }
    }

    @Test void noPoseCutsThroughVisibleArtOrIncludesAnEmptyCell() throws Exception {
        var data = new HeroAtlasData(manifest());
        var images = new HashMap<String, BufferedImage>();
        for (var e : data.sources.entrySet()) images.put(e.getKey(),ImageIO.read(Path.of("assets",e.getValue().path()).toFile()));
        for (var clip : data.clips.values()) for (var row : clip) for (var f : row) {
            var im = images.get(f.source());
            long visible = 0;
            for (int y=f.y(); y<f.y()+f.height(); y++) for (int x=f.x(); x<f.x()+f.width(); x++) {
                int alpha = im.getRGB(x,y)>>>24;
                if (alpha>32) visible++;
                if (x==f.x() || y==f.y() || x==f.x()+f.width()-1 || y==f.y()+f.height()-1)
                    assertTrue(alpha<=32,"Clipped silhouette: "+f.source()+" at "+x+","+y);
            }
            assertTrue(visible>1000,"Missing pose");
        }
    }

    @Test void diagonalRowsAreDistinctAndMirrorTheirOwnOrientation() {
        assertEquals(3,HeroAtlasData.row(Facing.DOWN_RIGHT));
        assertEquals(3,HeroAtlasData.row(Facing.DOWN_LEFT));
        assertEquals(4,HeroAtlasData.row(Facing.UP_RIGHT));
        assertEquals(4,HeroAtlasData.row(Facing.UP_LEFT));
        assertEquals(2,HeroAtlasData.row(Facing.RIGHT));
        assertEquals(0,HeroAtlasData.row(Facing.DOWN));
        assertEquals(1,HeroAtlasData.row(Facing.UP));
    }

    @Test void rejectBadScaleAndSourceBoundsInsteadOfSilentlyClipping() throws Exception {
        String json = manifest();
        assertThrows(IllegalArgumentException.class,()->new HeroAtlasData(json.replaceFirst("\"pixelsPerWorld\": [0-9.]+","\"pixelsPerWorld\": 0")));
        assertThrows(IllegalArgumentException.class,()->new HeroAtlasData(json.replaceFirst("\"width\": 1672","\"width\": 100")));
    }

    @Test void walkInterpolationReachesAdjacentPoseWithoutChangingFootAnchor() {
        for (int i=0; i<6; i++) {
            assertEquals(i,HeroMotion.walkFrame(i/12f));
            assertEquals(0,HeroMotion.walkFraction(i/12f),.00001f);
            assertEquals(.5f,HeroMotion.walkFraction((i+.5f)/12f),.00001f);
        }
        assertEquals(0,HeroMotion.walkFrame(.5f));
    }
}
