package com.yongsa.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Disposable;
import com.yongsa.world.*;
import java.util.*;

/** Repaints original art using reviewed silhouettes; handles room/corridor flips and joint seams. */
final class ReviewedForegrounds implements Disposable {
    static final class Front {
        final Texture texture;
        final float x, y, width, height, depth;
        final boolean flip, always;
        Front(Texture texture, float x, float y, float width, float height, float depth, boolean flip, boolean always) {
            this.texture=texture;this.x=x;this.y=y;this.width=width;this.height=height;
            this.depth=depth;this.flip=flip;this.always=always;
        }
        void draw(SpriteBatch batch) {
            batch.setColor(Color.WHITE);
            batch.draw(texture,x,y,width,height,0,0,texture.getWidth(),texture.getHeight(),flip,false);
        }
    }
    private static final class Placement {
        final String key;
        final float x,y,w,h;
        final boolean flip;
        final Room room;
        final Corridor corridor;
        final PaintedJointLayout.Joint joint;
        List<Front> fronts;
        Placement(String key,float x,float y,float w,float h,boolean flip,Room room,Corridor corridor,PaintedJointLayout.Joint joint) {
            this.key=key;this.x=x;this.y=y;this.w=w;this.h=h;this.flip=flip;
            this.room=room;this.corridor=corridor;this.joint=joint;
        }
        boolean visible(float x0,float y0,float x1,float y1) {return x<x1 && x+w>x0 && y<y1 && y+h>y0;}
        void unload() {if(fronts!=null){for(var f:fronts)f.texture.dispose();fronts=null;}}
    }
    private final ReviewedOcclusion data;
    private final WorldMap map;
    private final PassagePainter passages;
    private final List<Placement> placements=new ArrayList<>();
    private final Map<Room,RoomJoinMask> roomJoins=new LinkedHashMap<>();

    ReviewedForegrounds(WorldMap map,PassagePainter passages) {
        this.map=map;this.passages=passages;
        var file=Gdx.files.internal("data/reviewed-occlusion.json");
        data=file.exists()?ReviewedOcclusion.parse(file.readString("UTF-8")):new ReviewedOcclusion();
        for(var r:map.rooms)if(r.scene!=null)roomJoins.put(r,new RoomJoinMask(r,map.tileSize));
        for(var r:map.rooms)if(r.scene!=null && has("rooms/"+r.scene.id))
            placements.add(new Placement("rooms/"+r.scene.id,r.originX,r.originY,r.widthPx,r.heightPx,r.flip,r,null,null));
        if(passages.active)for(var c:map.corridors)if(c.scene!=null && has("passages/"+c.scene.id))
            placements.add(new Placement("passages/"+c.scene.id,c.originX,c.originY,c.widthPx,c.heightPx,c.flip,null,c,null));
        if(!Boolean.getBoolean("yongsa.disablePaintedJoints"))for(var j:map.paintedJoints)if(has("joints/"+j.id))
            placements.add(new Placement("joints/"+j.id,j.rect[0],j.rect[1],j.rect[2],j.rect[3],false,null,null,j));
        passages.joints.setReviewedScenes(data.scenes.keySet());
    }

    boolean has(String key) {return data.scenes.containsKey(key);}

    List<Front> visible(float x0,float y0,float x1,float y1) {
        var result=new ArrayList<Front>();
        for(var p:placements) {
            if(!p.visible(x0-512,y0-512,x1+512,y1+512))p.unload();
            if(!p.visible(x0,y0,x1,y1))continue;
            if(p.room!=null && Boolean.getBoolean("yongsa.disableSceneOcclusion"))continue;
            if(p.fronts==null)load(p);
            result.addAll(p.fronts);
        }
        return result;
    }

    private void load(Placement p) {
        var scene=data.scenes.get(p.key);
        Pixmap source=new Pixmap(Gdx.files.internal(scene.image));
        var fronts=new ArrayList<Front>();
        try {
            if(source.getWidth()!=scene.size[0] || source.getHeight()!=scene.size[1])
                throw new IllegalArgumentException("Reviewed artwork size changed: "+p.key);
            if(p.room!=null && passages.active)source=passages.joinForeground(source,p.room);
            if(p.corridor!=null)source=PassagePainter.featherPassage(source,p.corridor);
            for(var piece:scene.pieces) {
                int[] r=piece.rect;
                Pixmap mask=new Pixmap(Gdx.files.internal(piece.mask));
                Pixmap cut=new Pixmap(r[2],r[3],Pixmap.Format.RGBA8888);
                try {
                    if(mask.getWidth()!=r[2] || mask.getHeight()!=r[3])
                        throw new IllegalArgumentException("Reviewed mask size changed: "+piece.mask);
                    cut.setBlending(Pixmap.Blending.None);
                    for(int y=0;y<r[3];y++)for(int x=0;x<r[2];x++) {
                        int alpha=mask.getPixel(x,y)>>>24;
                        if(alpha==0)continue;
                        int pixel=source.getPixel(r[0]+x,r[1]+y);
                        // Never repaint feathered blends: that duplicates the seam over actors.
                        if((pixel&255)!=255)continue;
                        float u=(r[0]+x+.5f)/scene.size[0],v=(r[1]+y+.5f)/scene.size[1];
                        float wx=p.x+(p.flip?1-u:u)*p.w,wy=p.y+(1-v)*p.h;
                        if(!ownsPixel(p,wx,wy))continue;
                        cut.drawPixel(x,y,(pixel&0xffffff00)|alpha);
                    }
                    Texture texture=new Texture(cut,true);
                    texture.setFilter(Texture.TextureFilter.MipMapLinearLinear,Texture.TextureFilter.Linear);
                    fronts.add(new Front(texture,p.x+piece.left(scene.size[0],p.flip)*p.w,
                            p.y+(1f-(r[1]+r[3])/(float)scene.size[1])*p.h,
                            r[2]*p.w/scene.size[0],r[3]*p.h/scene.size[1],
                            p.y+(1-piece.depth)*p.h,p.flip,piece.always()));
                }finally{cut.dispose();mask.dispose();}
            }
            p.fronts=fronts;
        }catch(RuntimeException e){for(var f:fronts)f.texture.dispose();throw e;}
        finally{source.dispose();}
    }

    private boolean ownsPixel(Placement p,float x,float y) {
        if(p.joint!=null && p.joint.alpha(x,y)<1)return false;
        if(!Boolean.getBoolean("yongsa.disablePaintedJoints")) {
            int start=p.joint==null?0:map.paintedJoints.indexOf(p.joint)+1;
            for(int i=start;i<map.paintedJoints.size();i++)
                if(map.paintedJoints.get(i).alpha(x,y)>0)return false;
        }
        if(p.corridor!=null)for(var entry:roomJoins.entrySet()) {
            var r=entry.getKey();
            if(r.scene==null || x<r.originX || y<r.originY || x>r.originX+r.widthPx || y>r.originY+r.heightPx)continue;
            if(entry.getValue().alpha(x,y)>0)return false;
        }
        return true;
    }

    public void dispose(){for(var p:placements)p.unload();}
}
