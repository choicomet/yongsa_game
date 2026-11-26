package com.yongsa.map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Disposable;
import com.yongsa.model.GameSession;
import com.yongsa.map.Room;
import com.yongsa.map.RoomType;
import java.util.*;

/** Permanent northern doorway with the same theme barriers used at dungeon entrances. */
final class BossReturnDoors implements Disposable {
    private final GameSession game;
    private Texture arch;
    private Texture towerFrame;
    private final Map<String,Texture> gates = new HashMap<>();
    private final Map<Room,Float> closure = new IdentityHashMap<>();
    BossReturnDoors(GameSession game) {this.game=game;}
    void update(float dt) {
        for(Room room:game.map.rooms) if(room.type==RoomType.BOSS) {
            float target=game.hasPortal(room)?0:1;
            float before=closure.getOrDefault(room,target);
            closure.put(room,before+MathUtils.clamp(target-before,-dt/.55f,dt/.35f));
        }
    }
    float openness(Room room) {
        float open = 1 - closure.getOrDefault(room, game.hasPortal(room) ? 0f : 1f);
        return open * open * (3 - 2 * open);
    }

    /* The masonry belongs to the room and stays lit while the shutter travels. */
    void drawPainted(SpriteBatch batch, Room room, PaintedDoorways.Exit exit, PaintedDoorways doorways) {
        float x = game.portalX(room), sill = game.portalY(room) + 14;
        if (exit == PaintedDoorways.Exit.MINE) {
            doorways.draw(batch, room, PaintedDoorways.Inset.MINE, 1);
            float width = room.widthPx * .082f;
            // The mining tunnel recedes behind its stairs, up to the timber jambs.
            drawBarrier(batch, room, room.sceneX(.5f), room.sceneY(.229f), width, room.heightPx * .131f);
            return;
        }
        if (exit == PaintedDoorways.Exit.TOWER) {
            drawTower(batch, doorways, x, sill, room.widthPx * .183f, openness(room));
            return;
        }
        float width = room.widthPx * .181f;
        drawArch(batch, x, sill, width);
        drawBarrier(batch, room, x, sill, width * .37f, width * .47f);
    }

    private Texture stoneArch() {
        if (arch == null) {
            arch = new Texture(Gdx.files.internal("world/portals/return-door.png"), true);
            arch.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
        }
        return arch;
    }

    private void drawTower(SpriteBatch batch, PaintedDoorways doorways, float x, float sill, float size, float open) {
        if (towerFrame == null) {
            towerFrame = new Texture(Gdx.files.internal("world/portals/tower-return-frame.png"), true);
            towerFrame.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
        }
        // The carved frame masks the moving leaves along the pointed opening.
        float width = size * .27f, height = size * .605f;
        batch.setColor(.67f,.64f,.84f,1);
        batch.draw(stoneArch(),x-width/2,sill,width,height,.42f,.78f,.58f,.35f);
        doorways.drawTowerLeaves(batch,x,sill,width,height,open);
        batch.setColor(.82f,.82f,.90f,1);
        batch.draw(towerFrame,x-size/2,sill-size*.135f,size,size);
        batch.setColor(Color.WHITE);
    }

    private void drawArch(SpriteBatch batch, float x, float sill, float size) {
        stoneArch();
        batch.setColor(.85f,.77f,.76f,1);
        batch.draw(arch, x-size/2, sill-size*.20f, size, size);
        batch.setColor(Color.WHITE);
    }

    void draw(SpriteBatch batch, Room room) {
        float x = game.portalX(room), y = game.portalY(room);
        drawArch(batch,x,y+8,180);
        drawBarrier(batch,room,x,y+8,77,78);
    }

    private void drawBarrier(SpriteBatch batch, Room room, float x, float sill, float width, float height) {
        float closed = 1 - openness(room);
        if (closed <= 0) return;
        Texture gate = gates.computeIfAbsent(room.dungeonId, key -> {
            Texture t = new Texture(Gdx.files.internal("sprites/gate_"+key+".png"), true);
            t.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
            return t;
        });
        // The lower edge rises into the lintel. Preserve the scale of every bar as it retracts.
        batch.setColor(Color.WHITE);
        batch.draw(gate, x-width/2, sill+height*(1-closed), width, height*closed, 0,1,1,1-closed);
    }
    public void dispose(){if(arch!=null)arch.dispose();if(towerFrame!=null)towerFrame.dispose();for(Texture t:gates.values())t.dispose();}
}
