package com.yongsa.map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Disposable;
import java.util.EnumMap;

/** Insets sampled from the dungeon paintings. All coordinates use the top-left of the artwork. */
final class PaintedDoorways implements Disposable {
    enum Inset {
        MINE("scenes/natural-v1/mine-antechamber.png", .32f,0,.63f,.30f, .39f,.074f,.622f,.356f),
        CITADEL("world/portals/return-door.png", .348f,.276f,.655f,.84f, .456f,.012f,.544f,.166f),
        TOWER("scenes/natural-v1/tower-antechamber.png", .437f,.006f,.519f,.142f, .46f,-.006f,.538f,.147f),
        CAMP_SEAL("scenes/natural-v1/tower-boss-a.png", .46f,.002f,.539f,.141f, .469f,.016f,.56f,.163f);

        final String source;
        final float u0,v0,u1,v1,left,top,right,bottom;
        Inset(String source,float u0,float v0,float u1,float v1,float left,float top,float right,float bottom) {
            this.source=source; this.u0=u0; this.v0=v0; this.u1=u1; this.v1=v1;
            this.left=left; this.top=top; this.right=right; this.bottom=bottom;
        }
    }

    private final EnumMap<Inset,Texture> textures = new EnumMap<>(Inset.class);
    private Texture crown;

    static Inset exitFor(Room room) {
        if (room.scene == null || room.type != RoomType.BOSS) return null;
        return switch (room.scene.id) {
            case "mine-boss-a" -> Inset.MINE;
            case "citadel-boss-a" -> Inset.CITADEL;
            case "tower-boss-a" -> Inset.TOWER;
            default -> null;
        };
    }

    static boolean isCampSeal(Door door) {
        return door.room.scene != null && "central-camp".equals(door.room.scene.id) && "UP".equals(door.name);
    }

    void drawCrown(SpriteBatch batch, Room room) {
        if (crown == null) {
            crown = new Texture(Gdx.files.internal("world/portals/return-door.png"), true);
            crown.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
        }
        // Complete the masonry beyond the upper image boundary, underneath the painted arch.
        float width = .252f * room.widthPx;
        batch.setColor(.58f,.51f,.64f,1);
        batch.draw(crown, room.sceneX(.513f)-width/2, room.sceneY(.037f), width,width*.36f,0,.36f,1,0);
        batch.setColor(Color.WHITE);
    }

    void draw(SpriteBatch batch, Room room, Inset inset, float visibility) {
        if (visibility <= 0) return;
        Texture tex = textures.computeIfAbsent(inset, this::load);
        float x = room.sceneX(inset.left), y = room.sceneY(inset.bottom);
        float width = room.sceneX(inset.right)-x, height = room.sceneY(inset.top)-y;
        batch.setColor(1,1,1,visibility);
        batch.draw(tex,x,y,width,height);
        batch.setColor(Color.WHITE);
    }

    private Texture load(Inset inset) {
        Pixmap source = new Pixmap(Gdx.files.internal(inset.source));
        int x0=Math.round(inset.u0*source.getWidth()), y0=Math.round(inset.v0*source.getHeight());
        int w=Math.round(inset.u1*source.getWidth())-x0, h=Math.round(inset.v1*source.getHeight())-y0;
        Pixmap crop = new Pixmap(w,h,Pixmap.Format.RGBA8888);
        try {
            crop.setBlending(Pixmap.Blending.None);
            for (int y=0;y<h;y++) for (int x=0;x<w;x++) {
                float u=(x+.5f)/w, v=(y+.5f)/h;
                float side=Math.min(u,1-u), end=Math.min(v,1-v);
                float fx=inset==Inset.MINE?.20f:.065f, fy=inset==Inset.MINE?.17f:.095f;
                float alpha=smooth(side/fx)*smooth(end/fy);
                // Match the feather of the room painting at the top of a boss arena.
                if (inset==Inset.CITADEL || inset==Inset.TOWER)
                    alpha*=smooth(MathUtils.lerp(inset.top,inset.bottom,v)/.08f);
                if (inset==Inset.CAMP_SEAL) {
                    float dx=(u-.5f)*2;
                    float arch=.32f*(1-(float)Math.sqrt(Math.max(0,1-dx*dx)));
                    alpha*=smooth((v-arch)/.035f);
                }
                int rgba=source.getPixel(x0+x,y0+y);
                crop.drawPixel(x,y,(rgba & 0xffffff00) | Math.round((rgba & 255)*alpha));
            }
            Texture texture = new Texture(crop,true);
            texture.setFilter(Texture.TextureFilter.MipMapLinearLinear,Texture.TextureFilter.Linear);
            return texture;
        } finally { crop.dispose(); source.dispose(); }
    }

    private static float smooth(float t) {
        t=MathUtils.clamp(t,0,1);
        return t*t*(3-2*t);
    }

    public void dispose() {
        for (Texture texture:textures.values()) texture.dispose();
        if (crown!=null) crown.dispose();
    }
}
