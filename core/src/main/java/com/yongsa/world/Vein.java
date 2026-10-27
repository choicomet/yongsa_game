package com.yongsa.world;

import com.badlogic.gdx.math.Rectangle;

/** 광맥. 지나갈 수 없고, 3번 치면 부서지며 정해 둔 개수의 광물을 준다. */
public class Vein {
    public final String ore;
    public final float x, y;
    public final Rectangle body;
    private int hitsLeft;
    private final int amount;
    public float shake;

    public Vein(String ore, float x, float y, int hits, int amount) {
        this.ore = ore;
        this.x = x;
        this.y = y;
        this.hitsLeft = hits;
        this.amount = amount;
        this.body = new Rectangle(x - 12, y - 6, 24, 14);
    }

    public boolean depleted() { return hitsLeft <= 0; }
    public int hitsLeft() { return hitsLeft; }
    public int amount() { return amount; }

    /** 한 번 친다. 이번에 부서졌으면 true. */
    public boolean hit() {
        if (hitsLeft <= 0) return false;
        hitsLeft--;
        shake = 0.15f;
        return hitsLeft == 0;
    }

    /** 저장에서 복원할 때. */
    public void setHitsLeft(int h) { hitsLeft = h; }
}
