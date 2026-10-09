package com.yongsa.combat;

import com.yongsa.config.Balance;

/** 일반 적과 보스의 실행 상태. 보스는 bossDef 가 채워진다. */
public class Enemy {
    public enum State { IDLE, CHASE, TELEGRAPH, ATTACK, RECOVER }

    public final Balance.EnemyDef def;
    public final Balance.BossDef bossDef;
    public final String id;
    public float x, y;
    public int hp;
    public final int maxHp;
    public final float radius;
    public final float speed;
    public final int damage;

    public State state = State.IDLE;
    public float stateTime;
    public float grace;
    public float flash;
    public float kbTime, kbVx, kbVy;
    public boolean faceLeft;
    public float animTime;

    // 현재 공격
    public String attackId = "";
    public float telegraphTime, activeTime, recoverTime;
    public float lockDx = 1, lockDy;
    /** 예고 영역(시각화와 판정 공용). */
    public HitShape shape;
    /** 돌진 중 실제로 움직이는 판정 원. */
    public HitShape dashShape;
    public boolean hitHero;
    public float dashSpeed, dashLeft;

    // 보스 패턴
    public int patternIndex;
    public boolean enraged;
    public float aiTimer;

    public Enemy(Balance.EnemyDef def, float x, float y) {
        this.def = def;
        this.bossDef = null;
        this.id = def.id;
        this.x = x;
        this.y = y;
        this.hp = def.hp;
        this.maxHp = def.hp;
        this.radius = def.radius;
        this.speed = def.speed;
        this.damage = def.damage;
    }

    public Enemy(Balance.BossDef def, float x, float y) {
        this.def = null;
        this.bossDef = def;
        this.id = def.id;
        this.x = x;
        this.y = y;
        this.hp = def.hp;
        this.maxHp = def.hp;
        this.radius = def.radius;
        this.speed = def.speed;
        this.damage = def.damage;
    }

    public boolean isBoss() {
        return bossDef != null;
    }

    public boolean alive() {
        return hp > 0;
    }

    /** 예고 또는 공격 중인 적. 회복 중은 제외한다. */
    public boolean aggressive() {
        return state == State.TELEGRAPH || state == State.ATTACK;
    }

    public String name() {
        return isBoss() ? bossDef.name : def.name;
    }

    /** 피격 판정 원 중심 (몸통 중앙). */
    public float hurtY() {
        return y + radius * 0.6f;
    }

    /** 지금 유효한 공격 판정. 공격 중이 아니면 null. */
    public HitShape activeHit() {
        if (state != State.ATTACK) return null;
        return "charge".equals(attackId) ? dashShape : shape;
    }

    public void enter(State s) {
        state = s;
        stateTime = 0;
    }
}
