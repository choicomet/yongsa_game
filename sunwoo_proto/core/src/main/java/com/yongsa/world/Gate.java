package com.yongsa.world;

import com.badlogic.gdx.math.Rectangle;

/** 던전 입구 봉인. 앞 던전 보스를 잡으면 열린다 (FR-맵-08). */
public class Gate {
    public final String dungeon;
    public final Rectangle bounds;
    private boolean open;

    public Gate(String dungeon, Rectangle bounds) {
        this.dungeon = dungeon;
        this.bounds = new Rectangle(bounds);
    }

    public boolean open() { return open; }
    public void setOpen(boolean o) { open = o; }
}
