package com.yongsa.map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Disposable;
import com.yongsa.map.*;
import java.util.*;

/** New artwork, drawn once over both original scenes, before actors and locked gates. */
final class PaintedJoints implements Disposable {
    static final class Front {
        final Texture texture;
        final float x,y,width,height,depth;
        Front(Texture texture,float x,float y,float width,float height,float depth) {
            this.texture=texture;this.x=x;this.y=y;this.width=width;this.height=height;this.depth=depth;
        }
        void draw(SpriteBatch batch) {batch.setColor(Color.WHITE);batch.draw(texture,x,y,width,height);}
    }
    private static final class Painting {
        final PaintedJointLayout.Joint joint;
        final int index;
        Texture texture;
        final List<Front> fronts=new ArrayList<>();
        Painting(PaintedJointLayout.Joint joint,int index){this.joint=joint;this.index=index;}
        String key(){return "joint:"+joint.id;}
    }
    private final List<Painting> paintings=new ArrayList<>();
    private final MapLoader loader;
    private Set<String> layerScenes=Set.of();
    void setLayerScenes(Set<String> scenes){layerScenes=Set.copyOf(scenes);}

    PaintedJoints(WorldMap map, MapLoader loader) {
        this.loader=loader;
        if(Boolean.getBoolean("yongsa.disablePaintedJoints"))return;
        for(var j:map.paintedJoints)paintings.add(new Painting(j,paintings.size()));
    }

    /** 이 사각형에 걸치는 이음 그림만 (순서 유지). 밖에 있는 그림은 투명도 계산에서 늘 1 이라 빼도 결과가 같다. */
    List<PaintedJointLayout.Joint> overlapping(float x0,float y0,float x1,float y1) {
        var result=new ArrayList<PaintedJointLayout.Joint>();
        for(var p:paintings)if(p.joint.visible(x0,y0,x1,y1))result.add(p.joint);
        return result;
    }

    void draw(SpriteBatch batch,float x0,float y0,float x1,float y1) {
        batch.setColor(Color.WHITE);
        for(var p:paintings)if(p.joint.visible(x0,y0,x1,y1)) {
            load(p);float[] r=p.joint.rect;batch.draw(p.texture,r[0],r[1],r[2],r[3]);
        }
    }

    /**
     * 다가가는 이음 그림은 미리 계산해 두고(near), 아주 멀어진 것만 내린다(keep 밖).
     * 예전에는 화면 밖 512 만 벗어나도 내렸다가 돌아오면 다시 계산해서, 길목을 오갈 때마다 멈췄다.
     * 전체 맵을 돌아도 고해상도 그림을 모두 들고 있지는 않도록 keep 밖은 계속 내린다.
     */
    void prefetch(PrefetchZones zones) {
        for(var p:paintings) {
            float[] r=p.joint.rect;
            if(!zones.keep(r[0],r[1],r[0]+r[2],r[1]+r[3])) {unload(p);loader.drop(p.key());continue;}
            if(p.texture!=null || !zones.near(r[0],r[1],r[0]+r[2],r[1]+r[3]))continue;
            loader.request(p.key(),()->bake(p));
            if(loader.ready(p.key()) && loader.spendUpload())load(p);
        }
    }

    List<Front> fronts(float x0,float y0,float x1,float y1) {
        var result=new ArrayList<Front>();
        for(var p:paintings)if(p.joint.visible(x0,y0,x1,y1)){load(p);result.addAll(p.fronts);}
        return result;
    }

    private void load(Painting p) {
        if(p.texture!=null)return;
        Baked b=loader.take(p.key(),()->bake(p));
        try {
            var j=p.joint;
            BakeDigest.add("joint:"+j.id,b.main);
            p.texture=texture(b.main);
            for(int i=0;i<b.parts.size();i++) {
                float[] f=b.partInfo.get(i);
                BakeDigest.add("jointfront:"+j.id+":"+p.fronts.size(),b.parts.get(i));
                p.fronts.add(new Front(texture(b.parts.get(i)),f[0],f[1],f[2],f[3],f[4]));
            }
        } finally {b.dispose();}
    }

    /** 다른 스레드에서 해도 되는 계산: 그림을 읽어 가장자리 투명도를 넣고, 앞쪽 가림 조각을 잘라 둔다. */
    private Baked bake(Painting p) {
        Baked b=new Baked();
        var j=p.joint;Pixmap input=new Pixmap(Gdx.files.internal(j.image));
        Pixmap src=new Pixmap(input.getWidth(),input.getHeight(),Pixmap.Format.RGBA8888);
        src.setBlending(Pixmap.Blending.None);src.drawPixmap(input,0,0);input.dispose();
        b.main=src;
        try {
            int w=src.getWidth(),h=src.getHeight();var pixels=src.getPixels();
            for(int y=0;y<h;y++)for(int x=0;x<w;x++) {
                int i=(y*w+x)*4+3;
                int a=pixels.get(i)&255;
                if(a==0)continue; // 투명한 곳은 계산해도 0
                pixels.put(i,(byte)Math.round(a*j.alpha(j.x((x+.5f)/w),j.y((y+.5f)/h))));
            }
            boolean hasAuthoredLayers=layerScenes.contains("joints/"+j.id);
            // 이 그림보다 뒤에 그려지는 이음 그림 중 겹치는 것만 (나머지는 곱해도 1)
            var later=new ArrayList<PaintedJointLayout.Joint>();
            for(int k=p.index+1;k<paintings.size();k++) {
                var o=paintings.get(k).joint;
                if(o.visible(j.rect[0],j.rect[1],j.rect[0]+j.rect[2],j.rect[1]+j.rect[3]))later.add(o);
            }
            for(var mask:j.foregrounds) {
                if(hasAuthoredLayers && !mask.supplemental)continue;
                int x0=(int)(mask.minX()*w),y0=(int)(mask.minY()*h);
                int x1=Math.min(w,(int)Math.ceil(mask.maxX()*w)),y1=Math.min(h,(int)Math.ceil(mask.maxY()*h));
                Pixmap cut=new Pixmap(x1-x0,y1-y0,Pixmap.Format.RGBA8888);
                b.parts.add(cut);
                cut.setBlending(Pixmap.Blending.None);
                for(int y=y0;y<y1;y++)for(int x=x0;x<x1;x++) {
                    int pixel=src.getPixel(x,y);
                    if((pixel&255)==255 && mask.contains((x+.5f)/w,(y+.5f)/h)) {
                        float opacity=1,wx=j.x((x+.5f)/w),wy=j.y((y+.5f)/h);
                        for(var o:later)opacity*=1-o.alpha(wx,wy);
                        cut.drawPixel(x-x0,y-y0,(pixel&0xffffff00)|Math.round(255*opacity));
                    }
                }
                b.partInfo.add(new float[]{j.x(x0/(float)w),j.y(y1/(float)h),
                        (x1-x0)*j.rect[2]/w,(y1-y0)*j.rect[3]/h,j.y(mask.depth)});
            }
        } catch(RuntimeException e) {b.dispose();throw e;}
        return b;
    }
    private static Texture texture(Pixmap pixels) {
        Texture t=new Texture(pixels,true);t.setFilter(Texture.TextureFilter.MipMapLinearLinear,Texture.TextureFilter.Linear);return t;
    }
    private static void unload(Painting p) {
        if(p.texture!=null){p.texture.dispose();p.texture=null;}
        for(var f:p.fronts)f.texture.dispose();
        p.fronts.clear();
    }
    public void dispose() {
        for(var p:paintings){unload(p);loader.drop(p.key());}
        // A worker finishing a cancelled view may still read the immutable painting order.
    }
}
