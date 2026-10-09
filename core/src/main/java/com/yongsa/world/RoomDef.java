package com.yongsa.world;

import com.badlogic.gdx.math.Rectangle;

/** Tiled rooms 레이어의 방 하나. 좌표는 y가 위로 증가하는 월드 픽셀. */
public class RoomDef {
    public enum Kind { CAMP, ORE, BATTLE, ANTE, BOSS }

    public final String id;
    public final String zone;
    public final Kind kind;
    public final Rectangle bounds;

    public RoomDef(String id, String zone, Kind kind, Rectangle bounds) {
        this.id = id;
        this.zone = zone;
        this.kind = kind;
        this.bounds = bounds;
    }

    public boolean isCombat() {
        return kind == Kind.BATTLE || kind == Kind.BOSS;
    }

    public float centerX() {
        return bounds.x + bounds.width / 2f;
    }

    public float centerY() {
        return bounds.y + bounds.height / 2f;
    }
}
