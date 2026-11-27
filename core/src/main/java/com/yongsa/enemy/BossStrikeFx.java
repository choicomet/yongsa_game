package com.yongsa.enemy;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.yongsa.model.GameEvent;
import java.util.ArrayList;
import java.util.List;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.yongsa.gfx.Art;
import com.yongsa.gfx.DepthSink;

/** Short-lived marks from confirmed impacts. Geometry is stable as it fades. */
final class BossStrikeFx {
    private static final float LIFE = .46f;
    private record Mark(GameEvent hit, float start) {}
    private final List<Mark> marks = new ArrayList<>();
    private final TextureRegion[] impacts = new TextureRegion[6], casts = new TextureRegion[6];
    private final TextureRegion[] rubble = new TextureRegion[4];
    private float time;

    BossStrikeFx(Art art) {
        for (int i=0;i<4;i++) rubble[i]=art.cell("sprites/fx/golem-impact.png",2,2,i);
        for (int i=0;i<6;i++) {
            impacts[i]=art.cell("sprites/fx/boss-impacts.png",3,2,i);
            casts[i]=art.cell("sprites/fx/boss-casts.png",3,2,i);
        }
    }

    TextureRegion crescent() { return impacts[1]; }

    void update(float dt) {
        time += dt;
        marks.removeIf(mark -> time - mark.start >= LIFE);
    }
    void clear() { marks.clear(); }
    void hit(GameEvent hit) {
        if (hit.text != null && !"eye".equals(hit.text)) marks.add(new Mark(hit,time));
    }

    void collect(SpriteBatch batch, DepthSink out) {
        for (Mark mark : marks) out.add(mark.hit.y, () -> paint(batch,mark));
    }

    void drawCast(SpriteBatch b, Hazard h) {
        float r=h.radius, p=h.progress();
        if (h.kind==Hazard.Kind.POOL) {
            float fade=Math.min(1,h.age/.35f)*Math.min(1,h.life/.8f);
            b.setColor(1,1,1,fade);
            b.draw(casts[5],h.x-r*1.06f,h.y-r*1.65f,r*2.12f,r*3.3f);
        } else if ("boulder".equals(h.style) || "rock".equals(h.style)) {
            float size=r*("boulder".equals(h.style)?1.08f:.76f);
            float bottom=h.y+(1-p*p)*116-size*.13f;
            b.setColor(1,1,1,1);
            b.draw(casts[3],h.x-size/2,bottom,size/2,size/2,size,size,1,1,(1-p)*-29);
        }
        b.setColor(Color.WHITE);
    }

    private void paint(SpriteBatch b, Mark mark) {
        GameEvent e=mark.hit;
        float p=MathUtils.clamp((time-mark.start)/LIFE,0,1);
        float rise=1-(1-p)*(1-p)*(1-p), fade=1-MathUtils.clamp((p-.48f)/.52f,0,1);
        float x=e.x,y=e.y,r=e.size;
        b.setColor(1,1,1,fade);
        switch(e.text) {
            case "pound", "boulder", "rock", "punch" -> {
                float size=r*("pound".equals(e.text)?1.92f:"boulder".equals(e.text)?1.65f:"punch".equals(e.text)?1.4f:1.20f);
                if ("punch".equals(e.text)) { x=(e.x+e.dirX)/2; y=(e.y+e.dirY)/2; } // 충격파 한 칸의 가운데
                int frame=p<.15f?0:p<.38f?1:p<.64f?2:3;
                b.draw(rubble[frame],x-size/2,y-size*.19f,size,size);
            }
            case "quake" -> {
                // Each tuft sits on the travelling rim, leaving the safe center visible.
                int count=MathUtils.clamp((int)(r/12),6,18);
                float radius=r*(.91f+.08f*rise), size=Math.min(25,r*.25f)*(1+.24f*rise);
                for (int i=0;i<count;i++) {
                    float angle=(i+.23f)*MathUtils.PI2/count;
                    b.draw(impacts[0],x+radius*MathUtils.cos(angle)-size/2,
                            y+radius*MathUtils.sin(angle)-size*.18f,size,size);
                }
            }
            case "spin", "leap" -> {
                float size=r*(1.65f+.47f*rise), angle=165-rise*245;
                b.draw(impacts[1],x-size/2,y-size/2,size/2,size/2,size,size,1,1,angle);
                if ("spin".equals(e.text)) {
                    b.setColor(.85f,.64f,.60f,.52f*fade);
                    b.draw(impacts[1],x-size/2,y-size/2,size/2,size/2,size,size,.92f,.92f,angle+170);
                }
            }
            case "bolt" -> {
                float height=Math.max(112,r*3.15f);
                b.setColor(1,1,1,fade*(p<.23f || p>.32f?1:.45f));
                b.draw(impacts[2],x-height*.45f,y-height*.17f,height*.92f,height);
            }
            case "spear", "whip", "cage" -> {
                float dx=e.dirX-x,dy=e.dirY-y;
                float length=(float)Math.hypot(dx,dy), angle=MathUtils.atan2(dy,dx)*MathUtils.radiansToDegrees;
                int index="spear".equals(e.text)?0:"whip".equals(e.text)?1:2;
                float width=length*(.80f+.24f*Math.min(1,p*6));
                float height=Math.max(32,r*6)*(1-.24f*p);
                b.draw(casts[index],x,y-height/2,0,height/2,width,height,1,1,angle);
            }
            case "bone", "tentacle", "brand", "flame" -> {
                TextureRegion sprite=switch(e.text) {
                    case "bone" -> impacts[3]; case "tentacle" -> impacts[4];
                    case "brand" -> impacts[5]; default -> casts[4];
                };
                float width=r*1.95f, height=width*(.42f+.70f*Math.min(1,rise*2)-.17f*p);
                float sway="tentacle".equals(e.text)?-11*MathUtils.sin(p*MathUtils.PI2):0;
                b.draw(sprite,x-width/2,y-height*.14f,width/2,height*.14f,width,height,1,1,sway);
            }
            default -> { }
        }
        b.setColor(Color.WHITE);
    }

    static void arc(ShapeRenderer s,float x,float y,float radius,float start,float sweep,float width) {
        int n=Math.max(4,(int)Math.ceil(Math.abs(sweep)/9));
        float ax=x+MathUtils.cosDeg(start)*radius,ay=y+MathUtils.sinDeg(start)*radius;
        for(int i=1;i<=n;i++){
            float a=start+sweep*i/n;
            float bx=x+MathUtils.cosDeg(a)*radius,by=y+MathUtils.sinDeg(a)*radius;
            s.rectLine(ax,ay,bx,by,width);ax=bx;ay=by;
        }
    }

}
