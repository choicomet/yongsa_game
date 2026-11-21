package com.yongsa.map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.yongsa.gfx.Art;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Disposable;
import com.yongsa.map.*;
import java.util.*;

/** Painted architecture and the sill mark open rooms; the complete barrier rises in combat. */
final class RoomGates implements Disposable {
    record Part(float depth, Runnable draw) {}
    private final WorldMap map;
    private final Art art;
    private final Map<String, Texture> textures = new HashMap<>();
    private final Map<Door, Float> closure = new IdentityHashMap<>();
    private record SideSill(float nearX,float nearY,float farX,float farY) {}
    private final Map<Door,SideSill> sideSills=new IdentityHashMap<>();
    private Texture thresholdTexture;
    private TextureRegion threshold;

    private final PaintedDoorways doorways;

    RoomGates(WorldMap map, PaintedDoorways doorways, Art art) {
        this.art=art;
        this.doorways=doorways;
        this.map=map;
        for(var r:map.rooms)for(var d:r.doors)closure.put(d,d.isClosed()?1f:0f);
    }

    void update(float dt) {
        for(var r:map.rooms)for(var d:r.doors) {
            float old=closure.getOrDefault(d,d.isClosed()?1f:0f),target=d.isClosed()?1:0;
            float duration=target>old?.42f:.38f;
            closure.put(d,MathUtils.clamp(old+Math.signum(target-old)*Math.min(Math.abs(target-old),Math.max(0,dt)/duration),0,1));
        }
    }

    private String key(Door d) {
        if(d.dead() || d.room.scene==null || d.room.type==RoomType.TUNNEL)return null;
        String theme=d.room.dungeonId;
        if(d.room.type==RoomType.CAMP)theme=d.link.other(d.room).dungeonId;
        if(theme==null || !List.of("LEFT","RIGHT","TOP").contains(theme))return null;
        return "sprites/room-gate-"+(frontFacing(d)?"":"side-")+theme.toLowerCase(Locale.ROOT)+".png";
    }

    // The dungeon ends of cave transitions are painted as frontal arches even when
    // walking through them changes the hero's horizontal map coordinate.
    private static boolean frontFacing(Door d) {
        return d.horizontal() || (d.link != null && d.link.transition);
    }

    private static boolean timberMouth(Door d) {
        return "mine-mob-c".equals(d.room.scene.id) && "east".equals(d.name) && d.link.transition;
    }

    private static float span(Door d,float extra) {
        return (d.halfWidth*2+extra)*(timberMouth(d)?1.52f:1f);
    }

    private static float centerX(Door d) {
        if (timberMouth(d)) return d.gateX() - d.dirX*29;
        return d.gateX() + (!d.horizontal() && frontFacing(d) ? d.dirX * d.halfWidth * .28f : 0);
    }

    private static float sillY(Door d) {
        if (timberMouth(d)) return d.gateY()-5;
        return d.gateY() + (d.horizontal() ? -10f : frontFacing(d) ? 5f : 0f);
    }

    private SideSill measureSide(Door d) {
        // Trace the painted floor diagonally. A vertical collision cross-section is
        // longer than the visible bridge between its parapets.
        float tilt=d.dirX<0?-.70f:.70f,near=-d.halfWidth,far=d.halfWidth;
        for (var joint:map.paintedJoints) {
            if (!joint.room.equals(d.room.id) || Math.abs(joint.door[0]-d.footX)>.02f
                    || Math.abs(joint.door[1]-d.footY)>.02f) continue;
            if (!joint.allowsFoot(d.gateX(),d.gateY(),0)) break;
            for(int sign=-1;sign<=1;sign+=2) {
                float distance=0;
                for(float step=2;step<=2*d.halfWidth+map.tileSize;step+=2) {
                    float off=step*sign,x=d.gateX()+tilt*off,y=d.gateY()+off;
                    if(!joint.owns(x,y) || !joint.allowsFoot(x,y,0)) break;
                    distance=step;
                }
                if(distance>5) {
                    if(sign<0) near=-distance; else far=distance;
                }
            }
            break;
        }
        return new SideSill(d.gateX()+tilt*near,d.gateY()+near,d.gateX()+tilt*far,d.gateY()+far);
    }

    boolean supports(Door d) {return key(d)!=null;}

    /** A shallow permanent sill, drawn underneath actors and both gate states. */
    void drawThresholds(SpriteBatch batch,float x0,float y0,float x1,float y1) {
        if(threshold==null) {
            Pixmap p=new Pixmap(Gdx.files.internal("sprites/room-threshold.png"));
            int left=p.getWidth(),top=p.getHeight(),right=0,bottom=0;
            for(int y=0;y<p.getHeight();y++)for(int x=0;x<p.getWidth();x++)
                if((p.getPixel(x,y)&255)>16){left=Math.min(left,x);right=Math.max(right,x);top=Math.min(top,y);bottom=Math.max(bottom,y);}
            thresholdTexture=new Texture(p,true);p.dispose();
            thresholdTexture.setFilter(Texture.TextureFilter.MipMapLinearLinear,Texture.TextureFilter.Linear);
            threshold=new TextureRegion(thresholdTexture,left,top,right-left+1,bottom-top+1);
        }

        for(var r:map.rooms)for(var d:r.doors) {
            if(!supports(d)||r.type==RoomType.CAMP||d.footX<x0-256||d.footX>x1+256||d.footY<y0-256||d.footY>y1+256)continue;
            float width=span(d,0),height=Math.min(4,width*threshold.getRegionHeight()/threshold.getRegionWidth());
            float cx=centerX(d),cy=sillY(d);
            float closed=closure.getOrDefault(d,0f);
            batch.setColor(.075f,.055f,.045f,.48f*closed);
            if (frontFacing(d)) batch.draw(art.glow,cx-width*.59f,cy-9,width*1.18f,21);
            batch.setColor(.78f,.76f,.71f,.65f);
            if(timberMouth(d)) {
                float pitch=-d.dirX*.47f;
                float length=width*(float)Math.hypot(1,pitch);
                batch.draw(threshold,cx-length/2,cy-height/2,length/2,height/2,length,height,1,1,
                        MathUtils.atan2(pitch,1)*MathUtils.radiansToDegrees);
            } else if(frontFacing(d)) batch.draw(threshold,cx-width/2,cy-height/2,width,height);
            else {
                // A 90-degree rotated stone strip reads as an upright column.
                // Project a thin sill diagonally across the floor, like the gate's foot line.
                SideSill sill=sideSills.computeIfAbsent(d,this::measureSide);
                float ax=sill.nearX,ay=sill.nearY,bx=sill.farX,by=sill.farY;
                float length=(float)Math.hypot(bx-ax,by-ay);
                float nx=-(by-ay)/length*height/2,ny=(bx-ax)/length*height/2;
                float color=batch.getPackedColor();
                batch.draw(thresholdTexture,new float[]{
                    ax-nx,ay-ny,color,threshold.getU(),threshold.getV2(),
                    ax+nx,ay+ny,color,threshold.getU(),threshold.getV(),
                    bx+nx,by+ny,color,threshold.getU2(),threshold.getV(),
                    bx-nx,by-ny,color,threshold.getU2(),threshold.getV2()},0,20);
            }
        }
        batch.setColor(Color.WHITE);
    }

    List<Part> parts(SpriteBatch batch,float x0,float y0,float x1,float y1) {
        var parts=new ArrayList<Part>();
        for(var r:map.rooms)for(var d:r.doors) {
            if(d.footX<x0-256 || d.footX>x1+256 || d.footY<y0-256 || d.footY>y1+256)continue;
            String key=key(d);if(key==null)continue;
            float close=closure.getOrDefault(d,d.isClosed()?1f:0f);
            if (PaintedDoorways.isCampSeal(d)) {
                if (close>0) parts.add(new Part(d.gateY(), () -> doorways.draw(batch,r,PaintedDoorways.Inset.CAMP_SEAL,close)));
                continue;
            }
            if (r.type==RoomType.CAMP && close<=0) continue;
            Texture texture=textures.computeIfAbsent(key,k->{
                Texture t=new Texture(Gdx.files.internal(k),true);
                t.setFilter(Texture.TextureFilter.MipMapLinearLinear,Texture.TextureFilter.Linear);return t;
            });
            boolean side=!frontFacing(d);
            float width=span(d,map.tileSize*.35f), height=MathUtils.clamp(width*.41f,30,63);
            float cx=centerX(d), cy=sillY(d);
            float slope=timberMouth(d)?-d.dirX*.47f:0;
            SideSill sill=side?sideSills.computeIfAbsent(d,this::measureSide)
                    :new SideSill(cx-width*.42f,cy-width*.42f*slope,cx+width*.42f,cy+width*.42f*slope);
            boolean tower=key.endsWith("top.png"), iron=key.endsWith("right.png");
            float jamb=side?(tower?.25f:.214f):tower?.22f:iron?.129f:.162f;
            float sign=Math.signum(sill.farX-sill.nearX);
            float jambWidth=Math.min(width*.25f,side?20:25);
            float lx=sill.nearX+sign*jambWidth/2,rx=sill.farX-sign*jambWidth/2;
            float ly=sill.nearY,ry=sill.farY;
            // Jambs are rigid and sorted at their own feet. On a diagonal bridge
            // only the span between them follows the perspective of the floor.
            parts.add(new Part(ly,()->panel(batch,texture,lx-sign*jambWidth,ly,lx,ly,height,0,jamb,0,1,1,1,side)));
            parts.add(new Part(ry,()->panel(batch,texture,rx,ry,rx+sign*jambWidth,ry,height,1-jamb,1,0,1,1,1,side)));
            if (close<=0) continue;
            float amount=close*close*(3-2*close);
            int slices=side || slope!=0?12:2;
            for (int i=0;i<slices;i++) {
                float start=i/(float)slices,end=(i+1)/(float)slices;
                float u=start,v=end,top=0,bottom=1,heightScale=1,foldA=0,foldB=0;
                if (tower) {
                    if (start<.5f) v=Math.min(end,amount*.5f);
                    else u=Math.max(start,1-amount*.5f);
                    if (v<=u) continue;
                } else if (iron) {
                    top=1-amount;
                    heightScale=amount;
                } else {
                    // Each timber leaf swings from its own post.
                    float hinge=start<.5f?0:1;
                    float angle=(1-amount)*MathUtils.PI/2;
                    foldA=Math.abs(u-hinge)*MathUtils.sin(angle)*width*.31f;
                    foldB=Math.abs(v-hinge)*MathUtils.sin(angle)*width*.31f;
                }
                float hinge=start<.5f?0:1;
                float projection=tower || iron?1:MathUtils.cos((1-amount)*MathUtils.PI/2);
                float a=MathUtils.lerp(hinge,u,projection),b=MathUtils.lerp(hinge,v,projection);
                float ax=MathUtils.lerp(lx,rx,a),bx=MathUtils.lerp(lx,rx,b);
                float ay=MathUtils.lerp(ly,ry,a)+foldA,by=MathUtils.lerp(ly,ry,b)+foldB;
                float srcA=MathUtils.lerp(jamb,1-jamb,u),srcB=MathUtils.lerp(jamb,1-jamb,v);
                float v0=top,v1=bottom,h=heightScale,alpha=tower?.72f+.28f*amount:1f;
                parts.add(new Part((ay+by)/2,()->panel(batch,texture,ax,ay,bx,by,height,srcA,srcB,v0,v1,h,alpha,side)));
            }
        }
        return parts;
    }

    private static void panel(SpriteBatch batch,Texture texture,float ax,float ay,float bx,float by,
                              float height,float u0,float u1,float top,float bottom,float fraction,float alpha,boolean side) {
        float color=Color.toFloatBits(.92f,.88f,.84f,alpha);
        float low=height*(1-fraction);
        float[] quad={ax,ay+low,color,u0,bottom, ax,ay+height,color,u0,top,
                      bx,by+height,color,u1,top, bx,by+low,color,u1,bottom};
        if (side) {
            // Side paintings already contain the top faces and foreshortening.
            // Register each slice to its painted foot, then clip the falling teeth locally.
            for (int i=0;i<4;i++) {
                boolean left=i<2, base=i==0 || i==3;
                float u=left?u0:u1, y=left?ay:by;
                float foot=.99f-.47f*MathUtils.clamp((u-.14f)/.72f,0,1);
                float scale=height/.57f;
                float clipAt=foot-.57f;
                float v=base?1:(fraction>=.999f?0:Math.max(0,clipAt+(1-fraction)*.57f));
                float wy=y+(foot-v)*scale+(1-fraction)*height;
                // Teeth cannot draw through the guide lintel on their way down.
                quad[i*5+1]=wy;
                quad[i*5+4]=v;
            }
        }
        batch.draw(texture,quad,0,quad.length);
    }

    public void dispose() {for(var t:textures.values())t.dispose();textures.clear();if(thresholdTexture!=null)thresholdTexture.dispose();}
}
