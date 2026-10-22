package com.yongsa.world;

import com.badlogic.gdx.math.Rectangle;

/** 방 문. 방이 전투 중이면 잠긴다. */
public class Door {
    public final Rectangle bounds;
    private boolean locked;

    public Door(Rectangle bounds) {
        this.bounds = new Rectangle(bounds);
    }

    public boolean locked() { return locked; }
    void setLocked(boolean l) { locked = l; }
}
