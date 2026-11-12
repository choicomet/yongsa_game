package com.yongsa;

import com.yongsa.world.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReviewedLayerTest {
    private ReviewedOcclusion data() throws Exception {
        return ReviewedOcclusion.parse(Files.readString(Path.of("assets/data/reviewed-occlusion.json")));
    }

    @Test void everyReviewedMaskFitsItsUnscaledArtworkAndHasVisiblePixels() throws Exception {
        var data=data();
        assertEquals(46,data.scenes.size());
        assertTrue(data.scenes.keySet().stream().anyMatch(k->k.startsWith("passages/")));
        for(var entry:data.scenes.entrySet()) {
            var scene=entry.getValue();var art=ImageIO.read(Path.of("assets",scene.image).toFile());
            assertEquals(art.getWidth(),scene.size[0],entry.getKey());
            assertEquals(art.getHeight(),scene.size[1],entry.getKey());
            for(var piece:scene.pieces) {
                var mask=ImageIO.read(Path.of("assets",piece.mask).toFile());
                assertEquals(piece.rect[2],mask.getWidth());assertEquals(piece.rect[3],mask.getHeight());
                boolean painted=false;
                for(int y=0;y<mask.getHeight()&&!painted;y++)for(int x=0;x<mask.getWidth();x++)
                    if((mask.getRGB(x,y)&255)!=0){painted=true;break;}
                assertTrue(painted,piece.mask);
                assertEquals(1f-piece.left(scene.size[0],false)-piece.rect[2]/(float)scene.size[0],
                        piece.left(scene.size[0],true),.000001f,"Mirror crop and original picture together");
            }
        }
    }

    @Test void cartAndLampUseFootDepthRatherThanAlwaysCoveringActors() throws Exception {
        var data=data();
        for(String key:List.of("joints/entry-L3-L4","rooms/citadel-ore-b","rooms/village"))
            assertTrue(data.scenes.get(key).pieces.stream().anyMatch(p->!p.always()),key);
        var mine=data.scenes.get("rooms/mine");
        assertTrue(mine.pieces.stream().anyMatch(p->!p.always()&&Math.abs(p.depth-.603f)<.001f));
    }

    @Test void barePathBesideMineFenceIsNotRepaintedAboveTheHero() throws Exception {
        var scene=data().scenes.get("joints/exit-L1-L2");
        // Actual open lane immediately right of the rock cluster, not a brush-centre assertion.
        for(float[] uv:new float[][]{{.50f,.50f},{.49f,.48f},{.53f,.54f}}) {
            int x=(int)(uv[0]*scene.size[0]),y=(int)(uv[1]*scene.size[1]);
            for(var p:scene.pieces) {
                int px=x-p.rect[0],py=y-p.rect[1];
                if(px<0||py<0||px>=p.rect[2]||py>=p.rect[3])continue;
                var mask=ImageIO.read(Path.of("assets",p.mask).toFile());
                assertEquals(0,mask.getRGB(px,py)&255,"Bare floor must not clip the hero: "+Arrays.toString(uv));
            }
        }
    }

    @Test void reviewedPostFootingsBlockButTheirRearLandingsRemainOpen() {
        var map=TestData.map();for(var l:map.links)l.gateClosed=false;
        var j=map.paintedJoints.stream().filter(p->p.id.equals("exit-R1-R2")).findFirst().orElseThrow();
        assertTrue(map.free(j.x(.5944f),j.y(.2089f),9,6),"Landing behind the tall column");
        assertFalse(j.allowsFoot(j.x(.630f),j.y(.479f),0),"Stone footing");
        for(var c:map.corridors)if(c.scene.id.equals("tower-stair-bend")) {
            assertTrue(map.free(c.sceneX(.3583f),c.sceneY(.4263f),9,6),"Stair approach behind the pillar");
            assertFalse(c.allowsFoot(c.sceneX(.335f),c.sceneY(.475f),0),"Pillar footing");
        }
        for(var r:map.rooms)if(r.scene!=null&&r.scene.id.equals("tower"))
            assertTrue(map.free(r.sceneX(.28f),r.sceneY(.345f),9,6),"Paving has no baked obstacle here");
    }

    @Test void malformedCropCannotReadOutsideTheSource() {
        assertThrows(IllegalArgumentException.class,()->ReviewedOcclusion.parse("""
            {"version":1,"scenes":{"rooms/test":{"image":"scene.png","size":[10,10],
            "pieces":[{"mask":"occlusion/review/a.png","rect":[8,0,5,5],"mode":"behind","depth":0.5}]}}}
            """));
    }
}
