package com.yongsa.combat;

import com.badlogic.gdx.math.Rectangle;
import com.yongsa.config.Balance;

/** 실행 중 용사 상태. 위치와 체력은 저장하지 않는다(불러오면 항상 캠프에서 HP 최대). */
public class Hero {
    /** 발 위치(겹침 순서 기준). */
    public float x, y;
    public int hp;
    public int maxHp;
    public int aimDir = 6;
    public boolean faceLeft;
    public float invuln;
    public float kbTime, kbVx, kbVy;
    public AttackInstance attack;
    public float shockCooldown;
    public boolean dead;
    public float deathTimer;
    public boolean moving;
    public float animTime;
    public float hurtFlash;

    private final float bodyW, bodyH, hurtRadius;
    private final Rectangle body = new Rectangle();

    public Hero(Balance.HeroDef def) {
        this.maxHp = def.maxHp;
        this.hp = def.maxHp;
        this.bodyW = def.bodyWidth;
        this.bodyH = def.bodyHeight;
        this.hurtRadius = def.hurtRadius;
    }

    public Rectangle body() {
        return bodyAt(x, y);
    }

    public Rectangle bodyAt(float fx, float fy) {
        return body.set(fx - bodyW / 2f, fy - bodyH / 2f, bodyW, bodyH);
    }

    /** 피격 판정 원의 중심 (몸통 중앙). */
    public float hurtX() {
        return x;
    }

    public float hurtY() {
        return y + 12f;
    }

    public float hurtRadius() {
        return hurtRadius;
    }

    public boolean attacking() {
        return attack != null;
    }
}
