package com.yongsa.world;

/** 캠프의 시설 (대장간, 침대, 모닥불). 타일 위에 놓이고 지나갈 수 없다. */
public class Prop {
    public enum Kind { FORGE, BED, CAMPFIRE }

    public final Kind kind;
    public final int tileX, tileY, w, h;

    public Prop(Kind kind, int tileX, int tileY, int w, int h) {
        this.kind = kind;
        this.tileX = tileX;
        this.tileY = tileY;
        this.w = w;
        this.h = h;
    }

    public boolean covers(int tx, int ty) {
        return tx >= tileX && tx < tileX + w && ty >= tileY && ty < tileY + h;
    }
}
