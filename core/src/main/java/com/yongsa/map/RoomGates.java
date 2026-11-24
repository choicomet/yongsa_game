package com.yongsa.map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.yongsa.gfx.Art;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Disposable;
import com.yongsa.map.*;
import java.util.*;

/** Painted architecture and the sill mark open rooms; the complete barrier rises in combat. */
final class RoomGates implements Disposable {
    record Part(float depth, Runnable draw) {}
    private static final float[] CRYSTAL_EDGES={0,.18f,.385f,.61f,.82f,1};
    private final WorldMap map;
    private final Art art;
    private final Map<String, Texture> textures = new HashMap<>();
    private final Map<Door, Float> closure = new IdentityHashMap<>();
    private record SideSill(float nearX,float nearY,float farX,float farY) {}
    private final Map<Door,SideSill> sideSills=new IdentityHashMap<>();
    private final Map<Door,SideSill> paintedSills=new IdentityHashMap<>();

    private final float[] grilleQuad = new float[20];
    private final Map<Door,float[][]> grilleOpenings = new IdentityHashMap<>();
    private final PaintedDoorways doorways;

    RoomGates(WorldMap map, PaintedDoorways doorways, Art art) {
        this.art=art;
        this.doorways=doorways;
        this.map=map;
        for(var r:map.rooms)for(var d:r.doors)closure.put(d,d.isClosed()?1f:0f);
        for (var room : map.rooms) if (room.scene != null) {
            for (var portal : room.scene.portals) if (portal.grilleOpening != null) {
                for (var door : room.doors) if (door.name.equals(portal.id)) grilleOpenings.put(door,portal.grilleOpening);
            }
        }
        for (var joint:map.paintedJoints) {
            if (joint.gateSill==null) continue;
            var feet=joint.gateSill;
            for (var door:map.room(joint.room).doors)
                if (Math.abs(joint.door[0]-door.footX)<.02f && Math.abs(joint.door[1]-door.footY)<.02f)
                    paintedSills.put(door,new SideSill(joint.x(feet[0]),joint.y(feet[1]),joint.x(feet[2]),joint.y(feet[3])));
        }
    }

    void update(float dt) {
        for(var r:map.rooms)for(var d:r.doors) {
            float old=closure.getOrDefault(d,d.isClosed()?1f:0f),target=d.isClosed()?1:0;
            float duration=target>old?.42f:.38f;
            closure.put(d,MathUtils.clamp(old+Math.signum(target-old)*Math.min(Math.abs(target-old),Math.max(0,dt)/duration),0,1));
        }
    }

    private String key(Door d) {
        if(d.dead() || d.room.scene==null || (d.room.type==RoomType.TUNNEL && !grilleOpenings.containsKey(d)))return null;
        String theme=d.room.dungeonId;
        if(d.room.type==RoomType.CAMP)theme=d.link.other(d.room).dungeonId;
        if(theme==null || !List.of("LEFT","RIGHT","TOP").contains(theme))return null;
        return "sprites/room-gate-"+theme.toLowerCase(Locale.ROOT)+".png";
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
        float preferred = d.dirX < 0 ? -.95f : .95f;
        for (var joint : map.paintedJoints) {
            if (!joint.room.equals(d.room.id) || Math.abs(joint.door[0]-d.footX) > .02f
                    || Math.abs(joint.door[1]-d.footY) > .02f) continue;
            if (!joint.allowsFoot(d.gateX(),d.gateY(),0)) break;
            SideSill first = traceSill(d,joint,preferred);
            SideSill opposite = traceSill(d,joint,-preferred);
            if (first == null && opposite == null) break;
            if (first == null) return opposite;
            if (opposite == null) return first;
            // Cross the lane between its banks; do not point the gate along a staircase.
            return opposite.farY-opposite.nearY < .90f*(first.farY-first.nearY) ? opposite : first;
        }
        return new SideSill(d.gateX()-preferred*d.halfWidth,d.gateY()-d.halfWidth,
                d.gateX()+preferred*d.halfWidth,d.gateY()+d.halfWidth);
    }

    private SideSill traceSill(Door d, PaintedJointLayout.Joint joint, float tilt) {
        float[] ends = new float[2];
        float limit = 2*d.halfWidth+map.tileSize;
        for (int i=0; i<2; i++) {
            int sign=i==0?-1:1;
            for (float step=2; step<=limit; step+=2) {
                float off=sign*step, x=d.gateX()+tilt*off, y=d.gateY()+off;
                if (!joint.owns(x,y) || !joint.allowsFoot(x,y,0)) break;
                ends[i]=off;
            }
            if (Math.abs(ends[i])<6 || Math.abs(ends[i])+2>=limit) return null;
        }
        return new SideSill(d.gateX()+tilt*ends[0],d.gateY()+ends[0],d.gateX()+tilt*ends[1],d.gateY()+ends[1]);
    }

    boolean supports(Door d) {return key(d)!=null;}

    /** A shallow permanent sill, drawn underneath actors and both gate states. */
    void drawThresholds(SpriteBatch batch,float x0,float y0,float x1,float y1) {
        for (var room:map.rooms) for (var d:room.doors) {
            if (!supports(d) || grilleOpenings.containsKey(d) || room.type==RoomType.CAMP || d.footX<x0-256 || d.footX>x1+256
                    || d.footY<y0-256 || d.footY>y1+256) continue;
            SideSill sill = sill(d);
            batch.setColor(.07f,.045f,.055f,.52f);
            for (int end=0;end<2;end++) {
                float x=end==0?sill.nearX:sill.farX, y=end==0?sill.nearY:sill.farY;
                batch.draw(art.glow,x-14,y-4,28,9);
            }
        }
        batch.setColor(Color.WHITE);
    }

    private SideSill sill(Door d) {
        if (paintedSills.containsKey(d)) return paintedSills.get(d);
        if (!frontFacing(d)) return sideSills.computeIfAbsent(d,this::measureSide);
        float reach=span(d,map.tileSize*.35f)*.42f, cx=centerX(d), cy=sillY(d);
        float slope=timberMouth(d)?-d.dirX*.47f:0;
        return new SideSill(cx-reach,cy-reach*slope,cx+reach,cy+reach*slope);
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
            var opening=grilleOpenings.get(d);
            if (opening!=null) {
                if (close>0) {
                    float depth=r.sceneY((opening[0][2]+opening[opening.length-1][2])*.5f);
                    parts.add(new Part(depth,()->drawInsetGrille(batch,texture,r,opening,close)));
                }
                continue;
            }
            boolean side=!frontFacing(d), tower=key.endsWith("top.png"), iron=key.endsWith("right.png");
            float width=span(d,map.tileSize*.35f);
            float height=MathUtils.clamp(width*(tower?.30f:.34f),28,52);
            SideSill sill=sill(d);
            float sign=Math.signum(sill.farX-sill.nearX);
            float jamb=tower?.18f:iron?.165f:.15f;
            float post=Math.min(Math.abs(sill.farX-sill.nearX)*.25f,side?13:19);
            float lx=sill.nearX+sign*post/2, rx=sill.farX-sign*post/2, ly=sill.nearY, ry=sill.farY;
            parts.add(new Part(ly,()->panel(batch,texture,lx-sign*post,ly,lx,ly,height,0,jamb,0,1,0,1)));
            parts.add(new Part(ry,()->panel(batch,texture,rx,ry,rx+sign*post,ry,height,1-jamb,1,0,1,0,1)));
            float amount=close*close*(3-2*close);
            if (tower) {
                // The five crystals are separate solids. Each rises vertically from its own socket.
                float[] cuts=CRYSTAL_EDGES;
                for(int i=1;i<cuts.length;i++) {
                    float a=cuts[i-1], b=cuts[i], mid=(a+b)*.5f;
                    float xA=MathUtils.lerp(lx,rx,a), xB=MathUtils.lerp(lx,rx,b);
                    float yA=MathUtils.lerp(ly,ry,a), yB=MathUtils.lerp(ly,ry,b), foot=(yA+yB)*.5f;
                    float srcA=MathUtils.lerp(jamb,1-jamb,a), srcB=MathUtils.lerp(jamb,1-jamb,b);
                    // Keep each socket level with its crystal even on a steeply projected bridge.
                    float center=(xA+xB)*.5f, base=(xB-xA)*.49f;
                    parts.add(new Part(foot,()->panel(batch,texture,center-base,foot,center+base,foot,height,
                            srcA,srcB,.90f,1,-.03f,.075f)));
                    float rise=MathUtils.clamp((amount-.19f*Math.abs(2*mid-1))/.84f,0,1);
                    float radius=(xB-xA)*.44f;
                    if(rise>0) parts.add(new Part(foot,()->panel(batch,texture,center-radius,foot,center+radius,foot,
                            height,srcA,srcB,0,.925f*rise,.075f,.075f+.925f*rise)));
                }
                continue;
            }
            int slices=side || timberMouth(d)?16:2;
            for(int i=0;i<slices;i++) {
                float u=i/(float)slices, v=(i+1f)/slices;
                float ax=MathUtils.lerp(lx,rx,u), bx=MathUtils.lerp(lx,rx,v);
                float ay=MathUtils.lerp(ly,ry,u), by=MathUtils.lerp(ly,ry,v);
                float srcA=MathUtils.lerp(jamb,1-jamb,u), srcB=MathUtils.lerp(jamb,1-jamb,v);
                float depth=(ay+by)/2;
                if (iron) {
                    // The guide stays attached to both piers. Only the iron teeth travel.
                    parts.add(new Part(depth,()->panel(batch,texture,ax,ay,bx,by,height,srcA,srcB,0,.211f,.789f,1)));
                    if (amount>0) parts.add(new Part(depth,()->panel(batch,texture,ax,ay,bx,by,height,
                            srcA,srcB,1-.789f*amount,1,.789f*(1-amount),.789f)));
                } else if (amount>0) {
                    float hinge=u<.5f?0:1, angle=(1-amount)*MathUtils.PI/2;
                    float scale=MathUtils.cos(angle), fold=MathUtils.sin(angle)*width*.28f;
                    float a=MathUtils.lerp(hinge,u,scale), b=MathUtils.lerp(hinge,v,scale);
                    float xA=MathUtils.lerp(lx,rx,a), xB=MathUtils.lerp(lx,rx,b);
                    float yA=MathUtils.lerp(ly,ry,a)+Math.abs(u-hinge)*fold;
                    float yB=MathUtils.lerp(ly,ry,b)+Math.abs(v-hinge)*fold;
                    parts.add(new Part((yA+yB)/2,()->panel(batch,texture,xA,yA,xB,yB,height,srcA,srcB,0,1,0,1)));
                }
            }
        }
        return parts;
    }

    /** Slide the original ironwork behind the painted arch, clipping at the inner stones. */
    private void drawInsetGrille(SpriteBatch batch,Texture texture,Room room,float[][] outline,float close) {
        float crown=outline[0][1], floor=outline[0][2];
        for (float[] column : outline) {
            crown=Math.min(crown,column[1]);
            floor=Math.max(floor,column[2]);
        }
        float height=floor-crown;
        float lift=(1-(close*close*(3-2*close)))*height;
        float left=outline[0][0], width=outline[outline.length-1][0]-left;
        float color=Color.toFloatBits(.82f,.77f,.73f,1);
        for (int i=1;i<outline.length;i++) {
            float[] a=outline[i-1], b=outline[i];
            float clearanceA=a[2]-lift-a[1], clearanceB=b[2]-lift-b[1];
            if (clearanceA<=0 && clearanceB<=0) continue;
            float from=clearanceA<0 ? clearanceA/(clearanceA-clearanceB) : 0;
            float to=clearanceB<0 ? clearanceA/(clearanceA-clearanceB) : 1;
            grilleEdge(room,a,b,from,lift,crown,height,left,width,color,0,5);
            grilleEdge(room,a,b,to,lift,crown,height,left,width,color,15,10);
            batch.draw(texture,grilleQuad,0,grilleQuad.length);
        }
    }

    private void grilleEdge(Room room,float[] a,float[] b,float t,float lift,float crown,float height,
                            float left,float width,float color,int bottomVertex,int topVertex) {
        float u=MathUtils.lerp(a[0],b[0],t);
        float top=MathUtils.lerp(a[1],b[1],t), bottom=MathUtils.lerp(a[2],b[2],t)-lift;
        // The masonry jambs are already in the painting. Only sample the iron grille.
        float x=room.sceneX(u), texU=.19f+.62f*(u-left)/width;
        grilleVertex(bottomVertex,x,room.sceneY(bottom),color,texU,(bottom+lift-crown)/height);
        grilleVertex(topVertex,x,room.sceneY(top),color,texU,(top+lift-crown)/height);
    }

    private void grilleVertex(int i,float x,float y,float color,float u,float v) {
        grilleQuad[i]=x;
        grilleQuad[i+1]=y;
        grilleQuad[i+2]=color;
        grilleQuad[i+3]=u;
        grilleQuad[i+4]=MathUtils.clamp(v,0,1);
    }

    private static void panel(SpriteBatch batch,Texture texture,float ax,float ay,float bx,float by,
                              float height,float u0,float u1,float top,float bottom,float low,float high) {
        float color=Color.toFloatBits(.91f,.87f,.84f,1);
        float[] quad={ax,ay+low*height,color,u0,bottom, ax,ay+high*height,color,u0,top,
                bx,by+high*height,color,u1,top, bx,by+low*height,color,u1,bottom};
        batch.draw(texture,quad,0,quad.length);
    }

    public void dispose() {for(var t:textures.values())t.dispose();textures.clear();}
}
