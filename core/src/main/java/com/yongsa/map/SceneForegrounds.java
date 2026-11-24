package com.yongsa.map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Disposable;
import com.yongsa.map.Room;
import com.yongsa.map.SceneOcclusion;
import java.util.*;

/** Repaint exact source pixels above actors, sorted by each object's foot position. */
final class SceneForegrounds implements Disposable {
    static final class Piece {
        final Texture texture;
        final float left,top,width,height,depth;
        Piece(Texture t,float l,float top,float w,float h,float depth) {
            texture=t;left=l;this.top=top;width=w;height=h;this.depth=depth;
        }
    }
    private static final class Cached {
        final List<Piece> pieces=new ArrayList<>();
        float used;
        void dispose(){for(Piece p:pieces)p.texture.dispose();}
    }
    private final SceneOcclusion data;
    private final PassagePainter passages;
    private final Map<String,Cached> cache=new HashMap<>();

    SceneForegrounds(PassagePainter passages, MapLoader loader) {
        this.passages = passages;
        this.loader = loader;
        var f=Gdx.files.internal("data/scene-occlusion.json");
        data=f.exists()?SceneOcclusion.parse(f.readString("UTF-8")):null;
    }

    private final MapLoader loader;

    List<Piece> pieces(Room room,float time) {
        List<SceneOcclusion.Mask> masks=masks(room);
        if(masks==null)return List.of();
        Cached entry=cache.get(room.id);
        if(entry==null) entry=upload(room,loader.take(key(room),()->bake(room,masks)));
        entry.used=time;
        return entry.pieces;
    }

    private List<SceneOcclusion.Mask> masks(Room room) {
        if(data==null || room.scene==null || Boolean.getBoolean("yongsa.disableSceneOcclusion"))return null;
        return data.scenes.get(room.scene.id);
    }

    private static String key(Room room){return "pieces:"+room.id;}

    /** 가까운 방의 가림 조각을 미리 계산하고, 다 된 것은 (한 프레임에 한 방씩) 올려 둔다. */
    void prefetch(Room room,float time) {
        List<SceneOcclusion.Mask> masks=masks(room);
        if(masks==null)return;
        Cached have=cache.get(room.id);
        if(have!=null){have.used=time;return;}
        loader.request(key(room),()->bake(room,masks));
        if(loader.ready(key(room)) && loader.spendUpload()) upload(room,loader.take(key(room),()->bake(room,masks))).used=time;
    }

    /** 아직 가까운 방: 내리지 않는다. */
    void touch(Room room,float time) {Cached c=cache.get(room.id);if(c!=null)c.used=time;}

    void forget(Room room) {loader.drop(key(room));}

    private Cached upload(Room room,Baked b) {
        Cached entry=new Cached();
        try {
            for(int i=0;i<b.parts.size();i++) {
                float[] f=b.partInfo.get(i);
                Pixmap cut=b.parts.get(i);
                BakeDigest.add("piece:"+room.id+":"+i+":"+(int)f[5]+","+(int)f[6], cut);
                Texture tex=new Texture(cut,true);
                tex.setFilter(Texture.TextureFilter.MipMapLinearLinear,Texture.TextureFilter.Linear);
                entry.pieces.add(new Piece(tex,f[0],f[1],f[2],f[3],f[4]));
            }
        } finally {b.dispose();}
        cache.put(room.id,entry);
        return entry;
    }

    /**
     * 다른 스레드에서 해도 되는 계산: 가림 영역만큼 원본 픽셀을 잘라 둔다.
     * 예전에는 그림 전체(160만 픽셀)에 이음 투명도를 계산한 뒤 잘랐는데, 잘라 낼 칸에서만 계산해도 결과가 같다.
     */
    private Baked bake(Room room,List<SceneOcclusion.Mask> masks) {
        Baked b=new Baked();
        Pixmap source=new Pixmap(Gdx.files.internal(room.scene.image));
        PassagePainter.JoinAlpha join=passages.active?passages.joinAlpha(room,true):null;
        if(passages.active && join==null) source=passages.joinForeground(source,room); // 옛 통로 방식
        try {
            int w=source.getWidth(),h=source.getHeight();
            for(var mask:masks) {
                int x0=(int)Math.floor(mask.minX()*w),y0=(int)Math.floor(mask.minY()*h);
                int x1=Math.min(w,(int)Math.ceil(mask.maxX()*w)),y1=Math.min(h,(int)Math.ceil(mask.maxY()*h));
                Pixmap cut=new Pixmap(x1-x0,y1-y0,Pixmap.Format.RGBA8888);
                b.parts.add(cut);
                cut.setBlending(Pixmap.Blending.None);
                for(int y=y0;y<y1;y++)for(int x=x0;x<x1;x++) {
                    int pixel = source.getPixel(x,y);
                    if ((pixel & 255) != 255) continue;
                    // Feathered edges and doorway joins already blend with the ground.
                    // Repainting them would darken the blend and reveal a rectangular seam.
                    if (join!=null && Math.round(255*join.at(x,y,w,h))!=255) continue;
                    if (mask.contains((x+0.5f)/w,(y+0.5f)/h)) cut.drawPixel(x-x0,y-y0,pixel);
                }
                b.partInfo.add(new float[]{x0/(float)w,y0/(float)h,(x1-x0)/(float)w,(y1-y0)/(float)h,mask.depth,x0,y0});
            }
        } catch(RuntimeException e) {b.dispose();throw e;}
        finally {source.dispose();}
        return b;
    }

    void draw(SpriteBatch batch,Room room,Piece p) {
        float x=room.originX+(room.flip?1f-p.left-p.width:p.left)*room.widthPx;
        float y=room.originY+(1f-p.top-p.height)*room.heightPx;
        batch.setColor(Color.WHITE);
        batch.draw(p.texture,x,y,p.width*room.widthPx,p.height*room.heightPx,
                0,0,p.texture.getWidth(),p.texture.getHeight(),room.flip,false);
    }

    void expire(float time) {
        var it=cache.values().iterator();
        while(it.hasNext()){Cached c=it.next();if(time-c.used>20f){c.dispose();it.remove();}}
    }
    public void dispose(){for(Cached c:cache.values())c.dispose();cache.clear();}
}
