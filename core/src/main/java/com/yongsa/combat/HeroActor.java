package com.yongsa.combat;

/** 맵 위의 용사. 체력은 GameState 에 있고 여기서는 움직임과 쿨타임만 다룬다. */
public class HeroActor extends Actor {
    public Facing facing = Facing.DOWN;
    /** 공격 방향 (마지막으로 움직인 8방향). */
    public float aimX = 0, aimY = -1;
    public float attackCd, spinCd, dashCd, mineCd;
    public float invuln;
    public float dashT;
    public float walkTime;
    public boolean moving;
    /** 그리기용: 지금 휘두르는 공격 종류와 남은 시간. */
    public int swingKind;
    public float swingT;
    public float mineSwingT;

    public HeroActor() {
        hw = 9;
        hh = 6;
        hurtRadius = 12;
    }

    public boolean dashing() {
        return dashT > 0;
    }
}
