package com.yongsa.combat;

/** 걷기 그림에 쓰는 4방향 (FR-전투-12). */
public enum Facing {
    DOWN, UP, LEFT, RIGHT;

    public static Facing of(float dx, float dy) {
        if (Math.abs(dx) > Math.abs(dy)) return dx < 0 ? LEFT : RIGHT;
        return dy < 0 ? DOWN : UP;
    }
}
