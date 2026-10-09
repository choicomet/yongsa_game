package com.yongsa.combat;

import java.util.HashSet;
import java.util.Set;

/** 용사의 공격 한 번. 같은 공격에서 같은 적은 한 번만 맞는다. */
public class AttackInstance {
    public enum Kind { NORMAL, SHOCKWAVE }
    public enum Phase { WINDUP, ACTIVE, RECOVERY, DONE }

    public final Kind kind;
    public final int dir;
    public final float windup, active, recovery;
    public final int damage;
    public final float length, width;
    public float t;
    /** 충격파는 판정 시작 시점에 벽까지 잘라낸 길이를 고정한다. */
    public float effectiveLength = -1;
    public final HitShape shape = HitShape.box(0, 0, 1, 0, 0, 0);
    public final Set<Enemy> hit = new HashSet<>();

    public AttackInstance(Kind kind, int dir, float windup, float active, float recovery, int damage, float length, float width) {
        this.kind = kind;
        this.dir = dir;
        this.windup = windup;
        this.active = active;
        this.recovery = recovery;
        this.damage = damage;
        this.length = length;
        this.width = width;
    }

    public Phase phase() {
        if (t < windup) return Phase.WINDUP;
        if (t < windup + active) return Phase.ACTIVE;
        if (t < windup + active + recovery) return Phase.RECOVERY;
        return Phase.DONE;
    }

    public float total() {
        return windup + active + recovery;
    }
}
