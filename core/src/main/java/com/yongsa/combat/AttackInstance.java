package com.yongsa.combat;

import com.yongsa.config.Balance;

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
    public int combo = 1;
    /** 연속 공격의 마지막 타(마무리 일격)인지. */
    public boolean finisher;
    /** 머리 위에서 내려찍는 마무리 (그리는 동작만 다르고 판정은 같다). */
    public boolean slam;
    public float originX, originY;
    public boolean originLocked;
    /** 충격파는 판정 시작 시점에 벽까지 잘라낸 길이를 고정한다. */
    public float effectiveLength = -1;
    public final HitShape shape = HitShape.box(0, 0, 1, 0, 0, 0);
    public final Set<Enemy> hit = new HashSet<>();
    /** 이 공격에 적용된 무기 형태 (검기·원형 충격·상태 이상). 없으면 null. */
    public Balance.FormDef form;
    /** 검기도 벽까지 잘라낸 길이를 판정 시작 시점에 고정한다. */
    public float waveLength = -1;
    /** 형태가 더하는 판정 (검기 상자 / 원형 충격). 쓰이지 않으면 null. */
    public HitShape wave, burst;

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
