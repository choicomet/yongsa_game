package com.yongsa.world;

/** 두 방을 잇는 문 한 쌍과 그 사이 통로. gate 가 있으면 조건을 채워야 열린다. */
public class Link {
    public final Room a, b;
    /** null, 또는 열리는 조건: 던전 ID(LEFT/RIGHT/TOP) 또는 FINAL. */
    public final String gate;
    public Door doorA, doorB;
    public boolean gateClosed;

    Link(Room a, Room b, String gate) {
        this.a = a;
        this.b = b;
        this.gate = gate;
        this.gateClosed = gate != null;
    }

    public Room other(Room r) {
        return r == a ? b : a;
    }
}
