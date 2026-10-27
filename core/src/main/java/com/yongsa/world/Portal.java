package com.yongsa.world;

/** 보스방(또는 최종 전장)을 비우면 가운데에 나타나는 포탈. E로 캠프 시작 지점으로 간다. */
public class Portal {
    public final String roomId;
    public final float x, y;
    private boolean active;

    public Portal(String roomId, float x, float y) {
        this.roomId = roomId;
        this.x = x;
        this.y = y;
    }

    public boolean active() { return active; }
    public void setActive(boolean a) { active = a; }
}
