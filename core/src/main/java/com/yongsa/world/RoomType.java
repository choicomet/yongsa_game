package com.yongsa.world;

public enum RoomType {
    CAMP, MOB, ORE, BOSS, FINAL;

    /** 들어가면 문이 잠기고 몬스터가 나오는 방. */
    public boolean hasFight() {
        return this == MOB || this == BOSS || this == FINAL;
    }
}
