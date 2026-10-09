package com.yongsa.world;

import java.util.ArrayList;
import java.util.List;

/** 방 하나. x0~x1, y0~y1 은 벽을 포함한 타일 범위 (양 끝 포함). */
public class Room {
    public final String id;
    public final RoomType type;
    /** LEFT/RIGHT/TOP, 캠프면 null. */
    public final String dungeonId;
    public final int cellX, cellY;
    public final int x0, y0, x1, y1;
    public final List<Door> doors = new ArrayList<>();

    Room(String id, RoomType type, String dungeonId, int cellX, int cellY, int x0, int y0, int x1, int y1) {
        this.id = id;
        this.type = type;
        this.dungeonId = dungeonId;
        this.cellX = cellX;
        this.cellY = cellY;
        this.x0 = x0;
        this.y0 = y0;
        this.x1 = x1;
        this.y1 = y1;
    }

    public boolean contains(int tx, int ty) {
        return tx >= x0 && tx <= x1 && ty >= y0 && ty <= y1;
    }

    /** 벽 안쪽 바닥인가. */
    public boolean inside(int tx, int ty) {
        return tx > x0 && tx < x1 && ty > y0 && ty < y1;
    }

    /** 벽에서 margin 칸 이상 떨어진 안쪽인가. */
    public boolean insideWithMargin(int tx, int ty, int margin) {
        return tx > x0 + margin && tx < x1 - margin && ty > y0 + margin && ty < y1 - margin;
    }

    public float centerTileX() {
        return (x0 + x1) / 2f;
    }

    public float centerTileY() {
        return (y0 + y1) / 2f;
    }

    public void setFightLock(boolean locked) {
        for (Door d : doors) d.lockedByFight = locked;
    }
}
