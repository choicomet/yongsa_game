package com.yongsa;

import com.yongsa.map.*;
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

    @Test void separatePostsWithEqualFootDepthRemainIndependent() throws Exception {
        var mine=data().scenes.get("rooms/mine");
        var posts=mine.pieces.stream().filter(p->Math.abs(p.depth-.835f)<.001f).toList();
        assertEquals(2,posts.size(),"Two posts are not one wide cutout");
        assertNotEquals(posts.get(0).id,posts.get(1).id);
        assertTrue(posts.get(0).rect[0]+posts.get(0).rect[2]<posts.get(1).rect[0]);
        for(var scene:data().scenes.values()) {
            var ids=new HashSet<String>();
            for(var p:scene.pieces)assertTrue(p.id!=null&&ids.add(p.id),"Every object has its own stable ID");
        }
    }

    @Test void mineAnnotationBrushDoesNotEraseHeroOnBareFloor() throws Exception {
        var scene=data().scenes.get("passages/mine-traverse");
        // Bare paving to the left of the rock/post, previously included by the broad brush.
        for(float[] uv:new float[][]{{.38f,.55f},{.571f,.676f}}) {
            int x=(int)(uv[0]*scene.size[0]),y=(int)(uv[1]*scene.size[1]);
            for(var p:scene.pieces) {
                int px=x-p.rect[0],py=y-p.rect[1];
                if(px<0||py<0||px>=p.rect[2]||py>=p.rect[3])continue;
                var mask=ImageIO.read(Path.of("assets",p.mask).toFile());
                assertEquals(0,mask.getRGB(px,py)&255,"Floor beside prop: "+Arrays.toString(uv));
            }
        }
        assertTrue(scene.pieces.stream().noneMatch(ReviewedOcclusion.Piece::always));
    }

    @Test void diagonalRailingUsesLocalFootingAndClampsAtItsEnds() throws Exception {
        var p=data().scenes.get("passages/tower-stair-bend").pieces.stream()
                .filter(x->"stair-parapet".equals(x.id)).findFirst().orElseThrow();
        float left=p.depthLine[0],right=p.depthLine[2];
        assertFalse(p.always());
        assertTrue(p.depthAt(left)<p.depthAt(right));
        assertEquals((p.depthLine[1]+p.depthLine[3])/2,p.depthAt((left+right)/2),.000001f);
        assertEquals(p.depthLine[1],p.depthAt(-1));
        assertEquals(p.depthLine[3],p.depthAt(2));
        var malformed=new ReviewedOcclusion();malformed.version=1;
        var scene=data().scenes.get("passages/tower-stair-bend");malformed.scenes.put("passages/test",scene);
        p=scene.pieces.stream().filter(x->x.depthLine!=null).findFirst().orElseThrow();
        p.depthLine=new float[]{.8f,.4f,.2f,.5f};
        assertThrows(IllegalArgumentException.class,()->ReviewedOcclusion.parse(new com.google.gson.Gson().toJson(malformed)));
    }
}
