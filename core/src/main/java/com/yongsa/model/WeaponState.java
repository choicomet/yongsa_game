package com.yongsa.model;

/** 보유 중인 무기 하나. 종류별로 최대 1개만 존재한다. */
public class WeaponState {
    public String type;
    public int level;
    /** 현재 목표 단계(level+1)에서 누적된 실패 횟수. 성공하면 0으로 돌아간다. */
    public int failCount;

    public WeaponState() {}

    public WeaponState(String type) {
        this.type = type;
    }

    public WeaponState copy() {
        WeaponState w = new WeaponState(type);
        w.level = level;
        w.failCount = failCount;
        return w;
    }
}
