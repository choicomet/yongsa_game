package com.yongsa.world;

/** 방 벽에 난 문 하나 (너비 3칸). 잠기면 막힌다. */
public class Door {
    public enum Side { LEFT, RIGHT, DOWN, UP }

    public final Room room;
    public final Side side;
    /** 문이 차지하는 벽 칸들. */
    public final int[] tilesX, tilesY;
    /** 문 바로 안쪽 가운데 칸 (방 안). */
    public final int insideX, insideY;
    /** 문 바로 바깥 가운데 칸 (통로). */
    public final int outsideX, outsideY;
    public Link link;
    /** 방 잠금 때문에 닫힘. */
    public boolean lockedByFight;

    Door(Room room, Side side, int[] tx, int[] ty, int inX, int inY, int outX, int outY) {
        this.room = room;
        this.side = side;
        this.tilesX = tx;
        this.tilesY = ty;
        this.insideX = inX;
        this.insideY = inY;
        this.outsideX = outX;
        this.outsideY = outY;
    }

    public boolean isClosed() {
        return lockedByFight || (link != null && link.gateClosed);
    }

    public boolean horizontal() {
        return side == Side.DOWN || side == Side.UP;
    }
}
