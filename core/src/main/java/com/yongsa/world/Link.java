package com.yongsa.world;

/** 두 방을 잇는 문 한 쌍 (한쪽 문을 지나면 다른 쪽 문 앞에 선다). gate 가 있으면 조건을 채워야 열린다. */
public class Link {
    public final Room a, b;
    /** null, 또는 열리는 조건: 던전 ID(LEFT/RIGHT/TOP)  */
    public final String gate;
    public Door doorA, doorB;
    public boolean gateClosed;
    /** 한쪽 길: doorA 로만 지나 doorB 앞에 선다. doorB 는 다른 이음에 속한다. */
    public boolean oneWay;

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
