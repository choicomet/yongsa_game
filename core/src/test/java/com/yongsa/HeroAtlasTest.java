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

    @Test void rollBodyKeepsTheStandingCharactersScaleInEveryDirection() throws Exception {
        var data = new HeroAtlasData(manifest());
        var images = new HashMap<String, BufferedImage>();
        for (var e : data.sources.entrySet())
            images.put(e.getKey(), ImageIO.read(Path.of("assets",e.getValue().path()).toFile()));
        for (int row = 0; row < 5; row++) {
            double standing = bodyArea(data.clips.get("walk")[row][2], images);
            for (var pose : data.clips.get("roll")[row]) {
                double areaRatio = bodyArea(pose, images) / standing;
                // Tucking changes the silhouette; the painted body should retain its visual mass.
                assertTrue(areaRatio >= .64 && areaRatio <= 1.28, "Roll row " + row + " area ratio " + areaRatio);
            }
        }
    }

    private static double bodyArea(HeroAtlasData.Frame frame, HashMap<String, BufferedImage> images) {
        var image = images.get(frame.source());
        int opaquePixels = 0;
        for (int y = frame.y(); y < frame.y() + frame.height(); y++)
            for (int x = frame.x(); x < frame.x() + frame.width(); x++)
                if ((image.getRGB(x,y) >>> 24) > 100) opaquePixels++;
        return opaquePixels / (frame.pixelsPerWorld() * frame.pixelsPerWorld());
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
        assertThrows(IllegalArgumentException.class,()->new HeroAtlasData(json.replaceFirst("\"width\": [0-9]+","\"width\": 1")));
    }

    @Test void walkInterpolationReachesAdjacentPoseWithoutChangingFootAnchor() {
        for (int i=0; i<6; i++) {
            assertEquals(i,HeroMotion.walkFrame(i/12f));
            assertEquals(0,HeroMotion.walkFraction(i/12f),.00001f);
            assertEquals(.5f,HeroMotion.walkFraction((i+.5f)/12f),.00001f);
        }
        assertEquals(0,HeroMotion.walkFrame(.5f));
    }

    @Test void dedicatedWalkKeepsTwoCyclesPerWalkTime() throws Exception {
        HeroAtlasData data = new HeroAtlasData(manifest());
        for (int[] cycle : data.walkCycle) {
            for (int i=0; i<cycle.length; i++)
                assertEquals(cycle[i], HeroMotion.walkFrame(i/(HeroMotion.WALK_CYCLES*cycle.length) + .001f, cycle));
            assertEquals(cycle[0], HeroMotion.walkFrame(1/HeroMotion.WALK_CYCLES + .001f, cycle));
        }
        var invalid=new com.badlogic.gdx.utils.JsonReader().parse(manifest());
        invalid.get("walkCycle").get(0).get(0).set(new com.badlogic.gdx.utils.JsonValue(6));
        assertThrows(IllegalArgumentException.class,()->new HeroAtlasData(
                invalid.toJson(com.badlogic.gdx.utils.JsonWriter.OutputType.json)));
    }

    @Test void sixWalkPosesHaveDistinctFootworkAndStableHeight() throws Exception {
        var data=new HeroAtlasData(manifest());
        var frames=data.clips.get("walk");
        for(int row=0;row<5;row++) {
            assertArrayEquals(new int[]{0,1,2,3,4,5},data.walkCycle[row]);
            String source=frames[row][0].source();
            var image=ImageIO.read(Path.of("assets",data.sources.get(source).path()).toFile());
            var hashes=new java.util.HashSet<String>();
            for(int col:data.walkCycle[row]) {
                var frame=frames[row][col];
                assertEquals(source,frame.source(),"Do not mix different painting sheets inside one walk");
                int top=frame.y()+frame.height(),bottom=frame.y();
                var digest=MessageDigest.getInstance("SHA-256");
                for(int y=frame.y();y<frame.y()+frame.height();y++) for(int x=frame.x();x<frame.x()+frame.width();x++) {
                    int pixel=image.getRGB(x,y);
                    if((pixel>>>24)>32){top=Math.min(top,y);bottom=Math.max(bottom,y);}
                    // Distinguish the legs, not just changing scarf/hand pixels.
                    if(y>=frame.y()+frame.height()/2) for(int shift=24;shift>=0;shift-=8)
                        digest.update((byte)(pixel>>>shift));
                }
                assertEquals(58.1f,(bottom+1-top)/frame.pixelsPerWorld(),.4f,"Direction/phase must not shrink");
                hashes.add(HexFormat.of().formatHex(digest.digest()));
            }
            assertEquals(6,hashes.size(),"Six different lower-body poses required");
        }
    }

    @Test void frontIdleRetainsAllFourApprovedFrames() throws Exception {
        var data = new HeroAtlasData(manifest());
        String[] approved = {
                "a3a9573cbd75d5c34a201ac9d4735446fb6c131b8b61851d2a34bf585201ac62",
                "7f8c1e10dca7979bcc2cf078c8f63054141c0637e1c366e02449ea8a42405a01",
                "092634b11701767701ab70488b08f5c22b3713728fa70bf2263e4b249fe582ba",
                "16967edcf4d237a701cc64dc1547593b62ea2737207f3bd74a15f0115bcd62d1"};
        var idle = data.clips.get("idle")[0];
        assertEquals(approved.length, idle.length);
        var image = ImageIO.read(Path.of("assets", data.sources.get("idle").path()).toFile());
        for (int col = 0; col < idle.length; col++) {
            var f = idle[col];
            var digest = MessageDigest.getInstance("SHA-256");
            // The cell has one transparent pixel of packing padding on every side.
            for (int y = f.y() + 1; y < f.y() + f.height() - 1; y++) {
                for (int x = f.x() + 1; x < f.x() + f.width() - 1; x++) {
                    int argb = image.getRGB(x,y);
                    for (int shift : new int[]{16,8,0,24}) digest.update((byte)(argb >>> shift));
                }
            }
            assertEquals(approved[col], HexFormat.of().formatHex(digest.digest()));
        }
    }
}
