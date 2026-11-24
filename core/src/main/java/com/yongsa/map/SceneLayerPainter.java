package com.yongsa.map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.Disposable;
import com.yongsa.gfx.DepthSink;
import java.util.*;

/** Draws authored RGBA layers. Source canvases stay full size; only the GPU upload is cropped. */
final class SceneLayerPainter implements Disposable {
    private static final class Cut {
        Texture texture;
        SceneLayers.ObjectLayer object;
        int x,y,w,h,canvasW,canvasH;
        final List<float[]> edges=new ArrayList<>();
    }
    private final SceneLayerPlacements layout;
    private final PassagePainter passages;
    private final Map<SceneLayerPlacements.Placement,List<Cut>> cache=new LinkedHashMap<>();
    private final List<SceneLayerPlacements.Placement> visible=new ArrayList<>();
    private final MapLoader loader;
    SceneLayerPainter(WorldMap map,PassagePainter passages,MapLoader loader) {
        this.layout=map.sceneLayers;this.passages=passages;this.loader=loader;
        passages.joints.setLayerScenes(layout.data.scenes.keySet());
    }
    boolean has(String key){return layout.has(key);}
    boolean hasFront(String key) {
        SceneLayers.Scene scene = layout.data.scenes.get(key);
        return scene != null && scene.front != null;
    }
    void collect(DepthSink out,SpriteBatch batch,float x0,float y0,float x1,float y1) {
        visible.clear();
        for(var p:layout.all) {
            if(!p.visible(x0,y0,x1,y1)||skip(p))continue;
            visible.add(p);
            var cuts=cache.computeIfAbsent(p,this::load);
            for(var c:cuts)if(c.object!=null) {
                int step=c.object.footLine==null?c.w:4;
                for(int sx=0;sx<c.w;sx+=step) {
                    final int start=sx,width=Math.min(step,c.w-sx);
                    float u=(c.x+sx+width*.5f)/c.canvasW;
                    out.add(p.worldY(c.object.footAt(u)),()->draw(batch,p,c,start,width));
                }
            }
        }
    }
    void drawFronts(SpriteBatch batch) {
        for(var p:visible)for(var c:cache.get(p))if(c.object==null)draw(batch,p,c,0,c.w);
    }
    private static void draw(SpriteBatch batch,SceneLayerPlacements.Placement p,Cut c,int sx,int sw) {
        float left=p.flip?1f-(c.x+sx+sw)/(float)c.canvasW:(c.x+sx)/(float)c.canvasW;
        batch.setColor(Color.WHITE);
        batch.draw(c.texture,p.x+left*p.w,p.worldY((c.y+c.h)/(float)c.canvasH),sw*p.w/c.canvasW,c.h*p.h/c.canvasH,
                sx,0,sw,c.h,p.flip,false);
    }
    private boolean skip(SceneLayerPlacements.Placement p) {
        return p.corridor!=null&&!passages.active
                ||p.joint!=null&&Boolean.getBoolean("yongsa.disablePaintedJoints")
                ||p.room!=null&&Boolean.getBoolean("yongsa.disableSceneOcclusion");
    }

    /** 캠프는 순간이동으로 돌아오는 곳이라 늘 준비해 둔다. */
    private static boolean pinned(SceneLayerPlacements.Placement p){return p.room!=null&&p.room.type==RoomType.CAMP;}

    private static String key(SceneLayerPlacements.Placement p){return "layer:"+p.key+"@"+p.x+","+p.y;}

    /**
     * 렉 줄이기: 다가가는 레이어 그림은 미리 계산(near), 아주 멀어진 것만 내린다(keep 밖).
     * 예전에는 화면 밖 512 만 벗어나도 내렸다가 돌아오면 다시 계산해서, 오갈 때마다 멈췄다.
     */
    void prefetch(PrefetchZones zones) {
        var it=cache.entrySet().iterator();
        while(it.hasNext()) {var e=it.next();if(!pinned(e.getKey())&&!zones.keep(e.getKey().x,e.getKey().y,e.getKey().x+e.getKey().w,e.getKey().y+e.getKey().h)){release(e.getValue());it.remove();}}
        for(var p:layout.all) {
            if(skip(p))continue;
            boolean pin=pinned(p);
            if(!pin&&!zones.keep(p.x,p.y,p.x+p.w,p.y+p.h)){loader.drop(key(p));continue;}
            if(cache.containsKey(p)||!pin&&!zones.near(p.x,p.y,p.x+p.w,p.y+p.h))continue;
            loader.request(key(p),()->bake(p));
            if(loader.ready(key(p))&&loader.spendUpload())cache.put(p,load(p));
        }
    }

    private List<Cut> load(SceneLayerPlacements.Placement p) {
        Bake b=loader.take(key(p),()->bake(p));
        List<Cut> cuts=new ArrayList<>();
        try {
            for(var c:b.cuts) {
                BakeDigest.add("layer:"+c.file+":"+p.x+","+p.y+":"+c.cut.x+","+c.cut.y, c.pixels);
                c.cut.texture=new Texture(c.pixels,true);
                c.cut.texture.setFilter(Texture.TextureFilter.MipMapLinearLinear,Texture.TextureFilter.Linear);
                cuts.add(c.cut);
            }
            return cuts;
        }catch(RuntimeException e){release(cuts);throw e;}
        finally{b.dispose();}
    }

    /** 다른 스레드에서 계산한 레이어 조각들 (아직 그래픽에 올리기 전). */
    private static final class Bake implements Disposable {
        static final class Piece {Cut cut;Pixmap pixels;String file;}
        final List<Piece> cuts=new ArrayList<>();
        public void dispose(){for(var c:cuts)c.pixels.dispose();cuts.clear();}
    }

    /** 다른 스레드에서 해도 되는 계산: 그림을 읽고 이 자리에 남길 픽셀만 잘라 둔다 (Texture 는 만들지 않는다). */
    private Bake bake(SceneLayerPlacements.Placement p) {
        Bake b=new Bake();
        try {
            for(var o:p.scene.objects)bakeImage(p,o.image,o,b);
            if(p.scene.front!=null)bakeImage(p,p.scene.front,null,b);
            return b;
        }catch(RuntimeException e){b.dispose();throw e;}
    }
    private void bakeImage(SceneLayerPlacements.Placement p,String file,SceneLayers.ObjectLayer object,Bake b) {
        Pixmap source=new Pixmap(Gdx.files.internal(file));
        try {
            int w=source.getWidth(),h=source.getHeight();
            if(p.corridor!=null)source=PassagePainter.featherPassage(source,p.corridor);
            source.setBlending(Pixmap.Blending.None);
            int x0=w,y0=h,x1=0,y1=0;
            for(int y=0;y<h;y++)for(int x=0;x<w;x++) {
                int rgba=source.getPixel(x,y);
                if((rgba&255)==0)continue;
                // Respect the ground compositor: never repaint another scene's floor
                // or a feathered joint blend on top of actors.
                if((p.corridor!=null&&(rgba&255)!=255)||!layout.owns(p,p.worldX((x+.5f)/w),p.worldY((y+.5f)/h),false)) {
                    source.drawPixel(x,y,0);continue;
                }
                x0=Math.min(x0,x);y0=Math.min(y0,y);x1=Math.max(x1,x+1);y1=Math.max(y1,y+1);
            }
            if(x1<=x0||y1<=y0)return;
            Cut c=new Cut();c.object=object;c.x=x0;c.y=y0;c.w=x1-x0;c.h=y1-y0;c.canvasW=w;c.canvasH=h;
            Pixmap crop=new Pixmap(c.w,c.h,Pixmap.Format.RGBA8888);
            crop.setBlending(Pixmap.Blending.None);crop.drawPixmap(source,0,0,x0,y0,c.w,c.h);
            var piece=new Bake.Piece();piece.cut=c;piece.pixels=crop;piece.file=file;
            b.cuts.add(piece);
            for(int y=y0;y<y1;y++)for(int x=x0;x<x1;x++)if((source.getPixel(x,y)&255)>=128) {
                if(x==0||(source.getPixel(x-1,y)&255)<128)c.edges.add(new float[]{x/(float)w,y/(float)h,x/(float)w,(y+1f)/h});
                if(x==w-1||(source.getPixel(x+1,y)&255)<128)c.edges.add(new float[]{(x+1f)/w,y/(float)h,(x+1f)/w,(y+1f)/h});
                if(y==0||(source.getPixel(x,y-1)&255)<128)c.edges.add(new float[]{x/(float)w,y/(float)h,(x+1f)/w,y/(float)h});
                if(y==h-1||(source.getPixel(x,y+1)&255)<128)c.edges.add(new float[]{x/(float)w,(y+1f)/h,(x+1f)/w,(y+1f)/h});
            }
        }finally{source.dispose();}
    }
    void drawDebug(ShapeRenderer shapes) {
        shapes.begin(ShapeRenderer.ShapeType.Line);
        for(var p:visible)for(var c:cache.get(p)) {
            shapes.setColor(c.object==null?Color.ORANGE:Color.CYAN);
            for(var e:c.edges)shapes.line(p.worldX(e[0]),p.worldY(e[1]),p.worldX(e[2]),p.worldY(e[3]));
            if(c.object==null)continue;
            shapes.setColor(Color.YELLOW);
            if(c.object.footLine==null)shapes.line(p.worldX(c.x/(float)c.canvasW),p.worldY(c.object.foot),p.worldX((c.x+c.w)/(float)c.canvasW),p.worldY(c.object.foot));
            else for(int i=1;i<c.object.footLine.length;i++) {
                var a=c.object.footLine[i-1];var b=c.object.footLine[i];
                shapes.line(p.worldX(a[0]),p.worldY(a[1]),p.worldX(b[0]),p.worldY(b[1]));
            }
            if(c.object.block!=null) {var b=p.blockRect(c.object);shapes.setColor(Color.RED);shapes.rect(b[0],b[1],b[2]-b[0],b[3]-b[1]);}
        }
        shapes.end();
    }
    private static void release(List<Cut> cuts){for(var c:cuts)if(c.texture!=null)c.texture.dispose();}
    public void dispose(){
        for(var p:cache.keySet())loader.drop(key(p));
        for(var p:layout.all)loader.drop(key(p));
        for(var cuts:cache.values())release(cuts);cache.clear();visible.clear();
    }
}
